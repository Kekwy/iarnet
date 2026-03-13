package com.kekwy.iarnet.provider.registry;

import com.kekwy.iarnet.provider.actor.ActorRouter;
import com.kekwy.iarnet.proto.provider.SignalingEnvelope;

import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 处理 SignalingChannel 下发的 SignalingEnvelope（ConnectInstruction、ICE、actor_envelope 等），
 * 将 actor_envelope 转发给本地 Actor；上报由 ActorRouter 通过 setSignalingSender 发送。
 */
public class SignalingChannelHandler implements StreamObserver<SignalingEnvelope> {

    private static final Logger log = LoggerFactory.getLogger(SignalingChannelHandler.class);

    private final String providerId;
    private final ActorRouter actorRouter;
    private final StreamObserver<SignalingEnvelope> responseObserver;
    private final Runnable onDisconnect;

    public SignalingChannelHandler(String providerId,
                                   ActorRouter actorRouter,
                                   StreamObserver<SignalingEnvelope> responseObserver,
                                   Runnable onDisconnect) {
        this.providerId = providerId;
        this.actorRouter = actorRouter;
        this.responseObserver = responseObserver;
        this.onDisconnect = onDisconnect;
    }

    @Override
    public void onNext(SignalingEnvelope value) {
        if (value == null) return;

        switch (value.getPayloadCase()) {
            case ACTOR_ENVELOPE_FORWARD:
                String targetActorId = value.getTargetActorId();
                if (targetActorId != null && !targetActorId.isBlank()) {
                    actorRouter.forwardEnvelopeToActor(targetActorId, value.getActorEnvelopeForward());
                } else {
                    log.warn("SignalingEnvelope actor_envelope_forward 缺少 target_actor_id，无法转发");
                }
                break;
            case CONNECT_INSTRUCTION:
                log.debug("收到 ConnectInstruction: connectId={}", value.getConnectInstruction().getConnectId());
                break;
            case ICE_ENVELOPE:
                log.debug("收到 IceEnvelope: connectId={}", value.getIceEnvelope().getConnectId());
                break;
            case CANDIDATE_UPDATE:
            case ACTOR_CHANNEL:
            case ACTOR_READY:
                log.debug("SignalingChannel 收到: {}", value.getPayloadCase());
                break;
            default:
                log.debug("SignalingChannel 收到: {}", value.getPayloadCase());
        }
    }

    @Override
    public void onError(Throwable t) {
        log.warn("SignalingChannel 出错: {}", t.getMessage());
        if (onDisconnect != null) onDisconnect.run();
    }

    @Override
    public void onCompleted() {
        log.info("SignalingChannel 由服务端关闭");
        if (onDisconnect != null) onDisconnect.run();
    }
}
