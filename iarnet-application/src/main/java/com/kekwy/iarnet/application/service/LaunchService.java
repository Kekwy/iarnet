package com.kekwy.iarnet.application.service;

import com.kekwy.iarnet.application.model.Workspace;
import com.kekwy.iarnet.model.ID;

public interface LaunchService {


    boolean launchApplication(Workspace workspace, String lang);
}
