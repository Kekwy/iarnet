package com.kekwy.iarnet.api.graph;

import com.kekwy.iarnet.api.DataType;
import com.kekwy.iarnet.api.Resource;

/**
 * 算子节点，对应 proto OperatorNodeDetail。
 */
public class OperatorNode extends Node {

    public enum OperatorKind {
        MAP,
        FLAT_MAP,
        FILTER
    }

    private final OperatorKind operatorKind;
    private final FunctionDescriptor function;
    private final int replicas;
    private final Resource resource;

    private OperatorNode(Builder builder) {
        super(builder.id, builder.outputType);
        this.operatorKind = builder.operatorKind;
        this.function = builder.function;
        this.replicas = builder.replicas;
        this.resource = builder.resource;
    }

    public OperatorKind getOperatorKind() {
        return operatorKind;
    }

    public FunctionDescriptor getFunction() {
        return function;
    }

    public int getReplicas() {
        return replicas;
    }

    public Resource getResource() {
        return resource;
    }

    @Override
    public NodeKind getKind() {
        return NodeKind.OPERATOR;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private DataType outputType;
        private OperatorKind operatorKind;
        private FunctionDescriptor function;
        private int replicas = 1;
        private Resource resource;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder outputType(DataType outputType) {
            this.outputType = outputType;
            return this;
        }

        public Builder operatorKind(OperatorKind operatorKind) {
            this.operatorKind = operatorKind;
            return this;
        }

        public Builder function(FunctionDescriptor function) {
            this.function = function;
            return this;
        }

        public Builder replicas(int replicas) {
            this.replicas = replicas;
            return this;
        }

        public Builder resource(Resource resource) {
            this.resource = resource;
            return this;
        }

        public OperatorNode build() {
            return new OperatorNode(this);
        }
    }
}
