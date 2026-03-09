package com.kekwy.iarnet.sdk.dsl;

import com.kekwy.iarnet.sdk.function.OutputFunction;

public final class Outputs {

    private Outputs() {
    }

    public static <T> OutputFunction<T> println() {
        return System.out::println;
    }

    public static <T> OutputFunction<T> noop() {
        return value -> {};
    }

}