package com.kekwy.iarnet.api.graph;

/**
 * 数据汇节点，对应 proto 中的 Sink（如 PrintSink）。
 */
public record SinkNode(String id, SinkKind kind) implements Node {

    public static SinkNode print(String id) {
        return new SinkNode(id, SinkKind.PRINT);
    }

    @Override
    public String getId() {
        return id;
    }

    public enum SinkKind {
        PRINT
    }
}
