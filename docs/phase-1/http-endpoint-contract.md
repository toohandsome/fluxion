# Fluxion 一期 HTTP 发布契约

## 1. 文档目的

本文档用于定义一期 HTTP 发布能力的正式协议，覆盖：

1. `flx_http_endpoint` 字段语义
2. `request_config` 结构
3. `response_config` 结构
4. `rate_limit_config` 结构
5. 运行时认证和返回规范

## 2. 总体约定

### 2.1 路由规则

1. `flx_http_endpoint.path` 存储的就是最终对外暴露的实际运行时路径。
2. 运行时不再为 `path` 追加额外前缀（除了宿主项目的 context-path 前缀）。
3. `path` 的路由模板语法固定使用花括号占位符，例如 `/users/{id}`、`/tenants/{tenantCode}/users/{id}`。
4. `path` 必须以 `/` 开头，不允许携带 query string，不允许使用 `:id`、`${id}` 等其他占位语法。
5. 保存或更新端点时，只校验路径格式以及 Fluxion 自身端点配置内的唯一性。
6. 运行时启动或端点注册表重载时，若发现 `path + method` 与宿主项目已有路由冲突，则当前端点不注册到运行时分发器，并自动转为 `DISABLED`。
7. 只有 `status = ONLINE` 且未命中冲突的端点才能被运行时分发器匹配。

### 2.1.1 端点状态

| API 状态 | 说明 | DB 值 |
| --- | --- | --- |
| `OFFLINE` | 已下线，不参与运行时分发 | `0` |
| `ONLINE` | 已上线，允许尝试注册到运行时分发器 | `1` |
| `DISABLED` | 系统自动禁用；当前不参与运行时分发 | `2` |

约定：

1. 新建端点默认进入 `OFFLINE` 状态。
2. `DISABLED` 由系统侧写入，典型原因是 `ROUTE_CONFLICT`。
3. 当冲突消除并重新执行上线动作后，端点可重新回到 `ONLINE`。

### 2.2 版本解析规则

1. `versionPolicy = LATEST` 时，触发时解析流程当前生效版本。
2. `versionPolicy = FIXED` 时，触发时固定使用 `fixedVersionId`。
3. 若 `FIXED` 模式下对应版本不存在，则返回 `VERSION_NOT_FOUND`。

## 3. 端点管理对象

### 3.1 创建 / 更新请求体

```json
{
  "endpointCode": "user_sync_api",
  "flowDefId": 100,
  "versionPolicy": "LATEST",
  "fixedVersionId": null,
  "path": "/runtime/openapi/user/sync",
  "method": "POST",
  "authType": "BASIC_AUTH",
  "authCredentialId": 3001,
  "syncMode": true,
  "timeoutMs": 5000,
  "requestConfig": {},
  "responseConfig": {},
  "rateLimitConfig": {}
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `endpointCode` | string | 是 | 端点业务编码 |
| `flowDefId` | long | 是 | 绑定流程定义 |
| `versionPolicy` | string | 是 | `LATEST` / `FIXED` |
| `fixedVersionId` | long | 否 | `FIXED` 时必填 |
| `path` | string | 是 | 对外实际访问路径，模板语法固定为 `/users/{id}` |
| `method` | string | 是 | HTTP 方法 |
| `authType` | string | 是 | `OPEN` / `BASIC_AUTH` |
| `authCredentialId` | long | 否 | 认证凭证引用ID，`authType != OPEN` 时必填 |
| `syncMode` | boolean | 是 | `true` 表示同步，`false` 表示异步 |
| `timeoutMs` | number | 否 | 同步等待超时时间 |
| `requestConfig` | object | 否 | 请求提取、校验和业务键提取规则 |
| `responseConfig` | object | 否 | 响应 envelope / body 映射规则 |
| `rateLimitConfig` | object | 否 | 限流规则 |

落库枚举约定：

- `flx_http_endpoint.auth_type = 0` 表示 `OPEN`
- `flx_http_endpoint.auth_type = 1` 表示 `BASIC_AUTH`

## 4. request_config 结构

### 4.1 顶层结构

```json
{
  "pathParams": [],
  "queryParams": [],
  "headerParams": [],
  "body": {
    "mode": "JSON",
    "required": true
  },
  "businessKeyExpr": "${request.body.userId}"
}
```

### 4.2 参数定义结构

```json
{
  "name": "userId",
  "type": "LONG",
  "required": true,
  "defaultValue": null,
  "description": "用户ID"
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | string | 是 | 参数名 |
| `type` | string | 是 | `STRING` / `INT` / `LONG` / `DOUBLE` / `BOOLEAN` / `JSON` |
| `required` | boolean | 否 | 是否必填 |
| `defaultValue` | any | 否 | 缺省值 |
| `description` | string | 否 | 备注 |

### 4.3 body 结构

```json
{
  "mode": "JSON",
  "required": true,
  "schema": {
    "type": "object",
    "required": ["userId"]
  }
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `mode` | string | 是 | `NONE` / `JSON` / `FORM` / `RAW` |
| `required` | boolean | 否 | 是否要求请求体存在 |
| `schema` | object | 否 | `JSON` 模式下的最小校验规则 |

### 4.4 运行时提取结果

请求进入运行时后，提取结果统一写入以下上下文：

1. `request.path`
2. `request.query`
3. `request.headers`
4. `request.body`

规则：

1. 声明为 `required = true` 的字段缺失时，返回 `VALIDATION_ERROR`。
2. 类型转换失败时，返回 `VALIDATION_ERROR`。
3. 未声明的 Query/Header 不参与强校验，但原始请求仍可保留在上下文中。
4. `pathParams[].name` 必须与 `path` 模板中的花括号占位符同名，例如 `path = /users/{id}` 时必须声明 `name = id`。
5. `businessKeyExpr` 在参数提取完成后执行，并写入流程实例 `businessKey`。

### 4.5 request_config 示例

```json
{
  "pathParams": [
    {
      "name": "id",
      "type": "STRING",
      "required": true
    }
  ],
  "queryParams": [
    {
      "name": "dryRun",
      "type": "BOOLEAN",
      "required": false,
      "defaultValue": false
    }
  ],
  "headerParams": [
    {
      "name": "X-Trace-Id",
      "type": "STRING",
      "required": false
    }
  ],
  "body": {
    "mode": "JSON",
    "required": true,
    "schema": {
      "type": "object",
      "required": ["userId"]
    }
  },
  "businessKeyExpr": "${request.body.userId}"
}
```

若端点路径为 `/users/{id}`，则运行时提取后的占位符值写入 `request.path.id`。

## 5. response_config 结构

### 5.1 顶层结构（统一包装模式）

```json
{
  "envelopeMode": "UNIFIED",
  "successDataMapping": {
    "instanceId": "${instance.instanceId}",
    "status": "${instance.status}",
    "result": "${flow.output}"
  },
  "runningDataMapping": {
    "instanceId": "${instance.instanceId}",
    "status": "${instance.status}",
    "queryUrl": "/runtime/instances/${instance.instanceId}/result"
  },
  "failureDataMapping": {
    "instanceId": "${instance.instanceId}",
    "status": "${instance.status}",
    "errorCode": "${instance.errorCode}",
    "errorMessage": "${instance.errorMessage}"
  }
}
```

### 5.2 顶层结构（自定义 envelope 模式）

```json
{
  "envelopeMode": "CUSTOM_JSON",
  "successBodyMapping": {
    "ok": true,
    "result": "${flow.output}"
  },
  "runningBodyMapping": {
    "ok": false,
    "state": "${instance.status}",
    "poll": "/runtime/instances/${instance.instanceId}/result"
  },
  "failureBodyMapping": {
    "ok": false,
    "error": {
      "code": "${instance.errorCode}",
      "message": "${instance.errorMessage}"
    }
  }
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `envelopeMode` | string | 否 | `UNIFIED` / `CUSTOM_JSON`，默认 `UNIFIED` |
| `successDataMapping` | object | 否 | `UNIFIED` 模式下成功时映射到响应 `data` |
| `runningDataMapping` | object | 否 | `UNIFIED` 模式下运行中时映射到响应 `data` |
| `failureDataMapping` | object | 否 | `UNIFIED` 模式下失败时映射到响应 `data` |
| `successBodyMapping` | object | 否 | `CUSTOM_JSON` 模式下成功时映射整个 JSON body |
| `runningBodyMapping` | object | 否 | `CUSTOM_JSON` 模式下运行中时映射整个 JSON body |
| `failureBodyMapping` | object | 否 | `CUSTOM_JSON` 模式下失败时映射整个 JSON body |

规则：

1. `envelopeMode` 未配置时，默认按 `UNIFIED` 处理。
2. `UNIFIED` 模式下，平台固定生成 `code`、`message`、`requestId`、`data` 四元组；`successDataMapping`、`runningDataMapping`、`failureDataMapping` 只负责映射 `data`。
3. `CUSTOM_JSON` 模式下，运行时直接返回 `successBodyMapping`、`runningBodyMapping`、`failureBodyMapping` 的求值结果作为整个 JSON body，不再强制要求 `code`、`message`、`requestId`、`data` 四元组。
4. `successDataMapping`、`runningDataMapping`、`failureDataMapping`、`successBodyMapping`、`runningBodyMapping`、`failureBodyMapping` 只允许使用稳定命名空间求值；成功场景额外提供只读 `flow.output`。
5. `CUSTOM_JSON` 模式下，若某场景未配置对应 `*BodyMapping`，则回退为该场景的默认统一包装响应。
6. `response_config` 不允许访问节点执行器局部 `raw`；如需返回节点结果，应通过 `flow.output` 或稳定上下文 `nodes.*.output` 读取。
7. 路由未命中、端点未注册、端点未上线或因路由冲突未加载等场景，因拿不到端点级配置，仍返回平台默认错误结构。

## 6. rate_limit_config 结构

### 6.1 顶层结构

```json
{
  "enabled": true,
  "keyStrategy": "IP",
  "keyExpr": null,
  "windowSeconds": 60,
  "maxRequests": 100,
  "rejectCode": "RATE_LIMITED",
  "rejectMessage": "too many requests"
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `enabled` | boolean | 是 | 是否启用限流 |
| `keyStrategy` | string | 是 | `IP` / `REQUEST_EXPR` |
| `keyExpr` | string | 否 | `REQUEST_EXPR` 时必填 |
| `windowSeconds` | number | 是 | 限流窗口 |
| `maxRequests` | number | 是 | 窗口内最大请求数 |
| `rejectCode` | string | 否 | 超限时返回码，默认 `RATE_LIMITED` |
| `rejectMessage` | string | 否 | 超限时返回消息 |

规则：

1. 命中限流时不进入流程实例创建。
2. 命中限流时返回统一响应包，`code = rejectCode`；一期 `CUSTOM_JSON` 模式不覆盖限流拒绝 envelope。
3. 一期不实现令牌桶等复杂算法，固定窗口足够。

## 7. 认证

### 7.1 认证

| `authType` | 说明 | 请求头 |
| --- | --- | --- |
| `OPEN` | 不鉴权 | 无 |
| `BASIC_AUTH` | 基本认证 基于 HTTP 标准（RFC 7617）‌ | `Authorization: Basic <encoded-string>‌` |

约定：

1. 一期 `authType` 只实现 `OPEN`、 `BASIC_AUTH`
2. 鉴权失败时业务状态码为 `UNAUTHORIZED` 或 `FORBIDDEN`；一期 `CUSTOM_JSON` 模式不覆盖鉴权失败 envelope。
3. 数据库落库枚举固定为 `0-OPEN, 1-BASIC_AUTH`。

### 7.2 认证凭证引用规则

1. 端点通过 `authCredentialId` 关联认证凭证（`flx_auth_credential`）。
2. `authType = OPEN` 时 `authCredentialId` 必须为空。
3. `authType = BASIC_AUTH` 时 `authCredentialId` 必填。
4. 凭证不存在或已禁用时，返回 `UNAUTHORIZED` 或 `FORBIDDEN`。
5. 认证凭证密文由 `flx_auth_credential_secret` 托管，不在端点配置中存放明文。
6. 认证凭证详细契约见 [auth-credential-contract.md](./auth-credential-contract.md)。

### 7.3 一期认证凭证最小结构

非敏感配置（`config_json`）建议结构：

```json
{
  "username": "api_user",
  "realm": "fluxion"
}
```

敏感配置（`secret_ciphertext` 解密后的 JSON）建议结构：

```json
{
  "passwordHash": "$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "hashAlgo": "BCRYPT"
}
```

## 8. 运行时返回语义

### 8.1 同步触发成功（默认统一包装）

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

### 8.2 异步触发已受理（默认统一包装）

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

### 8.3 查询结果运行中（默认统一包装）

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

### 8.4 查询结果失败（默认统一包装）

```json
{
  "code": "FLOW_FAILED",
  "message": "flow execution failed",
  "requestId": "9f8d6f9f0d034f4d",
  "data": {
    "instanceId": 202603080001,
    "status": "FAILED",
    "errorCode": "HTTP_CALL_FAILED",
    "errorMessage": "remote service returned 500"
  }
}
```

### 8.5 自定义 envelope 示例

当 `responseConfig.envelopeMode = CUSTOM_JSON` 时，同步成功响应可直接返回：

```json
{
  "ok": true,
  "payload": {
    "userId": 1001,
    "status": "SYNCED"
  }
}
```

## 9. 一期设计取舍

1. 参数提取、校验、类型转换先于流程实例创建。
2. 同步和异步模式的差异仅在等待行为，不在执行语义。
3. 运行时 HTTP 发布端点默认使用统一包装，但允许通过 `responseConfig.envelopeMode = CUSTOM_JSON` 自定义整个 JSON envelope。
