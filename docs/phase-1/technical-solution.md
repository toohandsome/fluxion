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
   负责把引擎能力装配进业务系统，本身不作为独立服务启动，也不单独提供独立监听端口。
3. `Server`
   基于 `Starter` 底座能力额外聚合 Web/API，负责提供独立平台所需的管理和运行时接口。

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

### 4.3 调度与 HTTP

1. 一期采用单机 Quartz 调度。
2. 调度重入策略只支持 `FORBID` 和 `ALLOW`。
3. 任务并发、等待超时、错失策略通过任务表字段配置，其中 `waitTimeoutMs` 表示调度侧等待实例进入终态的最长时间。
4. 调度触发流水拆分为 `dispatchStatus`、`waitStatus`、`instanceStatusSnapshot` 三层内部状态，对外列表默认只展示 `summaryStatus`。
5. `CATCH_UP_BOUNDED` 为一期唯一推荐的补跑追赶策略，默认采用“最近 1 小时、最多 10 次、最老优先”。
6. 一期默认提供开放、AppKey、Bearer Token、basic auth 四种基础认证方式。（一期只实现basic auth）

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

## 5. 执行模型

### 5.1 模型约束

1. 主流程必须是 DAG
2. 连接关系必须通过编译期校验
3. 所有节点在忽略边方向后必须属于同一个连通分量
4. 所有节点都必须同时满足“起始节点可达”和“结束节点可达”
5. 发布时固定版本快照
6. 运行时只执行 `model_json`

### 5.2 执行语义

1. 默认 `at-least-once`
2. 节点支持重试
3. 失败默认终止流程
4. 支持有限并发
5. 补偿策略在一期只做声明与审计，不自动执行补偿编排

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
   负责核心能力自动装配，不单独提供独立监听端口。
9. `fluxion-server`
   依赖 `fluxion-spring-boot-starter`，额外聚合 Web/API 能力并负责独立服务启动。

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
   用于查看节点执行状态、输入输出快照、错误信息、重试明细。
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

#### 7.6.1 统一接口响应格式

一期管理接口与运行时接口统一采用如下响应结构：

```json
{
  "code": "OK",
  "message": "success",
  "requestId": "9f8d6f9f0d034f4d",
  "data": {}
}
```

补充约束：

1. 一期所有接口固定返回 HTTP `200`
2. HTTP 状态码不表达业务语义
3. `code` 统一采用字符串业务状态码
4. 所有接口都必须返回 `requestId`

分页接口统一采用如下 `data` 结构：

```json
{
  "items": [],
  "pageNo": 1,
  "pageSize": 20,
  "total": 100
}
```

#### 7.6.2 流程列表查询

`GET /admin/flows`

请求参数建议：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | 否 | 按编码或名称模糊搜索 |
| `category` | string | 否 | 分类 |
| `pageNo` | int | 否 | 页码，默认 1 |
| `pageSize` | int | 否 | 分页大小，默认 20 |

响应项建议：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | long | 流程定义 ID |
| `flowCode` | string | 流程编码 |
| `flowName` | string | 流程名称 |
| `category` | string | 分类 |
| `activeVersionId` | long | 当前生效版本 ID |
| `latestVersionNum` | int | 最新版本号 |
| `updateTime` | datetime | 更新时间 |

#### 7.6.3 草稿保存

`PUT /admin/flows/{flowId}/draft`

请求体建议：

```json
{
  "graphJson": {
    "dslVersion": "1.0",
    "flow": {
      "flowCode": "user_sync",
      "flowName": "用户同步",
      "outputMapping": {
        "result": "${vars.userId}"
      }
    },
    "variables": [],
    "nodes": [],
    "edges": []
  },
  "remark": "保存草稿"
}
```

响应体建议：

```json
{
  "draftId": 9001,
  "draftRevision": 12,
  "baseVersionId": null,
  "updateTime": "2026-03-08 10:00:00.000"
}
```

#### 7.6.4 发布校验

`POST /admin/flows/{flowId}/validate`

响应体建议：

```json
{
  "passed": false,
  "errors": [
    {
      "errorCode": "UNREACHABLE_NODE",
      "stage": "STRUCTURE_VALIDATE",
      "nodeId": "node_http_2",
      "field": "edges",
      "message": "node is unreachable from any start node",
      "severity": "ERROR"
    }
  ],
  "warnings": []
}
```

错误项建议字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `errorCode` | string | 编译或校验错误码 |
| `stage` | string | `STRUCTURE_VALIDATE` / `SCHEMA_VALIDATE` / `MODEL_COMPILE` |
| `nodeId` | string | 关联节点 ID |
| `field` | string | 关联字段 |
| `message` | string | 错误信息 |
| `severity` | string | `ERROR` / `WARN` |

补充约束：

1. `errors` 非空时不得发布
2. `warnings` 非空但 `errors` 为空时允许继续发布
3. 一期 `validate` 和 `publish` 失败时使用同一套编译错误结构

#### 7.6.5 发布流程版本

`POST /admin/flows/{flowId}/publish`

请求体建议：

```json
{
  "remark": "一期首版发布"
}
```

响应体建议：

```json
{
  "versionId": 1002,
  "versionNum": 1,
  "status": "PUBLISHED",
  "publishedTime": "2026-03-08 10:05:00.000"
}
```

#### 7.6.6 流程实例列表

`GET /admin/instances`

请求参数建议：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `flowCode` | string | 否 | 流程编码 |
| `status` | string | 否 | 实例状态 |
| `triggerType` | string | 否 | 触发方式 |
| `businessKey` | string | 否 | 业务键 |
| `startTimeFrom` | datetime | 否 | 开始时间下限 |
| `startTimeTo` | datetime | 否 | 开始时间上限 |
| `pageNo` | int | 否 | 页码 |
| `pageSize` | int | 否 | 分页大小 |

响应项建议：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | long | 实例 ID |
| `flowCode` | string | 流程编码 |
| `flowName` | string | 流程名称 |
| `status` | string | 实例状态 |
| `triggerType` | string | 触发方式 |
| `businessKey` | string | 业务键 |
| `startTime` | datetime | 开始时间 |
| `endTime` | datetime | 结束时间 |
| `durationMs` | long | 总耗时 |

#### 7.6.7 资源管理

`POST /admin/resources`

请求体建议：

```json
{
  "resourceCode": "main_db",
  "resourceName": "主数据库",
  "resourceType": "DB",
  "config": {
    "jdbcUrl": "jdbc:postgresql://localhost:5432/yourdatabase",
    "username": "app"
  },
  "secret": {
    "password": "******"
  }
}
```

响应体建议：

```json
{
  "resourceId": 2001,
  "status": "ENABLED",
  "testStatus": "UNTESTED"
}
```

#### 7.6.8 HTTP 发布配置

`POST /admin/endpoints`

请求体建议：

```json
{
  "endpointCode": "user_sync_api",
  "flowDefId": 100,
  "versionPolicy": "LATEST",
  "path": "/runtime/openapi/user/sync",
  "method": "POST",
  "authType": "BASIC_AUTH",
  "syncMode": true,
  "timeoutMs": 5000
}
```

#### 7.6.9 定时任务配置

`POST /admin/schedules`

请求体建议：

```json
{
  "jobCode": "user_sync_job",
  "jobName": "用户同步任务",
  "flowDefId": 100,
  "versionPolicy": "LATEST",
  "cronExpression": "0 0/5 * * * ?",
  "timezone": "Asia/Shanghai",
  "misfirePolicy": "CATCH_UP_BOUNDED",
  "catchUpConfig": {
    "maxCatchUpCount": 10,
    "maxCatchUpWindowSeconds": 3600,
    "order": "OLDEST_FIRST"
  },
  "jobParams": {
    "source": "scheduler"
  },
  "maxConcurrency": 1,
  "reentryPolicy": "FORBID",
  "waitTimeoutMs": 10000
}
```

详细调度契约见：

- [schedule-contract.md](./schedule-contract.md)

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

本节用于约束一期前后端联调时的接口契约风格，形式参考 OpenAPI，但先以文档草案方式固化。

#### 7.8.1 通用约定

1. Base Path: `/admin`
2. Content-Type: `application/json`
3. 字符集统一为 `UTF-8`
4. 一期接口统一响应包结构，错误时仍返回统一包体
5. 鉴权信息通过请求头传递，支持 `Authorization: Bearer xxx` 或 `X-App-Key`

通用请求头建议：

| Header | 必填 | 说明 |
| --- | --- | --- |
| `X-Request-Id` | 否 | 调用方请求 ID，便于链路追踪 |
| `Authorization` | 否 | Bearer Token 模式 |
| `X-App-Key` | 否 | AppKey 模式 |

#### 7.8.2 流程管理接口摘要

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/admin/flows` | 查询流程列表 |
| `POST` | `/admin/flows` | 创建流程 |
| `GET` | `/admin/flows/{flowId}` | 查询流程详情 |
| `PUT` | `/admin/flows/{flowId}` | 更新流程基础信息 |
| `DELETE` | `/admin/flows/{flowId}` | 逻辑删除流程 |
| `GET` | `/admin/flows/{flowId}/draft` | 查询草稿 |
| `PUT` | `/admin/flows/{flowId}/draft` | 保存草稿 |
| `POST` | `/admin/flows/{flowId}/validate` | 校验草稿 |
| `POST` | `/admin/flows/{flowId}/publish` | 发布版本 |
| `GET` | `/admin/flows/{flowId}/versions` | 查询正式版本列表 |

#### 7.8.3 运行监控接口摘要

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/admin/instances` | 查询流程实例 |
| `GET` | `/admin/instances/{instanceId}` | 查询实例详情 |
| `GET` | `/admin/instances/{instanceId}/executions` | 查询节点执行记录 |
| `GET` | `/admin/executions/{executionId}` | 查询节点执行详情 |
| `GET` | `/admin/executions/{executionId}/attempts` | 查询节点重试明细 |

#### 7.8.4 资源与发布管理接口摘要

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/admin/resources` | 查询资源列表 |
| `POST` | `/admin/resources` | 创建资源 |
| `PUT` | `/admin/resources/{resourceId}` | 更新资源 |
| `POST` | `/admin/resources/{resourceId}/test` | 测试资源 |
| `GET` | `/admin/endpoints` | 查询 HTTP 发布列表 |
| `POST` | `/admin/endpoints` | 创建 HTTP 发布配置 |
| `POST` | `/admin/endpoints/{endpointId}/online` | 上线接口 |
| `POST` | `/admin/endpoints/{endpointId}/offline` | 下线接口 |
| `GET` | `/admin/schedules` | 查询任务列表 |
| `POST` | `/admin/schedules` | 创建任务 |
| `POST` | `/admin/schedules/{jobId}/pause` | 暂停任务 |
| `POST` | `/admin/schedules/{jobId}/resume` | 恢复任务 |
| `GET` | `/admin/schedules/{jobId}/triggers` | 查询触发流水列表 |
| `GET` | `/admin/schedules/{jobId}/triggers/{triggerId}` | 查询触发流水详情 |



### 7.9 Runtime API 契约草案

本节用于约束一期发布后的业务 HTTP 接口契约。运行时接口与管理接口沿用同一套响应结构，HTTP 状态码固定返回 `200`，业务结果统一通过 `code` 和 `data` 表达。

详细契约见：

- [http-endpoint-contract.md](./http-endpoint-contract.md)
- [runtime-semantics.md](./runtime-semantics.md)

#### 7.9.1 同步触发接口

1. 运行时路径由已上线的 `flx_http_endpoint.path` 和 `method` 动态决定。
2. 当端点配置为同步模式时，请求进入运行时分发器后直接触发流程执行。
3. 执行成功时，在统一响应包的 `data` 中返回流程输出结果。
4. 若同步等待超时，则返回 `SYNC_TIMEOUT`，并在 `data` 中返回 `instanceId` 和结果查询地址。
5. 同步模式适合短流程和低时延调用场景。

同步触发示例：

```http
POST /runtime/openapi/user/sync
Content-Type: application/json

{
  "userId": 1001
}
```

成功返回示例：

```json
{
  "code": "OK",
  "message": "success",
  "requestId": "9f8d6f9f0d034f4d",
  "data": {
    "instanceId": 202603080001,
    "status": "SUCCESS",
    "result": {
      "userId": 1001,
      "status": "SYNCED"
    }
  }
}
```

#### 7.9.2 异步触发接口

1. 当端点配置为异步模式时，运行时接口只负责创建实例并快速返回。
2. 接口固定返回 HTTP `200`，业务状态码使用 `ACCEPTED`。
3. 响应体在 `data` 中返回 `instanceId`、`status` 和结果查询地址。
4. 异步模式适合长耗时流程、外部依赖较多或需要削峰的场景。

异步返回示例：

```json
{
  "code": "ACCEPTED",
  "message": "accepted",
  "requestId": "9f8d6f9f0d034f4d",
  "data": {
    "instanceId": 202603080001,
    "status": "RUNNING",
    "queryUrl": "/runtime/instances/202603080001/result"
  }
}
```

#### 7.9.3 实例结果查询接口

1. 一期固定提供 `GET /runtime/instances/{instanceId}/result` 用于查询异步实例结果。
2. 当实例仍在运行时，返回当前状态和必要的进度摘要。
3. 当实例成功结束时，返回最终输出结果。
4. 当实例失败时，返回失败状态、错误码和简要错误信息。

运行中返回示例：

```json
{
  "code": "INSTANCE_RUNNING",
  "message": "instance still running",
  "requestId": "9f8d6f9f0d034f4d",
  "data": {
    "instanceId": 202603080001,
    "status": "RUNNING"
  }
}
```

完成返回示例：

```json
{
  "code": "OK",
  "message": "success",
  "requestId": "9f8d6f9f0d034f4d",
  "data": {
    "instanceId": 202603080001,
    "status": "SUCCESS",
    "result": {
      "userId": 1001,
      "status": "SYNCED"
    }
  }
}
```

#### 7.9.4 Runtime 错误语义

1. 参数错误返回统一响应包，`code = VALIDATION_ERROR`。
2. 鉴权失败返回统一响应包，`code = UNAUTHORIZED` 或 `FORBIDDEN`。
3. 端点不存在或未上线返回统一响应包，`code = ENDPOINT_NOT_FOUND` 或 `ENDPOINT_OFFLINE`。
4. 异步接口受理成功返回统一响应包，`code = ACCEPTED`。
5. 同步接口执行失败时返回统一响应包，`code = FLOW_FAILED` 或平台定义的固定业务错误码；响应格式始终为 JSON 包装。
6. 异步接口创建实例成功后，即使后续流程失败，也通过结果查询接口暴露失败状态，而不是回写首个触发请求。

### 7.10 节点参数 Schema 规范

一期节点配置协议建议采用统一 Schema 结构，既能驱动前端表单，也能驱动后端参数校验。

一期 6 个基础节点的详细 Schema 定义见：

- [一期节点 Schema 详细定义](./node-schemas.md)

#### 7.10.1 顶层结构

```json
{
  "schemaVersion": "1.0",
  "nodeType": "http",
  "displayName": "HTTP 请求节点",
  "description": "向外部 HTTP 服务发起请求",
  "fields": []
}
```

顶层字段建议：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `schemaVersion` | string | 是 | Schema 版本 |
| `nodeType` | string | 是 | 对应节点类型 |
| `displayName` | string | 是 | 前端显示名称 |
| `description` | string | 否 | 节点说明 |
| `fields` | array | 是 | 表单字段定义 |

#### 7.10.2 字段结构

单个字段建议结构：

```json
{
  "name": "method",
  "label": "请求方法",
  "type": "select",
  "required": true,
  "defaultValue": "GET",
  "options": ["GET", "POST", "PUT", "DELETE"],
  "description": "HTTP Method"
}
```

字段属性建议：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | string | 是 | 配置键名 |
| `label` | string | 是 | 表单标签 |
| `type` | string | 是 | 字段类型 |
| `required` | boolean | 否 | 是否必填 |
| `defaultValue` | any | 否 | 默认值 |
| `options` | array | 否 | 枚举项 |
| `placeholder` | string | 否 | 占位提示 |
| `description` | string | 否 | 说明 |
| `expressionSupported` | boolean | 否 | 是否支持表达式 |
| `resourceSelectable` | boolean | 否 | 是否支持选择资源 |
| `validation` | object | 否 | 校验规则 |

#### 7.10.3 一期支持的字段类型

一期建议支持以下表单类型：

| 类型 | 说明 |
| --- | --- |
| `string` | 单行文本 |
| `text` | 多行文本 |
| `number` | 数值 |
| `boolean` | 布尔值 |
| `select` | 下拉选择 |
| `json` | JSON 编辑器 |
| `expression` | 表达式输入 |
| `kvList` | 键值对列表 |
| `resourceRef` | 资源引用 |

#### 7.10.4 通用运行策略继承规则
 
平台默认运行策略如下：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `timeoutMs` | number | 节点超时时间 |
| `retry` | number | 重试次数 |
| `retryIntervalMs` | number | 重试间隔 |
| `logEnabled` | boolean | 是否记录节点输入输出日志 |
 

对于 `http` 和 `dbUpdate` 这类访问外部系统或数据源的节点，一期设计态还必须声明副作用策略字段，发布时编译为运行态 `sideEffectPolicy` 快照。建议最小结构如下：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `type` | string | `READ_ONLY` /  `COMPENSABLE` |
| `compensationStrategy` | string | `type = COMPENSABLE` 时必填，用于描述补偿方式、补偿入口或人工处置方案 |

补充规则：

1. `READ_ONLY` 表示节点不产生外部业务副作用。
2. `COMPENSABLE` 表示节点必须声明失败后的补偿策略；一期只记录声明与审计信息，不自动执行补偿。
3. 未声明副作用策略的外部副作用节点不得发布。

#### 7.10.5 HTTP 节点 Schema 示例

```json
{
  "schemaVersion": "1.0",
  "nodeType": "http",
  "displayName": "HTTP 请求节点",
  "fields": [
    {
      "name": "resourceRef",
      "label": "HTTP 资源",
      "type": "resourceRef",
      "required": true,
      "resourceSelectable": true
    },
    {
      "name": "method",
      "label": "请求方法",
      "type": "select",
      "required": true,
      "defaultValue": "GET",
      "options": ["GET", "POST", "PUT", "DELETE"]
    },
    {
      "name": "path",
      "label": "请求路径",
      "type": "string",
      "required": true,
      "expressionSupported": true
    }
  ]
}
```

#### 7.10.6 数据库查询节点 Schema 示例

```json
{
  "schemaVersion": "1.0",
  "nodeType": "dbQuery",
  "displayName": "数据库查询节点",
  "fields": [
    {
      "name": "resourceRef",
      "label": "数据源",
      "type": "resourceRef",
      "required": true
    },
    {
      "name": "sql",
      "label": "SQL",
      "type": "text",
      "required": true
    },
    {
      "name": "params",
      "label": "参数映射",
      "type": "kvList",
      "required": false
    }
  ]
}
```

### 7.11 `model_json` 最小运行时结构定义

`model_json` 是后端编译后的运行时模型，用于执行引擎直接消费。

#### 7.11.1 顶层结构

```json
{
  "modelVersion": "1.0",
  "flowDefId": 100,
  "flowVersionId": 1002,
  "flowCode": "user_sync",
  "flowName": "用户同步",
  "flowOutputMapping": {
    "result": "${nodes.node_http_1.output.body}"
  },
  "startNodeIds": ["node_variable_1"],
  "nodes": [],
  "edges": [],
  "topology": {
    "orderedNodeIds": ["node_variable_1", "node_condition_1", "node_http_1"],
    "levelGroups": [
      ["node_variable_1"],
      ["node_condition_1"],
      ["node_http_1"]
    ],
    "terminalNodeIds": ["node_http_1"]
  }
}
```

顶层字段建议：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `modelVersion` | string | 运行时模型版本 |
| `flowDefId` | long | 流程定义 ID |
| `flowVersionId` | long | 流程版本 ID |
| `flowCode` | string | 流程编码 |
| `flowName` | string | 流程名称 |
| `flowOutputMapping` | object | 编译后的流程最终输出映射 |
| `startNodeIds` | array | 起始节点 ID 列表，由编译器根据入度为 `0` 的节点生成 |
| `nodes` | array | 编译后的节点列表 |
| `edges` | array | 编译后的边列表 |
| `topology` | object | 拓扑和依赖信息 |

`topology` 建议最小字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `orderedNodeIds` | array | 全流程拓扑排序结果 |
| `levelGroups` | array | 可并行执行层级分组 |
| `terminalNodeIds` | array | 编译后识别出的结束节点列表 |

#### 7.11.2 运行时节点结构

运行时节点建议结构：

```json
{
  "nodeId": "node_http_1",
  "nodeType": "http",
  "nodeName": "同步用户接口",
  "incomingNodeIds": ["node_condition_1"],
  "outgoingNodeIds": ["node_db_1"],
  "config": {},
  "inputMapping": {},
  "outputMapping": {},
  "sideEffectPolicy": {
    "type": "COMPENSABLE",
    "compensationStrategy": "${vars.userId}"
  },
  "runtimePolicy": {
    "timeoutMs": 3000,
    "retry": 0,
    "retryIntervalMs": 1000,
    "logEnabled": true
  }
}
```

运行时节点最小字段建议：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `nodeId` | string | 节点 ID |
| `nodeType` | string | 节点类型 |
| `nodeName` | string | 节点名称 |
| `incomingNodeIds` | array | 上游节点列表 |
| `outgoingNodeIds` | array | 下游节点列表 |
| `config` | object | 已解析节点配置 |
| `inputMapping` | object | 输入映射 |
| `outputMapping` | object | 输出映射 |
| `sideEffectPolicy` | object | 外部副作用策略快照，非副作用节点可为空或省略 |
| `runtimePolicy` | object | 运行时策略快照 |

补充约束：

1. `incomingNodeIds` 和 `outgoingNodeIds` 是编译后的冗余快照，便于执行器快速判定依赖关系
2. 运行时节点结构不得再依赖前端画布坐标、样式或其他展示态字段
3. 对 `http` / `dbUpdate` 等外部副作用节点，`sideEffectPolicy` 必须存在并通过发布校验
4. `outputMapping` 的产物在节点成功后写入稳定上下文 `nodes.<nodeId>.output`
5. `outputMapping` 可以使用当前节点局部 `raw`，但 `raw` 不进入全局稳定上下文

#### 7.11.3 运行时连线结构

运行时连线建议结构：

```json
{
  "edgeId": "edge_1",
  "sourceNodeId": "node_condition_1",
  "targetNodeId": "node_http_1",
  "branchKey": "true"
}
```

建议字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `edgeId` | string | 连线 ID |
| `sourceNodeId` | string | 起始节点 |
| `targetNodeId` | string | 目标节点 |
| `branchKey` | string | 分支标识，普通边使用 `default`，条件边使用 `true` / `false` |

#### 7.11.4 编译产物补充信息

编译后的 `model_json` 建议额外补齐以下信息：

1. 节点的上下游关系
2. 拓扑排序结果
3. 已归一化的运行时策略
4. 已固定的资源引用标识
5. 已校验通过的参数结构

#### 7.11.5 编译错误结构

编译错误建议采用如下统一结构：

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

字段建议：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `errorCode` | string | 错误码，如 `UNREACHABLE_NODE` |
| `stage` | string | `STRUCTURE_VALIDATE` / `SCHEMA_VALIDATE` / `MODEL_COMPILE` |
| `nodeId` | string | 关联节点 ID |
| `field` | string | 关联字段 |
| `message` | string | 错误说明 |
| `severity` | string | `ERROR` / `WARN` |

建议最小错误码集合：

1. `CYCLIC_GRAPH`
2. `NO_START_NODE`
3. `NO_TERMINAL_NODE`
4. `UNREACHABLE_NODE`
5. `DISCONNECTED_SUBGRAPH`
6. `NODE_NOT_ON_START_TO_END_PATH`
7. `INVALID_CONDITION_BRANCH`
8. `NODE_SCHEMA_INVALID`
9. `RESOURCE_REF_INVALID`

#### 7.11.6 引擎消费约定

1. 引擎只依赖 `model_json` 执行，不再回读 `graph_json`
2. `model_json` 必须可独立执行，不能依赖前端画布信息
3. `model_json` 变更必须通过 `modelVersion` 或 `dslVersion` 管理兼容性

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
3. 最后完成 Starter 自动装配和基于 Starter 的独立 Server 聚合
