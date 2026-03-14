package com.kekwy.iarnet.rpc;

import com.kekwy.iarnet.application.ApplicationFacade;
import com.kekwy.iarnet.proto.application.ApplicationServiceGrpc;
import com.kekwy.iarnet.proto.application.InputEntry;
import com.kekwy.iarnet.proto.application.SubmitJarRequest;
import com.kekwy.iarnet.proto.application.SubmitJarResponse;
import com.kekwy.iarnet.proto.application.SubmitJarWithInputRequest;
import com.kekwy.iarnet.proto.application.SubmitJarWithInputResponse;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class ApplicationGrpcService extends ApplicationServiceGrpc.ApplicationServiceImplBase {

    private ApplicationFacade  applicationFacade;

    @Autowired
    public void setApplicationFacade(ApplicationFacade applicationFacade) {
        this.applicationFacade = applicationFacade;
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
        log.info("submitJarWithInput, size={} bytes, inputs count={}",
                request.getContent().size(), request.getInputsCount());
        byte[] content = request.getContent().toByteArray();
        Map<String, String> inputs = new LinkedHashMap<>();
        for (InputEntry e : request.getInputsList()) {
            inputs.put(e.getKey(), e.getValue());
        }
        applicationFacade.launchApplicationWithJar(content, inputs);

        responseObserver.onNext(SubmitJarWithInputResponse.newBuilder().setMsg("提交成功，输入已写入工作空间 input.json").build());
        responseObserver.onCompleted();
    }
}
