package com.kekwy.iarnet.sdk.function;

import com.kekwy.iarnet.proto.common.Lang;
import com.kekwy.iarnet.sdk.type.OptionalValue;

@FunctionalInterface
public interface UnionFunction<T, U, V> extends Function {

    V union(OptionalValue<T> t, OptionalValue<U> u);

    @Override
    default Lang getLang() {
        return Lang.LANG_JAVA;
    }
}
