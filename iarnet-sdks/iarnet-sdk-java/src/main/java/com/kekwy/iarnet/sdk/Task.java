package com.kekwy.iarnet.sdk;

import java.util.function.Consumer;

/**
 * 工作流中的任务节点（如 checkpoint），在流经数据前后执行。
 */
public interface Task {

    String getName();

    void run(TaskContext context);
}
