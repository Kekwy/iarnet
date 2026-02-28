package com.kekwy.iarnet.api.graph;

import java.util.List;

/**
 * 工作流图：由节点与有向边组成的 DAG，表示一次工作流执行中的多条管道及其数据流。
 * <p>
 * 与 proto 对应关系：一条 Pipeline 在图中对应一条链 Source → Stage* → Task? → Sink，
 * 多条 Pipeline 可并列存在于同一图中（通过不同的节点与边表示）。
 */
public class Graph {

    private final String graphId;
    private final String workflowId;
    private final List<Node> nodes;
    private final List<Edge> edges;

    public Graph(String graphId, String workflowId, List<Node> nodes, List<Edge> edges) {
        this.graphId = graphId;
        this.workflowId = workflowId;
        this.nodes = nodes != null ? List.copyOf(nodes) : List.of();
        this.edges = edges != null ? List.copyOf(edges) : List.of();
    }

    public String getGraphId() {
        return graphId;
    }

    public String getWorkflowId() {
        return workflowId;
    }

    public List<Node> getNodes() {
        return nodes;
    }

    public List<Edge> getEdges() {
        return edges;
    }
}
