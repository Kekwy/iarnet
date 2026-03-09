package com.kekwy.iarnet.sdk.function;

import com.kekwy.iarnet.proto.common.Lang;

@FunctionalInterface
public interface JoinFunction<L, R, OUT> extends Function {
    OUT apply(L left, R right);

    @Override
    default Lang getLang() {
        return Lang.LANG_JAVA;
    }
}
