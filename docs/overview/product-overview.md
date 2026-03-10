# Fluxion 产品总览

## 项目定位

Fluxion 是一个基于 Spring Boot 的可视化流程编排系统，面向业务集成编排场景，而不是通用 BPM 或重量级 ESB。

它的核心目标是把常见的集成逻辑沉淀为统一的流程模型，并支持设计、发布、执行、监控和治理。

## 产品形态

Fluxion 支持两种产品形态：

1. `Starter`
   以 Spring Boot Starter 的方式嵌入业务系统，不作为独立平台启动，也不单独提供独立监听端口。
2. `Server`
   在 `Starter` 底座能力上额外聚合 Web 能力，以独立服务的方式提供流程设计、发布、监控和治理能力。

## 一期目标

一期聚焦“最小可用闭环”，包括：

1. 流程定义与版本管理
2. 基础前端管理页面与可视化设计器
3. HTTP 发布
4. 定时调度
5. 核心执行引擎
6. 基础监控与治理

## 相关文档


- [一期正式需求文档](../phase-1/requirements.md)
- [一期技术方案文档](../phase-1/technical-solution.md)
- [一期运行语义规范](../phase-1/runtime-semantics.md)
- [一期 HTTP 发布契约](../phase-1/http-endpoint-contract.md)
- [一期资源契约](../phase-1/resource-contract.md)
- [一期认证凭证契约](../phase-1/auth-credential-contract.md)
- [一期调度契约](../phase-1/schedule-contract.md)
- [一期节点 Schema 详细定义](../phase-1/node-schemas.md)
- [一期计划](../phase-1/plan.md)
