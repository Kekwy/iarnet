package com.kekwy.iarnet.sdk.exception;

/**
 * 配置相关异常。
 * <p>
 * 典型场景：环境变量未设置（如 {@code IARNET_APP_ID}、{@code IARNET_GRPC_PORT}）、
 * 端口格式非法、连接参数错误等。通常可通过修正运行环境或配置后重试解决。
 */
public class IarnetConfigurationException extends IarnetException {

    /**
     * @param message 错误描述
     */
    public IarnetConfigurationException(String message) {
        super(message);
    }

    /**
     * @param message 错误描述
     * @param cause   根因（如 {@link NumberFormatException}）
     */
    public IarnetConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
