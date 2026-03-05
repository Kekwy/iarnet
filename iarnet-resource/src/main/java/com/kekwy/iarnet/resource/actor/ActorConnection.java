package com.kekwy.iarnet.resource.actor;

import com.kekwy.iarnet.proto.actor.ActorDirective;
import com.kekwy.iarnet.proto.actor.ActorReport;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 封装单个 Actor 的 ControlChannel 双向流连接。
 * <p>
 * 提供 {@link #sendDirective(ActorDirective)} 下发指令，
 * 以及可选的 {@link #sendDirectiveAndAwait} 下发并等待下一次 Actor 上报。
 */
public class ActorConnection {

    private static final Logger log = LoggerFactory.getLogger(ActorConnection.class);
    private static final long DEFAULT_AWAIT_TIMEOUT_SECONDS = 30;

    private final String actorId;
    private final StreamObserver<ActorDirective> directiveSender;
    private final ConcurrentHashMap<String, CompletableFuture<ActorReport>> pendingAwaits =
            new ConcurrentHashMap<>();

    private volatile boolean closed = false;

    public ActorConnection(String actorId, StreamObserver<ActorDirective> directiveSender) {
        this.actorId = actorId;
        this.directiveSender = directiveSender;
    }

    public String getActorId() {
        return actorId;
    }

    public boolean isClosed() {
        return closed;
    }

    /**
     * 向 Actor 下发指令（fire-and-forget，无需等待响应）。
     */
    public void sendDirective(ActorDirective directive) {
        if (closed) {
            throw new IllegalStateException("ActorConnection 已关闭: " + actorId);
        }
        try {
            synchronized (directiveSender) {
                directiveSender.onNext(directive);
            }
        } catch (Exception e) {
            log.warn("下发指令失败: actorId={}, directiveId={}", actorId, directive.getDirectiveId(), e);
            throw e;
        }
    }

    /**
     * 向 Actor 下发指令并等待下一次上报（或超时）。
     * <p>
     * 使用 {@code directive_id} 作为 key，若 Actor 在超时前发送的 Report 中
     * 包含对该 directive 的确认则完成；否则任意下一次 Report 也会完成此 Future。
     *
     * @param directive 已设置 directive_id 的指令
     * @param timeout   超时时间
     * @param unit      时间单位
     * @return 收到下一次 ActorReport 时 complete，超时则 completeExceptionally
     */
    public CompletableFuture<ActorReport> sendDirectiveAndAwait(
            ActorDirective directive, long timeout, TimeUnit unit) {
        if (closed) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("ActorConnection 已关闭: " + actorId));
        }

        String directiveId = directive.getDirectiveId() != null && !directive.getDirectiveId().isEmpty()
                ? directive.getDirectiveId()
                : UUID.randomUUID().toString();
        ActorDirective withId = directive.toBuilder().setDirectiveId(directiveId).build();

        CompletableFuture<ActorReport> future = new CompletableFuture<>();
        pendingAwaits.put(directiveId, future);

        future.orTimeout(timeout, unit).whenComplete((r, ex) -> {
            pendingAwaits.remove(directiveId);
            if (ex != null) {
                log.debug("sendDirectiveAndAwait 超时或异常: actorId={}, directiveId={}", actorId, directiveId);
            }
        });

        try {
            synchronized (directiveSender) {
                directiveSender.onNext(withId);
            }
        } catch (Exception e) {
            pendingAwaits.remove(directiveId);
            future.completeExceptionally(e);
        }

        return future;
    }

    public CompletableFuture<ActorReport> sendDirectiveAndAwait(ActorDirective directive) {
        return sendDirectiveAndAwait(directive, DEFAULT_AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 处理 Actor 上报的消息；若有 pending 的 sendDirectiveAndAwait，则尝试完成对应 Future。
     */
    public void onReport(ActorReport report) {
        // 若存在以某 directive_id 等待的 Future，可用本次 report 完成（简化：用 report 的 actor_id 关联最近一次 await）
        if (!pendingAwaits.isEmpty()) {
            String firstKey = pendingAwaits.keySet().iterator().next();
            CompletableFuture<ActorReport> f = pendingAwaits.remove(firstKey);
            if (f != null && !f.isDone()) {
                f.complete(report);
            }
        }
    }

    /**
     * 关闭连接，取消所有待处理的 await。
     */
    public void close() {
        if (closed) return;
        closed = true;

        RuntimeException cause = new RuntimeException("ActorConnection 已关闭: " + actorId);
        pendingAwaits.values().forEach(f -> f.completeExceptionally(cause));
        pendingAwaits.clear();

        try {
            synchronized (directiveSender) {
                directiveSender.onCompleted();
            }
        } catch (Exception e) {
            log.debug("关闭 directiveSender 时出现异常: actorId={}", actorId, e);
        }

        log.info("ActorConnection 已关闭: actorId={}", actorId);
    }
}
