package com.kekwy.iarnet.sdk.function;

import com.kekwy.iarnet.proto.common.Lang;

/**
 * 工作流输出（Sink）函数。
 * <p>
 * 消费上游传入的每个元素，执行副作用（如打印、写入存储等）。
 * 用于 flow 末端的 {@code then("sink", outputFunction)}。
 *
 * @param <I> 输入元素类型
 */
@FunctionalInterface
public interface OutputFunction<I> extends Function {

    /**
     * 消费一个上游元素。
     *
     * @param input 上游传入的元素
     */
    void accept(I input);

    @Override
    default Lang getLang() {
        return Lang.LANG_JAVA;
    }
}
