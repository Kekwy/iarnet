package com.kekwy.iarnet.adapter.registry;

import com.kekwy.iarnet.adapter.engine.AdapterEngine;
import com.kekwy.iarnet.proto.adapter.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 注册客户端：管理与 control-plane 的全部通信。
 * <p>
 * 生命周期：Register → CommandChannel → Heartbeat → Deregister。
 * 所有连接均由 Adapter 主动发起，Adapter 无需暴露任何端口。
 */
public class RegistryClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RegistryClient.class);
    private static final long RECONNECT_DELAY_SECONDS = 5;

    private static final Metadata.Key<String> ADAPTER_ID_METADATA_KEY =
            Metadata.Key.of("adapter-id", Metadata.ASCII_STRING_MARSHALLER);

    private final String adapterName;
    private final String description;
    private final AdapterEngine engine;
    private final com.kekwy.iarnet.adapter.artifact.ArtifactFetcher artifactFetcher;
    private final List<String> tags;

    private final ManagedChannel channel;
    private final AdapterRegistryServiceGrpc.AdapterRegistryServiceBlockingStub blockingStub;
    private final AdapterRegistryServiceGrpc.AdapterRegistryServiceStub asyncStub;
    private final ScheduledExecutorService scheduler;

    private volatile String adapterId;
    private volatile boolean registered = false;
    private volatile boolean closed = false;

    public RegistryClient(String adapterName, String description, AdapterEngine engine,
                          com.kekwy.iarnet.adapter.artifact.ArtifactFetcher artifactFetcher,
                          List<String> tags, String cpHost, int cpPort) {
        this.adapterName = adapterName;
        this.description = description != null ? description : "";
        this.engine = engine;
        this.artifactFetcher = artifactFetcher;
        this.tags = tags != null ? tags : List.of();

        this.channel = ManagedChannelBuilder
                .forAddress(cpHost, cpPort)
                .usePlaintext()
                .build();
        this.blockingStub = AdapterRegistryServiceGrpc.newBlockingStub(channel);
        this.asyncStub = AdapterRegistryServiceGrpc.newStub(channel);
        this.scheduler = Executors.newScheduledThreadPool(2);
    }

    /**
     * 执行注册 → 打开命令通道 → 启动心跳。
     */
    public void start() {
        GetDeviceInfoResponse info = engine.getDeviceInfo();

        RegisterRequest request = RegisterRequest.newBuilder()
                .setAdapterName(adapterName)
                .setDescription(description)
                .setAdapterType(engine.adapterType())
                .setCapacity(info.getCapacity())
                .addAllTags(tags)
                .build();

        try {
            RegisterResponse response = blockingStub.register(request);
            if (response.getAccepted()) {
                adapterId = response.getAdapterId();
                registered = true;
                log.info("注册成功: adapterId={}, name={}", adapterId, adapterName);
                openCommandChannel();
                startHeartbeat();
            } else {
                log.error("注册被拒绝: name={}, message={}", adapterName, response.getMessage());
            }
        } catch (StatusRuntimeException e) {
            log.error("注册失败: name={}, status={}", adapterName, e.getStatus(), e);
        }
    }

    public String getAdapterId() {
        return adapterId;
    }

    @Override
    public void close() {
        closed = true;
        scheduler.shutdownNow();
        deregister();
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            channel.shutdownNow();
        }
    }

    // ======================== 命令通道 ========================

    /**
     * 打开双向流命令通道。
     * <p>
     * gRPC 签名: rpc CommandChannel(stream CommandResponse) returns (stream Command)
     * <br>
     * 客户端视角:
     * <ul>
     *   <li>传入 StreamObserver&lt;Command&gt; — 接收 CP 下发的命令</li>
     *   <li>返回 StreamObserver&lt;CommandResponse&gt; — 发送处理结果给 CP</li>
     * </ul>
     * CommandChannelHandler 需要 responseSender 才能回传结果，
     * 但 responseSender 需要先传入 handler 才能创建——使用代理解决循环依赖。
     */
    private void openCommandChannel() {
        if (closed) return;

        // 代理：延迟绑定真正的 responseSender
        DelegatingObserver<CommandResponse> proxy = new DelegatingObserver<>();

        StreamObserver<Command> commandReceiver = new CommandChannelHandler(
                engine, artifactFetcher, proxy, this::onChannelDisconnect);

        // 通过 gRPC metadata 传递 adapter_id，供 control-plane 识别本 Adapter
        Metadata headers = new Metadata();
        headers.put(ADAPTER_ID_METADATA_KEY, adapterId);
        AdapterRegistryServiceGrpc.AdapterRegistryServiceStub headerStub =
                asyncStub.withInterceptors(
                        MetadataUtils.newAttachHeadersInterceptor(headers));

        StreamObserver<CommandResponse> responseSender = headerStub.commandChannel(commandReceiver);
        proxy.setDelegate(responseSender);

        log.info("CommandChannel 已建立: adapterId={}", adapterId);
    }

    private void onChannelDisconnect() {
        if (closed || !registered) return;
        log.warn("CommandChannel 断开，{}s 后重连...", RECONNECT_DELAY_SECONDS);
        scheduler.schedule(this::openCommandChannel, RECONNECT_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    // ======================== 心跳 ========================

    private void startHeartbeat() {
        scheduler.scheduleAtFixedRate(this::sendHeartbeat, 30, 30, TimeUnit.SECONDS);
        log.info("心跳已启动: 间隔=30s, adapterId={}", adapterId);
    }

    private void sendHeartbeat() {
        if (!registered || adapterId == null || closed) return;
        try {
            GetResourceUsageResponse usage = engine.getResourceUsage();
            HeartbeatResponse response = blockingStub.heartbeat(HeartbeatRequest.newBuilder()
                    .setAdapterId(adapterId)
                    .setUsage(usage.getCapacity())
                    .build());
            if (!response.getAcknowledged()) {
                log.warn("心跳未确认: adapterId={}", adapterId);
            }
        } catch (StatusRuntimeException e) {
            log.warn("心跳发送失败: adapterId={}, status={}", adapterId, e.getStatus());
        } catch (Exception e) {
            log.error("心跳异常: adapterId={}", adapterId, e);
        }
    }

    // ======================== 注销 ========================

    private void deregister() {
        if (!registered || adapterId == null) return;
        try {
            DeregisterResponse response = blockingStub.deregister(
                    DeregisterRequest.newBuilder().setAdapterId(adapterId).build());
            registered = false;
            log.info("注销完成: adapterId={}, success={}", adapterId, response.getSuccess());
        } catch (StatusRuntimeException e) {
            log.warn("注销失败: adapterId={}, status={}", adapterId, e.getStatus());
        }
    }

    // ======================== 工具类 ========================

    /**
     * 代理 StreamObserver：创建时 delegate 可为空，后续通过 setDelegate 注入。
     * 用于解决双向流中 handler 和 sender 的循环依赖。
     */
    private static class DelegatingObserver<T> implements StreamObserver<T> {
        private volatile StreamObserver<T> delegate;

        void setDelegate(StreamObserver<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onNext(T value) {
            delegate.onNext(value);
        }

        @Override
        public void onError(Throwable t) {
            delegate.onError(t);
        }

        @Override
        public void onCompleted() {
            delegate.onCompleted();
        }
    }
}
