package com.kekwy.iarnet.resource;

import java.util.List;

public record InstanceRefGraph(
        String deploymentId,
        List<ActorInstanceRef> actorInstanceRefs
) {
}
