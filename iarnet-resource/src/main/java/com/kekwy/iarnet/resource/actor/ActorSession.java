package com.kekwy.iarnet.resource.actor;

import com.kekwy.iarnet.proto.actor.ActorReadyReport;

import java.time.Instant;

/**
 * 记录一个已连接 Actor 的元数据与实时状态。
 * <p>
 * 在收到 {@link ActorReadyReport} 时创建，在控制通道断开时失效。
 */
public class ActorSession {

    public enum Status {
        READY,    // 已就绪，正常运行
        DRAINING, // 正在排水/优雅停止
        ERROR     // 已上报错误状态
    }

    private final String actorId;
    private final String workflowId;
    private final String applicationId;
    private final String nodeId;
    private final int replicaIndex;
    private final String host;
    private final int listenPort;
    private final String deviceId;

    private volatile Instant lastHeartbeat;
    private volatile Status status;

    public ActorSession(String actorId, ActorReadyReport ready) {
        this.actorId = actorId;
        this.workflowId = ready.getWorkflowId();
        this.applicationId = ready.getApplicationId();
        this.nodeId = ready.getNodeId();
        this.replicaIndex = ready.getReplicaIndex();
        this.host = ready.getHost();
        this.listenPort = ready.getListenPort();
        this.deviceId = ready.getDeviceId();
        this.lastHeartbeat = Instant.now();
        this.status = Status.READY;
    }

    public String getActorId() {
        return actorId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getNodeId() {
        return nodeId;
    }

    public int getReplicaIndex() {
        return replicaIndex;
    }

    public String getHost() {
        return host;
    }

    public int getListenPort() {
        return listenPort;
    }

    public String getDeviceId() {
        return deviceId;
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
        return "ActorSession{actorId=" + actorId + ", workflowId=" + workflowId
                + ", nodeId=" + nodeId + ", status=" + status + "}";
    }
}
