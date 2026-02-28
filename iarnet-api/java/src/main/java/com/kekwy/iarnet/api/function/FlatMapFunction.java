package com.kekwy.iarnet.api;

/**
 * 自定义 flatMap 函数接口，将一个元素映射为 0..N 个元素。
 */
@FunctionalInterface
public interface FlatMapFunction<T, R> {

    Iterable<R> apply(T value);
}

