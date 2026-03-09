package com.kekwy.iarnet.sdk.function;

import com.kekwy.iarnet.proto.common.Lang;

/**
 * 自定义过滤函数接口。
 */
@FunctionalInterface
public interface FilterFunction<T> extends Function {

    boolean test(T value);

    @Override
    default Lang getLang() {
        return Lang.LANG_JAVA;
    }
}
