package com.kekwy.iarnet.resource.actor;

import java.time.Instant;

/**
 * 记录一个已连接 Actor 的元数据与实时状态。
 * <p>
 * 在 Actor 上报 ready 时创建，在控制通道断开时失效。
 */
public class ActorSession {

    public enum Status {
        READY,
        DRAINING,
        ERROR
    }

    private final String actorId;
    private volatile Instant lastHeartbeat;
    private volatile Status status;

    public ActorSession(String actorId) {
        this.actorId = actorId;
        this.lastHeartbeat = Instant.now();
        this.status = Status.READY;
    }

    public String getActorId() {
        return actorId;
    }

    public Instant getLastHeartbeat() {
        return lastHeartbeat;
    }

    public void updateHeartbeat() {
        this.lastHeartbeat = Instant.now();
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "ActorSession{actorId=" + actorId + ", status=" + status + "}";
    }
}
