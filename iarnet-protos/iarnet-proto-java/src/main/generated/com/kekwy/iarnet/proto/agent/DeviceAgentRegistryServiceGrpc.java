package com.kekwy.iarnet.proto.agent;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.62.2)",
    comments = "Source: iarnet/agent/device_agent.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class DeviceAgentRegistryServiceGrpc {

  private DeviceAgentRegistryServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "iarnet.agent.DeviceAgentRegistryService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.agent.RegisterDeviceRequest,
      com.kekwy.iarnet.proto.agent.RegisterDeviceResponse> getRegisterDeviceMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RegisterDevice",
      requestType = com.kekwy.iarnet.proto.agent.RegisterDeviceRequest.class,
      responseType = com.kekwy.iarnet.proto.agent.RegisterDeviceResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.agent.RegisterDeviceRequest,
      com.kekwy.iarnet.proto.agent.RegisterDeviceResponse> getRegisterDeviceMethod() {
    io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.agent.RegisterDeviceRequest, com.kekwy.iarnet.proto.agent.RegisterDeviceResponse> getRegisterDeviceMethod;
    if ((getRegisterDeviceMethod = DeviceAgentRegistryServiceGrpc.getRegisterDeviceMethod) == null) {
      synchronized (DeviceAgentRegistryServiceGrpc.class) {
        if ((getRegisterDeviceMethod = DeviceAgentRegistryServiceGrpc.getRegisterDeviceMethod) == null) {
          DeviceAgentRegistryServiceGrpc.getRegisterDeviceMethod = getRegisterDeviceMethod =
              io.grpc.MethodDescriptor.<com.kekwy.iarnet.proto.agent.RegisterDeviceRequest, com.kekwy.iarnet.proto.agent.RegisterDeviceResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RegisterDevice"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.agent.RegisterDeviceRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.agent.RegisterDeviceResponse.getDefaultInstance()))
              .setSchemaDescriptor(new DeviceAgentRegistryServiceMethodDescriptorSupplier("RegisterDevice"))
              .build();
        }
      }
    }
    return getRegisterDeviceMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.agent.DeviceHeartbeat,
      com.kekwy.iarnet.proto.agent.DeviceHeartbeatAck> getHeartbeatMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Heartbeat",
      requestType = com.kekwy.iarnet.proto.agent.DeviceHeartbeat.class,
      responseType = com.kekwy.iarnet.proto.agent.DeviceHeartbeatAck.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.agent.DeviceHeartbeat,
      com.kekwy.iarnet.proto.agent.DeviceHeartbeatAck> getHeartbeatMethod() {
    io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.agent.DeviceHeartbeat, com.kekwy.iarnet.proto.agent.DeviceHeartbeatAck> getHeartbeatMethod;
    if ((getHeartbeatMethod = DeviceAgentRegistryServiceGrpc.getHeartbeatMethod) == null) {
      synchronized (DeviceAgentRegistryServiceGrpc.class) {
        if ((getHeartbeatMethod = DeviceAgentRegistryServiceGrpc.getHeartbeatMethod) == null) {
          DeviceAgentRegistryServiceGrpc.getHeartbeatMethod = getHeartbeatMethod =
              io.grpc.MethodDescriptor.<com.kekwy.iarnet.proto.agent.DeviceHeartbeat, com.kekwy.iarnet.proto.agent.DeviceHeartbeatAck>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Heartbeat"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.agent.DeviceHeartbeat.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.agent.DeviceHeartbeatAck.getDefaultInstance()))
              .setSchemaDescriptor(new DeviceAgentRegistryServiceMethodDescriptorSupplier("Heartbeat"))
              .build();
        }
      }
    }
    return getHeartbeatMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.agent.SignalingMessage,
      com.kekwy.iarnet.proto.agent.SignalingMessage> getSignalingChannelMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SignalingChannel",
      requestType = com.kekwy.iarnet.proto.agent.SignalingMessage.class,
      responseType = com.kekwy.iarnet.proto.agent.SignalingMessage.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.agent.SignalingMessage,
      com.kekwy.iarnet.proto.agent.SignalingMessage> getSignalingChannelMethod() {
    io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.agent.SignalingMessage, com.kekwy.iarnet.proto.agent.SignalingMessage> getSignalingChannelMethod;
    if ((getSignalingChannelMethod = DeviceAgentRegistryServiceGrpc.getSignalingChannelMethod) == null) {
      synchronized (DeviceAgentRegistryServiceGrpc.class) {
        if ((getSignalingChannelMethod = DeviceAgentRegistryServiceGrpc.getSignalingChannelMethod) == null) {
          DeviceAgentRegistryServiceGrpc.getSignalingChannelMethod = getSignalingChannelMethod =
              io.grpc.MethodDescriptor.<com.kekwy.iarnet.proto.agent.SignalingMessage, com.kekwy.iarnet.proto.agent.SignalingMessage>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SignalingChannel"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.agent.SignalingMessage.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.agent.SignalingMessage.getDefaultInstance()))
              .setSchemaDescriptor(new DeviceAgentRegistryServiceMethodDescriptorSupplier("SignalingChannel"))
              .build();
        }
      }
    }
    return getSignalingChannelMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static DeviceAgentRegistryServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<DeviceAgentRegistryServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<DeviceAgentRegistryServiceStub>() {
        @java.lang.Override
        public DeviceAgentRegistryServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new DeviceAgentRegistryServiceStub(channel, callOptions);
        }
      };
    return DeviceAgentRegistryServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static DeviceAgentRegistryServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<DeviceAgentRegistryServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<DeviceAgentRegistryServiceBlockingStub>() {
        @java.lang.Override
        public DeviceAgentRegistryServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new DeviceAgentRegistryServiceBlockingStub(channel, callOptions);
        }
      };
    return DeviceAgentRegistryServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static DeviceAgentRegistryServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<DeviceAgentRegistryServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<DeviceAgentRegistryServiceFutureStub>() {
        @java.lang.Override
        public DeviceAgentRegistryServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new DeviceAgentRegistryServiceFutureStub(channel, callOptions);
        }
      };
    return DeviceAgentRegistryServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     * <pre>
     * 设备启动后主动向控制平面注册，获取全局唯一的 device_id。
     * </pre>
     */
    default void registerDevice(com.kekwy.iarnet.proto.agent.RegisterDeviceRequest request,
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.agent.RegisterDeviceResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRegisterDeviceMethod(), responseObserver);
    }

    /**
     * <pre>
     * 周期性心跳，上报基础状态与标签变化等（可按需扩展）。
     * </pre>
     */
    default void heartbeat(com.kekwy.iarnet.proto.agent.DeviceHeartbeat request,
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.agent.DeviceHeartbeatAck> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getHeartbeatMethod(), responseObserver);
    }

    /**
     * <pre>
     * 长连接信令通道：双向流。
     * Device Agent → 控制平面：
     *   - 上报候选地址、网络状态（CandidateUpdate）
     *   - 发送 ICE 信令包（IceEnvelope），由控制平面转发给对端设备
     * 控制平面 → Device Agent：
     *   - 下发 ConnectInstruction，要求为某条 workflow edge 建立 Actor 间通道
     *   - 将来自其他设备的 IceEnvelope 转发给本设备
     * </pre>
     */
    default io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.agent.SignalingMessage> signalingChannel(
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.agent.SignalingMessage> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getSignalingChannelMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service DeviceAgentRegistryService.
   */
  public static abstract class DeviceAgentRegistryServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return DeviceAgentRegistryServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service DeviceAgentRegistryService.
   */
  public static final class DeviceAgentRegistryServiceStub
      extends io.grpc.stub.AbstractAsyncStub<DeviceAgentRegistryServiceStub> {
    private DeviceAgentRegistryServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DeviceAgentRegistryServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new DeviceAgentRegistryServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * 设备启动后主动向控制平面注册，获取全局唯一的 device_id。
     * </pre>
     */
    public void registerDevice(com.kekwy.iarnet.proto.agent.RegisterDeviceRequest request,
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.agent.RegisterDeviceResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRegisterDeviceMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 周期性心跳，上报基础状态与标签变化等（可按需扩展）。
     * </pre>
     */
    public void heartbeat(com.kekwy.iarnet.proto.agent.DeviceHeartbeat request,
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.agent.DeviceHeartbeatAck> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getHeartbeatMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     * <pre>
     * 长连接信令通道：双向流。
     * Device Agent → 控制平面：
     *   - 上报候选地址、网络状态（CandidateUpdate）
     *   - 发送 ICE 信令包（IceEnvelope），由控制平面转发给对端设备
     * 控制平面 → Device Agent：
     *   - 下发 ConnectInstruction，要求为某条 workflow edge 建立 Actor 间通道
     *   - 将来自其他设备的 IceEnvelope 转发给本设备
     * </pre>
     */
    public io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.agent.SignalingMessage> signalingChannel(
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.agent.SignalingMessage> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncBidiStreamingCall(
          getChannel().newCall(getSignalingChannelMethod(), getCallOptions()), responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service DeviceAgentRegistryService.
   */
  public static final class DeviceAgentRegistryServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<DeviceAgentRegistryServiceBlockingStub> {
    private DeviceAgentRegistryServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DeviceAgentRegistryServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new DeviceAgentRegistryServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * 设备启动后主动向控制平面注册，获取全局唯一的 device_id。
     * </pre>
     */
    public com.kekwy.iarnet.proto.agent.RegisterDeviceResponse registerDevice(com.kekwy.iarnet.proto.agent.RegisterDeviceRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRegisterDeviceMethod(), getCallOptions(), request);
    }

    /**
     * <pre>
     * 周期性心跳，上报基础状态与标签变化等（可按需扩展）。
     * </pre>
     */
    public com.kekwy.iarnet.proto.agent.DeviceHeartbeatAck heartbeat(com.kekwy.iarnet.proto.agent.DeviceHeartbeat request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getHeartbeatMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service DeviceAgentRegistryService.
   */
  public static final class DeviceAgentRegistryServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<DeviceAgentRegistryServiceFutureStub> {
    private DeviceAgentRegistryServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected DeviceAgentRegistryServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new DeviceAgentRegistryServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * 设备启动后主动向控制平面注册，获取全局唯一的 device_id。
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.kekwy.iarnet.proto.agent.RegisterDeviceResponse> registerDevice(
        com.kekwy.iarnet.proto.agent.RegisterDeviceRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRegisterDeviceMethod(), getCallOptions()), request);
    }

    /**
     * <pre>
     * 周期性心跳，上报基础状态与标签变化等（可按需扩展）。
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.kekwy.iarnet.proto.agent.DeviceHeartbeatAck> heartbeat(
        com.kekwy.iarnet.proto.agent.DeviceHeartbeat request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getHeartbeatMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_REGISTER_DEVICE = 0;
  private static final int METHODID_HEARTBEAT = 1;
  private static final int METHODID_SIGNALING_CHANNEL = 2;

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
        case METHODID_REGISTER_DEVICE:
          serviceImpl.registerDevice((com.kekwy.iarnet.proto.agent.RegisterDeviceRequest) request,
              (io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.agent.RegisterDeviceResponse>) responseObserver);
          break;
        case METHODID_HEARTBEAT:
          serviceImpl.heartbeat((com.kekwy.iarnet.proto.agent.DeviceHeartbeat) request,
              (io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.agent.DeviceHeartbeatAck>) responseObserver);
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
        case METHODID_SIGNALING_CHANNEL:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.signalingChannel(
              (io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.agent.SignalingMessage>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getRegisterDeviceMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.kekwy.iarnet.proto.agent.RegisterDeviceRequest,
              com.kekwy.iarnet.proto.agent.RegisterDeviceResponse>(
                service, METHODID_REGISTER_DEVICE)))
        .addMethod(
          getHeartbeatMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.kekwy.iarnet.proto.agent.DeviceHeartbeat,
              com.kekwy.iarnet.proto.agent.DeviceHeartbeatAck>(
                service, METHODID_HEARTBEAT)))
        .addMethod(
          getSignalingChannelMethod(),
          io.grpc.stub.ServerCalls.asyncBidiStreamingCall(
            new MethodHandlers<
              com.kekwy.iarnet.proto.agent.SignalingMessage,
              com.kekwy.iarnet.proto.agent.SignalingMessage>(
                service, METHODID_SIGNALING_CHANNEL)))
        .build();
  }

  private static abstract class DeviceAgentRegistryServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    DeviceAgentRegistryServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.kekwy.iarnet.proto.agent.DeviceAgent.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("DeviceAgentRegistryService");
    }
  }

  private static final class DeviceAgentRegistryServiceFileDescriptorSupplier
      extends DeviceAgentRegistryServiceBaseDescriptorSupplier {
    DeviceAgentRegistryServiceFileDescriptorSupplier() {}
  }

  private static final class DeviceAgentRegistryServiceMethodDescriptorSupplier
      extends DeviceAgentRegistryServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    DeviceAgentRegistryServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (DeviceAgentRegistryServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new DeviceAgentRegistryServiceFileDescriptorSupplier())
              .addMethod(getRegisterDeviceMethod())
              .addMethod(getHeartbeatMethod())
              .addMethod(getSignalingChannelMethod())
              .build();
        }
      }
    }
    return result;
  }
}
