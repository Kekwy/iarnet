## DSL 合流与 Fork-Join 设计说明（参考 Flink）

本文结合之前讨论，说明在 DSL 中如何表达多条数据流的“合流”场景，尤其是：

- 简单合流（Join）
- 同一条原始数据多路处理后的 Fork-Join 合并
- 与 `keyBy`、状态管理的关系

---

### 1. 合流的两种核心语义

在流式系统里，“把两条流合成一条”的语义主要有两类：

- **Join（无对齐合流）**：  
  只是把多条类型相同的流拼在一起，得到一个更大的流，不要求元素之间一一对应。

- **Fork-Join（同一条数据多路处理后再对齐合并）**：  
  同一条原始记录在多条分支上分别处理，之后希望把这几路结果按“原始记录”对齐，组合成一个新对象。

这两个语义的 DSL API 和后端实现都不同，下面分别说明。

---

### 2. 简单合流：`join`

#### 2.1 语义

对标 Flink 的：

```java
DataStream<T> s3 = s1.union(s2);
```

语义：

- `s3` 中的元素 = `s1` 的所有元素 ∪ `s2` 的所有元素。
- 不要求同一条原始数据在 s1、s2 之间有任何“一一对应”的关系。

#### 2.2 DSL 设计

建议在 DSL 中提供：

```java
Flow<T> s1 = ...;
Flow<T> s2 = ...;

Flow<T> merged = s1.join(s2);
```

约束：

- 参与合流的流元素类型必须相同 `T`。

#### 2.3 IR 表达

在 `WorkflowGraph` 中：

- 引入 `JOIN` 类型的多输入算子节点，如：
  - `NodeKind.JOIN` 或类似标识。
  - 多条边 `s1 -> joinNode`、`s2 -> joinNode`。

运行时：

- 上游各分支把元素按原样推入 `JOIN` 节点，对下游表现为一条普通流。

---

### 3. Fork-Join：同一条数据多路处理后再合并

#### 3.1 典型场景

以你提到的场景为例：

- 对同一组原始数据 `X`：
  - 左流：每条原始数据 `x` 经分支 1 产生 **1 条**结果 `L(x)`。
  - 右流：每条原始数据 `x` 经分支 2 产生 **多条**结果 `R_i(x)`（例如 2 条）。
- 目标：将这些结果合成一个对象，如 `Combined(x) = (L(x), R_1(x), R_2(x))`。

这个场景属于 **Fork-Join + one-to-many join**。

#### 3.2 DSL 分支（fork）

首先从一个源流派生出两条分支：

```java
Flow<Input> src = wf.source(...);

// 分支 1：每条原始数据产出 1 个 LeftResult
Flow<LeftResult> left = src.map(f1);

// 分支 2：每条原始数据产出若干 RightResult（0..n 条）
Flow<RightResult> right = src.flatMap(f2);
```

- `f1: Input -> LeftResult`
- `f2: Input -> Iterable<RightResult>` 或 `Consumer<Collector<RightResult>>`。

#### 3.3 Fork-Join 合并：`connect` + `CoProcess`

借鉴 Flink 的 `connect + CoProcessFunction`，在 DSL 中引入 `CoFlow` 概念：

```java
// two keyed streams
Flow<LeftResult> leftKeyed  = left.keyBy(x -> x.getKey());
Flow<RightResult> rightKeyed = right.keyBy(x -> x.getKey());

CoFlow<LeftResult, RightResult> connected =
        leftKeyed.connect(rightKeyed);
```

然后在 `CoFlow` 上提供双输入处理能力：

```java
Flow<CombinedResult> joined = connected.process(
    new CoProcessFunction<LeftResult, RightResult, CombinedResult>() {

        // 每个 key 对应一条 LeftResult（单值）
        private ValueState<LeftResult> leftState;

        // 每个 key 对应一组 RightResult（多值）
        private ListState<RightResult> rightState;

        @Override
        public void processElement1(LeftResult value,
                                    Context ctx,
                                    Collector<CombinedResult> out) {
            // 更新左侧状态
            leftState.update(value);

            // 如果右侧已经有若干条结果，则触发输出
            Iterable<RightResult> rights = rightState.get();
            if (rights != null) {
                for (RightResult r : rights) {
                    out.collect(combine(value, r));
                }
            }
        }

        @Override
        public void processElement2(RightResult value,
                                    Context ctx,
                                    Collector<CombinedResult> out) {
            // 将右侧结果追加到当前 key 的列表中
            List<RightResult> current = listOrEmpty(rightState.get());
            current.add(value);
            rightState.update(current);

            // 如果左侧已到达，同样可以输出匹配结果
            LeftResult left = leftState.get();
            if (left != null) {
                out.collect(combine(left, value));
            }
        }
    });
```

说明：

- `leftState` / `rightState` 是与 key 相关的状态字段，但只在算子实例中 **各一份**：
  - 运行时内部通过 `key -> state` 映射，为每个 key 维护一份状态。
  - 不是“每个 key 一个函数对象”，而是“一个函数对象 + 多份 key 状态”。
- 你可以在 `combine` 中实现：
  - 输出多条组合记录 `(L(x), R_i(x))`；
  - 或者先累积所有 `R_i(x)`，在某个条件（完整、超时等）下输出一个 `CombinedResult(L(x), List<R_i(x)>)`。

#### 3.4 DSL API 建议

可以引入以下接口：

- 在 `Flow<T>` 上：

```java
<K> KeyedFlow<T, K> keyBy(KeySelector<T, K> selector);
```

- 在 `KeyedFlow<L,K>` 上：

```java
<R> CoKeyedFlow<L, R, K> connect(KeyedFlow<R, K> other);
```

- 在 `CoKeyedFlow<L,R,K>` 上：

```java
<OUT> Flow<OUT> process(CoProcessFunction<L, R, OUT> fn);
```

这样可以保持与 Flink 类似的心智模型。

---

### 4. 简化版 Fork-Join：单算子 `map2`

如果暂时不实现 `CoFlow` / 多输入算子，也可以提供一种“在一个算子里直接 fork-join”的简化形式：

```java
Flow<Input> src = wf.source(...);

Flow<CombinedResult> result = src.map2(
    f1,      // Input -> A
    f2,      // Input -> List<B>
    combiner // (A, List<B>) -> CombinedResult
);
```

- 底层实现为一个单输入算子：

```java
Input x ->
  A a = f1(x);
  List<B> bs = f2(x);
  return combiner(a, bs);
```

- 优点：
  - 实现简单，不需要多输入节点和复杂的状态同步。
  - 对于“同一个原始记录、两路处理后合并”的场景，语义直观。
- 缺点：
  - 分支不是真正独立的物理流，不能单独扩展/复用。
  - 且两个分支的并行度绑定在同一算子内（不过在你的 Actor 模式下，一个算子内也可以内部并发调用两个函数）。

这一形式适合作为 DSL v1 的入门能力，后续在需要更强表达力时，可引入 `connect + CoProcess` 风格的多输入算子。

---

### 5. 与 `keyBy`、Actor 实现的关系（概念性说明）

- `keyBy` 的本质：  
  将流按 key 分片，后续算子的每个实例只处理部分 key，并为每个 key 维护独立状态。

- 在 Actor 模式下：
  - 每个算子对应多个 Actor 实例（并行度 N）。
  - 对于 keyed 流，路由规则通常为：`shard = hash(key) % N`。
  - 每个 Actor 内部维护：

    ```java
    Map<Key, LeftResult>            leftState;
    Map<Key, List<RightResult>>     rightState;
    ```

    相当于 Flink 中的 `ValueState` / `ListState`。

- 合流算子（无论是 `join` 还是 `CoProcess`）在 IR 中都表现为多输入节点，  
  实际路由和状态拆分由运行时（Actor + Device Agent）根据 key 和分区规则完成。

---

### 6. 小结

1. **Join**：适合“同类型流的简单合并”，语义等同于 Flink 的 `union`。
2. **Fork-Join**：
   - 高级版：`connect + keyBy + CoProcess`，可表达复杂的多路 join 和 one-to-many join。
   - 简化版：`map2(f1, f2, combiner)`，在单算子内部完成 fork-join。
3. `keyBy` 并不会“每个 key 一个函数实例”，而是通过 per-key 状态在同一个算子/Actor 实例里维护大量 key 的独立状态，这一点在你的 Actor 运行时实现中同样适用。 

