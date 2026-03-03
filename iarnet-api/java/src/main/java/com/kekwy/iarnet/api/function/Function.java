package com.kekwy.iarnet.api.function;

import com.kekwy.iarnet.api.Lang;

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
        private final String function;
        private final Type returnType;
        private final String artifactPath;

        public PythonFunction(String codeFile, String function, Type returnType, String artifactPath) {
            this.codeFile = codeFile;
            this.function = function;
            this.returnType = returnType;
            this.artifactPath = artifactPath;
        }

        public String codeFile() {
            return codeFile;
        }

        public String function() {
            return function;
        }

        public Type returnType() {
            return returnType;
        }

        /**
         * 源码目录路径（requirements.txt 须位于同目录下）。
         */
        public String artifactPath() {
            return artifactPath;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (PythonFunction) obj;
            return Objects.equals(this.codeFile, that.codeFile) &&
                    Objects.equals(this.function, that.function) &&
                    Objects.equals(this.returnType, that.returnType) &&
                    Objects.equals(this.artifactPath, that.artifactPath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(codeFile, function, returnType, artifactPath);
        }

        @Override
        public String toString() {
            return "PythonFunction[" +
                    "codeFile=" + codeFile + ", " +
                    "function=" + function + ", " +
                    "returnType=" + returnType + ", " +
                    "artifactPath=" + artifactPath + ']';
        }

    }

    class PythonMapFunction<T, R> extends PythonFunction implements MapFunction<T, R> {

        public PythonMapFunction(String codeFile, String function, Type returnType, String artifactPath) {
            super(codeFile, function, returnType, artifactPath);
        }

        @Override
        public R apply(T value) {
            return null;
        }
    }

    static <T, R> MapFunction<T, R> pythonMap(String codeFile, String function, Class<R> returnType) {
        return new PythonMapFunction<>(codeFile, function, returnType, "");
    }

    static <T, R> MapFunction<T, R> pythonMap(String codeFile, String function, Class<R> returnType, String artifactPath) {
        return new PythonMapFunction<>(codeFile, function, returnType, artifactPath);
    }

    static <T, R> MapFunction<T, R> pythonMap(String file, String function, TypeRef<R> returnType) {
        return new PythonMapFunction<>(file, function, returnType.getType(), "");
    }
}
