package com.kekwy.iarnet.sdk.dsl;

import com.kekwy.iarnet.sdk.function.InputFunction;

import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 工作流输入源 DSL 的静态工厂。
 * <p>
 * 提供基于内存数组的 {@link InputFunction} 实现，用于在构建阶段向工作流注入固定数据序列。
 * 每次调用 {@link InputFunction#next()} 按顺序返回下一个元素，耗尽后返回 {@link Optional#empty()}。
 * <p>
 * 典型用法（与 {@link com.kekwy.iarnet.sdk.Workflow} 配合）：
 * <pre>{@code
 * Workflow w = Workflow.create("example");
 * w.input("src", Inputs.of(1, 2, 3))
 *  .then("double", x -> x * 2)
 *  .then("sink", ...);
 * }</pre>
 * <p>
 * 对 Integer、String、Long、Double、Float、Boolean、Byte、Short、Character 提供具名重载，
 * 使 TypeExtractor 能正确推断输出类型，无需 returns。其他类型使用泛型实现，若推断失败需调用 .returns(TypeToken)。
 */
public final class Inputs {

    private static final Logger LOG = Logger.getLogger(Inputs.class.getName());

    private Inputs() {
        // 工具类，禁止实例化
    }

    /**
     * 从 Integer 序列构造输入函数。具名实现保留泛型信息，TypeExtractor 可正确推断，无需 returns。
     */
    @SafeVarargs
    public static InputFunction<Integer> of(Integer... items) {
        return new IntegerInputFunction(items == null ? new Integer[0] : Arrays.copyOf(items, items.length));
    }

    /**
     * 从 String 序列构造输入函数。具名实现保留泛型信息，TypeExtractor 可正确推断，无需 returns。
     */
    @SafeVarargs
    public static InputFunction<String> of(String... items) {
        return new StringInputFunction(items == null ? new String[0] : Arrays.copyOf(items, items.length));
    }

    /**
     * 从 Long 序列构造输入函数。具名实现保留泛型信息，TypeExtractor 可正确推断，无需 returns。
     */
    @SafeVarargs
    public static InputFunction<Long> of(Long... items) {
        return new LongInputFunction(items == null ? new Long[0] : Arrays.copyOf(items, items.length));
    }

    /**
     * 从 Double 序列构造输入函数。具名实现保留泛型信息，TypeExtractor 可正确推断，无需 returns。
     */
    @SafeVarargs
    public static InputFunction<Double> of(Double... items) {
        return new DoubleInputFunction(items == null ? new Double[0] : Arrays.copyOf(items, items.length));
    }

    /**
     * 从 Float 序列构造输入函数。具名实现保留泛型信息，TypeExtractor 可正确推断，无需 returns。
     */
    @SafeVarargs
    public static InputFunction<Float> of(Float... items) {
        return new FloatInputFunction(items == null ? new Float[0] : Arrays.copyOf(items, items.length));
    }

    /**
     * 从 Boolean 序列构造输入函数。具名实现保留泛型信息，TypeExtractor 可正确推断，无需 returns。
     */
    @SafeVarargs
    public static InputFunction<Boolean> of(Boolean... items) {
        return new BooleanInputFunction(items == null ? new Boolean[0] : Arrays.copyOf(items, items.length));
    }

    /**
     * 从 Byte 序列构造输入函数。具名实现保留泛型信息，TypeExtractor 可正确推断，无需 returns。
     */
    @SafeVarargs
    public static InputFunction<Byte> of(Byte... items) {
        return new ByteInputFunction(items == null ? new Byte[0] : Arrays.copyOf(items, items.length));
    }

    /**
     * 从 Short 序列构造输入函数。具名实现保留泛型信息，TypeExtractor 可正确推断，无需 returns。
     */
    @SafeVarargs
    public static InputFunction<Short> of(Short... items) {
        return new ShortInputFunction(items == null ? new Short[0] : Arrays.copyOf(items, items.length));
    }

    /**
     * 从 Character 序列构造输入函数。具名实现保留泛型信息，TypeExtractor 可正确推断，无需 returns。
     */
    @SafeVarargs
    public static InputFunction<Character> of(Character... items) {
        return new CharacterInputFunction(items == null ? new Character[0] : Arrays.copyOf(items, items.length));
    }

    /**
     * 从可变参数构造一个按序产出元素的输入函数（泛型版本）。
     * <p>
     * 内部对传入数组做拷贝，调用方后续修改原数组不会影响该 InputFunction 的行为。
     * 若类型推断失败，需对该 flow 调用 .returns(new TypeToken&lt;YourType&gt;() {}) 提供类型提示。
     *
     * @param items 初始元素序列，可为空
     * @param <T>   元素类型（需与下游节点的泛型一致，且可被序列化/类型推断）
     * @return 按序返回 {@code items} 中元素的 InputFunction，耗尽后返回 empty
     */
    @SafeVarargs
    public static <T> InputFunction<T> of(T... items) {
        final int size = items == null ? 0 : items.length;
        final T[] data = items == null ? null : Arrays.copyOf(items, size);
        LOG.log(Level.FINE, "Input source created with {0} item(s)", size);

        return new InputFunction<>() {

            private int index = 0;

            @Override
            public Optional<T> next() {
                if (data == null || index >= data.length) {
                    return Optional.empty();
                }
                T value = data[index++];
                LOG.log(Level.FINEST, "Input source yielded item[{0}]: {1}", new Object[]{index - 1, value});
                return Optional.of(value);
            }
        };
    }

    private static final class IntegerInputFunction implements InputFunction<Integer> {
        private final Integer[] data;
        private int index;

        IntegerInputFunction(Integer[] items) {
            this.data = items;
        }

        @Override
        public Optional<Integer> next() {
            if (index >= data.length) return Optional.empty();
            return Optional.of(data[index++]);
        }
    }

    private static final class StringInputFunction implements InputFunction<String> {
        private final String[] data;
        private int index;

        StringInputFunction(String[] items) {
            this.data = items;
        }

        @Override
        public Optional<String> next() {
            if (index >= data.length) return Optional.empty();
            return Optional.of(data[index++]);
        }
    }

    private static final class LongInputFunction implements InputFunction<Long> {
        private final Long[] data;
        private int index;

        LongInputFunction(Long[] items) {
            this.data = items;
        }

        @Override
        public Optional<Long> next() {
            if (index >= data.length) return Optional.empty();
            return Optional.of(data[index++]);
        }
    }

    private static final class DoubleInputFunction implements InputFunction<Double> {
        private final Double[] data;
        private int index;

        DoubleInputFunction(Double[] items) {
            this.data = items;
        }

        @Override
        public Optional<Double> next() {
            if (index >= data.length) return Optional.empty();
            return Optional.of(data[index++]);
        }
    }

    private static final class FloatInputFunction implements InputFunction<Float> {
        private final Float[] data;
        private int index;

        FloatInputFunction(Float[] items) {
            this.data = items;
        }

        @Override
        public Optional<Float> next() {
            if (index >= data.length) return Optional.empty();
            return Optional.of(data[index++]);
        }
    }

    private static final class BooleanInputFunction implements InputFunction<Boolean> {
        private final Boolean[] data;
        private int index;

        BooleanInputFunction(Boolean[] items) {
            this.data = items;
        }

        @Override
        public Optional<Boolean> next() {
            if (index >= data.length) return Optional.empty();
            return Optional.of(data[index++]);
        }
    }

    private static final class ByteInputFunction implements InputFunction<Byte> {
        private final Byte[] data;
        private int index;

        ByteInputFunction(Byte[] items) {
            this.data = items;
        }

        @Override
        public Optional<Byte> next() {
            if (index >= data.length) return Optional.empty();
            return Optional.of(data[index++]);
        }
    }

    private static final class ShortInputFunction implements InputFunction<Short> {
        private final Short[] data;
        private int index;

        ShortInputFunction(Short[] items) {
            this.data = items;
        }

        @Override
        public Optional<Short> next() {
            if (index >= data.length) return Optional.empty();
            return Optional.of(data[index++]);
        }
    }

    private static final class CharacterInputFunction implements InputFunction<Character> {
        private final Character[] data;
        private int index;

        CharacterInputFunction(Character[] items) {
            this.data = items;
        }

        @Override
        public Optional<Character> next() {
            if (index >= data.length) return Optional.empty();
            return Optional.of(data[index++]);
        }
    }
}
