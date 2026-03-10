package com.kekwy.iarnet.application.service;

import com.kekwy.iarnet.application.model.Workspace;
import com.kekwy.iarnet.common.model.ID;

/**
 * Workspace 服务，负责为应用管理工作空间目录及 Git 仓库。
 */
public interface WorkspaceService {

    /**
     * 为指定应用创建工作空间并克隆 Git 仓库。
     *
     * @param applicationId 应用 ID（如 AppID.xxxx）
     * @param gitUrl        仓库地址
     * @param branch        分支，null 或空串时使用 main
     * @return 工作空间目录绝对路径
     */
    String createWorkspaceAndClone(String applicationId, String gitUrl, String branch);

    /**
     * 为指定应用创建一个「空」工作空间，不执行 git clone。
     * <p>
     * 仅用于通过 CLI / shell 上传 JAR 等场景：
     * <ul>
     *   <li>控制平面自动生成 applicationId；</li>
     *   <li>仅需在本地创建 workspace 目录结构与数据库记录，</li>
     *   <li>后续直接在该 workspace 下保存 artifact 并运行 JAR。</li>
     * </ul>
     *
     * @param applicationId 应用 ID（如 AppID.xxxx）
     * @return 工作空间目录绝对路径
     */
    String createEmptyWorkspace(String applicationId);

    Workspace getByApplicationID(ID applicationID);
}
