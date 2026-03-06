package com.kekwy.iarnet.api.converter;

import com.google.protobuf.ByteString;
import com.kekwy.iarnet.api.*;
import com.kekwy.iarnet.api.graph.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 将 Java DSL 的图对象转换为 Protobuf IR 对象。
 * <p>
 * 实现 {@link NodeVisitor}，通过访问者模式分派不同类型的节点。
 */
public class GraphToProtoConverter implements NodeVisitor<com.kekwy.iarnet.proto.ir.Node> {

    // ======================== 顶层入口 ========================

    public com.kekwy.iarnet.proto.ir.WorkflowGraph convert(
            String applicationId, List<Node> nodes, List<Edge> edges) {

        com.kekwy.iarnet.proto.ir.WorkflowGraph.Builder builder =
                com.kekwy.iarnet.proto.ir.WorkflowGraph.newBuilder()
                        .setWorkflowId(com.kekwy.iarnet.api.util.IDUtil.genUUID())
                        .setApplicationId(applicationId);

        for (Node node : nodes) {
            builder.addNodes(node.accept(this));
        }
        for (Edge edge : edges) {
            builder.addEdges(convertEdge(edge));
        }
        return builder.build();
    }

    // ======================== NodeVisitor 实现 ========================

    @Override
    public com.kekwy.iarnet.proto.ir.Node visit(ConstantSourceNode node) {
        com.kekwy.iarnet.proto.ir.SourceNodeDetail.Builder detail =
                com.kekwy.iarnet.proto.ir.SourceNodeDetail.newBuilder()
                        .setSourceKind(com.kekwy.iarnet.proto.ir.SourceKind.CONSTANT);

        if (node.getRows() != null) {
            for (Row row : node.getRows()) {
                detail.addRows(convertRow(row));
            }
        }

        return buildProtoNode(node)
                .setKind(com.kekwy.iarnet.proto.ir.NodeKind.SOURCE)
                .setSourceDetail(detail)
                .build();
    }

    @Override
    public com.kekwy.iarnet.proto.ir.Node visit(FileSourceNode node) {
        com.kekwy.iarnet.proto.ir.SourceNodeDetail detail =
                com.kekwy.iarnet.proto.ir.SourceNodeDetail.newBuilder()
                        .setSourceKind(com.kekwy.iarnet.proto.ir.SourceKind.FILE)
                        .setFilePath(node.getPath() != null ? node.getPath().toString() : "")
                        .build();

        return buildProtoNode(node)
                .setKind(com.kekwy.iarnet.proto.ir.NodeKind.SOURCE)
                .setSourceDetail(detail)
                .build();
    }

    @Override
    public com.kekwy.iarnet.proto.ir.Node visit(OperatorNode node) {
        com.kekwy.iarnet.proto.ir.OperatorNodeDetail.Builder detail =
                com.kekwy.iarnet.proto.ir.OperatorNodeDetail.newBuilder()
                        .setOperatorKind(convertOperatorKind(node.getOperatorKind()))
                        .setReplicas(node.getReplicas());

        if (node.getFunction() != null) {
            detail.setFunction(convertFunctionDescriptor(node.getFunction()));
        }
        if (node.getResource() != null) {
            detail.setResource(convertResource(node.getResource()));
        }

        return buildProtoNode(node)
                .setKind(com.kekwy.iarnet.proto.ir.NodeKind.OPERATOR)
                .setOperatorDetail(detail)
                .build();
    }

    @Override
    public com.kekwy.iarnet.proto.ir.Node visit(SinkNode node) {
        com.kekwy.iarnet.proto.ir.SinkNodeDetail detail =
                com.kekwy.iarnet.proto.ir.SinkNodeDetail.newBuilder()
                        .setSinkKind(convertSinkKind(node.getSinkKind()))
                        .build();

        return buildProtoNode(node)
                .setKind(com.kekwy.iarnet.proto.ir.NodeKind.SINK)
                .setSinkDetail(detail)
                .build();
    }

    // ======================== 公共 Node Builder ========================

    private com.kekwy.iarnet.proto.ir.Node.Builder buildProtoNode(Node node) {
        com.kekwy.iarnet.proto.ir.Node.Builder b =
                com.kekwy.iarnet.proto.ir.Node.newBuilder()
                        .setId(node.getId());

        if (node.getOutputType() != null) {
            b.setOutputType(convertDataType(node.getOutputType()));
        }
        return b;
    }

    // ======================== DataType 转换 ========================

    public com.kekwy.iarnet.proto.ir.DataType convertDataType(DataType dt) {
        if (dt == null) {
            return com.kekwy.iarnet.proto.ir.DataType.getDefaultInstance();
        }

        com.kekwy.iarnet.proto.ir.DataType.Builder b =
                com.kekwy.iarnet.proto.ir.DataType.newBuilder()
                        .setKind(convertTypeKind(dt.getKind()));

        if (dt instanceof ArrayType arr) {
            b.setElementType(convertDataType(arr.getElementType()));
        } else if (dt instanceof MapType map) {
            b.setKeyType(convertDataType(map.getKeyType()));
            b.setValueType(convertDataType(map.getValueType()));
        } else if (dt instanceof StructType struct) {
            for (Field f : struct.getFields()) {
                b.addFields(com.kekwy.iarnet.proto.ir.Field.newBuilder()
                        .setName(f.getName())
                        .setType(convertDataType(f.getType()))
                        .build());
            }
        }
        return b.build();
    }

    // ======================== 枚举映射 ========================

    private com.kekwy.iarnet.proto.ir.TypeKind convertTypeKind(TypeKind kind) {
        return switch (kind) {
            case STRING  -> com.kekwy.iarnet.proto.ir.TypeKind.STRING;
            case INT32   -> com.kekwy.iarnet.proto.ir.TypeKind.INT32;
            case INT64   -> com.kekwy.iarnet.proto.ir.TypeKind.INT64;
            case DOUBLE  -> com.kekwy.iarnet.proto.ir.TypeKind.DOUBLE;
            case BOOLEAN -> com.kekwy.iarnet.proto.ir.TypeKind.BOOLEAN;
            case ARRAY   -> com.kekwy.iarnet.proto.ir.TypeKind.ARRAY;
            case MAP     -> com.kekwy.iarnet.proto.ir.TypeKind.MAP;
            case STRUCT  -> com.kekwy.iarnet.proto.ir.TypeKind.STRUCT;
        };
    }

    private com.kekwy.iarnet.proto.ir.Lang convertLang(Lang lang) {
        return switch (lang) {
            case LANG_JAVA   -> com.kekwy.iarnet.proto.ir.Lang.LANG_JAVA;
            case LANG_PYTHON -> com.kekwy.iarnet.proto.ir.Lang.LANG_PYTHON;
        };
    }

    private com.kekwy.iarnet.proto.ir.OperatorKind convertOperatorKind(
            OperatorNode.OperatorKind kind) {
        return switch (kind) {
            case MAP      -> com.kekwy.iarnet.proto.ir.OperatorKind.OPERATOR_MAP;
            case FLAT_MAP -> com.kekwy.iarnet.proto.ir.OperatorKind.OPERATOR_FLAT_MAP;
            case FILTER   -> com.kekwy.iarnet.proto.ir.OperatorKind.OPERATOR_FILTER;
            case UNION    ->
                    // 目前 proto 中尚无 UNION 专用枚举值，先映射为 UNSPECIFIED，
                    // 由下游根据多输入边 + kind=UNION 的语义处理。
                    com.kekwy.iarnet.proto.ir.OperatorKind.OPERATOR_KIND_UNSPECIFIED;
        };
    }

    private com.kekwy.iarnet.proto.ir.SinkKind convertSinkKind(SinkNode.SinkKind kind) {
        return switch (kind) {
            case PRINT -> com.kekwy.iarnet.proto.ir.SinkKind.PRINT;
        };
    }

    // ======================== 复合类型转换 ========================

    private com.kekwy.iarnet.proto.ir.FunctionDescriptor convertFunctionDescriptor(
            FunctionDescriptor fd) {

        com.kekwy.iarnet.proto.ir.FunctionDescriptor.Builder b =
                com.kekwy.iarnet.proto.ir.FunctionDescriptor.newBuilder()
                        .setLang(convertLang(fd.getLang()));

        if (fd.getFunctionIdentifier() != null) {
            b.setFunctionIdentifier(fd.getFunctionIdentifier());
        }
        if (fd.getSerializedFunction() != null) {
            b.setSerializedFunction(ByteString.copyFrom(fd.getSerializedFunction()));
        }
        if (fd.getArtifactPath() != null && !fd.getArtifactPath().isEmpty()) {
            b.setArtifactPath(fd.getArtifactPath());
        }
        return b.build();
    }

    private com.kekwy.iarnet.proto.ir.Resource convertResource(Resource res) {
        return com.kekwy.iarnet.proto.ir.Resource.newBuilder()
                .setCpu(res.cpu())
                .setMemory(res.memory())
                .setGpu(res.gpu())
                .build();
    }

    private com.kekwy.iarnet.proto.ir.Edge convertEdge(Edge edge) {
        return com.kekwy.iarnet.proto.ir.Edge.newBuilder()
                .setFromNodeId(edge.fromNodeId())
                .setToNodeId(edge.toNodeId())
                .build();
    }

    private com.kekwy.iarnet.proto.ir.Row convertRow(Row row) {
        com.kekwy.iarnet.proto.ir.Row.Builder b =
                com.kekwy.iarnet.proto.ir.Row.newBuilder();

        if (row.getDataType() != null) {
            b.setDataType(convertDataType(row.getDataType()));
            if (row.getValue() != null) {
                b.setValue(ByteString.copyFrom(encodeValue(row.getValue(), row.getDataType())));
            }
        }
        return b.build();
    }

    // ======================== Row.value 编码（与 actor 侧 UpstreamRowDeserializer 对称） ========================

    private static byte[] encodeValue(Object value, DataType dataType) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(bos)) {
            writeValue(dos, value, dataType);
        } catch (IOException e) {
            throw new IllegalStateException("Row value 编码失败: " + e.getMessage(), e);
        }
        return bos.toByteArray();
    }

    private static void writeValue(DataOutputStream dos, Object value, DataType dataType)
            throws IOException {
        switch (dataType.getKind()) {
            case STRING  -> dos.writeUTF(value != null ? value.toString() : "");
            case INT32   -> dos.writeInt(value instanceof Number n ? n.intValue() : 0);
            case INT64   -> dos.writeLong(value instanceof Number n ? n.longValue() : 0L);
            case DOUBLE  -> dos.writeDouble(value instanceof Number n ? n.doubleValue() : 0.0);
            case BOOLEAN -> dos.writeBoolean(value instanceof Boolean b && b);
            case ARRAY   -> writeArray(dos, value, (ArrayType) dataType);
            case MAP     -> writeMap(dos, value, (MapType) dataType);
            case STRUCT  -> writeStruct(dos, value, (StructType) dataType);
        }
    }

    private static void writeArray(DataOutputStream dos, Object value, ArrayType arrayType)
            throws IOException {
        Collection<?> coll;
        if (value instanceof Collection<?> c) {
            coll = c;
        } else if (value != null && value.getClass().isArray()) {
            coll = java.util.Arrays.asList((Object[]) value);
        } else {
            dos.writeInt(0);
            return;
        }
        dos.writeInt(coll.size());
        DataType elementType = arrayType.getElementType();
        for (Object elem : coll) {
            writeValue(dos, elem, elementType);
        }
    }

    private static void writeMap(DataOutputStream dos, Object value, MapType mapType)
            throws IOException {
        if (!(value instanceof Map<?, ?> map)) {
            dos.writeInt(0);
            return;
        }
        dos.writeInt(map.size());
        DataType keyType = mapType.getKeyType();
        DataType valueType = mapType.getValueType();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            writeValue(dos, entry.getKey(), keyType);
            writeValue(dos, entry.getValue(), valueType);
        }
    }

    private static void writeStruct(DataOutputStream dos, Object value, StructType structType)
            throws IOException {
        if (value == null) {
            for (Field f : structType.getFields()) {
                writeValue(dos, null, f.getType());
            }
            return;
        }
        Class<?> clazz = value.getClass();
        for (Field field : structType.getFields()) {
            Object fieldValue;
            try {
                java.lang.reflect.Field javaField = clazz.getDeclaredField(field.getName());
                javaField.setAccessible(true);
                fieldValue = javaField.get(value);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                fieldValue = null;
            }
            writeValue(dos, fieldValue, field.getType());
        }
    }
}
