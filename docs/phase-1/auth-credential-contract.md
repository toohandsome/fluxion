# Fluxion 一期认证凭证契约

## 1. 文档目的

本文档用于定义一期认证凭证的正式协议，覆盖：

1. 认证凭证公共字段语义
2. BASIC_AUTH 凭证配置与密文结构
3. 查询与回显规则
4. 更新与禁用规则
5. 管理接口契约

## 2. 一期边界

1. 一期仅支持 `BASIC_AUTH`。
2. `OPEN` 不需要认证凭证。
3. HTTP 端点通过 `authCredentialId` 关联认证凭证。

## 3. 认证凭证公共模型

### 3.1 创建 / 更新请求体

```json
{
  "credentialCode": "runtime_basic_001",
  "credentialName": "运行时基础认证",
  "authType": "BASIC_AUTH",
  "description": "一期入站基础认证",
  "config": {},
  "secret": {}
}
```

### 3.2 字段说明

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `credentialCode` | string | 是 | 凭证业务编码 |
| `credentialName` | string | 是 | 凭证名称 |
| `authType` | string | 是 | 一期固定为 `BASIC_AUTH` |
| `description` | string | 否 | 备注说明 |
| `config` | object | 否 | 非敏感配置 |
| `secret` | object | 否 | 敏感配置 |

### 3.3 通用约束

1. `credentialCode` 在同租户下唯一。
2. `authType` 一期不可变更，若需更换类型需新建凭证。
3. `secret` 不允许明文回显。
4. `status` 默认启用，禁用通过独立接口完成。

## 4. BASIC_AUTH 凭证结构

### 4.1 config 结构

```json
{
  "username": "api_user",
  "realm": "fluxion"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `username` | string | 是 | Basic 用户名 |
| `realm` | string | 否 | realm 描述 |

### 4.2 secret 结构（请求体）

```json
{
  "password": "******"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `password` | string | 创建时是 | 明文密码，仅用于写入 |

### 4.3 存储结构（密文解密后的 JSON）

```json
{
  "passwordHash": "$2a$10$xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
  "hashAlgo": "BCRYPT"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `passwordHash` | string | 是 | 密码哈希 |
| `hashAlgo` | string | 是 | 哈希算法，推荐 `BCRYPT` |

### 4.4 哈希与校验规则

1. 服务端接收 `password` 后计算 `passwordHash` 写入密文表。
2. 校验时使用常量时间对比，避免时序侧信道。
3. 一期推荐使用 `BCRYPT`，并保留 `hashAlgo` 以便后续迁移。

## 5. 查询与回显规则

1. 查询列表与详情时不返回 `secret` 明文。
2. 返回 `secret` 字段时仅提供掩码占位或返回 `secretConfigured = true/false`。
3. 前端不得依赖掩码值做回填比较。

示例：

```json
{
  "credentialId": 3001,
  "credentialCode": "runtime_basic_001",
  "credentialName": "运行时基础认证",
  "authType": "BASIC_AUTH",
  "config": {
    "username": "api_user",
    "realm": "fluxion"
  },
  "secretConfigured": true,
  "status": "ENABLED"
}
```

## 6. 更新规则

1. 未显式传入 `secret` 时，保持原密码不变。
2. 若传入 `secret.password`，则覆盖更新并递增 `secret_version`。
3. 更新 `config` 不影响 `secret`。

## 7. 禁用后的行为

### 7.1 设计态

1. 端点创建时引用到禁用凭证，返回 `FORBIDDEN`。

### 7.2 运行态

1. 运行时解析到禁用凭证，返回 `UNAUTHORIZED` 或 `FORBIDDEN`。
2. 禁用不会自动下线已发布端点。

## 8. 接口契约

1. `GET /admin/auth-credentials` 查询凭证列表。
2. `POST /admin/auth-credentials` 创建凭证。
3. `GET /admin/auth-credentials/{credentialId}` 查询凭证详情。
4. `PUT /admin/auth-credentials/{credentialId}` 更新凭证。
5. `POST /admin/auth-credentials/{credentialId}/disable` 禁用凭证。

创建响应示例：

```json
{
  "code": "OK",
  "message": "success",
  "requestId": "9f8d6f9f0d034f4d",
  "data": {
    "credentialId": 3001
  }
}
```

## 9. 一期推荐实现取舍

1. 一期只实现 `BASIC_AUTH`，其余类型保留枚举位。
2. 密码仅通过哈希存储，不保留可逆密文。
3. 禁用优先于端点校验，避免已下线凭证被误用。
