package com.kekwy.iarnet.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class UpdateApplicationRequest {

    private String name;
    private String description;
    @JsonProperty("runner_env")
    private String runnerEnv;
}
