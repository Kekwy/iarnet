package com.kekwy.iarnet.workflow.port;

import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;

/**
 * 将本地 artifact 上传到 OSS 并返回拉取 URL。
 * 由 control-plane 在启用 OSS 时提供实现；未启用时 Executor 不注入，不传 URL。
 */
@Component
public interface ArtifactUrlProvider {

    /**
     * 上传本地文件到 OSS，并返回可供 Adapter 拉取的预签名 URL。
     *
     * @param artifactId  artifact 标识（如 nodeId）
     * @param localFile   本地文件路径
     * @return 预签名 URL，若未启用 OSS 或上传失败则 empty
     */
    default Optional<String> uploadAndGetUrl(String artifactId, Path localFile) {
        return Optional.empty();
    }
}
