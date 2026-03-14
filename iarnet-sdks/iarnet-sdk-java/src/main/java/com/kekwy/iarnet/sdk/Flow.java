package com.kekwy.iarnet.sdk;

import com.kekwy.iarnet.sdk.function.*;
import com.kekwy.iarnet.sdk.type.TypeToken;

/**
 * 工作流中间/末端节点的链式 DSL 接口。
 * <p>
 * 表示从 Source 或某一中间节点出发的数据流，可继续追加任务（{@code then}）、
 * 合并分支（{@code combine}）、条件分支（{@code when}），或连接到 Sink 形成 {@link EndFlow}。
 *
 * @param <T> 当前 flow 携带的数据类型
 */
@SuppressWarnings("UnusedReturnValue")
public interface Flow<T> {

    /**
     * 追加任务节点。
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
     * @param config   副本数、资源等配置
     * @param <R>      任务输出类型
     * @return 新的 Flow
     */
    <R> Flow<R> then(String name, TaskFunction<T, R> function, ExecutionConfig config);

    /**
     * 连接到输出节点（Sink），形成终结的 flow。
     *
     * @param name     节点名
     * @param function 输出函数
     * @return EndFlow，表示该分支已终结
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

    /**
     * 与另一 flow 合并为单一 flow。
     *
     * @param name     合并节点名
     * @param other    另一 flow（须来自同一 workflow）
     * @param function 合并函数
     * @param <U>      另一 flow 的数据类型
     * @param <V>      合并后的输出类型
     * @return 合并后的 Flow
     * @throws com.kekwy.iarnet.sdk.exception.IarnetValidationException 若 other 来自不同 workflow
     */
    <U, V> Flow<V> combine(String name, Flow<U> other, CombineFunction<T, U, V> function);

    /**
     * 与另一 flow 合并，并指定执行配置。
     *
     * @param name     合并节点名
     * @param other    另一 flow
     * @param function 合并函数
     * @param config   执行配置
     * @param <U>      另一 flow 的数据类型
     * @param <V>      合并后的输出类型
     * @return 合并后的 Flow
     */
    <U, V> Flow<V> combine(String name, Flow<U> other, CombineFunction<T, U, V> function, ExecutionConfig config);

    /**
     * 引入条件分支，后续的 {@code then} 仅在条件满足时传递数据。
     *
     * @param condition 条件函数
     * @return ConditionalFlow，可继续链式 {@code then}
     */
    ConditionalFlow<T> when(ConditionFunction<T> condition);

    /**
     * 为当前节点提供输出类型提示，用于类型推断失败时的兜底。
     *
     * @param typeHint 如 {@code new TypeToken<List<Frame>>() {}}
     * @return 本 Flow（支持链式调用）
     */
    Flow<T> returns(TypeToken<T> typeHint);
}
