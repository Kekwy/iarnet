package com.kekwy.iarnet.proto.api;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.62.2)",
    comments = "Source: iarnet/api/workflow_service.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class WorkflowServiceGrpc {

  private WorkflowServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "iarnet.api.WorkflowService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.api.SubmitWorkflowRequest,
      com.kekwy.iarnet.proto.api.SubmitWorkflowResponse> getSubmitWorkflowMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SubmitWorkflow",
      requestType = com.kekwy.iarnet.proto.api.SubmitWorkflowRequest.class,
      responseType = com.kekwy.iarnet.proto.api.SubmitWorkflowResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.api.SubmitWorkflowRequest,
      com.kekwy.iarnet.proto.api.SubmitWorkflowResponse> getSubmitWorkflowMethod() {
    io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.api.SubmitWorkflowRequest, com.kekwy.iarnet.proto.api.SubmitWorkflowResponse> getSubmitWorkflowMethod;
    if ((getSubmitWorkflowMethod = WorkflowServiceGrpc.getSubmitWorkflowMethod) == null) {
      synchronized (WorkflowServiceGrpc.class) {
        if ((getSubmitWorkflowMethod = WorkflowServiceGrpc.getSubmitWorkflowMethod) == null) {
          WorkflowServiceGrpc.getSubmitWorkflowMethod = getSubmitWorkflowMethod =
              io.grpc.MethodDescriptor.<com.kekwy.iarnet.proto.api.SubmitWorkflowRequest, com.kekwy.iarnet.proto.api.SubmitWorkflowResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SubmitWorkflow"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.api.SubmitWorkflowRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.api.SubmitWorkflowResponse.getDefaultInstance()))
              .setSchemaDescriptor(new WorkflowServiceMethodDescriptorSupplier("SubmitWorkflow"))
              .build();
        }
      }
    }
    return getSubmitWorkflowMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.api.GetWorkflowStatusRequest,
      com.kekwy.iarnet.proto.api.GetWorkflowStatusResponse> getGetWorkflowStatusMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetWorkflowStatus",
      requestType = com.kekwy.iarnet.proto.api.GetWorkflowStatusRequest.class,
      responseType = com.kekwy.iarnet.proto.api.GetWorkflowStatusResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.api.GetWorkflowStatusRequest,
      com.kekwy.iarnet.proto.api.GetWorkflowStatusResponse> getGetWorkflowStatusMethod() {
    io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.api.GetWorkflowStatusRequest, com.kekwy.iarnet.proto.api.GetWorkflowStatusResponse> getGetWorkflowStatusMethod;
    if ((getGetWorkflowStatusMethod = WorkflowServiceGrpc.getGetWorkflowStatusMethod) == null) {
      synchronized (WorkflowServiceGrpc.class) {
        if ((getGetWorkflowStatusMethod = WorkflowServiceGrpc.getGetWorkflowStatusMethod) == null) {
          WorkflowServiceGrpc.getGetWorkflowStatusMethod = getGetWorkflowStatusMethod =
              io.grpc.MethodDescriptor.<com.kekwy.iarnet.proto.api.GetWorkflowStatusRequest, com.kekwy.iarnet.proto.api.GetWorkflowStatusResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetWorkflowStatus"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.api.GetWorkflowStatusRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.api.GetWorkflowStatusResponse.getDefaultInstance()))
              .setSchemaDescriptor(new WorkflowServiceMethodDescriptorSupplier("GetWorkflowStatus"))
              .build();
        }
      }
    }
    return getGetWorkflowStatusMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.api.CancelWorkflowRequest,
      com.kekwy.iarnet.proto.api.CancelWorkflowResponse> getCancelWorkflowMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CancelWorkflow",
      requestType = com.kekwy.iarnet.proto.api.CancelWorkflowRequest.class,
      responseType = com.kekwy.iarnet.proto.api.CancelWorkflowResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.api.CancelWorkflowRequest,
      com.kekwy.iarnet.proto.api.CancelWorkflowResponse> getCancelWorkflowMethod() {
    io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.api.CancelWorkflowRequest, com.kekwy.iarnet.proto.api.CancelWorkflowResponse> getCancelWorkflowMethod;
    if ((getCancelWorkflowMethod = WorkflowServiceGrpc.getCancelWorkflowMethod) == null) {
      synchronized (WorkflowServiceGrpc.class) {
        if ((getCancelWorkflowMethod = WorkflowServiceGrpc.getCancelWorkflowMethod) == null) {
          WorkflowServiceGrpc.getCancelWorkflowMethod = getCancelWorkflowMethod =
              io.grpc.MethodDescriptor.<com.kekwy.iarnet.proto.api.CancelWorkflowRequest, com.kekwy.iarnet.proto.api.CancelWorkflowResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CancelWorkflow"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.api.CancelWorkflowRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.api.CancelWorkflowResponse.getDefaultInstance()))
              .setSchemaDescriptor(new WorkflowServiceMethodDescriptorSupplier("CancelWorkflow"))
              .build();
        }
      }
    }
    return getCancelWorkflowMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static WorkflowServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<WorkflowServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<WorkflowServiceStub>() {
        @java.lang.Override
        public WorkflowServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new WorkflowServiceStub(channel, callOptions);
        }
      };
    return WorkflowServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static WorkflowServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<WorkflowServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<WorkflowServiceBlockingStub>() {
        @java.lang.Override
        public WorkflowServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new WorkflowServiceBlockingStub(channel, callOptions);
        }
      };
    return WorkflowServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static WorkflowServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<WorkflowServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<WorkflowServiceFutureStub>() {
        @java.lang.Override
        public WorkflowServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new WorkflowServiceFutureStub(channel, callOptions);
        }
      };
    return WorkflowServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     * <pre>
     * 提交工作流图，control-plane 接收后进行调度执行
     * </pre>
     */
    default void submitWorkflow(com.kekwy.iarnet.proto.api.SubmitWorkflowRequest request,
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.api.SubmitWorkflowResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSubmitWorkflowMethod(), responseObserver);
    }

    /**
     * <pre>
     * 查询已提交工作流的执行状态
     * </pre>
     */
    default void getWorkflowStatus(com.kekwy.iarnet.proto.api.GetWorkflowStatusRequest request,
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.api.GetWorkflowStatusResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetWorkflowStatusMethod(), responseObserver);
    }

    /**
     * <pre>
     * 取消正在执行的工作流
     * </pre>
     */
    default void cancelWorkflow(com.kekwy.iarnet.proto.api.CancelWorkflowRequest request,
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.api.CancelWorkflowResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCancelWorkflowMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service WorkflowService.
   */
  public static abstract class WorkflowServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return WorkflowServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service WorkflowService.
   */
  public static final class WorkflowServiceStub
      extends io.grpc.stub.AbstractAsyncStub<WorkflowServiceStub> {
    private WorkflowServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected WorkflowServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new WorkflowServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * 提交工作流图，control-plane 接收后进行调度执行
     * </pre>
     */
    public void submitWorkflow(com.kekwy.iarnet.proto.api.SubmitWorkflowRequest request,
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.api.SubmitWorkflowResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSubmitWorkflowMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 查询已提交工作流的执行状态
     * </pre>
     */
    public void getWorkflowStatus(com.kekwy.iarnet.proto.api.GetWorkflowStatusRequest request,
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.api.GetWorkflowStatusResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetWorkflowStatusMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 取消正在执行的工作流
     * </pre>
     */
    public void cancelWorkflow(com.kekwy.iarnet.proto.api.CancelWorkflowRequest request,
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.api.CancelWorkflowResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCancelWorkflowMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service WorkflowService.
   */
  public static final class WorkflowServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<WorkflowServiceBlockingStub> {
    private WorkflowServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected WorkflowServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new WorkflowServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * 提交工作流图，control-plane 接收后进行调度执行
     * </pre>
     */
    public com.kekwy.iarnet.proto.api.SubmitWorkflowResponse submitWorkflow(com.kekwy.iarnet.proto.api.SubmitWorkflowRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSubmitWorkflowMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 查询已提交工作流的执行状态
     * </pre>
     */
    public com.kekwy.iarnet.proto.api.GetWorkflowStatusResponse getWorkflowStatus(com.kekwy.iarnet.proto.api.GetWorkflowStatusRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetWorkflowStatusMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 取消正在执行的工作流
     * </pre>
     */
    public com.kekwy.iarnet.proto.api.CancelWorkflowResponse cancelWorkflow(com.kekwy.iarnet.proto.api.CancelWorkflowRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCancelWorkflowMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service WorkflowService.
   */
  public static final class WorkflowServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<WorkflowServiceFutureStub> {
    private WorkflowServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected WorkflowServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new WorkflowServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * 提交工作流图，control-plane 接收后进行调度执行
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.kekwy.iarnet.proto.api.SubmitWorkflowResponse> submitWorkflow(
        com.kekwy.iarnet.proto.api.SubmitWorkflowRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSubmitWorkflowMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 查询已提交工作流的执行状态
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.kekwy.iarnet.proto.api.GetWorkflowStatusResponse> getWorkflowStatus(
        com.kekwy.iarnet.proto.api.GetWorkflowStatusRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetWorkflowStatusMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 取消正在执行的工作流
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.kekwy.iarnet.proto.api.CancelWorkflowResponse> cancelWorkflow(
        com.kekwy.iarnet.proto.api.CancelWorkflowRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCancelWorkflowMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_SUBMIT_WORKFLOW = 0;
  private static final int METHODID_GET_WORKFLOW_STATUS = 1;
  private static final int METHODID_CANCEL_WORKFLOW = 2;

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
        case METHODID_SUBMIT_WORKFLOW:
          serviceImpl.submitWorkflow((com.kekwy.iarnet.proto.api.SubmitWorkflowRequest) request,
              (io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.api.SubmitWorkflowResponse>) responseObserver);
          break;
        case METHODID_GET_WORKFLOW_STATUS:
          serviceImpl.getWorkflowStatus((com.kekwy.iarnet.proto.api.GetWorkflowStatusRequest) request,
              (io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.api.GetWorkflowStatusResponse>) responseObserver);
          break;
        case METHODID_CANCEL_WORKFLOW:
          serviceImpl.cancelWorkflow((com.kekwy.iarnet.proto.api.CancelWorkflowRequest) request,
              (io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.api.CancelWorkflowResponse>) responseObserver);
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
          getSubmitWorkflowMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.kekwy.iarnet.proto.api.SubmitWorkflowRequest,
              com.kekwy.iarnet.proto.api.SubmitWorkflowResponse>(
                service, METHODID_SUBMIT_WORKFLOW)))
        .addMethod(
          getGetWorkflowStatusMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.kekwy.iarnet.proto.api.GetWorkflowStatusRequest,
              com.kekwy.iarnet.proto.api.GetWorkflowStatusResponse>(
                service, METHODID_GET_WORKFLOW_STATUS)))
        .addMethod(
          getCancelWorkflowMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.kekwy.iarnet.proto.api.CancelWorkflowRequest,
              com.kekwy.iarnet.proto.api.CancelWorkflowResponse>(
                service, METHODID_CANCEL_WORKFLOW)))
        .build();
  }

  private static abstract class WorkflowServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    WorkflowServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.kekwy.iarnet.proto.api.WorkflowServiceOuterClass.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("WorkflowService");
    }
  }

  private static final class WorkflowServiceFileDescriptorSupplier
      extends WorkflowServiceBaseDescriptorSupplier {
    WorkflowServiceFileDescriptorSupplier() {}
  }

  private static final class WorkflowServiceMethodDescriptorSupplier
      extends WorkflowServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    WorkflowServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (WorkflowServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new WorkflowServiceFileDescriptorSupplier())
              .addMethod(getSubmitWorkflowMethod())
              .addMethod(getGetWorkflowStatusMethod())
              .addMethod(getCancelWorkflowMethod())
              .build();
        }
      }
    }
    return result;
  }
}
