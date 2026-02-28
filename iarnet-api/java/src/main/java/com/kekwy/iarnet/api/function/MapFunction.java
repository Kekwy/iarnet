package com.kekwy.iarnet.api.function;

/**
 * 自定义 map 函数接口，对元素进行一对一映射。
 */
@FunctionalInterface
public interface MapFunction<T, R> {

    R apply(T value);
}

