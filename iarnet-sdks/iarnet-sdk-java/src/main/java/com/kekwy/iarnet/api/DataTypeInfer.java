package com.kekwy.iarnet.api;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 基于 {@link Type} 的类型推断器，支持泛型、嵌套结构及循环引用防护。
 * <p>
 * 核心设计（参考 Flink TypeInformation）：
 * <ul>
 *   <li>泛型信息必须在 DSL 层通过 {@link Type} 保留，运行时 {@code Object.getClass()} 无法恢复</li>
 *   <li>优先使用 {@link #infer(Type)} 或 {@link TypeToken} 传入完整类型</li>
 *   <li>字段分析使用 {@code field.getGenericType()} 而非 {@code field.getType()}</li>
 * </ul>
 */
public class DataTypeInfer {

    /**
     * 根据 {@link TypeToken} 推断，用法：{@code infer(new TypeToken<List<String>>() {})}
     */
    public static DataType infer(TypeToken<?> token) {
        return infer(token.getType());
    }

    /**
     * 根据 {@link Type} 推断 {@link DataType}（推荐入口）。
     * 支持 {@link ParameterizedType}、嵌套 {@code List<Map<String,List<User>>>} 等。
     */
    public static DataType infer(Type type) {
        if (type == null) {
            throw new IllegalArgumentException("Cannot infer DataType from null Type");
        }
        return infer(type, new HashMap<>());
    }

    /**
     * 根据运行时对象推断。泛型已擦除，{@code List<String>} 会退化为 {@code ArrayType(STRING)}。
     * 若需保留泛型，请使用 {@link #infer(Type)} 或 {@link TypeToken}。
     */
    public static DataType infer(Object o) {
        if (o == null) {
            throw new IllegalArgumentException("Cannot infer DataType from null");
        }
        return infer(o.getClass(), new HashMap<>());
    }

    /**
     * 基于 Type 进行推断，通过 cache 避免循环引用导致的死循环。
     */
    private static DataType infer(Type type, Map<Type, DataType> cache) {
        if (type == null) {
            return PrimitiveType.STRING;
        }

        DataType cached = cache.get(type);
        if (cached != null) {
            return cached;
        }

        // --- 1. ParameterizedType: List<T>, Map<K,V>, Set<T>, Collection<T> ---
        if (type instanceof ParameterizedType) {
            return inferParameterizedType((ParameterizedType) type, cache);
        }

        // --- 2. GenericArrayType: T[] ---
        if (type instanceof GenericArrayType gat) {
            DataType componentType = infer(gat.getGenericComponentType(), cache);
            return new ArrayType(componentType);
        }

        // --- 3. TypeVariable: class Foo<T> ---
        if (type instanceof TypeVariable) {
            return inferTypeVariable((TypeVariable<?>) type, cache);
        }

        // --- 4. WildcardType: ? extends X, ? super Y ---
        if (type instanceof WildcardType) {
            return inferWildcardType((WildcardType) type, cache);
        }

        // --- 5. Class: 原始类型、数组、枚举、自定义类 ---
        if (type instanceof Class<?>) {
            return inferClass((Class<?>) type, cache);
        }

        return PrimitiveType.STRING;
    }

    private static DataType inferParameterizedType(ParameterizedType pt, Map<Type, DataType> cache) {
        Type rawType = pt.getRawType();
        Type[] args = pt.getActualTypeArguments();

        if (!(rawType instanceof Class<?>)) {
            return PrimitiveType.STRING;
        }

        Class<?> rawClass = (Class<?>) rawType;

        if (List.class.isAssignableFrom(rawClass) || Collection.class.isAssignableFrom(rawClass)
                || Set.class.isAssignableFrom(rawClass)) {
            if (args.length >= 1) {
                DataType elementType = infer(args[0], cache);
                return new ArrayType(elementType);
            }
            return new ArrayType(PrimitiveType.STRING);
        }

        if (Map.class.isAssignableFrom(rawClass)) {
            if (args.length >= 2) {
                DataType keyType = infer(args[0], cache);
                DataType valueType = infer(args[1], cache);
                return new MapType(keyType, valueType);
            }
            return new MapType(PrimitiveType.STRING, PrimitiveType.STRING);
        }

        // 其他 ParameterizedType（如 Optional<T>）按 raw 类型处理
        return inferClass(rawClass, cache);
    }

    private static DataType inferTypeVariable(TypeVariable<?> tv, Map<Type, DataType> cache) {
        Type[] bounds = tv.getBounds();
        if (bounds != null && bounds.length > 0) {
            Type bound = bounds[0];
            if (!(bound instanceof Class<?>) || ((Class<?>) bound) != Object.class) {
                return infer(bound, cache);
            }
        }
        return PrimitiveType.STRING;
    }

    private static DataType inferWildcardType(WildcardType wt, Map<Type, DataType> cache) {
        Type[] upper = wt.getUpperBounds();
        if (upper != null && upper.length > 0) {
            Type bound = upper[0];
            if (!(bound instanceof Class<?>) || ((Class<?>) bound) != Object.class) {
                return infer(bound, cache);
            }
        }
        Type[] lower = wt.getLowerBounds();
        if (lower != null && lower.length > 0) {
            return infer(lower[0], cache);
        }
        return PrimitiveType.STRING;
    }

    private static DataType inferClass(Class<?> clazz, Map<Type, DataType> cache) {
        if (clazz == null) {
            return PrimitiveType.STRING;
        }

        DataType cached = cache.get(clazz);
        if (cached != null) {
            return cached;
        }

        // --- 原始 / 包装 / 常用简单类型 ---
        if (clazz == String.class) {
            return PrimitiveType.STRING;
        }
        if (clazz == int.class || clazz == Integer.class
                || clazz == short.class || clazz == Short.class
                || clazz == byte.class || clazz == Byte.class) {
            return PrimitiveType.INT32;
        }
        if (clazz == long.class || clazz == Long.class) {
            return PrimitiveType.INT64;
        }
        if (clazz == double.class || clazz == Double.class
                || clazz == float.class || clazz == Float.class) {
            return PrimitiveType.DOUBLE;
        }
        if (clazz == boolean.class || clazz == Boolean.class) {
            return PrimitiveType.BOOLEAN;
        }
        if (clazz.isEnum()) {
            return PrimitiveType.STRING;
        }

        // --- 数组 ---
        if (clazz.isArray()) {
            Class<?> componentType = clazz.getComponentType();
            DataType elementType = infer(componentType, cache);
            return new ArrayType(elementType);
        }

        // --- Map / Collection（无泛型信息时）---
        if (Map.class.isAssignableFrom(clazz)) {
            return new MapType(PrimitiveType.STRING, PrimitiveType.STRING);
        }
        if (Collection.class.isAssignableFrom(clazz)) {
            return new ArrayType(PrimitiveType.STRING);
        }

        // --- java.* 包复杂类型 ---
        Package pkg = clazz.getPackage();
        if (pkg != null && pkg.getName().startsWith("java.")) {
            return PrimitiveType.STRING;
        }

        // --- 自定义类：按结构体处理，使用 getGenericType() ---
        List<Field> fields = new ArrayList<>();
        StructType structType = new StructType(fields);
        cache.put(clazz, structType);

        for (java.lang.reflect.Field javaField : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(javaField.getModifiers()) || javaField.isSynthetic()) {
                continue;
            }
            String name = javaField.getName();
            Type genericType = javaField.getGenericType();
            DataType fieldType = infer(genericType, cache);
            fields.add(new Field(name, fieldType));
        }

        return structType;
    }
}
