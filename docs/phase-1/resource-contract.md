# Fluxion 一期资源契约

## 1. 文档目的

本文档用于定义一期资源管理的正式协议，覆盖：

1. 资源公共字段语义
2. `DB` 资源配置结构
3. `HTTP` 资源配置结构
4. 敏感信息回显和更新规则
5. 资源测试接口契约
6. 资源禁用后的运行时行为

## 2. 资源公共模型

### 2.1 创建 / 更新请求体

```json
{
  "resourceCode": "main_db",
  "resourceName": "主数据库",
  "resourceType": "DB",
  "description": "核心业务库",
  "config": {},
  "secret": {}
}
```

### 2.2 公共字段说明

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `resourceCode` | string | 是 | 资源业务编码 |
| `resourceName` | string | 是 | 资源名称 |
| `resourceType` | string | 是 | 一期支持 `DB`、`HTTP` |
| `description` | string | 否 | 资源说明 |
| `config` | object | 是 | 非敏感配置 |
| `secret` | object | 否 | 敏感配置 |

### 2.3 通用约束

1. 非敏感配置写入 `flx_resource.config_json`。
2. 敏感配置写入 `flx_resource_secret.secret_ciphertext`。
3. 前端查询资源详情时，`secret` 永不回显明文。
4. 更新资源时，未显式传入的敏感字段保持原值。
5. 一期不支持在流程节点内直接保存连接凭证。
6. `resourceRef` 的一期正式含义固定为 `resourceCode`；设计态和运行态都不写入 `resourceId`。
7. 一期资源级并发保护配置统一放入 `config`，不新增独立表字段。
8. 资源级并发保护默认按 `resourceRef` 生效，推荐配置键为 `maxConcurrency`。

## 3. DB 资源契约

### 3.1 config 结构

```json
{
  "jdbcUrl": "jdbc:postgresql://localhost:5432/yourdatabase",
  "username": "app",
  "driverClassName": "org.postgresql.Driver",
  "connectTimeoutMs": 3000,
  "socketTimeoutMs": 5000,
  "maxPoolSize": 10,
  "maxConcurrency": 10,
  "connectionProperties": {
    "useUnicode": "true",
    "characterEncoding": "utf8"
  }
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `jdbcUrl` | string | 是 | JDBC 连接串 |
| `username` | string | 是 | 数据库用户名 |
| `driverClassName` | string | 否 | 驱动类名，默认 pg 驱动 |
| `connectTimeoutMs` | number | 否 | 连接超时 |
| `socketTimeoutMs` | number | 否 | 读写超时 |
| `maxPoolSize` | number | 否 | 最大连接池大小 |
| `maxConcurrency` | number | 否 | 资源级最大并发许可数，建议不高于 `maxPoolSize` |
| `connectionProperties` | object | 否 | 附加 JDBC 参数 |

补充约定：

1. `maxConcurrency` 用于引擎执行前的资源级并发保护，不影响 JDBC 连接池自身实现。
2. 未显式配置 `maxConcurrency` 时，引擎可回退为 `maxPoolSize` 或平台默认值。
3. 若同时配置了 `maxConcurrency` 和 `maxPoolSize`，则必须满足 `maxConcurrency <= maxPoolSize`；否则资源保存或发布校验应返回 `RESOURCE_CONFIG_INVALID`。

### 3.2 secret 结构

```json
{
  "password": "******"
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `password` | string | 是 | 数据库密码 |

### 3.3 测试规则

1. DB 资源测试固定执行 `SELECT 1`。
2. 测试成功时返回 `OK`。
3. 测试失败时返回 `RESOURCE_TEST_FAILED`，并在 `data.message` 中返回摘要。

## 4. HTTP 资源契约

### 4.1 config 结构

```json
{
  "baseUrl": "https://api.example.com",
  "connectTimeoutMs": 3000,
  "readTimeoutMs": 5000,
  "maxConcurrency": 50,
  "defaultHeaders": {
    "Accept": "application/json"
  },
  "authMode": "OPEN",
  "testPath": "/health",
  "testMethod": "GET"
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `baseUrl` | string | 是 | 外部服务基础地址 |
| `connectTimeoutMs` | number | 否 | 连接超时 |
| `readTimeoutMs` | number | 否 | 读超时 |
| `maxConcurrency` | number | 否 | 资源级最大并发许可数，用于保护下游 HTTP 服务 |
| `defaultHeaders` | object | 否 | 默认请求头 |
| `authMode` | string | 否 | `OPEN` / `BASIC_AUTH` |
| `testPath` | string | 否 | 测试连通性使用的路径 |
| `testMethod` | string | 否 | 测试方法，默认 `GET` |

补充约定：

1. `maxConcurrency` 用于限制同一 HTTP 资源在引擎侧的并发请求数。
2. 未配置 `maxConcurrency` 时，采用平台默认值。

### 4.2 secret 结构

#### `authMode = OPEN`

```json
{}
```

#### `authMode = BASIC_AUTH`

```json
{
  "username": "api_user",
  "password": "******"
}
```

### 4.3 测试规则

1. HTTP 资源测试调用 `baseUrl + testPath`。
2. 未配置 `testPath` 时默认请求 `/`。
3. 测试返回 `2xx` 视为成功。
4. 测试失败时记录状态码或异常摘要。

## 5. 敏感信息回显规则

### 5.1 查询资源详情

查询资源详情时返回如下结构：

```json
{
  "resourceId": 2001,
  "resourceCode": "main_db",
  "resourceType": "DB",
  "config": {
    "jdbcUrl": "jdbc:postgresql://localhost:5432/yourdatabase",
    "username": "app"
  },
  "secret": {
    "password": "***"
  }
}
```

规则：

1. 敏感字段只返回掩码占位。
2. 前端不得依赖掩码值做回填比较。
3. 若用户不修改敏感字段，更新请求可省略该字段。
4. 若用户传入新值，则整体替换对应敏感字段。

## 6. 资源测试语义

1. 管理端资源测试接口入口统一见 [admin-api/resources.md](./admin-api/resources.md)。
2. DB 资源测试固定执行 `SELECT 1`。
3. HTTP 资源测试调用 `baseUrl + testPath`；未配置 `testPath` 时默认请求 `/`。
4. 测试成功时返回 `OK`。
5. 测试失败时返回 `RESOURCE_TEST_FAILED`，并在结果对象中返回耗时与失败摘要。

## 7. 资源禁用后的行为

### 7.1 设计态

1. 流程保存草稿时允许引用已存在资源。
2. 发布校验时若引用资源已禁用，则校验失败并返回 `RESOURCE_DISABLED`。
3. 资源不存在时返回 `RESOURCE_NOT_FOUND`。

### 7.2 运行态

1. 已发布版本在运行时解析到禁用资源时，节点应失败并返回 `RESOURCE_DISABLED`。
2. 资源禁用不会自动下线已存在的 endpoint 或 schedule。
3. 资源禁用后产生的新实例会在首次使用该资源的节点处失败。
4. 若节点执行前未能获取该资源的并发许可，则该次按一次失败 attempt 处理，并继续复用节点既有重试语义。
5. 为降低同一资源持续繁忙时的重试风暴风险，`RESOURCE_PERMIT_EXHAUSTED` 的重试应在 `retryIntervalMs` 基础上附加随机抖动后执行。

## 8. 一期推荐实现取舍

1. 一期资源类型只正式收口 `DB` 和 `HTTP`。
2. `REDIS`、`OSS`、`CUSTOM` 仅保留数据库枚举兼容位，不纳入一期正式契约。
3. 敏感配置统一通过 `flx_resource_secret` 管理，不在节点配置和流程版本快照中出现。
