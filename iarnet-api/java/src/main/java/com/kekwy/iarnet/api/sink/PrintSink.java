package com.kekwy.iarnet.api.sink;

/**
 * 将每个元素打印到标准输出的 Sink。
 */
public final class PrintSink implements Sink<Object> {

    private static final PrintSink INSTANCE = new PrintSink();

    private PrintSink() {
    }

    public static PrintSink of() {
        return INSTANCE;
    }

    @Override
    public void accept(Object value) {
        System.out.println(value);
    }

    @Override
    public <R> R accept(SinkVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
