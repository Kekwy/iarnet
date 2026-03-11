package com.kekwy.iarnet.resource.adapter;

import com.kekwy.iarnet.proto.adapter.Command;
import com.kekwy.iarnet.proto.adapter.CommandResponse;
import com.kekwy.iarnet.proto.adapter.RegisterRequest;
import com.kekwy.iarnet.proto.adapter.ResourceCapacity;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * {@link AdapterRegistry} 的默认实现。
 * <p>
 * 维护 Adapter 元数据、CommandChannel 连接，
 * 并定期扫描心跳超时的 Adapter 将其标记为 OFFLINE。
 */
@Service
public class DefaultAdapterRegistry implements AdapterRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultAdapterRegistry.class);
    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(90);

    private final ConcurrentHashMap<String, AdapterInfo> adapters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AdapterConnection> connections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService watchdog;

    public DefaultAdapterRegistry() {
        watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "adapter-watchdog");
            t.setDaemon(true);
            return t;
        });
        watchdog.scheduleAtFixedRate(this::checkHeartbeats, 30, 30, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        watchdog.shutdownNow();
        connections.values().forEach(AdapterConnection::close);
    }

    // ======================== 注册/注销 ========================

    @Override
    public String register(RegisterRequest request) {
        String adapterId = UUID.randomUUID().toString();
        AdapterInfo info = new AdapterInfo(
                adapterId,
                request.getAdapterName(),
                request.getDescription(),
                request.getAdapterType(),
                request.getCapacity(),
                request.getTagsList()
        );
        adapters.put(adapterId, info);
        log.info("Adapter 注册成功: {}", info);
        return adapterId;
    }

    @Override
    public void deregister(String adapterId) {
        AdapterInfo removed = adapters.remove(adapterId);
        if (removed != null) {
            removed.setStatus(AdapterInfo.Status.OFFLINE);
            log.info("Adapter 已注销: {}", removed);
        }
        AdapterConnection conn = connections.remove(adapterId);
        if (conn != null) {
            conn.close();
        }
    }

    // ======================== 心跳 ========================

    @Override
    public void heartbeat(String adapterId, ResourceCapacity usage) {
        AdapterInfo info = adapters.get(adapterId);
        if (info == null) {
            log.warn("收到未知 Adapter 的心跳: adapterId={}", adapterId);
            return;
        }
        info.updateUsage(usage);
        if (info.getStatus() == AdapterInfo.Status.OFFLINE) {
            info.setStatus(AdapterInfo.Status.ONLINE);
            log.info("Adapter 重新上线: {}", info);
        }
    }

    // ======================== CommandChannel ========================

    @Override
    public void openCommandChannel(String adapterId, StreamObserver<Command> commandSender) {
        AdapterInfo info = adapters.get(adapterId);
        if (info == null) {
            log.warn("未注册的 Adapter 尝试打开 CommandChannel: adapterId={}", adapterId);
            commandSender.onError(
                    io.grpc.Status.NOT_FOUND
                            .withDescription("Adapter 未注册: " + adapterId)
                            .asRuntimeException());
            return;
        }

        AdapterConnection oldConn = connections.get(adapterId);
        if (oldConn != null && !oldConn.isClosed()) {
            log.info("替换 Adapter 已有的 CommandChannel: adapterId={}", adapterId);
            oldConn.close();
        }

        AdapterConnection conn = new AdapterConnection(adapterId, commandSender);
        connections.put(adapterId, conn);
        info.setStatus(AdapterInfo.Status.ONLINE);
        log.info("CommandChannel 已建立: adapterId={}, name={}", adapterId, info.getName());
    }

    @Override
    public void handleCommandResponse(String adapterId, CommandResponse response) {
        AdapterConnection conn = connections.get(adapterId);
        if (conn != null) {
            conn.onResponse(response);
        } else {
            log.warn("收到命令响应但无活跃连接: adapterId={}, requestId={}",
                    adapterId, response.getRequestId());
        }
    }

    @Override
    public void closeCommandChannel(String adapterId) {
        AdapterConnection conn = connections.remove(adapterId);
        if (conn != null) {
            conn.close();
        }
        AdapterInfo info = adapters.get(adapterId);
        if (info != null) {
            info.setStatus(AdapterInfo.Status.OFFLINE);
            log.info("CommandChannel 已断开: adapterId={}, name={}", adapterId, info.getName());
        }
    }

    // ======================== 命令发送 ========================

    @Override
    public CompletableFuture<CommandResponse> sendCommand(String adapterId, Command.Builder commandBuilder) {
        AdapterConnection conn = connections.get(adapterId);
        if (conn == null || conn.isClosed()) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Adapter 无活跃 CommandChannel: " + adapterId));
        }
        return conn.sendCommand(commandBuilder);
    }

    // ======================== 查询 ========================

    @Override
    public AdapterInfo getAdapter(String adapterId) {
        return adapters.get(adapterId);
    }

    @Override
    public List<AdapterInfo> listAdapters() {
        return List.copyOf(adapters.values());
    }

    @Override
    public List<AdapterInfo> listOnlineAdapters() {
        return adapters.values().stream()
                .filter(a -> a.getStatus() == AdapterInfo.Status.ONLINE)
                .filter(a -> {
                    AdapterConnection conn = connections.get(a.getAdapterId());
                    return conn != null && !conn.isClosed();
                })
                .toList();
    }

    @Override
    public List<AdapterInfo> listOnlineAdaptersByType(String adapterType) {
        return listOnlineAdapters().stream()
                .filter(a -> a.getAdapterType().equals(adapterType))
                .toList();
    }

    // ======================== 心跳超时检测 ========================

    private void checkHeartbeats() {
        Instant threshold = Instant.now().minus(HEARTBEAT_TIMEOUT);
        for (AdapterInfo info : adapters.values()) {
            if (info.getStatus() == AdapterInfo.Status.ONLINE
                    && info.getLastHeartbeat().isBefore(threshold)) {
                log.warn("Adapter 心跳超时，标记为 OFFLINE: {}", info);
                info.setStatus(AdapterInfo.Status.OFFLINE);
            }
        }
    }
}
