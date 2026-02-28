package com.kekwy.iarnet.api.source;

import java.util.Iterator;

public record ConstantSource<T>(T value) implements Source<T> {

    public static <T> ConstantSource<T> of(T value) {
        return new ConstantSource<>(value);
    }

    @Override
    public Iterator<T> iterator() {
        return null;
    }
}
