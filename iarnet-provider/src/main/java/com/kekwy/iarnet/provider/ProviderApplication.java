package com.kekwy.iarnet.provider;

import com.kekwy.iarnet.provider.config.ProviderProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Provider 启动入口。
 * <p>
 * Actor 注册 gRPC Server、ProviderRegistryClient、ProviderEngine 等由
 * {@link com.kekwy.iarnet.provider.config.ProviderRuntimeConfig} 提供并纳入 Spring 管理。
 */
@SpringBootApplication
@EnableConfigurationProperties(ProviderProperties.class)
public class ProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProviderApplication.class, args);
    }
}
