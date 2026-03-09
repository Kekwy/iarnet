package com.kekwy.iarnet.sdk;

import com.kekwy.iarnet.sdk.function.*;
import com.kekwy.iarnet.sdk.type.TypeToken;


public interface Flow<T> {

    <R> Flow<R> then(String name, TaskFunction<T, R> function);

    <R> Flow<R> then(String name, TaskFunction<T, R> function, ExecutionConfig config);

    @SuppressWarnings("UnusedReturnValue")
    EndFlow<T> then(String name, OutputFunction<T> function);

    @SuppressWarnings("UnusedReturnValue")
    EndFlow<T> then(String name, OutputFunction<T> function, ExecutionConfig config);

    <U, V> Flow<V> union(String name, Flow<U> other, UnionFunction<T, U, V> function);

    <U, V> Flow<V> union(String name, Flow<U> other, UnionFunction<T, U, V> function, ExecutionConfig config);

    ConditionalFlow<T> when(ConditionFunction<T> condition);

    Flow<T> returns(TypeToken<T> typeHint);

}
