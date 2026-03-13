package com.kekwy.iarnet.provider.actor;

import com.kekwy.iarnet.proto.actor.ActorEnvelope;
import com.kekwy.iarnet.proto.provider.ActorChannelStatus;
import com.kekwy.iarnet.proto.provider.ActorReadyReport;
import com.kekwy.iarnet.proto.provider.SignalingEnvelope;
import io.grpc.stub.StreamObserver;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单机场景下的 Actor 路由：维护 actor_id -> Stream 注册表，按 ActorEnvelope.target 路由消息；
 * 接收部署时的下游 actor_id 列表、Actor 注册，在双端均注册时上报通道就绪与 Actor 就绪。
 */
@Component
public class ActorRouter {

    private static final Logger log = LoggerFactory.getLogger(ActorRouter.class);

    @Setter
    private volatile StreamObserver<SignalingEnvelope> signalingSender;
    @Setter
    private volatile String providerId;

    /** actorId -> 该 Actor 的 ActorChannel 发送端 */
    private final Map<String, StreamObserver<ActorEnvelope>> actorIdToStream = new ConcurrentHashMap<>();

    private final Set<String> registeredActors = ConcurrentHashMap.newKeySet();
    private final Set<String> expectedEdges = ConcurrentHashMap.newKeySet();
    private final Set<String> establishedEdges = ConcurrentHashMap.newKeySet();

    /**
     * 注册 Actor 流，并通知拓扑（上报就绪、可能建立边）。
     */
    public void registerActorStream(String actorId, StreamObserver<ActorEnvelope> stream) {
        if (actorId == null || actorId.isBlank()) return;
        actorIdToStream.put(actorId, stream);
        onActorRegistered(actorId);
    }

    /**
     * 移除 Actor 流（连接断开时调用）。
     */
    public void unregisterActorStream(String actorId) {
        if (actorId != null && !actorId.isBlank()) {
            actorIdToStream.remove(actorId);
        }
    }

    /**
     * 根据 envelope.target 将消息路由到目标 actor。
     */
    public void routeEnvelope(ActorEnvelope envelope) {
        if (envelope == null) return;
        String target = envelope.getTarget();
        if (target.isBlank()) {
            log.warn("ActorEnvelope 缺少 target，无法路由");
            return;
        }
        StreamObserver<ActorEnvelope> stream = actorIdToStream.get(target);
        if (stream == null) {
            log.warn("目标 Actor 未注册，无法转发: target={}", target);
            return;
        }
        try {
            stream.onNext(envelope);
            log.debug("已路由 envelope 到 target={}, payloadCase={}", target, envelope.getPayloadCase());
        } catch (Exception e) {
            log.warn("路由 envelope 到 target={} 失败", target, e);
        }
    }

    /**
     * 将控制平面经 SignalingChannel 下发的 ActorEnvelope 转发给指定 actor_id。
     */
    public void forwardEnvelopeToActor(String actorId, ActorEnvelope envelope) {
        if (actorId == null || actorId.isBlank()) return;
        StreamObserver<ActorEnvelope> stream = actorIdToStream.get(actorId);
        if (stream == null) {
            log.warn("未找到 actorId={} 的 ActorChannel，无法转发", actorId);
            return;
        }
        try {
            stream.onNext(envelope);
            log.info("已向 Actor 转发 envelope: actorId={}, payloadCase={}", actorId, envelope.getPayloadCase());
        } catch (Exception e) {
            log.warn("转发 envelope 失败: actorId={}", actorId, e);
        }
    }

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

    private void onActorRegistered(String actorId) {
        registeredActors.add(actorId);
        log.info("ActorRouter: actor 已注册: actorId={}", actorId);

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
                log.info("ActorRouter: 本地通道已就绪: {} -> {}", srcId, dstId);
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
            log.info("ActorRouter: 已上报 ActorChannelStatus: src={}, dst={}", srcActorId, dstActorId);
        } catch (Exception e) {
            log.warn("ActorRouter: 上报 ActorChannelStatus 失败: src={}, dst={}", srcActorId, dstActorId, e);
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
            log.info("ActorRouter: 已上报 ActorReadyReport: actorId={}", actorId);
        } catch (Exception e) {
            log.warn("ActorRouter: 上报 ActorReadyReport 失败: actorId={}", actorId, e);
        }
    }

    private static String edgeKey(String src, String dst) {
        return src + "->" + dst;
    }
}
