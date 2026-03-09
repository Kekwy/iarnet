package com.kekwy.iarnet.sdk;

import com.google.protobuf.ByteString;
import com.kekwy.iarnet.proto.api.SubmissionStatus;
import com.kekwy.iarnet.proto.api.SubmitWorkflowRequest;
import com.kekwy.iarnet.proto.api.SubmitWorkflowResponse;
import com.kekwy.iarnet.proto.api.WorkflowServiceGrpc;
import com.kekwy.iarnet.proto.common.FunctionDescriptor;
import com.kekwy.iarnet.proto.common.Lang;
import com.kekwy.iarnet.proto.common.Type;
import com.kekwy.iarnet.proto.workflow.Edge;
import com.kekwy.iarnet.proto.workflow.Node;
import com.kekwy.iarnet.proto.workflow.NodeConfig;
import com.kekwy.iarnet.proto.workflow.WorkflowGraph;
import com.kekwy.iarnet.sdk.function.*;
import com.kekwy.iarnet.sdk.util.IDUtil;
import com.kekwy.iarnet.sdk.util.SerializationUtil;
import com.kekwy.iarnet.sdk.util.TypeExtractor;
import com.kekwy.iarnet.sdk.type.TypeToken;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 工作流 DSL 入口，用于创建 source、task 并执行。
 */
public class Workflow {

    private final String name;

    public String getName() {
        return name;
    }

    private Workflow(String name) {
        this.name = name;
    }

    // ======================== 构建 WorkflowGraph ========================

    private static final String ENV_APP_ID = "IARNET_APP_ID";

    public WorkflowGraph buildGraph() {
        String applicationId = System.getenv(ENV_APP_ID);
        if (applicationId == null || applicationId.isBlank()) {
            throw new IllegalStateException(
                    "环境变量 " + ENV_APP_ID + " 未设置，无法确定 application ID");
        }
        return buildGraph(applicationId);
    }

    public WorkflowGraph buildGraph(String applicationId) {
        List<Node> finalizedNodes = finalizeNodesWithTypes();
        return WorkflowGraph.newBuilder()
                .setWorkflowId(com.kekwy.iarnet.sdk.util.IDUtil.genUUID())
                .setApplicationId(applicationId)
                .setName(name)
                .addAllNodes(finalizedNodes)
                .addAllEdges(edges)
                .build();
    }

    /**
     * 在 buildGraph 前补齐所有节点的 output_type：若反射推断失败，从 returns() 的提示中获取；
     * 若仍无法获取则抛错。Sink 节点（无出边）无需 output_type，跳过。
     */
    private List<Node> finalizeNodesWithTypes() {
        Set<String> hasOutgoing = new HashSet<>();
        for (Edge e : edges) {
            hasOutgoing.add(e.getFromNodeId());
        }
        List<Node> result = new ArrayList<>();
        for (Node node : nodes) {
            FunctionDescriptor fd = node.getFunction();
            if (!fd.hasOutputType() || fd.getOutputType().getKind() == com.kekwy.iarnet.proto.common.TypeKind.TYPE_KIND_UNSPECIFIED) {
                if (!hasOutgoing.contains(node.getId())) {
                    result.add(node);
                    continue;
                }
                java.lang.reflect.Type hint = nodeOutputTypeHints.get(node.getId());
                if (hint == null) {
                    throw new IllegalStateException(
                            "无法推断节点 " + node.getId() + " 的输出类型，请对该 flow 调用 .returns(new TypeToken<YourType>() {}) 提供类型提示");
                }
                FunctionDescriptor patched = fd.toBuilder()
                        .setOutputType(com.kekwy.iarnet.proto.Types.fromType(hint))
                        .build();
                result.add(node.toBuilder().setFunction(patched).build());
            } else {
                result.add(node);
            }
        }
        return result;
    }

    // ======================== gRPC 提交 ========================

    private static final String ENV_GRPC_PORT = "IARNET_GRPC_PORT";
    private static final String DEFAULT_GRPC_HOST = "localhost";

    public void execute() {
        WorkflowGraph graph = buildGraph();

        String portStr = System.getenv(ENV_GRPC_PORT);
        if (portStr == null || portStr.isBlank()) {
            throw new IllegalStateException(
                    "环境变量 " + ENV_GRPC_PORT + " 未设置，无法连接 control-plane gRPC 服务");
        }
        int port = Integer.parseInt(portStr);

        submit(graph, DEFAULT_GRPC_HOST, port);
    }

    public static void submit(WorkflowGraph graph, String host, int port) {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();

        try {
            WorkflowServiceGrpc.WorkflowServiceBlockingStub stub =
                    WorkflowServiceGrpc.newBlockingStub(channel);

            SubmitWorkflowRequest request = SubmitWorkflowRequest.newBuilder()
                    .setGraph(graph)
                    .build();

            SubmitWorkflowResponse response = stub.submitWorkflow(request);

            if (response.getStatus() == SubmissionStatus.ACCEPTED) {
                System.out.println("[Workflow] 提交成功: submissionId=" + response.getSubmissionId()
                        + ", message=" + response.getMessage());
            } else {
                throw new RuntimeException(
                        "工作流提交被拒绝: " + response.getMessage());
            }
        } catch (StatusRuntimeException e) {
            throw new RuntimeException(
                    "gRPC 调用失败: " + e.getStatus(), e);
        } finally {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                channel.shutdownNow();
            }
        }
    }

    public static Workflow create(String name) {
        return new Workflow(name);
    }

    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();

    /**
     * 节点 ID -> 用户通过 returns() 传入的输出类型提示，用于类型擦除时兜底
     */
    private final Map<String, java.lang.reflect.Type> nodeOutputTypeHints = new HashMap<>();

    public <T> Flow<T> input(String name, InputFunction<T> function) {
        return input(name, function, null);
    }

    public <T> Flow<T> input(String name, InputFunction<T> function, ExecutionConfig config) {
        Node node = addNode(name, null, function, config);
        return new DefaultFlow<>(node);
    }

    // ======== DefaultFlow ========

    private record Precursor(Node node, int port) {
        Precursor(Node node) {
            this(node, 0);
        }
    }

    private class DefaultFlow<T> implements Flow<T> {

        private final Node precursor;

        private DefaultFlow(Node precursor) {
            this.precursor = precursor;
        }


        @Override
        public <R> Flow<R> then(String name, TaskFunction<T, R> function) {
            return then(name, function, null);
        }

        @Override
        public <R> Flow<R> then(String name, TaskFunction<T, R> function, ExecutionConfig config) {
            return new DefaultFlow<>(addNode(name, precursor, function, config));
        }

        @Override
        public EndFlow<T> then(String name, OutputFunction<T> function) {
            return then(name, function, null);
        }

        @Override
        public EndFlow<T> then(String name, OutputFunction<T> function, ExecutionConfig config) {
            return new DefaultEndFlow<>(addNode(name, precursor, function, config));
        }

        @Override
        public <U, V> Flow<V> union(String name, Flow<U> other, UnionFunction<T, U, V> function) {
            return union(name, other, function, null);
        }

        @Override
        public <U, V> Flow<V> union(String name, Flow<U> other, UnionFunction<T, U, V> function, ExecutionConfig config) {
            if (!(other instanceof DefaultFlow<U> otherFlow)) {
                throw new IllegalArgumentException("union() 仅支持同一 workflow 中的 flow");
            }
            return new DefaultFlow<>(addUnionNode(name, precursor, otherFlow.precursor, function, config));
        }

        @Override
        public ConditionalFlow<T> when(ConditionFunction<T> condition) {
            return new DefaultConditionalFlow<>(precursor, condition);
        }


        @Override
        public Flow<T> returns(TypeToken<T> typeHint) {
            nodeOutputTypeHints.put(precursor.getId(), typeHint.getType());
            return this;
        }

    }

    // ======== DefaultEndFlow ========

    private static class DefaultEndFlow<T> implements EndFlow<T> {
        @SuppressWarnings("FieldCanBeLocal")
        private final Node precursor; // 预留字段

        private DefaultEndFlow(Node precursor) {
            this.precursor = precursor;
        }
    }

    // ======== DefaultConditionalFlow ========

    private class DefaultConditionalFlow<T> implements ConditionalFlow<T> {
        private final Node precursor;
        private final ConditionFunction<T> condition;

        private DefaultConditionalFlow(Node precursor, ConditionFunction<T> condition) {
            this.precursor = precursor;
            this.condition = condition;
        }

        @Override
        public <R> Flow<R> then(String name, TaskFunction<T, R> function) {
            return then(name, function, null);
        }

        @Override
        public <R> Flow<R> then(String name, TaskFunction<T, R> function, ExecutionConfig config) {
            return new DefaultFlow<>(addNode(name, precursor, function, config, condition));
        }

        @Override
        public EndFlow<T> then(String name, OutputFunction<T> function) {
            return then(name, function, null);
        }

        @Override
        public EndFlow<T> then(String name, OutputFunction<T> function, ExecutionConfig config) {
            return new DefaultEndFlow<>(addNode(name, precursor, function, config, condition));
        }
    }

    // ======================== 共用工具方法 ========================

    private static String uniqueNodeId(String name) {
        return name + "_" + IDUtil.genUUID();
    }

    private Node addNode(String name, Node precursor,
                         Function function, ExecutionConfig config) {
        return addNode(name, precursor, function, config, null);
    }

    private Node addNode(String name, Node precursor,
                         Function function, ExecutionConfig config,
                         ConditionFunction<?> conditionFunction) {
        String nodeId = uniqueNodeId(name);
        Type inputType = precursor != null ? precursor.getFunction().getOutputType() : null;
        Type outputType = resolveOutputType(function, nodeId);

        Node node = Node.newBuilder()
                .setId(nodeId)
                .setName(name)
                .setFunction(buildFunctionDescriptor(function, inputType, outputType, nodeId))
                .setNodeConfig(buildNodeConfig(config))
                .build();
        nodes.add(node);
        if (precursor != null) {
            Type conditionInputType = precursor.getFunction().getOutputType();
            edges.add(buildEdge(precursor.getId(), nodeId, conditionFunction, conditionInputType));
        }
        return node;
    }

    private Node addUnionNode(String name, Node precursor1, Node precursor2,
                              UnionFunction<?, ?, ?> function, ExecutionConfig config) {
        String nodeId = uniqueNodeId(name);
        Type inputType1 = precursor1.getFunction().getOutputType();
        Type inputType2 = precursor2.getFunction().getOutputType();
        Type outputType = resolveOutputType(function, nodeId);

        Node node = Node.newBuilder()
                .setId(nodeId)
                .setName(name)
                .setFunction(buildFunctionDescriptor(function, List.of(inputType1, inputType2), outputType, nodeId))
                .setNodeConfig(buildNodeConfig(config))
                .build();
        nodes.add(node);
        edges.add(buildEdge(precursor1.getId(), nodeId, null, inputType1));
        edges.add(buildEdge(precursor2.getId(), nodeId, null, inputType2));
        return node;
    }

    private static Edge buildEdge(String fromNodeId, String toNodeId,
                                  ConditionFunction<?> conditionFunction, Type conditionInputType) {
        FunctionDescriptor conditionFn = conditionFunction != null
                ? buildFunctionDescriptorForCondition(conditionFunction, conditionInputType)
                : FunctionDescriptor.getDefaultInstance();
        return Edge.newBuilder()
                .setFromNodeId(fromNodeId)
                .setToNodeId(toNodeId)
                .setConditionFunction(conditionFn)
                .build();
    }

    private static NodeConfig buildNodeConfig(ExecutionConfig config) {
        if (config == null) {
            config = ExecutionConfig.of();
        }
        return NodeConfig.newBuilder()
                .setReplicas(config.getReplicas())
                .setResourceSpec(config.getResourceSpec())
                .build();
    }

    /**
     * 解析节点输出类型：优先从函数泛型反射推断，失败则用 returns() 提示，仍无则抛错。
     * OutputFunction 无输出，返回 null。
     */
    private Type resolveOutputType(Function function, String nodeId) {
        if (function instanceof OutputFunction) {
            return null;
        }
        java.lang.reflect.Type extracted = null;
        if (function instanceof InputFunction<?> fn) {
            extracted = TypeExtractor.extractOutputType(fn, InputFunction.class, 0);
        } else if (function instanceof TaskFunction<?, ?> fn) {
            extracted = TypeExtractor.extractOutputType(fn, TaskFunction.class, 1);
        } else if (function instanceof UnionFunction<?, ?, ?> fn) {
            extracted = TypeExtractor.extractOutputType(fn, UnionFunction.class, 2);
//        } else if (function instanceof PythonFunction pf) {
//            extracted = pf.getReturnType();
        }
        if (extracted != null && extracted != Object.class) {
            return com.kekwy.iarnet.proto.Types.fromType(extracted);
        }
        java.lang.reflect.Type hint = nodeOutputTypeHints.get(nodeId);
        if (hint != null) {
            return com.kekwy.iarnet.proto.Types.fromType(hint);
        }
        return null; // 交由 finalizeNodesWithTypes 检查并抛错
    }

    private static FunctionDescriptor buildFunctionDescriptor(Function function,
                                                              Type inputType, Type outputType, String nodeId) {
        return buildFunctionDescriptor(function, inputType != null ? List.of(inputType) : List.of(), outputType, nodeId);
    }

    private static FunctionDescriptor buildFunctionDescriptor(Function function,
                                                              List<Type> inputTypes, Type outputType, String nodeId) {

        if (function.getLang() == Lang.LANG_GO || function.getLang() == Lang.LANG_UNSPECIFIED) {
            throw new IllegalArgumentException("不支持的函数语言: " + function.getLang());
        }

        FunctionDescriptor.Builder b = FunctionDescriptor.newBuilder()
                .setLang(function.getLang());

        if (function instanceof PythonTaskFunction<?, ?> fn) {
            b.setFunctionIdentifier(fn.getFunctionIdentifier())
                    .setSourcePath(fn.getSourcePath());
        } else if (function.getLang() == Lang.LANG_JAVA) {
            b.setFunctionIdentifier(function.getClass().getName())
                    .setSerializedFunction(ByteString.copyFrom(SerializationUtil.serialize(function)));
        }

        for (Type t : inputTypes) {
            b.addInputsType(t);
        }
        if (outputType != null) {
            b.setOutputType(outputType);
        }
        return b.build();


    }

    /**
     * 构建边的条件函数的 FunctionDescriptor（input 来自前驱输出，output 为 boolean）。
     */
    private static FunctionDescriptor buildFunctionDescriptorForCondition(ConditionFunction<?> condition, Type inputType) {
        FunctionDescriptor.Builder b = FunctionDescriptor.newBuilder()
                .setLang(condition.getLang())
                .setFunctionIdentifier(condition.getClass().getName())
                .setSerializedFunction(ByteString.copyFrom(SerializationUtil.serialize(condition)));
        if (inputType != null) {
            b.addInputsType(inputType);
        }
        b.setOutputType(com.kekwy.iarnet.proto.Types.BOOLEAN);
        return b.build();
    }
}
