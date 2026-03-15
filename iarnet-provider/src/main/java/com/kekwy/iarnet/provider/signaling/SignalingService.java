package com.kekwy.iarnet.provider.signaling;

import com.kekwy.iarnet.provider.actor.ActorRouter;
import com.kekwy.iarnet.provider.config.ProviderIdentity;
import com.kekwy.iarnet.provider.registry.DelegatingObserver;
import com.kekwy.iarnet.provider.registry.ProviderRegistryClient;
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

import java.util.concurrent.ConcurrentLinkedQueue;
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
    /** Channel 未就绪时暂存的上报，建立/重连后统一发送 */
    private final ConcurrentLinkedQueue<SignalingEnvelope> pendingReports = new ConcurrentLinkedQueue<>();

    public SignalingService(ProviderRegistryServiceGrpc.ProviderRegistryServiceStub providerRegistryAsyncStub,
                            ScheduledExecutorService providerScheduler,
                            ActorRouter actorRouter,
                            ProviderIdentity identity) {
        this.asyncStub = providerRegistryAsyncStub;
        this.scheduler = providerScheduler;
        this.actorRouter = actorRouter;
        this.identity = identity;
    }

    /** 建立 Signaling 流（providerId 从 ProviderIdentity 读取，重连时同样）。重连前先关闭旧流，避免多流并存导致对端或框架主动关闭。 */
    public void openChannel() {
        String providerId = identity != null ? identity.getProviderId() : null;
        if (closed || asyncStub == null || providerId == null) return;

        StreamObserver<SignalingEnvelope> prev = this.signalingSender;
        this.signalingSender = null;
        if (prev != null) {
            try {
                prev.onCompleted();
            } catch (Exception e) {
                log.debug("关闭旧 Signaling 流: {}", e.getMessage());
            }
        }

        DelegatingObserver<SignalingEnvelope> proxy = new DelegatingObserver<>();
        StreamObserver<SignalingEnvelope> receiver = new SignalingMessageDispatcher(this, proxy, this::onDisconnect);

        Metadata headers = new Metadata();
        headers.put(ProviderRegistryClient.PROVIDER_ID_METADATA_KEY, providerId);
        var headerStub = asyncStub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));

        StreamObserver<SignalingEnvelope> sender = headerStub.signalingChannel(receiver);
        proxy.setDelegate(sender);
        this.signalingSender = proxy;

        log.info("SignalingChannel 已建立: providerId={}", providerId);
        flushPendingReports();
    }

    private void onDisconnect() {
        StreamObserver<SignalingEnvelope> oldSender = this.signalingSender;
        this.signalingSender = null;
        if (closed) return;
        log.warn("SignalingChannel 断开，{}s 后重连...", RECONNECT_DELAY_SECONDS);
        scheduler.schedule(() -> {
            if (oldSender != null) {
                try { oldSender.onCompleted(); } catch (Exception e) { log.trace("关闭旧流: {}", e.getMessage()); }
            }
            openChannel();
        }, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    /** 将暂存的上报按序发出（channel 建立/重连后调用） */
    private void flushPendingReports() {
        StreamObserver<SignalingEnvelope> sender = this.signalingSender;
        if (sender == null) return;
        SignalingEnvelope envelope;
        int count = 0;
        while ((envelope = pendingReports.poll()) != null) {
            try {
                sender.onNext(envelope);
                count++;
            } catch (Exception e) {
                log.warn("重放暂存上报失败，已丢弃剩余 {} 条", pendingReports.size() + 1, e);
                break;
            }
        }
        if (count > 0) {
            log.info("SignalingChannel 已重放 {} 条暂存上报", count);
        }
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

    /** 上报 Actor 就绪（供 ActorRegistrationServiceGrpcImpl 等调用）。Channel 未就绪时入队，建立后重放。 */
    public void reportActorReady(String actorId) {
        String providerId = identity != null ? identity.getProviderId() : "";
        SignalingEnvelope msg = SignalingEnvelope.newBuilder()
                .setProviderId(providerId != null ? providerId : "")
                .setTimestampMs(System.currentTimeMillis())
                .setActorReady(ActorReadyReport.newBuilder().setActorId(actorId).build())
                .build();
        if (!sendOrEnqueue(msg, "ActorReadyReport", actorId)) {
            log.debug("SignalingChannel 未就绪，ActorReadyReport 已入队: actorId={}", actorId);
        }
    }

    /** 上报本地通道已建立（供 ActorRegistrationServiceGrpcImpl、DeploymentService 等调用）。Channel 未就绪时入队。 */
    public void reportChannelEstablished(String srcActorId, String dstActorId) {
        String providerId = identity != null ? identity.getProviderId() : "";
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
        if (!sendOrEnqueue(msg, "ActorChannelStatus", srcActorId + "->" + dstActorId)) {
            log.debug("SignalingChannel 未就绪，ActorChannelStatus 已入队: src={}, dst={}", srcActorId, dstActorId);
        }
    }

    /**
     * 若 channel 已建立则立即发送，否则入队。
     * @return true 表示已发送，false 表示已入队
     */
    private boolean sendOrEnqueue(SignalingEnvelope msg, String reportType, String detail) {
        StreamObserver<SignalingEnvelope> sender = this.signalingSender;
        if (sender != null) {
            try {
                sender.onNext(msg);
                log.info("SignalingService: 已上报 {}: {}", reportType, detail);
                return true;
            } catch (Exception e) {
                log.warn("SignalingService: 上报 {} 失败: {}", reportType, detail, e);
                pendingReports.offer(msg);
                return false;
            }
        }
        pendingReports.offer(msg);
        return false;
    }
}
