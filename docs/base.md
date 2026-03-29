# Fluxion 规范文档

## 技术栈

### 后端

构建工具：maven
JDK21
框架：springboot 3.5
JSON 处理库: Jackson（springboot自带）
日志框架：logback
数据库连接池：druid
HTTP 客户端: OkHttp
orm：MyBatis-Plus
数据库自动化：Flyway
参数校验：spring-boot-starter-validation
接口文档：knife4j-openapi3-jakarta-spring-boot-starter
任务调度：Quartz
测试：JUnit5 + Mockito + Testcontainers + WireMock

### 前端

框架：vue3
语言：TypeScript
构建工具：vite
流程画布：AntV X6
动态表单：Formily
路由: Vue Router
状态: Pinia
UI组件: Element Plus
HTTP: Axios
工具: VueUse
代码提示：Monaco
工程质量：ESLint + Prettier + Stylelint
测试：Vitest + Vue Test Utils + Playwright

## 数据库规范

- **时间格式：** 所有时间字段统一使用 `yyyy-MM-dd HH:mm:ss.SSS` 格式
- **默认时区：** 一期默认业务时区固定为东八区（北京时间），IANA 时区标识为 `Asia/Shanghai`，UTC 偏移为 `UTC+08:00`
- **时间解释规则：** 文档、接口示例、数据库脚本注释以及未显式携带时区的时间字符串，均按 `Asia/Shanghai` 解释；若后续需要跨时区扩展，再通过显式 offset 或时区字段表达
- **命名规范：** 数据库表名采用下划线命名法（snake_case）
- **字符集：**
  数据库字符集统一采用 `utf8`
  前后端字符集统一采用 `UTF-8`

## 流程版本规范

- **设计态与发布态分离：** 草稿单独存储，正式版本以不可变快照独立存储
- **单草稿约束：** 一个流程定义同一时间只允许存在一个草稿
- **正式版本号规则：** 草稿不占正式版本号，发布时才分配新的 `version_num`
- **版本号分配方式：** 发布成功时使用 `latest_version_num + 1`，首个发布版本号固定为 `1`
- **草稿并发控制：** 草稿通过 `draft_revision` 或更新时间控制并发写入
- **草稿基线：** 草稿可记录 `base_version_id`，表示当前编辑基于哪个已发布版本展开
- **正式版本状态：** 正式版本只允许 `PUBLISHED` 和 `ARCHIVED` 两种状态

## 接口响应规范

一期管理接口、调度/资源接口以及运行时结果查询接口统一遵循同一套响应规范。

对已匹配到的运行时 HTTP 发布端点：

- 默认仍返回统一 JSON 包装体
- 若端点配置显式启用自定义 envelope，则允许返回自定义 JSON body，不再强制要求 `code`、`message`、`requestId`、`data` 四元组

### 统一约束

1. 统一包装体固定返回 JSON：`application/json`
2. HTTP 状态码不承载业务语义，成功、失败、运行中、鉴权失败、限流等状态全部通过业务状态码表达
3. 统一包装体固定包含 `code`、`message`、`requestId`、`data`
4. 除运行时 HTTP 接口启用自定义 envelope 外，其他接口都必须返回 `requestId`

### 响应结构

以下结构适用于统一包装体：

```json
{
  "code": "OK",
  "message": "success",
  "requestId": "9f8d6f9f0d034f4d",
  "data": {}
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `code` | string | 是 | 业务状态码 |
| `message` | string | 是 | 人类可读消息 |
| `requestId` | string | 是 | 请求唯一标识 |
| `data` | object / array / null | 是 | 业务数据载体 |

### 成功响应示例

```json
{
  "code": "OK",
  "message": "success",
  "requestId": "9f8d6f9f0d034f4d",
  "data": {
    "instanceId": 202603080001
  }
}
```

### 失败响应示例

```json
{
  "code": "VALIDATION_ERROR",
  "message": "validation failed",
  "requestId": "9f8d6f9f0d034f41",
  "data": {
    "errors": [
      {
        "field": "path",
        "message": "path is required"
      }
    ]
  }
}
```

### 业务状态码来源

业务状态码的正式定义、分层归属和使用约定以 [phase-1/error-codes.md](./phase-1/error-codes.md) 为准，`base.md` 不再重复维护完整状态码表。

本文件仅保留以下通用约束：

1. 统一包装体中的 `code` 一律使用字符串业务状态码。
2. 业务状态码不依赖 HTTP 状态码表达语义。
3. 若运行时 HTTP 端点启用自定义 envelope，则可不直接暴露统一包装体字段，但默认包装语义与错误码归属仍以 `error-codes.md` 为基线。
4. 若新增错误码，应优先补充到 `error-codes.md`，而不是直接扩写到 `base.md`。
