package com.kekwy.iarnet.proto.agent;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.62.2)",
    comments = "Source: iarnet/agent/local_agent.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class LocalAgentServiceGrpc {

  private LocalAgentServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "iarnet.agent.LocalAgentService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.agent.LocalAgentMessage,
      com.kekwy.iarnet.proto.agent.LocalAgentMessage> getLocalChannelMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "LocalChannel",
      requestType = com.kekwy.iarnet.proto.agent.LocalAgentMessage.class,
      responseType = com.kekwy.iarnet.proto.agent.LocalAgentMessage.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.agent.LocalAgentMessage,
      com.kekwy.iarnet.proto.agent.LocalAgentMessage> getLocalChannelMethod() {
    io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.agent.LocalAgentMessage, com.kekwy.iarnet.proto.agent.LocalAgentMessage> getLocalChannelMethod;
    if ((getLocalChannelMethod = LocalAgentServiceGrpc.getLocalChannelMethod) == null) {
      synchronized (LocalAgentServiceGrpc.class) {
        if ((getLocalChannelMethod = LocalAgentServiceGrpc.getLocalChannelMethod) == null) {
          LocalAgentServiceGrpc.getLocalChannelMethod = getLocalChannelMethod =
              io.grpc.MethodDescriptor.<com.kekwy.iarnet.proto.agent.LocalAgentMessage, com.kekwy.iarnet.proto.agent.LocalAgentMessage>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "LocalChannel"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.agent.LocalAgentMessage.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.agent.LocalAgentMessage.getDefaultInstance()))
              .setSchemaDescriptor(new LocalAgentServiceMethodDescriptorSupplier("LocalChannel"))
              .build();
        }
      }
    }
    return getLocalChannelMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static LocalAgentServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<LocalAgentServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<LocalAgentServiceStub>() {
        @java.lang.Override
        public LocalAgentServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new LocalAgentServiceStub(channel, callOptions);
        }
      };
    return LocalAgentServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static LocalAgentServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<LocalAgentServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<LocalAgentServiceBlockingStub>() {
        @java.lang.Override
        public LocalAgentServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new LocalAgentServiceBlockingStub(channel, callOptions);
        }
      };
    return LocalAgentServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static LocalAgentServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<LocalAgentServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<LocalAgentServiceFutureStub>() {
        @java.lang.Override
        public LocalAgentServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new LocalAgentServiceFutureStub(channel, callOptions);
        }
      };
    return LocalAgentServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     * <pre>
     * 基础版：单条双向流，后续在 LocalAgentMessage 中扩展消息类型。
     * </pre>
     */
    default io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.agent.LocalAgentMessage> localChannel(
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.agent.LocalAgentMessage> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getLocalChannelMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service LocalAgentService.
   */
  public static abstract class LocalAgentServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return LocalAgentServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service LocalAgentService.
   */
  public static final class LocalAgentServiceStub
      extends io.grpc.stub.AbstractAsyncStub<LocalAgentServiceStub> {
    private LocalAgentServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected LocalAgentServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new LocalAgentServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * 基础版：单条双向流，后续在 LocalAgentMessage 中扩展消息类型。
     * </pre>
     */
    public io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.agent.LocalAgentMessage> localChannel(
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.agent.LocalAgentMessage> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncBidiStreamingCall(
          getChannel().newCall(getLocalChannelMethod(), getCallOptions()), responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service LocalAgentService.
   */
  public static final class LocalAgentServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<LocalAgentServiceBlockingStub> {
    private LocalAgentServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected LocalAgentServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new LocalAgentServiceBlockingStub(channel, callOptions);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service LocalAgentService.
   */
  public static final class LocalAgentServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<LocalAgentServiceFutureStub> {
    private LocalAgentServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected LocalAgentServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new LocalAgentServiceFutureStub(channel, callOptions);
    }
  }

  private static final int METHODID_LOCAL_CHANNEL = 0;

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
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_LOCAL_CHANNEL:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.localChannel(
              (io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.agent.LocalAgentMessage>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getLocalChannelMethod(),
          io.grpc.stub.ServerCalls.asyncBidiStreamingCall(
            new MethodHandlers<
              com.kekwy.iarnet.proto.agent.LocalAgentMessage,
              com.kekwy.iarnet.proto.agent.LocalAgentMessage>(
                service, METHODID_LOCAL_CHANNEL)))
        .build();
  }

  private static abstract class LocalAgentServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    LocalAgentServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.kekwy.iarnet.proto.agent.LocalAgent.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("LocalAgentService");
    }
  }

  private static final class LocalAgentServiceFileDescriptorSupplier
      extends LocalAgentServiceBaseDescriptorSupplier {
    LocalAgentServiceFileDescriptorSupplier() {}
  }

  private static final class LocalAgentServiceMethodDescriptorSupplier
      extends LocalAgentServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    LocalAgentServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (LocalAgentServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new LocalAgentServiceFileDescriptorSupplier())
              .addMethod(getLocalChannelMethod())
              .build();
        }
      }
    }
    return result;
  }
}
