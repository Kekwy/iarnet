package com.kekwy.iarnet.api;

import com.kekwy.iarnet.api.function.FilterFunction;
import com.kekwy.iarnet.api.function.FlatMapFunction;
import com.kekwy.iarnet.api.function.Function;
import com.kekwy.iarnet.api.function.MapFunction;
import com.kekwy.iarnet.api.graph.Node;
import com.kekwy.iarnet.api.graph.NodeKind;
import com.kekwy.iarnet.api.graph.OperatorNode;
import com.kekwy.iarnet.api.graph.OperatorNode.OperatorKind;
import com.kekwy.iarnet.api.source.ConstantSource;
import com.kekwy.iarnet.api.util.SerializationUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowOperatorNodeTest {

    private Workflow wf;

    @BeforeEach
    void setUp() {
        wf = Workflow.create();
    }

    /** 从 workflow 的节点列表中获取第 index 个 OperatorNode */
    private OperatorNode operatorNode(int index) {
        List<Node> operators = wf.getNodes().stream()
                .filter(n -> n.getKind() == NodeKind.OPERATOR)
                .toList();
        assertInstanceOf(OperatorNode.class, operators.get(index));
        return (OperatorNode) operators.get(index);
    }

    // ====================================================================
    //  map: Lambda
    // ====================================================================

    @Nested
    class MapLambdaTests {

        /** Lambda 返回简单类型（Integer），outputType 应自动推断为 INT32 */
        @Test
        void lambda_simpleReturnType_infersOutputType() {
            wf.source(ConstantSource.of("hello", "world"))
                    .map(String::length);

            OperatorNode op = operatorNode(0);
            assertEquals(OperatorKind.MAP, op.getOperatorKind());
            assertEquals(Lang.LANG_JAVA, op.getLang());
            assertEquals(NodeKind.OPERATOR, op.getKind());
            assertNotNull(op.getId());
            assertNotNull(op.getOutputType(), "Lambda 返回 Integer，应自动推断出 outputType");
            assertEquals(TypeKind.INT32, op.getOutputType().getKind());
        }

        /** Lambda 返回 String，outputType 应为 STRING */
        @Test
        void lambda_stringReturnType_infersString() {
            wf.source(ConstantSource.of("a", "b"))
                    .map(String::toUpperCase);

            OperatorNode op = operatorNode(0);
            assertNotNull(op.getOutputType());
            assertEquals(TypeKind.STRING, op.getOutputType().getKind());
        }

        /** Java lambda 的序列化字节应非空，且可反序列化后执行 */
        @Test
        void lambda_serializedFunction_canDeserializeAndExecute() {
            wf.source(ConstantSource.of("abc"))
                    .map(String::length);

            OperatorNode op = operatorNode(0);
            assertNotNull(op.getSerializedFunction());
            assertTrue(op.getSerializedFunction().length > 0);

            MapFunction<String, Integer> restored = SerializationUtil.deserialize(op.getSerializedFunction());
            assertEquals(3, restored.apply("abc"));
        }

        /** 指定 replicas 和 resource */
        @Test
        void lambda_withReplicasAndResource_populatesFields() {
            Resource res = Resource.of(2.0, "1Gi", 1.0);
            wf.source(ConstantSource.of("x"))
                    .map(String::length, 4, res);

            OperatorNode op = operatorNode(0);
            assertEquals(4, op.getReplicas());
            assertEquals(res, op.getResource());
        }
    }

    // ====================================================================
    //  map: 匿名内部类（泛型信息完整保留）
    // ====================================================================

    /** 在 static 上下文中创建，避免捕获不可序列化的外围实例 */
    private static MapFunction<String, Double> stringToDoubleMapper() {
        return new MapFunction<String, Double>() {
            @Override
            public Double apply(String value) {
                return (double) value.length();
            }
        };
    }

    @Nested
    class MapAnonymousClassTests {

        /** 匿名内部类，通过 getGenericInterfaces() 推断 outputType */
        @Test
        void anonymousClass_infersOutputType() {
            wf.source(ConstantSource.of("hello"))
                    .map(stringToDoubleMapper());

            OperatorNode op = operatorNode(0);
            assertNotNull(op.getOutputType());
            assertEquals(TypeKind.DOUBLE, op.getOutputType().getKind());
        }
    }

    // ====================================================================
    //  map: Python 函数
    // ====================================================================

    @Nested
    class MapPythonFunctionTests {

        /** Python map 函数，outputType 来自显式声明的 returnType，serializedFunction 应为 null */
        @Test
        void pythonMap_infersOutputTypeFromReturnType() {
            MapFunction<String, Integer> pyMapper = Function.pythonMap("transform.py", "to_int", Integer.class);
            wf.source(ConstantSource.of("1", "2"))
                    .map(pyMapper);

            OperatorNode op = operatorNode(0);
            assertEquals(Lang.LANG_PYTHON, op.getLang());
            assertEquals("transform.py:to_int", op.getOperatorIdentifier());
            assertNull(op.getSerializedFunction(), "Python 函数不需要序列化");
            assertNotNull(op.getOutputType());
            assertEquals(TypeKind.INT32, op.getOutputType().getKind());
        }
    }

    // ====================================================================
    //  map: returns() 回填类型
    // ====================================================================

    @Nested
    class MapReturnsTests {

        /** Lambda 返回泛型类型（List），自动推断可能得到擦除后的 List，使用 returns() 补充精确类型 */
        @Test
        void returns_setsOutputTypeOnNode() {
            wf.source(ConstantSource.of("a,b", "c,d"))
                    .map((String s) -> Arrays.asList(s.split(",")))
                    .returns(new TypeToken<List<String>>() {});

            OperatorNode op = operatorNode(0);
            assertNotNull(op.getOutputType());
            assertEquals(TypeKind.ARRAY, op.getOutputType().getKind());
        }
    }

    // ====================================================================
    //  flatMap
    // ====================================================================

    @Nested
    class FlatMapTests {

        /** flatMap lambda，生成 FLAT_MAP 类型的 OperatorNode */
        @Test
        void flatMap_lambda_createsOperatorNode() {
            wf.source(ConstantSource.of("a,b", "c,d"))
                    .flatMap((String s) -> Arrays.asList(s.split(",")));

            OperatorNode op = operatorNode(0);
            assertEquals(OperatorKind.FLAT_MAP, op.getOperatorKind());
            assertEquals(Lang.LANG_JAVA, op.getLang());
            assertNotNull(op.getSerializedFunction());
        }
    }

    // ====================================================================
    //  filter
    // ====================================================================

    @Nested
    class FilterTests {

        /** filter 生成 FILTER 类型的 OperatorNode，outputType 继承自上游 */
        @Test
        void filter_createsOperatorNode_inheritOutputType() {
            wf.source(ConstantSource.of("hello", "hi", "hey"))
                    .filter((String s) -> s.length() > 2);

            OperatorNode op = operatorNode(0);
            assertEquals(OperatorKind.FILTER, op.getOperatorKind());
            assertEquals(Lang.LANG_JAVA, op.getLang());
            assertNotNull(op.getSerializedFunction());
            assertNotNull(op.getOutputType(), "filter 的 outputType 应继承自上游 SourceNode");
            assertEquals(TypeKind.STRING, op.getOutputType().getKind());
        }
    }

    // ====================================================================
    //  链式调用 (source → map → filter → map)
    // ====================================================================

    @Nested
    class ChainTests {

        /** 验证多步链式调用能正确生成多个 OperatorNode 并建立前驱/后继关系 */
        @Test
        void chainedOperators_allNodesRegistered() {
            wf.source(ConstantSource.of("hello", "world"))
                    .map((String s) -> s.length())
                    .filter((Integer n) -> n > 3)
                    .map((Integer n) -> n * 2.0);

            List<Node> allNodes = wf.getNodes();
            assertEquals(4, allNodes.size(), "1 SourceNode + 3 OperatorNodes");

            OperatorNode map1 = operatorNode(0);
            OperatorNode filter = operatorNode(1);
            OperatorNode map2 = operatorNode(2);

            assertEquals(OperatorKind.MAP, map1.getOperatorKind());
            assertEquals(OperatorKind.FILTER, filter.getOperatorKind());
            assertEquals(OperatorKind.MAP, map2.getOperatorKind());

            assertEquals(TypeKind.INT32, map1.getOutputType().getKind());
            assertEquals(TypeKind.INT32, filter.getOutputType().getKind());
            assertEquals(TypeKind.DOUBLE, map2.getOutputType().getKind());
        }

        /** 验证前驱/后继连接关系 */
        @Test
        void chainedOperators_precursorSuccessorLinked() {
            wf.source(ConstantSource.of("a"))
                    .map(String::length)
                    .map(Object::toString);

            List<Node> allNodes = wf.getNodes();
            Node sourceNode = allNodes.get(0);
            OperatorNode map1 = operatorNode(0);
            OperatorNode map2 = operatorNode(1);

            assertTrue(sourceNode.getSuccessors().contains(map1), "SourceNode → map1");
            assertTrue(map1.getPrecursors().contains(sourceNode), "map1 ← SourceNode");

            assertTrue(map1.getSuccessors().contains(map2), "map1 → map2");
            assertTrue(map2.getPrecursors().contains(map1), "map2 ← map1");
        }
    }

    // ====================================================================
    //  每次调用生成唯一 ID
    // ====================================================================

    @Test
    void eachOperator_hasUniqueId() {
        wf.source(ConstantSource.of("a"))
                .map(String::length)
                .map(Object::toString);

        OperatorNode op1 = operatorNode(0);
        OperatorNode op2 = operatorNode(1);
        assertNotEquals(op1.getId(), op2.getId());
    }
}
