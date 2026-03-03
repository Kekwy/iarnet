package com.kekwy.iarnet.application.executor;

import com.kekwy.iarnet.proto.ir.Node;
import com.kekwy.iarnet.proto.ir.OperatorNodeDetail;
import com.kekwy.iarnet.proto.ir.SinkNodeDetail;
import com.kekwy.iarnet.proto.ir.SourceNodeDetail;

/**
 * 访问者接口：对 Protobuf WorkflowGraph 中的各类节点进行处理。
 */
public interface ProtoNodeVisitor {

    void visitSource(Node node, SourceNodeDetail detail);

    void visitOperator(Node node, OperatorNodeDetail detail);

    void visitSink(Node node, SinkNodeDetail detail);

    /**
     * 根据 NodeKind 分派到对应的 visit 方法。
     */
    default void dispatch(Node node) {
        switch (node.getKind()) {
            case SOURCE -> visitSource(node, node.getSourceDetail());
            case OPERATOR -> visitOperator(node, node.getOperatorDetail());
            case SINK -> visitSink(node, node.getSinkDetail());
            default -> throw new IllegalArgumentException("未知的 NodeKind: " + node.getKind());
        }
    }
}
