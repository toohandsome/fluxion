# Fluxion 一期节点 Schema 详细定义

## 1. 文档目的

本文档用于定义一期 6 个基础节点的参数 Schema 详细结构，作为前端节点表单、后端节点参数校验以及 `graph_json -> model_json` 编译过程中的节点参数依据。

一期 6 个基础节点如下：

1. 日志节点 `log`
2. 变量处理节点 `variable`
3. 条件判断节点 `condition`
4. HTTP 请求节点 `http`
5. 数据库查询节点 `dbQuery`
6. 数据库更新节点 `dbUpdate`

说明：

- 变量处理节点统一承载“变量设置”和“变量转换”两种能力，通过 `mode` 区分。
- 一期节点参数协议统一采用 `schemaVersion = 1.0`。
- 一期表达式能力统一基于 `SpEL`，平台表达式占位采用 `${...}` 形式。

## 2. 通用 Schema 约定

### 2.1 顶层结构

所有节点 Schema 建议采用如下结构：

```json
{
  "schemaVersion": "1.0",
  "nodeType": "http",
  "displayName": "HTTP 请求节点",
  "description": "向外部 HTTP 服务发起请求",
  "ports": {
    "inputs": ["in"],
    "outputs": ["out"]
  },
  "fields": []
}
```

### 2.2 顶层字段说明

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `schemaVersion` | string | 是 | Schema 版本 |
| `nodeType` | string | 是 | 节点类型标识 |
| `displayName` | string | 是 | 节点显示名称 |
| `description` | string | 否 | 节点描述 |
| `ports` | object | 是 | 输入输出端口定义 |
| `fields` | array | 是 | 业务配置字段 |

### 2.3 通用字段定义

字段最小结构：

```json
{
  "name": "method",
  "label": "请求方法",
  "type": "select",
  "required": true,
  "defaultValue": "GET",
  "description": "HTTP Method"
}
```

字段属性说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | string | 是 | 字段名称 |
| `label` | string | 是 | 前端显示标签 |
| `type` | string | 是 | 字段类型 |
| `required` | boolean | 否 | 是否必填 |
| `defaultValue` | any | 否 | 默认值 |
| `options` | array | 否 | 选项列表 |
| `placeholder` | string | 否 | 占位提示 |
| `description` | string | 否 | 字段描述 |
| `expressionSupported` | boolean | 否 | 是否支持表达式 |
| `resourceSelectable` | boolean | 否 | 是否支持资源选择 |
| `visibleWhen` | object | 否 | 条件显示规则 |
| `validation` | object | 否 | 校验规则 |

### 2.4 表达式作用域与映射语义

一期节点级表达式固定遵循以下作用域约定：

1. 稳定命名空间固定为 `request`、`schedule`、`vars`、`instance`、`nodes`。
2. `request` 仅在 HTTP 触发场景有值；`schedule` 仅在调度触发场景有值。
3. `instance.traceId` 是访问链路追踪标识的唯一正式路径，不使用裸 `${traceId}`。
4. `nodes.<nodeId>.output` 表示某个已形成稳定输出的节点结果；跨节点引用统一通过该路径完成。
5. 当前节点执行器返回的原始结果只以局部命名空间 `raw` 暴露给本节点 `outputMapping`，不进入全局稳定上下文。
6. `inputMapping` 负责把稳定上下文映射为当前节点执行输入。
7. `outputMapping` 负责把当前节点 `raw` 归一为当前节点稳定输出。
8. `outputMapping` 不直接写入 `vars`；如需共享变量写入，应通过显式 `variable` 节点完成。
9. 流程级最终输出由设计态 `flow.outputMapping` 定义，发布后编译为运行态 `flowOutputMapping`。
10. 资源型节点中的 `resourceRef` 一期正式含义固定为 `resourceCode`。
11. `vars.<name>` 中的根变量名 `<name>` 必须来自 `graph_json.variables` 的正式声明。
12. 一期不支持 `vars[someExpr]` 这类动态 key 访问；变量访问必须能在编译期解析出固定根变量名。
13. 若根变量声明类型为 `JSON`，则允许继续访问其嵌套属性，如 `vars.userInfo.name`。

### 2.5 通用运行策略隐式继承

所有节点统一支持以下运行策略字段，但这些字段不再作为节点 Schema 的顶层显式声明，而是在流程发布时由平台自动写入运行时 `runtimePolicy`：

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `timeoutMs` | number | `3000` | 节点超时时间，毫秒 |
| `retry` | number | `0` | 重试次数 |
| `retryIntervalMs` | number | `1000` | 重试间隔，毫秒 |
| `logEnabled` | boolean | `true` | 是否记录输入输出日志 |

平台默认运行策略快照：

```json
{
  "timeoutMs": 3000,
  "retry": 0,
  "retryIntervalMs": 1000,
  "logEnabled": true
}
```

### 2.6 外部副作用节点策略

对会访问外部系统或数据源的节点，一期设计态必须显式声明副作用策略字段。当前一期涉及的节点包括 `http` 和 `dbUpdate`；其中 `http` 节点可通过 `READ_ONLY` 显式声明其为只读调用。

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `effectPolicyType` | `select` | 是 | `READ_ONLY` /   `COMPENSABLE` |
 | `compensationStrategy` | `text` | 条件必填 | `effectPolicyType = COMPENSABLE` 时必填，用于描述补偿方式、补偿入口或人工处置方案 |

规则：

1. `READ_ONLY` 表示节点不产生外部业务副作用。
2. `COMPENSABLE` 表示节点必须声明失败后的补偿策略；一期只做声明和审计，不自动执行补偿。
3. 非外部副作用节点不应携带上述字段。
4. 发布时后端将这些设计态字段编译为运行态 `sideEffectPolicy` 快照。

## 3. 日志节点 `log`

### 3.1 节点语义

用于输出一条日志记录，不产生外部业务副作用，一般用于调试、审计辅助和流程运行观测。

### 3.2 端口定义

```json
{
  "inputs": ["in"],
  "outputs": ["out"]
}
```

### 3.3 业务字段

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `level` | `select` | 是 | `INFO` | 日志级别 |
| `message` | `expression` | 是 | 无 | 日志内容，支持表达式 |
| `loggerName` | `string` | 否 | `fluxion.flow` | Logger 名称 |
| `printContext` | `boolean` | 否 | `false` | 是否输出上下文摘要 |

### 3.4 Schema 定义

```json
{
  "schemaVersion": "1.0",
  "nodeType": "log",
  "displayName": "日志节点",
  "description": "输出流程日志信息",
  "ports": {
    "inputs": ["in"],
    "outputs": ["out"]
  },
  "fields": [
    {
      "name": "level",
      "label": "日志级别",
      "type": "select",
      "required": true,
      "defaultValue": "INFO",
      "options": ["DEBUG", "INFO", "WARN", "ERROR"]
    },
    {
      "name": "message",
      "label": "日志内容",
      "type": "expression",
      "required": true,
      "expressionSupported": true,
      "placeholder": "${vars.userId}"
    },
    {
      "name": "loggerName",
      "label": "Logger 名称",
      "type": "string",
      "required": false,
      "defaultValue": "fluxion.flow"
    },
    {
      "name": "printContext",
      "label": "输出上下文摘要",
      "type": "boolean",
      "required": false,
      "defaultValue": false
    }
  ]
}
```

### 3.5 配置示例

```json
{
  "config": {
    "level": "INFO",
    "message": "开始处理用户: ${vars.userId}",
    "loggerName": "fluxion.user.sync",
    "printContext": false
  }
}
```

## 4. 变量处理节点 `variable`

### 4.1 节点语义

变量处理节点统一承载两种模式：

1. `SET`
   将表达式结果直接写入变量。
2. `TRANSFORM`
   基于源数据表达式进行转换后写入变量。

补充约定：

1. `variable` 节点只允许写入流程级变量；一期不引入节点局部变量或块级变量。
2. `targetVar` 在发布时必须能解析到 `graph_json.variables` 中的正式声明。

### 4.2 端口定义

```json
{
  "inputs": ["in"],
  "outputs": ["out"]
}
```

### 4.3 业务字段

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `mode` | `select` | 是 | `SET` | 处理模式 |
| `targetVar` | `string` | 是 | 无 | 目标变量名 |
| `valueExpr` | `expression` | `SET` 时是 | 无 | 直接赋值表达式 |
| `sourceExpr` | `expression` | `TRANSFORM` 时否 | 无 | 转换源表达式 |
| `transformExpr` | `expression` | `TRANSFORM` 时是 | 无 | 转换表达式 |
| `writeScope` | `select` | 否 | `FLOW` | 写入作用域 |
| `overwrite` | `boolean` | 否 | `true` | 是否覆盖已有变量 |

规则：

1. `writeScope` 一期固定为 `FLOW`，表示写入运行时 `vars` 的流程级共享变量空间。
2. `overwrite = true` 时，无论目标变量当前值为何，都以本次求值结果覆盖。
3. `overwrite = false` 时，仅当目标变量当前值为 `null` 时才写入；若当前值非 `null`，则本节点按“跳过写入但执行成功”处理。
4. `SET` 模式下使用 `valueExpr` 的求值结果作为待写入值；`TRANSFORM` 模式下使用 `transformExpr` 的求值结果作为待写入值。
5. 待写入值若非 `null`，运行时必须校验其与 `targetVar` 的声明类型兼容；不兼容时节点失败，错误码建议使用 `VARIABLE_ASSIGN_TYPE_MISMATCH`。
6. 发布阶段至少要校验 `targetVar` 是否已声明；对表达式结果类型只做保守校验，不要求一期完成完整静态类型推导。

### 4.4 Schema 定义

```json
{
  "schemaVersion": "1.0",
  "nodeType": "variable",
  "displayName": "变量处理节点",
  "description": "支持变量设置和变量转换",
  "ports": {
    "inputs": ["in"],
    "outputs": ["out"]
  },
  "fields": [
    {
      "name": "mode",
      "label": "处理模式",
      "type": "select",
      "required": true,
      "defaultValue": "SET",
      "options": ["SET", "TRANSFORM"]
    },
    {
      "name": "targetVar",
      "label": "目标变量名",
      "type": "string",
      "required": true,
      "validation": {
        "pattern": "^[a-zA-Z_][a-zA-Z0-9_]*$"
      }
    },
    {
      "name": "valueExpr",
      "label": "赋值表达式",
      "type": "expression",
      "required": false,
      "expressionSupported": true,
      "visibleWhen": {
        "mode": "SET"
      }
    },
    {
      "name": "sourceExpr",
      "label": "源表达式",
      "type": "expression",
      "required": false,
      "expressionSupported": true,
      "visibleWhen": {
        "mode": "TRANSFORM"
      }
    },
    {
      "name": "transformExpr",
      "label": "转换表达式",
      "type": "expression",
      "required": false,
      "expressionSupported": true,
      "visibleWhen": {
        "mode": "TRANSFORM"
      }
    },
    {
      "name": "writeScope",
      "label": "写入作用域",
      "type": "select",
      "required": false,
      "defaultValue": "FLOW",
      "options": ["FLOW"]
    },
    {
      "name": "overwrite",
      "label": "覆盖已有变量",
      "type": "boolean",
      "required": false,
      "defaultValue": true
    }
  ]
}
```

### 4.5 配置示例

`SET` 模式：

```json
{
  "config": {
    "mode": "SET",
    "targetVar": "userId",
    "valueExpr": "${request.query.userId}",
    "writeScope": "FLOW",
    "overwrite": true
  }
}
```

`TRANSFORM` 模式：

```json
{
  "config": {
    "mode": "TRANSFORM",
    "targetVar": "userNameUpper",
    "sourceExpr": "${vars.userInfo.name}",
    "transformExpr": "${vars.userInfo.name == null ? null : vars.userInfo.name.toUpperCase()}",
    "writeScope": "FLOW",
    "overwrite": true
  }
}
```

## 5. 条件判断节点 `condition`

### 5.1 节点语义

根据条件表达式结果选择后续分支。一期固定输出两个分支：`true` 和 `false`。

### 5.2 端口定义

```json
{
  "inputs": ["in"],
  "outputs": ["true", "false"]
}
```

### 5.3 业务字段

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `conditionExpr` | `expression` | 是 | 无 | 条件表达式 |
| `nullAsFalse` | `boolean` | 否 | `true` | 表达式结果为 null 时是否按 false 处理 |
| `description` | `string` | 否 | 无 | 条件说明 |

### 5.4 Schema 定义

```json
{
  "schemaVersion": "1.0",
  "nodeType": "condition",
  "displayName": "条件判断节点",
  "description": "根据表达式结果选择 true 或 false 分支",
  "ports": {
    "inputs": ["in"],
    "outputs": ["true", "false"]
  },
  "fields": [
    {
      "name": "conditionExpr",
      "label": "条件表达式",
      "type": "expression",
      "required": true,
      "expressionSupported": true
    },
    {
      "name": "nullAsFalse",
      "label": "null 视为 false",
      "type": "boolean",
      "required": false,
      "defaultValue": true
    },
    {
      "name": "description",
      "label": "条件说明",
      "type": "string",
      "required": false
    }
  ]
}
```

### 5.5 配置示例

```json
{
  "config": {
    "conditionExpr": "${vars.userInfo != null && vars.userInfo.active == true}",
    "nullAsFalse": true,
    "description": "判断用户是否有效"
  }
}
```

## 6. HTTP 请求节点 `http`

### 6.1 节点语义

通过资源连接信息向外部 HTTP 服务发起请求，支持路径、Query、Header、Body 参数映射。

### 6.2 端口定义

```json
{
  "inputs": ["in"],
  "outputs": ["out"]
}
```

### 6.3 业务字段

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `resourceRef` | `resourceRef` | 是 | 无 | HTTP 资源引用 |
| `method` | `select` | 是 | `GET` | 请求方法 |
| `path` | `string` | 是 | 无 | 请求路径，支持模板变量 |
| `queryParams` | `kvList` | 否 | `[]` | Query 参数 |
| `headers` | `kvList` | 否 | `[]` | 请求头 |
| `bodyType` | `select` | 否 | `NONE` | 请求体类型 |
| `bodyJson` | `json` | 否 | 无 | JSON 请求体，`bodyType = JSON` 时使用 |
| `formFields` | `kvList` | 否 | `[]` | 表单字段，`bodyType = FORM` 时使用 |
| `rawBody` | `text` | 否 | 无 | 原始文本请求体，`bodyType = RAW` 时使用 |
| `expectedStatus` | `string` | 否 | `2xx` | 期望状态码范围 |
| `effectPolicyType` | `select` | 是 | 无 | 副作用策略：`READ_ONLY` /  `COMPENSABLE` |
 | `compensationStrategy` | `text` | 条件必填 | 无 | `effectPolicyType = COMPENSABLE` 时必填 |

当前节点局部 `raw` 最小字段约定：

1. `raw.statusCode`：HTTP 状态码
2. `raw.headers`：响应头对象
3. `raw.body`：响应体内容
4. 跨节点复用 HTTP 结果时，统一通过 `nodes.<nodeId>.output.*` 引用；如需写共享变量，使用显式 `variable` 节点

### 6.4 Schema 定义

```json
{
  "schemaVersion": "1.0",
  "nodeType": "http",
  "displayName": "HTTP 请求节点",
  "description": "向外部 HTTP 服务发起请求",
  "ports": {
    "inputs": ["in"],
    "outputs": ["out"]
  },
  "fields": [
    {
      "name": "resourceRef",
      "label": "HTTP 资源",
      "type": "resourceRef",
      "required": true,
      "resourceSelectable": true
    },
    {
      "name": "method",
      "label": "请求方法",
      "type": "select",
      "required": true,
      "defaultValue": "GET",
      "options": ["GET", "POST", "PUT", "DELETE", "PATCH"]
    },
    {
      "name": "path",
      "label": "请求路径",
      "type": "string",
      "required": true,
      "expressionSupported": true
    },
    {
      "name": "queryParams",
      "label": "Query 参数",
      "type": "kvList",
      "required": false,
      "defaultValue": []
    },
    {
      "name": "headers",
      "label": "请求头",
      "type": "kvList",
      "required": false,
      "defaultValue": []
    },
    {
      "name": "bodyType",
      "label": "请求体类型",
      "type": "select",
      "required": false,
      "defaultValue": "NONE",
      "options": ["NONE", "JSON", "FORM", "RAW"]
    },
    {
      "name": "bodyJson",
      "label": "JSON 请求体",
      "type": "json",
      "required": false,
      "visibleWhen": {
        "bodyType": "JSON"
      }
    },
    {
      "name": "formFields",
      "label": "表单字段",
      "type": "kvList",
      "required": false,
      "defaultValue": [],
      "visibleWhen": {
        "bodyType": "FORM"
      }
    },
    {
      "name": "rawBody",
      "label": "原始请求体",
      "type": "text",
      "required": false,
      "visibleWhen": {
        "bodyType": "RAW"
      }
    },
    {
      "name": "expectedStatus",
      "label": "期望状态码",
      "type": "string",
      "required": false,
      "defaultValue": "2xx"
    },
    {
      "name": "effectPolicyType",
      "label": "副作用策略",
      "type": "select",
      "required": true,
      "options": ["READ_ONLY", "COMPENSABLE"]
    } ,
    {
      "name": "compensationStrategy",
      "label": "补偿策略说明",
      "type": "text",
      "required": false,
      "visibleWhen": {
        "effectPolicyType": "COMPENSABLE"
      }
    }
  ]
}
```

### 6.5 配置示例

```json
{
  "config": {
    "resourceRef": "user_service",
    "method": "GET",
    "path": "/users/${vars.userId}",
    "queryParams": [
      {
        "key": "withDetail",
        "value": "true"
      }
    ],
    "headers": [
      {
        "key": "X-Trace-Id",
        "value": "${instance.traceId}"
      }
    ],
    "bodyType": "NONE",
    "expectedStatus": "2xx",
    "effectPolicyType": "READ_ONLY"
  }
}
```

## 7. 数据库查询节点 `dbQuery`

### 7.1 节点语义

执行参数化查询 SQL，并返回单条或多条记录结果。

### 7.2 端口定义

```json
{
  "inputs": ["in"],
  "outputs": ["out"]
}
```

### 7.3 业务字段

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `resourceRef` | `resourceRef` | 是 | 无 | 数据源资源引用 |
| `sql` | `text` | 是 | 无 | 查询 SQL |
| `params` | `kvList` | 否 | `[]` | SQL 参数映射 |
| `resultMode` | `select` | 否 | `LIST` | 结果模式 |
| `fetchSize` | `number` | 否 | `200` | 查询抓取大小 |

SQL 参数规则：

1. 只允许使用命名参数，占位形式固定为 `:paramName`。
2. 一期不支持 `?`、`#{}`、`${}` 形式的 SQL 占位。
3. 所有动态值必须通过 `params` 传入，`params.key` 不带冒号。

当前节点局部 `raw` 最小字段约定：

1. `resultMode = ONE` 时，推荐提供 `raw.row` 和 `raw.rowCount`
2. `resultMode = LIST` 时，推荐提供 `raw.rows` 和 `raw.rowCount`
3. 跨节点复用查询结果时，统一通过 `nodes.<nodeId>.output.*` 引用；如需写共享变量，使用显式 `variable` 节点

### 7.4 Schema 定义

```json
{
  "schemaVersion": "1.0",
  "nodeType": "dbQuery",
  "displayName": "数据库查询节点",
  "description": "执行参数化 SQL 查询",
  "ports": {
    "inputs": ["in"],
    "outputs": ["out"]
  },
  "fields": [
    {
      "name": "resourceRef",
      "label": "数据源",
      "type": "resourceRef",
      "required": true,
      "resourceSelectable": true
    },
    {
      "name": "sql",
      "label": "查询 SQL",
      "type": "text",
      "required": true
    },
    {
      "name": "params",
      "label": "参数映射",
      "type": "kvList",
      "required": false,
      "defaultValue": []
    },
    {
      "name": "resultMode",
      "label": "结果模式",
      "type": "select",
      "required": false,
      "defaultValue": "LIST",
      "options": ["ONE", "LIST"]
    },
    {
      "name": "fetchSize",
      "label": "抓取大小",
      "type": "number",
      "required": false,
      "defaultValue": 200,
      "validation": {
        "min": 1,
        "max": 10000
      }
    }
  ]
}
```

### 7.5 配置示例

```json
{
  "config": {
    "resourceRef": "main_db",
    "sql": "select id, name, status from sys_user where id = :userId",
    "params": [
      {
        "key": "userId",
        "value": "${vars.userId}"
      }
    ],
    "resultMode": "ONE",
    "fetchSize": 200
  }
}
```

## 8. 数据库更新节点 `dbUpdate`

### 8.1 节点语义

执行参数化更新 SQL，包括 `insert`、`update`、`delete` 等操作，并返回受影响行数。

### 8.2 端口定义

```json
{
  "inputs": ["in"],
  "outputs": ["out"]
}
```

### 8.3 业务字段

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `resourceRef` | `resourceRef` | 是 | 无 | 数据源资源引用 |
| `sql` | `text` | 是 | 无 | 更新 SQL |
| `params` | `kvList` | 否 | `[]` | SQL 参数映射 |
| `returnGeneratedKeys` | `boolean` | 否 | `false` | 是否返回主键 |
| `effectPolicyType` | `select` | 是 | 无 | 副作用策略：  `COMPENSABLE` |
 | `compensationStrategy` | `text` | 条件必填 | 无 | `effectPolicyType = COMPENSABLE` 时必填 |

SQL 参数规则：

1. 只允许使用命名参数，占位形式固定为 `:paramName`。
2. 一期不支持 `?`、`#{}`、`${}` 形式的 SQL 占位。
3. 所有动态值必须通过 `params` 传入，`params.key` 不带冒号。

当前节点局部 `raw` 最小字段约定：

1. `raw.affectedRows`：受影响行数
2. `raw.generatedKeys`：返回主键集合；未开启 `returnGeneratedKeys` 时允许为 `null` 或空数组
3. 跨节点复用更新结果时，统一通过 `nodes.<nodeId>.output.*` 引用；如需写共享变量，使用显式 `variable` 节点

### 8.4 Schema 定义

```json
{
  "schemaVersion": "1.0",
  "nodeType": "dbUpdate",
  "displayName": "数据库更新节点",
  "description": "执行参数化 SQL 更新",
  "ports": {
    "inputs": ["in"],
    "outputs": ["out"]
  },
  "fields": [
    {
      "name": "resourceRef",
      "label": "数据源",
      "type": "resourceRef",
      "required": true,
      "resourceSelectable": true
    },
    {
      "name": "sql",
      "label": "更新 SQL",
      "type": "text",
      "required": true
    },
    {
      "name": "params",
      "label": "参数映射",
      "type": "kvList",
      "required": false,
      "defaultValue": []
    },
    {
      "name": "returnGeneratedKeys",
      "label": "返回主键",
      "type": "boolean",
      "required": false,
      "defaultValue": false
    },
    {
      "name": "effectPolicyType",
      "label": "副作用策略",
      "type": "select",
      "required": true,
      "options": [ "COMPENSABLE"]
    } ,
    {
      "name": "compensationStrategy",
      "label": "补偿策略说明",
      "type": "text",
      "required": false,
      "visibleWhen": {
        "effectPolicyType": "COMPENSABLE"
      }
    }
  ]
}
```

### 8.5 配置示例

```json
{
  "config": {
    "resourceRef": "main_db",
    "sql": "update sys_user set status = :status where id = :userId",
    "params": [
      {
        "key": "status",
        "value": "ACTIVE"
      },
      {
        "key": "userId",
        "value": "${vars.userId}"
      }
    ],
    "returnGeneratedKeys": false,
    "effectPolicyType": "COMPENSABLE",
    "compensationStrategy": "${vars.userId}"
  }
}
```

## 9. 落地约定

### 9.1 前端约定

1. 前端节点配置表单根据本文件 Schema 动态渲染。
2. `visibleWhen` 用于控制字段显隐。
3. 资源类字段通过资源管理接口动态下拉。
4. 表达式编辑器需提供基于 `request`、`schedule`、`vars`、`instance`、`nodes` 的自动补全。
5. 编辑节点级表达式时，节点引用选择器默认只展示当前节点拓扑上可稳定引用的上游节点；编辑流程级 `flow.outputMapping` 时展示全流程节点。
6. 编辑当前节点 `outputMapping` 时，表达式编辑器额外暴露局部 `raw` 命名空间。
7. 前端应为内置节点提供默认 `outputMapping` 模板，推荐如下：
   `log` -> 空对象模板；
   `variable` -> 空对象模板；
   `condition` -> 空对象模板；
   `http` -> `{ "statusCode": "${raw.statusCode}", "headers": "${raw.headers}", "body": "${raw.body}" }`；
   `dbQuery` -> `ONE` 模式默认 `{ "row": "${raw.row}", "rowCount": "${raw.rowCount}" }`，`LIST` 模式默认 `{ "rows": "${raw.rows}", "rowCount": "${raw.rowCount}" }`；
   `dbUpdate` -> `{ "affectedRows": "${raw.affectedRows}", "generatedKeys": "${raw.generatedKeys}" }`。
8. 前端应为 `flow.outputMapping` 提供默认模板向导，至少支持“单终点输出”“变量汇总”“空对象”三种起始模板。

### 9.2 后端约定

1. 后端保存 `graph_json` 时应校验必填字段和字段类型。
2. 发布时后端应将节点 Schema 与节点配置进行二次编译校验。
3. 对 `http` 和 `dbUpdate` 节点，后端发布校验时必须校验副作用策略字段，并编译为运行态 `sideEffectPolicy`。

### 9.3 兼容约定

1. 一期 Schema 版本固定为 `1.0`。
2. 后续字段扩展必须保持向后兼容。
3. 如节点能力升级，应优先新增可选字段，而不是修改既有字段语义。
