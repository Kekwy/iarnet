package com.kekwy.iarnet.sdk.function;

import com.kekwy.iarnet.proto.common.Lang;

import java.lang.reflect.Type;

public class PythonTaskFunction<I, O> implements TaskFunction<I, O> {

    private final String functionIdentifier;
    private final String sourcePath;
    private final Type outputTypeHint;

    public PythonTaskFunction(String functionIdentifier, String sourcePath) {
        this(functionIdentifier, sourcePath, null);
    }

    public PythonTaskFunction(String functionIdentifier, String sourcePath, Type outputTypeHint) {
        this.functionIdentifier = functionIdentifier;
        this.sourcePath = sourcePath;
        this.outputTypeHint = outputTypeHint;
    }

    public Type getOutputTypeHint() {
        return outputTypeHint;
    }

    public String getFunctionIdentifier() {
        return functionIdentifier;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    @Override
    public O apply(I input) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Lang getLang() {
        return Lang.LANG_PYTHON;
    }
}
