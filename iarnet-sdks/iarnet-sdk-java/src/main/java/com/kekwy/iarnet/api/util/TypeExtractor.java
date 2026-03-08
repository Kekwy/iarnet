package com.kekwy.iarnet.api.util;

import com.kekwy.iarnet.api.function.Function;
import com.kekwy.iarnet.api.function.Function.PythonFunction;

import java.io.Serializable;
import java.lang.invoke.SerializedLambda;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

/**
 * 从函数对象中提取返回类型的工具类。
 * <p>
 * 推断优先级：
 * <ol>
 *   <li>PythonFunction：直接从 {@link PythonFunction#returnType()} 获取</li>
 *   <li>具名类 / 匿名内部类：通过 {@code getGenericInterfaces()} 提取泛型参数</li>
 *   <li>Lambda（简单返回类型）：通过 {@link SerializedLambda} 提取实现方法的返回类型</li>
 *   <li>Lambda（泛型返回类型）：推断失败，返回 {@code null}，需用户显式调用 {@code .returns()}</li>
 * </ol>
 */
public final class TypeExtractor {

    private TypeExtractor() {
    }

    /**
     * 从函数对象中提取输出类型。
     *
     * @param function           函数对象（MapFunction / FlatMapFunction / FilterFunction）
     * @param functionalInterface 目标函数式接口的 Class（如 MapFunction.class）
     * @param typeArgIndex       要提取的泛型参数索引（MapFunction 的 R 是索引 1，FilterFunction 无输出泛型）
     * @return 提取到的 Type，推断失败时返回 null
     */
    public static Type extractOutputType(Object function, Class<?> functionalInterface, int typeArgIndex) {
        // 1. PythonFunction：信息已完备
        if (function instanceof PythonFunction pf) {
            return pf.returnType();
        }

        // 2. 尝试从 getGenericInterfaces() 提取（具名类 / 匿名内部类）
        Type fromInterfaces = extractFromGenericInterfaces(function, functionalInterface, typeArgIndex);
        if (fromInterfaces != null) {
            return fromInterfaces;
        }

        // 3. 尝试从 SerializedLambda 提取（lambda 简单返回类型）
        return extractFromSerializedLambda(function);
    }

    /**
     * 通过 getGenericInterfaces() 提取泛型参数。
     * 对具名类和匿名内部类有效，对 lambda 无效（返回擦除后的 raw type）。
     */
    private static Type extractFromGenericInterfaces(Object function, Class<?> targetInterface, int typeArgIndex) {
        for (Type iface : function.getClass().getGenericInterfaces()) {
            if (iface instanceof ParameterizedType pt) {
                if (targetInterface.isAssignableFrom((Class<?>) pt.getRawType())) {
                    Type[] args = pt.getActualTypeArguments();
                    if (args.length > typeArgIndex) {
                        Type arg = args[typeArgIndex];
                        if (!(arg instanceof TypeVariable)) {
                            return arg;
                        }
                    }
                }
            }
        }

        // 继续向上查找父类链（处理 class A extends B implements MapFunction<S,R> 的情况）
        Type superClass = function.getClass().getGenericSuperclass();
        if (superClass instanceof ParameterizedType pt) {
            Type rawType = pt.getRawType();
            if (rawType instanceof Class<?> rawClass && targetInterface.isAssignableFrom(rawClass)) {
                Type[] args = pt.getActualTypeArguments();
                if (args.length > typeArgIndex) {
                    Type arg = args[typeArgIndex];
                    if (!(arg instanceof TypeVariable)) {
                        return arg;
                    }
                }
            }
        }

        return null;
    }

    /**
     * 通过 SerializedLambda 提取 lambda 实现方法的返回类型。
     * 仅对返回具体 Class（非泛型）的 lambda 有效。
     */
    private static Type extractFromSerializedLambda(Object function) {
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

            // 如果返回的是 Object.class，说明类型被擦除了，推断失败
            if (returnType == Object.class) {
                return null;
            }

            return returnType;
        } catch (ReflectiveOperationException | SecurityException e) {
            return null;
        }
    }

    private static Method findImplMethod(Class<?> implClass, String methodName) {
        for (Method m : implClass.getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                return m;
            }
        }
        return null;
    }
}
