package com.kekwy.iarnet.workflow.runtime;

import com.kekwy.iarnet.proto.actor.ActorEnvelope;
import com.kekwy.iarnet.proto.actor.StartInputCommand;
import com.kekwy.iarnet.resource.ActorInstanceRef;
import com.kekwy.iarnet.workflow.RuntimeNode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@RequiredArgsConstructor
public class RuntimeSession {

    private static final Logger log = LoggerFactory.getLogger(RuntimeSession.class);

    private final RuntimeGraph runtimeGraph;

    public void handleActorMessage(String actorId, ActorEnvelope message) {

    }

    /**
     * 向所有 input node 的 actor 发送 StartInputCommand，启动数据流。
     */
    public void start() {
        ActorEnvelope startEnvelope = ActorEnvelope.newBuilder()
                .setStartInputCommand(StartInputCommand.getDefaultInstance())
                .build();

        for (RuntimeNode inputNode : runtimeGraph.getInputNodes()) {
            for (ActorInstanceRef ref : inputNode.actorInstanceRefs()) {
                log.info("发送 StartInputCommand: nodeId={}, actorId={}",
                        inputNode.nodeId(), ref.getActorId());
                ref.send(startEnvelope);
            }
        }
    }
}
