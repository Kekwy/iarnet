package com.kekwy.iarnet.workflow;

import com.kekwy.iarnet.resource.ActorInstanceRef;

import java.util.List;

public record RuntimeNode(
        String nodeId,
        String nodeName,
        List<ActorInstanceRef> actorInstanceRefs
) {



}
