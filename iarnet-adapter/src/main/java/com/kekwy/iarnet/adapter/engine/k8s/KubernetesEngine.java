package com.kekwy.iarnet.adapter.engine.k8s;

import com.kekwy.iarnet.adapter.artifact.ArtifactStore;
import com.kekwy.iarnet.adapter.engine.AdapterEngine;
import com.kekwy.iarnet.proto.adapter.*;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kubernetes 资源适配器引擎。
 * <p>
 * 使用 fabric8 kubernetes-client 与 K8s 集群通信，
 * 将每个 Actor 实例部署为一个 Pod。
 */
public class KubernetesEngine implements AdapterEngine {

    private static final Logger log = LoggerFactory.getLogger(KubernetesEngine.class);

    private final KubernetesClient kubeClient;
    private final ArtifactStore artifactStore;
    private final String namespace;
    private final String osArch;
    private final List<String> tags;
    private final com.kekwy.iarnet.proto.ir.Resource totalResource;

    /** instanceId → podName */
    private final Map<String, String> instancePods = new ConcurrentHashMap<>();
    /** instanceId → 分配的资源 */
    private final Map<String, com.kekwy.iarnet.proto.ir.Resource> instanceResources = new ConcurrentHashMap<>();

    public KubernetesEngine(String kubeconfig, boolean inCluster, String namespace,
                            List<String> tags, com.kekwy.iarnet.proto.ir.Resource totalResource,
                            ArtifactStore artifactStore) {
        this.namespace = namespace != null ? namespace : "default";
        this.tags = tags != null ? tags : List.of();
        this.totalResource = totalResource;
        this.artifactStore = artifactStore;
        this.osArch = System.getProperty("os.name") + "/" + System.getProperty("os.arch");

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

    /**
     * 接受预构建的 {@link KubernetesClient}，用于单元测试场景。
     */
    KubernetesEngine(KubernetesClient kubeClient, String namespace, List<String> tags,
                     com.kekwy.iarnet.proto.ir.Resource totalResource,
                     ArtifactStore artifactStore) {
        this.kubeClient = kubeClient;
        this.namespace = namespace != null ? namespace : "default";
        this.tags = tags != null ? tags : List.of();
        this.totalResource = totalResource;
        this.artifactStore = artifactStore;
        this.osArch = System.getProperty("os.name") + "/" + System.getProperty("os.arch");
    }

    @Override
    public String adapterType() {
        return "k8s";
    }

    @Override
    public GetDeviceInfoResponse getDeviceInfo() {
        return GetDeviceInfoResponse.newBuilder()
                .setAdapterType(adapterType())
                .setOsArch(osArch)
                .setCapacity(computeCapacity())
                .addAllTags(tags)
                .build();
    }

    @Override
    public TransferArtifactResponse transferArtifact(String artifactId, String fileName, InputStream data) throws IOException {
        var path = artifactStore.store(artifactId, fileName, data);
        return TransferArtifactResponse.newBuilder()
                .setArtifactId(artifactId)
                .setArtifactPath(path.toString())
                .build();
    }

    @Override
    public DeployInstanceResponse deployInstance(DeployInstanceRequest request, java.nio.file.Path artifactLocalPath) {
        String instanceId = request.getInstanceId();
        String podName = sanitizePodName(instanceId);
        var lang = request.getLang();
        log.info("部署 K8s Pod: instanceId={}, podName={}, lang={}, hasArtifact={}",
                instanceId, podName, lang, artifactLocalPath != null);

        try {
            Pod pod = buildPod(podName, request, artifactLocalPath);
            Pod created = kubeClient.pods().inNamespace(namespace).resource(pod).create();

            instancePods.put(instanceId, created.getMetadata().getName());
            instanceResources.put(instanceId, request.getResourceRequest());

            String podIp = waitForPodIp(created.getMetadata().getName());

            log.info("K8s Pod 部署成功: instanceId={}, pod={}", instanceId, created.getMetadata().getName());

            return DeployInstanceResponse.newBuilder()
                    .setInstance(InstanceInfo.newBuilder()
                            .setInstanceId(instanceId)
                            .setContainerId(created.getMetadata().getName())
                            .setHost(podIp != null ? podIp : "")
                            .setStatus(InstanceStatus.RUNNING)
                            .build())
                    .build();

        } catch (Exception e) {
            log.error("K8s Pod 部署失败: instanceId={}", instanceId, e);
            return DeployInstanceResponse.newBuilder()
                    .setInstance(InstanceInfo.newBuilder()
                            .setInstanceId(instanceId)
                            .setStatus(InstanceStatus.FAILED)
                            .setMessage(e.getMessage())
                            .build())
                    .build();
        }
    }

    @Override
    public StopInstanceResponse stopInstance(String instanceId) {
        String podName = instancePods.get(instanceId);
        if (podName == null) {
            return StopInstanceResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("未找到实例: " + instanceId)
                    .build();
        }

        try {
            kubeClient.pods().inNamespace(namespace).withName(podName).delete();
            log.info("K8s Pod 已删除（停止）: instanceId={}, pod={}", instanceId, podName);
            return StopInstanceResponse.newBuilder().setSuccess(true).build();
        } catch (Exception e) {
            log.error("停止 K8s Pod 失败: instanceId={}", instanceId, e);
            return StopInstanceResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public RemoveInstanceResponse removeInstance(String instanceId) {
        String podName = instancePods.remove(instanceId);
        if (podName == null) {
            return RemoveInstanceResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("未找到实例: " + instanceId)
                    .build();
        }

        try {
            kubeClient.pods().inNamespace(namespace).withName(podName).delete();
            instanceResources.remove(instanceId);
            log.info("K8s Pod 已移除: instanceId={}, pod={}", instanceId, podName);
            return RemoveInstanceResponse.newBuilder().setSuccess(true).build();
        } catch (Exception e) {
            log.error("移除 K8s Pod 失败: instanceId={}", instanceId, e);
            instancePods.put(instanceId, podName);
            return RemoveInstanceResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public GetInstanceStatusResponse getInstanceStatus(String instanceId) {
        String podName = instancePods.get(instanceId);
        if (podName == null) {
            return GetInstanceStatusResponse.newBuilder()
                    .setInstance(InstanceInfo.newBuilder()
                            .setInstanceId(instanceId)
                            .setStatus(InstanceStatus.INSTANCE_STATUS_UNSPECIFIED)
                            .setMessage("未找到实例")
                            .build())
                    .build();
        }

        try {
            Pod pod = kubeClient.pods().inNamespace(namespace).withName(podName).get();
            if (pod == null) {
                instancePods.remove(instanceId);
                return GetInstanceStatusResponse.newBuilder()
                        .setInstance(InstanceInfo.newBuilder()
                                .setInstanceId(instanceId)
                                .setStatus(InstanceStatus.REMOVED)
                                .build())
                        .build();
            }

            InstanceStatus status = mapPodPhase(pod.getStatus().getPhase());
            String podIp = pod.getStatus().getPodIP();

            return GetInstanceStatusResponse.newBuilder()
                    .setInstance(InstanceInfo.newBuilder()
                            .setInstanceId(instanceId)
                            .setContainerId(podName)
                            .setHost(podIp != null ? podIp : "")
                            .setStatus(status)
                            .build())
                    .build();
        } catch (Exception e) {
            return GetInstanceStatusResponse.newBuilder()
                    .setInstance(InstanceInfo.newBuilder()
                            .setInstanceId(instanceId)
                            .setContainerId(podName)
                            .setStatus(InstanceStatus.FAILED)
                            .setMessage(e.getMessage())
                            .build())
                    .build();
        }
    }

    @Override
    public GetResourceUsageResponse getResourceUsage() {
        return GetResourceUsageResponse.newBuilder()
                .setCapacity(computeCapacity())
                .build();
    }

    @Override
    public void close() {
        kubeClient.close();
        log.info("K8s 客户端已关闭");
    }

    // ======================== 内部方法 ========================

    private static final String CONTAINER_ARTIFACT_DIR = "/opt/iarnet/artifact";

    private Pod buildPod(String podName, DeployInstanceRequest request, java.nio.file.Path artifactLocalPath) {
        var resource = request.getResourceRequest();

        Map<String, Quantity> resourceRequests = new HashMap<>();
        Map<String, Quantity> resourceLimits = new HashMap<>();

        if (resource.getCpu() > 0) {
            String cpuMillis = (int) (resource.getCpu() * 1000) + "m";
            resourceRequests.put("cpu", new Quantity(cpuMillis));
            resourceLimits.put("cpu", new Quantity(cpuMillis));
        }
        if (resource.getMemory() != null && !resource.getMemory().isEmpty()) {
            resourceRequests.put("memory", new Quantity(resource.getMemory()));
            resourceLimits.put("memory", new Quantity(resource.getMemory()));
        }
        if (resource.getGpu() > 0) {
            String gpuStr = String.valueOf((int) resource.getGpu());
            resourceRequests.put("nvidia.com/gpu", new Quantity(gpuStr));
            resourceLimits.put("nvidia.com/gpu", new Quantity(gpuStr));
        }

        List<EnvVar> envVars = new ArrayList<>();
        request.getEnvVarsMap().forEach((k, v) -> envVars.add(new EnvVarBuilder().withName(k).withValue(v).build()));
        // 默认假设 Actor Pod 可访问 NodeIP:10000；后续可根据需要改为配置化
        envVars.add(new EnvVarBuilder().withName("IARNET_DEVICE_AGENT_ADDR")
                .withValue("127.0.0.1:10000").build());
        if (artifactLocalPath != null && java.nio.file.Files.isRegularFile(artifactLocalPath)) {
            String inContainerPath = CONTAINER_ARTIFACT_DIR + "/" + artifactLocalPath.getFileName().toString();
            envVars.add(new EnvVarBuilder().withName("IARNET_ARTIFACT_PATH").withValue(inContainerPath).build());
        }

        Map<String, String> labels = new HashMap<>(request.getLabelsMap());
        labels.put("iarnet.managed", "true");
        labels.put("iarnet.instance-id", request.getInstanceId());

        boolean hasArtifact = artifactLocalPath != null && java.nio.file.Files.isRegularFile(artifactLocalPath);
        String image = resolveImageForLang(request.getLang());

        if (hasArtifact) {
            return new PodBuilder()
                    .withNewMetadata()
                        .withName(podName)
                        .withNamespace(namespace)
                        .withLabels(labels)
                    .endMetadata()
                    .withNewSpec()
                        .addNewVolume()
                            .withName("artifact")
                            .withNewHostPath()
                                .withPath(artifactLocalPath.getParent().toAbsolutePath().toString())
                                .withType("Directory")
                            .endHostPath()
                        .endVolume()
                        .addNewContainer()
                            .withName("main")
                            .withImage(image)
                            .withImagePullPolicy("IfNotPresent")
                            .withEnv(envVars)
                            .addNewVolumeMount()
                                .withName("artifact")
                                .withMountPath(CONTAINER_ARTIFACT_DIR)
                                .withReadOnly(true)
                            .endVolumeMount()
                            .withNewResources()
                                .withRequests(resourceRequests)
                                .withLimits(resourceLimits)
                            .endResources()
                        .endContainer()
                        .withRestartPolicy("Never")
                    .endSpec()
                    .build();
        }

        return new PodBuilder()
                .withNewMetadata()
                    .withName(podName)
                    .withNamespace(namespace)
                    .withLabels(labels)
                .endMetadata()
                .withNewSpec()
                    .addNewContainer()
                        .withName("main")
                        .withImage(image)
                        .withImagePullPolicy("IfNotPresent")
                        .withEnv(envVars)
                        .withNewResources()
                            .withRequests(resourceRequests)
                            .withLimits(resourceLimits)
                        .endResources()
                    .endContainer()
                    .withRestartPolicy("Never")
                .endSpec()
                .build();
    }

    private String resolveImageForLang(com.kekwy.iarnet.proto.ir.Lang lang) {
        if (lang == null) {
            return "iarnet-actor-java:latest";
        }
        return switch (lang) {
            case LANG_PYTHON -> "iarnet-actor-python:latest";
//            case LANG_GO -> "iarnet-actor-go:latest";
            default -> "iarnet-actor-java:latest";
        };
    }

    private String waitForPodIp(String podName) {
        for (int i = 0; i < 30; i++) {
            Pod pod = kubeClient.pods().inNamespace(namespace).withName(podName).get();
            if (pod != null && pod.getStatus() != null && pod.getStatus().getPodIP() != null) {
                return pod.getStatus().getPodIP();
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        log.warn("等待 Pod IP 超时: pod={}", podName);
        return null;
    }

    private String sanitizePodName(String name) {
        String sanitized = name.toLowerCase().replaceAll("[^a-z0-9\\-.]", "-");
        while (sanitized.startsWith("-") || sanitized.startsWith(".")) {
            sanitized = sanitized.substring(1);
        }
        while (sanitized.endsWith("-") || sanitized.endsWith(".")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1);
        }
        if (sanitized.length() > 253) {
            sanitized = sanitized.substring(0, 253);
        }
        return sanitized.isEmpty() ? "pod" : sanitized;
    }

    private ResourceCapacity computeCapacity() {
        double usedCpu = 0;
        double usedGpu = 0;
        long usedMemBytes = 0;

        for (var res : instanceResources.values()) {
            usedCpu += res.getCpu();
            usedGpu += res.getGpu();
            usedMemBytes += parseMemoryBytes(res.getMemory());
        }

        long totalMemBytes = parseMemoryBytes(totalResource.getMemory());

        return ResourceCapacity.newBuilder()
                .setTotal(totalResource)
                .setUsed(com.kekwy.iarnet.proto.ir.Resource.newBuilder()
                        .setCpu(usedCpu)
                        .setMemory(usedMemBytes + "")
                        .setGpu(usedGpu)
                        .build())
                .setAvailable(com.kekwy.iarnet.proto.ir.Resource.newBuilder()
                        .setCpu(totalResource.getCpu() - usedCpu)
                        .setMemory((totalMemBytes - usedMemBytes) + "")
                        .setGpu(totalResource.getGpu() - usedGpu)
                        .build())
                .build();
    }

    private long parseMemoryBytes(String memory) {
        if (memory == null || memory.isBlank()) return 0;
        memory = memory.trim().toUpperCase();
        if (memory.endsWith("GI") || memory.endsWith("G")) {
            return (long) (Double.parseDouble(memory.replaceAll("[^\\d.]", "")) * 1024 * 1024 * 1024);
        }
        if (memory.endsWith("MI") || memory.endsWith("M")) {
            return (long) (Double.parseDouble(memory.replaceAll("[^\\d.]", "")) * 1024 * 1024);
        }
        if (memory.endsWith("KI") || memory.endsWith("K")) {
            return (long) (Double.parseDouble(memory.replaceAll("[^\\d.]", "")) * 1024);
        }
        try {
            return Long.parseLong(memory.replaceAll("[^\\d]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private InstanceStatus mapPodPhase(String phase) {
        if (phase == null) return InstanceStatus.INSTANCE_STATUS_UNSPECIFIED;
        return switch (phase) {
            case "Pending" -> InstanceStatus.PENDING;
            case "Running" -> InstanceStatus.RUNNING;
            case "Succeeded" -> InstanceStatus.STOPPED;
            case "Failed" -> InstanceStatus.FAILED;
            case "Unknown" -> InstanceStatus.INSTANCE_STATUS_UNSPECIFIED;
            default -> InstanceStatus.INSTANCE_STATUS_UNSPECIFIED;
        };
    }
}
