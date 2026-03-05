package com.kekwy.iarnet.resource.actor;

import com.kekwy.iarnet.proto.actor.ActorDirective;
import com.kekwy.iarnet.proto.actor.ActorReadyReport;
import com.kekwy.iarnet.proto.actor.ActorReport;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * {@link ActorRegistry} 的默认实现。
 * <p>
 * 维护 Actor 会话、ControlChannel 连接，并定期扫描心跳超时的 Actor。
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

    @Override
    public void onActorConnected(String actorId, ActorReadyReport ready,
                                 StreamObserver<ActorDirective> directiveSender) {
        ActorConnection oldConn = connections.get(actorId);
        if (oldConn != null && !oldConn.isClosed()) {
            log.info("替换 Actor 已有的 ControlChannel: actorId={}", actorId);
            oldConn.close();
            connections.remove(actorId);
            sessions.remove(actorId);
        }

        ActorSession session = new ActorSession(actorId, ready);
        ActorConnection conn = new ActorConnection(actorId, directiveSender);
        sessions.put(actorId, session);
        connections.put(actorId, conn);

        log.info("Actor ControlChannel 已建立: actorId={}, workflowId={}, nodeId={}",
                actorId, ready.getWorkflowId(), ready.getNodeId());

        for (ActorLifecycleListener listener : listeners) {
            try {
                listener.onActorReady(actorId, ready);
            } catch (Exception e) {
                log.warn("ActorLifecycleListener.onActorReady 异常: actorId={}", actorId, e);
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
            log.info("Actor ControlChannel 已断开: actorId={}, workflowId={}", actorId, session.getWorkflowId());
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
    public void handleReport(String actorId, ActorReport report) {
        ActorConnection conn = connections.get(actorId);
        ActorSession session = sessions.get(actorId);
        if (conn != null) {
            conn.onReport(report);
        }
        if (session != null) {
            switch (report.getPayloadCase()) {
                case HEARTBEAT -> session.updateHeartbeat();
                case STATUS_CHANGE -> {
                    var change = report.getStatusChange();
                    session.setStatus("error".equalsIgnoreCase(change.getStatus())
                            ? ActorSession.Status.ERROR
                            : ActorSession.Status.READY);
                    for (ActorLifecycleListener listener : listeners) {
                        try {
                            listener.onActorStatusChanged(actorId, change);
                        } catch (Exception e) {
                            log.warn("ActorLifecycleListener.onActorStatusChanged 异常: actorId={}", actorId, e);
                        }
                    }
                }
                case METRICS, READY, PAYLOAD_NOT_SET -> { }
            }
        }
        if (conn == null) {
            log.warn("收到 Actor 上报但无活跃连接: actorId={}", actorId);
        }
    }

    @Override
    public void sendDirective(String actorId, ActorDirective directive) {
        ActorConnection conn = connections.get(actorId);
        if (conn == null || conn.isClosed()) {
            throw new IllegalStateException("Actor 无活跃 ControlChannel: " + actorId);
        }
        conn.sendDirective(directive);
    }

    @Override
    public ActorSession getSession(String actorId) {
        return sessions.get(actorId);
    }

    @Override
    public List<ActorSession> listActorsByWorkflow(String workflowId) {
        List<ActorSession> result = new ArrayList<>();
        for (ActorSession s : sessions.values()) {
            if (workflowId.equals(s.getWorkflowId())) {
                result.add(s);
            }
        }
        return result;
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
                log.warn("Actor 心跳超时，断开连接: actorId={}, workflowId={}", actorId, session.getWorkflowId());
                onActorDisconnected(actorId);
            }
        }
    }
}
