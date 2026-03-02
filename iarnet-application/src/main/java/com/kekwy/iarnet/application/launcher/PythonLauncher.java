package com.kekwy.iarnet.application.launcher;

import com.kekwy.iarnet.model.ID;

public class PythonLauncher implements Launcher {


    @Override
    public String build(ID applicationID, String workspaceDir) {
        return "";
    }

    @Override
    public boolean launch() {
        return false;
    }
}
