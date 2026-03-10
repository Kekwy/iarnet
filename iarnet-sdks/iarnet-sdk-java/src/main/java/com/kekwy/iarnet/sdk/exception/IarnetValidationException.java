package com.kekwy.iarnet.sdk.exception;

/**
 * 校验失败异常，如参数非法、类型推断失败、DSL 用法错误等。
 */
public class IarnetValidationException extends IarnetException {

    public IarnetValidationException(String message) {
        super(message);
    }

    public IarnetValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
