package com.kekwy.iarnet.api;

import com.kekwy.iarnet.api.source.Source;

import java.util.function.Consumer;

/**
 * 工作流 DSL 入口，用于创建 source、task 并执行。
 */
public interface Workflow {

    /**
     * 创建新的工作流实例。
     */
    static Workflow create() {
        return new DefaultWorkflow();
    }

    /**
     * 从数据源创建流，元素类型为 T。
     */
    <T> Flow<T> source(Source<T> source);

    /**
     * 注册一个任务节点（如 checkpoint），返回的 Task 可用于 flow.after(task)。
     *
     * @param name     任务名称
     * @param action   执行逻辑，接收 TaskContext
     * @return 任务节点
     */
    Task task(String name, Consumer<TaskContext> action);

    /**
     * 执行已组装的工作流。
     * <p>
     * 当前实现不会在本地直接跑数据流，而是将 DSL 描述的工作流编译为
     * proto 形式的 IR（由后续 iarnet-proto 模块提供），再交由后端执行。
     */
    void execute();
}
