package com.kekwy.iarnet.sdk;

import com.kekwy.iarnet.sdk.function.FoldFunction;
import com.kekwy.iarnet.sdk.function.JoinFunction;

import java.time.Duration;

/**
 * 按 key 分区后的流抽象，支持 fold、join。
 */
public interface KeyedFlow<T, K> {

    /**
     * 流式折叠聚合：每收到一个元素更新累加器并发出当前值。
     *
     * @param initial 累加器初始值
     * @param fn      折叠函数 (accumulator, value) -> new accumulator
     */
    <ACC> Flow<ACC> fold(ACC initial, Duration timeout, FoldFunction<? super T, ACC> fn);

    /**
     * 双流 Join：仅支持一对一连接（Key 唯一）。
     *
     * @param other   另一条流
     * @param timeout 超时时间，超过该时间未匹配则丢弃
     * @param joiner  Join 函数
     */
    <R, OUT> Flow<OUT> join(KeyedFlow<R, K> other, Duration timeout, JoinFunction<? super T, ? super R, OUT> joiner);
}

