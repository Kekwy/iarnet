package com.kekwy.iarnet.application.service;

import com.kekwy.iarnet.model.ID;

public interface LaunchService {


    boolean launchApplication(ID applicationID, String workspaceDir, String lang);
}
