# Fluxion 一期 Admin API 契约总入口

## 1. 文档目的

本文档用于定义一期管理端 API 契约体系的总入口与边界，覆盖：

1. 管理端接口的统一约定
2. Admin API 分文档结构与职责边界
3. Admin API 与对象契约文档的事实源划分
4. 文档同步与防漂移规则

说明：

1. 本文档不再展开维护所有 `/admin/*` 的字段级协议。
2. 一期管理端接口采用“总入口 + 分文档”结构维护。
3. 运行时 HTTP 触发与结果查询不在本文档维护，统一以 [http-endpoint-contract.md](./http-endpoint-contract.md) 和 [runtime-semantics.md](./runtime-semantics.md) 为准。
4. 统一响应结构、HTTP `200` 约定与默认时区以 [../base.md](../base.md) 为准；本文档只补充管理端体系特有语义。

## 2. 适用范围与事实源

一期管理端接口至少覆盖以下对象：

1. 流程定义、草稿、版本
2. 流程实例、节点执行、attempt 明细
3. 资源
4. 认证凭证
5. HTTP 发布端点
6. 调度任务与触发流水

对应正式事实源如下：

| 主题 | 正式文档 |
| --- | --- |
| 管理端体系总入口、通用约定、分文档边界 | **本文档** |
| 流程定义、草稿、版本、实例、执行记录接口 | [admin-api/flows.md](./admin-api/flows.md) |
| 资源、认证凭证、HTTP 发布管理接口 | [admin-api/resources.md](./admin-api/resources.md) |
| 调度任务与触发流水管理接口 | [admin-api/schedules.md](./admin-api/schedules.md) |
| 资源 `config` / `secret` 结构 | [resource-contract.md](./resource-contract.md) |
| 认证凭证结构 | [auth-credential-contract.md](./auth-credential-contract.md) |
| HTTP 发布配置结构 | [http-endpoint-contract.md](./http-endpoint-contract.md) |
| 调度配置与触发流水字段语义 | [schedule-contract.md](./schedule-contract.md) |
| 运行语义、实例状态、节点状态 | [runtime-semantics.md](./runtime-semantics.md) |
| 错误码 | [error-codes.md](./error-codes.md) |
| 落库表结构 | [../schema-pg.sql](../schema-pg.sql) |

## 3. 通用约定

### 3.1 响应与状态码

1. 管理端接口统一使用 [../base.md](../base.md) 中定义的包装响应。
2. 一期管理端接口固定返回 HTTP `200`。
3. 业务成功返回 `code = OK`。
4. 参数非法返回 `VALIDATION_ERROR`。
5. 对象不存在、状态冲突、发布冲突等业务错误码，以 [error-codes.md](./error-codes.md) 为准。

### 3.2 时间与时区

1. 一期管理端默认业务时区固定为 `Asia/Shanghai`。
2. 管理接口中的时间字符串、列表筛选时间范围、示例返回值，统一按 `yyyy-MM-dd HH:mm:ss.SSS` 表达。
3. 一期不允许通过管理接口提交其他业务时区值。

### 3.3 分页查询约定

管理端列表接口统一支持以下查询参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `pageNum` | int | 否 | `1` | 页码，从 `1` 开始 |
| `pageSize` | int | 否 | `20` | 每页条数，一期建议上限 `200` |

统一分页响应 `data` 结构如下：

```json
{
  "items": [],
  "pageNum": 1,
  "pageSize": 20,
  "total": 0
}
```

### 3.4 并发控制约定

1. 流程草稿更新使用 `draftRevision` 进行乐观并发控制。
2. 流程定义更新使用 `revision` 进行乐观并发控制。
3. 资源、认证凭证、HTTP 端点、调度任务等其他管理对象若启用乐观锁，应在各自对象文档与 DDL 中显式声明，不得只在接口说明中隐含约定。
4. 若请求携带的修订号与当前存储值不一致，返回对应对象的冲突类错误码。
5. 创建类接口响应返回新对象主键；更新类接口响应返回最新修订号或对象快照。

## 4. 分文档结构

一期 Admin API 采用以下拆分方式：

| 文档 | 覆盖范围 | 职责 |
| --- | --- | --- |
| [admin-api/flows.md](./admin-api/flows.md) | 流程定义、草稿、版本、实例、执行记录 | 维护 `/admin/flows/*`、`/admin/instances/*`、`/admin/executions/*` 的接口路径、请求响应与列表筛选规则 |
| [admin-api/resources.md](./admin-api/resources.md) | 资源、认证凭证、HTTP 发布端点 | 维护 `/admin/resources/*`、`/admin/auth-credentials/*`、`/admin/endpoints/*` 的接口入口与管理规则 |
| [admin-api/schedules.md](./admin-api/schedules.md) | 调度任务、触发流水 | 维护 `/admin/schedules/*` 及触发流水查询接口入口与管理规则 |

## 5. 快速入口

1. 流程定义、草稿、版本、实例与执行记录：见 [admin-api/flows.md](./admin-api/flows.md)
2. 资源、认证凭证、HTTP 发布端点：见 [admin-api/resources.md](./admin-api/resources.md)
3. 调度任务与触发流水：见 [admin-api/schedules.md](./admin-api/schedules.md)

## 6. 与对象契约的边界

为避免 SoT 漂移，按以下边界维护：

1. `admin-api/*.md` 负责维护 `/admin/*` 的路径、方法、请求体、分页、列表筛选、响应对象与状态流转入口。
2. `resource-contract.md`、`auth-credential-contract.md`、`http-endpoint-contract.md`、`schedule-contract.md` 负责维护对象字段结构、运行时语义、状态解释与落库约定。
3. 对象契约文档若需要引用管理接口，只保留“入口链接 + 单句说明”，不再重复维护 `/admin/*` 路径、方法和响应示例。
4. `technical-solution.md` 只保留 Admin API 文档索引，不再维护 `/admin/*` 字段级协议。

## 7. 文档与 harness 同步规则

1. 修改 Admin API 相关文档时，应同步检查：
   - [admin-api/flows.md](./admin-api/flows.md)
   - [admin-api/resources.md](./admin-api/resources.md)
   - [admin-api/schedules.md](./admin-api/schedules.md)
   - [../schema-pg.sql](../schema-pg.sql)
   - `fixtures/*` 与 `tools/harness/run_contracts.py`
2. `technical-solution.md` 不再维护 `/admin/*` 的字段级协议与联调示例；只保留页面到 Admin API 文档的索引。
3. 对象契约文档不得重复维护 `/admin/*` 路径；若需要引用接口，只能链接到 `admin-api/*.md`。
4. `contracts` harness 应对以下漂移保持敏感：
   - `admin-api/*.md` 缺失或总入口链接缺失
   - 对象契约文档重新出现 `/admin/*` 路径
   - `technical-solution.md` 重新出现正式 `/admin/*` 协议
