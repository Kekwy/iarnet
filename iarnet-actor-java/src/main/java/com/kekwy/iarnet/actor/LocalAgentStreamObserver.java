package com.kekwy.iarnet.actor;

import com.kekwy.iarnet.proto.agent.LocalAgentMessage;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class LocalAgentStreamObserver implements StreamObserver<LocalAgentMessage> {

    private static final Logger log = LoggerFactory.getLogger(LocalAgentStreamObserver.class);

    @Override
    public void onNext(LocalAgentMessage value) {
        // 基础版暂不处理来自 Device Agent 的下行消息
    }

    @Override
    public void onError(Throwable t) {
        log.warn("LocalAgent LocalChannel 出错: {}", t.getMessage());
    }

    @Override
    public void onCompleted() {
        log.info("LocalAgent LocalChannel 已结束");
    }
}
