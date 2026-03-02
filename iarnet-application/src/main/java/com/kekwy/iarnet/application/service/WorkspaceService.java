package com.kekwy.iarnet.application.service;

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
}
