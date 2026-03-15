package com.kekwy.iarnet.actor;

import com.kekwy.iarnet.proto.ValueCodec;
import com.kekwy.iarnet.proto.actor.ActorEnvelope;
import com.kekwy.iarnet.proto.actor.DataRow;
import com.kekwy.iarnet.proto.actor.InvokeRequest;
import com.kekwy.iarnet.proto.actor.InvokeResponse;
import com.kekwy.iarnet.proto.actor.RegisterActorRequest;
import com.kekwy.iarnet.proto.common.Value;
import com.kekwy.iarnet.proto.provider.ActorRegistrationServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Actor 与 Provider 的 gRPC 双向流客户端：注册、发送 DataRow、接收 StartInputCommand / Row。
 */
public final class ActorChannelClient {

    private static final Logger log = LoggerFactory.getLogger(ActorChannelClient.class);

    private final String registryAddr;
    private final String actorId;
    private final FunctionInvoker invoker;
    private final ConditionEvaluator conditionEvaluator;
    private final CombineBuffer combineBuffer;
    private final ExecutorService executor;

    private ManagedChannel channel;
    private StreamObserver<ActorEnvelope> sendObserver;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final CountDownLatch closed = new CountDownLatch(1);

    public ActorChannelClient(String registryAddr, String actorId,
                              FunctionInvoker invoker, ConditionEvaluator conditionEvaluator,
                              long combineTimeoutMs) {
        this.registryAddr = registryAddr;
        this.actorId = actorId;
        this.invoker = invoker;
        this.conditionEvaluator = conditionEvaluator;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "actor-input-runner");
            t.setDaemon(true);
            return t;
        });
        if (invoker.getKind() == FunctionInvoker.Kind.COMBINE) {
            this.combineBuffer = new CombineBuffer(combineTimeoutMs, pair ->
                    executor.submit(() -> {
                        if (pair != null) {
                            Object result;
                            try {
                                result = invoker.runCombine(pair.left(), pair.right());
                            } catch (Throwable e) {
                                throw new RuntimeException(e);
                            }
                            evaluateAndSendResponse(result, pair.executionId());
                        }
                    }));
        } else {
            this.combineBuffer = null;
        }
    }

    /**
     * 解析 "host:port" 为 [host, port]。
     */
    private static String[] parseRegistryAddr(String addr) {
        int i = addr.lastIndexOf(':');
        if (i <= 0 || i == addr.length() - 1) {
            throw new IllegalArgumentException("无效的 registry 地址，期望 host:port: " + addr);
        }
        String host = addr.substring(0, i);
        String portStr = addr.substring(i + 1);
        try {
            Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无效的端口: " + portStr, e);
        }
        return new String[]{host, portStr};
    }

    /**
     * 连接并注册，之后接收端开始处理消息。
     */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        String[] parts = parseRegistryAddr(registryAddr);
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);

        channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        ActorRegistrationServiceGrpc.ActorRegistrationServiceStub stub =
                ActorRegistrationServiceGrpc.newStub(channel);

        sendObserver = stub.actorChannel(new StreamObserver<>() {
            @Override
            public void onNext(ActorEnvelope msg) {
                if (msg == null) return;
                //noinspection SwitchStatementWithTooFewBranches
                switch (msg.getPayloadCase()) {
                    case REQUEST:
                        executor.submit(() -> handleRequest(msg.getRequest()));
                        break;
                    default:
                        log.debug("收到: {}", msg.getPayloadCase());
                }
            }

            @Override
            public void onError(Throwable t) {
                log.warn("ActorChannel 错误", t);
                running.set(false);
                closed.countDown();
            }

            @Override
            public void onCompleted() {
                running.set(false);
                closed.countDown();
            }
        });

        sendObserver.onNext(ActorEnvelope.newBuilder()
                .setRegisterActor(RegisterActorRequest.newBuilder().setActorId(actorId).build())
                .build());
        log.info("已向 Provider 注册: actorId={}, registry={}", actorId, registryAddr);
    }

    private void handleRequest(InvokeRequest request) {
        if (request == null) return;
        // COMBINE 允许无 row 的请求（上游条件分支未命中时发的空响应），视为该 port 为空，立即参与汇聚
        if (invoker.getKind() != FunctionInvoker.Kind.COMBINE && !request.hasRow()) return;

        //noinspection ConstantValue
        String executionId = request.getExecutionId() != null ? request.getExecutionId() : "";
        int inputPort = request.getInputPort();
        // 无 row 时用默认 Value，Combine 端会当作 KIND_NOT_SET → OptionalValue.empty()
        Value value = request.hasRow() ? request.getRow().getValue() : Value.getDefaultInstance();

        try {
            switch (invoker.getKind()) {
                case TASK -> {
                    Object result = invoker.runTask(value);
                    evaluateAndSendResponse(result, executionId);
                }
                case OUTPUT -> invoker.runOutput(value);
                case COMBINE -> {
                    CombineBuffer.ReadyPair pair = combineBuffer.offer(executionId, inputPort, value);
                    if (pair != null) {
                        Object result = invoker.runCombine(pair.left(), pair.right());
                        evaluateAndSendResponse(result, executionId);
                    }
                }
                default -> log.warn("收到 ROW 但本节点类型为 {}", invoker.getKind());
            }
        } catch (Throwable t) {
            log.error("处理 Row 失败", t);
        }
    }

    void evaluateAndSendResponse(Object result, String executionId) {
        Value out = ValueCodec.encode(result);
        Set<Integer> portsWithData = conditionEvaluator.evaluate(result);
        Set<Integer> allPorts = conditionEvaluator.getOutputPorts();
        for (Integer port : portsWithData) {
            sendRow(out, port, executionId);
        }
        for (Integer port : allPorts) {
            if (!portsWithData.contains(port)) {
                sendEmptyRow(port, executionId);
            }
        }
    }

    /**
     * 发送仅含 executionId 的空响应，表示该 port 上该 executionId 无数据。
     * 下游 Combine 可据此立即以空值汇聚，避免因条件分支导致一直等待直到超时。
     */
    private void sendEmptyRow(int outputPort, String executionId) {
        if (sendObserver == null) return;
        InvokeResponse response = InvokeResponse.newBuilder()
                .setExecutionId(executionId != null ? executionId : "")
                .setPort(outputPort)
                .build();
        ActorEnvelope env = ActorEnvelope.newBuilder()
                .setResponse(response)
                .build();
        sendObserver.onNext(env);
    }

    /**
     * 发送一行数据到指定 output_port，透传 executionId 供下游 Combine 汇聚。
     */
    public void sendRow(Value value, int outputPort, String executionId) {
        if (sendObserver == null) return;
        DataRow row = DataRow.newBuilder()
                .setRowId(UUID.randomUUID().toString())
                .setValue(value)
                .build();
        InvokeResponse response = InvokeResponse.newBuilder()
                .setExecutionId(executionId != null ? executionId : "")
                .setRow(row)
                .setPort(outputPort)
                .build();
        ActorEnvelope env = ActorEnvelope.newBuilder()
                .setResponse(response)
                .build();
        sendObserver.onNext(env);
    }

    /**
     * 等待流关闭（onCompleted/onError）。
     */
    public void awaitClosed() throws InterruptedException {
        closed.await();
    }

    /**
     * 关闭 channel 与线程池。
     */
    public void shutdown() {
        running.set(false);
        if (combineBuffer != null) {
            combineBuffer.shutdown();
        }
        if (sendObserver != null) {
            sendObserver.onCompleted();
            sendObserver = null;
        }
        if (channel != null) {
            channel.shutdown();
            channel = null;
        }
        executor.shutdown();
        closed.countDown();
    }
}
