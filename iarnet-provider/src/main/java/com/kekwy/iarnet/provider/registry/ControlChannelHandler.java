package com.kekwy.iarnet.provider.registry;

import com.kekwy.iarnet.proto.provider.ControlEnvelope;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 处理 ControlChannel 下发的 ControlEnvelope（如 ProviderHeartbeatAck）。
 */
public class ControlChannelHandler implements StreamObserver<ControlEnvelope> {

    private static final Logger log = LoggerFactory.getLogger(ControlChannelHandler.class);

    private final Runnable onDisconnect;

    public ControlChannelHandler(Runnable onDisconnect) {
        this.onDisconnect = onDisconnect;
    }

    @Override
    public void onNext(ControlEnvelope value) {
        if (value == null) return;
        switch (value.getMessageCase()) {
            case PROVIDER_HEARTBEAT_ACK:
                if (!value.getProviderHeartbeatAck().getAcknowledged()) {
                    log.warn("心跳未确认: messageId={}", value.getMessageId());
                }
                break;
            case REGISTER_PROVIDER_REQUEST:
            case REGISTER_PROVIDER_RESPONSE:
                log.debug("ControlChannel 收到注册相关消息: {}", value.getMessageCase());
                break;
            default:
                log.debug("ControlChannel 收到: {}", value.getMessageCase());
        }
    }

    @Override
    public void onError(Throwable t) {
        log.warn("ControlChannel 出错: {}", t.getMessage());
        if (onDisconnect != null) onDisconnect.run();
    }

    @Override
    public void onCompleted() {
        log.info("ControlChannel 由服务端关闭");
        if (onDisconnect != null) onDisconnect.run();
    }
}
