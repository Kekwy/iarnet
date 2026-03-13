package com.kekwy.iarnet.fabric.deployment;

import com.kekwy.iarnet.proto.common.FunctionDescriptor;

public record ActorEdge(
        String fromActorId,
        String toActorId,
        FunctionDescriptor functionDescriptor
) {
}
