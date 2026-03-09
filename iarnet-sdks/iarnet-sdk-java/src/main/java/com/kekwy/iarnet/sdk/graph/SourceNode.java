package com.kekwy.iarnet.sdk.graph;

import com.kekwy.iarnet.proto.common.Type;
import com.kekwy.iarnet.proto.workflow.NodeKind;

public abstract class SourceNode extends Node {

    protected SourceNode(String id, Type outputType) {
        super(id, outputType);
    }

    @Override
    public NodeKind getKind() {
        return NodeKind.SOURCE;
    }
}
