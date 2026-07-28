# Spring Cloud 微服务企业级指南

> 本指南系统介绍 Spring Cloud 微服务完整知识体系。先建立分布式认知（为什么需要微服务、全景架构），再逐类深入服务发现、远程调用、网关、配置中心、熔断降级、链路追踪、分布式事务、安全。每章一个问题驱动，同一「电商三服务」场景贯穿全文。
>
> 适用版本：Spring Boot 4.0.x / Spring Cloud 2025.1.x (Oakwood) / Spring Cloud Alibaba 2025.1.0.0，Java 17+
>
> 面向读者：已掌握 Spring Boot 单体开发（IoC/DI、MVC、Security、数据访问、异常处理），准备学习微服务的开发者。如果你是前端出身，指南中嵌入了前端类比帮你快速建立直觉。

---

## 目录

0. [前置概念：为什么需要微服务](#0-前置概念为什么需要微服务)
   - [0.1 你现在的位置：单体舒适区](#01-你现在的位置单体舒适区)
   - [0.2 单体什么时候开始痛](#02-单体什么时候开始痛)
   - [0.3 微服务的承诺与代价](#03-微服务的承诺与代价)
   - [0.4 分布式系统八大谬误](#04-分布式系统八大谬误)
   - [0.5 前端视角的概念映射](#05-前端视角的概念映射)
1. [全景图：Spring Cloud 微服务完整架构](#1-全景图spring-cloud-微服务完整架构)
   - [1.1 一张图看懂所有组件](#11-一张图看懂所有组件)
   - [1.2 版本兼容矩阵](#12-版本兼容矩阵)
   - [1.3 贯穿场景：电商三服务](#13-贯穿场景电商三服务)
   - [1.4 Docker Compose 一键部署](#14-docker-compose-一键部署)
2. [服务注册与发现 — Nacos](#2-服务注册与发现--nacos)
   - [2.0 问题：服务怎么找到彼此](#20-问题服务怎么找到彼此)
   - [2.1 Nacos 是什么](#21-nacos-是什么)
   - [2.2 启动 Nacos Server](#22-启动-nacos-server)
   - [2.3 服务注册：让服务"报到"](#23-服务注册让服务报到)
   - [2.4 服务发现：用服务名替代 IP](#24-服务发现用服务名替代-ip)
   - [2.5 服务发现的完整请求链路](#25-服务发现的完整请求链路)
   - [2.6 本节回顾](#26-本节回顾)
3. [远程服务调用 — OpenFeign](#3-远程服务调用--openfeign)
4. [API 网关 — Spring Cloud Gateway](#4-api-网关--spring-cloud-gateway)
5. [统一配置管理 — Nacos Config](#5-统一配置管理--nacos-config)
6. [服务容错 — Sentinel](#6-服务容错--sentinel)
7. [分布式链路追踪 — Micrometer Tracing + Zipkin](#7-分布式链路追踪--micrometer-tracing--zipkin)
8. [分布式事务 — Seata](#8-分布式事务--seata)
9. [安全 — 微服务中的认证授权](#9-安全--微服务中的认证授权)
10. [延伸阅读：消息驱动](#10-延伸阅读消息驱动)
11. [实战决策](#11-实战决策)
12. [速查清单](#12-速查清单)

---

## 0. 前置概念：为什么需要微服务

微服务不是银弹。它解决了一些问题，也创造了一些新问题。理解「为什么」比理解「怎么做」更重要——否则你会为一个只需要单体的小项目引入不必要的复杂度。

### 0.1 你现在的位置：单体舒适区

如果你已掌握 Spring Boot 的单体开发（IoC/DI、MVC、Security、数据访问、事务、异常处理），你现在的项目结构大概是这样的：

```
┌─────────────────────────────────────────────┐
│              一个 JVM 进程                    │
│                                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
│  │ 用户模块  │  │ 商品模块  │  │ 订单模块  │  │
│  │Controller│  │Controller│  │Controller│  │
│  │ Service  │  │ Service  │  │ Service  │  │
│  │   ↓      │  │   ↓      │  │   ↓      │  │
│  │  Mapper  │  │  Mapper  │  │  Mapper  │  │
│  └──────────┘  └──────────┘  └──────────┘  │
│                    │                        │
│            ┌───────┴───────┐                │
│            │  同一个数据库   │                │
│            └───────────────┘                │
└─────────────────────────────────────────────┘

特点：
✅ 开发简单：一个 IDE 窗口搞定全部代码
✅ 调试方便：断点一路跟到底，调用栈完整
✅ 事务简单：@Transactional 一个注解，跨表操作原子执行
✅ 部署简单：一个 jar 包丢上服务器就跑
✅ 测试简单：启动一个 Spring 上下文，所有依赖都在
```

你现在正在舒适区。本章的目的不是让你立刻逃离舒适区，而是在心里埋一颗种子：**什么时候这个舒适区会变成瓶颈？**

### 0.2 单体什么时候开始痛

单体不是"不好"，单体是"会随着规模增长变得不好"。下面这张图展示了从"刚刚好"到"积重难返"的过程：

```
团队规模：  1 人          5 人              20 人              50 人
           │            │                 │                  │
单体体验    极佳         顺畅              开始摩擦            痛苦
           ✓            ✓                 ✗                  ✗

痛点出现的顺序（从先到后）：

① 代码冲突（5 人+）
   10 个人在同一个 Git 仓库改代码，每次合并都像拆雷。
   用户模块改了公共工具类 → 订单模块挂了，因为没人通知。

② 部署耦合（10 人+）
   订单模块改了一行文案，必须重新部署整个应用。
   部署 = 全部模块一起重启 = 5 分钟停机 = 所有人等着。

③ 数据库瓶颈（业务量增长）
   所有模块共享一个数据库。订单表的慢查询拖慢了用户登录。
   连接池被某个批量任务占满，其他模块拿不到连接。

④ 技术栈锁定
   商品模块想用 Elasticsearch 做全文搜索，但项目是 JPA + MySQL。
   "要么全换，要么不换"—— 结果就是技术债越积越多。

⑤ 认知负荷爆炸（代码量 10 万行+）
   新人 onboarding 需要 3 周才能跑通本地环境。
   没人敢动"那个老模块"——原来的作者已经离职了。
```

> **关键洞察**：单体的问题不是「单体不好」，而是「当系统复杂到一定程度后，单体架构的边际成本急剧上升」。如果你的系统永远只有 2~3 个模块、3~5 个开发者，单体完全够用。

### 0.3 微服务的承诺与代价

微服务把单体拆成多个独立的服务，每个服务有自己的进程、自己的数据库、自己的团队。它带来了解耦，也带来了分布式系统的一切复杂性。

```
                    单体架构                          微服务架构
                    ────────                          ────────

部署单元             1 个 jar                          N 个 jar
数据库               1 个共享库                        每个服务独立库
模块通信             方法调用（本地，瞬时）              网络调用（远程，不可靠）
事务                 本地事务（ACID）                   分布式事务（最终一致性）
配置                 一个 application.yml               N 个 application.yml
                                     +
                              配置中心统一管理
调试                 一条调用栈走到底                    请求跨多个服务，需要链路追踪
部署                 一次重启全部                        只重启变更的服务
扩容                 整个应用一起扩                      只扩瓶颈服务
```

**微服务承诺的收益**：

- 独立部署：改订单模块只需要部署订单服务
- 技术异构：搜索服务用 Elasticsearch，推荐服务用 Python——每个服务选最合适的栈
- 团队自治：每个团队独立开发、测试、部署自己的服务
- 故障隔离：商品服务挂了，用户登录不受影响

**微服务带来的代价**：

- 网络不可靠：方法调用变成了 HTTP 调用，引入了延迟、超时、重试
- 数据一致性难：跨服务的事务不再是 `@Transactional` 一句话的事
- 运维复杂度暴增：N 个服务 = N 套监控、N 套日志、N 套部署流水线
- 调试困难：一个请求跨 5 个服务，到底慢在哪一层？
- 分布式系统本身的复杂性：服务发现、负载均衡、配置管理、熔断降级——这些都是单体中不存在的概念

> **一句话总结**：微服务用「运维复杂度」换取「开发灵活性」。在团队小、业务简单时这是亏本买卖；在系统复杂到单体的边际成本高过微服务的运维成本时，才开始划算。

### 0.4 分布式系统八大谬误

1994 年，Sun 公司的工程师总结了分布式计算的 8 个常见错误假设。近 30 年后的今天，它们仍然是每个微服务开发者的必备常识：

| #   | 谬误           | 真相                                          |
| --- | -------------- | --------------------------------------------- |
| 1   | 网络是可靠的   | 网络随时会断、会丢包、会超时                  |
| 2   | 延迟为零       | 本地方法调用 1ns，网络调用 1ms——差了 100 万倍 |
| 3   | 带宽是无限的   | 大对象跨服务传输，序列化开销、网络开销都不小  |
| 4   | 网络是安全的   | 内网也有被攻破的可能，服务间通信需要认证      |
| 5   | 拓扑不会变     | 服务实例随时上线、下线、扩缩容                |
| 6   | 只有一个管理员 | 微服务意味着多团队、多管理员                  |
| 7   | 传输成本为零   | 序列化/反序列化消耗 CPU，JSON 比二进制大很多  |
| 8   | 网络是同构的   | 不同服务的语言、框架、协议可能不同            |

整个 Spring Cloud 生态，本质上就是在与这八大谬误对抗：

```
八大谬误                      Spring Cloud 的回应
────────                     ──────────────────
① 网络不可靠       →         Sentinel（熔断、重试、降级）
② 延迟不为零       →         Micrometer Tracing（找到瓶颈）
⑤ 拓扑会变         →         Nacos（服务注册与发现）
⑥ 多管理员         →         Nacos Config（统一配置管理）
④ 网络不安全       →         Spring Security + Gateway（统一认证）
```

### 0.5 前端视角的概念映射

如果你是前端开发者，以下映射帮你用熟悉的概念理解微服务：

| 微服务概念                   | 前端类比                                        | 相似之处                                                                                                     |
| ---------------------------- | ----------------------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| **服务注册发现**（Nacos）    | DNS 解析 + npm 包注册表                         | 你用 `import lodash from 'lodash'` 而不是 `import from '1.2.3.4'`，Nacos 让服务间用服务名而非 IP 通信        |
| **声明式调用**（OpenFeign）  | TypeScript 的 API 类型定义 + axios              | 你定义 `interface UserApi { getUser(id: string): Promise<User> }`，Feign 就是后端的"接口定义即调用"          |
| **API 网关**（Gateway）      | Nginx 反向代理 / BFF 层                         | 前端调用 `GET /api/orders/42` → Gateway 转发到 order-service。前端不需要知道 order-service 的地址            |
| **配置中心**（Nacos Config） | `.env` 文件 → Vercel/Netlify 环境变量面板       | 你在 Vercel 后台改一个环境变量，所有部署实例自动生效。Nacos Config 做同样的事                                |
| **熔断降级**（Sentinel）     | React `<ErrorBoundary>` + `<Suspense fallback>` | 组件报错 → 显示 fallback UI。服务调用失败 → 返回降级数据，不拖垮调用方                                       |
| **链路追踪**（Zipkin）       | Chrome DevTools Network 面板                    | 你看到 `GET /api/orders` 花了 2.3s，点开发现 `GET /api/products` 占了 2.1s。Zipkin 就是跨服务的 Network 面板 |
| **分布式事务**（Seata）      | 前端很少类比                                    | 这是后端独有的复杂度，§8 会从头讲起                                                                          |

---

## 1. 全景图：Spring Cloud 微服务完整架构

§0 建立了"为什么需要微服务"的认知。本节展示微服务的完整拼图——每个组件在什么位置、解决什么问题、它们如何协作。这张图就是本文的导航地图。

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
     │  :8081     │   │    :8082      │  │   :8083     │
     │            │   │               │  │             │
     │ MySQL      │   │  MySQL        │  │  MySQL      │
     │ (users)    │   │  (products)   │  │  (orders)   │
     └─────┬──────┘   └───────┬───────┘  └──────┬──────┘
           │                  │                  │
           │      Feign       │     Feign        │
           │◄────────────────►│◄────────────────►│
           │                  │                  │
           └──────────────────┼──────────────────┘
                              │
              服务注册 / 配置拉取 / 心跳上报 / 链路数据上报
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
  ┌─────▼─────┐  ┌───────────▼──┐  ┌──────────────▼──┐
  │   Nacos   │  │   Sentinel   │  │     Zipkin      │
  │  :8848    │  │    :8080     │  │     :9411       │
  │           │  │              │  │                  │
  │ 服务注册  │  │ 流量控制     │  │ 链路追踪可视化   │
  │ 配置中心  │  │ 熔断降级     │  │                  │
  └───────────┘  └──────────────┘  └──────────────────┘
        │
  ┌─────▼─────┐
  │   Seata   │
  │  :8091    │
  │           │
  │ 分布式事务 │
  │ 协调器    │
  └───────────┘
```

**各组件的职责一句话**：

| 组件          | 解决什么问题        | 在什么位置                                         |
| ------------- | ------------------- | -------------------------------------------------- |
| **Nacos**     | 服务发现 + 配置管理 | 基础层：所有服务启动时向它注册，调用时从它查询地址 |
| **OpenFeign** | 服务间 HTTP 调用    | 通信层：业务服务之间互相调用的工具                 |
| **Gateway**   | 统一入口 + 路由转发 | 边界层：客户端唯一入口，内网服务不直接暴露         |
| **Sentinel**  | 防止级联故障        | 防御层：嵌入在每个服务中，监控调用、限流、熔断     |
| **Zipkin**    | 定位调用链瓶颈      | 观测层：收集每个服务的调用数据，画拓扑图           |
| **Seata**     | 跨服务数据一致性    | 协调层：管理跨多个数据库的事务                     |

### 1.2 版本兼容矩阵

Spring Cloud 是版本敏感型生态。Spring Boot、Spring Cloud、Spring Cloud Alibaba 三者的版本必须严格匹配。以下是本指南使用的版本（已通过官方文档验证）：

```
Spring Boot          4.0.x           ← 本指南的基座
    │
    └── Spring Cloud  2025.1.x        ← Oakwood 发布列车，Boot 4.0 的唯一对应版本
           │
           └── Spring Cloud Alibaba  2025.1.0.0  ← 唯一适配 Boot 4.0 的 SCA 版本
                  │
                  ├── Nacos Client   3.1.1
                  ├── Sentinel       1.8.9
                  └── Seata          2.5.0
```

**完整依赖坐标**（可直接复制到 pom.xml）：

```xml
<!-- Spring Boot 父 POM -->
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.6</version>
</parent>

<!-- Spring Cloud BOM（统一管理 Spring Cloud 组件版本） -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2025.1.2</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-alibaba-dependencies</artifactId>
            <version>2025.1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

> **BOM 的作用**：在 `<dependencyManagement>` 中导入 BOM 后，下方 `<dependencies>` 中添加具体 starter 时**无需写版本号**——BOM 已为你锁定了所有子依赖的兼容版本。

### 1.3 贯穿场景：电商三服务

全文所有代码示例围绕同一个电商场景展开。三个服务、三个数据库、一条核心调用链：

```
用户下单的请求链路
─────────────────

前端 POST /api/orders（userId=1, productId=42, quantity=2）
    │
    ▼
Gateway (:8080)
    │ Path=/api/orders/** → 路由到 order-service
    ▼
order-service (:8083)
    │
    ├──→ Feign 调用 product-service (:8082)
    │    查询商品信息、扣减库存
    │
    ├──→ Feign 调用 user-service (:8081)
    │    查询用户信息、扣减余额
    │
    └──→ 写入 orders 表（MySQL order-db）
```

这条链路天然覆盖本指南的所有核心概念：

- **服务发现**：order-service 调用 product-service 时，用服务名而非 IP
- **远程调用**：Feign 声明式接口完成跨服务 HTTP 通信
- **网关**：前端只调用 Gateway，不关心后端有几个服务
- **容错**：如果 product-service 响应慢，Sentinel 熔断保护 order-service
- **追踪**：一次下单请求的完整链路在 Zipkin 中可视化
- **事务**：下单和扣库存属于两个数据库，Seata 保证一致性

### 1.4 Docker Compose 一键部署

所有基础设施通过 Docker Compose 一键启动。将以下文件保存为 `docker-compose.yml`，放在项目根目录，执行 `docker-compose up -d`：

```yaml
version: "3.8"
services:
  # ========== 服务注册 + 配置中心 ==========
  nacos:
    image: nacos/nacos-server:v3.1.1
    container_name: nacos
    environment:
      - MODE=standalone
      - PREFER_HOST_MODE=hostname
    ports:
      - "8848:8848"
      - "9848:9848" # gRPC 端口（Nacos 3.x 新增）
    volumes:
      - nacos-data:/home/nacos/data

  # ========== 数据库（三个服务各一个库） ==========
  mysql-user:
    image: mysql:8.0
    container_name: mysql-user
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: user_db
    ports:
      - "3307:3306"

  mysql-product:
    image: mysql:8.0
    container_name: mysql-product
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: product_db
    ports:
      - "3308:3306"

  mysql-order:
    image: mysql:8.0
    container_name: mysql-order
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: order_db
    ports:
      - "3309:3306"

  # ========== 流量控制控制台 ==========
  sentinel:
    image: bladex/sentinel-dashboard:1.8.9
    container_name: sentinel
    ports:
      - "8090:8080"

  # ========== 链路追踪可视化 ==========
  zipkin:
    image: openzipkin/zipkin:latest
    container_name: zipkin
    ports:
      - "9411:9411"

  # ========== 分布式事务协调器 ==========
  seata:
    image: seataio/seata-server:2.5.0
    container_name: seata
    ports:
      - "8091:8091"
      - "7091:7091"
    environment:
      - SEATA_PORT=8091
      - STORE_MODE=db
      - SEATA_STORE_DB_URL=jdbc:mysql://mysql-order:3306/seata?useSSL=false
      - SEATA_STORE_DB_USER=root
      - SEATA_STORE_DB_PASSWORD=root123

volumes:
  nacos-data:
```

> **启动顺序**：先 `docker-compose up -d mysql-user mysql-product mysql-order` 等 MySQL 就绪，再 `docker-compose up -d` 启动其余服务。MySQL 首次启动需要约 30 秒初始化。

---

## 2. 服务注册与发现 — Nacos

### 2.0 问题：服务怎么找到彼此

在单体中，模块之间通过方法调用通信——Spring 帮你注入依赖，方法调用在同一个 JVM 内瞬间完成。但在微服务中，`OrderService` 和 `ProductService` 运行在不同的 JVM 进程里，通过 HTTP 通信。

第一个问题就来了：**Order Service 怎么知道 Product Service 的地址？**

```
❌ 最原始的做法：把地址写死在配置里

order-service 的 application.yml：
─────────────────────────────────
product:
  url: http://192.168.1.100:8082

问题：
• Product Service 扩容到 3 个实例（192.168.1.100 ~ 102），改配置
• Product Service 某个实例挂了，调用方不知道，继续往挂了发请求
• Product Service 迁移到新机器，所有调用方都要改配置
• 50 个服务互相调用 → 每个服务都要维护一张"地址簿"
```

这个问题的本质是：**在动态变化的分布式环境中，调用方如何发现被调用方的地址？** Nacos 的答案是：让服务自己"报到"到一个中心，调用方从中心查询。

### 2.1 Nacos 是什么

Nacos（Naming and Configuration Service）是阿里巴巴开源的服务注册中心和配置中心。在本指南中它扮演两个角色：

```
┌──────────────────────────────────────┐
│              Nacos Server            │
│                                      │
│  ┌────────────────────────────────┐  │
│  │  服务注册中心（Naming Service） │  │
│  │                                │  │
│  │  维护在线服务列表：              │  │
│  │  user-service  → 172.17.0.3:8081│  │
│  │  product-service → 172.17.0.4:8082│ │
│  │  order-service → 172.17.0.5:8083│  │
│  │                                │  │
│  │  心跳检测：每 5 秒收一次心跳，   │  │
│  │  15 秒没收到 → 标记不健康       │  │
│  │  30 秒没收到 → 剔除             │  │
│  └────────────────────────────────┘  │
│                                      │
│  ┌────────────────────────────────┐  │
│  │  配置中心（Config Service）     │  │
│  │  → §5 详解                     │  │
│  └────────────────────────────────┘  │
└──────────────────────────────────────┘
```

> **前端类比**：Nacos 的服务注册中心就像 DNS 服务器。你在浏览器输入 `github.com`，DNS 告诉你 IP 是 `140.82.112.3`。在微服务里，Feign 调用 `product-service`，Nacos 告诉你这个服务当前有哪些 IP。

### 2.2 启动 Nacos Server

使用 §1.4 的 Docker Compose，或单独启动：

```bash
docker run -d --name nacos \
  -e MODE=standalone \
  -p 8848:8848 \
  -p 9848:9848 \
  nacos/nacos-server:v3.1.1
```

启动后访问 `http://localhost:8848/nacos`，默认用户名密码均为 `nacos`：

```
Nacos 控制台首页
────────────────
┌───────────────────────────────────┐
│  服务管理                         │
│  ├── 服务列表  (查看所有注册服务)  │
│  └── 订阅者列表                    │
│                                   │
│  配置管理                         │
│  ├── 配置列表  (管理配置文件)     │
│  └── 历史版本                     │
│                                   │
│  权限控制                         │
│  └── 用户管理                     │
└───────────────────────────────────┘
```

### 2.3 服务注册：让服务"报到"

以 `product-service` 为例，三步完成服务注册。

**第一步：添加依赖**（pom.xml）

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-discovery</artifactId>
</dependency>

<!-- Spring Cloud LoadBalancer（服务发现时必须） -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
```

> **LoadBalancer 的作用**：如果一个服务注册了 3 个实例，LoadBalancer 用轮询策略从 3 个 IP 中选一个。它是 Nacos 服务发现的标准搭档。

**第二步：配置 Nacos 地址**（application.yml）

```yaml
spring:
  application:
    name: product-service # 服务名 = Nacos 中的注册名
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        namespace: # 留空 = public 命名空间
        group: DEFAULT_GROUP

server:
  port: 8082
```

**第三步：启动类加注解**

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient    // ← 告诉 Spring Cloud："我要注册到 Nacos"
public class ProductServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
```

启动应用后，查看 Nacos 控制台 → 服务列表，你会看到 `product-service` 出现在列表中。对其他两个服务重复以上步骤，完成后 Nacos 中会有三个服务：

```
Nacos 服务列表
─────────────────────
服务名              实例数  健康实例
user-service        1       1
product-service     1       1
order-service       1       1
```

### 2.4 服务发现：用服务名替代 IP

注册完成后，`order-service` 如何调用 `product-service`？不再是写死 IP，而是用**服务名**。

在 Spring Cloud 中，`RestTemplate` 配合 `@LoadBalanced` 即可实现服务名调用：

```java
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced    // ← 关键：让 RestTemplate 拥有服务名解析能力
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
```

使用服务名替代 IP 调用：

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final RestTemplate restTemplate;

    public ProductDTO getProduct(Long productId) {
        // ❌ 旧方式：http://192.168.1.100:8082/products/42
        // ✅ 新方式：用服务名 product-service
        String url = "http://product-service/products/" + productId;
        return restTemplate.getForObject(url, ProductDTO.class);
    }
}
```

`@LoadBalanced` 背后发生了什么：

```
restTemplate.getForObject("http://product-service/products/42", ...)
    │
    ▼
Spring Cloud LoadBalancer 拦截请求
    │
    ├── 向 Nacos 查询 "product-service" 的实例列表
    │   → [172.17.0.4:8082, 172.17.0.5:8082, 172.17.0.6:8082]
    │
    ├── 通过负载均衡策略选一个（默认轮询）
    │   → 172.17.0.5:8082
    │
    └── 将 URL 替换为真实地址
        → http://172.17.0.5:8082/products/42
```

> **RestTemplate 不是最终方案**。虽然能工作，但 URL 拼接仍然啰嗦。§3 将引入 OpenFeign——声明式 HTTP 客户端，让远程调用像本地方法调用一样简洁。

### 2.5 服务发现的完整请求链路

```
order-service 启动
    │
    ├──①→ 向 Nacos 注册："我是 order-service，地址 172.17.0.5:8083"
    │      注册后每 5 秒发一次心跳
    │
product-service 启动
    │
    └──②→ 向 Nacos 注册："我是 product-service，地址 172.17.0.4:8082"

用户请求到达 order-service
    │
    ├──③→ order-service 需要调用 product-service
    │      restTemplate.getForObject("http://product-service/products/42")
    │
    ├──④→ LoadBalancer 向 Nacos 查询 "product-service" 的实例列表
    │      Nacos 返回：[172.17.0.4:8082]（只有健康实例）
    │
    ├──⑤→ LoadBalancer 选一个实例 → 172.17.0.4:8082
    │
    └──⑥→ 发起真实 HTTP 请求 → http://172.17.0.4:8082/products/42
           │
           └──→ product-service 处理请求，返回数据
```

### 2.6 本节回顾

```
问题                      解决方案                    去除的痛点
────                      ────────                    ──────────
服务地址写死        →    服务启动时注册到 Nacos        IP 变更不用改代码
单点调用            →    LoadBalancer 从实例列表选一个  自动负载均衡
实例挂了不知道      →    Nacos 心跳检测 + 自动剔除      故障实例自动摘除
手动维护地址簿      →    所有服务找 Nacos 查询         零人工维护
```

> **接下来**：RestTemplate + 服务名虽然解决了地址发现问题，但每次调用都要拼接 URL + 手动类型转换。§3 引入 OpenFeign——声明式 HTTP 客户端，让你像写接口一样写远程调用。

---

## 3. 远程服务调用 — OpenFeign

§2 用 `RestTemplate` + `@LoadBalanced` 实现了服务名调用。能用，但不好用。本节引入声明式 HTTP 客户端 OpenFeign，彻底消灭 URL 拼接和手动类型转换。

### 3.0 问题：RestTemplate 的局限

回顾 §2.4 的代码：

```java
// ❌ RestTemplate 调用：手写 URL、手动类型转换、占位符容易错位
String url = "http://product-service/products/" + productId;
ProductDTO product = restTemplate.getForObject(url, ProductDTO.class);

// 传查询参数更啰嗦：
String url = "http://product-service/products?name={name}&minPrice={minPrice}";
ProductDTO[] products = restTemplate.getForObject(
    url, ProductDTO[].class, name, minPrice);
```

痛点总结：

- **URL 拼接**：手写字符串，路径容易拼错
- **手动类型转换**：每次指定 `.class`
- **占位符绑定**：`{name}` 和参数位置必须严格对应
- **不可复用**：每个调用方都要写相同的模板代码

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

> **前端类比**：Feign 接口 ≈ TypeScript API 类型定义。你在前端写 `getUser(id: string): Promise<User>`，Feign 让你在后端写 `ProductDTO getProduct(Long id)`。这是和 `MongoRepository`（声明 `findByName` 自动生成查询）同一套哲学：**声明代替实现**。

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
@EnableDiscoveryClient
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

**对比**：

```java
// RestTemplate：3 行，字符串 URL，手动类型转换
String url = "http://product-service/products/" + productId;
ProductDTO product = restTemplate.getForObject(url, ProductDTO.class);

// Feign：1 行，类型安全，方法名即意图
ProductDTO product = productClient.getProduct(productId);
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

### 3.5 本节回顾

```
RestTemplate                        OpenFeign
───────────                        ────────
手写 URL 拼接                →     接口 + 注解声明
手动类型转换                  →     自动根据泛型反序列化
参数占位符绑定                →     @PathVariable/@RequestParam
不可复用                      →     接口可被多个 Service 注入
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

### 4.3 自定义 GlobalFilter：网关鉴权

```java
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class AuthFilter implements GlobalFilter, Ordered {

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
            return exchange.getResponse().setComplete();  // 直接返回 401
        }

        // 验证 JWT → 提取用户 ID → 写入 Header 传给下游
        exchange = exchange.mutate()
                .request(r -> r.header("X-User-Id", "extracted-user-id"))
                .build();

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -1;  // 数字越小越先执行
    }
}
```

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

### 4.5 本节回顾

```
没有网关                      有了网关
────────                      ────────
前端知道所有服务地址     →    前端只知道 http://localhost:8080
每个服务配 CORS          →    网关配一次
每个服务做认证           →    网关统一认证
服务拓扑暴露             →    内网服务不需公网 IP
```

---

## 5. 统一配置管理 — Nacos Config

### 5.0 问题：散落的配置文件

3 个服务 × 2 个环境 = 6 个 application.yml。Nacos 地址变了？改 3 个文件，打包，部署。遗漏任何一个 = 连不上注册中心。

### 5.1 Nacos Config 工作模型

```
        ① 启动时拉取配置
  ┌──────────────────────────┐
  │                          ▼
┌─┴──────────┐     ┌─────────────────┐
│user-service│     │   Nacos Config   │
└────────────┘     │ ┌─────────────┐ │
                   │ │order-service-dev.yml    │ │
        ② 变更通知 │ │product-service-dev.yml  │ │
  ◄────────────────│ │shared-common.yml       │ │
      （长轮询）     │ └─────────────┘ │
                   └─────────────────┘
```

**Data ID 命名规则**：

```
${spring.application.name}-${spring.profiles.active}.${file-extension}

  order-service-dev.yml → order-service + dev + yml
```

### 5.2 三步接入

**第一步：添加依赖**

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-bootstrap</artifactId>
</dependency>
```

**第二步：bootstrap.yml**（比 application.yml 更早加载）

```yaml
spring:
  application:
    name: order-service
  cloud:
    nacos:
      config:
        server-addr: localhost:8848
        file-extension: yml
```

**第三步：配置写入 Nacos 控制台**

```yaml
# Nacos → 配置管理 → 新建配置
# Data ID: order-service-dev.yml

server:
  port: 8083
spring:
  datasource:
    url: jdbc:mysql://localhost:3309/order_db
    username: root
    password: root123
order:
  max-items-per-order: 100
  shipping-fee: 15.0
```

### 5.3 @RefreshScope：热刷新

`@RefreshScope` 的原理：Spring 为带此注解的 Bean 创建代理。Nacos 通知配置变更 → 代理销毁旧 Bean → 下次访问创建新 Bean（读到新值）。

```java
@Service
@RefreshScope   // ← Nacos 配置变更 → Bean 自动重建 → 读到新值
public class OrderService {

    @Value("${order.max-items-per-order:50}")
    private int maxItemsPerOrder;

    public void validateOrder(OrderRequest request) {
        if (request.getItems().size() > maxItemsPerOrder) {
            throw new BusinessException(
                "单笔最多 " + maxItemsPerOrder + " 件");
        }
    }
}
```

**`@RefreshScope` 注意事项**：

- 不要滥用——只在需要热刷新的 Bean 上使用。每个 `@RefreshScope` Bean 都是一个代理，有额外开销
- `@Value` 注入的字段会刷新，但构造器注入的字段不会
- 数据库连接池配置（如 `spring.datasource.url`）不会热刷新——需要重启应用
- 适合刷新的配置：业务开关、阈值、外部服务地址

> **前端类比**：Nacos Config = Vercel / Netlify Environment Variables 面板。你在面板改一个变量，所有部署实例自动生效。

### 5.4 多环境与共享配置

**多环境切换**：

```yaml
# bootstrap.yml
spring:
  profiles:
    active: dev # 改 prod → 加载 order-service-prod.yml
  cloud:
    nacos:
      config:
        shared-configs: # 所有服务共享
          - data-id: shared-common.yml
            group: DEFAULT_GROUP
            refresh: true
```

**命名空间（Namespace）隔离**：

命名空间用于隔离不同环境（dev/test/prod）的配置和服务。在 Nacos 控制台创建命名空间后，拿到命名空间 ID：

```yaml
spring:
  cloud:
    nacos:
      discovery:
        namespace: dev-namespace-id # 服务注册也隔离
      config:
        namespace: dev-namespace-id # 配置也隔离
```

**Group 分组**：

同一命名空间内，可以用 Group 进一步分组（如按业务线）：

```yaml
spring:
  cloud:
    nacos:
      config:
        group: ORDER_GROUP # 订单相关的配置放一个组
```

> **最佳实践**：命名空间做环境隔离（dev/test/prod），Group 做业务隔离。

### 5.5 配置优先级

```
高 ──────────────────────────────────────────── 低
Nacos 共享配置 → Nacos 本服务配置 → application.yml → bootstrap.yml
```

### 5.6 本节回顾

```
本地配置                            Nacos 配置中心
────────                            ─────────────
修改 = 改文件 + 部署           →    Nacos 控制台一点 + 自动热刷新
多环境 = 散落各处的文件        →    集中管理，profiles.active 切换
无版本记录                     →    版本历史 + 一键回滚
```

> **接下来**：服务注册、调用、网关、配置都已就绪。但微服务的真正挑战在于——一个服务出问题，如何不拖垮整个系统？§6 引入 Sentinel 解决这个问题。

---

## 6. 服务容错 — Sentinel

### 6.0 问题：雪崩效应

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

### 6.1 Sentinel 是什么

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

### 6.2 三步接入 Sentinel

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

### 6.3 流量控制：QPS 限流

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

### 6.4 熔断降级：慢调用自动熔断

当被调用方响应变慢时，Sentinel 自动熔断——直接走降级逻辑，不给下游压力：

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

### 6.5 Sentinel 规则类型速览

| 规则类型         | 解决什么问题 | 关键参数                       |
| ---------------- | ------------ | ------------------------------ |
| **流量控制**     | 请求太多     | QPS / 并发线程数阈值           |
| **熔断降级**     | 下游太慢     | 慢调用比例 / 异常比例 / 异常数 |
| **热点参数限流** | 某个商品被刷 | 针对特定参数值限流             |
| **系统规则**     | 整体负载高   | CPU / Load / RT / 入口 QPS     |
| **授权规则**     | 黑白名单     | 来源应用 / IP                  |

### 6.6 本节回顾

```
没有 Sentinel                    有了 Sentinel
─────────────                    ─────────────
一个服务慢，拖垮全部        →    熔断：快速失败，不等待
流量突增打挂服务            →    限流：多余的请求直接拒绝
不知道哪个服务出了问题       →    Dashboard 实时监控 QPS / RT
服务挂了返回 500             →    降级：返回兜底数据，体验不中断
```

> **接下来**：Sentinel 防止了级联故障。但请求跨了 5 个服务，到底慢在哪一层？§7 引入链路追踪回答这个问题。

---

## 7. 分布式链路追踪 — Micrometer Tracing + Zipkin

### 7.0 问题：跨服务请求像黑盒

用户反馈"下单很慢，经常要 5 秒"。你打开日志，发现 3 个服务各有各的日志文件——你无法一眼看出是哪个环节慢了。

```
order-service 日志：          product-service 日志：       user-service 日志：
createOrder start             getProduct start            getUser start
createOrder end (耗时 4.7s)   getProduct end (80ms)       getUser end (60ms)

问题：order-service 总耗时 4.7s，但调用的两个下游都很快（80ms + 60ms）。
慢在哪？可能卡在 order-service 自身的业务逻辑，也可能有未记录的第三方调用。
```

链路追踪的答案是：**给每个请求一个全局唯一 ID，在所有服务间传递，串联起完整的调用链**。

### 7.1 核心概念：Trace ID 与 Span ID

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

> **前端类比**：Trace ID 就像是 Chrome DevTools Network 面板中一次页面加载的"请求组"。你看到 `/api/orders` 花了 2.3s，点开看到它内部发起了 `/api/products`（2.1s）和 `/api/users`（0.2s）。Zipkin 就是跨服务的 Network 面板——同一 Trace ID 把所有相关请求串在一起。

### 7.2 三步接入

**第一步：添加依赖**（每个服务都加）

```xml
<!-- Micrometer Tracing：创建 Trace/Span -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>

<!-- 将 Trace 数据上报到 Zipkin -->
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

**第二步：配置**（application.yml，每个服务配）

```yaml
management:
  tracing:
    sampling:
      probability: 1.0 # 100% 采样（生产环境建议 0.1）
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
```

**第三步：启动 Zipkin Server**

```bash
docker run -d --name zipkin -p 9411:9411 \
  openzipkin/zipkin:latest
```

**不需要写任何代码**。Spring Boot 自动为每个 HTTP 请求创建 Span，Feign 调用自动传播 Trace ID。

### 7.3 Zipkin UI 解读

访问 `http://localhost:9411`，点击"Run Query"查看最近的调用链：

```
┌─────────────────────────────────────────────────────────┐
│ Zipkin UI                                                │
│                                                         │
│ 调用链列表：                                              │
│ ┌─────────────────────────────────────────────────────┐ │
│ │ order-service: POST /orders             4.723s      │ │
│ │   ├── product-service: GET /products/42   82ms      │ │
│ │   │   └── mysql: SELECT                     45ms    │ │
│ │   └── user-service: GET /users/1          61ms      │ │
│ │       └── mysql: SELECT                     38ms    │ │
│ └─────────────────────────────────────────────────────┘ │
│                                                         │
│ 点击任意 Span 查看详情：                                   │
│   请求头、响应码、耗时、自定义 tag、所在服务 IP             │
└─────────────────────────────────────────────────────────┘
```

> **排查技巧**：找到最慢的那个 Span，点击查看它所在的服务实例 IP 和耗时明细。如果数据库查询慢，考虑加索引；如果 Feign 调用慢，检查网络或下游负载。

### 7.4 自定义 Span：标记关键业务步骤

除了框架自动创建的 Span，你还可以在关键业务逻辑中手动创建 Span：

```java
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.Span;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final Tracer tracer;    // 注入 Micrometer Tracer

    public void createOrder(OrderRequest request) {
        // 在关键业务步骤创建 Span
        Span validateSpan = tracer.nextSpan().name("validate-order").start();
        try (var ws = tracer.withSpan(validateSpan)) {
            // 校验库存
            validateStock(request);
            // 校验余额
            validateBalance(request);
        } finally {
            validateSpan.end();
        }

        // 正常业务逻辑（框架自动创建 Span）
        orderMapper.insert(order);
    }
}
```

这样在 Zipkin 中，你不仅能看到 HTTP 调用和数据库查询，还能看到 `validate-order` 这个自定义 Span——精准定位业务逻辑的耗时分布。

### 7.4 本节回顾

```
没有链路追踪                          有了链路追踪
────────────                          ────────────
找不到慢在哪一层              →       Zipkin 一眼看出瓶颈 Span
多个服务的日志无法串联        →       同一个 Trace ID 贯穿全链路
不知道一次请求跨了多少服务     →       拓扑图自动展示调用关系
```

> **接下来**：链路追踪让你看清调用关系。但还有一个更根本的问题——跨服务的数据一致性怎么保证？§8 引入 Seata 解决分布式事务。

---

## 8. 分布式事务 — Seata

### 8.0 问题：@Transactional 失效了

在单体中，下单操作是这样的：

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
order-service 的 order_db         product-service 的 product_db
┌──────────────┐                  ┌──────────────┐
│ orders 表    │                  │ products 表  │
│ INSERT ✓     │                  │ UPDATE stock │
└──────────────┘                  └──────────────┘

         两个独立的数据库 = 两个独立的事务

问题场景：
  ① INSERT orders → 成功（order_db 提交）
  ② UPDATE stock → 失败（商品库存不足）
                      ↓
  order_db 已经提交了 → 无法回滚 ← @Transactional 管不到另一个数据库
```

### 8.1 Seata AT 模式原理

Seata（Simple Extensible Autonomous Transaction Architecture）是阿里巴巴开源的分布式事务解决方案。AT 模式对业务代码侵入最小——只需一个注解 `@GlobalTransactional`。

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
```

**AT 模式两阶段（简化理解）**：

```
阶段一：执行 + 记录 Undo Log
─────────────────────────────
① order-service 执行 INSERT → 记录 Undo Log（如何删除这条记录）
② product-service 执行 UPDATE → 记录 Undo Log（如何恢复原库存值）
③ user-service 执行 UPDATE → 记录 Undo Log（如何恢复原余额值）

全部成功 → 提交（删除 Undo Log）
任一失败 → 回滚（根据 Undo Log 反向执行恢复）

阶段二：提交或回滚
─────────────────
TC 收集所有 RM 的结果：
  • 全部成功 → 全局提交（异步删除 Undo Log）
  • 任一失败 → 全局回滚（各 RM 根据 Undo Log 恢复数据）
```

### 8.2 三步接入 Seata

**第一步：启动 Seata Server**（Docker）

```bash
docker run -d --name seata-server -p 8091:8091 -p 7091:7091 \
  -e SEATA_PORT=8091 \
  seataio/seata-server:2.5.0
```

**第二步：每个服务加依赖**

```xml
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-seata</artifactId>
</dependency>
```

**第三步：配置**（每个服务的 application.yml）

```yaml
seata:
  tx-service-group: my_tx_group # 事务分组名
  service:
    vgroup-mapping:
      my_tx_group: default # 映射到 Seata Server 集群
    grouplist:
      default: localhost:8091
```

### 8.3 @GlobalTransactional：一行注解

```java
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final ProductClient productClient;
    private final UserClient userClient;

    @GlobalTransactional    // ← 替代 @Transactional
    public void createOrder(OrderRequest request) {
        // ① 创建订单（本服务数据库）
        orderMapper.insert(order);

        // ② 扣减库存（Feign 调用 product-service）
        productClient.deductStock(
            request.getProductId(), request.getQuantity());

        // ③ 扣减余额（Feign 调用 user-service）
        userClient.deductBalance(
            request.getUserId(), request.getTotalAmount());
    }
}
```

任何一步失败 → Seata 自动回滚前面所有的数据库操作。

### 8.4 最终一致性思想

不是所有跨服务操作都需要强一致性（Seata AT 模式）。很多场景下，**最终一致性**就够了：

```
强一致性（Seata AT）              最终一致性（消息驱动）
─────────────────                ────────────────────
下单 + 扣库存必须同时成功          下单成功后，发消息异步扣库存
或同时失败                         扣库存失败？重试或补偿

适合：金融交易、库存扣减            适合：发短信通知、生成报表
```

> **关键判断**：如果你能用"重试 + 补偿"解决的不一致，就别引入分布式事务。分布式事务是最后的手段，不是第一选择。

### 8.5 本节回顾

```
没有 Seata                         有了 Seata
─────────                          ────────
@Transactional 管一个库       →    @GlobalTransactional 协调多个库
跨服务失败 = 数据不一致       →    自动回滚所有分支事务
回滚代码散落各处              →    一个注解，声明式回滚
```

> **接下来**：分布式事务解决了数据一致性问题。但整个微服务系统的安全怎么做？认证该放在哪里？§9 介绍微服务中的安全方案。

---

## 9. 安全 — 微服务中的认证授权

### 9.0 问题：认证该放在哪里

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

### 9.1 网关统一认证流程

```
用户请求（Authorization: Bearer eyJ...）
    │
    ▼
Gateway AuthFilter（§4.3 已实现）
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

### 9.2 下游服务如何获取当前用户

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

### 9.3 Feign 调用时 Token 透传

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

### 9.4 下游服务的 SecurityContext 适配

网关验证了 JWT，但下游服务怎么让 Spring Security 认识这个用户？答案是写一个 Filter 从 `X-User-Id` 构建 `SecurityContext`：

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
            // 信任网关已认证 → 构建 Authentication
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

本指南不重复 `spring-security-guide.md` 的内容。阅读顺序建议：

```
① spring-security-guide.md（单体 Security）
   掌握：JWT 生成/验证、SecurityFilterChain、@PreAuthorize、SecurityContextHolder

② 本节点 §9（微服务 Security）
   掌握：认证放在网关、Token 透传、与单体 Security 的差异

③ 实战：网关 + Security 整合
   网关 AuthFilter 验签 JWT → 写入 X-User-Id
   下游服务用 Spring Security 读取 X-User-Id → 设置 SecurityContext
   → Controller 中 @PreAuthorize 正常工作
```

### 9.5 本节回顾

```
单体安全                             微服务安全
────────                             ────────
Spring Security 直接验 JWT      →    网关统一验签
SecurityContext 在本地管理       →    X-User-Id Header 跨服务传递
每个方法可 @PreAuthorize       →    下游仍可用（需适配 SecurityContext）
```

---

## 10. 延伸阅读：消息驱动 — Spring Cloud Stream + RocketMQ

前 9 章覆盖了微服务的同步通信模式（请求-响应）。但很多场景用异步通信更合适：

```
同步调用（OpenFeign）              异步消息（RocketMQ）
─────────────────                  ──────────────────
订单服务调用库存服务 → 等待响应      订单服务发消息 → 继续处理下一个请求
                                     库存服务收到消息 → 异步扣库存

✅ 实时性强                         ✅ 解耦：生产者和消费者互不知道
❌ 调用方阻塞等待                    ✅ 削峰：消息队列缓冲突发流量
❌ 下游挂了调用方也受影响             ✅ 最终一致性：消费者失败可重试
```

**典型异步场景**：

- 下单成功后发短信通知（不等短信发送结果）
- 用户注册后初始化账户数据
- 日志收集、数据同步

**Spring Cloud Stream** 是 Spring 对消息中间件的抽象层——你面向统一的 API 编程，底层切换 RocketMQ / Kafka / RabbitMQ 无需改代码：

```java
// 生产者：发消息
@Autowired
private StreamBridge streamBridge;

public void afterOrderCreated(Order order) {
    streamBridge.send("order-output", order);
}

// 消费者：收消息
@Bean
public Consumer<Order> orderConsumer() {
    return order -> {
        smsService.sendNotification(order.getUserId());
    };
}
```

> **下一步**：Spring Cloud Stream 是独立主题，本指南不做深入。掌握前 9 章的同步通信后，异步消息是自然的进阶方向。

---

## 11. 实战决策

### 11.1 什么时候该拆？什么时候不该拆？

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
  □ 引入 Zipkin（链路追踪）

第二步：拆数据（最难的也是最重要的）
  □ 每个模块独立数据库
  □ 原单体中的跨表 JOIN → 改为 Feign 调用 + 数据组装
  □ 为分布式事务做准备（识别哪些操作需要跨库一致性）

第三步：拆服务（按业务边界）
  □ 先拆变更最频繁的模块（减少部署耦合）
  □ 再拆资源消耗最大的模块（独立扩容）
  □ 最后拆核心模块（最了解业务后再动）

第四步：加防御
  □ 所有 Feign 调用加 Sentinel fallback
  □ 引入 Seata 处理跨库事务
  □ 完善监控和告警
```

### 11.2 组件选型决策树

```
你需要什么能力？
    │
    ├── 服务发现 → Nacos（推荐）/ Eureka（已停更，不推荐）
    │
    ├── 服务调用 → OpenFeign（同步）/ Spring Cloud Stream（异步）
    │
    ├── 统一入口 → Spring Cloud Gateway（推荐）/ Zuul（已停更）
    │
    ├── 配置管理 → Nacos Config（与注册中心同一套）/ Apollo（携程）
    │
    ├── 服务容错 → Sentinel（推荐）/ Resilience4j（国际站常用）
    │
    ├── 链路追踪 → Micrometer Tracing + Zipkin（轻量）
    │              / SkyWalking（重量级，功能更全）
    │
    ├── 分布式事务 → Seata AT（强一致）/ RocketMQ 最终一致性
    │
    └── 消息队列 → RocketMQ（阿里系首选）/ Kafka（大数据场景）/ RabbitMQ
```

### 11.3 10 个常见反模式

| #   | 反模式           | 问题                                       | 正确做法                                         |
| --- | ---------------- | ------------------------------------------ | ------------------------------------------------ |
| 1   | 拆得太细         | 一个功能 3 个服务，调试地狱                | 先按业务边界拆（用户/商品/订单），不过早按技术拆 |
| 2   | 共享数据库       | 所有服务连同一个库 → 单体换皮              | 每个服务独立数据库，通过 API 通信                |
| 3   | 分布式事务滥用   | 发通知也用 Seata，性能骤降                 | 能用最终一致性就别用强一致（§8.4）               |
| 4   | 没有熔断         | 一个服务挂了，全链路雪崩                   | 所有 Feign 调用必须有 fallback（§6.4）           |
| 5   | 没有链路追踪     | 出问题不知道看哪个服务的日志               | 接入 Micrometer Tracing + Zipkin（§7）           |
| 6   | 配置硬编码       | Nacos 地址写在 application.yml             | 用 Nacos Config 集中管理（§5）                   |
| 7   | 网关做业务逻辑   | Gateway 里写订单校验 → 网关变成新单体      | 网关只做路由 + 认证 + 限流，业务逻辑在服务中     |
| 8   | 没有统一响应格式 | 3 个服务返回 3 种 JSON 格式 → 前端适配地狱 | 参考你已有的 GlobalResponseBodyAdvice 模式统一   |
| 9   | 同步调用链过长   | A → B → C → D，一次请求串行等 4 个服务     | 能并行的并行，能异步的异步                       |
| 10  | 不做幂等         | 网络重试导致订单重复创建                   | 订单号做唯一索引，INSERT 前查重                  |

---

## 12. 速查清单

### 12.1 依赖坐标速查

```xml
<!-- ========== 父 POM（统一版本管理） ========== -->
<dependencyManagement>
    <dependencies>
        <!-- Spring Cloud BOM -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2025.1.2</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <!-- Spring Cloud Alibaba BOM -->
        <dependency>
            <groupId>com.alibaba.cloud</groupId>
            <artifactId>spring-cloud-alibaba-dependencies</artifactId>
            <version>2025.1.0.0</version>
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

<!-- Nacos 配置中心（bootstrap.yml 也需要） -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-nacos-config</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-bootstrap</artifactId>
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

<!-- Seata 分布式事务 -->
<dependency>
    <groupId>com.alibaba.cloud</groupId>
    <artifactId>spring-cloud-starter-alibaba-seata</artifactId>
</dependency>

<!-- 链路追踪 -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>

<!-- ========== Gateway 专用（替代 spring-boot-starter-webmvc） ========== -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway</artifactId>
</dependency>
```

### 12.2 注解速查

| 注解                       | 位置               | 作用             | 章节 |
| -------------------------- | ------------------ | ---------------- | ---- |
| `@EnableDiscoveryClient`   | 启动类             | 注册到 Nacos     | §2.3 |
| `@EnableFeignClients`      | 启动类             | 扫描 Feign 接口  | §3.2 |
| `@FeignClient(name="xxx")` | 接口               | 声明远程服务调用 | §3.2 |
| `@LoadBalanced`            | RestTemplate Bean  | 服务名 → IP 解析 | §2.4 |
| `@RefreshScope`            | Service/Controller | 配置热刷新       | §5.3 |
| `@SentinelResource`        | Controller 方法    | 声明限流/降级    | §6.3 |
| `@GlobalTransactional`     | Service 方法       | 分布式事务       | §8.3 |
| `@SpringBootApplication`   | 启动类             | Spring Boot 标配 | —    |
| `@RestController`          | Controller         | REST API         | —    |
| `@Service`                 | Service            | 业务逻辑         | —    |

### 12.3 配置项速查

```yaml
# ========== Nacos Discovery ==========
spring:
  application:
    name: my-service               # 服务名 = Nacos 中的注册名
  cloud:
    nacos:
      discovery:
        server-addr: localhost:8848
        namespace:                  # 命名空间 ID
        group: DEFAULT_GROUP

# ========== Nacos Config ==========
spring:
  cloud:
    nacos:
      config:
        server-addr: localhost:8848
        file-extension: yml
        shared-configs:             # 共享配置
          - data-id: shared.yml
            group: DEFAULT_GROUP

# ========== Sentinel ==========
spring:
  cloud:
    sentinel:
      transport:
        dashboard: localhost:8090
        port: 8719
      eager: true

# ========== Gateway ==========
spring:
  cloud:
    gateway:
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

# ========== Tracing ==========
management:
  tracing:
    sampling:
      probability: 1.0
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans

# ========== Seata ==========
seata:
  tx-service-group: my_tx_group
  service:
    vgroup-mapping:
      my_tx_group: default
    grouplist:
      default: localhost:8091
```

### 12.4 Docker Compose 速查

```yaml
# 完整 docker-compose.yml（§1.4）
services:
  nacos: # 注册中心 + 配置中心 → :8848
  mysql-user: # 用户数据库 → :3307
  mysql-product: # 商品数据库 → :3308
  mysql-order: # 订单数据库 → :3309
  sentinel: # 流量控制台 → :8090
  zipkin: # 链路追踪 → :9411
  seata: # 分布式事务 → :8091
```

### 12.5 服务端口速查

```
Gateway         :8080     ← 前端唯一入口
user-service    :8081     ← MySQL user_db :3307
product-service :8082     ← MySQL product_db :3308
order-service   :8083     ← MySQL order_db :3309
Nacos           :8848     ← 注册中心 + 配置中心
Sentinel        :8090     ← 流量控制台
Zipkin          :9411     ← 链路追踪 UI
Seata           :8091     ← 分布式事务协调器
```

### 12.6 调用链路速查

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
  │ @GlobalTransactional（Seata 全局事务开始）
  │
  ├──[Feign]→ product-service (:8082)
  │   │ GET /products/42 → 查询商品信息
  │   │ PUT /products/42/stock → 扣减库存
  │   │ [Sentinel 熔断保护]
  │   │ [Trace ID 自动传播]
  │
  ├──[Feign]→ user-service (:8081)
  │   │ GET /users/1 → 查询用户信息
  │   │ PUT /users/1/balance → 扣减余额
  │   │ [Sentinel 熔断保护]
  │   │ [Trace ID 自动传播]
  │
  │ INSERT INTO orders → 创建订单
  │
  │ @GlobalTransactional 结束 → Seata 提交/回滚
  ▼
返回统一响应 {code, message, data}

在 Zipkin (:9411) 中可以查看完整 Trace
在 Sentinel (:8090) 中可以看到 QPS/RT 实时数据
在 Nacos (:8848) 中可以看到所有服务在线状态
```

---

> **学习路线建议**：
>
> 1. **快速体验**（1 小时）：启动 Docker Compose → 创建 user/product/order 三个空服务 → 配好 Nacos 注册发现 → 写一个 Feign 调用 → 浏览器访问通过 Gateway 路由。
> 2. **系统学习**（1 天）：按本指南 §0 → §9 顺序阅读。每读完一章，在项目里实践对应的功能。
> 3. **进阶深入**（1 周）：添加 Sentinel 规则、接入 Zipkin、尝试 Seata 分布式事务。给每个 Feign 调加 fallback。
> 4. **生产准备**（持续）：安全加固（JWT 密钥管理）、监控告警（Prometheus + Grafana）、CI/CD 流水线、容器化部署。
>
> **关联阅读**：
>
> - [Spring Security 指南](spring-security-guide.md) — 单体 + 微服务安全
> - [Spring 异常处理指南](spring-exception-guide.md) — 统一响应格式
> - [Spring Filter/Interceptor 指南](spring-filter-interceptor-guide.md) — Filter 链深入理解（Gateway Filter 的基础）
> - [Spring Validation 指南](spring-validation-guide.md) — DTO 校验
> - [Spring Transaction 指南](spring-transaction-guide.md) — 本地事务（Seata 分布式事务的前置知识）
