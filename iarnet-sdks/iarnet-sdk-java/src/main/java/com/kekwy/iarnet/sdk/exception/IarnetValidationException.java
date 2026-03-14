package com.kekwy.iarnet.sdk.exception;

/**
 * 校验失败异常。
 * <p>
 * 典型场景：参数非法（如 {@code replicas <= 0}）、节点输出类型无法推断、
 * DSL 用法错误（如 {@code join()} 传入非同一 workflow 的 flow）、
 * 不支持的函数语言、{@link com.kekwy.iarnet.sdk.type.TypeToken} 创建方式错误等。
 * 需根据错误信息修正 DSL 代码后重新构建。
 */
public class IarnetValidationException extends IarnetException {

    /**
     * @param message 错误描述
     */
    public IarnetValidationException(String message) {
        super(message);
    }

    /**
     * @param message 错误描述
     * @param cause   根因
     */
    public IarnetValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
