package com.kekwy.iarnet.application;

import com.kekwy.iarnet.model.ApplicationInfo;

import java.util.List;
import java.util.Map;

public interface ApplicationFacade {

    List<ApplicationInfo> listApplicationInfo();

    ApplicationInfo createApplication(ApplicationInfo input);

    ApplicationInfo updateApplication(Long id, ApplicationInfo input);

    void deleteApplication(Long id);

    Map<String, Long> getApplicationStats();
}
