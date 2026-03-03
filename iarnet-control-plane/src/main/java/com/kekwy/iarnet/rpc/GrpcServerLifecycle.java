package com.kekwy.iarnet.rpc;

import com.kekwy.iarnet.config.GrpcServerProperties;
import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 管理 gRPC Server 的生命周期，随 Spring 容器启动/停止。
 * <p>
 * 实现 {@link SmartLifecycle} 以便在所有 Bean 初始化完成后自动启动，
 * 并在容器关闭时优雅停机。
 */
@Component
public class GrpcServerLifecycle implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(GrpcServerLifecycle.class);

    private final GrpcServerProperties properties;
    private final List<BindableService> grpcServices;

    private Server server;
    private volatile boolean running;

    public GrpcServerLifecycle(GrpcServerProperties properties,
                               List<BindableService> grpcServices) {
        this.properties = properties;
        this.grpcServices = grpcServices;
    }

    @Override
    public void start() {
        int port = properties.getPort();
        ServerBuilder<?> builder = ServerBuilder.forPort(port);
        for (BindableService service : grpcServices) {
            builder.addService(service);
            log.info("注册 gRPC 服务: {}", service.bindService().getServiceDescriptor().getName());
        }

        try {
            server = builder.build().start();
            running = true;
            log.info("gRPC Server 启动成功，监听端口: {}", port);
        } catch (IOException e) {
            throw new IllegalStateException("gRPC Server 启动失败", e);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            log.info("正在关闭 gRPC Server ...");
            server.shutdown();
            try {
                if (!server.awaitTermination(30, TimeUnit.SECONDS)) {
                    log.warn("gRPC Server 未能在 30 秒内优雅关闭，强制终止");
                    server.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                server.shutdownNow();
            }
            running = false;
            log.info("gRPC Server 已关闭");
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
