package com.kekwy.iarnet.application;

import com.kekwy.iarnet.model.ApplicationInfo;
import com.kekwy.iarnet.model.ID;

import java.util.List;
import java.util.Map;

public interface ApplicationFacade {

    List<ApplicationInfo> listApplicationInfo();

    ApplicationInfo createApplication(ApplicationInfo input);

    ApplicationInfo updateApplication(ID id, ApplicationInfo input);

    boolean launchApplication(ID id);

    void deleteApplication(ID id);

    Map<String, Long> getApplicationStats();
}
