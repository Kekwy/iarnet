package com.kekwy.iarnet.actor;

import com.kekwy.iarnet.proto.ValueCodec;
import com.kekwy.iarnet.proto.common.FunctionDescriptor;
import com.kekwy.iarnet.proto.common.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 根据 FunctionDescriptor 反序列化用户函数，并按类型（Input/Task/Output/Combine）反射调用。
 * 不依赖 SDK 接口，仅通过元数据推断类型。
 */
public final class FunctionInvoker {

    private static final String OPTIONAL_VALUE_CLASS = "com.kekwy.iarnet.sdk.type.OptionalValue";

    private static final Logger log = LoggerFactory.getLogger(FunctionInvoker.class);

    private final FunctionDescriptor descriptor;
    private final Object function;
    private final Kind kind;
    private final UserJarLoader jarLoader;

    // 启动时从 Method 解析并缓存入参/返回值对应的 Java 类型，调用时直接使用
    private final Method inputNext;
    private final Method optionalIsEmpty;
    private final Method optionalGet;
    private final Method taskApply;
    private final Class<?> taskInputClass;
    private final Method outputAccept;
    private final Class<?> outputInputClass;
    private final Class<?> combineOptionalValueClass;
    private final Method combineOfNullable;
    private final Method combineEmpty;
    private final Method combineCombine;
    private final Class<?> combineLeftInputClass;
    private final Class<?> combineRightInputClass;

    public enum Kind {
        INPUT,   // 0 inputs, has output -> next()
        TASK,    // 1 input, has output -> apply(I)
        OUTPUT,  // 1 input, no output -> accept(I)
        COMBINE  // 2 inputs, has output -> combine(OptionalValue, OptionalValue)
    }

    /**
     * 使用环境变量或描述符推断的节点类型创建。
     *
     * @param descriptor   函数描述符
     * @param jarLoader    用户 JAR ClassLoader
     * @param explicitKind 若非 null 则直接使用（如来自 IARNET_NODE_KIND），否则根据 descriptor 推断
     */
    public FunctionInvoker(FunctionDescriptor descriptor, UserJarLoader jarLoader, Kind explicitKind)
            throws IOException, ClassNotFoundException, NoSuchMethodException {
        this.descriptor = descriptor;
        this.jarLoader = jarLoader;
        if (!descriptor.getSerializedFunction().isEmpty()) {
            this.function = jarLoader.deserialize(descriptor.getSerializedFunction().toByteArray());
        } else {
            throw new IllegalArgumentException("FunctionDescriptor 缺少 serialized_function");
        }
        this.kind = explicitKind != null ? explicitKind : inferKind(descriptor);
        Method inputNext0 = null, optionalIsEmpty0 = null, optionalGet0 = null;
        Method taskApply0 = null;
        Class<?> taskInputClass0 = null;
        Method outputAccept0 = null;
        Class<?> outputInputClass0 = null;
        Class<?> combineOptionalValueClass0 = null;
        Method combineOfNullable0 = null, combineEmpty0 = null, combineCombine0 = null;
        Class<?> combineLeftInputClass0 = null, combineRightInputClass0 = null;
        Class<?> fnClass = this.function.getClass();
        switch (this.kind) {
            case INPUT -> {
                inputNext0 = fnClass.getMethod("next");
                optionalIsEmpty0 = Optional.class.getMethod("isEmpty");
                optionalGet0 = Optional.class.getMethod("get");
            }
            case TASK -> {
                taskApply0 = fnClass.getMethod("apply", Object.class);
                taskInputClass0 = taskApply0.getParameterTypes()[0];
            }
            case OUTPUT -> {
                outputAccept0 = fnClass.getMethod("accept", Object.class);
                outputInputClass0 = outputAccept0.getParameterTypes()[0];
            }
            case COMBINE -> {
                ClassLoader cl = fnClass.getClassLoader();
                combineOptionalValueClass0 = cl.loadClass(OPTIONAL_VALUE_CLASS);
                combineOfNullable0 = combineOptionalValueClass0.getMethod("ofNullable", Object.class);
                combineEmpty0 = combineOptionalValueClass0.getMethod("empty");
                combineCombine0 = fnClass.getMethod("combine", combineOptionalValueClass0, combineOptionalValueClass0);
                Type[] genericParams = combineCombine0.getGenericParameterTypes();
                combineLeftInputClass0 = toRawClass(firstTypeArgument(genericParams[0]));
                combineRightInputClass0 = toRawClass(firstTypeArgument(genericParams[1]));
            }
        }
        this.inputNext = inputNext0;
        this.optionalIsEmpty = optionalIsEmpty0;
        this.optionalGet = optionalGet0;
        this.taskApply = taskApply0;
        this.taskInputClass = taskInputClass0;
        this.outputAccept = outputAccept0;
        this.outputInputClass = outputInputClass0;
        this.combineOptionalValueClass = combineOptionalValueClass0;
        this.combineOfNullable = combineOfNullable0;
        this.combineEmpty = combineEmpty0;
        this.combineCombine = combineCombine0;
        this.combineLeftInputClass = combineLeftInputClass0;
        this.combineRightInputClass = combineRightInputClass0;
        log.debug("FunctionInvoker 已创建: kind={}, identifier={}", kind, descriptor.getFunctionIdentifier());
    }

    /** 等价于 {@code new FunctionInvoker(descriptor, jarLoader, null)}，完全由描述符推断类型。 */
    public FunctionInvoker(FunctionDescriptor descriptor, UserJarLoader jarLoader)
            throws IOException, ClassNotFoundException, NoSuchMethodException {
        this(descriptor, jarLoader, null);
    }

    private static Kind inferKind(FunctionDescriptor fd) {
        int inputCount = fd.getInputsTypeCount();
        boolean hasOutput = fd.hasOutputType() && fd.getOutputType().getKind() != com.kekwy.iarnet.proto.common.TypeKind.TYPE_KIND_UNSPECIFIED;

        if (inputCount == 0 && hasOutput) {
            return Kind.INPUT;
        }
        if (inputCount == 1 && hasOutput) {
            return Kind.TASK;
        }
        //noinspection ConstantValue
        if (inputCount == 1 && !hasOutput) {
            return Kind.OUTPUT;
        }
        if (inputCount == 2 && hasOutput) {
            return Kind.COMBINE;
        }
        throw new IllegalArgumentException("无法推断函数类型: inputs=" + inputCount + ", hasOutput=" + hasOutput);
    }

    public Kind getKind() {
        return kind;
    }

    /**
     * Input 函数：循环调用 next()，将每次返回值编码为 DataRow 并通过 callback 发送。
     * 在单独线程中调用，以免阻塞 gRPC 流。
     */
    public void runInput(Consumer<Value> sendValue) throws Throwable {
        if (kind != Kind.INPUT) {
            throw new IllegalStateException("非 Input 函数");
        }
        while (true) {
            Object result = inputNext.invoke(function);
            if (result == null) break;
            if (Boolean.TRUE.equals(optionalIsEmpty.invoke(result))) break;
            Object value = optionalGet.invoke(result);
            sendValue.accept(ValueCodec.encode(value));
        }
    }

    /**
     * Task 函数：单输入单输出，解码 input 后调用 apply，编码结果返回。
     */
    public Value runTask(Value input) throws Throwable {
        if (kind != Kind.TASK) {
            throw new IllegalStateException("非 Task 函数");
        }
        Object decoded = ValueCodec.decode(input, taskInputClass);
        Object result = taskApply.invoke(function, decoded);
        return ValueCodec.encode(result);
    }

    /**
     * Output（Sink）函数：解码 input 后调用 accept，无返回值。
     */
    public void runOutput(Value input) throws Throwable {
        if (kind != Kind.OUTPUT) {
            throw new IllegalStateException("非 Output 函数");
        }
        Object decoded = ValueCodec.decode(input, outputInputClass);
        outputAccept.invoke(function, decoded);
    }

    /**
     * Combine 函数：两路输入（可为空），解码后包装为 OptionalValue 调用 combine。
     */
    public Value runCombine(Value input1, Value input2) throws Throwable {
        if (kind != Kind.COMBINE) {
            throw new IllegalStateException("非 Combine 函数");
        }
        Object left = input1 == null || input1.getKindCase() == Value.KindCase.KIND_NOT_SET
                ? null
                : ValueCodec.decode(input1, combineLeftInputClass);
        Object right = input2 == null || input2.getKindCase() == Value.KindCase.KIND_NOT_SET
                ? null
                : ValueCodec.decode(input2, combineRightInputClass);
        Object optLeft = left == null ? combineEmpty.invoke(null) : combineOfNullable.invoke(null, left);
        Object optRight = right == null ? combineEmpty.invoke(null) : combineOfNullable.invoke(null, right);
        Object result = combineCombine.invoke(function, optLeft, optRight);
        return ValueCodec.encode(result);
    }

    /** 从 OptionalValue&lt;T&gt; 等参数化类型取第一个类型实参。 */
    private static Type firstTypeArgument(Type type) {
        if (type instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            return args.length > 0 ? args[0] : Object.class;
        }
        return Object.class;
    }

    /** 将 java.lang.reflect.Type 转为 Class（泛型取 raw type）。 */
    private static Class<?> toRawClass(Type type) {
        if (type instanceof Class<?> c) return c;
        if (type instanceof ParameterizedType pt) return (Class<?>) pt.getRawType();
        return Object.class;
    }
}
