package com.kekwy.iarnet.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CreateApplicationRequest {

    private String name;
    @JsonProperty("git_url")
    private String gitUrl;
    private String branch = "main";
    private String description;
    /** 运行语言，例如 java / python / go */
    @JsonProperty("lang")
    private String lang;
}
