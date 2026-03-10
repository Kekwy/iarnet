package com.kekwy.iarnet.sdk.function;

import com.kekwy.iarnet.proto.common.Lang;

import java.lang.reflect.Type;

/**
 * Go 实现的任务函数描述符。
 * <p>
 * 在 DSL 构建阶段仅描述“调用哪个 Go 函数、源码路径、输出类型”，
 * 实际执行由运行时调度到 Go worker。{@link #apply(Object)} 在此处抛出
 * {@link UnsupportedOperationException}，因为本类不是可执行的 Java 逻辑。
 *
 * @param <I> 输入类型（用于类型系统推断）
 * @param <O> 输出类型
 */
public class GoTaskFunction<I, O> implements TaskFunction<I, O> {

    private final String functionIdentifier;
    private final String sourcePath;
    private final Type outputTypeHint;

    /**
     * @param functionIdentifier Go 中可导出的函数名
     * @param sourcePath         源码文件或包路径，空串表示由运行时解析
     */
    public GoTaskFunction(String functionIdentifier, String sourcePath) {
        this(functionIdentifier, sourcePath, null);
    }

    /**
     * @param functionIdentifier Go 中可导出的函数名
     * @param sourcePath         源码文件或包路径
     * @param outputTypeHint     output 类型提示，当反射无法推断时使用
     */
    public GoTaskFunction(String functionIdentifier, String sourcePath, Type outputTypeHint) {
        this.functionIdentifier = functionIdentifier;
        this.sourcePath = sourcePath;
        this.outputTypeHint = outputTypeHint;
    }

    /** Go 中可导出的函数名。 */
    public String getFunctionIdentifier() {
        return functionIdentifier;
    }

    /** 源码文件或包路径。 */
    public String getSourcePath() {
        return sourcePath;
    }

    /** 输出类型提示，可为 null。 */
    public Type getOutputTypeHint() {
        return outputTypeHint;
    }

    @Override
    public O apply(I input) {
        throw new UnsupportedOperationException(
                "GoTaskFunction is a descriptor only; execution happens in Go worker.");
    }

    @Override
    public Lang getLang() {
        return Lang.LANG_GO;
    }
}
