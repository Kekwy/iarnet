package com.kekwy.iarnet.resource.service;

import com.kekwy.iarnet.proto.ir.WorkflowGraph;
import com.kekwy.iarnet.resource.model.PhysicalWorkflowGraph;

import java.nio.file.Path;
import java.util.Map;

/**
 * 调度服务：接收逻辑工作流 IR 与 artifact 信息，
 * 为每个节点的每个实例分配资源并部署 Actor，返回物理 IR。
 */
public interface SchedulerService {

    /**
     * 调度并部署工作流中的所有节点。
     *
     * @param graph         逻辑工作流 IR
     * @param nodeArtifacts nodeId → artifact 文件路径的映射（仅 Operator 节点有值）
     * @return 物理工作流 IR，包含每个节点的 Actor 部署信息
     */
    PhysicalWorkflowGraph schedule(WorkflowGraph graph, Map<String, Path> nodeArtifacts);
}
