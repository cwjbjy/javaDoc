## 1. 为什么需要 MCP Server（§1）

- [x] 1.1 痛点场景：AI Agent 调用传统 JSON API 的困难（参数猜测、接口发现、文档阅读成本）
- [x] 1.2 对比呈现：AI 眼中的"好 API"（自我描述）与人类眼中的"好 API"（RESTful 规范）差异
- [x] 1.3 引出 MCP 承诺：工具自我描述，核心心智模型首次亮相
- [x] 1.4 声明读者收益、前置知识与版本声称（Spring AI 1.1.x + Boot 3.5.x，链接 spring-ioc-di-guide / spring-mvc-guide）

## 2. MCP 协议最小认知（§2）

- [x] 2.1 client-server 架构 ASCII 图（Host/Client/Server 三角关系）
- [x] 2.2 JSON-RPC 消息格式：请求/响应最小示例
- [x] 2.3 tools 概念：工具自描述三要素（name/description/inputSchema）示例
- [x] 2.4 initialize 握手：协商能力与版本的最小说明
- [x] 2.5 明确"30 秒认知"边界：协议全量消息类型留给官方文档，不展开

## 3. Spring AI 编程模型（§3）

- [x] 3.1 `@McpTool` 注解：description 字段对 AI 可见性的意义
- [x] 3.2 `@McpToolParam` 注解：参数描述与类型约束如何进入 inputSchema
- [x] 3.3 注解自动扫描注册（`@Component` + annotation-scanner 配置）
- [x] 3.4 `McpSyncServer` vs `McpAsyncServer` 的线程模型差异（预告 §7 阻塞陷阱）
- [x] 3.5 依赖坐标：spring-ai-bom 1.1.x + `spring-ai-starter-mcp-server-webflux`

## 4. WebFlux 传输层与最小响应式认知（§4）

- [x] 4.1 starter 自动配置了什么：MCP 端点注册（注解式路由示意）
- [x] 4.2 SSE 传输直觉：`Flux<ServerSentEvent>` 数据流（不展开 Reactor 算子）
- [x] 4.3 SSE / STREAMABLE / STATELESS 三传输对照表（会话状态 / 服务端推送 / 客户端兼容 / 代理穿透 / 演进方向）
- [x] 4.4 "最小认知"边界标注：全文只此一处讲 WebFlux 内部

## 5. 自包含贯穿示例（§5）

- [x] 5.1 项目骨架：完整 pom 依赖坐标（spring-ai-bom 1.1.x + starter + Boot 3.5.x）
- [x] 5.2 内存数据层：Product 实体 + 内存存储，不引入外部数据库
- [x] 5.3 三个工具：`searchProducts`（关键词查询）/ `getStock`（按 ID 查库存）/ `recommendProducts`（类目推荐）
- [x] 5.4 工具描述质量对比：好描述 vs 坏描述对 AI 调用效果的影响
- [x] 5.5 配置类与启动类，给出运行命令与验证步骤（标注 complete example / not yet verified）
- [x] 5.6 服务身份与配置：application.yml（name/version/instructions + 环境变量占位符模式）

## 6. 测试与客户端接入（§6）

- [x] 6.1 `WebTestClient` 直测 MCP 端点：SSE 响应断言
- [x] 6.2 Claude Desktop `mcpServers` 配置 JSON（与 §5 端点路径一致）
- [x] 6.3 端到端演示流程：客户端发现工具 → 调用工具 → 观察结果

## 7. 边界与选型（§7）

- [x] 7.1 webmvc vs webflux 两种 MCP server starter 选型对照
- [x] 7.2 阻塞陷阱：事件循环线程上阻塞调用的机理（图）+ 三种解法（McpSyncServer / 非阻塞调用 / 显式卸载）+ ASYNC 第二坑（ThreadLocal 上下文丢失与 Reactor 上下文传播）
- [x] 7.3 版本矩阵（已核实）：1.0.x ↔ Boot 3.4/3.5、1.1.x ↔ Boot 3.5.x、Boot 4.0 需 2.0.x
- [x] 7.4 互链：multithreading-guide（异步边界前置）、microservices-guide（Gateway 延伸）、未来 WebFlux 指南占位、编程式工具注册（ToolCallback）提点

## 8. 验证与交付

- [x] 8.1 目录生成（≥4 个 H2 章节）
- [x] 8.2 运行 guide-writing 的 `validate_guide.py` 校验目录锚点与代码围栏
- [x] 8.3 运行 `openspec validate create-spring-ai-mcp-server-guide` 通过
- [x] 8.4 交付说明：代码证据分类标注核对 + Unverified 清单（版本矩阵核实状态、§5 示例是否实际运行）
