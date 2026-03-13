package com.kekwy.iarnet.provider.registry;

import com.kekwy.iarnet.provider.actor.ActorRouter;
import com.kekwy.iarnet.provider.control.ControlService;
import com.kekwy.iarnet.provider.deployment.DeploymentService;
import com.kekwy.iarnet.provider.engine.ProviderEngine;
import com.kekwy.iarnet.provider.artifact.ArtifactFetcher;
import com.kekwy.iarnet.proto.fabric.ProviderRegistryServiceGrpc;
import com.kekwy.iarnet.proto.provider.*;
import com.kekwy.iarnet.provider.signaling.SignalingService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Provider 注册客户端：RegisterProvider(unary) + ControlChannel / DeploymentChannel / SignalingChannel 三个双向流。
 * 心跳通过 ControlChannel 发送 ProviderHeartbeat，接收 ProviderHeartbeatAck。
 */
public class ProviderRegistryClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ProviderRegistryClient.class);
    private static final long RECONNECT_DELAY_SECONDS = 5;

    private static final Metadata.Key<String> PROVIDER_ID_METADATA_KEY =
            Metadata.Key.of("provider-id", Metadata.ASCII_STRING_MARSHALLER);

    private final String providerName;
    private final String description;
    private final String zone;
    private final String providerType;
    private final List<String> tags;
    private final ProviderEngine engine;
    private final ArtifactFetcher artifactFetcher;
    private final ActorRouter actorRouter;

    private final ManagedChannel channel;
    private final ProviderRegistryServiceGrpc.ProviderRegistryServiceBlockingStub blockingStub;
    private final ProviderRegistryServiceGrpc.ProviderRegistryServiceStub asyncStub;
    private final ScheduledExecutorService scheduler;

    @Getter
    private volatile String providerId;
    private volatile boolean registered = false;
    private volatile boolean closed = false;

    private final ControlService controlService;
    private final DeploymentService deploymentService;
    private final SignalingService signalingService;

    public ProviderRegistryClient(String providerName, String description, String zone,
                                  String providerType, List<String> tags,
                                  ProviderEngine engine, ArtifactFetcher artifactFetcher,
                                  ActorRouter actorRouter,
                                  String cpHost, int cpPort,
                                  ControlService controlService, DeploymentService deploymentService, SignalingService signalingService) {
        this.providerName = providerName != null ? providerName : "provider";
        this.description = description != null ? description : "";
        this.zone = zone != null ? zone : "";
        this.providerType = providerType != null ? providerType : "docker";
        this.tags = tags != null ? tags : List.of();
        this.engine = engine;
        this.artifactFetcher = artifactFetcher;
        this.actorRouter = actorRouter;

        this.channel = ManagedChannelBuilder
                .forAddress(cpHost, cpPort)
                .usePlaintext()
                .build();
        this.blockingStub = ProviderRegistryServiceGrpc.newBlockingStub(channel);
        this.asyncStub = ProviderRegistryServiceGrpc.newStub(channel);
        this.scheduler = Executors.newScheduledThreadPool(2);

        this.controlService = controlService;
        this.deploymentService = deploymentService;
        this.signalingService = signalingService;
    }

    public void start() {
        RegisterProviderRequest request = RegisterProviderRequest.newBuilder()
                .setProviderName(providerName)
                .setProviderType(providerType)
                .setDescription(description)
                .setZone(zone)
                .addAllTags(tags)
                .build();

        try {
            RegisterProviderResponse response = blockingStub.registerProvider(request);
            if (response.getAccepted()) {
                providerId = response.getProviderId();
                registered = true;
                log.info("Provider 注册成功: providerId={}, name={}", providerId, providerName);
                openControlChannel();
                openDeploymentChannel();
                openSignalingChannel();
            } else {
                log.error("Provider 注册被拒绝: name={}, message={}", providerName, response.getMessage());
            }
        } catch (Exception e) {
            log.error("Provider 注册失败: name={}", providerName, e);
        }
    }

    @Override
    public void close() {
        closed = true;
        scheduler.shutdownNow();
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            channel.shutdownNow();
        }
    }

    private void openControlChannel() {
        if (closed) return;

        DelegatingObserver<ControlEnvelope> proxy = new DelegatingObserver<>();

        StreamObserver<ControlEnvelope> receiver = new ControlChannelHandler(this::onControlChannelDisconnect);

        Metadata headers = new Metadata();
        headers.put(PROVIDER_ID_METADATA_KEY, providerId);
        var headerStub = asyncStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));

        StreamObserver<ControlEnvelope> sender = headerStub.controlChannel(receiver);
        proxy.setDelegate(sender);

        startHeartbeat(proxy);
        log.info("ControlChannel 已建立: providerId={}", providerId);
    }

    private void startHeartbeat(DelegatingObserver<ControlEnvelope> controlSender) {
        scheduler.scheduleAtFixedRate(() -> {
            if (!registered || providerId == null || closed) return;
            try {
                ControlEnvelope heartbeat = ControlEnvelope.newBuilder()
                        .setProviderHeartbeat(ProviderHeartbeat.newBuilder()
                                .setProviderId(providerId)
                                .setTimestampMs(System.currentTimeMillis())
                                .addAllTags(tags)
                                .build())
                        .build();
                controlSender.onNext(heartbeat);
            } catch (Exception e) {
                log.warn("发送心跳失败: providerId={}", providerId, e);
            }
        }, 30, 30, TimeUnit.SECONDS);
        log.info("心跳已启动: 间隔=30s, providerId={}", providerId);
    }

    private void onControlChannelDisconnect() {
        if (closed || !registered) return;
        log.warn("ControlChannel 断开，{}s 后重连...", RECONNECT_DELAY_SECONDS);
        scheduler.schedule(this::openControlChannel, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private void openDeploymentChannel() {
        if (closed) return;

        DelegatingObserver<DeploymentEnvelope> proxy = new DelegatingObserver<>();

        StreamObserver<DeploymentEnvelope> receiver = new DeploymentChannelHandler(
                engine, artifactFetcher, actorRouter, proxy, this::onDeploymentChannelDisconnect);

        Metadata headers = new Metadata();
        headers.put(PROVIDER_ID_METADATA_KEY, providerId);
        var headerStub = asyncStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));

        StreamObserver<DeploymentEnvelope> sender = headerStub.deploymentChannel(receiver);
        proxy.setDelegate(sender);

        log.info("DeploymentChannel 已建立: providerId={}", providerId);
    }

    private void onDeploymentChannelDisconnect() {
        if (closed || !registered) return;
        log.warn("DeploymentChannel 断开，{}s 后重连...", RECONNECT_DELAY_SECONDS);
        scheduler.schedule(this::openDeploymentChannel, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private void openSignalingChannel() {
        if (closed) return;

        DelegatingObserver<SignalingEnvelope> proxy = new DelegatingObserver<>();

        StreamObserver<SignalingEnvelope> receiver = new SignalingChannelHandler(providerId, actorRouter, proxy, this::onSignalingChannelDisconnect);

        Metadata headers = new Metadata();
        headers.put(PROVIDER_ID_METADATA_KEY, providerId);
        var headerStub = asyncStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));

        StreamObserver<SignalingEnvelope> sender = headerStub.signalingChannel(receiver);
        proxy.setDelegate(sender);

        actorRouter.setSignalingSender(proxy);
        actorRouter.setProviderId(providerId);

        log.info("SignalingChannel 已建立: providerId={}", providerId);
    }

    private void onSignalingChannelDisconnect() {
        if (closed || !registered) return;
        log.warn("SignalingChannel 断开，{}s 后重连...", RECONNECT_DELAY_SECONDS);
        scheduler.schedule(this::openSignalingChannel, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private static class DelegatingObserver<T> implements StreamObserver<T> {
        private volatile StreamObserver<T> delegate;

        void setDelegate(StreamObserver<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onNext(T value) {
            if (delegate != null) delegate.onNext(value);
        }

        @Override
        public void onError(Throwable t) {
            if (delegate != null) delegate.onError(t);
        }

        @Override
        public void onCompleted() {
            if (delegate != null) delegate.onCompleted();
        }
    }
}
