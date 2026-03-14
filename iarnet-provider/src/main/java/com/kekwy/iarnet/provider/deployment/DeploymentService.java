package com.kekwy.iarnet.provider.deployment;

import com.kekwy.iarnet.provider.actor.ActorRouter;
import com.kekwy.iarnet.provider.artifact.ArtifactFetcher;
import com.kekwy.iarnet.provider.artifact.ArtifactStore;
import com.kekwy.iarnet.provider.signaling.SignalingService;
import com.kekwy.iarnet.provider.config.ProviderIdentity;
import com.kekwy.iarnet.provider.engine.ProviderEngine;
import com.kekwy.iarnet.provider.registry.DelegatingObserver;
import com.kekwy.iarnet.provider.registry.ProviderRegistryClient;
import com.kekwy.iarnet.proto.fabric.ProviderRegistryServiceGrpc;
import com.kekwy.iarnet.proto.provider.DeployActorRequest;
import com.kekwy.iarnet.proto.provider.DeployActorResponse;
import com.kekwy.iarnet.proto.provider.DeploymentEnvelope;
import com.kekwy.iarnet.proto.provider.GetActorStatusRequest;
import com.kekwy.iarnet.proto.provider.GetActorStatusResponse;
import com.kekwy.iarnet.proto.provider.RemoveActorRequest;
import com.kekwy.iarnet.proto.provider.RemoveActorResponse;
import com.kekwy.iarnet.proto.provider.DownstreamGroup;
import com.kekwy.iarnet.proto.provider.StopActorRequest;
import com.kekwy.iarnet.proto.provider.StopActorResponse;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 管理 DeploymentChannel 生命周期；asyncStub、scheduler 及业务依赖由 Spring 构造注入，
 * providerId 从 {@link ProviderIdentity} 读取。
 */
@Service
public class DeploymentService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentService.class);
    private static final long RECONNECT_DELAY_SECONDS = 5;

    private final ProviderRegistryServiceGrpc.ProviderRegistryServiceStub asyncStub;
    private final ScheduledExecutorService scheduler;
    private final ProviderEngine engine;
    private final ArtifactFetcher artifactFetcher;
    private final ArtifactStore artifactStore;
    private final ActorRouter actorRouter;
    private final ProviderIdentity identity;

    private volatile boolean closed = false;

    public DeploymentService(ProviderRegistryServiceGrpc.ProviderRegistryServiceStub providerRegistryAsyncStub,
                             ScheduledExecutorService providerScheduler,
                             ProviderEngine engine,
                             ArtifactFetcher artifactFetcher,
                             ArtifactStore artifactStore,
                             ActorRouter actorRouter,
                             ProviderIdentity identity) {
        this.asyncStub = providerRegistryAsyncStub;
        this.scheduler = providerScheduler;
        this.engine = engine;
        this.artifactFetcher = artifactFetcher;
        this.artifactStore = artifactStore;
        this.actorRouter = actorRouter;
        this.identity = identity;
    }

    /** 建立 Deployment 流（providerId 从 ProviderIdentity 读取，重连时同样）。 */
    public void openChannel() {
        String providerId = identity != null ? identity.getProviderId() : null;
        if (closed || asyncStub == null || providerId == null) return;

        DelegatingObserver<DeploymentEnvelope> proxy = new DelegatingObserver<>();
        StreamObserver<DeploymentEnvelope> receiver = new DeploymentMessageDispatcher(this, proxy, this::onDisconnect);

        Metadata headers = new Metadata();
        headers.put(ProviderRegistryClient.PROVIDER_ID_METADATA_KEY, providerId);
        var headerStub = asyncStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));

        StreamObserver<DeploymentEnvelope> sender = headerStub.deploymentChannel(receiver);
        proxy.setDelegate(sender);

        log.info("DeploymentChannel 已建立: providerId={}", providerId);
    }

    private void onDisconnect() {
        if (closed) return;
        log.warn("DeploymentChannel 断开，{}s 后重连...", RECONNECT_DELAY_SECONDS);
        scheduler.schedule(this::openChannel, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    public void close() {
        closed = true;
    }

    // --- 业务方法，供 DeploymentMessageDispatcher 委托调用 ---

    @SuppressWarnings("ConstantValue")
    public DeployActorResponse deployActor(DeployActorRequest request) throws IOException {
        Path artifactPath = null;
        String artifactUrl = request.getArtifactUrl();
        if (artifactUrl != null && !artifactUrl.isBlank()) {
            artifactPath = artifactFetcher.fetch(request.getActorId(), artifactUrl);
        }
        String actorId = request.getActorId();
        actorRouter.registerActorRouting(actorId, request.getInstanceIndex(),
                request.getUpstreamActorAddrsList(), request.getDownstreamGroupsList());

        Map<Integer, Path> conditionFunctionPaths = new HashMap<>();
        for (DownstreamGroup group : request.getDownstreamGroupsList()) {
            if (!group.hasConditionFunction()) {
                continue;
            }
            int port = group.getOutputPort();
            Path path = artifactStore.storeConditionFunction(actorId, port, group.getConditionFunction().toByteArray());
            conditionFunctionPaths.put(port, path);
        }

        return engine.deployActor(request, artifactPath, conditionFunctionPaths);
    }

    public StopActorResponse stopActor(StopActorRequest request) {
        return engine.stopActor(request.getActorId());
    }

    public RemoveActorResponse removeActor(RemoveActorRequest request) {
        return engine.removeActor(request.getActorId());
    }

    public GetActorStatusResponse getActorStatus(GetActorStatusRequest request) {
        return engine.getActorStatus(request.getActorId());
    }
}
