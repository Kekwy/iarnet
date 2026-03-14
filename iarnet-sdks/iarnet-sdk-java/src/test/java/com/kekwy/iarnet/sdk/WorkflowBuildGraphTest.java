package com.kekwy.iarnet.sdk;

import com.google.protobuf.util.JsonFormat;
import com.kekwy.iarnet.proto.workflow.WorkflowGraph;
import com.kekwy.iarnet.sdk.dsl.Tasks;
import com.kekwy.iarnet.sdk.exception.IarnetValidationException;
import com.kekwy.iarnet.sdk.function.TaskFunction;
import com.kekwy.iarnet.sdk.type.OptionalValue;
import com.kekwy.iarnet.sdk.type.TypeToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Workflow buildGraph 单元测试。
 * <p>
 * 通过系统属性 {@code iarnet.test.print.graph=true} 或环境变量 {@code IARNET_TEST_PRINT_GRAPH=true}
 * 可将构建的 WorkflowGraph 以 JSON 格式输出到控制台，便于调试。
 */
class WorkflowBuildGraphTest {

    private static final String APP_ID = "test-app-001";

    /**
     * 构建 graph 并视配置决定是否输出到控制台。
     */
    private WorkflowGraph buildAndMaybePrint(Workflow workflow) {
        WorkflowGraph graph = workflow.buildGraph(APP_ID);
        if (shouldPrintGraph()) {
            try {
                String json = JsonFormat.printer()
                        .includingDefaultValueFields()
                        .print(graph);
                System.out.println("========== WorkflowGraph ==========");
                System.out.println(json);
                System.out.println("==================================");
            } catch (Exception e) {
                System.err.println("输出 WorkflowGraph 失败: " + e.getMessage());
            }
        }
        return graph;
    }

    /**
     * 是否将构建的 WorkflowGraph 以 JSON 格式输出到控制台。
     * <p>
     * 配置方式（任一即可）：
     * <ul>
     *   <li>系统属性：{@code -Diarnet.test.print.graph=true}（Maven: {@code mvn test -Diarnet.test.print.graph=true}，IDEA: Run Configuration → VM options）</li>
     *   <li>环境变量：{@code IARNET_TEST_PRINT_GRAPH=true}</li>
     * </ul>
     */
    private static boolean shouldPrintGraph() {
        return "true".equalsIgnoreCase(System.getProperty("iarnet.test.print.graph"))
                || "true".equalsIgnoreCase(System.getenv("IARNET_TEST_PRINT_GRAPH"));
    }

    @Nested
    @DisplayName("简单线性流")
    class LinearFlow {

        @Test
        @DisplayName("input 参数 + task + output 两节点")
        void inputTaskOutput() {
            Workflow w = Workflow.create("linear");
            w.input("src", new TypeToken<Integer>() {})
                    .then("double", (Integer x) -> x * 2)
                    .then("sink", (Integer x) -> {
                    });

            WorkflowGraph graph = buildAndMaybePrint(w);

            assertEquals("linear", graph.getName());
            assertEquals(APP_ID, graph.getApplicationId());
            assertFalse(graph.getWorkflowId().isBlank());
            assertEquals(2, graph.getNodesCount());
            assertEquals(1, graph.getEdgesCount());
            assertEquals(1, graph.getInputsCount());
            assertEquals("src", graph.getInputs(0).getName());

            assertTrue(graph.getNodesList().stream()
                    .anyMatch(n -> "double".equals(n.getName())));
            assertTrue(graph.getNodesList().stream()
                    .anyMatch(n -> "sink".equals(n.getName())));
            assertTrue(graph.getNodesList().stream()
                    .anyMatch(n -> "src".equals(n.getInputParam())));
        }

        @Test
        @DisplayName("单 input 参数 -> output")
        void inputToOutput() {
            Workflow w = Workflow.create("simple");
            w.input("src", new TypeToken<String>() {}).then("print", (String s) -> {
            });

            WorkflowGraph graph = buildAndMaybePrint(w);

            assertEquals(1, graph.getNodesCount());
            assertEquals(0, graph.getEdgesCount());
            assertEquals(1, graph.getInputsCount());
            assertEquals("src", graph.getInputs(0).getName());
        }

        @Test
        @DisplayName("多段 task 链")
        void multiStageChain() {
            Workflow w = Workflow.create("chain");
            w.input("src", new TypeToken<Integer>() {})
                    .then("add1", (Integer x) -> x + 1)
                    .then("mul2", (Integer x) -> x * 2)
                    .then("sink", (Integer x) -> {
                    });

            WorkflowGraph graph = buildAndMaybePrint(w);

            assertEquals(3, graph.getNodesCount());
            assertEquals(2, graph.getEdgesCount());
            assertEquals(1, graph.getInputsCount());
        }
    }

    @Nested
    @DisplayName("returns 类型提示")
    class ReturnsTypeHint {

        @Test
        @DisplayName("需要 returns 时的类型推断兜底")
        void returnsForTypeHint() {
            record CustomOut(String value) {
            }

            Workflow w = Workflow.create("with-returns");
            w.input("src", new TypeToken<String>() {})
                    .then("toCustom", (TaskFunction<String, CustomOut>) CustomOut::new)
                    .returns(new TypeToken<CustomOut>() {
                    })
                    .then("sink", (CustomOut o) -> {
                    });

            WorkflowGraph graph = buildAndMaybePrint(w);

            assertEquals(3, graph.getNodesCount());
        }
    }

    @Nested
    @DisplayName("Combine 汇合")
    class CombineFlow {

        @Test
        @DisplayName("双路 input 参数 combine 后 output")
        void twoInputsCombine() {
            Workflow w = Workflow.create("combine-demo");
            var flow1 = w.input("a", new TypeToken<Integer>() {}).then("a-task", (Integer x) -> x);
            var flow2 = w.input("b", new TypeToken<Integer>() {}).then("b-task", (Integer x) -> x);
            flow1.combine("merge", flow2, (OptionalValue<Integer> a, OptionalValue<Integer> b) ->
                            a.isPresent() ? a.get() : b.get())
                    .then("sink", (Integer x) -> {
                    });

            WorkflowGraph graph = buildAndMaybePrint(w);

            assertEquals(4, graph.getNodesCount()); // a-task, b-task, merge, sink
            assertEquals(3, graph.getEdgesCount()); // a-task->merge, b-task->merge, merge->sink
            assertEquals(2, graph.getInputsCount());

            // Combine 左路 input_port=0，右路 input_port=1；普通边 output_port=0
            String mergeNodeId = graph.getEdgesList().stream()
                    .filter(e -> e.getToNodeId().contains("merge"))
                    .map(e -> e.getToNodeId())
                    .findFirst().orElse(null);
            assertNotNull(mergeNodeId);
            var combineEdges = graph.getEdgesList().stream()
                    .filter(e -> e.getToNodeId().equals(mergeNodeId))
                    .toList();
            assertEquals(2, combineEdges.size());
            var inputPorts = combineEdges.stream().map(e -> e.getInputPort()).sorted().toList();
            assertEquals(List.of(0, 1), inputPorts);
            combineEdges.forEach(e -> assertEquals(0, e.getOutputPort()));
        }
    }

    @Nested
    @DisplayName("条件分支")
    class ConditionalFlow {

        @Test
        @DisplayName("when().then() 条件分流")
        void whenThen() {
            Workflow w = Workflow.create("conditional");
            w.input("src", new TypeToken<Integer>() {})
                    .then("first", (Integer x) -> x)
                    .when((Integer x) -> x % 2 == 0)
                    .then("even-sink", (Integer x) -> {
                    });

            WorkflowGraph graph = buildAndMaybePrint(w);

            assertEquals(2, graph.getNodesCount());
            assertEquals(1, graph.getEdgesCount());
            var edge = graph.getEdges(0);
            assertTrue(edge.hasConditionFunction());
        }
    }

    @Nested
    @DisplayName("Python/Go 任务函数")
    class ScriptTaskFunction {

        @Test
        @DisplayName("Python task 构建正确的 FunctionDescriptor")
        void pythonTask() {
            Workflow w = Workflow.create("python-demo");
            w.input("src", new TypeToken<String>() {})
                    .then("py-transform", Tasks.pythonTask("transform", new TypeToken<String>() {
                    }))
                    .then("sink", (String s) -> {
                    });

            WorkflowGraph graph = buildAndMaybePrint(w);

            var pyNode = graph.getNodesList().stream()
                    .filter(n -> "py-transform".equals(n.getName()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("transform", pyNode.getFunction().getFunctionIdentifier());
        }

        @Test
        @DisplayName("Go task 构建正确的 FunctionDescriptor")
        void goTask() {
            Workflow w = Workflow.create("go-demo");
            w.input("src", new TypeToken<Integer>() {})
                    .then("go-process", Tasks.goTask("Process", new TypeToken<Integer>() {
                    }))
                    .then("sink", (Integer x) -> {
                    });

            WorkflowGraph graph = buildAndMaybePrint(w);

            var goNode = graph.getNodesList().stream()
                    .filter(n -> "go-process".equals(n.getName()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("Process", goNode.getFunction().getFunctionIdentifier());
        }
    }

    @Nested
    @DisplayName("ExecutionConfig")
    class ExecutionConfigTests {

        @Test
        @DisplayName("replicas 与 resource 写入 NodeConfig")
        void replicasAndResource() {
            Workflow w = Workflow.create("config-demo");
            var config = ExecutionConfig.of()
                    .replicas(3)
                    .resource(b -> b.cpu(2).memory("4Gi").gpu(1));

            w.input("src", new TypeToken<Integer>() {})
                    .then("task", (Integer x) -> x, config)
                    .then("sink", (Integer x) -> {
                    });

            WorkflowGraph graph = buildAndMaybePrint(w);

            var taskNode = graph.getNodesList().stream()
                    .filter(n -> "task".equals(n.getName()))
                    .findFirst()
                    .orElseThrow();
            assertEquals(3, taskNode.getNodeConfig().getReplicas());
            assertTrue(taskNode.getNodeConfig().hasResourceSpec());
        }
    }

    @Nested
    @DisplayName("异常场景")
    class ErrorCases {

        @Test
        @DisplayName("缺少 returns 且无法推断类型时抛出 IarnetValidationException")
        void missingReturnsThrows() {
            Workflow w = Workflow.create("need-returns");
            w.input("src", new TypeToken<Integer>() {})
                    .then("erased", (Integer x) -> (Object) x)  // 擦除为 Object
                    .then("sink", (Object o) -> {
                    });

            assertThrows(IarnetValidationException.class, () -> w.buildGraph(APP_ID));
        }
    }

    // 简化异常测试：不依赖反射修改 env，仅测试 buildGraph(String) 行为
    @Nested
    @DisplayName("buildGraph 基础")
    class BuildGraphBasics {

        @Test
        @DisplayName("buildGraph(String) 生成有效 workflowId")
        void buildGraphGeneratesWorkflowId() {
            Workflow w = Workflow.create("id-test");
            w.input("src", new TypeToken<Integer>() {}).then("sink", (Integer x) -> {
            });

            WorkflowGraph graph = w.buildGraph(APP_ID);

            assertFalse(graph.getWorkflowId().isBlank());
            assertTrue(graph.getWorkflowId().length() > 10);
        }

        @Test
        @DisplayName("多次 buildGraph 生成不同 workflowId")
        void eachBuildGeneratesNewId() {
            Workflow w = Workflow.create("multi-build");
            w.input("src", new TypeToken<Integer>() {}).then("sink", (Integer x) -> {
            });

            WorkflowGraph g1 = w.buildGraph(APP_ID);
            WorkflowGraph g2 = w.buildGraph(APP_ID);

            assertNotEquals(g1.getWorkflowId(), g2.getWorkflowId());
        }
    }
}
