package com.kekwy.iarnet.sdk.exception;

/**
 * 工作流提交被服务端拒绝时的异常。
 */
public class IarnetSubmissionException extends IarnetException {

    public IarnetSubmissionException(String message) {
        super(message);
    }
}
