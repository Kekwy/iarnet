package com.kekwy.iarnet.service;

import com.kekwy.iarnet.config.OssProperties;
import io.minio.*;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 基于 MinIO 的 OSS 实现：启动时确保 bucket 存在，提供上传与预签名 URL。
 */
@Service
@ConditionalOnBean(MinioClient.class)
public class DefaultOssService implements OssService {

    private static final Logger log = LoggerFactory.getLogger(DefaultOssService.class);

    private final MinioClient minioClient;
    private final OssProperties oss;

    public DefaultOssService(MinioClient minioClient, OssProperties oss) {
        this.minioClient = minioClient;
        this.oss = oss;
    }

    /** 启动时确保 bucket 存在，完成 OSS 初始化 */
    @PostConstruct
    public void init() {
        ensureBucket();
    }

    @Override
    public void ensureBucket() {
        String bucket = oss.getBucket();
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("OSS bucket 已创建: {}", bucket);
            } else {
                log.debug("OSS bucket 已存在: {}", bucket);
            }
        } catch (Exception e) {
            throw new RuntimeException("OSS bucket 初始化失败: " + bucket, e);
        }
    }

    @Override
    public String upload(String artifactId, Path localFile) {
        if (!Files.isRegularFile(localFile)) {
            throw new IllegalArgumentException("不是有效文件: " + localFile);
        }
        String fileName = localFile.getFileName().toString();
        String objectKey = artifactId + "/" + fileName;
        String bucket = oss.getBucket();
        try {
            long size = Files.size(localFile);
            String contentType = contentTypeFromFileName(fileName);
            try (InputStream in = Files.newInputStream(localFile)) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucket)
                                .object(objectKey)
                                .stream(in, size, -1)
                                .contentType(contentType)
                                .build());
            }
            log.info("Artifact 已上传 OSS: objectKey={}, size={}", objectKey, size);
            return objectKey;
        } catch (Exception e) {
            throw new RuntimeException("上传 artifact 失败: " + objectKey, e);
        }
    }

    @Override
    public int getPresignedExpirySeconds() {
        return oss.getPresignedExpirySeconds();
    }

    @Override
    public String createPresignedGetUrl(String objectKey, Duration expiry) {
        try {
            int seconds = (int) Math.min(expiry.getSeconds(), Integer.MAX_VALUE);
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(oss.getBucket())
                            .object(objectKey)
                            .expiry(seconds, TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("生成预签名 URL 失败: " + objectKey, e);
        }
    }

    private static String contentTypeFromFileName(String fileName) {
        if (fileName.endsWith(".jar")) return "application/java-archive";
        if (fileName.endsWith(".tar.gz")) return "application/gzip";
        return "application/octet-stream";
    }
}
