package com.kekwy.iarnet.resource;

import com.kekwy.iarnet.proto.actor.ActorMessage;

public interface ActorMessageChannel {

    ActorMessage receive();
    void send(ActorMessage actorMessage);

}
