package com.kekwy.iarnet.api.graph;

import java.util.List;

/**
 * 流上的处理阶段节点，对应 proto 中的 Stage（MapStage / FilterStage / FlatMapStage）。
 */
public record StageNode(String id, StageKind kind, String function) {

    public static StageNode map(String id, String function) {
        return new StageNode(id, StageKind.MAP, function);
    }

    public static StageNode filter(String id, String function) {
        return new StageNode(id, StageKind.FILTER, function);
    }

    public static StageNode flatMap(String id, String function) {
        return new StageNode(id, StageKind.FLAT_MAP, function);
    }



    public enum StageKind {
        MAP,
        FILTER,
        FLAT_MAP
    }
}
