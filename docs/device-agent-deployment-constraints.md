## Device Agent 部署约束与 Actor 回连方案

本文档说明在本研究中 Adapter 兼任 Device Agent 时，为保证 Actor 能高效、稳定地通过 gRPC 回连本机 Device Agent，在部署 Adapter 及其管理的 Docker / K8s 引擎时所做的工程约束。

---

## 1. 最小 Device Agent 能力与环境变量约定

### 1.1 Device Agent 最小能力

- 每台物理设备只运行 **一个 Adapter + Device Agent** 进程。
- Device Agent 在本机启动一个 gRPC Server，监听：
  - `device_agent_host`（通常 `0.0.0.0`）
  - `device_agent_port`（如 `10000`）。
- 对外暴露一个本地服务（可简化为一个双向流）：
  - 后续在该流上承载：Actor 注册、心跳、ICE 信令等本地控制面消息。

### 1.2 向 Actor 注入回连地址

在调度部署阶段，控制平面通过 `DeployInstanceRequest.env_vars` 为每个 Actor 注入：

- **`IARNET_DEVICE_AGENT_ADDR`**：Device Agent 对该 Actor 可达的地址，形如：
  - Docker（host 网络）：`127.0.0.1:10000`
  - Docker（bridge 网络）：`<宿主机 IP>:10000`
  - Kubernetes：`<NodeIP>:10000`

Actor 容器启动后：

- 从环境变量中读取 `IARNET_DEVICE_AGENT_ADDR`；
- 使用 gRPC Dial 该地址，建立一个双向流式连接，作为 Actor 与 Device Agent 的本地控制面通道。

---

## 2. Docker 场景的部署约束

为保证 Actor 容器中看到的 `IARNET_DEVICE_AGENT_ADDR` 一定可达 Device Agent，本研究在 Docker 下采用如下约束：

- **约束 1：同一物理节点**  
  - 每个 Adapter 实例只管理本物理节点上的资源，Actor 容器与 Adapter/Device Agent 必须调度到同一台宿主机。

- **约束 2：网络可达性（推荐 host 网络）**
  - 推荐方案：Actor 容器使用 `--network=host`：
    - Device Agent 在宿主机监听 `0.0.0.0:10000`；
    - 部署 Actor 时注入：`IARNET_DEVICE_AGENT_ADDR=127.0.0.1:10000`；
    - 容器与宿主机共享网络栈，Actor 通过回连 `127.0.0.1:10000` 即可访问本机 Device Agent。
  - 备选方案：保持 Docker bridge 网络，但要求：
    - 确定宿主机对容器可达的 IP（如 `192.168.x.x`）；
    - Device Agent 监听在该 IP 的 10000 端口；
    - 部署时注入 `IARNET_DEVICE_AGENT_ADDR=<宿主机 IP>:10000`。

在论文中可以将 Docker 场景的假设简化叙述为：

> 本研究约束 Actor 容器与 Device Agent 共用宿主网络（`--network=host`），Actor 始终通过 `127.0.0.1:<port>` 回连本机 Device Agent，以避免额外的地址发现与 NAT 处理逻辑。

---

## 3. Kubernetes 场景的部署约束

Kubernetes 下，Device Agent 与 Actor Pod 之间的可达性更加依赖集群网络。为简化设计，本研究采用“Device Agent 作为 DaemonSet + hostNetwork + hostPort”的约束：

### 3.1 Device Agent 以 DaemonSet 形式运行

- 在每个节点上以 DaemonSet 部署 Device Agent：
  - `hostNetwork: true`
  - 固定 `hostPort: 10000`
  - Device Agent gRPC Server 监听 `0.0.0.0:10000`。
- 这样，位于任意 Pod 中的 Actor 只要知道本节点的 **NodeIP**，就可以通过 `NodeIP:10000` 回连本节点的 Device Agent。

### 3.2 调度与注入约束

控制平面在两阶段调度中：

- **Phase 1（Placement）**：
  - 为每个 Actor 副本选择 `device_id`（即某个 Adapter 所在节点）；
  - 在资源域元信息中持有该 `device_id -> NodeIP` 的映射。
- **Phase 2（Provisioning）**：
  - KubernetesEngine 在为该 Actor 构造 PodSpec 时：
    - 使用 `nodeSelector` 或 `nodeName` 将 Pod 固定到对应节点；
    - 在 env 中注入：`IARNET_DEVICE_AGENT_ADDR=<NodeIP>:10000`。

