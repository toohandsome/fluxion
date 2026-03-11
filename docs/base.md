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
安全：Spring Security
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

一期所有接口统一遵循同一套响应规范，包含管理接口和运行时接口。

### 统一约束

1. 除运行时 HTTP 接口可自定义响应 data 映射外，其他接口固定返回统一 JSON 格式：`application/json`
2. 除运行时 HTTP 接口可自定义响应 状态码 映射外，其他接口的 HTTP 状态码固定返回 `200`
3. HTTP 状态码不承载业务语义，成功、失败、运行中、鉴权失败、限流等状态全部通过业务状态码表达
4. 除运行时 HTTP 接口可自定义响应 状态码 映射外，其他接口都必须返回 `requestId`
5. `code` 统一使用字符串业务状态码

### 响应结构

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

### 一期推荐业务状态码

| 状态码 | 说明 |
| --- | --- |
| `OK` | 请求成功 |
| `ACCEPTED` | 异步请求已受理 |
| `INSTANCE_RUNNING` | 实例仍在运行 |
| `VALIDATION_ERROR` | 参数或模型校验失败 |
| `FLOW_NOT_FOUND` | 流程不存在 |
| `VERSION_NOT_FOUND` | 版本不存在 |
| `ENDPOINT_NOT_FOUND` | 运行时端点不存在 |
| `ENDPOINT_OFFLINE` | 运行时端点未上线 |
| `SCHEDULE_NOT_FOUND` | 定时任务不存在 |
| `SCHEDULE_CONFLICT` | 定时任务状态冲突或不允许的状态变更 |
| `RESOURCE_NOT_FOUND` | 资源不存在 |
| `RESOURCE_DISABLED` | 资源已禁用 |
| `RESOURCE_TEST_FAILED` | 资源连通性测试失败 |
| `UNAUTHORIZED` | 未通过认证 |
| `FORBIDDEN` | 无权限访问 |
| `RATE_LIMITED` | 触发限流 |
| `FLOW_FAILED` | 流程执行失败 |
| `SYNC_TIMEOUT` | 同步等待超时 |
| `INTERNAL_ERROR` | 系统内部错误 |
