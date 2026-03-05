Actor 控制通道设计方案

整体架构定位

在现有架构中，Adapter 已通过 CommandChannel 双向流连回控制平面，Actor 控制通道采用相同模式：

flowchart TB
  subgraph ControlPlane ["Control Plane"]
    ActorControlGrpc["ActorControlGrpcService"]
    ActorReg["ActorRegistry"]
    ActorConn["ActorConnection"]
    ActorControlGrpc --> ActorReg
    ActorReg --> ActorConn
  end

  subgraph ActorContainer ["Actor Container"]
    ActorRuntime["Actor Runtime"]
    ControlClient["ControlPlaneClient"]
    ActorRuntime --> ControlClient
  end

  ControlClient -- "gRPC bidi stream" --> ActorControlGrpc

与 Adapter 通道的关键区别：Adapter 通道管理的是"一台设备"粒度的连接，Actor 通道管理的是"一个算子实例"粒度的连接，数量远大于 Adapter。



1. Proto 定义（iarnet-proto）

在 [iarnet-proto/proto/iarnet/actor/](iarnet/iarnet-proto/proto/iarnet/actor/) 下新增 actor_control.proto：

service ActorControlService {
  // Actor 启动后主动建立控制通道，双向流。
  // Actor → ControlPlane: 上报状态/心跳
  // ControlPlane → Actor: 下发配置/指令
  rpc ControlChannel(stream ActorReport) returns (stream ActorDirective);
}

ActorReport（Actor → 控制平面）

message ActorReport {
  string actor_id = 1;

  oneof payload {
    ActorReadyReport    ready    = 10;  // 启动就绪通知（首条消息）
    ActorHeartbeat      heartbeat = 11; // 周期性心跳
    ActorStatusChange   status_change = 12; // 状态变更上报
    ActorMetrics        metrics  = 13; // 可选：性能指标上报
  }
}





ActorReadyReport: 携带 actor_id、workflow_id、node_id、replica_index、监听地址等，作为流的第一条消息，表示"我已启动并就绪"



ActorHeartbeat: 定期心跳，简单确认存活



ActorStatusChange: Actor 状态变更（如出错、恢复等）

ActorDirective（控制平面 → Actor）

message ActorDirective {
  string directive_id = 1;

  oneof payload {
    ActorTopologyConfig   topology   = 10; // 下发邻居拓扑/路由表
    ActorStopDirective    stop       = 11; // 停止指令
    ActorReconfigure      reconfigure = 12; // 重配置指令
    ActorAck              ack        = 13; // 对 ActorReport 的确认
  }
}





ActorTopologyConfig: 下推 actor 的上下游 actor 地址信息，告诉它"你的数据从哪来、往哪送"



ActorStopDirective: 优雅停止指令



ActorReconfigure: 运行时参数变更

与现有 actor_service.proto 的关系：ActorService.Invoke 是数据面接口（由 Device Agent 或其他 Actor 调用），ActorControlService.ControlChannel 是控制面接口（由控制平面管理生命周期），两者职责正交。



2. 控制平面侧（iarnet-resource + iarnet-control-plane）

2.1 新增 iarnet-resource 中的 actor 连接管理

在 [iarnet-resource/.../resource/actor/](iarnet/iarnet-resource/src/main/java/com/kekwy/iarnet/resource/) 下新建 actor 包，参照 adapter 包的结构：







类



职责



对标 adapter 包





ActorConnection



封装单个 Actor 的双向流连接，提供 sendDirective() + onReport()



AdapterConnection





ActorSession



记录一个已连接 Actor 的元数据（actorId, workflowId, nodeId, 状态, 上次心跳时间等）



AdapterInfo





ActorRegistry (接口)



管理所有 Actor 连接的注册表



AdapterRegistry





DefaultActorRegistry



ActorRegistry 的默认实现



DefaultAdapterRegistry

ActorConnection 核心 API（类比 AdapterConnection）：

public class ActorConnection {
    // 向 Actor 下发指令（fire-and-forget，无需等待响应）
    public void sendDirective(ActorDirective directive);

    // 向 Actor 下发指令并等待 ACK
    public CompletableFuture<ActorReport> sendDirectiveAndAwait(
        ActorDirective directive, long timeout, TimeUnit unit);

    // 处理 Actor 上报的消息
    public void onReport(ActorReport report);
}

ActorRegistry 核心 API：

public interface ActorRegistry {
    // 当 Actor 建立 ControlChannel 时调用
    void onActorConnected(String actorId, ActorReadyReport ready,
                          StreamObserver<ActorDirective> directiveSender);

    // Actor 断连
    void onActorDisconnected(String actorId);

    // 处理 Actor 上报
    void handleReport(String actorId, ActorReport report);

    // 向指定 Actor 发送指令
    void sendDirective(String actorId, ActorDirective directive);

    // 查询 Actor 会话信息
    ActorSession getSession(String actorId);

    // 查询指定工作流下所有在线 Actor
    List<ActorSession> listActorsByWorkflow(String workflowId);

    // 注册 Actor 生命周期事件监听器
    void addListener(ActorLifecycleListener listener);
}

ActorLifecycleListener（上层消费 Actor 事件的钩子）：

public interface ActorLifecycleListener {
    void onActorReady(String actorId, ActorReadyReport report);
    void onActorDisconnected(String actorId);
    void onActorStatusChanged(String actorId, ActorStatusChange change);
}

2.2 新增 iarnet-control-plane 中的 gRPC 服务

在 [iarnet-control-plane/.../rpc/](iarnet/iarnet-control-plane/src/main/java/com/kekwy/iarnet/rpc/) 下新增 ActorControlGrpcService：

@Component
public class ActorControlGrpcService
        extends ActorControlServiceGrpc.ActorControlServiceImplBase {

    private final ActorRegistry actorRegistry;

    @Override
    public StreamObserver<ActorReport> controlChannel(
            StreamObserver<ActorDirective> directiveSender) {
        return new StreamObserver<>() {
            private String actorId;

            @Override
            public void onNext(ActorReport report) {
                if (actorId == null) {
                    // 第一条消息必须是 ReadyReport，完成注册
                    actorId = report.getActorId();
                    actorRegistry.onActorConnected(
                        actorId, report.getReady(), directiveSender);
                } else {
                    actorRegistry.handleReport(actorId, report);
                }
            }

            @Override
            public void onError(Throwable t) {
                if (actorId != null) actorRegistry.onActorDisconnected(actorId);
            }

            @Override
            public void onCompleted() {
                if (actorId != null) actorRegistry.onActorDisconnected(actorId);
            }
        };
    }
}



3. Actor 侧（iarnet-actor-java）

在 ActorApplication 启动 gRPC Server 之后，增加一步：主动连接控制平面并建立 ControlChannel。

新增 ControlPlaneClient 类：

public class ControlPlaneClient {
    private final String controlPlaneAddress;
    private final String actorId;
    private ManagedChannel channel;

    public void connect() {
        channel = ManagedChannelBuilder.forTarget(controlPlaneAddress)
            .usePlaintext().build();

        var stub = ActorControlServiceGrpc.newStub(channel);
        var directiveHandler = new StreamObserver<ActorDirective>() {
            @Override
            public void onNext(ActorDirective directive) {
                handleDirective(directive);  // 分发给具体 handler
            }
            // ...
        };

        var reportSender = stub.controlChannel(directiveHandler);

        // 首条消息：上报就绪
        reportSender.onNext(ActorReport.newBuilder()
            .setActorId(actorId)
            .setReady(ActorReadyReport.newBuilder()
                .setWorkflowId(...)
                .setNodeId(...)
                .setListenPort(...)
                .build())
            .build());

        // 启动心跳定时任务
        startHeartbeatLoop(reportSender);
    }
}

控制平面地址通过环境变量 IARNET_CONTROL_PLANE_ADDR 注入（在 DefaultSchedulerService.deployActor() 的 env 中设置）。



4. 上层收发消息的设计

4.1 调度完成后的拓扑下发流程

sequenceDiagram
  participant Scheduler as SchedulerService
  participant AR as ActorRegistry
  participant AC as ActorConnection

  Scheduler->>AR: schedule() 部署完所有 Actor
  Note over Scheduler: 等待所有 Actor 就绪
  AR-->>Scheduler: onActorReady 事件
  Scheduler->>AR: sendDirective(actorId, TopologyConfig)
  AR->>AC: sendDirective(TopologyConfig)
  AC-->>Actor: gRPC stream push

具体步骤：





SchedulerService.schedule() 部署 Actor（通过 Adapter CommandChannel）



Actor 容器启动后，通过 ControlChannel 发送 ActorReadyReport



ActorRegistry 触发 ActorLifecycleListener.onActorReady()



上层监听到所有 Actor 就绪后，通过 ActorRegistry.sendDirective() 下发拓扑配置

4.2 上层使用示例

// 等待所有 actor 就绪
CompletableFuture<Void> allReady = actorRegistry.waitForActors(actorIds);
allReady.thenAccept(v -> {
    // 所有 actor 就绪，下发拓扑
    for (ActorDeployment dep : physicalGraph.deployments()) {
        for (ActorInstance inst : dep.instances()) {
            ActorDirective topo = buildTopologyDirective(physicalGraph, inst);
            actorRegistry.sendDirective(inst.actorId(), topo);
        }
    }
});

4.3 与 Adapter 通道的职责边界





Adapter CommandChannel：管理"容器"的创建/销毁/状态查询（基础设施层）



Actor ControlChannel：管理"Actor 实例"的生命周期、配置下发、运行时控制（应用层）



Device Agent SignalingChannel（未来）：管理 Actor 间的 P2P 数据通道建立（网络层）



5. 涉及的文件变更





新增 proto: iarnet-proto/proto/iarnet/actor/actor_control.proto



新增 Java（iarnet-resource）:





resource/actor/ActorConnection.java



resource/actor/ActorSession.java



resource/actor/ActorRegistry.java（接口）



resource/actor/DefaultActorRegistry.java



resource/actor/ActorLifecycleListener.java



新增 Java（iarnet-control-plane）:





rpc/ActorControlGrpcService.java



GrpcServerLifecycle.java 中注册新服务



修改 Java（iarnet-actor-java）:





新增 runtime/ControlPlaneClient.java



修改 ActorApplication.java，在 gRPC Server 启动后连接控制平面



修改 ActorServerProperties.java，增加控制平面地址配置



修改 Java（iarnet-resource）:





修改 DefaultSchedulerService.deployActor()，在 env 中注入 IARNET_CONTROL_PLANE_ADDR

