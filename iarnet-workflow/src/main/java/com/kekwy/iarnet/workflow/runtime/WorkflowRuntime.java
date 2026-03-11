package com.kekwy.iarnet.workflow.runtime;

import com.kekwy.iarnet.common.Constants;
import com.kekwy.iarnet.proto.common.FunctionDescriptor;
import com.kekwy.iarnet.proto.common.Lang;
import com.kekwy.iarnet.proto.common.ResourceSpec;
import com.kekwy.iarnet.proto.workflow.Edge;
import com.kekwy.iarnet.proto.workflow.Node;
import com.kekwy.iarnet.resource.ActorEdge;
import com.kekwy.iarnet.proto.workflow.WorkflowGraph;
import com.kekwy.iarnet.resource.ActorSpec;
import com.kekwy.iarnet.resource.ActorMessageEnvelope;
import com.kekwy.iarnet.resource.DeploymentCallback;
import com.kekwy.iarnet.resource.DeploymentGraph;
import com.kekwy.iarnet.resource.DeploymentPlanGraph;
import com.kekwy.iarnet.resource.InstanceGraph;
import com.kekwy.iarnet.resource.service.SchedulerService;
import com.kekwy.iarnet.workflow.port.ArtifactUrlProvider;
import com.kekwy.iarnet.workflow.util.ArtifactBuilder;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;

@Component
@RequiredArgsConstructor
// TODO: 考虑如何进行持久化，主要用于断线重连场景，非高优先级，来得及的话就搞
public class WorkflowRuntime {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRuntime.class);

    private final SchedulerService schedulerService;
    private final ArtifactUrlProvider artifactUrlProvider;

    private final RuntimeInbox inbox = new RuntimeInbox();

    // 后续取决于时间看看是否能引入并发处理工作流提交请求，并考虑全平台线程安全
    private final BlockingQueue<SubmitRequest> queue = new LinkedBlockingQueue<>();
    private volatile boolean running = true;
    private Thread workerThread;

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
        log.info("工作流执行线程已启动");
    }

    @PreDestroy
    public void shutdown() {
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
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

    private void handleWorkflow(SubmitRequest request) {

        WorkflowGraph workflowGraph = request.workflowGraph();
        String workflowId = workflowGraph.getWorkflowId();
        String applicationId = workflowGraph.getApplicationId();

        Map<String, RuntimeSession> wfIdRuntionSessionMap = runtimeSessions.getOrDefault(applicationId, new ConcurrentHashMap<>());

        if (wfIdRuntionSessionMap.containsKey(workflowId)) {
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
        schedulerService.deploy(deploymentPlanGraph, inbox, new DeploymentCallback() {

            @Override
            public void onSuccess(InstanceGraph instanceGraph) {

            }

            @Override
            public void onFailure(Exception e) {

            }
        });

        // TODO: 完成部署，输出一些日志信息

        // TODO: 运行时图是否真的需要？？建议是先不要这么过度设计
        // 今天下午的目标是吧 outpost 和 fabric 模块的所有基础功能全部调通，并且预留好拓展点（资源发现、跨域调度、ICE信道初始化），然后让cursor把代码整理好。
        // 明天就是把上述核心功能搞定并调通（周四）
        // 后天是找论文优化调度算法，引入恢复机制（周五）
        // 完成论文实验部分之前的所有草稿（周六）
        // 进行第一次修改+补图片（来不及可以先放放），（周天）
        // 找老师确认论文（周一，3.16号）应该不至于需要大改，方向上应该基本没问题，相当于是上学期 iarnet 项目的 plus 版（希望。。。）

        // 创建 runtime session，负责与workflow中的各个actor收发消息

    }

    private final ConcurrentMap<String, ConcurrentMap<String, RuntimeSession>> runtimeSessions = new ConcurrentHashMap<>();

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
                        langArtifactUrlMap.get(function.getLang())
                );
                actorSpecs.add(spec);
            }
        }

        // 将 node 级边展开为 actor 级边：源节点所有实例与后继节点所有实例全连接
        List<ActorEdge> actorEdges = new ArrayList<>();
        for (Edge edge : workflowGraph.getEdgesList()) {
            String srcNodeId = edge.getFromNodeId();
            String dstNodeId = edge.getToNodeId();
            int srcReplicas = replicasByNodeId.getOrDefault(srcNodeId, 1);
            int dstReplicas = replicasByNodeId.getOrDefault(dstNodeId, 1);
            var conditionFn = edge.hasConditionFunction() ? edge.getConditionFunction() : null;

            for (int i = 0; i < srcReplicas; i++) {
                String fromActorId = "actor-" + srcNodeId + "-" + i;
                for (int j = 0; j < dstReplicas; j++) {
                    String toActorId = "actor-" + dstNodeId + "-" + j;
                    actorEdges.add(new ActorEdge(fromActorId, toActorId, conditionFn));
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

    private static RuntimeGraph buildRuntimeGraph(WorkflowGraph workflowGraph, DeploymentGraph deploymentGraph) {
        return new RuntimeGraph();
    }


}
