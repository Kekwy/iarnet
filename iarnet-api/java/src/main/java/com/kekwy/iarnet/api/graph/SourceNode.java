package com.kekwy.iarnet.api.graph;

import java.util.List;

/**
 * 数据源节点，对应 proto 中的 Source（FileSource / ConstantSource）。
 */
public record SourceNode(String id, SourceKind kind, String path, List<String> constantValues)
        implements Node {

    public static SourceNode file(String id, String path) {
        return new SourceNode(id, SourceKind.FILE, path, null);
    }

    public static SourceNode constant(String id, List<String> values) {
        return new SourceNode(id, SourceKind.CONSTANT, null, values != null ? List.copyOf(values) : List.of());
    }

    @Override
    public String getId() {
        return id;
    }

    public enum SourceKind {
        FILE,
        CONSTANT
    }
}
