package com.kekwy.iarnet.api.graph;

/**
 * 工作流图中的节点，对应一条管道上的逻辑单元：Source、Stage、Task、Sink。
 */
public sealed interface Node permits SourceNode, StageNode, TaskNode, SinkNode {

    /**
     * 节点在图中的唯一标识。
     */
    String getId();
}
