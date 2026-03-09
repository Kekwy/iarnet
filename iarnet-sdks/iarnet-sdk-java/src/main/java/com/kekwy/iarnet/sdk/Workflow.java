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
import com.kekwy.iarnet.proto.workflow.OperatorKind;
import com.kekwy.iarnet.proto.workflow.WorkflowGraph;
import com.kekwy.iarnet.sdk.converter.SinkToNodeVisitor;
import com.kekwy.iarnet.sdk.converter.SourceToNodeVisitor;
import com.kekwy.iarnet.sdk.converter.WorkflowGraphBuilder;
import com.kekwy.iarnet.sdk.function.*;
import com.kekwy.iarnet.sdk.function.Function;
import com.kekwy.iarnet.sdk.function.Function.PythonFunction;
import com.kekwy.iarnet.sdk.graph.Node;
import com.kekwy.iarnet.sdk.graph.OperatorNode;
import com.kekwy.iarnet.sdk.graph.SinkNode;
import com.kekwy.iarnet.sdk.graph.SourceNode;
import com.kekwy.iarnet.sdk.sink.Sink;
import com.kekwy.iarnet.sdk.source.Source;
import com.kekwy.iarnet.sdk.util.IDUtil;
import com.kekwy.iarnet.sdk.util.SerializationUtil;
import com.kekwy.iarnet.sdk.util.TypeExtractor;
import com.kekwy.iarnet.sdk.util.TypeToken;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 工作流 DSL 入口，用于创建 source、task 并执行。
 */
public class Workflow {

    private Workflow() {
    }

    public static Workflow create() {
        return new Workflow();
    }

    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();

    public <T> Flow<T> source(Source<T> source) {
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

    // ======================== TaskContext / Task 实现 ========================

    private static final class DefaultTaskContext implements TaskContext {
        private final java.util.Map<String, Object> state = new java.util.HashMap<>();
        private Workflow workflow;

        void setWorkflow(Workflow w) {
            this.workflow = w;
        }

        @Override
        public Workflow getWorkflow() {
            return workflow;
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void setState(String key, Object value) {
            state.put(key, value);
        }
    }

    private static final class DefaultTask implements Task {
        private final String name;
        private final Consumer<TaskContext> action;

        DefaultTask(String name, Consumer<TaskContext> action) {
            this.name = name;
            this.action = action;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void run(TaskContext context) {
            action.accept(context);
        }
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

        // -------- map --------

        @Override
        public <R> Flow<R> map(MapFunction<? super T, ? extends R> mapper) {
            return this.map(mapper, 1, Resource.of(0.5, "512Mi"));
        }

        @Override
        public <R> Flow<R> map(MapFunction<? super T, ? extends R> mapper, Resource resource) {
            return this.map(mapper, 1, resource);
        }

        @Override
        public <R> Flow<R> map(MapFunction<? super T, ? extends R> mapper, int replicas) {
            return this.map(mapper, replicas, Resource.of(0.5, "512Mi"));
        }

        @Override
        public <R> Flow<R> map(MapFunction<? super T, ? extends R> mapper, int replicas, Resource resource) {
            java.lang.reflect.Type returnType = TypeExtractor.extractOutputType(mapper, MapFunction.class, 1);
            Type outputType = returnType != null ? Types.fromType(returnType) : null;

            OperatorNode node = buildOperatorNode(mapper, OperatorKind.OPERATOR_MAP, outputType, replicas, resource);
            linkAndRegister(node);
            return new DefaultFlow<>(List.of(new Precursor(node)));
        }

        // -------- flatMap --------

        @Override
        public <R> Flow<R> flatMap(FlatMapFunction<? super T, ? extends R> mapper) {
            return this.flatMap(mapper, 1, Resource.of(0.5, "512Mi"));
        }

        @Override
        public <R> Flow<R> flatMap(FlatMapFunction<? super T, ? extends R> mapper, Resource resource) {
            return this.flatMap(mapper, 1, resource);
        }

        @Override
        public <R> Flow<R> flatMap(FlatMapFunction<? super T, ? extends R> mapper, int replicas) {
            return this.flatMap(mapper, replicas, Resource.of(0.5, "512Mi"));
        }

        @Override
        public <R> Flow<R> flatMap(FlatMapFunction<? super T, ? extends R> mapper, int replicas, Resource resource) {
            java.lang.reflect.Type returnType = TypeExtractor.extractOutputType(mapper, FlatMapFunction.class, 1);
            Type outputType = returnType != null ? Types.fromType(returnType) : null;

            OperatorNode node = buildOperatorNode(mapper, OperatorKind.OPERATOR_FLAT_MAP, outputType, replicas, resource);
            linkAndRegister(node);
            return new DefaultFlow<>(List.of(new Precursor(node)));
        }

        // -------- filter --------

        @Override
        public Flow<T> filter(FilterFunction<? super T> predicate) {
            Type outputType = precursors.isEmpty() ? null : precursors.get(0).node().getOutputType();

            OperatorNode node = buildOperatorNode(predicate, OperatorKind.OPERATOR_FILTER, outputType, 1, Resource.of(0.5, "512Mi"));
            linkAndRegister(node);
            return new DefaultFlow<>(List.of(new Precursor(node)));
        }

        // -------- returns --------

        @Override
        public Flow<T> returns(TypeToken<T> typeHint) {
            Type resolvedType = Types.fromType(typeHint.getType());
            for (Precursor p : precursors) {
                Node node = p.node();
                if (node instanceof OperatorNode op && op.getOutputType() == null) {
                    node.setOutputType(resolvedType);
                }
            }
            return this;
        }

        // -------- keyBy --------

        @Override
        public <K> KeyedFlow<T, K> keyBy(KeySelector<? super T, ? extends K> selector) {
            if (selector == null) {
                throw new IllegalArgumentException("key selector must not be null");
            }

            FunctionDescriptor keySelectorFd = buildFunctionDescriptor(selector);

            OperatorNode keyByNode = OperatorNode.builder()
                    .id(IDUtil.genUUID())
                    .inputType(precursors.isEmpty() ? null : precursors.get(0).node().getOutputType())
                    .outputType(precursors.isEmpty() ? null : precursors.get(0).node().getOutputType())
                    .operatorKind(OperatorKind.OPERATOR_KEY_BY)
                    .keySelector(keySelectorFd)
                    .replicas(1)
                    .resource(Resource.of(0.5, "512Mi"))
                    .build();

            precursors.forEach(p -> edges.add(edge(p.node().getId(), keyByNode.getId(), p.port())));
            nodes.add(keyByNode);

            return new DefaultKeyedFlow<>(List.of(keyByNode), selector);
        }

        // -------- branch --------

        @Override
        public BranchedFlow<T> branch(BranchFunction<? super T> predicate) {
            if (predicate == null) {
                throw new IllegalArgumentException("predicate must not be null");
            }
            Type outputType = precursors.isEmpty() ? null : precursors.get(0).node().getOutputType();

            OperatorNode branchNode = OperatorNode.builder()
                    .id(IDUtil.genUUID())
                    .inputType(outputType)
                    .outputType(outputType)
                    .operatorKind(OperatorKind.OPERATOR_BRANCH)
                    .function(buildFunctionDescriptor(predicate))
                    .replicas(1)
                    .resource(Resource.of(0.5, "512Mi"))
                    .build();

            precursors.forEach(p -> edges.add(edge(p.node().getId(), branchNode.getId(), p.port())));
            nodes.add(branchNode);

            return new DefaultBranchedFlow<>(branchNode);
        }

        // -------- union --------

        @Override
        public Flow<T> union(Flow<T> other) {
            if (other == null) {
                throw new IllegalArgumentException("other flow must not be null");
            }
            if (!(other instanceof DefaultFlow<?> otherFlow)) {
                throw new IllegalArgumentException("只能与同一 Workflow 创建的 Flow 执行 union");
            }

            Type outputType = null;
            if (!precursors.isEmpty()) {
                outputType = precursors.get(0).node().getOutputType();
            } else if (!otherFlow.precursors.isEmpty()) {
                outputType = otherFlow.precursors.get(0).node().getOutputType();
            }

            OperatorNode unionNode = OperatorNode.builder()
                    .id(IDUtil.genUUID())
                    .outputType(outputType)
                    .operatorKind(OperatorKind.OPERATOR_UNION)
                    .replicas(1)
                    .resource(Resource.of(0.5, "512Mi"))
                    .build();

            precursors.forEach(p -> edges.add(edge(p.node().getId(), unionNode.getId())));
            otherFlow.precursors.forEach(p -> edges.add(edge(p.node().getId(), unionNode.getId())));
            nodes.add(unionNode);

            return new DefaultFlow<>(List.of(new Precursor(unionNode)));
        }

        // -------- after / sink --------

        @Override
        public Flow<T> after(Task task) {
            return this;
        }

        @Override
        public void sink(Sink<? super T> sink) {
            SinkToNodeVisitor visitor = new SinkToNodeVisitor();
            SinkNode sinkNode = sink.accept(visitor);

            Type inputType = precursors.isEmpty() ? null : precursors.get(0).node().getOutputType();
            sinkNode.setOutputType(inputType);

            precursors.forEach(p -> edges.add(edge(p.node().getId(), sinkNode.getId())));
            nodes.add(sinkNode);
        }

        // -------- internal helpers --------

        private OperatorNode buildOperatorNode(
                Function function, OperatorKind kind,
                Type outputType, int replicas, Resource resource) {

            FunctionDescriptor fd = buildFunctionDescriptor(function);

            return OperatorNode.builder()
                    .id(IDUtil.genUUID())
                    .outputType(outputType)
                    .operatorKind(kind)
                    .function(fd)
                    .replicas(replicas)
                    .resource(resource)
                    .build();
        }

        private void linkAndRegister(OperatorNode node) {
            precursors.forEach(p -> edges.add(edge(p.node().getId(), node.getId())));
            nodes.add(node);
        }
    }

    // ======== KeyedFlow / CoKeyedFlow 实现 ========

    private class DefaultKeyedFlow<T, K> implements KeyedFlow<T, K> {

        private final List<Node> precursors;
        @SuppressWarnings("unused")
        private final KeySelector<? super T, ? extends K> keySelector;

        private DefaultKeyedFlow(List<Node> precursors,
                                 KeySelector<? super T, ? extends K> keySelector) {
            this.precursors = precursors;
            this.keySelector = keySelector;
        }

        @Override
        public <ACC> Flow<ACC> fold(ACC initial, Duration timeout, FoldFunction<? super T, ACC> fn) {
            if (initial == null || fn == null) {
                throw new IllegalArgumentException("initial value and fold function must not be null");
            }

            java.lang.reflect.Type returnType = TypeExtractor.extractOutputType(fn, FoldFunction.class, 1);
            Type outputType = returnType != null ? Types.fromType(returnType) : null;

            OperatorNode node = OperatorNode.builder()
                    .id(IDUtil.genUUID())
                    .outputType(outputType)
                    .operatorKind(OperatorKind.OPERATOR_FOLD)
                    .function(buildFunctionDescriptor(fn))
                    .foldInitialValue(initial)
                    .replicas(1)
                    .resource(Resource.of(0.5, "512Mi"))
                    .build();

            precursors.forEach(p -> edges.add(edge(p.getId(), node.getId())));
            nodes.add(node);

            return new DefaultFlow<>(List.of(new Precursor(node)));
        }

        @Override
        public <R, OUT> Flow<OUT> join(KeyedFlow<R, K> other, Duration timeout, JoinFunction<? super T, ? super R, OUT> joiner) {
            if (other == null || timeout == null || joiner == null) {
                throw new IllegalArgumentException("other, timeout, joiner must not be null");
            }
            if (!(other instanceof DefaultKeyedFlow<?, ?> otherKeyed)) {
                throw new IllegalArgumentException("只能与同一 Workflow 创建的 KeyedFlow 执行 join");
            }
            @SuppressWarnings("unchecked")
            DefaultKeyedFlow<R, K> right = (DefaultKeyedFlow<R, K>) otherKeyed;

            java.lang.reflect.Type returnType = TypeExtractor.extractOutputType(joiner, JoinFunction.class, 2);
            Type outputType = returnType != null ? Types.fromType(returnType) : null;

            OperatorNode joinNode = OperatorNode.builder()
                    .id(IDUtil.genUUID())
                    .outputType(outputType)
                    .operatorKind(OperatorKind.OPERATOR_CORRELATE)
                    .function(buildFunctionDescriptor(joiner))
                    .timeoutMs(timeout.toMillis())
                    .replicas(1)
                    .resource(Resource.of(0.5, "512Mi"))
                    .build();

            precursors.forEach(p -> edges.add(edge(p.getId(), joinNode.getId())));
            right.precursors.forEach(p -> edges.add(edge(p.getId(), joinNode.getId())));
            nodes.add(joinNode);

            return new DefaultFlow<>(List.of(new Precursor(joinNode)));
        }
    }

    private class DefaultBranchedFlow<T> implements BranchedFlow<T> {
        private final OperatorNode owner;

        DefaultBranchedFlow(OperatorNode owner) {
            this.owner = owner;
        }

        @Override
        public Flow<T> getFlow(int port) {
            return new DefaultFlow<>(List.of(new Precursor(owner, port)));
        }

    }


    // ======================== 共用工具方法 ========================

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

        Function fn = (Function) function;
        FunctionDescriptor.Builder b = FunctionDescriptor.newBuilder()
                .setLang(fn.getLang())
                .setFunctionIdentifier(fn.getClass().getName())
                .setSerializedFunction(ByteString.copyFrom(SerializationUtil.serialize(fn)));
        return b.build();
    }
}
