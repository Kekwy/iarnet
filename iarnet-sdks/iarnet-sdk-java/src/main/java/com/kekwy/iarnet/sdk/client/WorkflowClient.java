package com.kekwy.iarnet.sdk.client;

import com.kekwy.iarnet.proto.api.SubmissionStatus;
import com.kekwy.iarnet.proto.api.SubmitWorkflowRequest;
import com.kekwy.iarnet.proto.api.SubmitWorkflowResponse;
import com.kekwy.iarnet.proto.api.WorkflowServiceGrpc;
import com.kekwy.iarnet.proto.workflow.WorkflowGraph;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;

import java.util.concurrent.TimeUnit;

/**
 * 底层 gRPC 客户端工具类，直接操作 {@link WorkflowGraph} 进行提交。
 * <p>
 * 一般情况下应使用 {@link com.kekwy.iarnet.sdk.Workflow#execute()} 提交工作流，
 * 本类适用于需要精细控制提交参数的场景。
 */
public final class WorkflowClient {

    private WorkflowClient() {
    }

    /**
     * 将 WorkflowGraph 提交到指定地址的 control-plane。
     */
    public static void submit(WorkflowGraph graph, String host, int port) {
        ManagedChannel channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext()
                .build();

        try {
            WorkflowServiceGrpc.WorkflowServiceBlockingStub stub =
                    WorkflowServiceGrpc.newBlockingStub(channel);

            SubmitWorkflowRequest request = SubmitWorkflowRequest.newBuilder()
                    .setGraph(graph)
                    .build();

            SubmitWorkflowResponse response = stub.submitWorkflow(request);

            if (response.getStatus() == SubmissionStatus.ACCEPTED) {
                System.out.println("[WorkflowClient] 提交成功: submissionId=" + response.getSubmissionId()
                        + ", message=" + response.getMessage());
            } else {
                throw new RuntimeException(
                        "工作流提交被拒绝: " + response.getMessage());
            }
        } catch (StatusRuntimeException e) {
            throw new RuntimeException(
                    "gRPC 调用失败: " + e.getStatus(), e);
        } finally {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                channel.shutdownNow();
            }
        }
    }
}
