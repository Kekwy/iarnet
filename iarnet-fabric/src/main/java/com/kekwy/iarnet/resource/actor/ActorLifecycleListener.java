package com.kekwy.iarnet.resource.actor;

import com.kekwy.iarnet.proto.actor.ActorReadyReport;
import com.kekwy.iarnet.proto.actor.ActorStatusChange;

/**
 * 上层消费 Actor 生命周期事件的钩子。
 * <p>
 * 通过 {@link ActorRegistry#addListener(ActorLifecycleListener)} 注册后，
 * 当 Actor 就绪、断连或状态变更时会收到回调。
 */
public interface ActorLifecycleListener {

    /**
     * Actor 已启动并就绪（收到首条 ActorReadyReport 后触发）。
     */
    void onActorReady(String actorId, ActorReadyReport report);

    /**
     * Actor 控制通道已断开。
     */
    void onActorDisconnected(String actorId);

    /**
     * Actor 上报了状态变更（如 error、recovered、draining）。
     */
    void onActorStatusChanged(String actorId, ActorStatusChange change);
}
