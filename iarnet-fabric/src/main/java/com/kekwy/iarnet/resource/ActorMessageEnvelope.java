package com.kekwy.iarnet.resource;

import com.kekwy.iarnet.proto.actor.ActorMessage;

public record ActorMessageEnvelope(
        String applicationId,
        String workflowId,
        String nodeId,
        String actorId,
        ActorMessage message
) {
}
