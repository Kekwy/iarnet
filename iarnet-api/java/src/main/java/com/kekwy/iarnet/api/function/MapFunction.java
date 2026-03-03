package com.kekwy.iarnet.api.function;

import com.kekwy.iarnet.api.Lang;

/**
 * 自定义 map 函数接口，对元素进行一对一映射。
 */
@FunctionalInterface
public interface MapFunction<T, R> extends Function {

    R apply(T value);

    @Override
    default Lang getLang() {
        return Lang.LANG_JAVA;
    }
}

