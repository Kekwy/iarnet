package com.kekwy.iarnet.actor.runtime;

import com.kekwy.iarnet.proto.actor.ActorInvokeRequest;
import com.kekwy.iarnet.proto.actor.ActorInvokeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * 通过反射调用用户 JAR 中指定类的方法，实现 {@link JavaInvokeHandler}。
 * <p>
 * 支持的方法签名：
 * <ul>
 *   <li>{@code public static byte[] methodName(byte[] payload)}</li>
 *   <li>{@code public byte[] methodName(byte[] payload)}（实例方法，无参构造创建实例）</li>
 * </ul>
 * 将请求的 payload 传入，方法返回的 byte[] 写入响应的 payload。
 */
public class ReflectionInvokeHandler implements JavaInvokeHandler {

    private static final Logger log = LoggerFactory.getLogger(ReflectionInvokeHandler.class);

    private final Method method;
    private final Object target; // null 表示静态方法

    public ReflectionInvokeHandler(Method method, Object target) {
        this.method = method;
        this.target = target;
        this.method.setAccessible(true);
    }

    @Override
    public ActorInvokeResponse handle(ActorInvokeRequest request) throws Exception {
        byte[] input = request.getPayload() != null && !request.getPayload().isEmpty()
                ? request.getPayload().toByteArray()
                : new byte[0];

        Object result = method.invoke(target, input);
        byte[] output = result != null ? (byte[]) result : new byte[0];

        return ActorInvokeResponse.newBuilder()
                .setInvocationId(request.getInvocationId())
                .setPayload(com.google.protobuf.ByteString.copyFrom(output))
                .build();
    }
}
