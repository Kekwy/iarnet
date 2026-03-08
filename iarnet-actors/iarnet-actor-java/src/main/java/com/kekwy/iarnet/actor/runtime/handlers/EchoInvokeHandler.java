package com.kekwy.iarnet.actor.runtime.handlers;

import com.kekwy.iarnet.actor.runtime.JavaInvokeHandler;
import com.kekwy.iarnet.proto.actor.ActorInvokeRequest;
import com.kekwy.iarnet.proto.actor.ActorInvokeResponse;

/**
 * 一个简单的回显处理器示例：
 * <ul>
 *   <li>将请求中的 payload 原样返回</li>
 *   <li>不设置 error，ActorInfo 由外层 {@link com.kekwy.iarnet.actor.runtime.ActorServiceImpl} 补充</li>
 * </ul>
 * 可作为开发和调试阶段的默认实现。
 */
public class EchoInvokeHandler implements JavaInvokeHandler {

    @Override
    public ActorInvokeResponse handle(ActorInvokeRequest request) {
        return ActorInvokeResponse.newBuilder()
                .setInvocationId(request.getInvocationId())
                .setPayload(request.getPayload())
                .build();
    }
}

