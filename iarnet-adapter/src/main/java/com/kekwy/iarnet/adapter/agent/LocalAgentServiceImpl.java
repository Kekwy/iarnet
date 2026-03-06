package com.kekwy.iarnet.adapter.agent;

import com.kekwy.iarnet.proto.agent.LocalAgentMessage;
import com.kekwy.iarnet.proto.agent.LocalAgentServiceGrpc;
import com.kekwy.iarnet.proto.agent.LocalRegisterActor;
import com.kekwy.iarnet.proto.ir.FunctionDescriptor;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基础版本地 Device Agent 实现：
 * <ul>
 *   <li>接收 Actor 的注册请求（LocalRegisterActor），记录 ActorAddr -> endpoint 映射</li>
 *   <li>若已通过 {@link #setFunctionForActor(String, FunctionDescriptor)} 为该 actor 设置函数，注册后向该连接下发 assign_function</li>
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

    /** ActorAddr -> 该 Actor 要执行的 IR 函数描述（由 control plane / 测试在启动前设置） */
    private final Map<String, FunctionDescriptor> functionByActor = new ConcurrentHashMap<>();

    @Override
    public StreamObserver<LocalAgentMessage> localChannel(StreamObserver<LocalAgentMessage> responseObserver) {
        return new StreamObserver<>() {
            @Override
            public void onNext(LocalAgentMessage msg) {
                if (msg.hasRegisterActor()) {
                    handleRegister(msg.getRegisterActor(), responseObserver);
                } else {
                    log.debug("忽略未知 LocalAgentMessage: {}", msg.getPayloadCase());
                }
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
        log.info("Actor 注册到本机 Device Agent: actorAddr={}", actorAddr);

        // 单机场景：通知本地拓扑图该 Actor 已建立到 Device Agent 的本地通道
        LocalActorGraph.getInstance().onActorRegistered(actorAddr);

        FunctionDescriptor fd = functionByActor.get(actorAddr);
        if (fd != null) {
            try {
                responseObserver.onNext(LocalAgentMessage.newBuilder().setAssignFunction(fd).build());
                log.info("已向 Actor 下发 assign_function: actorAddr={}", actorAddr);
            } catch (Exception e) {
                log.warn("下发 assign_function 失败: actorAddr={}", actorAddr, e);
            }
        }
    }

    public LocalEndpoint getEndpoint(String actorAddr) {
        return actors.get(actorAddr);
    }

    /**
     * 设置某 Actor 地址对应的函数描述。在该 Actor 调用 RegisterActor 后，本机会向其下发 assign_function。
     * 由 control plane 或测试在启动/调度时调用。
     */
    public void setFunctionForActor(String actorAddr, FunctionDescriptor fd) {
        if (fd != null) {
            functionByActor.put(actorAddr, fd);
        } else {
            functionByActor.remove(actorAddr);
        }
    }
}

