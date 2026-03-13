package com.kekwy.iarnet.fabric.deployment;

import com.kekwy.iarnet.fabric.actor.ActorMessage;
import com.kekwy.iarnet.fabric.messaging.MessageInbox;

public interface DeploymentService {

    void deploy(DeploymentPlanGraph deploymentPlanGraph,
                MessageInbox<ActorMessage> inbox,
                DeploymentCallback callback);
}
