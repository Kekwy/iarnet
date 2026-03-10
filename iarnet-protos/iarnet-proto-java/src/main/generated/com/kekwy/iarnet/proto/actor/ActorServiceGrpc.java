package com.kekwy.iarnet.proto.actor;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.62.2)",
    comments = "Source: iarnet/actor/actor_service.proto")
@io.grpc.stub.annotations.GrpcGenerated
public final class ActorServiceGrpc {

  private ActorServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "iarnet.actor.ActorService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.actor.ActorInvokeRequest,
      com.kekwy.iarnet.proto.actor.ActorInvokeResponse> getInvokeMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Invoke",
      requestType = com.kekwy.iarnet.proto.actor.ActorInvokeRequest.class,
      responseType = com.kekwy.iarnet.proto.actor.ActorInvokeResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.actor.ActorInvokeRequest,
      com.kekwy.iarnet.proto.actor.ActorInvokeResponse> getInvokeMethod() {
    io.grpc.MethodDescriptor<com.kekwy.iarnet.proto.actor.ActorInvokeRequest, com.kekwy.iarnet.proto.actor.ActorInvokeResponse> getInvokeMethod;
    if ((getInvokeMethod = ActorServiceGrpc.getInvokeMethod) == null) {
      synchronized (ActorServiceGrpc.class) {
        if ((getInvokeMethod = ActorServiceGrpc.getInvokeMethod) == null) {
          ActorServiceGrpc.getInvokeMethod = getInvokeMethod =
              io.grpc.MethodDescriptor.<com.kekwy.iarnet.proto.actor.ActorInvokeRequest, com.kekwy.iarnet.proto.actor.ActorInvokeResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Invoke"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.actor.ActorInvokeRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.kekwy.iarnet.proto.actor.ActorInvokeResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ActorServiceMethodDescriptorSupplier("Invoke"))
              .build();
        }
      }
    }
    return getInvokeMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ActorServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ActorServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ActorServiceStub>() {
        @java.lang.Override
        public ActorServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ActorServiceStub(channel, callOptions);
        }
      };
    return ActorServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ActorServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ActorServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ActorServiceBlockingStub>() {
        @java.lang.Override
        public ActorServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ActorServiceBlockingStub(channel, callOptions);
        }
      };
    return ActorServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ActorServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ActorServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ActorServiceFutureStub>() {
        @java.lang.Override
        public ActorServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ActorServiceFutureStub(channel, callOptions);
        }
      };
    return ActorServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     * <pre>
     * 同步调用：一次请求对应一次响应。
     * 上层通常会在 InvokeRequest.payload 中放入序列化后的参数包，
     * 例如自定义的 Proto / JSON / Avro 等，由 Actor runtime 自行解码。
     * 返回的 InvokeResponse.payload 则承载序列化后的结果对象。
     * </pre>
     */
    default void invoke(com.kekwy.iarnet.proto.actor.ActorInvokeRequest request,
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.actor.ActorInvokeResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getInvokeMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service ActorService.
   */
  public static abstract class ActorServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return ActorServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service ActorService.
   */
  public static final class ActorServiceStub
      extends io.grpc.stub.AbstractAsyncStub<ActorServiceStub> {
    private ActorServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ActorServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ActorServiceStub(channel, callOptions);
    }

    /**
     * <pre>
     * 同步调用：一次请求对应一次响应。
     * 上层通常会在 InvokeRequest.payload 中放入序列化后的参数包，
     * 例如自定义的 Proto / JSON / Avro 等，由 Actor runtime 自行解码。
     * 返回的 InvokeResponse.payload 则承载序列化后的结果对象。
     * </pre>
     */
    public void invoke(com.kekwy.iarnet.proto.actor.ActorInvokeRequest request,
        io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.actor.ActorInvokeResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getInvokeMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service ActorService.
   */
  public static final class ActorServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<ActorServiceBlockingStub> {
    private ActorServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ActorServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ActorServiceBlockingStub(channel, callOptions);
    }

    /**
     * <pre>
     * 同步调用：一次请求对应一次响应。
     * 上层通常会在 InvokeRequest.payload 中放入序列化后的参数包，
     * 例如自定义的 Proto / JSON / Avro 等，由 Actor runtime 自行解码。
     * 返回的 InvokeResponse.payload 则承载序列化后的结果对象。
     * </pre>
     */
    public com.kekwy.iarnet.proto.actor.ActorInvokeResponse invoke(com.kekwy.iarnet.proto.actor.ActorInvokeRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getInvokeMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service ActorService.
   */
  public static final class ActorServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<ActorServiceFutureStub> {
    private ActorServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ActorServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ActorServiceFutureStub(channel, callOptions);
    }

    /**
     * <pre>
     * 同步调用：一次请求对应一次响应。
     * 上层通常会在 InvokeRequest.payload 中放入序列化后的参数包，
     * 例如自定义的 Proto / JSON / Avro 等，由 Actor runtime 自行解码。
     * 返回的 InvokeResponse.payload 则承载序列化后的结果对象。
     * </pre>
     */
    public com.google.common.util.concurrent.ListenableFuture<com.kekwy.iarnet.proto.actor.ActorInvokeResponse> invoke(
        com.kekwy.iarnet.proto.actor.ActorInvokeRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getInvokeMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_INVOKE = 0;

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
        case METHODID_INVOKE:
          serviceImpl.invoke((com.kekwy.iarnet.proto.actor.ActorInvokeRequest) request,
              (io.grpc.stub.StreamObserver<com.kekwy.iarnet.proto.actor.ActorInvokeResponse>) responseObserver);
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
          getInvokeMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.kekwy.iarnet.proto.actor.ActorInvokeRequest,
              com.kekwy.iarnet.proto.actor.ActorInvokeResponse>(
                service, METHODID_INVOKE)))
        .build();
  }

  private static abstract class ActorServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ActorServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.kekwy.iarnet.proto.actor.ActorServiceOuterClass.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("ActorService");
    }
  }

  private static final class ActorServiceFileDescriptorSupplier
      extends ActorServiceBaseDescriptorSupplier {
    ActorServiceFileDescriptorSupplier() {}
  }

  private static final class ActorServiceMethodDescriptorSupplier
      extends ActorServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    ActorServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (ActorServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ActorServiceFileDescriptorSupplier())
              .addMethod(getInvokeMethod())
              .build();
        }
      }
    }
    return result;
  }
}
