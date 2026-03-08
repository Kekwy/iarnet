package com.kekwy.iarnet.actor;

import com.kekwy.iarnet.proto.ir.DataType;
import com.kekwy.iarnet.proto.ir.Row;
import com.kekwy.iarnet.proto.ir.TypeKind;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.lang.reflect.Field;
import java.util.*;

/**
 * 根据 {@link Row} 中的 {@link DataType} 以及用户函数入参类型，
 * 通过反射手动构造 Java 对象。
 * <p>
 * 编码约定（与 API 侧 RowEncoder 对称）：
 * <ul>
 *   <li>STRING  → DataOutput.writeUTF</li>
 *   <li>INT32   → DataOutput.writeInt</li>
 *   <li>INT64   → DataOutput.writeLong</li>
 *   <li>DOUBLE  → DataOutput.writeDouble</li>
 *   <li>BOOLEAN → DataOutput.writeBoolean</li>
 *   <li>ARRAY   → writeInt(count) + 逐元素递归</li>
 *   <li>MAP     → writeInt(count) + 逐 key/value 递归</li>
 *   <li>STRUCT  → 按 DataType.fields 顺序逐字段递归</li>
 * </ul>
 */
public final class UpstreamRowDeserializer {

    /**
     * 将 {@link Row} 反序列化为 Java 对象。
     * <p>
     * 对于原始类型，直接返回对应的 Java 包装类型；
     * 对于 STRUCT，根据 {@code targetType} 通过反射创建实例并填充字段。
     *
     * @param row        来自 LocalAgentMessage.getRowDelivery() 的 Row
     * @param targetType 用户函数入参类型；为 null 时 STRUCT 返回 {@code Map<String,Object>}
     * @return 反序列化后的 Java 对象
     */
    public static Object toObject(Row row, Class<?> targetType) {
        if (row == null || row.getValue().isEmpty()) {
            return null;
        }
        DataType dataType = row.getDataType();
        if (dataType == null || dataType.getKind() == TypeKind.TYPE_KIND_UNSPECIFIED) {
            throw new IllegalArgumentException("Row 必须携带 data_type，与 DSL 约定一致；当前 row 的 data_type 为空或 UNSPECIFIED");
        }
        byte[] bytes = row.getValue().toByteArray();
        try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(bytes))) {
            return readValue(dis, dataType, targetType);
        } catch (Exception e) {
            throw new IllegalStateException("Row 反序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 不指定目标类型的便捷方法。
     * 原始类型返回对应 Java 包装类型，STRUCT 返回 {@code Map<String,Object>}。
     */
    public static Object toObject(Row row) {
        return toObject(row, null);
    }

    // ======================== 递归读取 ========================

    private static Object readValue(DataInputStream dis, DataType dataType, Class<?> targetType)
            throws Exception {
        if (dataType == null || dataType.getKind() == TypeKind.TYPE_KIND_UNSPECIFIED) {
            return null;
        }
        return switch (dataType.getKind()) {
            case STRING  -> dis.readUTF();
            case INT32   -> dis.readInt();
            case INT64   -> dis.readLong();
            case DOUBLE  -> dis.readDouble();
            case BOOLEAN -> dis.readBoolean();
            case ARRAY   -> readArray(dis, dataType);
            case MAP     -> readMap(dis, dataType);
            case STRUCT  -> readStruct(dis, dataType, targetType);
            default      -> null;
        };
    }

    private static List<Object> readArray(DataInputStream dis, DataType dataType) throws Exception {
        int count = dis.readInt();
        DataType elementType = dataType.getElementType();
        List<Object> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(readValue(dis, elementType, null));
        }
        return list;
    }

    private static Map<Object, Object> readMap(DataInputStream dis, DataType dataType) throws Exception {
        int count = dis.readInt();
        DataType keyType = dataType.getKeyType();
        DataType valueType = dataType.getValueType();
        Map<Object, Object> map = new LinkedHashMap<>(count);
        for (int i = 0; i < count; i++) {
            Object k = readValue(dis, keyType, null);
            Object v = readValue(dis, valueType, null);
            map.put(k, v);
        }
        return map;
    }

    private static Object readStruct(DataInputStream dis, DataType dataType, Class<?> targetType)
            throws Exception {
        List<com.kekwy.iarnet.proto.ir.Field> protoFields = dataType.getFieldsList();

        if (targetType == null || targetType == Object.class || Map.class.isAssignableFrom(targetType)) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (com.kekwy.iarnet.proto.ir.Field f : protoFields) {
                map.put(f.getName(), readValue(dis, f.getType(), null));
            }
            return map;
        }

        Object instance = targetType.getDeclaredConstructor().newInstance();
        for (com.kekwy.iarnet.proto.ir.Field protoField : protoFields) {
            String fieldName = protoField.getName();
            DataType fieldDataType = protoField.getType();
            try {
                Field javaField = targetType.getDeclaredField(fieldName);
                javaField.setAccessible(true);
                Class<?> fieldJavaType = javaField.getType();
                Object fieldValue = readValue(dis, fieldDataType, fieldJavaType);
                javaField.set(instance, adaptPrimitive(fieldValue, fieldJavaType));
            } catch (NoSuchFieldException e) {
                readValue(dis, fieldDataType, null);
            }
        }
        return instance;
    }

    // ======================== 原始类型适配 ========================

    /**
     * 在数值类型之间做必要的窄化/宽化转换，
     * 例如 proto INT32 解出 Integer，但目标字段是 long。
     */
    private static Object adaptPrimitive(Object value, Class<?> targetType) {
        if (value == null || targetType.isInstance(value)) {
            return value;
        }
        if (value instanceof Number num) {
            if (targetType == int.class    || targetType == Integer.class) return num.intValue();
            if (targetType == long.class   || targetType == Long.class)   return num.longValue();
            if (targetType == double.class || targetType == Double.class) return num.doubleValue();
            if (targetType == float.class  || targetType == Float.class)  return num.floatValue();
            if (targetType == short.class  || targetType == Short.class)  return num.shortValue();
            if (targetType == byte.class   || targetType == Byte.class)   return num.byteValue();
        }
        if (value instanceof String str && (targetType == boolean.class || targetType == Boolean.class)) {
            return Boolean.parseBoolean(str);
        }
        return value;
    }

    private UpstreamRowDeserializer() {}
}
