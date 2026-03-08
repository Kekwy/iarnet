package com.kekwy.iarnet.actor.runtime;

import com.kekwy.iarnet.proto.actor.ActorServiceGrpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Objects;

/**
 * 轻量级的 Java Actor gRPC Server 封装。
 * <p>
 * 用于在容器内启动一个 {@link ActorServiceGrpc.ActorServiceImplBase}，
 * 将 {@link JavaInvokeHandler} 暴露为 gRPC ActorService。
 * <p>
 * 典型用法：
 * <pre>
 *   JavaInvokeHandler handler = req -&gt; {
 *       // 解码 req.getPayload()，调用用户函数，再编码结果
 *       return ActorInvokeResponse.newBuilder()
 *           .setInvocationId(req.getInvocationId())
 *           .setPayload(resultBytes)
 *           .build();
 *   };
 *
 *   ActorServer server = new ActorServer(9000, handler);
 *   server.start();
 *   server.blockUntilShutdown();
 * </pre>
 */
public class ActorServer {

    private static final Logger log = LoggerFactory.getLogger(ActorServer.class);

    private final int port;
    private final JavaInvokeHandler handler;

    private Server server;

    public ActorServer(int port, JavaInvokeHandler handler) {
        if (port <= 0 || port > 65535) {
            throw new IllegalArgumentException("port must be in range (0, 65535)");
        }
        this.port = port;
        this.handler = Objects.requireNonNull(handler, "handler must not be null");
    }

    /**
     * 构建并启动 gRPC Server。
     */
    public void start() throws IOException {
        if (server != null) {
            throw new IllegalStateException("Server already started");
        }

        this.server = ServerBuilder.forPort(port)
                .addService(new ActorServiceImpl(handler))
                .build()
                .start();

        log.info("ActorServer started, listening on port {}", port);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("JVM shutdown detected, stopping ActorServer...");
            ActorServer.this.stop();
            log.info("ActorServer stopped.");
        }, "actor-server-shutdown"));
    }

    /**
     * 请求优雅关闭 gRPC Server。
     */
    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * 当前线程阻塞，直到 gRPC Server 关闭。
     */
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public int getPort() {
        return port;
    }
}

