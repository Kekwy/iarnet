package com.kekwy.iarnet.execution.domain;

import com.kekwy.iarnet.fabric.actor.ActorInstanceRef;

import java.util.List;

public record RuntimeNode(
        String nodeId,
        String nodeName,
        List<ActorInstanceRef> actorInstanceRefs
) {



}
