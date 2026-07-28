## Why

我已掌握 Spring Boot 单体应用开发（IoC/DI、MVC、Security、MongoDB、Validation、异常处理、事务、事件等 17 篇指南覆盖的完整知识体系），现在需要学习 Spring Cloud 微服务体系。当前 `docs/` 中的指南全部聚焦于「单体内部」的技术栈，缺少「服务之间」的分布式知识——服务发现、远程调用、网关、配置中心、熔断降级、链路追踪、分布式事务。本指南将填补这个空白，作为从单体到微服务的认知桥梁。

## What Changes

- **新增** `docs/spring-cloud-microservices-guide.md`：一份 2500~3000 行的企业级 Spring Cloud 微服务指南
- 内容覆盖 Spring Cloud 生态的 8 大核心领域（服务发现、远程调用、网关、配置中心、熔断降级、链路追踪、分布式事务、安全），外加前置概念、实战决策、速查清单
- 贯穿场景：电商三服务（用户 / 商品 / 订单），数据库使用 MySQL
- 基础设施通过 Docker Compose 一键部署（Nacos、Sentinel、Zipkin、MySQL、Seata）
- 技术版本锁定：Spring Boot 4.0.x + Spring Cloud 2025.1.x + Spring Cloud Alibaba 2025.1.0.0
- 面向受众：已掌握 Spring Boot 的前端开发者，使用前端类比辅助理解分布式概念
- 消息驱动（RocketMQ）作为「延伸阅读」章节，不作为正文核心内容

## Capabilities

### New Capabilities

- `microservices-guide`: 一份完整的 Spring Cloud 微服务知识文档，包含架构全景图、组件逐一深入、版本兼容矩阵、贯穿案例、速查清单

## Impact

- `docs/spring-cloud-microservices-guide.md`: 新增文件，约 2500~3000 行
- `openspec/specs/microservices-guide/spec.md`: 新增 capability spec
- 无代码修改（纯文档交付物）
- 无向后兼容影响
