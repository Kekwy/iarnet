package com.kekwy.iarnet.api.graph;

/**
 * 工作流图中的有向边，表示数据流从 fromNodeId 指向 toNodeId。
 */
public record Edge(String fromNodeId, String toNodeId) {

    public static Edge of(String fromNodeId, String toNodeId) {
        return new Edge(fromNodeId, toNodeId);
    }

}
