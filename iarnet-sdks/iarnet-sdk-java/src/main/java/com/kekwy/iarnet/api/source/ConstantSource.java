package com.kekwy.iarnet.api.source;

import java.util.Arrays;
import java.util.List;

public class ConstantSource<T> implements Source<T> {

    private final List<T> value;

    public ConstantSource(final List<T> value) {
        this.value = value;
    }

    @SafeVarargs
    public static <T> ConstantSource<T> of(T... value) {
        return new ConstantSource<>(Arrays.stream(value).toList());
    }

    public List<T> getValue() {
        return value;
    }

    @Override
    public <R> R accept(SourceVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
