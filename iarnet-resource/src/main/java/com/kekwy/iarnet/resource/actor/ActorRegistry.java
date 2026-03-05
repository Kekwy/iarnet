package com.kekwy.iarnet.resource.actor;

import com.kekwy.iarnet.proto.actor.ActorDirective;
import com.kekwy.iarnet.proto.actor.ActorReadyReport;
import com.kekwy.iarnet.proto.actor.ActorReport;
import io.grpc.stub.StreamObserver;

import java.util.List;

/**
 * Actor 控制通道注册表：维护所有已建立 ControlChannel 的 Actor 连接。
 * <p>
 * 由 control-plane 持有，供 gRPC 服务、调度器等组件使用。
 */
public interface ActorRegistry {

    /**
     * Actor 建立 ControlChannel 时调用，完成注册并创建 {@link ActorConnection}。
     *
     * @param actorId          Actor 唯一标识
     * @param ready            首条消息中的 ActorReadyReport
     * @param directiveSender 用于向该 Actor 推送 ActorDirective 的流
     */
    void onActorConnected(String actorId, ActorReadyReport ready,
                          StreamObserver<ActorDirective> directiveSender);

    /**
     * Actor 控制通道断开时调用。
     */
    void onActorDisconnected(String actorId);

    /**
     * 处理 Actor 上报的消息（心跳、状态变更、指标等）。
     */
    void handleReport(String actorId, ActorReport report);

    /**
     * 向指定 Actor 发送指令（fire-and-forget）。
     *
     * @throws IllegalStateException 若该 Actor 无活跃连接
     */
    void sendDirective(String actorId, ActorDirective directive);

    /**
     * 获取指定 Actor 的会话信息，未注册则返回 null。
     */
    ActorSession getSession(String actorId);

    /**
     * 查询指定工作流下所有在线 Actor 的会话列表。
     */
    List<ActorSession> listActorsByWorkflow(String workflowId);

    /**
     * 注册 Actor 生命周期事件监听器。
     */
    void addListener(ActorLifecycleListener listener);

    /**
     * 移除监听器。
     */
    void removeListener(ActorLifecycleListener listener);
}
