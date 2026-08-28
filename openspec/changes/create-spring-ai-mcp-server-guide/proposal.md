## Why

AI Agent 生态正在把"AI 能否调用你的业务能力"变成日常需求，而 MCP（Model Context Protocol）已是事实标准。docs 知识库目前没有任何 MCP 主题的指南，读者面对 `spring-ai-starter-mcp-server-webflux` 这类依赖时无从下手；同时既有多线程指南明确排除了 WebFlux 话题，微服务指南的 Gateway 章节也缺乏上游认知支撑。借 Spring AI 1.1.x 已 GA 且同时支持 Boot 3.5 / 4.0 的时机，补上一篇小而扎实的 MCP Server 指南正当其时。

## What Changes

- 新增 `docs/Spring/spring-ai-mcp-server-guide.md`：渐进式七章指南，教读者用 Spring AI + WebFlux 传输把业务能力包装成 AI 可调用的 MCP 工具。
- 版本声称锁定 Spring AI 1.1.x + Spring Boot 3.5.x（符合 docs 惯例的 3.x 线；1.1.x 兼容 Boot 4，与项目 pom 的 4.0.6 不冲突）。
- 路线 A：示例全部自包含，不修改 `pom.xml`、不新增依赖、不动任何项目源码。
- 只引入"最小 WebFlux 认知"（SSE 传输与阻塞陷阱两处），不展开 Reactor；为未来可能的 WebFlux 指南预留链接。
- 与既有指南互链：`spring-boot-multithreading-guide.md`（异步边界）、`spring-cloud-microservices-guide.md`（Gateway 底层即 WebFlux），不重复其内容。

## Capabilities

### New Capabilities

- `mcp-server-guide`: 指南的章节结构、核心心智模型、版本声称、示例策略、证据门槛，以及与既有指南的边界和非目标。

### Modified Capabilities

（无。本变更不修改任何现有 spec。）

## Impact

- 仅新增一个文档文件 `docs/Spring/spring-ai-mcp-server-guide.md`。
- 无代码、无 API、无依赖、无配置变更（路线 A）。
- 与现有文档的链接关系：本指南将链接 `spring-boot-multithreading-guide.md` 与 `spring-cloud-microservices-guide.md`，并在文中声明"不涵盖完整 WebFlux/Reactor"。
