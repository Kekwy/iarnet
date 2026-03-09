package com.kekwy.iarnet.sdk.converter;

import com.kekwy.iarnet.proto.workflow.SinkKind;
import com.kekwy.iarnet.sdk.graph.SinkNode;
import com.kekwy.iarnet.sdk.sink.PrintSink;
import com.kekwy.iarnet.sdk.util.IDUtil;

public class SinkToNodeVisitor implements SinkVisitor<SinkNode> {

    @Override
    public SinkNode visit(PrintSink sink) {
        return new SinkNode(IDUtil.genUUID(), null, SinkKind.PRINT);
    }
}
