package com.kekwy.iarnet.application;

import com.google.protobuf.ByteString;
import com.kekwy.iarnet.common.model.ApplicationInfo;
import com.kekwy.iarnet.common.model.ID;
import com.kekwy.iarnet.proto.ir.WorkflowGraph;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ApplicationFacade {

    List<ApplicationInfo> listApplicationInfo();

    // TOOD: 通过 shell 导入的应用也需要在 web 上可见
    void createApplicationWithJar(byte[] content);

    void launchApplicationWithJar(byte[] content);

    ApplicationInfo createApplication(ApplicationInfo input);

    ApplicationInfo updateApplication(ID id, ApplicationInfo input);

    boolean launchApplication(ID id);

    void deleteApplication(ID id);

    Map<String, Long> getApplicationStats();

    /**
     * 获取指定应用最近一次 Maven 构建日志内容。
     *
     * @param id 应用 ID
     * @return 日志全文（可能为空字符串）
     */
    Optional<String> getBuildLog(ID id);

    /**
     * 提交工作流图到执行器进行调度处理。
     *
     * @param graph 工作流图 IR
     */
    void submitWorkflow(WorkflowGraph graph);

    /**
     * 提交工作流图及其打包好的 artifact（如 JAR / tar.gz）。
     * <p>
     * 默认实现忽略二进制内容，仅委托到 {@link #submitWorkflow(WorkflowGraph)}，
     * 具体实现类可根据需要将 artifact 落盘到 Workspace。
     *
     * @param graph         工作流图 IR
     * @param artifact      工件字节（可以为空）
     * @param artifactName  工件文件名提示（可以为空）
     */
    default void submitWorkflow(WorkflowGraph graph, ByteString artifact, String artifactName) {
        submitWorkflow(graph);
    }
}
