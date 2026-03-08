package com.kekwy.iarnet.actor;

import com.kekwy.iarnet.actor.runtime.DelegatingInvokeHandler;
import com.kekwy.iarnet.actor.runtime.FunctionDescriptorLoader;
import com.kekwy.iarnet.actor.runtime.JavaInvokeHandler;
import com.kekwy.iarnet.actor.runtime.SerializedFunctionInvokeHandler;
import com.kekwy.iarnet.proto.actor.ActorDirective;
import com.kekwy.iarnet.proto.agent.LocalAgentMessage;
import com.kekwy.iarnet.proto.ir.Row;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;


public class LocalAgentStreamObserver implements StreamObserver<LocalAgentMessage> {

    private static final Logger log = LoggerFactory.getLogger(LocalAgentStreamObserver.class);

    private final Consumer<Object> rowHandler;
    private final DelegatingInvokeHandler delegatingHandler;
    private final Consumer<ActorDirective> directiveHandler;
    private volatile Class<?> rowTargetType;
    /** 当前 handler 的返回值类型（用于下行 Row 携带 data_type），仅 SerializedFunctionInvokeHandler 时设置 */
    private volatile Class<?> rowOutputType;

    /** 当前算子的语义类型（MAP / FILTER / FLAT_MAP），决定 UDF 返回值如何路由到下游 */
    private volatile OperatorSemantics operatorSemantics;

    /** Source Actor 收到的 constant rows（由 Device Agent 下发的 source_config） */
    private final List<Row> sourceRows = new CopyOnWriteArrayList<>();

    /** LocalChannel 发送端引用（Actor → Device Agent 方向），用于发送 row_output 等 */
    private volatile StreamObserver<LocalAgentMessage> sendStream;

    public LocalAgentStreamObserver() {
        this(null, null, null);
    }

    public LocalAgentStreamObserver(Consumer<Object> rowHandler) {
        this(rowHandler, null, null);
    }

    public LocalAgentStreamObserver(Consumer<Object> rowHandler, DelegatingInvokeHandler delegatingHandler) {
        this(rowHandler, delegatingHandler, null);
    }

    /**
     * @param directiveHandler 收到控制平面经 Device Agent 转发的 ActorDirective（如 StartSourceDirective）时调用
     */
    public LocalAgentStreamObserver(Consumer<Object> rowHandler, DelegatingInvokeHandler delegatingHandler,
                                    Consumer<ActorDirective> directiveHandler) {
        this.rowHandler = rowHandler;
        this.delegatingHandler = delegatingHandler;
        this.directiveHandler = directiveHandler;
    }

    /**
     * 注入 LocalChannel 发送端，供 Actor 主动向 Device Agent 发送消息。
     */
    public void setSendStream(StreamObserver<LocalAgentMessage> sendStream) {
        this.sendStream = sendStream;
    }

    public StreamObserver<LocalAgentMessage> getSendStream() {
        return sendStream;
    }

    public List<Row> getSourceRows() {
        return sourceRows;
    }

    public void setRowTargetType(Class<?> rowTargetType) {
        this.rowTargetType = rowTargetType;
    }

    public void setRowOutputType(Class<?> rowOutputType) {
        this.rowOutputType = rowOutputType;
    }

    public Class<?> getRowOutputType() {
        return rowOutputType;
    }

    public void setOperatorSemantics(OperatorSemantics operatorSemantics) {
        this.operatorSemantics = operatorSemantics;
    }

    public OperatorSemantics getOperatorSemantics() {
        return operatorSemantics;
    }

    @Override
    public void onNext(LocalAgentMessage value) {
        log.info("LocalChannel 收到消息: payloadCase={}", value.getPayloadCase());
        switch (value.getPayloadCase()) {
            case ROW_DELIVERY:
                if (rowHandler != null) {
                    Object obj = UpstreamRowDeserializer.toObject(value.getRowDelivery(), rowTargetType);
                    if (obj != null) {
                        rowHandler.accept(obj);
                    }
                }
                break;
            case ASSIGN_FUNCTION:
                if (delegatingHandler != null) {
                    JavaInvokeHandler loaded = FunctionDescriptorLoader.fromDescriptor(value.getAssignFunction());
                    if (loaded != null) {
                        delegatingHandler.setDelegate(loaded);
                        if (loaded instanceof SerializedFunctionInvokeHandler sfh) {
                            this.rowTargetType = sfh.getInputType();
                            this.rowOutputType = sfh.getReturnType();
                            this.operatorSemantics = OperatorSemantics.inferFromReturnType(sfh.getReturnType());
                            log.info("已从用户函数提取入参类型: {}, 返回值类型: {}, 算子语义: {}",
                                    rowTargetType.getName(), rowOutputType.getName(), operatorSemantics);
                        }
                        log.info("已根据 assign_function 切换执行函数");
                    } else {
                        log.warn("assign_function 加载失败，继续使用当前 handler");
                    }
                } else {
                    log.debug("未注入 delegatingHandler，忽略 assign_function");
                }
                break;
            case ACTOR_DIRECTIVE:
                if (directiveHandler != null) {
                    var directive = value.getActorDirective();
                    log.info("收到 ActorDirective，转发给 handleDirective: payloadCase={}", directive.getPayloadCase());
                    directiveHandler.accept(directive);
                } else {
                    log.warn("未注入 directiveHandler，忽略 actor_directive");
                }
                break;
            case SOURCE_CONFIG:
                var config = value.getSourceConfig();
                sourceRows.addAll(config.getRowsList());
                log.info("收到 source_config: {} 行数据已缓存", config.getRowsCount());
                break;
            default:
                break;
        }
    }

    @Override
    public void onError(Throwable t) {
        log.warn("LocalAgent LocalChannel 出错: {}", t.getMessage());
    }

    @Override
    public void onCompleted() {
        log.info("LocalAgent LocalChannel 已结束");
    }
}
