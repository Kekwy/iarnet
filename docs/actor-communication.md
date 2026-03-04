## Device Agent + 虚拟寻址 + ICE 式连接协商通信机制说明

### 1. 目标与总体架构

**目标**  
在复杂网络环境（双端都在 NAT/防火墙之后）下，实现 **Actor–Actor 之间的直接 P2P 数据通道**，同时：

- 控制平面 **不** 中继业务数据，只做 **信令协调**。
- 支持多种部署环境（K8s、Docker、物理机等）。
- 为未来优化（带宽/延迟感知、路径重选、重连）留下空间。

**核心组件**

- **控制平面（Control Plane）**
  - 提供 `DeviceAgentRegistryService`（gRPC）：
    - 设备注册 & 心跳。
    - `SignalingChannel`：双向流，用于转发 Device Agent 之间的 ICE 信令和建链指令。
  - 持有全局视图：`device_id -> signaling stream`、`ActorAddr -> device_id`。

- **Device Agent**（每台设备一进程）
  - 主动连到控制平面的 `DeviceAgentRegistryService`。
  - 维护本机 Actor 注册表（`ActorAddr -> LocalEndpoint`）。
  - 执行 ICE 流程：收集候选地址、交换信令、尝试打洞和建链。
  - 为本机 Actor 提供本地通信接口（如 `LocalAgentService`）。

- **Actor Runtime（容器内 Actor 底座）**
  - 暴露 `ActorService`（gRPC）用于业务调用：
    - `Invoke(ActorInvokeRequest) -> ActorInvokeResponse`。
  - 通过本机 Device Agent 打开到其他 Actor 的数据通道（后续可扩展为流式数据）。

---

### 2. 虚拟寻址（Virtual Addressing）

#### 2.1 Actor 虚拟地址

为每个 Actor 分配逻辑地址 `ActorAddr`，与物理 IP/容器 ID 解耦。建议组合字段：

- `application_id`
- `workflow_id`
- `node_id`（对应 WorkflowGraph 中的节点 ID）
- `replica_index`
- `device_id`（Actor 所属设备，由调度器决定）

可以序列化为字符串形式，例如：

```text
actor://{application_id}/{workflow_id}/{node_id}/{replica_index}
```

#### 2.2 Device Agent 本地注册表

每个 Device Agent 内部维护：

- `map<ActorAddr, LocalEndpoint>`：
  - `LocalEndpoint` 包含：
    - 本机监听端口（Actor gRPC / QUIC / 其他协议）。
    - 协议类型（如 `grpc-tcp`, `quic`, `webrtc-data`）。
    - 进程/容器标识（便于监控和回收）。

Actor 容器启动后，通过本机 Device Agent 的本地接口（如 `RegisterActor`）注册自身的 `ActorAddr` 与监听信息。

---

### 3. Device ↔ 控制平面：注册与信令协议

对应 proto：`iarnet/agent/device_agent.proto`。

#### 3.1 设备注册 / 心跳

- `RegisterDeviceRequest` / `RegisterDeviceResponse`：
  - Device Agent 启动后调用 `RegisterDevice`，获得全局唯一 `device_id`。
  - 携带 `device_name`、`description`、`tags`、`zone` 等元信息。
- `DeviceHeartbeat` / `DeviceHeartbeatAck`：
  - 周期性汇报存活状态（`device_id`、`timestamp_ms`、动态标签等）。

#### 3.2 SignalingChannel：双向信令流

```protobuf
rpc SignalingChannel(stream SignalingMessage)
  returns (stream SignalingMessage);
```

- 双向 stream，长连接。
- `SignalingMessage` 的 `oneof payload` 包含三类：
  - `ConnectInstruction`：**控制平面 → DeviceAgent**
    - 通知某设备：“请和某个对端设备一起，为 ActorX→ActorY 建立通道。”
  - `IceEnvelope`：**DeviceAgent ↔ DeviceAgent（经控制平面中转）**
    - 包装 ICE 相关的信令：offer/answer/candidate 等。
  - `CandidateUpdate`：**DeviceAgent → 控制平面（可选）**
    - 上报本机网络候选，用于控制平面构建全局视图。

#### 3.3 ConnectInstruction

```protobuf
message ConnectInstruction {
  string connect_id      = 1; // 本次建链会话 ID
  string src_actor_addr  = 2;
  string dst_actor_addr  = 3;
  bool   initiator       = 4; // 本设备是否作为主动发起方
}
```

- 调度完成后，控制平面根据 WorkflowGraph 的边，向源/目的两端所在的 Device 发出指令：
  - `DeviceA`：initiator = true（Caller）
  - `DeviceB`：initiator = false（Callee）

---

### 4. ICE 式连接协商与 NAT 穿透

#### 4.1 候选（NetworkCandidate）收集

每个 Device Agent 在本地收集一组候选地址：

- `DIRECT`：局域网地址，适合同网/同子网直连，如 `192.168.1.10:50000`。
- `NAT_MAPPED`：通过 STUN 或探测节点得到的公网映射，如 `203.0.113.5:62001`。
- `RELAY`（可选）：预置中继节点地址，用于极端 NAT（对称 NAT）场景兜底。

对应 proto：

```protobuf
message NetworkCandidate {
  enum CandidateType { DIRECT = 1; NAT_MAPPED = 2; RELAY = 3; ... }
  CandidateType type = 1;
  string host        = 2;
  int32  port        = 3;
  int32  priority    = 4; // 优先级
}
```

#### 4.2 ICE 信令交换（IceEnvelope）

- 双方 Device Agent 通过 `IceEnvelope` 在 `SignalingChannel` 上互发：
  - 初始参数（如 pseudo-SDP：候选列表、协议版本等）。
  - 后续增量候选（追加 new candidate）。
- 控制平面只根据 `IceEnvelope.to_device_id` 做简单路由转发，不理解信令具体语义。

#### 4.3 打洞与建链（TCP/UDP/QUIC 示例）

对每个候选对 `(candA, candB)`：

1. 双方在本地绑定各自的候选端口，几乎同时对对方候选发起连接 / 探测报文。
2. 在典型 NAT 场景下：
   - 当内部主机向外发出报文，NAT 会为该 `内网IP:port` 建立一条映射到 `公网IP:port'` 的“洞”。
   - 如果对端也向这个 `公网IP:port'` 发包，并通过路由器的映射表匹配到原始内网端口，这个双向“洞”就打通。
3. 双方只需要检测出**至少一对候选成功握手**，即可建立一个可靠的 P2P 通道：
   - 对上层表现为一个 `Socket`/`Stream`/`Channel` 抽象。
   - 通道建立成功后，Device Agent 会在本地注册一个 `channel_id`，供 Actor runtime 使用。

#### 4.4 Relay 兜底（可选）

- 当所有 DIRECT / NAT_MAPPED 候选对都无法建立通道时，使用 `RELAY` 类型候选：
  - DeviceA 和 DeviceB 都与同一个 Relay 节点建立连接。
  - Relay 只做数据转发，不是控制平面本体，可以按需要弹性扩展。
- 从系统架构上看，Relay 仍属于“数据通道层”，控制平面仍然只负责信令。

---

### 5. Actor 视角：如何使用通道

#### 5.1 容器内 Actor 服务接口

对应 proto：`iarnet/actor/actor_service.proto`。

- Actor runtime 对外提供：

```protobuf
service ActorService {
  rpc Invoke(ActorInvokeRequest) returns (ActorInvokeResponse);
}
```

- 其中：

```protobuf
message ActorInvokeRequest {
  string actor_addr    = 1;   // 可选，用于调试/追踪
  string invocation_id = 2;   // 调用 ID
  bytes  payload       = 3;   // 序列化后的参数包
  map<string, string> metadata = 4;
}

message ActorInvokeResponse {
  string    invocation_id = 1;
  bytes     payload       = 2;
  string    error         = 3;
  ActorInfo info          = 4;
}
```

#### 5.2 Actor 与 Device Agent 的本地交互

- 在每台设备上，Actor 通过本地接口（可以是 gRPC/Unix socket/进程内 API）调用 Device Agent：
  - **注册**：`RegisterActor(ActorAddr, local_port, protocol)`。
  - **请求建链**：`OpenChannel(self_actor_addr, peer_actor_addr)`，获得 `channel_id` 或 Stream。
- Device Agent 收到请求后：
  - 如果是**本机内通信**（两个 Actor 在同一设备上），可以直接用本地 IPC / loopback 建通道。
  - 如果是跨设备：
    - 触发或复用已有的 `ConnectInstruction + ICE 协商`。
    - 将底层 P2P 通道映射到给定的 `channel_id` 上，下发给双方 Actor runtime。

#### 5.3 数据流向

数据路径完全是：

```text
ActorX
  ↕ (本地 API / local socket)
DeviceAgentA
  ↔ (P2P Socket/QUIC/WebRTC)
DeviceAgentB
  ↕
ActorY
```

控制平面不会看到 `Invoke` 的 payload，也不会转发 Actor 间的业务数据。

---

### 6. 与当前 iarnet-Java 项目的集成位置

- **`iarnet-proto`**：
  - 已新增：
    - `iarnet/actor/actor_service.proto`
    - `iarnet/agent/device_agent.proto`

- **控制平面（iarnet-control-plane）**：
  - 实现 `DeviceAgentRegistryService`：
    - 管理设备注册/心跳。
    - 维护 `device_id -> signaling stream`。
    - 将 `IceEnvelope` 与 `ConnectInstruction` 进行路由和转发。

- **资源层 / Adapter / Device Agent**：
  - Device Agent 运行在资源节点上，与控制平面建立 `SignalingChannel`。
  - 通过 DockerEngine / KubernetesEngine 部署 Actor 容器，并为其注册 `ActorAddr` 和 gRPC 监听端口。

- **Actor 容器内（Java 优先）**：
  - Java Actor runtime 实现 `ActorService`：
    - 根据 `ActorInvokeRequest` 解码参数并执行用户函数。
    - 使用本机 Device Agent 提供的数据通道与上游/下游 Actor 直接通信（未来扩展为流式数据）。

整体上，这套设计实现了：**控制平面做“智能信令交换机”，Device Agent 做“网络打洞与通道管理”，Actor runtime 专注于“执行用户函数”** 的清晰分层。 

