package com.kekwy.iarnet.execution;

import com.kekwy.iarnet.execution.runtime.WorkflowRuntime;
import com.kekwy.iarnet.proto.workflow.WorkflowGraph;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class ExecutionFacade {

    private final WorkflowRuntime runtime;

    /**
     * 提交工作流图到执行层，入队后立即返回 token，供后续 execute 时校验使用。
     *
     * @param graph              工作流图
     * @param artifactDir        制品目录
     * @param externalSourceDir  外部源码目录
     * @return 本次提交的 token
     */
    public String submit(WorkflowGraph graph, Path artifactDir, Path externalSourceDir) {
        return runtime.submit(graph, artifactDir, externalSourceDir);
    }

}
