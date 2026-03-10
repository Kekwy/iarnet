package com.kekwy.iarnet.sdk.util;

import com.kekwy.iarnet.sdk.exception.IarnetSerializationException;

import java.io.*;

/**
 * Java 对象序列化与反序列化工具。
 * <p>
 * 用于将 Java 实现的 {@link com.kekwy.iarnet.sdk.function.Function}（如 lambda、匿名内部类）
 * 转为 byte[] 存入 {@link com.kekwy.iarnet.proto.workflow.Node}，供运行时在 worker 端反序列化执行。
 * 失败时抛出 {@link com.kekwy.iarnet.sdk.exception.IarnetSerializationException}。
 */
public final class SerializationUtil {

    private SerializationUtil() {
        // 工具类，禁止实例化
    }

    /**
     * 序列化对象为 byte 数组。
     *
     * @param obj 可序列化对象
     * @return 序列化后的字节数组
     * @throws IarnetSerializationException 若序列化失败（如捕获的变量未实现 Serializable）
     */
    public static byte[] serialize(Serializable obj) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            oos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IarnetSerializationException(
                    "Failed to serialize function: " + obj.getClass().getName()
                            + ". All captured variables must be Serializable.", e);
        }
    }

    /**
     * 使用指定 ClassLoader 反序列化，用于 worker 在用户 JAR 的 ClassLoader 下加载类。
     *
     * @param bytes       序列化后的字节数组
     * @param classLoader 用于解析类的 ClassLoader，null 表示使用默认
     * @param <T>         目标类型
     * @return 反序列化后的对象
     * @throws IarnetSerializationException 若反序列化失败
     */
    @SuppressWarnings("unchecked")
    public static <T> T deserialize(byte[] bytes, ClassLoader classLoader) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = classLoader != null
                     ? new ClassLoaderObjectInputStream(bis, classLoader)
                     : new ObjectInputStream(bis)) {
            return (T) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IarnetSerializationException("Failed to deserialize function.", e);
        }
    }

    /**
     * 使用默认 ClassLoader 反序列化。
     *
     * @param bytes 序列化后的字节数组
     * @param <T>   目标类型
     * @return 反序列化后的对象
     * @throws IarnetSerializationException 若反序列化失败
     */
    public static <T> T deserialize(byte[] bytes) {
        return deserialize(bytes, null);
    }

    /**
     * 使用指定 ClassLoader 解析类的 ObjectInputStream，
     * 用于后端在用户 JAR 的 ClassLoader 下反序列化 lambda。
     */
    private static class ClassLoaderObjectInputStream extends ObjectInputStream {
        private final ClassLoader classLoader;

        ClassLoaderObjectInputStream(InputStream in, ClassLoader classLoader) throws IOException {
            super(in);
            this.classLoader = classLoader;
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            try {
                return Class.forName(desc.getName(), false, classLoader);
            } catch (ClassNotFoundException e) {
                return super.resolveClass(desc);
            }
        }
    }
}
