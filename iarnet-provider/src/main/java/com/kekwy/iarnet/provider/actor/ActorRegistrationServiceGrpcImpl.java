package com.kekwy.iarnet.provider.actor;

import com.kekwy.iarnet.proto.actor.ActorEnvelope;
import com.kekwy.iarnet.proto.provider.ActorRegistrationServiceGrpc;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 本地 Actor 注册与消息入口：实现 ActorRegistrationService，流使用 ActorEnvelope。
 * 处理 register_actor、row（DataRow），将消息交由 ActorRouter 按 target 路由。
 */
@Slf4j
@Component
public class ActorRegistrationServiceGrpcImpl extends ActorRegistrationServiceGrpc.ActorRegistrationServiceImplBase {

    private final ActorRouter router;

    public ActorRegistrationServiceGrpcImpl(ActorRouter router) {
        this.router = router;
    }

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
                        router.registerActorStream(registeredActorId, responseObserver);
                        break;
                    case ROW:
                        if (registeredActorId != null) {
                            router.routeEnvelope(msg);
                        } else {
                            log.warn("收到 row 但该连接尚未 register_actor");
                        }
                        break;
                    default:
                        log.debug("ActorChannel 收到: {}", msg.getPayloadCase());
                }
            }

            @Override
            public void onError(Throwable t) {
                if (registeredActorId != null) router.unregisterActorStream(registeredActorId);
                log.warn("ActorChannel 出错: {}", t.getMessage());
            }

            @Override
            public void onCompleted() {
                if (registeredActorId != null) router.unregisterActorStream(registeredActorId);
                responseObserver.onCompleted();
            }
        };
    }
}
