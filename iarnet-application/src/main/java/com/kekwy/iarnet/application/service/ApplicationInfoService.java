package com.kekwy.iarnet.application.service;

import com.kekwy.iarnet.model.ApplicationInfo;

import java.util.List;
import java.util.Map;

public interface ApplicationInfoService {

    List<ApplicationInfo> list();

    ApplicationInfo create(ApplicationInfo input);

    ApplicationInfo update(Long id, ApplicationInfo input);

    void delete(Long id);

    /** 返回 total, running, stopped, undeployed, failed, importing */
    Map<String, Long> getStats();
}
