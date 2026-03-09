package com.kekwy.iarnet.sdk.sink;

import com.kekwy.iarnet.sdk.converter.SinkVisitor;

/**
 * 数据汇，消费类型为 T 的元素。
 */
public interface Sink<T> {

    void accept(T value);

    <R> R accept(SinkVisitor<R> visitor);
}
