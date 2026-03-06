package com.kekwy.iarnet.adapter.registry;

import com.kekwy.iarnet.adapter.artifact.ArtifactFetcher;
import com.kekwy.iarnet.adapter.engine.AdapterEngine;
import com.kekwy.iarnet.proto.adapter.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 命令通道处理器：处理 control-plane 通过 CommandChannel 双向流下发的指令。
 * <p>
 * 本类作为 {@code StreamObserver<Command>} 接收 control-plane 推送的 {@link Command}，
 * 委托给 {@link AdapterEngine} 执行，将结果通过 {@code responseObserver} 回传。
 * <p>
 * 对于 Artifact 传输，支持多个 chunk 的聚合（同一 request_id 的连续 chunk）。
 */
public class CommandChannelHandler implements StreamObserver<Command> {

    private static final Logger log = LoggerFactory.getLogger(CommandChannelHandler.class);

    private final AdapterEngine engine;
    private final ArtifactFetcher artifactFetcher;
    private final StreamObserver<CommandResponse> responseObserver;
    private final Runnable onDisconnect;

    /** artifact 传输缓冲：request_id → 聚合上下文 */
    private final Map<String, ArtifactTransferContext> artifactBuffers = new ConcurrentHashMap<>();

    public CommandChannelHandler(AdapterEngine engine,
                                 ArtifactFetcher artifactFetcher,
                                 StreamObserver<CommandResponse> responseObserver,
                                 Runnable onDisconnect) {
        this.engine = engine;
        this.artifactFetcher = artifactFetcher;
        this.responseObserver = responseObserver;
        this.onDisconnect = onDisconnect;
    }

    @Override
    public void onNext(Command command) {
        String requestId = command.getRequestId();
        try {
            CommandResponse response = dispatch(command);
            if (response != null) {
                responseObserver.onNext(response);
            }
        } catch (Exception e) {
            log.error("处理命令失败: requestId={}", requestId, e);
            responseObserver.onNext(CommandResponse.newBuilder()
                    .setRequestId(requestId)
                    .setError(CommandError.newBuilder()
                            .setMessage(e.getMessage())
                            .build())
                    .build());
        }
    }

    @Override
    public void onError(Throwable t) {
        log.warn("CommandChannel 连接断开: {}", t.getMessage());
        artifactBuffers.clear();
        if (onDisconnect != null) onDisconnect.run();
    }

    @Override
    public void onCompleted() {
        log.info("CommandChannel 由 control-plane 正常关闭");
        artifactBuffers.clear();
        if (onDisconnect != null) onDisconnect.run();
    }

    private CommandResponse dispatch(Command command) throws IOException {
        String rid = command.getRequestId();

        return switch (command.getPayloadCase()) {
            case GET_DEVICE_INFO -> CommandResponse.newBuilder()
                    .setRequestId(rid)
                    .setGetDeviceInfo(engine.getDeviceInfo())
                    .build();

            case TRANSFER_ARTIFACT -> handleTransferArtifact(rid, command.getTransferArtifact());

            case DEPLOY_INSTANCE -> handleDeployInstance(rid, command.getDeployInstance());

            case STOP_INSTANCE -> CommandResponse.newBuilder()
                    .setRequestId(rid)
                    .setStopInstance(engine.stopInstance(command.getStopInstance().getInstanceId()))
                    .build();

            case REMOVE_INSTANCE -> CommandResponse.newBuilder()
                    .setRequestId(rid)
                    .setRemoveInstance(engine.removeInstance(command.getRemoveInstance().getInstanceId()))
                    .build();

            case GET_INSTANCE_STATUS -> CommandResponse.newBuilder()
                    .setRequestId(rid)
                    .setGetInstanceStatus(engine.getInstanceStatus(
                            command.getGetInstanceStatus().getInstanceId()))
                    .build();

            case GET_RESOURCE_USAGE -> CommandResponse.newBuilder()
                    .setRequestId(rid)
                    .setGetResourceUsage(engine.getResourceUsage())
                    .build();

            case GET_NETWORK_CANDIDATES -> CommandResponse.newBuilder()
                    .setRequestId(rid)
                    .setGetNetworkCandidates(GetNetworkCandidatesResponse.getDefaultInstance())
                    .build();

            case PAYLOAD_NOT_SET -> {
                log.warn("收到空 payload 的命令: requestId={}", rid);
                yield CommandResponse.newBuilder()
                        .setRequestId(rid)
                        .setError(CommandError.newBuilder()
                                .setMessage("payload 为空")
                                .build())
                        .build();
            }
        };
    }

    /**
     * 若请求带 artifact_url 则先拉取到本地（按 artifact_id 去重），再部署。
     */
    private CommandResponse handleDeployInstance(String requestId, DeployInstanceRequest request) throws IOException {
        Path artifactPath = null;
        if (!request.getArtifactUrl().isBlank()
                && !request.getArtifactId().isBlank()) {
            artifactPath = artifactFetcher.fetch(request.getArtifactId(), request.getArtifactUrl());
        }

        // 仅处理“所有 Actor 在同一 Device”场景下的本地拓扑记录：
        // 从 env_vars 中解析当前 ActorAddr 以及下游 ActorAddr 列表，
        // 在 LocalActorGraph 中记录边关系，后续由 LocalActorGraph 在 Actor
        // 完成 LocalChannel 注册后判断通道是否就绪。
        String actorAddr = request.getEnvVarsOrDefault("IARNET_ACTOR_ADDR", "");
        if (!actorAddr.isBlank()) {
            com.kekwy.iarnet.adapter.agent.LocalActorGraph.getInstance()
                    .onDeploy(actorAddr, request.getDownstreamActorAddrsList());
        }

        DeployInstanceResponse response = engine.deployInstance(request, artifactPath);
        return CommandResponse.newBuilder()
                .setRequestId(requestId)
                .setDeployInstance(response)
                .build();
    }

    /**
     * 处理 artifact 分块传输。
     * 同一 request_id 的多个 chunk 被聚合到缓冲区，
     * 当收到 last_chunk=true 时，完成存储并返回响应。
     * 中间 chunk 返回 null（不发送响应）。
     */
    private CommandResponse handleTransferArtifact(String requestId,
                                                    TransferArtifactChunk chunk) throws IOException {
        ArtifactTransferContext ctx = artifactBuffers.computeIfAbsent(
                requestId, k -> new ArtifactTransferContext());

        if (!chunk.getArtifactId().isEmpty()) {
            ctx.artifactId = chunk.getArtifactId();
        }
        if (!chunk.getFileName().isEmpty()) {
            ctx.fileName = chunk.getFileName();
        }
        chunk.getData().writeTo(ctx.buffer);

        if (chunk.getLastChunk()) {
            artifactBuffers.remove(requestId);
            TransferArtifactResponse response = engine.transferArtifact(
                    ctx.artifactId, ctx.fileName,
                    new ByteArrayInputStream(ctx.buffer.toByteArray()));
            log.info("Artifact 传输完成: artifactId={}, size={}", ctx.artifactId, ctx.buffer.size());
            return CommandResponse.newBuilder()
                    .setRequestId(requestId)
                    .setTransferArtifact(response)
                    .build();
        }

        return null;
    }

    private static class ArtifactTransferContext {
        String artifactId;
        String fileName;
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    }
}
