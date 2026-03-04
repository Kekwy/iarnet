package com.kekwy.iarnet.adapter.engine.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.*;
import com.github.dockerjava.api.model.*;
import com.kekwy.iarnet.adapter.artifact.ArtifactStore;
import com.kekwy.iarnet.proto.adapter.*;
import com.kekwy.iarnet.proto.ir.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * DockerEngine 单元测试。
 * <p>
 * 通过 Mock DockerClient 验证引擎的部署、停止、移除、状态查询、资源统计等逻辑，
 * 不依赖真实 Docker daemon。
 */
class DockerEngineTest {

    private DockerClient dockerClient;
    private ArtifactStore artifactStore;
    private DockerEngine engine;

    private static final Resource TOTAL_RESOURCE = Resource.newBuilder()
            .setCpu(8.0)
            .setMemory("16Gi")
            .setGpu(1)
            .build();

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        dockerClient = mock(DockerClient.class);
        artifactStore = new ArtifactStore(tempDir);
        engine = new DockerEngine(dockerClient, "bridge", List.of("gpu", "linux"),
                TOTAL_RESOURCE, artifactStore);
    }

    // ======================== adapterType ========================

    /** 适配器类型应为 "docker" */
    @Test
    @DisplayName("adapterType 应返回 docker")
    void adapterType_shouldReturnDocker() {
        assertEquals("docker", engine.adapterType());
    }

    // ======================== getDeviceInfo ========================

    /** 获取设备信息：应包含类型、OS 架构、容量和标签 */
    @Test
    @DisplayName("getDeviceInfo：应返回完整的设备信息")
    void getDeviceInfo_shouldReturnCompleteInfo() {
        GetDeviceInfoResponse info = engine.getDeviceInfo();

        assertEquals("docker", info.getAdapterType());
        assertFalse(info.getOsArch().isEmpty(), "osArch 不应为空");
        assertNotNull(info.getCapacity());
        assertEquals(8.0, info.getCapacity().getTotal().getCpu(), 0.01);
        assertEquals("16Gi", info.getCapacity().getTotal().getMemory());
        assertEquals(2, info.getTagsList().size());
        assertTrue(info.getTagsList().contains("gpu"));
        assertTrue(info.getTagsList().contains("linux"));
    }

    // ======================== transferArtifact ========================

    /** artifact 传输：应写入本地文件并返回路径 */
    @Test
    @DisplayName("transferArtifact：应存储 artifact 并返回存储路径")
    void transferArtifact_shouldStoreAndReturnPath() throws IOException {
        byte[] content = "hello artifact".getBytes();
        TransferArtifactResponse response = engine.transferArtifact(
                "art-001", "app.jar", new ByteArrayInputStream(content));

        assertEquals("art-001", response.getArtifactId());
        assertFalse(response.getArtifactPath().isEmpty());
        assertTrue(response.getArtifactPath().contains("app.jar"),
                "存储路径应包含文件名");
    }

    // ======================== deployInstance ========================

    /** 部署成功：验证 Docker 创建并启动容器，返回 RUNNING 状态 */
    @Test
    @DisplayName("deployInstance：创建并启动容器，返回 RUNNING 状态")
    void deployInstance_success_shouldCreateAndStartContainer() {
        String instanceId = "inst-001";
        String containerId = "abc123";

        // Mock createContainerCmd
        CreateContainerCmd createCmd = mock(CreateContainerCmd.class, RETURNS_DEEP_STUBS);
        when(dockerClient.createContainerCmd(anyString())).thenReturn(createCmd);
        when(createCmd.withName(anyString())).thenReturn(createCmd);
        when(createCmd.withEnv(anyList())).thenReturn(createCmd);
        when(createCmd.withLabels(anyMap())).thenReturn(createCmd);
        when(createCmd.withHostConfig(any(HostConfig.class))).thenReturn(createCmd);
        when(createCmd.exec()).thenReturn(new CreateContainerResponse() {{
            setId(containerId);
        }});

        // Mock connectToNetworkCmd
        ConnectToNetworkCmd connectCmd = mock(ConnectToNetworkCmd.class);
        when(dockerClient.connectToNetworkCmd()).thenReturn(connectCmd);
        when(connectCmd.withNetworkId(anyString())).thenReturn(connectCmd);
        when(connectCmd.withContainerId(anyString())).thenReturn(connectCmd);

        // Mock startContainerCmd
        StartContainerCmd startCmd = mock(StartContainerCmd.class);
        when(dockerClient.startContainerCmd(containerId)).thenReturn(startCmd);

        // Mock inspectContainerCmd
        InspectContainerCmd inspectCmd = mock(InspectContainerCmd.class);
        when(dockerClient.inspectContainerCmd(containerId)).thenReturn(inspectCmd);
        InspectContainerResponse inspectResp = mock(InspectContainerResponse.class);
        when(inspectCmd.exec()).thenReturn(inspectResp);

        NetworkSettings networkSettings = mock(NetworkSettings.class);
        when(inspectResp.getNetworkSettings()).thenReturn(networkSettings);
        ContainerNetwork containerNetwork = mock(ContainerNetwork.class);
        when(containerNetwork.getIpAddress()).thenReturn("172.17.0.2");
        when(networkSettings.getNetworks()).thenReturn(Map.of("bridge", containerNetwork));
        when(networkSettings.getPorts()).thenReturn(null);

        // 执行部署
        DeployInstanceRequest request = DeployInstanceRequest.newBuilder()
                .setInstanceId(instanceId)
                .setArtifactId("art-001")
                .setImage("openjdk:17")
                .setResourceRequest(Resource.newBuilder().setCpu(2.0).setMemory("4Gi").build())
                .putEnvVars("APP_MODE", "worker")
                .putLabels("app", "test")
                .build();

        DeployInstanceResponse response = engine.deployInstance(request);

        assertEquals(instanceId, response.getInstance().getInstanceId());
        assertEquals(containerId, response.getInstance().getContainerId());
        assertEquals("172.17.0.2", response.getInstance().getHost());
        assertEquals(InstanceStatus.RUNNING, response.getInstance().getStatus());

        // 验证 Docker 命令调用顺序
        verify(dockerClient).createContainerCmd("openjdk:17");
        verify(dockerClient).startContainerCmd(containerId);
    }

    /** 部署失败：Docker API 抛异常，应返回 FAILED 状态 */
    @Test
    @DisplayName("deployInstance：Docker 异常时返回 FAILED 状态")
    void deployInstance_failure_shouldReturnFailed() {
        CreateContainerCmd createCmd = mock(CreateContainerCmd.class, RETURNS_DEEP_STUBS);
        when(dockerClient.createContainerCmd(anyString())).thenReturn(createCmd);
        when(createCmd.withName(anyString())).thenReturn(createCmd);
        when(createCmd.withEnv(anyList())).thenReturn(createCmd);
        when(createCmd.withLabels(anyMap())).thenReturn(createCmd);
        when(createCmd.withHostConfig(any(HostConfig.class))).thenReturn(createCmd);
        when(createCmd.exec()).thenThrow(new RuntimeException("镜像不存在"));

        DeployInstanceResponse response = engine.deployInstance(
                DeployInstanceRequest.newBuilder()
                        .setInstanceId("inst-fail")
                        .setImage("non-existent:latest")
                        .build());

        assertEquals("inst-fail", response.getInstance().getInstanceId());
        assertEquals(InstanceStatus.FAILED, response.getInstance().getStatus());
        assertTrue(response.getInstance().getMessage().contains("镜像不存在"));
    }

    // ======================== stopInstance ========================

    /** 停止已部署实例：应调用 Docker stop 并返回成功 */
    @Test
    @DisplayName("stopInstance：已部署实例停止应成功")
    void stopInstance_existingInstance_shouldSucceed() {
        deployMockInstance("inst-stop", "container-stop");

        StopContainerCmd stopCmd = mock(StopContainerCmd.class);
        when(dockerClient.stopContainerCmd("container-stop")).thenReturn(stopCmd);
        when(stopCmd.withTimeout(anyInt())).thenReturn(stopCmd);

        StopInstanceResponse response = engine.stopInstance("inst-stop");
        assertTrue(response.getSuccess());
        verify(dockerClient).stopContainerCmd("container-stop");
    }

    /** 停止不存在的实例：应返回失败 */
    @Test
    @DisplayName("stopInstance：不存在的实例应返回失败")
    void stopInstance_unknownInstance_shouldFail() {
        StopInstanceResponse response = engine.stopInstance("no-such-instance");
        assertFalse(response.getSuccess());
        assertTrue(response.getMessage().contains("未找到实例"));
    }

    // ======================== removeInstance ========================

    /** 移除已部署实例：应调用 Docker remove 并清理内部记录 */
    @Test
    @DisplayName("removeInstance：已部署实例移除应成功，内部记录被清理")
    void removeInstance_existingInstance_shouldSucceedAndCleanup() {
        deployMockInstance("inst-rm", "container-rm");

        RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
        when(dockerClient.removeContainerCmd("container-rm")).thenReturn(removeCmd);
        when(removeCmd.withForce(true)).thenReturn(removeCmd);

        RemoveInstanceResponse response = engine.removeInstance("inst-rm");
        assertTrue(response.getSuccess());

        // 再次移除应失败（已不在记录中）
        RemoveInstanceResponse again = engine.removeInstance("inst-rm");
        assertFalse(again.getSuccess());
    }

    /** 移除不存在的实例：应返回失败 */
    @Test
    @DisplayName("removeInstance：不存在的实例应返回失败")
    void removeInstance_unknownInstance_shouldFail() {
        RemoveInstanceResponse response = engine.removeInstance("no-such-instance");
        assertFalse(response.getSuccess());
    }

    // ======================== getInstanceStatus ========================

    /** 查询已部署实例状态：应返回来自 Docker inspect 的状态 */
    @Test
    @DisplayName("getInstanceStatus：已部署实例应返回来自 Docker 的状态信息")
    void getInstanceStatus_existingInstance_shouldReturnStatus() {
        deployMockInstance("inst-status", "container-status");

        InspectContainerCmd inspectCmd = mock(InspectContainerCmd.class);
        when(dockerClient.inspectContainerCmd("container-status")).thenReturn(inspectCmd);

        InspectContainerResponse inspectResp = mock(InspectContainerResponse.class);
        when(inspectCmd.exec()).thenReturn(inspectResp);

        InspectContainerResponse.ContainerState state = mock(InspectContainerResponse.ContainerState.class);
        when(inspectResp.getState()).thenReturn(state);
        when(state.getRunning()).thenReturn(true);

        NetworkSettings networkSettings = mock(NetworkSettings.class);
        when(inspectResp.getNetworkSettings()).thenReturn(networkSettings);
        ContainerNetwork cn = mock(ContainerNetwork.class);
        when(cn.getIpAddress()).thenReturn("172.17.0.5");
        when(networkSettings.getNetworks()).thenReturn(Map.of("bridge", cn));
        when(networkSettings.getPorts()).thenReturn(null);

        GetInstanceStatusResponse response = engine.getInstanceStatus("inst-status");
        assertEquals("inst-status", response.getInstance().getInstanceId());
        assertEquals(InstanceStatus.RUNNING, response.getInstance().getStatus());
        assertEquals("172.17.0.5", response.getInstance().getHost());
    }

    /** 查询未知实例状态：应返回 UNSPECIFIED */
    @Test
    @DisplayName("getInstanceStatus：不存在的实例应返回 UNSPECIFIED")
    void getInstanceStatus_unknownInstance_shouldReturnUnspecified() {
        GetInstanceStatusResponse response = engine.getInstanceStatus("unknown");
        assertEquals(InstanceStatus.INSTANCE_STATUS_UNSPECIFIED,
                response.getInstance().getStatus());
    }

    // ======================== getResourceUsage ========================

    /** 资源统计：未部署任何实例时，used 应为 0，available 等于 total */
    @Test
    @DisplayName("getResourceUsage：无实例时 available 应等于 total")
    void getResourceUsage_noInstances_shouldEqualTotal() {
        GetResourceUsageResponse response = engine.getResourceUsage();
        ResourceCapacity cap = response.getCapacity();

        assertEquals(8.0, cap.getTotal().getCpu(), 0.01);
        assertEquals(0.0, cap.getUsed().getCpu(), 0.01);
        assertEquals(8.0, cap.getAvailable().getCpu(), 0.01);
    }

    /** 资源统计：部署实例后 used 和 available 应正确更新 */
    @Test
    @DisplayName("getResourceUsage：部署实例后资源统计应正确更新")
    void getResourceUsage_afterDeploy_shouldReflectUsage() {
        deployMockInstanceWithResources("inst-res", "cid-res",
                Resource.newBuilder().setCpu(2.0).setMemory("4Gi").build());

        GetResourceUsageResponse response = engine.getResourceUsage();
        ResourceCapacity cap = response.getCapacity();

        assertEquals(2.0, cap.getUsed().getCpu(), 0.01, "已用 CPU 应为 2.0");
        assertEquals(6.0, cap.getAvailable().getCpu(), 0.01, "可用 CPU 应为 6.0");
    }

    // ======================== 完整生命周期 ========================

    /** 完整生命周期：部署 → 查询状态 → 停止 → 移除 */
    @Test
    @DisplayName("完整生命周期：部署 → 查询状态 → 停止 → 移除")
    void fullLifecycle() {
        String instanceId = "lifecycle-inst";
        String containerId = "lifecycle-cid";

        // --- 部署 ---
        setupMockDeploySuccess(instanceId, containerId, "172.17.0.10");
        DeployInstanceResponse deployResp = engine.deployInstance(
                DeployInstanceRequest.newBuilder()
                        .setInstanceId(instanceId)
                        .setImage("openjdk:17")
                        .setResourceRequest(Resource.newBuilder().setCpu(1.0).setMemory("2Gi").build())
                        .build());
        assertEquals(InstanceStatus.RUNNING, deployResp.getInstance().getStatus());
        System.out.println("[1/4] 部署成功: " + deployResp.getInstance());

        // --- 查询状态 ---
        setupMockInspectRunning(containerId, "172.17.0.10");
        GetInstanceStatusResponse statusResp = engine.getInstanceStatus(instanceId);
        assertEquals(InstanceStatus.RUNNING, statusResp.getInstance().getStatus());
        System.out.println("[2/4] 状态查询: " + statusResp.getInstance().getStatus());

        // --- 资源占用 ---
        GetResourceUsageResponse usageResp = engine.getResourceUsage();
        assertEquals(1.0, usageResp.getCapacity().getUsed().getCpu(), 0.01);
        System.out.println("[3/4] 资源使用: used_cpu=" + usageResp.getCapacity().getUsed().getCpu());

        // --- 停止 ---
        StopContainerCmd stopCmd = mock(StopContainerCmd.class);
        when(dockerClient.stopContainerCmd(containerId)).thenReturn(stopCmd);
        when(stopCmd.withTimeout(anyInt())).thenReturn(stopCmd);
        assertTrue(engine.stopInstance(instanceId).getSuccess());

        // --- 移除 ---
        RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
        when(dockerClient.removeContainerCmd(containerId)).thenReturn(removeCmd);
        when(removeCmd.withForce(true)).thenReturn(removeCmd);
        assertTrue(engine.removeInstance(instanceId).getSuccess());

        // 移除后资源应释放
        usageResp = engine.getResourceUsage();
        assertEquals(0.0, usageResp.getCapacity().getUsed().getCpu(), 0.01);
        System.out.println("[4/4] 移除后资源释放完成");
    }

    // ======================== 辅助方法 ========================

    /**
     * 在 engine 内部注册一个已部署实例（绕过 Docker 调用，直接操作内部 map）。
     */
    private void deployMockInstance(String instanceId, String containerId) {
        deployMockInstanceWithResources(instanceId, containerId,
                Resource.newBuilder().build());
    }

    private void deployMockInstanceWithResources(String instanceId, String containerId,
                                                  Resource resource) {
        setupMockDeploySuccess(instanceId, containerId, "172.17.0.2");

        engine.deployInstance(DeployInstanceRequest.newBuilder()
                .setInstanceId(instanceId)
                .setImage("test:latest")
                .setResourceRequest(resource)
                .build());
    }

    private void setupMockDeploySuccess(String instanceId, String containerId, String ip) {
        CreateContainerCmd createCmd = mock(CreateContainerCmd.class, RETURNS_DEEP_STUBS);
        when(dockerClient.createContainerCmd(anyString())).thenReturn(createCmd);
        when(createCmd.withName(anyString())).thenReturn(createCmd);
        when(createCmd.withEnv(anyList())).thenReturn(createCmd);
        when(createCmd.withLabels(anyMap())).thenReturn(createCmd);
        when(createCmd.withHostConfig(any(HostConfig.class))).thenReturn(createCmd);
        when(createCmd.exec()).thenReturn(new CreateContainerResponse() {{
            setId(containerId);
        }});

        ConnectToNetworkCmd connectCmd = mock(ConnectToNetworkCmd.class);
        when(dockerClient.connectToNetworkCmd()).thenReturn(connectCmd);
        when(connectCmd.withNetworkId(anyString())).thenReturn(connectCmd);
        when(connectCmd.withContainerId(anyString())).thenReturn(connectCmd);

        StartContainerCmd startCmd = mock(StartContainerCmd.class);
        when(dockerClient.startContainerCmd(containerId)).thenReturn(startCmd);

        setupMockInspectRunning(containerId, ip);
    }

    private void setupMockInspectRunning(String containerId, String ip) {
        InspectContainerCmd inspectCmd = mock(InspectContainerCmd.class);
        when(dockerClient.inspectContainerCmd(containerId)).thenReturn(inspectCmd);

        InspectContainerResponse inspectResp = mock(InspectContainerResponse.class);
        when(inspectCmd.exec()).thenReturn(inspectResp);

        InspectContainerResponse.ContainerState state = mock(InspectContainerResponse.ContainerState.class);
        when(inspectResp.getState()).thenReturn(state);
        when(state.getRunning()).thenReturn(true);

        NetworkSettings networkSettings = mock(NetworkSettings.class);
        when(inspectResp.getNetworkSettings()).thenReturn(networkSettings);
        ContainerNetwork cn = mock(ContainerNetwork.class);
        when(cn.getIpAddress()).thenReturn(ip);
        when(networkSettings.getNetworks()).thenReturn(Map.of("bridge", cn));
        when(networkSettings.getPorts()).thenReturn(null);
    }
}
