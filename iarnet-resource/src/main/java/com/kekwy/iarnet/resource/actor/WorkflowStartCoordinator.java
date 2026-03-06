package com.kekwy.iarnet.resource.actor;

import com.kekwy.iarnet.proto.actor.ActorDirective;
import com.kekwy.iarnet.proto.actor.ActorReadyReport;
import com.kekwy.iarnet.proto.actor.StartSourceDirective;
import com.kekwy.iarnet.proto.ir.NodeKind;
import com.kekwy.iarnet.resource.model.ActorDeployment;
import com.kekwy.iarnet.resource.model.ActorInstance;
import com.kekwy.iarnet.resource.model.PhysicalWorkflowGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 协调工作流启动时机：
 * - schedule() 完成部署后，注册每个 workflow 的 Actor 列表与 Source Actor 集合；
 * - 当该 workflow 的所有 Actor 都通过 ControlChannel 报 Ready 时，
 *   向该 workflow 的所有 Source Actor 下发 StartSource 指令。
 *
 * 当前仅依据 ReadyReport 判断“部署完成”，后续可在建立下游连接成功后再补充触发条件。
 */
@Service
public class WorkflowStartCoordinator implements ActorLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(WorkflowStartCoordinator.class);

    private final ActorRegistry actorRegistry;

    /**
     * workflowId -> 工作流状态
     */
    private final Map<String, WorkflowState> workflows = new ConcurrentHashMap<>();

    public WorkflowStartCoordinator(ActorRegistry actorRegistry) {
        this.actorRegistry = actorRegistry;
        this.actorRegistry.addListener(this);
    }

    /**
     * 在调度完成后注册物理工作流信息。
     */
    public void registerWorkflow(PhysicalWorkflowGraph graph) {
        String workflowId = graph.workflowId();
        WorkflowState state = new WorkflowState(workflowId);

        for (ActorDeployment dep : graph.deployments()) {
            boolean isSource = dep.nodeKind() == NodeKind.SOURCE;
            for (ActorInstance inst : dep.instances()) {
                String actorId = inst.actorId();
                state.allActorIds.add(actorId);
                if (isSource) {
                    state.sourceActorIds.add(actorId);
                }
            }
        }

        if (state.allActorIds.isEmpty()) {
            log.warn("registerWorkflow 时发现 workflow 无 Actor: workflowId={}", workflowId);
            return;
        }

        workflows.put(workflowId, state);
        log.info("已注册 workflow: workflowId={}, actors={}, sources={}",
                workflowId, state.allActorIds.size(), state.sourceActorIds.size());
    }

    @Override
    public void onActorReady(String actorId, ActorReadyReport report) {
        String workflowId = report.getWorkflowId();
        if (workflowId == null || workflowId.isBlank()) {
            return;
        }
        WorkflowState state = workflows.get(workflowId);
        if (state == null) {
            // 可能是旧 workflow 或尚未注册的 workflow，忽略
            return;
        }

        state.readyActorIds.add(actorId);
        log.debug("Actor Ready: workflowId={}, actorId={}, readyCount={}/{}",
                workflowId, actorId, state.readyActorIds.size(), state.allActorIds.size());

        maybeStartSources(state);
    }

    /**
     * 当 Device Agent 上报某条 Actor 间通道已建立时调用。
     * 当前简化为：任意一条边连接成功即可认为该 workflow 的链路已就绪。
     */
    public void onChannelConnected(String workflowId, String srcActorAddr, String dstActorAddr) {
        WorkflowState state = workflows.get(workflowId);
        if (state == null) {
            return;
        }
        state.edgesConnected = true;
        log.info("WorkflowStartCoordinator: 检测到链路已建立(简化): workflowId={}, src={}, dst={}",
                workflowId, srcActorAddr, dstActorAddr);
        maybeStartSources(state);
    }

    @Override
    public void onActorDisconnected(String actorId) {
        // 当前不在断连时重启 Source，后续可按需扩展
    }

    @Override
    public void onActorStatusChanged(String actorId,
                                     com.kekwy.iarnet.proto.actor.ActorStatusChange change) {
        // 暂不处理状态变更，仅基于 Ready 触发 StartSource
    }

    private void startSources(WorkflowState state) {
        state.started = true;
        if (state.sourceActorIds.isEmpty()) {
            log.info("workflow 无 Source Actor，无需下发 StartSource: workflowId={}", state.workflowId);
            return;
        }

        log.info("所有 Actor 已 Ready，开始下发 StartSource 指令: workflowId={}, sources={}",
                state.workflowId, state.sourceActorIds.size());

        ActorDirective directive = ActorDirective.newBuilder()
                .setStartSource(StartSourceDirective.getDefaultInstance())
                .build();

        for (String actorId : state.sourceActorIds) {
            try {
                actorRegistry.sendDirective(actorId, directive);
                log.info("StartSource 指令已下发: workflowId={}, actorId={}", state.workflowId, actorId);
            } catch (Exception e) {
                log.warn("StartSource 指令下发失败: workflowId={}, actorId={}", state.workflowId, actorId, e);
            }
        }
    }

    private static final class WorkflowState {
        final String workflowId;
        final Set<String> allActorIds = ConcurrentHashMap.newKeySet();
        final Set<String> sourceActorIds = ConcurrentHashMap.newKeySet();
        final Set<String> readyActorIds = ConcurrentHashMap.newKeySet();
        volatile boolean started = false;
        /** 简化版：是否已有任意一条链路被报告为 connected。 */
        volatile boolean edgesConnected = false;

        WorkflowState(String workflowId) {
            this.workflowId = workflowId;
        }

        Set<String> getAllActorIds() {
            return Collections.unmodifiableSet(allActorIds);
        }

        Set<String> getSourceActorIds() {
            return Collections.unmodifiableSet(sourceActorIds);
        }
    }

    /**
     * 在所有 Actor Ready 且链路已建立后触发 Source 启动。
     */
    private void maybeStartSources(WorkflowState state) {
        if (!state.started
                && state.edgesConnected
                && state.readyActorIds.containsAll(state.allActorIds)) {
            startSources(state);
        }
    }
}

