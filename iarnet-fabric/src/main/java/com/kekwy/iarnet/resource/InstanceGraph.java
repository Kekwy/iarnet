package com.kekwy.iarnet.resource;

import java.util.List;

public record InstanceGraph(
        List<ActorInstanceRef> actorInstanceRefs
) {
}
