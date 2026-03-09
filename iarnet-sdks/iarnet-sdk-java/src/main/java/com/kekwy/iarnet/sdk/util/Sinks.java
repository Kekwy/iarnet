package com.kekwy.iarnet.sdk.util;

import com.kekwy.iarnet.sdk.function.SinkFunction;

public final class Sinks {

    private Sinks() {
    }

    public static <T> SinkFunction<T> println() {
        return System.out::println;
    }

    public static <T> SinkFunction<T> noop() {
        return value -> {};
    }

}