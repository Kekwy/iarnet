package com.kekwy.iarnet.api.graph;

import com.kekwy.iarnet.api.DataType;
import com.kekwy.iarnet.api.Lang;
import com.kekwy.iarnet.api.Resource;

public class OperatorNode extends Node {

    public enum OperatorKind {
        MAP,
        FLAT_MAP,
        FILTER
    }

    private final OperatorKind operatorKind;

    private final Lang lang;

    /**
     * Java 函数：序列化后的字节（后端通过反序列化还原 lambda / 具名类实例）。
     * Python 函数：为 null，由 operatorIdentifier 定位。
     */
    private final byte[] serializedFunction;

    /**
     * 函数定位标识。
     * <ul>
     *   <li>Java 具名类：全限定类名</li>
     *   <li>Java lambda：宿主类名（辅助信息，主要靠 serializedFunction）</li>
     *   <li>Python：{@code codeFile:functionName}</li>
     * </ul>
     */
    private final String operatorIdentifier;

    private final int replicas;

    private final Resource resource;

    /**
     * Python 函数的源码目录 / requirements 文件等附加信息。
     */
    private final String sourceDir;

    public OperatorNode(Builder builder) {
        super(builder.id, builder.outputType);
        this.operatorKind = builder.operatorKind;
        this.lang = builder.lang;
        this.serializedFunction = builder.serializedFunction;
        this.operatorIdentifier = builder.operatorIdentifier;
        this.replicas = builder.replicas;
        this.resource = builder.resource;
        this.sourceDir = builder.sourceDir;
    }

    public OperatorKind getOperatorKind() {
        return operatorKind;
    }

    public Lang getLang() {
        return lang;
    }

    public byte[] getSerializedFunction() {
        return serializedFunction;
    }

    public String getOperatorIdentifier() {
        return operatorIdentifier;
    }

    public int getReplicas() {
        return replicas;
    }

    public Resource getResource() {
        return resource;
    }

    public String getSourceDir() {
        return sourceDir;
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
        private Lang lang;
        private byte[] serializedFunction;
        private String operatorIdentifier;
        private int replicas = 1;
        private Resource resource;
        private String sourceDir = "";

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

        public Builder lang(Lang lang) {
            this.lang = lang;
            return this;
        }

        public Builder serializedFunction(byte[] serializedFunction) {
            this.serializedFunction = serializedFunction;
            return this;
        }

        public Builder operatorIdentifier(String operatorIdentifier) {
            this.operatorIdentifier = operatorIdentifier;
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

        public Builder sourceDir(String sourceDir) {
            this.sourceDir = sourceDir;
            return this;
        }

        public OperatorNode build() {
            return new OperatorNode(this);
        }
    }
}
