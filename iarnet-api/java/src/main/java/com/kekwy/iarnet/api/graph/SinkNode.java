package com.kekwy.iarnet.api.graph;

/**
 * 数据汇节点，对应 proto 中的 Sink（如 PrintSink）。
 */
public record SinkNode(String id, SinkKind kind) {

    public static SinkNode print(String id) {
        return new SinkNode(id, SinkKind.PRINT);
    }


    public enum SinkKind {
        PRINT
    }
}
