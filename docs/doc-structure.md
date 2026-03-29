# Fluxion 文档总览

本文档用于定义 Fluxion 文档结构、职责边界与维护规则，避免同一概念在多个文档中重复定义并逐步漂移。

## 1. 文档分层

### 1.1 L0：项目导读

- [../README.md](../README.md)
- 作用：面向仓库访客的快速说明，不承载可执行细则。

### 1.2 L1：通用规范与路线

- [base.md](./base.md)
- [roadmap.md](./roadmap.md)
- [agent/index.md](./agent/index.md)
- 作用：定义跨阶段通用规范与分期规划。

### 1.3 L2：阶段基线

- 一期：`phase-1/*`（正式基线）
- 二期：`phase-2/*`（占位与规划）
- 三期：`phase-3/*`（占位与规划）

## 2. 单一事实源（SoT）

| 主题 | 唯一维护文档 | 备注 |
| --- | --- | --- |
| 技术栈、数据库规范、统一响应结构、业务状态码 | [base.md](./base.md) | 跨阶段通用规范 |
| 分期目标与阶段范围 | [roadmap.md](./roadmap.md) | 总体规划入口 |
| agent / harness 使用入口 | [agent/index.md](./agent/index.md) | 只维护执行入口与反馈循环说明，不重复定义一期正式契约 |
| harness 输出与 diagnostics 字段契约 | [agent/harness-spec.md](./agent/harness-spec.md) | 只维护 harness 产物与 agent-facing 字段约定 |
| 一期产品边界、功能范围、交付物 | [phase-1/requirements.md](./phase-1/requirements.md) | 需求基线 |
| 一期技术实现与工程决策 | [phase-1/technical-solution.md](./phase-1/technical-solution.md) | 决策基线 |
| 一期运行时语义 | [phase-1/runtime-semantics.md](./phase-1/runtime-semantics.md) | 执行语义唯一来源 |
| Admin API 管理端契约体系 | [phase-1/admin-api-contract.md](./phase-1/admin-api-contract.md) | `/admin/*` 契约总入口；具体接口分拆到 `phase-1/admin-api/*` |
| HTTP 发布契约 | [phase-1/http-endpoint-contract.md](./phase-1/http-endpoint-contract.md) | 端点协议唯一来源 |
| 资源契约 | [phase-1/resource-contract.md](./phase-1/resource-contract.md) | 资源协议唯一来源 |
| 认证凭证契约 | [phase-1/auth-credential-contract.md](./phase-1/auth-credential-contract.md) | 凭证协议唯一来源 |
| 调度契约 | [phase-1/schedule-contract.md](./phase-1/schedule-contract.md) | 调度协议唯一来源 |
| 节点参数 Schema | [phase-1/node-schemas.md](./phase-1/node-schemas.md) | 节点配置唯一来源 |
| `graph_json` 设计态契约 | [phase-1/graph-json-contract.md](./phase-1/graph-json-contract.md) | Draft / Modeler 核心契约唯一来源 |
| `model_json` 运行时契约 | [phase-1/model-json-contract.md](./phase-1/model-json-contract.md) | Engine / Modeler 核心契约唯一来源 |
| 一期错误码基线 | [phase-1/error-codes.md](./phase-1/error-codes.md) | 错误码唯一来源 |
| 一期实施计划（里程碑与出口标准） | [phase-1/plan.md](./phase-1/plan.md) | 仅覆盖一期 |

## 3. 维护规则

1. `doc-structure.md` 只保留摘要和入口链接，不重复定义契约细节。
2. `roadmap.md` 只维护阶段目标与范围，不展开字段级协议。
3. `technical-solution.md` 只保留架构、模块拆分、设计取舍和实现建议；凡已在契约文档固化的字段、枚举、请求体、响应体、Schema 与运行时模型定义，不再在该文档中做字段级重复维护。
4. `/admin/*` 的路径、方法、请求体、响应体统一收口到 `phase-1/admin-api-contract.md` 与 `phase-1/admin-api/*`；对象契约文档不得重复维护管理接口路径。
5. 当 `technical-solution.md` 出于说明目的引用协议时，只允许保留“摘要 + 链接”，不得形成第二套可独立演化的字段定义。
6. 同一概念需要在多文档出现时，主文档写完整定义，其他文档仅写摘要并链接。
7. 若后续需要补充新的字段级协议，应优先新增或扩展对应 contract 文档，而不是继续堆叠到 `technical-solution.md`。
8. 若 `RAWDOC/*` 与正式基线冲突，以 `docs/base.md` 与 `docs/phase-1/*` 为准。

## 4. 快速入口

- 一期总入口：[phase-1/requirements.md](./phase-1/requirements.md)
- 一期契约集合：
  - [phase-1/admin-api-contract.md](./phase-1/admin-api-contract.md)
  - [phase-1/admin-api/flows.md](./phase-1/admin-api/flows.md)
  - [phase-1/admin-api/resources.md](./phase-1/admin-api/resources.md)
  - [phase-1/admin-api/schedules.md](./phase-1/admin-api/schedules.md)
  - [phase-1/graph-json-contract.md](./phase-1/graph-json-contract.md)
  - [phase-1/http-endpoint-contract.md](./phase-1/http-endpoint-contract.md)
  - [phase-1/resource-contract.md](./phase-1/resource-contract.md)
  - [phase-1/auth-credential-contract.md](./phase-1/auth-credential-contract.md)
  - [phase-1/schedule-contract.md](./phase-1/schedule-contract.md)
  - [phase-1/node-schemas.md](./phase-1/node-schemas.md)
  - [phase-1/model-json-contract.md](./phase-1/model-json-contract.md)
  - [phase-1/error-codes.md](./phase-1/error-codes.md)
- 数据库脚本：[schema-pg.sql](./schema-pg.sql)
