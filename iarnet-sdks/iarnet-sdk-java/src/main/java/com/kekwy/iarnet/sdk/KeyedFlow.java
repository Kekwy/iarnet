package com.kekwy.iarnet.sdk;

/**
 * 按 key 分区后的流抽象，仅暴露 connect 能力。
 */
public interface KeyedFlow<T, K> {

    /**
     * 将当前 keyed 流与另一条相同 key 类型的 keyed 流连接，
     * 构造一个双输入的 CoKeyedFlow。
     */
    <R> CoKeyedFlow<T, R, K> connect(KeyedFlow<R, K> other);
}

