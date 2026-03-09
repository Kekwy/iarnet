package com.kekwy.iarnet.sdk.graph;

public interface NodeVisitor<R> {

    R visit(ConstantSourceNode node);

    R visit(FileSourceNode node);

    R visit(OperatorNode node);

    R visit(SinkNode node);
}
