package com.kekwy.iarnet.resource.port;

import com.kekwy.iarnet.proto.actor.ActorMessage;

@FunctionalInterface
public interface ActorMessageHandler {

    void handleActorMessage(String nodeId, String actorId, ActorMessage message);

}
