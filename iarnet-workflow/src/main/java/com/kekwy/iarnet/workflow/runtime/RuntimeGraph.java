package com.kekwy.iarnet.workflow.runtime;

import com.kekwy.iarnet.workflow.RuntimeNode;

import java.util.List;

public class RuntimeGraph {

    private final List<RuntimeNode> inputNodes;
    private final List<RuntimeNode> outputNodes;
    private final List<RuntimeNode> taskNodes;

    public RuntimeGraph(List<RuntimeNode> inputNodes, List<RuntimeNode> outputNodes, List<RuntimeNode> taskNodes) {
        this.inputNodes = inputNodes != null ? List.copyOf(inputNodes) : List.of();
        this.outputNodes = outputNodes != null ? List.copyOf(outputNodes) : List.of();
        this.taskNodes = taskNodes != null ? List.copyOf(taskNodes) : List.of();
    }

    public List<RuntimeNode> getInputNodes() {
        return inputNodes;
    }

    public List<RuntimeNode> getOutputNodes() {
        return outputNodes;
    }

    public List<RuntimeNode> getTaskNodes() {
        return taskNodes;
    }
}
