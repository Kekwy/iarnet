package com.kekwy.iarnet.sdk;

import com.google.protobuf.util.JsonFormat;
import com.kekwy.iarnet.proto.api.*;
import com.kekwy.iarnet.proto.workflow.WorkflowGraph;
import com.kekwy.iarnet.sdk.sink.PrintSink;
import com.kekwy.iarnet.sdk.source.ConstantSource;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 集成测试：验证 DSL 构建的工作流可以通过 gRPC 成功提交到后端。
 */
class WorkflowSubmitTest {

    private Server server;
    private int port;

    private final AtomicReference<SubmitWorkflowRequest> receivedRequest = new AtomicReference<>();
    private final CountDownLatch requestLatch = new CountDownLatch(1);

    @BeforeEach
    void startServer() throws Exception {
        server = ServerBuilder.forPort(0)
                .addService(new WorkflowServiceGrpc.WorkflowServiceImplBase() {
                    @Override
                    public void submitWorkflow(SubmitWorkflowRequest request,
                                               StreamObserver<SubmitWorkflowResponse> responseObserver) {
                        receivedRequest.set(request);
                        requestLatch.countDown();

                        System.out.println("[MockServer] 收到工作流提交: workflowId="
                                + request.getGraph().getWorkflowId()
                                + ", nodes=" + request.getGraph().getNodesCount()
                                + ", edges=" + request.getGraph().getEdgesCount());

                        responseObserver.onNext(SubmitWorkflowResponse.newBuilder()
                                .setSubmissionId(UUID.randomUUID().toString())
                                .setStatus(SubmissionStatus.ACCEPTED)
                                .setMessage("测试服务端已接收")
                                .build());
                        responseObserver.onCompleted();
                    }
                })
                .build()
                .start();

        port = server.getPort();
        System.out.println("[Test] gRPC Mock Server 启动成功, port=" + port);
    }

    @AfterEach
    void stopServer() throws Exception {
        if (server != null) {
            server.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void dslWorkflow_submitViaGrpc_serverReceivesGraph() throws Exception {
        Workflow wf = Workflow.create();

        wf.source(ConstantSource.of("hello", "world", "test"))
                .map(String::length)
                .filter((Integer n) -> n > 3)
                .sink(PrintSink.of());

        WorkflowGraph graph = wf.buildGraph("test-app-integration");

        System.out.println("[Test] 构建的 WorkflowGraph:");
        System.out.println(JsonFormat.printer().includingDefaultValueFields().print(graph));

        ManagedChannel channel = ManagedChannelBuilder
                .forAddress("localhost", port)
                .usePlaintext()
                .build();

        try {
            WorkflowServiceGrpc.WorkflowServiceBlockingStub stub =
                    WorkflowServiceGrpc.newBlockingStub(channel);

            SubmitWorkflowRequest request = SubmitWorkflowRequest.newBuilder()
                    .setGraph(graph)
                    .build();

            SubmitWorkflowResponse response = stub.submitWorkflow(request);

            System.out.println("[Test] 提交响应: submissionId=" + response.getSubmissionId()
                    + ", status=" + response.getStatus()
                    + ", message=" + response.getMessage());

            assertEquals(SubmissionStatus.ACCEPTED, response.getStatus());
            assertFalse(response.getSubmissionId().isEmpty());
        } finally {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }

        assertTrue(requestLatch.await(5, TimeUnit.SECONDS), "服务端应在 5 秒内收到请求");

        SubmitWorkflowRequest received = receivedRequest.get();
        assertNotNull(received);

        WorkflowGraph receivedGraph = received.getGraph();
        assertEquals(graph.getWorkflowId(), receivedGraph.getWorkflowId());
        assertEquals("test-app-integration", receivedGraph.getApplicationId());
        assertEquals(4, receivedGraph.getNodesCount(), "1 Source + 1 Map + 1 Filter + 1 Sink");
        assertEquals(3, receivedGraph.getEdgesCount(), "3 edges linking 4 nodes");

        System.out.println("[Test] 服务端收到的 graph 验证通过 ✓");
    }
}
