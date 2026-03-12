package com.kekwy.iarnet.resource.service;

import com.kekwy.iarnet.resource.ActorMessageInbox;
import com.kekwy.iarnet.resource.DeploymentCallback;
import com.kekwy.iarnet.resource.DeploymentPlanGraph;

public interface DeploymentService {

    void deploy(DeploymentPlanGraph deploymentPlanGraph,
                ActorMessageInbox inbox,
                DeploymentCallback callback);
}
