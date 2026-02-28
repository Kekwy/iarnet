package com.kekwy.iarnet.api.function;

/**
 * 自定义过滤函数接口。
 */
@FunctionalInterface
public interface FilterFunction<T> {

    boolean test(T value);
}

