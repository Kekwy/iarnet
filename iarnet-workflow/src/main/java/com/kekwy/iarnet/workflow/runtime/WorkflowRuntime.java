package com.kekwy.iarnet.workflow.runtime;

import com.kekwy.iarnet.common.Constants;
import com.kekwy.iarnet.proto.common.Lang;
import com.kekwy.iarnet.proto.workflow.Node;
import com.kekwy.iarnet.proto.workflow.WorkflowGraph;
import com.kekwy.iarnet.resource.model.ActorDeployment;
import com.kekwy.iarnet.resource.model.ActorInstance;
import com.kekwy.iarnet.resource.model.PhysicalWorkflowGraph;
import com.kekwy.iarnet.resource.service.SchedulerService;
import com.kekwy.iarnet.workflow.port.ArtifactUrlProvider;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
@RequiredArgsConstructor
public class WorkflowRuntime {

    private static final Logger log = LoggerFactory.getLogger(WorkflowRuntime.class);

    private final SchedulerService schedulerService;
    private final ArtifactUrlProvider artifactUrlProvider;
    private final BlockingQueue<SubmitRequest> queue = new LinkedBlockingQueue<>();
    private volatile boolean running = true;
    private Thread workerThread;


    private record SubmitRequest(
            WorkflowGraph workflowGraph,
            Path artifactDir,
            Path externalSourceBaseDir
    ) {
    }

    public void submit(WorkflowGraph graph, Path artifactDir,  Path externalSourceDir) {
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
        WorkflowGraph graph = request.workflowGraph();

        String workflowId = graph.getWorkflowId();
        String applicationId = graph.getApplicationId();

        log.info("开始处理工作流: workflowId={}, applicationId={}, nodes={}, edges={}",
                workflowId, applicationId, graph.getNodesCount(), graph.getEdgesCount());

        Path artifactDir = request.artifactDir();
        Path externalSourceBaseDir = request.externalSourceBaseDir();
        List<Node> nodes = graph.getNodesList();

        Map<Lang, String>  langArtifactUrlMap = new HashMap<>();

        for (Node node : nodes) {
            Lang lang = node.getFunction().getLang();

            Path artifactPath = getArtifactPath(lang, artifactDir);

            if (!artifactPath.toFile().exists()) {
                // 说明是有其他语言的 sdk 跨语言引入的，要打包
                Path sourcePath = getSourceDir(lang, externalSourceBaseDir);
            }

            if (!langArtifactUrlMap.containsKey(lang)) {
                Optional<String> urlOptional = artifactUrlProvider.uploadAndGetUrl(workflowId + lang.name(), artifactPath);
                String url = urlOptional.orElseThrow(()->new RuntimeException("获取 url 失败"));
                langArtifactUrlMap.put(lang, url);
            }
        }

        // 3. 提交给调度服务，部署 Actor 并获取物理 IR
        log.info("开始调度工作流: workflowId={}, nodes={}", workflowId, graph.getNodesCount());

        // TODO: DeploymentGraph

        DeploymentGraph deploymentGraph = schedulerService.schedule(graph, langArtifactUrlMap);

        // TODO: 开始接收每个 actor 的上报消息，发送触发消息等业务消息

        log.info("调度完成: workflowId={}, 共部署 {} 个 Actor",
                workflowId, deploymentGraph.totalActorCount());

        log.info("物理 IR 生成完成: workflowId={}, 共 {} 个节点, {} 个 Actor",
                workflowId, deploymentGraph.deployments().size(), physicalGraph.totalActorCount());

        for (ActorDeployment deployment : deploymentGraph.deployments()) {
            for (ActorInstance actor : deployment.instances()) {
                log.info("  节点 {} [{}] → actor={}, device={}, container={}, {}:{}",
                        deployment.nodeId(), deployment.nodeKind(),
                        actor.actorId(), actor.deviceId(), actor.containerId(),
                        actor.host(), actor.port());
            }
        }

        // TODO: 4. 根据物理 IR 建立 Actor 间的数据流连接

        log.info("工作流处理完成: workflowId={}", workflowId);
    }

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

}
