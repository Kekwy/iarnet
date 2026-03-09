package com.kekwy.iarnet.sdk.function;

import com.kekwy.iarnet.proto.common.Lang;

/**
 * 双输入算子函数，用于 keyBy + connect 之后的对齐处理。
 * <p>
 * 方法命名对标 Flink 的 CoProcessFunction：左流使用 {@link #processElement1}，
 * 右流使用 {@link #processElement2}。
 */
public interface CoProcessFunction<IN1, IN2, OUT> extends Function {

    void processElement1(IN1 value, Context ctx, Collector<OUT> out) throws Exception;

    void processElement2(IN2 value, Context ctx, Collector<OUT> out) throws Exception;

    interface Context {
    }

    interface Collector<OUT> {
        void collect(OUT record);
    }

    @Override
    default Lang getLang() {
        return Lang.LANG_JAVA;
    }
}
