# Fluxion Agent Guide

本文件面向“接手仓库继续开发的 agent / 开发者”。

唯一目标：

> 任何涉及 engine / persistence / runtime-api / scheduler / contracts 的改动，都优先进入 Fluxion 的 harness 反馈循环。

## 1. 接手后先读什么

按顺序阅读：

1. `README.md`
2. `docs/doc-structure.md`
3. `docs/agent/index.md`
4. `docs/agent/harness-entrypoints.md`
5. `docs/agent/harness-spec.md`
6. `docs/phase-1/runtime-semantics.md`
7. `docs/phase-1/model-json-contract.md`
8. `docs/phase-1/error-codes.md`

## 2. 默认工作方式

遵循四个原则：

1. **docs-first**：正式语义以 `docs/phase-1/*` 为准。
2. **fixture-first**：语义改动先改 fixture，再改实现。
3. **diagnostics-first**：失败结果要能输出结构化 diagnostics。
4. **small feedback loop**：先最小 case，再 suite，再 Maven / 全量。

以下改动默认都应先走 harness：

- `fluxion-engine/*`
- `fluxion-persistence-mybatisplus/*`
- `fluxion-runtime-api/*`
- `fluxion-scheduler/*`
- `fixtures/*`
- `tools/harness/*`
- `tools/contracts/*`
- `docs/phase-1/runtime-semantics.md`
- `docs/phase-1/model-json-contract.md`
- `docs/phase-1/error-codes.md`
- `docs/schema-pg.sql`

## 3. 从哪里开始

优先使用统一入口：

```bash
python tools/harness/dev_loop.py --changed-file <你刚修改的文件>
```

如果还不确定影响范围：

```bash
python tools/harness/dev_loop.py
```

如果改动涉及 Java 模块 / adapter / bridge，优先使用：

```bash
python tools/harness/dev_loop.py --changed-file <你刚修改的文件> --smart
```

说明：

- `--smart` = 自动补 real bridge，并只在 Java 相关改动时补 Maven。
- 详细命令矩阵、changed-files 路由、Maven 触发规则，统一看：
  - `docs/agent/harness-entrypoints.md`

## 4. 修改时的最小约束

1. 改运行语义前，先确认 `docs/phase-1/runtime-semantics.md` 是否需要同步。
2. 改 `model_json` 契约前，先确认 `docs/phase-1/model-json-contract.md` 是否需要同步。
3. 新增或调整错误码前，先确认 `docs/phase-1/error-codes.md` 是否需要同步。
4. 改 diagnostics / results / junit 等结构化产物时，先看：
   - `docs/agent/harness-spec.md`

## 5. 输出产物在哪里看

- 给人看：`.artifacts/harness/summary.md`
- 给 CI / test UI 看：`.artifacts/harness/junit.xml`
- 给 agent / 自动修复流程看：`.artifacts/harness/diagnostics.json`
- suite 级明细：`.artifacts/harness/*/results.json`

## 6. 不要在这里重复维护什么

以下内容不要在 `AGENTS.md` 里展开复制，统一以专门文档为准：

- 命令矩阵 / suite 路由 / Maven 触发规则：
  - `docs/agent/harness-entrypoints.md`
- diagnostics / results / artifacts 字段契约：
  - `docs/agent/harness-spec.md`
- 正式产品语义与契约：
  - `docs/phase-1/*`
