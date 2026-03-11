package com.kekwy.iarnet.rpc;

import com.kekwy.iarnet.proto.agent.ActorChannelStatus;
import com.kekwy.iarnet.proto.agent.ActorDirectiveForward;
import com.kekwy.iarnet.proto.agent.DeviceAgentRegistryServiceGrpc;
import com.kekwy.iarnet.proto.agent.SignalingMessage;
import com.kekwy.iarnet.proto.actor.ActorDirective;
import com.kekwy.iarnet.proto.actor.ActorReadyReport;
import com.kekwy.iarnet.resource.actor.ActorRegistry;
import com.kekwy.iarnet.resource.actor.WorkflowStartCoordinator;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * control-plane 侧的 DeviceAgentRegistryService gRPC 实现。
 * <p>
 * 通过 SignalingChannel 接收 Device Agent 上报的 ActorChannelStatus、ActorReadyReport，
 * 并通过同一通道向 Device Agent 下发 ActorDirective（如 StartSourceDirective）。
 * <p>
 * 当某设备的流异常/关闭时，会从 deviceIdToSender 移除该连接，以便设备重连后能重新注册，
 * 避免「旧流已失效但未清理导致后续上报/下发全部丢失」的问题。
 */
@Component
public class DeviceAgentRegistryGrpcService extends DeviceAgentRegistryServiceGrpc.DeviceAgentRegistryServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(DeviceAgentRegistryGrpcService.class);

    private final WorkflowStartCoordinator workflowStartCoordinator;
    private final ActorRegistry actorRegistry;

    /** device_id -> 该 Device Agent 的 SignalingChannel 发送端，用于下发指令 */
    private final ConcurrentHashMap<String, StreamObserver<SignalingMessage>> deviceIdToSender = new ConcurrentHashMap<>();

    public DeviceAgentRegistryGrpcService(WorkflowStartCoordinator workflowStartCoordinator,
                                         ActorRegistry actorRegistry) {
        this.workflowStartCoordinator = workflowStartCoordinator;
        this.actorRegistry = actorRegistry;
    }

    @Override
    public StreamObserver<SignalingMessage> signalingChannel(StreamObserver<SignalingMessage> responseObserver) {
        // 记录本流首次上报时使用的 deviceId，便于在 onError/onCompleted 时只移除本连接的映射
        AtomicReference<String> thisStreamDeviceId = new AtomicReference<>(null);

        return new StreamObserver<>() {
            @Override
            public void onNext(SignalingMessage value) {
                try {
                    String deviceId = value.getDeviceId() != null ? value.getDeviceId() : "";
                    thisStreamDeviceId.compareAndSet(null, deviceId);
                    deviceIdToSender.put(deviceId, responseObserver);

                    switch (value.getPayloadCase()) {
                        case ACTOR_CHANNEL -> handleActorChannel(value.getActorChannel());
                        case ACTOR_READY -> handleActorReady(deviceId, value.getActorReady(), responseObserver);
                        default -> { /* 其他类型当前忽略 */ }
                    }
                } catch (Exception e) {
                    // 任何未捕获异常会导致 gRPC 关闭流，Adapter 端会看到 "CANCELLED: Failed to read message"
                    log.error("SignalingChannel 处理消息异常，流将保持打开: payloadCase={}",
                            value != null ? value.getPayloadCase() : "null", e);
                }
            }

            @Override
            public void onError(Throwable t) {
                log.warn("SignalingChannel 异常: {}", t.getMessage());
                removeThisStream(thisStreamDeviceId.get(), responseObserver);
            }

            @Override
            public void onCompleted() {
                log.info("SignalingChannel 由 Device Agent 正常关闭");
                removeThisStream(thisStreamDeviceId.get(), responseObserver);
                try {
                    responseObserver.onCompleted();
                } catch (Exception e) {
                    log.debug("responseObserver.onCompleted() 异常（流已关闭可忽略）: {}", e.getMessage());
                }
            }
        };
    }

    /**
     * 仅当 map 中该 deviceId 仍指向当前 responseObserver 时才移除，避免误删重连后的新连接。
     */
    private void removeThisStream(String deviceId, StreamObserver<SignalingMessage> responseObserver) {
        if (deviceId == null) return;
        if (deviceIdToSender.remove(deviceId, responseObserver)) {
            log.info("已从 deviceIdToSender 移除断开的连接: deviceId={}", deviceId);
        }
    }

    private void handleActorReady(String deviceId, ActorReadyReport ready,
                                  StreamObserver<SignalingMessage> responseObserver) {
        String actorId = "actor-" + ready.getNodeId() + "-" + ready.getReplicaIndex();
        log.info("收到 ActorReadyReport(经 SignalingChannel): actorId={}, workflowId={}, nodeId={}",
                actorId, ready.getWorkflowId(), ready.getNodeId());

        StreamObserver<ActorDirective> directiveSender = new StreamObserver<>() {
            @Override
            public void onNext(ActorDirective directive) {
                try {
                    ActorDirectiveForward forward = ActorDirectiveForward.newBuilder()
                            .setActorId(actorId)
                            .setDirective(directive)
                            .build();
                    responseObserver.onNext(SignalingMessage.newBuilder()
                            .setTimestampMs(System.currentTimeMillis())
                            .setActorDirective(forward)
                            .build());
                } catch (Exception e) {
                    log.warn("经 SignalingChannel 下发 ActorDirective 失败: actorId={}", actorId, e);
                }
            }

            @Override
            public void onError(Throwable t) {
                log.warn("ActorDirective 流异常: actorId={}", actorId, t);
            }

            @Override
            public void onCompleted() {
                // 控制平面不主动关闭该流
            }
        };

        actorRegistry.onActorConnected(actorId, ready, directiveSender);
    }

    private void handleActorChannel(ActorChannelStatus status) {
        String workflowId = status.getWorkflowId();
        String src = status.getSrcActorAddr();
        String dst = status.getDstActorAddr();
        log.info("收到 ActorChannelStatus: workflowId={}, src={}, dst={}, connected={}",
                workflowId, src, dst, status.getConnected());

        if (status.getConnected()) {
            workflowStartCoordinator.onChannelConnected(workflowId, src, dst);
        }
    }
}

