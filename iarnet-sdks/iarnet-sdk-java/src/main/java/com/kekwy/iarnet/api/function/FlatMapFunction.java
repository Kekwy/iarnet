package com.kekwy.iarnet.api.function;

import com.kekwy.iarnet.api.Lang;

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

