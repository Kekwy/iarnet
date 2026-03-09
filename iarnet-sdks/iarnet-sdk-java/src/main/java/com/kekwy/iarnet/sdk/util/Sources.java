package com.kekwy.iarnet.sdk.util;

import com.kekwy.iarnet.sdk.function.SourceFunction;

import java.util.Arrays;
import java.util.Optional;

public final class Sources {

    private Sources() {
    }

    @SafeVarargs
    public static <T> SourceFunction<T> of(T... items) {
        return new SourceFunction<>() {

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
