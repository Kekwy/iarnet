package com.kekwy.iarnet.sdk.function;

import com.kekwy.iarnet.proto.common.Lang;

/**
 * 工作流条件分支函数。
 * <p>
 * 根据上游元素决定边上的条件是否满足，用于 {@link com.kekwy.iarnet.sdk.ConditionalFlow} 的
 * {@code when(condition).then(...)} 分支。
 *
 * @param <T> 输入元素类型
 */
@FunctionalInterface
public interface ConditionFunction<T> extends Function {

    /**
     * 判断输入是否满足条件。
     *
     * @param input 上游传入的元素
     * @return true 表示满足条件，数据将沿该分支传递
     */
    boolean test(T input);

    @Override
    default Lang getLang() {
        return Lang.LANG_JAVA;
    }
}
