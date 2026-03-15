package com.kekwy.iarnet.execution.domain;

import com.kekwy.iarnet.execution.WorkflowEngine;
import com.kekwy.iarnet.proto.actor.ActorEnvelope;
import com.kekwy.iarnet.proto.actor.DataRow;
import com.kekwy.iarnet.proto.actor.InvokeRequest;
import com.kekwy.iarnet.proto.workflow.WorkflowInput;
import com.kekwy.iarnet.proto.common.Value;
import com.kekwy.iarnet.fabric.actor.ActorInstanceRef;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单次工作流运行的会话，持有运行图与输入定义。
 * 工作流输入参数定义（{@link #getWorkflowInputs()}）及入口节点与参数名的映射（{@link #getNodeIdToInputParamName()}）
 * 在提交时从 WorkflowGraph 解析并保存，供后续 {@link WorkflowEngine#execute} 注入实际值时使用。
 */
@Slf4j
public class RuntimeWorkflow {


    @Getter
    private final RuntimeGraph runtimeGraph;
    /** 工作流输入参数定义（名称 + 类型），来自 WorkflowGraph.inputs
     * -- GETTER --
     * 工作流输入参数定义（名称 + 类型）。
     */
    @Getter
    private final List<WorkflowInput> workflowInputs;
    /** 入口节点 ID -> 关联的工作流输入参数名，来自 Node.input_param
     * -- GETTER --
     * 入口节点 ID -> 工作流输入参数名，用于将 execute 传入的 inputs 映射到对应节点。
     */
    @Getter
    private final Map<String, String> nodeIdToInputParamName;

    /** 轮询计数器，用于在入口节点的多个 actor 间负载均衡选取一个下发输入 */
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    public RuntimeWorkflow(RuntimeGraph runtimeGraph, List<WorkflowInput> workflowInputs,
                           Map<String, String> nodeIdToInputParamName) {
        this.runtimeGraph = runtimeGraph;
        this.workflowInputs = workflowInputs != null ? List.copyOf(workflowInputs) : List.of();
        this.nodeIdToInputParamName = nodeIdToInputParamName != null ? Map.copyOf(nodeIdToInputParamName) : Map.of();
    }

    /**
     * 处理本次执行请求：生成提交 ID，向各直接接收输入的入口节点按负载均衡各选一个 actor 下发其所需参数值。
     *
     * @param inputs 参数名到 proto Value 的映射（已由 runtime 校验与工作流输入定义一致）
     * @return 本次提交的 submissionId，供后续查询或取消使用
     */
    public String execute(Map<String, Value> inputs) {
        String executionId = UUID.randomUUID().toString();
        for (RuntimeNode inputNode : getRuntimeGraph().getInputNodes()) {
            String paramName = nodeIdToInputParamName.get(inputNode.nodeId());
            if (paramName == null) {
                continue;
            }
            Value value = inputs.get(paramName);
            if (value == null) {
                value = Value.getDefaultInstance();
            }
            DataRow row = DataRow.newBuilder()
                    .setValue(value)
                    .build();
            InvokeRequest request = InvokeRequest.newBuilder()
                    .setExecutionId(executionId)
                    .setRow(row)
                    .setInputPort(0)
                    .build();
            ActorEnvelope envelope = ActorEnvelope.newBuilder()
                    .setRequest(request)
                    .build();

            List<ActorInstanceRef> refs = inputNode.actorInstanceRefs();
            if (refs.isEmpty()) {
                log.warn("入口节点无可用 actor，跳过: nodeId={}", inputNode.nodeId());
                continue;
            }
            int idx = Math.floorMod(roundRobinCounter.getAndIncrement(), refs.size());
            ActorInstanceRef ref = refs.get(idx);
            log.info("下发输入: executionId={}, nodeId={}, param={}, actorId={}",
                    executionId, inputNode.nodeId(), paramName, ref.getActorId());
            ref.send(envelope);
        }
        return executionId;
    }

    public void handleActorMessage(String actorId, ActorEnvelope message) {

    }

    /**
     * 已废弃：入口节点现为 Task/Output，由 execute() 下发的 Row 驱动，不再发送 StartInputCommand。
     * 保留空实现以免调用方编译报错。
     */
    @Deprecated
    public void start() {
        // no-op: entry nodes receive data via execute() -> InvokeRequest with Row
    }
}
