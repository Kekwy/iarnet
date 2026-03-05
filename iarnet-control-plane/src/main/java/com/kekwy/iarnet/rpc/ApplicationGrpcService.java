package com.kekwy.iarnet.rpc;

import com.kekwy.iarnet.application.ApplicationFacade;
import com.kekwy.iarnet.proto.api.ApplicationServiceGrpc;
import com.kekwy.iarnet.proto.api.SubmitJarRequest;
import com.kekwy.iarnet.proto.api.SubmitJarResponse;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
}
