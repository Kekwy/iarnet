package com.kekwy.iarnet.application;

import com.kekwy.iarnet.application.model.Workspace;
import com.kekwy.iarnet.application.service.ApplicationInfoService;
import com.kekwy.iarnet.application.service.LaunchService;
import com.kekwy.iarnet.application.service.WorkspaceService;
import com.kekwy.iarnet.common.model.ApplicationInfo;
import com.kekwy.iarnet.common.model.ID;
import com.kekwy.iarnet.common.util.IDUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class DefaultApplicationFacade implements ApplicationFacade {

    private ApplicationInfoService applicationInfoService;
    private WorkspaceService workspaceService;
    private LaunchService launchService;

    @Value("${grpc.server.port:9090}")
    private int grpcPort;

    private static final String INPUT_FILE_NAME = "input.json";

    @Override
    public void launchApplicationWithJar(byte[] content, Map<String, String> inputs) {
        // 1. 为本次 JAR 启动生成应用 ID
        ID appId = IDUtil.genAppID();
        String appIdValue = appId.getValue();
        log.info("接收到直接 JAR 启动请求，生成应用 ID: {}", appIdValue);

        // 2. 为该应用创建一个空的 Workspace（不做 git clone）
        String workspaceDir = workspaceService.createEmptyWorkspace(appIdValue);
        log.info("已为 JAR 应用创建工作空间: {}", workspaceDir);

        // 3. 持久化应用信息，便于后续在 Web 端展示与统计
        ApplicationInfo info = new ApplicationInfo();
        info.setName(appIdValue);
        info.setId(appId);
        info.setLang("java");
        info.setGitUrl("No git url, imported by jar.");
        info.setBranch("");
        applicationInfoService.create(info);

        // 4. 将上传的 JAR 字节写入 Workspace 的 artifacts 目录
        Workspace workspace = workspaceService.getByApplicationID(appId);
        Path artifactDir = workspace.getArtifactDir();
        try {
            Files.createDirectories(artifactDir);
        } catch (IOException e) {
            throw new IllegalStateException("创建 artifact 目录失败: " + artifactDir, e);
        }

        Path jarPath = artifactDir.resolve("app-" + appIdValue + ".jar");
        try (var out = Files.newOutputStream(jarPath)) {
            out.write(content);
        } catch (IOException e) {
            throw new IllegalStateException("写入 JAR 文件失败: " + jarPath, e);
        }
        log.info("已保存 JAR 到 workspace: {}", jarPath.toAbsolutePath());

        // 4b. 若有输入，写入工作空间下的 input.json 并供进程通过 IARNET_INPUT_FILE 读取
        Path inputFile = null;
        if (inputs != null && !inputs.isEmpty()) {
            Path wsDir = workspace.getWorkspaceDir();
            inputFile = wsDir.resolve(INPUT_FILE_NAME);
            String json = toSimpleJson(inputs);
            try {
                Files.writeString(inputFile, json, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("写入输入文件失败: " + inputFile, e);
            }
            log.info("已写入测试输入到: {}", inputFile.toAbsolutePath());
        }

        // 5. 参考 JavaLauncher 中的实现，直接通过 java -jar 启动该 JAR
        Path appLogFile = workspace.getAppLogFile();
        try {
            Files.createDirectories(appLogFile.getParent());
        } catch (IOException e) {
            throw new IllegalStateException("创建应用日志目录失败: " + appLogFile.getParent(), e);
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "java",
                    "-jar",
                    jarPath.toAbsolutePath().toString()
            );
            if (appIdValue != null) {
                pb.environment().put("IARNET_APP_ID", appIdValue);
            }
            pb.environment().put("IARNET_GRPC_PORT", String.valueOf(grpcPort));
            if (inputFile != null) {
                pb.environment().put("IARNET_INPUT_FILE", inputFile.toAbsolutePath().toString());
            }
            pb.directory(workspace.getWorkspaceDir().toFile());
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(appLogFile.toFile()));

            Process appProcess = pb.start();
            log.info("已启动直接 JAR 应用进程: pid={}, jar={}, logFile={}",
                    appProcess.pid(), jarPath.toAbsolutePath(), appLogFile.toAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException("启动 JAR 进程失败: " + jarPath, e);
        }
    }

    private static String toSimpleJson(Map<String, String> map) {
        StringBuilder sb = new StringBuilder().append('{');
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(escapeJson(e.getKey())).append("\":\"")
                    .append(escapeJson(e.getValue())).append('"');
        }
        return sb.append('}').toString();
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') sb.append('\\');
            sb.append(c);
        }
        return sb.toString();
    }

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
    public void createApplicationWithJar(byte[] content) {
        // TODO Auto-generated method stub
        ApplicationInfo input = new ApplicationInfo();
        // input.setName("application-" + id.getValue());
        input.setLang("java");
        input.setGitUrl("No git url, imported by jar.");
        input.setBranch("");
        createApplication(input);
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

