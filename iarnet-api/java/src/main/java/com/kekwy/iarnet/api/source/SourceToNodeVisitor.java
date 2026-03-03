package com.kekwy.iarnet.api.source;

import com.kekwy.iarnet.api.DataType;
import com.kekwy.iarnet.api.DataTypeInfer;
import com.kekwy.iarnet.api.PrimitiveType;
import com.kekwy.iarnet.api.graph.ConstantSourceNode;
import com.kekwy.iarnet.api.graph.FileSourceNode;
import com.kekwy.iarnet.api.graph.Row;
import com.kekwy.iarnet.api.graph.SourceNode;
import com.kekwy.iarnet.api.util.IDUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SourceToNodeVisitor implements SourceVisitor<SourceNode> {
    @Override
    public SourceNode visit(ConstantSource<?> source) {
        List<?> value = source.getValue();
        DataType outputType;
        List<Row> rows;
        if (value == null || value.isEmpty()) {
            outputType = PrimitiveType.STRING;
            rows = new ArrayList<>();
        } else {
            outputType = DataTypeInfer.infer(value.get(0));
            rows = value.stream().map(v -> new Row(v, outputType)).toList();
        }
        return new ConstantSourceNode(
                IDUtil.genUUID(),
                outputType,
                rows);
    }

    @Override
    public SourceNode visit(FileSource source) {
        return new FileSourceNode(
                nextSourceNodeId("file"),
                PrimitiveType.STRING,
                source.getPath());
    }

    private static String nextSourceNodeId(String prefix) {
        return "source-" + prefix + "-" + UUID.randomUUID();
    }
}
