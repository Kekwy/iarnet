package com.kekwy.iarnet.api;

import com.kekwy.iarnet.api.function.FilterFunction;
import com.kekwy.iarnet.api.function.FlatMapFunction;
import com.kekwy.iarnet.api.function.Function;
import com.kekwy.iarnet.api.function.Function.PythonFunction;
import com.kekwy.iarnet.api.function.MapFunction;
import com.kekwy.iarnet.api.converter.GraphToProtoConverter;
import com.kekwy.iarnet.api.graph.Edge;
import com.kekwy.iarnet.api.graph.FunctionDescriptor;
import com.kekwy.iarnet.api.graph.Node;
import com.kekwy.iarnet.api.graph.OperatorNode;
import com.kekwy.iarnet.api.graph.OperatorNode.OperatorKind;
import com.kekwy.iarnet.api.graph.SourceNode;
import com.kekwy.iarnet.api.graph.SinkNode;
import com.kekwy.iarnet.proto.api.SubmitWorkflowRequest;
import com.kekwy.iarnet.proto.api.SubmitWorkflowResponse;
import com.kekwy.iarnet.proto.api.SubmissionStatus;
import com.kekwy.iarnet.proto.api.WorkflowServiceGrpc;
import com.kekwy.iarnet.proto.ir.WorkflowGraph;
import com.kekwy.iarnet.api.sink.Sink;
import com.kekwy.iarnet.api.sink.SinkToNodeVisitor;
import com.kekwy.iarnet.api.source.Source;
import com.kekwy.iarnet.api.source.SourceToNodeVisitor;
import com.kekwy.iarnet.api.util.IDUtil;
import com.kekwy.iarnet.api.util.SerializationUtil;
import com.kekwy.iarnet.api.util.TypeExtractor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.lang.reflect.Type;
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

    /**
     * 创建新的工作流实例。
     */
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

    private static final String ENV_APP_ID = "IARNET_APP_ID";
    private static final String ENV_GRPC_PORT = "IARNET_GRPC_PORT";
    private static final String DEFAULT_GRPC_HOST = "localhost";

    /**
     * 将 DSL 构建的图转换为 Protobuf IR，并通过 gRPC 提交到 control-plane。
     * <p>
     * gRPC 连接信息从环境变量读取：
     * <ul>
     *   <li>{@code IARNET_APP_ID} — application ID（必须）</li>
     *   <li>{@code IARNET_GRPC_PORT} — gRPC 服务端口（必须）</li>
     * </ul>
     */
    public void execute() {
        WorkflowGraph graph = toProtoGraph();

        String portStr = System.getenv(ENV_GRPC_PORT);
        if (portStr == null || portStr.isBlank()) {
            throw new IllegalStateException(
                    "环境变量 " + ENV_GRPC_PORT + " 未设置，无法连接 control-plane gRPC 服务");
        }
        int port = Integer.parseInt(portStr);

        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(DEFAULT_GRPC_HOST, port)
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

    /**
     * 将当前 DSL 构建的节点和边转换为 Protobuf {@link WorkflowGraph}。
     * application ID 从环境变量 {@code IARNET_APP_ID} 读取。
     */
    public WorkflowGraph toProtoGraph() {
        String applicationId = System.getenv(ENV_APP_ID);
        if (applicationId == null || applicationId.isBlank()) {
            throw new IllegalStateException(
                    "环境变量 " + ENV_APP_ID + " 未设置，无法确定 application ID");
        }
        return toProtoGraph(applicationId);
    }

    /**
     * 将当前 DSL 构建的节点和边转换为 Protobuf {@link WorkflowGraph}。
     *
     * @param applicationId 显式指定 application ID（测试用）
     */
    public WorkflowGraph toProtoGraph(String applicationId) {
        GraphToProtoConverter converter = new GraphToProtoConverter();
        return converter.convert(applicationId, nodes, edges);
    }

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
            Type returnType = TypeExtractor.extractOutputType(mapper, MapFunction.class, 1);
            DataType outputType = returnType != null ? DataTypeInfer.infer(returnType) : null;

            OperatorNode node = buildOperatorNode(mapper, OperatorKind.MAP, outputType, replicas, resource);
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
            Type returnType = TypeExtractor.extractOutputType(mapper, FlatMapFunction.class, 1);
            DataType outputType = returnType != null ? DataTypeInfer.infer(returnType) : null;

            OperatorNode node = buildOperatorNode(mapper, OperatorKind.FLAT_MAP, outputType, replicas, resource);
            linkAndRegister(node);
            return new DefaultFlow<>(List.of(node));
        }

        // -------- filter --------

        @Override
        public Flow<T> filter(FilterFunction<? super T> predicate) {
            DataType outputType = precursors.isEmpty() ? null : precursors.get(0).getOutputType();

            OperatorNode node = buildOperatorNode(predicate, OperatorKind.FILTER, outputType, 1, Resource.of(0.5, "512Mi"));
            linkAndRegister(node);
            return new DefaultFlow<>(List.of(node));
        }

        // -------- returns --------

        @Override
        public Flow<T> returns(TypeToken<T> typeHint) {
            DataType resolvedType = DataTypeInfer.infer(typeHint);
            for (Node node : precursors) {
                if (node instanceof OperatorNode op && op.getOutputType() == null) {
                    node.setOutputType(resolvedType);
                }
            }
            return this;
        }

        // -------- keyBy --------

        @Override
        public <K> KeyedFlow<T, K> keyBy(com.kekwy.iarnet.api.function.KeySelector<? super T, ? extends K> selector) {
            if (selector == null) {
                throw new IllegalArgumentException("key selector must not be null");
            }
            return new DefaultKeyedFlow<>(new java.util.ArrayList<>(precursors), selector);
        }

        // -------- map2 (对齐 Fork-Join，单算子实现) --------

        @Override
        public <L, R, OUT> Flow<OUT> map2(
                MapFunction<? super T, ? extends L> left,
                FlatMapFunction<? super T, ? extends R> right,
                com.kekwy.iarnet.api.function.CombineFunction<? super L, ? super java.util.List<R>, ? extends OUT> combiner) {

            if (left == null || right == null || combiner == null) {
                throw new IllegalArgumentException("left/right/combiner must not be null");
            }

            MapFunction<T, OUT> combined = new MapFunction<>() {
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
            };

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

            // 合流节点的输出类型与输入流保持一致
            DataType outputType = null;
            if (!precursors.isEmpty()) {
                outputType = precursors.get(0).getOutputType();
            } else if (!otherFlow.precursors.isEmpty()) {
                outputType = otherFlow.precursors.get(0).getOutputType();
            }

            OperatorNode unionNode = OperatorNode.builder()
                    .id(IDUtil.genUUID())
                    .outputType(outputType)
                    .operatorKind(OperatorKind.UNION)
                    .replicas(1)
                    .resource(Resource.of(0.5, "512Mi"))
                    .build();

            // 当前流与另一条流的所有前驱都连到同一个 UNION 节点
            precursors.forEach(p -> edges.add(Edge.of(p.getId(), unionNode.getId())));
            otherFlow.precursors.forEach(p -> edges.add(Edge.of(p.getId(), unionNode.getId())));
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

            DataType inputType = precursors.isEmpty() ? null : precursors.get(0).getOutputType();
            sinkNode.setOutputType(inputType);

            precursors.forEach(p -> edges.add(Edge.of(p.getId(), sinkNode.getId())));
            nodes.add(sinkNode);
        }

        // -------- internal helpers --------

        private OperatorNode buildOperatorNode(
                Function function, OperatorKind kind,
                DataType outputType, int replicas, Resource resource) {

            FunctionDescriptor fd;
            if (function instanceof PythonFunction pf) {
                fd = FunctionDescriptor.builder()
                        .lang(Lang.LANG_PYTHON)
                        .functionIdentifier(pf.codeFile() + ":" + pf.function())
                        .artifactPath(pf.artifactPath())
                        .build();
            } else {
                fd = FunctionDescriptor.builder()
                        .lang(function.getLang())
                        .functionIdentifier(function.getClass().getName())
                        .serializedFunction(SerializationUtil.serialize(function))
                        .build();
            }

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
            precursors.forEach(p -> edges.add(Edge.of(p.getId(), node.getId())));
            nodes.add(node);
        }
    }

    // ======== KeyedFlow / CoKeyedFlow 实现 ========

    private class DefaultKeyedFlow<T, K> implements KeyedFlow<T, K> {

        private final java.util.List<Node> precursors;
        @SuppressWarnings("unused")
        private final com.kekwy.iarnet.api.function.KeySelector<? super T, ? extends K> keySelector;

        private DefaultKeyedFlow(java.util.List<Node> precursors,
                                 com.kekwy.iarnet.api.function.KeySelector<? super T, ? extends K> keySelector) {
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
        public <OUT> Flow<OUT> process(com.kekwy.iarnet.api.function.CoProcessFunction<L, R, OUT> fn) {
            if (fn == null) {
                throw new IllegalArgumentException("CoProcessFunction must not be null");
            }

            java.lang.reflect.Type returnType =
                    com.kekwy.iarnet.api.util.TypeExtractor.extractOutputType(
                            fn,
                            com.kekwy.iarnet.api.function.CoProcessFunction.class,
                            2);
            DataType outputType = returnType != null ? DataTypeInfer.infer(returnType) : null;

            // 构造 OperatorNode（使用 FLAT_MAP 语义：一次输入可产生 0..N 条输出）
            com.kekwy.iarnet.api.graph.FunctionDescriptor fd;
            if (fn instanceof com.kekwy.iarnet.api.function.Function.PythonFunction pf) {
                fd = com.kekwy.iarnet.api.graph.FunctionDescriptor.builder()
                        .lang(Lang.LANG_PYTHON)
                        .functionIdentifier(pf.codeFile() + ":" + pf.function())
                        .artifactPath(pf.artifactPath())
                        .build();
            } else {
                fd = com.kekwy.iarnet.api.graph.FunctionDescriptor.builder()
                        .lang(fn.getLang())
                        .functionIdentifier(fn.getClass().getName())
                        .serializedFunction(SerializationUtil.serialize(fn))
                        .build();
            }

            OperatorNode node = OperatorNode.builder()
                    .id(IDUtil.genUUID())
                    .outputType(outputType)
                    .operatorKind(OperatorKind.FLAT_MAP)
                    .function(fd)
                    .replicas(1)
                    .resource(Resource.of(0.5, "512Mi"))
                    .build();

            // 两侧 keyed 流的末端都指向同一个 CoProcess 节点
            left.precursors.forEach(p -> edges.add(Edge.of(p.getId(), node.getId())));
            right.precursors.forEach(p -> edges.add(Edge.of(p.getId(), node.getId())));
            nodes.add(node);

            return new DefaultFlow<>(java.util.List.of(node));
        }
    }
}
