package com.kekwy.iarnet.application.model;

import com.kekwy.iarnet.model.ID;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Workspace 领域模型，负责描述并初始化与某个应用相关的工作目录结构。
 *
 * 约定目录结构（workspaceDir 为根）：
 * - workspaceDir/                  工作空间根目录
 *   - source/                      源码克隆目录（git clone 目标）
 *   - artifacts/                   构建产物在工作空间下的缓存目录（当前预留）
 *   - git-clone.log                git clone 日志
 *   - .iarnet/build.log            Maven 构建日志
 */
@Data
@Slf4j
public class Workspace {

    /** Workspace ID（主键，与数据库中的 workspace_id 对应） */
    private final ID workspaceID;

    /** 应用 ID（AppID.xxxx） */
    private final ID applicationID;

    /** 工作空间根目录 */
    private final Path workspaceDir;

    /** 源码目录（git clone 的目标目录） */
    private final Path sourceDir;

    /** 构建产物目录（当前预留，可用于缓存 jar 等） */
    private final Path artifactDir;

    /** git clone 日志文件路径 */
    private final Path gitCloneLogFile;

    /** Maven 构建日志文件路径 */
    private final Path buildLogFile;

    /** 应用运行时日志文件路径 */
    private final Path appLogFile;

    /**
     * 构造 Workspace 时，负责检查并初始化必要的目录结构。
     *
     * @param workspaceID  工作空间 ID
     * @param applicationID 应用 ID
     * @param workspaceDir  工作空间根目录（字符串路径）
     */
    public Workspace(ID workspaceID, ID applicationID, String workspaceDir) {
        if (workspaceID == null || workspaceID.getValue() == null || workspaceID.getValue().isBlank()) {
            throw new IllegalArgumentException("workspaceID 不能为空");
        }
        if (applicationID == null || applicationID.getValue() == null || applicationID.getValue().isBlank()) {
            throw new IllegalArgumentException("applicationID 不能为空");
        }
        if (workspaceDir == null || workspaceDir.isBlank()) {
            throw new IllegalArgumentException("workspaceDir 不能为空");
        }

        Path basePath = Paths.get(workspaceDir).toAbsolutePath().normalize();
        Path srcPath = basePath.resolve("source");
        Path artifactPath = basePath.resolve("artifacts");
        Path gitLogPath = basePath.resolve("git-clone.log");
        Path buildLogPath = basePath.resolve("build.log");
        Path appLogPath = basePath.resolve("app.log");

        try {
            // 确保基础目录及子目录存在
            Files.createDirectories(basePath);
            Files.createDirectories(srcPath);
            Files.createDirectories(artifactPath);
            Files.createDirectories(buildLogPath.getParent());

            log.info("Workspace 初始化完成: workspaceDir={}, sourceDir={}, artifactDir={}",
                    basePath, srcPath, artifactPath);
        } catch (IOException e) {
            throw new IllegalStateException("初始化工作空间目录结构失败: " + basePath, e);
        }

        this.workspaceID = workspaceID;
        this.applicationID = applicationID;
        this.workspaceDir = basePath;
        this.sourceDir = srcPath;
        this.artifactDir = artifactPath;
        this.gitCloneLogFile = gitLogPath;
        this.buildLogFile = buildLogPath;
        this.appLogFile = appLogPath;
    }

    public Path addArtifact(Path artifactFile) {
        if (!Files.isRegularFile(artifactFile)) {
            throw new IllegalArgumentException("artifactPath 不是有效文件: " + artifactFile);
        }

        // 目标文件名：沿用原文件名
        Path target = artifactDir.resolve(artifactFile.getFileName());
        try {
            Files.copy(artifactFile, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(
                    "复制 artifact 失败: " + artifactFile + " -> " + target, e);
        }
        return target;
    }

}
