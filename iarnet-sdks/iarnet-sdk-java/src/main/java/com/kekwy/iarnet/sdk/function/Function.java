package com.kekwy.iarnet.sdk.function;

import com.kekwy.iarnet.proto.common.Lang;
import com.kekwy.iarnet.sdk.util.TypeToken;

import java.lang.reflect.Type;
import java.util.Objects;

public interface Function extends java.io.Serializable {

    Lang getLang();

    abstract class PythonFunction implements Function {

        @Override
        public Lang getLang() {
            return Lang.LANG_PYTHON;
        }

        private final String codeFile;
        private final String functionName;
        private final Type returnType;
        private final String sourcePath;

        public PythonFunction(String codeFile, String functionName, Type returnType, String sourcePath) {
            this.codeFile = codeFile;
            this.functionName = functionName;
            this.returnType = returnType;
            this.sourcePath = sourcePath;
        }

        public String getCodeFile() {
            return codeFile;
        }

        public String getFunctionName() {
            return functionName;
        }

        public Type getReturnType() {
            return returnType;
        }

        public String getSourcePath() {
            return sourcePath;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (PythonFunction) obj;
            return Objects.equals(this.codeFile, that.codeFile) &&
                    Objects.equals(this.functionName, that.functionName) &&
                    Objects.equals(this.returnType, that.returnType) &&
                    Objects.equals(this.sourcePath, that.sourcePath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(codeFile, functionName, returnType, sourcePath);
        }

        @Override
        public String toString() {
            return "PythonFunction[" +
                    "codeFile=" + codeFile + ", " +
                    "functionName=" + functionName + ", " +
                    "returnType=" + returnType + ", " +
                    "sourcePath=" + sourcePath + ']';
        }

    }

    class PythonMapFunction<T, R> extends PythonFunction implements MapFunction<T, R> {

        public PythonMapFunction(String codeFile, String functionName, Type returnType, String sourcePath) {
            super(codeFile, functionName, returnType, sourcePath);
        }

        @Override
        public R apply(T value) {
            return null;
        }
    }

    static <T, R> MapFunction<T, R> pythonMap(String codeFile, String functionName, Class<R> returnType) {
        return new PythonMapFunction<>(codeFile, functionName, returnType, "");
    }

    static <T, R> MapFunction<T, R> pythonMap(String codeFile, String functionName, Class<R> returnType, String sourcePath) {
        return new PythonMapFunction<>(codeFile, functionName, returnType, sourcePath);
    }

    static <T, R> MapFunction<T, R> pythonMap(String file, String functionName, TypeToken<R> returnType) {
        return new PythonMapFunction<>(file, functionName, returnType.getType(), "");
    }
}
