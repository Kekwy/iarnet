package com.kekwy.iarnet.adapter.engine.k8s;

import com.kekwy.iarnet.adapter.artifact.ArtifactStore;
import com.kekwy.iarnet.proto.adapter.*;
import com.kekwy.iarnet.proto.ir.Resource;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodStatusBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

/**
 * KubernetesEngine 单元测试。
 * <p>
 * 通过 Mock KubernetesClient 验证引擎的部署、停止、移除、状态查询、资源统计等逻辑，
 * 不依赖真实 K8s 集群。
 */
class KubernetesEngineTest {

    private KubernetesClient kubeClient;
    private ArtifactStore artifactStore;
    private KubernetesEngine engine;

    private static final Resource TOTAL_RESOURCE = Resource.newBuilder()
            .setCpu(8.0)
            .setMemory("16Gi")
            .setGpu(1)
            .build();

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        // 使用普通 mock，具体链式调用在各测试中显式 stub
        kubeClient = mock(KubernetesClient.class);
        artifactStore = new ArtifactStore(tempDir);
        engine = new KubernetesEngine(kubeClient, "default", List.of("gpu", "k8s"),
                TOTAL_RESOURCE, artifactStore);
    }

    // ======================== adapterType / getDeviceInfo ========================

    @Test
    @DisplayName("adapterType 应返回 k8s")
    void adapterType_shouldReturnK8s() {
        assertEquals("k8s", engine.adapterType());
    }

    @Test
    @DisplayName("getDeviceInfo：应返回完整的设备信息")
    void getDeviceInfo_shouldReturnCompleteInfo() {
        GetDeviceInfoResponse info = engine.getDeviceInfo();

        assertEquals("k8s", info.getAdapterType());
        assertFalse(info.getOsArch().isEmpty(), "osArch 不应为空");
        assertNotNull(info.getCapacity());
        assertEquals(8.0, info.getCapacity().getTotal().getCpu(), 0.01);
        assertEquals("16Gi", info.getCapacity().getTotal().getMemory());
        assertEquals(2, info.getTagsCount());
        assertTrue(info.getTagsList().contains("gpu"));
        assertTrue(info.getTagsList().contains("k8s"));
    }

    // ======================== transferArtifact ========================

    @Test
    @DisplayName("transferArtifact：应存储 artifact 并返回存储路径")
    void transferArtifact_shouldStoreAndReturnPath() throws IOException {
        byte[] content = "hello k8s artifact".getBytes();
        TransferArtifactResponse response = engine.transferArtifact(
                "art-001", "app.tar.gz", new ByteArrayInputStream(content));

        assertEquals("art-001", response.getArtifactId());
        assertFalse(response.getArtifactPath().isEmpty());
        assertTrue(response.getArtifactPath().contains("app.tar.gz"));
    }

    // ======================== deployInstance ========================

    @Test
    @DisplayName("deployInstance：创建 Pod 并返回 RUNNING 状态")
    void deployInstance_success_shouldCreatePodAndReturnRunning() {
        String instanceId = "inst-001";
        String podName = "inst-001";

        // 构造创建后返回的 Pod（仅包含 metadata.name）
        Pod createdPod = new PodBuilder()
                .withNewMetadata()
                .withName(podName)
                .endMetadata()
                .build();

        // 准备 pods()/inNamespace()/resource()/create() 的调用链
        @SuppressWarnings("rawtypes")
        MixedOperation podsOp = mock(MixedOperation.class);
        @SuppressWarnings("rawtypes")
        NonNamespaceOperation nsOp = mock(NonNamespaceOperation.class);
        PodResource resCreate = mock(PodResource.class);

        when(kubeClient.pods()).thenReturn(podsOp);
        when(podsOp.inNamespace("default")).thenReturn(nsOp);
        when(nsOp.resource(any(Pod.class))).thenReturn(resCreate);
        when(resCreate.create()).thenReturn(createdPod);

        // waitForPodIp：pods().inNamespace("default").withName(podName).get() 返回带 IP 的 Pod
        Pod podWithIp = new PodBuilder(createdPod)
                .withStatus(new PodStatusBuilder().withPodIP("10.0.0.5").build())
                .build();
        PodResource podRes = mock(PodResource.class);
        when(nsOp.withName(podName)).thenReturn(podRes);
        when(podRes.get()).thenReturn(podWithIp);

        DeployInstanceRequest request = DeployInstanceRequest.newBuilder()
                .setInstanceId(instanceId)
                .setResourceRequest(Resource.newBuilder().setCpu(1.0).setMemory("2Gi").build())
                .putEnvVars("APP_MODE", "worker")
                .putLabels("app", "test")
                .build();

        DeployInstanceResponse response = engine.deployInstance(request);

        assertEquals(instanceId, response.getInstance().getInstanceId());
        assertEquals(podName, response.getInstance().getContainerId());
        assertEquals("10.0.0.5", response.getInstance().getHost());
        assertEquals(InstanceStatus.RUNNING, response.getInstance().getStatus());
    }

    @Test
    @DisplayName("deployInstance：K8s 客户端异常时返回 FAILED 状态")
    void deployInstance_failure_shouldReturnFailed() {
        @SuppressWarnings("rawtypes")
        MixedOperation podsOp = mock(MixedOperation.class);
        @SuppressWarnings("rawtypes")
        NonNamespaceOperation nsOp = mock(NonNamespaceOperation.class);

        when(kubeClient.pods()).thenReturn(podsOp);
        when(podsOp.inNamespace("default")).thenReturn(nsOp);
        when(nsOp.resource(any(Pod.class))).thenThrow(new RuntimeException("API 服务器不可用"));

        DeployInstanceResponse response = engine.deployInstance(
                DeployInstanceRequest.newBuilder()
                        .setInstanceId("inst-fail")
                        .build());

        assertEquals("inst-fail", response.getInstance().getInstanceId());
        assertEquals(InstanceStatus.FAILED, response.getInstance().getStatus());
        assertTrue(response.getInstance().getMessage().contains("API 服务器不可用"));
    }

    // ======================== stopInstance / removeInstance ========================

    @Test
    @DisplayName("stopInstance：已部署实例停止应成功")
    void stopInstance_existingInstance_shouldSucceed() {
        // 先部署一个实例
        prepareDeployedPod("inst-stop", "pod-stop", "10.0.0.6");

        @SuppressWarnings("rawtypes")
        MixedOperation podsOp = mock(MixedOperation.class);
        @SuppressWarnings("rawtypes")
        NonNamespaceOperation nsOp = mock(NonNamespaceOperation.class);
        PodResource resDelete = mock(PodResource.class);

        when(kubeClient.pods()).thenReturn(podsOp);
        when(podsOp.inNamespace("default")).thenReturn(nsOp);
        when(nsOp.withName("pod-stop")).thenReturn(resDelete);
        when(resDelete.delete()).thenReturn(java.util.Collections.emptyList());

        StopInstanceResponse response = engine.stopInstance("inst-stop");
        assertTrue(response.getSuccess());
    }

    @Test
    @DisplayName("removeInstance：已部署实例移除应成功并从内部记录删除")
    void removeInstance_existingInstance_shouldSucceedAndCleanup() {
        prepareDeployedPod("inst-rm", "pod-rm", "10.0.0.7");

        @SuppressWarnings("rawtypes")
        MixedOperation podsOp = mock(MixedOperation.class);
        @SuppressWarnings("rawtypes")
        NonNamespaceOperation nsOp = mock(NonNamespaceOperation.class);
        PodResource resDelete = mock(PodResource.class);

        when(kubeClient.pods()).thenReturn(podsOp);
        when(podsOp.inNamespace("default")).thenReturn(nsOp);
        when(nsOp.withName("pod-rm")).thenReturn(resDelete);
        when(resDelete.delete()).thenReturn(java.util.Collections.emptyList());

        RemoveInstanceResponse response = engine.removeInstance("inst-rm");
        assertTrue(response.getSuccess());

        // 再次移除应失败（内部记录已删除）
        RemoveInstanceResponse again = engine.removeInstance("inst-rm");
        assertFalse(again.getSuccess());
    }

    @Test
    @DisplayName("stopInstance：不存在的实例应返回失败")
    void stopInstance_unknownInstance_shouldFail() {
        StopInstanceResponse response = engine.stopInstance("no-such-instance");
        assertFalse(response.getSuccess());
        assertTrue(response.getMessage().contains("未找到实例"));
    }

    @Test
    @DisplayName("removeInstance：不存在的实例应返回失败")
    void removeInstance_unknownInstance_shouldFail() {
        RemoveInstanceResponse response = engine.removeInstance("no-such-instance");
        assertFalse(response.getSuccess());
    }

    // ======================== getInstanceStatus ========================

    @Test
    @DisplayName("getInstanceStatus：已部署实例应返回 Running 状态")
    void getInstanceStatus_existingInstance_shouldReturnStatus() {
        prepareDeployedPod("inst-status", "pod-status", "10.0.0.8");

        GetInstanceStatusResponse response = engine.getInstanceStatus("inst-status");
        assertEquals("inst-status", response.getInstance().getInstanceId());
        assertEquals(InstanceStatus.RUNNING, response.getInstance().getStatus());
        assertEquals("10.0.0.8", response.getInstance().getHost());
    }

    @Test
    @DisplayName("getInstanceStatus：未知实例应返回 UNSPECIFIED")
    void getInstanceStatus_unknownInstance_shouldReturnUnspecified() {
        GetInstanceStatusResponse response = engine.getInstanceStatus("unknown");
        assertEquals(InstanceStatus.INSTANCE_STATUS_UNSPECIFIED,
                response.getInstance().getStatus());
    }

    // ======================== getResourceUsage ========================

    @Test
    @DisplayName("getResourceUsage：部署实例后资源统计应正确更新 CPU")
    void getResourceUsage_afterDeploy_shouldReflectUsage() {
        prepareDeployedPod("inst-res", "pod-res", "10.0.0.9",
                Resource.newBuilder().setCpu(2.0).setMemory("4Gi").build());

        GetResourceUsageResponse response = engine.getResourceUsage();
        ResourceCapacity cap = response.getCapacity();

        assertEquals(2.0, cap.getUsed().getCpu(), 0.01, "已用 CPU 应为 2.0");
        assertEquals(6.0, cap.getAvailable().getCpu(), 0.01, "可用 CPU 应为 6.0");
    }

    // ======================== 辅助方法 ========================

    private void prepareDeployedPod(String instanceId, String podName, String ip) {
        prepareDeployedPod(instanceId, podName, ip,
                Resource.newBuilder().build());
    }

    private void prepareDeployedPod(String instanceId, String podName, String ip, Resource res) {
        // 创建阶段：pods().inNamespace("default").resource(pod).create()
        Pod createdPod = new PodBuilder()
                .withNewMetadata().withName(podName).endMetadata()
                .build();
        @SuppressWarnings("rawtypes")
        MixedOperation podsOp = mock(MixedOperation.class);
        @SuppressWarnings("rawtypes")
        NonNamespaceOperation nsOp = mock(NonNamespaceOperation.class);
        PodResource resCreate = mock(PodResource.class);

        when(kubeClient.pods()).thenReturn(podsOp);
        when(podsOp.inNamespace("default")).thenReturn(nsOp);
        when(nsOp.resource(any(Pod.class))).thenReturn(resCreate);
        when(resCreate.create()).thenReturn(createdPod);

        // waitForPodIp 阶段
        Pod podWithIp = new PodBuilder(createdPod)
                .withStatus(new PodStatusBuilder().withPhase("Running").withPodIP(ip).build())
                .build();
        PodResource podRes = mock(PodResource.class);
        when(nsOp.withName(podName)).thenReturn(podRes);
        when(podRes.get()).thenReturn(podWithIp);

        // 触发 deployInstance，使内部 maps 填充
        DeployInstanceRequest request = DeployInstanceRequest.newBuilder()
                .setInstanceId(instanceId)
                .setResourceRequest(res)
                .build();
        DeployInstanceResponse resp = engine.deployInstance(request);
        assertEquals(InstanceStatus.RUNNING, resp.getInstance().getStatus());
    }
}

