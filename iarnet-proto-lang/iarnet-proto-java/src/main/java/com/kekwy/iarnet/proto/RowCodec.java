package com.kekwy.iarnet.proto;

import com.google.protobuf.ByteString;
import com.kekwy.iarnet.proto.ir.DataType;
import com.kekwy.iarnet.proto.ir.Row;
import com.kekwy.iarnet.proto.ir.TypeKind;

import java.io.*;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Java 对象 ↔ {@link Row} 的编解码器。
 *
 * <h3>编码（encode）</h3>
 * <pre>{@code
 * Row row = RowCodec.encode("hello");               // 自动推断 DataType
 * Row row = RowCodec.encode(myPojo);                 // POJO → STRUCT
 * Row row = RowCodec.encode(list, DataTypes.array(DataTypes.INT32)); // 显式指定 DataType
 * }</pre>
 *
 * <h3>解码（decode）</h3>
 * <pre>{@code
 * String s   = RowCodec.decode(row, String.class);
 * MyPojo obj = RowCodec.decode(row, MyPojo.class);   // STRUCT → POJO
 * Object raw = RowCodec.decode(row);                 // STRUCT 解码为 Map<String,Object>
 * }</pre>
 *
 * <h3>二进制格式</h3>
 * <ul>
 *   <li>STRING  — 4-byte length (big-endian) + UTF-8 bytes</li>
 *   <li>INT32   — 4 bytes big-endian</li>
 *   <li>INT64   — 8 bytes big-endian</li>
 *   <li>DOUBLE  — 8 bytes (IEEE 754, big-endian)</li>
 *   <li>BOOLEAN — 1 byte (0 / 1)</li>
 *   <li>ARRAY   — 4-byte count + elements</li>
 *   <li>MAP     — 4-byte count + key-value pairs</li>
 *   <li>STRUCT  — fields in {@link DataType#getFieldsList()} order</li>
 * </ul>
 */
public final class RowCodec {

    private RowCodec() {}

    /* ================================================================
     *  Encode
     * ================================================================ */

    /**
     * 编码 Java 对象，自动推断 {@link DataType}。
     * <p>
     * 支持 {@code String / Integer / Long / Double / Boolean / List / Map} 以及 POJO（映射为 STRUCT）。
     * {@code Float} 提升为 DOUBLE，{@code Short / Byte} 提升为 INT32。
     */
    public static Row encode(Object value) {
        Objects.requireNonNull(value, "value must not be null");
        DataType dataType = inferDataType(value);
        return encode(value, dataType);
    }

    /**
     * 使用显式 {@link DataType} 编码 Java 对象。
     */
    public static Row encode(Object value, DataType dataType) {
        Objects.requireNonNull(value, "value must not be null");
        Objects.requireNonNull(dataType, "dataType must not be null");
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(buf);
            writeValue(out, value, dataType);
            out.flush();
            return Row.newBuilder()
                    .setValue(ByteString.copyFrom(buf.toByteArray()))
                    .setDataType(dataType)
                    .build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /* ================================================================
     *  Decode
     * ================================================================ */

    /**
     * 解码 {@link Row} 为 Java 对象。
     * <p>
     * STRUCT 类型解码为 {@code Map<String, Object>}；如需还原为 POJO，请使用 {@link #decode(Row, Class)}。
     */
    public static Object decode(Row row) {
        Objects.requireNonNull(row, "row must not be null");
        try {
            DataInputStream in = new DataInputStream(
                    new ByteArrayInputStream(row.getValue().toByteArray()));
            return readValue(in, row.getDataType());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 解码 {@link Row} 为指定 Java 类型。
     * <p>
     * 当 {@code DataType.kind == STRUCT} 且目标不是 {@code Map} 时，
     * 会通过反射将字段值注入 POJO（要求无参构造器）。
     */
    @SuppressWarnings("unchecked")
    public static <T> T decode(Row row, Class<T> clazz) {
        Objects.requireNonNull(row, "row must not be null");
        Objects.requireNonNull(clazz, "clazz must not be null");
        Object value = decode(row);
        if (clazz.isInstance(value)) {
            return clazz.cast(value);
        }
        if (row.getDataType().getKind() == TypeKind.STRUCT && value instanceof Map) {
            return mapToPojo((Map<String, Object>) value, clazz);
        }
        throw new IllegalArgumentException(
                "Cannot convert " + value.getClass().getName() + " to " + clazz.getName());
    }

    /* ================================================================
     *  DataType inference (from object value)
     * ================================================================ */

    /**
     * 根据 Java 对象推断 {@link DataType}。
     */
    public static DataType inferDataType(Object value) {
        Objects.requireNonNull(value, "value must not be null");

        if (value instanceof String)                        return DataTypes.STRING;
        if (value instanceof Integer)                       return DataTypes.INT32;
        if (value instanceof Short || value instanceof Byte) return DataTypes.INT32;
        if (value instanceof Long)                          return DataTypes.INT64;
        if (value instanceof Double || value instanceof Float) return DataTypes.DOUBLE;
        if (value instanceof Boolean)                       return DataTypes.BOOLEAN;

        if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                return DataTypes.array(DataTypes.STRING);
            }
            return DataTypes.array(inferDataType(list.get(0)));
        }
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                return DataTypes.map(DataTypes.STRING, DataTypes.STRING);
            }
            Map.Entry<?, ?> first = map.entrySet().iterator().next();
            return DataTypes.map(
                    inferDataType(first.getKey()),
                    inferDataType(first.getValue()));
        }
        return inferStructType(value);
    }

    private static DataType inferStructType(Object obj) {
        DataType.Builder b = DataType.newBuilder().setKind(TypeKind.STRUCT);
        for (java.lang.reflect.Field f : obj.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())
                    || Modifier.isTransient(f.getModifiers())
                    || f.isSynthetic()) {
                continue;
            }
            f.setAccessible(true);
            try {
                Object fv = f.get(obj);
                DataType ft = (fv != null) ? inferDataType(fv) : DataTypes.STRING;
                b.addFields(com.kekwy.iarnet.proto.ir.Field.newBuilder()
                        .setName(f.getName())
                        .setType(ft));
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Cannot access field: " + f.getName(), e);
            }
        }
        return b.build();
    }

    /* ================================================================
     *  Binary write
     * ================================================================ */

    private static void writeValue(DataOutputStream out, Object value, DataType type)
            throws IOException {
        switch (type.getKind()) {
            case STRING  -> writeString(out, (String) value);
            case INT32   -> out.writeInt(((Number) value).intValue());
            case INT64   -> out.writeLong(((Number) value).longValue());
            case DOUBLE  -> out.writeDouble(((Number) value).doubleValue());
            case BOOLEAN -> out.writeBoolean((Boolean) value);
            case ARRAY   -> writeArray(out, (List<?>) value, type);
            case MAP     -> writeMap(out, (Map<?, ?>) value, type);
            case STRUCT  -> writeStruct(out, value, type);
            default -> throw new IllegalArgumentException("Unsupported TypeKind: " + type.getKind());
        }
    }

    private static void writeString(DataOutputStream out, String s) throws IOException {
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static void writeArray(DataOutputStream out, List<?> list, DataType type)
            throws IOException {
        DataType elemType = type.getElementType();
        out.writeInt(list.size());
        for (Object elem : list) {
            writeValue(out, elem, elemType);
        }
    }

    private static void writeMap(DataOutputStream out, Map<?, ?> map, DataType type)
            throws IOException {
        DataType keyType = type.getKeyType();
        DataType valType = type.getValueType();
        out.writeInt(map.size());
        for (Map.Entry<?, ?> e : map.entrySet()) {
            writeValue(out, e.getKey(), keyType);
            writeValue(out, e.getValue(), valType);
        }
    }

    private static void writeStruct(DataOutputStream out, Object value, DataType type)
            throws IOException {
        if (value instanceof Map<?, ?> map) {
            for (com.kekwy.iarnet.proto.ir.Field field : type.getFieldsList()) {
                writeValue(out, map.get(field.getName()), field.getType());
            }
        } else {
            Class<?> clazz = value.getClass();
            for (com.kekwy.iarnet.proto.ir.Field field : type.getFieldsList()) {
                try {
                    java.lang.reflect.Field jf = clazz.getDeclaredField(field.getName());
                    jf.setAccessible(true);
                    writeValue(out, jf.get(value), field.getType());
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    throw new IOException(
                            "Cannot read field '" + field.getName() + "' from " + clazz.getName(), e);
                }
            }
        }
    }

    /* ================================================================
     *  Binary read
     * ================================================================ */

    private static Object readValue(DataInputStream in, DataType type) throws IOException {
        return switch (type.getKind()) {
            case STRING  -> readString(in);
            case INT32   -> in.readInt();
            case INT64   -> in.readLong();
            case DOUBLE  -> in.readDouble();
            case BOOLEAN -> in.readBoolean();
            case ARRAY   -> readArray(in, type);
            case MAP     -> readMap(in, type);
            case STRUCT  -> readStruct(in, type);
            default -> throw new IllegalArgumentException("Unsupported TypeKind: " + type.getKind());
        };
    }

    private static String readString(DataInputStream in) throws IOException {
        int len = in.readInt();
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static List<Object> readArray(DataInputStream in, DataType type) throws IOException {
        int size = in.readInt();
        DataType elemType = type.getElementType();
        List<Object> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(readValue(in, elemType));
        }
        return list;
    }

    private static Map<Object, Object> readMap(DataInputStream in, DataType type) throws IOException {
        int size = in.readInt();
        DataType keyType = type.getKeyType();
        DataType valType = type.getValueType();
        Map<Object, Object> map = new LinkedHashMap<>(size);
        for (int i = 0; i < size; i++) {
            map.put(readValue(in, keyType), readValue(in, valType));
        }
        return map;
    }

    private static Map<String, Object> readStruct(DataInputStream in, DataType type)
            throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        for (com.kekwy.iarnet.proto.ir.Field field : type.getFieldsList()) {
            map.put(field.getName(), readValue(in, field.getType()));
        }
        return map;
    }

    /* ================================================================
     *  Map → POJO
     * ================================================================ */

    private static <T> T mapToPojo(Map<String, Object> map, Class<T> clazz) {
        try {
            T instance = clazz.getDeclaredConstructor().newInstance();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                try {
                    java.lang.reflect.Field f = clazz.getDeclaredField(entry.getKey());
                    f.setAccessible(true);
                    f.set(instance, coerce(entry.getValue(), f.getType()));
                } catch (NoSuchFieldException ignored) {
                    // 跳过 DataType 中存在但 POJO 中不存在的字段
                }
            }
            return instance;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Cannot instantiate " + clazz.getName(), e);
        }
    }

    /**
     * 数值类型窄化 / 宽化适配，使解码值能赋给目标字段。
     */
    private static Object coerce(Object value, Class<?> target) {
        if (value == null || target.isInstance(value)) {
            return value;
        }
        if (value instanceof Number num) {
            if (target == int.class     || target == Integer.class) return num.intValue();
            if (target == long.class    || target == Long.class)    return num.longValue();
            if (target == double.class  || target == Double.class)  return num.doubleValue();
            if (target == float.class   || target == Float.class)   return num.floatValue();
            if (target == short.class   || target == Short.class)   return num.shortValue();
            if (target == byte.class    || target == Byte.class)    return num.byteValue();
        }
        return value;
    }
}
