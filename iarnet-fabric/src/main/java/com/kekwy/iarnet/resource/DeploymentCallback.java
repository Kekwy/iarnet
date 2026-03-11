package com.kekwy.iarnet.resource;

public interface DeploymentCallback {

    void onSuccess(InstanceRefGraph instanceRefGraph);

    void onFailure(Exception e); // TODO

}
