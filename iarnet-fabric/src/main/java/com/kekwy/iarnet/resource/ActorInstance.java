package com.kekwy.iarnet.resource;

import com.kekwy.iarnet.proto.actor.ActorMessage;
import lombok.Data;

@Data
public class ActorInstance {
    private final String actorId;
    private String deviceId; // TODO: 是否可以弃用了，直接根据 actorId 寻址？由于需要保证逻辑地址不变

    public void send(ActorMessage message) {

    }

}
