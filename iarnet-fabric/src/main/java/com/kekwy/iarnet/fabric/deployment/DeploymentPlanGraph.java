package com.kekwy.iarnet.fabric.deployment;

import java.util.List;

public record DeploymentPlanGraph(
        String deploymentId,
        List<ActorSpec> actorSpecs,
        List<ActorEdge> edges
) {
}
