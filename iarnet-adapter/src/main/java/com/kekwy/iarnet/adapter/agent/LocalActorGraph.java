package com.kekwy.iarnet.adapter.agent;

import com.kekwy.iarnet.proto.agent.ActorChannelStatus;
import com.kekwy.iarnet.proto.agent.SignalingMessage;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单机（单 Device）场景下的本地 Actor 拓扑视图。
 * <p>
 * 当前目标：
 * <ul>
 *   <li>接收 DeployInstanceRequest 中的拓扑信息：actorAddr 及其 downstreamActorAddrs</li>
 *   <li>接收 Actor 启动后通过 LocalChannel 注册的 actorAddr</li>
 *   <li>在“同一设备”假设下，当某条边的 src / dst 均已在本机注册时，认为本地通道已就绪</li>
 * </ul>
 * <p>
 * 未来可在此处触发对 control-plane 的 ActorChannelStatus 上报。
 */
public final class LocalActorGraph {

    private static final Logger log = LoggerFactory.getLogger(LocalActorGraph.class);

    /** 由于 Adapter 应用中 LocalAgentServiceImpl 与 CommandChannelHandler 为同一进程，这里用简单单例共享状态。 */
    private static final LocalActorGraph INSTANCE = new LocalActorGraph();

    public static LocalActorGraph getInstance() {
        return INSTANCE;
    }

    /**
     * 发往 control-plane 的 SignalingChannel 发送端，由 AdapterApplication 注入。
     */
    private volatile StreamObserver<SignalingMessage> signalingSender;

    public void setSignalingSender(StreamObserver<SignalingMessage> signalingSender) {
        this.signalingSender = signalingSender;
    }

    /**
     * actorAddr -> true，表示该 Actor 已通过 LocalChannel 向本机 Device Agent 注册。
     */
    private final Set<String> registeredActors = ConcurrentHashMap.newKeySet();

    /**
     * 预期的本地边集合：key = srcActorAddr + "->" + dstActorAddr。
     */
    private final Set<String> expectedEdges = ConcurrentHashMap.newKeySet();

    /**
     * 已经认为“本地通道就绪”的边集合。
     */
    private final Set<String> establishedEdges = ConcurrentHashMap.newKeySet();

    private LocalActorGraph() {}

    /**
     * 在收到某个 Actor 的 DeployInstanceRequest 时调用。
     *
     * @param actorAddr          当前部署 Actor 的虚拟地址
     * @param downstreamActorAddrs 该 Actor 的下游 Actor 地址列表（来自 DeployInstanceRequest.downstream_actor_addrs）
     */
    public void onDeploy(String actorAddr, List<String> downstreamActorAddrs) {
        if (actorAddr == null || actorAddr.isBlank()) {
            return;
        }
        if (downstreamActorAddrs == null) {
            downstreamActorAddrs = Collections.emptyList();
        }

        for (String dst : downstreamActorAddrs) {
            if (dst == null || dst.isBlank()) {
                continue;
            }
            String edgeKey = edgeKey(actorAddr, dst);
            expectedEdges.add(edgeKey);
            maybeEstablish(edgeKey, actorAddr, dst);
        }
    }

    /**
     * 在 Actor 通过 LocalChannel 调用 LocalRegisterActor 成功注册时调用。
     */
    public void onActorRegistered(String actorAddr) {
        if (actorAddr == null || actorAddr.isBlank()) {
            return;
        }
        registeredActors.add(actorAddr);
        log.info("LocalActorGraph: actor 已注册本地通道: actorAddr={}", actorAddr);

        // 检查以该 actor 作为 src 或 dst 的所有预期边
        for (String edge : expectedEdges) {
            if (establishedEdges.contains(edge)) {
                continue;
            }
            String[] parts = edge.split("->", 2);
            if (parts.length != 2) continue;
            String src = parts[0];
            String dst = parts[1];
            if (actorAddr.equals(src) || actorAddr.equals(dst)) {
                maybeEstablish(edge, src, dst);
            }
        }
    }

    private void maybeEstablish(String edgeKey, String srcActor, String dstActor) {
        // 目前仅处理“所有 Actor 在同一 Device”场景：
        // 简化判定：只要 src / dst 均已在本机注册 LocalChannel，即认为本地通道已就绪。
        if (registeredActors.contains(srcActor) && registeredActors.contains(dstActor)) {
            if (establishedEdges.add(edgeKey)) {
                log.info("LocalActorGraph: 本地 Actor 通道已就绪: {} -> {}", srcActor, dstActor);
                reportChannelEstablished(srcActor, dstActor);
            }
        }
    }

    private void reportChannelEstablished(String srcActor, String dstActor) {
        StreamObserver<SignalingMessage> sender = this.signalingSender;
        if (sender == null) {
            return;
        }
        try {
            // actorAddr 形如 actor://{app}/{workflow}/{node}/{replica}
            String[] parts = srcActor.split("://", 2);
            String rest = parts.length == 2 ? parts[1] : srcActor;
            String[] segs = rest.split("/");
            if (segs.length < 4) {
                log.warn("LocalActorGraph: 无法从 actorAddr 解析 application/workflow: {}", srcActor);
                return;
            }
            // buildActorAddr 定义为 actor://applicationId/workflowId/nodeId/replica
            // 因此 segs[0]=applicationId, segs[1]=workflowId, segs[2]=nodeId, segs[3]=replica
            String applicationId = segs[0];
            String workflowId = segs[1];

            ActorChannelStatus status = ActorChannelStatus.newBuilder()
                    .setWorkflowId(workflowId)
                    .setApplicationId(applicationId)
                    .setSrcActorAddr(srcActor)
                    .setDstActorAddr(dstActor)
                    .setConnected(true)
                    .build();

            SignalingMessage msg = SignalingMessage.newBuilder()
                    .setDeviceId("") // 单机场景暂不使用 device_id
                    .setTimestampMs(System.currentTimeMillis())
                    .setActorChannel(status)
                    .build();

            sender.onNext(msg);
            log.info("LocalActorGraph: 已上报 ActorChannelStatus: workflowId={}, src={}, dst={}",
                    workflowId, srcActor, dstActor);
        } catch (Exception e) {
            log.warn("LocalActorGraph: 上报 ActorChannelStatus 失败: src={}, dst={}", srcActor, dstActor, e);
        }
    }

    private static String edgeKey(String src, String dst) {
        return src + "->" + dst;
    }
}

