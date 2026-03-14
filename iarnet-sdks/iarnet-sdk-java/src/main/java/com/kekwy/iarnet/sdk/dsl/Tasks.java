package com.kekwy.iarnet.sdk.dsl;

import com.kekwy.iarnet.sdk.function.GoTaskFunction;
import com.kekwy.iarnet.sdk.function.PythonTaskFunction;
import com.kekwy.iarnet.sdk.function.TaskFunction;
import com.kekwy.iarnet.sdk.type.TypeToken;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 工作流任务节点 DSL 的静态工厂。
 * <p>
 * 提供 Python、Go 等非 Java 语言实现的 {@link TaskFunction} 构造入口。
 * 这些任务在 DSL 构建阶段仅做描述，实际执行由运行时调度到对应语言的 worker。
 * <p>
 * 典型用法（与 {@link com.kekwy.iarnet.sdk.Workflow} 配合）：
 * <pre>{@code
 * // Python 任务：约定源码位于 resource/function/python/
 * w.input("src", new TypeToken<Frame>() {}).then("decode", Tasks.pythonTask("decode_frame")).then("sink", Outputs.println());
 *
 * // Go 任务：约定源码位于 resource/function/go/，带输出类型提示
 * w.input("src", new TypeToken<X>() {}).then("process", Tasks.goTask("Process", new TypeToken<Result>() {})).then("sink", ...);
 * }</pre>
 */
public final class Tasks {

    private static final Logger LOG = Logger.getLogger(Tasks.class.getName());

    private Tasks() {
        // 工具类，禁止实例化
    }

    // ======================== Python Task ========================

    /**
     * 创建 Python 任务。源码约定位于 {@code resource/function/python/}。
     *
     * @param functionIdentifier Python 中可调用的函数标识，如 {@code module.func_name}
     * @param <I>                输入类型
     * @param <O>                输出类型
     * @return 对应的 TaskFunction
     */
    public static <I, O> TaskFunction<I, O> pythonTask(String functionIdentifier) {
        LOG.log(Level.FINE, "Python task created: functionIdentifier={0}", functionIdentifier);
        return new PythonTaskFunction<>(functionIdentifier);
    }

    /**
     * 创建带输出类型提示的 Python 任务，无需在 flow 上额外调用 {@code .returns(TypeToken)}。
     * 源码约定位于 {@code resource/function/python/}。
     *
     * @param functionIdentifier Python 中可调用的函数标识
     * @param outputType         输出类型的 TypeToken，如 {@code new TypeToken<List<Frame>>() {}}
     * @param <I>                输入类型
     * @param <O>                输出类型
     * @return 对应的 TaskFunction
     */
    public static <I, O> TaskFunction<I, O> pythonTask(String functionIdentifier, TypeToken<O> outputType) {
        LOG.log(Level.FINE, "Python task created: functionIdentifier={0}, outputType={1}",
                new Object[]{functionIdentifier, outputType.getType()});
        return new PythonTaskFunction<>(functionIdentifier, outputType.getType());
    }

    // ======================== Go Task ========================

    /**
     * 创建 Go 任务。源码约定位于 {@code resource/function/go/}。
     *
     * @param functionIdentifier Go 中可导出的函数名
     * @param <I>                输入类型
     * @param <O>                输出类型
     * @return 对应的 TaskFunction
     */
    public static <I, O> TaskFunction<I, O> goTask(String functionIdentifier) {
        LOG.log(Level.FINE, "Go task created: functionIdentifier={0}", functionIdentifier);
        return new GoTaskFunction<>(functionIdentifier);
    }

    /**
     * 创建带输出类型提示的 Go 任务，无需在 flow 上额外调用 {@code .returns(TypeToken)}。
     * 源码约定位于 {@code resource/function/go/}。
     *
     * @param functionIdentifier Go 中可导出的函数名
     * @param outputType         输出类型的 TypeToken
     * @param <I>                输入类型
     * @param <O>                输出类型
     * @return 对应的 TaskFunction
     */
    public static <I, O> TaskFunction<I, O> goTask(String functionIdentifier, TypeToken<O> outputType) {
        LOG.log(Level.FINE, "Go task created: functionIdentifier={0}, outputType={1}",
                new Object[]{functionIdentifier, outputType.getType()});
        return new GoTaskFunction<>(functionIdentifier, outputType.getType());
    }
}
