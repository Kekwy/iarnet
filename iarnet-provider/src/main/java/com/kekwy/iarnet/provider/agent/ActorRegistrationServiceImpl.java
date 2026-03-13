package com.kekwy.iarnet.provider.agent;

import com.kekwy.iarnet.proto.actor.ActorEnvelope;
import com.kekwy.iarnet.proto.actor.DataRow;
import com.kekwy.iarnet.proto.provider.ActorRegistrationServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地 Actor 注册与消息路由：实现 ActorRegistrationService，流使用 ActorEnvelope。
 * 处理 register_actor、row_output，转发控制指令（如 StartInputCommand）给对应 actor_id。
 */
public class ActorRegistrationServiceImpl extends ActorRegistrationServiceGrpc.ActorRegistrationServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(ActorRegistrationServiceImpl.class);

    /** actorId -> 该 Actor 的 ActorChannel 发送端 */
    private final Map<String, StreamObserver<ActorEnvelope>> actorIdToStream = new ConcurrentHashMap<>();

    /** actorId -> 下游 actor_id 列表 */
    private final Map<String, List<String>> downstreamsByActorId = new ConcurrentHashMap<>();

    @Override
    public StreamObserver<ActorEnvelope> actorChannel(StreamObserver<ActorEnvelope> responseObserver) {
        return new StreamObserver<>() {
            private volatile String registeredActorId;

            @Override
            public void onNext(ActorEnvelope msg) {
                if (msg == null) return;
                switch (msg.getPayloadCase()) {
                    case REGISTER_ACTOR:
                        registeredActorId = msg.getRegisterActor().getActorId();
                        handleRegister(registeredActorId, responseObserver);
                        break;
                    case ROW_OUTPUT:
                        if (registeredActorId != null) {
                            handleRowOutput(registeredActorId, msg.getRowOutput(), responseObserver);
                        } else {
                            log.warn("收到 row_output 但该连接尚未 register_actor");
                        }
                        break;
                    default:
                        log.debug("ActorChannel 收到: {}", msg.getPayloadCase());
                }
            }

            @Override
            public void onError(Throwable t) {
                if (registeredActorId != null) actorIdToStream.remove(registeredActorId);
                log.warn("ActorChannel 出错: {}", t.getMessage());
            }

            @Override
            public void onCompleted() {
                if (registeredActorId != null) actorIdToStream.remove(registeredActorId);
                responseObserver.onCompleted();
            }
        };
    }

    private void handleRegister(String actorId, StreamObserver<ActorEnvelope> responseObserver) {
        actorIdToStream.put(actorId, responseObserver);
        log.info("Actor 已注册: actorId={}", actorId);
        LocalActorGraph.getInstance().onActorRegistered(actorId);
    }

    private void handleRowOutput(String srcActorId, DataRow row, StreamObserver<ActorEnvelope> responseObserver) {
        List<String> downstreams = downstreamsByActorId.get(srcActorId);
        if (downstreams == null || downstreams.isEmpty()) {
            log.debug("Actor {} 无下游，丢弃 row_output", srcActorId);
            return;
        }
        ActorEnvelope delivery = ActorEnvelope.newBuilder().setRowDelivery(row).build();
        for (String dstActorId : downstreams) {
            StreamObserver<ActorEnvelope> dstStream = actorIdToStream.get(dstActorId);
            if (dstStream == null) {
                log.warn("下游 Actor {} 未注册，无法转发 row", dstActorId);
                continue;
            }
            try {
                dstStream.onNext(delivery);
            } catch (Exception e) {
                log.warn("转发 row 到 {} 失败", dstActorId, e);
            }
        }
    }

    /**
     * 将控制平面下发的 ActorEnvelope（如 StartInputCommand）转发给指定 actor_id。
     */
    public void forwardEnvelopeToActor(String actorId, ActorEnvelope envelope) {
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

    public void setDownstreamsForActor(String actorId, List<String> downstreamActorIds) {
        if (downstreamActorIds != null && !downstreamActorIds.isEmpty()) {
            downstreamsByActorId.put(actorId, List.copyOf(downstreamActorIds));
            log.info("已记录下游: actorId={}, downstreams={}", actorId, downstreamActorIds);
        }
    }
}
