# Fluxion 架构总览

## 核心分层

Fluxion 采用三层架构：

1. `Engine`
   负责流程执行语义、调度、上下文和状态流转。
2. `Starter`
   负责把 Fluxion 能力装配到业务系统中，核心能力与 `Server` 等价；默认不自启独立 Web 端口，可按宿主需要启用 Web/API 与前端页面能力。
3. `Server`
   基于 `Starter` 提供开箱即用的独立部署形态，默认启用 Web/API 暴露与前端页面集成能力，并以独立进程运行。

## 模块总览

一期模块结构如下：

```text
fluxion-parent/
├── fluxion-dependencies
├── fluxion-common
├── fluxion-model
├── fluxion-spi
├── fluxion-modeler
├── fluxion-engine
├── fluxion-persistence-mybatisplus
├── fluxion-scheduler
├── fluxion-runtime-api
├── fluxion-admin-api
├── fluxion-expression-spel
├── fluxion-node-control
├── fluxion-node-http
├── fluxion-node-database
├── fluxion-spring-boot-starter
├── fluxion-server
└── fluxion-test
```

## 数据分层

数据库设计分为四层：

1. 设计层
2. 运行执行层
3. 暴露与调度层
4. 治理层

## 相关文档

- [一期技术方案](../phase-1/technical-solution.md)
- [一期数据库脚本](../schema-pg.sql)
