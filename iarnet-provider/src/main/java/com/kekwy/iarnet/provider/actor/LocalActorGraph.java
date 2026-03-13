package com.kekwy.iarnet.provider.actor;

import com.kekwy.iarnet.proto.actor.ActorEnvelope;
import com.kekwy.iarnet.proto.provider.ActorChannelStatus;
import com.kekwy.iarnet.proto.provider.ActorReadyReport;
import com.kekwy.iarnet.proto.provider.SignalingEnvelope;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单机场景下的本地 Actor 拓扑视图，统一使用 actor_id。
 * 接收部署时的下游 actor_id 列表、Actor 注册，在双端均注册时上报通道就绪与 Actor 就绪。
 */
public final class LocalActorGraph {

    private static final Logger log = LoggerFactory.getLogger(LocalActorGraph.class);

    private static final LocalActorGraph INSTANCE = new LocalActorGraph();

    public static LocalActorGraph getInstance() {
        return INSTANCE;
    }

    private volatile StreamObserver<SignalingEnvelope> signalingSender;
    private volatile String providerId;
    private volatile ActorRegistrationServiceImpl actorRegistrationService;

    public void setSignalingSender(StreamObserver<SignalingEnvelope> signalingSender) {
        this.signalingSender = signalingSender;
    }

    public void setProviderId(String providerId) {
        this.providerId = providerId;
    }

    public void setActorRegistrationService(ActorRegistrationServiceImpl actorRegistrationService) {
        this.actorRegistrationService = actorRegistrationService;
    }

    public ActorRegistrationServiceImpl getActorRegistrationService() {
        return actorRegistrationService;
    }

    /**
     * 将控制平面经 SignalingChannel 下发的 ActorEnvelope 转发给指定 actor_id。
     */
    public void forwardEnvelopeToActor(String actorId, ActorEnvelope envelope) {
        ActorRegistrationServiceImpl service = this.actorRegistrationService;
        if (service != null) {
            service.forwardEnvelopeToActor(actorId, envelope);
        } else {
            log.warn("ActorRegistrationService 未注入，无法转发: actorId={}", actorId);
        }
    }

    private final Set<String> registeredActors = ConcurrentHashMap.newKeySet();
    private final Set<String> expectedEdges = ConcurrentHashMap.newKeySet();
    private final Set<String> establishedEdges = ConcurrentHashMap.newKeySet();

    private LocalActorGraph() {}

    /**
     * 部署时记录 actor_id 与其下游 actor_id 列表。
     */
    public void onDeploy(String actorId, List<String> downstreamActorIds) {
        if (actorId == null || actorId.isBlank()) return;
        if (downstreamActorIds == null) downstreamActorIds = Collections.emptyList();

        for (String dst : downstreamActorIds) {
            if (dst == null || dst.isBlank()) continue;
            String edgeKey = edgeKey(actorId, dst);
            expectedEdges.add(edgeKey);
            maybeEstablish(edgeKey, actorId, dst);
        }
    }

    /**
     * Actor 通过 ActorChannel 注册时调用。
     */
    public void onActorRegistered(String actorId) {
        if (actorId == null || actorId.isBlank()) return;
        registeredActors.add(actorId);
        log.info("LocalActorGraph: actor 已注册: actorId={}", actorId);

        reportActorReady(actorId);

        for (String edge : expectedEdges) {
            if (establishedEdges.contains(edge)) continue;
            String[] parts = edge.split("->", 2);
            if (parts.length != 2) continue;
            String src = parts[0];
            String dst = parts[1];
            if (actorId.equals(src) || actorId.equals(dst)) {
                maybeEstablish(edge, src, dst);
            }
        }
    }

    private void maybeEstablish(String edgeKey, String srcId, String dstId) {
        if (registeredActors.contains(srcId) && registeredActors.contains(dstId)) {
            if (establishedEdges.add(edgeKey)) {
                log.info("LocalActorGraph: 本地通道已就绪: {} -> {}", srcId, dstId);
                reportChannelEstablished(srcId, dstId);
            }
        }
    }

    private void reportChannelEstablished(String srcActorId, String dstActorId) {
        StreamObserver<SignalingEnvelope> sender = this.signalingSender;
        if (sender == null) return;
        try {
            SignalingEnvelope msg = SignalingEnvelope.newBuilder()
                    .setProviderId(providerId != null ? providerId : "")
                    .setTimestampMs(System.currentTimeMillis())
                    .setActorChannel(ActorChannelStatus.newBuilder()
                            .setWorkflowId("")
                            .setApplicationId("")
                            .setSrcActorAddr(srcActorId)
                            .setDstActorAddr(dstActorId)
                            .setConnected(true)
                            .build())
                    .build();
            sender.onNext(msg);
            log.info("LocalActorGraph: 已上报 ActorChannelStatus: src={}, dst={}", srcActorId, dstActorId);
        } catch (Exception e) {
            log.warn("LocalActorGraph: 上报 ActorChannelStatus 失败: src={}, dst={}", srcActorId, dstActorId, e);
        }
    }

    private void reportActorReady(String actorId) {
        StreamObserver<SignalingEnvelope> sender = this.signalingSender;
        if (sender == null) return;
        try {
            SignalingEnvelope msg = SignalingEnvelope.newBuilder()
                    .setProviderId(providerId != null ? providerId : "")
                    .setTimestampMs(System.currentTimeMillis())
                    .setActorReady(ActorReadyReport.newBuilder().setActorId(actorId).build())
                    .build();
            sender.onNext(msg);
            log.info("LocalActorGraph: 已上报 ActorReadyReport: actorId={}", actorId);
        } catch (Exception e) {
            log.warn("LocalActorGraph: 上报 ActorReadyReport 失败: actorId={}", actorId, e);
        }
    }

    private static String edgeKey(String src, String dst) {
        return src + "->" + dst;
    }
}
