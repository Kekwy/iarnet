package com.kekwy.iarnet.api.graph;

import com.kekwy.iarnet.api.DataType;

/**
 * 数据汇节点，对应 proto 中的 Sink（如 PrintSink）。
 */
public class SinkNode extends Node {

    public enum SinkKind {
        PRINT
    }

    private final SinkKind sinkKind;

    public SinkNode(String id, DataType inputType, SinkKind sinkKind) {
        super(id, inputType);
        this.sinkKind = sinkKind;
    }

    public SinkKind getSinkKind() {
        return sinkKind;
    }

    @Override
    public NodeKind getKind() {
        return NodeKind.SINK;
    }

    @Override
    public <R> R accept(NodeVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
