package com.kekwy.iarnet.api;

/**
 * 流式数据处理 DSL，支持 map、flatMap、filter，以及 after(task) 与 sink。
 *
 * @param <T> 当前流元素类型
 */
public interface Flow<T> {

    /**
     * 将每个元素映射为新元素。
     */
    <R> Flow<R> map(MapFunction<? super T, ? extends R> mapper);

    /**
     * 将每个元素映射为可迭代集合，并展平为流。
     */
    <R> Flow<R> flatMap(FlatMapFunction<? super T, ? extends R> mapper);

    /**
     * 仅保留满足谓词的元素。
     */
    Flow<T> filter(FilterFunction<? super T> predicate);

    /**
     * 在本流之后插入任务节点（如 checkpoint），再连接 sink。
     */
    Flow<T> after(Task task);

    /**
     * 将本流输出到指定的 Sink，完成管道注册；之后调用 workflow.execute() 时运行。
     */
    void sink(Sink<? super T> sink);
}

