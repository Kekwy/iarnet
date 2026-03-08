# IARNet — 面向云边协同场景的分布式数据流工作流执行平台

## 项目概述

IARNet 是一个面向云边协同场景的分布式工作流执行平台，旨在将用户定义的数据流处理工作流自动部署并运行在异构的云端与边缘资源之上。平台采用 Actor 模型作为执行抽象，以 DAG（有向无环图）形式描述工作流拓扑，并通过统一的控制平面协调跨域资源的调度、通信与生命周期管理。

用户通过类 Flink 风格的 Java DSL 声明式地描述数据处理流水线，平台负责将其编译为中间表示（IR）、拆分为分布式 Actor 实例、调度到合适的资源节点上执行，并自动建立 Actor 之间的数据通道。

## 核心设计理念

- **出站优先的连接模型**：资源适配器（Adapter）和设备代理（Device Agent）主动向控制平面发起连接，无需暴露公网 IP 或开放端口，天然穿越 NAT 和防火墙。这一设计借鉴了 Cloudflare Tunnel、Kubernetes kubelet 等工业实践。
- **Actor 模型驱动执行**：工作流 DAG 中的每个节点映射为一个或多个 Actor 实例，Actor 作为最小执行单元独立运行于容器中，支持算子级别的副本配置与资源声明。
- **控制平面与数据平面分离**：控制平面负责工作流调度、Actor 生命周期管理和全局协调；数据平面中 Actor 之间通过本地代理（Local Agent）进行高效的行级数据路由。

## 系统架构

系统由以下核心组件构成：

```
┌──────────────────────────────────────────────────────────┐
│                     用户层 (User Layer)                    │
│   Java DSL / CLI  →  gRPC  →  控制平面                     │
└────────────────────────┬─────────────────────────────────┘
                         │ SubmitWorkflow
┌────────────────────────▼─────────────────────────────────┐
│                  控制平面 (Control Plane)                   │
│  WorkflowService │ SchedulerService │ AdapterRegistry     │
│  ActorRegistry   │ WorkflowStartCoordinator               │
│                  │ ArtifactManager (MinIO/OSS)             │
└───┬──────────────────────────────────┬───────────────────┘
    │ CommandChannel (gRPC bidi)       │ SignalingChannel
    │                                  │
┌───▼──────────────────┐    ┌─────────▼────────────────────┐
│  资源适配器 (Adapter)  │    │  设备代理 (Device Agent)       │
│  Docker / Kubernetes  │    │  Actor ↔ 控制平面 信令中继      │
│  Local Agent Service  │    │                              │
└───┬──────────────────┘    └──────────────────────────────┘
    │ LocalChannel (gRPC)
┌───▼──────────────────┐
│   Actor 容器实例       │
│  UDF 执行 + 数据路由   │
└──────────────────────┘
```

## 模块结构

| 模块 | 职责 |
|------|------|
| **iarnet-proto** | Protobuf / gRPC 服务定义与生成代码，是各模块间通信的契约层 |
| **iarnet-common** | 公共工具类与共享依赖 |
| **iarnet-resource** | 资源层：调度服务、适配器注册表、Actor 注册表、工作流启动协调器 |
| **iarnet-application** | 应用层：工作流执行器、工作空间管理、制品（Artifact）准备与上传 |
| **iarnet-control-plane** | 控制平面：Spring Boot 应用，整合 gRPC 服务端，对外提供工作流提交、适配器注册、设备代理注册等接口 |
| **iarnet-adapter** | 资源适配器：运行于边缘或云端节点，通过 Docker 或 Kubernetes 引擎部署 Actor 容器，同时承载 Local Agent 服务 |
| **iarnet-actor-java** | Java Actor 运行时：轻量级独立进程，接收用户函数并执行数据处理逻辑 |
| **iarnet-api/java** | 用户端 Java DSL：提供 `Workflow`、`Flow`、`Source`、`Sink` 等流式 API，以及 gRPC 工作流提交客户端 |
| **iarnet-api-cli** | 命令行客户端 |
| **iarnet-example/java** | 使用 Java DSL 编写的示例工作流 |

## 工作流 DSL

用户通过链式 API 声明数据处理管道，支持 `map`、`flatMap`、`filter`、`union`、`keyBy`、`connect` 等操作。每个算子可指定副本数量和资源需求（CPU、内存）。

```java
Workflow wf = Workflow.create();

Flow<String> input = wf.source(ConstantSource.of("Hello World! test1 test2 TTT"));

Flow<String> words = input
        .flatMap(line -> Arrays.asList(line.split(" ")),
                2, Resource.of(1.5, "1Gi"))    // 2 个副本，每个 1.5 核 CPU + 1Gi 内存
        .filter(w -> w.length() > 3)
        .map(String::toLowerCase);

words.sink(PrintSink.of());
wf.execute();
```

DSL 内部将用户定义的处理逻辑构建为由 `Node`（Source / Operator / Sink）和 `Edge` 组成的 DAG 图，随后通过 `GraphToProtoConverter` 转换为 Protobuf 格式的 `WorkflowGraph` IR，最终经 gRPC 提交至控制平面。

## 中间表示 (IR)

工作流的中间表示以 Protobuf 定义，核心结构包括：

- **WorkflowGraph**：包含 `workflow_id`、`application_id`、节点列表和边列表
- **Node**：分为 SOURCE、OPERATOR、SINK 三类
  - Source 类型：CONSTANT（常量数据源）、FILE（文件数据源）
  - Operator 类型：MAP、FLAT_MAP、FILTER、UNION
  - Sink 类型：PRINT
- **FunctionDescriptor**：描述用户自定义函数（UDF），包含语言标识、函数标识符、序列化函数体或制品路径
- **Resource**：声明算子所需的 CPU、内存、GPU 资源

## 通信机制

系统中的通信全部基于 gRPC，采用三条核心通道：

### CommandChannel（适配器 ↔ 控制平面）

由 Adapter 主动建立的双向流式 gRPC 连接。控制平面通过该通道下发部署指令（DeployInstance）、停止指令（StopInstance）、制品传输请求（TransferArtifact）等，Adapter 异步返回执行结果。

### SignalingChannel（设备代理 ↔ 控制平面）

由 Device Agent 主动建立的双向流式连接，承载 Actor 生命周期事件（ActorReadyReport、ActorChannelStatus）和控制指令的转发（如 StartSourceDirective）。

### LocalChannel（Actor ↔ 本地代理）

Actor 容器与同一节点上的 Local Agent 之间的 gRPC 连接。Actor 通过该通道注册自身、接收函数分配和数据源配置，并进行行级数据的输入输出（RowOutput / RowDelivery）。

## 调度与资源管理

`DefaultSchedulerService` 负责将逻辑工作流图转换为物理部署方案：

1. **放置决策**：遍历工作流 DAG 中的每个节点和副本，从在线适配器列表中选择资源充足的节点进行放置
2. **拓扑计算**：根据 DAG 边关系为每个 Actor 实例计算上游和下游 Actor 地址
3. **Actor 注册**：在 `WorkflowStartCoordinator` 中预注册所有 Actor，建立启动协调机制
4. **部署下发**：通过 CommandChannel 向目标 Adapter 发送 `DeployInstanceRequest`
5. **物理图构建**：生成包含 `ActorDeployment` 和 `ActorInstance` 的 `PhysicalWorkflowGraph`

Actor 地址采用统一格式：`actor://{appId}/{workflowId}/{nodeId}/{replicaIndex}`。

## 端到端执行流程

1. **提交**：用户通过 DSL 或 CLI 将工作流提交至控制平面的 `WorkflowService`
2. **制品准备**：`ArtifactPrepareVisitor` 遍历 DAG 节点，将用户 JAR 等制品上传至 MinIO/OSS
3. **调度**：`DefaultSchedulerService` 选择适配器、分配 Actor 地址、下发部署命令
4. **容器启动**：Adapter 收到 DeployInstance 指令后，通过 Docker/Kubernetes 引擎启动 Actor 容器
5. **Actor 注册**：Actor 启动后连接本地 Local Agent，注册自身并接收函数分配和数据源配置
6. **就绪上报**：Local Agent 通过 SignalingChannel 向控制平面上报 ActorReadyReport 和 ActorChannelStatus
7. **启动协调**：`WorkflowStartCoordinator` 等待所有 Actor 就绪且至少一条数据通道已建立后，向所有 Source Actor 下发 `StartSourceDirective`
8. **数据流转**：Source Actor 产生数据行，经 Local Agent 路由至下游 Operator/Sink Actor 处理

## 技术栈

| 类别 | 技术选型 |
|------|---------|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.2 |
| RPC | gRPC 1.64 + Protobuf 3.25 |
| 容器运行时 | Docker (docker-java 3.3)、Kubernetes (fabric8 6.13) |
| 对象存储 | MinIO (用于制品管理) |
| 持久化 | SQLite + JPA/Hibernate (应用元数据) |

## 当前状态与后续规划

### 已实现

- 完整的工作流 DSL（Java）及 IR 设计
- 基于 gRPC 双向流的 CommandChannel / SignalingChannel / LocalChannel 通信体系
- 控制平面核心功能：工作流提交、调度、Actor 生命周期管理
- 资源适配器：支持 Docker 和 Kubernetes 两种容器引擎
- Java Actor 运行时：支持 MAP、FLAT_MAP、FILTER 算子语义
- 端到端的工作流提交、调度、部署、启动与数据流转

### 待实现 / 规划中

- Gossip 协议实现多节点资源发现
- ICE/STUN/TURN 机制支持更复杂的 NAT 穿越场景
- Python Actor 运行时
- 跨域 OSS 管理
- 调度算法优化与调度冲突的指数退避策略
- 容错与恢复机制
- 资源监听与清理
