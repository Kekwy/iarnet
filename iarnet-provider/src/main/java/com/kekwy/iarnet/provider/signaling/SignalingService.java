package com.kekwy.iarnet.provider.signaling;

import com.kekwy.iarnet.provider.actor.ActorRouter;
import com.kekwy.iarnet.provider.config.ProviderIdentity;
import com.kekwy.iarnet.provider.registry.DelegatingObserver;
import com.kekwy.iarnet.provider.registry.ProviderRegistryClient;
import com.kekwy.iarnet.proto.actor.ActorEnvelope;
import com.kekwy.iarnet.proto.provider.ActorMessageForward;
import com.kekwy.iarnet.proto.fabric.ProviderRegistryServiceGrpc;
import com.kekwy.iarnet.proto.provider.ActorChannelStatus;
import com.kekwy.iarnet.proto.provider.ActorReadyReport;
import com.kekwy.iarnet.proto.provider.ConnectInstruction;
import com.kekwy.iarnet.proto.provider.IceEnvelope;
import com.kekwy.iarnet.proto.provider.SignalingEnvelope;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 管理 SignalingChannel 生命周期；asyncStub、scheduler、actorRouter 由 Spring 构造注入，
 * providerId 从 {@link ProviderIdentity} 读取。
 */
@Service
public class SignalingService {

    private static final Logger log = LoggerFactory.getLogger(SignalingService.class);
    private static final long RECONNECT_DELAY_SECONDS = 5;

    private final ProviderRegistryServiceGrpc.ProviderRegistryServiceStub asyncStub;
    private final ScheduledExecutorService scheduler;
    private final ActorRouter actorRouter;
    private final ProviderIdentity identity;

    private volatile boolean closed = false;
    private volatile StreamObserver<SignalingEnvelope> signalingSender;

    public SignalingService(ProviderRegistryServiceGrpc.ProviderRegistryServiceStub providerRegistryAsyncStub,
                            ScheduledExecutorService providerScheduler,
                            ActorRouter actorRouter,
                            ProviderIdentity identity) {
        this.asyncStub = providerRegistryAsyncStub;
        this.scheduler = providerScheduler;
        this.actorRouter = actorRouter;
        this.identity = identity;
    }

    /** 建立 Signaling 流（providerId 从 ProviderIdentity 读取，重连时同样）。 */
    public void openChannel() {
        String providerId = identity != null ? identity.getProviderId() : null;
        if (closed || asyncStub == null || providerId == null) return;

        DelegatingObserver<SignalingEnvelope> proxy = new DelegatingObserver<>();
        StreamObserver<SignalingEnvelope> receiver = new SignalingMessageDispatcher(this, proxy, this::onDisconnect);

        Metadata headers = new Metadata();
        headers.put(ProviderRegistryClient.PROVIDER_ID_METADATA_KEY, providerId);
        var headerStub = asyncStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));

        StreamObserver<SignalingEnvelope> sender = headerStub.signalingChannel(receiver);
        proxy.setDelegate(sender);
        this.signalingSender = proxy;

        log.info("SignalingChannel 已建立: providerId={}", providerId);
    }

    private void onDisconnect() {
        if (closed) return;
        log.warn("SignalingChannel 断开，{}s 后重连...", RECONNECT_DELAY_SECONDS);
        scheduler.schedule(this::openChannel, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    public void close() {
        closed = true;
    }

    // --- 业务方法，供 SignalingMessageDispatcher 委托调用 ---

    /** 跨 Provider 转发：从 ActorMessageForward 取 target 与 envelope，投递到本地 actor。 */
    public void forwardEnvelopeToActor(ActorMessageForward forward) {
        if (forward == null) return;
        String targetActorId = forward.getTarget();
        if (targetActorId == null || targetActorId.isBlank()) {
            log.warn("ActorMessageForward 缺少 target，无法转发");
            return;
        }
        actorRouter.deliverToTarget(targetActorId, forward.getActorEnvelope());
    }

    public void handleConnectInstruction(ConnectInstruction instruction) {
        log.debug("收到 ConnectInstruction: connectId={}", instruction != null ? instruction.getConnectId() : null);
    }

    public void handleIceEnvelope(IceEnvelope iceEnvelope) {
        log.debug("收到 IceEnvelope: connectId={}", iceEnvelope != null ? iceEnvelope.getConnectId() : null);
    }

    /** 上报 Actor 就绪（供 ActorRegistrationServiceGrpcImpl 等调用）。 */
    public void reportActorReady(String actorId) {
        StreamObserver<SignalingEnvelope> sender = this.signalingSender;
        if (sender == null) return;
        String providerId = identity != null ? identity.getProviderId() : "";
        try {
            SignalingEnvelope msg = SignalingEnvelope.newBuilder()
                    .setProviderId(providerId != null ? providerId : "")
                    .setTimestampMs(System.currentTimeMillis())
                    .setActorReady(ActorReadyReport.newBuilder().setActorId(actorId).build())
                    .build();
            sender.onNext(msg);
            log.info("SignalingService: 已上报 ActorReadyReport: actorId={}", actorId);
        } catch (Exception e) {
            log.warn("SignalingService: 上报 ActorReadyReport 失败: actorId={}", actorId, e);
        }
    }

    /** 上报本地通道已建立（供 ActorRegistrationServiceGrpcImpl、DeploymentService 等调用）。 */
    public void reportChannelEstablished(String srcActorId, String dstActorId) {
        StreamObserver<SignalingEnvelope> sender = this.signalingSender;
        if (sender == null) return;
        String providerId = identity != null ? identity.getProviderId() : "";
        try {
            SignalingEnvelope msg = SignalingEnvelope.newBuilder()
                    .setProviderId(providerId != null ? providerId : "")
                    .setTimestampMs(System.currentTimeMillis())
                    .setActorChannel(ActorChannelStatus.newBuilder()
                            .setWorkflowId("")
                            .setApplicationId("")
                            .setSrcActorAddr(srcActorId)
                            .setDstActorAddr(dstActorId)
                            .setConnected(true)
                            .build())
                    .build();
            sender.onNext(msg);
            log.info("SignalingService: 已上报 ActorChannelStatus: src={}, dst={}", srcActorId, dstActorId);
        } catch (Exception e) {
            log.warn("SignalingService: 上报 ActorChannelStatus 失败: src={}, dst={}", srcActorId, dstActorId, e);
        }
    }
}
