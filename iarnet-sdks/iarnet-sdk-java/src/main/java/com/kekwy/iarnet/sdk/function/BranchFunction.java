package com.kekwy.iarnet.sdk.function;

import com.kekwy.iarnet.proto.common.Lang;

public interface BranchFunction <T> extends Function {

    int toPort(T value);

    @Override
    default Lang getLang() {
        return Lang.LANG_JAVA;
    }
}