package com.kekwy.iarnet.sdk.exception;

/**
 * 与 control-plane 通信失败异常。
 * <p>
 * 典型场景：gRPC 调用失败（如 {@link io.grpc.StatusRuntimeException}）、
 * 网络不可达、连接超时、服务不可用等。通常可通过检查网络、服务状态后重试。
 */
public class IarnetCommunicationException extends IarnetException {

    /**
     * @param message 错误描述
     */
    public IarnetCommunicationException(String message) {
        super(message);
    }

    /**
     * @param message 错误描述
     * @param cause   根因（如 {@link io.grpc.StatusRuntimeException}）
     */
    public IarnetCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
