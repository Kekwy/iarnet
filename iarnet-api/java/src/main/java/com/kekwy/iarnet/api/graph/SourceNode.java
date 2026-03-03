package com.kekwy.iarnet.api.graph;

import com.kekwy.iarnet.api.DataType;

public abstract class SourceNode extends Node{

    protected SourceNode(String id, DataType outputType) {
        super(id, outputType);
    }

    @Override
    public NodeKind getKind() {
        return NodeKind.SOURCE;
    }

}
