package com.kekwy.iarnet.rpc;

import com.kekwy.iarnet.proto.agent.ActorChannelStatus;
import com.kekwy.iarnet.proto.agent.DeviceAgentRegistryServiceGrpc;
import com.kekwy.iarnet.proto.agent.SignalingMessage;
import com.kekwy.iarnet.resource.actor.WorkflowStartCoordinator;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * control-plane 侧的 DeviceAgentRegistryService gRPC 实现。
 * <p>
 * 当前仅实现 SignalingChannel，用于接收 Device Agent 上报的 ActorChannelStatus，
 * 并通知 WorkflowStartCoordinator，当所有 Actor Ready 且链路已建立后启动 Source。
 */
@Component
public class DeviceAgentRegistryGrpcService extends DeviceAgentRegistryServiceGrpc.DeviceAgentRegistryServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(DeviceAgentRegistryGrpcService.class);

    private final WorkflowStartCoordinator workflowStartCoordinator;

    public DeviceAgentRegistryGrpcService(WorkflowStartCoordinator workflowStartCoordinator) {
        this.workflowStartCoordinator = workflowStartCoordinator;
    }

    @Override
    public StreamObserver<SignalingMessage> signalingChannel(StreamObserver<SignalingMessage> responseObserver) {
        return new StreamObserver<>() {
            @Override
            public void onNext(SignalingMessage value) {
                switch (value.getPayloadCase()) {
                    case ACTOR_CHANNEL -> handleActorChannel(value.getActorChannel());
                    default -> { /* 其他类型当前忽略 */ }
                }
            }

            @Override
            public void onError(Throwable t) {
                log.warn("SignalingChannel 异常: {}", t.getMessage());
            }

            @Override
            public void onCompleted() {
                log.info("SignalingChannel 由 Device Agent 正常关闭");
                responseObserver.onCompleted();
            }
        };
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

