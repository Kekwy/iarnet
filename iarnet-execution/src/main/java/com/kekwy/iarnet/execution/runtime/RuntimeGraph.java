package com.kekwy.iarnet.execution.runtime;

import com.kekwy.iarnet.execution.RuntimeNode;
import lombok.Getter;

import java.util.List;

@Getter
public class RuntimeGraph {

    private final List<RuntimeNode> inputNodes;
    private final List<RuntimeNode> outputNodes;
    private final List<RuntimeNode> taskNodes;

    public RuntimeGraph(List<RuntimeNode> inputNodes, List<RuntimeNode> outputNodes, List<RuntimeNode> taskNodes) {
        this.inputNodes = inputNodes != null ? List.copyOf(inputNodes) : List.of();
        this.outputNodes = outputNodes != null ? List.copyOf(outputNodes) : List.of();
        this.taskNodes = taskNodes != null ? List.copyOf(taskNodes) : List.of();
    }

}
