package com.kekwy.iarnet.sdk.graph;

import com.kekwy.iarnet.proto.common.FunctionDescriptor;
import com.kekwy.iarnet.proto.common.Type;
import com.kekwy.iarnet.proto.workflow.NodeConfig;
import com.kekwy.iarnet.proto.workflow.NodeKind;
import com.kekwy.iarnet.sdk.Resource;


public abstract class Node {

    private final String id;
    private FunctionDescriptor function;
    private NodeConfig nodeConfig;



    protected Node(String id, Type outputType) {
        this.id = id;
        this.outputType = outputType;
    }

    protected Node(String id, Type inputType, Type outputType,
                   FunctionDescriptor function, int replicas, Resource resource) {
        this.id = id;
        this.inputType = inputType;
        this.outputType = outputType;
        this.function = function;
        this.replicas = replicas;
        this.resource = resource;
    }

    public String getId() {
        return id;
    }

    public Type getInputType() {
        return inputType;
    }

    public void setInputType(Type inputType) {
        this.inputType = inputType;
    }

    public Type getOutputType() {
        return outputType;
    }

    public void setOutputType(Type outputType) {
        this.outputType = outputType;
    }

    public FunctionDescriptor getFunction() {
        return function;
    }

    public void setFunction(FunctionDescriptor function) {
        this.function = function;
    }

    public int getReplicas() {
        return replicas;
    }

    public void setReplicas(int replicas) {
        this.replicas = replicas;
    }

    public Resource getResource() {
        return resource;
    }

    public void setResource(Resource resource) {
        this.resource = resource;
    }

    public abstract NodeKind getKind();

    public abstract <R> R accept(NodeVisitor<R> visitor);
}
