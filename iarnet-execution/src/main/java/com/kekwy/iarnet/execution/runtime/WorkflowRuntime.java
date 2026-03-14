package com.kekwy.iarnet.execution.runtime;

import com.kekwy.iarnet.common.Constants;
import com.kekwy.iarnet.fabric.messaging.ActorMessageInbox;
import com.kekwy.iarnet.proto.common.FunctionDescriptor;
import com.kekwy.iarnet.proto.common.Lang;
import com.kekwy.iarnet.proto.common.ResourceSpec;
import com.kekwy.iarnet.proto.provider.RoutingStrategy;
import com.kekwy.iarnet.proto.workflow.Edge;
import com.kekwy.iarnet.proto.workflow.Node;
import com.kekwy.iarnet.fabric.actor.ActorInstanceRef;
import com.kekwy.iarnet.fabric.actor.ActorMessage;
import com.kekwy.iarnet.fabric.deployment.ActorEdge;
import com.kekwy.iarnet.fabric.deployment.ActorSpec;
import com.kekwy.iarnet.fabric.deployment.DeploymentCallback;
import com.kekwy.iarnet.fabric.deployment.DeploymentPlanGraph;
import com.kekwy.iarnet.fabric.deployment.DeploymentService;
import com.kekwy.iarnet.fabric.deployment.InstanceRefGraph;
import com.kekwy.iarnet.proto.workflow.WorkflowGraph;
import com.kekwy.iarnet.execution.RuntimeNode;
import com.kekwy.iarnet.execution.port.ArtifactUrlProvider;
import com.kekwy.iarnet.execution.util.ArtifactBuilder;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;

@Component
@RequiredArgsConstructor
// TODO: 考虑如何进行持久化，主要用于断线重连场景，非高优先级，来得及的话就搞
public class WorkflowRuntime {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRuntime.class);

    private final DeploymentService deploymentService;
    private final ArtifactUrlProvider artifactUrlProvider;

    private final ActorMessageInbox actorMessageInbox = new ActorMessageInbox();

    // 后续取决于时间看看是否能引入并发处理工作流提交请求，并考虑全平台线程安全
    private final BlockingQueue<SubmitRequest> queue = new LinkedBlockingQueue<>();
    private volatile boolean running = true;
    private Thread workerThread;
    private Thread inboxDispatcherThread;

    // TODO: 提供一些监测工作流运行情况的端点

    private record SubmitRequest(
            WorkflowGraph workflowGraph,
            Path artifactDir,
            Path externalSourceBaseDir
    ) {
    }

    public void submit(WorkflowGraph graph, Path artifactDir, Path externalSourceDir) {
        if (!running) {
            throw new IllegalStateException("Executor 已关闭，无法接收新的工作流");
        }
        queue.add(new SubmitRequest(graph, artifactDir, externalSourceDir));
        log.info("工作流已入队: workflowId={}, 当前队列长度={}", graph.getWorkflowId(), queue.size());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startWorker() {
        workerThread = new Thread(this::pollLoop, "workflow-executor");
        workerThread.setDaemon(true);
        workerThread.start();
        inboxDispatcherThread = new Thread(this::inboxDispatcherLoop, "inbox-dispatcher");
        inboxDispatcherThread.setDaemon(true);
        inboxDispatcherThread.start();
        log.info("工作流执行线程与 inbox 分发线程已启动");
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
        if (inboxDispatcherThread != null) {
            inboxDispatcherThread.interrupt();
        }
        log.info("工作流执行线程已停止，剩余未处理: {}", queue.size());
    }

    private void pollLoop() {
        while (running) {
            try {
                SubmitRequest request = queue.take();
                handleWorkflow(request);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (!running) break;
            } catch (Exception e) {
                log.error("处理工作流时发生异常", e);
            }
        }
    }

    private void inboxDispatcherLoop() {
        while (running) {
            try {
                ActorMessage envelope = actorMessageInbox.get();
                if (envelope == null) {
                    continue;
                }
                // TODO: 需要显式约束 deploymentId = workflowId
                RuntimeSession session = runtimeSessions.get(envelope.deploymentId());
                if (session != null) {
                    session.handleActorMessage(envelope.actorId(), envelope.message());
                } else {
                    log.warn("收到未知 workflow 的消息，已丢弃: deploymentId={}, actorId={}",
                            envelope.deploymentId(), envelope.actorId());
                }
            } catch (Exception e) {
                log.error("分发 inbox 消息时发生异常", e);
            }
        }
    }

    private void handleWorkflow(SubmitRequest request) {

        WorkflowGraph workflowGraph = request.workflowGraph();
        String workflowId = workflowGraph.getWorkflowId();
        String applicationId = workflowGraph.getApplicationId();

        if (runtimeSessions.containsKey(workflowId)) {
            throw new RuntimeException("重复提交的工作流");
        }

        log.info("开始处理工作流: workflowId={}, applicationId={}, nodes={}, edges={}",
                workflowId, applicationId, workflowGraph.getNodesCount(), workflowGraph.getEdgesCount());

        Path artifactDir = request.artifactDir();
        Path externalSourceBaseDir = request.externalSourceBaseDir();
        List<Node> nodes = workflowGraph.getNodesList();

        Map<Lang, String> langArtifactUrlMap = new HashMap<>();

        for (Node node : nodes) {
            Lang lang = node.getFunction().getLang();

            Path artifactPath = getArtifactPath(lang, artifactDir);

            if (!artifactPath.toFile().exists()) {
                // 说明这个函数是由其他语言的 sdk 跨语言引入的，要打包
                Path sourcePath = getSourceDir(lang, externalSourceBaseDir);
                ArtifactBuilder.buildArtifact(lang, sourcePath, artifactPath);
            }

            if (!langArtifactUrlMap.containsKey(lang)) {
                Optional<String> urlOptional = artifactUrlProvider.uploadAndGetUrl(workflowId + lang.name(), artifactPath);
                String url = urlOptional.orElseThrow(() -> new RuntimeException("获取 url 失败"));
                langArtifactUrlMap.put(lang, url);
            }
        }

        // 3. 提交给调度服务，部署 Actor 并获取物理 IR
        log.info("开始调度工作流: workflowId={}, nodes={}", workflowId, workflowGraph.getNodesCount());

        DeploymentPlanGraph deploymentPlanGraph = buildDeploymentPlanGraph(workflowGraph, langArtifactUrlMap);


        // 部署的时候就需要为每个actor传入一个 handler
        deploymentService.deploy(deploymentPlanGraph, actorMessageInbox, new DeploymentCallback() {
            @Override
            public void onSuccess(InstanceRefGraph instanceRefGraph) {
                RuntimeGraph runtimeGraph = buildRuntimeGraph(workflowGraph, instanceRefGraph);
                RuntimeSession session = new RuntimeSession(runtimeGraph);
                runtimeSessions.put(workflowId, session);
                session.start();
            }

            @Override
            public void onFailure(Exception e) {
                throw new RuntimeException(e);
            }
        });

    }

    private final ConcurrentMap<String, RuntimeSession> runtimeSessions = new ConcurrentHashMap<>();

    private static @NonNull Path getSourceDir(Lang lang, Path externalSourceBaseDir) {
        Path sourceDir;
        switch (lang) {
            case LANG_JAVA -> sourceDir = externalSourceBaseDir.resolve(Constants.SOURCE_DIR_JAVA);
            case LANG_PYTHON -> sourceDir = externalSourceBaseDir.resolve(Constants.SOURCE_DIR_PYTHON);
            case LANG_GO -> sourceDir = externalSourceBaseDir.resolve(Constants.SOURCE_DIR_GO);
            default -> throw new IllegalStateException("不支持的语言");
        }
        return sourceDir;
    }

    private static @NonNull Path getArtifactPath(Lang lang, Path artifactDir) {
        Path artifactPath;
        switch (lang) {
            case LANG_JAVA -> artifactPath = artifactDir.resolve(Constants.ARTIFACT_FILENAME_JAVA);
            case LANG_PYTHON -> artifactPath = artifactDir.resolve(Constants.ARTIFACT_FILENAME_PYTHON);
            case LANG_GO -> artifactPath = artifactDir.resolve(Constants.ARTIFACT_FILENAME_GO);
            default -> throw new IllegalStateException("不支持的语言");
        }
        return artifactPath;
    }


    private static RuntimeGraph buildRuntimeGraph(WorkflowGraph workflowGraph, InstanceRefGraph instanceRefGraph) {
        // actorId 格式: "actor-" + nodeId + "-" + replicaIndex
        Map<String, List<ActorInstanceRef>> refsByNodeId = new HashMap<>();
        for (ActorInstanceRef ref : instanceRefGraph.actorInstanceRefs()) {
            String nodeId = extractNodeIdFromActorId(ref.getActorId());
            refsByNodeId.computeIfAbsent(nodeId, k -> new ArrayList<>()).add(ref);
        }

        Set<String> hasIncoming = new HashSet<>();
        Set<String> hasOutgoing = new HashSet<>();
        for (Edge edge : workflowGraph.getEdgesList()) {
            hasOutgoing.add(edge.getFromNodeId());
            hasIncoming.add(edge.getToNodeId());
        }

        List<RuntimeNode> inputNodes = new ArrayList<>();
        List<RuntimeNode> outputNodes = new ArrayList<>();
        List<RuntimeNode> taskNodes = new ArrayList<>();

        for (Node node : workflowGraph.getNodesList()) {
            String nodeId = node.getId();
            List<ActorInstanceRef> refs = refsByNodeId.getOrDefault(nodeId, List.of());
            RuntimeNode runtimeNode = new RuntimeNode(nodeId, node.getName(), refs);

            boolean isInput = !hasIncoming.contains(nodeId);
            boolean isOutput = !hasOutgoing.contains(nodeId);

            if (isInput) {
                inputNodes.add(runtimeNode);
            }
            if (isOutput) {
                outputNodes.add(runtimeNode);
            }
            if (!isInput && !isOutput) {
                taskNodes.add(runtimeNode);
            }
        }

        return new RuntimeGraph(inputNodes, outputNodes, taskNodes);
    }

    /**
     * 从 actorId（格式 "actor-{nodeId}-{replicaIndex}"）中解析出 nodeId。
     */
    private static String extractNodeIdFromActorId(String actorId) {
        if (actorId == null || !actorId.startsWith("actor-")) {
            return actorId;
        }
        int prefixLen = "actor-".length();
        int lastHyphen = actorId.lastIndexOf('-');
        if (lastHyphen <= prefixLen) {
            return actorId.substring(prefixLen);
        }
        return actorId.substring(prefixLen, lastHyphen);
    }

    private static DeploymentPlanGraph buildDeploymentPlanGraph(WorkflowGraph workflowGraph,
                                                                Map<Lang, String> langArtifactUrlMap) {
        String deploymentId = workflowGraph.getWorkflowId();
        List<ActorSpec> actorSpecs = new ArrayList<>();
        Map<String, Integer> replicasByNodeId = new HashMap<>();

        for (Node node : workflowGraph.getNodesList()) {
            String nodeId = node.getId();
            int replicas = 1;
            ResourceSpec resourceSpec = defaultResourceSpec();
            if (node.hasNodeConfig()) {
                var nodeConfig = node.getNodeConfig();
                replicas = Math.max(nodeConfig.getReplicas(), 1);
                if (nodeConfig.hasResourceSpec()) {
                    resourceSpec = nodeConfig.getResourceSpec();
                }
            }
            replicasByNodeId.put(nodeId, replicas);

            for (int i = 0; i < replicas; i++) {
                String actorId = "actor-" + nodeId + "-" + i;
                FunctionDescriptor function = node.getFunction();
                ActorSpec spec = new ActorSpec(
                        actorId,
                        function,
                        resourceSpec,
                        langArtifactUrlMap.get(function.getLang()),
                        i,
                        node.getNodeKind()
                );
                actorSpecs.add(spec);
            }
        }

        // 目标节点的不同源节点数：多于 1 为 Union 场景，用 HASH_BY_ROW_ID
        Map<String, Set<String>> sourceNodesByDstNode = new HashMap<>();
        for (Edge edge : workflowGraph.getEdgesList()) {
            sourceNodesByDstNode
                    .computeIfAbsent(edge.getToNodeId(), k -> new HashSet<>())
                    .add(edge.getFromNodeId());
        }

        // 每个源节点下一条条件边的 output_port 从 1 自增
        Map<String, Integer> nextConditionalPortByNodeId = new HashMap<>();

        // 将 node 级边展开为 actor 级边：源节点所有实例与后继节点所有实例全连接
        List<ActorEdge> actorEdges = new ArrayList<>();
        for (Edge edge : workflowGraph.getEdgesList()) {
            String srcNodeId = edge.getFromNodeId();
            String dstNodeId = edge.getToNodeId();
            int srcReplicas = replicasByNodeId.getOrDefault(srcNodeId, 1);
            int dstReplicas = replicasByNodeId.getOrDefault(dstNodeId, 1);
            var conditionFn = edge.hasConditionFunction() ? edge.getConditionFunction() : null;

            int outputPort = conditionFn != null
                    ? nextConditionalPortByNodeId.merge(srcNodeId, 1, Integer::sum)
                    : 0;

            Set<String> sources = sourceNodesByDstNode.get(dstNodeId);
            RoutingStrategy routingStrategy = (sources != null && sources.size() > 1)
                    ? RoutingStrategy.HASH_BY_ROW_ID
                    : RoutingStrategy.ROUND_ROBIN;

            for (int i = 0; i < srcReplicas; i++) {
                String fromActorId = "actor-" + srcNodeId + "-" + i;
                for (int j = 0; j < dstReplicas; j++) {
                    String toActorId = "actor-" + dstNodeId + "-" + j;
                    actorEdges.add(new ActorEdge(fromActorId, toActorId, conditionFn, outputPort, routingStrategy));
                }
            }
        }

        return new DeploymentPlanGraph(deploymentId, actorSpecs, actorEdges);
    }

    private static ResourceSpec defaultResourceSpec() {
        return ResourceSpec.newBuilder()
                .setCpu(0.5)
                .setMemory("256Mi")
                .build();
    }


}
