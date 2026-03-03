package com.kekwy.iarnet.api;

import com.google.protobuf.util.JsonFormat;
import com.kekwy.iarnet.api.sink.PrintSink;
import com.kekwy.iarnet.api.source.ConstantSource;
import com.kekwy.iarnet.proto.api.*;
import com.kekwy.iarnet.proto.ir.WorkflowGraph;
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
 *
 * <p>测试在 JVM 内启动一个轻量 gRPC Server（模拟 control-plane），
 * 然后使用 DSL 客户端连接并提交 WorkflowGraph。
 */
class WorkflowSubmitTest {

    private Server server;
    private int port;

    /** 服务端收到的请求，供断言使用 */
    private final AtomicReference<SubmitWorkflowRequest> receivedRequest = new AtomicReference<>();
    private final CountDownLatch requestLatch = new CountDownLatch(1);

    @BeforeEach
    void startServer() throws Exception {
        // 在随机端口启动一个模拟的 WorkflowService gRPC Server
        server = ServerBuilder.forPort(0)
                .addService(new WorkflowServiceGrpc.WorkflowServiceImplBase() {
                    @Override
                    public void submitWorkflow(SubmitWorkflowRequest request,
                                               StreamObserver<SubmitWorkflowResponse> responseObserver) {
                        // 记录请求
                        receivedRequest.set(request);
                        requestLatch.countDown();

                        System.out.println("[MockServer] 收到工作流提交: workflowId="
                                + request.getGraph().getWorkflowId()
                                + ", nodes=" + request.getGraph().getNodesCount()
                                + ", edges=" + request.getGraph().getEdgesCount());

                        // 返回成功响应
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

    /**
     * 完整链路测试：DSL 构建 → toProtoGraph → gRPC 提交 → 服务端接收验证。
     */
    @Test
    void dslWorkflow_submitViaGrpc_serverReceivesGraph() throws Exception {
        // ---- 1. DSL 侧：构建工作流并生成 proto IR ----
        Workflow wf = Workflow.create();

        wf.source(ConstantSource.of("hello", "world", "test"))
                .map(String::length)
                .filter((Integer n) -> n > 3)
                .sink(PrintSink.of());

        WorkflowGraph graph = wf.toProtoGraph("test-app-integration");

        System.out.println("[Test] 构建的 WorkflowGraph:");
        System.out.println(JsonFormat.printer().includingDefaultValueFields().print(graph));

        // ---- 2. 模拟 DSL 客户端通过 gRPC 提交 ----
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

            // ---- 3. 验证客户端侧收到正确响应 ----
            System.out.println("[Test] 提交响应: submissionId=" + response.getSubmissionId()
                    + ", status=" + response.getStatus()
                    + ", message=" + response.getMessage());

            assertEquals(SubmissionStatus.ACCEPTED, response.getStatus());
            assertFalse(response.getSubmissionId().isEmpty());
        } finally {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }

        // ---- 4. 验证服务端侧确实收到了完整的 graph ----
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
