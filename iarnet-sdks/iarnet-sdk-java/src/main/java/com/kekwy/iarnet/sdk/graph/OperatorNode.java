package com.kekwy.iarnet.sdk.graph;

import com.kekwy.iarnet.proto.common.FunctionDescriptor;
import com.kekwy.iarnet.proto.common.Type;
import com.kekwy.iarnet.proto.workflow.NodeKind;
import com.kekwy.iarnet.proto.workflow.OperatorKind;
import com.kekwy.iarnet.sdk.Resource;

/**
 * 算子节点，对应 proto {@code OperatorNodeDetail}。
 * <p>
 * {@code function}、{@code replicas}、{@code resource} 已上移到 {@link Node}，
 * 本类仅保留算子特有的 {@link OperatorKind}。
 */
public class OperatorNode extends Node {

    private final OperatorKind operatorKind;
    private final FunctionDescriptor keySelector;

    private OperatorNode(Builder builder) {
        super(builder.id, builder.inputType, builder.outputType,
              builder.function, builder.replicas, builder.resource);
        this.operatorKind = builder.operatorKind;
        this.keySelector = builder.keySelector;
    }

    public OperatorKind getOperatorKind() {
        return operatorKind;
    }

    /** KEY_BY 专用：key 提取函数描述符，其他算子类型返回 {@code null} */
    public FunctionDescriptor getKeySelector() {
        return keySelector;
    }

    @Override
    public NodeKind getKind() {
        return NodeKind.OPERATOR;
    }

    @Override
    public <R> R accept(NodeVisitor<R> visitor) {
        return visitor.visit(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private Type inputType;
        private Type outputType;
        private OperatorKind operatorKind;
        private FunctionDescriptor function;
        private FunctionDescriptor keySelector;
        private int replicas = 1;
        private Resource resource;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder inputType(Type inputType) {
            this.inputType = inputType;
            return this;
        }

        public Builder outputType(Type outputType) {
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

        public Builder keySelector(FunctionDescriptor keySelector) {
            this.keySelector = keySelector;
            return this;
        }

        public OperatorNode build() {
            return new OperatorNode(this);
        }
    }
}
