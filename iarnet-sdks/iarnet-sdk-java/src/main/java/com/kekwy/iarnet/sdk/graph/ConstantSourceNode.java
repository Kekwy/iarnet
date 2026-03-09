package com.kekwy.iarnet.sdk.graph;

import com.kekwy.iarnet.proto.common.Type;

import java.util.List;

/**
 * 常量数据源节点，存储原始 Java 对象列表。
 * <p>
 * 转换为 proto 时通过 {@code ValueCodec.encode()} 逐个编码为 {@code common.Value}。
 */
public class ConstantSourceNode extends SourceNode {

    private final List<Object> values;

    public ConstantSourceNode(String id, Type outputType, List<Object> values) {
        super(id, outputType);
        this.values = values;
    }

    public List<Object> getValues() {
        return values;
    }

    @Override
    public <R> R accept(NodeVisitor<R> visitor) {
        return visitor.visit(this);
    }
}
