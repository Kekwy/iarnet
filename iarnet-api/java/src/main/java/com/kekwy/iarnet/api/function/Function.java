package com.kekwy.iarnet.api.function;

import java.lang.reflect.Type;
import java.util.Objects;

public interface Function {

    abstract class PythonFunction {
        private final String codeFile;
        private final String function;
        private final Type returnType;
        private final String requirementsFile;

        public PythonFunction(String codeFile, String function, Type returnType, String requirementsFile) {
            this.codeFile = codeFile;
            this.function = function;
            this.returnType = returnType;
            this.requirementsFile = requirementsFile;
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

        public String requirementsFile() {
            return requirementsFile;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (PythonFunction) obj;
            return Objects.equals(this.codeFile, that.codeFile) &&
                    Objects.equals(this.function, that.function) &&
                    Objects.equals(this.returnType, that.returnType) &&
                    Objects.equals(this.requirementsFile, that.requirementsFile);
        }

        @Override
        public int hashCode() {
            return Objects.hash(codeFile, function, returnType, requirementsFile);
        }

        @Override
        public String toString() {
            return "PythonFunction[" +
                    "codeFile=" + codeFile + ", " +
                    "function=" + function + ", " +
                    "returnType=" + returnType + ", " +
                    "requirementsFile=" + requirementsFile + ']';
        }

    }

    class PythonMapFunction<T, R> extends PythonFunction implements MapFunction<T, R> {

        public PythonMapFunction(String codeFile, String function, Type returnType, String requirementsFile) {
            super(codeFile, function, returnType, requirementsFile);
        }

        @Override
        public R apply(T value) {
            // 真实执行逻辑由后端 Python 实现，此处仅作为占位。
            return null;
        }
    }

    /**
     * 指定非泛型或简单类型的返回值类型，例如 String、Integer 等。
     */
    static <T, R> MapFunction<T, R> pythonMap(String codeFile, String function, Class<R> returnType) {
        return new PythonMapFunction<>(codeFile, function, returnType, "");
    }

    static <T, R> MapFunction<T, R> pythonMap(String codeFile, String function, Class<R> returnType, String requirementsFile) {
        return new PythonMapFunction<>(codeFile, function, returnType, requirementsFile);
    }

    /**
     * 支持 List&lt;String&gt; 等泛型返回类型的工厂方法。
     *
     * <pre>
     *   Function.pythonMap(\"m\", \"f\", new TypeRef&lt;List&lt;String&gt;&gt;() {})
     * </pre>
     */
    static <T, R> MapFunction<T, R> pythonMap(String file, String function, TypeRef<R> returnType) {
        return new PythonMapFunction<>(file, function, returnType.getType(), "");
    }
}

