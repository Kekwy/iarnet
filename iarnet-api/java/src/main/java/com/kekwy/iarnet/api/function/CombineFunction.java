package com.kekwy.iarnet.api.function;

import java.io.Serializable;

/**
 * 双输入组合函数，用于在单算子内完成 Fork-Join 合并：
 * 接收左侧单值和右侧列表，返回合并结果。
 */
@FunctionalInterface
public interface CombineFunction<L, R, OUT> extends Serializable {

    OUT apply(L left, R right);
}

