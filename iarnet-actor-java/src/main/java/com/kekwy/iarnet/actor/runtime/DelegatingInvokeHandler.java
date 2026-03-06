package com.kekwy.iarnet.actor.runtime;

import com.kekwy.iarnet.proto.actor.ActorInvokeRequest;
import com.kekwy.iarnet.proto.actor.ActorInvokeResponse;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 可动态替换委托的 {@link JavaInvokeHandler}。
 * 用于 Actor 启动时先用默认实现，收到 Device Agent 下发的 {@code assign_function} 后再切换为从 IR FunctionDescriptor 加载的 handler。
 */
public class DelegatingInvokeHandler implements JavaInvokeHandler {

    private final AtomicReference<JavaInvokeHandler> delegate;

    public DelegatingInvokeHandler(JavaInvokeHandler initial) {
        this.delegate = new AtomicReference<>(initial);
    }

    /**
     * 替换当前委托的 handler（如收到 assign_function 时调用）。
     */
    public void setDelegate(JavaInvokeHandler handler) {
        if (handler != null) {
            this.delegate.set(handler);
        }
    }

    @Override
    public ActorInvokeResponse handle(ActorInvokeRequest request) throws Exception {
        return delegate.get().handle(request);
    }
}
