package com.kekwy.iarnet.resource;

public record ActorLifeCycleEvent(
        String actorId,
        String deploymentId,
        ActorInstanceRef actorInstance,
        long timestamp,
        String reason
) {

//    private final ActorLifecycleState state;
//    private final long timestamp;
//    private final String reason;

}