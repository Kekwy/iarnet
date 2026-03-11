package com.kekwy.iarnet.rpc;

import com.kekwy.iarnet.proto.adapter.*;
import com.kekwy.iarnet.resource.adapter.AdapterRegistry;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * control-plane 侧的 {@code AdapterRegistryService} gRPC 实现。
 * <p>
 * 处理 Adapter 的注册、心跳、注销以及 CommandChannel 双向流。
 * CommandChannel 通过 gRPC metadata 中的 {@code adapter-id} 标识连接所属 Adapter。
 */
@Component
public class AdapterRegistryGrpcService
        extends AdapterRegistryServiceGrpc.AdapterRegistryServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(AdapterRegistryGrpcService.class);

    static final Metadata.Key<String> ADAPTER_ID_METADATA_KEY =
            Metadata.Key.of("adapter-id", Metadata.ASCII_STRING_MARSHALLER);
    static final Context.Key<String> ADAPTER_ID_CTX_KEY = Context.key("adapter-id");

    private final AdapterRegistry adapterRegistry;

    public AdapterRegistryGrpcService(AdapterRegistry adapterRegistry) {
        this.adapterRegistry = adapterRegistry;
    }

    // ======================== Register ========================

    @Override
    public void register(RegisterRequest request,
                         StreamObserver<RegisterResponse> responseObserver) {
        log.info("收到 Adapter 注册请求: name={}, type={}",
                request.getAdapterName(), request.getAdapterType());
        try {
            String adapterId = adapterRegistry.register(request);
            responseObserver.onNext(RegisterResponse.newBuilder()
                    .setAccepted(true)
                    .setAdapterId(adapterId)
                    .setMessage("注册成功")
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Adapter 注册失败: name={}", request.getAdapterName(), e);
            responseObserver.onNext(RegisterResponse.newBuilder()
                    .setAccepted(false)
                    .setMessage("注册失败: " + e.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    // ======================== Heartbeat ========================

    @Override
    public void heartbeat(HeartbeatRequest request,
                          StreamObserver<HeartbeatResponse> responseObserver) {
        try {
            adapterRegistry.heartbeat(request.getAdapterId(), request.getUsage());
            responseObserver.onNext(HeartbeatResponse.newBuilder()
                    .setAcknowledged(true)
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("处理心跳失败: adapterId={}", request.getAdapterId(), e);
            responseObserver.onNext(HeartbeatResponse.newBuilder()
                    .setAcknowledged(false)
                    .build());
            responseObserver.onCompleted();
        }
    }

    // ======================== Deregister ========================

    @Override
    public void deregister(DeregisterRequest request,
                           StreamObserver<DeregisterResponse> responseObserver) {
        log.info("收到 Adapter 注销请求: adapterId={}", request.getAdapterId());
        try {
            adapterRegistry.deregister(request.getAdapterId());
            responseObserver.onNext(DeregisterResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage("注销成功")
                    .build());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Adapter 注销失败: adapterId={}", request.getAdapterId(), e);
            responseObserver.onNext(DeregisterResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("注销失败: " + e.getMessage())
                    .build());
            responseObserver.onCompleted();
        }
    }

    // ======================== CommandChannel ========================

    /**
     * 双向流 CommandChannel。
     * <p>
     * gRPC 签名: {@code rpc CommandChannel(stream CommandResponse) returns (stream Command)}
     * <ul>
     *   <li>{@code commandSender} — 用于向 Adapter 推送 Command</li>
     *   <li>返回值 — 处理 Adapter 回传的 CommandResponse</li>
     * </ul>
     * Adapter 在调用时通过 metadata {@code adapter-id} 标识自身，
     * 由 {@link AdapterIdInterceptor} 提取到 gRPC Context 中。
     */
    @Override
    public StreamObserver<CommandResponse> commandChannel(StreamObserver<Command> commandSender) {
        String adapterId = ADAPTER_ID_CTX_KEY.get();
        if (adapterId == null || adapterId.isEmpty()) {
            log.error("CommandChannel 缺少 adapter-id metadata");
            commandSender.onError(Status.UNAUTHENTICATED
                    .withDescription("缺少 adapter-id metadata header")
                    .asRuntimeException());
            return new NoOpStreamObserver<>();
        }

        log.info("Adapter 建立 CommandChannel: adapterId={}", adapterId);
        adapterRegistry.openCommandChannel(adapterId, commandSender);

        return new StreamObserver<>() {
            @Override
            public void onNext(CommandResponse response) {
                adapterRegistry.handleCommandResponse(adapterId, response);
            }

            @Override
            public void onError(Throwable t) {
                log.warn("CommandChannel 异常断开: adapterId={}, reason={}",
                        adapterId, t.getMessage());
                adapterRegistry.closeCommandChannel(adapterId);
            }

            @Override
            public void onCompleted() {
                log.info("CommandChannel 由 Adapter 正常关闭: adapterId={}", adapterId);
                adapterRegistry.closeCommandChannel(adapterId);
            }
        };
    }

    // ======================== 内部类 ========================

    /**
     * 从 gRPC metadata 提取 {@code adapter-id} 并注入 Context。
     * 仅作用于 {@code AdapterRegistryService} 的方法。
     * <p>
     * 注意：由于 gRPC 生成的基类中 {@code bindService()} 是 final，
     * 因此无法在本类中重写 bindService 直接附加拦截器。
     * 实际使用时应在构建 Server 时通过
     * {@code ServerInterceptors.intercept(service, new AdapterIdInterceptor())}
     * 显式包装本服务。
     */
    public static class AdapterIdInterceptor implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call, Metadata headers,
                ServerCallHandler<ReqT, RespT> next) {
            String adapterId = headers.get(ADAPTER_ID_METADATA_KEY);
            if (adapterId != null) {
                Context ctx = Context.current().withValue(ADAPTER_ID_CTX_KEY, adapterId);
                return Contexts.interceptCall(ctx, call, headers, next);
            }
            return next.startCall(call, headers);
        }
    }

    /**
     * 空操作的 StreamObserver，用于错误场景下的占位返回。
     */
    private static class NoOpStreamObserver<T> implements StreamObserver<T> {
        @Override
        public void onNext(T value) { }

        @Override
        public void onError(Throwable t) { }

        @Override
        public void onCompleted() { }
    }
}
