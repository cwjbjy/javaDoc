## 1. 前置概念与架构全景（§0 ~ §1）

- [ ] 1.1 §0 前置概念：单体 vs 微服务（痛点驱动、前端类比映射表、分布式八大谬误）
- [ ] 1.2 §1 全景图：完整架构 ASCII 图（客户端 → Gateway → 服务 → 基础设施）
- [ ] 1.3 §1 版本兼容矩阵（Spring Boot / Cloud / Alibaba / Nacos / Sentinel / Seata）
- [ ] 1.4 §1 贯穿场景介绍：电商三服务 + Docker Compose 总览

## 2. 服务注册与发现 — Nacos（§2）

- [ ] 2.1 问题演示：IP 硬编码的痛苦（RestTemplate 直连的脆弱性）
- [ ] 2.2 Nacos Docker 部署命令 + 控制台介绍
- [ ] 2.3 服务注册：依赖引入 + @EnableDiscoveryClient + application.yml
- [ ] 2.4 服务发现：Spring Cloud LoadBalancer + 服务名调用
- [ ] 2.5 前端类比：DNS 解析 + npm 包名

## 3. 远程服务调用 — OpenFeign（§3）

- [ ] 3.1 RestTemplate 的局限（URL 拼接、类型转换、模板代码）
- [ ] 3.2 OpenFeign：声明式接口 + @FeignClient 注解
- [ ] 3.3 超时配置、重试策略、日志级别
- [ ] 3.4 请求拦截器（传递认证 Token）
- [ ] 3.5 前端类比：TypeScript API 类型定义 + axios instance

## 4. API 网关 — Spring Cloud Gateway（§4）

- [ ] 4.1 为什么需要网关：客户端不应知道服务拓扑
- [ ] 4.2 路由配置：Path 断言 → Nacos 服务名 → 自动转发
- [ ] 4.3 过滤器：内置过滤器（AddRequestHeader、StripPrefix）+ 自定义 GlobalFilter
- [ ] 4.4 跨域 CORS 在网关统一配置
- [ ] 4.5 前端视角：网关 = Nginx 反向代理 + BFF 雏形

## 5. 统一配置管理 — Nacos Config（§5）

- [ ] 5.1 配置散落的问题：修改一个配置要改 N 个 application.yml
- [ ] 5.2 配置中心引入：bootstrap.yml + Nacos Config 依赖
- [ ] 5.3 @RefreshScope 热刷新演示
- [ ] 5.4 多环境 + 共享配置策略
- [ ] 5.5 前端类比：集中式环境变量平台（Vercel Env / Cloudflare Workers）

## 6. 服务容错 — Sentinel（§6）

- [ ] 6.1 雪崩效应：一张多米诺骨牌图讲清楚
- [ ] 6.2 Sentinel Dashboard Docker 部署
- [ ] 6.3 流量控制：QPS 限流规则
- [ ] 6.4 熔断降级：慢调用比例 / 异常比例 / 异常数
- [ ] 6.5 @SentinelResource 注解 + blockHandler / fallback
- [ ] 6.6 Feign 整合 Sentinel（fallbackFactory 降级逻辑）
- [ ] 6.7 前端类比：Error Boundary + Suspense 降级 UI

## 7. 分布式链路追踪 — Micrometer Tracing + Zipkin（§7）

- [ ] 7.1 问题：跨 5 个服务的请求，慢在哪一环？
- [ ] 7.2 核心概念：Trace ID / Span ID / 自动传播
- [ ] 7.3 依赖引入（micrometer-tracing-bridge-brave + zipkin-reporter-brave）
- [ ] 7.4 Zipkin Docker 部署 + UI 解读
- [ ] 7.5 前端类比：Chrome DevTools Network 面板 → Performance API 的跨服务版

## 8. 分布式事务 — Seata（§8）

- [ ] 8.1 问题：下单需要「创建订单 + 扣减库存」，跨了两个服务两个数据库
- [ ] 8.2 Seata AT 模式原理（两阶段提交简化版）
- [ ] 8.3 Seata Server Docker 部署 + 配置
- [ ] 8.4 @GlobalTransactional 注解使用
- [ ] 8.5 最终一致性思想：不是所有场景都需要强一致

## 9. 安全 — 微服务中的认证授权（§9）

- [ ] 9.1 认证该放在哪里？网关 vs 服务内部
- [ ] 9.2 网关统一认证方案：Gateway Filter 解析 JWT → 设置 Header 透传
- [ ] 9.3 服务间 Feign 调用时 Token 自动传递（RequestInterceptor）
- [ ] 9.4 与现有 Security 指南的衔接（引用而非重复）

## 10. 延伸阅读与实战决策（§10 ~ §11）

- [ ] 10.1 §10 延伸阅读：Spring Cloud Stream + RocketMQ 简介（异步通信 vs 同步调用）
- [ ] 10.2 §11 实战决策：何时该拆 / 何时不该拆
- [ ] 10.3 §11 组件选型决策树
- [ ] 10.4 §11 10 个常见反模式

## 11. 速查清单（§12）

- [ ] 11.1 依赖坐标速查（所有服务的 pom.xml 片段）
- [ ] 11.2 注解速查（@EnableDiscoveryClient / @FeignClient / @SentinelResource / @GlobalTransactional）
- [ ] 11.3 配置项速查（Nacos / Sentinel / Gateway / Seata 常用配置）
- [ ] 11.4 Docker Compose 完整文件速查
- [ ] 11.5 调用链路速查（从 Gateway → Order → Product → User 的完整配置轨迹）

## 12. Capability Spec

- [ ] 12.1 创建 `openspec/specs/microservices-guide/spec.md`
