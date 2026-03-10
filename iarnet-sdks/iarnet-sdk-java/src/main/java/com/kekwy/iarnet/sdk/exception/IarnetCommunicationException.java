package com.kekwy.iarnet.sdk.exception;

/**
 * 与 control-plane 通信失败异常，如 gRPC 调用失败、网络不可达等。
 */
public class IarnetCommunicationException extends IarnetException {

    public IarnetCommunicationException(String message) {
        super(message);
    }

    public IarnetCommunicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
