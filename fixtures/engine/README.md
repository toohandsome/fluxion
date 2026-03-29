# Engine Scenario Fixtures

当前仓库还没有引擎实现代码，因此本目录先作为场景占位和 CI/agent 入口。

每个 `*.scenario.json` 都代表一个未来需要由真实引擎执行并验证的运行语义场景，例如：

- 条件分支与 `SKIPPED`
- fail-fast
- timeout / retry
- resource permit exhausted

现阶段 `tools/harness/run_engine_cases.py` 会：

1. 扫描这些场景
2. 产出统一的 `results.json` / `junit.xml`
3. 先将它们标记为 `skipped`

等引擎代码落地后，再把 runner 改成真实执行。
