package com.kekwy.iarnet.actor;

import com.kekwy.iarnet.actor.runtime.DelegatingInvokeHandler;
import com.kekwy.iarnet.actor.runtime.FunctionDescriptorLoader;
import com.kekwy.iarnet.actor.runtime.JavaInvokeHandler;
import com.kekwy.iarnet.actor.runtime.UserJarLoader;
import com.kekwy.iarnet.actor.runtime.handlers.EchoInvokeHandler;
import com.kekwy.iarnet.actor.runtime.handlers.PrintInvokeHandler;
import com.kekwy.iarnet.proto.agent.LocalAgentMessage;
import com.kekwy.iarnet.proto.agent.LocalAgentServiceGrpc;
import com.kekwy.iarnet.proto.agent.LocalRegisterActor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

/**
 * Java Actor 容器进程入口（纯 Java，无 Spring Boot）。
 * <p>
 * 责任：
 * <ul>
 *   <li>启动时即加载执行函数（无需等待 gRPC）：若设置 {@code IARNET_ACTOR_FUNCTION_FILE}，从该文件读取 IR FunctionDescriptor（proto 二进制）并加载；否则从 env {@code IARNET_ACTOR_JAR} / {@code IARNET_ACTOR_CLASS} / {@code IARNET_ACTOR_METHOD} 加载；均未配置时使用 {@link EchoInvokeHandler}</li>
 *   <li>解析端口配置（环境变量 ACTOR_SERVER_PORT，默认 9000）</li>
 *   <li>创建 {@link ActorServer} 并启动 gRPC ActorService</li>
 *   <li>回连 Device Agent 并注册自身地址</li>
 *   <li>阻塞主线程直到进程退出</li>
 * </ul>
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final String ENV_FUNCTION_FILE = "IARNET_ACTOR_FUNCTION_FILE";
    private static final String ENV_NODE_KIND = "IARNET_NODE_KIND";
    private static final String ENV_SINK_KIND = "IARNET_SINK_KIND";
    public static void main(String[] args) throws InterruptedException {
        DelegatingInvokeHandler delegatingHandler = new DelegatingInvokeHandler(createHandler());

        String agentAddr = System.getenv("IARNET_DEVICE_AGENT_ADDR");
        String actorAddr = System.getenv("IARNET_ACTOR_ADDR");

        LocalAgentStreamObserver streamObserver = new LocalAgentStreamObserver(null, delegatingHandler);
        ManagedChannel channel = connectToDeviceAgent(agentAddr, actorAddr, streamObserver);

        log.info("Actor 启动成功，并已连接本机 Device Agent");

        CountDownLatch latch = new CountDownLatch(1);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("收到关闭信号，准备退出 Actor");
            channel.shutdown();
            latch.countDown();
        }));

        latch.await();
        log.info("Actor 进程退出");
    }

    /**
     * 按优先级加载执行函数（均在连接 Device Agent 前完成，无需等待 gRPC）：
     * 1）若设置 IARNET_ACTOR_FUNCTION_FILE，从该文件读取 FunctionDescriptor（proto 二进制）并加载；
     * 2）否则从 IARNET_ACTOR_JAR / IARNET_ACTOR_CLASS / IARNET_ACTOR_METHOD 反射加载；
     * 3）否则使用默认回显 Handler。
     */
    private static JavaInvokeHandler createHandler() {
        // 方案 A：根据 NodeKind / SinkKind 选择内建算子
        String nodeKind = System.getenv(ENV_NODE_KIND);
        if (nodeKind != null && nodeKind.equalsIgnoreCase("SINK")) {
            String sinkKind = System.getenv(ENV_SINK_KIND);
            if (sinkKind != null && sinkKind.equalsIgnoreCase("PRINT")) {
                log.info("检测到 Sink=PRINT，使用内建 PrintInvokeHandler");
                return new PrintInvokeHandler();
            }
        }

        String functionFile = System.getenv(ENV_FUNCTION_FILE);
        if (functionFile != null && !functionFile.isBlank()) {
            JavaInvokeHandler fromFile = FunctionDescriptorLoader.fromFile(functionFile.trim());
            if (fromFile != null) {
                return fromFile;
            }
            log.warn("从 {} 加载失败，尝试其他方式", ENV_FUNCTION_FILE);
        }
        JavaInvokeHandler fromJar = UserJarLoader.loadFromEnv();
        if (fromJar != null) {
            return fromJar;
        }
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

