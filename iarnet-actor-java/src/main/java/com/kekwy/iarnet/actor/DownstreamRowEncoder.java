package com.kekwy.iarnet.actor;

import com.kekwy.iarnet.proto.ir.DataType;
import com.kekwy.iarnet.proto.ir.TypeKind;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 将算子输出对象按 {@link DataType} 编码为 DataOutput 格式字节，与 {@link UpstreamRowDeserializer} 及 API 侧 Row 编码约定对称。
 * <p>
 * 当前仅支持原始类型（STRING, INT32, INT64, DOUBLE, BOOLEAN），用于算子返回值向下游发送时携带 data_type。
 */
public final class DownstreamRowEncoder {

    /**
     * 根据 Java 类型推断 proto DataType（仅支持原始类型与 String）。
     *
     * @param clazz 返回值类型
     * @return 对应的 proto DataType；若无法推断则返回 null
     */
    public static DataType dataTypeFromClass(Class<?> clazz) {
        if (clazz == null) return null;
        if (clazz == String.class) return DataType.newBuilder().setKind(TypeKind.STRING).build();
        if (clazz == int.class || clazz == Integer.class) return DataType.newBuilder().setKind(TypeKind.INT32).build();
        if (clazz == long.class || clazz == Long.class) return DataType.newBuilder().setKind(TypeKind.INT64).build();
        if (clazz == double.class || clazz == Double.class) return DataType.newBuilder().setKind(TypeKind.DOUBLE).build();
        if (clazz == boolean.class || clazz == Boolean.class) return DataType.newBuilder().setKind(TypeKind.BOOLEAN).build();
        if (clazz == float.class || clazz == Float.class) return DataType.newBuilder().setKind(TypeKind.DOUBLE).build();
        if (clazz == short.class || clazz == Short.class || clazz == byte.class || clazz == Byte.class) {
            return DataType.newBuilder().setKind(TypeKind.INT32).build();
        }
        return null;
    }

    /**
     * 将对象按给定 DataType 编码为 DataOutput 格式字节。
     *
     * @param value    算子返回值（可为 null）
     * @param dataType 必须为原始类型（STRING/INT32/INT64/DOUBLE/BOOLEAN）
     * @return 编码后的字节，下游可用 UpstreamRowDeserializer 按同一 data_type 反序列化
     */
    public static byte[] encode(Object value, DataType dataType) throws IOException {
        if (dataType == null || dataType.getKind() == TypeKind.TYPE_KIND_UNSPECIFIED) {
            throw new IllegalArgumentException("Row 必须携带 data_type；encode 时 dataType 不能为空或 UNSPECIFIED");
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(bos)) {
            writeValue(dos, value, dataType);
        }
        return bos.toByteArray();
    }

    private static void writeValue(DataOutputStream dos, Object value, DataType dataType) throws IOException {
        switch (dataType.getKind()) {
            case STRING -> dos.writeUTF(value != null ? value.toString() : "");
            case INT32 -> dos.writeInt(value instanceof Number n ? n.intValue() : 0);
            case INT64 -> dos.writeLong(value instanceof Number n ? n.longValue() : 0L);
            case DOUBLE -> dos.writeDouble(value instanceof Number n ? n.doubleValue() : 0.0);
            case BOOLEAN -> dos.writeBoolean(value instanceof Boolean b && b);
            // case ARRAY->
            // case MAP->
            // case STRUCT->
            default -> throw new UnsupportedOperationException("DownstreamRowEncoder 暂仅支持原始类型，当前 kind=" + dataType.getKind());
        }
    }

    private DownstreamRowEncoder() {}
}
