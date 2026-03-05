package com.kekwy.iarnet.resource.service;

import com.kekwy.iarnet.proto.adapter.Command;
import com.kekwy.iarnet.proto.adapter.CommandResponse;
import com.kekwy.iarnet.proto.adapter.DeployInstanceRequest;
import com.kekwy.iarnet.proto.adapter.DeployInstanceResponse;
import com.kekwy.iarnet.proto.adapter.InstanceInfo;
import com.kekwy.iarnet.proto.adapter.ResourceCapacity;
import com.kekwy.iarnet.proto.ir.*;
import com.kekwy.iarnet.resource.adapter.AdapterInfo;
import com.kekwy.iarnet.resource.adapter.AdapterRegistry;
import com.kekwy.iarnet.resource.model.ActorDeployment;
import com.kekwy.iarnet.resource.model.ActorInstance;
import com.kekwy.iarnet.resource.model.PhysicalWorkflowGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 默认调度服务实现：遍历工作流中每个节点，
 * 根据节点类型和副本数创建 Actor 实例并分配资源。
 * <p>
 * 当前为占位实现，Actor 部署细节（容器创建、设备选择等）待后续细化。
 */
@Service
public class DefaultSchedulerService implements SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(DefaultSchedulerService.class);

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final String ENV_ACTOR_IMAGE = "IARNET_ACTOR_IMAGE";
    private static final String DEFAULT_ACTOR_IMAGE = "iarnet/actor:latest";

    private final AdapterRegistry adapterRegistry;

    public DefaultSchedulerService(AdapterRegistry adapterRegistry) {
        this.adapterRegistry = adapterRegistry;
    }

    @Override
    public PhysicalWorkflowGraph schedule(WorkflowGraph graph, Map<String, Path> nodeArtifacts,
                                          Map<String, String> nodeArtifactUrls) {
        String workflowId = graph.getWorkflowId();
        log.info("开始调度工作流: workflowId={}, nodes={}", workflowId, graph.getNodesCount());

        List<ActorDeployment> deployments = new ArrayList<>();
        Map<String, String> urls = nodeArtifactUrls != null ? nodeArtifactUrls : Map.of();

        for (Node node : graph.getNodesList()) {
            ActorDeployment deployment = scheduleNode(node, nodeArtifacts.get(node.getId()),
                    urls.getOrDefault(node.getId(), ""));
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

    private ActorDeployment scheduleNode(Node node, Path artifactPath, String artifactUrl) {
        int replicas = determineReplicas(node);

        log.info("调度节点: id={}, kind={}, replicas={}, artifact={}, hasUrl={}",
                node.getId(), node.getKind(), replicas,
                artifactPath != null ? artifactPath : "<none>", !artifactUrl.isBlank());

        List<ActorInstance> instances = new ArrayList<>();
        for (int i = 0; i < replicas; i++) {
            ActorInstance actor = deployActor(node, i, artifactPath, artifactUrl);
            instances.add(actor);
        }

        return new ActorDeployment(node.getId(), node.getKind(), instances);
    }

    @Value("#{${iarnet.actor-image:{}}}")
    private Map<Lang, String> langToImageMap;


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
    private ActorInstance deployActor(Node node, int replicaIndex, Path artifactPath, String artifactUrl) {
        String actorId = "actor-" + node.getId() + "-" + replicaIndex;

        // 1. 解析该节点的资源需求
        Resource resourceRequest = extractResourceRequest(node);

        // 2. 选择一个在线且资源满足需求的 Adapter
        AdapterInfo adapter = selectAdapter(resourceRequest); // TODO: 优化选择算法，解决不一致的问题

        // 3. 构造部署请求并通过 AdapterRegistry 发送命令
        String artifactId = artifactPath != null ? artifactPath.getFileName().toString() : "";
        String image = resolveActorImage();

        Map<String, String> env = new HashMap<>();
        env.put("IARNET_WORKFLOW_NODE_ID", node.getId());
        env.put("IARNET_ACTOR_ID", actorId);
        // 无 artifact_url 时用本地路径；有 artifact_url 时由 Adapter 拉取后注入容器内路径
        if (artifactPath != null && (artifactUrl == null || artifactUrl.isBlank())) {
            env.put("IARNET_ARTIFACT_PATH", artifactPath.toString());
        }

        Map<String, String> labels = new HashMap<>();
        labels.put("iarnet.workflow_node_id", node.getId());
        labels.put("iarnet.actor_id", actorId);

        DeployInstanceRequest.Builder reqBuilder = DeployInstanceRequest.newBuilder()
                .setInstanceId(actorId)
                .setArtifactId(artifactId)
                .setImage(image);
        if (artifactUrl != null && !artifactUrl.isBlank()) {
            reqBuilder.setArtifactUrl(artifactUrl);
        }

        DeployInstanceRequest deployReq = reqBuilder
                .setResourceRequest(resourceRequest)
                .putAllEnvVars(env)
                .putAllLabels(labels)
                .build();

        Command.Builder commandBuilder = Command.newBuilder()
                .setDeployInstance(deployReq);

        CommandResponse response;
        try {
            response = adapterRegistry.sendCommand(adapter.getAdapterId(), commandBuilder).join();
        } catch (Exception e) {
            throw new RuntimeException("通过 Adapter 部署 Actor 失败: nodeId=" + node.getId()
                    + ", adapterId=" + adapter.getAdapterId(), e);
        }

        if (response.getPayloadCase() == CommandResponse.PayloadCase.ERROR) {
            throw new RuntimeException("Adapter 返回错误: " + response.getError().getMessage());
        }
        if (response.getPayloadCase() != CommandResponse.PayloadCase.DEPLOY_INSTANCE) {
            throw new IllegalStateException("意外的 CommandResponse 类型: " + response.getPayloadCase());
        }

        DeployInstanceResponse deployResp = response.getDeployInstance();
        InstanceInfo instance = deployResp.getInstance();

        String deviceId = adapter.getAdapterId();
        String containerId = instance.getContainerId();
        String host = instance.getHost() != null && !instance.getHost().isBlank()
                ? instance.getHost()
                : DEFAULT_HOST;
        int port = instance.getPort();

        log.debug("  部署 Actor: actorId={}, device={}, container={}, host={}, port={}",
                actorId, deviceId, containerId, host, port);

        return new ActorInstance(actorId, deviceId, containerId, host, port);
    }

    /**
     * 从 IR 节点中提取资源需求；若无显式设置则返回一个空的 Resource。
     */
    private Resource extractResourceRequest(Node node) {
        if (node.getKind() == NodeKind.OPERATOR && node.hasOperatorDetail()
                && node.getOperatorDetail().hasResource()) {
            return node.getOperatorDetail().getResource();
        }
        // Source / Sink 或未声明资源时，返回默认空资源
        return Resource.newBuilder().build();
    }

    /**
     * 从在线 Adapter 中选择一个资源满足需求的 Adapter。
     * 当前实现为最简策略：找到第一个 CPU/GPU/内存都满足的 Adapter；
     * 如均不满足，则退化为任意在线 Adapter。
     */
    private AdapterInfo selectAdapter(Resource resourceRequest) {
        List<AdapterInfo> candidates = adapterRegistry.listOnlineAdapters();
        if (candidates.isEmpty()) {
            throw new IllegalStateException("当前无在线 Adapter 可用于部署 Actor");
        }

        for (AdapterInfo info : candidates) {
            ResourceCapacity cap = info.getCapacity();
            if (cap == null || !cap.hasAvailable()) {
                // 没有可用资源信息，视为可选
                return info;
            }
            if (fits(cap.getAvailable(), resourceRequest)) {
                return info;
            }
        }

        // 没有完全满足资源需求的 Adapter，则退化为第一个在线 Adapter
        log.warn("未找到完全满足资源需求的 Adapter，退化为任意在线 Adapter");
        return candidates.get(0);
    }

    private boolean fits(Resource available, Resource request) {
        if (request.getCpu() > 0 && available.getCpu() < request.getCpu()) {
            return false;
        }
        if (request.getGpu() > 0 && available.getGpu() < request.getGpu()) {
            return false;
        }
        String reqMem = request.getMemory();
        String availMem = available.getMemory();
        if (reqMem != null && !reqMem.isBlank()
                && availMem != null && !availMem.isBlank()) {
            long reqBytes = parseMemory(reqMem);
            long availBytes = parseMemory(availMem);
            if (reqBytes > 0 && availBytes > 0 && availBytes < reqBytes) {
                return false;
            }
        }
        return true;
    }

    /**
     * 解析形如 "512Mi"、"2Gi" 的内存字符串为字节数；解析失败时返回 0。
     */
    private long parseMemory(String memory) {
        if (memory == null || memory.isBlank()) {
            return 0L;
        }
        try {
            String m = memory.trim().toUpperCase();
            if (m.endsWith("GI") || m.endsWith("G")) {
                return (long) (Double.parseDouble(m.replaceAll("[^\\d.]", "")) * 1024 * 1024 * 1024);
            }
            if (m.endsWith("MI") || m.endsWith("M")) {
                return (long) (Double.parseDouble(m.replaceAll("[^\\d.]", "")) * 1024 * 1024);
            }
            if (m.endsWith("KI") || m.endsWith("K")) {
                return (long) (Double.parseDouble(m.replaceAll("[^\\d.]", "")) * 1024);
            }
            return Long.parseLong(m.replaceAll("[^\\d]", ""));
        } catch (Exception e) {
            log.warn("解析内存字符串失败: '{}'", memory, e);
            return 0L;
        }
    }

    /**
     * 解析 Actor 运行镜像；优先从环境变量 {@code IARNET_ACTOR_IMAGE} 读取。
     */
    private String resolveActorImage() {
        String fromEnv = System.getenv(ENV_ACTOR_IMAGE);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        return DEFAULT_ACTOR_IMAGE;
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
