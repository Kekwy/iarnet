package com.kekwy.iarnet.sdk.function;

import com.kekwy.iarnet.proto.common.Lang;

import java.util.Optional;

@FunctionalInterface
public interface InputFunction<O> extends Function {
    Optional<O> next() throws Exception;

    @Override
    default Lang getLang() {
        return Lang.LANG_JAVA;
    }
}
