package com.kekwy.iarnet.api.util;

import java.io.*;

/**
 * Java 对象序列化 / 反序列化工具，用于将 lambda 或函数对象转为 byte[] 存入 OperatorNode。
 */
public final class SerializationUtil {

    private SerializationUtil() {
    }

    public static byte[] serialize(Serializable obj) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            oos.flush();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to serialize function: " + obj.getClass().getName()
                            + ". All captured variables must be Serializable.", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T deserialize(byte[] bytes, ClassLoader classLoader) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream ois = classLoader != null
                     ? new ClassLoaderObjectInputStream(bis, classLoader)
                     : new ObjectInputStream(bis)) {
            return (T) ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Failed to deserialize function.", e);
        }
    }

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
