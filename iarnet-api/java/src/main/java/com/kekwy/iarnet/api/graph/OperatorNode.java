package com.kekwy.iarnet.api.graph;

import com.kekwy.iarnet.api.Lang;

import java.util.ArrayList;
import java.util.List;

public class OperatorNode {

    private final List<Node> precursors = new ArrayList<>();

    private final List<Node> successors = new ArrayList<>();

    private final Lang lang;

    private final String operatorIdentifier;

    private final String sourceDir;

    public OperatorNode(Lang lang, String operatorIdentifier) {
        this(lang, operatorIdentifier, "");
    }

    public OperatorNode(Lang lang, String operatorIdentifier, String sourceDir) {
        this.lang = lang;
        this.operatorIdentifier = operatorIdentifier;
        this.sourceDir = sourceDir;
    }

    public String getSourceDir() {
        return sourceDir;
    }

    public List<Node> getPrecursors() {
        return precursors;
    }

    public List<Node> getSuccessors() {
        return successors;
    }

    public Lang getLang() {
        return lang;
    }

    public String getOperatorIdentifier() {
        return operatorIdentifier;
    }

}
