package com.kekwy.iarnet.sdk.function;

import java.io.Serializable;

/**
 * 从元素中提取 key，用于 keyBy/分区。
 */
@FunctionalInterface
public interface KeySelector<T, K> extends Serializable {

    K getKey(T value);
}

