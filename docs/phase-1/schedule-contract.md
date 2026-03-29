# Fluxion 一期调度契约

## 1. 文档目的

本文档用于定义一期定时任务能力的正式协议，覆盖：

1. 定时任务配置结构
2. `misfirePolicy` 与 `reentryPolicy` 语义
3. `waitTimeoutMs` 与调度等待行为
4. `jobParams` 结构和运行时注入规则
5. `pause` / `resume` 对运行中的实例影响
6. `maxConcurrency` 的准确作用范围
7. 触发流水状态模型与查询结构

## 2. 总体约定

一期默认业务时区固定为东八区（北京时间），IANA 时区标识为 `Asia/Shanghai`。本文件中未显式携带 offset 的时间字符串，均按该时区解释；一期管理接口不开放其他业务时区配置。

### 2.1 调度实现边界

1. 一期采用单机 Quartz。
2. 一期一个任务对应一个流程定义。
3. 任务触发后创建新的流程实例执行。
4. 一期不支持调度级补偿和失败重放。
5. 一期调度语义拆分为“调度投递结果”和“流程实例结果”两层，不再使用单一状态混合表达。
6. 一期不提供独立排队语义；触发点在并发门禁不通过时直接拒绝。

### 2.1.1 Quartz 映射约定

1. 每条 `flx_schedule_job` 映射为一个 Quartz `JobDetail` 和一个关联 `Trigger`。
2. Quartz `JobDetail` 的稳定标识建议使用 `tenantId + jobId` 或 `tenantId + jobCode` 组合。
3. Quartz `JobDataMap` 一期只要求放入最小定位信息，如 `tenantId`、`jobId`，运行时详细配置仍以数据库中的 `flx_schedule_job` 为准。
4. 一期不要求自定义 Quartz `Job` 长时间持有 `CompletableFuture`；调度等待行为由调度执行逻辑实现，而不是由 Quartz 原生状态机承载。

### 2.2 接口与状态码规则

1. 管理接口统一使用 [../base.md](../base.md) 中定义的响应结构。
2. 所有管理接口固定返回 HTTP `200`。
3. 调度任务不存在时返回 `SCHEDULE_NOT_FOUND`。
4. 非法状态切换或不允许的操作返回 `SCHEDULE_CONFLICT`。

### 2.3 任务状态

| API 状态 | 说明 | DB 值 |
| --- | --- | --- |
| `PAUSED` | 已暂停，不再产生新触发 | `0` |
| `ENABLED` | 已启用，允许按 Cron 触发 | `1` |

约定：

1. 新创建的任务默认进入 `PAUSED` 状态。
2. 只有显式执行 `resume` 后才进入 `ENABLED`。

## 3. 任务配置结构

### 3.1 创建 / 更新请求体

```json
{
  "jobCode": "user_sync_job",
  "jobName": "用户同步任务",
  "flowDefId": 100,
  "versionPolicy": "LATEST",
  "fixedVersionId": null,
  "cronExpression": "0 0/5 * * * ?",
  "timezone": "Asia/Shanghai",
  "misfirePolicy": "CATCH_UP_BOUNDED",
  "catchUpConfig": {
    "maxCatchUpCount": 10,
    "maxCatchUpWindowSeconds": 3600,
    "order": "OLDEST_FIRST"
  },
  "jobParams": {
    "source": "scheduler",
    "operator": "system"
  },
  "maxConcurrency": 1,
  "reentryPolicy": "FORBID",
  "waitTimeoutMs": 10000
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `jobCode` | string | 是 | 任务业务编码 |
| `jobName` | string | 是 | 任务名称 |
| `flowDefId` | long | 是 | 触发的流程定义 ID |
| `versionPolicy` | string | 是 | `LATEST` / `FIXED` |
| `fixedVersionId` | long | 否 | `FIXED` 时必填 |
| `cronExpression` | string | 是 | Quartz Cron 表达式 |
| `timezone` | string | 否 | 一期固定为 `Asia/Shanghai`；可省略，若传入必须等于该值 |
| `misfirePolicy` | string | 是 | 错失触发处理策略 |
| `catchUpConfig` | object | 否 | `misfirePolicy = CATCH_UP_BOUNDED` 时的补跑边界配置 |
| `jobParams` | object | 否 | 静态任务参数 |
| `maxConcurrency` | int | 是 | 同一任务允许同时运行的实例数 |
| `reentryPolicy` | string | 是 | 重入策略 |
| `waitTimeoutMs` | int | 否 | 调度侧等待实例进入终态的最长时间 |

补充规则：

1. 未配置 `waitTimeoutMs` 时，调度触发只负责创建实例并完成本次触发记录，不等待流程结束。
2. 配置 `waitTimeoutMs` 时，调度侧最多等待到 `waitTimeoutMs` 后再结束本次触发观察。
3. 超时后本次触发流水不记为失败，而是记为“已投递但等待超时”；流程实例继续后台运行，不做强制取消。
4. `waitTimeoutMs` 只影响调度线程等待时间，不影响流程实例生命周期，也不等同于流程执行超时。
5. 当 `reentryPolicy = FORBID` 时，`maxConcurrency` 必须固定为 `1`；否则视为非法配置。
6. 当 `misfirePolicy = CATCH_UP_BOUNDED` 且未显式提供 `catchUpConfig` 时，默认使用 `maxCatchUpWindowSeconds = 3600`、`maxCatchUpCount = 10`、`order = OLDEST_FIRST`。
7. `timezone` 未传时由服务端补为 `Asia/Shanghai`；若显式传入其他值，应返回 `VALIDATION_ERROR`。

## 4. 版本解析规则

1. `versionPolicy = LATEST` 时，每次触发前解析流程当前生效版本。
2. `versionPolicy = FIXED` 时，每次触发都使用 `fixedVersionId`。
3. `FIXED` 模式下若版本不存在，触发流水记 `dispatchStatus = VERSION_NOT_FOUND`，并在列表汇总态中归并为 `INSTANCE_FAILED`。

## 5. misfirePolicy 语义

### 5.1 API 枚举

| API 值 | 说明 | DB 值 |
| --- | --- | --- |
| `FIRE_ONCE_NOW` | 错过后立即补触发一次 | `1` |
| `DO_NOTHING` | 丢弃错过的本次触发 | `2` |
| `CATCH_UP_BOUNDED` | 在边界内按顺序补跑历史触发点 | `3` |

### 5.2 语义说明

1. `FIRE_ONCE_NOW` 适合对实时性较敏感、但不要求完全补齐的任务。
2. `DO_NOTHING` 适合补跑成本高的任务。
3. `CATCH_UP_BOUNDED` 适合可接受补跑、但必须限制补跑风暴的轻量任务。

### 5.3 catchUpConfig 结构

```json
{
  "maxCatchUpCount": 10,
  "maxCatchUpWindowSeconds": 3600,
  "order": "OLDEST_FIRST"
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `maxCatchUpCount` | int | 否 | 本次恢复最多补跑的历史触发点数量，默认 `10` |
| `maxCatchUpWindowSeconds` | int | 否 | 仅补跑最近窗口内的 miss，默认 `3600` 秒 |
| `order` | string | 否 | 一期固定只支持 `OLDEST_FIRST` |

### 5.4 `CATCH_UP_BOUNDED` 语义

1. 调度器先计算自上次正常调度以来的 miss 触发点集合。
2. 只保留最近 `maxCatchUpWindowSeconds` 窗口内的触发点。
3. 按 `scheduledFireTime` 升序排序。
4. 截断到 `maxCatchUpCount`。
5. 每个保留下来的 miss 触发点都生成一条独立触发流水，`fireKind = MISFIRE_CATCH_UP`。
6. 每个补跑触发点与正常触发完全一致，都要经过 `reentryPolicy` 和 `maxConcurrency` 门禁。
7. 一期不做补跑排队；门禁不通过时直接拒绝该补跑触发点。
8. 一期不保证补齐所有历史 miss，只保证在边界内尽力补跑。

## 6. reentryPolicy 与 maxConcurrency 语义

### 6.1 API 枚举

| API 值 | 说明 | DB 值 |
| --- | --- | --- |
| `FORBID` | 有运行中实例时拒绝本次新触发 | `0` |
| `ALLOW` | 允许并发触发，受 `maxConcurrency` 上限控制 | `1` |

### 6.2 maxConcurrency 定义

1. `maxConcurrency` 只作用于“同一个任务同时可运行的流程实例数”。
2. `maxConcurrency` 不控制单个流程实例内部的节点并发。
3. `maxConcurrency = 1` 表示同一任务任一时刻最多只有一个运行中实例。
4. 当 `reentryPolicy = FORBID` 时，配置层必须保证 `maxConcurrency = 1`。

### 6.3 重入判定顺序

1. 先检查当前运行中实例数。
2. 若当前运行数已达到 `maxConcurrency`，本次触发直接拒绝，不创建新实例。
3. 若 `reentryPolicy = FORBID` 且当前运行数大于 `0`，本次触发直接拒绝。
4. 若 `reentryPolicy = ALLOW` 且当前运行数小于 `maxConcurrency`，允许创建新实例。
5. 上述门禁同时适用于正常触发与 `MISFIRE_CATCH_UP` 补跑触发。

### 6.4 触发拒绝时的记录

1. 即使未创建实例，也必须写入 `flx_schedule_trigger_log`。
2. 并发门禁拒绝时，记录 `dispatchStatus = REJECTED_CONCURRENCY`。
3. 被拒绝的触发记录不创建流程实例，`flowInstanceId = null`。
4. `errorMessage` 记录拒绝原因摘要。

## 7. 触发流水状态模型

### 7.1 内部状态字段

触发流水固定拆分为以下状态字段：

| 字段 | 说明 |
| --- | --- |
| `fireKind` | 触发种类，`NORMAL` / `MISFIRE_CATCH_UP` |
| `dispatchStatus` | 调度投递结果 |
| `waitStatus` | 调度侧等待结果 |
| `instanceStatusSnapshot` | 触发记录完成时或回填时看到的实例状态快照 |

推荐枚举：

1. `dispatchStatus`: `ACCEPTED` / `REJECTED_CONCURRENCY` / `VERSION_NOT_FOUND` / `DISPATCH_FAILED`
2. `waitStatus`: `NOT_WAITED` / `COMPLETED` / `TIMEOUT`
3. `instanceStatusSnapshot`: `UNKNOWN` / `RUNNING` / `SUCCESS` / `FAILED` / `CANCELLED`

### 7.2 `waitTimeoutMs` 行为

1. 当实例创建成功且未配置 `waitTimeoutMs` 时：
   `dispatchStatus = ACCEPTED`，`waitStatus = NOT_WAITED`。
2. 当实例创建成功且在 `waitTimeoutMs` 内进入终态时：
   `dispatchStatus = ACCEPTED`，`waitStatus = COMPLETED`。
3. 当实例创建成功但等待超过 `waitTimeoutMs` 时：
   `dispatchStatus = ACCEPTED`，`waitStatus = TIMEOUT`，`instanceStatusSnapshot` 通常为 `RUNNING`。
4. 发生 `TIMEOUT` 后，实例仍可在后台继续运行，并允许后续异步回填 `instanceStatusSnapshot`。

### 7.2.1 调度等待实现建议

1. `waitTimeoutMs` 的实现目标是“调度侧有限观察”，而不是改变流程实例生命周期。
2. 一期推荐由调度执行逻辑在实例创建成功后进行有限等待观察；不要求 Quartz `Job` 本身持有长生命周期 future。
3. 观察实现可采用短周期轮询实例状态或等价的轻量等待机制；只要对外语义满足 `COMPLETED` / `TIMEOUT` 即可。
4. 当观察窗口结束时，应立即写回本次触发流水的 `waitStatus`、`instanceStatusSnapshot` 和 `finishTime`。

### 7.2.2 超时后的异步回填

1. 当触发流水已记为 `TIMEOUT` 后，实例仍可在后台继续执行至终态。
2. 实例进入终态后，应允许异步回填对应 `flx_schedule_trigger_log.instanceStatusSnapshot`。
3. 一期推荐以“实例终态回调更新触发流水”为主路径；必要时可增加定时修复任务作为兜底。
4. 一期不强制要求通过事件总线实现回填，但必须保证回填不会重复创建新的触发流水记录。

### 7.3 列表汇总状态与详情展开

对外 UI 不直接暴露全部内部状态字段，默认只展示汇总状态 `summaryStatus`。

推荐汇总状态枚举：

1. `ACCEPTED`：已受理
2. `REJECTED_CONCURRENCY`：并发拒绝
3. `WAIT_TIMEOUT`：等待超时
4. `INSTANCE_SUCCESS`：实例成功
5. `INSTANCE_FAILED`：实例失败

推荐映射规则：

1. `dispatchStatus = REJECTED_CONCURRENCY` 时，`summaryStatus = REJECTED_CONCURRENCY`。
2. `dispatchStatus = ACCEPTED` 且 `waitStatus = TIMEOUT` 时，`summaryStatus = WAIT_TIMEOUT`。
3. `dispatchStatus = ACCEPTED` 且 `instanceStatusSnapshot = SUCCESS` 时，`summaryStatus = INSTANCE_SUCCESS`。
4. `dispatchStatus = ACCEPTED` 且 `waitStatus != TIMEOUT` 且 `instanceStatusSnapshot ∈ {UNKNOWN, RUNNING}` 时，`summaryStatus = ACCEPTED`。
5. 其他所有失败类场景，包括 `VERSION_NOT_FOUND`、`DISPATCH_FAILED`、`instanceStatusSnapshot = FAILED/CANCELLED`，统一归并为 `summaryStatus = INSTANCE_FAILED`。

详情页或详情抽屉再展开显示 `dispatchStatus`、`waitStatus`、`instanceStatusSnapshot` 与 `errorMessage`。

## 8. jobParams 结构与运行时注入

### 8.1 结构

`jobParams` 为任意 JSON 对象，例如：

```json
{
  "source": "scheduler",
  "operator": "system",
  "fullSync": false
}
```

### 8.2 注入规则

1. `jobParams` 在实例启动时注入到运行时上下文 `schedule.params`。
2. `jobParams` 不直接覆盖 `vars`。
3. 节点表达式可通过 `schedule.params.xxx` 访问这些参数。
4. 一期不支持在调度层对 `jobParams` 做额外表达式求值。

## 9. pause / resume 行为

### 9.1 pause

规则：

1. `pause` 后不再产生新的调度触发。
2. 已经创建并运行中的流程实例继续执行，不被取消。
3. 已写入 Quartz 的下一次计划触发应在暂停后失效。
4. 对已经是 `PAUSED` 的任务再次执行 `pause`，返回 `SCHEDULE_CONFLICT`。

### 9.2 resume

规则：

1. `resume` 后从当前时间点重新参与后续 Cron 计算。
2. `resume` 不会自动补齐暂停期间错过的全部触发点，是否补一次由 `misfirePolicy` 决定。
3. 对已经是 `ENABLED` 的任务再次执行 `resume`，返回 `SCHEDULE_CONFLICT`。

## 10. 触发流水结构

### 10.1 管理接口入口

调度任务与触发流水相关 `/admin/*` 接口统一见 [admin-api/schedules.md](./admin-api/schedules.md)。

### 10.2 返回项建议

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | long | 触发流水 ID |
| `jobId` | long | 任务 ID |
| `quartzFireInstanceId` | string | Quartz fire instance 标识 |
| `fireKind` | string | `NORMAL` / `MISFIRE_CATCH_UP` |
| `summaryStatus` | string | 列表汇总状态，推荐用于对外 UI |
| `dispatchStatus` | string | 调度投递结果 |
| `waitStatus` | string | 调度等待结果 |
| `instanceStatusSnapshot` | string | 实例状态快照 |
| `scheduledFireTime` | datetime | 理论触发时间 |
| `triggerTime` | datetime | 实际开始触发时间 |
| `finishTime` | datetime | 触发完成时间 |
| `durationMs` | long | 触发耗时 |
| `flowInstanceId` | long | 成功创建实例时的实例 ID |
| `errorMessage` | string | 失败或拒绝原因摘要 |

### 10.3 触发流水示例

```json
{
  "id": 3001,
  "jobId": 1001,
  "quartzFireInstanceId": "DEFAULT.user_sync_job.20260309093000",
  "fireKind": "MISFIRE_CATCH_UP",
  "summaryStatus": "WAIT_TIMEOUT",
  "dispatchStatus": "ACCEPTED",
  "waitStatus": "TIMEOUT",
  "instanceStatusSnapshot": "RUNNING",
  "scheduledFireTime": "2026-03-09 09:30:00.000",
  "triggerTime": "2026-03-09 09:30:01.102",
  "finishTime": "2026-03-09 09:30:11.110",
  "durationMs": 10008,
  "flowInstanceId": 202603090001,
  "errorMessage": "wait timeout while instance is still running"
}
```

并发拒绝示例：

```json
{
  "id": 3002,
  "jobId": 1001,
  "quartzFireInstanceId": "DEFAULT.user_sync_job.20260309093500",
  "fireKind": "NORMAL",
  "summaryStatus": "REJECTED_CONCURRENCY",
  "dispatchStatus": "REJECTED_CONCURRENCY",
  "waitStatus": "NOT_WAITED",
  "instanceStatusSnapshot": "UNKNOWN",
  "scheduledFireTime": "2026-03-09 09:35:00.000",
  "triggerTime": "2026-03-09 09:35:00.015",
  "finishTime": "2026-03-09 09:35:00.020",
  "durationMs": 5,
  "flowInstanceId": null,
  "errorMessage": "reentry forbidden while another instance is running"
}
```

## 11. 一期推荐实现取舍

1. API 层使用字符串枚举，数据库层继续保留整数枚举。
2. 调度任务默认创建为 `PAUSED`，避免保存后立即触发。
3. `maxConcurrency` 只约束实例并发，不下沉到节点并发。
4. 一期不提供独立排队调度语义，重入策略只支持 `FORBID` 和 `ALLOW`。
5. 一期默认错失补跑策略采用有界补跑，推荐默认值为“最近 1 小时、最多 10 次、最老优先”。
6. 一期触发流水列表默认展示 `summaryStatus`，详情页再展开 `dispatchStatus`、`waitStatus`、`instanceStatusSnapshot`。
