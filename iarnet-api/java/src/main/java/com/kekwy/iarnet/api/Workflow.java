package com.kekwy.iarnet.api;

import com.kekwy.iarnet.api.function.FilterFunction;
import com.kekwy.iarnet.api.function.FlatMapFunction;
import com.kekwy.iarnet.api.function.Function;
import com.kekwy.iarnet.api.function.Function.PythonFunction;
import com.kekwy.iarnet.api.function.MapFunction;
import com.kekwy.iarnet.api.graph.Node;
import com.kekwy.iarnet.api.graph.OperatorNode;
import com.kekwy.iarnet.api.graph.OperatorNode.OperatorKind;
import com.kekwy.iarnet.api.graph.SourceNode;
import com.kekwy.iarnet.api.graph.SinkNode;
import com.kekwy.iarnet.api.sink.Sink;
import com.kekwy.iarnet.api.sink.SinkToNodeVisitor;
import com.kekwy.iarnet.api.source.Source;
import com.kekwy.iarnet.api.source.SourceToNodeVisitor;
import com.kekwy.iarnet.api.util.IDUtil;
import com.kekwy.iarnet.api.util.SerializationUtil;
import com.kekwy.iarnet.api.util.TypeExtractor;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
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

    public <T> Flow<T> source(Source<T> source) {
        SourceToNodeVisitor visitor = new SourceToNodeVisitor();
        SourceNode node = source.accept(visitor);
        nodes.add(node);
        return new DefaultFlow<>(List.of(node));
    }

    public List<Node> getNodes() {
        return List.copyOf(nodes);
    }

    public void execute() {
        throw new UnsupportedOperationException("Remote workflow execution is not implemented yet.");
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

            precursors.forEach(p -> {
                p.addSuccessor(sinkNode);
                sinkNode.addPrecursor(p);
            });
            nodes.add(sinkNode);
        }

        // -------- internal helpers --------

        private OperatorNode buildOperatorNode(
                Function function, OperatorKind kind,
                DataType outputType, int replicas, Resource resource) {

            Lang lang = function.getLang();
            String operatorIdentifier;
            byte[] serializedFunction = null;
            String sourceDir = "";

            if (function instanceof PythonFunction pf) {
                operatorIdentifier = pf.codeFile() + ":" + pf.function();
                sourceDir = pf.requirementsFile();
            } else {
                operatorIdentifier = function.getClass().getName();
                serializedFunction = SerializationUtil.serialize(function);
            }

            return OperatorNode.builder()
                    .id(IDUtil.genUUID())
                    .outputType(outputType)
                    .operatorKind(kind)
                    .lang(lang)
                    .serializedFunction(serializedFunction)
                    .operatorIdentifier(operatorIdentifier)
                    .replicas(replicas)
                    .resource(resource)
                    .sourceDir(sourceDir)
                    .build();
        }

        private void linkAndRegister(OperatorNode node) {
            precursors.forEach(p -> {
                p.addSuccessor(node);
                node.addPrecursor(p);
            });
            nodes.add(node);
        }
    }
}
