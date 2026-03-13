package com.kekwy.iarnet.fabric.actor;

import com.kekwy.iarnet.proto.actor.ActorEnvelope;

public record ActorMessage(
        String deploymentId,
        String actorId,
        ActorEnvelope message
) {
}
