package com.kekwy.iarnet.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class UpdateApplicationRequest {

    private String name;
    private String description;
    /** 运行语言，例如 java / python / go */
    @JsonProperty("lang")
    private String lang;
}
