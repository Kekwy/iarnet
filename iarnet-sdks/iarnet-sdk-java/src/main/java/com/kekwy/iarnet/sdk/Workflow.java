package com.kekwy.iarnet.sdk;

import com.google.protobuf.ByteString;
import com.kekwy.iarnet.proto.api.SubmissionStatus;
import com.kekwy.iarnet.proto.api.SubmitWorkflowRequest;
import com.kekwy.iarnet.proto.api.SubmitWorkflowResponse;
import com.kekwy.iarnet.proto.api.WorkflowServiceGrpc;
import com.kekwy.iarnet.proto.common.FunctionDescriptor;
import com.kekwy.iarnet.proto.common.Lang;
import com.kekwy.iarnet.proto.common.Type;
import com.kekwy.iarnet.proto.common.TypeKind;
import com.kekwy.iarnet.proto.workflow.Edge;
import com.kekwy.iarnet.proto.workflow.Node;
import com.kekwy.iarnet.proto.workflow.NodeConfig;
import com.kekwy.iarnet.proto.workflow.WorkflowGraph;
import com.kekwy.iarnet.sdk.exception.IarnetCommunicationException;
import com.kekwy.iarnet.sdk.exception.IarnetConfigurationException;
import com.kekwy.iarnet.sdk.exception.IarnetSubmissionException;
import com.kekwy.iarnet.sdk.exception.IarnetValidationException;
import com.kekwy.iarnet.sdk.function.ConditionFunction;
import com.kekwy.iarnet.sdk.function.Function;
import com.kekwy.iarnet.sdk.function.GoTaskFunction;
import com.kekwy.iarnet.sdk.function.InputFunction;
import com.kekwy.iarnet.sdk.function.OutputFunction;
import com.kekwy.iarnet.sdk.function.PythonTaskFunction;
import com.kekwy.iarnet.sdk.function.TaskFunction;
import com.kekwy.iarnet.sdk.function.UnionFunction;
import com.kekwy.iarnet.sdk.type.TypeToken;
import com.kekwy.iarnet.sdk.util.IDUtil;
import com.kekwy.iarnet.sdk.util.SerializationUtil;
import com.kekwy.iarnet.sdk.util.TypeExtractor;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 工作流 DSL 入口。
 * <p>
 * 通过 {@link #create(String)} 创建实例，{@link #input(String, InputFunction)} 定义 Source，
 * 返回的 {@link Flow} 可链式追加任务、条件分支、合并与输出。{@link #buildGraph()} 构建图结构，
 * {@link #execute()} 或 {@link #submit(WorkflowGraph, String, int)} 提交至 control-plane。
 * <p>
 * 典型用法：
 * <pre>{@code
 * Workflow w = Workflow.create("example");
 * w.input("src", Inputs.of(1, 2, 3))
 *  .then("double", x -> x * 2)
 *  .then("sink", Outputs.println());
 * w.execute();  // 需设置 IARNET_APP_ID、IARNET_GRPC_PORT
 * }</pre>
 */
public class Workflow {

    private static final Logger LOG = Logger.getLogger(Workflow.class.getName());

    private static final String ENV_APP_ID = "IARNET_APP_ID";
    private static final String ENV_GRPC_PORT = "IARNET_GRPC_PORT";
    private static final String DEFAULT_GRPC_HOST = "localhost";

    private final String name;

    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();

    /**
     * 节点 ID -> 用户通过 returns() 传入的输出类型提示，用于类型擦除时兜底。
     */
    private final Map<String, java.lang.reflect.Type> nodeOutputTypeHints = new HashMap<>();

    private Workflow(String name) {
        this.name = name;
    }

    // ======================== 工厂与入口 ========================

    /**
     * 创建指定名称的工作流。
     *
     * @param name 工作流名称
     * @return 新 Workflow 实例
     */
    public static Workflow create(String name) {
        return new Workflow(name);
    }

    /** 工作流名称。 */
    public String getName() {
        return name;
    }

    /**
     * 定义输入源（Source）节点。
     *
     * @param name     节点名
     * @param function 输入函数，按序产出元素
     * @param <T>      输出元素类型
     * @return Flow，可继续链式 then / union / when
     */
    public <T> Flow<T> input(String name, InputFunction<T> function) {
        return input(name, function, null);
    }

    /**
     * 定义输入源节点，并指定执行配置。
     *
     * @param name     节点名
     * @param function 输入函数
     * @param config   副本数、资源等配置
     * @param <T>      输出元素类型
     * @return Flow
     */
    public <T> Flow<T> input(String name, InputFunction<T> function, ExecutionConfig config) {
        Node node = addNode(name, null, function, config);
        return new DefaultFlow<>(node);
    }

    // ======================== 构建 WorkflowGraph ========================

    /**
     * 从环境变量 {@value #ENV_APP_ID} 读取 application ID 并构建图。
     *
     * @return WorkflowGraph
     * @throws IarnetConfigurationException 若环境变量未设置
     * @throws IarnetValidationException     若存在节点输出类型无法推断
     */
    public WorkflowGraph buildGraph() {
        String applicationId = System.getenv(ENV_APP_ID);
        if (applicationId == null || applicationId.isBlank()) {
            throw new IarnetConfigurationException(
                    "环境变量 " + ENV_APP_ID + " 未设置，无法确定 application ID");
        }
        return buildGraph(applicationId);
    }

    /**
     * 使用指定 application ID 构建图。
     *
     * @param applicationId 应用 ID
     * @return WorkflowGraph
     * @throws IarnetValidationException 若存在节点输出类型无法推断
     */
    public WorkflowGraph buildGraph(String applicationId) {
        List<Node> finalizedNodes = finalizeNodesWithTypes();
        WorkflowGraph graph = WorkflowGraph.newBuilder()
                .setWorkflowId(IDUtil.genUUIDWith("wf"))
                .setApplicationId(applicationId)
                .setName(name)
                .addAllNodes(finalizedNodes)
                .addAllEdges(edges)
                .build();
        LOG.log(Level.FINE, "Workflow graph built: name={0}, nodes={1}, edges={2}",
                new Object[]{name, finalizedNodes.size(), edges.size()});
        return graph;
    }

    /**
     * 补齐所有有出边节点的 output_type。
     * <p>
     * 若反射推断失败，则从 returns() 的提示中获取；若仍无法获取则抛出
     * {@link IarnetValidationException}。Sink 节点（无出边）无需 output_type，跳过。
     */
    private List<Node> finalizeNodesWithTypes() {
        Set<String> hasOutgoing = new HashSet<>();
        for (Edge e : edges) {
            hasOutgoing.add(e.getFromNodeId());
        }
        List<Node> result = new ArrayList<>();
        for (Node node : nodes) {
            FunctionDescriptor fd = node.getFunction();
            if (!fd.hasOutputType() || fd.getOutputType().getKind() == TypeKind.TYPE_KIND_UNSPECIFIED) {
                if (!hasOutgoing.contains(node.getId())) {
                    result.add(node);
                    continue;
                }
                java.lang.reflect.Type hint = nodeOutputTypeHints.get(node.getId());
                if (hint == null) {
                    throw new IarnetValidationException(
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

    /**
     * 构建图并从环境变量 {@value #ENV_GRPC_PORT} 读取端口，提交至 localhost。
     *
     * @throws IarnetConfigurationException 若环境变量未设置或格式无效
     * @throws IarnetSubmissionException    若服务端拒绝提交
     * @throws IarnetCommunicationException 若 gRPC 调用失败
     */
    public void execute() {
        WorkflowGraph graph = buildGraph();

        String portStr = System.getenv(ENV_GRPC_PORT);
        if (portStr == null || portStr.isBlank()) {
            throw new IarnetConfigurationException(
                    "环境变量 " + ENV_GRPC_PORT + " 未设置，无法连接 control-plane gRPC 服务");
        }
        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            throw new IarnetConfigurationException(
                    "环境变量 " + ENV_GRPC_PORT + " 格式无效，期望整数: " + portStr, e);
        }
        LOG.log(Level.FINE, "Executing workflow: {0}, host={1}, port={2}", new Object[]{name, DEFAULT_GRPC_HOST, port});
        submit(graph, DEFAULT_GRPC_HOST, port);
    }

    /**
     * 将工作流图提交至 control-plane gRPC 服务。
     *
     * @param graph 工作流图
     * @param host  control-plane 地址
     * @param port  gRPC 端口
     * @throws IarnetSubmissionException    若服务端拒绝
     * @throws IarnetCommunicationException 若 gRPC 调用失败
     */
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
                LOG.log(Level.INFO, "Workflow submitted successfully: submissionId={0}, message={1}",
                        new Object[]{response.getSubmissionId(), response.getMessage()});
            } else {
                throw new IarnetSubmissionException(
                        "工作流提交被拒绝: " + response.getMessage());
            }
        } catch (StatusRuntimeException e) {
            throw new IarnetCommunicationException(
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
                throw new IarnetValidationException("union() 仅支持同一 workflow 中的 flow");
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

    // ======================== 内部工具方法 ========================

    /** 生成带名称前缀的唯一节点 ID。 */
    private static String uniqueNodeId(String name) {
        return name + "-" + IDUtil.genUUID();
    }

    /** 添加普通节点（任务或输出），并建立与前驱的边。 */
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

    /** 添加合并节点，连接两个前驱。 */
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

    /** 构建边，可选附带条件函数。 */
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

    /** 将 ExecutionConfig 转为 proto NodeConfig。 */
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
     * 解析节点输出类型。
     * <p>
     * 优先级：Python/Go 的 outputTypeHint > TypeExtractor 反射 > returns() 提示。
     * OutputFunction 无输出，返回 null。
     *
     * @param nodeId 节点 ID，用于查找 returns() 提示
     * @return proto Type，无法解析时返回 null（由 finalizeNodesWithTypes 检查）
     */
    private Type resolveOutputType(Function function, String nodeId) {
        if (function instanceof OutputFunction) {
            return null;
        }
        java.lang.reflect.Type extracted = null;
        if (function instanceof PythonTaskFunction<?, ?> fn && fn.getOutputTypeHint() != null) {
            extracted = fn.getOutputTypeHint();
        } else if (function instanceof GoTaskFunction<?, ?> fn && fn.getOutputTypeHint() != null) {
            extracted = fn.getOutputTypeHint();
        } else if (function instanceof InputFunction<?> fn) {
            extracted = TypeExtractor.extractOutputType(fn, InputFunction.class, 0);
        } else if (function instanceof TaskFunction<?, ?> fn) {
            extracted = TypeExtractor.extractOutputType(fn, TaskFunction.class, 1);
        } else if (function instanceof UnionFunction<?, ?, ?> fn) {
            extracted = TypeExtractor.extractOutputType(fn, UnionFunction.class, 2);
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

    /** 构建 FunctionDescriptor，支持 Java 序列化、Python、Go 三种形式。 */
    private static FunctionDescriptor buildFunctionDescriptor(Function function,
                                                              Type inputType, Type outputType, String nodeId) {
        return buildFunctionDescriptor(function, inputType != null ? List.of(inputType) : List.of(), outputType, nodeId);
    }

    private static FunctionDescriptor buildFunctionDescriptor(Function function,
                                                              List<Type> inputTypes, Type outputType, String nodeId) {

        if (function.getLang() == Lang.LANG_UNSPECIFIED) {
            throw new IarnetValidationException("不支持的函数语言: " + function.getLang());
        }

        FunctionDescriptor.Builder b = FunctionDescriptor.newBuilder()
                .setLang(function.getLang());

        if (function instanceof PythonTaskFunction<?, ?> fn) {
            b.setFunctionIdentifier(fn.getFunctionIdentifier())
                    .setSourcePath(fn.getSourcePath());
        } else if (function instanceof GoTaskFunction<?, ?> fn) {
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

    /** 构建边的条件函数描述符（input 来自前驱输出，output 为 boolean）。 */
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
