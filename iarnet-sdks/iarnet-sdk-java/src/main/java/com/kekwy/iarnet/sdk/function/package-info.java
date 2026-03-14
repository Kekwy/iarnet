/**
 * 工作流函数类型定义。
 * <p>
 * 本包定义了 DSL 中各节点所对应的函数接口：输入源（{@link com.kekwy.iarnet.sdk.function.InputFunction}）、
 * 任务（{@link com.kekwy.iarnet.sdk.function.TaskFunction}）、输出（{@link com.kekwy.iarnet.sdk.function.OutputFunction}）、
 * 条件分支（{@link com.kekwy.iarnet.sdk.function.ConditionFunction}）、
 * 多路合并（{@link com.kekwy.iarnet.sdk.function.JoinFunction}）。
 * 所有函数均继承 {@link com.kekwy.iarnet.sdk.function.Function}，需实现 {@link java.io.Serializable} 以便序列化下发。
 *
 * @see com.kekwy.iarnet.sdk.function.Function
 * @see com.kekwy.iarnet.sdk.function.InputFunction
 * @see com.kekwy.iarnet.sdk.function.TaskFunction
 * @see com.kekwy.iarnet.sdk.function.OutputFunction
 * @see com.kekwy.iarnet.sdk.function.ConditionFunction
 * @see com.kekwy.iarnet.sdk.function.JoinFunction
 */
package com.kekwy.iarnet.sdk.function;
