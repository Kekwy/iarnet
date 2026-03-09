package com.kekwy.iarnet.sdk;

import com.google.protobuf.util.JsonFormat;
import com.kekwy.iarnet.proto.workflow.WorkflowGraph;
import com.kekwy.iarnet.sdk.function.Function;
import com.kekwy.iarnet.sdk.sink.PrintSink;
import com.kekwy.iarnet.sdk.source.ConstantSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证完整的 DSL 管道能正确转换为 Protobuf WorkflowGraph IR 并输出到控制台。
 *
 * <p>测试通过 {@code buildGraph(applicationId)} 显式传入 applicationId。
 * 实际运行时 applicationId 从环境变量 {@code IARNET_APP_ID} 读取。
 */
class WorkflowToProtoTest {

    private static final String TEST_APP_ID = "test-app-001";

    @Test
    void fullPipeline_buildGraph_printsToConsole() throws Exception {
        Workflow wf = Workflow.create();

        wf.source(ConstantSource.of("hello", "hi", "hey", "world"))
                .map((String s) -> s.length())
                .filter((Integer n) -> n > 2)
                .map((Integer n) -> n * 1.5)
                .sink(PrintSink.of());

        WorkflowGraph graph = wf.buildGraph(TEST_APP_ID);

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

    @Test
    void mixedPipeline_withPythonFunction_printsToConsole() throws Exception {
        Workflow wf = Workflow.create();

        wf.source(ConstantSource.of("input1", "input2"))
                .map(Function.pythonMap("transform.py", "to_upper", String.class, "/opt/python-src"))
                .sink(PrintSink.of());

        WorkflowGraph graph = wf.buildGraph(TEST_APP_ID);

        assertNotNull(graph);
        assertEquals(3, graph.getNodesCount(), "1 Source + 1 Operator + 1 Sink");
        assertEquals(2, graph.getEdgesCount());
        assertEquals(TEST_APP_ID, graph.getApplicationId());

        String json = JsonFormat.printer().includingDefaultValueFields().print(graph);

        System.out.println("============ Mixed Pipeline IR (JSON) ============");
        System.out.println(json);
        System.out.println("==================================================");
    }

    @Test
    void buildGraph_withoutEnvVar_throwsException() {
        Workflow wf = Workflow.create();
        wf.source(ConstantSource.of("a")).sink(PrintSink.of());

        if (System.getenv("IARNET_APP_ID") == null) {
            assertThrows(IllegalStateException.class, wf::buildGraph,
                    "环境变量未设置时应抛出 IllegalStateException");
        }
    }
}
