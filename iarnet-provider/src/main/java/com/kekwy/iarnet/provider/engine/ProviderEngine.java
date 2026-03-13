package com.kekwy.iarnet.provider.engine;

import com.kekwy.iarnet.proto.provider.DeployActorRequest;
import com.kekwy.iarnet.proto.provider.DeployActorResponse;
import com.kekwy.iarnet.proto.provider.GetActorStatusResponse;
import com.kekwy.iarnet.proto.provider.RemoveActorResponse;
import com.kekwy.iarnet.proto.provider.StopActorResponse;

import java.nio.file.Path;

/**
 * Provider 引擎 SPI：每种运行时（Docker / K8s）提供一个实现，
 * 负责 Actor 的部署、停止、移除与状态查询。
 */
public interface ProviderEngine extends AutoCloseable {

    /** 引擎类型标识，如 "docker"、"k8s" */
    String providerType();

    /**
     * 部署一个 Actor。
     *
     * @param request           部署请求（含 actor_id、artifact_url、resource_request、lang、function_descriptor 等）
     * @param artifactLocalPath 已拉取到本地的 artifact 文件路径；为 null 表示无 artifact
     */
    DeployActorResponse deployActor(DeployActorRequest request, Path artifactLocalPath);

    /** 停止一个 Actor */
    StopActorResponse stopActor(String actorId);

    /** 移除一个 Actor */
    RemoveActorResponse removeActor(String actorId);

    /** 查询 Actor 状态 */
    GetActorStatusResponse getActorStatus(String actorId);
}
