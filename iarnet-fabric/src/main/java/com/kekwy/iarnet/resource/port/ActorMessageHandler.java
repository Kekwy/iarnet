package com.kekwy.iarnet.resource.port;

import com.kekwy.iarnet.proto.actor.ActorEnvelope;

@FunctionalInterface
public interface ActorMessageHandler {

    void handleActorMessage(String nodeId, String actorId, ActorEnvelope message);

}
