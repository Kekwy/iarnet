package com.kekwy.iarnet.api.source;

import com.kekwy.iarnet.api.PrimitiveType;
import com.kekwy.iarnet.api.StructType;
import com.kekwy.iarnet.api.TypeKind;
import com.kekwy.iarnet.api.graph.ConstantSourceNode;
import com.kekwy.iarnet.api.graph.NodeKind;
import com.kekwy.iarnet.api.graph.Row;
import com.kekwy.iarnet.api.graph.SourceNode;
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

    /** 传入 null 列表时，应返回空 rows 且 outputType 回退为 STRING */
    @Test
    void visitConstantSource_nullList_returnsEmptyRowsWithStringType() {
        ConstantSource<?> source = new ConstantSource<>(null);
        SourceNode node = source.accept(visitor);

        assertInstanceOf(ConstantSourceNode.class, node);
        ConstantSourceNode csn = (ConstantSourceNode) node;

        assertEquals(PrimitiveType.STRING, csn.getOutputType());
        assertTrue(csn.getRows().isEmpty());
        assertNotNull(csn.getId());
        assertEquals(NodeKind.SOURCE, csn.getKind());
    }

    /** 传入空列表时，行为与 null 一致：空 rows、STRING 类型 */
    @Test
    void visitConstantSource_emptyList_returnsEmptyRowsWithStringType() {
        ConstantSource<String> source = new ConstantSource<>(List.of());
        SourceNode node = source.accept(visitor);

        ConstantSourceNode csn = (ConstantSourceNode) node;
        assertEquals(PrimitiveType.STRING, csn.getOutputType());
        assertTrue(csn.getRows().isEmpty());
    }

    // ---------- ConstantSource: 字符串类型 ----------

    /** 传入字符串列表，outputType 应推断为 STRING，rows 值与原始数据一致 */
    @Test
    void visitConstantSource_strings_returnsStringType() {
        ConstantSource<String> source = ConstantSource.of("hello", "world");
        SourceNode node = source.accept(visitor);

        ConstantSourceNode csn = (ConstantSourceNode) node;
        assertEquals(PrimitiveType.STRING, csn.getOutputType());
        assertEquals(2, csn.getRows().size());

        Row first = csn.getRows().get(0);
        assertEquals("hello", first.getValue());
        assertEquals(PrimitiveType.STRING, first.getDataType());
    }

    // ---------- ConstantSource: 整型 ----------

    /** 传入 Integer 列表，outputType 应推断为 INT32 */
    @Test
    void visitConstantSource_integers_returnsInt32Type() {
        ConstantSource<Integer> source = ConstantSource.of(1, 2, 3);
        SourceNode node = source.accept(visitor);

        ConstantSourceNode csn = (ConstantSourceNode) node;
        assertEquals(TypeKind.INT32, csn.getOutputType().getKind());
        assertEquals(3, csn.getRows().size());
        assertEquals(2, csn.getRows().get(1).getValue());
    }

    // ---------- ConstantSource: 长整型 ----------

    /** 传入 Long 列表，outputType 应推断为 INT64 */
    @Test
    void visitConstantSource_longs_returnsInt64Type() {
        ConstantSource<Long> source = ConstantSource.of(100L, 200L);
        SourceNode node = source.accept(visitor);

        ConstantSourceNode csn = (ConstantSourceNode) node;
        assertEquals(TypeKind.INT64, csn.getOutputType().getKind());
        assertEquals(2, csn.getRows().size());
    }

    // ---------- ConstantSource: 浮点型 ----------

    /** 传入 Double 列表，outputType 应推断为 DOUBLE */
    @Test
    void visitConstantSource_doubles_returnsDoubleType() {
        ConstantSource<Double> source = ConstantSource.of(1.5, 2.5);
        SourceNode node = source.accept(visitor);

        ConstantSourceNode csn = (ConstantSourceNode) node;
        assertEquals(TypeKind.DOUBLE, csn.getOutputType().getKind());
    }

    // ---------- ConstantSource: 布尔型 ----------

    /** 传入 Boolean 列表，outputType 应推断为 BOOLEAN */
    @Test
    void visitConstantSource_booleans_returnsBooleanType() {
        ConstantSource<Boolean> source = ConstantSource.of(true, false);
        SourceNode node = source.accept(visitor);

        ConstantSourceNode csn = (ConstantSourceNode) node;
        assertEquals(TypeKind.BOOLEAN, csn.getOutputType().getKind());
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

    /** 传入自定义 POJO，outputType 应推断为 StructType，字段名称与顺序正确 */
    @Test
    void visitConstantSource_pojo_returnsStructType() {
        ConstantSource<User> source = ConstantSource.of(new User("alice", 30));
        SourceNode node = source.accept(visitor);

        ConstantSourceNode csn = (ConstantSourceNode) node;
        assertInstanceOf(StructType.class, csn.getOutputType());

        StructType st = (StructType) csn.getOutputType();
        assertEquals(TypeKind.STRUCT, st.getKind());
        assertEquals(2, st.getFields().size());
        assertEquals("name", st.getFields().get(0).getName());
        assertEquals("age", st.getFields().get(1).getName());
    }

    // ---------- ConstantSource: 单元素 ----------

    /** 只传入一个元素时，应恰好生成一条 Row */
    @Test
    void visitConstantSource_singleElement_producesOneRow() {
        ConstantSource<String> source = ConstantSource.of("only");
        SourceNode node = source.accept(visitor);

        ConstantSourceNode csn = (ConstantSourceNode) node;
        assertEquals(1, csn.getRows().size());
        assertEquals("only", csn.getRows().get(0).getValue());
    }

    // ---------- 所有 Row 的类型与节点 outputType 一致 ----------

    /** 每条 Row 的 dataType 应与节点的 outputType 是同一个对象引用 */
    @Test
    void visitConstantSource_allRowsShareSameDataType() {
        ConstantSource<Integer> source = ConstantSource.of(10, 20, 30);
        ConstantSourceNode csn = (ConstantSourceNode) source.accept(visitor);

        for (Row row : csn.getRows()) {
            assertSame(csn.getOutputType(), row.getDataType());
        }
    }

    // ---------- 每次调用生成唯一 ID ----------

    /** 对同一个 Source 多次调用 visit，每次应产出不同的节点 ID */
    @Test
    void visitConstantSource_differentCallsProduceDifferentIds() {
        ConstantSource<String> source = ConstantSource.of("a");

        SourceNode node1 = source.accept(visitor);
        SourceNode node2 = source.accept(visitor);

        assertNotEquals(node1.getId(), node2.getId());
    }
}
