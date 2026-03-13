package com.kekwy.iarnet.fabric.deployment;

import com.kekwy.iarnet.fabric.actor.ActorInstanceRef;

import java.util.List;

public record InstanceRefGraph(
        String deploymentId,
        List<ActorInstanceRef> actorInstanceRefs
) {
}
