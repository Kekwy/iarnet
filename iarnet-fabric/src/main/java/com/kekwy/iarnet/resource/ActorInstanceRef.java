package com.kekwy.iarnet.resource;

import com.kekwy.iarnet.proto.actor.ActorMessage;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Data
@RequiredArgsConstructor
public class ActorInstanceRef {

    private final String actorId;
    private String outpostId; // TODO: 支持运行时动态装载和卸载

    private List<ActorInstanceRef> precursors;
    private List<ActorInstanceRef> successors;

    public void send(ActorMessage message) {

    }

}
