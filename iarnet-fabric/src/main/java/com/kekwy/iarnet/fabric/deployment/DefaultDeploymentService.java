package com.kekwy.iarnet.fabric.deployment;

import com.kekwy.iarnet.fabric.actor.ActorInstanceRef;
import com.kekwy.iarnet.fabric.actor.ActorRegistry;
import com.kekwy.iarnet.fabric.actor.ActorLifecycleListener;
import com.kekwy.iarnet.fabric.messaging.ActorMessageInbox;
import com.kekwy.iarnet.proto.provider.DeployActorRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 {@link DeploymentPlanGraph} 部署 actor 并跟踪其就绪状态。
 * <p>
 * 为每次 deploy 创建 {@link DeploymentContext}，
 * 当所有 actor ready 且所有预期链路 connected 时，通过回调通知上层。
 */
@Service
public class DefaultDeploymentService implements DeploymentService, ActorLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(DefaultDeploymentService.class);

    private final ActorRegistry actorRegistry;

    private final Map<String, DeploymentContext> contextsByDeploymentId = new ConcurrentHashMap<>();
    private final Map<String, DeploymentContext> actorIdToContext = new ConcurrentHashMap<>();

    public DefaultDeploymentService(ActorRegistry actorRegistry) {
        this.actorRegistry = actorRegistry;
        this.actorRegistry.addListener(this);
    }

    @Override
    public void deploy(DeploymentPlanGraph planGraph, ActorMessageInbox inbox, DeploymentCallback callback) {
        String deploymentId = planGraph.deploymentId();
        List<ActorSpec> specs = planGraph.actorSpecs();
        List<ActorEdge> edges = planGraph.edges();

        log.info("开始部署: deploymentId={}, actors={}, edges={}", deploymentId, specs.size(), edges.size());

        Map<String, List<String>> upstreamsByActorId = new HashMap<>();
        Map<String, List<String>> downstreamsByActorId = new HashMap<>();
        for (ActorEdge edge : edges) {
            downstreamsByActorId.computeIfAbsent(edge.fromActorId(), k -> new ArrayList<>()).add(edge.toActorId());
            upstreamsByActorId.computeIfAbsent(edge.toActorId(), k -> new ArrayList<>()).add(edge.fromActorId());
        }

        Set<String> allActorIds = new HashSet<>();
        Map<String, ActorInstanceRef> refsByActorId = new HashMap<>();

        for (ActorSpec spec : specs) {
            allActorIds.add(spec.actorId());
            ActorInstanceRef ref = new ActorInstanceRef(spec.actorId());
            ref.setActorRegistry(actorRegistry);
            refsByActorId.put(spec.actorId(), ref);
        }

        for (ActorSpec spec : specs) {
            ActorInstanceRef ref = refsByActorId.get(spec.actorId());
            ref.setPrecursors(
                    upstreamsByActorId.getOrDefault(spec.actorId(), List.of())
                            .stream().map(refsByActorId::get).filter(Objects::nonNull).toList());
            ref.setSuccessors(
                    downstreamsByActorId.getOrDefault(spec.actorId(), List.of())
                            .stream().map(refsByActorId::get).filter(Objects::nonNull).toList());
        }

        InstanceRefGraph instanceRefGraph = new InstanceRefGraph(
                deploymentId, List.copyOf(refsByActorId.values()));

        Set<DeploymentContext.EdgeKey> expectedEdges = new HashSet<>();
        for (ActorEdge edge : edges) {
            expectedEdges.add(new DeploymentContext.EdgeKey(edge.fromActorId(), edge.toActorId()));
        }

        DeploymentContext context = new DeploymentContext(
                deploymentId, allActorIds, expectedEdges, callback, instanceRefGraph);
        contextsByDeploymentId.put(deploymentId, context);
        for (String actorId : allActorIds) {
            actorIdToContext.put(actorId, context);
        }

        for (ActorSpec spec : specs) {
            List<String> upstreams = upstreamsByActorId.getOrDefault(spec.actorId(), List.of());
            List<String> downstreams = downstreamsByActorId.getOrDefault(spec.actorId(), List.of());
            sendDeployActorRequest(deploymentId, spec, upstreams, downstreams);
        }

        log.info("部署请求已发出: deploymentId={}, actors={}", deploymentId, allActorIds.size());
    }

    /**
     * 向 Provider 发送部署请求。
     * <p>
     * 当前构造 {@link DeployActorRequest} 并通过日志输出；
     * 实际发送需通过 ProviderRegistryService 的 DeploymentChannel 下发。
     */
    private void sendDeployActorRequest(String deploymentId,
                                        ActorSpec spec,
                                        List<String> upstreamActorIds,
                                        List<String> downstreamActorIds) {
        DeployActorRequest.Builder reqBuilder = DeployActorRequest.newBuilder()
                .setActorId(spec.actorId())
                .setResourceRequest(spec.resourceSpec())
                .setLang(spec.function().getLang())
                .addAllUpstreamActorAddrs(upstreamActorIds)
                .addAllDownstreamActorAddrs(downstreamActorIds);

        if (spec.artifactUrl() != null && !spec.artifactUrl().isBlank()) {
            reqBuilder.setArtifactUrl(spec.artifactUrl());
        }
        if (spec.function() != null) {
            reqBuilder.setFunctionDescriptor(spec.function());
        }

        DeployActorRequest request = reqBuilder.build();

        // TODO: 通过 ProviderRegistryService.DeploymentChannel 发送 DeploymentEnvelope
        log.info("发送部署请求: deploymentId={}, actorId={}, upstreams={}, downstreams={}",
                deploymentId, spec.actorId(), upstreamActorIds, downstreamActorIds);
    }

    // ======================== Actor 生命周期事件路由 ========================

    @Override
    public void onActorReady(String actorId) {
        DeploymentContext context = actorIdToContext.get(actorId);
        if (context != null) {
            context.onActorReady(actorId);
        }
    }

    @Override
    public void onChannelConnected(String srcActorId, String dstActorId) {
        DeploymentContext ctx = actorIdToContext.get(srcActorId);
        if (ctx != null) {
            ctx.onChannelConnected(srcActorId, dstActorId);
        } else {
            ctx = actorIdToContext.get(dstActorId);
            if (ctx != null) {
                ctx.onChannelConnected(srcActorId, dstActorId);
            }
        }
    }

    @Override
    public void onActorDisconnected(String actorId) {
        // 当前不做处理，后续可扩展失败重试逻辑
    }
}
