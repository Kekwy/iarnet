package com.kekwy.iarnet.actor;

import com.kekwy.iarnet.actor.runtime.DelegatingInvokeHandler;
import com.kekwy.iarnet.actor.runtime.FunctionDescriptorLoader;
import com.kekwy.iarnet.actor.runtime.JavaInvokeHandler;
import com.kekwy.iarnet.actor.runtime.SerializedFunctionInvokeHandler;
import com.kekwy.iarnet.proto.agent.LocalAgentMessage;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;


public class LocalAgentStreamObserver implements StreamObserver<LocalAgentMessage> {

    private static final Logger log = LoggerFactory.getLogger(LocalAgentStreamObserver.class);

    private final Consumer<Object> rowHandler;
    private final DelegatingInvokeHandler delegatingHandler;
    private volatile Class<?> rowTargetType;

    public LocalAgentStreamObserver() {
        this(null, null);
    }

    /** @param rowHandler 收到上游 row 时，将反序列化后的 Java 对象传入；可为 null 表示不处理 */
    public LocalAgentStreamObserver(Consumer<Object> rowHandler) {
        this(rowHandler, null);
    }

    /**
     * 若传入 delegatingHandler，则收到服务端下发的 assign_function 时会从 IR FunctionDescriptor 加载 handler 并替换委托。
     */
    public LocalAgentStreamObserver(Consumer<Object> rowHandler, DelegatingInvokeHandler delegatingHandler) {
        this.rowHandler = rowHandler;
        this.delegatingHandler = delegatingHandler;
    }

    /**
     * 设置 Row 反序列化的目标 Java 类型（来自用户函数入参）。
     * 在 assign_function 加载成功后自动更新；也可由外部显式设置。
     */
    public void setRowTargetType(Class<?> rowTargetType) {
        this.rowTargetType = rowTargetType;
    }

    @Override
    public void onNext(LocalAgentMessage value) {
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
                            log.info("已从用户函数提取入参类型: {}", rowTargetType.getName());
                        }
                        log.info("已根据 assign_function 切换执行函数");
                    } else {
                        log.warn("assign_function 加载失败，继续使用当前 handler");
                    }
                } else {
                    log.debug("未注入 delegatingHandler，忽略 assign_function");
                }
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
