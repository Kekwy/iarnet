package com.kekwy.iarnet.resource.adapter;

import com.kekwy.iarnet.proto.adapter.Command;
import com.kekwy.iarnet.proto.adapter.CommandResponse;
import com.kekwy.iarnet.proto.adapter.RegisterRequest;
import com.kekwy.iarnet.proto.adapter.ResourceCapacity;
import io.grpc.stub.StreamObserver;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Adapter 注册表：维护所有已注册 Adapter 的状态与命令通道。
 * <p>
 * 由 control-plane 持有，供 gRPC 服务、调度器等组件使用。
 */
public interface AdapterRegistry {

    /**
     * 注册新的 Adapter，返回分配的 adapter_id。
     */
    String register(RegisterRequest request);

    /**
     * 注销 Adapter。
     */
    void deregister(String adapterId);

    /**
     * 处理心跳，更新资源使用快照和最后活跃时间。
     */
    void heartbeat(String adapterId, ResourceCapacity usage);

    /**
     * 建立 CommandChannel 连接。
     *
     * @param adapterId     Adapter 标识
     * @param commandSender 用于向 Adapter 推送 Command 的流
     */
    void openCommandChannel(String adapterId, StreamObserver<Command> commandSender);

    /**
     * 处理 Adapter 回传的命令响应。
     */
    void handleCommandResponse(String adapterId, CommandResponse response);

    /**
     * 关闭 CommandChannel 连接（断开或 Adapter 主动关闭时调用）。
     */
    void closeCommandChannel(String adapterId);

    /**
     * 通过 CommandChannel 向指定 Adapter 发送命令，异步获取响应。
     */
    CompletableFuture<CommandResponse> sendCommand(String adapterId, Command.Builder commandBuilder);

    /**
     * 获取指定 Adapter 信息。
     */
    AdapterInfo getAdapter(String adapterId);

    /**
     * 列出所有已注册的 Adapter。
     */
    List<AdapterInfo> listAdapters();

    /**
     * 列出所有在线且已建立 CommandChannel 的 Adapter。
     */
    List<AdapterInfo> listOnlineAdapters();

    /**
     * 按类型筛选在线 Adapter（如 "docker"、"k8s"）。
     */
    List<AdapterInfo> listOnlineAdaptersByType(String adapterType);
}
