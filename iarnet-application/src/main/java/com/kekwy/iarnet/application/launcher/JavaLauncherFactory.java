package com.kekwy.iarnet.application.launcher;

public class JavaLauncherFactory implements LauncherFactory {


    @Override
    public Launcher createLauncher() {
        return new JavaLauncher();
    }

}
