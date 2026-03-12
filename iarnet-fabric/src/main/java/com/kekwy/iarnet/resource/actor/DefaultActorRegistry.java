package com.kekwy.iarnet.resource.actor;

import com.kekwy.iarnet.proto.actor.ActorEnvelope;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * {@link ActorRegistry} 的默认实现。
 * <p>
 * 维护 Actor 会话与连接，并定期扫描心跳超时的 Actor。
 */
@Service
public class DefaultActorRegistry implements ActorRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultActorRegistry.class);
    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(120);

    private final ConcurrentHashMap<String, ActorSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ActorConnection> connections = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<ActorLifecycleListener> listeners = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService watchdog;

    public DefaultActorRegistry() {
        watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "actor-registry-watchdog");
            t.setDaemon(true);
            return t;
        });
        watchdog.scheduleAtFixedRate(this::checkHeartbeats, 30, 30, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        watchdog.shutdownNow();
        connections.values().forEach(ActorConnection::close);
        connections.clear();
        sessions.clear();
    }

    /**
     * 注册 actor 的消息发送通道（由 gRPC 服务层在 SignalingChannel 建立时调用）。
     */
    public void registerConnection(String actorId, StreamObserver<ActorEnvelope> sender) {
        ActorConnection oldConn = connections.get(actorId);
        if (oldConn != null && !oldConn.isClosed()) {
            log.info("替换 Actor 已有连接: actorId={}", actorId);
            oldConn.close();
        }
        connections.put(actorId, new ActorConnection(actorId, sender));
    }

    @Override
    public void onActorReady(String actorId) {
        ActorSession oldSession = sessions.get(actorId);
        if (oldSession != null) {
            log.debug("Actor 重新上报 ready，更新 session: actorId={}", actorId);
        }

        ActorSession session = new ActorSession(actorId);
        sessions.put(actorId, session);

        log.info("Actor ready: actorId={}", actorId);

        for (ActorLifecycleListener listener : listeners) {
            try {
                listener.onActorReady(actorId);
            } catch (Exception e) {
                log.warn("ActorLifecycleListener.onActorReady 异常: actorId={}", actorId, e);
            }
        }
    }

    @Override
    public void onChannelConnected(String srcActorId, String dstActorId) {
        log.info("Channel connected: src={}, dst={}", srcActorId, dstActorId);

        for (ActorLifecycleListener listener : listeners) {
            try {
                listener.onChannelConnected(srcActorId, dstActorId);
            } catch (Exception e) {
                log.warn("ActorLifecycleListener.onChannelConnected 异常: src={}, dst={}",
                        srcActorId, dstActorId, e);
            }
        }
    }

    @Override
    public void onActorDisconnected(String actorId) {
        ActorConnection conn = connections.remove(actorId);
        if (conn != null) {
            conn.close();
        }
        ActorSession session = sessions.remove(actorId);
        if (session != null) {
            log.info("Actor 已断开: actorId={}", actorId);
        }

        for (ActorLifecycleListener listener : listeners) {
            try {
                listener.onActorDisconnected(actorId);
            } catch (Exception e) {
                log.warn("ActorLifecycleListener.onActorDisconnected 异常: actorId={}", actorId, e);
            }
        }
    }

    @Override
    public void sendToActor(String actorId, ActorEnvelope envelope) {
        ActorConnection conn = connections.get(actorId);
        if (conn == null || conn.isClosed()) {
            throw new IllegalStateException("Actor 无活跃连接: " + actorId);
        }
        conn.send(envelope);
    }

    @Override
    public ActorSession getSession(String actorId) {
        return sessions.get(actorId);
    }

    @Override
    public List<ActorSession> listSessions() {
        return List.copyOf(sessions.values());
    }

    @Override
    public void addListener(ActorLifecycleListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(ActorLifecycleListener listener) {
        listeners.remove(listener);
    }

    private void checkHeartbeats() {
        Instant threshold = Instant.now().minus(HEARTBEAT_TIMEOUT);
        for (ActorSession session : sessions.values()) {
            if (session.getLastHeartbeat().isBefore(threshold)) {
                String actorId = session.getActorId();
                log.warn("Actor 心跳超时: actorId={}", actorId);
                onActorDisconnected(actorId);
            }
        }
    }
}
