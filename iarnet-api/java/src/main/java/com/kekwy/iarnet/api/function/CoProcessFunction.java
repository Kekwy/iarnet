package com.kekwy.iarnet.api.function;

import com.kekwy.iarnet.api.Lang;

/**
 * 双输入算子函数，用于 keyBy + connect 之后的对齐处理。
 * <p>
 * 方法命名对标 Flink 的 CoProcessFunction：左流使用 {@link #processElement1}，
 * 右流使用 {@link #processElement2}。
 */
public interface CoProcessFunction<IN1, IN2, OUT> extends Function {

    /**
     * 处理来自左侧流的元素。
     */
    void processElement1(IN1 value, Context ctx, Collector<OUT> out) throws Exception;

    /**
     * 处理来自右侧流的元素。
     */
    void processElement2(IN2 value, Context ctx, Collector<OUT> out) throws Exception;

    /**
     * 上下文信息（预留扩展 point，当前仅占位）。
     */
    interface Context {
    }

    /**
     * 输出收集器。
     */
    interface Collector<OUT> {
        void collect(OUT record);
    }

    @Override
    default Lang getLang() {
        return Lang.LANG_JAVA;
    }
}

