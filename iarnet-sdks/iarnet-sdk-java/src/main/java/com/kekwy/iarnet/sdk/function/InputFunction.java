package com.kekwy.iarnet.sdk.function;

import com.kekwy.iarnet.proto.common.Lang;

import java.util.Optional;

/**
 * 工作流输入源函数。
 * <p>
 * 按序产出元素，每次调用 {@link #next()} 返回下一个元素；无更多数据时返回 {@link Optional#empty()}。
 * 用于 {@link com.kekwy.iarnet.sdk.Workflow#input(String, InputFunction)} 构建 Source 节点。
 *
 * @param <O> 输出元素类型
 */
@FunctionalInterface
public interface InputFunction<O> extends Function {

    /**
     * 获取下一个元素。
     *
     * @return 若有下一个元素则 {@link Optional#of(Object)}，否则 {@link Optional#empty()}
     * @throws Exception 若读取过程出错（如 I/O 异常）
     */
    Optional<O> next() throws Exception;

    @Override
    default Lang getLang() {
        return Lang.LANG_JAVA;
    }
}
