package com.kekwy.iarnet.adapter.agent;

import com.kekwy.iarnet.proto.agent.LocalAgentMessage;
import com.kekwy.iarnet.proto.agent.LocalAgentServiceGrpc;
import com.kekwy.iarnet.proto.agent.LocalRegisterActor;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基础版本地 Device Agent 实现：
 * - 接收 Actor 的注册请求（LocalRegisterActor），记录 ActorAddr -> endpoint 映射；
 * - 目前不下发任何指令，仅作为占位实现，后续可在同一 LocalChannel 上扩展更多消息类型。
 */
public class LocalAgentServiceImpl extends LocalAgentServiceGrpc.LocalAgentServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(LocalAgentServiceImpl.class);

    public static final class LocalEndpoint {


        public final String actorAddr;

        public LocalEndpoint(String actorAddr) {
            this.actorAddr = actorAddr;

        }


    }

    /**
     * ActorAddr -> 本机监听信息
     */
    private final Map<String, LocalEndpoint> actors = new ConcurrentHashMap<>();

    @Override
    public StreamObserver<LocalAgentMessage> localChannel(StreamObserver<LocalAgentMessage> responseObserver) {
        return new StreamObserver<>() {

            @Override
            public void onNext(LocalAgentMessage msg) {
                if (msg.hasRegisterActor()) {
                    handleRegister(msg.getRegisterActor());
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
                // 当前基础版不做连接级别清理；后续可按需扩展
                responseObserver.onCompleted();
            }
        };
    }

    private void handleRegister(LocalRegisterActor reg) {
        LocalEndpoint ep = new LocalEndpoint(reg.getActorAddr());
        actors.put(reg.getActorAddr(), ep);
        log.info("Actor 注册到本机 Device Agent: actorAddr={}", reg.getActorAddr());
    }

    public LocalEndpoint getEndpoint(String actorAddr) {
        return actors.get(actorAddr);
    }
}

