package com.kekwy.iarnet.application.service;

import com.kekwy.iarnet.application.model.Workspace;
import com.kekwy.iarnet.application.model.WorkspaceEntity;
import com.kekwy.iarnet.application.repository.WorkspaceRepository;
import com.kekwy.iarnet.model.ID;
import com.kekwy.iarnet.util.IDUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Service
public class DefaultWorkspaceService implements WorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(DefaultWorkspaceService.class);

    private final WorkspaceRepository workspaceRepository;
    private final Path baseDir;

    public DefaultWorkspaceService(
            WorkspaceRepository workspaceRepository,
            @Value("${iarnet.workspace-base-dir:./workspaces}") String baseDir) {
        this.workspaceRepository = workspaceRepository;
        this.baseDir = Paths.get(baseDir);
    }

    /**
     * 为指定应用创建工作空间目录并克隆 Git 仓库。
     *
     * @param applicationId 应用 ID（如 AppID.xxxx）
     * @param gitUrl        仓库地址
     * @param branch        分支，null 或空串时使用 main
     * @return 工作空间目录的绝对路径
     */
    @Override
    @Transactional
    public String createWorkspaceAndClone(String applicationId, String gitUrl, String branch) {
        if (applicationId == null || applicationId.isBlank()) {
            throw new IllegalArgumentException("applicationId 不能为空");
        }
        if (gitUrl == null || gitUrl.isBlank()) {
            throw new IllegalArgumentException("gitUrl 不能为空");
        }
        String actualBranch = (branch == null || branch.isBlank()) ? "main" : branch;

        try {
            // 确保基础目录存在
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建工作空间基础目录: " + baseDir, e);
        }

        Path workspacePath = baseDir.resolve(applicationId);

        // 如果已经存在旧目录，先尝试清理
        try {
            if (Files.exists(workspacePath)) {
                FileSystemUtils.deleteRecursively(workspacePath);
            }
        } catch (IOException e) {
            throw new IllegalStateException("无法清理已有工作空间目录: " + workspacePath, e);
        }

        // 执行 git clone
        runGitClone(gitUrl, actualBranch, workspacePath);

        WorkspaceEntity entity = new WorkspaceEntity();
        entity.setWorkspaceID(IDUtil.genWorkspaceID().getValue());
        entity.setApplicationID(applicationId);
        entity.setWorkspaceDir(workspacePath.toAbsolutePath().toString());
        workspaceRepository.save(entity);

        log.info("工作空间已创建并完成克隆: applicationId={}, dir={}", applicationId, workspacePath.toAbsolutePath());
        return workspacePath.toAbsolutePath().toString();
    }

    @Override
    public Workspace getByApplicationID(ID applicationID) {
        if (applicationID == null || applicationID.getValue() == null) {
            throw new IllegalArgumentException("applicationID 不能为空");
        }

        Optional<WorkspaceEntity> workspaceEntity =
                workspaceRepository.findByApplicationID(applicationID.getValue());

        WorkspaceEntity entity = workspaceEntity.orElseThrow(
                () -> new IllegalArgumentException("未找到应用对应的工作空间，applicationId=" + applicationID.getValue()));

        Workspace workspace = new Workspace();
        workspace.setWorkspaceID(ID.of(entity.getWorkspaceID()));
        workspace.setApplicationID(ID.of(entity.getApplicationID()));
        workspace.setWorkspaceDir(entity.getWorkspaceDir());
        return workspace;
    }

    private void runGitClone(String gitUrl, String branch, Path workspacePath) {
        ProcessBuilder pb = new ProcessBuilder(
                "git", "clone", "-b", branch, "--single-branch", gitUrl, workspacePath.toString());
        pb.redirectErrorStream(true);

        log.info("执行 git clone，url={}, branch={}, dir={}", gitUrl, branch, workspacePath);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new IllegalStateException("启动 git 进程失败", e);
        }

        String output;
        try (InputStream is = process.getInputStream()) {
            output = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            output = "";
        }

        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                // 克隆失败时清理目录
                try {
                    FileSystemUtils.deleteRecursively(workspacePath);
                } catch (IOException ignored) {
                    // 忽略清理失败
                }
                throw new IllegalStateException(
                        "git clone 失败，exitCode=" + exitCode + ", output=\n" + output);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("git clone 被中断", e);
        }
    }
}

