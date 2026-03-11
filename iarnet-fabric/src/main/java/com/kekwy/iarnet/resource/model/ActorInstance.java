package com.kekwy.iarnet.resource.model;

/**
 * 表示一个已部署的 Actor 实例。
 * <p>
 * 每个算子节点的每个副本都会被部署为一个 Actor，
 * 该记录描述其物理位置信息。
 *
 * @param actorId     Actor 唯一标识（由调度器生成）
 * @param deviceId    所部署到的资源设备标识
 * @param containerId 容器标识（如 Docker container ID）
 * @param host        Actor 可达的主机地址
 * @param port        Actor 监听的端口
 */
public record ActorInstance(
        String actorId,
        String deviceId,
        String containerId,
        String host,
        int port
) {
}
