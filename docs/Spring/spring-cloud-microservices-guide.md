# Spring Cloud 微服务企业级指南

> 本指南系统介绍 Spring Cloud 微服务完整知识体系。先建立分布式认知（为什么需要微服务、全景架构），再逐类深入服务发现、远程调用、网关、配置中心、熔断降级、链路追踪、异步消息、安全。每章一个问题驱动，同一「电商三服务」场景贯穿全文。
>
> 技术栈基线：Spring Boot 3.5.14 / Spring Cloud 2025.0.0 / Spring Cloud Alibaba 2025.0.0.0 / Nacos Client 3.0.3 / Java 17；链路追踪 SkyWalking 9.x；消息队列 RabbitMQ 3.13 + Kafka 3.x。后续新增组件时必须先验证它们与这组依赖的兼容性。
>
> 面向读者：已掌握 Spring Boot 单体开发（IoC/DI、MVC、Security、数据访问、异常处理），准备学习微服务的开发者。如果你是前端出身，指南中嵌入了前端类比帮你快速建立直觉。

---

## 目录

1. [全景图：Spring Cloud 微服务完整架构](#1-全景图spring-cloud-微服务完整架构)
   - [1.1 一张图看懂所有组件](#11-一张图看懂所有组件)
   - [1.2 版本兼容矩阵](#12-版本兼容矩阵)
   - [1.3 贯穿场景：电商三服务](#13-贯穿场景电商三服务)
   - [1.4 Docker Compose 一键部署](#14-docker-compose-一键部署)
   - [1.5 数据库迁移：Flyway](#15-数据库迁移flyway)
2. [Nacos：配置加载、服务注册与发现](#2-nacos配置加载服务注册与发现)
   - [2.1 本项目完整启动链路](#21-本项目完整启动链路)
   - [2.2 启动与配置导入](#22-启动与配置导入)
   - [2.3 每个应用的最小本地配置](#23-每个应用的最小本地配置)
   - [2.4 Nacos 中的服务配置](#24-nacos-中的服务配置)
   - [2.5 依赖范围](#25-依赖范围)
   - [2.6 客户端负载均衡 — Spring Cloud LoadBalancer](#26-客户端负载均衡--spring-cloud-loadbalancer)
   - [2.7 注册与发现](#27-注册与发现)
   - [2.8 生产实践：namespace、group 与共享配置](#28-生产实践namespacegroup-与共享配置)
   - [2.9 本章回顾](#29-本章回顾)
3. [远程服务调用 — OpenFeign](#3-远程服务调用--openfeign)
   - [3.1 OpenFeign 核心思想](#31-openfeign-核心思想)
   - [3.2 三步接入 Feign](#32-三步接入-feign)
   - [3.3 Feign 配置：超时、日志](#33-feign-配置超时日志)
   - [3.4 请求拦截器：Token 透传](#34-请求拦截器token-透传)
   - [3.5 生产实践：Feign API 模块独立成 jar](#35-生产实践feign-api-模块独立成-jar)
   - [3.6 本节回顾](#36-本节回顾)
4. [API 网关 — Spring Cloud Gateway](#4-api-网关--spring-cloud-gateway)
   - [4.0 问题：客户端该调哪个服务](#40-问题客户端该调哪个服务)
   - [4.1 Gateway 三大核心概念](#41-gateway-三大核心概念)
   - [4.2 Gateway 项目搭建](#42-gateway-项目搭建)
   - [4.3 自定义 GlobalFilter：拦截模式](#43-自定义-globalfilter拦截模式)
   - [4.4 CORS 统一配置](#44-cors-统一配置)
   - [4.5 生产实践：网关超时配置](#45-生产实践网关超时配置)
   - [4.6 本节回顾](#46-本节回顾)
5. [服务容错 — Sentinel](#5-服务容错--sentinel)
   - [5.0 问题：雪崩效应](#50-问题雪崩效应)
   - [5.1 Sentinel 是什么](#51-sentinel-是什么)
   - [5.2 三步接入 Sentinel](#52-三步接入-sentinel)
   - [5.3 流量控制：QPS 限流](#53-流量控制qps-限流)
   - [5.4 熔断降级：慢调用自动熔断](#54-熔断降级慢调用自动熔断)
   - [5.5 Sentinel 规则类型速览](#55-sentinel-规则类型速览)
   - [5.6 生产实践：规则持久化到 Nacos 与网关流控](#56-生产实践规则持久化到-nacos-与网关流控)
   - [5.7 本节回顾](#57-本节回顾)
6. [分布式链路追踪 — SkyWalking](#6-分布式链路追踪--skywalking)
   - [6.0 问题：跨服务请求像黑盒](#60-问题跨服务请求像黑盒)
   - [6.1 核心概念：Trace ID 与 Span ID](#61-核心概念trace-id-与-span-id)
   - [6.2 SkyWalking 与 Zipkin 方案的本质差异](#62-skywalking-与-zipkin-方案的本质差异)
   - [6.3 三步接入 SkyWalking](#63-三步接入-skywalking)
   - [6.4 SkyWalking UI 解读](#64-skywalking-ui-解读)
   - [6.5 日志关联：让每行日志带上 Trace ID](#65-日志关联让每行日志带上-trace-id)
   - [6.6 本节回顾](#66-本节回顾)
7. [安全 — 微服务中的认证授权](#7-安全--微服务中的认证授权)
   - [7.0 问题：认证该放在哪里](#70-问题认证该放在哪里)
   - [7.1 网关统一认证：完整实现](#71-网关统一认证完整实现)
   - [7.2 下游服务如何获取当前用户](#72-下游服务如何获取当前用户)
   - [7.3 Feign 调用时 Token 透传](#73-feign-调用时-token-透传)
   - [7.4 下游服务的 SecurityContext 适配](#74-下游服务的-securitycontext-适配)
   - [7.5 本节回顾](#75-本节回顾)
8. [消息队列 — RabbitMQ](#8-消息队列--rabbitmq)
   - [8.0 问题：同步调用的三个痛点](#80-问题同步调用的三个痛点)
   - [8.1 核心概念：Exchange、Queue、Binding](#81-核心概念exchangequeuebinding)
   - [8.2 三步接入 RabbitMQ](#82-三步接入-rabbitmq)
   - [8.3 发送消息：RabbitTemplate](#83-发送消息rabbittemplate)
   - [8.4 接收消息：@RabbitListener](#84-接收消息rabbitlistener)
   - [8.5 可靠投递：Confirm、Return 与手动 ack](#85-可靠投递confirmreturn-与手动-ack)
   - [8.6 死信队列：消息的兜底处理](#86-死信队列消息的兜底处理)
   - [8.7 本节回顾](#87-本节回顾)
9. [消息队列 — Kafka](#9-消息队列--kafka)
   - [9.0 问题：每天上亿条行为数据怎么收](#90-问题每天上亿条行为数据怎么收)
   - [9.1 核心概念：Topic、Partition、Consumer Group](#91-核心概念topicpartitionconsumer-group)
   - [9.2 三步接入 Kafka](#92-三步接入-kafka)
   - [9.3 发送消息：KafkaTemplate](#93-发送消息kafkatemplate)
   - [9.4 接收消息：@KafkaListener 与手动 ack](#94-接收消息kafkalistener-与手动-ack)
   - [9.5 RabbitMQ vs Kafka：为什么很多团队两个都用](#95-rabbitmq-vs-kafka为什么很多团队两个都用)
   - [9.6 本节回顾](#96-本节回顾)
10. [实战决策](#10-实战决策)
    - [10.1 什么时候该拆？什么时候不该拆？](#101-什么时候该拆什么时候不该拆)
    - [10.2 组件选型决策树](#102-组件选型决策树)
    - [10.3 10 个常见反模式](#103-10-个常见反模式)
11. [速查清单](#11-速查清单)
    - [11.1 依赖坐标速查](#111-依赖坐标速查)
    - [11.2 注解速查](#112-注解速查)
    - [11.3 配置项速查](#113-配置项速查)
    - [11.4 Docker Compose 速查](#114-docker-compose-速查)
    - [11.5 服务端口速查](#115-服务端口速查)
    - [11.6 调用链路速查](#116-调用链路速查)
12. [延伸阅读](#12-延伸阅读)
    - [12.1 分布式事务 — Seata AT 模式](#121-分布式事务--seata-at-模式)
    - [12.2 Spring Cloud Stream：消息队列的统一抽象](#122-spring-cloud-stream消息队列的统一抽象)

---

## 1. 全景图：Spring Cloud 微服务完整架构

### 1.1 一张图看懂所有组件

```
                        ┌──────────────┐
                        │   Browser    │
                        │  (前端应用)   │
                        └──────┬───────┘
                               │  HTTP
                               │
                        ┌──────▼───────────────────────────────┐
                        │         API 网关（Gateway）            │
                        │  ┌─────────────────────────────────┐  │
                        │  │ 统一入口、路由转发、认证、限流    │  │
                        │  │ CORS 在此统一处理                 │  │
                        │  └─────────────────────────────────┘  │
                        └──────┬───────────────────────────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
     ┌────────▼───┐   ┌───────▼──────┐  ┌──────▼──────┐
     │user-service│   │product-service│  │order-service│
     │ MySQL      │   │  MySQL        │  │  MySQL      │
     │ (users)    │   │  (products)   │  │  (orders)   │
     └─────┬──────┘   └───────┬───────┘  └──────┬──────┘
           │                  │                  │
           │      Feign       │     Feign        │
           │◄────────────────►│◄────────────────►│
           │                  │                  │
           └──────────────────┼──────────────────┘
                              │
        服务注册 / 配置拉取 / 心跳上报 / 链路数据上报 / 规则下发
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
  ┌─────▼─────┐  ┌───────────▼──┐  ┌──────────────▼──┐
  │   Nacos   │  │   Sentinel   │  │   SkyWalking    │
  │ 服务注册  │  │ 流量控制     │  │ 链路追踪可视化   │
  │ 配置中心  │  │ 熔断降级     │  │ 服务拓扑 + 告警  │
  └───────────┘  └──────────────┘  └─────────────────┘

        ┌─────────────────────┴─────────────────────┐
        │                异步消息层                   │
  ┌─────▼─────┐                           ┌─────────▼─┐
  │ RabbitMQ  │                           │   Kafka   │
  │ 业务消息   │                           │ 日志/行为  │
  │ 可靠投递   │                           │ 数据管道   │
  └───────────┘                           └───────────┘
```

**各组件的职责一句话**：

| 组件           | 解决什么问题        | 在什么位置                                         |
| -------------- | ------------------- | -------------------------------------------------- |
| **Nacos**      | 服务发现 + 配置管理 | 基础层：所有服务启动时向它注册，调用时从它查询地址 |
| **OpenFeign**  | 服务间 HTTP 调用    | 通信层：业务服务之间互相调用的工具                 |
| **Gateway**    | 统一入口 + 路由转发 | 边界层：客户端唯一入口，内网服务不直接暴露         |
| **Sentinel**   | 防止级联故障        | 防御层：嵌入在每个服务中，监控调用、限流、熔断     |
| **SkyWalking** | 定位调用链瓶颈      | 观测层：agent 探针零侵入采集调用数据，画拓扑图     |
| **RabbitMQ**   | 业务异步解耦        | 消息层：订单通知、任务派发等业务消息，强调可靠投递 |
| **Kafka**      | 高吞吐数据管道      | 消息层：日志、行为埋点、数据同步，强调吞吐与回放   |

> **为什么消息队列有两个？** 这不是重复建设。RabbitMQ 擅长"一条消息必须可靠送达一个消费者"的业务场景（路由灵活、确认机制完善），Kafka 擅长"海量数据持续流过、多方订阅、可回放"的数据管道场景（顺序写磁盘、分区水平扩展）。真实生产环境经常两个都用：业务事件走 RabbitMQ，日志和行为数据走 Kafka。§9.5 有完整对比。

### 1.2 版本兼容矩阵

Spring Cloud 是版本敏感型生态。Spring Boot、Spring Cloud、Spring Cloud Alibaba 三者必须使用同一兼容组合。以下是当前 `microservice-demo` 已通过 Maven 构建验证的基线：

```
Spring Boot          3.5.14          ← 当前项目基座
    │
    └── Spring Cloud  2025.0.0        ← 当前项目导入的 BOM
           │
           └── Spring Cloud Alibaba  2025.0.0.0  ← 当前项目导入的 BOM
                   │
                   ├── Nacos Client   3.0.3
                   └── Sentinel       1.8.9

独立组件（不在上述 BOM 内，版本单独选择）：
                   ├── SkyWalking     9.x（agent + OAP + UI，agent 与应用依赖解耦）
                   ├── RabbitMQ       3.13.x（spring-boot-starter-amqp 由 Boot BOM 管理）
                   └── Kafka          3.x（spring-kafka 由 Boot BOM 管理）
```

**为什么选 Spring Cloud Alibaba？国内 vs 国际技术选型对比**：

```
                    国际主流                                国内主流
                    ─────────                              ─────────

注册中心             Eureka (已凉) / Consul                 Nacos ★
远程调用             OpenFeign（声明式 HTTP）                OpenFeign / Dubbo
网关                 Spring Cloud Gateway                   Spring Cloud Gateway
配置中心             Spring Cloud Config / Consul           Nacos ★
熔断降级             Resilience4j                          Sentinel ★
链路追踪             Micrometer Tracing + Zipkin            SkyWalking（国内采用率极高）
消息队列             RabbitMQ / Kafka                       RabbitMQ / Kafka / RocketMQ
分布式事务           自研 / Saga 模式                       Seata（AT 模式）★（见 §12.1 延伸阅读）
```

> 标 ★ 的是 Spring Cloud Alibaba 组件。2019 年 Netflix 宣布技术栈进入维护模式后，国内企业大规模从 Eureka + Hystrix 迁移到 Nacos + Sentinel——阿里系组件在双十一级别场景下久经考验，中文社区活跃，且提供注册 + 配置 + 熔断的一站式方案，无需拼凑多个项目。本指南因此选择 Spring Cloud Alibaba 作为核心依赖；链路追踪选择 SkyWalking（国产 Apache 顶级项目，agent 探针对代码零侵入，国内企业采用率极高）；消息队列选择 RabbitMQ + Kafka 双栈（生产环境最常见的组合）。

### 1.3 贯穿场景：电商三服务

全文所有代码示例围绕同一个电商场景展开。三个服务、三个数据库、一条核心调用链：

```
用户下单的请求链路
─────────────────────────────────────

前端 POST /api/orders（userId=1, productId=42, quantity=2）
    │
    ▼
Gateway (:8080)                              ← JWT 统一认证（§7）
    │ Path=/api/orders/** → 路由到 order-service
    ▼
order-service (:8083)
    │
    ├──→ Feign 调用 product-service (:8082)  ← Sentinel 熔断保护（§5）
    │    查询商品信息、扣减库存
    │
    ├──→ Feign 调用 user-service (:8081)
    │    查询用户信息、扣减余额
    │
    ├──→ 写入 orders 表（MySQL order_db）
    │
    ├──→ 发 RabbitMQ 消息「订单已创建」         ← 业务异步消息（§8）
    │    通知消费者异步发短信/推送，失败可重试
    │
    └──→ 发 Kafka 消息「下单行为日志」          ← 高吞吐数据管道（§9）
         日志消费者异步落库、统计分析

整条链路：SkyWalking agent 自动采集 Trace（§6），一行代码都不用写
```

这条链路天然覆盖本指南的所有核心概念：

- **服务发现**：order-service 调用 product-service 时，用服务名而非 IP
- **远程调用**：Feign 声明式接口完成跨服务 HTTP 通信
- **网关**：前端只调用 Gateway，不关心后端有几个服务
- **容错**：如果 product-service 响应慢，Sentinel 熔断保护 order-service
- **追踪**：一次下单请求的完整链路在 SkyWalking 中可视化
- **异步消息**：发通知这种"做了就行、不用等"的事交给 RabbitMQ；行为日志这种"量大、要分析"的数据交给 Kafka

> **跨库一致性说明**：扣库存（product_db）和写订单（order_db）是两个数据库，`@Transactional` 管不住。实战中更常用消息驱动的最终一致性 + 幂等重试（§8、§9），强一致的分布式事务方案（Seata）已移至延伸阅读 §12.1——它是重要知识，但不是每个团队的必选项。

### 1.4 Docker Compose 一键部署

所有基础设施通过 Docker Compose 一键启动。将以下文件保存为 `docker-compose.yml`，放在项目根目录，执行 `docker-compose up -d`：

```yaml
version: "3.8"
services:
  # ========== 服务注册 + 配置中心 ==========
  nacos:
    # 当前学习项目使用 Nacos 3.0.3；服务端与客户端均由同一 3.x 大版本演进。
    image: nacos/nacos-server:v3.0.3
    container_name: nacos
    environment:
      MODE: standalone
      NACOS_AUTH_ENABLE: "false"
      # Nacos 3.x 启动脚本要求以下三项非空，即使本地关闭认证也一样。
      # NACOS_AUTH_TOKEN 这里为了演示提供一个固定值
      NACOS_AUTH_TOKEN: bG9jYWwtbGVhcm5pbmctbmFjb3MtdG9rZW4tMjAyNi0wOC0wNQ==
      NACOS_AUTH_IDENTITY_KEY: serverIdentity
      NACOS_AUTH_IDENTITY_VALUE: local-learning
    ports:
      - "8848:8848"
      - "9848:9848" # gRPC 端口（Nacos 2.x 引入，3.x 沿用）
      # 容器内控制台仍为 8080；映射到宿主机 8084，避免与 Gateway :8080 冲突。
      - "8084:8080"
    healthcheck:
      # test是固定配置
      test:
        [
          "CMD-SHELL",
          "curl -fsS http://localhost:8848/nacos/v1/ns/operator/metrics >/dev/null || exit 1",
        ]
      interval: 10s
      timeout: 5s
      retries: 18
    volumes:
      - nacos-data:/home/nacos/data

  # 当前项目将 Nacos 配置以 YAML 保存在仓库中，并在 Nacos 健康后自动导入。
  nacos-init:
    image: curlimages/curl:8.12.1
    depends_on:
      nacos:
        # 表示 nacos-init 必须等到 Nacos 的健康检查通过后才启动
        condition: service_healthy
    volumes:
      - ./infra/nacos:/configs:ro
      # 容器启动后，/bin/sh 执行 /configs/import.sh
    entrypoint: ["/bin/sh", "/configs/import.sh"]
    restart: "no"

  # ========== 数据库（三个服务各一个库） ==========
  mysql-user:
    image: mysql:8.0
    container_name: mysql-user
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: user_db
    ports:
      - "3307:3306"
    volumes:
      # 命名卷由 Docker 管理；容器重建后仍保留 user_db 数据。
      - mysql-user-data:/var/lib/mysql

  mysql-product:
    image: mysql:8.0
    container_name: mysql-product
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: product_db
    ports:
      - "3308:3306"
    volumes:
      # 容器内 MySQL 的默认数据目录。
      - mysql-product-data:/var/lib/mysql

  mysql-order:
    image: mysql:8.0
    container_name: mysql-order
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: order_db
    ports:
      - "3309:3306"
    volumes:
      - mysql-order-data:/var/lib/mysql

volumes:
  nacos-data:
  mysql-user-data:
  mysql-product-data:
  mysql-order-data:
```

> **启动顺序**：使用当前 Compose 时直接执行 `docker compose up -d`。`nacos-init` 通过健康检查等待 Nacos 后再导入配置；它完成后显示 `Exited (0)` 是正常状态。
>
> **首期范围**：当前 Compose 只启动 Nacos、三个 MySQL 和 `nacos-init`。Sentinel Dashboard、SkyWalking OAP、RabbitMQ、Kafka 是本指南后续章节的学习主题，暂未接入当前项目；学习对应章节时，按该章节给出的独立 `docker run` / Compose 配置添加即可（§5.2、§6.3、§8.2、§9.2）。

---

### 1.5 数据库迁移：Flyway

Docker Compose 只负责创建空的 `user_db`、`product_db`、`order_db`；表结构不应依赖手工执行 SQL。当前项目的每个业务服务使用 Flyway，在首次连接自己的数据库时执行迁移脚本。

```text
服务读取 Nacos 数据源配置
  → 创建 DataSource
  → Flyway 扫描 classpath:db/migration
  → 执行尚未记录的迁移脚本
  → 写入 flyway_schema_history
  → 应用完成初始化并注册到 Nacos
```

`classpath:db/migration` 是 Flyway 的默认约定目录。因此 order-service 中的实际文件：

```text
order-service/src/main/resources/db/migration/V1__create_orders.sql
```

会被打包进应用 classpath，并在 `order_db` 中只执行一次。文件名格式为 `V<版本号>__<说明>.sql`，版本号后的两个下划线不可省略。

order-service 的 Nacos 配置位于 `infra/nacos/order-service.yaml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3309/order_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: root
    password: root123
  flyway:
    enabled: true
```

对应的首个迁移脚本：

```sql
-- order-service/src/main/resources/db/migration/V1__create_orders.sql
create table orders (
    id bigint auto_increment primary key,
    user_id bigint not null,
    product_id bigint not null,
    product_name varchar(120) not null,
    unit_price decimal(12,2) not null,
    quantity int not null,
    created_at timestamp not null default current_timestamp
);
```

业务服务的 `pom.xml` 还需要：

```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>
```

后续变更应新增例如 `V2__add_order_status.sql`，不要修改已经在任何环境执行过的 `V1__create_orders.sql`。Flyway 用 `flyway_schema_history` 记录和校验版本；开发环境若要从零开始，应明确删除对应数据库卷后再启动。

---

## 2. Nacos：配置加载、服务注册与发现

Nacos 在本项目中有两个职责：**配置中心**保存端口、数据源、Gateway 路由等运行配置；**注册中心**保存已启动实例，供 Gateway 和 OpenFeign 按服务名调用。

nacos-init 只把 infra/nacos/\*.yaml 导入配置中心；应用启动后，Nacos Discovery 才注册服务实例。

### 2.1 本项目完整启动链路

```text
docker compose up -d
  └── nacos-init 导入 infra/nacos/*.yaml

启动 product-service
  ├── 本地 application.yml：应用名、Nacos 地址、config import
  ├── Nacos product-service.yaml：server.port=8082、数据源
  ├── 应用监听 :8082
  └── Discovery 注册 product-service:8082
```

### 2.2 启动与配置导入

执行 docker compose up -d。Nacos 主地址为 localhost:8848，控制台为 http://localhost:8084/。nacos-init 显示 Exited (0) 表示导入成功；它不会启动或注册 Java 应用。

### 2.3 每个应用的最小本地配置

四个模块的 src/main/resources/application.yml 只保留连接 Nacos 所需配置：

```yaml
spring:
  application: { name: product-service }
  config: { import: "optional:nacos:${spring.application.name}.yaml" }
  cloud:
    nacos:
      discovery: { server-addr: "${NACOS_SERVER_ADDR:localhost:8848}" }
      config:
        {
          server-addr: "${NACOS_SERVER_ADDR:localhost:8848}",
          file-extension: yaml,
        }
```

### 2.4 Nacos 中的服务配置

infra/nacos/product-service.yaml 由 nacos-init 导入：

```yaml
server: { port: 8082 }
spring:
  datasource:
    url: jdbc:mysql://localhost:3308/product_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
    username: root
    password: root123
```

gateway-service.yaml、user-service.yaml、product-service.yaml、order-service.yaml 分别保存 8080 至 8083 的端口，以及各自数据源或网关路由。本地文件负责**连接配置中心**，Nacos YAML 负责**集中运行配置**；

### 2.5 依赖范围

**父 POM（版本统一管理）**：根 pom.xml 通过 BOM 锁定所有 Spring Cloud 组件版本，子模块不需要写 `<version>`：

```xml
<!-- 根 pom.xml -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2025.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-alibaba-dependencies</artifactId>
            <version>2025.0.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**各服务通用依赖**（所有子模块都需要这两个）：

```xml
<!-- Nacos 服务注册与发现 -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>

<!-- Nacos 配置中心（通过 spring.config.import 导入，无需 bootstrap） -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
```

**LoadBalancer** 不是每个服务都加：

| 模块                          | 是否需要 | 原因                 |
| ----------------------------- | -------- | -------------------- |
| gateway-service               | 是       | lb:// 路由           |
| order-service                 | 是       | OpenFeign 服务名调用 |
| user-service、product-service | 否       | 当前没有下游调用     |

根 pom.xml 的 dependencyManagement 只锁定版本，不会把依赖加入子模块；不要在父工程 dependencies 中加入 LoadBalancer。

### 2.6 客户端负载均衡 — Spring Cloud LoadBalancer

#### 为什么需要 LoadBalancer

服务发现（§2.7）解决了"实例在哪里"——Nacos 返回一组 IP。但如果有 3 个实例，请求发给谁？这就是 LoadBalancer 的职责：**从多个实例中选一个，把请求发出去**。

> **什么是"多实例"？** 同一个微服务（如 product-service）部署了 3 份，跑在不同机器上，各自独立向 Nacos 注册。目的是**负载均衡**（请求分散到多台机器）和**高可用**（一个挂了还有 2 个继续服务）。流量大了加实例，流量小了减实例——这就是水平扩展。

```
                    Nacos 返回
                    ──────────
                    ① 192.168.1.10:8082
                    ② 192.168.1.11:8082
                    ③ 192.168.1.12:8082
                         │
order-service ──→ LoadBalancer ──→ 选一个实例 ──→ 发起 HTTP 请求
                    │
                    默认策略：轮询（Round Robin）
                    第 1 次 → ①    第 2 次 → ②    第 3 次 → ③    第 4 次 → ① ...
```

> **前端类比**：LoadBalancer ≈ Nginx 的 `upstream`。前端把请求发给 Nginx，Nginx 从后端列表中轮询选一个。区别是 LoadBalancer 跑在**调用方进程内**（客户端负载均衡），不需要额外的 Nginx 节点。

#### 谁需要 LoadBalancer

| 调用方式                             | 谁在用 LoadBalancer | 触发场景               |
| ------------------------------------ | ------------------- | ---------------------- |
| Gateway `lb://`                      | gateway-service     | 网关路由转发到下游服务 |
| OpenFeign `@FeignClient(name="xxx")` | order-service       | 通过服务名调用其他服务 |

> **关键点**：LoadBalancer 只部署在**调用方**。被调用的服务（如 user-service、product-service）不需要加此依赖。

#### 依赖与使用

LoadBalancer 已在 §2.5 的依赖范围表中说明。添加依赖后，Gateway 和 OpenFeign **自动使用它**，无需额外配置：

```xml
<!-- 调用方模块添加（如 order-service、gateway-service） -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
```

Gateway 的 `lb://` 前缀和 OpenFeign 的 `@FeignClient(name = "product-service")` 都会自动触发 LoadBalancer——你不需要写任何 LoadBalancer 代码，只需确保依赖存在。

### 2.7 注册与发现

以 product-service 为例，执行 .\mvnw.cmd -pl product-service spring-boot:run。应用拉取 product-service.yaml、监听 8082 后注册到 Nacos；可在控制台“服务管理 → 服务列表”查看，也可直接访问 http://localhost:8082/api/products/1。

Gateway 的 uri: lb://user-service 及 order-service 的 OpenFeign 都以服务名调用。调用方的 LoadBalancer 从 Nacos 健康实例中选择一个，无需写死 localhost:8081 或容器 IP。

### 2.8 生产实践：namespace、group 与共享配置

学习阶段一个 default namespace + DEFAULT_GROUP 就够了。但真实项目里有 3 套环境、十几个服务、几十个配置文件，Nacos 提供了两层隔离机制和一种配置复用手段：

```
Nacos 配置组织方式（生产模式）
─────────────────────────────────────────────

namespace（命名空间）── 环境级隔离
  ├── dev          开发环境：所有服务、所有配置都在里面
  ├── uat          预发环境：与 dev 完全隔离，互不可见
  └── prod         生产环境：通常再加严格的权限控制

group（分组）── 业务域隔离（同一 namespace 内）
  ├── ORDER_GROUP      订单域服务的配置
  ├── PRODUCT_GROUP    商品域服务的配置
  └── SHARED_GROUP     跨域共享的公共配置

shared-configs（共享配置）── 配置复用
  多个服务都要用的配置（日志格式、超时、数据源连接池参数）
  抽成一个公共 dataId，各服务引用，改一处全生效
```

**为什么需要共享配置？** 假设 20 个服务都连同一个 MySQL 集群，连接池参数要调优。没有共享配置时要改 20 个 dataId；有了共享配置，只改 `shared-datasource.yaml` 一个文件，所有服务下次刷新即生效。

本地 `application.yml` 的对应写法（`spring.config.import` 支持导入多个配置，**后导入的优先级更高**，因此应用专属配置放最后）：

```yaml
spring:
  application: { name: order-service }
  config:
    import:
      - "optional:nacos:shared-common.yaml" # ① 公共配置（最先加载，优先级最低）
      - "optional:nacos:shared-datasource.yaml" # ② 数据源公共配置
      - "optional:nacos:${spring.application.name}.yaml" # ③ 应用专属（最后加载，优先级最高）
  cloud:
    nacos:
      discovery:
        server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
        namespace: ${NACOS_NAMESPACE:} # 环境隔离：dev/uat/prod 各一个 namespace
        group: ORDER_GROUP # 业务域分组
      config:
        server-addr: ${NACOS_SERVER_ADDR:localhost:8848}
        namespace: ${NACOS_NAMESPACE:}
        group: ORDER_GROUP
        file-extension: yaml
```

> **配置优先级口诀**：应用专属 > 共享配置；共享配置列表中，靠后声明的 > 靠前的。环境变量（如 `NACOS_NAMESPACE`）由部署平台注入，本地开发用默认值即可。
>
> **真实项目参考**：不少公司把 namespace 用于环境隔离、group 用于业务线分组、共享配置拆成「公共配置」和「数据源配置」两份，这套组合在实践中被验证过，可以直接借鉴。

### 2.9 本章回顾

- nacos-init 导入配置，Java 应用启动后才注册实例。
- 本地 application.yml 保存应用名、Nacos 地址与 spring.config.import。
- Nacos YAML 保存端口、数据源与 Gateway 路由。
- Discovery 解决“实例在哪里”；LoadBalancer 只由当前调用方模块使用。
- 生产环境用 namespace 隔离环境、group 分业务域、shared-configs 抽公共配置（§2.8）。

> **接下来**：§3 使用 order-service 中已存在的 OpenFeign 学习服务间调用。

---

## 3. 远程服务调用 — OpenFeign

### 3.1 OpenFeign 核心思想

OpenFeign 是**声明式 HTTP 客户端**——你定义接口，框架自动生成实现。

```
你写的接口                         Feign 生成的代理
───────────                       ────────────────
@FeignClient("product-service")   →  自动创建 Bean，注入 Spring 容器
public interface ProductClient {      方法实现 = HTTP 请求 + JSON 序列化
                                      + LoadBalancer 解析服务名
    @GetMapping("/products/{id}")
    ProductDTO getProduct(
        @PathVariable Long id);
}
```

> 这是和 `MongoRepository`（声明 `findByName` 自动生成查询）同一套哲学：**声明代替实现**。

### 3.2 三步接入 Feign

**第一步：添加依赖**（pom.xml）

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
```

**第二步：启动类加注解**

```java
@SpringBootApplication
@EnableFeignClients    // ← 扫描所有 @FeignClient 接口，生成代理 Bean
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
```

**第三步：定义 Feign 接口**（写在调用方项目中）

```java
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

@FeignClient(name = "product-service")  // name = Nacos 中的服务名
public interface ProductClient {

    @GetMapping("/products/{id}")
    ProductDTO getProduct(@PathVariable("id") Long id);

    @GetMapping("/products")
    List<ProductDTO> searchProducts(
            @RequestParam("name") String name,
            @RequestParam("minPrice") Double minPrice);

    @PutMapping("/products/{id}/stock")
    void deductStock(@PathVariable("id") Long id,
                     @RequestParam("quantity") Integer quantity);
}
```

使用起来就像调用本地方法：

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final ProductClient productClient;  // 注入 Feign 代理

    public OrderDTO createOrder(CreateOrderRequest request) {
        // 远程调用 → 一行代码，像调本地方法一样
        ProductDTO product = productClient.getProduct(request.getProductId());
        if (product.getStock() < request.getQuantity()) {
            throw new BusinessException("库存不足");
        }
        productClient.deductStock(product.getId(), request.getQuantity());
        // ...创建订单
    }
}
```

### 3.3 Feign 配置：超时、日志

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          default: # 所有 FeignClient 的默认值
            connect-timeout: 2000
            read-timeout: 5000
            logger-level: BASIC # NONE / BASIC / HEADERS / FULL
          product-service: # 对特定服务覆盖
            read-timeout: 3000

logging:
  level:
    com.example.order.feign.ProductClient: DEBUG
```

| 日志级别 | 输出内容                | 适用场景         |
| -------- | ----------------------- | ---------------- |
| NONE     | 不输出                  | 生产环境         |
| BASIC    | 方法、URL、状态码、耗时 | 日常开发         |
| HEADERS  | + 请求头、响应头        | 排查 Header 问题 |
| FULL     | + 请求体、响应体        | 排查数据问题     |

### 3.4 请求拦截器：Token 透传

服务间调用需要传递认证 Token。用一个 `RequestInterceptor` 自动透传：

```java
import feign.RequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                String token = attributes.getRequest().getHeader("Authorization");
                if (token != null) {
                    template.header("Authorization", token);
                }
            }
        };
    }
}
```

Token 自动从 order-service → product-service 透传，无需每个接口手动处理。

### 3.5 生产实践：Feign API 模块独立成 jar

§3.2 的写法是"调用方定义接口"。服务一多就会出现重复：

```
传统写法：调用方各自定义                API 模块写法：接口独立成 jar
─────────────────────                ──────────────────────────

order-service 里写                    product-api 模块（独立 maven 模块）：
  ProductClient 接口                    ProductClient 接口
  ProductDTO（照着提供方抄一遍）          ProductDTO
                                            │ 打成 jar 发布到私服
report-service 也要调商品接口？              ├── order-service 依赖 product-api
  再抄一遍接口和 DTO                        └── report-service 依赖 product-api

❌ DTO 到处复制，字段悄悄漂移          ✅ 单一来源：提供方改了签名，
❌ 提供方改了返回结构，调用方             所有调用方编译期立即报错
   运行期才发现
```

`@FeignClient(name = "product-service")` 的注解本身不变，变的只是接口和 DTO 的**存放位置**——从"调用方抄一份"变成"提供方发布一个 API jar，调用方直接依赖"。

**两种流派的取舍**：

| 流派            | 做法                   | 适合场景                                   |
| --------------- | ---------------------- | ------------------------------------------ |
| **复制派**      | 调用方自定义接口和 DTO | 强调服务边界、跨团队/跨语言、外部开放 API  |
| **共享 jar 派** | 提供方发布 API 模块    | 内部服务、同语言（Java）、迭代快的业务系统 |

国内业务型公司用共享 jar 流派很普遍（接口、DTO、枚举集中在一个 `-api` 模块里维护）。新手两种都要认识：面试问"Feign 接口放在哪"，答得出两种流派及其取舍，就是加分项。

### 3.6 本节回顾

```
RestClient                          OpenFeign
─────────                          ────────
手写 URI + 链式调用          →     接口 + 注解声明
手动类型转换                  →     自动根据泛型反序列化
参数手动构造                  →     @PathVariable/@RequestParam
不可复用                      →     接口可被多个 Service 注入
接口定义在调用方              →     也可抽成独立 API jar 共享（§3.5）
```

> **接下来**：OpenFeign 解决了服务间调用。但前端该调谁？§4 引入 Gateway——统一入口，前端只调一个地址。

---

## 4. API 网关 — Spring Cloud Gateway

### 4.0 问题：客户端该调哪个服务

拆分出 3 个服务后，前端面临一个现实问题：

```
❌ 没有网关时，前端需要知道每个服务的地址：

  GET  http://192.168.1.3:8081/users/1       ← 用户服务
  GET  http://192.168.1.4:8082/products/42    ← 商品服务
  POST http://192.168.1.5:8083/orders         ← 订单服务

问题：
  • 前端耦合了后端服务拓扑
  • CORS 要在 3 个服务上各配一套
  • 认证要在 3 个服务上各实现一次
```

Gateway 的答案是：**前端只调一个入口，其余由网关转发**。

### 4.1 Gateway 三大核心概念

```
┌─────────────────────────────────────────────────────────┐
│                Spring Cloud Gateway                      │
│                                                         │
│  Route（路由）：这个请求该转发给谁？                      │
│    /api/users/** → user-service                         │
│    /api/products/** → product-service                    │
│    /api/orders/** → order-service                        │
│                                                         │
│  Predicate（断言）：这个请求匹配这条路由吗？              │
│    Path=/api/orders/** → 匹配                           │
│    Header X-API-Version: v2 → 匹配                      │
│                                                         │
│  Filter（过滤器）：请求经过时做什么？                     │
│    添加请求头、去掉路径前缀、限流、认证                   │
└─────────────────────────────────────────────────────────┘
```

> **前端视角**：Gateway 就是后端的 Nginx 反向代理。你只需要知道 `http://localhost:8080`，Gateway 负责把 `/api/orders` 转发给 `order-service:8083`。

### 4.2 Gateway 项目搭建

Gateway 本身也是一个 Spring Boot 应用。

**依赖**（pom.xml）：

```xml
<!-- ⚠️ Gateway 基于 WebFlux，不要引入 spring-boot-starter-webmvc！ -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway</artifactId>
</dependency>
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
<!-- lb:// 路由必须有 LoadBalancer；Gateway starter 不应假定会自动带入它。 -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
```

**路由配置**（application.yml）：

```yaml
server:
  port: 8080 # 网关统一入口

spring:
  application:
    name: gateway
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
    gateway:
      routes:
        - id: user-service-route
          uri: lb://user-service # lb:// = 从 Nacos 获取实例 + 负载均衡
          predicates:
            - Path=/api/users/**
          filters:
            - StripPrefix=1 # 去掉 /api → 下游收到 /users/**

        - id: product-service-route
          uri: lb://product-service
          predicates:
            - Path=/api/products/**
          filters:
            - StripPrefix=1

        - id: order-service-route
          uri: lb://order-service
          predicates:
            - Path=/api/orders/**
          filters:
            - StripPrefix=1
```

路由生效后的请求流转：

```
前端 GET /api/products/42
         │
         ▼
Gateway
    ├── 匹配 Path=/api/products/** → 命中路由
    ├── StripPrefix=1：/api/products/42 → /products/42
    └── lb://product-service → Nacos 查实例 → 转发
         │
         ▼
product-service 收到 GET /products/42
```

### 4.3 自定义 GlobalFilter：拦截模式

GlobalFilter 是 Gateway 的拦截器——每个请求都会经过。它有三个核心能力：**白名单放行**、**修改请求**、**拒绝请求**。完整的 JWT 鉴权实现见 §7，这里只看 Filter 骨架：

```java
@Component
public class AuthFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // ① 白名单：登录接口直接放行，不做任何拦截
        if (path.startsWith("/api/auth/")) {
            return chain.filter(exchange);
        }

        // ② 拒绝：缺少必要信息时，直接返回 401
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (token == null) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        // ③ 修改请求：验证通过后，往 Header 中注入信息，传给下游
        //    （完整实现：解析 JWT → 提取 userId → 写入 X-User-Id Header）
        exchange = exchange.mutate()
                .request(r -> r.header("X-User-Id", "..."))
                .build();

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1;  // 数字越小越先执行
    }
}
```

> Gateway 基于 WebFlux，Filter 接口是 `GlobalFilter`（不是 MVC 的 `javax.servlet.Filter`）。请求对象是 `ServerWebExchange`，响应是 `Mono<Void>`。

### 4.4 CORS 统一配置

Gateway 中配一次 CORS，下游服务全免：

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOrigin("http://localhost:5173");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
```

> **注意**：Gateway 用 `CorsWebFilter`（WebFlux），不是 MVC 的 `WebMvcConfigurer`。

### 4.5 生产实践：网关超时配置

网关是所有请求的入口，它自身的 HTTP 客户端超时配置经常被忽略——下游服务卡死时，网关线程会一直挂起等待，最终拖垮整个入口。生产环境务必显式设置：

```yaml
spring:
  cloud:
    gateway:
      httpclient:
        connect-timeout: 3000 # 与下游建立连接的最长等待（毫秒）
        response-timeout: 10000 # 等待下游返回响应的最长时间（毫秒）
```

```
不配超时的后果                          配置后的行为
─────────────                          ────────────
下游 product-service 卡死（不响应）  →   10 秒后网关主动断开，返回 504
网关线程一个个被挂起                 →   线程及时释放，入口保持可用
所有请求堆积，网关先崩                →   故障被隔离在出问题的那条链路
```

> **经验值**：`connect-timeout` 内网环境 1~3 秒足够；`response-timeout` 设为你能容忍的最长接口耗时（5~15 秒），比下游最慢接口略长即可。它与 Feign 的 `read-timeout`（§3.3）、Sentinel 的慢调用熔断（§5.4）是同一思想在不同层的体现：**任何等待都必须有上限**。

### 4.6 本节回顾

```
没有网关                      有了网关
────────                      ────────
前端知道所有服务地址     →    前端只知道 http://localhost:8080
每个服务配 CORS          →    网关配一次
每个服务做认证           →    网关统一认证
服务拓扑暴露             →    内网服务不需公网 IP
网关等待无上限           →    httpclient 超时兜底（§4.5）
```

---

## 5. 服务容错 — Sentinel

### 5.0 问题：雪崩效应

微服务架构中，一个服务依赖另一个服务。当被依赖的服务出问题时，调用方如果持续等待，最终会导致整个调用链崩溃。这就是**雪崩效应**：

```
正常状态                          雪崩状态
────────                          ──────
                                  ③ order-service 线程池也被占满
    order-service                  → 整个系统不可用
    │ 线程池: 10                   ▲
    ├──→ product-service           │
    │    响应: 50ms                ② order-service 的 10 个线程
    │                              │  全在等 product-service 超时
    └──→ user-service              │  新的下单请求直接拒绝
         响应: 30ms                ▲
                                   │
                                  ① product-service 挂了
                                  │  但 order-service 不知道
                                  │  仍在发请求、等待 5 秒超时
                                  │  每次请求占用一个线程
```

Sentinel 从三个层面防止雪崩：**流量控制**（太多请求？拦住一部分）、**熔断降级**（被调用方太慢？快速失败）、**系统保护**（系统负载过高？整体限流）。

### 5.1 Sentinel 是什么

Sentinel 是阿里巴巴开源的流量治理组件，以流量为切入点，从流量控制、熔断降级、系统负载保护等多个维度保护服务的稳定性。

```
                   ┌─────────────────┐
                   │    Sentinel     │
                   │    Dashboard    │
                   │    :8090        │
                   └────────┬────────┘
                            │ 规则下发 + 实时监控
            ┌───────────────┼───────────────┐
            │               │               │
     ┌──────▼──────┐ ┌──────▼──────┐ ┌──────▼──────┐
     │user-service │ │product-svc  │ │order-service│
     │ Sentinel    │ │ Sentinel    │ │ Sentinel    │
     │ 客户端嵌入   │ │ 客户端嵌入   │ │ 客户端嵌入   │
     └─────────────┘ └─────────────┘ └─────────────┘
```

> **前端类比**：Sentinel 的熔断降级 ≈ React 的 `<ErrorBoundary>` + `<Suspense fallback={Loading}>`。组件挂了 → 显示 fallback。服务挂了 → 返回降级数据，不阻塞调用方。

### 5.2 三步接入 Sentinel

**第一步：添加依赖**

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
</dependency>
```

**第二步：配置**（application.yml）

```yaml
spring:
  cloud:
    sentinel:
      transport:
        dashboard: localhost:8090 # Sentinel Dashboard 地址
        port: 8719 # 与控制台通信的本地端口
      eager: true # 启动时立即注册到 Dashboard
```

**第三步：启动 Sentinel Dashboard**（Docker）

```bash
docker run -d --name sentinel -p 8090:8080 \
  bladex/sentinel-dashboard:1.8.9
```

访问 `http://localhost:8090`，默认用户名密码均为 `sentinel`。

> **注意**：Sentinel 采用懒加载——服务第一次被调用后才会出现在 Dashboard 中。等有了第一次请求再去 Dashboard 查看。

### 5.3 流量控制：QPS 限流

限制某个接口每秒最多处理多少请求。超过的直接拒绝。

在 Sentinel Dashboard 中配置：

```
资源名:   GET:/products/{id}
阈值类型:  QPS
阈值:     10
流控效果:  快速失败

含义：GET /products/{id} 每秒最多 10 个请求。第 11 个直接返回 429。
```

也可以用代码定义（无需 Dashboard）：

```java
import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;

@RestController
public class ProductController {

    @GetMapping("/products/{id}")
    @SentinelResource(
        value = "getProduct",
        blockHandler = "handleBlock"   // 被限流时执行的方法
    )
    public ProductDTO getProduct(@PathVariable Long id) {
        return productService.findById(id);
    }

    // blockHandler 方法：签名必须和原方法一致，多一个 BlockException 参数
    public ProductDTO handleBlock(Long id, BlockException e) {
        // 返回降级数据
        ProductDTO fallback = new ProductDTO();
        fallback.setName("系统繁忙，请稍后再试");
        return fallback;
    }
}
```

### 5.4 熔断降级：慢调用自动熔断

> **"熔断"是什么？** 熔断（Circuit Breaker）借鉴了电路保险丝的原理：当检测到下游服务异常（响应慢、报错多），自动**切断**对该服务的调用，后续请求直接走降级逻辑（fallback），不再等待超时。熔断不是永久的——经过一段冷却时间后，会放行少量请求"试探"下游是否恢复，成功则关闭熔断，失败则继续断开。三态转换如下：

```
     正常（Closed）                      熔断（Open）                       半开（Half-Open）
  ┌─────────────────┐            ┌─────────────────────┐            ┌─────────────────────┐
  │ 请求正常通过下游   │  ──慢调用>50%──▶  │ 不调用下游，直接 fallback │  ──冷却结束──▶  │ 放行 1 个请求试探      │
  │                  │            │                     │            │  成功 → 回到 Closed   │
  └─────────────────┘            └─────────────────────┘            │  失败 → 回到 Open     │
                                                                    └─────────────────────┘
```

当被调用方响应变慢时，Sentinel 自动进入熔断——直接走降级逻辑，不给下游压力：

在 Sentinel Dashboard 中配置熔断规则：

```
资源名:         GET:/products/{id}
熔断策略:        慢调用比例
最大 RT:         200ms         ← 响应超过 200ms 算"慢调用"
比例阈值:        0.5           ← 50% 的请求是慢调用就触发熔断
熔断时长:        10s           ← 熔断 10 秒后尝试恢复
最小请求数:      5             ← 至少 5 个请求后才开始判断
```

```
时间线：
─────────────────────────────────────────────────────►
  正常             慢调用 > 50%         熔断中            半开（试探）
  │               │                  │                 │
  │               ▼                  ▼                 ▼
  │         触发熔断             所有请求直接         放行一个请求
  │         开始快速失败         走 fallback          如果成功 → 恢复
  │         （不调用下游）                           如果失败 → 继续熔断
```

代码中定义降级逻辑（与 Feign 整合时最常用）：

```java
import org.springframework.cloud.openfeign.FallbackFactory;

// Feign 接口中指定 fallback 工厂
@FeignClient(
    name = "product-service",
    fallbackFactory = ProductClientFallbackFactory.class
)
public interface ProductClient {
    @GetMapping("/products/{id}")
    ProductDTO getProduct(@PathVariable("id") Long id);
}

// Fallback 工厂：获取异常信息，返回降级数据
@Component
public class ProductClientFallbackFactory
        implements FallbackFactory<ProductClient> {

    @Override
    public ProductClient create(Throwable cause) {
        log.error("product-service 调用失败，触发降级", cause);
        return new ProductClient() {
            @Override
            public ProductDTO getProduct(Long id) {
                ProductDTO fallback = new ProductDTO();
                fallback.setName("商品服务暂不可用");
                return fallback;
            }
        };
    }
}
```

### 5.5 Sentinel 规则类型速览

| 规则类型         | 解决什么问题 | 关键参数                       |
| ---------------- | ------------ | ------------------------------ |
| **流量控制**     | 请求太多     | QPS / 并发线程数阈值           |
| **熔断降级**     | 下游太慢     | 慢调用比例 / 异常比例 / 异常数 |
| **热点参数限流** | 某个商品被刷 | 针对特定参数值限流             |
| **系统规则**     | 整体负载高   | CPU / Load / RT / 入口 QPS     |
| **授权规则**     | 黑白名单     | 来源应用 / IP                  |

### 5.6 生产实践：规则持久化到 Nacos 与网关流控

学习阶段在 Dashboard 上点几下就能配规则，但有两个生产级问题必须解决。

**问题一：Dashboard 配的规则是临时的。** 它只存在 Dashboard 内存里，服务重启或 Dashboard 重启后规则全部丢失。生产环境要求规则**持久化、可版本化管理**——正好，我们已经有 Nacos。引入 `sentinel-datasource-nacos` 后，规则以 JSON 形式存进 Nacos 配置中心：Sentinel 客户端启动时拉取，Nacos 中修改后实时推送到所有服务：

```
                ┌──────────────┐
                │    Nacos     │
                │ 配置中心      │
                │              │
                │ sentinel-flow-rules (JSON)
                └──────┬───────┘
                       │ ① 启动拉取 ② 修改后实时推送
            ┌──────────┼──────────┐
            ▼          ▼          ▼
      user-service  product-svc  order-service
      （Sentinel 客户端内嵌，规则来自 Nacos）
```

依赖与配置：

```xml
<dependency>
    <groupId>com.alibaba.csp</groupId>
    <artifactId>sentinel-datasource-nacos</artifactId>
</dependency>
```

```yaml
spring:
  cloud:
    sentinel:
      datasource:
        flow: # 规则类型标识（流控规则）
          nacos:
            server-addr: localhost:8848
            dataId: order-service-flow-rules # 存规则的 dataId
            groupId: SENTINEL_GROUP
            rule-type: flow # flow / degrade / param-flow / system / authority
```

Nacos 中 `order-service-flow-rules` 的内容就是规则 JSON：

```json
[
  {
    "resource": "GET:/products/{id}",
    "limitApp": "default",
    "grade": 1,
    "count": 10,
    "strategy": 0,
    "controlBehavior": 0,
    "clusterMode": false
  }
]
```

> **分工建议**：Nacos 持久化负责"规则不丢 + 集中管理"，Dashboard 负责"实时监控 + 临时调参"。注意 Dashboard 上修改规则**不会回写 Nacos**，生产环境应以 Nacos 中的规则为准。

**问题二：入口流量在网关层就要拦住。** 前面讲的限流熔断都嵌在业务服务里，但秒杀、爬虫等流量冲击的是网关本身。Sentinel 提供网关专用适配器，直接在 Gateway 上配置流控规则（按路由 ID 或自定义 API 分组）：

```xml
<!-- Gateway 模块中使用 -->
<dependency>
    <groupId>com.alibaba.csp</groupId>
    <artifactId>sentinel-spring-cloud-gateway-adapter</artifactId>
</dependency>
```

```
分层防御全景：
                                                         资源名示例
第一层：网关限流（入口 QPS）        Gateway ────────────  order-service-route
第二层：服务内接口限流             product-service ────  GET:/products/{id}
第三层：下游调用熔断               order-service ──────  ProductClient#getProduct

流量从外到内逐层收缩，每层都有独立的保护规则
```

### 5.7 本节回顾

```
没有 Sentinel                    有了 Sentinel
─────────────                    ─────────────
一个服务慢，拖垮全部        →    熔断：快速失败，不等待
流量突增打挂服务            →    限流：多余的请求直接拒绝
不知道哪个服务出了问题       →    Dashboard 实时监控 QPS / RT
服务挂了返回 500             →    降级：返回兜底数据，体验不中断
重启后规则全丢               →    规则持久化到 Nacos（§5.6）
入口流量无防护               →    网关适配器在边界限流（§5.6）
```

> **接下来**：Sentinel 防止了级联故障。但请求跨了 5 个服务，到底慢在哪一层？§6 引入链路追踪回答这个问题。

---

## 6. 分布式链路追踪 — SkyWalking

### 6.0 问题：跨服务请求像黑盒

用户反馈"下单很慢，经常要 5 秒"。你打开日志，发现 3 个服务各有各的日志文件——你无法一眼看出是哪个环节慢了。

```
order-service 日志：          product-service 日志：       user-service 日志：
createOrder start             getProduct start            getUser start
createOrder end (耗时 4.7s)   getProduct end (80ms)       getUser end (60ms)

问题：order-service 总耗时 4.7s，但调用的两个下游都很快（80ms + 60ms）。
慢在哪？可能卡在 order-service 自身的业务逻辑，也可能有未记录的第三方调用。
```

链路追踪的答案是：**给每个请求一个全局唯一 ID，在所有服务间传递，串联起完整的调用链**。

### 6.1 核心概念：Trace ID 与 Span ID

```
一次下单请求的完整链路：

Trace ID: abc123（全局唯一，贯穿整个调用链）
│
├── Span A: Gateway 收到请求                        [Span ID: a1, Parent: null]
│   │
│   └── Span B: order-service 处理请求               [Span ID: b2, Parent: a1]
│       │
│       ├── Span C: Feign 调用 product-service        [Span ID: c3, Parent: b2]
│       │   └── Span D: product-service 查数据库      [Span ID: d4, Parent: c3]
│       │
│       └── Span E: Feign 调用 user-service           [Span ID: e5, Parent: b2]
│           └── Span F: user-service 查数据库         [Span ID: f6, Parent: e5]
│
└── Span G: Gateway 返回响应                         [Span ID: g7, Parent: a1]

Trace ID = 一次请求的"身份证号"
Span ID  = 调用链上的一步操作
Parent Span ID = 上一步的 Span ID（形成父子关系）
```

> **前端类比**：Trace ID 就像是 Chrome DevTools Network 面板中一次页面加载的"请求组"。你看到 `/api/orders` 花了 2.3s，点开看到它内部发起了 `/api/products`（2.1s）和 `/api/users`（0.2s）。SkyWalking 就是跨服务的 Network 面板——同一 Trace ID 把所有相关请求串在一起。

### 6.2 SkyWalking 与 Zipkin 方案的本质差异

链路追踪有两类主流实现，理解它们的差异比记住操作步骤更重要：

```
方案一：Zipkin（SDK 上报）                方案二：SkyWalking（agent 探针）
─────────────────────                    ────────────────────────────

pom.xml 加 2 个依赖                       pom.xml 什么都不加（或只加日志工具包）
application.yml 配上报地址                 启动命令加 -javaagent:skywalking-agent.jar
代码耦合 Micrometer API                   代码零侵入，对业务完全透明
只覆盖 Spring 生态的组件                   覆盖 JVM 层面的一切：
                                        HTTP、JDBC、Redis、MQ、Feign、Dubbo...

适合你只想给 Spring 应用加追踪            适合你有几十个服务、多种中间件、
                                        还想看 JVM 指标和服务拓扑
```

SkyWalking 的 agent 基于 Java 字节码增强技术：JVM 启动时，agent 在类加载过程中给 Tomcat、Feign、MySQL 驱动等框架的关键方法"织入"采集逻辑——你的业务代码从头到尾不知道它的存在。这也是它在企业级落地的主流形态：**运维统一挂载 agent，开发完全无感**。

```
SkyWalking 整体架构：
                                              ┌─────────────────┐
                                              │  SkyWalking UI  │
                                              │     :8088       │
                                              └────────▲────────┘
                                                       │ 查询（HTTP :12800）
                                              ┌────────┴────────┐
                                              │  OAP Server     │
                                              │  接收/分析/存储  │
                                              └────────▲────────┘
                                                       │ 上报（gRPC :11800）
              ┌───────────────┬───────────────────────┼────────────┐
              │               │                       │            │
        ┌─────┴─────┐   ┌─────┴─────┐          ┌──────┴────┐ ┌─────┴─────┐
        │ Gateway   │   │order-svc  │          │product-svc│ │ user-svc  │
        │ + agent   │   │ + agent   │          │ + agent   │ │ + agent   │
        └───────────┘   └───────────┘          └───────────┘ └───────────┘
        每个服务启动时挂载同一个 agent，Trace 数据自动上报
```

### 6.3 三步接入 SkyWalking

**第一步：启动 OAP Server 与 UI**（Docker Compose 片段，加入 §1.4 的 compose 或单独保存）

```yaml
services:
  skywalking-oap:
    image: apache/skywalking-oap-server:9.4.0
    container_name: skywalking-oap
    ports:
      - "11800:11800" # agent 上报数据的 gRPC 端口
      - "12800:12800" # UI 查询数据的 HTTP 端口
    environment:
      SW_STORAGE: elasticsearch # 学习时也可省略，默认 H2 内存存储
      SW_HEALTH_CHECKER: default
      JAVA_OPTS: "-Xms512m -Xmx1g"

  skywalking-ui:
    image: apache/skywalking-ui:9.4.0
    container_name: skywalking-ui
    depends_on:
      - skywalking-oap
    ports:
      - "8088:8080" # UI 容器内固定 8080，映射到宿主机 8088 避免与 Gateway 冲突
    environment:
      SW_OAP_ADDRESS: http://skywalking-oap:12800
```

> 学习阶段去掉 `SW_STORAGE` 一行即使用内置 H2（重启后数据丢失，够用来练习）。生产环境通常配 Elasticsearch 存储。

**第二步：给服务挂载 agent**

下载 SkyWalking agent（[官网发行包](https://skywalking.apache.org/downloads/)中的 `apache-skywalking-java-agent-x.y.z.tgz`），解压后在启动命令中挂载：

```bash
java -javaagent:/opt/skywalking-agent/skywalking-agent.jar \
     -Dskywalking.agent.service_name=order-service \
     -Dskywalking.collector.backend_service=localhost:11800 \
     -jar order-service.jar
```

> **本地开发更便捷的方式**：在 IDEA 的 Run Configuration → VM options 里加上面三个参数即可。`service_name` 决定该服务在 UI 拓扑图中的名字；也可以编辑 agent 目录下 `config/agent.config` 统一配置。

**第三步：什么都不用写。** 启动服务，访问几次接口，打开 `http://localhost:8088`——Trace、拓扑图、慢端点全部自动出现。HTTP 入口、Feign 调用、MySQL 查询的 Span 都由 agent 自动采集。

### 6.4 SkyWalking UI 解读

```
┌─────────────────────────────────────────────────────────┐
│ SkyWalking UI (:8088)                                    │
│                                                         │
│ ① General Service 面板：                                 │
│   每个服务的吞吐量(CPM)、延迟、可用率、JVM 监控           │
│                                                         │
│ ② Topology（拓扑图）：                                   │
│   自动画出服务间调用关系 + 每条边上的延迟和吞吐           │
│   Gateway → order-service → product-service             │
│                           └→ user-service               │
│                           └→ MySQL                      │
│                                                         │
│ ③ Trace（追踪）：                                        │
│   按条件查询调用链，展开看每个 Span 的耗时                │
│   POST /orders            4.723s   ← 点进来找最慢的 Span │
│   ├── GET /products/42      82ms                        │
│   │   └── mysql: SELECT     45ms                        │
│   └── GET /users/1          61ms                        │
│       └── mysql: SELECT     38ms                        │
└─────────────────────────────────────────────────────────┘
```

> **排查技巧**：先在 Trace 页按耗时倒序找到慢请求，点开展开 Span 树找最慢的环节；如果某类 Span 普遍慢（比如所有 MySQL 查询），去 Topology 看这条边是不是整体延迟升高——那通常是数据库或网络问题，而不是单次请求的问题。

### 6.5 日志关联：让每行日志带上 Trace ID

UI 里看调用链很爽，但日常排障的第一现场往往是**日志文件**。理想状态：日志里每一行都打印当前请求的 Trace ID，拿到用户报障 → 日志里搜出 Trace ID → 粘贴到 SkyWalking UI → 完整链路秒开。

agent 已经把 Trace ID 放进了 MDC（日志上下文），你只需要一个 toolkit 依赖让 logback 认识它：

```xml
<dependency>
    <groupId>org.apache.skywalking</groupId>
    <artifactId>apm-toolkit-logback-1.x</artifactId>
    <version>9.4.0</version>
</dependency>
```

在 `logback-spring.xml` 中把 pattern 的 layout 换成 SkyWalking 提供的实现，并加上 `%tid` 占位符：

```xml
<appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
        <layout class="org.apache.skywalking.apm.toolkit.log.logback.v1.x.TraceIdPatternLogbackLayout">
            <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} [%tid] - %msg%n</pattern>
        </layout>
    </encoder>
</appender>
```

效果对比：

```
接入前：14:23:01.123 [http-nio-8083-exec-1] INFO  OrderService - 创建订单成功, orderId=9527
接入后：14:23:01.123 [http-nio-8083-exec-1] INFO  OrderService [TID: abc123...] - 创建订单成功, orderId=9527
                                                                  ↑ 复制它去 UI 查询，一次请求的所有服务日志全部串起来
```

> **进阶（了解即可）**：SkyWalking 还提供 `GRPCLogClientAppender`，把日志直接上报到 OAP 与 Trace 关联存储，在 UI 里点开 Trace 就能看到该链路的所有日志。多数团队的做法更简单：日志照旧落盘/收集到 ELK，靠 `%tid` 做人工关联——成本最低，收益已经很大。

### 6.6 本节回顾

```
没有链路追踪                          有了 SkyWalking
────────────                          ──────────────
找不到慢在哪一层              →       Trace 页一眼看出瓶颈 Span
多个服务的日志无法串联        →       同一 Trace ID 贯穿全链路 + 日志带 %tid
不知道服务间的调用关系        →       Topology 自动画出拓扑图
加监控要改代码                →       agent 探针零侵入，运维挂载即可
```

> **接下来**：可观测性就位了。但整个系统的安全怎么做？认证该放在哪里？§7 介绍微服务中的安全方案。

---

## 7. 安全 — 微服务中的认证授权

### 7.0 问题：认证该放在哪里

在单体中，认证很简单——用户登录后，Spring Security 在同一个 JVM 中管理 SecurityContext。但微服务中有两个选择：

```
方案 A：各服务各自认证               方案 B：网关统一认证
─────────────────                   ────────────────
                                    前端 → Gateway
 前端 → Gateway → 各服务                  │ JWT 验证
              │                          │
 user-service: 验证 JWT                  ├──→ user-service
 product-service: 验证 JWT               │    （只验 Header）
 order-service: 验证 JWT                 ├──→ product-service
                                         │    （只验 Header）
 ❌ JWT 密钥要在 4 个地方维护              └──→ order-service
 ❌ 验签逻辑重复 N 次                          （只验 Header）

                                        ✅ 密钥只存在网关
                                        ✅ 验签只执行一次
```

**推荐方案 B**：网关统一认证 + 下游服务信任 Header。这与现有的 `spring-security-guide.md` 是互补关系——单体 Security 指南讲 JWT 认证本身，本节点讲"在微服务架构中把认证放在哪里"。

### 7.1 网关统一认证：完整实现

网关通过 GlobalFilter 拦截所有请求，完成 JWT 验证后将用户身份注入 Header 传给下游：

```
用户请求（Authorization: Bearer eyJ...）
    │
    ▼
Gateway AuthFilter
    │
    ├──① 从 Header 取出 JWT
    ├──② 验证签名 + 有效期
    ├──③ 解析出 userId
    └──④ 写入 X-User-Id Header → 转发给下游
         │
         ▼
order-service
    │
    └── 从 X-User-Id 获取当前用户（信任网关已验证）
```

完整实现代码（Gateway 模块）：

```java
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class AuthFilter implements GlobalFilter, Ordered {

    // ⚠️ 生产环境应从配置中心读取，不可硬编码
    private static final String JWT_SECRET = "your-256-bit-secret-key-min-32-chars!!";
    private static final SecretKey KEY =
            Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 登录接口放行
        if (path.startsWith("/api/auth/")) {
            return chain.filter(exchange);
        }

        String token = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        try {
            // ① 验证 JWT：验签 + 有效期 + 提取 Claims
            Claims claims = Jwts.parser()
                    .verifyWith(KEY)
                    .build()
                    .parseSignedClaims(token.substring(7)) // 去掉 "Bearer " 前缀
                    .getPayload();

            // ② 从 Claims 中提取用户 ID
            String userId = claims.getSubject();

            // ③ exchange.mutate() 将 userId 写入请求头，传给下游服务
            exchange = exchange.mutate()
                    .request(r -> r.header("X-User-Id", userId))
                    .build();

        } catch (Exception e) {
            // JWT 过期、签名无效、格式错误 → 一律 401
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1;  // 数字越小越先执行
    }
}
```

### 7.2 下游服务如何获取当前用户

下游服务不需要再次验签 JWT，直接从网关传入的 Header 中获取用户信息：

```java
// 在 order-service 中获取当前用户
@RestController
public class OrderController {

    @GetMapping("/orders")
    public List<OrderDTO> listOrders(
            @RequestHeader("X-User-Id") Long userId) {
        // 信任网关已认证 → 直接使用 userId
        return orderService.findByUserId(userId);
    }
}
```

### 7.3 Feign 调用时 Token 透传

当 order-service 通过 Feign 调用 product-service 时，Token 需要继续传递（回想 §3.4 的 RequestInterceptor）：

```java
@Configuration
public class FeignConfig {
    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                // 透传网关注入的 Header
                String userId = attributes.getRequest().getHeader("X-User-Id");
                if (userId != null) {
                    template.header("X-User-Id", userId);
                }
                // 同时透传原始 JWT（如果下游需要）
                String token = attributes.getRequest().getHeader("Authorization");
                if (token != null) {
                    template.header("Authorization", token);
                }
            }
        };
    }
}
```

### 7.4 下游服务的 SecurityContext 适配

网关验证了 JWT，但下游服务怎么让 Spring Security 认识这个用户？答案是写一个 Filter 从 `X-User-Id` 构建 `SecurityContext`。

**信任链：网关验证 + 下游信任 = 完整闭环**

下游之所以能"跳过验证"，是因为网关在 §7.1 的 `AuthFilter` 中已经完成了全部验证工作。两端代码的协作关系：

```
§7.1 网关 AuthFilter                     §7.4 下游 GatewayAuthFilter
────────────────────                    ───────────────────────────
① Jwts.parser().verifyWith(KEY)         ① request.getHeader("X-User-Id")
       .parseSignedClaims(token)            ← 读网关注入的 Header
   → 验签名：token 未被篡改 ✓
   → 验有效期：token 未过期 ✓

② String userId = claims.getSubject()   ② new UsernamePasswordAuthenticationToken(
   → 从 JWT Claims 提取身份                     userId, null, authorities)

③ exchange.mutate()                     ③ SecurityContextHolder.setAuthentication(auth)
   .request(r - r.header(                    → 后续 @PreAuthorize 正常工作
       "X-User-Id", userId))            ← 不再验证：能到达这里的 X-User-Id
   → 注入 Header                             必定是网关 ①+②+③ 写入的
```

存入 `SecurityContextHolder` 后，Spring Security 的整个框架就"看见"这个用户了：

```
SecurityContextHolder（ThreadLocal，每个请求线程独立一份）
        │
        └── SecurityContext
                │
                └── Authentication = auth  ← setAuthentication() 塞进去
                        │
                        ├── auth.getPrincipal()      → "123"         ← @PreAuthorize 从这里取
                        ├── auth.getAuthorities()    → [ROLE_USER]   ← hasRole("USER") 从这里取
                        └── auth.isAuthenticated()   → true          ← 是否放行

```

```java
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class GatewayAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String userId = request.getHeader("X-User-Id");
        if (userId != null) {
            // 信任网关已认证 → 直接构建带 userId 的已认证 token
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(
                        userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }

        filterChain.doFilter(request, response);
    }
}
```

注册到 SecurityFilterChain 中（参考 `spring-security-guide.md` §2.1）：

```java
http.addFilterBefore(
    new GatewayAuthFilter(), UsernamePasswordAuthenticationFilter.class);
```

这样下游 Controller 中 `@PreAuthorize`、`SecurityContextHolder.getContext().getAuthentication()` 都能正常工作。

> **那 `UserDetailsService` 去哪了？** 对比两种模式的认证流程：
>
> ```
> ┌── 标准认证流程（如登录接口）──────────────────────────────────┐
> │                                                              │
> │  请求 → AuthenticationManager                                │
> │       → UserDetailsService.loadUserByUsername("zhangsan")    │
> │       → 从数据库查出用户 → 比对密码                           │
> │       → 构建带 UserDetails 的 token → 存入 SecurityContext    │
> │                                                              │
> │  UserDetailsService 的作用：查数据库 + 验证密码               │
> └──────────────────────────────────────────────────────────────┘
>
> ┌── 网关信任模式（微服务内部调用）───────────────────────────────┐
> │                                                              │
> │  请求（Header: X-User-Id=123）                                │
> │       → GatewayAuthFilter 读取 X-User-Id                      │
> │       → 直接构建带 userId 的已认证 token                       │
> │       → 存入 SecurityContext（跳过数据库查询）                  │
> │                                                              │
> │  UserDetailsService 不需要：网关已验过 JWT，下游只需信任       │
> └──────────────────────────────────────────────────────────────┘
> ```

本指南不重复 `spring-security-guide.md` 的内容。阅读顺序建议：

```
① spring-security-guide.md（单体 Security）
   掌握：JWT 生成/验证、SecurityFilterChain、@PreAuthorize、SecurityContextHolder

② 本节点 §7（微服务 Security）
   掌握：认证放在网关、Token 透传、与单体 Security 的差异

③ 实战：网关 + Security 整合
   网关 AuthFilter 验签 JWT → 写入 X-User-Id
   下游服务用 Spring Security 读取 X-User-Id → 设置 SecurityContext
   → Controller 中 @PreAuthorize 正常工作
```

### 7.5 本节回顾

```
单体安全                             微服务安全
────────                             ────────
Spring Security 直接验 JWT      →    网关统一验签
SecurityContext 在本地管理       →    X-User-Id Header 跨服务传递
每个方法可 @PreAuthorize       →    下游仍可用（需适配 SecurityContext）
```

> **接下来**：前 7 章覆盖的都是同步通信（请求-响应）。但"下单后发短信"这类事，同步等待既慢又脆弱——§8 引入 RabbitMQ，用异步消息解耦。

---

## 8. 消息队列 — RabbitMQ

### 8.0 问题：同步调用的三个痛点

下单成功后要发短信通知用户。用 Feign 同步调用通知服务会怎样？

```
同步调用（OpenFeign）                  异步消息（RabbitMQ）
─────────────────                      ──────────────────
订单服务调用通知服务 → 阻塞等待响应      订单服务发消息 → 立刻返回，处理下一个请求
                                        通知服务从队列取消息 → 异步发短信

❌ 下单接口耗时 = 下单 + 发短信          ✅ 解耦：订单服务根本不知道通知服务的存在
❌ 通知服务挂了 → 下单也失败             ✅ 削峰：1000 个订单瞬间涌入，消息在队列里排队慢慢发
❌ 想加个"发邮件"功能 → 改订单代码      ✅ 可靠：消费者失败可重试，消息不丢
```

**判断标准一句话**：调用方需要立刻拿到结果才能继续 → 用 Feign 同步调用；"做了就行，什么时候做完我不关心" → 用消息队列异步。典型异步场景：发短信/推送通知、生成报表、同步数据到搜索引擎、记录行为日志。

### 8.1 核心概念：Exchange、Queue、Binding

RabbitMQ 与直觉相反的一点：**生产者不直接发消息到队列，而是发到交换机（Exchange），由交换机按规则路由到队列（Queue）**。

```
生产者                    RabbitMQ                          消费者
                ┌──────────────────────────────────┐
 订单服务  ───▶ │  Exchange          Queue          │ ───▶ 通知服务（短信）
 "订单已创建"    │  order.exchange ──▶ order.notify  │
                │     │              (订单通知队列)  │ ───▶ 通知服务（App 推送）
                │     │ Binding ───▶ order.notify2  │
                │     │ (routing key)               │
                └──────────────────────────────────┘

Exchange：接收消息，按类型和 routing key 决定发给哪些队列
Queue：存消息的队列，消费者从这里取
Binding：Exchange 和 Queue 之间的绑定关系（含 routing key 匹配规则）
```

Exchange 的三种常用类型：

| 类型   | 路由规则                                | 类比         | 典型场景             |
| ------ | --------------------------------------- | ------------ | -------------------- |
| direct | routing key 精确匹配                    | 精确快递地址 | 订单通知（指定队列） |
| fanout | 不管 key，广播到所有绑定队列            | 群里@所有人  | 缓存失效广播         |
| topic  | key 按通配符匹配（`order.*`、`*.paid`） | 按标签订阅   | 多级事件分类         |

> **为什么多一层 Exchange？** decoupling（解耦）again。生产者只说"这是一条 order.created 消息"，不关心谁消费；之后新增一个"订单统计"消费者，只需新建队列并绑定到同一个 Exchange——生产者零改动。如果生产者直连队列，每加一个消费者都要改生产者代码。

### 8.2 三步接入 RabbitMQ

**第一步：启动 RabbitMQ**（Docker）

```bash
docker run -d --name rabbitmq \
  -p 5672:5672 -p 15672:15672 \
  rabbitmq:3.13-management
```

两个端口：`5672` 是 AMQP 协议端口（应用收发消息用），`15672` 是管理控制台（浏览器访问 `http://localhost:15672`，默认账号密码都是 `guest`）。

**第二步：添加依赖**（生产者、消费者两边都加）

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

**第三步：配置**（application.yml，或放入 Nacos）

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    publisher-confirm-type: correlated # 开启发布确认（§8.5 详解）
    publisher-returns: true # 开启路由失败回退
    listener:
      simple:
        acknowledge-mode: manual # 消费手动确认
```

### 8.3 发送消息：RabbitTemplate

先声明 Exchange、Queue 和 Binding（声明为 Spring Bean，应用启动时自动在 broker 上创建，已存在则跳过）：

```java
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String ORDER_NOTIFY_QUEUE = "order.notify.queue";
    public static final String ROUTING_KEY_CREATED = "order.created";

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE, true, false);  // durable=持久化
    }

    @Bean
    public Queue orderNotifyQueue() {
        return new Queue(ORDER_NOTIFY_QUEUE, true);              // durable=持久化
    }

    @Bean
    public Binding orderNotifyBinding() {
        return BindingBuilder
                .bind(orderNotifyQueue())
                .to(orderExchange())
                .with(ROUTING_KEY_CREATED);
    }
}
```

默认情况下 Spring AMQP 用 Java 序列化发消息（跨语言不友好，管理台里也是乱码）。生产环境统一换成 JSON：

```java
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMessageConverterConfig {

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();   // RabbitTemplate 和 @RabbitListener 都会用它
    }
}
```

发送方（order-service 下单成功后）：

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final RabbitTemplate rabbitTemplate;

    public void createOrder(CreateOrderRequest request) {
        // ... 创建订单的业务逻辑 ...

        // 发异步消息：订单已创建，谁关心谁去消费
        OrderCreatedEvent event = new OrderCreatedEvent(order.getId(), order.getUserId());
        rabbitTemplate.convertAndSend(
                RabbitConfig.ORDER_EXCHANGE,
                RabbitConfig.ROUTING_KEY_CREATED,
                event);
        // 发完立刻返回，不等"短信发没发出去"
    }
}
```

### 8.4 接收消息：@RabbitListener

消费者侧（可以是通知服务，也可以是同应用内的一个组件）：

```java
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderNotificationListener {

    private final SmsService smsService;

    @RabbitListener(queues = RabbitConfig.ORDER_NOTIFY_QUEUE)
    public void onOrderCreated(OrderCreatedEvent event) {
        // 消息体自动按 JSON 反序列化为 OrderCreatedEvent
        smsService.sendOrderCreated(event.getUserId(), event.getOrderId());
    }
}
```

> **前端类比**：`@RabbitListener` ≈ `useEffect` 里订阅一个事件总线——队列里有消息进来，方法自动被调用。区别在于：RabbitMQ 的消费者方法执行失败时，消息不会丢（前提是正确处理确认，见下节）。

### 8.5 可靠投递：Confirm、Return 与手动 ack

"消息发出去"和"消息被消费成功"之间有三个可能丢失的环节。生产环境的可靠投递就是逐环节兜底：

```
环节①：生产者 → Exchange          环节②：Exchange → Queue       环节③：Queue → 消费者
        │ 网络抖动/broker 宕机           │ routing key 配错            │ 消费者处理到一半宕机
        ▼                               ▼                            ▼
  publisher-confirm                publisher-returns            手动 ack
  （broker 确认收到了吗）           （路由到队列了吗）             （处理成功才确认）
```

**环节①：发布确认（ConfirmCallback）**——broker 收到消息后异步回调生产者：

```java
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitConfirmCallback implements RabbitTemplate.ConfirmCallback {

    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        if (!ack) {
            // broker 没收到消息！记录日志 + 重发（或落库后定时任务补偿）
            log.error("消息未到达 Exchange, id={}, cause={}", correlationData.getId(), cause);
        }
    }
}
```

**环节②：路由回退（ReturnsCallback）**——消息到了 Exchange 但没路由到任何队列时退回：

```java
@Component
public class RabbitReturnsCallback implements RabbitTemplate.ReturnsCallback {

    @Override
    public void returnedMessage(ReturnedMessage returned) {
        // 通常是 routing key 写错或队列没绑定 → 开发期就能发现
        log.error("消息路由失败, exchange={}, routingKey={}, msg={}",
                returned.getExchange(), returned.getRoutingKey(), returned.getMessage());
    }
}
```

两个回调注册到 RabbitTemplate：

```java
@Configuration
public class RabbitTemplateConfig {

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter,
            RabbitConfirmCallback confirmCallback,
            RabbitReturnsCallback returnsCallback) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        template.setConfirmCallback(confirmCallback);
        template.setReturnsCallback(returnsCallback);
        return template;
    }
}
```

**环节③：消费手动确认（manual ack）**——§8.2 已配置 `acknowledge-mode: manual`，消费者处理成功才确认；失败则拒绝并让消息重回队列：

```java
import com.rabbitmq.client.Channel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;

@RabbitListener(queues = RabbitConfig.ORDER_NOTIFY_QUEUE)
public void onOrderCreated(OrderCreatedEvent event, Channel channel,
        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
    try {
        smsService.sendOrderCreated(event.getUserId(), event.getOrderId());
        channel.basicAck(deliveryTag, false);          // 处理成功 → 确认，消息从队列删除
    } catch (Exception e) {
        channel.basicNack(deliveryTag, false, true);   // 失败 → 拒绝，requeue=true 重回队列
        // ⚠️ 无限重回队列会死循环，生产环境应配合死信队列（§8.6）限制重试
    }
}
```

> **三层兜底总结**：队列/交换机/消息持久化（`durable=true` + 持久化消息）保证 broker 重启不丢；Confirm/Return 保证"发"的环节可知；手动 ack 保证"收"的环节不丢。三者配齐，才叫"可靠投递"。

### 8.6 死信队列：消息的兜底处理

消息不可能无限重试。死信队列（DLQ）是"坏消息的回收站"：满足条件的消息会被自动转发到 DLQ，由人工或补偿任务兜底。

```
                        正常流程                       兜底流程
                order.notify.queue              order.notify.dlq
 生产者 ───▶ order.exchange ───▶ 主队列 ───▶ 消费者
                                     │
                                     │ 满足以下任一条件，消息转入 DLQ：
                                     │  ① 被消费者 basicNack 且 requeue=false
                                     │  ② 消息过期（TTL）
                                     │  ③ 队列满了
                                     ▼
                              死信交换机（dlx）───▶ 死信队列 ───▶ 人工排查/补偿任务
```

声明方式（在主队列上挂死信参数）：

```java
@Bean
public Queue orderNotifyQueue() {
    return QueueBuilder.durable(RabbitConfig.ORDER_NOTIFY_QUEUE)
            .deadLetterExchange("order.dlx")                 // 死信交换机
            .deadLetterRoutingKey("order.notify.dead")       // 死信路由键
            .build();
}

@Bean
public DirectExchange orderDlx() {
    return new DirectExchange("order.dlx");
}

@Bean
public Queue orderNotifyDlq() {
    return new Queue("order.notify.dlq", true);
}

@Bean
public Binding orderDlqBinding() {
    return BindingBuilder.bind(orderNotifyDlq())
            .to(orderDlx()).with("order.notify.dead");
}
```

消费端的拒绝策略随之调整：重试几次（可用本地计数或 Redis）仍失败 → `basicNack(tag, false, false)` 不再重回队列，让消息进死信队列，事后排查。

### 8.7 本节回顾

```
没有消息队列                          有了 RabbitMQ
────────────                          ─────────────
下单接口还要等短信发完          →     发消息立刻返回，通知异步完成
通知服务挂了，下单也失败        →     消息在队列里等它恢复
加个"发邮件"要改订单代码        →     新队列绑到 Exchange，生产者零改动
消息发了就不知道死活            →     Confirm/Return/手动 ack 三层兜底
坏消息无限重试                  →     死信队列统一收容，人工兜底
```

> **接下来**：RabbitMQ 擅长"一条消息可靠送达"的业务消息。但用户行为埋点、应用日志这类每秒几万条的数据，RabbitMQ 就力不从心了——§9 引入为高吞吐而生的 Kafka。

---

## 9. 消息队列 — Kafka

### 9.0 问题：每天上亿条行为数据怎么收

产品要求记录用户的每一次点击、每一次推送的送达/应答/回调，用于后续统计分析。估算一下：日活 50 万 × 人均 200 次行为 = **每天 1 亿条**，峰值每秒数万条。

```
这类数据的三个特点，恰好都是 RabbitMQ 的短板、Kafka 的主场：

① 量大           每秒数万条持续写入，RabbitMQ 单队列吞吐在万级会开始吃力
② 允许少量丢失    丢 0.01% 的埋点不影响统计结论（但订单消息一条都不能丢！）
③ 多方消费        同一份行为数据：实时统计要消费、离线数仓也要消费，
                 还要求能"把昨天的数据重新跑一遍"（消息回放）
```

Kafka 的设计哲学与 RabbitMQ 根本不同：它本质是一个**分布式追加日志（append-only log）**——消息顺序写磁盘、按偏移量（offset）索引、消费后不删除。这带来了恐怖的顺序写吞吐和"随时回放"的能力。

### 9.1 核心概念：Topic、Partition、Consumer Group

```
Topic: user-action（用户行为）
─────────────────────────────────────────────────────────────

Partition 0:  [msg0][msg3][msg6] ...     ← 分区是物理存储和
Partition 1:  [msg1][msg4][msg7] ...        并行的最小单位
Partition 2:  [msg2][msg5][msg8] ...        消息按 key 哈希分散到各分区

                    │
Consumer Group: action-log-consumers（消费组）
  ├── Consumer A ──→ Partition 0
  ├── Consumer B ──→ Partition 1
  └── Consumer C ──→ Partition 2

  规则：一个分区同一时刻只能被组内一个消费者读取
       → 3 个分区最多支撑 3 个消费者并行；想更快？加分区

另一个 Consumer Group: warehouse-sync（离线数仓同步组）
  └── 独立维护一份 offset，从头再读一遍同样的数据，互不干扰
```

对照 RabbitMQ 理解：

| 概念     | RabbitMQ              | Kafka                            |
| -------- | --------------------- | -------------------------------- |
| 消息去哪 | Queue                 | Topic（逻辑）→ Partition（物理） |
| 消费语义 | 消费后删除            | 消费后保留，按保留期过期         |
| 谁来定位 | 队列总是从头给        | 每个消费组自己记 offset，可回拨  |
| 并行扩容 | 多消费者抢一个队列    | 分区数 = 组内最大并行度          |
| 多方订阅 | 多队列绑一个 Exchange | 多 Consumer Group 读同一 Topic   |

### 9.2 三步接入 Kafka

**第一步：启动 Kafka**（Docker，KRaft 模式——Kafka 3.x 起不再依赖 Zookeeper）

```yaml
services:
  kafka:
    image: apache/kafka:3.7.0
    container_name: kafka
    ports:
      - "9092:9092"
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      CLUSTER_ID: MkU3OEVBNTcwNTJENDM2Qk
```

**第二步：添加依赖**

```xml
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>
```

**第三步：配置**（application.yml）

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all # 等所有副本确认（可靠性最高）
    consumer:
      group-id: action-log-consumers
      auto-offset-reset: earliest # 首次启动从最早的消息读起
      enable-auto-commit: false # 关闭自动提交，手动 ack（§9.4）
```

> **消息体约定**：Kafka 客户端只认字节，教学和生产中最通用的做法是 **value 用 JSON 字符串**（任何语言都能解析，排查问题时控制台直接可读）。生产者和消费者各自负责序列化/反序列化。

### 9.3 发送消息：KafkaTemplate

```java
@Service
@RequiredArgsConstructor
public class UserActionProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public void recordAction(UserAction action) {
        String json = JSON.toJSONString(action);   // 任意 JSON 库均可
        // send(topic, key, value)：key 决定分区——同一 userId 的行为总是进同一分区，保序
        kafkaTemplate.send("user-action",
                String.valueOf(action.getUserId()), json);
    }
}
```

> **key 的作用**：`send(topic, key, value)` 中的 key 决定消息进哪个分区（hash(key) % 分区数）。用 userId 做 key，就能保证"同一个用户的行为在分区内严格有序"——Kafka 只保证**分区内有序**，不保证全局有序，这是面试高频考点。

### 9.4 接收消息：@KafkaListener 与手动 ack

默认配置下 `@KafkaListener` 就能工作。生产环境的标准做法是自定义 containerFactory：**手动提交 offset + 批量消费 + 并发控制**：

```java
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3);                                  // 并发线程数 = 分区数
        factory.getContainerProperties()
                .setAckMode(ContainerProperties.AckMode.MANUAL);    // 手动提交 offset
        return factory;
    }
}
```

消费者：

```java
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserActionConsumer {

    private final UserActionRepository repository;

    @KafkaListener(topics = "user-action", groupId = "action-log-consumers")
    public void onAction(ConsumerRecord<String, String> record, Acknowledgment ack) {
        try {
            UserAction action = JSON.parseObject(record.value(), UserAction.class);
            repository.save(action);          // 落库
            ack.acknowledge();                // 处理成功 → 手动提交 offset
        } catch (Exception e) {
            // 不提交 offset → 重启或再平衡后消息会被重新消费
            // 配合死信 Topic 或重试 Topic 做兜底（思想同 §8.6）
            log.error("行为日志消费失败, offset={}, value={}", record.offset(), record.value(), e);
        }
    }
}
```

> **手动 ack 的本质**：offset 就是"消费进度书签"。处理成功才提交 = 书签前移；处理失败不提交 = 书签不动，消息下次还会被读到。这和 RabbitMQ 的手动 ack 是同一思想，只是 RabbitMQ 确认的是"单条消息"，Kafka 提交的是"分区位置"。

### 9.5 RabbitMQ vs Kafka：为什么很多团队两个都用

```
                       RabbitMQ                          Kafka
──────────────────────────────────────────────────────────────────────
定位          企业级消息代理（broker）            分布式流数据平台
擅长          一条消息可靠送达指定消费者          海量数据持续流过、多方订阅
吞吐          万级/秒（单队列）                   十万~百万级/秒（分区并行）
路由能力      ★★★★★（direct/topic/fanout）      ★★（只有 topic + key 分区）
消息确认      单条 ack/nack，语义丰富             按 offset 批量提交
消息保留      消费即删                            保留期内可反复消费（回放）
延迟消息/死信  原生支持，体系成熟                 需自行用额外 Topic 实现
典型场景      订单通知、任务派发、事务消息        日志收集、行为埋点、CDC 数据同步、
              （"必须送达，不能丢"）             事件溯源（"量大、要分析、可回放"）
──────────────────────────────────────────────────────────────────────
选型一句话：业务事件用 RabbitMQ，数据管道用 Kafka
```

**真实世界的组合用法**：消息队列不是二选一的考题。不少生产系统里两个 MQ 各司其职，甚至首尾相接成一条管道：

```
                    ┌─────────────────────────────────────────────┐
                    │           一条典型的"数据接力管道"            │
                    └─────────────────────────────────────────────┘

外部数据源/埋点                                   业务动作
     │                                               ▲
     ▼                                               │
┌──────────┐   海量原始数据    ┌───────────┐   匹配命中   ┌──────────┐
│  采集端   │ ───────────────▶ │  计算服务  │ ──────────▶ │ 消息中心  │
└──────────┘     Kafka        │ （消费、   │  RabbitMQ   │ （可靠送  │
                             │  过滤匹配） │             │  达用户） │
                             └───────────┘             └──────────┘

Kafka 段：吞得下、可回放、算错了重跑
RabbitMQ 段：到用户这最后一步，一条都不能丢
```

**为什么不能只用 Kafka？** Kafka 没有成熟的单条 nack、延迟消息、灵活路由——用 Kafka 做"订单超时关闭"这类业务消息，你要自己造很多轮子。**为什么不能只用 RabbitMQ？** 每天上亿条行为日志灌进 RabbitMQ，broker 内存和磁盘 IO 会先扛不住，而且消费完就删，数仓想再要一份历史数据就没了。选型不是选"最好的"，而是选"最匹配场景的"。

### 9.6 本节回顾

```
没有 Kafka                            有了 Kafka
─────────                             ─────────
行为数据打爆业务库              →     先进 Kafka，消费者异步落库
数据只能一方消费                →     多消费组独立订阅同一 Topic
统计口径改了，历史数据没法重算   →     offset 回拨，消息回放重跑
消费者扩容但提速不明显          →     分区数决定并行度，水平扩展
```

> **接下来**：至此，同步调用、异步消息、可观测性、容错、安全全部就位。§10 把这些技术决策汇总成实战清单。

---

## 10. 实战决策

### 10.1 什么时候该拆？什么时候不该拆？

```
该拆的信号                         不该拆的信号
─────────                         ──────────
□ 单个模块的开发速度明显下降       □ 系统只有 2~3 个模块
□ 不同模块需要不同的扩缩容节奏     □ 团队只有 3~5 人
□ 不同模块需要不同的技术栈         □ 业务还在快速试错阶段
□ 多人协作经常代码冲突             □ 没有专门的运维人员
□ 测试/部署时间超过 10 分钟        □ 单体性能还没到瓶颈
```

> **黄金法则**：先让单体跑通业务（MVP），等业务验证了、团队成长了、单体开始痛了，再按模块边界逐步拆分。不要为了微服务而微服务。

**拆分优先级指南**：

```
第一步：拆基础设施（不影响业务代码）
  □ 引入 Nacos（服务注册 + 配置中心）
  □ 引入 Gateway（统一入口）
  □ 引入 SkyWalking（链路追踪，agent 挂载零侵入）

第二步：拆数据（最难的也是最重要的）
  □ 每个模块独立数据库
  □ 原单体中的跨表 JOIN → 改为 Feign 调用 + 数据组装
  □ 梳理跨库写操作：哪些要最终一致性（为引入 MQ 做准备）

第三步：拆服务（按业务边界）
  □ 先拆变更最频繁的模块（减少部署耦合）
  □ 再拆资源消耗最大的模块（独立扩容）
  □ 最后拆核心模块（最了解业务后再动）

第四步：加防御与异步化
  □ 所有 Feign 调用加 Sentinel fallback，规则持久化到 Nacos
  □ "做了就行不用等"的调用改为 RabbitMQ 异步消息
  □ 日志、行为埋点接入 Kafka 数据管道
  □ 完善监控和告警
```

### 10.2 组件选型决策树

```
你需要什么能力？
    │
    ├── 服务发现 → Nacos（推荐）/ Eureka（已停更，不推荐）
    │
    ├── 服务调用 → OpenFeign（同步）/ RabbitMQ・Kafka（异步，见下）
    │
    ├── 统一入口 → Spring Cloud Gateway（推荐）/ Zuul（已停更）
    │
    ├── 配置管理 → Nacos Config（与注册中心同一套）/ Apollo（携程）
    │
    ├── 服务容错 → Sentinel（推荐）/ Resilience4j（国际站常用）
    │
    ├── 链路追踪 → SkyWalking（推荐：agent 零侵入 + 拓扑 + JVM 指标）
    │              / Micrometer Tracing + Zipkin（轻量，SDK 式）
    │
    ├── 异步消息 → 业务消息（要可靠送达）→ RabbitMQ
    │              数据管道（量大、回放）→ Kafka
    │              阿里生态/事务消息   → RocketMQ
    │
    └── 分布式事务 → 优先：消息驱动最终一致性（§8/§9）
                     强一致刚需：Seata AT（§12.1 延伸阅读）
```

### 10.3 10 个常见反模式

| #   | 反模式           | 问题                                       | 正确做法                                         |
| --- | ---------------- | ------------------------------------------ | ------------------------------------------------ |
| 1   | 拆得太细         | 一个功能 3 个服务，调试地狱                | 先按业务边界拆（用户/商品/订单），不过早按技术拆 |
| 2   | 共享数据库       | 所有服务连同一个库 → 单体换皮              | 每个服务独立数据库，通过 API 通信                |
| 3   | 分布式事务滥用   | 发通知也用 Seata，性能骤降                 | 能用最终一致性就别用强一致（§12.1）              |
| 4   | 没有熔断         | 一个服务挂了，全链路雪崩                   | 所有 Feign 调用必须有 fallback（§5.4）           |
| 5   | 没有链路追踪     | 出问题不知道看哪个服务的日志               | 挂载 SkyWalking agent（§6），零代码侵入          |
| 6   | 配置硬编码       | Nacos 地址写在 application.yml             | 用 Nacos Config 集中管理（§2.4）                 |
| 7   | 网关做业务逻辑   | Gateway 里写订单校验 → 网关变成新单体      | 网关只做路由 + 认证 + 限流，业务逻辑在服务中     |
| 8   | 没有统一响应格式 | 3 个服务返回 3 种 JSON 格式 → 前端适配地狱 | 参考你已有的 GlobalResponseBodyAdvice 模式统一   |
| 9   | 同步调用链过长   | A → B → C → D，一次请求串行等 4 个服务     | 能并行的并行，"做了就行"的环节改 MQ 异步（§8）   |
| 10  | 不做幂等         | 网络重试/MQ 重复消费导致订单重复创建       | 订单号做唯一索引；消费者按业务键去重             |

---

## 11. 速查清单

### 11.1 依赖坐标速查

```xml
<!-- ========== 父 POM（统一版本管理） ========== -->
<dependencyManagement>
    <dependencies>
        <!-- Spring Cloud BOM -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2025.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <!-- Spring Cloud Alibaba BOM -->
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-alibaba-dependencies</artifactId>
            <version>2025.0.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- ========== 各服务通用依赖 ========== -->
<!-- Nacos 服务发现 -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>

<!-- Nacos 配置中心（通过 spring.config.import 导入，无需 bootstrap） -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>

<!-- OpenFeign 远程调用 -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>

<!-- Sentinel 服务容错 -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-sentinel</artifactId>
</dependency>
<!-- Sentinel 规则持久化到 Nacos（生产实践，§5.6） -->
<dependency>
    <groupId>com.alibaba.csp</groupId>
    <artifactId>sentinel-datasource-nacos</artifactId>
</dependency>

<!-- SkyWalking 日志关联：logback 打印 %tid（§6.5）；agent 本体不在 pom 中 -->
<dependency>
    <groupId>org.apache.skywalking</groupId>
    <artifactId>apm-toolkit-logback-1.x</artifactId>
    <version>9.4.0</version>
</dependency>

<!-- RabbitMQ（§8） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>

<!-- Kafka（§9） -->
<dependency>
    <groupId>org.springframework.kafka</groupId>
    <artifactId>spring-kafka</artifactId>
</dependency>

<!-- ========== Gateway 专用（替代 spring-boot-starter-webmvc） ========== -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
<!-- Gateway 层限流：Sentinel 网关适配器（§5.6） -->
<dependency>
    <groupId>com.alibaba.csp</groupId>
    <artifactId>sentinel-spring-cloud-gateway-adapter</artifactId>
</dependency>
```

### 11.2 注解速查

| 注解                              | 位置                    | 作用                                            | 章节  |
| --------------------------------- | ----------------------- | ----------------------------------------------- | ----- |
| （无需 `@EnableDiscoveryClient`） | 启动类                  | Nacos Discovery 自动注册                        | §2.6  |
| `@EnableFeignClients`             | 启动类                  | 扫描 Feign 接口                                 | §3.2  |
| `@FeignClient(name="xxx")`        | 接口                    | 声明远程服务调用                                | §3.2  |
| `@LoadBalanced`                   | RestClient.Builder Bean | RestClient 场景中服务名 → IP 解析；本项目未使用 | §3.1  |
| `@SentinelResource`               | Controller 方法         | 声明限流/降级                                   | §5.3  |
| `@RabbitListener(queues="xxx")`   | 组件方法                | 监听 RabbitMQ 队列                              | §8.4  |
| `@KafkaListener(topics="xxx")`    | 组件方法                | 监听 Kafka Topic                                | §9.4  |
| `@GlobalTransactional`            | Service 方法            | 分布式事务（延伸阅读）                          | §12.1 |
| `@SpringBootApplication`          | 启动类                  | Spring Boot 标配                                | —     |
| `@RestController`                 | Controller              | REST API                                        | —     |
| `@Service`                        | Service                 | 业务逻辑                                        | —     |

### 11.3 配置项速查

```yaml
# ========== Nacos Discovery ==========
spring:
  application:
    name: my-service               # 服务名 = Nacos 中的注册名
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        namespace:                  # 命名空间 ID（环境隔离，§2.8）
        group: DEFAULT_GROUP        # 业务域分组（§2.8）

# ========== Nacos Config（含共享配置，§2.8） ==========
spring:
  config:
    import:
      - "optional:nacos:shared-common.yaml"
      - "optional:nacos:my-service.yaml"
  cloud:
    nacos:
      config:
        server-addr: localhost:8848
        file-extension: yaml

# ========== Sentinel ==========
spring:
  cloud:
    sentinel:
      transport:
        dashboard: localhost:8090
        port: 8719
      eager: true
      datasource:                   # 规则持久化到 Nacos（§5.6）
        flow:
          nacos:
            server-addr: localhost:8848
            dataId: my-service-flow-rules
            groupId: SENTINEL_GROUP
            rule-type: flow

# ========== Gateway（含生产超时，§4.5） ==========
spring:
  cloud:
    gateway:
      httpclient:
        connect-timeout: 3000
        response-timeout: 10000
      routes:
        - id: my-route
          uri: lb://target-service
          predicates:
            - Path=/api/xxx/**
          filters:
            - StripPrefix=1

# ========== Feign ==========
spring:
  cloud:
    openfeign:
      client:
        config:
          default:
            connect-timeout: 2000
            read-timeout: 5000

# ========== RabbitMQ（§8.2，可靠投递三件套） ==========
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    publisher-confirm-type: correlated
    publisher-returns: true
    listener:
      simple:
        acknowledge-mode: manual

# ========== Kafka（§9.2） ==========
spring:
  kafka:
    bootstrap-servers: localhost:9092
    producer:
      acks: all
    consumer:
      group-id: my-consumers
      enable-auto-commit: false
      auto-offset-reset: earliest

# ========== SkyWalking：不是 yml，是 JVM 启动参数（§6.3） ==========
# java -javaagent:/opt/skywalking-agent/skywalking-agent.jar \
#      -Dskywalking.agent.service_name=my-service \
#      -Dskywalking.collector.backend_service=localhost:11800 \
#      -jar my-service.jar
```

### 11.4 Docker Compose 速查

```yaml
# 完整 docker-compose.yml（§1.4 主 compose；以下为各章节扩展服务）
services:
  nacos: # 注册中心 + 配置中心 → :8848（主 compose，§1.4）
  mysql-user: # 用户数据库 → :3307（主 compose，§1.4）
  mysql-product: # 商品数据库 → :3308（主 compose，§1.4）
  mysql-order: # 订单数据库 → :3309（主 compose，§1.4）
  sentinel: # 流量控制台 → :8090（§5.2，docker run 单容器即可）
  skywalking-oap: # 链路数据接收/分析 → :11800/:12800（§6.3）
  skywalking-ui: # 链路追踪 UI → :8088（§6.3）
  rabbitmq: # 业务消息 → :5672，管理台 → :15672（§8.2）
  kafka: # 数据管道 → :9092（§9.2）
```

### 11.5 服务端口速查

```
Gateway         :8080     ← 前端唯一入口
user-service    :8081     ← MySQL user_db :3307
product-service :8082     ← MySQL product_db :3308
order-service   :8083     ← MySQL order_db :3309
Nacos           :8848     ← 注册中心 + 配置中心
Nacos Console   :8084     ← 宿主机端口，映射到容器内 :8080；避免占用 Gateway :8080
Sentinel        :8090     ← 流量控制台
SkyWalking OAP  :11800    ← agent 上报（gRPC）；:12800 查询 API
SkyWalking UI   :8088     ← 宿主机端口，映射到容器内 :8080
RabbitMQ        :5672     ← AMQP；:15672 管理控制台
Kafka           :9092     ← broker
```

### 11.6 调用链路速查

```
一次下单请求的完整路径
─────────────────────

前端 POST /api/orders
  │
  ▼
Gateway (:8080)
  │ AuthFilter 验 JWT
  │ Path=/api/orders/** → lb://order-service
  │ StripPrefix=1 → /orders
  ▼
order-service (:8083)
  │
  ├──[Feign]→ product-service (:8082)
  │   │ GET /products/42 → 查询商品信息
  │   │ PUT /products/42/stock → 扣减库存
  │   │ [Sentinel 熔断保护]
  │   │ [SkyWalking agent 自动传播 Trace]
  │
  ├──[Feign]→ user-service (:8081)
  │   │ GET /users/1 → 查询用户信息
  │   │ PUT /users/1/balance → 扣减余额
  │   │ [Sentinel 熔断保护]
  │   │ [SkyWalking agent 自动传播 Trace]
  │
  │ INSERT INTO orders → 创建订单
  │
  ├──[RabbitMQ]→ order.exchange / order.created
  │   └─▶ 通知消费者异步发短信/推送（Confirm + 手动 ack 保可靠）
  │
  └──[Kafka]→ user-action Topic
      └─▶ 行为日志消费者异步落库、统计（多消费组可重复订阅）
  ▼
返回统一响应 {code, message, data}

在 SkyWalking UI (:8088) 中可以查看完整 Trace 与服务拓扑
在 Sentinel (:8090) 中可以看到 QPS/RT 实时数据
在 Nacos (:8848) 中可以看到所有服务在线状态与配置
在 RabbitMQ 管理台 (:15672) 中可以看到队列深度与消费速率
```

---

## 12. 延伸阅读

本章内容是重要但非本指南主线的主题。学有余力或工作中遇到时再深入。

### 12.1 分布式事务 — Seata AT 模式

> **定位说明**：分布式事务是微服务的经典难题，但它不是每个团队的必需品——很多公司优先用消息驱动的最终一致性（§8/§9）解决问题。本节压缩介绍 Seata AT 模式的核心思想，了解即可。

**问题：@Transactional 失效了**

在单体中，下单操作一个注解搞定：

```java
@Transactional    // ← 一个注解，全部原子执行
public void createOrder(OrderRequest request) {
    orderMapper.insert(order);              // ① 写 orders 表
    productMapper.deductStock(productId, quantity); // ② 扣库存
    userMapper.deductBalance(userId, amount); // ③ 扣余额
    // 任何一步失败 → 全部回滚
}
```

在微服务中，三个操作分散在不同的服务和数据库中：

```
① INSERT orders → 成功（order_db 提交）
② UPDATE stock → 失败（商品库存不足）
                    ↓
order_db 已经提交了 → 无法回滚 ← @Transactional 管不到另一个数据库
```

**Seata AT 模式原理（一图流）**

```
                        ┌─────────────┐
                        │   Seata     │
                        │   Server    │
                        │  (TC 协调器) │
                        └──────┬──────┘
                               │
               ┌───────────────┼───────────────┐
               │               │               │
        ┌──────▼──────┐ ┌──────▼──────┐ ┌──────▼──────┐
        │order-service│ │product-svc  │ │ user-service│
        │     TM      │ │     RM      │ │     RM      │
        │ (事务发起方) │ │ (资源参与者) │ │ (资源参与者) │
        └─────────────┘ └─────────────┘ └─────────────┘

TM = Transaction Manager（事务管理器，定义事务边界）
RM = Resource Manager（资源管理器，管理分支事务）
TC = Transaction Coordinator（事务协调器，维护全局事务状态）

阶段一：各 RM 执行本地 SQL，同时记录 Undo Log（如何恢复旧值）
阶段二：TC 收集结果 → 全部成功则全局提交（异步删 Undo Log）
       → 任一失败则全局回滚（各 RM 按 Undo Log 恢复数据）
```

**使用：一行注解**

```java
@GlobalTransactional    // ← 替代 @Transactional，其余业务代码不变
public void createOrder(OrderRequest request) {
    orderMapper.insert(order);
    productClient.deductStock(request.getProductId(), request.getQuantity());
    userClient.deductBalance(request.getUserId(), request.getTotalAmount());
}
```

依赖为 `spring-cloud-starter-alibaba-seata`，Seata Server 用 `seataio/seata-server` 镜像启动（控制台 :7091，业务 RPC :8091），接入细节以官方文档为准。

**最终一致性思想（比工具更重要）**

不是所有跨服务操作都需要强一致性。很多场景下，**最终一致性**就够了：

```
强一致性（Seata AT）              最终一致性（消息驱动）
─────────────────                ────────────────────
下单 + 扣库存必须同时成功          下单成功后，发消息异步扣库存
或同时失败                         扣库存失败？重试或进死信队列补偿

适合：金融交易、库存扣减            适合：发短信通知、生成报表、同步搜索索引
```

> **关键判断**：如果你能用"重试 + 补偿"解决的不一致，就别引入分布式事务。分布式事务是最后的手段，不是第一选择——这正是 §8/§9 的消息队列在实战中比 Seata 更常用的原因。

### 12.2 Spring Cloud Stream：消息队列的统一抽象

学了 RabbitMQ（§8）和 Kafka（§9）后你会发现：两边的发送/监听 API 长得不一样。**Spring Cloud Stream** 是 Spring 对消息中间件的抽象层——面向统一的 Binder API 编程，底层切换 RabbitMQ / Kafka / RocketMQ 不改业务代码：

```java
// 生产者：发消息（不关心底层是 RabbitMQ 还是 Kafka）
streamBridge.send("orderCreated-out-0", orderEvent);

// 消费者：函数式声明
@Bean
public Consumer<OrderCreatedEvent> orderCreated() {
    return event -> smsService.sendOrderCreated(event.getUserId(), event.getOrderId());
}
```

**什么时候用它**：多 MQ 并存且想统一编程模型；或团队习惯函数式风格。**什么时候不用**：需要精细控制 RabbitMQ 的 Confirm/Return、Kafka 的 offset/分区策略时，原生 API（§8/§9）更直接。本指南选择原生 API 作为主线——先懂底层，抽象层一学就会。

---

> **学习路线建议**：
>
> 1. **快速体验**（1 小时）：启动 Docker Compose → 创建 user/product/order 三个空服务 → 配好 Nacos 注册发现 → 写一个 Feign 调用 → 浏览器访问通过 Gateway 路由。
> 2. **系统学习**（1 天）：按本指南 §1 → §7 顺序阅读。每读完一章，在项目里实践对应的功能。
> 3. **进阶深入**（1 周）：接入 Sentinel 规则并持久化到 Nacos、挂载 SkyWalking agent 打通链路追踪、用 RabbitMQ 改造一个"下单发通知"场景、用 Kafka 收一条行为日志。给每个 Feign 调用加 fallback。
> 4. **生产准备**（持续）：安全加固（JWT 密钥管理）、MQ 可靠性演练（kill 消费者看消息是否重回队列）、监控告警、CI/CD 流水线、容器化部署。
>
> **关联阅读**：
>
> - [Spring Security 指南](spring-security-guide.md) — 单体 + 微服务安全
> - [Spring 异常处理指南](spring-exception-guide.md) — 统一响应格式
> - [Spring Filter/Interceptor 指南](spring-filter-interceptor-guide.md) — Filter 链深入理解（Gateway Filter 的基础）
> - [Spring Validation 指南](spring-validation-guide.md) — DTO 校验
> - [Spring Transaction 指南](spring-transaction-guide.md) — 本地事务（§12.1 分布式事务的前置知识）
