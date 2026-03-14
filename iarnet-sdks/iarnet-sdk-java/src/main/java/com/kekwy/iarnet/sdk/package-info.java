/**
 * Iarnet 工作流 SDK 主包。
 * <p>
 * 提供工作流 DSL 的入口与核心类型：
 * <ul>
 *   <li>{@link com.kekwy.iarnet.sdk.Workflow}：DSL 入口，创建 Source、构建图、提交执行</li>
 *   <li>{@link com.kekwy.iarnet.sdk.Flow}：链式 DSL，追加任务、条件、合并与输出</li>
 *   <li>{@link com.kekwy.iarnet.sdk.EndFlow}：末端 flow（已连接 Sink）</li>
 *   <li>{@link com.kekwy.iarnet.sdk.ConditionalFlow}：条件分支下的 flow</li>
 *   <li>{@link com.kekwy.iarnet.sdk.ExecutionConfig}：节点执行配置</li>
 * </ul>
 *
 * @see com.kekwy.iarnet.sdk.Workflow
 * @see com.kekwy.iarnet.sdk.Flow
 * @see com.kekwy.iarnet.sdk.dsl.Outputs
 * @see com.kekwy.iarnet.sdk.dsl.Tasks
 */
package com.kekwy.iarnet.sdk;
