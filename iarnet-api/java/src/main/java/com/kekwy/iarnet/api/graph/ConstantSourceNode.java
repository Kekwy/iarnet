package com.kekwy.iarnet.api.graph;

import com.kekwy.iarnet.api.DataType;

import java.util.List;

public class ConstantSourceNode extends SourceNode {

    private final List<Row> rows;

    public ConstantSourceNode(
            String id,
            DataType outputType,
            List<Row> rows) {

        super(id, outputType);
        this.rows = rows;
    }

    public List<Row> getRows() {
        return rows;
    }
}
