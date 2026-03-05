# 控制平面接入 MinIO 与初始化

控制平面通过配置与可选 Bean 接入 MinIO，启动时自动完成 bucket 初始化。

---

## 1. 配置

在 `iarnet-control-plane/src/main/resources/application.yml` 中已增加 `iarnet.oss` 配置：

```yaml
iarnet:
  oss:
    enabled: true
    endpoint: http://localhost:9000
    access-key: minioadmin
    secret-key: minioadmin
    bucket: iarnet-artifacts
    presigned-expiry-seconds: 3600
```

| 项 | 说明 |
|----|------|
| `enabled` | 为 `true` 时创建 MinIO 客户端并执行 bucket 初始化；为 `false` 时不依赖 MinIO，不创建相关 Bean。 |
| `endpoint` | MinIO API 地址，本地部署一般为 `http://localhost:9000`。 |
| `access-key` / `secret-key` | MinIO 控制台或环境配置的访问密钥。 |
| `bucket` | 用于存放 artifact 的 bucket 名称，启动时若不存在会自动创建。 |
| `presigned-expiry-seconds` | 生成给 Adapter 拉取用的预签名 URL 的有效期（秒）。 |

部署前请先启动 MinIO（例如使用 `deploy/minio/docker-compose.yml`），并将 `endpoint` 改为实际地址（非本机时改为 MinIO 的 host:port）。

---

## 2. 初始化时机

- 当 `iarnet.oss.enabled=true` 时，会创建 `MinioClient` 与 `OssService`。
- `DefaultOssService` 在构造完成后通过 `@PostConstruct` 调用 `ensureBucket()`：
  - 若配置的 `bucket` 不存在则创建；
  - 若已存在则跳过。
- 因此**无需额外脚本**，控制平面启动即完成 OSS 初始化。

---

## 3. 使用方式（后续接入调度/Executor）

- **上传 artifact**：`ossService.upload(artifactId, localPath)`，返回对象键 `objectKey`（格式为 `artifactId/文件名`）。
- **生成拉取 URL**：`ossService.createPresignedGetUrl(objectKey)`（使用配置的默认有效期）或 `ossService.createPresignedGetUrl(objectKey, Duration.ofMinutes(30))`。
- 在部署请求中把该 URL 下发给 Adapter，由 Adapter 按「pull-on-deploy」拉取后再部署。

当 `iarnet.oss.enabled=false` 时，`OssService` 不会存在，注入处需使用 `@Autowired(required = false)` 或 `@ConditionalOnBean(OssService.class)` 等做可选依赖处理。

---

## 4. 依赖

控制平面已引入 MinIO Java SDK（`io.minio:minio`），无需额外依赖。
