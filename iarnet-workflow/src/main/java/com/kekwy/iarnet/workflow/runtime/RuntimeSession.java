package com.kekwy.iarnet.workflow.runtime;

import com.kekwy.iarnet.proto.actor.ActorMessage;
import com.kekwy.iarnet.resource.port.ActorMessageHandler;
import lombok.RequiredArgsConstructor;


@RequiredArgsConstructor
public class RuntimeSession implements ActorMessageHandler {

    private final RuntimeGraph runtimeGraph;


    // TODO
    public void handleActorMessage(String nodeId, String actorId, ActorMessage message) {

    }

    public void start() {

    }
}
