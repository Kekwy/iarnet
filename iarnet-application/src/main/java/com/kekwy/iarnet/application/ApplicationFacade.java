package com.kekwy.iarnet.application;

import com.kekwy.iarnet.common.model.ApplicationInfo;
import com.kekwy.iarnet.common.model.ID;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ApplicationFacade {

    List<ApplicationInfo> listApplicationInfo();

    // TOOD: 通过 shell 导入的应用也需要在 web 上可见
    void createApplicationWithJar(byte[] content);

    /**
     * 提交 JAR 并启动进程，不传递预注册的 workflow 信息。
     */
    default void launchApplicationWithJar(byte[] content) {
        launchApplicationWithJar(content, null, null);
    }

    /**
     * 提交 JAR 并启动进程；若 workflowId、token 非空，则通过环境变量 IARNET_WORKFLOW_ID、IARNET_WORKFLOW_TOKEN 传给 JAR 进程，供 SDK 使用预注册的 workflow 提交图，主进程再通过 execute 提交输入。
     *
     * @param content    JAR 字节
     * @param workflowId 预注册的 workflowId，可为 null
     * @param token      预注册的 token，可为 null
     */
    void launchApplicationWithJar(byte[] content, String workflowId, String token);

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
}
