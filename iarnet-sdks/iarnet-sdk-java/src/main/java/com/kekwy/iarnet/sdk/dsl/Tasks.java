package com.kekwy.iarnet.sdk.dsl;

import com.kekwy.iarnet.proto.common.Lang;
import com.kekwy.iarnet.sdk.function.TaskFunction;

public final class Tasks {

    public static <I, O> TaskFunction<I, O> pythonTask() {
        return new TaskFunction<>() {
            @Override
            public O apply(I input) {
                return null;
            }

            @Override
            public Lang getLang() {
                return Lang.LANG_PYTHON;
            }
        };
    }

}
