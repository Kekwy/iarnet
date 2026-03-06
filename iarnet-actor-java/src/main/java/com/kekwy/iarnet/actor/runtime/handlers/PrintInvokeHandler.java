package com.kekwy.iarnet.actor.runtime.handlers;

import com.google.protobuf.ByteString;
import com.kekwy.iarnet.actor.runtime.JavaInvokeHandler;
import com.kekwy.iarnet.proto.actor.ActorInvokeRequest;
import com.kekwy.iarnet.proto.actor.ActorInvokeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 内建 Print Sink：将请求 payload 打印到日志，不再向下游返回业务结果。
 */
public class PrintInvokeHandler implements JavaInvokeHandler {

    private static final Logger log = LoggerFactory.getLogger(PrintInvokeHandler.class);

    @Override
    public ActorInvokeResponse handle(ActorInvokeRequest request) {
        ByteString payload = request.getPayload();
        int len = payload != null ? payload.size() : 0;
        String preview;
        if (len == 0) {
            preview = "<empty>";
        } else {
            byte[] bytes = payload.toByteArray();
            int show = Math.min(bytes.length, 128);
            preview = new String(bytes, 0, show);
            if (show < bytes.length) {
                preview += "...(" + bytes.length + " bytes)";
            }
        }
        log.info("PrintSink 收到数据: invocationId={}, actorAddr={}, size={}, preview={}",
                request.getInvocationId(), request.getActorAddr(), len, preview);

        // 对于 Sink，不需要向下游产生新的业务结果，返回空 payload 即可
        return ActorInvokeResponse.newBuilder()
                .setInvocationId(request.getInvocationId())
                .build();
    }
}

