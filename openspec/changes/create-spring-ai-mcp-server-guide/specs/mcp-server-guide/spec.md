## ADDED Requirements

### Requirement: 渐进式叙事结构与章节顺序

指南 SHALL 采用渐进式叙事，按依赖链组织七章：§1 问题动机（AI Agent 时代 JSON API 的痛点）→ §2 MCP 协议最小认知 → §3 Spring AI 编程模型 → §4 WebFlux 传输层 → §5 自包含实战示例 → §6 测试与客户端接入 → §7 边界与选型。每步只引入一个新概念，SHALL NOT 在第一章罗列 API 清单。

#### Scenario: 读者按顺序建立心智模型

- **WHEN** 读者从未接触过 MCP，从头阅读指南
- **THEN** 在见到第一个 `@Tool` 注解之前，已理解"为什么 AI 调用业务 API 需要协议"的动机

### Requirement: 核心心智模型——工具自我描述

指南 SHALL 让读者最终记住一个心智模型："MCP 让工具自我描述，AI 无需翻文档即可发现并调用业务能力"；全篇 SHALL 围绕该模型展开，避免演变为 API 罗列。

#### Scenario: 读者复述指南核心

- **WHEN** 读者被问"这篇指南在讲什么"
- **THEN** 能用一句话答出"把业务能力包装成 AI 可发现的工具"

### Requirement: MCP 协议最小认知（§2）

指南 SHALL 用最小篇幅讲清 MCP 协议的四个要素：client-server 架构、JSON-RPC 消息格式、tools（工具自描述）、initialize 握手；SHALL NOT 展开协议完整消息类型清单、版本协商细节或 resources/prompts 的完整语义。

#### Scenario: 读者读懂协议交互

- **WHEN** 读者看到 §4 中传输层的 SSE 消息流
- **THEN** 能辨认出 initialize 与 tools/call 消息，不依赖逐字节协议知识

### Requirement: Spring AI 编程模型（§3）

指南 SHALL 覆盖 Spring AI MCP Server 的编程模型：`@McpTool` 与 `@McpToolParam` 注解（含 description 字段对 AI 可见性的意义）、注解自动扫描注册方式（`@Component` + `spring.ai.mcp.server.annotation-scanner` 配置）、`McpSyncServer` 与 `McpAsyncServer` 的选择依据（`spring.ai.mcp.server.type`，线程模型差异）。

#### Scenario: 读者写出第一个工具

- **WHEN** 读者读完 §3
- **THEN** 能写出带参数描述的 `@McpTool` 方法并被自动扫描注册进 Spring 容器

### Requirement: WebFlux 传输层与最小响应式认知（§4）

指南 SHALL 说明 `spring-ai-starter-mcp-server-webflux` 自动配置了什么（MCP 端点的 WebFlux 路由与 SSE 传输，默认端点 POST /mcp），并给出 SSE、streamable HTTP、stateless HTTP 三种传输方式的取舍（1.1.x 三者均可用，stateless 适用于无状态多实例部署且不支持服务端推送）；SHALL 只引入最小 WebFlux 认知（`Flux<ServerSentEvent>`），SHALL NOT 展开 Reactor 算子体系与函数式路由（端点注册用注解式路由示意）。

#### Scenario: 读者理解 starter 的封装

- **WHEN** 读者阅读 §4 后检查依赖
- **THEN** 能说出 starter 隐藏了哪些 WebFlux 细节、SSE 传输为何适合 MCP 场景

### Requirement: 自包含贯穿示例（§5）

指南 SHALL 包含一个自包含的贯穿示例（商品域：商品查询 + 库存查询 + 推荐，2~3 个 `@McpTool`），展示多工具注册与描述质量对 AI 调用效果的影响；示例 SHALL 包含完整 `application.yml`（服务身份字段 `name`/`version`/`instructions` 与环境变量占位符模式）；示例 SHALL 不依赖本项目源码与 `pom.xml`（路线 A），依赖坐标完整给出。

#### Scenario: 读者复现完整示例

- **WHEN** 读者按 §5 的依赖坐标与代码搭建项目
- **THEN** 能独立运行一个提供三个工具的 MCP Server

### Requirement: 测试与客户端接入（§6）

指南 SHALL 展示两种验证方式：用 `WebTestClient` 直接测 MCP 端点（SSE 响应断言）、用 MCP 客户端（如 Claude Desktop 的 mcpServers 配置 JSON）完成真实接入；配置示例 SHALL 与 §5 的端点路径一致。

#### Scenario: 读者验证自己写的服务

- **WHEN** 读者完成 §5 示例
- **THEN** 能按 §6 的测试与配置步骤，在客户端列表中看到并调用自己的工具

### Requirement: 阻塞陷阱与选型边界（§7）

指南 SHALL 覆盖三个边界问题：webmvc 与 webflux 两种 MCP server starter 的选型、事件循环线程上禁止阻塞调用（`@McpTool` 方法内阻塞式数据库调用导致会话卡死的机理与三种解法）、以及 ASYNC 模式下 ThreadLocal 上下文丢失与 Reactor 上下文传播解法（版本边界以已核实事实为准）。

#### Scenario: 读者规避阻塞陷阱

- **WHEN** 读者在 `McpAsyncServer` 的 `@McpTool` 方法中需要访问数据库
- **THEN** 能识别阻塞风险并选择合适的卸载方案

### Requirement: 版本声称与来源

指南 SHALL 在开头声明目标版本 Spring AI 1.1.x + Spring Boot 3.5.x；涉及可变事实（版本兼容矩阵、传输方式支持范围）SHALL 以 Spring AI 官方文档为准，不得凭记忆断言。

#### Scenario: 版本信息可溯源

- **WHEN** 读者需要确认自己的依赖版本是否兼容
- **THEN** 指南中给出的版本声称与官方兼容矩阵一致

### Requirement: 代码示例证据分类

指南中每个代码块 SHALL 按 guide-writing 契约标注证据状态（Illustrative fragment / Complete example, not yet verified / Verified runnable example）；未经实际运行的示例 SHALL NOT 标注为可运行，未验证项 SHALL 在交付说明中列出。

#### Scenario: 示例状态诚实标注

- **WHEN** 读者查看任一代码块
- **THEN** 能立即知道该示例是示意片段还是经过运行验证

### Requirement: 与既有指南互补互链

本指南 SHALL NOT 重复 `spring-boot-multithreading-guide.md` 的异步边界内容与 `spring-cloud-microservices-guide.md` 的 Gateway 内容；SHALL 互链这两篇（异步边界作为阻塞陷阱的前置认知、Gateway 作为 WebFlux 技术栈的延伸），并为未来可能的 WebFlux 指南预留链接位置。

#### Scenario: 指南间分工无重叠

- **WHEN** 读者分别查阅三篇指南
- **THEN** 异步边界属于多线程指南、Gateway 属于微服务指南，本指南只引用不展开

### Requirement: 范围排除

指南 SHALL NOT 教完整 WebFlux/Reactor（只保留 §4 与 §7 所需的最小认知）；SHALL NOT 教 LLM 提示词工程；SHALL NOT 覆盖 MCP client 端编程（仅 §6 测试所需的最小配置）；SHALL NOT 修改项目 `pom.xml` 或任何源码（路线 A）。

#### Scenario: 指南保持聚焦

- **WHEN** 读者通读全篇
- **THEN** 不遇到上述排除主题的展开讲解，篇幅保持在入门定位内
