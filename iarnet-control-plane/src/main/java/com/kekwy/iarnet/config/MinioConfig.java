package com.kekwy.iarnet.config;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置；仅当 iarnet.oss.enabled=true 时创建。
 */
@Configuration
@ConditionalOnProperty(name = "iarnet.oss.enabled", havingValue = "true")
public class MinioConfig {

    @Bean
    public MinioClient minioClient(OssProperties oss) {
        return MinioClient.builder()
                .endpoint(oss.getEndpoint())
                .credentials(oss.getAccessKey(), oss.getSecretKey())
                .build();
    }
}
