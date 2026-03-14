# IARNet SDK（Java 实现）

IARNet SDK（Java 实现）提供一套面向工作流编排的 Java DSL，支持用户描述、构建并提交分布式计算工作流至 IARNet control-plane。

## 环境要求

- **Java** 17+
- **Maven** 3.8+
- 依赖 `iarnet-proto-java`（proto 生成的 Java 代码 + gRPC stub）

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.kekwy.iarnet</groupId>
    <artifactId>iarnet-sdk-java</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. 编写工作流

```java
import com.kekwy.iarnet.sdk.Workflow;
import com.kekwy.iarnet.sdk.dsl.Inputs;
import com.kekwy.iarnet.sdk.dsl.Outputs;

Workflow w = Workflow.create("my-workflow");
w.input("src", Inputs.of(1, 2, 3))
 .then("double", (Integer x) -> x * 2)
 .then("sink", Outputs.println());
w.execute();
```

### 3. 设置环境变量

```bash
export IARNET_APP_ID=my-app-id
export IARNET_GRPC_PORT=9090
```

## 核心概念

### Workflow

`Workflow` 是 DSL 入口。通过 `Workflow.create(name)` 创建实例，调用 `input()` 定义数据源，返回的 `Flow` 对象支持链式操作。

### Flow

`Flow<T>` 表示携带类型 `T` 的数据流，支持以下操作：

| 方法 | 说明 |
|------|------|
| `then(name, TaskFunction)` | 追加任务节点（单输入单输出） |
| `then(name, OutputFunction)` | 连接到 Sink，形成 `EndFlow` |
| `join(name, otherFlow, JoinFunction)` | 合并两路数据流 |
| `when(ConditionFunction)` | 条件分支 |
| `returns(TypeToken)` | 提供输出类型提示（类型推断失败时使用） |

所有 `then` / `join` 方法均支持附加 `ExecutionConfig` 参数指定副本数与资源。

### 函数接口

所有函数接口位于 `com.kekwy.iarnet.sdk.function` 包下，均继承 `Function`（`Serializable`）：

| 接口 | 签名 | 用途 |
|------|------|------|
| `InputFunction<O>` | `Optional<O> next()` | 数据源，按序产出元素 |
| `TaskFunction<I, O>` | `O apply(I input)` | 单输入单输出转换 |
| `OutputFunction<I>` | `void accept(I input)` | Sink，消费元素 |
| `JoinFunction<T, U, V>` | `V join(OptionalValue<T>, OptionalValue<U>)` | 合并两路输入 |
| `ConditionFunction<T>` | `boolean test(T input)` | 条件分支判定 |

### DSL 工厂

`com.kekwy.iarnet.sdk.dsl` 包提供便捷静态工厂：

- **`Inputs.of(T... items)`** — 从内存数组构造输入源
- **`Outputs.println()`** — 打印到 stdout 的 Sink
- **`Outputs.noop()`** — 空操作 Sink
- **`Tasks.pythonTask(identifier)`** — Python 任务描述符，源码约定位于 `resource/function/python/`
- **`Tasks.goTask(identifier)`** — Go 任务描述符，源码约定位于 `resource/function/go/`

### 跨语言任务

通过 `Tasks` 创建 Python / Go 任务描述符，DSL 阶段仅做描述，实际执行由运行时调度至对应语言的 worker。外部函数源码须按约定放置于 `resource/function/python/` 或 `resource/function/go/` 目录：

```java
// Python 任务
Flow<DecodedFrame> decoded = cam.then(
    "decode",
    Tasks.<VideoFrame, DecodedFrame>pythonTask("decode_frame"),
    edgeConfig
);

// Go 任务（带输出类型提示）
Flow<Result> result = input.then(
    "process",
    Tasks.<Input, Result>goTask("Process", new TypeToken<Result>() {})
);
```

### 执行配置

`ExecutionConfig` 配置节点的副本数与资源规格：

```java
ExecutionConfig config = ExecutionConfig.of()
    .replicas(2)
    .resource(b -> b.cpu(2).memory("4Gi").gpu(1));

flow.then("detect", Main::detect, config);
```

### 类型推断

SDK 通过反射自动推断节点的输出类型。当推断失败时（如 lambda 泛型擦除），可通过以下方式提供类型提示：

```java
// 方式一：在 flow 上调用 returns()
flow.then("task", myLambda)
    .returns(new TypeToken<List<Frame>>() {});

// 方式二：在 Tasks 工厂方法中传入 TypeToken
Tasks.pythonTask("func", "source.py", new TypeToken<List<Frame>>() {});
```

## 异常体系

所有 SDK 异常继承自 `IarnetException`（`RuntimeException`），可统一捕获或按子类处理：

| 异常 | 场景 |
|------|------|
| `IarnetConfigurationException` | 环境变量未设置、端口格式错误 |
| `IarnetValidationException` | 参数非法、类型推断失败、DSL 用法错误 |
| `IarnetSerializationException` | 函数序列化/反序列化失败 |
| `IarnetSubmissionException` | 工作流提交被服务端拒绝 |
| `IarnetCommunicationException` | gRPC 调用失败、网络不可达 |

## 包结构

```
com.kekwy.iarnet.sdk
├── Workflow              # DSL 入口
├── Flow                  # 链式 DSL 接口
├── ConditionalFlow       # 条件分支 DSL
├── EndFlow               # 末端 flow
├── ExecutionConfig       # 节点执行配置
├── dsl/                  # 静态工厂
│   ├── Inputs            # 输入源工厂
│   ├── Outputs           # 输出 Sink 工厂
│   └── Tasks             # 跨语言任务工厂
├── function/             # 函数接口
│   ├── Function          # 根接口（Serializable）
│   ├── InputFunction     # 数据源
│   ├── TaskFunction      # 任务
│   ├── OutputFunction    # Sink
│   ├── JoinFunction      # 合并
│   ├── ConditionFunction # 条件
│   ├── PythonTaskFunction# Python 任务描述符
│   └── GoTaskFunction    # Go 任务描述符
├── type/                 # 类型工具
│   ├── TypeToken         # 泛型类型标记
│   └── OptionalValue     # 可序列化可选值
├── exception/            # 异常体系
│   ├── IarnetException
│   ├── IarnetConfigurationException
│   ├── IarnetValidationException
│   ├── IarnetSerializationException
│   ├── IarnetSubmissionException
│   └── IarnetCommunicationException
└── util/                 # 内部工具
    ├── IDUtil            # ID 生成
    ├── SerializationUtil # 序列化
    └── TypeExtractor     # 类型提取
```

## 构建与测试

```bash
mvn clean install        # 编译与安装
mvn test                 # 运行测试
```
