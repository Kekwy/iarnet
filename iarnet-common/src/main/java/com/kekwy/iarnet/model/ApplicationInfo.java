package com.kekwy.iarnet.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

@Data
public class ApplicationInfo {

    private ID id;

    /** 应用名称 */
    private String name;

    /** Git 仓库地址 */
    @JsonProperty("git_url")
    private String gitUrl;

    /** 分支 */
    private String branch = "main";

    /** 状态：idle / running / stopped / error / deploying / cloning / importing */
    private String status = "idle";

    /** 描述 */
    private String description;

    /** 运行环境 */
    @JsonProperty("runner_env")
    private String runnerEnv;

    /** 最后部署时间 */
    @JsonProperty("last_deployed")
    private Instant lastDeployed;

    /** 创建时间 */
    @JsonProperty("created_at")
    private Instant createdAt;

}
