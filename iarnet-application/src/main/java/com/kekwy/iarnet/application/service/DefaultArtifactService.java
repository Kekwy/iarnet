package com.kekwy.iarnet.application.service;

import com.kekwy.iarnet.application.model.Artifact;
import com.kekwy.iarnet.application.model.ArtifactEntity;
import com.kekwy.iarnet.application.repository.ArtifactRepository;
import com.kekwy.iarnet.model.ID;
import com.kekwy.iarnet.util.IDUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
@Slf4j
public class DefaultArtifactService implements ArtifactService {

    private final ArtifactRepository artifactRepository;

    /**
     * 构建产物存放基础目录，可在 application.yml 中通过
     * {@code iarnet.artifact-base-dir} 配置，默认为 {@code ./artifacts}。
     */
    private final Path artifactBaseDir;

    public DefaultArtifactService(
            ArtifactRepository artifactRepository,
            @Value("${iarnet.artifact-base-dir:./artifacts}") String artifactBaseDir) {
        this.artifactRepository = artifactRepository;
        this.artifactBaseDir = Paths.get(artifactBaseDir);
    }

    @Override
    @Transactional
    public Artifact create(ID applicationID, String artifactPath) {
        if (applicationID == null || applicationID.getValue() == null) {
            throw new IllegalArgumentException("applicationID 不能为空");
        }
        if (artifactPath == null || artifactPath.isBlank()) {
            throw new IllegalArgumentException("artifactPath 不能为空");
        }

        Path source = Paths.get(artifactPath);
        if (!Files.isRegularFile(source)) {
            throw new IllegalArgumentException("artifactPath 不是有效文件: " + artifactPath);
        }

        // 目标目录：<artifactBaseDir>/<AppID>/
        Path appDir = artifactBaseDir.resolve(applicationID.getValue());
        try {
            Files.createDirectories(appDir);
        } catch (IOException e) {
            throw new RuntimeException("无法创建 artifact 目录: " + appDir, e);
        }

        // 目标文件名：沿用原文件名
        Path target = appDir.resolve(source.getFileName());
        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(
                    "复制 artifact 失败: " + source + " -> " + target, e);
        }

        // 生成 Artifact ID，并持久化记录
        ID artifactID = IDUtil.genArtifactID();

        ArtifactEntity entity = new ArtifactEntity();
        entity.setArtifactID(artifactID.getValue());
        entity.setApplicationID(applicationID.getValue());
        entity.setArtifactPath(target.toAbsolutePath().toString());

        artifactRepository.save(entity);

        Artifact artifact = new Artifact();
        artifact.setApplicationID(applicationID);
        artifact.setArtifactID(artifactID);
        artifact.setArtifactPath(entity.getArtifactPath());

        log.info("已保存 artifact: appId={}, artifactId={}, path={}",
                applicationID.getValue(), artifactID.getValue(), entity.getArtifactPath());

        return artifact;
    }

    @Override
    @Transactional(readOnly = true)
    public Artifact get(ID artifactID) {
        if (artifactID == null || artifactID.getValue() == null) {
            throw new IllegalArgumentException("artifactID 不能为空");
        }

        ArtifactEntity entity = artifactRepository.findById(artifactID.getValue())
                .orElseThrow(() -> new IllegalArgumentException("构建产物不存在: " + artifactID.getValue()));

        Artifact artifact = new Artifact();
        artifact.setArtifactID(artifactID);
        artifact.setApplicationID(ID.of(entity.getApplicationID()));
        artifact.setArtifactPath(entity.getArtifactPath());
        return artifact;
    }
}
