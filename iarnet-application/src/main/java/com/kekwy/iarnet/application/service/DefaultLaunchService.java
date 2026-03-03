package com.kekwy.iarnet.application.service;

import com.kekwy.iarnet.application.launcher.JavaLauncher;
import com.kekwy.iarnet.application.launcher.Launcher;
import com.kekwy.iarnet.application.launcher.PythonLauncher;
import com.kekwy.iarnet.application.model.ApplicationInfoEntity;
import com.kekwy.iarnet.application.model.Workspace;
import com.kekwy.iarnet.application.repository.ApplicationInfoRepository;
import com.kekwy.iarnet.enums.AppStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class DefaultLaunchService implements LaunchService {

    private static final Logger log = LoggerFactory.getLogger(DefaultLaunchService.class);

    private final Map<String, Launcher> launcherFactoryMap;

    private ApplicationInfoRepository applicationInfoRepository;

    @Autowired
    public DefaultLaunchService(@Value("${grpc.server.port:9090}") int grpcPort) {
        this.launcherFactoryMap = Map.of(
                "java", new JavaLauncher(grpcPort),
                "python", new PythonLauncher()
        );
    }

    @Autowired
    public void setApplicationInfoRepository(ApplicationInfoRepository applicationInfoRepository) {
        this.applicationInfoRepository = applicationInfoRepository;
    }

    @Override
    public boolean launchApplication(Workspace workspace, String lang) {
        String applicationIDStr = workspace.getApplicationID().getValue();
        log.info("Start launching application. id={}, workspaceDir={}, lang={}", applicationIDStr, workspace.getWorkspaceDir().toAbsolutePath(), lang);

        ApplicationInfoEntity entity = applicationInfoRepository.findById(applicationIDStr)
                .orElseThrow(() -> new IllegalArgumentException("应用不存在: " + applicationIDStr));

        try {
            // 标记为部署中并清理历史错误
            entity.setStatus(AppStatus.APP_STATUS_DEPLOYING.getName());
            entity.setLastError(null);
            applicationInfoRepository.save(entity);

            Launcher launcher = launcherFactoryMap.get(lang);
            boolean result = launcher.launch(workspace);

            if (result) {
                log.info("Application launched successfully. id={}", applicationIDStr);
                entity.setStatus(AppStatus.APP_STATUS_RUNNING.getName());
            } else {
                log.warn("Application launch failed. id={}", applicationIDStr);
                entity.setStatus(AppStatus.APP_STATUS_FAILED.getName());
                entity.setLastError("Launcher 返回失败结果");
            }
            applicationInfoRepository.save(entity);
            return result;
        } catch (Exception e) {
            log.error("Application launch exception. id={}", applicationIDStr, e);
            entity.setStatus(AppStatus.APP_STATUS_FAILED.getName());
            entity.setLastError(e.getMessage());
            applicationInfoRepository.save(entity);
            return false;
        }
    }

}
