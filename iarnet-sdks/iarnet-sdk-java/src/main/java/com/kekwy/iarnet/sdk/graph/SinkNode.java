package com.kekwy.iarnet.sdk.graph;

import com.kekwy.iarnet.proto.common.Type;
import com.kekwy.iarnet.proto.workflow.NodeKind;
import com.kekwy.iarnet.proto.workflow.SinkKind;

/**
 * 数据汇节点，对应 proto 中的 {@code SinkNodeDetail}。
 */
public class SinkNode extends Node {

    private final SinkKind sinkKind;

    public SinkNode(String id, Type inputType, SinkKind sinkKind) {
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
