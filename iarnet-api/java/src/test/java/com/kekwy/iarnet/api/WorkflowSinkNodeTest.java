package com.kekwy.iarnet.api;

import com.kekwy.iarnet.api.graph.Edge;
import com.kekwy.iarnet.api.graph.Node;
import com.kekwy.iarnet.api.graph.NodeKind;
import com.kekwy.iarnet.api.graph.SinkNode;
import com.kekwy.iarnet.api.graph.SinkNode.SinkKind;
import com.kekwy.iarnet.api.sink.PrintSink;
import com.kekwy.iarnet.api.source.ConstantSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkflowSinkNodeTest {

    private Workflow wf;

    @BeforeEach
    void setUp() {
        wf = Workflow.create();
    }

    private SinkNode findSinkNode() {
        return wf.getNodes().stream()
                .filter(n -> n.getKind() == NodeKind.SINK)
                .map(SinkNode.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("No SinkNode found"));
    }

    // -------- PrintSink 基础 --------

    /** PrintSink 应生成 SinkKind.PRINT 类型的 SinkNode */
    @Test
    void printSink_createsSinkNodeWithPrintKind() {
        wf.source(ConstantSource.of("hello"))
                .sink(PrintSink.of());

        SinkNode sinkNode = findSinkNode();
        assertEquals(SinkKind.PRINT, sinkNode.getSinkKind());
        assertEquals(NodeKind.SINK, sinkNode.getKind());
        assertNotNull(sinkNode.getId());
    }

    /** SinkNode 的 outputType 应继承自上游节点 */
    @Test
    void printSink_inheritsOutputTypeFromUpstream() {
        wf.source(ConstantSource.of("hello", "world"))
                .sink(PrintSink.of());

        SinkNode sinkNode = findSinkNode();
        assertNotNull(sinkNode.getOutputType());
        assertEquals(TypeKind.STRING, sinkNode.getOutputType().getKind());
    }

    /** SinkNode 应注册到 Workflow 的节点列表中 */
    @Test
    void printSink_registeredInWorkflowNodes() {
        wf.source(ConstantSource.of("a"))
                .sink(PrintSink.of());

        List<Node> allNodes = wf.getNodes();
        assertEquals(2, allNodes.size(), "1 SourceNode + 1 SinkNode");
        assertInstanceOf(SinkNode.class, allNodes.get(1));
    }

    // -------- edges 连接 --------

    /** SourceNode → SinkNode 的 edge 正确 */
    @Test
    void printSink_edgeFromSourceToSink() {
        wf.source(ConstantSource.of("a"))
                .sink(PrintSink.of());

        Node sourceNode = wf.getNodes().get(0);
        SinkNode sinkNode = findSinkNode();

        List<Edge> allEdges = wf.getEdges();
        assertEquals(1, allEdges.size());
        assertEquals(Edge.of(sourceNode.getId(), sinkNode.getId()), allEdges.get(0));
    }

    /** OperatorNode → SinkNode 的 edge 正确 */
    @Test
    void printSink_edgeFromOperatorToSink() {
        wf.source(ConstantSource.of("hello"))
                .map((String s) -> s.toUpperCase())
                .sink(PrintSink.of());

        List<Node> allNodes = wf.getNodes();
        assertEquals(3, allNodes.size(), "1 Source + 1 Operator + 1 Sink");

        Node operatorNode = allNodes.get(1);
        SinkNode sinkNode = findSinkNode();

        List<Edge> allEdges = wf.getEdges();
        assertEquals(2, allEdges.size());
        assertEquals(Edge.of(operatorNode.getId(), sinkNode.getId()), allEdges.get(1));
    }

    // -------- 经过 map 后类型传递 --------

    /** source(String) → map(→Integer) → sink，SinkNode 的 outputType 应为 INT32 */
    @Test
    void printSink_afterMap_inheritsMapOutputType() {
        wf.source(ConstantSource.of("hello"))
                .map((String s) -> s.length())
                .sink(PrintSink.of());

        SinkNode sinkNode = findSinkNode();
        assertNotNull(sinkNode.getOutputType());
        assertEquals(TypeKind.INT32, sinkNode.getOutputType().getKind());
    }

    // -------- 完整管道 source → map → filter → sink --------

    /** 验证完整管道的节点数量、edges 数量和 SinkNode 的类型 */
    @Test
    void fullPipeline_sourceMapFilterSink_allNodesAndEdges() {
        wf.source(ConstantSource.of("hello", "hi", "hey"))
                .map((String s) -> s.length())
                .filter((Integer n) -> n > 2)
                .sink(PrintSink.of());

        List<Node> allNodes = wf.getNodes();
        assertEquals(4, allNodes.size(), "1 Source + 1 Map + 1 Filter + 1 Sink");

        List<Edge> allEdges = wf.getEdges();
        assertEquals(3, allEdges.size(), "Source→Map, Map→Filter, Filter→Sink");

        SinkNode sinkNode = findSinkNode();
        assertEquals(SinkKind.PRINT, sinkNode.getSinkKind());
        assertNotNull(sinkNode.getOutputType());
        assertEquals(TypeKind.INT32, sinkNode.getOutputType().getKind());
    }
}
