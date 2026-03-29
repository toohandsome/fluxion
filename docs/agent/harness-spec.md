# Harness Spec

本文档定义 Fluxion harness 的稳定约定：输入、执行单元、输出产物以及结构化 diagnostics 的最小字段语义。

它的目标不是重复一期业务语义，而是回答：

1. harness 的“执行单位”是什么
2. CI / agent 应该读取哪些产物
3. 哪些字段可以视为稳定契约

## 1. 适用范围

本规范覆盖：

- `tools/contracts/*`
- `tools/harness/*`
- `.artifacts/harness/*`
- `fluxion-test` 中对 reference harness / real bridge 的桥接执行

本规范**不**定义：

- 一期业务语义本身：以 `docs/phase-1/*` 为准
- 单个 suite 的具体实现细节：以对应 runner 为准

## 2. 核心对象

### 2.1 suite

当前主要 suite：

- `contracts`
- `engine`
- `runtime-api`
- `scheduler`
- `persistence`
- `engine-real`
- `persistence-real`

约定：

- Python reference harness 主要对应前五个 suite
- `*-real` 表示通过真实 Java 模块 / adapter bridge 执行

### 2.2 caseId

`caseId` 是最小执行单位标识，格式约定为：

```text
<suite>/<case-name>
```

例如：

- `contracts/doc-sync`
- `engine/branch-skipped`
- `persistence/retry-attempt-snapshots`
- `runtime-api/sync-success`

约定：

- `caseId` 应稳定、可重跑、可用于 diagnostics 反查 fixture
- 若 suite 不支持某 case，runner 应给出明确失败信息，而不是静默跳过

### 2.3 fixture

fixture 是 harness 的场景输入与期望来源。

约定：

- 语义改动优先改 fixture
- diagnostics 中应尽量能反向定位到 fixture

## 3. 标准执行入口

默认统一入口：

```bash
python tools/harness/dev_loop.py
```

执行入口的职责边界：

- `dev_loop.py`
  - 统一编排入口
  - changed-files -> selective tests
  - 可选补跑 Java bridge / Maven
- `run_*.py`
  - 单 suite 执行器
- `run_java_adapter_bridge.py`
  - Java adapter registry / real module bridge 执行入口
- `mvn -q -pl fluxion-test -am test`
  - Java 侧总桥接回归

更细的命令矩阵见：

- [harness-entrypoints.md](./harness-entrypoints.md)

## 4. 输出产物

统一位于：

```text
.artifacts/harness/
```

### 4.1 面向人类阅读

- `summary.md`

用途：

- 本地快速查看
- CI job summary

### 4.2 面向测试平台 / CI

- `junit.xml`

用途：

- CI test report
- 测试平台聚合展示

### 4.3 面向 agent / 自动修复流程

- `diagnostics.json`
- 各 suite 下的 `results.json`

用途：

- 结构化定位问题
- 构建修复建议
- 追踪 case / fixture / 文档引用关系

## 5. diagnostics 最小字段约定

`diagnostics.json` 中，以下字段应视为**优先保持稳定**的 agent-facing 契约：

| 字段 | 含义 |
| --- | --- |
| `suite` | 所属 suite |
| `caseId` | 最小执行单位标识 |
| `stage` | 失败阶段，例如 contract / engine / persistence / bridge |
| `errorCode` | 可归类的错误码 |
| `message` | 面向人类的简短失败说明 |
| `fixHint` | 直接修复方向 |
| `docRefs` | 相关文档引用 |
| `suggestedCommand` | 推荐重跑命令 |
| `candidateFiles` | 优先排查文件 |
| `relatedFixture` | 关联 fixture |
| `sourcePath` | 更精确的源码 / 文档路径提示 |
| `lineHint` | 行号或邻近位置提示 |
| `attemptSummary` | attempt 级失败摘要 |

说明：

- `message` 面向阅读
- `errorCode + fixHint + candidateFiles + docRefs` 面向 agent 修复决策
- `sourcePath + lineHint` 面向快速跳转定位
- `attemptSummary` 面向重试类 / at-least-once 语义问题定位

## 6. results.json 最小约定

各 suite 下的 `results.json` 至少应能表达：

- suite 名称
- case 级通过 / 失败 / 跳过状态
- 失败摘要
- 可关联 diagnostics 的标识

允许不同 suite 有扩展字段，但不应破坏上述最小可解析能力。

## 7. Maven 补跑说明

`dev_loop.py` 支持按 changed files 决定是否补跑：

```bash
mvn -q -pl fluxion-test -am test
```

当 Maven 被补跑时，JSON 输出中：

- `withMaven` 表示是否补跑
- `mavenMode` 表示触发模式
- `mavenTriggeredBy` 表示触发原因

`mavenTriggeredBy` 的推荐用途：

- 让 agent 解释“为什么这次需要 Java 回归”
- 让 CI 做可视化展示

## 8. 兼容性原则

1. 新增字段优先向后兼容，不删除已有稳定字段。
2. 若必须调整 `diagnostics.json` / `results.json` 的稳定字段，应同步更新：
   - 本文档
   - 相关 runner
   - 生成的 schema / catalog（如适用）
3. README 与 AGENTS 只保留摘要，不重复维护本规范细节。

## 9. 单一事实源

- harness 执行入口与命令矩阵：
  - [harness-entrypoints.md](./harness-entrypoints.md)
- harness 方法论：
  - [harness-feedback-loop.md](./harness-feedback-loop.md)
- harness 输出与字段契约：
  - **本文档**
- 一期正式语义与业务契约：
  - `docs/phase-1/*`
