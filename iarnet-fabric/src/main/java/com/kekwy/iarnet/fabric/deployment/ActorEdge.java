package com.kekwy.iarnet.fabric.deployment;

import com.kekwy.iarnet.proto.common.FunctionDescriptor;
import com.kekwy.iarnet.proto.provider.RoutingStrategy;

public record ActorEdge(
        String fromActorId,
        String toActorId,
        FunctionDescriptor functionDescriptor,
        int outputPort,
        RoutingStrategy routingStrategy
) {
}
