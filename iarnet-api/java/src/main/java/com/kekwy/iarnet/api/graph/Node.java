package com.kekwy.iarnet.api.graph;

import com.kekwy.iarnet.api.DataType;
import com.kekwy.iarnet.api.Lang;

import java.util.ArrayList;
import java.util.List;

public abstract class Node {

    private final String id;

    // 输出类型（非常关键）
    private final DataType outputType;


    public Node(String id, DataType outputType) {
        this.id = id;
        this.outputType = outputType;
    }

    public String getId() {
        return id;
    }

    public DataType getOutputType() {
        return outputType;
    }

    public abstract NodeKind getKind();

}
