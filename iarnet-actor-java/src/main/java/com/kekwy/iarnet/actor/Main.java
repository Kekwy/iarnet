package com.kekwy.iarnet.actor;

import com.kekwy.iarnet.actor.runtime.DelegatingInvokeHandler;
import com.kekwy.iarnet.actor.runtime.FunctionDescriptorLoader;
import com.kekwy.iarnet.actor.runtime.JavaInvokeHandler;
import com.kekwy.iarnet.actor.runtime.SerializedFunctionInvokeHandler;
import com.kekwy.iarnet.actor.runtime.UserJarLoader;
import com.kekwy.iarnet.actor.runtime.handlers.EchoInvokeHandler;
import com.kekwy.iarnet.actor.runtime.handlers.PrintInvokeHandler;
import com.kekwy.iarnet.proto.actor.ActorDirective;
import com.kekwy.iarnet.proto.actor.ActorInvokeRequest;
import com.kekwy.iarnet.proto.actor.ActorInvokeResponse;
import com.kekwy.iarnet.proto.agent.LocalAgentMessage;
import com.kekwy.iarnet.proto.agent.LocalAgentServiceGrpc;
import com.kekwy.iarnet.proto.agent.LocalRegisterActor;
import com.kekwy.iarnet.proto.agent.RowOutput;
import com.kekwy.iarnet.proto.ir.DataType;
import com.kekwy.iarnet.proto.ir.Row;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * Java Actor 容器进程入口（纯 Java，无 Spring Boot）。
 * <p>
 * 启动流程：
 * <ol>
 *   <li>加载执行函数（从 FunctionDescriptor 文件 / 用户 JAR / 默认 Echo）</li>
 *   <li>回连本机 Device Agent（LocalAgentService.LocalChannel），注册 actorAddr</li>
 *   <li>Ready 由 Device Agent 经 SignalingChannel 上报；指令由控制平面 → SignalingChannel → Device Agent → LocalChannel → 本 Observer 的 directiveHandler</li>
 * </ol>
 */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final String ENV_FUNCTION_FILE = "IARNET_ACTOR_FUNCTION_FILE";
    private static final String ENV_NODE_KIND = "IARNET_NODE_KIND";
    private static final String ENV_SINK_KIND = "IARNET_SINK_KIND";
    private static final String ENV_OPERATOR_KIND = "IARNET_OPERATOR_KIND";

    private static volatile LocalAgentStreamObserver streamObserver;

    public static void main(String[] args) throws InterruptedException {
        JavaInvokeHandler initialHandler = createHandler();
        DelegatingInvokeHandler delegatingHandler = new DelegatingInvokeHandler(initialHandler);

        String agentAddr = System.getenv("IARNET_DEVICE_AGENT_ADDR");
        String actorAddr = System.getenv("IARNET_ACTOR_ADDR");

        streamObserver = new LocalAgentStreamObserver(
                row -> handleUpstreamRow(row, delegatingHandler), delegatingHandler, Main::handleDirective);

        // 从启动时加载的 handler 提取入参/返回值类型（assign_function 路径会覆盖）
        if (initialHandler instanceof SerializedFunctionInvokeHandler sfh) {
            streamObserver.setRowTargetType(sfh.getInputType());
            streamObserver.setRowOutputType(sfh.getReturnType());
            log.info("启动时提取函数类型: inputType={}, returnType={}", sfh.getInputType().getName(), sfh.getReturnType().getName());
        }

        // 初始化算子语义：优先从环境变量读取，否则从 handler 返回值类型推断
        OperatorSemantics semantics = OperatorSemantics.fromEnvString(System.getenv(ENV_OPERATOR_KIND));
        if (semantics == null && initialHandler instanceof SerializedFunctionInvokeHandler sfh2) {
            semantics = OperatorSemantics.inferFromReturnType(sfh2.getReturnType());
        }
        if (semantics != null) {
            streamObserver.setOperatorSemantics(semantics);
            log.info("启动时算子语义: {}", semantics);
        }

        ManagedChannel agentChannel = connectToDeviceAgent(agentAddr, actorAddr, streamObserver);

        log.info("Actor 启动成功，并已连接本机 Device Agent（指令经 SignalingChannel → Device Agent → LocalChannel 下发）");

        CountDownLatch latch = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("收到关闭信号，准备退出 Actor");
            agentChannel.shutdown();
            latch.countDown();
        }));

        latch.await();
        log.info("Actor 进程退出");
    }

    private static JavaInvokeHandler createHandler() {
        String nodeKind = System.getenv(ENV_NODE_KIND);
        if (nodeKind != null && nodeKind.equalsIgnoreCase("SINK")) {
            String sinkKind = System.getenv(ENV_SINK_KIND);
            if (sinkKind != null && sinkKind.equalsIgnoreCase("PRINT")) {
                log.info("检测到 Sink=PRINT，使用内建 PrintInvokeHandler");
                return new PrintInvokeHandler();
            }
        }

        String functionFile = System.getenv(ENV_FUNCTION_FILE);
        if (functionFile != null && !functionFile.isBlank()) {
            JavaInvokeHandler fromFile = FunctionDescriptorLoader.fromFile(functionFile.trim());
            if (fromFile != null) {
                return fromFile;
            }
            log.warn("从 {} 加载失败，尝试其他方式", ENV_FUNCTION_FILE);
        }
        JavaInvokeHandler fromJar = UserJarLoader.loadFromEnv();
        if (fromJar != null) {
            return fromJar;
        }
        return new EchoInvokeHandler();
    }

    private static ManagedChannel connectToDeviceAgent(String agentAddr, String actorAddr,
                                             LocalAgentStreamObserver observer) {
        if (agentAddr == null || agentAddr.isBlank()) {
            throw new RuntimeException("IARNET_DEVICE_AGENT_ADDR 未配置");
        }
        if (actorAddr == null) {
            throw new RuntimeException("没有 actor 地址");
        }

        try {
            ManagedChannel channel = ManagedChannelBuilder
                    .forTarget(agentAddr)
                    .usePlaintext()
                    .build();

            LocalAgentServiceGrpc.LocalAgentServiceStub stub =
                    LocalAgentServiceGrpc.newStub(channel);

            StreamObserver<LocalAgentMessage> sendStream = stub.localChannel(observer);
            observer.setSendStream(sendStream);

            LocalRegisterActor reg = LocalRegisterActor.newBuilder()
                    .setActorAddr(actorAddr)
                    .build();
            sendStream.onNext(LocalAgentMessage.newBuilder()
                    .setRegisterActor(reg)
                    .build());

            log.info("已向 Device Agent 注册 Actor: actorAddr='{}',  agent={}", actorAddr, agentAddr);

            return channel;
        } catch (Exception e) {
            log.error("连接 Device Agent 失败: addr={}, error={}", agentAddr, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * 处理经 Device Agent 转发的 ActorDirective（控制平面经 SignalingChannel → Device Agent → LocalChannel）。
     */
    public static void handleDirective(ActorDirective directive) {
        var payloadCase = directive.getPayloadCase();
        log.info("handleDirective 被调用: payloadCase={}", payloadCase);
        switch (payloadCase) {
            case START_SOURCE -> startSource();
            case STOP -> log.info("收到 StopDirective: {}", directive.getStop().getReason());
            case ACK -> log.debug("收到 Ack: {}", directive.getAck().getAckFor());
            case PAYLOAD_NOT_SET -> log.warn("收到 PAYLOAD_NOT_SET 的 ActorDirective（可能控制平面未正确设置 start_source）");
            default -> log.info("收到未处理的 Directive: {}", payloadCase);
        }
    }

    /**
     * Constant Source 触发逻辑：将缓存的 source rows 逐行通过 LocalChannel 发送给 Device Agent，
     * 由 Device Agent 路由转发给下游 Actor。
     */
    private static void startSource() {
        log.info("收到 StartSourceDirective，Source Actor 开始执行");

        LocalAgentStreamObserver obs = streamObserver;
        if (obs == null) {
            log.error("streamObserver 未初始化，无法发送数据");
            return;
        }

        StreamObserver<LocalAgentMessage> sendStream = obs.getSendStream();
        if (sendStream == null) {
            log.error("sendStream 未初始化，无法发送数据");
            return;
        }

        List<Row> rows = obs.getSourceRows();
        if (rows.isEmpty()) {
            log.warn("Source Actor 没有可发送的 rows（source_config 为空或未收到）");
            return;
        }

        log.info("开始发送 {} 行 constant source 数据", rows.size());
        int sent = 0;
        for (Row row : rows) {
            try {
                RowOutput output = RowOutput.newBuilder().setRow(row).build();
                sendStream.onNext(LocalAgentMessage.newBuilder().setRowOutput(output).build());
                sent++;
            } catch (Exception e) {
                log.error("发送 row_output 失败: row #{}", sent, e);
                break;
            }
        }
        log.info("Source Actor 已发送 {}/{} 行数据", sent, rows.size());
    }

    /**
     * 收到上游 ROW_DELIVERY 时调用：将反序列化后的行对象封装为 ActorInvokeRequest，
     * 交给当前 handler（算子/Sink）处理，并根据算子语义（MAP / FILTER / FLAT_MAP）决定如何将结果路由到下游。
     */
    private static void handleUpstreamRow(Object rowObj, DelegatingInvokeHandler delegatingHandler) {
        if (rowObj == null || delegatingHandler == null) return;
        try {
            byte[] payload = javaSerialize(rowObj);
            ActorInvokeRequest req = ActorInvokeRequest.newBuilder()
                    .setInvocationId(UUID.randomUUID().toString())
                    .setPayload(ByteString.copyFrom(payload))
                    .build();
            ActorInvokeResponse resp = delegatingHandler.handle(req);

            if (resp == null || resp.getPayload() == null || resp.getPayload().isEmpty()) return;
            LocalAgentStreamObserver obs = streamObserver;
            if (obs == null || obs.getSendStream() == null) return;

            OperatorSemantics semantics = obs.getOperatorSemantics();
            if (semantics == null) semantics = OperatorSemantics.MAP;

            switch (semantics) {
                case FILTER -> {
                    Object result = javaDeserialize(resp.getPayload().toByteArray());
                    if (Boolean.TRUE.equals(result)) {
                        log.debug("FILTER 通过，转发原始输入: invocationId={}", req.getInvocationId());
                        trySendSingleOutput(obs.getSendStream(), rowObj);
                    } else {
                        log.debug("FILTER 未通过，丢弃: invocationId={}", req.getInvocationId());
                    }
                }
                case FLAT_MAP -> {
                    Object result = javaDeserialize(resp.getPayload().toByteArray());
                    if (result instanceof Iterable<?> iterable) {
                        int sentCount = 0;
                        for (Object element : iterable) {
                            if (trySendSingleOutput(obs.getSendStream(), element)) {
                                sentCount++;
                            }
                        }
                        log.debug("FLAT_MAP 展开发送 {} 个元素: invocationId={}", sentCount, req.getInvocationId());
                    } else if (result != null) {
                        log.warn("FLAT_MAP 算子返回了非 Iterable 类型: {}，尝试作为单元素发送", result.getClass().getName());
                        trySendSingleOutput(obs.getSendStream(), result);
                    }
                }
                default -> {
                    sendMapOutputToDownstream(resp.getPayload());
                }
            }
        } catch (Exception e) {
            log.error("处理上游 Row 失败", e);
        }
    }

    /**
     * MAP 语义：将算子返回的 Java 序列化 payload 反序列化为对象，再按 Row 格式重新编码发给下游。
     */
    private static void sendMapOutputToDownstream(ByteString responsePayload) {
        if (responsePayload == null || responsePayload.isEmpty()) return;
        LocalAgentStreamObserver obs = streamObserver;
        if (obs == null) return;
        StreamObserver<LocalAgentMessage> send = obs.getSendStream();
        if (send == null) return;
        try {
            Object outputObj = javaDeserialize(responsePayload.toByteArray());
            if (outputObj == null) {
                log.warn("MAP 算子返回值反序列化结果为 null，跳过发送");
                return;
            }
            if (!trySendSingleOutput(send, outputObj)) {
                Class<?> outputType = obs.getRowOutputType();
                log.warn("无法为返回值类型 {} 推断 data_type，跳过向下游发送",
                        outputType != null ? outputType : outputObj.getClass());
            }
        } catch (Exception e) {
            log.warn("向下游发送 row_output 失败", e);
        }
    }

    private static boolean trySendSingleOutput(StreamObserver<LocalAgentMessage> send, Object value) throws Exception {
        if (value == null) return false;
        DataType dataType = DownstreamRowEncoder.dataTypeFromClass(value.getClass());
        if (dataType == null) return false;

        byte[] encoded = DownstreamRowEncoder.encode(value, dataType);
        Row row = Row.newBuilder()
                .setValue(ByteString.copyFrom(encoded))
                .setDataType(dataType)
                .build();
        send.onNext(LocalAgentMessage.newBuilder()
                .setRowOutput(RowOutput.newBuilder().setRow(row).build())
                .build());
        return true;
    }

    private static Object javaDeserialize(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return ois.readObject();
        } catch (Exception e) {
            log.warn("Java 反序列化算子返回值失败", e);
            return null;
        }
    }

    private static byte[] javaSerialize(Object obj) throws Exception {
        if (obj == null) return new byte[0];
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
        }
        return bos.toByteArray();
    }
}
