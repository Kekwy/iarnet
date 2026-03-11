package com.kekwy.iarnet.resource;

import java.util.List;

public record DeploymentPlanGraph(
        String deploymentId,
        List<ActorSpec> actorSpecs,
        List<ActorEdge> edges
) {
}
