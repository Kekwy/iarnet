package com.kekwy.iarnet.api.sink;

/**
 * 数据汇，消费类型为 T 的元素。
 */
public interface Sink<T> {

    void accept(T value);
}
