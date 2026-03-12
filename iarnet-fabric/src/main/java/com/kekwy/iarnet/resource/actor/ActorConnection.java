package com.kekwy.iarnet.resource.actor;

import com.kekwy.iarnet.proto.actor.ActorEnvelope;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 封装单个 Actor 的消息发送通道。
 * <p>
 * 通过 SignalingChannel 向 Provider 转发 {@link ActorEnvelope} 消息。
 */
public class ActorConnection {

    private static final Logger log = LoggerFactory.getLogger(ActorConnection.class);

    private final String actorId;
    private final StreamObserver<ActorEnvelope> sender;
    private volatile boolean closed = false;

    public ActorConnection(String actorId, StreamObserver<ActorEnvelope> sender) {
        this.actorId = actorId;
        this.sender = sender;
    }

    public String getActorId() {
        return actorId;
    }

    public boolean isClosed() {
        return closed;
    }

    public void send(ActorEnvelope envelope) {
        if (closed) {
            throw new IllegalStateException("ActorConnection 已关闭: " + actorId);
        }
        try {
            synchronized (sender) {
                sender.onNext(envelope);
            }
        } catch (Exception e) {
            log.warn("发送消息失败: actorId={}", actorId, e);
            throw e;
        }
    }

    public void close() {
        if (closed) return;
        closed = true;
        try {
            synchronized (sender) {
                sender.onCompleted();
            }
        } catch (Exception e) {
            log.debug("关闭 sender 时出现异常: actorId={}", actorId, e);
        }
        log.info("ActorConnection 已关闭: actorId={}", actorId);
    }
}
