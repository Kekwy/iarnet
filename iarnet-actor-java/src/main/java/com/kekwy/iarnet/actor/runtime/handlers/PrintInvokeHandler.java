package com.kekwy.iarnet.actor.runtime.handlers;

import com.google.protobuf.ByteString;
import com.kekwy.iarnet.actor.runtime.JavaInvokeHandler;
import com.kekwy.iarnet.proto.actor.ActorInvokeRequest;
import com.kekwy.iarnet.proto.actor.ActorInvokeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;

/**
 * 内建 Print Sink：将请求 payload 打印到日志，不再向下游返回业务结果。
 * <p>
 * 请求 payload 为上游 Row 经 Java 序列化后的字节；预览时优先反序列化为对象再 toString，
 * 避免把二进制当字符串显示成乱码（如 0x74 与长度前缀显示为t）。
 */
public class PrintInvokeHandler implements JavaInvokeHandler {

    private static final Logger log = LoggerFactory.getLogger(PrintInvokeHandler.class);

    @Override
    public ActorInvokeResponse handle(ActorInvokeRequest request) {
        ByteString payload = request.getPayload();
        int len = payload != null ? payload.size() : 0;
        String preview = formatPreview(payload, len);
        log.info("PrintSink 收到数据: invocationId={}, actorAddr={}, size={}, preview={}",
                request.getInvocationId(), request.getActorAddr(), len, preview);

        return ActorInvokeResponse.newBuilder()
                .setInvocationId(request.getInvocationId())
                .build();
    }

    /**
     * 优先按 Java 反序列化得到对象再 toString，可读；失败则回退为原始字节的简短描述（避免乱码）。
     */
    private static String formatPreview(ByteString payload, int len) {
        if (len == 0) {
            return "<empty>";
        }
        byte[] bytes = payload.toByteArray();
        Object obj = javaDeserialize(bytes);
        if (obj != null) {
            return String.valueOf(obj);
        }
        return "bytes(" + len + ")";
    }

    private static Object javaDeserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return ois.readObject();
        } catch (Exception e) {
            return null;
        }
    }
}

