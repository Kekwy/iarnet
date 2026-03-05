package com.kekwy.iarnet.application.executor;

import com.kekwy.iarnet.application.artifact.ArtifactUrlProvider;
import com.kekwy.iarnet.application.model.Workspace;
import com.kekwy.iarnet.application.service.WorkspaceService;
import com.kekwy.iarnet.model.ID;
import com.kekwy.iarnet.proto.ir.Node;
import com.kekwy.iarnet.proto.ir.WorkflowGraph;
import com.kekwy.iarnet.resource.model.ActorDeployment;
import com.kekwy.iarnet.resource.model.ActorInstance;
import com.kekwy.iarnet.resource.model.PhysicalWorkflowGraph;
import com.kekwy.iarnet.resource.service.SchedulerService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class DefaultExecutor implements Executor {

    private static final Logger log = LoggerFactory.getLogger(DefaultExecutor.class);

    private final WorkspaceService workspaceService;
    private final SchedulerService schedulerService;
    private final ArtifactUrlProvider artifactUrlProvider;
    private final BlockingQueue<WorkflowGraph> queue = new LinkedBlockingQueue<>();
    private volatile boolean running = true;
    private Thread workerThread;

    public DefaultExecutor(WorkspaceService workspaceService, SchedulerService schedulerService,
                           @Autowired(required = false) ArtifactUrlProvider artifactUrlProvider) {
        this.workspaceService = workspaceService;
        this.schedulerService = schedulerService;
        this.artifactUrlProvider = artifactUrlProvider;
    }

    @Override
    public void submit(WorkflowGraph graph) {
        if (!running) {
            throw new IllegalStateException("Executor 已关闭，无法接收新的工作流");
        }
        queue.add(graph);
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
                WorkflowGraph graph = queue.take();
                handleWorkflow(graph);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (!running) break;
            } catch (Exception e) {
                log.error("处理工作流时发生异常", e);
            }
        }
    }

    private void handleWorkflow(WorkflowGraph graph) {
        String workflowId = graph.getWorkflowId();
        String applicationId = graph.getApplicationId();

        log.info("开始处理工作流: workflowId={}, applicationId={}, nodes={}, edges={}",
                workflowId, applicationId, graph.getNodesCount(), graph.getEdgesCount());

        // 1. 获取该应用的 Workspace
        Workspace workspace = workspaceService.getByApplicationID(ID.of(applicationId));

        // 2. 使用访问者模式为每个节点准备 artifact
        ArtifactPrepareVisitor visitor = new ArtifactPrepareVisitor(workspace);
        for (Node node : graph.getNodesList()) {
            visitor.dispatch(node);
        }

        //TODO: 测试 python 打包（论文中的目标检测示例要使用 python 来编写，目标符合主流应用场景）
        Map<String, Path> nodeArtifacts = visitor.getNodeArtifacts();
        log.info("Artifact 准备完成: workflowId={}, 已解析 {} 个节点 artifact",
                workflowId, nodeArtifacts.size());

        // 若有 OSS，上传 artifact 并得到拉取 URL，供部署请求下发给 Adapter
        Map<String, String> nodeArtifactUrls = new HashMap<>();
        if (artifactUrlProvider != null) {
            for (Map.Entry<String, Path> e : nodeArtifacts.entrySet()) {
                artifactUrlProvider.uploadAndGetUrl(e.getKey(), e.getValue())
                        .ifPresent(url -> nodeArtifactUrls.put(e.getKey(), url));
            }
            log.info("Artifact URL 已生成: workflowId={}, urls={}", workflowId, nodeArtifactUrls.size());
        }

        // 3. 提交给调度服务，部署 Actor 并获取物理 IR
        log.info("开始调度工作流: workflowId={}, nodes={}", workflowId, graph.getNodesCount());

        PhysicalWorkflowGraph physicalGraph = schedulerService.schedule(graph, nodeArtifacts, nodeArtifactUrls);

        log.info("调度完成: workflowId={}, 共部署 {} 个 Actor",
                workflowId, physicalGraph.totalActorCount());

        log.info("物理 IR 生成完成: workflowId={}, 共 {} 个节点, {} 个 Actor",
                workflowId, physicalGraph.deployments().size(), physicalGraph.totalActorCount());

        for (ActorDeployment deployment : physicalGraph.deployments()) {
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
}
