package com.kekwy.iarnet.api.sink;

import com.kekwy.iarnet.api.graph.SinkNode;
import com.kekwy.iarnet.api.graph.SinkNode.SinkKind;
import com.kekwy.iarnet.api.util.IDUtil;

public class SinkToNodeVisitor implements SinkVisitor<SinkNode> {

    @Override
    public SinkNode visit(PrintSink sink) {
        return new SinkNode(IDUtil.genUUID(), null, SinkKind.PRINT);
    }
}
