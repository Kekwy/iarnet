package com.kekwy.iarnet.api;

/**
 * 任务执行时的上下文，可在 checkpoint 等任务中访问或保存状态。
 */
public interface TaskContext {

    /**
     * 当前所属工作流。
     */
    Workflow getWorkflow();

    /**
     * 可扩展：获取/设置用户状态等。
     */
    Object getState(String key);

    void setState(String key, Object value);
}
