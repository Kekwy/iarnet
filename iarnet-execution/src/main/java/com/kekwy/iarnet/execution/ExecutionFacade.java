package com.kekwy.iarnet.execution;

import com.kekwy.iarnet.proto.common.Value;
import com.kekwy.iarnet.proto.workflow.WorkflowGraph;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ExecutionFacade {

    private final WorkflowEngine engine;

    /**
     * 预注册工作流，返回 workflowId 与 token，用于 submitJarWithInput 流程：通过环境变量下发给 JAR，SDK 用该 workflowId 构建图并提交，主进程用该 token 调用 execute。
     *
     * @return workflowId 与 token
     */
    public WorkflowEngine.RegistrationResult register() {
        return engine.register();
    }

    /**
     * 提交工作流图到执行层，入队后立即返回 token，供后续 execute 时校验使用。
     *
     * @param graph              工作流图
     * @param artifactDir        制品目录
     * @param externalSourceDir  外部源码目录
     * @return 本次提交的 token
     */
    public String submit(WorkflowGraph graph, Path artifactDir, Path externalSourceDir) {
        return engine.submit(graph, artifactDir, externalSourceDir);
    }

    /**
     * 使用 submit 返回的 token 校验后，将输入（proto Value 映射）转交引擎执行。
     *
     * @param workflowId 工作流 ID
     * @param token      submit 返回的 token
     * @param inputs     参数名到 proto Value 的映射
     * @return 本次执行的 submissionId
     */
    public String execute(String workflowId, String token, Map<String, Value> inputs) {
        return engine.execute(workflowId, token, inputs);
    }
}
