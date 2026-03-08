package com.kekwy.iarnet.api;

import com.google.protobuf.util.JsonFormat;
import com.kekwy.iarnet.api.function.Function;
import com.kekwy.iarnet.api.sink.PrintSink;
import com.kekwy.iarnet.api.source.ConstantSource;
import com.kekwy.iarnet.proto.ir.WorkflowGraph;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证完整的 DSL 管道能正确转换为 Protobuf WorkflowGraph IR 并输出到控制台。
 *
 * <p>测试通过 {@code toProtoGraph(applicationId)} 显式传入 applicationId。
 * 实际运行时 applicationId 从环境变量 {@code IARNET_APP_ID} 读取。
 */
class WorkflowToProtoTest {

    private static final String TEST_APP_ID = "test-app-001";

    /**
     * 完整管道：source(String) → map(→Integer) → filter → map(→Double) → sink(Print)
     * 验证生成的 WorkflowGraph 包含正确数量的节点、边，并以 JSON 格式输出到控制台。
     */
    @Test
    void fullPipeline_toProtoGraph_printsToConsole() throws Exception {
        Workflow wf = Workflow.create();

        wf.source(ConstantSource.of("hello", "hi", "hey", "world"))
                .map((String s) -> s.length())
                .filter((Integer n) -> n > 2)
                .map((Integer n) -> n * 1.5)
                .sink(PrintSink.of());

        WorkflowGraph graph = wf.toProtoGraph(TEST_APP_ID);

        assertNotNull(graph);
        assertEquals(5, graph.getNodesCount(), "1 Source + 1 Map + 1 Filter + 1 Map + 1 Sink");
        assertEquals(4, graph.getEdgesCount(), "4 edges linking 5 nodes");
        assertFalse(graph.getWorkflowId().isEmpty(), "workflow_id 应为自动生成的 UUID");
        assertEquals(TEST_APP_ID, graph.getApplicationId(), "application_id 应等于传入值");

        String json = JsonFormat.printer().includingDefaultValueFields().print(graph);

        System.out.println("================ WorkflowGraph IR (JSON) ================");
        System.out.println(json);
        System.out.println("==========================================================");
    }

    /**
     * 包含 Python 函数的混合管道：source → pythonMap → sink
     */
    @Test
    void mixedPipeline_withPythonFunction_printsToConsole() throws Exception {
        Workflow wf = Workflow.create();

        wf.source(ConstantSource.of("input1", "input2"))
                .map(Function.pythonMap("transform.py", "to_upper", String.class, "/opt/python-src"))
                .sink(PrintSink.of());

        WorkflowGraph graph = wf.toProtoGraph(TEST_APP_ID);

        assertNotNull(graph);
        assertEquals(3, graph.getNodesCount(), "1 Source + 1 Operator + 1 Sink");
        assertEquals(2, graph.getEdgesCount());
        assertEquals(TEST_APP_ID, graph.getApplicationId());

        String json = JsonFormat.printer().includingDefaultValueFields().print(graph);

        System.out.println("============ Mixed Pipeline IR (JSON) ============");
        System.out.println(json);
        System.out.println("==================================================");
    }

    /**
     * 未设置环境变量时调用无参 toProtoGraph() 应抛出异常。
     */
    @Test
    void toProtoGraph_withoutEnvVar_throwsException() {
        Workflow wf = Workflow.create();
        wf.source(ConstantSource.of("a")).sink(PrintSink.of());

        if (System.getenv("IARNET_APP_ID") == null) {
            assertThrows(IllegalStateException.class, wf::toProtoGraph,
                    "环境变量未设置时应抛出 IllegalStateException");
        }
    }
}
