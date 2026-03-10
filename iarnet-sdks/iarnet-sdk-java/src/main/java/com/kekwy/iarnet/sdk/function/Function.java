package com.kekwy.iarnet.sdk.function;

import com.kekwy.iarnet.proto.common.Lang;

import java.io.Serializable;

/**
 * 工作流函数根接口。
 * <p>
 * 所有 DSL 中的节点函数（输入、任务、输出、条件、合并等）均继承本接口。
 * 需实现 {@link Serializable}，以便 Java 实现的函数在构建阶段被序列化并下发至运行时。
 *
 * @see Lang 函数实现语言（Java、Python、Go 等）
 */
public interface Function extends Serializable {

    /**
     * 返回本函数的实现语言，用于运行时调度到对应语言的 worker。
     *
     * @return 语言枚举
     */
    Lang getLang();
}
