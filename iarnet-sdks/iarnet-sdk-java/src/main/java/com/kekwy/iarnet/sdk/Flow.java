package com.kekwy.iarnet.sdk;

import com.kekwy.iarnet.sdk.function.*;
import com.kekwy.iarnet.sdk.sink.Sink;
import com.kekwy.iarnet.sdk.util.TypeToken;

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

    <R> Flow<R> map(MapFunction<? super T, ? extends R> mapper, Resource resource);

    <R> Flow<R> map(MapFunction<? super T, ? extends R> mapper, int replicas);

    <R> Flow<R> map(MapFunction<? super T, ? extends R> mapper, int replicas, Resource resource);

    /**
     * 将每个元素映射为可迭代集合，并展平为流。
     */
    <R> Flow<R> flatMap(FlatMapFunction<? super T, ? extends R> mapper);

    <R> Flow<R> flatMap(FlatMapFunction<? super T, ? extends R> mapper, Resource resource);

    <R> Flow<R> flatMap(FlatMapFunction<? super T, ? extends R> mapper, int replicas);

    <R> Flow<R> flatMap(FlatMapFunction<? super T, ? extends R> mapper, int replicas, Resource resource);

    /**
     * 仅保留满足谓词的元素。
     */
    Flow<T> filter(FilterFunction<? super T> predicate);

    /**
     * 按 key 对当前流进行逻辑分区，返回 keyed 视图，用于后续 fold 或 join。
     */
    <K> KeyedFlow<T, K> keyBy(KeySelector<? super T, ? extends K> selector);

    /**
     * 按谓词条件分支，返回 matched / unmatched 两条子流。
     */
    BranchedFlow<T> branch(BranchFunction<? super T> predicate);

    /**
     * 显式声明当前流的输出类型，用于 lambda 返回泛型类型时自动推断失败的场景。
     * <pre>{@code
     * flow.map(s -> Arrays.asList(s))
     *     .returns(new TypeToken<List<String>>() {});
     * }</pre>
     */
    Flow<T> returns(TypeToken<T> typeHint);

    /**
     * 将当前流与另一条相同元素类型的流进行无对齐合流（Union）。
     * <p>
     * 语义等同于 Flink 的 {@code DataStream.union}：
     * 结果流中的元素是两条输入流元素的简单并集，不保证一一对齐。
     *
     * @param other 另一条同类型的流，必须由同一个 {@link Workflow} 实例创建
     * @return 合流后的新流
     */
    Flow<T> union(Flow<T> other);

    /**
     * 在本流之后插入任务节点（如 checkpoint），再连接 sink。
     */
    Flow<T> after(Task task);

    /**
     * 将本流输出到指定的 Sink，完成管道注册；之后调用 workflow.execute() 时运行。
     */
    void sink(Sink<? super T> sink);

}
