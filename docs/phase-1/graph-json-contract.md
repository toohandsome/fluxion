# Fluxion 一期 `graph_json` 契约

## 1. 文档目的

本文档用于定义一期设计态 `graph_json` 的正式契约边界，作为前端设计器、草稿存储和发布编译入口的唯一来源。

相关文档：

- 技术方案摘要：[technical-solution.md](./technical-solution.md)
- 节点字段定义：[node-schemas.md](./node-schemas.md)
- 运行时模型契约：[model-json-contract.md](./model-json-contract.md)

## 2. 角色边界

`graph_json` 的定位如下：

1. `graph_json` 是设计态草稿模型，保存到 `flx_flow_draft.graph_json`。
2. `graph_json` 允许包含设计器需要的展示态信息，如节点坐标、端口信息和前端扩展字段。
3. `graph_json` 不是引擎直接执行的模型；引擎只执行发布编译后的 `model_json`。
4. 一期禁止前端直接提交 `model_json` 替代 `graph_json`。

## 3. 顶层结构

一期 `graph_json` 至少包含以下顶层字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `dslVersion` | string | 是 | 设计态 DSL 版本，一期固定为 `1.0` |
| `flow` | object | 是 | 流程基础信息与流程最终输出定义 |
| `variables` | array | 否 | 流程级变量定义 |
| `nodes` | array | 是 | 设计态节点列表 |
| `edges` | array | 是 | 设计态连线列表 |
| `extensions` | object | 否 | 设计态扩展保留区 |

## 4. `flow` 结构

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `flowCode` | string | 是 | 流程业务编码 |
| `flowName` | string | 是 | 流程名称 |
| `description` | string | 否 | 流程描述 |
| `category` | string | 否 | 流程分类 |
| `outputMapping` | object | 否 | 设计态流程最终输出映射；发布时编译为 `flowOutputMapping` |
| `extensions` | object | 否 | 扩展保留区 |

规则：

1. `flow.outputMapping` 是流程最终输出的唯一正式来源。
2. 草稿阶段允许缺失 `flow.outputMapping`，发布校验阶段必须补齐。
3. `flow.outputMapping` 的表达式边界与运行时 `flowOutputMapping` 保持一致，只允许访问稳定命名空间。

## 5. `variables` 结构

单个变量对象至少包含：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | string | 是 | 变量名 |
| `type` | string | 是 | 变量类型：`STRING` / `INT` / `LONG` / `DOUBLE` / `BOOLEAN` / `JSON` |
| `defaultValue` | any | 否 | 默认值 |
| `description` | string | 否 | 变量说明 |
| `extensions` | object | 否 | 扩展保留区 |

规则：

1. `graph_json.variables` 是流程级变量声明的唯一正式来源，也是 `vars` 根命名空间的符号表。
2. `name` 在同一流程内必须唯一，并遵循标识符规则 `^[a-zA-Z_][a-zA-Z0-9_]*$`。
3. `defaultValue` 若存在，发布校验时必须与声明 `type` 匹配；若缺失，则发布后的运行时初始值固定为 `null`。
4. 若任一节点配置、`inputMapping`、`outputMapping` 或 `flow.outputMapping` 中读取了 `vars.<name>`，则 `<name>` 在发布时必须已声明。
5. 若存在 `variable` 节点写入 `targetVar`，则 `targetVar` 在发布时必须已声明。
6. 草稿保存阶段允许暂存“引用未声明变量”的半成品模型，但应输出 warning diagnostics；发布阶段必须升级为 error。
7. 一期只校验 `vars` 的根变量名是否声明；如 `vars.userInfo.name` 合法的前提是根变量 `userInfo` 已声明，且通常建议其类型为 `JSON`。
8. 一期不支持 `vars[someExpr]` 这类动态 key 访问；变量访问必须能在编译期解析出固定根变量名。

## 6. 节点对象

单个设计态节点至少包含：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `nodeId` | string | 是 | 画布内唯一标识 |
| `nodeType` | string | 是 | 节点类型 |
| `nodeName` | string | 是 | 节点显示名称 |
| `position` | object | 是 | 画布坐标 |
| `config` | object | 是 | 节点配置 |
| `inputMapping` | object | 否 | 当前节点执行输入映射 |
| `outputMapping` | object | 否 | 当前节点稳定输出映射 |
| `extensions` | object | 否 | 扩展保留区 |

说明：

1. 节点 `config` 的字段级协议统一以 [node-schemas.md](./node-schemas.md) 为准。
2. 资源型节点中的 `config.resourceRef` 一期正式含义固定为 `resourceCode`。
3. `resourceId` 只作为数据库内部主键使用，不进入 `graph_json`。
4. 节点 `outputMapping` 的产物是当前节点稳定输出，不直接写入 `vars`。

## 7. 连线对象

单个设计态连线至少包含：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `edgeId` | string | 是 | 连线唯一标识 |
| `sourceNodeId` | string | 是 | 起始节点 |
| `sourcePort` | string | 否 | 起始端口 |
| `targetNodeId` | string | 是 | 目标节点 |
| `targetPort` | string | 否 | 目标端口 |
| `condition` | object | 否 | 分支条件，仅条件节点输出边使用 |
| `extensions` | object | 否 | 扩展保留区 |

## 8. 设计态标识规则

一期统一采用以下标识规则：

1. `flowCode` 是流程的稳定业务编码。
2. `resourceRef = resourceCode`，作为资源型节点的稳定引用标识。
3. 设计态与运行态都不写入 `resourceId`。
4. 若资源业务编码需要变更，建议新建资源并重新发布依赖该资源的流程版本，而不是静默改写已发布版本依赖。

## 9. 编译与校验边界

发布编译阶段至少负责：

1. 校验 `graph_json` 顶层结构、节点结构和连线结构。
2. 校验节点类型与节点配置是否匹配。
3. 校验 `variables` 声明表的唯一性、类型合法性、默认值合法性以及变量引用一致性。
4. 校验 DAG、可达性、连通性、分支合法性和表达式作用域。
5. 将设计态 `flow.outputMapping` 编译为运行态 `flowOutputMapping`。
6. 将设计态变量声明、节点配置、运行策略、副作用策略和资源引用归一化后写入 `model_json`。

## 10. 兼容性规则

1. 一期正式 `graph_json` 版本固定为 `dslVersion = 1.0`。
2. 新增设计态展示字段优先放入 `extensions`。
3. 节点字段升级优先通过 [node-schemas.md](./node-schemas.md) 扩展，而不是在本文件重复定义字段细节。
