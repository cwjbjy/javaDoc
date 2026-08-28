## Context

docs 知识库已有 21 篇 Spring 指南（框架级为主：MVC、事务、安全；tool 级先例：knife4j、logstash-logback-encoder），但 MCP 主题完全空白。`spring-boot-multithreading-guide.md` 明确排除了 WebFlux 话题，`create-microservices-guide` 变更的 Gateway 章节也需要 WebFlux 认知上游。本变更新增一篇 MCP Server 指南（路线 A：只写文档，不碰项目代码），属于 tool 级指南家族。

约束：

- 版本声称走 docs 惯例（Boot 3.x 线）
- 读者默认具备 Spring Boot 基础（有 spring-ioc-di-guide、spring-mvc-guide 可链接）
- 证据门槛遵循 guide-writing skill 的契约（代码示例分类标注）

## Goals / Non-Goals

**Goals:**

- 让读者读完能用 `spring-ai-starter-mcp-server-webflux` 独立跑起一个 MCP Server
- 读者保留一个心智模型："MCP 让工具自我描述，AI 无需翻文档即可调用业务能力"
- 为微服务指南 Gateway 章节、未来可能的 WebFlux 指南提供可链接的上游
- 所有可变事实（版本矩阵、传输方式）以官方文档核实为准

**Non-Goals:**

- 不教完整 WebFlux/Reactor（只保留 §4、§7 所需的最小认知）
- 不教 LLM 提示词工程、不覆盖 MCP client 端编程
- 不修改 `pom.xml`、不新增项目依赖、不动任何源码（路线 A）
- 不追求覆盖 MCP 协议全量消息类型与版本协商细节

## Decisions

### D1. 指南形态：渐进式（progressive），而非参考型（reference）

七章之间有严格依赖链：不知道"为什么 AI 调不动 JSON API"就看不懂协议动机；不懂协议就看不懂 `@Tool` 描述为何重要；不懂传输层就看不懂阻塞陷阱。参考型结构（各章独立跳读）会破坏这个因果链。
备选：参考型结构 —— 被否，因为 MCP Server 的知识点不是平行分类而是因果序列。

### D2. 版本声称：Spring AI 1.1.x + Spring Boot 3.5.x

- docs 惯例要求 Boot 3.x 线（spring-mvc-guide、multithreading-guide 均如此）
- 官方兼容矩阵（2026-08 已核实）：Spring AI 1.0.x ↔ Boot 3.4/3.5；1.1.x ↔ Boot 3.5.x；Boot 4.0 需要 Spring AI 2.0.x
- 选 1.1.x：符合 docs 惯例；demo1 的 Boot 4.0.6 若未来引入 MCP 需用 2.0.x，届时另开变更
- 指南开头显式声明版本，可变事实以官方兼容矩阵为准

### D3. §5 贯穿示例：商品域三工具 + 内存数据

工具设计：`searchProducts`（按关键词查询）、`getStock`（按商品 ID 查库存）、`recommendProducts`（基于类目推荐）。选商品域是为了与 MVC 指南的 Product 主题、微服务指南的 Order/Product 链路保持业务语言一致。
数据层用内存 Map/List 而非外部数据库：MCP 指南不教数据访问，外部 DB 会引入 R2DBC/JDBC 等无关概念；内存数据让示例"依赖坐标完整 + 可直接运行"，达到 complete example 证据等级。
备选：连接真实 MongoDB —— 被否，路线 A 不允许项目依赖，且数据层复杂度会挤占 MCP 主题篇幅。

### D4. 最小 WebFlux 认知的精确边界

只引入两个概念：`Flux<ServerSentEvent>`（SSE 数据流）、"事件循环线程上禁止阻塞"。端点注册用读者熟悉的注解式路由示意（`@RestController` + `@GetMapping`），不引入函数式路由（`RouterFunction`）——starter 内部实现与教学示意分开。不引入：Reactor 算子体系（map/flatMap 语义不展开，遇到只用自然语言说明）、背压理论、Reactor 线程调度器（Scheduler）。§7 阻塞陷阱的三解法（McpSyncServer / 非阻塞调用 / 显式卸载）是边界内容，属于选型而非 Reactor 教学。
备选：完全不提 WebFlux 内部 —— 被否，§4"starter 自动配置了什么"是 spec 的硬性要求，不提内部就讲不清 SSE vs streamable HTTP 取舍。

### D5. 传输层取舍：SSE / STREAMABLE / STATELESS 用对照表呈现

Spring AI 1.1.x 同时支持 SSE（webflux starter 默认）、STREAMABLE 与 STATELESS（已核实：STATELESS 为无状态 Streamable-HTTP，请求间不维持会话，不支持服务端推送 elicitation/sampling/ping）。指南不选边，用对照表呈现取舍维度：会话状态、服务端推送能力、与旧客户端兼容性、穿透代理的友好度、演进方向。STATELESS 的加入源于真实生产项目调研（金融数据 MCP 服务以 ASYNC + STATELESS 部署于微服务集群），同时吸收该项目的身份配置（name/version/instructions）与 ThreadLocal 上下文传播实践。这与 guide-writing 的"三个以上可复用选择配决策表"原则一致。

### D6. 证据门槛：§5 为 complete example，全篇诚实标注

§5 给出完整依赖坐标（spring-ai BOM + starter）与运行命令，标注为"complete example"。写作环境若无法实际运行，则保持"not yet verified"并列入交付说明的 Unverified 清单——不把未运行的代码标为可运行。§1-§4 的概念片段标 Illustrative fragment。

### D7. 互链策略

- 链接 `spring-boot-multithreading-guide.md`：§7 阻塞陷阱引用其"异步边界"认知作为前置
- 链接 `spring-cloud-microservices-guide.md`：§7 指出 Gateway 同为 WebFlux 技术栈，作为延伸阅读
- 预留未来 WebFlux 指南链接位置（文中一句话占位，不创建死链）
- 不反向修改现有指南文件（路线 A 边界；若未来需要双向互链，另开变更）

## Risks / Trade-offs

- [Spring AI 1.1.x API 细节与我的知识不一致（如 transport 配置项名称）] → 已核实并修正两处：版本矩阵（1.1.x 不支持 Boot 4.0）与注解名（`@McpTool`/`@McpToolParam`）；写作时其余细节继续以官方 1.1 文档为准
- [§5 示例未实际运行，读者复现时可能踩环境坑] → 证据分类诚实标注 not yet verified；运行命令与依赖坐标给全；交付说明列出 Unverified 项
- [MCP 协议讲解失控，指南膨胀为协议手册] → §2 硬性限定四要素；协议全量内容明确排除（spec 范围排除条款）
- [阻塞陷阱一节滑向 Reactor 教学] → D4 边界约束；三解法只讲选择不讲算子
- [未来 WebFlux 指南出现后与本指南重叠] → 本指南只保留"最小认知"，重叠区天然是 WebFlux 指南的领地；届时再开变更做双向互链

## Open Questions

- （无阻塞性问题。协议细节与版本矩阵在写作阶段以官方文档核实。）
