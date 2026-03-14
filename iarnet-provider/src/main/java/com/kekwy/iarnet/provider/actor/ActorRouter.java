package com.kekwy.iarnet.provider.actor;

import com.kekwy.iarnet.proto.actor.ActorEnvelope;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单机场景下的 Actor 路由：维护 actor_id -> Stream 注册表与拓扑（上下游），
 * 仅负责通道注册、拓扑记录与消息转发；就绪/通道建立上报由调用方通过 SignalingService 完成。
 */
@Slf4j
@Component
public class ActorRouter {

    private record ActorInfo(
            String actorId,
            Set<String> upstreamActors,
            Set<String> downstreamActors
    ) {
    }

    private final Map<String, StreamObserver<ActorEnvelope>> connectedActors = new HashMap<>();
    private final Map<String, ActorInfo> actorInfos = new HashMap<>();

    /**
     * 注册 Actor 流（仅登记 stream，不触发上报；调用方应随后调用 markActorRegistered 并自行上报）。
     */
    public Set<ActorEdge> onActorConnected(String actorId, StreamObserver<ActorEnvelope> stream) {
        if (actorId == null || actorId.isBlank()) return Set.of();
        Set<ActorEdge> establishedEdges = new HashSet<>();
        synchronized (connectedActors) {
            connectedActors.put(actorId, stream);
            ActorInfo actorInfo = actorInfos.get(actorId);
            Set<String> upstreamActors = actorInfo.upstreamActors();
            Set<String> downstreamActors = actorInfo.downstreamActors();
            for (String upstreamActorId : upstreamActors) {
                if (connectedActors.containsKey(upstreamActorId)) {
                    establishedEdges.add(new ActorEdge(upstreamActorId, actorId));
                }
            }
            for (String downstreamActorId : downstreamActors) {
                if (connectedActors.containsKey(downstreamActorId)) {
                    establishedEdges.add(new ActorEdge(actorId, downstreamActorId));
                }
            }
        }
        return establishedEdges;
    }

    /**
     * 移除 Actor 流（连接断开时调用）。
     */
    public void onActorLostConnection(String actorId) {
        if (actorId != null && !actorId.isBlank()) {
            synchronized (connectedActors) {
                // TODO: 考虑断线重连的情况
                connectedActors.remove(actorId);
            }
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
        StreamObserver<ActorEnvelope> stream;
        synchronized (connectedActors) {
            stream = connectedActors.get(target);
        }
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

    public void registerActorTopology(String actorId,
                                      List<String> upstreamActorIds,
                                      List<String> downstreamActorIds) {
        if (actorId == null || actorId.isBlank()) return;
        if (downstreamActorIds == null) downstreamActorIds = Collections.emptyList();

        synchronized (connectedActors) {
            actorInfos.put(actorId, new ActorInfo(
                    actorId, Set.copyOf(upstreamActorIds), Set.copyOf(downstreamActorIds)
            ));
        }
    }

}
