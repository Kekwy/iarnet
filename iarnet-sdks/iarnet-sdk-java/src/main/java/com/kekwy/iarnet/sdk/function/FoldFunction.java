package com.kekwy.iarnet.sdk.function;

import com.kekwy.iarnet.proto.common.Lang;

/**
 * 流式折叠聚合函数，用于 KeyedFlow.fold()。
 * 每收到一个元素更新累加器并发出当前值。
 */
@FunctionalInterface
public interface FoldFunction<T, ACC> extends Function {

    ACC apply(ACC accumulator, T value);

    @Override
    default Lang getLang() {
        return Lang.LANG_JAVA;
    }
}
