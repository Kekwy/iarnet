package com.kekwy.iarnet.sdk.converter;

import com.kekwy.iarnet.proto.Types;
import com.kekwy.iarnet.proto.ValueCodec;
import com.kekwy.iarnet.proto.common.Type;
import com.kekwy.iarnet.sdk.graph.ConstantSourceNode;
import com.kekwy.iarnet.sdk.graph.FileSourceNode;
import com.kekwy.iarnet.sdk.graph.SourceNode;
import com.kekwy.iarnet.sdk.source.ConstantSource;
import com.kekwy.iarnet.sdk.source.FileSource;
import com.kekwy.iarnet.sdk.util.IDUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SourceToNodeVisitor implements SourceVisitor<SourceNode> {

    @Override
    public SourceNode visit(ConstantSource<?> source) {
        List<?> value = source.getValue();
        Type outputType;
        List<Object> values;
        if (value == null || value.isEmpty()) {
            outputType = Types.STRING;
            values = new ArrayList<>();
        } else {
            outputType = ValueCodec.inferType(value.get(0));
            values = new ArrayList<>(value);
        }
        return new ConstantSourceNode(
                IDUtil.genUUID(),
                outputType,
                values);
    }

    @Override
    public SourceNode visit(FileSource source) {
        return new FileSourceNode(
                nextSourceNodeId("file"),
                Types.STRING,
                source.getPath());
    }

    private static String nextSourceNodeId(String prefix) {
        return "source-" + prefix + "-" + UUID.randomUUID();
    }
}
