package com.kekwy.iarnet.sdk.function;

import com.kekwy.iarnet.proto.common.Lang;

public class PythonTaskFunction<I, O> implements TaskFunction<I, O> {

    private final String functionIdentifier;
    private final String sourcePath;

    public PythonTaskFunction(String functionIdentifier, String sourcePath) {
        this.functionIdentifier = functionIdentifier;
        this.sourcePath = sourcePath;
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
