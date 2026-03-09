package com.kekwy.iarnet.sdk.function;

import com.kekwy.iarnet.proto.common.Lang;

/**
 * 自定义 flatMap 函数接口，将一个元素映射为 0..N 个元素。
 */
@FunctionalInterface
public interface FlatMapFunction<T, R> extends Function {

    Iterable<R> apply(T value);

    @Override
    default Lang getLang() {
        return Lang.LANG_JAVA;
    }
}
