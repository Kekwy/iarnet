package com.kekwy.iarnet.sdk.util;

import com.kekwy.iarnet.sdk.function.InputFunction;
import com.kekwy.iarnet.sdk.function.TaskFunction;
import com.kekwy.iarnet.sdk.function.UnionFunction;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Optional;

/**
 * 从函数对象中提取输出类型的工具类。
 * <p>
 * 支持接口：{@link InputFunction}、{@link TaskFunction}、{@link UnionFunction}。
 * <p>
 * 推断优先级：
 * <ol>
 *   <li>具名类 / 匿名内部类：通过 {@code getGenericInterfaces()} / {@code getGenericSuperclass()} 提取泛型参数</li>
 *   <li>Lambda：通过 {@link SerializedLambda} 提取实现方法的返回类型；{@link InputFunction} 的 {@code next()} 返回 {@link Optional}{@code <O>}，会自动解包得到 O</li>
 *   <li>推断失败（如 lambda 返回类型被擦除为 Object）：返回 {@code null}，需用户显式调用 {@code .returns(TypeToken)}</li>
 * </ol>
 */
public final class TypeExtractor {

    private TypeExtractor() {
    }

    /**
     * 从函数对象中提取输出类型。
     *
     * @param function            函数对象（InputFunction / TaskFunction / UnionFunction）
     * @param functionalInterface 目标函数式接口的 Class（如 InputFunction.class、TaskFunction.class、UnionFunction.class）
     * @param typeArgIndex        要提取的泛型参数索引：InputFunction 的 O 为 0，TaskFunction 的 O 为 1，UnionFunction 的 V 为 2
     * @return 提取到的 Type，推断失败时返回 null
     */
    public static Type extractOutputType(Object function, Class<?> functionalInterface, int typeArgIndex) {
        // 1. 尝试从 getGenericInterfaces() / getGenericSuperclass() 提取（具名类 / 匿名内部类）
        Type fromInterfaces = extractFromGenericInterfaces(function, functionalInterface, typeArgIndex);
        if (fromInterfaces != null) {
            return fromInterfaces;
        }

        // 2. 尝试从 SerializedLambda 提取（lambda）
        return extractFromSerializedLambda(function, functionalInterface);
    }

    /**
     * 通过 getGenericInterfaces() / getGenericSuperclass() 提取泛型参数。
     * 对具名类和匿名内部类有效，对 lambda 无效（返回擦除后的 raw type）。
     */
    private static Type extractFromGenericInterfaces(Object function, Class<?> targetInterface, int typeArgIndex) {
        for (Type iface : function.getClass().getGenericInterfaces()) {
            Type extracted = extractTypeArgFromParameterizedType(iface, targetInterface, typeArgIndex);
            if (extracted != null) {
                return extracted;
            }
        }

        Type superClass = function.getClass().getGenericSuperclass();
        if (superClass != null) {
            return extractTypeArgFromParameterizedType(superClass, targetInterface, typeArgIndex);
        }

        return null;
    }

    private static Type extractTypeArgFromParameterizedType(Type type, Class<?> targetInterface, int typeArgIndex) {
        if (!(type instanceof ParameterizedType pt)) {
            return null;
        }
        Type rawType = pt.getRawType();
        if (!(rawType instanceof Class<?> rawClass) || !targetInterface.isAssignableFrom(rawClass)) {
            return null;
        }
        Type[] args = pt.getActualTypeArguments();
        if (args.length <= typeArgIndex) {
            return null;
        }
        Type arg = args[typeArgIndex];
        return (arg instanceof TypeVariable) ? null : arg;
    }

    /**
     * 通过 SerializedLambda 提取 lambda 实现方法的返回类型。
     * 对于 InputFunction，{@code next()} 返回 {@link Optional}{@code <O>}，会解包得到 O。
     */
    private static Type extractFromSerializedLambda(Object function, Class<?> functionalInterface) {
        if (!(function instanceof Serializable)) {
            return null;
        }

        try {
            Method writeReplace = function.getClass().getDeclaredMethod("writeReplace");
            writeReplace.setAccessible(true);
            Object replacement = writeReplace.invoke(function);

            if (!(replacement instanceof SerializedLambda sl)) {
                return null;
            }

            String implClassName = sl.getImplClass().replace('/', '.');
            String implMethodName = sl.getImplMethodName();

            Class<?> implClass = Class.forName(implClassName);
            Method implMethod = findImplMethod(implClass, implMethodName);

            if (implMethod == null) {
                return null;
            }

            Type returnType = implMethod.getGenericReturnType();

            // 类型擦除（如 lambda 返回泛型）时无法推断
            if (returnType == Object.class) {
                return null;
            }

            // InputFunction.next() 返回 Optional<O>，需解包得到 O
            if (functionalInterface == InputFunction.class && returnType instanceof ParameterizedType pt) {
                if (pt.getRawType() == Optional.class) {
                    Type[] args = pt.getActualTypeArguments();
                    if (args.length > 0 && !(args[0] instanceof TypeVariable)) {
                        return args[0];
                    }
                    return null;
                }
            }

            return returnType;
        } catch (ReflectiveOperationException | SecurityException e) {
            return null;
        }
    }

    private static Method findImplMethod(Class<?> implClass, String methodName) {
        for (Class<?> c = implClass; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(methodName)) {
                    return m;
                }
            }
        }
        return null;
    }
}
