# Fluxion 一期 Admin API：资源与发布管理

## 1. 文档目的

本文档定义一期管理端中与资源治理和发布配置相关的 `/admin/*` 接口，覆盖：

1. 资源管理接口
2. 认证凭证管理接口
3. HTTP 发布端点管理接口

管理端通用约定（统一响应、时间格式、分页、并发控制）统一以 [../admin-api-contract.md](../admin-api-contract.md) 为准。

## 2. 适用范围与事实源

本文档维护以下管理端接口：

1. `/admin/resources/*`
2. `/admin/auth-credentials/*`
3. `/admin/endpoints/*`

相关对象语义与事实源：

| 主题 | 正式文档 |
| --- | --- |
| 管理端通用约定 | [../admin-api-contract.md](../admin-api-contract.md) |
| 资源 `config` / `secret` 结构 | [../resource-contract.md](../resource-contract.md) |
| 认证凭证结构 | [../auth-credential-contract.md](../auth-credential-contract.md) |
| HTTP 发布配置结构 | [../http-endpoint-contract.md](../http-endpoint-contract.md) |
| 错误码 | [../error-codes.md](../error-codes.md) |
| 落库表结构 | [../../schema-pg.sql](../../schema-pg.sql) |

## 3. 资源管理接口

### 3.1 接口清单

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/admin/resources` | 查询资源列表 |
| `POST` | `/admin/resources` | 创建资源 |
| `GET` | `/admin/resources/{resourceId}` | 查询资源详情 |
| `PUT` | `/admin/resources/{resourceId}` | 更新资源 |
| `DELETE` | `/admin/resources/{resourceId}` | 删除资源 |
| `POST` | `/admin/resources/{resourceId}/test` | 测试资源 |

### 3.2 规则

1. `config` / `secret` 的字段级结构统一以 [../resource-contract.md](../resource-contract.md) 为准。
2. 列表与详情接口不得回显敏感明文。
3. 资源删除采用逻辑删除；若仍被已发布流程引用，推荐返回冲突类错误而不是静默删除。

### 3.3 测试接口返回

`POST /admin/resources/{resourceId}/test` 成功或失败时，响应 `data` 至少包含：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `status` | string | `SUCCESS` / `FAILED` |
| `latencyMs` | long | 测试耗时 |
| `message` | string | 连通性测试摘要 |

## 4. 认证凭证管理接口

### 4.1 接口清单

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/admin/auth-credentials` | 查询凭证列表 |
| `POST` | `/admin/auth-credentials` | 创建凭证 |
| `GET` | `/admin/auth-credentials/{credentialId}` | 查询凭证详情 |
| `PUT` | `/admin/auth-credentials/{credentialId}` | 更新凭证 |
| `POST` | `/admin/auth-credentials/{credentialId}/disable` | 禁用凭证 |

### 4.2 规则

1. 凭证字段级结构统一以 [../auth-credential-contract.md](../auth-credential-contract.md) 为准。
2. 禁用接口为状态流转接口，不做物理删除。
3. 详情接口只返回掩码或 `secretConfigured`，不回显明文敏感信息。

## 5. HTTP 发布端点管理接口

### 5.1 接口清单

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/admin/endpoints` | 查询端点列表 |
| `POST` | `/admin/endpoints` | 创建端点配置 |
| `GET` | `/admin/endpoints/{endpointId}` | 查询端点详情 |
| `PUT` | `/admin/endpoints/{endpointId}` | 更新端点配置 |
| `POST` | `/admin/endpoints/{endpointId}/online` | 上线端点 |
| `POST` | `/admin/endpoints/{endpointId}/offline` | 下线端点 |

### 5.2 规则

1. 端点字段级结构统一以 [../http-endpoint-contract.md](../http-endpoint-contract.md) 为准。
2. 新建端点默认 `OFFLINE`。
3. `online` 成功只表示允许注册；若运行时发现宿主路由冲突，可自动转为 `DISABLED`。
4. 端点详情建议返回 `disableReason`，便于说明 `ROUTE_CONFLICT` 等系统禁用原因。
