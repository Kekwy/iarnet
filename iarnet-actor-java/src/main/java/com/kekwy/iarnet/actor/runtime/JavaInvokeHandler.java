package com.kekwy.iarnet.actor.runtime;

import com.kekwy.iarnet.proto.actor.ActorInvokeRequest;
import com.kekwy.iarnet.proto.actor.ActorInvokeResponse;

/**
 * Java Actor 运行时时用于处理 Invoke 请求的抽象接口。
 * <p>
 * 容器内的 ActorService 实现会将 gRPC 的 {@link ActorInvokeRequest}
 * 委托给该接口的实现进行业务处理，然后返回 {@link ActorInvokeResponse}。
 * <p>
 * 上层可以自由选择 payload 的编码格式（例如 JSON / Proto / Avro），
 * 本接口只处理“整体请求 → 整体响应”的转换逻辑。
 */
@FunctionalInterface
public interface JavaInvokeHandler {

    /**
     * 处理一次 Invoke 调用。
     *
     * @param request gRPC ActorInvokeRequest
     * @return 对应的 ActorInvokeResponse
     * @throws Exception 处理过程中出现的任何异常，外层会捕获并转换为 error 字段
     */
    ActorInvokeResponse handle(ActorInvokeRequest request) throws Exception;
}

