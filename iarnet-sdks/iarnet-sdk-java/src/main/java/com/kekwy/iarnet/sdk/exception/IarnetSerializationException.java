package com.kekwy.iarnet.sdk.exception;

/**
 * 序列化或反序列化失败异常。
 */
public class IarnetSerializationException extends IarnetException {

    public IarnetSerializationException(String message) {
        super(message);
    }

    public IarnetSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
