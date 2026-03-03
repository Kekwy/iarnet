package com.kekwy.iarnet.api.graph;

import com.kekwy.iarnet.api.DataType;

import java.util.ArrayList;
import java.util.List;

public abstract class Node {

    private final String id;

    private DataType outputType;

    private final List<Node> precursors = new ArrayList<>();

    private final List<Node> successors = new ArrayList<>();

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

    public void addPrecursor(Node node) {
        precursors.add(node);
    }

    public void addSuccessor(Node node) {
        successors.add(node);
    }

    public List<Node> getPrecursors() {
        return List.copyOf(precursors);
    }

    public List<Node> getSuccessors() {
        return List.copyOf(successors);
    }

}
