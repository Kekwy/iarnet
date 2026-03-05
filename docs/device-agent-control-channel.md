# Device Agent 与 Control Plane 控制通道设计

本文档说明在「Adapter 兼任 Device Agent」的前提下，Actor 与 Control Plane 的通信归属，以及 Device Agent 与 Control Plane 是否复用 Command Channel。

---

## 一、Actor 是否还需直连 Control Plane

### 1.1 背景

Adapter 兼任 Device Agent。Actor 启动后会先与本机 Device Agent（Adapter）建立流式连接。需要明确：Actor 是否还要与 Control Plane 单独建立流式连接，还是与 Control Plane 的通信也统一经 Device Agent 转发。

### 1.2 两种方案

| 方案 | 做法 | 特点 |
|------|------|------|
| **A：Actor 仍直连 Control Plane** | Actor 自己建一条到 Control Plane 的流（如现有 ControlChannel），同时再连本机 Device Agent（注册、建数据通道）。 | Actor 需能访问 Control Plane，且维护两条连接。 |
| **B：全部经 Device Agent** | Actor 只连本机 Device Agent；上报与下发的控制信息都由 Device Agent 在已有「Device ↔ Control Plane」通道上承载并转发。 | Actor 只认一个出口（Device Agent），拓扑简单。 |

### 1.3 建议：控制面也经 Device Agent（方案 B）

- **网络更现实**：Actor 跑在容器/边侧，往往只能访问本机或本节点；Control Plane 在云端时，Actor 直连需处理 NAT/路由/暴露端口。Device Agent 本身就要连 Control Plane，由它做控制面代理更自然。
- **Actor 只依赖一个出口**：Actor 只需知道「本机 Device Agent 的地址」（如 localhost 或 unix socket），不必配 Control Plane 的地址、证书等，部署与权限更简单。
- **与数据面一致**：数据面已是「Actor ↔ Device Agent ↔ 对端 Device Agent ↔ 对端 Actor」。控制面也走 Device Agent，则控制+数据都只对 Agent，模型统一。
- **生命周期统一**：Actor 启动后向 Device Agent 注册；Agent 在已有「Device ↔ Control Plane」通道上把该 Actor 的注册/心跳/状态上报给 Control Plane，并接收下发给该 Actor 的指令。建连、重连、认证都集中在 Agent 侧。

### 1.4 结论

**Actor 不需要再和 Control Plane 建立流式连接；与 Control Plane 的通信也交给 Device Agent。** 逻辑上仍是「Control Plane ↔ Actor」的语义（Report / Directive），物理上为「Control Plane ↔ Device Agent ↔ Actor」，由 Device Agent 做控制面中继。

---

## 二、Device Agent 与 Control Plane：复用 Command Channel 还是新开 Channel

### 2.1 问题

Device Agent 与 Control Plane 的通信，是复用现有的 **Command Channel**（Adapter 与 Control Plane 的 Command/CommandResponse 双向流），还是单独再开一个「Actor 控制专用」的 channel？

### 2.2 建议：复用 Command Channel

- **一个设备一条流**：当前已是「一个 Adapter = 一个 Device = 一条 Command Channel」。Adapter 兼任 Device Agent 后，这条流就是「本设备到 Control Plane 的唯一下行管道」。设备级命令（部署、停止、拉 artifact）和 Actor 控制（上报、下发指令）都走这条流，Control Plane 只需维护「device_id → 一条流」，重连、顺序、背压都简单。
- **实现方式**：不新增 RPC，只扩展**同一流上的消息类型**。例如：
  - **CP → Device**：在现有 `Command` 旁增加「发给某 actor 的指令」，用 `oneof` 区分，例如 `command`（原有：DeployInstance、StopInstance、TransferArtifact…）与 `actor_directive`（新增：带 `actor_id` + 拓扑/停止等，由 Agent 转发给本机对应 Actor）。
  - **Device → CP**：在现有 `CommandResponse` 旁增加「来自某 actor 的上报」，例如 `command_response`（原有）与 `actor_report`（新增：带 `actor_id` + ready/heartbeat/status，由 Agent 在收到本机 Actor 上报后发往 CP）。
  物理上仍是「一个 Command Channel」，逻辑上多两种消息，Agent 侧根据类型做命令执行或 Actor 转发即可。
- **为何不单独再开 channel**：再开一条「Actor 控制专用」流会变成每个设备两条长连接（Command Channel + Actor Control Channel）。Control Plane 要维护两套流、两套重连和生命周期，且两条流之间的顺序也不易保证。复用一条流可避免上述问题，且 Agent 本身已按 device 建一条连，扩成「设备命令 + Actor 控制」的复用通道更自然。

### 2.3 结论

**Device Agent 与 Control Plane 的通信继续使用原来的 Command Channel。** 不新开 channel，在该双向流上扩展消息类型，把「设备级 Command/CommandResponse」和「Actor 级 ActorReport/ActorDirective」都放在同一条流里，由 Device Agent 在本地区分并转发给对应 Actor 或执行设备命令即可。
