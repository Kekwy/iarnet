package com.kekwy.iarnet.api.graph;

/**
 * 任务节点（如 checkpoint），对应 proto 中的 Task。
 */
public record TaskNode(String id, String name, String function) {

    public static TaskNode of(String id, String name, String function) {
        return new TaskNode(id, name, function);
    }

}
