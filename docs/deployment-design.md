## 部署与调度总体方案（artifact + 虚拟地址 + 两阶段调度）

本文档总结当前 iarnet 的部署/调度方案，包括：

- artifact 的上传与按需拉取（OSS + MinIO）
- 部署请求结构（以 lang 为主，不直接传镜像）
- 两阶段调度（先选设备再部署）
- Actor 虚拟地址生成与下游地址下发
- Adapter / Device Agent / Actor 各自的职责边界

---

## 1. Artifact 流程：控制平面上传，Adapter 按需拉取

- **控制平面 → OSS（MinIO）**
  - control-plane 中的 `OssService` 使用 MinIO (`http://localhost:9002`)：
    - `upload(artifactId, localFile)`：将本地构建产物上传到 bucket，返回 `objectKey`。
    - `createPresignedGetUrl(objectKey)`：生成 GET 预签名 URL，供 Adapter 拉取。
  - 应用层 `DefaultExecutor` 在调度前，先对每个需要 artifact 的 node：
    - 从 Workspace 得到 `nodeId -> artifactPath` 映射；
    - 调用 `ArtifactUrlProvider.uploadAndGetUrl(nodeId, path)` 上传并生成 **artifact_url**；
    - 得到 `nodeId -> artifact_url` 的映射。

- **控制平面 → Adapter**
  - 在调度阶段构造 `DeployInstanceRequest` 时：
    - `artifact_id`：通常取 artifact 文件名或 nodeId 作为标识；
    - `artifact_url`：从 `nodeId -> artifact_url` 映射中取出；
    - 部署请求中不再直接携带 artifact 本体，只传 `artifact_id` + `artifact_url`。

- **Adapter → OSS（MinIO）**
  - Adapter 侧的 `ArtifactFetcher`：
    - 接收 `artifact_id` 与 `artifact_url`；
    - 通过 HTTP GET 从 OSS 真实拉取 artifact；
    - 落盘到本地的 `ArtifactStore` 中，按 `artifact_id` 建目录；
    - 按 `artifact_id` 去重：同一 id 正在拉取则等待，已拉取则直接返回本地路径。
  - 引擎（如 DockerEngine、KubernetesEngine）在真正部署实例时：
    - 使用 `artifactLocalPath` 把产物目录挂载进容器（如 `/opt/iarnet/artifact`）；
    - 通过环境变量向容器注入 `IARNET_ARTIFACT_PATH`（容器内路径）。

---

## 2. 部署请求结构：以语言为核心，不直接传镜像

- `DeployInstanceRequest`（简化）：

  ```protobuf
  message DeployInstanceRequest {
    string              instance_id      = 1;  // 实例唯一标识
    string              artifact_id      = 2;  // artifact 标识（用于本地缓存与目录命名）
    string              artifact_url     = 3;  // artifact 拉取地址（如 OSS 预签名 URL）
    iarnet.ir.Lang      lang             = 4;  // 算子使用的编程语言 / 运行时
    iarnet.ir.Resource  resource_request = 5;  // 资源需求
    map<string, string> env_vars         = 6;  // 环境变量
    map<string, string> labels           = 7;  // 实例标签（便于管理和查询）
  }
  ```

- 控制平面 **不再决定具体镜像（image）**，只传：
  - `lang`：来自 IR 的 `FunctionDescriptor.lang`；
  - `resource_request`：算子所需 CPU/MEM/GPU 等。
- **Adapter 自主选择运行时**：
  - DockerEngine：根据 `lang` 从自身配置 `lang -> image` 映射中选择容器镜像；
  - KubernetesEngine：同理，选择合适的镜像或 Pod 模板；
  - 将来可能的 ProcessEngine（裸进程）：根据 `lang` 决定启动命令（`java -jar` / `python` 等），不使用容器。

这样部署请求抽象为 `(lang, artifact, resource)` 三元组，由资源域内的 Adapter 自主决定具体运行方式，符合“资源域自治”的目标。

---

## 3. 两阶段调度：先选设备，再部署实例

### 3.1 Phase 1：全局决策（Placement）

- 在 `DefaultSchedulerService.schedule(WorkflowGraph graph, ...)` 中第一个阶段只做「选址」：
  - 从 `AdapterRegistry` 获取当前在线设备列表及资源容量：
    - `listOnlineAdapters()` → 多个 `AdapterInfo`（`adapterId == device_id`，带 `ResourceCapacity`）。
  - 遍历 `WorkflowGraph` 中所有节点与其副本：
    - 为每个 `(nodeId, replicaIndex)` 选择一个设备 `device_id`；
    - 使用简单策略（如轮询 / 最小负载优先 / 按 tag 匹配），并在内存中维护一个“预估占用”避免超载。
  - 生成 `Placement` 列表（伪结构）：

    ```java
    class Placement {
        String actorId;      // e.g. "actor-<nodeId>-<replicaIndex>"
        String actorAddr;    // actor://{appId}/{workflowId}/{nodeId}/{replicaIndex}
        String nodeId;
        int    replicaIndex;
        String deviceId;     // Adapter / Device Agent 的 id
    }
    ```

- 在这一阶段：
  - **生成 Actor 虚拟地址（ActorAddr）**；
  - 构建 `ActorAddr -> device_id` 的映射；
  - 结合工作流边生成每个 Actor 的**下游 ActorAddr 列表**。

> Phase 1 不调用任何 Adapter 的部署接口，只计算“谁跑在哪台设备上”，并生成虚拟寻址与拓扑信息。

### 3.2 Phase 2：按 Placement 真正部署实例

- 将 `Placement` 按 `deviceId` 分组：`deviceId -> List<Placement>`，每组对应一个 Adapter。
- 对于每个 `Placement`：
  - 找到对应的 IR 节点、artifact、本节点的 `lang`、资源需求；
  - 构造 `DeployInstanceRequest`：
    - `instance_id = actorId`；
    - `artifact_id` / `artifact_url`；
    - `lang` / `resource_request`；
    - `env_vars` 中注入：
      - `IARNET_APPLICATION_ID`
      - `IARNET_WORKFLOW_ID`
      - `IARNET_ACTOR_ADDR`（虚拟地址）
      - `IARNET_ACTOR_ID`
      - `IARNET_DEVICE_ID`
      - `IARNET_SUCCESSORS`（该 Actor 下游 ActorAddr 列表，逗号分隔或由 proto 字段承载）。
  - 调用 `adapterRegistry.sendCommand(deviceId, DeployInstance)` 真正创建实例。

> 这样整体时序变为：  
> **Phase 1：Placement = 纯调度算法** → **Phase 2：Provisioning = 按 Placement 执行部署**。

---

## 4. 虚拟地址与下游拓扑的生成与下发

### 4.1 虚拟地址格式（与通信文档一致）

根据 `docs/actor-communication.md` 中的设计，为每个 Actor 分配逻辑地址 `ActorAddr`，与物理 IP/容器 ID 解耦，字段包括：

- `application_id`
- `workflow_id`
- `node_id`
- `replica_index`
- `device_id`（所在设备，由调度器决定）

地址字符串形式推荐为（不直接包含 device）：

```text
actor://{application_id}/{workflow_id}/{node_id}/{replica_index}
```

同时在控制平面维护：

```text
ActorAddr -> device_id
```

用于 ConnectInstruction 与 Device Agent 注册表。

### 4.2 在调度阶段生成 ActorAddr

- 在 `DefaultSchedulerService` 中，结合 Phase 1 的 Placement：

  ```java
  String actorAddr = String.format(
      "actor://%s/%s/%s/%d",
      applicationId, workflowId, nodeId, replicaIndex
  );
  actorAddrByActorId.put(actorId, actorAddr);
  deviceByActorAddr.put(actorAddr, deviceId);
  ```

### 4.3 下游 ActorAddr 列表

- 根据 `WorkflowGraph` 中的边 `(srcNodeId, dstNodeId)`，以及各节点的副本信息，展开成实例级的边：
  - 简单策略：`src(nodeId, k) -> dst(nodeId, k)`（一一对应），后续可拓展为更复杂的分配策略。
- 构建：

  ```java
  Map<String, List<String>> successorsByActorAddr; // ActorAddr -> 下游 ActorAddr 列表
  ```

- 在部署请求中通过 env 或 proto 字段传给 Adapter：
  - env 方案：

    ```java
    String successors = String.join(",", successorsByActorAddr.getOrDefault(actorAddr, List.of()));
    if (!successors.isEmpty()) {
        env.put("IARNET_SUCCESSORS", successors);
    }
    ```

  - proto 字段方案（例如在 `DeployInstanceRequest` 中增加 `repeated string downstream_actor_addrs = N;`）。

### 4.4 Adapter / Device Agent / Actor 各自使用方式

- **Adapter**：
  - 不解析虚拟地址的结构，只负责把 `IARNET_ACTOR_ADDR`、`IARNET_SUCCESSORS` 注入容器（或通过本机 Device Agent 的本地接口下发）。
- **Device Agent**：
  - 容器启动后，Actor 通过本地接口向 Device Agent 注册 `(ActorAddr, LocalEndpoint)`；
  - Device Agent 维护本机注册表 `map<ActorAddr, LocalEndpoint>`；
  - ConnectInstruction 中的 `src_actor_addr` / `dst_actor_addr` 与本地注册表联动，执行 ICE 建链。
- **Actor runtime**：
  - 从环境变量中读取自身份的 `ActorAddr` 与下游列表；
  - 使用本机 Device Agent 提供的 `OpenChannel(self_addr, peer_addr)` 打开到下游 Actor 的通道。

---

## 5. 职责划分小结

- **控制平面**：
  - 负责：
    - Placement：全局工作流的两阶段调度（选设备 + 部署）；
    - 虚拟地址生成（ActorAddr）与 `ActorAddr -> device_id` 映射；
    - artifact 上传到 OSS、生成 `artifact_url`；
    - 将 `lang`、artifact、虚拟地址与下游拓扑打包进 `DeployInstanceRequest`。
  - 不负责：
    - 具体镜像选择；
    - 具体建链细节（ICE / P2P）。

- **Adapter / Device Agent（每个资源域一套）**：
  - Adapter 负责：
    - 从 OSS 拉取 artifact（按 `artifact_url`）并缓存；
    - 基于 `lang` / 资源情况选择 Docker/K8s/裸进程等运行方式；
    - 启动 Actor 容器/进程，并注入虚拟地址与下游信息。
  - Device Agent 负责：
    - ActorAddr 注册表；
    - ConnectInstruction + ICE 协商与 P2P 通道管理；
    - 将控制平面下发的拓扑与指令转化为本机 Actor 的本地调用。

- **Actor runtime（iarnet-actor-java 等）**：
  - 只负责执行用户函数与使用本机 Device Agent 提供的数据通道；
  - 不直接与控制平面通信用于部署/调度，只通过虚拟地址与 Device Agent 建立通信。

