# Fluxion

Fluxion 是一个基于 Spring Boot 的可视化流程编排系统，用于把常见业务集成逻辑沉淀为可设计、可发布、可执行、可监控、可治理的流程。

## 项目定位

Fluxion 提供两种产品形态：

1. `Starter`：以 Spring Boot Starter 方式嵌入业务系统，核心能力与 `Server` 等价。
2. `Server`：基于 `Starter` 的独立部署形态，默认启用 Web/API 与前端页面能力。

一期设计边界：

- 主流程必须是 DAG
- 默认执行语义为 `at-least-once`
- 不提供分布式事务，由重试和补偿策略兜底
- 插件、脚本、流式处理等高阶能力按阶段推进

## 当前仓库状态

当前仓库处于“一期开发基线”阶段，已包含：

- 一期正式需求与技术方案
- 一期运行语义与各类契约文档
- 一期数据库 DDL
- Maven 多模块骨架

## 文档导航

建议从这里进入：

- 文档总入口与职责分层：[docs/doc-structure.md](./docs/doc-structure.md)
- 通用规范（技术栈、响应规范、状态码）：[docs/base.md](./docs/base.md)
- 分期路线图：[docs/roadmap.md](./docs/roadmap.md)
- 一期需求基线：[docs/phase-1/requirements.md](./docs/phase-1/requirements.md)
- 一期技术方案：[docs/phase-1/technical-solution.md](./docs/phase-1/technical-solution.md)
- 一期运行语义：[docs/phase-1/runtime-semantics.md](./docs/phase-1/runtime-semantics.md)
- 一期计划：[docs/phase-1/plan.md](./docs/phase-1/plan.md)
- 一期数据库脚本：[docs/schema-pg.sql](./docs/schema-pg.sql)

文档优先级：

1. `docs/base.md` 与 `docs/phase-1/*`（正式基线）
2. `docs/roadmap.md`、`docs/phase-2/*`、`docs/phase-3/*`（规划与占位）
3. `README.md`（项目导读）
4. `RAWDOC/*`（历史资料）

## 快速开始

### 环境要求

- JDK 21
- Maven 3.9+
- PostgreSQL

### 校验 Maven 骨架

```bash
mvn -q validate
```

### 初始化数据库

```text
docs/schema-pg.sql
```
