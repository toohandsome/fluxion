# Fluxion Agent / Harness 首页

面向对象：

- 接手开发的 agent
- 本地开发者
- CI / 自动修复流程

这组文档主要解决三件事：

1. 第一次接手时先看什么
2. 改代码时该跑什么
3. 失败后去哪里定位问题

## 1. 第一次接手看什么

建议顺序：

1. [../../README.md](../../README.md)
2. [../doc-structure.md](../doc-structure.md)
3. [harness-entrypoints.md](./harness-entrypoints.md)
4. [harness-spec.md](./harness-spec.md)
5. [harness-feedback-loop.md](./harness-feedback-loop.md)
6. [../phase-1/runtime-semantics.md](../phase-1/runtime-semantics.md)
7. [../phase-1/model-json-contract.md](../phase-1/model-json-contract.md)
8. [../phase-1/error-codes.md](../phase-1/error-codes.md)

你会快速建立三层认知：

- **项目与文档分层**：`README.md`、`doc-structure.md`
- **harness 怎么跑**：`harness-entrypoints.md`
- **harness 输出长什么样**：`harness-spec.md`

## 2. 改代码时看什么

优先看：

- [harness-entrypoints.md](./harness-entrypoints.md)
- [harness-feedback-loop.md](./harness-feedback-loop.md)

如果你关心“我改了什么 -> 我该跑什么”，直接看：

- [harness-entrypoints.md](./harness-entrypoints.md)

如果你关心工作原则，先看：

- [harness-feedback-loop.md](./harness-feedback-loop.md)

最常用命令：

```bash
python tools/harness/dev_loop.py --changed-file <你刚修改的文件>
python tools/harness/dev_loop.py --changed-file <你刚修改的文件> --smart
python tools/harness/dev_loop.py --suite engine --case engine/branch-skipped
```

## 3. 排查失败时看什么

优先看：

- [harness-spec.md](./harness-spec.md)
- [../phase-1/runtime-semantics.md](../phase-1/runtime-semantics.md)
- [../phase-1/model-json-contract.md](../phase-1/model-json-contract.md)
- [../phase-1/error-codes.md](../phase-1/error-codes.md)

排查时通常按这个顺序：

1. 看 `.artifacts/harness/diagnostics.json`
2. 看对应 suite 的 `results.json`
3. 根据 `errorCode` / `fixHint` / `candidateFiles` / `sourcePath` / `lineHint` 回跳源码或文档
4. 用 `suggestedCommand` 或最小 case 重跑

常看产物：

- `.artifacts/harness/diagnostics.json`
- `.artifacts/harness/junit.xml`
- `.artifacts/harness/summary.md`
- `.artifacts/harness/*/results.json`

## 4. 三份核心文档各负责什么

- [harness-entrypoints.md](./harness-entrypoints.md)
  - 运行手册
  - 改什么跑什么
  - `--smart` / bridge / Maven 触发方式

- [harness-spec.md](./harness-spec.md)
  - diagnostics / results / artifacts 的字段契约
  - suite / caseId / fixture 的最小约定

- [harness-feedback-loop.md](./harness-feedback-loop.md)
  - docs-first / fixture-first / diagnostics-first 方法论

## 5. 一条默认建议

如果你什么都不确定，就先跑：

```bash
python tools/harness/dev_loop.py --changed-file <你刚修改的文件>
```

如果改动明显涉及 Java 模块 / adapter / bridge，就先跑：

```bash
python tools/harness/dev_loop.py --changed-file <你刚修改的文件> --smart
```
