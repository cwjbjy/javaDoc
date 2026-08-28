# Spring AI MCP Server 指南

> Audience: 具备 Spring Boot 基础（理解 `@Component`、自动配置与 `application.yml`），希望把业务能力暴露给 AI Agent 的开发人员
> Outcome: 能用 `spring-ai-starter-mcp-server-webflux` 独立跑起一个 MCP Server，理解工具描述为何决定 AI 调用效果，并能规避阻塞陷阱
> Applicable version: Spring AI 1.1.x、Spring Boot 3.5.x、JDK 17

## Scope

这篇指南解决的是"如何让 AI Agent 发现并调用你的业务能力"。阅读前不需要任何响应式编程知识：MCP Server 的传输层由 starter 自动配置，你只需要写带注解的业务方法。

**涵盖**：MCP 协议最小认知、`@McpTool`/`@McpToolParam` 编程模型、WebFlux SSE 传输层、一个商品域贯穿示例、WebTestClient 测试与 Claude Desktop 接入、选型边界。

**不涵盖**：完整 WebFlux/Reactor（只保留理解传输层所需的最小认知）、LLM 提示词工程、MCP client 端编程（只给测试所需的最小配置）。异步任务边界请参考 [Spring Boot 多线程指南](spring-boot-multithreading-guide.md)，Spring Cloud Gateway 等 WebFlux 技术栈延伸请参考 [微服务指南](spring-cloud-microservices-guide.md)。

> **核心认知**：MCP 让工具自我描述，AI 无需翻文档即可发现并调用业务能力。全篇都围绕这句话展开。

## 目录

1. [为什么需要 MCP Server](#1-为什么需要-mcp-server)
2. [MCP 协议 30 秒](#2-mcp-协议-30-秒)
3. [Spring AI 编程模型](#3-spring-ai-编程模型)
4. [WebFlux 传输层](#4-webflux-传输层)
5. [实战：商品助手 MCP Server](#5-实战商品助手-mcp-server)
6. [测试与客户端接入](#6-测试与客户端接入)
7. [边界与选型](#7-边界与选型)

---

## 1. 为什么需要 MCP Server

### 1.1 痛点：AI Agent 面前的 JSON API

2026 年，AI Agent 已经被期待去做真实的工作：查数据、下订单、调库存。而你的业务能力——查询商品、扣减库存、生成报告——都还住在传统的 REST API 里。

问题来了：**人类觉得一目了然的 API，AI 觉得寸步难行。**

```
  AI Agent 调用传统 JSON API 时的三道坎
  ═══════════════════════════════════════════════════

  ① 接口发现难
     GET /api/v1/products?keyword=xxx  —— 这个接口存在吗？
     参数叫什么？返回什么结构？AI 得先"翻文档"

  ② 语义猜测难
     GET /api/v1/products?keyword=iPhone
     参数 keyword 是模糊匹配还是精确匹配？
     返回的 price 单位是元还是分？分页从 0 开始还是 1？

  ③ 无差别暴露难
     一个系统里有 50 个接口，AI 该调哪个？
     没有"工具清单"这个层，AI 只能盲试
```

这些坎的本质是：**JSON API 是为"人 + 文档"设计的，不是为"AI + 上下文"设计的**。人类会读接口文档、会从字段名猜语义、会问同事；AI 只能依靠你喂给它的描述。

### 1.2 人类眼中的"好 API" vs AI 眼中的"好 API"

| 维度     | 人类眼中的好 API          | AI 眼中的好 API                       |
| -------- | ------------------------- | ------------------------------------- |
| 发现方式 | 翻文档、看 OpenAPI 页面   | 一问就列出所有可调用的工具            |
| 参数理解 | 字段名 + 类型 + 试错      | 每个参数都有自然语言描述和必填标记    |
| 调用结果 | 状态码 + JSON，自己判断   | 结构化结果 + 错误信息可直接解释给用户 |
| 集成成本 | 每个新 API 写一套对接代码 | 同一套协议，新工具零对接成本          |

AI 需要的不是更好的 JSON 文档，而是**工具自己会说话**：它叫什么、能干什么、每个参数是什么意思、返回什么。

### 1.3 MCP 的承诺：让工具自我描述

Model Context Protocol（MCP）正是为这个问题而生。它由 Anthropic 提出，2026 年已成为 AI 集成的**事实标准**，各大 IDE、桌面客户端（Claude Desktop 等）与云平台原生支持。

MCP 的承诺一句话：**你按约定描述工具，AI 按描述发现并调用工具，双方都无需事先约定接口细节。**

```
  传统方式                          MCP 方式
  ─────────                        ─────────────

  业务方法                           业务方法
     │                                 │
  手写 Controller                     加 @McpTool 注解
     │                                 │
  手写 OpenAPI 文档                    框架自动生成工具描述
     │                                 │
  人读文档 → 人写调用代码               AI 读描述 → AI 直接调用
     │                                 │
  每个客户端重复一次                   所有 MCP 客户端通用
```

对你（Spring 开发者）而言，MCP Server 就是**加了特殊注解的 Spring Boot 应用**。剩下的交给 Spring AI。

### 1.4 本指南的前提与范围

- **前提知识**：Spring Boot 的 `@Component`、自动配置、`application.yml`（可参考 [Spring IOC/DI 指南](spring-ioc-di-guide.md)）；HTTP 与 JSON 常识。
- **版本声称**：Spring AI 1.1.x + Spring Boot 3.5.x + JDK 17。版本兼容矩阵见 [§7.3](#73-版本矩阵)。
- **贯穿示例**：一个"商品助手"MCP Server，提供三个工具（商品搜索、库存查询、类目推荐），全部代码自包含，不依赖本项目其他源码。

---

## 2. MCP 协议 30 秒

> 目标：能看懂后面章节里的协议名词和消息流。不需要逐字节理解协议。

### 2.1 三个角色：Host / Client / Server

MCP 架构里有三个角色，各司其职：

```
  ┌──────────────────────────────────────────────┐
  │                  Host（宿主）                  │
  │     Claude Desktop / IDE / 你的业务应用        │
  │                                              │
  │   ┌─────────────┐      ┌───────────────┐     │
  │   │ MCP Client  │◀────▶│  MCP Server   │     │
  │   │ （内置的协议  │ JSON │ （你要写的那个）│     │
  │   │   客户端）   │ -RPC │               │     │
  │   └─────────────┘      └───────────────┘     │
  │                                              │
  │   LLM（大模型）── AI 的"大脑"，决定调哪个工具   │
  └──────────────────────────────────────────────┘
```

- **Host**：用户正在使用的应用（Claude Desktop、IDE、你自己的程序），它内置了一个 **MCP Client**，并把 AI 大模型接入对话。
- **MCP Client**：负责与 MCP Server 通信、把工具清单喂给 LLM、执行 LLM 发起的工具调用。
- **MCP Server**：**你要写的东西**。它暴露"工具"（Tools），也可能暴露"资源"（Resources）和"提示模板"（Prompts）——本指南只讲 Tools。

### 2.2 消息格式：JSON-RPC

MCP 的通信基于 JSON-RPC 2.0：所有消息都是 JSON，分"请求"和"响应"，靠 `id` 配对。

```json
// 请求：客户端请服务端列出所有工具
{ "jsonrpc": "2.0", "id": 1, "method": "tools/list", "params": {} }

// 响应：服务端返回工具清单
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "tools": [
      {
        "name": "search_products",
        "description": "按关键词搜索商品",
        "inputSchema": { "type": "object", "properties": { "keyword": { "type": "string" } } }
      }
    ]
  }
}
```

### 2.3 工具三要素：name / description / inputSchema

每个工具的自描述由三要素构成——**这正是 §1 里 AI 需要的"会说话的接口"**：

| 要素          | 含义                                 | AI 怎么用它                      |
| ------------- | ------------------------------------ | -------------------------------- |
| `name`        | 工具唯一标识                         | 决定调用哪个工具                 |
| `description` | 自然语言说明                         | 判断"这个工具能不能解决用户问题" |
| `inputSchema` | 参数 JSON Schema（类型、必填、说明） | 决定填什么参数                   |

一次完整的工具调用（`tools/call`）是：AI 根据 `description` 选中工具 → 按 `inputSchema` 构造参数 → 客户端发出 `tools/call` 请求 → 服务端执行并返回结果。

### 2.4 initialize 握手

客户端连接后第一件事是 `initialize`：双方交换**协议版本**与**能力声明**（我支持 tools，你支持 logging）。握手成功后，客户端才会发 `tools/list` 和 `tools/call`。

```
  Client                          Server
    │  initialize(协议版本, 能力)      │
    │ ─────────────────────────────▶ │
    │  ◀───────────────────────────── │ initialized(能力)
    │  tools/list                    │
    │ ─────────────────────────────▶ │
    │  ◀───────────────────────────── │ 工具清单
    │  tools/call(search_products)   │
    │ ─────────────────────────────▶ │
    │  ◀───────────────────────────── │ 执行结果
```

### 2.5 认知边界

这 30 秒就是本指南需要你懂的**全部协议知识**。完整的消息类型清单、版本协商细节、resources/prompts 的语义，请到 [MCP 官方规范](https://modelcontextprotocol.io/)查阅——用 Spring AI 开发时，这些细节框架都会替你处理。

---

## 3. Spring AI 编程模型

> 目标：用注解把业务方法变成 MCP 工具。这是整篇指南的核心章节。

### 3.1 依赖坐标

Spring AI 用 BOM 统一管理版本。`pom.xml` 里加两处：

```xml
<!-- 依赖管理：Spring AI BOM 1.1.x -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.1.5</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- 依赖：WebFlux 传输的 MCP Server starter（版本由 BOM 管理） -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webflux</artifactId>
</dependency>
```

这个 starter 同时拉入 WebFlux 运行环境（Netty + Reactor），**你不需要自己加任何 WebFlux 依赖**。它自动配置了一个 MCP Server，接下来你的工作只有一件事：写工具。

### 3.2 `@McpTool`：把一个方法变成工具

> Illustrative fragment：省略了类与包的样板，只展示注解的核心用法。

```java
import org.springframework.ai.mcp.server.McpTool;

@McpTool(description = "按关键词搜索商品，返回匹配的商品列表")
public List<Product> searchProducts(String keyword) {
    return productStore.findByKeyword(keyword);
}
```

一个 `@McpTool` 方法在启动时被框架扫描，自动生成 §2.3 里的三要素：

| 要素          | 生成来源                        | 默认值                        |
| ------------- | ------------------------------- | ----------------------------- |
| `name`        | `@McpTool(name = "...")`        | 方法名（如 `searchProducts`） |
| `description` | `@McpTool(description = "...")` | 空——**强烈建议显式填写**      |
| `inputSchema` | 参数类型 + `@McpToolParam` 描述 | 仅类型信息                    |

**`description` 是整个工具的灵魂。** 它直接进入 AI 的上下文，决定 AI 是否会选这个工具。写一条好的描述要回答三个问题：这个工具做什么、什么时候该用它、返回什么。

```
  同一个方法，两种 description，两种命运
  ═══════════════════════════════════════════════════

  ✗ @McpTool(description = "查询")
     → AI 看到"查询"：查什么？什么时候用？——大概率不会调用

  ✓ @McpTool(description = "按关键词模糊搜索商品。当用户想找商品但不知道
     具体 ID 时使用。返回商品列表（含 ID、名称、价格、库存）。")
     → AI 看到完整语义：能判断"用户想找商品"这个场景该调它
```

### 3.3 `@McpToolParam`：让参数也说话

> Illustrative fragment：只展示参数注解，方法体省略。

```java
import org.springframework.ai.mcp.server.McpTool;
import org.springframework.ai.mcp.server.McpToolParam;

@McpTool(description = "按关键词模糊搜索商品。当用户想找商品但不知道具体 ID 时使用。返回商品列表。")
public List<Product> searchProducts(
        @McpToolParam(description = "搜索关键词，匹配商品名称，不区分大小写") String keyword,
        @McpToolParam(description = "最多返回的商品数量，默认 10") int limit) {
    // ...
}
```

每个 `@McpToolParam` 的 `description` 进入工具的 `inputSchema`，AI 据此决定参数值。要点：

- **写参数约束**：`required = true`（默认），可选参数设 `false`
- **写格式约定**：日期是 `yyyy-MM-dd` 还是时间戳？枚举有哪些取值？——写进 description
- **写默认行为**：`limit` 不传时怎么办？写清楚，AI 就不会猜

### 3.4 注册方式：自动扫描

Spring AI 1.1.x 的注册路径是**注解自动扫描**：只要工具方法所在的类是 Spring Bean（`@Component`、`@Service` 等），starter 的自动配置就会把它注册到 MCP Server。

```java
import org.springframework.ai.mcp.server.McpTool;
import org.springframework.stereotype.Component;

@Component
public class ProductTools {

    @McpTool(description = "...")
    public List<Product> searchProducts(String keyword) { /* ... */ }
}
```

无需手写任何注册代码。扫描开关在配置里（默认开启）：

```yaml
spring:
  ai:
    mcp:
      server:
        annotation-scanner:
          enabled: true # 默认值，显式写出以表明确认
```

### 3.5 Sync 还是 Async？线程模型的选择

MCP Server 有两种运行模式，通过配置切换：

| 配置                                      | 实现             | 适用场景                                       |
| ----------------------------------------- | ---------------- | ---------------------------------------------- |
| `spring.ai.mcp.server.type: SYNC`（默认） | `McpSyncServer`  | 工具方法就是普通同步方法，业务简单直接         |
| `spring.ai.mcp.server.type: ASYNC`        | `McpAsyncServer` | 工具方法返回 `Mono`/`Flux`，做真正的非阻塞调用 |

**重要规则**：SYNC 模式**只注册同步方法**（返回普通对象），ASYNC 模式**只注册响应式方法**（返回 `Mono`/`Flux`）。选错模式，方法会被静默忽略——这是最常见的一号坑。

```
  SYNC 模式（默认）                ASYNC 模式
  ────────────────                ────────────────
  工具方法：                       工具方法：
  List<Product> search(...)       Mono<List<Product>> search(...)
      │ 同步执行                        │ 非阻塞
      ▼                                ▼
  McpSyncServer 用自己的           McpAsyncServer 跑在
  线程池执行工具                    WebFlux 事件循环线程上
      │                                │
      └── 线程模型简单 ──┘               └── 禁止阻塞！§7.2 详解
```

本指南的贯穿示例用默认的 SYNC 模式——工具方法里查内存数据，简单直接。什么时候该上 ASYNC、为什么 ASYNC 里不能随便阻塞，留到 [§7.2](#72-阻塞陷阱) 讲透。

---

## 4. WebFlux 传输层

> 目标：理解 starter 替你做了什么。这是全篇唯一深入 WebFlux 内部的章节。

### 4.1 starter 自动配置了什么

引入 `spring-ai-starter-mcp-server-webflux` 后，自动配置做了三件事：

```
  你写的                      starter 自动配置的
  ──────                     ──────────────────────────────
                             ┌───────────────────────────┐
  @McpTool 方法       ──▶    │ 1. 扫描工具 → 生成协议描述    │
                             │ 2. 组装 MCP Server 实例     │
                             │ 3. 注册 HTTP 路由：          │
                             │    GET  /sse               │
                             │    POST /mcp/message       │
                             └───────────────────────────┘
```

第 3 步用的是 WebFlux 的**函数式路由**（`RouterFunction`）——WebFlux 世界里不写 `@RestController` 也能注册路由的另一种方式：

> Illustrative fragment：示意 starter 内部注册 SSE 路由的形态，非实际源码。

```java
RouterFunction<ServerResponse> route() {
    return RouterFunctions
            .route(GET("/sse"), request -> /* 返回 SSE 事件流 */)
            .andRoute(POST("/mcp/message"), request -> /* 接收 JSON-RPC 消息 */);
}
```

两个默认端点（可通过 `spring.ai.mcp.server.sse-endpoint` / `sse-message-endpoint` 修改）：

| 端点           | 方法 | 作用                                                                |
| -------------- | ---- | ------------------------------------------------------------------- |
| `/sse`         | GET  | 客户端建立 SSE 长连接，服务端从这里推送事件（工具清单、响应、通知） |
| `/mcp/message` | POST | 客户端把 JSON-RPC 请求 POST 到这里                                  |

### 4.2 SSE：一条只出不进的长连接

SSE（Server-Sent Events）是 WebFlux 的杀手锏场景。WebFlux 用 Reactor 的 `Flux<ServerSentEvent<?>>` 描述事件流：

> Illustrative fragment：展示 `Flux<ServerSentEvent>` 的形态，不展开 Reactor 算子。

```java
// 一个"事件流"的声明：逐个发出事件，直到结束
Flux<ServerSentEvent<String>> events =
        Flux.just(
                ServerSentEvent.builder("第一条消息").build(),
                ServerSentEvent.builder("第二条消息").build());
```

**最小响应式认知**（够用即止）：

```
  普通方法返回"一个结果"            Flux 返回"一串事件"
  ─────────────────────           ─────────────────────
  String getName()                Flux<ServerSentEvent<?>> events()
      │                                │
      调用即拿到结果                    声明了一个数据流，
      │                                订阅后才逐个推送
      ▼                                ▼
  [结果]                           [事件1]→[事件2]→[事件3]→…
```

MCP 与 SSE 是天作之合：工具清单、日志通知、执行结果天然是"一串消息"，SSE 让服务端可以随时向已连接的客户端推送，而不必等客户端轮询。**本指南对 Reactor 的认知到此为止**——想深入请等待后续的 WebFlux 指南。

### 4.3 SSE vs Streamable HTTP：怎么选

Spring AI 1.1.x 的 WebFlux starter 支持两种传输，用 `spring.ai.mcp.server.protocol` 切换：

| 传输            | 配置                   | 默认端点                         |
| --------------- | ---------------------- | -------------------------------- |
| SSE（默认）     | `protocol: SSE`        | GET `/sse` + POST `/mcp/message` |
| Streamable HTTP | `protocol: STREAMABLE` | POST `/mcp`                      |

对照取舍：

| 维度          | SSE                                    | Streamable HTTP                              |
| ------------- | -------------------------------------- | -------------------------------------------- |
| 流式推送      | 原生支持（长连接）                     | 可选 SSE 流（POST 响应内）                   |
| 客户端兼容    | 需要客户端支持 SSE 长连接              | 基于普通 HTTP POST，更通用                   |
| 代理/网关穿透 | 长连接对超时敏感的代理不友好           | 普通请求，穿透友好                           |
| 演进方向      | 1.1.x 可用；官方 2.0 起标记 deprecated | 官方推荐方向（"replaces the SSE transport"） |

**选型建议**：目标是 Claude Desktop 等成熟客户端、追求流式体验 → SSE 完全够用且生态成熟；服务要上云网关、或面向新客户端 → 直接上 Streamable HTTP。本指南贯穿示例用默认的 SSE，不写 `protocol` 配置就是它。

### 4.4 安全警告：端点默认裸奔

官方文档明确警告：HTTP 传输的 MCP 端点**默认没有任何认证**。任何能访问到端口的人都可以列出并调用你的全部工具。在暴露到 localhost 之外前，必须在前面加安全层（Spring Security 或网关鉴权）——这是把工具注册进应用时就必须想清楚的事。

---

## 5. 实战：商品助手 MCP Server

> 目标：跑起一个真实可用的 MCP Server。本节是 Complete example，依赖坐标完整，可直接搭建。
> 示例状态：Complete example, not yet verified（本指南编写环境未实际运行，命令与坐标完整给出）

### 5.1 项目骨架

一个最小 Maven 项目，完整 `pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.14</version>
        <relativePath/>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>product-mcp-server</artifactId>
    <version>0.0.1-SNAPSHOT</version>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.ai</groupId>
                <artifactId>spring-ai-bom</artifactId>
                <version>1.1.5</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-starter-mcp-server-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

### 5.2 内存数据层

路线 A：数据放内存，不引入任何数据库。一个实体 + 一个存储组件：

```java
package com.example.product;

import java.math.BigDecimal;

public record Product(
        long id,
        String name,
        String category,
        BigDecimal price,
        int stock) {
}
```

```java
package com.example.product;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Component
public class ProductStore {

    private static final List<Product> PRODUCTS = List.of(
            new Product(1, "iPhone 17 Pro", "手机数码", new BigDecimal("8999.00"), 42),
            new Product(2, "MacBook Air M4", "电脑办公", new BigDecimal("7499.00"), 18),
            new Product(3, "AirPods Pro 3", "手机数码", new BigDecimal("1899.00"), 120),
            new Product(4, "机械键盘 K870", "电脑办公", new BigDecimal("399.00"), 76),
            new Product(5, "显示器 27 寸 4K", "电脑办公", new BigDecimal("2199.00"), 0)
    );

    public List<Product> searchByKeyword(String keyword, int limit) {
        String lower = keyword.toLowerCase(Locale.ROOT);
        return PRODUCTS.stream()
                .filter(p -> p.name().toLowerCase(Locale.ROOT).contains(lower))
                .limit(limit)
                .toList();
    }

    public Product findById(long id) {
        return PRODUCTS.stream()
                .filter(p -> p.id() == id)
                .findFirst()
                .orElse(null);
    }

    public List<Product> recommendByCategory(String category, int limit) {
        return PRODUCTS.stream()
                .filter(p -> p.category().equals(category))
                .limit(limit)
                .toList();
    }
}
```

### 5.3 三个工具

把存储包装成三个 `@McpTool` 方法。**注意每个描述都写足了"做什么 / 何时用 / 返回什么"三要素**：

```java
package com.example.product;

import org.springframework.ai.mcp.server.McpTool;
import org.springframework.ai.mcp.server.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProductTools {

    private final ProductStore store;

    public ProductTools(ProductStore store) {
        this.store = store;
    }

    @McpTool(description = """
            按关键词模糊搜索商品。当用户想找某个商品但不知道具体 ID 时使用。
            返回商品列表（含 ID、名称、类目、价格、库存），无匹配时返回空列表。""")
    public List<Product> searchProducts(
            @McpToolParam(description = "搜索关键词，匹配商品名称，不区分大小写") String keyword,
            @McpToolParam(description = "最多返回的商品数量，默认 10") int limit) {
        return store.searchByKeyword(keyword, limit);
    }

    @McpTool(description = """
            按商品 ID 查询库存。当用户询问某个具体商品还有没有货时使用。
            返回该商品的名称与库存数量；商品不存在时返回"商品不存在"。""")
    public String getStock(
            @McpToolParam(description = "商品 ID，来自商品列表中的 id 字段") long id) {
        Product product = store.findById(id);
        if (product == null) {
            return "商品不存在";
        }
        return product.name() + " 当前库存：" + product.stock() + " 件";
    }

    @McpTool(description = """
            按类目推荐商品。当用户想看某一类目下有什么可选商品时使用。
            类目可选值：手机数码、电脑办公。返回该类目下的商品列表。""")
    public List<Product> recommendProducts(
            @McpToolParam(description = "商品类目，可选值：手机数码、电脑办公") String category,
            @McpToolParam(description = "最多返回的商品数量，默认 5") int limit) {
        return store.recommendByCategory(category, limit);
    }
}
```

### 5.4 描述质量的对比实验

三个工具都注册后，AI 能看到什么，取决于描述。对照一下好坏描述对调用效果的影响：

```
  坏描述                               好描述
  ──────                               ──────
  getStock："查库存"                    getStock："按商品 ID 查询库存。当用户
                                       询问某个具体商品还有没有货时使用……"

  用户问"iPhone 17 Pro 还有货吗？"        用户问同样的问题
  → AI 不知道哪个工具能回答              → AI 读描述："查库存，需要 ID"
  → 可能乱调或拒绝执行                   → 先调 searchProducts 拿 ID
                                        → 再调 getStock 查库存
                                        → 两段式调用，成功回答
```

**这就是 §1 那句话的兑现**：描述质量直接决定 AI 的调用成功率。工具描述就是给 AI 写的"接口文档"。

### 5.5 启动类与运行

```java
package com.example.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ProductMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductMcpServerApplication.class, args);
    }
}
```

运行：

```text
mvn spring-boot:run
```

启动后，MCP Server 监听默认端口 8080：

```text
GET  http://localhost:8080/sse           ← SSE 事件流端点
POST http://localhost:8080/mcp/message   ← JSON-RPC 消息端点
```

> 验证步骤：本示例的完整验收路径在 §6（WebTestClient 自动化验证 + Claude Desktop 手动接入）。

---

## 6. 测试与客户端接入

> 目标：证明你的工具真的能被发现、被调用。

### 6.1 用 WebTestClient 直测端点

> 示例状态：Complete example, not yet verified（依赖坐标在 §5.1 已给出）

不需要真实 AI，`WebTestClient` 直接对 MCP 端点发 JSON-RPC 请求就能验证：

```java
package com.example.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductMcpServerApplicationTests {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void toolsListReturnsThreeTools() {
        String request = """
                {"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}
                """;

        webTestClient.post()
                .uri("/mcp/message")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk();
    }
}
```

> 说明：SSE 传输下完整断言需要处理 `/sse` 事件流上的响应（JSON-RPC 响应经 SSE 通道返回），完整示例可参考 Spring AI 官方示例仓库的 weather 应用。本节的 POST 断言验证的是消息端点可达；要断言工具清单内容，推荐用 §6.2 的真实客户端做验收，或对 SSE 流做逐步断言。

### 6.2 Claude Desktop 接入

在 Claude Desktop 的配置文件里注册你的 Server（路径因操作系统而异，见客户端文档）：

```json
{
  "mcpServers": {
    "product-assistant": {
      "type": "sse",
      "url": "http://localhost:8080/sse"
    }
  }
}
```

> SSE 传输的客户端配置填 `/sse` 端点地址（`type: sse`）；若使用 Streamable HTTP 传输则填 `http://localhost:8080/mcp`。

### 6.3 端到端验收流程

重启 Claude Desktop 后，完整的验收路径：

```
  ① 发现    客户端连接 → initialize 握手 → tools/list
             你看到三个工具：search_products / get_stock / recommend_products
             （或你配置的自定义 name）

  ② 调用    对话中输入："iPhone 17 Pro 还有货吗？"
             → AI 读工具描述 → 先 search_products("iPhone")
             → 拿到 ID=1 → 再 get_stock(1)
             → 回答："iPhone 17 Pro 当前库存 42 件"

  ③ 反例    对话中输入："帮我查一下仓库里键盘的价格"
             → 若描述不清晰，AI 可能调错工具或拒绝调用
             → 这正是 §5.4 对比实验的现场版
```

验收通过的标志：**AI 在没有任何额外提示的情况下，正确选择了工具并给出了基于工具返回值的回答。**

---

## 7. 边界与选型

> 目标：知道什么场景下怎么做选择，什么坑不能踩。

### 7.1 webmvc 还是 webflux starter？

Spring AI 提供两个 HTTP 传输 starter：

| 维度     | `spring-ai-starter-mcp-server-webmvc`         | `spring-ai-starter-mcp-server-webflux` |
| -------- | --------------------------------------------- | -------------------------------------- |
| 底层     | Servlet（Tomcat）                             | Netty + Reactor                        |
| 线程模型 | 每请求一线程，工具方法阻塞执行也无妨          | 事件循环，工具方法禁止阻塞             |
| 适合     | 已有 MVC 技术栈的团队；工具全是同步 JDBC 查询 | 追求高并发长连接；工具方法本身非阻塞   |
| 本项目   | demo1 用的是 webmvc starter 同款技术栈        | 本指南的贯穿示例                       |

**选型核心问题只有一个：你的工具方法是不是阻塞的。** 全是同步 JDBC/HTTP 调用 → webmvc 省心；有真正的非阻塞数据源（如响应式 MongoDB、WebClient 调用链）→ webflux 才能发挥价值。

### 7.2 阻塞陷阱

这是 WebFlux 传输下最容易踩的坑：**ASYNC 模式的工具方法跑在事件循环线程上，一旦阻塞，所有 MCP 会话一起卡死。**

```
  事件循环线程池：Netty 默认很少的线程（通常 = CPU 核数）

  会话 A ──▶ [事件循环线程] ──▶ 工具方法里 JDBC 查询（阻塞 500ms）
  会话 B ──▶ [事件循环线程] ──▶ 工具方法里 JDBC 查询（阻塞 500ms）
  会话 C ──▶ 排队等线程……
                    │
                    只有少数几个线程被阻塞占满
                    → 全部会话超时，包括没在查数据库的
```

三种解法：

| 解法           | 做法                                                                                         | 适用                                 |
| -------------- | -------------------------------------------------------------------------------------------- | ------------------------------------ |
| ① 用 SYNC 模式 | `spring.ai.mcp.server.type: SYNC`，同步方法在服务自身线程池执行，阻塞不伤事件循环            | 工具方法天生同步——**默认就该这么选** |
| ② 方法非阻塞化 | 工具方法返回 `Mono`/`Flux`，内部全程用响应式数据源（如响应式 MongoDB、WebClient）            | 数据源本身支持响应式                 |
| ③ 显式卸载     | ASYNC 模式下把阻塞调用包进 `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` | 个别阻塞调用，量少可控               |

**判断口诀**：工具方法里写了 `jdbcTemplate.query(...)`、`mongoTemplate.find(...)`（阻塞版）或 `restTemplate.getForObject(...)`？那就选 SYNC 模式（解法①），别硬上 ASYNC。异步边界的更深入讨论见 [Spring Boot 多线程指南](spring-boot-multithreading-guide.md)。

### 7.3 版本矩阵

| Spring AI | Spring Boot   | 说明                                                              |
| --------- | ------------- | ----------------------------------------------------------------- |
| 1.0.x     | 3.4.x / 3.5.x | 使用 `@Tool`/`@ToolParam` 注解（旧名）                            |
| **1.1.x** | **3.5.x**     | **本指南版本**；`@McpTool`/`@McpToolParam`；SSE + Streamable HTTP |
| 2.0.x     | 4.0.x / 4.1.x | Boot 4 专属；SSE 传输标记 deprecated，官方推荐 STREAMABLE         |

> 本项目的 `pom.xml` 使用 Spring Boot 4.0.6：若未来要在项目内引入 MCP，需用 Spring AI 2.0.x（注解与配置属性以 2.0 文档为准）。矩阵以 Spring AI 官方文档为准，写作时已逐条核实。

### 7.4 延伸阅读

- 异步任务的边界与线程池设计：[Spring Boot 多线程指南](spring-boot-multithreading-guide.md)
- WebFlux 技术栈的另一个应用场景（Spring Cloud Gateway）：[微服务指南](spring-cloud-microservices-guide.md)
- 响应式编程基础：WebFlux/Reactor 深入指南（规划中，本节是它的上游引子）
- 官方资料：[Spring AI MCP Server 文档](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html)、[MCP 规范](https://modelcontextprotocol.io/)
