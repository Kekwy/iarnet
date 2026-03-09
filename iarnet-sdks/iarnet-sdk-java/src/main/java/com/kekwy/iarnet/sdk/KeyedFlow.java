package com.kekwy.iarnet.sdk;

import com.kekwy.iarnet.sdk.function.FoldFunction;
import com.kekwy.iarnet.sdk.function.JoinFunction;

/**
 * 按 key 分区后的流抽象，支持 fold、join 与 window。
 */
public interface KeyedFlow<T, K> {

    /**
     * 流式折叠聚合：每收到一个元素更新累加器并发出当前值。
     *
     * @param initial 累加器初始值
     * @param fn      折叠函数 (accumulator, value) -> new accumulator
     */
    <ACC> Flow<ACC> fold(ACC initial, FoldFunction<? super T, ACC> fn);

    <R, OUT> Flow<OUT> join(KeyedFlow<R, K> other, Window window, JoinFunction<? super T, ? super R, OUT> joiner);
}

