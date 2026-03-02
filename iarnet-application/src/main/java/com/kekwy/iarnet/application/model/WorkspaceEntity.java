package com.kekwy.iarnet.application.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Workspace 实体，记录每个应用对应的工作空间目录。
 */
@Data
@Entity
@Table(name = "t_workspace")
public class WorkspaceEntity {

    /**
     * 工作空间 ID，这里与 applicationID 保持一致，方便一对一映射。
     */
    @Id
    @Column(name = "workspace_id", length = 64, nullable = false)
    private String workspaceID;

    /**
     * 应用 ID（App.xxx），唯一。
     */
    @Column(name = "application_id", length = 64, nullable = false, unique = true)
    private String applicationID;

    /**
     * 工作空间在文件系统中的目录。
     */
    @Column(name = "workspace_dir", length = 1024, nullable = false)
    private String workspaceDir;
}
