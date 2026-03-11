package com.kekwy.iarnet.resource;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class DeploymentGraph {

    private final String applicationId;
    private final String workflowId;
    private final List<NodeDeployment> deployments;

    /**
     * @return 所有已部署的 Actor 实例总数
     */
    public int getTotalActorCount() {
        return deployments.stream()
                .mapToInt(d -> d.getInstances().size())
                .sum();
    }

}
