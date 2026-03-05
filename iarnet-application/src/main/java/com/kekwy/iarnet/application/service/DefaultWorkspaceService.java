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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

// TODO: 运行额外进程的代码可以提取到工具类
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
    
        // 创建 source 目录
        Path sourcePath = workspacePath.resolve("source");
        try {
            Files.createDirectories(sourcePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // 执行 git clone
        runGitClone(gitUrl, actualBranch, sourcePath);
        Path gitFilePath = sourcePath.resolve(".git");
        // 删除 .git 文件
        try {
            Files.deleteIfExists(gitFilePath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        WorkspaceEntity entity = new WorkspaceEntity();
        entity.setWorkspaceID(IDUtil.genWorkspaceID().getValue());
        entity.setApplicationID(applicationId);
        entity.setWorkspaceDir(workspacePath.toAbsolutePath().toString());
        workspaceRepository.save(entity);

        log.info("工作空间已创建并完成克隆: applicationId={}, dir={}", applicationId, workspacePath.toAbsolutePath());
        return workspacePath.toAbsolutePath().toString();
    }

    /**
     * 为指定应用创建一个「空」工作空间，不执行 git clone。
     * <p>
     * 适用于通过 shell / CLI 直接上传 JAR 的场景：
     * 仅用于在本地创建 workspace 目录与 WorkspaceEntity 记录，
     * 方便后续在该目录下保存 artifact 并运行进程。
     */
    @Override
    @Transactional
    public String createEmptyWorkspace(String applicationId) {
        if (applicationId == null || applicationId.isBlank()) {
            throw new IllegalArgumentException("applicationId 不能为空");
        }

        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建工作空间基础目录: " + baseDir, e);
        }

        Path workspacePath = baseDir.resolve(applicationId);

        // 若已有旧目录，直接清理掉，确保是干净的 workspace
        try {
            if (Files.exists(workspacePath)) {
                FileSystemUtils.deleteRecursively(workspacePath);
            }
            Files.createDirectories(workspacePath);
        } catch (IOException e) {
            throw new IllegalStateException("创建空工作空间目录失败: " + workspacePath, e);
        }

        WorkspaceEntity entity = new WorkspaceEntity();
        entity.setWorkspaceID(IDUtil.genWorkspaceID().getValue());
        entity.setApplicationID(applicationId);
        entity.setWorkspaceDir(workspacePath.toAbsolutePath().toString());
        workspaceRepository.save(entity);

        log.info("空工作空间已创建: applicationId={}, dir={}", applicationId, workspacePath.toAbsolutePath());
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

        // 使用领域模型 Workspace 封装目录结构，并在构造时确保目录存在
        return new Workspace(
                ID.of(entity.getWorkspaceID()),
                ID.of(entity.getApplicationID()),
                entity.getWorkspaceDir()
        );
    }

    private void runGitClone(String gitUrl, String branch, Path sourcePath) {
        // 使用 --progress -v 强制输出详细进度到日志文件（即使不是交互式终端）
        ProcessBuilder pb = new ProcessBuilder(
                "git", "clone",
                "--progress",
                "-v",
                "-b", branch,
                "--single-branch",
                gitUrl,
                sourcePath.toString());

        // 禁用交互式凭证输入，避免进程卡死等待输入
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");
        // 将 stderr 合并到 stdout，统一按日志输出（避免被日志系统误判为 error）
        pb.redirectErrorStream(true);

        log.info("执行 git clone，url={}, branch={}, dir={}", gitUrl, branch, sourcePath);

        // 将 git 输出写入 Workspace 目录下的 git-clone.log 文件
        Path workspacePath = sourcePath.getParent();
        Path logFile = workspacePath.resolve("git-clone.log");
        try {
            Files.createDirectories(workspacePath);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建工作空间目录: " + workspacePath, e);
        }
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new IllegalStateException("启动 git 进程失败", e);
        }

        try {
            // 最长等待 10 分钟，避免无限卡死
            boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.MINUTES);

            if (!finished) {
                process.destroyForcibly();
                try {
                    FileSystemUtils.deleteRecursively(sourcePath);
                } catch (IOException ignored) {
                }
                throw new IllegalStateException("git clone 超时，已强制终止");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                try {
                    FileSystemUtils.deleteRecursively(sourcePath);
                } catch (IOException ignored) {
                }
                throw new IllegalStateException(
                        "git clone 失败，exitCode=" + exitCode + "，详情见日志文件: " + logFile.toAbsolutePath());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("git clone 被中断", e);
        }
    }
}

