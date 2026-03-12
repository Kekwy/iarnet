package com.kekwy.iarnet.resource;

import com.kekwy.iarnet.proto.actor.ActorEnvelope;

public record ActorMessage(
        String deploymentId,
        String actorId,
        ActorEnvelope message
) {
}
