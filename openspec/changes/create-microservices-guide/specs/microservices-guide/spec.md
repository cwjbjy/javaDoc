# Microservices Guide

Spring Cloud 微服务企业级指南，涵盖从服务注册发现到分布式事务的完整 Spring Cloud 微服务知识体系。

## ADDED Requirements

### REQ-MSG-001: 前置概念与架构全景

指南 SHALL 包含前置概念章节（§0），说明单体架构与微服务架构的差异、微服务的承诺与代价、分布式系统八大谬误，以及面向前端开发者的概念类比映射表。

指南 SHALL 包含架构全景图章节（§1），使用 ASCII 图展示完整的微服务请求链路（客户端 → Gateway → 业务服务 → 基础设施），并包含精确的版本兼容矩阵（Spring Boot / Spring Cloud / Spring Cloud Alibaba / Nacos / Sentinel / Seata）。

#### Scenario: 读者建立全局认知

- **WHEN** 读者打开指南
- **THEN** 首先看到的是从客户端到数据库的完整请求链路全景图
- **AND** 每个组件的定位和职责在图中一目了然
- **AND** 版本兼容矩阵给出可直接复制的依赖坐标

### REQ-MSG-002: 服务注册与发现

指南 SHALL 包含 Nacos 服务注册与发现章节（§2），以问题驱动的方式展示从 IP 硬编码到服务发现的过程。内容应包括 Nacos Docker 部署、服务注册（@EnableDiscoveryClient）、服务发现（Spring Cloud LoadBalancer）。

#### Scenario: 读者理解服务发现的价值

- **WHEN** 读者阅读 §2
- **THEN** 首先看到 IP 硬编码带来的维护噩梦
- **THEN** 通过 Nacos 注册中心解决该问题
- **AND** 理解服务名替代 IP 地址的核心原理

### REQ-MSG-003: 远程服务调用

指南 SHALL 包含 OpenFeign 章节（§3），展示从 RestTemplate 到 Feign 声明式调用的演进。内容应包括 @FeignClient 注解使用、超时/重试配置、请求拦截器。

#### Scenario: 读者学会声明式服务调用

- **WHEN** 读者需要在 Order Service 中调用 Product Service
- **THEN** 指南展示如何通过 @FeignClient 接口声明实现调用
- **AND** 对比 RestTemplate 的模板代码，突出 Feign 的简洁性

### REQ-MSG-004: API 网关

指南 SHALL 包含 Spring Cloud Gateway 章节（§4），说明网关的定位（统一入口、路由转发、过滤器链）。内容应包括 Route/Predicate/Filter 三层模型、路由配置、CORS 统一处理。

#### Scenario: 读者理解网关的价值

- **WHEN** 读者阅读 §4
- **THEN** 理解客户端不应直接知道内部服务拓扑
- **AND** 学会通过 Path 断言将请求路由到正确的下游服务

### REQ-MSG-005: 统一配置管理

指南 SHALL 包含 Nacos Config 章节（§5），说明配置中心的必要性。内容应包括 bootstrap.yml 配置、@RefreshScope 热刷新、多环境与共享配置策略。

#### Scenario: 读者学会集中管理配置

- **WHEN** 读者需要修改多个服务的同一配置项
- **THEN** 指南展示如何在 Nacos 控制台一次性修改并热刷新

### REQ-MSG-006: 服务容错

指南 SHALL 包含 Sentinel 章节（§6），覆盖雪崩效应的成因与解决方案。内容应包括流量控制（QPS 限流）、熔断降级（慢调用比例/异常比例）、@SentinelResource 注解、Feign 整合 Sentinel。

#### Scenario: 读者理解如何防止级联故障

- **WHEN** Product Service 响应变慢
- **THEN** 指南展示 Sentinel 如何熔断对 Product Service 的调用
- **AND** 返回降级数据而非让调用方无限等待

### REQ-MSG-007: 分布式链路追踪

指南 SHALL 包含 Micrometer Tracing + Zipkin 章节（§7），说明 Trace ID/Span ID 概念、自动传播机制、Zipkin 可视化。

#### Scenario: 读者学会排查跨服务调用问题

- **WHEN** 一个请求跨越多 3 个服务
- **THEN** 指南展示如何在 Zipkin UI 中查看完整调用链
- **AND** 定位每个 Span 的耗时

### REQ-MSG-008: 分布式事务

指南 SHALL 包含 Seata 章节（§8），说明分布式事务的挑战（为什么 @Transactional 失效）。内容应包括 Seata AT 模式原理、@GlobalTransactional 注解、最终一致性思想。

#### Scenario: 读者理解跨服务事务

- **WHEN** 下单操作需要同时写 Order 表和扣减 Product 库存
- **THEN** 指南展示 @GlobalTransactional 如何保证原子性
- **AND** 说明 AT 模式的两阶段提交流程

### REQ-MSG-009: 安全

指南 SHALL 包含微服务安全章节（§9），说明网关统一认证 + JWT 透传方案。内容应与现有 `spring-security-guide.md` 衔接而非重复。

#### Scenario: 读者学会微服务认证方案

- **WHEN** 读者已有 Spring Security 单体认证知识
- **THEN** 指南展示如何在网关层统一处理 JWT 认证
- **AND** Token 如何通过 Feign RequestInterceptor 在服务间透传

### REQ-MSG-010: 延伸阅读与实战决策

指南 SHALL 包含延伸阅读章节（§10，Spring Cloud Stream + RocketMQ 简介）和实战决策章节（§11，拆分决策、组件选型决策树、10 个常见反模式）。

### REQ-MSG-011: 速查清单

指南 SHALL 包含速查清单章节（§12），提供依赖坐标速查、注解速查、配置项速查、Docker Compose 文件速查、调用链路速查。
