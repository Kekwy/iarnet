package com.kekwy.iarnet.sdk;

import com.kekwy.iarnet.sdk.function.CoProcessFunction;

/**
 * 两条 keyed 流连接后的中间抽象，允许通过 CoProcessFunction 进行双输入处理。
 */
public interface CoKeyedFlow<L, R, K> {

    /**
     * 使用 CoProcessFunction 处理两条 keyed 流，生成新的单输出流。
     */
    <OUT> Flow<OUT> process(CoProcessFunction<L, R, OUT> fn);
}

