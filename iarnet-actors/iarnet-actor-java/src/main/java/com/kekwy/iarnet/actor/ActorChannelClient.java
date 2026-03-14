package com.kekwy.iarnet.actor;

import com.kekwy.iarnet.proto.ValueCodec;
import com.kekwy.iarnet.proto.actor.ActorEnvelope;
import com.kekwy.iarnet.proto.actor.DataRow;
import com.kekwy.iarnet.proto.actor.RegisterActorRequest;
import com.kekwy.iarnet.proto.common.Value;
import com.kekwy.iarnet.proto.provider.ActorRegistrationServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final ExecutorService executor;

    private ManagedChannel channel;
    private StreamObserver<ActorEnvelope> sendObserver;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final CountDownLatch closed = new CountDownLatch(1);

    public ActorChannelClient(String registryAddr, String actorId,
                              FunctionInvoker invoker, ConditionEvaluator conditionEvaluator) {
        this.registryAddr = registryAddr;
        this.actorId = actorId;
        this.invoker = invoker;
        this.conditionEvaluator = conditionEvaluator;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "actor-input-runner");
            t.setDaemon(true);
            return t;
        });
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
                switch (msg.getPayloadCase()) {
                    case START_INPUT_COMMAND:
                        executor.submit(() -> {
                            try {
                                invoker.runInput(v -> sendRow(v, 0));
                            } catch (Throwable t) {
                                log.error("Input 函数执行异常", t);
                            }
                        });
                        break;
                    case ROW:
                        handleRow(msg.getRow());
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

    private void handleRow(DataRow row) {
        Value value = row.getValue();
        try {
            switch (invoker.getKind()) {
                case TASK -> {
                    Value out = invoker.runTask(value);
                    Object outObj = ValueCodec.decode(out);
                    for (Integer port : conditionEvaluator.evaluate(outObj)) {
                        sendRow(out, port);
                    }
                }
                case OUTPUT -> invoker.runOutput(value);
                case UNION -> {
                    // Union 需两路输入，此处简化为单行：当作一路有值、一路空
                    Value out = invoker.runUnion(value, null);
                    Object outObj = ValueCodec.decode(out);
                    for (Integer port : conditionEvaluator.evaluate(outObj)) {
                        sendRow(out, port);
                    }
                }
                default -> log.warn("收到 ROW 但本节点类型为 {}", invoker.getKind());
            }
        } catch (Throwable t) {
            log.error("处理 Row 失败", t);
        }
    }

    /**
     * 发送一行数据到指定 output_port。
     */
    public void sendRow(Value value, int outputPort) {
        if (sendObserver == null) return;
        DataRow row = DataRow.newBuilder()
                .setRowId(UUID.randomUUID().toString())
                .setValue(value)
                .build();
        ActorEnvelope env = ActorEnvelope.newBuilder()
                .setRow(row)
                .setOutputPort(outputPort)
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
