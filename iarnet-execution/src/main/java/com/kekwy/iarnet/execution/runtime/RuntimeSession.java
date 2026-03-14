package com.kekwy.iarnet.execution.runtime;

import com.kekwy.iarnet.proto.actor.ActorEnvelope;
import com.kekwy.iarnet.proto.actor.DataRow;
import com.kekwy.iarnet.proto.actor.InvokeRequest;
import com.kekwy.iarnet.proto.actor.StartInputCommand;
import com.kekwy.iarnet.proto.workflow.WorkflowInput;
import com.kekwy.iarnet.proto.ValueCodec;
import com.kekwy.iarnet.fabric.actor.ActorInstanceRef;
import com.kekwy.iarnet.execution.RuntimeNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单次工作流运行的会话，持有运行图与输入定义。
 * 工作流输入参数定义（{@link #getWorkflowInputs()}）及入口节点与参数名的映射（{@link #getNodeIdToInputParamName()}）
 * 在提交时从 WorkflowGraph 解析并保存，供后续 {@link WorkflowRuntime#execute} 注入实际值时使用。
 */
public class RuntimeSession {

    private static final Logger log = LoggerFactory.getLogger(RuntimeSession.class);

    private final RuntimeGraph runtimeGraph;
    /** 工作流输入参数定义（名称 + 类型），来自 WorkflowGraph.inputs */
    private final List<WorkflowInput> workflowInputs;
    /** 入口节点 ID -> 关联的工作流输入参数名，来自 Node.input_param */
    private final Map<String, String> nodeIdToInputParamName;

    /** 轮询计数器，用于在入口节点的多个 actor 间负载均衡选取一个下发输入 */
    private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

    public RuntimeSession(RuntimeGraph runtimeGraph, List<WorkflowInput> workflowInputs,
                          Map<String, String> nodeIdToInputParamName) {
        this.runtimeGraph = runtimeGraph;
        this.workflowInputs = workflowInputs != null ? List.copyOf(workflowInputs) : List.of();
        this.nodeIdToInputParamName = nodeIdToInputParamName != null ? Map.copyOf(nodeIdToInputParamName) : Map.of();
    }

    public RuntimeGraph getRuntimeGraph() {
        return runtimeGraph;
    }

    /** 工作流输入参数定义（名称 + 类型）。 */
    public List<WorkflowInput> getWorkflowInputs() {
        return workflowInputs;
    }

    /** 入口节点 ID -> 工作流输入参数名，用于将 execute 传入的 inputs 映射到对应节点。 */
    public Map<String, String> getNodeIdToInputParamName() {
        return nodeIdToInputParamName;
    }

    /**
     * 处理本次执行请求：生成提交 ID，向各直接接收输入的入口节点按负载均衡各选一个 actor 下发其所需参数值。
     *
     * @param inputs 参数名到值的映射（已由 runtime 校验与工作流输入定义一致）
     * @return 本次提交的 submissionId，供后续查询或取消使用
     */
    public String execute(Map<String, Object> inputs) {
        String submissionId = UUID.randomUUID().toString();
        for (RuntimeNode inputNode : getRuntimeGraph().getInputNodes()) {
            String paramName = nodeIdToInputParamName.get(inputNode.nodeId());
            if (paramName == null) {
                continue;
            }
            Object value = inputs.get(paramName);
            com.kekwy.iarnet.proto.common.Value encoded = ValueCodec.encode(value);
            DataRow row = DataRow.newBuilder()
                    .setRowId(submissionId)
                    .setValue(encoded)
                    .build();
            InvokeRequest request = InvokeRequest.newBuilder()
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
            log.info("下发输入: submissionId={}, nodeId={}, param={}, actorId={}",
                    submissionId, inputNode.nodeId(), paramName, ref.getActorId());
            ref.send(envelope);
        }
        return submissionId;
    }

    public void handleActorMessage(String actorId, ActorEnvelope message) {

    }

    /**
     * 向所有 input node 的 actor 发送 StartInputCommand，启动数据流。
     */
    public void start() {
        ActorEnvelope startEnvelope = ActorEnvelope.newBuilder()
                .setStartInputCommand(StartInputCommand.getDefaultInstance())
                .build();

        for (RuntimeNode inputNode : getRuntimeGraph().getInputNodes()) {
            for (ActorInstanceRef ref : inputNode.actorInstanceRefs()) {
                log.info("发送 StartInputCommand: nodeId={}, actorId={}",
                        inputNode.nodeId(), ref.getActorId());
                ref.send(startEnvelope);
            }
        }
    }
}
