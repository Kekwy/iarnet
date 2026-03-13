package com.kekwy.iarnet.fabric.deployment;

import com.kekwy.iarnet.fabric.messaging.ActorMessageInbox;

public interface DeploymentService {

    void deploy(DeploymentPlanGraph deploymentPlanGraph,
                ActorMessageInbox inbox,
                DeploymentCallback callback);
}
