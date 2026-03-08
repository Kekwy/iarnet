package com.kekwy.iarnet.proto;

import com.kekwy.iarnet.proto.ir.DataType;
import com.kekwy.iarnet.proto.ir.Field;
import com.kekwy.iarnet.proto.ir.TypeKind;

import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * 便捷工具：构建 {@link DataType} 实例以及从 Java 类型推断 {@link DataType}。
 *
 * <pre>{@code
 * DataType type = DataTypes.struct(
 *     DataTypes.field("name", DataTypes.STRING),
 *     DataTypes.field("age",  DataTypes.INT32),
 *     DataTypes.field("tags", DataTypes.array(DataTypes.STRING))
 * );
 * }</pre>
 */
public final class DataTypes {

    public static final DataType STRING  = primitive(TypeKind.STRING);
    public static final DataType INT32   = primitive(TypeKind.INT32);
    public static final DataType INT64   = primitive(TypeKind.INT64);
    public static final DataType DOUBLE  = primitive(TypeKind.DOUBLE);
    public static final DataType BOOLEAN = primitive(TypeKind.BOOLEAN);

    private DataTypes() {}

    /* ---------- composite builders ---------- */

    public static DataType array(DataType elementType) {
        return DataType.newBuilder()
                .setKind(TypeKind.ARRAY)
                .setElementType(elementType)
                .build();
    }

    public static DataType map(DataType keyType, DataType valueType) {
        return DataType.newBuilder()
                .setKind(TypeKind.MAP)
                .setKeyType(keyType)
                .setValueType(valueType)
                .build();
    }

    public static DataType struct(Field... fields) {
        DataType.Builder b = DataType.newBuilder().setKind(TypeKind.STRUCT);
        for (Field f : fields) {
            b.addFields(f);
        }
        return b.build();
    }

    public static Field field(String name, DataType type) {
        return Field.newBuilder().setName(name).setType(type).build();
    }

    /* ---------- Java 反射推断 ---------- */

    /**
     * 从 Java {@link Class} 推断 {@link DataType}。
     * <p>
     * 基本类型直接映射；{@code float/Float} 提升为 DOUBLE，{@code short/byte} 提升为 INT32。
     * 不支持裸 {@code List}/{@code Map}（无法推断泛型参数），请使用
     * {@link #fromType(Type)} 或手动构建。
     */
    public static DataType fromClass(Class<?> clazz) {
        if (clazz == String.class)                                       return STRING;
        if (clazz == int.class    || clazz == Integer.class)             return INT32;
        if (clazz == short.class  || clazz == Short.class)               return INT32;
        if (clazz == byte.class   || clazz == Byte.class)                return INT32;
        if (clazz == long.class   || clazz == Long.class)                return INT64;
        if (clazz == double.class || clazz == Double.class)              return DOUBLE;
        if (clazz == float.class  || clazz == Float.class)               return DOUBLE;
        if (clazz == boolean.class || clazz == Boolean.class)            return BOOLEAN;
        if (java.util.List.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException(
                    "Cannot infer element type from raw List; use DataTypes.array() or fromType()");
        }
        if (java.util.Map.class.isAssignableFrom(clazz)) {
            throw new IllegalArgumentException(
                    "Cannot infer key/value types from raw Map; use DataTypes.map() or fromType()");
        }
        return structFromClass(clazz);
    }

    /**
     * 从 Java {@link Type}（含泛型信息）推断 {@link DataType}。
     * <p>
     * 对于 {@code List<String>}、{@code Map<String, Integer>} 等参数化类型，
     * 会递归推断其泛型参数。
     */
    public static DataType fromType(Type type) {
        if (type instanceof Class<?> clazz) {
            return fromClass(clazz);
        }
        if (type instanceof ParameterizedType pt) {
            Class<?> raw = (Class<?>) pt.getRawType();
            Type[] args = pt.getActualTypeArguments();
            if (java.util.List.class.isAssignableFrom(raw)) {
                return array(fromType(args[0]));
            }
            if (java.util.Map.class.isAssignableFrom(raw)) {
                return map(fromType(args[0]), fromType(args[1]));
            }
        }
        throw new IllegalArgumentException("Cannot infer DataType from: " + type);
    }

    /* ---------- internal ---------- */

    private static DataType primitive(TypeKind kind) {
        return DataType.newBuilder().setKind(kind).build();
    }

    private static DataType structFromClass(Class<?> clazz) {
        DataType.Builder b = DataType.newBuilder().setKind(TypeKind.STRUCT);
        for (java.lang.reflect.Field f : clazz.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())
                    || Modifier.isTransient(f.getModifiers())
                    || f.isSynthetic()) {
                continue;
            }
            b.addFields(Field.newBuilder()
                    .setName(f.getName())
                    .setType(fromType(f.getGenericType())));
        }
        return b.build();
    }
}
