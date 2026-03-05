package com.kekwy.iarnet.service;

import java.nio.file.Path;
import java.time.Duration;

/**
 * OSS 服务：bucket 初始化、artifact 上传、预签名拉取 URL 生成。
 * 仅当 iarnet.oss.enabled=true 时存在该 Bean。
 */
public interface OssService {

    /**
     * 确保配置的 bucket 存在，不存在则创建。启动时调用一次完成初始化。
     */
    void ensureBucket();

    /**
     * 将本地文件上传到 OSS，对象键为 {@code artifactId/文件名}。
     *
     * @param artifactId  artifact 唯一标识（如 nodeId 或 workflowId-nodeId）
     * @param localFile   本地文件路径
     * @return 对象键（object key），用于后续生成预签名 URL
     */
    String upload(String artifactId, Path localFile);

    /**
     * 生成用于 GET 的预签名 URL，Adapter 凭此 URL 拉取 artifact。
     *
     * @param objectKey 对象键（通常由 {@link #upload} 返回）
     * @param expiry    链接有效期
     * @return 预签名 URL 字符串
     */
    String createPresignedGetUrl(String objectKey, Duration expiry);

    /**
     * 使用配置的默认有效期生成预签名 GET URL。
     *
     * @param objectKey 对象键（通常由 {@link #upload} 返回）
     * @return 预签名 URL 字符串
     */
    default String createPresignedGetUrl(String objectKey) {
        return createPresignedGetUrl(objectKey, Duration.ofSeconds(getPresignedExpirySeconds()));
    }

    /** 预签名 URL 默认有效期（秒），由实现从配置读取 */
    int getPresignedExpirySeconds();
}
