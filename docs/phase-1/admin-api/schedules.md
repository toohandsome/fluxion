# Fluxion 一期 Admin API：调度任务与触发流水

## 1. 文档目的

本文档定义一期管理端中与调度任务和触发流水相关的 `/admin/*` 接口，覆盖：

1. 调度任务管理接口
2. 触发流水查询接口

管理端通用约定（统一响应、时间格式、分页、并发控制）统一以 [../admin-api-contract.md](../admin-api-contract.md) 为准。

## 2. 适用范围与事实源

本文档维护以下管理端接口：

1. `/admin/schedules/*`
2. `/admin/schedules/{jobId}/triggers/*`

相关对象语义与事实源：

| 主题 | 正式文档 |
| --- | --- |
| 管理端通用约定 | [../admin-api-contract.md](../admin-api-contract.md) |
| 调度配置、状态语义、触发流水结构 | [../schedule-contract.md](../schedule-contract.md) |
| 错误码 | [../error-codes.md](../error-codes.md) |
| 落库表结构 | [../../schema-pg.sql](../../schema-pg.sql) |

## 3. 接口清单

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/admin/schedules` | 查询任务列表 |
| `POST` | `/admin/schedules` | 创建任务 |
| `GET` | `/admin/schedules/{jobId}` | 查询任务详情 |
| `PUT` | `/admin/schedules/{jobId}` | 更新任务 |
| `POST` | `/admin/schedules/{jobId}/pause` | 暂停任务 |
| `POST` | `/admin/schedules/{jobId}/resume` | 恢复任务 |
| `GET` | `/admin/schedules/{jobId}/triggers` | 查询触发流水列表 |
| `GET` | `/admin/schedules/{jobId}/triggers/{triggerId}` | 查询触发流水详情 |

## 4. 任务管理规则

1. 调度字段级结构统一以 [../schedule-contract.md](../schedule-contract.md) 为准。
2. 一期调度业务时区固定为 `Asia/Shanghai`；管理接口不开放其他时区配置。
3. `pause` / `resume` 只改变后续触发，不取消已启动实例。
4. `pause`、`resume`、版本解析、`waitTimeoutMs`、`misfirePolicy`、`reentryPolicy` 的语义统一以 [../schedule-contract.md](../schedule-contract.md) 为准。

## 5. 触发流水查询规则

1. 触发流水列表默认返回 `summaryStatus`；详情接口再展开 `dispatchStatus`、`waitStatus`、`instanceStatusSnapshot`。
2. 触发流水稳定字段、推荐枚举与汇总映射规则统一以 [../schedule-contract.md](../schedule-contract.md) 第 `7` 节与第 `10` 节为准。
