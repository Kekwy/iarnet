package com.kekwy.iarnet.workflow.runtime;

import com.kekwy.iarnet.resource.ActorMessageEnvelope;
import com.kekwy.iarnet.resource.MessageInbox;

public class RuntimeInbox implements MessageInbox<ActorMessageEnvelope> {
    @Override
    public ActorMessageEnvelope get() {
        return null;
    }

    @Override
    public void put(ActorMessageEnvelope message) {

    }
}
