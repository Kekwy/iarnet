package com.kekwy.iarnet.actor;

import com.kekwy.iarnet.actor.runtime.ActorServer;
import com.kekwy.iarnet.actor.runtime.JavaInvokeHandler;
import com.kekwy.iarnet.actor.runtime.handlers.EchoInvokeHandler;
import com.kekwy.iarnet.proto.agent.LocalAgentMessage;
import com.kekwy.iarnet.proto.agent.LocalAgentServiceGrpc;
import com.kekwy.iarnet.proto.agent.LocalRegisterActor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.shaded.io.netty.channel.socket.nio.NioSocketChannel;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * Java Actor 容器进程入口（纯 Java，无 Spring Boot）。
 * <p>
 * 责任：
 * <ul>
 *   <li>解析端口配置（环境变量 ACTOR_SERVER_PORT，默认 9000）</li>
 *   <li>创建 {@link ActorServer} 并启动 gRPC ActorService</li>
 *   <li>阻塞主线程直到进程退出</li>
 * </ul>
 * 初始版本使用 {@link EchoInvokeHandler} 作为默认实现。
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final String ENV_PORT = "ACTOR_SERVER_PORT";
    private static final int DEFAULT_PORT = 9000;

    public static void main(String[] args) throws InterruptedException {
        String agentAddr = System.getenv("IARNET_DEVICE_AGENT_ADDR");
        String actorAddr = System.getenv("IARNET_ACTOR_ADDR");

        LocalAgentStreamObserver streamObserver = new LocalAgentStreamObserver();
        ManagedChannel channel = connectToDeviceAgent(agentAddr, actorAddr, streamObserver);

        log.info("Actor 启动成功！");

        CountDownLatch latch = new CountDownLatch(1);

        // 收到 Ctrl+C / SIGTERM 时，关闭 channel 并唤醒主线程
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("收到关闭信号，准备退出 Actor");
            channel.shutdown();
            latch.countDown();
        }));

        // 像 Go 里阻塞在 <-sigCh 一样
        latch.await();
        log.info("Actor 进程退出");

    }

    private static JavaInvokeHandler createDefaultHandler() {
        // 目前使用简单回显实现；未来可根据配置替换为用户函数执行逻辑
        return new EchoInvokeHandler();
    }

    /**
     * 在 Actor 启动后回连本机 Device Agent，建立本地控制通道并注册自身地址。
     * 依赖环境变量：
     * - IARNET_DEVICE_AGENT_ADDR: Device Agent 可达地址，如 "127.0.0.1:10000"
     * - IARNET_ACTOR_ADDR: Actor 的虚拟地址（可选，用于注册）
     */
    private static ManagedChannel connectToDeviceAgent(String agentAddr, String actorAddr,
                                             StreamObserver<LocalAgentMessage> streamObserver) {
        if (agentAddr == null || agentAddr.isBlank()) {
            throw new RuntimeException("IARNET_DEVICE_AGENT_ADDR 未配置");
        }
        if (actorAddr == null) {
            throw new RuntimeException("没有 actor 地址");
        }

        try {
            ManagedChannel channel = ManagedChannelBuilder
                    .forTarget(agentAddr)
                    .usePlaintext()
                    .build();

            LocalAgentServiceGrpc.LocalAgentServiceStub stub =
                    LocalAgentServiceGrpc.newStub(channel);

            StreamObserver<LocalAgentMessage> stream = stub.localChannel(streamObserver);

            LocalRegisterActor reg = LocalRegisterActor.newBuilder()
                    .setActorAddr(actorAddr)
                    .build();
            stream.onNext(LocalAgentMessage.newBuilder()
                    .setRegisterActor(reg)
                    .build());

            log.info("已向 Device Agent 注册 Actor: actorAddr='{}',  agent={}", actorAddr, agentAddr);
            // 不在此处关闭 channel，保持长连接，供后续扩展使用

            return channel;
        } catch (Exception e) {
            log.error("连接 Device Agent 失败: addr={}, error={}", agentAddr, e.getMessage());
            throw new RuntimeException(e);
        }
    }
}

