## Context

受众是已掌握 Spring Boot 单体开发全部知识的前端开发者（已阅读并掌握 `docs/` 中 17 篇指南）。现有指南覆盖了 Spring IoC/DI、MVC、Security、MongoDB、Validation、MapStruct、Jackson、异常处理、事务、事件、Filter/Interceptor 等「单体内部」技术栈。本指南是第一篇跨入「分布式」领域的文档，需要建立从单体到微服务的认知迁移。

项目现有技术栈：Spring Boot 4.0.6、Java 17、Maven、Lombok、MapStruct 1.5.5。现有 demo1 是 MongoDB 单体应用。但本指南独立于 demo1，使用 MySQL 以贴合主流电商场景。

## Goals / Non-Goals

**Goals:**

- 构建完整的 Spring Cloud 微服务认知体系，覆盖 8 大核心组件
- 版本依赖精确锁定，读者可一键复制 pom.xml
- Docker Compose 一键部署全部基础设施
- 使用贯穿案例（电商三服务）让概念落地
- 前端类比辅助理解分布式概念
- 延续现有指南风格：渐进式学习 + ASCII 架构图 + 速查清单

**Non-Goals:**

- 不涉及 Kubernetes（聚焦 Spring Cloud 层面）
- 不深入 Docker 原理（仅提供可用的 compose 文件）
- 不覆盖消息驱动（RocketMQ 仅延伸阅读）
- 不涉及 demo1 现有代码改造
- 不实现任何可运行的微服务代码（纯知识文档）

## Decisions

### 1. 技术版本矩阵（已通过官方文档验证）

| 组件                 | 版本               | 选型理由                      |
| -------------------- | ------------------ | ----------------------------- |
| Spring Boot          | 4.0.x              | 与用户现有环境一致            |
| Spring Cloud         | 2025.1.x (Oakwood) | Boot 4.0 的唯一对应发布列车   |
| Spring Cloud Alibaba | 2025.1.0.0         | 唯一适配 Boot 4.0 的 SCA 版本 |
| Nacos                | 3.1.1              | SCA 2025.1.0.0 内嵌版本       |
| Sentinel             | 1.8.9              | SCA 内嵌                      |
| Seata                | 2.5.0              | SCA 内嵌                      |
| MySQL                | 8.0                | Docker 部署                   |
| JDK                  | 17                 | 与现有项目一致                |

### 2. 指南结构（12 章，预估 2500~3000 行）

```
0. 前置概念：为什么需要微服务
   - 单体舒适区 → 增长痛点 → 微服务的承诺与代价
   - 前端类比映射表（帮前端开发者建立直觉）

1. 全景图：一张图看懂所有组件
   - 完整架构图（从客户端到数据库的请求链路）
   - 版本兼容矩阵
   - 贯穿场景介绍（用户/商品/订单三服务）

2. 服务注册与发现 — Nacos
   - 问题驱动：IP 写死的悲剧
   - Docker 部署 Nacos
   - 服务注册 + 服务发现 + LoadBalancer

3. 远程服务调用 — OpenFeign
   - RestTemplate → OpenFeign 演进
   - 声明式 HTTP 客户端
   - 超时/重试/日志配置

4. API 网关 — Spring Cloud Gateway
   - Gateway 定位（前端最相关的组件）
   - Route / Predicate / Filter 三层模型
   - 与前端的关系：CORS、统一入口

5. 统一配置管理 — Nacos Config
   - 配置中心的诞生
   - @RefreshScope 热刷新
   - 多环境 + 共享配置

6. 服务容错 — Sentinel
   - 雪崩效应可视化
   - 流量控制 + 熔断降级
   - Sentinel 控制台

7. 分布式链路追踪 — Micrometer Tracing + Zipkin
   - Trace ID 概念
   - 自动传播（零代码）
   - Zipkin 可视化

8. 分布式事务 — Seata
   - 为什么 @Transactional 失效了
   - Seata AT 模式
   - 最终一致性思想

9. 安全 — 微服务中的认证授权
   - 网关统一认证 + JWT 透传
   - 与现有 Security 指南的衔接

10. 延伸阅读：消息驱动 — Spring Cloud Stream + RocketMQ
    - 异步通信场景
    - 与同步调用的对比

11. 实战决策
    - 何时拆/何时不拆
    - 组件选型决策树
    - 10 个常见反模式

12. 速查清单
    - 依赖坐标速查
    - 注解速查
    - 配置项速查
    - Docker Compose 速查
```

### 3. 贯穿场景：电商三服务

```
                    ┌──────────────┐
                    │   Browser    │
                    │  (前端调用)   │
                    └──────┬───────┘
                           │
                    ┌──────▼───────┐
                    │   Gateway    │  ← Spring Cloud Gateway (:8080)
                    │  统一入口     │
                    └──────┬───────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
     ┌────────▼───┐  ┌─────▼──────┐  ┌─▼──────────┐
     │user-service│  │product-svc │  │order-service│
     │  (:8081)   │  │  (:8082)   │  │  (:8083)    │
     │  MySQL:    │  │  MySQL:    │  │  MySQL:     │
     │  users     │  │  products  │  │  orders     │
     └────────────┘  └────────────┘  └─────────────┘
                           │
              ┌────────────┼────────────┐
              │            │            │
        ┌─────▼────┐ ┌─────▼────┐ ┌─────▼────┐
        │  Nacos   │ │ Sentinel │ │  Zipkin  │
        │  :8848   │ │  :8080   │ │  :9411   │
        └──────────┘ └──────────┘ └──────────┘
```

贯穿案例的调用链：前端 → Gateway → Order Service → (Feign) → Product Service（查询商品信息）→ (Feign) → User Service（查询用户信息）。这条链路天然覆盖注册发现、远程调用、网关、容错、追踪等所有核心概念。

### 4. 写作风格（延续现有指南模式）

- **渐进式**：每章一个问题驱动 → 演示痛点 → 引入解决方案 → 代码示例 → 回顾
- **图表密集**：ASCII 架构图、流程图、对比表、决策树
- **速查清单**：每章结尾 + 全局速查（第 12 章）
- **代码可复制**：pom.xml 依赖、application.yml 配置、Java 代码均为完整可运行片段
- **前端类比**：在关键概念跳跃处插入，帮助前端开发者建立直觉，但不喧宾夺主

### 5. Docker Compose 基础设施

```yaml
# 一键启动所有基础设施
services:
  nacos: # 服务注册 + 配置中心
  mysql-user: # 用户服务数据库
  mysql-product: # 商品服务数据库
  mysql-order: # 订单服务数据库
  sentinel: # 流量控制控制台
  zipkin: # 链路追踪可视化
  seata: # 分布式事务协调器
```

指南中只提供 compose 文件和必要的配置说明，不深入 Docker 原理。

## Risks / Trade-offs

- **[版本过新]** Spring Cloud Alibaba 2025.1.0.0 是首个适配 Boot 4.0 的版本，社区踩坑资料较少。缓解：指南中使用的均为核心功能（注册发现、配置管理），已通过官方验证。
- **[MySQL vs MongoDB]** 用户现有项目使用 MongoDB，本指南使用 MySQL。这是刻意选择——电商场景下 MySQL 的事务支持更成熟，且 Seata AT 模式对 MySQL 支持更好。指南中会简要提及 MongoDB 在微服务中的适用场景。
- **[文档长度]** 预估 2500~3000 行，阅读时间约 2~3 小时。缓解：目录导航清晰，每章独立可读，速查清单支持快速查阅。
- **[无运行时代码]** 本指南是知识文档，不包含可运行的微服务代码。读者需要自行创建项目。缓解：所有代码片段完整可复制，pom.xml 和 application.yml 提供完整版本。

## Open Questions

- 第 9 章「安全」的深度：是简要说明网关统一认证方案，还是深入 JWT 透传 + Spring Security 微服务配置？建议中等深度——说明方案选型和关键配置，详细实现引用现有 Security 指南。
- JSON 响应格式：是否沿用 demo1 的 `{code, message, data}` 统一包装？建议在指南中使用此格式以保持一致性，这也是企业级 API 的推荐做法。
