# Fluxion 一期 Admin API：流程与运行监控

## 1. 文档目的

本文档定义一期管理端中与流程生命周期和运行监控相关的 `/admin/*` 接口，覆盖：

1. 流程定义、草稿与版本接口
2. 流程实例、节点执行与 attempt 明细接口

管理端通用约定（统一响应、时间格式、分页、并发控制）统一以 [../admin-api-contract.md](../admin-api-contract.md) 为准。

## 2. 适用范围与事实源

本文档维护以下管理端接口：

1. `/admin/flows/*`
2. `/admin/instances/*`
3. `/admin/executions/*`

相关对象语义与事实源：

| 主题 | 正式文档 |
| --- | --- |
| 管理端通用约定 | [../admin-api-contract.md](../admin-api-contract.md) |
| 运行语义、实例状态、节点状态 | [../runtime-semantics.md](../runtime-semantics.md) |
| `graph_json` / `model_json` 编译与校验 | [../graph-json-contract.md](../graph-json-contract.md) / [../model-json-contract.md](../model-json-contract.md) |
| 错误码 | [../error-codes.md](../error-codes.md) |
| 落库表结构 | [../../schema-pg.sql](../../schema-pg.sql) |

## 3. 流程定义、草稿与版本接口

### 3.1 接口清单

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/admin/flows` | 查询流程定义列表 |
| `POST` | `/admin/flows` | 创建流程定义 |
| `GET` | `/admin/flows/{flowId}` | 查询流程定义详情 |
| `PUT` | `/admin/flows/{flowId}` | 更新流程定义基础信息 |
| `DELETE` | `/admin/flows/{flowId}` | 逻辑删除流程定义 |
| `GET` | `/admin/flows/{flowId}/draft` | 查询当前草稿 |
| `PUT` | `/admin/flows/{flowId}/draft` | 保存当前草稿 |
| `POST` | `/admin/flows/{flowId}/validate` | 校验当前草稿 |
| `POST` | `/admin/flows/{flowId}/publish` | 发布新版本 |
| `GET` | `/admin/flows/{flowId}/versions` | 查询版本列表 |
| `GET` | `/admin/flows/{flowId}/versions/{versionId}` | 查询版本详情 |

### 3.2 流程定义对象

流程定义详情 `data` 至少包含：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `flowId` | long | 流程定义 ID |
| `flowCode` | string | 流程业务编码 |
| `flowName` | string | 流程名称 |
| `description` | string / null | 描述 |
| `category` | string / null | 分类 |
| `activeVersionId` | long / null | 当前生效版本 |
| `latestVersionNum` | int | 当前最大正式版本号 |
| `revision` | int | 流程定义修订号 |
| `isDeleted` | boolean | 是否已删除 |
| `createTime` | datetime | 创建时间 |
| `updateTime` | datetime / null | 更新时间 |

### 3.3 创建 / 更新 / 删除规则

1. 创建流程定义请求体至少包含 `flowCode`、`flowName`。
2. 更新流程定义请求体至少包含 `flowName`、`revision`；允许附带 `description`、`category`。
3. `DELETE /admin/flows/{flowId}` 为逻辑删除。
4. 删除前必须满足：
   - 全部 HTTP 端点已下线或禁用
   - 全部调度任务已暂停
5. 删除后不可再编辑、发布或新触发，但历史版本、实例与审计仍可查询。

### 3.4 草稿接口

草稿详情 `data` 至少包含：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `draftId` | long | 草稿 ID |
| `flowId` | long | 关联流程定义 |
| `baseVersionId` | long / null | 草稿基线版本 |
| `dslVersion` | string | 一期固定 `1.0` |
| `graphJson` | object | 设计态草稿 |
| `remark` | string / null | 最近保存说明 |
| `draftRevision` | int | 草稿修订号 |
| `createTime` | datetime | 创建时间 |
| `updateTime` | datetime / null | 更新时间 |

草稿保存请求体至少包含：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `graphJson` | object | 是 | 设计态草稿 |
| `draftRevision` | int | 是 | 乐观锁修订号 |
| `remark` | string | 否 | 保存说明 |

规则：

1. 草稿保存允许保留未发布的半成品，但必须返回结构化校验结果。
2. 草稿接口不直接返回 `modelJson`。
3. 草稿保存成功后递增 `draftRevision`。

### 3.5 校验与发布接口

`POST /admin/flows/{flowId}/validate` 的返回 `data` 至少包含：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `valid` | boolean | 是否通过校验 |
| `stage` | string / null | 主要失败阶段 |
| `errors` | array | 结构化错误列表 |
| `warnings` | array | warning 列表 |

单条错误对象至少包含：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `errorCode` | string | 错误码 |
| `stage` | string | `SCHEMA_VALIDATE` / `STRUCTURE_VALIDATE` / `MODEL_COMPILE` |
| `field` | string / null | 相关字段 |
| `nodeId` | string / null | 相关节点 |
| `message` | string | 人类可读消息 |
| `severity` | string | `ERROR` / `WARNING` |

`POST /admin/flows/{flowId}/publish` 请求体至少包含：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `draftRevision` | int | 是 | 当前草稿修订号 |
| `remark` | string | 否 | 发布说明 |

发布成功返回 `data` 至少包含：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `flowVersionId` | long | 新版本 ID |
| `versionNum` | int | 新正式版本号 |
| `activeVersionId` | long | 最新生效版本 |

### 3.6 版本接口

版本详情 `data` 至少包含：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `flowVersionId` | long | 版本 ID |
| `flowId` | long | 流程定义 ID |
| `versionNum` | int | 正式版本号 |
| `status` | string | `PUBLISHED` / `ARCHIVED` |
| `dslVersion` | string | 设计 DSL 版本 |
| `graphJson` | object | 发布时固化的设计态快照 |
| `modelJson` | object | 发布后的运行时快照 |
| `remark` | string / null | 发布说明 |
| `publishedTime` | datetime / null | 发布时间 |

## 4. 流程实例与执行记录接口

### 4.1 接口清单

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/admin/instances` | 查询实例列表 |
| `GET` | `/admin/instances/{instanceId}` | 查询实例详情 |
| `GET` | `/admin/instances/{instanceId}/executions` | 查询节点执行列表 |
| `GET` | `/admin/executions/{executionId}` | 查询节点执行详情 |
| `GET` | `/admin/executions/{executionId}/attempts` | 查询 attempt 明细 |

### 4.2 列表筛选约定

`GET /admin/instances` 额外支持以下筛选参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `flowCode` | string | 按流程编码筛选 |
| `businessKey` | string | 按业务键筛选 |
| `status` | string | 按实例状态筛选 |
| `triggerType` | string | 按触发方式筛选 |
| `startTimeFrom` | datetime | 开始时间下界 |
| `startTimeTo` | datetime | 开始时间上界 |

### 4.3 实例详情对象

实例详情 `data` 至少包含：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `instanceId` | long | 实例 ID |
| `flowDefId` | long | 流程定义 ID |
| `flowVersionId` | long | 实际执行版本 ID |
| `flowCode` | string | 流程编码快照 |
| `flowName` | string | 流程名称快照 |
| `flowVersionNum` | int | 流程版本号快照 |
| `businessKey` | string / null | 业务键 |
| `traceId` | string / null | 链路追踪 ID |
| `triggerType` | string | 触发类型 |
| `status` | string | 实例状态 |
| `errorCode` | string / null | 错误摘要码 |
| `errorMessage` | string / null | 错误摘要消息 |
| `inputData` | object / null | 输入快照 |
| `outputData` | object / null | 最终输出快照 |
| `errorDetail` | string / null | 详细错误 |

### 4.4 执行记录对象

节点执行详情 `data` 至少包含：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `executionId` | long | 执行记录 ID |
| `instanceId` | long | 实例 ID |
| `nodeId` | string | 节点 ID |
| `nodeName` | string | 节点名称 |
| `nodeType` | string | 节点类型 |
| `status` | string | 节点状态 |
| `attemptCount` | int | 真实尝试次数 |
| `retryCount` | int | 重试次数 |
| `skipReason` | string / null | 跳过原因 |
| `errorCode` | string / null | 错误码 |
| `errorMessage` | string / null | 错误摘要 |
| `inputData` | object / null | 输入快照 |
| `outputData` | object / null | 输出快照 |
| `errorDetail` | string / null | 详细错误 |

attempt 明细返回项至少包含 `attemptNo`、`status`、`durationMs`、`errorCode`、`errorMessage`。
