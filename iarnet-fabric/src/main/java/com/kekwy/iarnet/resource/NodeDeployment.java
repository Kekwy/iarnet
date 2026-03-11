package com.kekwy.iarnet.resource;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class NodeDeployment {

    private final String nodeId;
    private final List<ActorInstance> instances;

}
