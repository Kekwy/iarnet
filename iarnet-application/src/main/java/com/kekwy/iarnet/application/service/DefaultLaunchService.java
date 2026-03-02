package com.kekwy.iarnet.application.service;

import com.kekwy.iarnet.application.launcher.JavaLauncherFactory;
import com.kekwy.iarnet.application.launcher.Launcher;
import com.kekwy.iarnet.application.launcher.LauncherFactory;
import com.kekwy.iarnet.application.launcher.PythonLauncherFactory;
import com.kekwy.iarnet.application.model.Artifact;
import com.kekwy.iarnet.application.model.ApplicationInfoEntity;
import com.kekwy.iarnet.application.repository.ApplicationInfoRepository;
import com.kekwy.iarnet.enums.AppStatus;
import com.kekwy.iarnet.model.ID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class DefaultLaunchService implements LaunchService {

    private static final Logger log = LoggerFactory.getLogger(DefaultLaunchService.class);

    /* language -> luncher factory */
    private final Map<String, LauncherFactory>  launcherFactoryMap =  Map.of(
            "java", new JavaLauncherFactory(),
            "python", new PythonLauncherFactory()
    );

    private ArtifactService artifactService;
    private ApplicationInfoRepository applicationInfoRepository;


    @Autowired
    public void setArtifactService(ArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    @Autowired
    public void setApplicationInfoRepository(ApplicationInfoRepository applicationInfoRepository) {
        this.applicationInfoRepository = applicationInfoRepository;
    }

    @Override
    public boolean launchApplication(ID applicationID, String workspaceDir, String lang) {
        log.info("Start launching application. id={}, workspaceDir={}, lang={}", applicationID, workspaceDir, lang);

        ApplicationInfoEntity entity = applicationInfoRepository.findById(applicationID.getValue())
                .orElseThrow(() -> new IllegalArgumentException("应用不存在: " + applicationID.getValue()));

        try {
            // 标记为部署中并清理历史错误
            entity.setStatus(AppStatus.APP_STATUS_DEPLOYING.getName());
            entity.setLastError(null);
            applicationInfoRepository.save(entity);

            Launcher launcher = launcherFactoryMap.get(lang).createLauncher();
            String artifactPath = launcher.build(applicationID, workspaceDir);
            Artifact artifact = artifactService.create(applicationID, artifactPath);
            log.info("Artifact created. id={}, path={}", artifact.getArtifactID(), artifact.getArtifactPath());

            boolean result = launcher.launch();
            if (result) {
                log.info("Application launched successfully. id={}", applicationID);
                entity.setStatus(AppStatus.APP_STATUS_RUNNING.getName());
            } else {
                log.warn("Application launch failed. id={}", applicationID);
                entity.setStatus(AppStatus.APP_STATUS_FAILED.getName());
                entity.setLastError("Launcher 返回失败结果");
            }
            applicationInfoRepository.save(entity);
            return result;
        } catch (Exception e) {
            log.error("Application launch exception. id={}", applicationID, e);
            entity.setStatus(AppStatus.APP_STATUS_FAILED.getName());
            entity.setLastError(e.getMessage());
            applicationInfoRepository.save(entity);
            return false;
        }
    }

}
