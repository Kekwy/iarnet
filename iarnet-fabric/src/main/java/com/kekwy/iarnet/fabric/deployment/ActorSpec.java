package com.kekwy.iarnet.fabric.deployment;

import com.kekwy.iarnet.proto.common.FunctionDescriptor;
import com.kekwy.iarnet.proto.common.ResourceSpec;
import com.kekwy.iarnet.proto.workflow.NodeKind;

public record ActorSpec(
        String actorId,
        FunctionDescriptor function,
        ResourceSpec resourceSpec,
        String artifactUrl,
        int instanceIndex,
        NodeKind nodeKind
) {
}
