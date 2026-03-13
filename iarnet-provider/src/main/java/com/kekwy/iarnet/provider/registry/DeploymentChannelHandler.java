package com.kekwy.iarnet.provider.registry;

import com.kekwy.iarnet.provider.engine.ProviderEngine;
import com.kekwy.iarnet.provider.actor.LocalActorGraph;
import com.kekwy.iarnet.provider.artifact.ArtifactFetcher;
import com.kekwy.iarnet.proto.provider.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 处理 DeploymentChannel 下发的 DeploymentEnvelope（DeployActor / StopActor / RemoveActor / GetActorStatus），
 * 通过 correlation_id 回传响应。
 */
public class DeploymentChannelHandler implements StreamObserver<DeploymentEnvelope> {

    private static final Logger log = LoggerFactory.getLogger(DeploymentChannelHandler.class);

    private final ProviderEngine engine;
    private final ArtifactFetcher artifactFetcher;
    private final StreamObserver<DeploymentEnvelope> responseObserver;
    private final Runnable onDisconnect;

    public DeploymentChannelHandler(ProviderEngine engine, ArtifactFetcher artifactFetcher,
                                    StreamObserver<DeploymentEnvelope> responseObserver,
                                    Runnable onDisconnect) {
        this.engine = engine;
        this.artifactFetcher = artifactFetcher;
        this.responseObserver = responseObserver;
        this.onDisconnect = onDisconnect;
    }

    @Override
    public void onNext(DeploymentEnvelope envelope) {
        String correlationId = envelope.getCorrelationId();
        String messageId = envelope.getMessageId();

        try {
            DeploymentEnvelope response = dispatch(envelope);
            if (response != null) {
                responseObserver.onNext(response);
            }
        } catch (Exception e) {
            log.error("处理部署消息失败: messageId={}, correlationId={}", messageId, correlationId, e);
        }
    }

    @Override
    public void onError(Throwable t) {
        log.warn("DeploymentChannel 出错: {}", t.getMessage());
        if (onDisconnect != null) onDisconnect.run();
    }

    @Override
    public void onCompleted() {
        log.info("DeploymentChannel 由服务端关闭");
        if (onDisconnect != null) onDisconnect.run();
    }

    private DeploymentEnvelope dispatch(DeploymentEnvelope envelope) throws IOException {
        String correlationId = envelope.getCorrelationId();
        String messageId = envelope.getMessageId();

        switch (envelope.getMessageCase()) {
            case DEPLOY_ACTOR_REQUEST:
                return handleDeployActor(correlationId, messageId, envelope.getDeployActorRequest());

            case STOP_ACTOR_REQUEST:
                StopActorResponse stopResp = engine.stopActor(envelope.getStopActorRequest().getActorId());
                return DeploymentEnvelope.newBuilder()
                        .setCorrelationId(correlationId)
                        .setMessageId(messageId)
                        .setStopActorResponse(stopResp)
                        .build();

            case REMOVE_ACTOR_REQUEST:
                RemoveActorResponse removeResp = engine.removeActor(envelope.getRemoveActorRequest().getActorId());
                return DeploymentEnvelope.newBuilder()
                        .setCorrelationId(correlationId)
                        .setMessageId(messageId)
                        .setRemoveActorResponse(removeResp)
                        .build();

            case GET_ACTOR_STATUS_REQUEST:
                GetActorStatusResponse statusResp = engine.getActorStatus(envelope.getGetActorStatusRequest().getActorId());
                return DeploymentEnvelope.newBuilder()
                        .setCorrelationId(correlationId)
                        .setMessageId(messageId)
                        .setGetActorStatusResponse(statusResp)
                        .build();

            default:
                log.debug("忽略 DeploymentEnvelope: {}", envelope.getMessageCase());
                return null;
        }
    }

    private DeploymentEnvelope handleDeployActor(String correlationId, String messageId,
                                                DeployActorRequest request) throws IOException {
        Path artifactPath = null;
        if (request.getArtifactUrl() != null && !request.getArtifactUrl().isBlank()) {
            artifactPath = artifactFetcher.fetch(request.getActorId(), request.getArtifactUrl());
        }

        String actorId = request.getActorId();
        LocalActorGraph graph = LocalActorGraph.getInstance();
        graph.onDeploy(actorId, request.getDownstreamActorAddrsList());

        var localAgent = graph.getActorRegistrationService();
        if (localAgent != null) {
            localAgent.setDownstreamsForActor(actorId, request.getDownstreamActorAddrsList());
        }

        DeployActorResponse response = engine.deployActor(request, artifactPath);
        return DeploymentEnvelope.newBuilder()
                .setCorrelationId(correlationId)
                .setMessageId(messageId)
                .setDeployActorResponse(response)
                .build();
    }
}
