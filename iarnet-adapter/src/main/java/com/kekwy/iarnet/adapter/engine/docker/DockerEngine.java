package com.kekwy.iarnet.adapter.engine.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import com.kekwy.iarnet.adapter.artifact.ArtifactStore;
import com.kekwy.iarnet.adapter.engine.AdapterEngine;
import com.kekwy.iarnet.proto.adapter.*;
import com.kekwy.iarnet.proto.ir.FunctionDescriptor;
import com.kekwy.iarnet.proto.ir.Lang;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Docker 资源适配器引擎。
 * <p>
 * 使用 docker-java 客户端与本地或远程 Docker daemon 通信，
 * 实现容器的部署、停止、移除和状态查询。
 */
public class DockerEngine implements AdapterEngine {

    private static final Logger log = LoggerFactory.getLogger(DockerEngine.class);

    private final DockerClient dockerClient;
    private final ArtifactStore artifactStore;
    private final String network;
    private final String osArch;
    private final List<String> tags;
    private final com.kekwy.iarnet.proto.adapter.ResourceCapacity capacity;

    /** instanceId → containerId */
    private final Map<String, String> instanceContainers = new ConcurrentHashMap<>();
    /** instanceId → 分配的资源 */
    private final Map<String, com.kekwy.iarnet.proto.ir.Resource> instanceResources = new ConcurrentHashMap<>();

    private final String deviceAgentAddr;

    public DockerEngine(String dockerHost, String network, List<String> tags,
                        com.kekwy.iarnet.proto.ir.Resource totalResource,
                        ArtifactStore artifactStore) {
        this.network = network;
        this.tags = tags != null ? tags : List.of();
        this.artifactStore = artifactStore;
        this.osArch = System.getProperty("os.name") + "/" + System.getProperty("os.arch");
        // 默认假设 Actor 容器与 Adapter 共用 host 网络，通过 127.0.0.1 回连本机 Device Agent
        this.deviceAgentAddr = "172.30.23.95:10000"; // TODO: 使用配置文件中的配置

        this.capacity = ResourceCapacity.newBuilder()
                .setTotal(totalResource)
                .setUsed(com.kekwy.iarnet.proto.ir.Resource.newBuilder().build())
                .setAvailable(totalResource)
                .build();

        DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerHost != null ? dockerHost : "unix:///var/run/docker.sock")
                .build();

        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

        this.dockerClient = DockerClientImpl.getInstance(config, httpClient);

        dockerClient.pingCmd().exec();
        log.info("Docker daemon 连接成功: host={}", dockerHost);
    }

    /**
     * 接受预构建的 {@link DockerClient}，用于单元测试场景。
     */
    DockerEngine(DockerClient dockerClient, String network, List<String> tags,
                 com.kekwy.iarnet.proto.ir.Resource totalResource,
                 ArtifactStore artifactStore) {
        this.dockerClient = dockerClient;
        this.network = network;
        this.tags = tags != null ? tags : List.of();
        this.artifactStore = artifactStore;
        this.osArch = System.getProperty("os.name") + "/" + System.getProperty("os.arch");
        this.deviceAgentAddr = "127.0.0.1:10000";
        this.capacity = ResourceCapacity.newBuilder()
                .setTotal(totalResource)
                .setUsed(com.kekwy.iarnet.proto.ir.Resource.newBuilder().build())
                .setAvailable(totalResource)
                .build();
    }

    @Override
    public String adapterType() {
        return "docker";
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

    private static final String CONTAINER_ARTIFACT_DIR = "/opt/iarnet/artifact";
    private static final String CONTAINER_FUNCTION_DIR = "/opt/iarnet/function";
    private static final String CONTAINER_FUNCTION_FILE = CONTAINER_FUNCTION_DIR + "/function.pb";

    private String resolveImageForLang(Lang lang) {
        if (lang == null) {
            return "iarnet-actor-java:latest";
        }
        return switch (lang) {
            case LANG_PYTHON -> "iarnet-actor-python:latest";
//            case LANG_GO -> "iarnet-actor-go:latest";
            default -> "iarnet-actor-java:latest";
        };
    }

    @Override
    public DeployInstanceResponse deployInstance(DeployInstanceRequest request, Path artifactLocalPath) {
        String instanceId = request.getInstanceId();
        Lang lang = request.getLang();
        String image = resolveImageForLang(lang);
        log.info("部署 Docker 实例: instanceId={}, artifactId={}, lang={}, image={}, hasArtifact={}, hasFunctionDescriptor={}",
                instanceId, request.getArtifactId(), lang, image, artifactLocalPath != null, request.hasFunctionDescriptor());

        try {
            Path functionDescriptorPath = null;
            if (request.hasFunctionDescriptor()) {
                FunctionDescriptor fd = request.getFunctionDescriptor();
                functionDescriptorPath = artifactStore.storeFunctionDescriptor(instanceId, fd.toByteArray());
            }

            List<String> envList = new ArrayList<>();
            request.getEnvVarsMap().forEach((k, v) -> envList.add(k + "=" + v));
            envList.add("IARNET_DEVICE_AGENT_ADDR=" + deviceAgentAddr);
            if (artifactLocalPath != null && java.nio.file.Files.isRegularFile(artifactLocalPath)) {
                String inContainerPath = CONTAINER_ARTIFACT_DIR + "/" + artifactLocalPath.getFileName().toString();
                envList.add("IARNET_ARTIFACT_PATH=" + inContainerPath);
            }
            if (functionDescriptorPath != null) {
                envList.add("IARNET_ACTOR_FUNCTION_FILE=" + CONTAINER_FUNCTION_FILE);
            }

            Map<String, String> labels = new HashMap<>(request.getLabelsMap());
            labels.put("iarnet.managed", "true");
            labels.put("iarnet.instance_id", instanceId);

            var createCmd = dockerClient.createContainerCmd(image)
                    .withName(instanceId)
                    .withEnv(envList)
                    .withLabels(labels)
                    .withHostConfig(buildHostConfig(request, artifactLocalPath, functionDescriptorPath));

            CreateContainerResponse container = createCmd.exec();
            String containerId = container.getId();

            if (network != null && !network.isBlank()) {
                dockerClient.connectToNetworkCmd()
                        .withNetworkId(network)
                        .withContainerId(containerId)
                        .exec();
            }

            dockerClient.startContainerCmd(containerId).exec();

            instanceContainers.put(instanceId, containerId);
            instanceResources.put(instanceId, request.getResourceRequest());

            InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerId).exec();
            String host = extractHost(inspect);
            int port = extractPort(inspect);

            log.info("Docker 实例部署成功: instanceId={}, containerId={}", instanceId, containerId);

            return DeployInstanceResponse.newBuilder()
                    .setInstance(InstanceInfo.newBuilder()
                            .setInstanceId(instanceId)
                            .setContainerId(containerId)
                            .setHost(host)
                            .setPort(port)
                            .setStatus(InstanceStatus.RUNNING)
                            .build())
                    .build();

        } catch (Exception e) {
            log.error("Docker 实例部署失败: instanceId={}", instanceId, e);
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
        String containerId = instanceContainers.get(instanceId);
        if (containerId == null) {
            return StopInstanceResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("未找到实例: " + instanceId)
                    .build();
        }

        try {
            dockerClient.stopContainerCmd(containerId).withTimeout(10).exec();
            log.info("Docker 实例已停止: instanceId={}", instanceId);
            return StopInstanceResponse.newBuilder().setSuccess(true).build();
        } catch (Exception e) {
            log.error("停止 Docker 实例失败: instanceId={}", instanceId, e);
            return StopInstanceResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public RemoveInstanceResponse removeInstance(String instanceId) {
        String containerId = instanceContainers.remove(instanceId);
        if (containerId == null) {
            return RemoveInstanceResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage("未找到实例: " + instanceId)
                    .build();
        }

        try {
            dockerClient.removeContainerCmd(containerId).withForce(true).exec();
            instanceResources.remove(instanceId);
            log.info("Docker 实例已移除: instanceId={}", instanceId);
            return RemoveInstanceResponse.newBuilder().setSuccess(true).build();
        } catch (Exception e) {
            log.error("移除 Docker 实例失败: instanceId={}", instanceId, e);
            instanceContainers.put(instanceId, containerId);
            return RemoveInstanceResponse.newBuilder()
                    .setSuccess(false)
                    .setMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public GetInstanceStatusResponse getInstanceStatus(String instanceId) {
        String containerId = instanceContainers.get(instanceId);
        if (containerId == null) {
            return GetInstanceStatusResponse.newBuilder()
                    .setInstance(InstanceInfo.newBuilder()
                            .setInstanceId(instanceId)
                            .setStatus(InstanceStatus.INSTANCE_STATUS_UNSPECIFIED)
                            .setMessage("未找到实例")
                            .build())
                    .build();
        }

        try {
            InspectContainerResponse inspect = dockerClient.inspectContainerCmd(containerId).exec();
            InstanceStatus status = mapContainerStatus(inspect.getState());

            return GetInstanceStatusResponse.newBuilder()
                    .setInstance(InstanceInfo.newBuilder()
                            .setInstanceId(instanceId)
                            .setContainerId(containerId)
                            .setHost(extractHost(inspect))
                            .setPort(extractPort(inspect))
                            .setStatus(status)
                            .build())
                    .build();
        } catch (Exception e) {
            return GetInstanceStatusResponse.newBuilder()
                    .setInstance(InstanceInfo.newBuilder()
                            .setInstanceId(instanceId)
                            .setContainerId(containerId)
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
    public void close() throws Exception {
        dockerClient.close();
        log.info("Docker 客户端已关闭");
    }

    // ======================== 内部方法 ========================

    private HostConfig buildHostConfig(DeployInstanceRequest request, Path artifactLocalPath, Path functionDescriptorPath) {
        var resource = request.getResourceRequest();
        HostConfig hostConfig = HostConfig.newHostConfig();

        if (resource.getCpu() > 0) {
            hostConfig.withNanoCPUs((long) (resource.getCpu() * 1_000_000_000));
        }
        if (resource.getMemory() != null && !resource.getMemory().isEmpty()) {
            hostConfig.withMemory(parseMemory(resource.getMemory()));
        }
        List<Bind> binds = new ArrayList<>();
        if (artifactLocalPath != null && java.nio.file.Files.isRegularFile(artifactLocalPath)) {
            Path hostDir = artifactLocalPath.getParent();
            binds.add(new Bind(hostDir.toAbsolutePath().toString(), new Volume(CONTAINER_ARTIFACT_DIR)));
        }
        if (functionDescriptorPath != null && java.nio.file.Files.isRegularFile(functionDescriptorPath)) {
            String hostDir = functionDescriptorPath.getParent().toAbsolutePath().toString();
            binds.add(new Bind(hostDir, new Volume(CONTAINER_FUNCTION_DIR)));
        }
        if (!binds.isEmpty()) {
            hostConfig.withBinds(binds);
        }
        return hostConfig;
    }

    private long parseMemory(String memory) {
        if (memory == null) {
            return 0L;
        }
        memory = memory.trim().toUpperCase();
        if (memory.isEmpty()) {
            return 0L;
        }
        if (memory.endsWith("GI") || memory.endsWith("G")) {
            return (long) (Double.parseDouble(memory.replaceAll("[^\\d.]", "")) * 1024 * 1024 * 1024);
        }
        if (memory.endsWith("MI") || memory.endsWith("M")) {
            return (long) (Double.parseDouble(memory.replaceAll("[^\\d.]", "")) * 1024 * 1024);
        }
        if (memory.endsWith("KI") || memory.endsWith("K")) {
            return (long) (Double.parseDouble(memory.replaceAll("[^\\d.]", "")) * 1024);
        }
        String digits = memory.replaceAll("[^\\d]", "");
        if (digits.isEmpty()) {
            return 0L;
        }
        return Long.parseLong(digits);
    }

    private ResourceCapacity computeCapacity() {
        double usedCpu = 0;
        double usedGpu = 0;
        long usedMemBytes = 0;

        for (var res : instanceResources.values()) {
            usedCpu += res.getCpu();
            usedGpu += res.getGpu();
            usedMemBytes += parseMemory(res.getMemory());
        }

        var total = capacity.getTotal();
        long totalMemBytes = parseMemory(total.getMemory());

        return ResourceCapacity.newBuilder()
                .setTotal(total)
                .setUsed(com.kekwy.iarnet.proto.ir.Resource.newBuilder()
                        .setCpu(usedCpu)
                        .setMemory(usedMemBytes + "")
                        .setGpu(usedGpu)
                        .build())
                .setAvailable(com.kekwy.iarnet.proto.ir.Resource.newBuilder()
                        .setCpu(total.getCpu() - usedCpu)
                        .setMemory((totalMemBytes - usedMemBytes) + "")
                        .setGpu(total.getGpu() - usedGpu)
                        .build())
                .build();
    }

    private String extractHost(InspectContainerResponse inspect) {
        var networkSettings = inspect.getNetworkSettings();
        if (networkSettings != null && networkSettings.getNetworks() != null) {
            for (var entry : networkSettings.getNetworks().entrySet()) {
                String ip = entry.getValue().getIpAddress();
                if (ip != null && !ip.isBlank()) {
                    return ip;
                }
            }
        }
        return "127.0.0.1";
    }

    private int extractPort(InspectContainerResponse inspect) {
        var networkSettings = inspect.getNetworkSettings();
        if (networkSettings != null && networkSettings.getPorts() != null) {
            var bindings = networkSettings.getPorts().getBindings();
            if (bindings != null) {
                for (var entry : bindings.entrySet()) {
                    Ports.Binding[] portBindings = entry.getValue();
                    if (portBindings != null && portBindings.length > 0) {
                        try {
                            return Integer.parseInt(portBindings[0].getHostPortSpec());
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }
        }
        return 0;
    }

    private InstanceStatus mapContainerStatus(InspectContainerResponse.ContainerState state) {
        if (state == null) return InstanceStatus.INSTANCE_STATUS_UNSPECIFIED;
        Boolean running = state.getRunning();
        if (Boolean.TRUE.equals(running)) return InstanceStatus.RUNNING;
        Boolean dead = state.getDead();
        if (Boolean.TRUE.equals(dead)) return InstanceStatus.FAILED;
        String status = state.getStatus();
        if (status != null) {
            return switch (status.toLowerCase()) {
                case "created" -> InstanceStatus.PENDING;
                case "running" -> InstanceStatus.RUNNING;
                case "paused", "exited" -> InstanceStatus.STOPPED;
                case "dead" -> InstanceStatus.FAILED;
                case "removing" -> InstanceStatus.REMOVED;
                default -> InstanceStatus.INSTANCE_STATUS_UNSPECIFIED;
            };
        }
        return InstanceStatus.INSTANCE_STATUS_UNSPECIFIED;
    }
}
