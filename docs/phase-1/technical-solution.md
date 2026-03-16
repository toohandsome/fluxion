# Fluxion 技术方案文档

## 1. 技术目标

一期技术方案的目标是建立一个稳定可扩展的核心底座，优先保障以下能力：

1. 流程设计模型稳定
2. 流程执行模型稳定
3. HTTP 与调度触发闭环可运行
4. 平台治理能力具备最小可用水平

## 2. 总体架构

一期采用三层结构：

1. `Engine`
   负责流程模型、执行调度、上下文、状态机。
2. `Starter`
   负责把 Fluxion 能力装配进业务系统，核心能力与 `Server` 等价；默认不自启独立 Web 端口，可按宿主需要启用 Web/API 与前端页面能力。
3. `Server`
   基于 `Starter` 提供开箱即用的独立部署形态，默认启用 Web/API 暴露与前端页面集成能力，并以独立进程运行。

整体调用链如下：

1. 设计器保存画布 JSON 到草稿
2. Modeler 基于草稿执行模型校验
3. 发布时从草稿编译执行模型并写入流程版本快照
4. HTTP 或调度触发选择生效版本
5. Engine 执行流程并写入实例和节点日志
6. 管理接口查询执行结果和运行状态

## 3. 核心技术栈

### 3.1 后端

1. `JDK 21`
2. `Spring Boot 3.5`
3. `Spring MVC`
4. `MyBatis-Plus`
5. `Jackson`
6. `Quartz`
7. `Spring Expression`

选型原则：

1. 一期表达式引擎只实现 `SpEL`
2. 虚拟线程用于 I/O 型执行场景
3. PG 作为一期正式支持数据库

### 3.2 前端

1. `Vue 3`
2. `AntV X6`
3. Schema 表单方案
4. 后续预留 Monaco 编辑器扩展位

## 4. 一期默认技术决策

以下内容作为一期编码阶段的默认技术实现方案。

### 4.1 表达式与模型

1. 一期表达式引擎固定为 `SpEL`。
2. 设计态草稿与正式版本快照分离存储，草稿不占正式版本号。
3. 发布时生成独立的 `model_json` 执行模型，并分配新的正式 `version_num`。
4. 运行时只依赖已发布版本快照，不直接执行设计态画布 JSON。

### 4.2 执行语义

1. 默认执行语义为 `at-least-once`。
2. 默认策略为“节点失败即流程失败”。
3. 节点支持超时和重试。
4. 并发执行只覆盖 I/O 型场景，执行载体默认采用虚拟线程。
5. 一期采用简化版混合调度，而不是“万物皆虚拟线程”：
   - `log`、`variable`、`condition` 等轻量 CPU 节点默认内联执行；
   - `http`、`dbQuery`、`dbUpdate` 等 I/O 节点进入引擎执行器，默认采用虚拟线程承载。
6. 一期不引入通用事件总线、复杂 `ExecutionType` 体系、多级排队模型；先保持“就绪节点 -> 分发执行 -> 完成后推进 DAG”的最小闭环。
7. 对外部资源访问统一增加基于 `resourceRef` 的并发保护；并发限额通过引擎或资源侧配置控制，不开放为节点级单独配置。

### 4.3 调度与 HTTP

1. 一期采用单机 Quartz 调度。
2. 调度重入策略只支持 `FORBID` 和 `ALLOW`。
3. 任务并发、等待超时、错失策略通过任务表字段配置，其中 `waitTimeoutMs` 表示调度侧等待实例进入终态的最长时间。
4. 调度触发流水拆分为 `dispatchStatus`、`waitStatus`、`instanceStatusSnapshot` 三层内部状态，对外列表默认只展示 `summaryStatus`。
5. `CATCH_UP_BOUNDED` 为一期唯一推荐的补跑追赶策略，默认采用“最近 1 小时、最多 10 次、最老优先”。
6. 一期提供 open、AppKey、Bearer Token、basic auth 四种基础认证方式进行配置。（一期只实现open 与 basic auth；AppKey、Bearer Token为预留后期实现）

### 4.4 数据库与资源

1. 数据库变更工具默认采用 `Flyway`。
2. 数据库节点允许执行参数化 SQL，不在一期实现复杂事务编排。
3. 多数据源通过资源连接表统一管理。
4. 敏感配置通过应用主密钥进行 `AES-GCM` 加密。

### 4.5 工程约定

1. 主键默认采用应用侧雪花算法生成。
2. 管理 API 统一分页格式和错误码风格。
3. 后续编码优先顺序为：`model -> spi -> engine -> persistence -> api -> starter`。
4. 虽然一期产品包含基础前端设计器，但编码阶段可优先落后端底座，再与前端联调。
5. 一期正式错误码基线见 [error-codes.md](./error-codes.md)。

## 5. 执行模型

### 5.1 模型约束

1. 主流程必须是 DAG
2. 连接关系必须通过编译期校验
3. 所有节点在忽略边方向后必须属于同一个连通分量
4. 所有节点都必须同时满足“起始节点可达”和“结束节点可达”
5. 发布时固定版本快照
6. 运行时只执行 `model_json`

### 5.1.1 编译器结构校验推荐步骤

1. 先根据 `nodes` 与 `edges` 构建邻接表、入度表、出度表，并校验节点引用完整性。
2. 以“入度为 `0`”识别起始节点，以“出度为 `0`”识别结束节点。
3. 一期推荐采用 Kahn 拓扑排序：
   - 初始化所有入度为 `0` 的节点队列；
   - 逐个弹出节点并削减其下游入度；
   - 若最终输出节点数小于总节点数，则判定图中存在环。
4. DAG 检测、拓扑排序和起始节点识别以同一套入度计算结果为准，不额外要求引入 Tarjan 等强连通分量算法。
5. `orderedNodeIds`、`levelGroups`、`startNodeIds`、`terminalNodeIds` 均应由上述编译结果一次性生成。

### 5.2 执行语义

1. 默认 `at-least-once`
2. 节点支持重试
3. 失败默认终止流程
4. 支持有限并发
5. 补偿策略在一期只做声明与审计，不自动执行补偿编排

一期简化版执行分发约定：

1. `log`、`variable`、`condition` 视为轻量 CPU 节点，由当前 DAG 推进线程内联执行，避免无意义线程切换。
2. `http`、`dbQuery`、`dbUpdate` 视为 I/O 节点，交由引擎执行器异步执行，执行载体默认采用虚拟线程。
3. `dbQuery`、`dbUpdate` 一期先不引入专用平台线程池隔离；若后续验证 JDBC 驱动或资源访问存在明显 pinning/阻塞风险，再演进为专用执行池。
4. 一期不引入独立的流程级事件总线；节点完成后直接回到调度推进逻辑，由引擎计算新的就绪节点。
5. 对 `http`、`dbQuery`、`dbUpdate` 这类依赖外部资源的节点，执行前应先通过 `resourceRef` 级并发许可校验，用于保护下游系统容量。
6. 一期不在正式文档中固化 JDBC 驱动黑名单；若目标驱动在压测中表现出明显虚拟线程 pinning 风险，可将 `dbQuery`、`dbUpdate` 回退为平台线程池执行，这属于实现层兼容策略。

### 5.3 运行上下文

运行时上下文由以下稳定命名空间组成：

1. `request`
2. `schedule`
3. `vars`
4. `instance`
5. `nodes`

补充约定：

1. 节点级表达式只使用上述稳定命名空间。
2. 当前节点执行器返回的原始结果只以局部 `raw` 暴露给本节点 `outputMapping`。
3. 流程最终输出通过显式 `flowOutputMapping` 计算，并以 `flow.output` 形式暴露给响应映射阶段。

详细执行语义见：

- [runtime-semantics.md](./runtime-semantics.md)
- [error-codes.md](./error-codes.md)

## 6. 模块设计

一期模块骨架采用如下结构：

```text
fluxion-parent/
├── fluxion-dependencies
├── fluxion-common
├── fluxion-model
├── fluxion-spi
├── fluxion-modeler
├── fluxion-engine
├── fluxion-persistence-mybatisplus
├── fluxion-scheduler
├── fluxion-runtime-api
├── fluxion-admin-api
├── fluxion-expression-spel
├── fluxion-node-control
├── fluxion-node-http
├── fluxion-node-database
├── fluxion-spring-boot-starter
├── fluxion-server
└── fluxion-test
```

模块职责如下：

1. `fluxion-model`
   定义流程、实例、执行状态等领域对象。
2. `fluxion-spi`
   定义 NodeRunner、Repository、ExpressionEvaluator 等扩展接口。
3. `fluxion-modeler`
   负责画布 JSON 编译和模型校验。
4. `fluxion-engine`
   负责运行时调度与执行。
5. `fluxion-persistence-mybatisplus`
   负责数据库持久化实现。
6. `fluxion-runtime-api`
   负责运行时触发接口。
7. `fluxion-admin-api`
   负责流程管理、发布管理、监控接口。
8. `fluxion-spring-boot-starter`
   负责核心能力自动装配；默认不自启独立端口，Web/API 与前端页面能力可按宿主应用需要启用。
9. `fluxion-server`
   依赖 `fluxion-spring-boot-starter`，提供开箱即用的独立部署与独立进程启动，默认启用 Web/API 与前端页面能力。

## 7. 一期前端设计器方案

### 7.1 最小页面清单

一期前端设计器最小交付页面建议如下：

1. 流程列表页
   用于查询、创建、编辑、删除流程定义。
2. 流程编辑页
   包含流程基础信息、画布区域、节点面板、属性面板。
3. 节点配置面板
   用于编辑节点参数、输入映射和输出映射等配置。
4. 发布面板
   用于执行模型校验、填写版本说明、发布流程版本。
5. 流程实例列表页
   用于查询流程实例、状态、耗时、触发方式。
6. 节点执行详情页
   用于查看节点执行状态、输入输出快照、错误信息、重试明细；对 fail-fast 后模型中存在但无执行记录的节点，可派生展示 `NOT_SCHEDULED` / `ABORTED_BY_FAIL_FAST`。
7. 资源管理页
   用于维护数据库连接、HTTP 认证信息等资源配置。
8. HTTP 发布管理页
   用于维护流程发布接口、上下线、查看路由配置。
9. 定时任务管理页
   用于维护 Cron 任务、启停任务、查看最近执行情况。

### 7.2 页面职责边界

1. 流程编辑页只负责设计态编辑，不直接承载运行态监控逻辑。
2. 运行监控相关页面只读展示执行结果，不允许直接修改已发布版本。
3. 资源管理页负责统一维护连接信息，流程配置中只引用资源标识，不直接保存敏感凭证。
4. 一期前端不实现插件动态加载、多人协作编辑和复杂调试能力。
5. 监控页允许在不改变数据库正式状态的前提下，基于 `model_json` 与执行记录派生展示态；例如对 fail-fast 后无执行记录的节点展示 `NOT_SCHEDULED` / `ABORTED_BY_FAIL_FAST`。

### 7.2.1 表达式与映射可用性补偿设计

为降低显式命名空间和显式流程输出带来的使用门槛，一期前端设计器补充以下能力：

1. 表达式编辑器提供按命名空间分组的自动补全，至少覆盖 `request`、`schedule`、`vars`、`instance`、`nodes`，以及当前节点 `outputMapping` 场景下的局部 `raw`。
2. 节点引用选择器用于插入标准表达式片段；在节点级编辑场景只展示当前节点拓扑上可稳定引用的上游节点，在流程输出配置面板展示全流程节点。
3. 为每种内置节点提供默认 `outputMapping` 模板，减少用户从零书写表达式的成本。
4. 为流程级 `flow.outputMapping` 提供默认模板向导，优先支持“单终点节点输出模板”“变量汇总模板”“空对象模板”三种起始方案。
5. 表达式编辑器在保存前提供静态校验与预览提示，但最终发布校验仍以后端编译结果为准。
6. 调度触发流水列表默认只展示 `summaryStatus`，对外 UI 不直接暴露全部内部状态；详情页或详情抽屉再展开 `dispatchStatus`、`waitStatus`、`instanceStatusSnapshot`。

### 7.3 接口清单

前端最小页面建议对应以下后端接口组。

#### 7.3.1 流程定义与版本接口

1. `GET /admin/flows`
   查询流程列表。
2. `POST /admin/flows`
   创建流程定义。
3. `GET /admin/flows/{flowId}`
   查询流程定义详情。
4. `PUT /admin/flows/{flowId}`
   更新流程基础信息。
5. `DELETE /admin/flows/{flowId}`
   删除流程定义。
6. `GET /admin/flows/{flowId}/draft`
   查询当前草稿。
7. `PUT /admin/flows/{flowId}/draft`
   保存当前草稿内容。
8. `POST /admin/flows/{flowId}/validate`
   对当前草稿执行模型校验。
9. `POST /admin/flows/{flowId}/publish`
   发布流程版本。
10. `GET /admin/flows/{flowId}/versions`
    查询正式版本列表。
11. `GET /admin/flows/{flowId}/versions/{versionId}`
    查询指定正式版本详情。

#### 7.3.2 流程实例与执行记录接口

1. `GET /admin/instances`
   查询流程实例列表。
2. `GET /admin/instances/{instanceId}`
   查询流程实例详情。
3. `GET /admin/instances/{instanceId}/executions`
   查询节点执行记录列表。
4. `GET /admin/executions/{executionId}`
   查询节点执行详情。
5. `GET /admin/executions/{executionId}/attempts`
   查询节点重试尝试明细。

#### 7.3.3 资源管理接口

1. `GET /admin/resources`
   查询资源列表。
2. `POST /admin/resources`
   创建资源。
3. `GET /admin/resources/{resourceId}`
   查询资源详情。
4. `PUT /admin/resources/{resourceId}`
   更新资源。
5. `DELETE /admin/resources/{resourceId}`
   删除资源。
6. `POST /admin/resources/{resourceId}/test`
   测试资源连通性。

#### 7.3.4 认证凭证管理接口

1. `GET /admin/auth-credentials`
   查询认证凭证列表。
2. `POST /admin/auth-credentials`
   创建认证凭证。
3. `GET /admin/auth-credentials/{credentialId}`
   查询认证凭证详情。
4. `PUT /admin/auth-credentials/{credentialId}`
   更新认证凭证。
5. `POST /admin/auth-credentials/{credentialId}/disable`
   禁用认证凭证。

#### 7.3.5 HTTP 发布管理接口

1. `GET /admin/endpoints`
   查询已发布 HTTP 接口列表。
2. `POST /admin/endpoints`
   创建 HTTP 发布配置。
3. `GET /admin/endpoints/{endpointId}`
   查询 HTTP 发布配置详情。
4. `PUT /admin/endpoints/{endpointId}`
   更新 HTTP 发布配置。
5. `POST /admin/endpoints/{endpointId}/online`
   上线接口。
6. `POST /admin/endpoints/{endpointId}/offline`
   下线接口。

#### 7.3.6 定时任务管理接口

1. `GET /admin/schedules`
   查询定时任务列表。
2. `POST /admin/schedules`
   创建定时任务。
3. `GET /admin/schedules/{jobId}`
   查询定时任务详情。
4. `PUT /admin/schedules/{jobId}`
   更新定时任务。
5. `POST /admin/schedules/{jobId}/pause`
   暂停任务。
6. `POST /admin/schedules/{jobId}/resume`
   恢复任务。
7. `GET /admin/schedules/{jobId}/triggers`
   查询触发流水。
8. `GET /admin/schedules/{jobId}/triggers/{triggerId}`
   查询单条触发流水详情。

### 7.4 前后端协作约定

1. 前端保存的是设计态画布 JSON，发布时后端负责编译为执行模型。
2. 前端保存的草稿不占正式版本号，发布时后端才分配新的正式版本号。
3. 前端画布节点 `nodeType` 与后端节点实现采用统一标识。
4. 前端配置表单与后端节点参数协议采用 Schema 驱动，避免双端重复硬编码。
5. 一期前端只依赖稳定的 Admin API，不直接访问底层运行时模块。
6. 表达式补全、节点引用选择器和默认模板由“稳定命名空间定义 + 当前草稿拓扑 + 节点类型默认模板”共同驱动，一期允许前端本地实现，不强依赖额外后端接口。

### 7.5 页面与接口映射表

| 页面 | 主要接口 |
| --- | --- |
| 流程列表页 | `GET /admin/flows` `POST /admin/flows` `DELETE /admin/flows/{flowId}` |
| 流程编辑页 | `GET /admin/flows/{flowId}` `GET /admin/flows/{flowId}/draft` `PUT /admin/flows/{flowId}/draft` |
| 发布面板 | `POST /admin/flows/{flowId}/validate` `POST /admin/flows/{flowId}/publish` `GET /admin/flows/{flowId}/versions` |
| 流程实例列表页 | `GET /admin/instances` `GET /admin/instances/{instanceId}` |
| 节点执行详情页 | `GET /admin/instances/{instanceId}/executions` `GET /admin/executions/{executionId}` `GET /admin/executions/{executionId}/attempts` |
| 资源管理页 | `GET /admin/resources` `POST /admin/resources` `PUT /admin/resources/{resourceId}` `POST /admin/resources/{resourceId}/test` |
| HTTP 发布管理页 | `GET /admin/endpoints` `POST /admin/endpoints` `PUT /admin/endpoints/{endpointId}` `POST /admin/endpoints/{endpointId}/online` |
| 定时任务管理页 | `GET /admin/schedules` `POST /admin/schedules` `PUT /admin/schedules/{jobId}` `POST /admin/schedules/{jobId}/pause` `POST /admin/schedules/{jobId}/resume` `GET /admin/schedules/{jobId}/triggers` `GET /admin/schedules/{jobId}/triggers/{triggerId}` |

### 7.6 请求与响应草案

本节仅保留联调视角下的摘要信息，不再在 `technical-solution.md` 中重复维护字段级请求体、响应体和错误对象定义。

正式协议请分别以下列文档为准：

1. 统一响应结构、HTTP `200` 约定、默认时区：见 [../base.md](../base.md)
2. HTTP 发布请求 / 响应映射、默认成功返回结构：见 [http-endpoint-contract.md](./http-endpoint-contract.md)
3. 调度任务配置、`timezone`、`misfirePolicy`、触发流水结构：见 [schedule-contract.md](./schedule-contract.md)
4. 资源管理与测试接口：见 [resource-contract.md](./resource-contract.md)
5. 认证凭证接口：见 [auth-credential-contract.md](./auth-credential-contract.md)
6. 流程发布校验错误对象与 `model_json` 编译错误归类：见 [model-json-contract.md](./model-json-contract.md) 与 [error-codes.md](./error-codes.md)

前端页面与接口关系以本文件 `7.3 接口清单`、`7.5 页面与接口映射表` 为摘要入口；若后续新增正式 Admin API 契约文档，应以新契约文档替代此处的联调说明。

### 7.7 画布 JSON 最小协议定义

一期画布 JSON 建议采用如下最小结构：

```json
{
  "dslVersion": "1.0",
  "flow": {
    "flowCode": "user_sync",
    "flowName": "用户同步",
    "description": "同步外部用户数据",
    "category": "integration",
    "outputMapping": {
      "result": "${vars.userId}"
    }
  },
  "variables": [
    {
      "name": "userId",
      "type": "string",
      "defaultValue": null
    }
  ],
  "nodes": [],
  "edges": []
}
```

#### 7.7.1 节点对象

节点最小字段建议：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `nodeId` | string | 是 | 画布内唯一标识 |
| `nodeType` | string | 是 | 节点类型，如 `http`、`dbQuery` |
| `nodeName` | string | 是 | 节点显示名称 |
| `position` | object | 是 | 节点画布坐标 |
| `config` | object | 是 | 节点配置 |
| `inputMapping` | object | 否 | 当前节点执行输入映射，表达式可访问 `request` / `schedule` / `vars` / `instance` / `nodes` |
| `outputMapping` | object | 否 | 当前节点稳定输出映射，表达式可访问稳定命名空间加当前节点局部 `raw` |

节点示例：

```json
{
  "nodeId": "node_http_1",
  "nodeType": "http",
  "nodeName": "查询用户接口",
  "position": {
    "x": 240,
    "y": 120
  },
  "config": {
    "resourceRef": "user_service",
    "path": "/users/{id}",
    "method": "GET"
  },
  "inputMapping": {
    "path.id": "${vars.userId}"
  },
  "outputMapping": {
    "statusCode": "${raw.statusCode}",
    "body": "${raw.body}"
  }
}
```

#### 7.7.2 连线对象

连线最小字段建议：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `edgeId` | string | 是 | 连线唯一标识 |
| `sourceNodeId` | string | 是 | 起始节点 |
| `sourcePort` | string | 否 | 起始端口 |
| `targetNodeId` | string | 是 | 目标节点 |
| `targetPort` | string | 否 | 目标端口 |
| `condition` | object | 否 | 分支条件，仅条件节点输出边使用 |

连线示例：

```json
{
  "edgeId": "edge_1",
  "sourceNodeId": "node_condition_1",
  "sourcePort": "true",
  "targetNodeId": "node_http_1",
  "targetPort": "in"
}
```

#### 7.7.3 编译约定

1. `graph_json` 由前端保存到草稿，面向设计态。
2. `model_json` 由后端在发布时编译生成，面向运行态。
3. 正式 `version_num` 只在发布成功时分配，草稿阶段不占号。
4. 编译时会补齐拓扑顺序、节点依赖关系、运行时策略快照。
5. 一期禁止前端直接提交 `model_json`。
6. 发布时必须将设计态 `flow.outputMapping` 编译为运行态 `flowOutputMapping`。
7. 节点 `outputMapping` 的产物是当前节点稳定输出，不直接写入 `vars`；共享变量写入通过显式 `variable` 节点完成。

#### 7.7.4 流程最终输出定义

设计态在 `flow.outputMapping` 中显式定义流程最终输出。例如：

```json
{
  "result": "${nodes.node_http_1.output.body}",
  "traceId": "${instance.traceId}"
}
```

规则如下：

1. `flow.outputMapping` 是流程最终输出的唯一正式来源。
2. `flow.outputMapping` 表达式只能访问 `request`、`schedule`、`vars`、`instance`、`nodes`。
3. `flow.outputMapping` 在草稿阶段可为空，但发布时必须补齐。
4. 运行时实例成功结束后，`flow.outputMapping` 的求值结果写入实例最终输出。

### 7.8 Admin API 契约草案

本节不再重复维护字段级 Admin API 协议。当前联调入口以 `7.3 接口清单` 和 `7.5 页面与接口映射表` 为摘要索引；若后续新增正式 `admin-api-contract.md`，则该文档成为唯一事实源。

### 7.9 Runtime API 契约草案

本节仅保留运行时接口的设计意图，不再重复维护请求体、响应体和错误示例。

运行时正式协议以以下文档为准：

- [http-endpoint-contract.md](./http-endpoint-contract.md)
- [runtime-semantics.md](./runtime-semantics.md)
- [error-codes.md](./error-codes.md)

当前技术决策仅保留以下摘要：

1. 运行时路径由已上线端点的 `path + method` 动态决定。
2. 同步模式未配置 `successDataMapping` 时，默认在统一响应包 `data` 中返回 `instanceId`、`status`、`result`。
3. 异步模式未配置 `runningDataMapping` 时，默认在统一响应包 `data` 中返回 `instanceId`、`status`、`queryUrl`。
4. 结果查询接口固定为 `GET /runtime/instances/{instanceId}/result`。
5. 流程失败摘要优先读取实例主表中的 `error_code`、`error_message`；详细错误上下文读取 `error_detail`。

### 7.10 节点参数 Schema 规范

本节仅保留节点 Schema 的设计原则；字段级定义、节点级业务字段、默认模板和兼容规则统一以 [node-schemas.md](./node-schemas.md) 为准。

一期节点 Schema 的设计原则如下：

1. 同一份 Schema 同时驱动前端表单与后端二次校验。
2. 节点表达式能力、稳定命名空间和 `raw` 的使用边界以 `node-schemas.md` 为唯一来源。
3. 节点运行策略在发布时编译为运行态 `runtimePolicy`，不在前端画布协议中重复推导。
4. 外部副作用节点的副作用策略声明在设计态强制要求，运行态固化为 `sideEffectPolicy`。

### 7.11 `model_json` 最小运行时结构定义

本节只保留 `model_json` 的角色定位，不再重复维护正式字段表、示例结构和编译错误对象。

`model_json` 的正式契约以以下文档为准：

- [model-json-contract.md](./model-json-contract.md)
- [model-json.schema.json](./model-json.schema.json)

当前技术决策摘要如下：

1. `model_json` 是发布后生成的不可变运行时快照，引擎只消费该模型执行。
2. `model_json` 必须可独立执行，不依赖前端画布坐标、样式或其他展示态信息。
3. 编译阶段负责补齐拓扑、上下游关系、运行策略快照、资源引用快照和已归一化节点参数。
4. 结构校验与语义校验采用“两层契约”模式：JSON Schema 负责结构，编译器负责 DAG、拓扑、引用和表达式作用域。

### 7.12 SQL 参数规范

一期数据库节点只支持命名参数占位，统一采用 `:paramName` 形式。

1. SQL 中只允许使用命名参数，如 `:userId`、`:status`。
2. `params` 配置中的 `key` 必须与 SQL 中的占位名一致，但不包含前缀冒号。
3. 一期不支持 JDBC `?` 占位。
4. 一期不支持 MyBatis 风格的 `#{}` 或 `${}` 占位。
5. 所有动态值都必须通过 `params` 传入，不允许直接拼接到 SQL 字符串。
6. 同名参数可在同一条 SQL 中重复引用，运行时按同一个参数值绑定。

示例：

```json
{
  "sql": "update sys_user set status = :status where id = :userId",
  "params": [
    {
      "key": "status",
      "value": "ACTIVE"
    },
    {
      "key": "userId",
      "value": "${vars.userId}"
    }
  ]
}
```

## 8. 数据库方案

一期数据库方案采用“设计态与运行态分层”的原则。

### 8.1 设计态

1. 流程定义表
2. 流程草稿表
3. 流程版本表

### 8.2 运行态

1. 流程实例表
2. 流程实例大字段表
3. 节点执行表
4. 节点执行大字段表
5. 节点执行尝试明细表

落库约定：

1. `flx_node_execution` 记录所有已经形成调度结论的节点，包含 `SUCCESS`、`FAILED`、`SKIPPED` 等终态。
2. `SKIPPED` 只用于“分支未命中或全部有效上游路径失效”后已明确不会执行的节点，不用于 fail-fast 后尚未进入调度判定的节点。
3. `flx_node_execution_attempt` 只记录真实执行尝试；未真实执行的 `SKIPPED` 节点不写入 attempt 记录。
4. `attempt_count` 表示真实执行尝试次数；对 `SKIPPED` 节点固定为 `0`。
5. `skip_reason` 用于记录节点跳过原因，便于监控页和执行详情页展示。
6. 对 fail-fast 后尚未形成调度结论、因此未落 `flx_node_execution` 的节点，一期不新增持久化状态；监控页可基于 `model_json` 与执行记录派生展示 `NOT_SCHEDULED` / `ABORTED_BY_FAIL_FAST`。
7. 节点终态写入时，`flx_node_execution` 与 `flx_node_execution_data` 应保持本地事务一致性。
8. 节点输出在 `outputMapping` 求值成功后先写入运行时上下文，再在节点终态事务中落入 `flx_node_execution_data.output_data`。
9. `flx_flow_instance` 保存实例级错误摘要字段：`error_code`、`error_message`；用于结果查询、实例列表和调度回填等轻量读取场景。
10. `flx_flow_instance_data.error_detail` 保存详细错误说明、长文本、异常堆栈和补充上下文。
11. 流程最终输出在 `flowOutputMapping` 成功求值后写入 `flx_flow_instance_data.output_data`；若求值失败，实例按 `FAILED` 处理，并写入实例级 `error_code = FLOW_OUTPUT_EVAL_FAILED` 与对应 `error_message`。

### 8.3 暴露与调度

1. HTTP 发布表
2. 调度任务表
3. 调度触发流水表

### 8.4 治理

1. 资源连接表
2. 资源密钥表
3. 认证凭证表
4. 认证凭证密钥表
5. 操作审计表

## 9. 安全与治理

### 9.1 资源与密钥

1. 非敏感配置写入 `flx_resource`
2. 敏感配置密文写入 `flx_resource_secret`
3. 前端永不回显明文
4. 认证凭证敏感配置密文写入 `flx_auth_credential_secret`

详细资源契约见：

- [resource-contract.md](./resource-contract.md)
- [auth-credential-contract.md](./auth-credential-contract.md)


### 9.3 审计

以下操作必须审计：

1. 流程创建、修改、发布、归档
2. HTTP 接口启停
3. 调度任务启停
4. 资源连接变更
5. 认证凭证变更

## 10. 技术风险与控制

### 10.1 风险

1. 执行模型与画布模型耦合过深
2. 运行日志大字段增长过快
3. 虚拟线程被误用到 CPU 密集任务
4. 业务系统直接把密钥写进流程配置

### 10.2 控制策略

1. 发布时生成独立执行模型
2. 日志保留周期与归档策略前置设计
3. 节点级超时与并发限制前置实现
4. 资源与密钥表作为唯一连接配置入口

## 11. 交付建议

建议按以下顺序实施：

1. 先完成模型、引擎、持久化
2. 再完成运行时接口与管理接口
3. 最后完成 Starter 自动装配，以及基于 Starter 的独立 Server 运行形态封装
