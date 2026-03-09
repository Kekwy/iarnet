package com.kekwy.iarnet.sdk.function;

import java.io.Serializable;

@FunctionalInterface
public interface ConditionFunction<T> extends Serializable {
    boolean test(T input);
}