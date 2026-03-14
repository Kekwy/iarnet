package com.kekwy.iarnet.sdk.type;

import java.io.Serial;
import java.io.Serializable;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 可序列化的“可选值”容器。
 * <p>
 * 语义类似 {@link java.util.Optional}，但实现 {@link Serializable}，
 * 用于 {@link com.kekwy.iarnet.sdk.function.CombineFunction} 的两路输入：
 * 每路可能“有值”或“无值”（对应分支暂无数据到达）。
 *
 * @param <T> 值类型
 */
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

    /**
     * 创建包含非 null 值的 OptionalValue。
     *
     * @param value 值，不能为 null
     * @return 包含该值的 OptionalValue
     * @throws NullPointerException 若 value 为 null
     */
    public static <T> OptionalValue<T> of(T value) {
        return new OptionalValue<>(true, Objects.requireNonNull(value, "value must not be null"));
    }

    /**
     * 若 value 非 null 则创建包含该值的 OptionalValue，否则返回 empty。
     *
     * @param value 可为 null 的值
     * @return OptionalValue 或 empty
     */
    public static <T> OptionalValue<T> ofNullable(T value) {
        return value == null ? empty() : of(value);
    }

    /**
     * 返回一个空的 OptionalValue。
     *
     * @return 空的 OptionalValue
     */
    @SuppressWarnings("unchecked")
    public static <T> OptionalValue<T> empty() {
        return (OptionalValue<T>) EMPTY;
    }

    /** 是否有值。 */
    public boolean isPresent() {
        return present;
    }

    /** 是否为空（无值）。 */
    public boolean isEmpty() {
        return !present;
    }

    /**
     * 获取值，若为空则抛出 {@link NoSuchElementException}。
     *
     * @return 当前值
     * @throws NoSuchElementException 若为空
     */
    public T get() {
        if (!present) {
            throw new NoSuchElementException("No value present");
        }
        return value;
    }

    /**
     * 若有值则返回该值，否则返回 other。
     *
     * @param other 空值时的替代
     * @return 值或 other
     */
    public T orElse(T other) {
        return present ? value : other;
    }

    /**
     * 若有值则返回该值，否则抛出由 exceptionSupplier 提供的异常。
     *
     * @param exceptionSupplier 无值时的异常提供者
     * @param <X>               异常类型
     * @return 当前值
     * @throws X                    若为空
     * @throws NullPointerException 若 exceptionSupplier 为 null
     */
    public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
        if (present) return value;
        throw exceptionSupplier.get();
    }

    /**
     * 若当前有值则返回 this，否则返回 supplier 提供的 OptionalValue。
     *
     * @param supplier 无值时的替代
     * @return 本实例或 supplier 结果
     * @throws NullPointerException 若 supplier 为 null
     */
    @SuppressWarnings("unchecked")
    public OptionalValue<T> or(Supplier<? extends OptionalValue<? extends T>> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        if (present) return this;
        return (OptionalValue<T>) supplier.get();
    }

    /**
     * 若有值则对值应用 mapper 并包装结果，否则返回 empty。
     *
     * @param mapper 映射函数
     * @param <R>    映射结果类型
     * @return 映射后的 OptionalValue 或 empty
     * @throws NullPointerException 若 mapper 为 null
     */
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
