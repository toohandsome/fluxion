# Fluxion 基础反馈循环说明

## 1. 目标

围绕以下目标构建最小可执行反馈循环：

> 让 agent 能执行测试、CI 能产出可解析结果、失败信息能指向具体修复方向。

## 2. 方法论

### docs-first

正式行为以文档为准，尤其是：

- `docs/phase-1/runtime-semantics.md`
- `docs/phase-1/model-json-contract.md`
- `docs/phase-1/admin-api-contract.md`
- `docs/phase-1/admin-api/*`
- `docs/phase-1/error-codes.md`
- `docs/schema-pg.sql`

### fixture-first

场景和预期优先沉淀在 fixture 中，而不是散落在临时测试代码里。

### diagnostics-first

失败时除了 exit code，还要产出结构化诊断，便于：

- agent 自动定位
- CI 展示
- 后续脚本解析

## 3. 当前反馈循环由什么组成

### 输入

- docs
- fixtures
- changed files

### 执行

- `tools/contracts/build_catalogs.py`
- `tools/harness/run_*.py`
- `tools/harness/run_java_adapter_bridge.py`
- `tools/harness/dev_loop.py`
- `mvn -q -pl fluxion-test -am test`

### 输出

- `results.json`
- `diagnostics.json`
- `junit.xml`
- `summary.md`

## 4. 为什么对 agent 友好

因为失败信息不是纯文本，而是结构化对象，能直接提供：

- `errorCode`
- `fixHint`
- `docRefs`
- `suggestedCommand`
- `candidateFiles`
- `relatedFixture`
- `sourcePath`
- `lineHint`
- `attemptSummary`

## 5. 默认开发顺序

1. 先定位受影响的 suite / fixture
2. 先补或修改 fixture
3. 再改实现
4. 先跑最小 case
5. 再跑对应 suite
6. 最后跑 Maven bridge / 全量回归

## 6. 统一入口

推荐优先使用：

```bash
python tools/harness/dev_loop.py
```

更多命令见：

- [harness-entrypoints.md](./harness-entrypoints.md)
- [harness-spec.md](./harness-spec.md)
- [../../AGENTS.md](../../AGENTS.md)
