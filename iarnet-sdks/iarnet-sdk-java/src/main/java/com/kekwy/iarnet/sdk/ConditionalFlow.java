package com.kekwy.iarnet.sdk;

import com.kekwy.iarnet.sdk.function.OutputFunction;
import com.kekwy.iarnet.sdk.function.TaskFunction;

public interface ConditionalFlow<T> {

    <R> Flow<R> then(String name, TaskFunction<T, R> function);

    <R> Flow<R> then(String name, TaskFunction<T, R> function, ExecutionConfig config);

    EndFlow<T> then(String name, OutputFunction<T> function);

    EndFlow<T> then(String name, OutputFunction<T> function, ExecutionConfig config);
}