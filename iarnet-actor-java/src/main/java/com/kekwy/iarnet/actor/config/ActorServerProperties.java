package com.kekwy.iarnet.actor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Java Actor gRPC Server 的配置属性。
 *
 * 配置前缀：actor.server
 */
@ConfigurationProperties(prefix = "actor.server")
public class ActorServerProperties {

    /**
     * gRPC 监听端口。
     * 默认 9000，可通过配置或环境变量覆盖：
     * - application.yml: actor.server.port=9000
     * - 环境变量: ACTOR_SERVER_PORT=9000
     */
    private int port = 9000;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}

