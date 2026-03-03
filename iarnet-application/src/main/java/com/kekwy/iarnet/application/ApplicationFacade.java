package com.kekwy.iarnet.application;

import com.kekwy.iarnet.model.ApplicationInfo;
import com.kekwy.iarnet.model.ID;
import com.kekwy.iarnet.proto.ir.WorkflowGraph;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ApplicationFacade {

    List<ApplicationInfo> listApplicationInfo();

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
}
