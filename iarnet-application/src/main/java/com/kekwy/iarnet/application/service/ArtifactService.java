package com.kekwy.iarnet.application.service;

import com.kekwy.iarnet.application.model.Artifact;
import com.kekwy.iarnet.model.ID;

public interface ArtifactService {

    Artifact create(ID applicationID, String artifactPath);

    Artifact get(ID artifactID);

}
