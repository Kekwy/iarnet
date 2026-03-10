package com.kekwy.iarnet.sdk;

import com.kekwy.iarnet.sdk.function.OutputFunction;
import com.kekwy.iarnet.sdk.function.TaskFunction;

/**
 * 条件分支下的 Flow DSL。
 * <p>
 * 由 {@link Flow#when(ConditionFunction)} 返回，表示数据流在满足条件时才会传递到后续节点。
 * 后续 {@code then} 的行为与普通 {@link Flow} 一致，可追加任务或连接到 Sink。
 *
 * @param <T> 当前 flow 携带的数据类型（条件函数的输入类型）
 */
@SuppressWarnings("UnusedReturnValue")
public interface ConditionalFlow<T> {

    /**
     * 追加任务节点（仅在条件满足时执行）。
     *
     * @param name     节点名
     * @param function 任务函数
     * @param <R>      任务输出类型
     * @return 新的 Flow
     */
    <R> Flow<R> then(String name, TaskFunction<T, R> function);

    /**
     * 追加任务节点，并指定执行配置。
     *
     * @param name     节点名
     * @param function 任务函数
     * @param config   执行配置
     * @param <R>      任务输出类型
     * @return 新的 Flow
     */
    <R> Flow<R> then(String name, TaskFunction<T, R> function, ExecutionConfig config);

    /**
     * 连接到输出节点（Sink）。
     *
     * @param name     节点名
     * @param function 输出函数
     * @return EndFlow
     */
    EndFlow<T> then(String name, OutputFunction<T> function);

    /**
     * 连接到输出节点，并指定执行配置。
     *
     * @param name     节点名
     * @param function 输出函数
     * @param config   执行配置
     * @return EndFlow
     */
    EndFlow<T> then(String name, OutputFunction<T> function, ExecutionConfig config);
}
