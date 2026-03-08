package com.kekwy.iarnet.proto.adapter;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.62.2)",
    comments = "Source: iarnet/adapter/adapter_registry.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class AdapterRegistryServiceGrpc {

  private AdapterRegistryServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "iarnet.adapter.AdapterRegistryService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.adapter.RegisterRequest,
      com.kekwy.iarnet.proto.adapter.RegisterResponse> getRegisterMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Register",
      requestType = com.kekwy.iarnet.proto.adapter.RegisterRequest.class,
      responseType = com.kekwy.iarnet.proto.adapter.RegisterResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.adapter.RegisterRequest,
      com.kekwy.iarnet.proto.adapter.RegisterResponse> getRegisterMethod() {
    io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.adapter.RegisterRequest, com.kekwy.iarnet.proto.adapter.RegisterResponse> getRegisterMethod;
    if ((getRegisterMethod = AdapterRegistryServiceGrpc.getRegisterMethod) == null) {
      synchronized (AdapterRegistryServiceGrpc.class) {
        if ((getRegisterMethod = AdapterRegistryServiceGrpc.getRegisterMethod) == null) {
          AdapterRegistryServiceGrpc.getRegisterMethod = getRegisterMethod =
              io.grpc.MethodDescriptor.<com.kekwy.iarnet.proto.adapter.RegisterRequest, com.kekwy.iarnet.proto.adapter.RegisterResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Register"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.adapter.RegisterRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.adapter.RegisterResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AdapterRegistryServiceMethodDescriptorSupplier("Register"))
              .build();
        }
      }
    }
    return getRegisterMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.adapter.HeartbeatRequest,
      com.kekwy.iarnet.proto.adapter.HeartbeatResponse> getHeartbeatMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Heartbeat",
      requestType = com.kekwy.iarnet.proto.adapter.HeartbeatRequest.class,
      responseType = com.kekwy.iarnet.proto.adapter.HeartbeatResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.adapter.HeartbeatRequest,
      com.kekwy.iarnet.proto.adapter.HeartbeatResponse> getHeartbeatMethod() {
    io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.adapter.HeartbeatRequest, com.kekwy.iarnet.proto.adapter.HeartbeatResponse> getHeartbeatMethod;
    if ((getHeartbeatMethod = AdapterRegistryServiceGrpc.getHeartbeatMethod) == null) {
      synchronized (AdapterRegistryServiceGrpc.class) {
        if ((getHeartbeatMethod = AdapterRegistryServiceGrpc.getHeartbeatMethod) == null) {
          AdapterRegistryServiceGrpc.getHeartbeatMethod = getHeartbeatMethod =
              io.grpc.MethodDescriptor.<com.kekwy.iarnet.proto.adapter.HeartbeatRequest, com.kekwy.iarnet.proto.adapter.HeartbeatResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Heartbeat"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.adapter.HeartbeatRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.adapter.HeartbeatResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AdapterRegistryServiceMethodDescriptorSupplier("Heartbeat"))
              .build();
        }
      }
    }
    return getHeartbeatMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.adapter.DeregisterRequest,
      com.kekwy.iarnet.proto.adapter.DeregisterResponse> getDeregisterMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Deregister",
      requestType = com.kekwy.iarnet.proto.adapter.DeregisterRequest.class,
      responseType = com.kekwy.iarnet.proto.adapter.DeregisterResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.adapter.DeregisterRequest,
      com.kekwy.iarnet.proto.adapter.DeregisterResponse> getDeregisterMethod() {
    io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.adapter.DeregisterRequest, com.kekwy.iarnet.proto.adapter.DeregisterResponse> getDeregisterMethod;
    if ((getDeregisterMethod = AdapterRegistryServiceGrpc.getDeregisterMethod) == null) {
      synchronized (AdapterRegistryServiceGrpc.class) {
        if ((getDeregisterMethod = AdapterRegistryServiceGrpc.getDeregisterMethod) == null) {
          AdapterRegistryServiceGrpc.getDeregisterMethod = getDeregisterMethod =
              io.grpc.MethodDescriptor.<com.kekwy.iarnet.proto.adapter.DeregisterRequest, com.kekwy.iarnet.proto.adapter.DeregisterResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Deregister"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.adapter.DeregisterRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.adapter.DeregisterResponse.getDefaultInstance()))
              .setSchemaDescriptor(new AdapterRegistryServiceMethodDescriptorSupplier("Deregister"))
              .build();
        }
      }
    }
    return getDeregisterMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.adapter.CommandResponse,
      com.kekwy.iarnet.proto.adapter.Command> getCommandChannelMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CommandChannel",
      requestType = com.kekwy.iarnet.proto.adapter.CommandResponse.class,
      responseType = com.kekwy.iarnet.proto.adapter.Command.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.adapter.CommandResponse,
      com.kekwy.iarnet.proto.adapter.Command> getCommandChannelMethod() {
    io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.adapter.CommandResponse, com.kekwy.iarnet.proto.adapter.Command> getCommandChannelMethod;
    if ((getCommandChannelMethod = AdapterRegistryServiceGrpc.getCommandChannelMethod) == null) {
      synchronized (AdapterRegistryServiceGrpc.class) {
        if ((getCommandChannelMethod = AdapterRegistryServiceGrpc.getCommandChannelMethod) == null) {
          AdapterRegistryServiceGrpc.getCommandChannelMethod = getCommandChannelMethod =
              io.grpc.MethodDescriptor.<com.kekwy.iarnet.proto.adapter.CommandResponse, com.kekwy.iarnet.proto.adapter.Command>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CommandChannel"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.adapter.CommandResponse.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.adapter.Command.getDefaultInstance()))
              .setSchemaDescriptor(new AdapterRegistryServiceMethodDescriptorSupplier("CommandChannel"))
              .build();
        }
      }
    }
    return getCommandChannelMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static AdapterRegistryServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AdapterRegistryServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AdapterRegistryServiceStub>() {
        @java.lang.Override
        public AdapterRegistryServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AdapterRegistryServiceStub(channel, callOptions);
        }
      };
    return AdapterRegistryServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static AdapterRegistryServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AdapterRegistryServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AdapterRegistryServiceBlockingStub>() {
        @java.lang.Override
        public AdapterRegistryServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AdapterRegistryServiceBlockingStub(channel, callOptions);
        }
      };
    return AdapterRegistryServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static AdapterRegistryServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<AdapterRegistryServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<AdapterRegistryServiceFutureStub>() {
        @java.lang.Override
        public AdapterRegistryServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new AdapterRegistryServiceFutureStub(channel, callOptions);
        }
      };
    return AdapterRegistryServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     * <pre>
     * Adapter 启动后主动向 control-plane 注册
     * </pre>
     */
    default void register(com.kekwy.iarnet.proto.adapter.RegisterRequest request,
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.adapter.RegisterResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRegisterMethod(), responseObserver);
    }

    /**
     * <pre>
     * 定期心跳，上报当前资源使用情况
     * </pre>
     */
    default void heartbeat(com.kekwy.iarnet.proto.adapter.HeartbeatRequest request,
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.adapter.HeartbeatResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getHeartbeatMethod(), responseObserver);
    }

    /**
     * <pre>
     * Adapter 下线时主动注销
     * </pre>
     */
    default void deregister(com.kekwy.iarnet.proto.adapter.DeregisterRequest request,
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.adapter.DeregisterResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getDeregisterMethod(), responseObserver);
    }

    /**
     * <pre>
     * 命令通道：Adapter 主动发起的长连接双向流。
     * control-plane 通过此通道向 Adapter 下发操作指令（部署、停止等），
     * Adapter 处理后通过同一通道返回结果。
     * </pre>
     */
    default io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.adapter.CommandResponse> commandChannel(
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.adapter.Command> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getCommandChannelMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service AdapterRegistryService.
   */
  public static abstract class AdapterRegistryServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return AdapterRegistryServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service AdapterRegistryService.
   */
  public static final class AdapterRegistryServiceStub
      extends io.grpc.stub.AbstractAsyncStub<AdapterRegistryServiceStub> {
    private AdapterRegistryServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AdapterRegistryServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AdapterRegistryServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Adapter 启动后主动向 control-plane 注册
     * </pre>
     */
    public void register(com.kekwy.iarnet.proto.adapter.RegisterRequest request,
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.adapter.RegisterResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRegisterMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 定期心跳，上报当前资源使用情况
     * </pre>
     */
    public void heartbeat(com.kekwy.iarnet.proto.adapter.HeartbeatRequest request,
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.adapter.HeartbeatResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getHeartbeatMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * Adapter 下线时主动注销
     * </pre>
     */
    public void deregister(com.kekwy.iarnet.proto.adapter.DeregisterRequest request,
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.adapter.DeregisterResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getDeregisterMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 命令通道：Adapter 主动发起的长连接双向流。
     * control-plane 通过此通道向 Adapter 下发操作指令（部署、停止等），
     * Adapter 处理后通过同一通道返回结果。
     * </pre>
     */
    public io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.adapter.CommandResponse> commandChannel(
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.adapter.Command> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncBidiStreamingCall(
          getChannel().newCall(getCommandChannelMethod(), getCallOptions()), responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service AdapterRegistryService.
   */
  public static final class AdapterRegistryServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<AdapterRegistryServiceBlockingStub> {
    private AdapterRegistryServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AdapterRegistryServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AdapterRegistryServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * Adapter 启动后主动向 control-plane 注册
     * </pre>
     */
    public com.kekwy.iarnet.proto.adapter.RegisterResponse register(com.kekwy.iarnet.proto.adapter.RegisterRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRegisterMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 定期心跳，上报当前资源使用情况
     * </pre>
     */
    public com.kekwy.iarnet.proto.adapter.HeartbeatResponse heartbeat(com.kekwy.iarnet.proto.adapter.HeartbeatRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getHeartbeatMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * Adapter 下线时主动注销
     * </pre>
     */
    public com.kekwy.iarnet.proto.adapter.DeregisterResponse deregister(com.kekwy.iarnet.proto.adapter.DeregisterRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getDeregisterMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service AdapterRegistryService.
   */
  public static final class AdapterRegistryServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<AdapterRegistryServiceFutureStub> {
    private AdapterRegistryServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected AdapterRegistryServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new AdapterRegistryServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * Adapter 启动后主动向 control-plane 注册
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.kekwy.iarnet.proto.adapter.RegisterResponse> register(
        com.kekwy.iarnet.proto.adapter.RegisterRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRegisterMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 定期心跳，上报当前资源使用情况
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.kekwy.iarnet.proto.adapter.HeartbeatResponse> heartbeat(
        com.kekwy.iarnet.proto.adapter.HeartbeatRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getHeartbeatMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * Adapter 下线时主动注销
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.kekwy.iarnet.proto.adapter.DeregisterResponse> deregister(
        com.kekwy.iarnet.proto.adapter.DeregisterRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getDeregisterMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_REGISTER = 0;
  private static final int METHODID_HEARTBEAT = 1;
  private static final int METHODID_DEREGISTER = 2;
  private static final int METHODID_COMMAND_CHANNEL = 3;

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
        case METHODID_REGISTER:
          serviceImpl.register((com.kekwy.iarnet.proto.adapter.RegisterRequest) request,
              (io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.adapter.RegisterResponse>) responseObserver);
          break;
        case METHODID_HEARTBEAT:
          serviceImpl.heartbeat((com.kekwy.iarnet.proto.adapter.HeartbeatRequest) request,
              (io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.adapter.HeartbeatResponse>) responseObserver);
          break;
        case METHODID_DEREGISTER:
          serviceImpl.deregister((com.kekwy.iarnet.proto.adapter.DeregisterRequest) request,
              (io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.adapter.DeregisterResponse>) responseObserver);
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
        case METHODID_COMMAND_CHANNEL:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.commandChannel(
              (io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.adapter.Command>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getRegisterMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.kekwy.iarnet.proto.adapter.RegisterRequest,
              com.kekwy.iarnet.proto.adapter.RegisterResponse>(
                service, METHODID_REGISTER)))
        .addMethod(
          getHeartbeatMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.kekwy.iarnet.proto.adapter.HeartbeatRequest,
              com.kekwy.iarnet.proto.adapter.HeartbeatResponse>(
                service, METHODID_HEARTBEAT)))
        .addMethod(
          getDeregisterMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.kekwy.iarnet.proto.adapter.DeregisterRequest,
              com.kekwy.iarnet.proto.adapter.DeregisterResponse>(
                service, METHODID_DEREGISTER)))
        .addMethod(
          getCommandChannelMethod(),
          io.grpc.stub.ServerCalls.asyncBidiStreamingCall(
            new MethodHandlers<
              com.kekwy.iarnet.proto.adapter.CommandResponse,
              com.kekwy.iarnet.proto.adapter.Command>(
                service, METHODID_COMMAND_CHANNEL)))
        .build();
  }

  private static abstract class AdapterRegistryServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    AdapterRegistryServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.kekwy.iarnet.proto.adapter.AdapterRegistry.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("AdapterRegistryService");
    }
  }

  private static final class AdapterRegistryServiceFileDescriptorSupplier
      extends AdapterRegistryServiceBaseDescriptorSupplier {
    AdapterRegistryServiceFileDescriptorSupplier() {}
  }

  private static final class AdapterRegistryServiceMethodDescriptorSupplier
      extends AdapterRegistryServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    AdapterRegistryServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (AdapterRegistryServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new AdapterRegistryServiceFileDescriptorSupplier())
              .addMethod(getRegisterMethod())
              .addMethod(getHeartbeatMethod())
              .addMethod(getDeregisterMethod())
              .addMethod(getCommandChannelMethod())
              .build();
        }
      }
    }
    return result;
  }
}
