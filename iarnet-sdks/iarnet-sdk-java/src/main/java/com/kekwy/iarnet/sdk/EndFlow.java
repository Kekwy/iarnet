package com.kekwy.iarnet.sdk;

/**
 * 工作流末端（Sink）节点的 Flow 类型。
 * <p>
 * 表示 flow 已通过 {@link Flow#then(String, OutputFunction)} 连接到输出节点，
 * 不再支持继续链式追加任务。用于类型系统区分“可继续扩展的 flow”与“已终结的 flow”。
 *
 * @param <T> 流入 Sink 的元素类型
 */
public interface EndFlow<T> {
}
