package com.kekwy.iarnet.sdk.dsl;

import com.kekwy.iarnet.sdk.function.InputFunction;

import java.util.Arrays;
import java.util.Optional;

public final class Inputs {

    private Inputs() {
    }

    @SafeVarargs
    public static <T> InputFunction<T> of(T... items) {
        return new InputFunction<>() {

            private final T[] data = Arrays.copyOf(items, items.length);
            private int index = 0;

            @Override
            public Optional<T> next() {
                if (index >= data.length) {
                    return Optional.empty();
                }
                return Optional.of(data[index++]);
            }
        };
    }

}
