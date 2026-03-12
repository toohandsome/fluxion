# Fluxion 路线图

## 总览

Fluxion 按三个阶段推进：

1. 一期：建立最小可用闭环
2. 二期：提升复用性与运维能力
3. 三期：建立生态扩展与高阶处理能力

## 一期（当前基线）

目标：建立可设计、可发布、可触发、可监控、可治理的最小可用闭环。

核心范围：

- 流程定义与版本管理
- 基础前端可视化设计器
- HTTP 发布与运行时触发
- Quartz 调度
- 执行引擎与基础节点
- 监控、审计、资源治理

详细文档：

- [一期正式需求文档](./phase-1/requirements.md)
- [一期技术方案文档](./phase-1/technical-solution.md)
- [一期运行语义规范](./phase-1/runtime-semantics.md)
- [一期 HTTP 发布契约](./phase-1/http-endpoint-contract.md)
- [一期资源契约](./phase-1/resource-contract.md)
- [一期认证凭证契约](./phase-1/auth-credential-contract.md)
- [一期调度契约](./phase-1/schedule-contract.md)
- [一期节点 Schema 详细定义](./phase-1/node-schemas.md)
- [一期实施计划](./phase-1/plan.md)

## 二期（规划）

目标：提升平台复用性与可运维性。

规划能力：

- 子流程
- 模板库
- 脚本节点
- Redis 节点
- Quartz 集群
- 指标、链路追踪与告警
- 失败重放与补偿
- 更多数据库支持

参见：[二期占位说明](./phase-2/README.md)

## 三期（规划）

目标：建立生态扩展与高阶处理能力。

规划能力：

- 插件体系
- 前端插件动态加载
- 流式处理
- 断点调试
- 死信与背压
- 更多数据库方言支持

参见：[三期占位说明](./phase-3/README.md)
