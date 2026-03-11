package com.kekwy.iarnet.resource.model;

import com.kekwy.iarnet.proto.ir.Edge;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作流的物理 IR：在逻辑 IR 的基础上，为每个节点关联了实际部署的 Actor 信息。
 * <p>
 * 逻辑 IR ({@code WorkflowGraph}) 描述"做什么"，
 * 物理 IR ({@code PhysicalWorkflowGraph}) 描述"在哪做"。
 *
 * @param workflowId    工作流 ID
 * @param applicationId 应用 ID
 * @param deployments   各节点的部署信息列表
 * @param edges         节点间的数据流边（与逻辑 IR 保持一致）
 */
public record PhysicalWorkflowGraph(
        String workflowId,
        String applicationId,
        List<ActorDeployment> deployments,
        List<Edge> edges
) {
    public PhysicalWorkflowGraph {
        deployments = List.copyOf(deployments);
        edges = List.copyOf(edges);
    }

    /**
     * @return nodeId → ActorDeployment 的映射
     */
    public Map<String, ActorDeployment> deploymentMap() {
        return deployments.stream()
                .collect(Collectors.toMap(ActorDeployment::nodeId, d -> d));
    }

    /**
     * @return 所有已部署的 Actor 实例总数
     */
    public int totalActorCount() {
        return deployments.stream()
                .mapToInt(d -> d.instances().size())
                .sum();
    }
}
