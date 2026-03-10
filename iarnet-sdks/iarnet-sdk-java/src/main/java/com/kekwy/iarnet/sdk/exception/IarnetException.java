package com.kekwy.iarnet.sdk.exception;

/**
 * Iarnet SDK 所有异常的基类。
 * <p>
 * 继承自 {@link RuntimeException}，调用方无需声明 {@code throws}。
 * 用户可通过 {@code catch (IarnetException e)} 统一捕获 SDK 抛出的异常，
 * 也可捕获具体子类进行更细粒度的处理（如仅重试通信类异常）。
 *
 * @see IarnetConfigurationException  配置错误（环境变量、端口等）
 * @see IarnetValidationException     校验失败（参数、类型、DSL 用法）
 * @see IarnetSerializationException  序列化/反序列化失败
 * @see IarnetSubmissionException    工作流提交被服务端拒绝
 * @see IarnetCommunicationException 与 control-plane 通信失败
 */
public class IarnetException extends RuntimeException {

    /**
     * 构造异常，仅携带错误信息。
     *
     * @param message 错误描述
     */
    public IarnetException(String message) {
        super(message);
    }

    /**
     * 构造异常，携带错误信息与根因。
     *
     * @param message 错误描述
     * @param cause   导致本异常的根因
     */
    public IarnetException(String message, Throwable cause) {
        super(message, cause);
    }
}
