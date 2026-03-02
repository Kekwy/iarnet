package com.kekwy.iarnet.application.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * 应用实体，对应 SQLite applications 表。
 */
@Data
@Entity
@Table(name = "t_application_info")
public class ApplicationInfoEntity {

    @Id
    @Column(name = "application_id", length = 64, nullable = false)
    private String applicationID;

    /** 应用名称 */
    @Column(nullable = false, length = 128)
    private String name;

    /** Git 仓库地址 */
    @Column(name = "git_url", nullable = false, length = 512)
    private String gitUrl;

    /** 分支 */
    @Column(nullable = false, length = 64)
    private String branch = "main";

    /** 状态：idle / running / stopped / error / deploying / cloning / importing */
    @Column(nullable = false, length = 32)
    private String status = "idle";

    /** 描述 */
    @Column(length = 500)
    private String description;

    /** 运行环境 */
    @Column(name = "lang", length = 128)
    private String lang;

    /** 最近一次构建/部署错误信息 */
    @Column(name = "last_error", length = 1024)
    private String lastError;

    /** 最后部署时间 */
    @Column(name = "last_deployed")
    private Instant lastDeployed;

    /** 创建时间 */
    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
