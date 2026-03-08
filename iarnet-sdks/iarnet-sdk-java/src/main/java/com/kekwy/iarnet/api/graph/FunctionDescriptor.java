package com.kekwy.iarnet.api.graph;

import com.kekwy.iarnet.api.Lang;

/**
 * 语言无关的函数描述，对应 proto FunctionDescriptor。
 */
public class FunctionDescriptor {

    private final Lang lang;
    private final String functionIdentifier;
    private final byte[] serializedFunction;
    private final String artifactPath;

    private FunctionDescriptor(Builder builder) {
        this.lang = builder.lang;
        this.functionIdentifier = builder.functionIdentifier;
        this.serializedFunction = builder.serializedFunction;
        this.artifactPath = builder.artifactPath;
    }

    public Lang getLang() {
        return lang;
    }

    /**
     * Java: 完整类名；Python: "模块:函数名"
     */
    public String getFunctionIdentifier() {
        return functionIdentifier;
    }

    /**
     * 序列化的函数体（可选）。
     * Java: Serializable 字节；Python: pickle 字节；无则为 null。
     */
    public byte[] getSerializedFunction() {
        return serializedFunction;
    }

    /**
     * 函数代码所在的工件路径（可选）。
     * Java: JAR 路径；Python: 源码目录（requirements.txt 须在同目录下）。
     */
    public String getArtifactPath() {
        return artifactPath;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Lang lang;
        private String functionIdentifier;
        private byte[] serializedFunction;
        private String artifactPath = "";

        public Builder lang(Lang lang) {
            this.lang = lang;
            return this;
        }

        public Builder functionIdentifier(String functionIdentifier) {
            this.functionIdentifier = functionIdentifier;
            return this;
        }

        public Builder serializedFunction(byte[] serializedFunction) {
            this.serializedFunction = serializedFunction;
            return this;
        }

        public Builder artifactPath(String artifactPath) {
            this.artifactPath = artifactPath;
            return this;
        }

        public FunctionDescriptor build() {
            return new FunctionDescriptor(this);
        }
    }
}
