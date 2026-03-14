package com.kekwy.iarnet.fabric.deployment;

import com.kekwy.iarnet.fabric.actor.ActorInstanceRef;
import com.kekwy.iarnet.fabric.actor.ActorRegistry;
import com.kekwy.iarnet.fabric.actor.ActorLifecycleListener;
import com.kekwy.iarnet.fabric.messaging.ActorMessageInbox;
import com.kekwy.iarnet.proto.common.FunctionDescriptor;
import com.kekwy.iarnet.proto.provider.DeployActorRequest;
import com.kekwy.iarnet.proto.provider.DownstreamGroup;
import com.kekwy.iarnet.proto.provider.RoutingStrategy;
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
            List<DownstreamGroup> downstreamGroups = buildDownstreamGroups(spec.actorId(), edges);
            sendDeployActorRequest(deploymentId, spec, upstreams, downstreamGroups);
        }

        log.info("部署请求已发出: deploymentId={}, actors={}", deploymentId, allActorIds.size());
    }

    /**
     * 按 (outputPort, 目标 nodeId) 将边分组，构建 DownstreamGroup 列表。
     */
    private static List<DownstreamGroup> buildDownstreamGroups(String sourceActorId, List<ActorEdge> edges) {
        // key: (outputPort, logicalOperatorId) -> (actor_addrs, routingStrategy, conditionFn)
        Map<String, List<String>> addrsByKey = new HashMap<>();
        Map<String, RoutingStrategy> strategyByKey = new HashMap<>();
        Map<String, FunctionDescriptor> conditionByKey = new HashMap<>();

        for (ActorEdge edge : edges) {
            if (!edge.fromActorId().equals(sourceActorId)) {
                continue;
            }
            String logicalId = extractNodeIdFromActorId(edge.toActorId());
            String key = edge.outputPort() + ":" + logicalId;

            addrsByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(edge.toActorId());
            strategyByKey.putIfAbsent(key, edge.routingStrategy());
            if (edge.functionDescriptor() != null) {
                conditionByKey.putIfAbsent(key, edge.functionDescriptor());
            }
        }

        List<DownstreamGroup> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : addrsByKey.entrySet()) {
            String[] parts = e.getKey().split(":", 2);
            int outputPort = Integer.parseInt(parts[0]);
            String logicalOperatorId = parts[1];
            DownstreamGroup.Builder builder = DownstreamGroup.newBuilder()
                    .setLogicalOperatorId(logicalOperatorId)
                    .addAllActorAddrs(e.getValue())
                    .setRoutingStrategy(strategyByKey.get(e.getKey()))
                    .setOutputPort(outputPort);
            FunctionDescriptor cond = conditionByKey.get(e.getKey());
            if (cond != null) {
                builder.setConditionFunction(cond);
            }
            result.add(builder.build());
        }
        return result;
    }

    private static String extractNodeIdFromActorId(String actorId) {
        if (actorId == null || !actorId.startsWith("actor-")) {
            return actorId != null ? actorId : "";
        }
        int prefixLen = "actor-".length();
        int lastHyphen = actorId.lastIndexOf('-');
        if (lastHyphen <= prefixLen) {
            return actorId.substring(prefixLen);
        }
        return actorId.substring(prefixLen, lastHyphen);
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
                                        List<DownstreamGroup> downstreamGroups) {
        DeployActorRequest.Builder reqBuilder = DeployActorRequest.newBuilder()
                .setActorId(spec.actorId())
                .setResourceRequest(spec.resourceSpec())
                .setLang(spec.function().getLang())
                .addAllUpstreamActorAddrs(upstreamActorIds)
                .setInstanceIndex(spec.instanceIndex())
                .addAllDownstreamGroups(downstreamGroups);

        if (spec.artifactUrl() != null && !spec.artifactUrl().isBlank()) {
            reqBuilder.setArtifactUrl(spec.artifactUrl());
        }
        if (spec.function() != null) {
            reqBuilder.setFunctionDescriptor(spec.function());
        }

        DeployActorRequest request = reqBuilder.build();

        // TODO: 通过 ProviderRegistryService.DeploymentChannel 发送 DeploymentEnvelope
        log.info("发送部署请求: deploymentId={}, actorId={}, upstreams={}, downstreamGroups={}",
                deploymentId, spec.actorId(), upstreamActorIds, downstreamGroups.size());
    }

    // ======================== Actor 生命周期事件路由 ========================

    @Override
    public void onActorReady(String actorId, String providerId) {
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
