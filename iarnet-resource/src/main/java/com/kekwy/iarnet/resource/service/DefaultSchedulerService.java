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

    private final AdapterRegistry adapterRegistry;

    private static final class Placement {
        final String actorId;
        final String actorAddr;
        final String nodeId;
        final int replicaIndex;
        final AdapterInfo adapter;
        final Resource resourceRequest;
        final Path artifactPath;
        final String artifactUrl;
        final Lang lang;

        Placement(String actorId, String actorAddr, String nodeId, int replicaIndex,
                  AdapterInfo adapter, Resource resourceRequest,
                  Path artifactPath, String artifactUrl, Lang lang) {
            this.actorId = actorId;
            this.actorAddr = actorAddr;
            this.nodeId = nodeId;
            this.replicaIndex = replicaIndex;
            this.adapter = adapter;
            this.resourceRequest = resourceRequest;
            this.artifactPath = artifactPath;
            this.artifactUrl = artifactUrl;
            this.lang = lang;
        }
    }

    public DefaultSchedulerService(AdapterRegistry adapterRegistry) {
        this.adapterRegistry = adapterRegistry;
    }

    @Override
    public PhysicalWorkflowGraph schedule(WorkflowGraph graph, Map<String, Path> nodeArtifacts,
                                          Map<String, String> nodeArtifactUrls) {
        String workflowId = graph.getWorkflowId();
        String applicationId = graph.getApplicationId();
        log.info("开始调度工作流: workflowId={}, nodes={}", workflowId, graph.getNodesCount());

        Map<String, String> urls = nodeArtifactUrls != null ? nodeArtifactUrls : Map.of();

        // 1) 预计算每个节点的副本数与 Node 映射
        Map<String, Integer> replicasByNodeId = new HashMap<>();
        Map<String, Node> nodeById = new HashMap<>();
        for (Node node : graph.getNodesList()) {
            nodeById.put(node.getId(), node);
            replicasByNodeId.put(node.getId(), determineReplicas(node));
        }

        // 2) Phase 1: Placement（只选设备和生成 ActorAddr，不真正部署）
        List<Placement> placements = new ArrayList<>();
        Map<String, List<Placement>> placementsByNode = new HashMap<>();
        Map<String, String> actorAddrByKey = new HashMap<>(); // nodeId#replicaIndex -> actorAddr

        for (Node node : graph.getNodesList()) {
            String nodeId = node.getId();
            int replicas = replicasByNodeId.get(nodeId);
            for (int replicaIndex = 0; replicaIndex < replicas; replicaIndex++) {
                String actorId = "actor-" + nodeId + "-" + replicaIndex;
                String actorAddr = buildActorAddr(applicationId, workflowId, nodeId, replicaIndex);

                Resource resourceRequest = extractResourceRequest(node);
                AdapterInfo adapter = selectAdapter(resourceRequest);
                Path artifactPath = nodeArtifacts.get(nodeId);
                String artifactUrl = urls.getOrDefault(nodeId, "");
                Lang lang = resolveNodeLang(node);

                Placement placement = new Placement(
                        actorId, actorAddr, nodeId, replicaIndex,
                        adapter, resourceRequest, artifactPath, artifactUrl, lang);
                placements.add(placement);
                placementsByNode.computeIfAbsent(nodeId, k -> new ArrayList<>()).add(placement);
                actorAddrByKey.put(actorKey(nodeId, replicaIndex), actorAddr);
            }
        }

        // 3) 基于 Edge 构建每个 Actor 的上游/下游 ActorAddr 列表
        Map<String, List<String>> upstreamByActorAddr = new HashMap<>();
        Map<String, List<String>> downstreamByActorAddr = new HashMap<>();
        for (Edge edge : graph.getEdgesList()) {
            String srcNodeId = edge.getFromNodeId();
            String dstNodeId = edge.getToNodeId();
            int srcReplicas = replicasByNodeId.getOrDefault(srcNodeId, 1);
            int dstReplicas = replicasByNodeId.getOrDefault(dstNodeId, 1);
            for (int i = 0; i < srcReplicas; i++) {
                String srcAddr = actorAddrByKey.get(actorKey(srcNodeId, i));
                int j = i % dstReplicas;
                String dstAddr = actorAddrByKey.get(actorKey(dstNodeId, j));
                if (srcAddr == null || dstAddr == null) {
                    continue;
                }
                downstreamByActorAddr
                        .computeIfAbsent(srcAddr, k -> new ArrayList<>())
                        .add(dstAddr);
                upstreamByActorAddr
                        .computeIfAbsent(dstAddr, k -> new ArrayList<>())
                        .add(srcAddr);
            }
        }

        // 4) Phase 2: 按 Placement 真正部署，并构建 PhysicalWorkflowGraph
        List<ActorDeployment> deployments = new ArrayList<>();

        for (Node node : graph.getNodesList()) {
            String nodeId = node.getId();
            List<Placement> nodePlacements = placementsByNode.getOrDefault(nodeId, List.of());
            List<ActorInstance> instances = new ArrayList<>();
            for (Placement p : nodePlacements) {
                List<String> upstream = upstreamByActorAddr.getOrDefault(p.actorAddr, List.of());
                List<String> downstream = downstreamByActorAddr.getOrDefault(p.actorAddr, List.of());
                ActorInstance actor = deployActor(applicationId, workflowId, node, p, upstream, downstream);
                instances.add(actor);
            }
            deployments.add(new ActorDeployment(nodeId, node.getKind(), instances));
        }

        PhysicalWorkflowGraph physicalGraph = new PhysicalWorkflowGraph(
                workflowId,
                applicationId,
                deployments,
                graph.getEdgesList()
        );

        log.info("调度完成: workflowId={}, 共部署 {} 个 Actor",
                workflowId, physicalGraph.totalActorCount());

        return physicalGraph;
    }

    private String buildActorAddr(String applicationId, String workflowId, String nodeId, int replicaIndex) {
        return "actor://" + applicationId + "/" + workflowId + "/" + nodeId + "/" + replicaIndex;
    }

    private String actorKey(String nodeId, int replicaIndex) {
        return nodeId + "#" + replicaIndex;
    }

    private Lang resolveNodeLang(Node node) {
        if (node.getKind() == NodeKind.OPERATOR
                && node.hasOperatorDetail()
                && node.getOperatorDetail().hasFunction()) {
            return node.getOperatorDetail().getFunction().getLang();
        }
        // Source / Sink 或未设置语言时，默认使用 Java
        return Lang.LANG_JAVA;
    }

    /**
     * 部署单个 Actor 实例（Phase 2）。
     */
    private ActorInstance deployActor(String applicationId,
                                      String workflowId,
                                      Node node,
                                      Placement placement,
                                      List<String> upstreamAddrs,
                                      List<String> downstreamAddrs) {
        String actorId = placement.actorId;
        String actorAddr = placement.actorAddr;
        AdapterInfo adapter = placement.adapter;

        // 1. 构造部署请求并通过 AdapterRegistry 发送命令
        String artifactId = placement.artifactPath != null
                ? placement.artifactPath.getFileName().toString()
                : "";

        Map<String, String> env = new HashMap<>();
        env.put("IARNET_APPLICATION_ID", applicationId);
        env.put("IARNET_WORKFLOW_ID", workflowId);
        env.put("IARNET_WORKFLOW_NODE_ID", node.getId());
        env.put("IARNET_ACTOR_ID", actorId);
        env.put("IARNET_ACTOR_ADDR", actorAddr);
        env.put("IARNET_DEVICE_ID", adapter.getAdapterId());
        if (!upstreamAddrs.isEmpty()) {
            env.put("IARNET_UPSTREAMS", String.join(",", upstreamAddrs));
        }
        if (!downstreamAddrs.isEmpty()) {
            env.put("IARNET_SUCCESSORS", String.join(",", downstreamAddrs));
        }
        // 无 artifact_url 时用本地路径；有 artifact_url 时由 Adapter 拉取后注入容器内路径
        if (placement.artifactPath != null
                && (placement.artifactUrl == null || placement.artifactUrl.isBlank())) {
            env.put("IARNET_ARTIFACT_PATH", placement.artifactPath.toString());
        }

        Map<String, String> labels = new HashMap<>();
        labels.put("iarnet.workflow_node_id", node.getId());
        labels.put("iarnet.actor_id", actorId);

        DeployInstanceRequest.Builder reqBuilder = DeployInstanceRequest.newBuilder()
                .setInstanceId(actorId)
                .setArtifactId(artifactId)
                .setResourceRequest(placement.resourceRequest)
                .setLang(placement.lang)
                .addAllUpstreamActorAddrs(upstreamAddrs)
                .addAllDownstreamActorAddrs(downstreamAddrs)
                .putAllEnvVars(env)
                .putAllLabels(labels);

        if (placement.artifactUrl != null && !placement.artifactUrl.isBlank()) {
            reqBuilder.setArtifactUrl(placement.artifactUrl);
        }

        DeployInstanceRequest deployReq = reqBuilder.build();

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

    private int determineReplicas(Node node) {
        if (node.getKind() == NodeKind.OPERATOR && node.hasOperatorDetail()) {
            int replicas = node.getOperatorDetail().getReplicas();
            return Math.max(replicas, 1);
        }
        // Source 和 Sink 默认单副本
        return 1;
    }
}
