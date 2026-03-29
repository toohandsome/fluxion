# Harness Runbook

这份文档按一个问题组织：

> **我刚改了什么，接下来该跑什么？**

关于 diagnostics / results / artifacts 的字段契约，统一看：

- [harness-spec.md](./harness-spec.md)

## 1. 默认起手式

如果你已经知道自己改了哪个文件，先跑：

```bash
python tools/harness/dev_loop.py --changed-file <你刚修改的文件>
```

如果你不确定影响范围，先跑：

```bash
python tools/harness/dev_loop.py
```

如果你改动涉及 Java 模块 / adapter / bridge，优先跑：

```bash
python tools/harness/dev_loop.py --changed-file <你刚修改的文件> --smart
```

说明：

- `--smart` = `--auto-java-bridge --maven-if-changed-java`
- 会自动补 real bridge
- 只有命中 Java 相关路径时才补 Maven

## 2. 我改了什么 -> 我该跑什么

### 2.1 我改了文档语义

典型文件：

- `docs/phase-1/runtime-semantics.md`
- `docs/phase-1/model-json-contract.md`
- `docs/phase-1/admin-api-contract.md`
- `docs/phase-1/admin-api/*`
- `docs/phase-1/error-codes.md`
- `docs/schema-pg.sql`

先跑：

```bash
python tools/harness/dev_loop.py --changed-file docs/phase-1/runtime-semantics.md
```

对应关系：

- `runtime-semantics.md` -> 主要看 `engine`，有时也会带到 `persistence` / `runtime-api` / `scheduler`
- `model-json-contract.md` -> 主要看 `contracts`
- `admin-api-contract.md` / `admin-api/*` -> 主要看 `contracts`，必要时带到 `runtime-api` / `scheduler` / `persistence`
- `error-codes.md` -> 主要看 diagnostics / contracts / 对应 suite
- `schema-pg.sql` -> 主要看 `persistence`

如果文档改的是正式语义，不要只改文档；通常还应补：

1. 对应 fixture
2. 最小相关 case
3. 对应 suite

### 2.2 我改了 contracts / modeler

典型文件：

- `fixtures/modeler/*`
- `tools/contracts/*`
- `fluxion-modeler/*`
- `docs/phase-1/model-json-contract.md`

先跑：

```bash
python tools/harness/dev_loop.py --suite contracts
```

如果你知道具体 case，再跑最小重跑：

```bash
python tools/harness/run_contracts.py --case <caseId>
```

### 2.3 我改了 engine 语义或 engine fixture

典型文件：

- `fixtures/engine/*`
- `fluxion-engine/*`
- `docs/phase-1/runtime-semantics.md`

建议顺序：

```bash
python tools/harness/dev_loop.py --suite engine --case engine/<caseId>
python tools/harness/dev_loop.py --suite engine
python tools/harness/dev_loop.py --changed-file fluxion-engine/src/main/java/... --smart
```

说明：

- 第一步：先确认最小 case
- 第二步：再跑整个 `engine` suite
- 第三步：如果涉及 Java 实现，`--smart` 会自动补 `engine-real`，必要时补 Maven

### 2.4 我改了 persistence / schema / repository / MyBatis 相关

典型文件：

- `fixtures/persistence/*`
- `fluxion-persistence-mybatisplus/*`
- `docs/schema-pg.sql`
- `docs/phase-1/runtime-semantics.md`

建议顺序：

```bash
python tools/harness/dev_loop.py --suite persistence --case persistence/<caseId>
python tools/harness/dev_loop.py --suite persistence
python tools/harness/dev_loop.py --changed-file fluxion-persistence-mybatisplus/src/main/java/... --smart
```

如果你明确在验证真实 Java bridge，可再补：

```bash
python tools/harness/dev_loop.py --suite persistence-real --case persistence/retry-attempt-snapshots
```

### 2.5 我改了 runtime-api

典型文件：

- `fixtures/runtime-api/*`
- `fluxion-runtime-api/*`
- `docs/phase-1/runtime-semantics.md`

建议顺序：

```bash
python tools/harness/dev_loop.py --suite runtime-api --case runtime-api/<caseId>
python tools/harness/dev_loop.py --suite runtime-api
```

如果改的是 Java 模块实现，再补：

```bash
python tools/harness/dev_loop.py --changed-file fluxion-runtime-api/src/main/java/... --smart
```

### 2.6 我改了 scheduler

典型文件：

- `fixtures/scheduler/*`
- `fluxion-scheduler/*`
- `docs/phase-1/runtime-semantics.md`

建议顺序：

```bash
python tools/harness/dev_loop.py --suite scheduler --case scheduler/<caseId>
python tools/harness/dev_loop.py --suite scheduler
```

如果改的是 Java 模块实现，再补：

```bash
python tools/harness/dev_loop.py --changed-file fluxion-scheduler/src/main/java/... --smart
```

### 2.7 我改了 harness runner / diagnostics / selective tests / dev loop

典型文件：

- `tools/harness/*`
- `tools/contracts/*`

建议顺序：

```bash
python tools/harness/dev_loop.py
```

如果改动涉及 Java bridge 编排、suite 数量、真实模块接桥，再补：

```bash
python tools/harness/dev_loop.py --changed-file tools/harness/run_java_adapter_bridge.py --smart
mvn -q -pl fluxion-test -am test
```

### 2.8 我只想重跑一个真实 Java bridge

```bash
python tools/harness/dev_loop.py --suite engine-real --case engine/<caseId>
python tools/harness/dev_loop.py --suite persistence-real --case persistence/<caseId>
```

适用场景：

- 你已经确认 Python reference harness 通过
- 你只想看 Java adapter / Spring / MyBatis / registry 接桥层是否正确

## 3. 什么时候直接用 --smart

满足以下任一情况，优先直接用：

```bash
python tools/harness/dev_loop.py --changed-file <你刚修改的文件> --smart
```

场景：

- 改了 `pom.xml`
- 改了 `fluxion-*/src/main/java/*`
- 改了 `fluxion-*/src/test/java/*`
- 改了 `fluxion-*/src/main/resources/*`
- 改了 `fluxion-test/*`
- 改了 `tools/harness/run_java_adapter_bridge.py`

`--smart` 的好处：

- 自动补 real bridge
- 只有在 Java 相关改动时才补 Maven
- JSON 输出会带 `mavenTriggeredBy`

## 4. 我怎么快速识别 harness 相关目录

### fixtures

- `fixtures/modeler/*`
- `fixtures/engine/*`
- `fixtures/runtime-api/*`
- `fixtures/scheduler/*`
- `fixtures/persistence/*`

### runners

- `tools/contracts/build_catalogs.py`
- `tools/harness/dev_loop.py`
- `tools/harness/run_contracts.py`
- `tools/harness/run_engine_cases.py`
- `tools/harness/run_runtime_api_cases.py`
- `tools/harness/run_scheduler_cases.py`
- `tools/harness/run_persistence_cases.py`
- `tools/harness/run_java_adapter_bridge.py`
- `tools/harness/selective_tests.py`
- `tools/harness/collect_results.py`

### Java bridge

- `fluxion-test/src/test/java/io/github/fluxion/test/harness/ReferenceHarnessBridgeTest.java`
- `fluxion-test/src/test/java/io/github/fluxion/test/harness/JavaAdapterHarnessExecutionTest.java`

## 5. 常用附录

### 5.1 suite -> runner 映射

| suite | runner |
|---|---|
| `contracts` | `python tools/harness/run_contracts.py` |
| `engine` | `python tools/harness/run_engine_cases.py` |
| `runtime-api` | `python tools/harness/run_runtime_api_cases.py` |
| `scheduler` | `python tools/harness/run_scheduler_cases.py` |
| `persistence` | `python tools/harness/run_persistence_cases.py` |
| `engine-real` | `python tools/harness/run_java_adapter_bridge.py --suite engine-real` |
| `persistence-real` | `python tools/harness/run_java_adapter_bridge.py --suite persistence-real` |

### 5.2 changed-files -> selective tests

统一使用：

```bash
python tools/harness/dev_loop.py --changed-file <path>
```

或：

```bash
python tools/harness/selective_tests.py --changed-file <path>
```

当前大致规则：

- model contract / modeler 改动 -> `contracts`
- runtime semantics / engine 改动 -> `engine`
- runtime-api 文档 / 模块改动 -> `runtime-api`
- scheduler 文档 / 模块改动 -> `scheduler`
- schema / persistence 模块改动 -> `persistence`

### 5.3 输出产物位置

统一汇总：

- `.artifacts/harness/summary.md`
- `.artifacts/harness/diagnostics.json`
- `.artifacts/harness/junit.xml`

suite 级产物：

- `.artifacts/harness/contracts/results.json`
- `.artifacts/harness/engine/results.json`
- `.artifacts/harness/runtime-api/results.json`
- `.artifacts/harness/scheduler/results.json`
- `.artifacts/harness/persistence/results.json`
