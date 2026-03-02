package com.kekwy.iarnet.application.model;

import com.kekwy.iarnet.model.ID;
import lombok.Data;

@Data
public class Artifact {
    private ID applicationID;
    private ID artifactID;
    private String artifactPath;
}
