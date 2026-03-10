package com.kekwy.iarnet.sdk.exception;

/**
 * Iarnet SDK 所有异常的基类。
 * <p>
 * 用户可通过 {@code catch (IarnetException e)} 统一捕获 SDK 抛出的异常；
 * 也可捕获具体子类进行更细粒度的处理。
 */
public class IarnetException extends RuntimeException {

    public IarnetException(String message) {
        super(message);
    }

    public IarnetException(String message, Throwable cause) {
        super(message, cause);
    }
}
