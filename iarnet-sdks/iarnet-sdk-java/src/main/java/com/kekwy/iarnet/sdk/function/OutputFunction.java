package com.kekwy.iarnet.sdk.function;

import com.kekwy.iarnet.proto.common.Lang;

@FunctionalInterface
public interface OutputFunction<I> extends Function {

    void accept(I input);

    @Override
    default Lang getLang() {
        return Lang.LANG_JAVA;
    }
}
