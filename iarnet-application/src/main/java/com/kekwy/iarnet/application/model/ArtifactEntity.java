package com.kekwy.iarnet.application.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "t_artifact")
public class ArtifactEntity {

    @Id
    @Column(name = "artifact_id", length = 64, nullable = false)
    private String artifactID;

    @Column(name = "application_id", length = 64, nullable = false)
    private String applicationID;

    /**
     * 构建产物（fat jar 等）在文件系统中的绝对路径。
     */
    @Column(name = "artifact_path", length = 1024, nullable = false)
    private String artifactPath;
}
