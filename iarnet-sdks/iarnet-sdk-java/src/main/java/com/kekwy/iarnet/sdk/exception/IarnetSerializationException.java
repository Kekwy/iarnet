package com.kekwy.iarnet.sdk.exception;

/**
 * 序列化或反序列化失败异常。
 * <p>
 * 典型场景：Java 函数对象序列化时，捕获的变量未实现 {@link java.io.Serializable}；
 * 反序列化时类找不到或版本不兼容。需检查函数闭包中的对象是否可序列化，
 * 或确保运行时的 ClassLoader 能正确加载相关类。
 */
public class IarnetSerializationException extends IarnetException {

    /**
     * @param message 错误描述
     */
    public IarnetSerializationException(String message) {
        super(message);
    }

    /**
     * @param message 错误描述
     * @param cause   根因（如 {@link java.io.IOException}、{@link ClassNotFoundException}）
     */
    public IarnetSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
