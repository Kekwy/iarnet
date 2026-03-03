package com.kekwy.iarnet.api.graph;

import com.kekwy.iarnet.api.DataType;

public abstract class Node {

    private final String id;

    private DataType outputType;

    public Node(String id, DataType outputType) {
        this.id = id;
        this.outputType = outputType;
    }

    public String getId() {
        return id;
    }

    public DataType getOutputType() {
        return outputType;
    }

    public void setOutputType(DataType outputType) {
        this.outputType = outputType;
    }

    public abstract NodeKind getKind();

    public abstract <R> R accept(NodeVisitor<R> visitor);

}
