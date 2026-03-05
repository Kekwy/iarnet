package com.kekwy.iarnet.rpc;

import com.kekwy.iarnet.proto.actor.ActorDirective;
import com.kekwy.iarnet.proto.actor.ActorReport;
import com.kekwy.iarnet.resource.actor.ActorRegistry;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * control-plane 侧的 {@code ActorControlService} gRPC 实现。
 * <p>
 * 处理 Actor 主动建立的 ControlChannel 双向流：
 * 首条消息必须为 ActorReadyReport 完成注册，后续为心跳/状态上报；
 * 控制平面通过同一流下发 ActorDirective。
 */
@Component
public class ActorControlGrpcService
        extends com.kekwy.iarnet.proto.actor.ActorControlServiceGrpc.ActorControlServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(ActorControlGrpcService.class);

    private final ActorRegistry actorRegistry;

    public ActorControlGrpcService(ActorRegistry actorRegistry) {
        this.actorRegistry = actorRegistry;
    }

    @Override
    public StreamObserver<ActorReport> controlChannel(
            StreamObserver<ActorDirective> directiveSender) {
        return new StreamObserver<>() {
            private String actorId;

            @Override
            public void onNext(ActorReport report) {
                if (actorId == null) {
                    if (report.getPayloadCase() != ActorReport.PayloadCase.READY) {
                        log.error("ControlChannel 首条消息必须是 ReadyReport, 收到: {}", report.getPayloadCase());
                        directiveSender.onError(Status.INVALID_ARGUMENT
                                .withDescription("首条消息必须为 ActorReadyReport")
                                .asRuntimeException());
                        return;
                    }
                    actorId = report.getActorId();
                    if (actorId == null || actorId.isEmpty()) {
                        log.error("ControlChannel ReadyReport 缺少 actor_id");
                        directiveSender.onError(Status.INVALID_ARGUMENT
                                .withDescription("ReadyReport 必须设置 actor_id")
                                .asRuntimeException());
                        return;
                    }
                    actorRegistry.onActorConnected(actorId, report.getReady(), directiveSender);
                } else {
                    actorRegistry.handleReport(actorId, report);
                }
            }

            @Override
            public void onError(Throwable t) {
                log.warn("ControlChannel 异常断开: actorId={}, reason={}", actorId, t.getMessage());
                if (actorId != null) {
                    actorRegistry.onActorDisconnected(actorId);
                }
            }

            @Override
            public void onCompleted() {
                log.info("ControlChannel 由 Actor 正常关闭: actorId={}", actorId);
                if (actorId != null) {
                    actorRegistry.onActorDisconnected(actorId);
                }
            }
        };
    }
}
