package com.kekwy.iarnet.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "grpc.server")
public class GrpcServerProperties {

    private int port = 9090;
    private int maxInboundMessageSize = 64 * 1024 * 1024;

}
