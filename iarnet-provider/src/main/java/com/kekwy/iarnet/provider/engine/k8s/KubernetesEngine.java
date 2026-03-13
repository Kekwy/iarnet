package com.kekwy.iarnet.provider.engine.k8s;

import com.kekwy.iarnet.provider.artifact.ArtifactStore;
import com.kekwy.iarnet.provider.engine.ProviderEngine;
import com.kekwy.iarnet.proto.common.FunctionDescriptor;
import com.kekwy.iarnet.proto.common.Lang;
import com.kekwy.iarnet.proto.common.ResourceSpec;
import com.kekwy.iarnet.proto.provider.*;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kubernetes Provider 引擎：将每个 Actor 部署为一个 Pod。
 */
public class KubernetesEngine implements ProviderEngine {

    private static final Logger log = LoggerFactory.getLogger(KubernetesEngine.class);

    private static final String CONTAINER_FUNCTION_DIR = "/opt/iarnet/function";
    private static final String CONTAINER_FUNCTION_FILE = CONTAINER_FUNCTION_DIR + "/function.pb";
    private static final String CONTAINER_ARTIFACT_DIR = "/opt/iarnet/artifact";

    private final KubernetesClient kubeClient;
    private final ArtifactStore artifactStore;
    private final String namespace;

    /** actorId → podName */
    private final Map<String, String> actorPods = new ConcurrentHashMap<>();
    private final Map<String, ResourceSpec> actorResources = new ConcurrentHashMap<>();

    public KubernetesEngine(String kubeconfig, boolean inCluster, String namespace,
                            ArtifactStore artifactStore) {
        this.namespace = namespace != null ? namespace : "default";
        this.artifactStore = artifactStore;

        if (inCluster) {
            this.kubeClient = new KubernetesClientBuilder().build();
        } else {
            Config config = kubeconfig != null && !kubeconfig.isBlank()
                    ? Config.fromKubeconfig(kubeconfig)
                    : Config.autoConfigure(null);
            this.kubeClient = new KubernetesClientBuilder().withConfig(config).build();
        }

        var version = kubeClient.getKubernetesVersion();
        log.info("K8s 集群连接成功: version={}, namespace={}", version.getGitVersion(), this.namespace);
    }

    KubernetesEngine(KubernetesClient kubeClient, String namespace, ArtifactStore artifactStore) {
        this.kubeClient = kubeClient;
        this.namespace = namespace != null ? namespace : "default";
        this.artifactStore = artifactStore;
    }

    @Override
    public String providerType() {
        return "k8s";
    }

    @Override
    public DeployActorResponse deployActor(DeployActorRequest request, Path artifactLocalPath) {
        String actorId = request.getActorId();
        String podName = sanitizePodName(actorId);
        log.info("部署 K8s Pod: actorId={}, podName={}, lang={}, hasArtifact={}, hasFunctionDescriptor={}",
                actorId, podName, request.getLang(), artifactLocalPath != null, request.hasFunctionDescriptor());

        try {
            String functionConfigMapName = null;
            if (request.hasFunctionDescriptor()) {
                functionConfigMapName = createFunctionDescriptorConfigMap(actorId, request.getFunctionDescriptor());
            }
            Pod pod = buildPod(podName, request, artifactLocalPath, functionConfigMapName);
            if (pod == null) {
                throw new UnsupportedOperationException("buildPod 尚未实现，请补全 Pod 构建逻辑");
            }

            Pod created = kubeClient.pods().inNamespace(namespace).resource(pod).create();

            actorPods.put(actorId, created.getMetadata().getName());
            actorResources.put(actorId, request.getResourceRequest());

            log.info("K8s Pod 部署成功: actorId={}, pod={}", actorId, created.getMetadata().getName());

            return DeployActorResponse.newBuilder()
                    .setActor(ActorInfo.newBuilder()
                            .setActorId(actorId)
                            .setStatus(ActorStatus.RUNNING)
                            .build())
                    .build();

        } catch (Exception e) {
            log.error("K8s Pod 部署失败: actorId={}", actorId, e);
            return DeployActorResponse.newBuilder()
                    .setActor(ActorInfo.newBuilder()
                            .setActorId(actorId)
                            .setStatus(ActorStatus.FAILED)
                            .setMessage(e.getMessage())
                            .build())
                    .build();
        }
    }

    @Override
    public StopActorResponse stopActor(String actorId) {
        String podName = actorPods.get(actorId);
        if (podName == null) {
            return StopActorResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("未找到 Actor: " + actorId)
                    .build();
        }
        try {
            kubeClient.pods().inNamespace(namespace).withName(podName).delete();
            log.info("K8s Pod 已停止: actorId={}, pod={}", actorId, podName);
            return StopActorResponse.newBuilder().setSuccess(true).build();
        } catch (Exception e) {
            log.error("停止 K8s Pod 失败: actorId={}", actorId, e);
            return StopActorResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public RemoveActorResponse removeActor(String actorId) {
        String podName = actorPods.remove(actorId);
        if (podName == null) {
            return RemoveActorResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("未找到 Actor: " + actorId)
                    .build();
        }
        try {
            kubeClient.pods().inNamespace(namespace).withName(podName).delete();
            actorResources.remove(actorId);
            log.info("K8s Pod 已移除: actorId={}, pod={}", actorId, podName);
            return RemoveActorResponse.newBuilder().setSuccess(true).build();
        } catch (Exception e) {
            log.error("移除 K8s Pod 失败: actorId={}", actorId, e);
            actorPods.put(actorId, podName);
            return RemoveActorResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public GetActorStatusResponse getActorStatus(String actorId) {
        String podName = actorPods.get(actorId);
        if (podName == null) {
            return GetActorStatusResponse.newBuilder()
                    .setActor(ActorInfo.newBuilder()
                            .setActorId(actorId)
                            .setStatus(ActorStatus.ACTOR_STATUS_UNSPECIFIED)
                            .setMessage("未找到 Actor")
                            .build())
                    .build();
        }
        try {
            Pod pod = kubeClient.pods().inNamespace(namespace).withName(podName).get();
            if (pod == null) {
                actorPods.remove(actorId);
                return GetActorStatusResponse.newBuilder()
                        .setActor(ActorInfo.newBuilder()
                                .setActorId(actorId)
                                .setStatus(ActorStatus.REMOVED)
                                .build())
                        .build();
            }
            ActorStatus status = mapPodPhase(pod.getStatus().getPhase());
            return GetActorStatusResponse.newBuilder()
                    .setActor(ActorInfo.newBuilder()
                            .setActorId(actorId)
                            .setStatus(status)
                            .build())
                    .build();
        } catch (Exception e) {
            return GetActorStatusResponse.newBuilder()
                    .setActor(ActorInfo.newBuilder()
                            .setActorId(actorId)
                            .setStatus(ActorStatus.FAILED)
                            .setMessage(e.getMessage())
                            .build())
                    .build();
        }
    }

    @Override
    public void close() {
        kubeClient.close();
        log.info("K8s 客户端已关闭");
    }

    private String createFunctionDescriptorConfigMap(String actorId, FunctionDescriptor fd) {
        String name = "iarnet-fd-" + sanitizePodName(actorId);
        ConfigMap cm = new ConfigMapBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(namespace)
                .endMetadata()
                .addToBinaryData("function.pb", Arrays.toString(fd.toByteArray()))
                .build();
        kubeClient.configMaps().inNamespace(namespace).resource(cm).create();
        log.info("已创建函数描述 ConfigMap: actorId={}, name={}", actorId, name);
        return name;
    }

    /**
     * TODO: 补全 Pod 构建逻辑（镜像、资源、volume 挂载、env 等）。
     */
    private Pod buildPod(String podName, DeployActorRequest request, Path artifactLocalPath,
                         String functionConfigMapName) {
        // TODO: 实现完整的 Pod 构建
        return null;
    }

    private String sanitizePodName(String name) {
        String s = name.toLowerCase().replaceAll("[^a-z0-9\\-.]", "-");
        while (s.startsWith("-") || s.startsWith(".")) s = s.substring(1);
        while (s.endsWith("-") || s.endsWith(".")) s = s.substring(0, s.length() - 1);
        if (s.length() > 253) s = s.substring(0, 253);
        return s.isEmpty() ? "pod" : s;
    }

    private ActorStatus mapPodPhase(String phase) {
        if (phase == null) return ActorStatus.ACTOR_STATUS_UNSPECIFIED;
        return switch (phase) {
            case "Pending" -> ActorStatus.PENDING;
            case "Running" -> ActorStatus.RUNNING;
            case "Succeeded" -> ActorStatus.STOPPED;
            case "Failed" -> ActorStatus.FAILED;
            default -> ActorStatus.ACTOR_STATUS_UNSPECIFIED;
        };
    }
}
