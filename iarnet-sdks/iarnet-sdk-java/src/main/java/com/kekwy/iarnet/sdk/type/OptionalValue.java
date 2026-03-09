package com.kekwy.iarnet.sdk.type;

import java.io.Serial;
import java.io.Serializable;
import java.util.NoSuchElementException;
import java.util.Objects;

public final class OptionalValue<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final OptionalValue<?> EMPTY = new OptionalValue<>(false, null);

    private final boolean present;
    private final T value;

    private OptionalValue(boolean present, T value) {
        this.present = present;
        this.value = value;
    }

    public static <T> OptionalValue<T> of(T value) {
        return new OptionalValue<>(true, Objects.requireNonNull(value, "value must not be null"));
    }

    public static <T> OptionalValue<T> ofNullable(T value) {
        return value == null ? empty() : of(value);
    }

    @SuppressWarnings("unchecked")
    public static <T> OptionalValue<T> empty() {
        return (OptionalValue<T>) EMPTY;
    }

    public boolean isPresent() {
        return present;
    }

    public boolean isEmpty() {
        return !present;
    }

    public T get() {
        if (!present) {
            throw new NoSuchElementException("No value present");
        }
        return value;
    }

    public T orElse(T other) {
        return present ? value : other;
    }

    public <R> OptionalValue<R> map(java.util.function.Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        if (isEmpty()) {
            return empty();
        }
        return OptionalValue.ofNullable(mapper.apply(value));
    }

    @Override
    public String toString() {
        return present ? "OptionalValue[" + value + "]" : "OptionalValue.empty";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof OptionalValue<?> other)) return false;
        if (present != other.present) return false;
        return Objects.equals(value, other.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(present, value);
    }
}