package com.kekwy.iarnet.resource.model;

import com.kekwy.iarnet.proto.ir.NodeKind;

import java.util.List;

/**
 * 表示一个逻辑节点的所有 Actor 部署信息。
 * <p>
 * 一个逻辑节点可以有多个副本（replicas），每个副本对应一个 {@link ActorInstance}。
 *
 * @param nodeId    对应的逻辑节点 ID（与 WorkflowGraph.Node.id 一致）
 * @param nodeKind  节点类型
 * @param instances 该节点所有已部署的 Actor 实例列表
 */
public record ActorDeployment(
        String nodeId,
        NodeKind nodeKind,
        List<ActorInstance> instances
) {
    public ActorDeployment {
        instances = List.copyOf(instances);
    }
}
