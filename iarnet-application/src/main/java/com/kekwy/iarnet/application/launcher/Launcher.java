package com.kekwy.iarnet.application.launcher;

import com.kekwy.iarnet.model.ID;

public interface Launcher {

    String build(ID applicationID, String workspaceDir);

    boolean launch();

}
