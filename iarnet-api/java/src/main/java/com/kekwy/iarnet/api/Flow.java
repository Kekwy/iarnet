package com.kekwy.iarnet.api;

import com.kekwy.iarnet.api.function.FilterFunction;
import com.kekwy.iarnet.api.function.FlatMapFunction;
import com.kekwy.iarnet.api.function.MapFunction;
import com.kekwy.iarnet.api.function.CombineFunction;
import com.kekwy.iarnet.api.function.KeySelector;
import com.kekwy.iarnet.api.sink.Sink;

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
     * 按 key 对当前流进行逻辑分区，返回 keyed 视图，用于后续 connect。
     */
    <K> KeyedFlow<T, K> keyBy(KeySelector<? super T, ? extends K> selector);

    /**
     * 显式声明当前流的输出类型，用于 lambda 返回泛型类型时自动推断失败的场景。
     * <pre>{@code
     * flow.map(s -> Arrays.asList(s))
     *     .returns(new TypeToken<List<String>>() {});
     * }</pre>
     */
    Flow<T> returns(TypeToken<T> typeHint);

    /**
     * 在单个算子中完成 Fork-Join 风格的对齐合并：
     * <ul>
     *     <li>先对同一条输入记录应用 {@code left} 产生单值结果</li>
     *     <li>再应用 {@code right} 产生 0..N 条右侧结果</li>
     *     <li>最后使用 {@code combiner} 将二者合并为一个输出元素</li>
     * </ul>
     *
     * @param left     左侧一对一映射函数
     * @param right    右侧一对多映射函数
     * @param combiner 将左值与右侧结果列表合并为最终输出的函数
     * @param <L>      左侧中间结果类型
     * @param <R>      右侧中间结果类型
     * @param <OUT>    输出元素类型
     * @return 合并后的新流
     */
    <L, R, OUT> Flow<OUT> map2(
            MapFunction<? super T, ? extends L> left,
            FlatMapFunction<? super T, ? extends R> right,
            CombineFunction<? super L, ? super java.util.List<R>, ? extends OUT> combiner);

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
