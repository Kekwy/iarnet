package com.kekwy.iarnet.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OSS（MinIO / S3 兼容）配置，用于 artifact 存储与预签名 URL 生成。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "iarnet.oss")
public class OssProperties {

    /** 是否启用 OSS；为 false 时不创建 MinIO 客户端，不执行 bucket 初始化 */
    private boolean enabled = false;
    /** MinIO 服务地址，如 http://localhost:9000 */
    private String endpoint = "http://localhost:9000";
    /** 访问密钥 */
    private String accessKey = "minioadmin";
    /** 秘密密钥 */
    private String secretKey = "minioadmin";
    /** 用于存放 artifact 的 bucket 名称 */
    private String bucket = "iarnet-artifacts";
    /** 预签名 GET URL 有效期（秒），Adapter 拉取 artifact 使用 */
    private int presignedExpirySeconds = 3600;
}
