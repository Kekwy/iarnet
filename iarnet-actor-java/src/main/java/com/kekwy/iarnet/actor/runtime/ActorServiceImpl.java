package com.kekwy.iarnet.actor.runtime;

import com.kekwy.iarnet.proto.actor.ActorInfo;
import com.kekwy.iarnet.proto.actor.ActorInvokeRequest;
import com.kekwy.iarnet.proto.actor.ActorInvokeResponse;
import com.kekwy.iarnet.proto.actor.ActorServiceGrpc;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认的 Java ActorService gRPC 实现。
 * <p>
 * 负责：
 * <ul>
 *   <li>接收 Invoke 请求</li>
 *   <li>记录执行耗时，填充 {@link ActorInfo}</li>
 *   <li>调用注入的 {@link JavaInvokeHandler} 执行具体业务逻辑</li>
 *   <li>统一处理异常，将错误信息写入 response.error 字段</li>
 * </ul>
 */
public class ActorServiceImpl extends ActorServiceGrpc.ActorServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(ActorServiceImpl.class);

    private final JavaInvokeHandler handler;

    public ActorServiceImpl(JavaInvokeHandler handler) {
        this.handler = handler;
    }

    @Override
    public void invoke(ActorInvokeRequest request,
                       StreamObserver<ActorInvokeResponse> responseObserver) {
        long startNs = System.nanoTime();
        ActorInvokeResponse.Builder respBuilder = ActorInvokeResponse.newBuilder()
                .setInvocationId(request.getInvocationId());

        try {
            ActorInvokeResponse businessResp = handler.handle(request);

            // 如果业务层已经构造了完整的 ActorInvokeResponse，则在其基础上补充 ActorInfo
            ActorInvokeResponse.Builder merged = businessResp.toBuilder();
            long calcLatencyMs = (System.nanoTime() - startNs) / 1_000_000L;
            ActorInfo info = businessResp.hasInfo()
                    ? businessResp.getInfo().toBuilder()
                    .setCalcLatencyMs(calcLatencyMs)
                    .build()
                    : ActorInfo.newBuilder()
                    .setCalcLatencyMs(calcLatencyMs)
                    .build();

            merged.setInfo(info);
            responseObserver.onNext(merged.build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            long calcLatencyMs = (System.nanoTime() - startNs) / 1_000_000L;
            log.error("Actor invoke failed: invocationId={}, actorAddr={}",
                    request.getInvocationId(), request.getActorAddr(), e);

            ActorInfo info = ActorInfo.newBuilder()
                    .setCalcLatencyMs(calcLatencyMs)
                    .build();

            ActorInvokeResponse errorResp = respBuilder
                    .setError(e.toString())
                    .setInfo(info)
                    .build();

            responseObserver.onNext(errorResp);
            responseObserver.onCompleted();
        }
    }
}

