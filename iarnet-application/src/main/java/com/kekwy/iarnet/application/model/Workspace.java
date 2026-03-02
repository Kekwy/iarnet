package com.kekwy.iarnet.application.model;

import com.kekwy.iarnet.model.ID;
import lombok.Data;

@Data
public class Workspace {


    private ID workspaceID;


    private ID applicationID;


    private String workspaceDir;
}
