package com.kekwy.iarnet.proto.actor;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.62.2)",
    comments = "Source: iarnet/actor/actor_control.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class ActorControlServiceGrpc {

  private ActorControlServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "iarnet.actor.ActorControlService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.actor.ActorReport,
      com.kekwy.iarnet.proto.actor.ActorDirective> getControlChannelMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ControlChannel",
      requestType = com.kekwy.iarnet.proto.actor.ActorReport.class,
      responseType = com.kekwy.iarnet.proto.actor.ActorDirective.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.actor.ActorReport,
      com.kekwy.iarnet.proto.actor.ActorDirective> getControlChannelMethod() {
    io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.actor.ActorReport, com.kekwy.iarnet.proto.actor.ActorDirective> getControlChannelMethod;
    if ((getControlChannelMethod = ActorControlServiceGrpc.getControlChannelMethod) == null) {
      synchronized (ActorControlServiceGrpc.class) {
        if ((getControlChannelMethod = ActorControlServiceGrpc.getControlChannelMethod) == null) {
          ActorControlServiceGrpc.getControlChannelMethod = getControlChannelMethod =
              io.grpc.MethodDescriptor.<com.kekwy.iarnet.proto.actor.ActorReport, com.kekwy.iarnet.proto.actor.ActorDirective>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ControlChannel"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.actor.ActorReport.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.actor.ActorDirective.getDefaultInstance()))
              .setSchemaDescriptor(new ActorControlServiceMethodDescriptorSupplier("ControlChannel"))
              .build();
        }
      }
    }
    return getControlChannelMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ActorControlServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ActorControlServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ActorControlServiceStub>() {
        @java.lang.Override
        public ActorControlServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ActorControlServiceStub(channel, callOptions);
        }
      };
    return ActorControlServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ActorControlServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ActorControlServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ActorControlServiceBlockingStub>() {
        @java.lang.Override
        public ActorControlServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ActorControlServiceBlockingStub(channel, callOptions);
        }
      };
    return ActorControlServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ActorControlServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ActorControlServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ActorControlServiceFutureStub>() {
        @java.lang.Override
        public ActorControlServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ActorControlServiceFutureStub(channel, callOptions);
        }
      };
    return ActorControlServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     * <pre>
     * Actor 启动后主动建立控制通道，双向流。
     * </pre>
     */
    default io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.actor.ActorReport> controlChannel(
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.actor.ActorDirective> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getControlChannelMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service ActorControlService.
   */
  public static abstract class ActorControlServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return ActorControlServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service ActorControlService.
   */
  public static final class ActorControlServiceStub
      extends io.grpc.stub.AbstractAsyncStub<ActorControlServiceStub> {
    private ActorControlServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ActorControlServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ActorControlServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * Actor 启动后主动建立控制通道，双向流。
     * </pre>
     */
    public io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.actor.ActorReport> controlChannel(
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.actor.ActorDirective> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncBidiStreamingCall(
          getChannel().newCall(getControlChannelMethod(), getCallOptions()), responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service ActorControlService.
   */
  public static final class ActorControlServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<ActorControlServiceBlockingStub> {
    private ActorControlServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ActorControlServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ActorControlServiceBlockingStub(channel, callOptions);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service ActorControlService.
   */
  public static final class ActorControlServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<ActorControlServiceFutureStub> {
    private ActorControlServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ActorControlServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ActorControlServiceFutureStub(channel, callOptions);
    }
  }

  private static final int METHODID_CONTROL_CHANNEL = 0;

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
        case METHODID_CONTROL_CHANNEL:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.controlChannel(
              (io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.actor.ActorDirective>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getControlChannelMethod(),
          io.grpc.stub.ServerCalls.asyncBidiStreamingCall(
            new MethodHandlers<
              com.kekwy.iarnet.proto.actor.ActorReport,
              com.kekwy.iarnet.proto.actor.ActorDirective>(
                service, METHODID_CONTROL_CHANNEL)))
        .build();
  }

  private static abstract class ActorControlServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ActorControlServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.kekwy.iarnet.proto.actor.ActorControl.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("ActorControlService");
    }
  }

  private static final class ActorControlServiceFileDescriptorSupplier
      extends ActorControlServiceBaseDescriptorSupplier {
    ActorControlServiceFileDescriptorSupplier() {}
  }

  private static final class ActorControlServiceMethodDescriptorSupplier
      extends ActorControlServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    ActorControlServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (ActorControlServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ActorControlServiceFileDescriptorSupplier())
              .addMethod(getControlChannelMethod())
              .build();
        }
      }
    }
    return result;
  }
}
