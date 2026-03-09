package com.kekwy.iarnet.sdk.converter;

import com.kekwy.iarnet.proto.Types;
import com.kekwy.iarnet.proto.common.TypeKind;
import com.kekwy.iarnet.proto.workflow.NodeKind;
import com.kekwy.iarnet.sdk.graph.ConstantSourceNode;
import com.kekwy.iarnet.sdk.graph.SourceNode;
import com.kekwy.iarnet.sdk.source.ConstantSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SourceToNodeVisitorTest {

    private SourceToNodeVisitor visitor;

    @BeforeEach
    void setUp() {
        visitor = new SourceToNodeVisitor();
    }

    // ---------- ConstantSource: null / 空列表 ----------

    @Test
    void visitConstantSource_nullList_returnsEmptyValuesWithStringType() {
        ConstantSource<?> source = new ConstantSource<>(null);
        SourceNode node = source.accept(visitor);

        assertInstanceOf(ConstantSourceNode.class, node);
        ConstantSourceNode csn = (ConstantSourceNode) node;

        assertEquals(Types.STRING, csn.getOutputType());
        assertTrue(csn.getValues().isEmpty());
        assertNotNull(csn.getId());
        assertEquals(NodeKind.SOURCE, csn.getKind());
    }

    @Test
    void visitConstantSource_emptyList_returnsEmptyValuesWithStringType() {
        ConstantSource<String> source = new ConstantSource<>(List.of());
        SourceNode node = source.accept(visitor);

        ConstantSourceNode csn = (ConstantSourceNode) node;
        assertEquals(Types.STRING, csn.getOutputType());
        assertTrue(csn.getValues().isEmpty());
    }

    // ---------- ConstantSource: 字符串类型 ----------

    @Test
    void visitConstantSource_strings_returnsStringType() {
        ConstantSource<String> source = ConstantSource.of("hello", "world");
        SourceNode node = source.accept(visitor);

        ConstantSourceNode csn = (ConstantSourceNode) node;
        assertEquals(TypeKind.TYPE_KIND_STRING, csn.getOutputType().getKind());
        assertEquals(2, csn.getValues().size());
        assertEquals("hello", csn.getValues().get(0));
    }

    // ---------- ConstantSource: 整型 ----------

    @Test
    void visitConstantSource_integers_returnsInt32Type() {
        ConstantSource<Integer> source = ConstantSource.of(1, 2, 3);
        SourceNode node = source.accept(visitor);

        ConstantSourceNode csn = (ConstantSourceNode) node;
        assertEquals(TypeKind.TYPE_KIND_INT32, csn.getOutputType().getKind());
        assertEquals(3, csn.getValues().size());
        assertEquals(2, csn.getValues().get(1));
    }

    // ---------- ConstantSource: 长整型 ----------

    @Test
    void visitConstantSource_longs_returnsInt64Type() {
        ConstantSource<Long> source = ConstantSource.of(100L, 200L);
        SourceNode node = source.accept(visitor);

        ConstantSourceNode csn = (ConstantSourceNode) node;
        assertEquals(TypeKind.TYPE_KIND_INT64, csn.getOutputType().getKind());
        assertEquals(2, csn.getValues().size());
    }

    // ---------- ConstantSource: 浮点型 ----------

    @Test
    void visitConstantSource_doubles_returnsDoubleType() {
        ConstantSource<Double> source = ConstantSource.of(1.5, 2.5);
        SourceNode node = source.accept(visitor);

        ConstantSourceNode csn = (ConstantSourceNode) node;
        assertEquals(TypeKind.TYPE_KIND_DOUBLE, csn.getOutputType().getKind());
    }

    // ---------- ConstantSource: 布尔型 ----------

    @Test
    void visitConstantSource_booleans_returnsBooleanType() {
        ConstantSource<Boolean> source = ConstantSource.of(true, false);
        SourceNode node = source.accept(visitor);

        ConstantSourceNode csn = (ConstantSourceNode) node;
        assertEquals(TypeKind.TYPE_KIND_BOOLEAN, csn.getOutputType().getKind());
    }

    // ---------- ConstantSource: 自定义对象（结构体） ----------

    static class User {
        String name;
        int age;

        User(String name, int age) {
            this.name = name;
            this.age = age;
        }
    }

    @Test
    void visitConstantSource_pojo_returnsStructType() {
        ConstantSource<User> source = ConstantSource.of(new User("alice", 30));
        SourceNode node = source.accept(visitor);

        ConstantSourceNode csn = (ConstantSourceNode) node;
        assertEquals(TypeKind.TYPE_KIND_STRUCT, csn.getOutputType().getKind());
        assertEquals(2, csn.getOutputType().getStructDetail().getFieldsCount());
        assertEquals("name", csn.getOutputType().getStructDetail().getFields(0).getName());
        assertEquals("age", csn.getOutputType().getStructDetail().getFields(1).getName());
    }

    // ---------- ConstantSource: 单元素 ----------

    @Test
    void visitConstantSource_singleElement_producesOneValue() {
        ConstantSource<String> source = ConstantSource.of("only");
        SourceNode node = source.accept(visitor);

        ConstantSourceNode csn = (ConstantSourceNode) node;
        assertEquals(1, csn.getValues().size());
        assertEquals("only", csn.getValues().get(0));
    }

    // ---------- 每次调用生成唯一 ID ----------

    @Test
    void visitConstantSource_differentCallsProduceDifferentIds() {
        ConstantSource<String> source = ConstantSource.of("a");

        SourceNode node1 = source.accept(visitor);
        SourceNode node2 = source.accept(visitor);

        assertNotEquals(node1.getId(), node2.getId());
    }
}
