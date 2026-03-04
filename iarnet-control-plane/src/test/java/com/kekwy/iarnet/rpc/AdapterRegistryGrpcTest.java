package com.kekwy.iarnet.rpc;

import com.kekwy.iarnet.proto.adapter.*;
import com.kekwy.iarnet.proto.ir.Resource;
import com.kekwy.iarnet.resource.adapter.AdapterInfo;
import com.kekwy.iarnet.resource.adapter.DefaultAdapterRegistry;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 控制平面 gRPC 服务端集成测试：
 * 启动真实的 gRPC Server，通过客户端 Stub 模拟 Adapter 发起注册、心跳、注销请求，
 * 验证 control-plane 能正确处理并记录 Adapter 信息。
 */
class AdapterRegistryGrpcTest {

    private Server server;
    private ManagedChannel channel;
    private AdapterRegistryServiceGrpc.AdapterRegistryServiceBlockingStub blockingStub;
    private DefaultAdapterRegistry registry;

    @BeforeEach
    void setUp() throws IOException {
        registry = new DefaultAdapterRegistry();
        AdapterRegistryGrpcService grpcService = new AdapterRegistryGrpcService(registry);

        // 端口 0 → 操作系统随机分配可用端口
        server = ServerBuilder.forPort(0)
                // 测试环境中也附加 AdapterIdInterceptor，与生产环境保持一致
                .addService(ServerInterceptors.intercept(
                        grpcService,
                        new AdapterRegistryGrpcService.AdapterIdInterceptor()))
                .build()
                .start();

        channel = ManagedChannelBuilder
                .forAddress("localhost", server.getPort())
                .usePlaintext()
                .build();

        blockingStub = AdapterRegistryServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        registry.shutdown();
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    // ======================== 注册测试 ========================

    /** 发送注册请求，验证返回 accepted=true、分配了非空的 adapter_id，以及注册表中已记录完整信息 */
    @Test
    @DisplayName("注册 Adapter：应返回 accepted=true 并分配唯一 adapter_id，注册表中记录完整元数据")
    void register_shouldReturnAcceptedAndRecordInfo() {
        RegisterRequest request = RegisterRequest.newBuilder()
                .setAdapterName("test-docker-adapter")
                .setDescription("测试用 Docker 适配器")
                .setAdapterType("docker")
                .setCapacity(ResourceCapacity.newBuilder()
                        .setTotal(Resource.newBuilder()
                                .setCpu(4.0)
                                .setMemory("8Gi")
                                .build())
                        .build())
                .addTags("gpu")
                .addTags("linux")
                .build();

        RegisterResponse response = blockingStub.register(request);

        // 验证 gRPC 响应
        assertTrue(response.getAccepted(), "注册应被接受");
        assertFalse(response.getAdapterId().isEmpty(), "应返回非空的 adapter_id");

        // 验证注册表内记录
        AdapterInfo info = registry.getAdapter(response.getAdapterId());
        assertNotNull(info, "注册表中应存在该 Adapter");
        assertEquals("test-docker-adapter", info.getName());
        assertEquals("测试用 Docker 适配器", info.getDescription());
        assertEquals("docker", info.getAdapterType());
        assertEquals(2, info.getTags().size());
        assertTrue(info.getTags().contains("gpu"));
        assertTrue(info.getTags().contains("linux"));
        assertEquals(AdapterInfo.Status.ONLINE, info.getStatus());
        assertNotNull(info.getLastHeartbeat(), "应记录注册时间");

        System.out.println("注册成功: adapterId=" + response.getAdapterId());
        System.out.println("Adapter 信息: " + info);
    }

    /** 多次注册同名 Adapter，每次应分配不同的 adapter_id */
    @Test
    @DisplayName("多次注册：每次应分配不同的 adapter_id")
    void register_multipleTimes_shouldAssignDifferentIds() {
        RegisterRequest request = RegisterRequest.newBuilder()
                .setAdapterName("repeated-adapter")
                .setAdapterType("docker")
                .build();

        RegisterResponse r1 = blockingStub.register(request);
        RegisterResponse r2 = blockingStub.register(request);
        RegisterResponse r3 = blockingStub.register(request);

        assertTrue(r1.getAccepted());
        assertTrue(r2.getAccepted());
        assertTrue(r3.getAccepted());

        assertNotEquals(r1.getAdapterId(), r2.getAdapterId(), "不同注册应分配不同 ID");
        assertNotEquals(r2.getAdapterId(), r3.getAdapterId(), "不同注册应分配不同 ID");
        assertNotEquals(r1.getAdapterId(), r3.getAdapterId(), "不同注册应分配不同 ID");

        assertEquals(3, registry.listAdapters().size(), "注册表中应有 3 个 Adapter");
    }

    /** 注册时不传 capacity 和 tags，验证也能正常处理 */
    @Test
    @DisplayName("注册（最小字段）：仅传 name 和 type，应正常注册")
    void register_minimalFields_shouldSucceed() {
        RegisterResponse response = blockingStub.register(
                RegisterRequest.newBuilder()
                        .setAdapterName("minimal")
                        .setAdapterType("k8s")
                        .build());

        assertTrue(response.getAccepted());
        AdapterInfo info = registry.getAdapter(response.getAdapterId());
        assertNotNull(info);
        assertEquals("minimal", info.getName());
        assertEquals("k8s", info.getAdapterType());
        assertTrue(info.getTags().isEmpty(), "未传 tags 应为空列表");
    }

    // ======================== 心跳测试 ========================

    /** 已注册 Adapter 发送心跳，验证 acknowledged=true 且资源使用快照已更新 */
    @Test
    @DisplayName("心跳：已注册 Adapter 发送心跳应返回 acknowledged=true 并更新资源快照")
    void heartbeat_afterRegister_shouldUpdateUsage() {
        RegisterResponse registerResp = blockingStub.register(
                RegisterRequest.newBuilder()
                        .setAdapterName("heartbeat-test")
                        .setAdapterType("docker")
                        .setCapacity(ResourceCapacity.newBuilder()
                                .setTotal(Resource.newBuilder().setCpu(8.0).setMemory("16Gi").build())
                                .build())
                        .build());
        String adapterId = registerResp.getAdapterId();

        ResourceCapacity usage = ResourceCapacity.newBuilder()
                .setUsed(Resource.newBuilder().setCpu(1.5).setMemory("2Gi").build())
                .setAvailable(Resource.newBuilder().setCpu(6.5).setMemory("14Gi").build())
                .build();

        HeartbeatResponse hbResp = blockingStub.heartbeat(
                HeartbeatRequest.newBuilder()
                        .setAdapterId(adapterId)
                        .setUsage(usage)
                        .build());

        assertTrue(hbResp.getAcknowledged(), "心跳应被确认");

        AdapterInfo info = registry.getAdapter(adapterId);
        assertNotNull(info.getUsage(), "应记录资源使用信息");
        assertEquals(1.5, info.getUsage().getUsed().getCpu(), 0.01, "CPU 使用量应匹配");
        assertEquals("2Gi", info.getUsage().getUsed().getMemory(), "内存使用量应匹配");
    }

    /** 未注册的 adapter_id 发送心跳，应静默处理（不抛异常），acknowledged 仍返回 true */
    @Test
    @DisplayName("心跳（未注册 ID）：应正常返回，不会导致异常")
    void heartbeat_unknownAdapterId_shouldNotThrow() {
        HeartbeatResponse hbResp = blockingStub.heartbeat(
                HeartbeatRequest.newBuilder()
                        .setAdapterId("non-existent-id")
                        .setUsage(ResourceCapacity.getDefaultInstance())
                        .build());

        assertTrue(hbResp.getAcknowledged(), "即使 ID 未知，gRPC 层面不应报错");
    }

    // ======================== 注销测试 ========================

    /** 已注册 Adapter 注销后应从注册表移除 */
    @Test
    @DisplayName("注销：已注册 Adapter 注销后应从注册表中删除")
    void deregister_shouldRemoveAdapterFromRegistry() {
        RegisterResponse registerResp = blockingStub.register(
                RegisterRequest.newBuilder()
                        .setAdapterName("deregister-test")
                        .setAdapterType("docker")
                        .build());
        String adapterId = registerResp.getAdapterId();
        assertNotNull(registry.getAdapter(adapterId), "注销前应存在");

        DeregisterResponse deregResp = blockingStub.deregister(
                DeregisterRequest.newBuilder()
                        .setAdapterId(adapterId)
                        .build());

        assertTrue(deregResp.getSuccess(), "注销应成功");
        assertNull(registry.getAdapter(adapterId), "注销后不应存在");
        assertEquals(0, registry.listAdapters().size(), "注册表应为空");
    }

    /** 注销不存在的 adapter_id，应正常返回（幂等语义） */
    @Test
    @DisplayName("注销（不存在的 ID）：应正常返回，幂等处理")
    void deregister_unknownId_shouldSucceed() {
        DeregisterResponse response = blockingStub.deregister(
                DeregisterRequest.newBuilder()
                        .setAdapterId("does-not-exist")
                        .build());

        assertTrue(response.getSuccess(), "注销不存在的 ID 应视为成功（幂等）");
    }

    // ======================== 完整生命周期测试 ========================

    /** 模拟 Adapter 完整生命周期：注册 → 心跳 → 注销 */
    @Test
    @DisplayName("完整生命周期：注册 → 心跳 → 注销，验证各阶段状态一致")
    void fullLifecycle_register_heartbeat_deregister() {
        System.out.println("========== Adapter 完整生命周期测试 ==========");

        // 1. 注册
        RegisterResponse registerResp = blockingStub.register(
                RegisterRequest.newBuilder()
                        .setAdapterName("lifecycle-adapter")
                        .setDescription("完整生命周期测试")
                        .setAdapterType("docker")
                        .setCapacity(ResourceCapacity.newBuilder()
                                .setTotal(Resource.newBuilder()
                                        .setCpu(8.0)
                                        .setMemory("16Gi")
                                        .setGpu(1)
                                        .build())
                                .build())
                        .addTags("test")
                        .addTags("lifecycle")
                        .build());
        assertTrue(registerResp.getAccepted());
        String adapterId = registerResp.getAdapterId();
        System.out.println("[1/3] 注册成功: adapterId=" + adapterId);

        AdapterInfo info = registry.getAdapter(adapterId);
        assertEquals("lifecycle-adapter", info.getName());
        assertEquals("完整生命周期测试", info.getDescription());
        assertEquals("docker", info.getAdapterType());
        assertEquals(AdapterInfo.Status.ONLINE, info.getStatus());

        // 2. 心跳（模拟一段时间后上报资源使用情况）
        HeartbeatResponse hbResp = blockingStub.heartbeat(
                HeartbeatRequest.newBuilder()
                        .setAdapterId(adapterId)
                        .setUsage(ResourceCapacity.newBuilder()
                                .setUsed(Resource.newBuilder()
                                        .setCpu(2.0)
                                        .setMemory("4Gi")
                                        .build())
                                .setAvailable(Resource.newBuilder()
                                        .setCpu(6.0)
                                        .setMemory("12Gi")
                                        .setGpu(1)
                                        .build())
                                .build())
                        .build());
        assertTrue(hbResp.getAcknowledged());
        System.out.println("[2/3] 心跳确认: CPU使用=" + info.getUsage().getUsed().getCpu());

        assertEquals(AdapterInfo.Status.ONLINE, registry.getAdapter(adapterId).getStatus());

        // 3. 注销
        DeregisterResponse deregResp = blockingStub.deregister(
                DeregisterRequest.newBuilder()
                        .setAdapterId(adapterId)
                        .build());
        assertTrue(deregResp.getSuccess());
        assertNull(registry.getAdapter(adapterId), "注销后应从注册表移除");
        assertEquals(0, registry.listAdapters().size());
        System.out.println("[3/3] 注销成功");

        System.out.println("========== 生命周期测试通过 ==========");
    }
}
