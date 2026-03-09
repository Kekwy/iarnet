package com.kekwy.iarnet.sdk.function;

import com.kekwy.iarnet.proto.common.Lang;

@FunctionalInterface
public interface ConditionFunction<T> extends Function {
    boolean test(T input);

    @Override
    default Lang getLang() {
        return Lang.LANG_JAVA;
    }
}