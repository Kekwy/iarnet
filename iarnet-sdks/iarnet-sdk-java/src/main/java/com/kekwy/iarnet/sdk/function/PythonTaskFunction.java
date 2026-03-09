package com.kekwy.iarnet.sdk.function;

import com.kekwy.iarnet.proto.common.Lang;

public interface PythonTaskFunction<I, O> extends TaskFunction<I, O> {

    @Override
    default Lang getLang() {
        return Lang.LANG_PYTHON;
    }
}
