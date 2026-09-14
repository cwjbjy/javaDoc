# Spring AI MCP Server 指南

> Audience: 具备 Spring Boot 基础（理解 `@Component`、自动配置与 `application.yml`），希望把业务能力暴露给 AI Agent 的开发人员
> Outcome: 能用 `spring-ai-starter-mcp-server-webflux` 独立跑起一个 MCP Server，理解工具描述为何决定 AI 调用效果，并能规避阻塞陷阱
> Applicable version: Spring AI 1.1.7、Spring Boot 3.5.14、JDK 17

## Scope

这篇指南解决的是"如何让 AI Agent 发现并调用你的业务能力"。阅读前不需要任何响应式编程知识：MCP Server 的传输层由 starter 自动配置，你只需要写带注解的业务方法。

**涵盖**：MCP 协议最小认知、`@McpTool`/`@McpToolParam` 编程模型与编程式注册、HTTP 传输层（默认 SSE）、一个商品域贯穿示例、Spring 上下文测试与 MCP 客户端接入、选型边界。

**不涵盖**：完整 WebFlux/Reactor（只保留理解传输层所需的最小认知）、LLM 提示词工程、MCP client 端编程（只给测试所需的最小配置）。异步任务边界请参考 [Spring Boot 多线程指南](spring-boot-multithreading-guide.md)，Spring Cloud Gateway 等 WebFlux 技术栈延伸请参考 [微服务指南](spring-cloud-microservices-guide.md)。

> **核心认知**：MCP 让工具自我描述，AI 无需翻文档即可发现并调用业务能力。全篇都围绕这句话展开。

## 目录

1. [为什么需要 MCP Server](#1-为什么需要-mcp-server)
2. [MCP 协议](#2-mcp-协议)
3. [Spring AI 编程模型](#3-spring-ai-编程模型)
4. [HTTP 传输层](#4-http-传输层)
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

Model Context Protocol（MCP）正是为这个问题而生。它由 Anthropic 提出，并已被多种 IDE、桌面客户端与开发框架采用。具体支持的传输类型和配置方式取决于客户端版本。

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
- **版本声称**：Spring AI 1.1.7 + Spring Boot 3.5.14 + JDK 17。与其他发布线的边界见 [§7.4](#74-版本边界)。
- **贯穿示例**：一个"商品助手"MCP Server，提供三个工具（商品搜索、库存查询、类目推荐），全部代码自包含，不依赖本项目其他源码。

---

## 2. MCP 协议

> 目标：能看懂后面章节里的协议名词和消息流。不需要逐字节理解协议。

### 2.1 三个角色：Host / Client / Server

MCP 架构里有三个角色，各司其职：

```
  ┌──────────────────────────────────────────────┐
  │                  Host（宿主）                  │
  │     Claude Desktop / IDE / 你的业务应用        │
  │                                              │
  │   ┌─────────────┐                            │
  │   │ MCP Client  │                            │
  │   │ （内置的协议  │                            │
  │   │   客户端）   │                            │
  │   └──────┬──────┘                            │
  │          │                                   │
  │   LLM（大模型）── AI 的"大脑"，决定建议调哪个工具 │
  └──────────────────────────────────────────────┘
             │ JSON-RPC
             ▼
      ┌───────────────┐
      │  MCP Server   │
      │ （你要写的那个）│
      └───────────────┘
```

- **Host**：用户正在使用的应用（桌面客户端、IDE、你自己的程序），它管理 LLM 与一个或多个 **MCP Client**。
- **MCP Client**：负责与 MCP Server 通信、把工具清单喂给 LLM、执行 LLM 发起的工具调用。
- **MCP Server**：**你要写的东西**。它通常是 Host 之外的独立进程或远程服务，暴露"工具"（Tools），也可能暴露"资源"（Resources）和"提示模板"（Prompts）——本指南只讲 Tools。

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

一次完整的工具调用（`tools/call`）是：用户用自然语言描述需求 → AI 根据 `description` 选中工具 → 按 `inputSchema` 构造参数 → 客户端发出 `tools/call` 请求 → 服务端执行并返回结果。

---

## 3. Spring AI 编程模型

> 目标：用注解把业务方法变成 MCP 工具。这是整篇指南的核心章节。

### 3.1 依赖坐标

Spring AI 用 BOM 统一管理版本。`pom.xml` 里加两处：

```xml
<!-- 依赖管理：Spring AI BOM 1.1.7 -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.1.7</version>
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
import org.springaicommunity.mcp.annotation.McpTool;

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
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;

@McpTool(description = "按关键词模糊搜索商品。当用户想找商品但不知道具体 ID 时使用。返回商品列表。")
public List<Product> searchProducts(
        @McpToolParam(description = "搜索关键词，匹配商品名称，不区分大小写") String keyword,
        @McpToolParam(description = "最多返回的商品数量，取值 1～50", required = false) Integer limit) {
    // ...
}
```

每个 `@McpToolParam` 的 `description` 进入工具的 `inputSchema`，AI 据此决定参数值。要点：

- **写参数约束**：`required = true`（默认），可选参数设 `false`
- **写格式约定**：日期是 `yyyy-MM-dd` 还是时间戳？枚举有哪些取值？——写进 description
- **写默认行为**：`limit` 不传时怎么办？写清楚，AI 就不会猜

### 3.4 注册方式：注解扫描与编程式注册

Spring AI 1.1.x 注册工具有两条路：

| 路径         | 做法                                              | 适合                               |
| ------------ | ------------------------------------------------- | ---------------------------------- |
| 注解自动扫描 | `@McpTool` 方法 + Spring Bean，启动时扫描注册     | 工具自己写、数量固定               |
| 编程式注册   | 注册 `ToolCallback` / `ToolCallbackProvider` Bean | 包装已有方法、工具量大、运行时增删 |

#### 3.4.1 注解自动扫描

只要工具方法所在的类是 Spring Bean（`@Component`、`@Service` 等），starter 的自动配置就会把 `@McpTool` 方法注册到 MCP Server：

```java
import org.springaicommunity.mcp.annotation.McpTool;
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

#### 3.4.2 编程式注册：ToolCallback

**`ToolCallback` 是 Spring AI 的统一工具抽象**：一个 `ToolCallback` = 工具定义（name / description / inputSchema）+ 执行逻辑。starter 的自动配置会把容器里所有 `ToolCallback` Bean 和 `ToolCallbackProvider` Bean 收集起来，转换成 MCP 工具规格注册进服务端。

这也顺带回答了 `@Tool` 与 `@McpTool` 的关系：**`@Tool` 是应用内 AI 直接调用的工具注解（不走 MCP）**；把 `@Tool` 方法经 `MethodToolCallbackProvider` 包装成 `ToolCallback` 后，同样能发布为 MCP 工具——两个体系在 `ToolCallback` 这一层汇合。

三种注册形态：

> Illustrative fragment：展示三种 Bean 的注册形态，省略业务方法细节。

```java
// ① 把已有对象上的 @Tool 方法发布出去（最常见的包装方式）
@Bean
public ToolCallbackProvider legacyTools(OrderService orderService) {
    return MethodToolCallbackProvider.builder()
            .toolObjects(orderService) // 对象上没有 @Tool 方法会直接启动失败
            .build();
}

// ② 直接注册一组 ToolCallback（精细控制单个工具）
@Bean
public List<ToolCallback> customTools(StockService stockService) {
    return List.of(FunctionToolCallback
            .builder("queryStock", stockService::query)
            .description("查询指定商品 SKU 的实时库存")
            .inputType(StockQuery.class)
            .build());
}

// ③ 低一层：直接提供 MCP 工具规格清单（可混合自定义规格与转换结果）
@Bean
public List<McpServerFeatures.SyncToolSpecification> specs(List<ToolCallback> callbacks) {
    return McpToolUtils.toSyncToolSpecification(callbacks); // 也可手工逐个构建规格
}
```

注册细节（自动配置的既定行为）：

- **聚合范围**：`ToolCallback`、`List<ToolCallback>`、`ToolCallbackProvider`、`List<ToolCallbackProvider>` 四种形态的 Bean 全部纳入；
- **去重规则**：按工具名去重，**先注册的保留、后注册的同名工具被静默丢弃**——动态注册时要自己管好名字；
- **总开关**：`spring.ai.mcp.server.tool-callback-converter: false` 可关闭整个转换；
- **结果 MIME 类型**：`spring.ai.mcp.server.tool-response-mime-type.<工具名>` 可为单个工具指定返回值类型（如返回图片流）。

#### 3.4.3 运行时动态增删工具

工具清单不一定要在启动时定死。典型场景：数百个工具由配置中心或数据库管理，需要运行时启停、灰度。

**协议层有现成支持**：MCP 定义了 `notifications/tools/list_changed` 通知，服务端在握手时声明该能力（`spring.ai.mcp.server.tool-change-notification`，默认 `true`），客户端就能感知工具清单变化并重新拉取。

**SDK 层三个动作**（`McpSyncServer`）：`addTool(spec)` 注册、`removeTool(name)` 注销——只要声明了上述能力，增删时 SDK 会**自动向已连接客户端推送变更通知**；需要时也可手动调 `notifyToolsListChanged()`。

> Illustrative fragment：展示动态注册的组合方式；配置监听与 `spec` 的构建来源未展开。

```java
@Service
public class DynamicToolManager {

    private final McpSyncServer mcpServer;

    public DynamicToolManager(McpSyncServer mcpServer) {
        this.mcpServer = mcpServer;
    }

    public void register(ToolCallback callback) {
        mcpServer.addTool(McpToolUtils.toSyncToolSpecification(callback)); // SDK 自动通知客户端
    }

    public void unregister(String toolName) {
        mcpServer.removeTool(toolName);
    }
}
```

生产上的大规模工具管理通常是这个形态：

```
  配置中心 / 数据库                     MCP Server（有状态传输）
  ┌─────────────────┐                ┌──────────────────────────────┐
  │ 工具元数据       │   启动全量加载  │ DynamicToolManager           │
  │ · name/描述     │ ─────────────▶ │  · 启动：逐个 addTool()       │
  │ · 参数 schema   │   变更增量推送  │  · 监听变更：增量 add/remove  │
  │ · 启停开关      │ ─────────────▶ │  · SDK 自动推 list_changed    │
  └─────────────────┘                └──────────────────────────────┘
```

三个要点：

- **多实例部署的一致性**：动态变更只发生在收到变更的那个实例上，客户端连到其他实例时看到的还是旧清单。每个实例都要监听配置源、各自应用变更（配合 Nacos 等注册发现时尤其要注意）；
- **描述质量要求不变**：动态注册的工具同样由 name/description/inputSchema 决定 AI 会不会调它（§3.2 的标准一条不降）；
- **STATELESS 传输不适用**：它没有会话和推送通道，动态增删的语义与有状态传输不同，本节只覆盖 SSE / STREAMABLE 下的动态注册。

### 3.5 Sync 还是 Async？线程模型的选择

MCP Server 有两种运行模式，通过配置切换：

| 配置                                      | 实现             | 只注册的方法                     |
| ----------------------------------------- | ---------------- | -------------------------------- |
| `spring.ai.mcp.server.type: SYNC`（默认） | `McpSyncServer`  | 同步方法（返回普通对象）         |
| `spring.ai.mcp.server.type: ASYNC`        | `McpAsyncServer` | 响应式方法（返回 `Mono`/`Flux`） |

**重要规则**：SYNC 模式**只注册同步方法**，ASYNC 模式**只注册响应式方法**（返回 `Mono`/`Flux`/`Publisher`）。选错模式时，方法会被跳过并写入 WARN 日志——这是常见配置错误。

两种写法对比：

```java
// SYNC 模式：同步方法，返回普通对象
@McpTool(description = "按关键词搜索商品")
public List<Product> searchProducts(String keyword) {
    return productStore.findByKeyword(keyword);  // 阻塞调用，SDK 自动卸载到 boundedElastic
}

// ASYNC 模式：响应式方法，返回 Mono/Flux
@McpTool(description = "按关键词搜索商品")
public Flux<Product> searchProducts(String keyword) {
    return reactiveProductStore.findByKeyword(keyword);  // 非阻塞，全程响应式数据源
}
```

**Mono 与 Flux 的区别**：都是响应式数据容器，区别在于元素数量——`Mono<T>` 表示 0 或 1 个元素（类似 `Optional`），`Flux<T>` 表示 0 到 N 个元素（类似 `List` 但是惰性流）。上面示例里 SYNC 返回 `List<Product>`（一次性返回全部），ASYNC 返回 `Flux<Product>`（逐个推送，适合大数据集或流式场景）。

**`reactiveProductStore` 是谁**：响应式数据源，比如 Reactive MongoDB、R2DBC（关系型数据库的响应式驱动）、WebClient（响应式 HTTP 客户端）。它们底层用非阻塞 I/O，遇到数据库查询或网络请求时不会卡住线程，而是立即返回控制权给事件循环，等 I/O 完成后再通过回调继续处理。这正是 ASYNC 模式能扛高并发的关键——事件循环线程不被阻塞，能服务更多请求。

---

## 4. HTTP 传输层

> 目标：知道三种传输怎么选、端点为什么默认裸奔。

### 4.1 三种传输怎么选：SSE / STREAMABLE / STATELESS

Spring AI 1.1.x 的 WebFlux starter 支持三种传输，用 `spring.ai.mcp.server.protocol` 切换：

| 传输            | 配置                   | 默认端点                         |
| --------------- | ---------------------- | -------------------------------- |
| SSE（默认）     | `protocol: SSE`        | GET `/sse` + POST `/mcp/message` |
| Streamable HTTP | `protocol: STREAMABLE` | POST `/mcp`                      |
| Stateless HTTP  | `protocol: STATELESS`  | POST `/mcp`                      |

推送通道结构——看清推送发生在哪里：

```
  SSE：请求与推送走两条路
  ────────────────────────────────
  客户端  GET /sse ──────────▶ 服务端   （先建立长连接）
  客户端  POST /mcp/message ──▶ 服务端   （请求从这里进，只回 202）
    ◀─────────────────────── 事件1、事件2、事件3……（响应、通知都从 GET 长连接推出）

  STREAMABLE：推送在"同一个 POST 的响应"里
  ────────────────────────────────
  客户端  POST /mcp ──────▶ 服务端
    ◀─────────────────────── 响应切换为 SSE 流：data: 消息1、data: 消息2

  STATELESS：纯一问一答，无会话
  ────────────────────────────────
  客户端  POST /mcp ──────▶ 服务端
    ◀──── 恰好一个 JSON-RPC 响应（可流式分块，但不能再推第二条）
```

对照取舍：

| 维度          | SSE                              | Streamable HTTP                 | Stateless HTTP           |
| ------------- | -------------------------------- | ------------------------------- | ------------------------ |
| 客户端兼容    | 旧版 HTTP+SSE 客户端             | 新客户端                        | Streamable HTTP 客户端   |
| 代理/网关穿透 | GET 长连接对代理配置敏感         | 响应可能也是 SSE 流，同样需配置 | 无状态请求，负载均衡友好 |
| 演进方向      | 1.1.x 可用，主要用于兼容旧客户端 | MCP 规范当前推荐的 HTTP 传输    | 为微服务/云原生设计      |

**选型建议**：兼容旧客户端选 SSE；客户端支持新规范优先选 `STREAMABLE`；多实例部署、需负载均衡选 `STATELESS`（代价：不支持服务端主动推送）。本指南贯穿示例用默认 SSE，不写 `protocol` 配置就是它。

> 实战案例：某生产环境金融数据 MCP 服务以 `type: ASYNC` + `protocol: STATELESS` 部署在微服务集群（Nacos 注册发现），实例无状态，客户端请求可被任意实例处理。

### 4.2 安全警告：端点默认裸奔

官方文档明确警告：HTTP 传输的 MCP 端点**默认没有任何认证**。starter 只负责接线端点，不施加任何认证/授权——任何能访问到端口的客户端，都可以列出并调用你注册的**全部**工具、资源与提示词。在暴露到 localhost 之外前，必须在它前面加一层安全边界。

**加安全层的两个常见方向**：

| 方式            | 做法                                                      | 适用场景                           |
| --------------- | --------------------------------------------------------- | ---------------------------------- |
| Spring Security | 在应用内加认证，直接保护 MCP 端点                         | MCP Server 集成在 Spring Boot 应用 |
| 网关鉴权        | 在网关（如 Spring Cloud Gateway）统一认证，应用本身不感知 | 微服务部署、网关统一收口           |

本指南示例跑在 WebFlux 上，Spring Security 方向用 `spring-boot-starter-security`（同一个 starter 按类路径自动适配 MVC 与 WebFlux），核心是声明一个 `SecurityWebFilterChain` Bean，把 MCP 端点划进"必须认证"的范围：

> Illustrative fragment：只展示保护 MCP 端点的形态；用户存储、密码编码器、客户端如何携带凭证均未展开。

```java
@Bean
public SecurityWebFilterChain mcpSecurity(ServerHttpSecurity http) {
    return http
            .authorizeExchange(exchanges -> exchanges
                    // MCP 的两个端点都要保护：GET 长连接和 POST 请求缺一不可
                    .pathMatchers("/sse", "/mcp/message").authenticated()
                    .anyExchange().permitAll())
            // 示意用 Basic Auth；生产通常选 OAuth2 / JWT 等标准方案
            .httpBasic(Customizer.withDefaults())
            .build();
}
```

两个容易遗漏的点：

- **SSE 的 GET 端点同样要保护**。只拦 `POST /mcp/message` 不够——攻击者连上 `/sse` 后照样能收到推送、发起握手。
- **客户端要跟着改**。端点加了认证后，第 6 章测试里的 MCP 客户端必须在请求里携带凭证（如 `Authorization` 头），否则连不上。

本地开发时只监听 localhost 没有实际风险；但只要打算部署到测试/生产环境，鉴权就不是"以后再加"的事，而是工具注册的一部分。

---

## 5. 实战：商品助手 MCP Server

> 目标：跑起一个真实可用的 MCP Server。本节为 Complete example，业务代码完整给出；`pom.xml` 只展示相对标准 Spring Boot 项目的新增部分。
> 示例状态：Complete example, not yet verified（本指南编写环境未实际运行，命令与坐标完整给出）

### 5.1 项目骨架

基于 Spring Boot 3.5.x 的普通 Maven 项目（`spring-boot-starter-parent` + Java 17），`pom.xml` 只需新增两处：

**① 引入 Spring AI BOM 统一管理版本**（对齐 Spring Cloud BOM 的用法，保证所有 Spring AI 模块版本一致）：

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.1.7</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**② 添加 WebFlux 传输的 MCP Server starter**（版本由 BOM 管理，无需手写；starter 会连带引入 WebFlux 运行环境）：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webflux</artifactId>
</dependency>
```

测试依赖使用 `spring-boot-starter-test`（已内置 WebTestClient），无需额外坐标。

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

import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProductTools {

    private final ProductStore store;

    public ProductTools(ProductStore store) {
        this.store = store;
    }

    @McpTool(name = "search_products", description = """
            按关键词模糊搜索商品。当用户想找某个商品但不知道具体 ID 时使用。
            返回商品列表（含 ID、名称、类目、价格、库存），无匹配时返回空列表。""")
    public List<Product> searchProducts(
            @McpToolParam(description = "搜索关键词，匹配商品名称，不区分大小写") String keyword,
            @McpToolParam(description = "最多返回的商品数量，取值 1～50；省略时为 10", required = false)
            Integer limit) {
        return store.searchByKeyword(keyword, resolveLimit(limit, 10));
    }

    @McpTool(name = "get_stock", description = """
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

    @McpTool(name = "recommend_products", description = """
            按类目推荐商品。当用户想看某一类目下有什么可选商品时使用。
            类目可选值：手机数码、电脑办公。返回该类目下的商品列表。""")
    public List<Product> recommendProducts(
            @McpToolParam(description = "商品类目，可选值：手机数码、电脑办公") String category,
            @McpToolParam(description = "最多返回的商品数量，取值 1～50；省略时为 5", required = false)
            Integer limit) {
        return store.recommendByCategory(category, resolveLimit(limit, 5));
    }

    private static int resolveLimit(Integer limit, int defaultValue) {
        if (limit == null) {
            return defaultValue;
        }
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("limit 必须在 1～50 之间");
        }
        return limit;
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

> 验证步骤：本示例的完整验收路径在 §6（Spring 上下文自动化验证 + MCP 客户端手动接入）。

### 5.6 服务身份与配置

示例的完整 `application.yml`：

```yaml
spring:
  ai:
    mcp:
      server:
        name: product-assistant # 服务名称：initialize 握手时声明，客户端 UI 显示
        version: 1.0.0 # 服务版本：升级后客户端可感知变化
        instructions: "商品助手：帮助用户搜索商品、查询库存、按类目推荐" # 给 AI 的全局使用说明
        type: SYNC # 本示例工具全是同步方法
        # protocol: SSE            # 默认 SSE，省略不写即可
```

三个身份字段随 `initialize` 握手发给客户端：

- `name` / `version`：服务标识，出现在客户端日志与 UI 里。生产服务会带上业务名与真实版本号（例如真实金融数据服务的 `caihui-mcp-server` v1.0.7）
- `instructions`：给客户端/AI 的全局使用说明，作为所有工具描述的"背景板"，可选但推荐填写

生产环境通常用环境变量占位符管理这些值，配合配置中心（Nacos / Spring Cloud Config）在部署时注入：

```yaml
server:
  port: ${SERVER_PORT:8080}
spring:
  ai:
    mcp:
      server:
        name: ${MCP_SERVER_NAME:product-assistant}
        version: ${MCP_SERVER_VERSION:1.0.0}
```

本地开发用默认值零配置启动，部署时同一份代码靠环境变量切换——真实生产 MCP 服务普遍采用这种模式。

---

## 6. 测试与客户端接入

> 目标：证明你的工具真的能被发现、被调用。

### 6.1 启动 Spring 上下文并检查工具注册

> 示例状态：Complete example, not yet verified（依赖坐标在 §5.1 已给出）

不需要真实 AI。启动 Spring 上下文并从 `McpSyncServer` 读取工具清单，可以同时验证自动配置、Bean 扫描和工具注册：

```java
package com.example.product;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import io.modelcontextprotocol.server.McpSyncServer;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductMcpServerApplicationTests {

    @Autowired
    private McpSyncServer mcpServer;

    @Test
    void registersThreeTools() {
        assertThat(mcpServer.listTools())
                .extracting(tool -> tool.name())
                .containsExactlyInAnyOrder(
                        "search_products",
                        "get_stock",
                        "recommend_products");
    }
}
```

运行 `mvn test`。这个测试不验证 HTTP 传输；SSE 是有状态传输，客户端必须先连接 `/sse`，取得会话对应的消息端点后才能发送 JSON-RPC，不能把 `tools/list` 直接 POST 到固定的 `/mcp/message` 并期待同步拿到工具清单。传输层请用 §6.2 的真实 MCP 客户端验收。

### 6.2 Claude Desktop 接入

若目标客户端支持用 URL 配置远程 MCP Server，可按该客户端当前版本的配置格式注册 SSE 地址。下面只是常见形态，字段名与配置入口应以客户端文档为准：

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

> SSE 传输填 `/sse` 端点地址；若服务端改为 Streamable HTTP，则填 `http://localhost:8080/mcp`。某些桌面客户端的本地配置只接受 `command`/`args` 形式的 stdio Server，此时不能直接套用上面的 URL 配置。

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

| 维度     | `spring-ai-starter-mcp-server-webmvc`      | `spring-ai-starter-mcp-server-webflux`                                      |
| -------- | ------------------------------------------ | --------------------------------------------------------------------------- |
| 底层     | Servlet（默认 Tomcat）                     | Reactor（默认 Reactor Netty）                                               |
| 线程模型 | Servlet 请求线程；适合同步 HTTP 处理       | 传输层响应式；SYNC 工具默认卸载到 bounded elastic，ASYNC 工具必须保持非阻塞 |
| 适合     | 已有 MVC 技术栈；希望沿用 Servlet 运维模型 | 已有 WebFlux 技术栈；需要响应式调用链                                       |

### 7.2 阻塞陷阱

WebFlux 传输下最容易踩的坑：**ASYNC 模式下，阻塞调用会占住事件循环线程（默认只有几个），导致所有请求排队超时。**

```
事件循环线程池：Netty 默认很少的线程（通常 = CPU 核数）

会话 A ──▶ [事件循环线程] ──▶ JDBC 查询（阻塞 500ms）
会话 B ──▶ [事件循环线程] ──▶ JDBC 查询（阻塞 500ms）
会话 C ──▶ 排队等线程……
              │
              少数线程被阻塞占满 → 全部会话超时
```

三种解法：

| 解法           | 做法                                                                             | 适用                                 |
| -------------- | -------------------------------------------------------------------------------- | ------------------------------------ |
| ① 用 SYNC 模式 | `spring.ai.mcp.server.type: SYNC`，SDK 自动卸载阻塞调用                          | 工具方法天生同步——**默认就该这么选** |
| ② 方法非阻塞化 | 工具方法返回 `Mono`/`Flux`，用响应式数据源                                       | 数据源本身支持响应式                 |
| ③ 显式卸载     | 把阻塞调用包进 `Mono.fromCallable(...).subscribeOn(Schedulers.boundedElastic())` | 个别阻塞调用，量少可控               |

**判断口诀**：工具方法里写了 `jdbcTemplate.query(...)`、`restTemplate.getForObject(...)` 这类阻塞调用？选 SYNC 模式，别硬上 ASYNC。

### 7.3 ASYNC 模式的上下文传播

ASYNC 模式下还有一个容易踩的坑：**ThreadLocal 上下文丢失**。

问题机制：你在 WebFilter 里把用户身份、请求头、链路追踪 ID 放进 `ThreadLocal`，这是 Servlet/MVC 时代的常见做法。但 ASYNC 模式下，工具方法跑在 Reactor 的事件循环线程上，和 WebFilter 所在的请求线程**不是同一个线程**。`ThreadLocal` 是线程绑定的，线程切换后直接 `get()` 读不到——响应式框架不会自动搬运 `ThreadLocal`。

影响：工具方法里拿不到用户身份（鉴权失败）、拿不到请求头（多租户路由错误）、拿不到链路 ID（日志追踪断裂）。

**生产解法**：开启 Reactor 的上下文传播机制，把自定义的 `ThreadLocal` 注册为"可跨线程搬运"。

> Illustrative fragment：`RequestHeadersHolder` 是自定义的 `ThreadLocalAccessor` 实现，负责跨线程搬运请求头。

```java
// ① 自定义 ThreadLocalAccessor：定义如何读取、写入、清理 ThreadLocal
public class RequestHeadersHolder implements ThreadLocalAccessor<RequestHeaders> {
    private static final ThreadLocal<RequestHeaders> HOLDER = new ThreadLocal<>();

    @Override
    public Object key() { return "request-headers"; }

    @Override
    public RequestHeaders getValue() { return HOLDER.get(); }

    @Override
    public void setValue(RequestHeaders value) { HOLDER.set(value); }

    @Override
    public void setValue() { HOLDER.remove(); }  // 清理，防止线程复用污染
}

// ② 启动时注册
@PostConstruct
void started() {
    ContextRegistry.getInstance()
            .registerThreadLocalAccessor(new RequestHeadersHolder());
    Hooks.enableAutomaticContextPropagation();  // 开启自动上下文传播
}

// ③ WebFilter 里写入（请求线程）
@Component
public class AuthFilter implements WebFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        RequestHeaders headers = extractHeaders(exchange.getRequest());
        return Mono.deferContextual(ctx -> {
            // 把请求头放进 Reactor Context，框架会自动搬运到工具执行线程
            return chain.filter(exchange)
                    .contextWrite(ctx -> ctx.put("request-headers", headers));
        });
    }
}

// ④ 工具方法里读取（事件循环线程）
@McpTool
public String getUserInfo() {
    // 框架自动从 Reactor Context 搬运到当前线程的 ThreadLocal
    RequestHeaders headers = RequestContextHolder.getHeaders();
    return "User: " + headers.getUserId();
}
```

关键点：`Hooks.enableAutomaticContextPropagation()` 开启后，Reactor 会在每次线程切换时自动调用 `ThreadLocalAccessor` 的 `setValue()` 把上下文搬运过去，工具执行结束后调用 `setValue()`（无参版本）清理，防止线程池复用时污染下一个请求。

### 7.4 版本边界

本指南只验证 Spring AI 1.1.7 + Spring Boot 3.5.14，不把其他发布线的注解、配置属性和传输默认值混入示例。Spring AI 的版本与 Spring Boot 基线是易变信息，升级时应以目标 Spring AI 版本的官方系统要求和升级说明为准。

> 本项目根 `pom.xml` 使用 Spring Boot 4.0.6，不能直接复制本指南的 1.1.7 依赖组合。若要在本项目内引入 MCP，应先选择官方声明兼容 Boot 4.0.6 的 Spring AI 版本，再按该版本文档核对注解包名、配置属性与传输支持情况。

### 7.5 延伸阅读

- 异步任务的边界与线程池设计：[Spring Boot 多线程指南](spring-boot-multithreading-guide.md)
- WebFlux 技术栈的另一个应用场景（Spring Cloud Gateway）：[微服务指南](spring-cloud-microservices-guide.md)
- 响应式编程基础：WebFlux/Reactor 深入指南（规划中，本节是它的上游引子）
- 官方资料：[Spring AI MCP Server 文档](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html)、[MCP 规范](https://modelcontextprotocol.io/)
- 大规模工具管理：见 §3.4.2（编程式注册）与 §3.4.3（运行时动态增删）
