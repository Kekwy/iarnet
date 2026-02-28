package com.kekwy.iarnet.api;

import com.kekwy.iarnet.api.function.FilterFunction;
import com.kekwy.iarnet.api.function.FlatMapFunction;
import com.kekwy.iarnet.api.function.MapFunction;
import com.kekwy.iarnet.api.sink.Sink;
import com.kekwy.iarnet.api.source.Source;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
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

    private final List<Task> tasks = new ArrayList<>();
    private final List<Pipeline<?>> pipelines = new ArrayList<>();
    private final DefaultTaskContext taskContext = new DefaultTaskContext();

    public <T> Flow<T> source(Source<T> source) {
        return new DefaultFlow<>(this, source, new ArrayList<>(), null);
    }

    public Task task(String name, Consumer<TaskContext> action) {
        Task t = new DefaultTask(name, action);
        tasks.add(t);
        return t;
    }

    public void execute() {
        // 当前实现：将 DSL 描述的工作流编译为 IR，后续再由后端执行。
        WorkflowIr ir = buildIr();
        // TODO: 将 WorkflowIr 转为 proto（由 iarnet-proto 模块提供），并通过客户端发送到后端执行。
        throw new UnsupportedOperationException("Remote workflow execution is not implemented yet. IR: " + ir);
    }

    void registerPipeline(Pipeline<?> pipeline) {
        pipelines.add(pipeline);
    }

    private WorkflowIr buildIr() {
        // 目前直接把内部 Pipeline 结构打包出去，后续再在 iarnet-proto 中定义真正的 proto IR。
        return new WorkflowIr(pipelines);
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

    /**
     * 一条管道：source + 若干 stage + 可选 task + sink。
     */
    abstract static class Pipeline<T> {
        final Source<?> source;
        final List<Stage> stages;
        final Task task;
        final Sink<? super T> sink;

        Pipeline(Source<?> source, List<Stage> stages, Task task, Sink<? super T> sink) {
            this.source = source;
            this.stages = stages;
            this.task = task;
            this.sink = sink;
        }

        @SuppressWarnings("unchecked")
        void run(TaskContext ctx) {
            Iterator<Object> it = applyStages(source.iterator(), 0);
            while (it.hasNext()) {
                Object item = it.next();
                if (task != null) {
                    task.run(ctx);
                }
                ((Sink<Object>) sink).accept(item);
            }
        }

        private Iterator<Object> applyStages(Iterator<?> input, int fromIndex) {
            if (fromIndex >= stages.size()) {
                return (Iterator<Object>) input;
            }
            Stage s = stages.get(fromIndex);
            if (s instanceof MapStage m) {
                return applyStages(map(input, m.fn), fromIndex + 1);
            }
            if (s instanceof FilterStage f) {
                return applyStages(filter(input, f.fn), fromIndex + 1);
            }
            if (s instanceof FlatMapStage fm) {
                return applyStages(flatMap(input, fm.fn), fromIndex + 1);
            }
            return applyStages(input, fromIndex + 1);
        }

        private static Iterator<Object> map(Iterator<?> it, MapFunction<Object, Object> fn) {
            return new Iterator<Object>() {
                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public Object next() {
                    return fn.apply(it.next());
                }
            };
        }

        private static Iterator<Object> filter(Iterator<?> it, FilterFunction<Object> fn) {
            return new Iterator<Object>() {
                Object next;
                boolean hasNext = advance();

                private boolean advance() {
                    while (it.hasNext()) {
                        next = it.next();
                        if (fn.test(next)) return true;
                    }
                    next = null;
                    return false;
                }

                @Override
                public boolean hasNext() {
                    return hasNext;
                }

                @Override
                public Object next() {
                    Object n = next;
                    hasNext = advance();
                    return n;
                }
            };
        }

        private static Iterator<Object> flatMap(Iterator<?> it, FlatMapFunction<Object, ?> fn) {
            return new Iterator<Object>() {
                Iterator<?> current;
                Object next;
                boolean hasNext = advance();

                private boolean advance() {
                    while (true) {
                        if (current != null && current.hasNext()) {
                            next = current.next();
                            return true;
                        }
                        if (!it.hasNext()) {
                            next = null;
                            return false;
                        }
                        Iterable<?> iterable = fn.apply(it.next());
                        current = iterable != null ? iterable.iterator() : null;
                    }
                }

                @Override
                public boolean hasNext() {
                    return hasNext;
                }

                @Override
                public Object next() {
                    Object n = next;
                    hasNext = advance();
                    return n;
                }
            };
        }
    }

    private sealed interface Stage permits MapStage, FilterStage, FlatMapStage {
    }

    private record MapStage(MapFunction<Object, Object> fn) implements Stage {
    }

    private record FilterStage(FilterFunction<Object> fn) implements Stage {
    }

    private record FlatMapStage(FlatMapFunction<Object, ?> fn) implements Stage {
    }

    /**
     * 工作流的中间表示（IR）。
     * 目前只是简单封装了已注册的 pipeline，后续会映射到 proto 定义。
     */
    static final class WorkflowIr {
        final List<Pipeline<?>> pipelines;

        WorkflowIr(List<Pipeline<?>> pipelines) {
            this.pipelines = List.copyOf(pipelines);
        }

        @Override
        public String toString() {
            return "WorkflowIr{pipelines=" + pipelines.size() + '}';
        }
    }


    private record DefaultFlow<T>(Workflow workflow, Source<?> source, List<Stage> stages,
                                  Task afterTask) implements Flow<T> {

        @Override
            public <R> Flow<R> map(MapFunction<? super T, ? extends R> mapper) {
                return null;
            }

            @Override
            public <R> Flow<R> map(MapFunction<? super T, ? extends R> mapper, Resource resource) {
                return null;
            }

            @Override
            public <R> Flow<R> map(MapFunction<? super T, ? extends R> mapper, int replicas) {
                return null;
            }

            @Override
            public <R> Flow<R> map(MapFunction<? super T, ? extends R> mapper, int replicas, Resource resource) {
                List<Stage> next = new ArrayList<>(stages);
                next.add(new MapStage(value -> mapper.apply((T) value)));
                return new DefaultFlow<>(workflow, source, next, afterTask);
            }

            @Override
            public <R> Flow<R> flatMap(FlatMapFunction<? super T, ? extends R> mapper) {
                List<Stage> next = new ArrayList<>(stages);
                next.add(new FlatMapStage(value -> Collections.singleton(mapper.apply((T) value))));
                return new DefaultFlow<>(workflow, source, next, afterTask);
            }

            @Override
            public <R> Flow<R> flatMap(FlatMapFunction<? super T, ? extends R> mapper, Resource resource) {
                return null;
            }

            @Override
            public <R> Flow<R> flatMap(FlatMapFunction<? super T, ? extends R> mapper, int replicas) {
                return null;
            }

            @Override
            public <R> Flow<R> flatMap(FlatMapFunction<? super T, ? extends R> mapper, int replicas, Resource resource) {
                return null;
            }

            @Override
            public Flow<T> filter(FilterFunction<? super T> predicate) {
                List<Stage> next = new ArrayList<>(stages);
                next.add(new FilterStage(value -> predicate.test((T) value)));
                return new DefaultFlow<>(workflow, source, next, afterTask);
            }

            @Override
            public Flow<T> after(Task task) {
                return new DefaultFlow<>(workflow, source, stages, task);
            }

            @Override
            public void sink(Sink<? super T> sink) {
                workflow.registerPipeline(new Pipeline<T>(source, stages, afterTask, sink) {
                });
            }

        }
}

