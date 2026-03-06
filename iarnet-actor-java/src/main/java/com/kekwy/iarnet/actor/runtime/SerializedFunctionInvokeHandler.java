package com.kekwy.iarnet.actor.runtime;

import com.kekwy.iarnet.proto.actor.ActorInvokeRequest;
import com.kekwy.iarnet.proto.actor.ActorInvokeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;

/**
 * 对 IR 中 {@code serialized_function}（Java 序列化字节）反序列化得到的对象进行调用，实现 {@link JavaInvokeHandler}。
 * <p>
 * 约定：反序列化后的对象具有「单参单返回值」方法（如 {@link com.kekwy.iarnet.api.function.MapFunction#apply}）。
 * 请求/响应的 payload 均为 Java 序列化字节：请求 payload 反序列化为输入对象，方法返回值序列化后作为响应 payload。
 */
public class SerializedFunctionInvokeHandler implements JavaInvokeHandler {

    private static final Logger log = LoggerFactory.getLogger(SerializedFunctionInvokeHandler.class);

    private final Object udf;
    private final Method singleArgMethod;

    public SerializedFunctionInvokeHandler(Object udf, Method singleArgMethod) {
        this.udf = udf;
        this.singleArgMethod = singleArgMethod;
        this.singleArgMethod.setAccessible(true);
    }

    /** 返回用户函数单参方法的入参类型 */
    public Class<?> getInputType() {
        return singleArgMethod.getParameterTypes()[0];
    }

    @Override
    public ActorInvokeResponse handle(ActorInvokeRequest request) throws Exception {
        byte[] inputBytes = request.getPayload() != null && !request.getPayload().isEmpty()
                ? request.getPayload().toByteArray()
                : new byte[0];

        Object in = deserialize(inputBytes);
        Object out = singleArgMethod.invoke(udf, in);
        byte[] outputBytes = serialize(out);

        return ActorInvokeResponse.newBuilder()
                .setInvocationId(request.getInvocationId())
                .setPayload(com.google.protobuf.ByteString.copyFrom(outputBytes))
                .build();
    }

    /**
     * 从对象中查找「单参、非 void 返回」的实例方法，优先 {@code apply}。
     */
    public static Method findSingleArgMethod(Object target) {
        Class<?> clazz = target.getClass();
        Method apply = null;
        for (Method m : clazz.getMethods()) {
            if (m.getParameterCount() != 1 || m.getReturnType() == void.class) {
                continue;
            }
            if ("apply".equals(m.getName())) {
                apply = m;
                break;
            }
            if (apply == null) {
                apply = m;
            }
        }
        return apply;
    }

    private static Object deserialize(byte[] bytes) throws Exception {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return ois.readObject();
        }
    }

    private static byte[] serialize(Object obj) throws Exception {
        if (obj == null) {
            return new byte[0];
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
        }
        return bos.toByteArray();
    }
}
