package com.kekwy.iarnet.sdk;

import com.kekwy.iarnet.proto.common.FunctionDescriptor;
import com.kekwy.iarnet.proto.common.Lang;
import com.kekwy.iarnet.proto.common.TypeKind;
import com.kekwy.iarnet.proto.workflow.Edge;
import com.kekwy.iarnet.proto.workflow.NodeKind;
import com.kekwy.iarnet.proto.workflow.OperatorKind;
import com.kekwy.iarnet.sdk.function.CombineFunction;
import com.kekwy.iarnet.sdk.function.Function;
import com.kekwy.iarnet.sdk.function.MapFunction;
import com.kekwy.iarnet.sdk.graph.Node;
import com.kekwy.iarnet.sdk.graph.OperatorNode;
import com.kekwy.iarnet.sdk.source.ConstantSource;
import com.kekwy.iarnet.sdk.util.SerializationUtil;
import com.kekwy.iarnet.sdk.util.TypeToken;
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

        @Test
        void lambda_simpleReturnType_infersOutputType() {
            wf.source(ConstantSource.of("hello", "world"))
                    .map(String::length);

            OperatorNode op = operatorNode(0);
            assertEquals(OperatorKind.OPERATOR_MAP, op.getOperatorKind());
            assertEquals(Lang.LANG_JAVA, op.getFunction().getLang());
            assertEquals(NodeKind.OPERATOR, op.getKind());
            assertNotNull(op.getId());
            assertNotNull(op.getOutputType(), "Lambda 返回 Integer，应自动推断出 outputType");
            assertEquals(TypeKind.TYPE_KIND_INT32, op.getOutputType().getKind());
        }

        @Test
        void lambda_stringReturnType_infersString() {
            wf.source(ConstantSource.of("a", "b"))
                    .map(String::toUpperCase);

            OperatorNode op = operatorNode(0);
            assertNotNull(op.getOutputType());
            assertEquals(TypeKind.TYPE_KIND_STRING, op.getOutputType().getKind());
        }

        @Test
        void lambda_serializedFunction_canDeserializeAndExecute() {
            wf.source(ConstantSource.of("abc"))
                    .map(String::length);

            OperatorNode op = operatorNode(0);
            FunctionDescriptor fd = op.getFunction();
            assertFalse(fd.getSerializedFunction().isEmpty());

            MapFunction<String, Integer> restored =
                    SerializationUtil.deserialize(fd.getSerializedFunction().toByteArray());
            assertEquals(3, restored.apply("abc"));
        }

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
    //  map: 匿名内部类
    // ====================================================================

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

        @Test
        void anonymousClass_infersOutputType() {
            wf.source(ConstantSource.of("hello"))
                    .map(stringToDoubleMapper());

            OperatorNode op = operatorNode(0);
            assertNotNull(op.getOutputType());
            assertEquals(TypeKind.TYPE_KIND_DOUBLE, op.getOutputType().getKind());
        }
    }

    private static com.kekwy.iarnet.sdk.function.CoProcessFunction<String, String, String> simpleCoProcessFunction() {
        return new com.kekwy.iarnet.sdk.function.CoProcessFunction<String, String, String>() {
            @Override
            public void processElement1(String value, Context ctx, Collector<String> out) {
                out.collect("L:" + value);
            }

            @Override
            public void processElement2(String value, Context ctx, Collector<String> out) {
                out.collect("R:" + value);
            }
        };
    }

    // ====================================================================
    //  map: Python 函数
    // ====================================================================

    @Nested
    class MapPythonFunctionTests {

        @Test
        void pythonMap_infersOutputTypeFromReturnType() {
            MapFunction<String, Integer> pyMapper = Function.pythonMap("transform.py", "to_int", Integer.class);
            wf.source(ConstantSource.of("1", "2"))
                    .map(pyMapper);

            OperatorNode op = operatorNode(0);
            FunctionDescriptor fd = op.getFunction();
            assertEquals(Lang.LANG_PYTHON, fd.getLang());
            assertEquals("transform.py:to_int", fd.getFunctionIdentifier());
            assertTrue(fd.getSerializedFunction().isEmpty(), "Python 函数不需要序列化");
            assertNotNull(op.getOutputType());
            assertEquals(TypeKind.TYPE_KIND_INT32, op.getOutputType().getKind());
        }
    }

    // ====================================================================
    //  map: returns() 回填类型
    // ====================================================================

    @Nested
    class MapReturnsTests {

        @Test
        void returns_setsOutputTypeOnNode() {
            wf.source(ConstantSource.of("a,b", "c,d"))
                    .map((String s) -> Arrays.asList(s.split(",")))
                    .returns(new TypeToken<List<String>>() {});

            OperatorNode op = operatorNode(0);
            assertNotNull(op.getOutputType());
            assertEquals(TypeKind.TYPE_KIND_ARRAY, op.getOutputType().getKind());
        }
    }

    // ====================================================================
    //  flatMap
    // ====================================================================

    @Nested
    class FlatMapTests {

        @Test
        void flatMap_lambda_createsOperatorNode() {
            wf.source(ConstantSource.of("a,b", "c,d"))
                    .flatMap((String s) -> Arrays.asList(s.split(",")));

            OperatorNode op = operatorNode(0);
            assertEquals(OperatorKind.OPERATOR_FLAT_MAP, op.getOperatorKind());
            assertEquals(Lang.LANG_JAVA, op.getFunction().getLang());
            assertFalse(op.getFunction().getSerializedFunction().isEmpty());
        }
    }

    // ====================================================================
    //  forkJoin: 单算子内对齐 Fork-Join
    // ====================================================================

    @Nested
    class ForkJoinTests {

        @Test
        void forkJoin_createsSingleMapOperator_withCombinedLogic() {
            wf.source(ConstantSource.of("a,b", "c,d"))
                    .forkJoin(
                            (String s) -> s.toUpperCase(),
                            (String s) -> Arrays.asList(s.split(",")),
                            (CombineFunction<String, java.util.List<String>, String>) (left, rights) ->
                                    left + ":" + String.join("|", rights)
                    );

            OperatorNode op = operatorNode(0);
            assertEquals(OperatorKind.OPERATOR_MAP, op.getOperatorKind(), "forkJoin 当前作为单输入 MAP 实现");
        }
    }

    // ====================================================================
    //  filter
    // ====================================================================

    @Nested
    class FilterTests {

        @Test
        void filter_createsOperatorNode_inheritOutputType() {
            wf.source(ConstantSource.of("hello", "hi", "hey"))
                    .filter((String s) -> s.length() > 2);

            OperatorNode op = operatorNode(0);
            assertEquals(OperatorKind.OPERATOR_FILTER, op.getOperatorKind());
            assertEquals(Lang.LANG_JAVA, op.getFunction().getLang());
            assertFalse(op.getFunction().getSerializedFunction().isEmpty());
            assertNotNull(op.getOutputType(), "filter 的 outputType 应继承自上游 SourceNode");
            assertEquals(TypeKind.TYPE_KIND_STRING, op.getOutputType().getKind());
        }
    }

    // ====================================================================
    //  链式调用
    // ====================================================================

    @Nested
    class ChainTests {

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

            assertEquals(OperatorKind.OPERATOR_MAP, map1.getOperatorKind());
            assertEquals(OperatorKind.OPERATOR_FILTER, filter.getOperatorKind());
            assertEquals(OperatorKind.OPERATOR_MAP, map2.getOperatorKind());

            assertEquals(TypeKind.TYPE_KIND_INT32, map1.getOutputType().getKind());
            assertEquals(TypeKind.TYPE_KIND_INT32, filter.getOutputType().getKind());
            assertEquals(TypeKind.TYPE_KIND_DOUBLE, map2.getOutputType().getKind());
        }

        @Test
        void chainedOperators_edgesLinked() {
            wf.source(ConstantSource.of("a"))
                    .map(String::length)
                    .map(Object::toString);

            List<Node> allNodes = wf.getNodes();
            List<Edge> allEdges = wf.getEdges();

            Node sourceNode = allNodes.get(0);
            OperatorNode map1 = operatorNode(0);
            OperatorNode map2 = operatorNode(1);

            assertEquals(2, allEdges.size());
            assertEquals(edge(sourceNode.getId(), map1.getId()), allEdges.get(0));
            assertEquals(edge(map1.getId(), map2.getId()), allEdges.get(1));
        }
    }

    // ====================================================================
    //  union
    // ====================================================================

    @Nested
    class UnionTests {

        @Test
        void union_createsUnionOperatorNode_andLinksFromBothBranches() {
            Flow<String> left = wf.source(ConstantSource.of("a", "b"))
                    .map(String::toUpperCase);
            Flow<String> right = wf.source(ConstantSource.of("x", "y"))
                    .filter(s -> s.length() > 0);

            left.union(right);

            List<Node> allNodes = wf.getNodes();
            List<Edge> allEdges = wf.getEdges();

            assertEquals(5, allNodes.size());

            OperatorNode unionNode = operatorNode(2);
            assertEquals(OperatorKind.OPERATOR_UNION, unionNode.getOperatorKind());

            List<Edge> toUnionEdges = allEdges.stream()
                    .filter(e -> e.getToNodeId().equals(unionNode.getId()))
                    .toList();
            assertEquals(2, toUnionEdges.size());
        }
    }

    // ====================================================================
    //  keyBy + connect + CoProcess
    // ====================================================================

    @Nested
    class CoProcessTests {

        @Test
        void keyBy_connect_process_createsSingleOperatorWithTwoInputs() {
            Flow<String> right = wf.source(ConstantSource.of("x", "yy", "zzz"));

            wf.source(ConstantSource.of("hello", "world"))
                    .keyBy((String s) -> s.length())
                    .connect(
                            right.keyBy((String s) -> s.length())
                    )
                    .process(simpleCoProcessFunction());

            java.util.List<Node> allNodes = wf.getNodes();
            boolean hasOperator = allNodes.stream().anyMatch(n -> n.getKind() == NodeKind.OPERATOR);
            assertTrue(hasOperator);
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

    private static Edge edge(String fromNodeId, String toNodeId) {
        return Edge.newBuilder()
                .setFromNodeId(fromNodeId)
                .setToNodeId(toNodeId)
                .build();
    }
}
