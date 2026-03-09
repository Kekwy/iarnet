package com.kekwy.iarnet.sdk.function;

import com.kekwy.iarnet.proto.common.Lang;

@FunctionalInterface
public interface BranchFunction <T> extends Function {

    int selectPort(T value);

    @Override
    default Lang getLang() {
        return Lang.LANG_JAVA;
    }
}