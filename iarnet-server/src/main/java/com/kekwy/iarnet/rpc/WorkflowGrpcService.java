package com.kekwy.iarnet.rpc;

import com.kekwy.iarnet.execution.ExecutionFacade;
import com.kekwy.iarnet.proto.workflow.*;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * gRPC 服务端实现：接收 DSL 进程提交的工作流图并管理其生命周期。
 * 提交工作流委托给 {@link ExecutionFacade#submit}，返回的 token 作为 submission_id 供客户端后续 execute 使用。
 */
@Component
public class WorkflowGrpcService extends WorkflowServiceGrpc.WorkflowServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(WorkflowGrpcService.class);

    private final ExecutionFacade executionFacade;

    public WorkflowGrpcService(ExecutionFacade executionFacade) {
        this.executionFacade = executionFacade;
    }

    @Override
    public void submitWorkflow(SubmitWorkflowRequest request,
                               StreamObserver<SubmitWorkflowResponse> responseObserver) {
        log.info("收到工作流提交请求: workflowId={}", request.getGraph().getWorkflowId());

        try {
            Path artifactDir = Files.createTempDirectory("iarnet-artifact-");
            Path externalSourceDir = Files.createTempDirectory("iarnet-external-");
            String token = executionFacade.submit(request.getGraph(), artifactDir, externalSourceDir);

            SubmitWorkflowResponse response = SubmitWorkflowResponse.newBuilder()
                    .setSubmissionId(token)
                    .setStatus(SubmissionStatus.ACCEPTED)
                    .setMessage("工作流已接收，等待调度；submission_id 即 token，供 execute 使用")
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("工作流提交失败: workflowId={}", request.getGraph().getWorkflowId(), e);

            SubmitWorkflowResponse response = SubmitWorkflowResponse.newBuilder()
                    .setSubmissionId("")
                    .setStatus(SubmissionStatus.REJECTED)
                    .setMessage("提交失败: " + e.getMessage())
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }

    @Override
    public void getWorkflowStatus(GetWorkflowStatusRequest request,
                                  StreamObserver<GetWorkflowStatusResponse> responseObserver) {
        log.info("查询工作流状态: submissionId={}", request.getSubmissionId());

        // TODO: 从存储中查询实际状态

        GetWorkflowStatusResponse response = GetWorkflowStatusResponse.newBuilder()
                .setSubmissionId(request.getSubmissionId())
                .setStatus(WorkflowStatus.PENDING)
                .setMessage("工作流正在等待调度")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void cancelWorkflow(CancelWorkflowRequest request,
                               StreamObserver<CancelWorkflowResponse> responseObserver) {
        log.info("取消工作流: submissionId={}", request.getSubmissionId());

        // TODO: 执行实际的取消逻辑

        CancelWorkflowResponse response = CancelWorkflowResponse.newBuilder()
                .setSuccess(true)
                .setMessage("取消请求已受理")
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
