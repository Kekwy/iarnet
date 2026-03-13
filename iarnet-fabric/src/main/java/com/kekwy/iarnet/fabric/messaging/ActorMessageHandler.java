package com.kekwy.iarnet.fabric.messaging;

import com.kekwy.iarnet.proto.actor.ActorEnvelope;

@FunctionalInterface
public interface ActorMessageHandler {

    void handleActorMessage(String nodeId, String actorId, ActorEnvelope message);

}
