package com.kekwy.iarnet.sdk.function;

import com.kekwy.iarnet.proto.common.Lang;

/**
 * 工作流任务函数（单输入单输出）。
 * <p>
 * 将上游元素转换为新类型并传递给下游。用于 flow 的 {@code then("task", taskFunction)}。
 * 支持 Java lambda、以及通过 {@link com.kekwy.iarnet.sdk.dsl.Tasks} 创建的 Python/Go 任务描述符。
 *
 * @param <I> 输入类型
 * @param <O> 输出类型
 */
@FunctionalInterface
public interface TaskFunction<I, O> extends Function {

    /**
     * 对输入执行转换并返回输出。
     *
     * @param input 上游传入的元素
     * @return 转换后的结果
     */
    O apply(I input);

    @Override
    default Lang getLang() {
        return Lang.LANG_JAVA;
    }
}
