package com.kekwy.iarnet.sdk.exception;

/**
 * 工作流提交被服务端拒绝时的异常。
 * <p>
 * 典型场景：control-plane 返回 {@code SubmissionStatus.REJECTED}，
 * 通常伴有服务端返回的拒绝原因（如校验失败、配额超限等）。
 * 需根据异常信息修正工作流或联系服务端排查。
 */
public class IarnetSubmissionException extends IarnetException {

    /**
     * @param message 错误描述（通常包含服务端返回的拒绝原因）
     */
    public IarnetSubmissionException(String message) {
        super(message);
    }

    /**
     * @param message 错误描述
     * @param cause   根因（如需要包装底层错误时使用）
     */
    public IarnetSubmissionException(String message, Throwable cause) {
        super(message, cause);
    }
}
