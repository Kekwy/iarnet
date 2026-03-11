package com.kekwy.iarnet.resource;

import com.kekwy.iarnet.proto.common.FunctionDescriptor;

public record ActorEdge(
        String fromActorId,
        String toActorId,
        FunctionDescriptor functionDescriptor
) {
}
