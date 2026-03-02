package com.kekwy.iarnet.application;

import com.kekwy.iarnet.application.model.Workspace;
import com.kekwy.iarnet.application.service.ApplicationInfoService;
import com.kekwy.iarnet.application.service.LaunchService;
import com.kekwy.iarnet.application.service.WorkspaceService;
import com.kekwy.iarnet.model.ApplicationInfo;
import com.kekwy.iarnet.model.ID;
import com.kekwy.iarnet.util.IDUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class DefaultApplicationFacade implements ApplicationFacade {

    private ApplicationInfoService applicationInfoService;
    private WorkspaceService workspaceService;
    private LaunchService launchService;

    @Autowired
    public void setApplicationInfoService(ApplicationInfoService applicationInfoService) {
        this.applicationInfoService = applicationInfoService;
    }

    @Autowired
    public void setWorkspaceService(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @Autowired
    public void setLaunchService(LaunchService launchService) {
        this.launchService = launchService;
    }

    @Override
    public List<ApplicationInfo> listApplicationInfo() {
        return applicationInfoService.list();
    }

    @Override
    public ApplicationInfo createApplication(ApplicationInfo input) {
        // 先为应用生成 ID，确保 workspace 与应用使用同一个 ID
        ID id = IDUtil.genAppID();
        input.setId(id);

        String appId = input.getId().getValue();

        // 先创建工作空间并克隆仓库，失败则直接抛错，终止应用创建
        log.info("为应用创建工作空间并克隆仓库: appId={}, gitUrl={}, branch={}", appId, input.getGitUrl(), input.getBranch());
        String workspaceDir = workspaceService.createWorkspaceAndClone(appId, input.getGitUrl(), input.getBranch());
        log.info("应用仓库克隆成功，工作目录：{}", workspaceDir);
        // 工作空间准备完成后，再持久化应用信息
        return applicationInfoService.create(input);
    }

    @Override
    public ApplicationInfo updateApplication(ID id, ApplicationInfo input) {
        return applicationInfoService.update(id, input);
    }

    @Override
    public boolean launchApplication(ID id) {
        if (id == null || id.getValue() == null) {
            throw new IllegalArgumentException("应用 ID 不能为空");
        }

        // 读取应用信息
        ApplicationInfo applicationInfo = applicationInfoService.getByID(id);
        if (applicationInfo == null) {
            throw new IllegalArgumentException("应用不存在: " + id.getValue());
        }

        // 找到对应的工作空间
        Workspace workspace = workspaceService.getByApplicationID(id);

        String lang = applicationInfo.getLang();
        if (lang == null || lang.isBlank()) {
            throw new IllegalStateException("应用未配置运行语言(lang)，无法启动: id=" + id.getValue());
        }

        log.info("准备启动应用: id={}, name={}, lang={}, workspaceDir={}",
                id.getValue(), applicationInfo.getName(), lang, workspace.getWorkspaceDir().toAbsolutePath());

        return launchService.launchApplication(workspace, lang);
    }

    @Override
    public void deleteApplication(ID id) {
        applicationInfoService.delete(id);
        // 后续可在此处增加删除 workspace 的逻辑
    }

    @Override
    public Map<String, Long> getApplicationStats() {
        return applicationInfoService.getStats();
    }

    @Override
    public Optional<String> getBuildLog(ID id) {
        if (id == null || id.getValue() == null || id.getValue().isBlank()) {
            throw new IllegalArgumentException("应用 ID 不能为空");
        }

        // 通过 WorkspaceService 获取应用工作空间目录
        Workspace workspace = workspaceService.getByApplicationID(id);
        if (workspace == null || workspace.getWorkspaceDir() == null) {
            throw new IllegalStateException("应用工作空间目录无效");
        }

        Path logFile = workspace.getBuildLogFile();
        if (!Files.exists(logFile)) {
            log.info("应用 {} 尚未生成构建日志文件: {}", id.getValue(), logFile.toAbsolutePath());
            return Optional.empty();
        }

        try {
            String content = Files.readString(logFile, StandardCharsets.UTF_8);
            return Optional.of(content);
        } catch (IOException e) {
            log.error("读取应用 {} 构建日志失败: {}", id.getValue(), e.getMessage(), e);
            throw new IllegalStateException("读取构建日志失败", e);
        }
    }
}

