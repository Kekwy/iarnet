package com.kekwy.iarnet.application;

import com.kekwy.iarnet.application.service.ApplicationInfoService;
import com.kekwy.iarnet.model.ApplicationInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DefaultApplicationFacade implements ApplicationFacade {

    private ApplicationInfoService applicationInfoService;

    @Autowired
    public void setApplicationInfoService(ApplicationInfoService applicationInfoService) {
        this.applicationInfoService = applicationInfoService;
    }

    @Override
    public List<ApplicationInfo> listApplicationInfo() {
        return applicationInfoService.list();
    }

    @Override
    public ApplicationInfo createApplication(ApplicationInfo input) {
        return applicationInfoService.create(input);
    }

    @Override
    public ApplicationInfo updateApplication(Long id, ApplicationInfo input) {
        return applicationInfoService.update(id, input);
    }

    @Override
    public void deleteApplication(Long id) {
        applicationInfoService.delete(id);
    }

    @Override
    public Map<String, Long> getApplicationStats() {
        return applicationInfoService.getStats();
    }
}
