package com.kekwy.iarnet.sdk.function;

import com.kekwy.iarnet.proto.common.Lang;
import com.kekwy.iarnet.sdk.type.OptionalValue;

/**
 * 工作流多路合并函数。
 * <p>
 * 接收来自两个上游分支的元素（以 {@link OptionalValue} 形式，表示某分支可能暂无数据），
 * 合并为单一输出。用于 {@link com.kekwy.iarnet.sdk.Flow#join} 构建合并节点。
 *
 * @param <T> 第一路输入类型
 * @param <U> 第二路输入类型
 * @param <V> 合并输出类型
 */
@FunctionalInterface
public interface JoinFunction<T, U, V> extends Function {

    /**
     * 合并两路输入为单一输出。
     *
     * @param t 第一路（可能无值）
     * @param u 第二路（可能无值）
     * @return 合并后的结果
     */
    V join(OptionalValue<T> t, OptionalValue<U> u);

    @Override
    default Lang getLang() {
        return Lang.LANG_JAVA;
    }
}
