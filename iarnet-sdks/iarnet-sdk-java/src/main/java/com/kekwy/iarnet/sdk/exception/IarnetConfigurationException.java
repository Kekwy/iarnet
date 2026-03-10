package com.kekwy.iarnet.sdk.exception;

/**
 * 配置相关异常，如环境变量未设置、端口格式错误等。
 */
public class IarnetConfigurationException extends IarnetException {

    public IarnetConfigurationException(String message) {
        super(message);
    }

    public IarnetConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
