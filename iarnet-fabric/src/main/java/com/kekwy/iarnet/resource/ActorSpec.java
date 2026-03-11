package com.kekwy.iarnet.resource;


import com.kekwy.iarnet.proto.common.FunctionDescriptor;
import com.kekwy.iarnet.proto.common.ResourceSpec;

public record ActorSpec(
        String actorId,
        FunctionDescriptor function,
        ResourceSpec resourceSpec,
        String artifactUrl
) {
}
