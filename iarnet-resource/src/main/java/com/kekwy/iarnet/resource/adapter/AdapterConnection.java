package com.kekwy.iarnet.resource.adapter;

import com.kekwy.iarnet.proto.adapter.Command;
import com.kekwy.iarnet.proto.adapter.CommandError;
import com.kekwy.iarnet.proto.adapter.CommandResponse;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 封装单个 Adapter 的 CommandChannel 双向流连接。
 * <p>
 * 提供基于 {@code request_id} 的请求-响应关联机制：
 * 调用 {@link #sendCommand} 发出命令后会得到一个 {@link CompletableFuture}，
 * 当 Adapter 回传对应 {@code request_id} 的响应时自动 complete。
 */
public class AdapterConnection {

    private static final Logger log = LoggerFactory.getLogger(AdapterConnection.class);
    private static final long DEFAULT_TIMEOUT_SECONDS = 60;

    private final String adapterId;
    private final StreamObserver<Command> commandSender;
    private final ConcurrentHashMap<String, CompletableFuture<CommandResponse>> pendingRequests =
            new ConcurrentHashMap<>();

    private volatile boolean closed = false;

    public AdapterConnection(String adapterId, StreamObserver<Command> commandSender) {
        this.adapterId = adapterId;
        this.commandSender = commandSender;
    }

    public String getAdapterId() {
        return adapterId;
    }

    public boolean isClosed() {
        return closed;
    }

    /**
     * 向 Adapter 发送命令，返回异步结果。
     *
     * @param commandBuilder 已设置好 payload 的 Command.Builder（request_id 由本方法自动填充）
     * @return 包含 Adapter 响应的 Future
     */
    public CompletableFuture<CommandResponse> sendCommand(Command.Builder commandBuilder) {
        return sendCommand(commandBuilder, DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    public CompletableFuture<CommandResponse> sendCommand(Command.Builder commandBuilder,
                                                          long timeout, TimeUnit unit) {
        if (closed) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("AdapterConnection 已关闭: " + adapterId));
        }

        String requestId = UUID.randomUUID().toString();
        Command command = commandBuilder.setRequestId(requestId).build();

        CompletableFuture<CommandResponse> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        future.orTimeout(timeout, unit).whenComplete((resp, ex) -> {
            pendingRequests.remove(requestId);
            if (ex != null) {
                log.warn("命令超时或异常: adapterId={}, requestId={}", adapterId, requestId);
            }
        });

        try {
            synchronized (commandSender) {
                commandSender.onNext(command);
            }
        } catch (Exception e) {
            pendingRequests.remove(requestId);
            future.completeExceptionally(e);
        }

        return future;
    }

    /**
     * 处理从 Adapter 回传的响应，匹配并完成对应的 Future。
     */
    public void onResponse(CommandResponse response) {
        String requestId = response.getRequestId();
        CompletableFuture<CommandResponse> future = pendingRequests.remove(requestId);
        if (future != null) {
            if (response.getPayloadCase() == CommandResponse.PayloadCase.ERROR) {
                CommandError error = response.getError();
                future.completeExceptionally(new RuntimeException(
                        "Adapter 返回错误: " + error.getMessage()));
            } else {
                future.complete(response);
            }
        } else {
            log.warn("收到无法匹配的响应: adapterId={}, requestId={}", adapterId, requestId);
        }
    }

    /**
     * 关闭连接，取消所有待处理请求。
     */
    public void close() {
        if (closed) return;
        closed = true;

        RuntimeException cause = new RuntimeException("AdapterConnection 已关闭: " + adapterId);
        pendingRequests.values().forEach(f -> f.completeExceptionally(cause));
        pendingRequests.clear();

        try {
            synchronized (commandSender) {
                commandSender.onCompleted();
            }
        } catch (Exception e) {
            log.debug("关闭 commandSender 时出现异常: adapterId={}", adapterId, e);
        }

        log.info("AdapterConnection 已关闭: adapterId={}", adapterId);
    }
}
