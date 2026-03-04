package com.kekwy.iarnet.actor;

import com.kekwy.iarnet.actor.config.ActorServerProperties;
import com.kekwy.iarnet.actor.runtime.ActorServer;
import com.kekwy.iarnet.actor.runtime.JavaInvokeHandler;
import com.kekwy.iarnet.actor.runtime.handlers.EchoInvokeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.io.IOException;

/**
 * Java Actor 容器进程的 Spring Boot 入口。
 * <p>
 * 责任：
 * <ul>
 *   <li>加载配置（actor.server.port 等）</li>
 *   <li>创建 {@link ActorServer} 并启动 gRPC ActorService</li>
 *   <li>阻塞主线程直到进程退出</li>
 * </ul>
 * <p>
 * 初始版本使用 {@link EchoInvokeHandler} 作为默认实现，
 * 后续可以通过 Spring 配置/条件装配替换为真正的用户函数执行逻辑。
 */
@SpringBootApplication
@EnableConfigurationProperties(ActorServerProperties.class)
public class ActorApplication implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ActorApplication.class);

    private final ActorServerProperties properties;
    private final JavaInvokeHandler invokeHandler;

    public ActorApplication(ActorServerProperties properties,
                            JavaInvokeHandler invokeHandler) {
        this.properties = properties;
        this.invokeHandler = invokeHandler;
    }

    public static void main(String[] args) {
        SpringApplication.run(ActorApplication.class, args);
    }

    /**
     * 默认的 JavaInvokeHandler Bean：简单回显实现。
     * <p>
     * 未来可以在容器中定义自定义的 {@link JavaInvokeHandler} Bean，
     * 覆盖此默认实现以加载/调用真正的用户函数。
     */
    @Bean
    public JavaInvokeHandler javaInvokeHandler() {
        return new EchoInvokeHandler();
    }

    @Override
    public void run(String... args) throws Exception {
        int port = properties.getPort();
        ActorServer server = new ActorServer(port, invokeHandler);

        try {
            server.start();
            log.info("Java Actor gRPC server started on port {}", port);
            server.blockUntilShutdown();
        } catch (IOException e) {
            log.error("Failed to start Actor gRPC server on port {}", port, e);
            // 启动失败时退出进程，避免容器处于不健康状态
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Actor gRPC server interrupted, shutting down");
        }
    }
}

