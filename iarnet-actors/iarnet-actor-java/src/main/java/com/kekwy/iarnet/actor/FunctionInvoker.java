package com.kekwy.iarnet.actor;

import com.kekwy.iarnet.proto.ValueCodec;
import com.kekwy.iarnet.proto.common.FunctionDescriptor;
import com.kekwy.iarnet.proto.common.Type;
import com.kekwy.iarnet.proto.common.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * 根据 FunctionDescriptor 反序列化用户函数，并按类型（Input/Task/Output/Union）反射调用。
 * 不依赖 SDK 接口，仅通过元数据推断类型。
 */
public final class FunctionInvoker {

    private static final String OPTIONAL_VALUE_CLASS = "com.kekwy.iarnet.sdk.type.OptionalValue";

    private static final Logger log = LoggerFactory.getLogger(FunctionInvoker.class);

    private final FunctionDescriptor descriptor;
    private final Object function;
    private final Kind kind;
    private final UserJarLoader jarLoader;

    public enum Kind {
        INPUT,   // 0 inputs, has output -> next()
        TASK,    // 1 input, has output -> apply(I)
        OUTPUT,  // 1 input, no output -> accept(I)
        UNION    // 2 inputs, has output -> union(OptionalValue, OptionalValue)
    }

    /**
     * 使用环境变量或描述符推断的节点类型创建。
     *
     * @param descriptor   函数描述符
     * @param jarLoader    用户 JAR ClassLoader
     * @param explicitKind 若非 null 则直接使用（如来自 IARNET_NODE_KIND），否则根据 descriptor 推断
     */
    public FunctionInvoker(FunctionDescriptor descriptor, UserJarLoader jarLoader, Kind explicitKind)
            throws IOException, ClassNotFoundException {
        this.descriptor = descriptor;
        this.jarLoader = jarLoader;
        if (!descriptor.getSerializedFunction().isEmpty()) {
            this.function = jarLoader.deserialize(descriptor.getSerializedFunction().toByteArray());
        } else {
            throw new IllegalArgumentException("FunctionDescriptor 缺少 serialized_function");
        }
        this.kind = explicitKind != null ? explicitKind : inferKind(descriptor);
        log.debug("FunctionInvoker 已创建: kind={}, identifier={}", kind, descriptor.getFunctionIdentifier());
    }

    /** 等价于 {@code new FunctionInvoker(descriptor, jarLoader, null)}，完全由描述符推断类型。 */
    public FunctionInvoker(FunctionDescriptor descriptor, UserJarLoader jarLoader)
            throws IOException, ClassNotFoundException {
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
        if (inputCount == 1 && !hasOutput) {
            return Kind.OUTPUT;
        }
        if (inputCount == 2 && hasOutput) {
            return Kind.UNION;
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
        Method next = function.getClass().getMethod("next");
        Type outputType = descriptor.getOutputType();
        while (true) {
            Object result = next.invoke(function);
            // Optional<?> from InputFunction.next()
            if (result == null) {
                break;
            }
            boolean empty = (Boolean) result.getClass().getMethod("isEmpty").invoke(result);
            if (empty) {
                break;
            }
            Object value = result.getClass().getMethod("get").invoke(result);
            Value encoded = ValueCodec.encode(value, outputType);
            sendValue.accept(encoded);
        }
    }

    /**
     * Task 函数：单输入单输出，解码 input 后调用 apply，编码结果返回。
     */
    public Value runTask(Value input) throws Throwable {
        if (kind != Kind.TASK) {
            throw new IllegalStateException("非 Task 函数");
        }
        Type inputType = descriptor.getInputsType(0);
        Type outputType = descriptor.getOutputType();
        Object decoded = ValueCodec.decode(input, typeToClass(inputType));
        Method apply = function.getClass().getMethod("apply", Object.class);
        Object result = apply.invoke(function, decoded);
        return ValueCodec.encode(result, outputType);
    }

    /**
     * Output（Sink）函数：解码 input 后调用 accept，无返回值。
     */
    public void runOutput(Value input) throws Throwable {
        if (kind != Kind.OUTPUT) {
            throw new IllegalStateException("非 Output 函数");
        }
        Type inputType = descriptor.getInputsType(0);
        Object decoded = ValueCodec.decode(input, typeToClass(inputType));
        Method accept = function.getClass().getMethod("accept", Object.class);
        accept.invoke(function, decoded);
    }

    /**
     * Union 函数：两路输入（可为空），解码后包装为 OptionalValue 调用 union。
     */
    public Value runUnion(Value input1, Value input2) throws Throwable {
        if (kind != Kind.UNION) {
            throw new IllegalStateException("非 Union 函数");
        }
        ClassLoader cl = function.getClass().getClassLoader();
        Class<?> optionalValueClass = cl.loadClass(OPTIONAL_VALUE_CLASS);
        Type inputType1 = descriptor.getInputsType(0);
        Type inputType2 = descriptor.getInputsType(1);
        Type outputType = descriptor.getOutputType();

        Object left = input1 == null || input1.getKindCase() == Value.KindCase.KIND_NOT_SET
                ? null
                : ValueCodec.decode(input1, typeToClass(inputType1));
        Object right = input2 == null || input2.getKindCase() == Value.KindCase.KIND_NOT_SET
                ? null
                : ValueCodec.decode(input2, typeToClass(inputType2));

        Method of = optionalValueClass.getMethod("ofNullable", Object.class);
        Method empty = optionalValueClass.getMethod("empty");
        Object optLeft = left == null ? empty.invoke(null) : of.invoke(null, left);
        Object optRight = right == null ? empty.invoke(null) : of.invoke(null, right);

        Method union = function.getClass().getMethod("union", optionalValueClass, optionalValueClass);
        Object result = union.invoke(function, optLeft, optRight);
        return ValueCodec.encode(result, outputType);
    }

    private static Class<?> typeToClass(Type type) {
        return switch (type.getKind()) {
            case TYPE_KIND_STRING -> String.class;
            case TYPE_KIND_INT32 -> Integer.class;
            case TYPE_KIND_INT64 -> Long.class;
            case TYPE_KIND_FLOAT -> Float.class;
            case TYPE_KIND_DOUBLE -> Double.class;
            case TYPE_KIND_BOOLEAN -> Boolean.class;
            case TYPE_KIND_BYTES -> byte[].class;
            case TYPE_KIND_NULL -> Void.class;
            case TYPE_KIND_ARRAY -> java.util.List.class;
            case TYPE_KIND_MAP -> java.util.Map.class;
            case TYPE_KIND_STRUCT -> java.util.Map.class;
            default -> Object.class;
        };
    }
}
