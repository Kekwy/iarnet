package com.kekwy.iarnet.sdk;

import com.kekwy.iarnet.proto.common.TypeKind;
import com.kekwy.iarnet.proto.workflow.Edge;
import com.kekwy.iarnet.proto.workflow.NodeKind;
import com.kekwy.iarnet.proto.workflow.SinkKind;
import com.kekwy.iarnet.sdk.graph.Node;
import com.kekwy.iarnet.sdk.graph.SinkNode;
import com.kekwy.iarnet.sdk.sink.PrintSink;
import com.kekwy.iarnet.sdk.source.ConstantSource;
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

    @Test
    void printSink_createsSinkNodeWithPrintKind() {
        wf.source(ConstantSource.of("hello"))
                .sink(PrintSink.of());

        SinkNode sinkNode = findSinkNode();
        assertEquals(SinkKind.PRINT, sinkNode.getSinkKind());
        assertEquals(NodeKind.SINK, sinkNode.getKind());
        assertNotNull(sinkNode.getId());
    }

    @Test
    void printSink_inheritsOutputTypeFromUpstream() {
        wf.source(ConstantSource.of("hello", "world"))
                .sink(PrintSink.of());

        SinkNode sinkNode = findSinkNode();
        assertNotNull(sinkNode.getOutputType());
        assertEquals(TypeKind.TYPE_KIND_STRING, sinkNode.getOutputType().getKind());
    }

    @Test
    void printSink_registeredInWorkflowNodes() {
        wf.source(ConstantSource.of("a"))
                .sink(PrintSink.of());

        List<Node> allNodes = wf.getNodes();
        assertEquals(2, allNodes.size(), "1 SourceNode + 1 SinkNode");
        assertInstanceOf(SinkNode.class, allNodes.get(1));
    }

    @Test
    void printSink_edgeFromSourceToSink() {
        wf.source(ConstantSource.of("a"))
                .sink(PrintSink.of());

        Node sourceNode = wf.getNodes().get(0);
        SinkNode sinkNode = findSinkNode();

        List<Edge> allEdges = wf.getEdges();
        assertEquals(1, allEdges.size());
        assertEquals(edge(sourceNode.getId(), sinkNode.getId()), allEdges.get(0));
    }

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
        assertEquals(edge(operatorNode.getId(), sinkNode.getId()), allEdges.get(1));
    }

    @Test
    void printSink_afterMap_inheritsMapOutputType() {
        wf.source(ConstantSource.of("hello"))
                .map((String s) -> s.length())
                .sink(PrintSink.of());

        SinkNode sinkNode = findSinkNode();
        assertNotNull(sinkNode.getOutputType());
        assertEquals(TypeKind.TYPE_KIND_INT32, sinkNode.getOutputType().getKind());
    }

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
        assertEquals(TypeKind.TYPE_KIND_INT32, sinkNode.getOutputType().getKind());
    }

    private static Edge edge(String fromNodeId, String toNodeId) {
        return Edge.newBuilder()
                .setFromNodeId(fromNodeId)
                .setToNodeId(toNodeId)
                .build();
    }
}
