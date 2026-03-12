# Fluxion 一期 `model_json` 契约

## 1. 文档目的

本文档用于正式定义一期 `model_json` 的契约边界、校验方式和兼容规则，作为 Engine 与 Modeler 之间的核心协议唯一来源。

`model_json` 的设计目标如下：

1. 只承载运行时必需信息，不携带画布坐标、样式等展示态数据。
2. 作为发布后的不可变快照，由后端编译生成，引擎只消费该模型执行。
3. 通过“两层契约”同时覆盖结构校验与编译期语义校验。

相关文档：

- 技术方案摘要：[technical-solution.md](./technical-solution.md)
- 运行语义：[runtime-semantics.md](./runtime-semantics.md)
- 节点参数定义：[node-schemas.md](./node-schemas.md)
- 结构校验 Schema：[model-json.schema.json](./model-json.schema.json)

## 2. 两层契约

一期 `model_json` 采用“两层契约”：

1. 第一层：`JSON Schema`
   - 负责校验 JSON 结构、字段类型、必填项、枚举值、条件必填关系。
   - 负责约束已归一化后的运行时字段形态，如 `runtimePolicy`、`sideEffectPolicy`、节点 `config` 的最小结构。
   - 不负责 DAG、拓扑、引用一致性、表达式作用域等跨字段语义规则。
2. 第二层：编译期语义校验
   - 负责校验 DAG 约束、拓扑排序、节点和连线引用一致性、条件分支合法性、表达式可用命名空间、资源引用有效性等。
   - 由 Modeler/发布编译器执行，错误分期归类为 `SCHEMA_VALIDATE`、`STRUCTURE_VALIDATE`、`MODEL_COMPILE`。

## 3. 保守兼容版 Schema 约定

一期正式 Schema 采用“保守兼容版”风格，原则如下：

1. 核心字段严格定义，确保 Engine 与 Modeler 对基础字段理解一致。
2. 扩展字段统一收敛到 `extensions` 对象，避免任意字段漂移。
3. 节点按 `nodeType` 做判别校验，不同节点的 `config` 结构分别约束。
4. 发布编译后应补齐归一化字段；对缺省值，运行态建议写成显式值，而不是依赖引擎再次推断。
5. 后续新增字段时，优先放入 `extensions`；若要升级核心字段集合，应通过新的 `modelVersion` 处理兼容性。

说明：

- 一期 Schema 以 `modelVersion = 1.0` 为正式版本。
- `extensions` 是保留扩展区，不参与一期引擎最小执行语义。

## 4. 第一层：JSON Schema 负责什么

`JSON Schema` 负责以下内容：

- 顶层结构存在且类型正确：`modelVersion`、`flowDefId`、`flowVersionId`、`flowCode`、`flowName`、`flowOutputMapping`、`startNodeIds`、`nodes`、`edges`、`topology`
- `topology` 最小结构存在：`orderedNodeIds`、`levelGroups`、`terminalNodeIds`
- 运行时节点公共字段存在：`nodeId`、`nodeType`、`nodeName`、`incomingNodeIds`、`outgoingNodeIds`、`config`、`inputMapping`、`outputMapping`、`runtimePolicy`
- `runtimePolicy` 已归一化为显式对象，至少包含 `timeoutMs`、`retry`、`retryIntervalMs`、`logEnabled`
- `http` / `dbUpdate` 节点必须带 `sideEffectPolicy`
- 资源型节点的 `config.resourceRef` 必须是字符串标识
- 节点 `config` 必须满足对应节点类型的一期最小参数结构

`JSON Schema` 不负责以下内容：

- 图是否为 DAG
- `nodeId` / `edgeId` 是否全局唯一
- `startNodeIds` 是否等于真实入度为 `0` 的节点集合
- `topology.orderedNodeIds` / `levelGroups` / `terminalNodeIds` 是否与真实图结构一致
- `edges` 中引用的节点是否真实存在
- `branchKey` 是否与源节点类型匹配
- 表达式内容是否合法、是否只使用允许的稳定命名空间
- `resourceRef` 是否能在资源体系中解析到真实资源

以上内容由第二层编译期语义校验负责。

## 5. 第二层：编译期语义校验负责什么

发布编译阶段至少要补齐并校验以下规则：

### 5.1 结构规则

1. 主流程必须是 DAG。
2. 所有节点都必须从至少一个起始节点可达。
3. 所有节点都必须能到达至少一个结束节点。
4. 所有节点忽略边方向后必须属于同一个连通分量。
5. 所有节点都必须位于至少一条“起始节点 -> 结束节点”的有效路径上。

### 5.2 引用规则

1. `nodeId`、`edgeId` 必须全局唯一。
2. `edges.sourceNodeId`、`edges.targetNodeId`、`startNodeIds`、`topology.*` 中出现的节点必须真实存在。
3. `incomingNodeIds`、`outgoingNodeIds` 必须与 `edges` 计算结果一致。
4. `topology.orderedNodeIds` 必须完整覆盖全部节点且满足拓扑顺序。
5. `topology.levelGroups` 必须与 `orderedNodeIds` 覆盖同一集合。

### 5.3 分支规则

1. 条件节点固定只有 `true` / `false` 两个输出分支。
2. 条件节点出边的 `branchKey` 只能是 `true` 或 `false`。
3. 非条件节点出边的 `branchKey` 固定为 `default`。

### 5.4 表达式与资源规则

1. `inputMapping`、`outputMapping`、`flowOutputMapping` 中的表达式只允许依赖稳定命名空间。
2. 节点局部 `raw` 只允许在当前节点 `outputMapping` 中使用。
3. `http` / `dbUpdate` 的副作用策略必须与节点类型匹配。
4. `config.resourceRef` 必须能解析到合法资源。
5. 节点 `config` 必须是已校验通过、默认值已补齐、条件字段已裁剪后的结构。

## 6. 一期运行时模型正式字段

### 6.1 顶层字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `modelVersion` | string | 是 | 运行时模型版本，一期固定为 `1.0` |
| `flowDefId` | long | 是 | 流程定义 ID |
| `flowVersionId` | long | 是 | 流程版本 ID |
| `flowCode` | string | 是 | 流程编码 |
| `flowName` | string | 是 | 流程名称 |
| `flowOutputMapping` | object | 是 | 编译后的流程最终输出映射 |
| `startNodeIds` | array | 是 | 起始节点 ID 列表 |
| `nodes` | array | 是 | 编译后的运行时节点列表 |
| `edges` | array | 是 | 编译后的运行时边列表 |
| `topology` | object | 是 | 拓扑与调度辅助信息 |
| `extensions` | object | 否 | 扩展保留区 |

### 6.2 `topology` 字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `orderedNodeIds` | array | 是 | 全流程拓扑排序结果 |
| `levelGroups` | array | 是 | 可并行执行层级分组 |
| `terminalNodeIds` | array | 是 | 编译后识别出的结束节点 |
| `extensions` | object | 否 | 扩展保留区 |

### 6.3 节点公共字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `nodeId` | string | 是 | 节点 ID |
| `nodeType` | string | 是 | 节点类型 |
| `nodeName` | string | 是 | 节点名称 |
| `incomingNodeIds` | array | 是 | 上游节点快照 |
| `outgoingNodeIds` | array | 是 | 下游节点快照 |
| `config` | object | 是 | 已校验、已归一化的节点参数结构 |
| `inputMapping` | object | 是 | 输入映射；建议发布后补齐为空对象而非省略 |
| `outputMapping` | object | 是 | 输出映射；建议发布后补齐为空对象而非省略 |
| `runtimePolicy` | object | 是 | 已归一化的运行时策略 |
| `sideEffectPolicy` | object | 条件必填 | `http` / `dbUpdate` 节点必须存在 |
| `extensions` | object | 否 | 扩展保留区 |

### 6.4 运行时边字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `edgeId` | string | 是 | 边 ID |
| `sourceNodeId` | string | 是 | 起始节点 |
| `targetNodeId` | string | 是 | 目标节点 |
| `branchKey` | string | 是 | 普通边取 `default`，条件边取 `true` / `false` |
| `extensions` | object | 否 | 扩展保留区 |

## 7. 编译产物必须显式补齐的 5 类信息

`model_json` 至少应显式体现以下编译产物信息：

| 要求 | 在 `model_json` 中的体现 |
| --- | --- |
| 1. 节点的上下游关系 | 节点上的 `incomingNodeIds`、`outgoingNodeIds` |
| 2. 拓扑排序结果 | 顶层 `topology.orderedNodeIds`、`topology.levelGroups`、`topology.terminalNodeIds` |
| 3. 已归一化的运行时策略 | 节点上的 `runtimePolicy` |
| 4. 已固定的资源引用标识 | 资源型节点 `config.resourceRef` |
| 5. 已校验通过的参数结构 | 节点上的 `config`，即发布编译后的运行态参数快照 |

说明：

- 第 5 点不要求额外引入 `validated = true` 一类冗余标记；节点 `config` 本身就是已校验通过的编译结果。
- 若后续需要记录更多编译元数据，优先放入 `extensions`，避免污染一期最小执行契约。

## 8. 校验阶段与错误归类

建议维持如下校验阶段：

| 阶段 | 主要职责 |
| --- | --- |
| `SCHEMA_VALIDATE` | 校验 JSON 结构、字段类型、必填项、条件必填关系 |
| `STRUCTURE_VALIDATE` | 校验 DAG、可达性、连通性、分支合法性、拓扑一致性 |
| `MODEL_COMPILE` | 校验表达式作用域、资源引用、运行时策略归一化、节点参数编译 |

建议错误对象沿用以下结构：

```json
{
  "errorCode": "UNREACHABLE_NODE",
  "stage": "STRUCTURE_VALIDATE",
  "nodeId": "node_http_2",
  "field": "edges",
  "message": "node is unreachable from any start node",
  "severity": "ERROR"
}
```

## 9. 兼容性规则

1. 一期正式 `model_json` 版本固定为 `modelVersion = 1.0`。
2. 不得在不升级契约说明的情况下随意扩张核心字段集合。
3. 后续新增的非核心信息优先放入 `extensions`。
4. 引擎只依赖本文件定义的正式字段与语义，不得回读设计态 `graph_json`。
