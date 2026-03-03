package com.kekwy.iarnet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "grpc.server")
public class GrpcServerProperties {

    private int port = 9090;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
