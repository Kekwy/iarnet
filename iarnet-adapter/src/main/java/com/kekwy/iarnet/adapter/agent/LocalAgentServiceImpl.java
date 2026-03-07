package com.kekwy.iarnet.adapter.agent;

import com.kekwy.iarnet.proto.agent.LocalAgentMessage;
import com.kekwy.iarnet.proto.agent.LocalAgentServiceGrpc;
import com.kekwy.iarnet.proto.agent.LocalRegisterActor;
import com.kekwy.iarnet.proto.agent.SourceConfig;
import com.kekwy.iarnet.proto.actor.ActorDirective;
import com.kekwy.iarnet.proto.ir.FunctionDescriptor;
import com.kekwy.iarnet.proto.ir.Row;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地 Device Agent 实现：
 * <ul>
 *   <li>接收 Actor 的注册请求（LocalRegisterActor），记录映射</li>
 *   <li>注册后向 Actor 下发 assign_function / source_config</li>
 *   <li>接收 Actor 发出的 row_output，路由转发给下游 Actor</li>
 *   <li>接收控制平面经 SignalingChannel 下发的 ActorDirective，转发给对应 Actor</li>
 * </ul>
 */
public class LocalAgentServiceImpl extends LocalAgentServiceGrpc.LocalAgentServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(LocalAgentServiceImpl.class);

    public static final class LocalEndpoint {
        public final String actorAddr;

        public LocalEndpoint(String actorAddr) {
            this.actorAddr = actorAddr;
        }
    }

    /** ActorAddr -> 本机监听信息 */
    private final Map<String, LocalEndpoint> actors = new ConcurrentHashMap<>();

    /** actorId -> 该 Actor 的 LocalChannel 发送端 */
    private final Map<String, StreamObserver<LocalAgentMessage>> actorIdToStream = new ConcurrentHashMap<>();

    /** actorAddr -> 该 Actor 的 LocalChannel 发送端（用于按 actorAddr 路由数据） */
    private final Map<String, StreamObserver<LocalAgentMessage>> actorAddrToStream = new ConcurrentHashMap<>();

    /** ActorAddr -> 该 Actor 要执行的 IR 函数描述 */
    private final Map<String, FunctionDescriptor> functionByActor = new ConcurrentHashMap<>();

    /** actorAddr -> 下游 Actor 地址列表（由调度器在部署时设置） */
    private final Map<String, List<String>> downstreamsByActorAddr = new ConcurrentHashMap<>();

    /** actorAddr -> Source 的 constant rows（由调度器在部署时设置） */
    private final Map<String, List<Row>> sourceRowsByActorAddr = new ConcurrentHashMap<>();

    @Override
    public StreamObserver<LocalAgentMessage> localChannel(StreamObserver<LocalAgentMessage> responseObserver) {
        return new StreamObserver<>() {
            private volatile String registeredActorAddr;

            @Override
            public void onNext(LocalAgentMessage msg) {
                switch (msg.getPayloadCase()) {
                    case REGISTER_ACTOR -> handleRegister(msg.getRegisterActor(), responseObserver);
                    case ROW_OUTPUT -> {
                        if (registeredActorAddr != null) {
                            handleRowOutput(registeredActorAddr, msg.getRowOutput().getRow());
                        } else {
                            log.warn("收到 row_output 但该连接尚未注册 actor");
                        }
                    }
                    default -> log.debug("忽略未知 LocalAgentMessage: {}", msg.getPayloadCase());
                }
            }

            private void handleRegister(LocalRegisterActor reg,
                                        StreamObserver<LocalAgentMessage> respObs) {
                registeredActorAddr = reg.getActorAddr();
                LocalAgentServiceImpl.this.handleRegister(reg, respObs);
            }

            @Override
            public void onError(Throwable t) {
                log.warn("LocalChannel 出错: {}", t.getMessage());
            }

            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }

    private void handleRegister(LocalRegisterActor reg, StreamObserver<LocalAgentMessage> responseObserver) {
        String actorAddr = reg.getActorAddr();
        actors.put(actorAddr, new LocalEndpoint(actorAddr));

        String actorId = parseActorId(actorAddr);
        if (actorId != null) {
            actorIdToStream.put(actorId, responseObserver);
        }
        actorAddrToStream.put(actorAddr, responseObserver);

        log.info("Actor 注册到本机 Device Agent: actorAddr={}, actorId={}", actorAddr, actorId);

        LocalActorGraph.getInstance().onActorRegistered(actorAddr);

        // 下发 assign_function（如有）
        FunctionDescriptor fd = functionByActor.get(actorAddr);
        if (fd != null) {
            try {
                responseObserver.onNext(LocalAgentMessage.newBuilder().setAssignFunction(fd).build());
                log.info("已向 Actor 下发 assign_function: actorAddr={}", actorAddr);
            } catch (Exception e) {
                log.warn("下发 assign_function 失败: actorAddr={}", actorAddr, e);
            }
        }

        // 下发 source_config（如有）
        List<Row> sourceRows = sourceRowsByActorAddr.get(actorAddr);
        if (sourceRows != null && !sourceRows.isEmpty()) {
            try {
                SourceConfig config = SourceConfig.newBuilder().addAllRows(sourceRows).build();
                responseObserver.onNext(LocalAgentMessage.newBuilder().setSourceConfig(config).build());
                log.info("已向 Source Actor 下发 source_config: actorAddr={}, rows={}", actorAddr, sourceRows.size());
            } catch (Exception e) {
                log.warn("下发 source_config 失败: actorAddr={}", actorAddr, e);
            }
        }
    }

    /**
     * 处理 Actor 发出的 row_output：路由转发给所有下游 Actor。
     */
    private void handleRowOutput(String srcActorAddr, Row row) {
        List<String> downstreams = downstreamsByActorAddr.get(srcActorAddr);
        if (downstreams == null || downstreams.isEmpty()) {
            log.debug("Actor {} 无下游，丢弃 row_output", srcActorAddr);
            return;
        }

        for (String dstAddr : downstreams) {
            StreamObserver<LocalAgentMessage> dstStream = actorAddrToStream.get(dstAddr);
            if (dstStream == null) {
                log.warn("下游 Actor {} 未注册 LocalChannel，无法转发 row", dstAddr);
                continue;
            }
            try {
                dstStream.onNext(LocalAgentMessage.newBuilder().setRowDelivery(row).build());
            } catch (Exception e) {
                log.warn("转发 row 到下游 Actor 失败: dst={}", dstAddr, e);
            }
        }
        log.debug("已将 row_output 从 {} 转发给 {} 个下游", srcActorAddr, downstreams.size());
    }

    static String parseActorId(String actorAddr) {
        if (actorAddr == null || actorAddr.isBlank()) return null;
        String[] parts = actorAddr.split("://", 2);
        String rest = parts.length == 2 ? parts[1] : actorAddr;
        String[] segs = rest.split("/");
        if (segs.length < 4) return null;
        return "actor-" + segs[2] + "-" + segs[3];
    }

    /**
     * 将控制平面下发的 ActorDirective 转发给指定 Actor。
     */
    public void forwardDirectiveToActor(String actorId, ActorDirective directive) {
        StreamObserver<LocalAgentMessage> stream = actorIdToStream.get(actorId);
        if (stream == null) {
            log.warn("未找到 actorId={} 的 LocalChannel，无法转发 ActorDirective", actorId);
            return;
        }
        try {
            stream.onNext(LocalAgentMessage.newBuilder().setActorDirective(directive).build());
            log.info("已向 Actor 转发 ActorDirective: actorId={}, payloadCase={}", actorId, directive.getPayloadCase());
        } catch (Exception e) {
            log.warn("转发 ActorDirective 失败: actorId={}", actorId, e);
        }
    }

    public LocalEndpoint getEndpoint(String actorAddr) {
        return actors.get(actorAddr);
    }

    public void setFunctionForActor(String actorAddr, FunctionDescriptor fd) {
        if (fd != null) {
            functionByActor.put(actorAddr, fd);
        } else {
            functionByActor.remove(actorAddr);
        }
    }

    public void setDownstreamsForActor(String actorAddr, List<String> downstreamAddrs) {
        if (downstreamAddrs != null && !downstreamAddrs.isEmpty()) {
            downstreamsByActorAddr.put(actorAddr, List.copyOf(downstreamAddrs));
            log.info("已记录 Actor 下游路由: actorAddr={}, downstreams={}", actorAddr, downstreamAddrs);
        }
    }

    public void setSourceRowsForActor(String actorAddr, List<Row> rows) {
        if (rows != null && !rows.isEmpty()) {
            sourceRowsByActorAddr.put(actorAddr, List.copyOf(rows));
            log.info("已记录 Source Actor 的 constant rows: actorAddr={}, rowCount={}", actorAddr, rows.size());
        }
    }
}
