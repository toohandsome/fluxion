# Fluxion 一期错误码基线

## 1. 文档目的

本文档用于定义一期统一错误码基线，覆盖管理接口、运行时接口、调度与引擎四个维度。

## 2. 分层规则

| 维度 | 范围 | 说明 |
| --- | --- | --- |
| `COMMON` | 通用平台错误 | 可被多个接口层复用的统一错误 |
| `MANAGEMENT` | Admin API | 面向设计、发布、资源、调度管理 |
| `RUNTIME` | Runtime API | 面向同步/异步触发与结果查询 |
| `SCHEDULER` | 调度投递与等待 | 面向 Quartz 触发、并发门禁、等待回填 |
| `ENGINE` | 模型编译与节点执行 | 面向 DAG 校验、表达式求值、节点执行 |

## 3. COMMON 错误码

| 错误码 | 说明 |
| --- | --- |
| `OK` | 请求成功 |
| `VALIDATION_ERROR` | 参数或请求结构非法 |
| `UNAUTHORIZED` | 未通过认证 |
| `FORBIDDEN` | 无权限访问或访问被拒绝 |
| `RATE_LIMITED` | 触发限流 |
| `INTERNAL_ERROR` | 系统内部错误 |

## 4. MANAGEMENT 错误码

| 错误码 | 说明 |
| --- | --- |
| `FLOW_NOT_FOUND` | 流程不存在 |
| `FLOW_DRAFT_NOT_FOUND` | 草稿不存在 |
| `FLOW_DRAFT_CONFLICT` | 草稿并发冲突 |
| `FLOW_VERSION_NOT_FOUND` | 流程版本不存在 |
| `FLOW_PUBLISH_CONFLICT` | 发布冲突或状态不允许 |
| `VALIDATION_ERROR` | 管理请求参数非法 |
| `RESOURCE_NOT_FOUND` | 资源不存在 |
| `RESOURCE_DISABLED` | 资源已禁用 |
| `RESOURCE_CONFIG_INVALID` | 资源配置非法 |
| `AUTH_CREDENTIAL_NOT_FOUND` | 认证凭证不存在 |
| `AUTH_CREDENTIAL_DISABLED` | 认证凭证已禁用 |
| `SCHEDULE_NOT_FOUND` | 调度任务不存在 |
| `SCHEDULE_CONFLICT` | 调度任务状态冲突或非法状态切换 |
| `HTTP_ENDPOINT_NOT_FOUND` | HTTP 发布接口不存在 |
| `RESOURCE_TEST_FAILED` | 资源连通性测试失败 |

## 5. RUNTIME 错误码

| 错误码 | 说明 |
| --- | --- |
| `ACCEPTED` | 异步触发已受理 |
| `INSTANCE_RUNNING` | 实例仍在运行 |
| `SYNC_TIMEOUT` | 同步触发等待超时，实例继续后台运行 |
| `FLOW_FAILED` | 流程失败结束 |
| `INSTANCE_NOT_FOUND` | 实例不存在 |
| `FLOW_OUTPUT_EVAL_FAILED` | `flowOutputMapping` 求值失败 |
| `ENDPOINT_NOT_FOUND` | 运行时端点不存在 |
| `ENDPOINT_OFFLINE` | 运行时端点未上线 |

## 6. SCHEDULER 错误码 / 状态原因码

| 错误码 | 说明 |
| --- | --- |
| `REJECTED_CONCURRENCY` | 触发被实例并发门禁拒绝 |
| `VERSION_NOT_FOUND` | 调度解析版本失败 |
| `DISPATCH_FAILED` | 调度投递失败 |
| `WAIT_TIMEOUT` | 调度侧等待超时 |
| `TRIGGER_LOG_NOT_FOUND` | 触发流水不存在 |

## 7. ENGINE 错误码

### 7.1 模型编译与结构校验

| 错误码 | 说明 |
| --- | --- |
| `CYCLIC_GRAPH` | 图中存在环 |
| `NO_START_NODE` | 不存在起始节点 |
| `NO_TERMINAL_NODE` | 不存在结束节点 |
| `UNREACHABLE_NODE` | 节点无法从任一起始节点到达 |
| `DISCONNECTED_SUBGRAPH` | 存在独立子图 |
| `NODE_NOT_ON_START_TO_END_PATH` | 节点不在任何合法起点到终点路径上 |
| `INVALID_CONDITION_BRANCH` | 条件分支结构非法 |
| `NODE_SCHEMA_INVALID` | 节点参数结构不合法 |
| `RESOURCE_REF_INVALID` | 资源引用非法 |
| `FLOW_OUTPUT_MAPPING_MISSING` | 发布时缺少 `flowOutputMapping` |
| `VARIABLE_NOT_DECLARED` | 变量未在流程级声明表中定义 |
| `VARIABLE_DEFAULT_TYPE_MISMATCH` | 变量默认值与声明类型不匹配 |
| `VARIABLE_DYNAMIC_ACCESS_NOT_ALLOWED` | 变量访问使用了不支持的动态 key 语法 |

### 7.2 表达式与执行

| 错误码 | 说明 |
| --- | --- |
| `NODE_INPUT_EVAL_FAILED` | 节点 `inputMapping` 求值失败 |
| `NODE_OUTPUT_EVAL_FAILED` | 节点 `outputMapping` 求值失败 |
| `FLOW_OUTPUT_EVAL_FAILED` | 流程 `flowOutputMapping` 求值失败 |
| `NODE_TIMEOUT` | 节点执行超时 |
| `RESOURCE_PERMIT_EXHAUSTED` | 资源并发许可不足 |
| `HTTP_CALL_FAILED` | HTTP 节点调用失败 |
| `DB_QUERY_FAILED` | 数据库查询节点失败 |
| `DB_UPDATE_FAILED` | 数据库更新节点失败 |
| `VARIABLE_ASSIGN_TYPE_MISMATCH` | 变量写入值与声明类型不兼容 |
| `RESOURCE_DISABLED` | 运行时解析到禁用资源 |

## 8. 使用约定

1. 一期管理接口、运行时结果查询接口以及未启用自定义 envelope 的运行时 HTTP 响应，统一返回 HTTP `200` 包装体，业务状态由 `code` 表示。
2. 节点执行表和 attempt 表中的 `error_code` 优先使用本文档中的 `ENGINE` / `SCHEDULER` 错误码。
3. 同一失败场景若同时存在平台错误码与底层异常信息，平台错误码写入 `errorCode`，底层异常摘要写入 `errorMessage`，详细长文本或堆栈写入 `error_detail`。
