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
        return new DefaultFlow<>(List.of(node));
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

    private static final class ForkJoinCombinedFunction<T, L, R, OUT> implements MapFunction<T, OUT> {

        private final MapFunction<? super T, ? extends L> left;
        private final FlatMapFunction<? super T, ? extends R> right;
        private final CombineFunction<? super L, ? super java.util.List<R>, ? extends OUT> combiner;

        private ForkJoinCombinedFunction(
                MapFunction<? super T, ? extends L> left,
                FlatMapFunction<? super T, ? extends R> right,
                CombineFunction<? super L, ? super java.util.List<R>, ? extends OUT> combiner) {
            this.left = left;
            this.right = right;
            this.combiner = combiner;
        }

        @Override
        public OUT apply(T value) {
            L l = left.apply(value);
            Iterable<? extends R> rightsIterable = right.apply(value);
            java.util.List<R> rights = new java.util.ArrayList<>();
            if (rightsIterable != null) {
                for (R r : rightsIterable) {
                    rights.add(r);
                }
            }
            return combiner.apply(l, rights);
        }
    }

    private class DefaultFlow<T> implements Flow<T> {

        private final List<Node> precursors;

        private DefaultFlow(List<Node> precursors) {
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
            return new DefaultFlow<>(List.of(node));
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
            return new DefaultFlow<>(List.of(node));
        }

        // -------- filter --------

        @Override
        public Flow<T> filter(FilterFunction<? super T> predicate) {
            Type outputType = precursors.isEmpty() ? null : precursors.get(0).getOutputType();

            OperatorNode node = buildOperatorNode(predicate, OperatorKind.OPERATOR_FILTER, outputType, 1, Resource.of(0.5, "512Mi"));
            linkAndRegister(node);
            return new DefaultFlow<>(List.of(node));
        }

        // -------- returns --------

        @Override
        public Flow<T> returns(TypeToken<T> typeHint) {
            Type resolvedType = Types.fromType(typeHint.getType());
            for (Node node : precursors) {
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
                    .inputType(precursors.isEmpty() ? null : precursors.get(0).getOutputType())
                    .outputType(precursors.isEmpty() ? null : precursors.get(0).getOutputType())
                    .operatorKind(OperatorKind.OPERATOR_KEY_BY)
                    .keySelector(keySelectorFd)
                    .replicas(1)
                    .resource(Resource.of(0.5, "512Mi"))
                    .build();

            precursors.forEach(p -> edges.add(edge(p.getId(), keyByNode.getId())));
            nodes.add(keyByNode);

            return new DefaultKeyedFlow<>(List.of(keyByNode), selector);
        }

        // -------- forkJoin --------

        @Override
        public <L, R, OUT> Flow<OUT> forkJoin(
                MapFunction<? super T, ? extends L> left,
                FlatMapFunction<? super T, ? extends R> right,
                CombineFunction<? super L, ? super java.util.List<R>, ? extends OUT> combiner) {

            if (left == null || right == null || combiner == null) {
                throw new IllegalArgumentException("left/right/combiner must not be null");
            }

            MapFunction<T, OUT> combined = new ForkJoinCombinedFunction<>(left, right, combiner);
            return this.map(combined);
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
                outputType = precursors.get(0).getOutputType();
            } else if (!otherFlow.precursors.isEmpty()) {
                outputType = otherFlow.precursors.get(0).getOutputType();
            }

            OperatorNode unionNode = OperatorNode.builder()
                    .id(IDUtil.genUUID())
                    .outputType(outputType)
                    .operatorKind(OperatorKind.OPERATOR_UNION)
                    .replicas(1)
                    .resource(Resource.of(0.5, "512Mi"))
                    .build();

            precursors.forEach(p -> edges.add(edge(p.getId(), unionNode.getId())));
            otherFlow.precursors.forEach(p -> edges.add(edge(p.getId(), unionNode.getId())));
            nodes.add(unionNode);

            return new DefaultFlow<>(List.of(unionNode));
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

            Type inputType = precursors.isEmpty() ? null : precursors.get(0).getOutputType();
            sinkNode.setOutputType(inputType);

            precursors.forEach(p -> edges.add(edge(p.getId(), sinkNode.getId())));
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
            precursors.forEach(p -> edges.add(edge(p.getId(), node.getId())));
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
        public <R> CoKeyedFlow<T, R, K> connect(KeyedFlow<R, K> other) {
            if (other == null) {
                throw new IllegalArgumentException("other keyed flow must not be null");
            }
            if (!(other instanceof DefaultKeyedFlow<?, ?> otherKeyed)) {
                throw new IllegalArgumentException("只能连接由同一 Workflow 创建的 KeyedFlow");
            }
            @SuppressWarnings("unchecked")
            DefaultKeyedFlow<R, K> right = (DefaultKeyedFlow<R, K>) otherKeyed;
            return new DefaultCoKeyedFlow<>(this, right);
        }
    }

    private class DefaultCoKeyedFlow<L, R, K> implements CoKeyedFlow<L, R, K> {

        private final DefaultKeyedFlow<L, K> left;
        private final DefaultKeyedFlow<R, K> right;

        private DefaultCoKeyedFlow(DefaultKeyedFlow<L, K> left,
                                   DefaultKeyedFlow<R, K> right) {
            this.left = left;
            this.right = right;
        }

        @Override
        public <OUT> Flow<OUT> process(CoProcessFunction<L, R, OUT> fn) {
            if (fn == null) {
                throw new IllegalArgumentException("CoProcessFunction must not be null");
            }

            java.lang.reflect.Type returnType =
                    TypeExtractor.extractOutputType(fn, CoProcessFunction.class, 2);
            Type outputType = returnType != null ? Types.fromType(returnType) : null;

            FunctionDescriptor fd = buildFunctionDescriptor(fn);

            OperatorNode node = OperatorNode.builder()
                    .id(IDUtil.genUUID())
                    .outputType(outputType)
                    .operatorKind(OperatorKind.OPERATOR_CO_PROCESS)
                    .function(fd)
                    .replicas(1)
                    .resource(Resource.of(0.5, "512Mi"))
                    .build();

            left.precursors.forEach(p -> edges.add(edge(p.getId(), node.getId())));
            right.precursors.forEach(p -> edges.add(edge(p.getId(), node.getId())));
            nodes.add(node);

            return new DefaultFlow<>(List.of(node));
        }
    }

    // ======================== 共用工具方法 ========================

    private static Edge edge(String fromNodeId, String toNodeId) {
        return Edge.newBuilder()
                .setFromNodeId(fromNodeId)
                .setToNodeId(toNodeId)
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
