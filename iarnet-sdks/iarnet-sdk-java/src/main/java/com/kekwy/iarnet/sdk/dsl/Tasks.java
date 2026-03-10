package com.kekwy.iarnet.sdk.dsl;

import com.kekwy.iarnet.sdk.function.GoTaskFunction;
import com.kekwy.iarnet.sdk.function.PythonTaskFunction;
import com.kekwy.iarnet.sdk.function.TaskFunction;

public final class Tasks {

    public static <I, O> TaskFunction<I, O> pythonTask(String functionIdentifier) {
        return new PythonTaskFunction<>(functionIdentifier, ""); // 使用 classpath resources（Python 代码在 src/main/resources/function/
    }

    public static <I, O> TaskFunction<I, O> pythonTask(String functionIdentifier, String sourcePath) {
        return new PythonTaskFunction<>(functionIdentifier, sourcePath);
    }

    public static <I, O> TaskFunction<I, O> goTask(String functionIdentifier) {
        return new GoTaskFunction<>(functionIdentifier, ""); // 使用 classpath resources（Go 代码在 src/main/resources/function/
    }

    public static <I, O> TaskFunction<I, O> goTask(String functionIdentifier, String sourcePath) {
        return new GoTaskFunction<>(functionIdentifier, sourcePath);
    }

}
