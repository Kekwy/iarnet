package com.kekwy.iarnet.sdk.dsl;

import com.kekwy.iarnet.sdk.function.GoTaskFunction;
import com.kekwy.iarnet.sdk.function.PythonTaskFunction;
import com.kekwy.iarnet.sdk.function.TaskFunction;
import com.kekwy.iarnet.sdk.type.TypeToken;

public final class Tasks {

    public static <I, O> TaskFunction<I, O> pythonTask(String functionIdentifier) {
        return new PythonTaskFunction<>(functionIdentifier, "");
    }

    public static <I, O> TaskFunction<I, O> pythonTask(String functionIdentifier, String sourcePath) {
        return new PythonTaskFunction<>(functionIdentifier, sourcePath);
    }

    /**
     * 带输出类型提示的 Python task，TypeExtractor 可正确推断，无需 returns。
     */
    public static <I, O> TaskFunction<I, O> pythonTask(String functionIdentifier, TypeToken<O> outputType) {
        return new PythonTaskFunction<>(functionIdentifier, "", outputType.getType());
    }

    public static <I, O> TaskFunction<I, O> pythonTask(String functionIdentifier, String sourcePath, TypeToken<O> outputType) {
        return new PythonTaskFunction<>(functionIdentifier, sourcePath, outputType.getType());
    }

    public static <I, O> TaskFunction<I, O> goTask(String functionIdentifier) {
        return new GoTaskFunction<>(functionIdentifier, "");
    }

    public static <I, O> TaskFunction<I, O> goTask(String functionIdentifier, String sourcePath) {
        return new GoTaskFunction<>(functionIdentifier, sourcePath);
    }

    /**
     * 带输出类型提示的 Go task，TypeExtractor 可正确推断，无需 returns。
     */
    public static <I, O> TaskFunction<I, O> goTask(String functionIdentifier, TypeToken<O> outputType) {
        return new GoTaskFunction<>(functionIdentifier, "", outputType.getType());
    }

    public static <I, O> TaskFunction<I, O> goTask(String functionIdentifier, String sourcePath, TypeToken<O> outputType) {
        return new GoTaskFunction<>(functionIdentifier, sourcePath, outputType.getType());
    }

}
