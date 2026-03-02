package com.kekwy.iarnet.application.service;

import com.kekwy.iarnet.model.ApplicationInfo;
import com.kekwy.iarnet.model.ID;

import java.util.List;
import java.util.Map;

public interface ApplicationInfoService {

    List<ApplicationInfo> list();

    ApplicationInfo getByID(ID id);

    ApplicationInfo create(ApplicationInfo input);

    ApplicationInfo update(ID id, ApplicationInfo input);

    void delete(ID id);

    /** 返回 total, running, stopped, undeployed, failed, importing */
    Map<String, Long> getStats();
}
