package com.kekwy.iarnet.application.executor;

import com.kekwy.iarnet.application.model.Workspace;
import com.kekwy.iarnet.application.service.WorkspaceService;
import com.kekwy.iarnet.model.ID;
import com.kekwy.iarnet.proto.ir.Node;
import com.kekwy.iarnet.proto.ir.WorkflowGraph;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class DefaultExecutor implements Executor {

    private static final Logger log = LoggerFactory.getLogger(DefaultExecutor.class);

    private final WorkspaceService workspaceService;
    private final BlockingQueue<WorkflowGraph> queue = new LinkedBlockingQueue<>();
    private volatile boolean running = true;
    private Thread workerThread;

    public DefaultExecutor(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
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

        Map<String, Path> nodeArtifacts = visitor.getNodeArtifacts();
        log.info("Artifact 准备完成: workflowId={}, 已解析 {} 个节点 artifact",
                workflowId, nodeArtifacts.size());

        nodeArtifacts.forEach((nodeId, path) ->
                log.info("  节点 {} → {}", nodeId, path));

        // TODO: 3. 根据 nodeArtifacts 为每个算子节点调度容器/进程

        log.info("工作流处理完成: workflowId={}", workflowId);
    }
}
