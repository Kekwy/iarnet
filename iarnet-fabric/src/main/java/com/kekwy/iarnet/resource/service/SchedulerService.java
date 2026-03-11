package com.kekwy.iarnet.resource.service;

import com.kekwy.iarnet.proto.common.Lang;
import com.kekwy.iarnet.proto.workflow.WorkflowGraph;
import com.kekwy.iarnet.resource.ActorMessageEnvelope;
import com.kekwy.iarnet.resource.DeploymentCallback;
import com.kekwy.iarnet.resource.DeploymentGraph;
import com.kekwy.iarnet.resource.DeploymentPlanGraph;
import com.kekwy.iarnet.resource.MessageInbox;
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
     * @param graph            工作流图
     * @param nodeArtifacts    nodeId → artifact 本地路径（仅 Operator 节点有值）
     * @param nodeArtifactUrls nodeId → artifact 拉取 URL（OSS 预签名等）；可为空，有 URL 时写入部署请求供 Adapter 拉取
     * @return 物理工作流 IR，包含每个节点的 Actor 部署信息
     */
    PhysicalWorkflowGraph deploy(WorkflowGraph graph, Map<String, Path> nodeArtifacts,
                                 Map<String, String> nodeArtifactUrls);

    void deploy(DeploymentPlanGraph deploymentPlanGraph,
                MessageInbox<ActorMessageEnvelope> inbox,
                DeploymentCallback callback);

}
