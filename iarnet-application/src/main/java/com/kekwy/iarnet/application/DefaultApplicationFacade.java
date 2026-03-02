package com.kekwy.iarnet.application;

import com.kekwy.iarnet.application.service.ApplicationInfoService;
import com.kekwy.iarnet.application.service.WorkspaceService;
import com.kekwy.iarnet.model.ApplicationInfo;
import com.kekwy.iarnet.model.ID;
import com.kekwy.iarnet.util.IDUtil;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class DefaultApplicationFacade implements ApplicationFacade {

    private ApplicationInfoService applicationInfoService;
    private WorkspaceService workspaceService;

    @Autowired
    public void setApplicationInfoService(ApplicationInfoService applicationInfoService) {
        this.applicationInfoService = applicationInfoService;
    }

    @Autowired
    public void setWorkspaceService(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
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
    public void deleteApplication(ID id) {
        applicationInfoService.delete(id);
        // 后续可在此处增加删除 workspace 的逻辑
    }

    @Override
    public Map<String, Long> getApplicationStats() {
        return applicationInfoService.getStats();
    }
}

