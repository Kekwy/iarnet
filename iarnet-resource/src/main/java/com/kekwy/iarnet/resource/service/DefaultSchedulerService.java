package com.kekwy.iarnet.resource.service;

import com.kekwy.iarnet.proto.ir.*;
import com.kekwy.iarnet.resource.model.ActorDeployment;
import com.kekwy.iarnet.resource.model.ActorInstance;
import com.kekwy.iarnet.resource.model.PhysicalWorkflowGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.*;

/**
 * 默认调度服务实现：遍历工作流中每个节点，
 * 根据节点类型和副本数创建 Actor 实例并分配资源。
 * <p>
 * 当前为占位实现，Actor 部署细节（容器创建、设备选择等）待后续细化。
 */
@Service
public class DefaultSchedulerService implements SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(DefaultSchedulerService.class);

    private static final String DEFAULT_DEVICE_ID = "device-local-0";
    private static final String DEFAULT_HOST = "127.0.0.1";
    private int nextPort = 10000;

    @Override
    public PhysicalWorkflowGraph schedule(WorkflowGraph graph, Map<String, Path> nodeArtifacts) {
        String workflowId = graph.getWorkflowId();
        log.info("开始调度工作流: workflowId={}, nodes={}", workflowId, graph.getNodesCount());

        List<ActorDeployment> deployments = new ArrayList<>();

        for (Node node : graph.getNodesList()) {
            ActorDeployment deployment = scheduleNode(node, nodeArtifacts.get(node.getId()));
            deployments.add(deployment);
        }

        PhysicalWorkflowGraph physicalGraph = new PhysicalWorkflowGraph(
                workflowId,
                graph.getApplicationId(),
                deployments,
                graph.getEdgesList()
        );

        log.info("调度完成: workflowId={}, 共部署 {} 个 Actor",
                workflowId, physicalGraph.totalActorCount());

        return physicalGraph;
    }

    private ActorDeployment scheduleNode(Node node, Path artifactPath) {
        int replicas = determineReplicas(node);

        log.info("调度节点: id={}, kind={}, replicas={}, artifact={}",
                node.getId(), node.getKind(), replicas,
                artifactPath != null ? artifactPath : "<none>");

        List<ActorInstance> instances = new ArrayList<>();
        for (int i = 0; i < replicas; i++) {
            ActorInstance actor = deployActor(node, i, artifactPath);
            instances.add(actor);
        }

        return new ActorDeployment(node.getId(), node.getKind(), instances);
    }

    /**
     * 部署单个 Actor 实例。
     * <p>
     * TODO: 实际实现应在此处：
     * <ul>
     *   <li>选择目标设备（基于资源需求和设备可用资源）</li>
     *   <li>创建容器/进程，注入 artifact</li>
     *   <li>启动 Actor 并获取通信地址</li>
     * </ul>
     */
    private ActorInstance deployActor(Node node, int replicaIndex, Path artifactPath) {
        String actorId = "actor-" + node.getId() + "-" + replicaIndex;
        String containerId = "container-" + UUID.randomUUID().toString().substring(0, 8);
        int port = nextPort++;

        log.debug("  部署 Actor: actorId={}, device={}, container={}, port={}",
                actorId, DEFAULT_DEVICE_ID, containerId, port);

        return new ActorInstance(actorId, DEFAULT_DEVICE_ID, containerId, DEFAULT_HOST, port);
    }

    private int determineReplicas(Node node) {
        if (node.getKind() == NodeKind.OPERATOR && node.hasOperatorDetail()) {
            int replicas = node.getOperatorDetail().getReplicas();
            return Math.max(replicas, 1);
        }
        // Source 和 Sink 默认单副本
        return 1;
    }
}
