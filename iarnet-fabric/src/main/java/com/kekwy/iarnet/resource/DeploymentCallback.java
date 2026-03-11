package com.kekwy.iarnet.resource;

public interface DeploymentCallback {

    void onSuccess(InstanceGraph instanceGraph);

    void onFailure(Exception e); // TODO

}
