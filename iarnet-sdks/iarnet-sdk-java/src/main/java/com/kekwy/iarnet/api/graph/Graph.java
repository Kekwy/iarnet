package com.kekwy.iarnet.api.graph;

import java.util.List;

/**
 * 工作流图：由节点与有向边组成的 DAG。
 */
public class Graph {

    private final String workflowId;
    private final String applicationId;
    private final List<Node> nodes;
    private final List<Edge> edges;

    public Graph(String workflowId, String applicationId, List<Node> nodes, List<Edge> edges) {
        this.workflowId = workflowId;
        this.applicationId = applicationId;
        this.nodes = nodes != null ? List.copyOf(nodes) : List.of();
        this.edges = edges != null ? List.copyOf(edges) : List.of();
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public List<Edge> getEdges() {
        return edges;
    }
}
