package com.kekwy.iarnet.sdk;

import com.google.protobuf.ByteString;
import com.kekwy.iarnet.proto.Types;
import com.kekwy.iarnet.proto.api.SubmissionStatus;
import com.kekwy.iarnet.proto.api.SubmitWorkflowRequest;
import com.kekwy.iarnet.proto.api.SubmitWorkflowResponse;
import com.kekwy.iarnet.proto.api.WorkflowServiceGrpc;
import com.kekwy.iarnet.proto.common.FunctionDescriptor;
import com.kekwy.iarnet.proto.common.Lang;
import com.kekwy.iarnet.proto.common.Type;
import com.kekwy.iarnet.proto.workflow.Edge;
import com.kekwy.iarnet.proto.workflow.WorkflowGraph;
import com.kekwy.iarnet.sdk.converter.SourceToNodeVisitor;
import com.kekwy.iarnet.sdk.converter.WorkflowGraphBuilder;
import com.kekwy.iarnet.sdk.function.*;
import com.kekwy.iarnet.sdk.util.IDUtil;
import com.kekwy.iarnet.sdk.util.SerializationUtil;
import com.kekwy.iarnet.sdk.util.TypeToken;
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
        WorkflowGraphBuilder builder = new WorkflowGraphBuilder();
        return builder.build(applicationId, nodes, edges);
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

    public <T> Flow<T> input(InputFunction<T> function) {
        SourceToNodeVisitor visitor = new SourceToNodeVisitor();
        SourceNode node = source.accept(visitor);
        nodes.add(node);
        return new DefaultFlow<>(List.of(new Precursor(node)));
    }

    public List<Node> getNodes() {
        return List.copyOf(nodes);
    }

    public List<Edge> getEdges() {
        return List.copyOf(edges);
    }


    // ======== DefaultFlow ========

    private record Precursor(Node node, int port) {
        Precursor(Node node) {
            this(node, 0);
        }
    }

    private class DefaultFlow<T> implements Flow<T> {

        // TODO: 什么场景下存在多个前驱
        private final List<Precursor> precursors;

        private DefaultFlow(List<Precursor> precursors) {
            this.precursors = precursors;
        }


        @Override
        public <R> Flow<R> then(String name, TaskFunction<T, R> function) {
            return then(name, function, null);
        }

        @Override
        public <R> Flow<R> then(String name, TaskFunction<T, R> function, ExecutionConfig config) {
            TaskNode node = addTaskNode(name, precursors, function, config, null);
            return new DefaultFlow<>(List.of(new Precursor(node)));
        }

        @Override
        public EndFlow<T> then(String name, OutputFunction<T> function) {
            return then(name, function, null);
        }

        @Override
        public EndFlow<T> then(String name, OutputFunction<T> function, ExecutionConfig config) {
            TaskNode node = addTaskNode(name, precursors, function, config, null);
            return new DefaultEndFlow<>(List.of(new Precursor(node)));
        }

        @Override
        public <U, V> Flow<V> union(String name, Flow<U> other, UnionFunction<T, U, V> function) {
            return union(name, other, function, null);
        }

        @Override
        public <U, V> Flow<V> union(String name, Flow<U> other, UnionFunction<T, U, V> function, ExecutionConfig config) {
            if (!(other instanceof DefaultFlow)) {
                throw new IllegalArgumentException("union() 仅支持同一 workflow 中的 flow");
            }
            @SuppressWarnings("unchecked")
            DefaultFlow<U> otherFlow = (DefaultFlow<U>) other;
            List<Precursor> otherPrecursors = otherFlow.precursors;

            String nodeId = uniqueNodeId(name);
            Type inputType = Types.NULL; // UNION 多输入，由运行时推断
            TaskNode unionNode = TaskNode.builder()
                    .id(nodeId)
                    .inputType(inputType)
                    .outputType((Type) null)
                    .operatorKind(OperatorKind.OPERATOR_UNION)
                    .function(buildFunctionDescriptor(function))
                    .replicas(config != null ? config.getReplicas() : 1)
                    .resource(config != null ? Resource.fromSpec(config.getResource()) : null)
                    .build();
            nodes.add(unionNode);
            for (Precursor p : precursors) {
                edges.add(edge(p.node().getId(), nodeId, p.port()));
            }
            for (Precursor p : otherPrecursors) {
                edges.add(edge(p.node().getId(), nodeId, p.port()));
            }
            return new DefaultFlow<>(List.of(new Precursor(unionNode)));
        }

        @Override
        public ConditionalFlow<T> when(ConditionFunction<T> condition) {
            return new DefaultConditionalFlow<>(precursors, condition);
        }


        @Override
        public Flow<T> returns(TypeToken<T> typeHint) {
            Type resolvedType = Types.fromType(typeHint.getType());
            for (Precursor p : precursors) {
                Node node = p.node();
                if (node instanceof TaskNode op && op.getOutputType() == null) {
                    node.setOutputType(resolvedType);
                }
            }
            return this;
        }
    }

    // ======== DefaultEndFlow ========

    private static class DefaultEndFlow<T> implements EndFlow<T> {
        private final List<Precursor> precursors;

        private DefaultEndFlow(List<Precursor> precursors) {
            this.precursors = precursors;
        }

        public List<Precursor> getPrecursors() {
            return precursors;
        }
    }

    // ======== DefaultConditionalFlow ========

    private class DefaultConditionalFlow<T> implements ConditionalFlow<T> {
        private final List<Precursor> precursors;
        private final ConditionFunction<T> condition;

        private DefaultConditionalFlow(List<Precursor> precursors, ConditionFunction<T> condition) {
            this.precursors = precursors;
            this.condition = condition;
        }

        @Override
        public <R> Flow<R> then(String name, TaskFunction<T, R> function) {
            return then(name, function, null);
        }

        @Override
        public <R> Flow<R> then(String name, TaskFunction<T, R> function, ExecutionConfig config) {
            BranchFunction<T> branchFn = value -> condition.test(value) ? 0 : 1;
            TaskNode branchNode = addBranchNode(precursors, branchFn);
            // 仅连接 port 0（满足条件的输出）到下一节点
            TaskNode taskNode = addTaskNode(name, List.of(new Precursor(branchNode, 0)), function, config, null);
            return new DefaultFlow<>(List.of(new Precursor(taskNode)));
        }

        @Override
        public EndFlow<T> then(String name, OutputFunction<T> function) {
            return then(name, function, null);
        }

        @Override
        public EndFlow<T> then(String name, OutputFunction<T> function, ExecutionConfig config) {
            BranchFunction<T> branchFn = value -> condition.test(value) ? 0 : 1;
            TaskNode branchNode = addBranchNode(precursors, branchFn);
            TaskNode sinkNode = addTaskNode(name, List.of(new Precursor(branchNode, 0)), function, config, null);
            return new DefaultEndFlow<>(List.of(new Precursor(sinkNode)));
        }
    }

    // ======================== 共用工具方法 ========================

    private static String uniqueNodeId(String name) {
        return name + "_" + IDUtil.genUUID();
    }

    private TaskNode addTaskNode(String name, List<Precursor> fromPrecursors,
                                 java.io.Serializable function, ExecutionConfig config, Type outputType) {
        String nodeId = uniqueNodeId(name);
        Type inputType = fromPrecursors.isEmpty() ? Types.NULL
                : fromPrecursors.get(0).node().getOutputType();
        if (inputType == null) {
            inputType = Types.NULL;
        }
        TaskNode node = TaskNode.builder()
                .id(nodeId)
                .inputType(inputType)
                .outputType(outputType)
                .operatorKind(OperatorKind.OPERATOR_MAP)
                .function(buildFunctionDescriptor(function))
                .replicas(config != null ? config.getReplicas() : 1)
                .resource(config != null ? Resource.fromSpec(config.getResource()) : null)
                .build();
        nodes.add(node);
        for (Precursor p : fromPrecursors) {
            edges.add(edge(p.node().getId(), nodeId, p.port()));
        }
        return node;
    }

    private TaskNode addBranchNode(List<Precursor> fromPrecursors, BranchFunction<?> branchFunction) {
        String nodeId = uniqueNodeId("branch");
        Type inputType = fromPrecursors.isEmpty() ? Types.NULL
                : fromPrecursors.get(0).node().getOutputType();
        if (inputType == null) {
            inputType = Types.NULL;
        }
        TaskNode node = TaskNode.builder()
                .id(nodeId)
                .inputType(inputType)
                .outputType(inputType)
                .operatorKind(OperatorKind.OPERATOR_BRANCH)
                .function(buildFunctionDescriptor(branchFunction))
                .replicas(1)
                .resource(null)
                .build();
        nodes.add(node);
        for (Precursor p : fromPrecursors) {
            edges.add(edge(p.node().getId(), nodeId, p.port()));
        }
        return node;
    }

    private static Edge edge(String fromNodeId, String toNodeId) {
        return edge(fromNodeId, toNodeId, 0);
    }

    private static Edge edge(String fromNodeId, String toNodeId, int fromPort) {
        return Edge.newBuilder()
                .setFromNodeId(fromNodeId)
                .setToNodeId(toNodeId)
                .setFromPort(fromPort)
                .build();
    }

    private static FunctionDescriptor buildFunctionDescriptor(java.io.Serializable function) {
        if (function instanceof PythonFunction pf) {
            FunctionDescriptor.Builder b = FunctionDescriptor.newBuilder()
                    .setLang(Lang.LANG_PYTHON)
                    .setFunctionIdentifier(pf.getCodeFile() + ":" + pf.getFunctionName());
            if (pf.getSourcePath() != null && !pf.getSourcePath().isEmpty()) {
                b.setSourcePath(pf.getSourcePath());
            }
            return b.build();
        }

        if (function instanceof Function fn) {
            FunctionDescriptor.Builder b = FunctionDescriptor.newBuilder()
                    .setLang(fn.getLang())
                    .setFunctionIdentifier(fn.getClass().getName())
                    .setSerializedFunction(ByteString.copyFrom(SerializationUtil.serialize(fn)));
            return b.build();
        }

        // TaskFunction, SinkFunction, UnionFunction 等仅实现 Serializable 的接口
        return FunctionDescriptor.newBuilder()
                .setLang(Lang.LANG_JAVA)
                .setFunctionIdentifier(function.getClass().getName())
                .setSerializedFunction(ByteString.copyFrom(SerializationUtil.serialize(function)))
                .build();
    }
}
