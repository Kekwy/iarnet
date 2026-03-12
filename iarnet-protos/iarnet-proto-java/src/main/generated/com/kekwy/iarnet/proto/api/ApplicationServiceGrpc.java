package com.kekwy.iarnet.proto.api;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.62.2)",
    comments = "Source: iarnet/application/application_service.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class ApplicationServiceGrpc {

  private ApplicationServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "iarnet.api.ApplicationService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.api.SubmitJarRequest,
      com.kekwy.iarnet.proto.api.SubmitJarResponse> getSubmitJarMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "submitJar",
      requestType = com.kekwy.iarnet.proto.api.SubmitJarRequest.class,
      responseType = com.kekwy.iarnet.proto.api.SubmitJarResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.api.SubmitJarRequest,
      com.kekwy.iarnet.proto.api.SubmitJarResponse> getSubmitJarMethod() {
    io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.api.SubmitJarRequest, com.kekwy.iarnet.proto.api.SubmitJarResponse> getSubmitJarMethod;
    if ((getSubmitJarMethod = ApplicationServiceGrpc.getSubmitJarMethod) == null) {
      synchronized (ApplicationServiceGrpc.class) {
        if ((getSubmitJarMethod = ApplicationServiceGrpc.getSubmitJarMethod) == null) {
          ApplicationServiceGrpc.getSubmitJarMethod = getSubmitJarMethod =
              io.grpc.MethodDescriptor.<com.kekwy.iarnet.proto.api.SubmitJarRequest, com.kekwy.iarnet.proto.api.SubmitJarResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "submitJar"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.api.SubmitJarRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.api.SubmitJarResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ApplicationServiceMethodDescriptorSupplier("submitJar"))
              .build();
        }
      }
    }
    return getSubmitJarMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ApplicationServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ApplicationServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ApplicationServiceStub>() {
        @java.lang.Override
        public ApplicationServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ApplicationServiceStub(channel, callOptions);
        }
      };
    return ApplicationServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ApplicationServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ApplicationServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ApplicationServiceBlockingStub>() {
        @java.lang.Override
        public ApplicationServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ApplicationServiceBlockingStub(channel, callOptions);
        }
      };
    return ApplicationServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ApplicationServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ApplicationServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ApplicationServiceFutureStub>() {
        @java.lang.Override
        public ApplicationServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ApplicationServiceFutureStub(channel, callOptions);
        }
      };
    return ApplicationServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void submitJar(com.kekwy.iarnet.proto.api.SubmitJarRequest request,
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.api.SubmitJarResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSubmitJarMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service ApplicationService.
   */
  public static abstract class ApplicationServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return ApplicationServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service ApplicationService.
   */
  public static final class ApplicationServiceStub
      extends io.grpc.stub.AbstractAsyncStub<ApplicationServiceStub> {
    private ApplicationServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ApplicationServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ApplicationServiceStub(channel, callOptions);
    }

    /**
     */
    public void submitJar(com.kekwy.iarnet.proto.api.SubmitJarRequest request,
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.api.SubmitJarResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSubmitJarMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service ApplicationService.
   */
  public static final class ApplicationServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<ApplicationServiceBlockingStub> {
    private ApplicationServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ApplicationServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ApplicationServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.kekwy.iarnet.proto.api.SubmitJarResponse submitJar(com.kekwy.iarnet.proto.api.SubmitJarRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSubmitJarMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service ApplicationService.
   */
  public static final class ApplicationServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<ApplicationServiceFutureStub> {
    private ApplicationServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ApplicationServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ApplicationServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.kekwy.iarnet.proto.api.SubmitJarResponse> submitJar(
        com.kekwy.iarnet.proto.api.SubmitJarRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSubmitJarMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_SUBMIT_JAR = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_SUBMIT_JAR:
          serviceImpl.submitJar((com.kekwy.iarnet.proto.api.SubmitJarRequest) request,
              (io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.api.SubmitJarResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getSubmitJarMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.kekwy.iarnet.proto.api.SubmitJarRequest,
              com.kekwy.iarnet.proto.api.SubmitJarResponse>(
                service, METHODID_SUBMIT_JAR)))
        .build();
  }

  private static abstract class ApplicationServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ApplicationServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.kekwy.iarnet.proto.api.ApplicationServiceOuterClass.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("ApplicationService");
    }
  }

  private static final class ApplicationServiceFileDescriptorSupplier
      extends ApplicationServiceBaseDescriptorSupplier {
    ApplicationServiceFileDescriptorSupplier() {}
  }

  private static final class ApplicationServiceMethodDescriptorSupplier
      extends ApplicationServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    ApplicationServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (ApplicationServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ApplicationServiceFileDescriptorSupplier())
              .addMethod(getSubmitJarMethod())
              .build();
        }
      }
    }
    return result;
  }
}
