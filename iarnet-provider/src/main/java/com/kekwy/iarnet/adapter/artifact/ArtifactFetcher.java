package com.kekwy.iarnet.adapter.artifact;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 artifact_url 拉取 artifact 到本地 ArtifactStore，按 artifact_id 去重：
 * 同一 artifact_id 正在拉取时等待完成，已拉取则直接返回本地路径。
 */
public class ArtifactFetcher {

    private static final Logger log = LoggerFactory.getLogger(ArtifactFetcher.class);

    private final ArtifactStore artifactStore;
    private final HttpClient httpClient;

    /** artifact_id → 已拉取到的本地文件路径 */
    private final Map<String, Path> cache = new ConcurrentHashMap<>();
    /** artifact_id → 正在拉取的 Future */
    private final Map<String, CompletableFuture<Path>> inFlight = new ConcurrentHashMap<>();

    public ArtifactFetcher(ArtifactStore artifactStore) {
        this.artifactStore = artifactStore;
        this.httpClient = HttpClient.newBuilder().build();
    }

    /**
     * 拉取 artifact：若已缓存或正在拉取则复用，否则从 url 下载并存入 store。
     *
     * @param artifactId artifact 标识
     * @param artifactUrl 拉取地址（如 OSS 预签名 URL）
     * @return 本地文件路径
     */
    public Path fetch(String artifactId, String artifactUrl) throws IOException {
        if (artifactId == null || artifactId.isBlank() || artifactUrl == null || artifactUrl.isBlank()) {
            throw new IllegalArgumentException("artifactId 与 artifactUrl 不能为空");
        }

        Path cached = cache.get(artifactId);
        if (cached != null && java.nio.file.Files.exists(cached)) {
            log.debug("Artifact 命中缓存: artifactId={}", artifactId);
            return cached;
        }

        CompletableFuture<Path> future = inFlight.computeIfAbsent(artifactId, k ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return doFetch(artifactId, artifactUrl);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }).whenComplete((path, ex) -> {
                    if (path != null) cache.put(artifactId, path);
                    inFlight.remove(artifactId);
                }));

        try {
            return future.join();
        } catch (Exception e) {
            inFlight.remove(artifactId);
            if (e.getCause() instanceof IOException ioe) throw ioe;
            throw new IOException("拉取 artifact 失败: " + artifactId, e);
        }
    }

    private Path doFetch(String artifactId, String artifactUrl) throws IOException {
        log.info("开始拉取 artifact: artifactId={}", artifactId);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(artifactUrl))
                .GET()
                .build();

        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (response.statusCode() != 200) {
            throw new IOException("拉取失败 HTTP " + response.statusCode() + ": " + artifactUrl);
        }

        String fileName = fileNameFromUrl(artifactUrl);
        Path path = artifactStore.store(artifactId, fileName, response.body());
        log.info("Artifact 拉取完成: artifactId={}, path={}", artifactId, path);
        return path;
    }

    private static String fileNameFromUrl(String url) {
        try {
            String path = URI.create(url).getPath();
            if (path != null && path.contains("/")) {
                String name = path.substring(path.lastIndexOf('/') + 1);
                if (!name.isBlank()) return name;
            }
        } catch (Exception ignored) { }
        return "artifact";
    }
}
