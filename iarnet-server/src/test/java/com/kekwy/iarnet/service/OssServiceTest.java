package com.kekwy.iarnet.service;

import com.kekwy.iarnet.config.OssProperties;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Control Plane 侧 artifact 提交（上传至 OSS）功能单元测试。
 * <p>
 * 依赖本地 MinIO 运行在 {@code http://localhost:9002}，默认账号 minioadmin/minioadmin。
 * 运行前请启动 MinIO，例如：{@code cd deploy/minio && docker compose up -d} 并确保 API 端口为 9002。
 */
@DisplayName("OssService 提交 artifact")
class OssServiceTest {

    private static final String MINIO_ENDPOINT = "http://localhost:9002";
    private static final String TEST_BUCKET = "iarnet-artifacts-test";

    private OssProperties ossProperties;
    private MinioClient minioClient;
    private DefaultOssService ossService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ossProperties = new OssProperties();
        ossProperties.setEnabled(true);
        ossProperties.setEndpoint(MINIO_ENDPOINT);
        ossProperties.setAccessKey("minioadmin");
        ossProperties.setSecretKey("minioadmin");
        ossProperties.setBucket(TEST_BUCKET);
        ossProperties.setPresignedExpirySeconds(3600);

        minioClient = MinioClient.builder()
                .endpoint(MINIO_ENDPOINT)
                .credentials(ossProperties.getAccessKey(), ossProperties.getSecretKey())
                .build();

        ossService = new DefaultOssService(minioClient, ossProperties);
    }

    @Test
    @DisplayName("ensureBucket：应创建测试用 bucket")
    void ensureBucket_shouldCreateBucket() {
        assertDoesNotThrow(() -> ossService.ensureBucket());
        assertDoesNotThrow(() -> ossService.ensureBucket()); // 第二次调用应幂等
    }

    @Test
    @DisplayName("upload：上传文件应返回 objectKey 且可生成预签名 URL")
    void upload_shouldUploadAndReturnObjectKey() throws Exception {
        ossService.ensureBucket();

        Path file = tempDir.resolve("test-artifact.jar");
        Files.writeString(file, "fake jar content for test");

        String artifactId = "node-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String objectKey = ossService.upload(artifactId, file);

        assertNotNull(objectKey);
        assertTrue(objectKey.startsWith(artifactId + "/"));
        assertTrue(objectKey.endsWith("test-artifact.jar"));

        String presignedUrl = ossService.createPresignedGetUrl(objectKey, Duration.ofSeconds(60));
        assertNotNull(presignedUrl);
        assertTrue(presignedUrl.contains(MINIO_ENDPOINT) || presignedUrl.contains("localhost"));
        assertTrue(presignedUrl.contains(TEST_BUCKET));
    }

    @Test
    @DisplayName("createPresignedGetUrl：使用默认有效期应成功")
    void createPresignedGetUrl_defaultExpiry_shouldSucceed() throws Exception {
        ossService.ensureBucket();

        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "hello artifact");

        String objectKey = ossService.upload("sample-id", file);
        String url = ossService.createPresignedGetUrl(objectKey);

        assertNotNull(url);
        assertFalse(url.isBlank());
        assertTrue(url.contains(TEST_BUCKET));
    }

    @Test
    @DisplayName("upload：传入非文件路径应抛出 IllegalArgumentException")
    void upload_invalidPath_shouldThrow() {
        Path notAFile = tempDir.resolve("nonexistent.txt");
        assertFalse(Files.exists(notAFile));

        assertThrows(IllegalArgumentException.class, () ->
                ossService.upload("any-id", notAFile));
    }
}
