package com.kekwy.iarnet.artifact;

import com.kekwy.iarnet.execution.port.ArtifactUrlProvider;
import com.kekwy.iarnet.service.OssService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;

/**
 * 使用控制平面 OSS 服务上传 artifact 并返回预签名拉取 URL。
 */
@Component
@ConditionalOnBean(OssService.class)
public class OssArtifactUrlProvider implements ArtifactUrlProvider {

    private static final Logger log = LoggerFactory.getLogger(OssArtifactUrlProvider.class);

    private final OssService ossService;

    public OssArtifactUrlProvider(OssService ossService) {
        this.ossService = ossService;
    }

    @Override
    public Optional<String> uploadAndGetUrl(String artifactId, Path localFile) {
        try {
            String objectKey = ossService.upload(artifactId, localFile);
            String url = ossService.createPresignedGetUrl(objectKey);
            return Optional.of(url);
        } catch (Exception e) {
            log.warn("上传 artifact 失败: artifactId={}, path={}", artifactId, localFile, e);
            return Optional.empty();
        }
    }
}
