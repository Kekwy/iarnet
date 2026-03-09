package com.kekwy.iarnet.sdk.converter;

import com.kekwy.iarnet.proto.ValueCodec;
import com.kekwy.iarnet.proto.workflow.*;
import com.kekwy.iarnet.sdk.Resource;
import com.kekwy.iarnet.sdk.graph.*;
import com.kekwy.iarnet.sdk.graph.Node;

import java.util.List;

/**
 * 从 SDK 的 Node / Edge 列表组装 proto {@link WorkflowGraph}。
 * <p>
 * graph 包内的字段已直连 proto 类型，因此本类仅需处理
 * SDK {@link Resource} → proto {@link com.kekwy.iarnet.proto.common.Resource} 的转换，
 * 其余字段直接透传。
 */
public class WorkflowGraphBuilder implements NodeVisitor<com.kekwy.iarnet.proto.workflow.Node> {

    public WorkflowGraph build(String applicationId, List<Node> nodes, List<Edge> edges) {
        WorkflowGraph.Builder builder = WorkflowGraph.newBuilder()
                .setWorkflowId(com.kekwy.iarnet.sdk.util.IDUtil.genUUID())
                .setApplicationId(applicationId);

        for (Node node : nodes) {
            builder.addNodes(node.accept(this));
        }
        for (Edge edge : edges) {
            builder.addEdges(edge);
        }
        return builder.build();
    }

    // ======================== NodeVisitor 实现 ========================

    @Override
    public com.kekwy.iarnet.proto.workflow.Node visit(ConstantSourceNode node) {
        ConstantSourceDetail.Builder constantDetail = ConstantSourceDetail.newBuilder();
        if (node.getValues() != null) {
            for (Object value : node.getValues()) {
                constantDetail.addValues(ValueCodec.encode(value));
            }
        }

        SourceNodeDetail sourceDetail = SourceNodeDetail.newBuilder()
                .setSourceKind(SourceKind.CONSTANT)
                .setConstantSourceDetail(constantDetail)
                .build();

        return buildProtoNode(node)
                .setKind(NodeKind.SOURCE)
                .setSourceDetail(sourceDetail)
                .build();
    }

    @Override
    public com.kekwy.iarnet.proto.workflow.Node visit(FileSourceNode node) {
        FileSourceDetail fileDetail = FileSourceDetail.newBuilder()
                .setFilePath(node.getPath() != null ? node.getPath().toString() : "")
                .build();

        SourceNodeDetail sourceDetail = SourceNodeDetail.newBuilder()
                .setSourceKind(SourceKind.FILE)
                .setFileSourceDetail(fileDetail)
                .build();

        return buildProtoNode(node)
                .setKind(NodeKind.SOURCE)
                .setSourceDetail(sourceDetail)
                .build();
    }

    @Override
    public com.kekwy.iarnet.proto.workflow.Node visit(OperatorNode node) {
        OperatorNodeDetail.Builder detailBuilder = OperatorNodeDetail.newBuilder()
                .setOperatorKind(node.getOperatorKind());

        if (node.getKeySelector() != null) {
            detailBuilder.setKeySelector(node.getKeySelector());
        }
        if (node.getFoldInitialValue() != null) {
            detailBuilder.setFoldInitialValue(ValueCodec.encode(node.getFoldInitialValue()));
        }
        if (node.getTimeoutMs() > 0) {
            detailBuilder.setJoinTimeoutMs(node.getTimeoutMs());
        }

        return buildProtoNode(node)
                .setKind(NodeKind.OPERATOR)
                .setOperatorDetail(detailBuilder.build())
                .build();
    }

    @Override
    public com.kekwy.iarnet.proto.workflow.Node visit(SinkNode node) {
        SinkNodeDetail detail = SinkNodeDetail.newBuilder()
                .setSinkKind(node.getSinkKind())
                .build();

        return buildProtoNode(node)
                .setKind(NodeKind.SINK)
                .setSinkDetail(detail)
                .build();
    }

    // ======================== 公共 Node Builder ========================

    private com.kekwy.iarnet.proto.workflow.Node.Builder buildProtoNode(Node node) {
        com.kekwy.iarnet.proto.workflow.Node.Builder b =
                com.kekwy.iarnet.proto.workflow.Node.newBuilder()
                        .setId(node.getId());

        if (node.getInputType() != null) {
            b.setInputType(node.getInputType());
        }
        if (node.getOutputType() != null) {
            b.setOutputType(node.getOutputType());
        }
        if (node.getFunction() != null) {
            b.setFunction(node.getFunction());
        }
        if (node.getReplicas() > 0) {
            b.setReplicas(node.getReplicas());
        }
        if (node.getResource() != null) {
            b.setResource(convertResource(node.getResource()));
        }
        return b;
    }

    private com.kekwy.iarnet.proto.common.Resource convertResource(Resource res) {
        return com.kekwy.iarnet.proto.common.Resource.newBuilder()
                .setCpu(res.cpu())
                .setMemory(res.memory())
                .setGpu(res.gpu())
                .build();
    }
}
