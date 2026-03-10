package com.kekwy.iarnet.application.service;

import com.kekwy.iarnet.application.model.Workspace;

public interface LaunchService {


    boolean launchApplication(Workspace workspace, String lang);
}
