/**
 * 工作流 DSL 的静态工厂包。
 * <p>
 * 提供 Source、Task、Output 的快捷构造入口，与 {@link com.kekwy.iarnet.sdk.Workflow} 配合使用：
 * <ul>
 *   <li>{@link com.kekwy.iarnet.sdk.dsl.Inputs}：输入源工厂，如 {@code Inputs.of(1, 2, 3)}</li>
 *   <li>{@link com.kekwy.iarnet.sdk.dsl.Outputs}：输出 Sink 工厂，如 {@code Outputs.println()}</li>
 *   <li>{@link com.kekwy.iarnet.sdk.dsl.Tasks}：Python/Go 任务工厂，如 {@code Tasks.pythonTask(...)}</li>
 * </ul>
 *
 * @see com.kekwy.iarnet.sdk.dsl.Inputs
 * @see com.kekwy.iarnet.sdk.dsl.Outputs
 * @see com.kekwy.iarnet.sdk.dsl.Tasks
 */
package com.kekwy.iarnet.sdk.dsl;
