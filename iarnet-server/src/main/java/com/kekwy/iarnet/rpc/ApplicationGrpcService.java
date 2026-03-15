package com.kekwy.iarnet.rpc;

import com.kekwy.iarnet.application.ApplicationFacade;
import com.kekwy.iarnet.execution.ExecutionFacade;
import com.kekwy.iarnet.proto.application.ApplicationServiceGrpc;
import com.kekwy.iarnet.proto.application.InputEntry;
import com.kekwy.iarnet.proto.application.InputGroup;
import com.kekwy.iarnet.proto.application.SubmitJarRequest;
import com.kekwy.iarnet.proto.application.SubmitJarResponse;
import com.kekwy.iarnet.proto.application.SubmitJarWithInputRequest;
import com.kekwy.iarnet.proto.application.SubmitJarWithInputResponse;
import com.kekwy.iarnet.proto.common.Value;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class ApplicationGrpcService extends ApplicationServiceGrpc.ApplicationServiceImplBase {

    private ApplicationFacade applicationFacade;
    private ExecutionFacade executionFacade;

    @Autowired
    public void setApplicationFacade(ApplicationFacade applicationFacade) {
        this.applicationFacade = applicationFacade;
    }

    @Autowired
    public void setExecutionFacade(ExecutionFacade executionFacade) {
        this.executionFacade = executionFacade;
    }

    @Override
    public void submitJar(SubmitJarRequest request, StreamObserver<SubmitJarResponse> responseObserver) {
        log.info("submitJar, size={} bytes", request.getContent().size());
        byte[] content = request.getContent().toByteArray();
        applicationFacade.launchApplicationWithJar(content);

        responseObserver.onNext(SubmitJarResponse.newBuilder().setMsg("提交成功").build());
        responseObserver.onCompleted();
    }

    @Override
    public void submitJarWithInput(SubmitJarWithInputRequest request,
                                  StreamObserver<SubmitJarWithInputResponse> responseObserver) {
        int groupCount = request.getInputGroupsCount();
        int singleInputCount = request.getInputsCount();
        log.info("submitJarWithInput, size={} bytes, input_groups={}, inputs(count)={}",
                request.getContent().size(), groupCount, singleInputCount);
        byte[] content = request.getContent().toByteArray();

        var workspace = applicationFacade.prepareJarWorkspace(content);
        var registration = executionFacade.register(workspace.getArtifactDir(), workspace.getSourceDir());
        String workflowId = registration.workflowId();
        String token = registration.token();

        applicationFacade.launchPreparedJar(workspace, workflowId, token);

        if (groupCount > 0) {
            for (int i = 0; i < groupCount; i++) {
                InputGroup group = request.getInputGroups(i);
                Map<String, Value> inputs = new LinkedHashMap<>();
                for (InputEntry e : group.getEntriesList()) {
                    inputs.put(e.getKey(), e.getValue());
                }
                final int groupIndex = i;
                CompletableFuture.runAsync(() -> {
                    try {
                        String executionId = executionFacade.execute(workflowId, token, inputs);
                        log.info("execute 已提交: workflowId={}, groupIndex={}, executionId={}", workflowId, groupIndex, executionId);
                    } catch (Exception e) {
                        log.error("execute 失败: workflowId={}, groupIndex={}", workflowId, groupIndex, e);
                    }
                });
            }
            responseObserver.onNext(SubmitJarWithInputResponse.newBuilder()
                    .setMsg("提交成功，工作流已预注册，共 " + groupCount + " 组输入将随工作流就绪后执行")
                    .build());
        } else {
            Map<String, Value> inputs = new LinkedHashMap<>();
            for (InputEntry e : request.getInputsList()) {
                inputs.put(e.getKey(), e.getValue());
            }
            CompletableFuture.runAsync(() -> {
                try {
                    String executionId = executionFacade.execute(workflowId, token, inputs);
                    log.info("execute 已提交/已处理: workflowId={}, executionId={}", workflowId, executionId);
                } catch (Exception e) {
                    log.error("execute 失败: workflowId={}", workflowId, e);
                }
            });
            responseObserver.onNext(SubmitJarWithInputResponse.newBuilder()
                    .setMsg("提交成功，工作流已预注册，输入将随工作流就绪后执行")
                    .build());
        }
        responseObserver.onCompleted();
    }
}
