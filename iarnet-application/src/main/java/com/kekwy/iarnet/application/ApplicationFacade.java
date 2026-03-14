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
     * 提交 JAR 并启动进程，不携带输入。
     */
    default void launchApplicationWithJar(byte[] content) {
        launchApplicationWithJar(content, null);
    }

    /**
     * 提交 JAR 并携带输入（便于测试）。与无参重载相同地创建并启动进程，
     * 若 inputs 非空则写入工作空间下的 input.json，并设置环境变量 IARNET_INPUT_FILE。
     *
     * @param content JAR 字节
     * @param inputs  键值对输入，可为 null 或空，等效于 {@link #launchApplicationWithJar(byte[])}
     */
    void launchApplicationWithJar(byte[] content, Map<String, String> inputs);

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
