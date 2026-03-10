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
2. 运行时不再为 `path` 追加额外前缀（除了宿主项目的context-path前缀,需要提前检查是否与宿主项目已有path 冲突）。
3. 如果与宿主项目已有path 冲突, 页面要提出出具体报错信息，禁止保存
. 只有 `status = ONLINE` 的端点才能被运行时分发器匹配。

### 2.2 响应规则

1. 管理接口和运行时接口统一使用 [../base.md](../base.md) 中定义的响应包结构。
2. 所有运行时接口固定返回 HTTP `200`。
3. 一期 HTTP 发布响应格式固定为 JSON 包装。
4. 业务状态全部通过字符串 `code` 表达。
5. 所有运行时接口必须返回 `requestId`。
6. HTTP接口发布时如果自定义了返回格式、则自定义优先，不受全局格式影响

### 2.3 版本解析规则

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
| `path` | string | 是 | 对外实际访问路径 |
| `method` | string | 是 | HTTP 方法 |
| `authType` | string | 是 | `OPEN` / `APP_KEY` / `BEARER_TOKEN` / `BASIC_AUTH` |
| `authCredentialId` | long | 否 | 认证凭证引用ID，`authType != OPEN` 时必填 |
| `syncMode` | boolean | 是 | `true` 表示同步，`false` 表示异步 |
| `timeoutMs` | number | 否 | 同步等待超时时间 |
| `requestConfig` | object | 否 | 请求提取、校验和业务键提取规则 |
| `responseConfig` | object | 否 | 响应 `data` 映射规则 |
| `rateLimitConfig` | object | 否 | 限流规则 |

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
4. `businessKeyExpr` 在参数提取完成后执行，并写入流程实例 `businessKey`。

### 4.5 request_config 示例

```json
{
  "pathParams": [
    {
      "name": "tenantCode",
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

## 5. response_config 结构

### 5.1 顶层结构

```json
{
  "successDataMapping": {
    "instanceId": "${instance.instanceId}",
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

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `successDataMapping` | object | 否 | 成功时映射到响应 `data` |
| `runningDataMapping` | object | 否 | 运行中时映射到响应 `data` |
| `failureDataMapping` | object | 否 | 失败时映射到响应 `data` |

规则：

1. `successDataMapping`、`runningDataMapping`、`failureDataMapping` 只允许使用稳定命名空间求值；成功场景额外提供只读 `flow.output`。
2. 未配置 `successDataMapping` 时，默认返回 `{ "result": flow.output }`。
3. 未配置 `runningDataMapping` 时，默认返回 `instanceId`、`status`、`queryUrl`。
4. 未配置 `failureDataMapping` 时，默认返回 `instanceId`、`status`、`errorCode`、`errorMessage`。
5. `response_config` 只负责映射统一响应包中的 `data`，不改变 `code`、`message`、`requestId` 和 HTTP 状态码。
6. `response_config` 不允许访问节点执行器局部 `raw`；如需返回节点结果，应通过 `flow.output` 或稳定上下文 `nodes.*.output` 读取。

## 6. rate_limit_config 结构

### 6.1 顶层结构

```json
{
  "enabled": true,
  "keyStrategy": "APP_KEY",
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
| `keyStrategy` | string | 是 | `IP` / `APP_KEY` / `REQUEST_EXPR` |
| `keyExpr` | string | 否 | `REQUEST_EXPR` 时必填 |
| `windowSeconds` | number | 是 | 限流窗口 |
| `maxRequests` | number | 是 | 窗口内最大请求数 |
| `rejectCode` | string | 否 | 超限时返回码，默认 `RATE_LIMITED` |
| `rejectMessage` | string | 否 | 超限时返回消息 |

规则：

1. 命中限流时不进入流程实例创建。
2. 命中限流时返回统一响应包，`code = rejectCode`。
3. 一期不实现令牌桶等复杂算法，固定窗口足够。

## 7. 认证

### 7.1 认证

| `authType` | 说明 | 请求头 |
| --- | --- | --- |
| `OPEN` | 不鉴权 | 无 |
| `APP_KEY` | 使用应用 Key 鉴权 | `X-App-Key` |
| `BEARER_TOKEN` | 使用 Bearer Token 鉴权 | `Authorization: Bearer xxx` |
| `BASIC_AUTH` | 基本认证 基于 HTTP 标准（RFC 7617）‌ | `Authorization: Basic <encoded-string>‌` |

约定：

1. 一期 `authType` 只实现 BASIC_AUTH，`OPEN` 允许配置但不做校验。
2. 鉴权失败时固定返回 HTTP `200`，业务状态码为 `UNAUTHORIZED` 或 `FORBIDDEN`。

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

### 8.1 同步触发成功

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

### 8.2 异步触发已受理

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

### 8.3 查询结果运行中

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

### 8.4 查询结果失败

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

## 9. 一期设计取舍

1. 运行时全部返回 HTTP `200`，避免双重状态语义（除了Http接口发布时的自定义状态码）。
2. 参数提取、校验、类型转换先于流程实例创建。
3. 同步和异步模式的差异仅在等待行为，不在响应结构。
4. 一期响应格式固定为 JSON 包装，`response_config` 只控制 `data` 映射。
