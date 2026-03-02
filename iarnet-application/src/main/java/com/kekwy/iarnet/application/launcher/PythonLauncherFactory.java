package com.kekwy.iarnet.application.launcher;

public class PythonLauncherFactory implements LauncherFactory {
    @Override
    public Launcher createLauncher() {
        return new PythonLauncher();
    }
}
