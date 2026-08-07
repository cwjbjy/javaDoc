# Spring Cloud 微服务企业级指南

> 本指南系统介绍 Spring Cloud 微服务完整知识体系。先建立分布式认知（为什么需要微服务、全景架构），再逐类深入服务发现、远程调用、网关、配置中心、熔断降级、链路追踪、分布式事务、安全。每章一个问题驱动，同一「电商三服务」场景贯穿全文。
>
> 当前项目基线：Spring Boot 3.5.14 / Spring Cloud 2025.0.0 / Spring Cloud Alibaba 2025.0.0.0 / Nacos Client 3.0.3 / Java 17。后续新增组件时必须先验证它们与这组依赖的兼容性。
>
> 面向读者：已掌握 Spring Boot 单体开发（IoC/DI、MVC、Security、数据访问、异常处理），准备学习微服务的开发者。如果你是前端出身，指南中嵌入了前端类比帮你快速建立直觉。

---

## 目录

1. [全景图：Spring Cloud 微服务完整架构](#1-全景图spring-cloud-微服务完整架构)
   - [1.1 一张图看懂所有组件](#10-一张图看懂所有组件)
   - [1.2 版本兼容矩阵](#11-版本兼容矩阵)
   - [1.3 贯穿场景：电商三服务](#13-贯穿场景电商三服务)
   - [1.4 Docker Compose 一键部署](#14-docker-compose-一键部署)
   - [1.5 数据库迁移：Flyway](#15-数据库迁移flyway)
2. [Nacos：服务注册、发现与统一配置](#2-nacos服务注册发现与统一配置)
   - [2.1 本项目完整启动链路](#21-本项目完整启动链路)
   - [2.2 启动与配置导入](#22-启动与配置导入)
   - [2.3 每个应用的最小本地配置](#23-每个应用的最小本地配置)
   - [2.4 Nacos 中的服务配置](#24-nacos-中的服务配置)
   - [2.5 依赖范围](#25-依赖范围)
   - [2.6 客户端负载均衡 — Spring Cloud LoadBalancer](#26-客户端负载均衡--spring-cloud-loadbalancer)
   - [2.7 注册与发现](#27-注册与发现)
   - [2.8 本章回顾](#28-本章回顾)
3. [远程服务调用 — OpenFeign](#3-远程服务调用--openfeign)
4. [API 网关 — Spring Cloud Gateway](#4-api-网关--spring-cloud-gateway)
5. [服务容错 — Sentinel](#5-服务容错--sentinel)
6. [分布式链路追踪 — Micrometer Tracing + Zipkin](#6-分布式链路追踪--micrometer-tracing--zipkin)
7. [分布式事务 — Seata](#7-分布式事务--seata)
8. [安全 — 微服务中的认证授权](#8-安全--微服务中的认证授权)
9. [延伸阅读：消息驱动](#9-延伸阅读消息驱动)
10. [实战决策](#10-实战决策)
11. [速查清单](#11-速查清单)

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
              服务注册 / 配置拉取 / 心跳上报 / 链路数据上报
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
  ┌─────▼─────┐  ┌───────────▼──┐  ┌──────────────▼──┐
  │   Nacos   │  │   Sentinel   │  │     Zipkin      │
  │ 服务注册  │  │ 流量控制     │  │ 链路追踪可视化   │
  │ 配置中心  │  │ 熔断降级     │  │                  │
  └───────────┘  └──────────────┘  └──────────────────┘
        │
  ┌─────▼─────┐
  │   Seata   │
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

Spring Cloud 是版本敏感型生态。Spring Boot、Spring Cloud、Spring Cloud Alibaba 三者必须使用同一兼容组合。以下是当前 `microservice-demo` 已通过 Maven 构建验证的基线：

```
Spring Boot          3.5.14          ← 当前项目基座
    │
    └── Spring Cloud  2025.0.0        ← 当前项目导入的 BOM
           │
           └── Spring Cloud Alibaba  2025.0.0.0  ← 当前项目导入的 BOM
                   │
                   ├── Nacos Client   3.0.3
                   ├── Sentinel       1.8.9
                   └── Seata          2.5.0
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
链路追踪             Micrometer Tracing + Zipkin            Micrometer Tracing + Zipkin
消息驱动             RabbitMQ / Kafka                       RocketMQ ★（阿里系）
分布式事务           自研 / Saga 模式                       Seata（AT 模式） ★
```

> 标 ★ 的是 Spring Cloud Alibaba 组件。2019 年 Netflix 宣布技术栈进入维护模式后，国内企业大规模从 Eureka + Hystrix 迁移到 Nacos + Sentinel——阿里系组件在双十一级别场景下久经考验，中文社区活跃，且提供注册 + 配置 + 熔断 + 事务的一站式方案，无需拼凑多个项目。本指南因此选择 Spring Cloud Alibaba 作为核心依赖。

### 1.3 贯穿场景：电商三服务

全文所有代码示例围绕同一个电商场景展开。三个服务、三个数据库、一条核心调用链：

```
用户下单的请求链路（Seata 分布式事务）
─────────────────────────────────────

前端 POST /api/orders（userId=1, productId=42, quantity=2）
    │
    ▼
Gateway (:8080)
    │ Path=/api/orders/** → 路由到 order-service
    ▼
order-service (:8083)                      ← @GlobalTransactional
    │
    ├──→ Feign 调用 product-service (:8082)
    │    查询商品信息、扣减库存            ← 分支事务①
    │
    ├──→ Feign 调用 user-service (:8081)
    │    查询用户信息、扣减余额            ← 分支事务②
    │
    └──→ 写入 orders 表（MySQL order-db） ← 分支事务③
    │
    ▼
Seata TC 协调：全部成功 → 提交，任一失败 → 全部回滚
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
> **首期范围**：当前 Compose 只启动 Nacos、三个 MySQL 和 `nacos-init`。Sentinel、Zipkin、Seata 是本指南后续章节的学习主题，暂未接入当前项目；需要学习对应章节时，再按该章节的兼容版本和独立 Compose 配置添加。

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

### 2.8 本章回顾

- nacos-init 导入配置，Java 应用启动后才注册实例。
- 本地 application.yml 保存应用名、Nacos 地址与 spring.config.import。
- Nacos YAML 保存端口、数据源与 Gateway 路由。
- Discovery 解决“实例在哪里”；LoadBalancer 只由当前调用方模块使用。

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

### 3.5 本节回顾

```
RestClient                          OpenFeign
─────────                          ────────
手写 URI + 链式调用          →     接口 + 注解声明
手动类型转换                  →     自动根据泛型反序列化
参数手动构造                  →     @PathVariable/@RequestParam
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

GlobalFilter 是 Gateway 的拦截器——每个请求都会经过。它有三个核心能力：**白名单放行**、**修改请求**、**拒绝请求**。完整的 JWT 鉴权实现见 §8，这里只看 Filter 骨架：

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

### 5.6 本节回顾

```
没有 Sentinel                    有了 Sentinel
─────────────                    ─────────────
一个服务慢，拖垮全部        →    熔断：快速失败，不等待
流量突增打挂服务            →    限流：多余的请求直接拒绝
不知道哪个服务出了问题       →    Dashboard 实时监控 QPS / RT
服务挂了返回 500             →    降级：返回兜底数据，体验不中断
```

> **接下来**：Sentinel 防止了级联故障。但请求跨了 5 个服务，到底慢在哪一层？§6 引入链路追踪回答这个问题。

---

## 6. 分布式链路追踪 — Micrometer Tracing + Zipkin

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

> **前端类比**：Trace ID 就像是 Chrome DevTools Network 面板中一次页面加载的"请求组"。你看到 `/api/orders` 花了 2.3s，点开看到它内部发起了 `/api/products`（2.1s）和 `/api/users`（0.2s）。Zipkin 就是跨服务的 Network 面板——同一 Trace ID 把所有相关请求串在一起。

### 6.2 三步接入

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

### 6.3 Zipkin UI 解读

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

### 6.4 自定义 Span：标记关键业务步骤

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

### 6.4 本节回顾

```
没有链路追踪                          有了链路追踪
────────────                          ────────────
找不到慢在哪一层              →       Zipkin 一眼看出瓶颈 Span
多个服务的日志无法串联        →       同一个 Trace ID 贯穿全链路
不知道一次请求跨了多少服务     →       拓扑图自动展示调用关系
```

> **接下来**：链路追踪让你看清调用关系。但还有一个更根本的问题——跨服务的数据一致性怎么保证？§7 引入 Seata 解决分布式事务。

---

## 7. 分布式事务 — Seata

### 7.0 问题：@Transactional 失效了

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

### 7.1 Seata AT 模式原理

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

阶段二：提交或回滚
─────────────────
TC 收集所有 RM 的结果：
  • 全部成功 → 全局提交（异步删除 Undo Log）
  • 任一失败 → 全局回滚（各 RM 根据 Undo Log 恢复数据）
```

### 7.2 三步接入 Seata

**第一步：启动 Seata Server**（Docker）

```bash
docker run -d --name seata-server -p 8091:8091 -p 7091:7091 \
  -e SEATA_PORT=8091 \
  seataio/seata-server:2.5.0
```

**Seata 两个端口的分工**（Seata 2.0 引入）：

| 端口     | 协议                 | 职责                                                                             |
| -------- | -------------------- | -------------------------------------------------------------------------------- |
| **7091** | HTTP                 | 管理控制台（浏览器访问 `http://localhost:7091`），查看事务状态、全局锁、回滚记录 |
| **8091** | TCP（私有 RPC 协议） | 业务通信——TM、RM 与 TC 之间的 RPC 交互                                           |

> **拆分动机**：Seata 1.x 所有流量走 8091。2.0 拆开是为了**安全隔离**——7091（控制台）可限制为仅运维网段访问，8091（RPC）对业务服务开放，互不干扰。

> **与 Nacos 的对比**：Nacos 拆端口是协议升级（HTTP → HTTP/2 gRPC），Seata 拆端口是流量隔离（控制台 vs 业务）。

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

### 7.3 @GlobalTransactional：一行注解

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

### 7.4 最终一致性思想

不是所有跨服务操作都需要强一致性（Seata AT 模式）。很多场景下，**最终一致性**就够了：

```
强一致性（Seata AT）              最终一致性（消息驱动）
─────────────────                ────────────────────
下单 + 扣库存必须同时成功          下单成功后，发消息异步扣库存
或同时失败                         扣库存失败？重试或补偿

适合：金融交易、库存扣减            适合：发短信通知、生成报表
```

> **关键判断**：如果你能用"重试 + 补偿"解决的不一致，就别引入分布式事务。分布式事务是最后的手段，不是第一选择。

### 7.5 本节回顾

```
没有 Seata                         有了 Seata
─────────                          ────────
@Transactional 管一个库       →    @GlobalTransactional 协调多个库
跨服务失败 = 数据不一致       →    自动回滚所有分支事务
回滚代码散落各处              →    一个注解，声明式回滚
```

> **接下来**：分布式事务解决了数据一致性问题。但整个微服务系统的安全怎么做？认证该放在哪里？§8 介绍微服务中的安全方案。

---

## 8. 安全 — 微服务中的认证授权

### 8.0 问题：认证该放在哪里

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

### 8.1 网关统一认证：完整实现

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

### 8.2 下游服务如何获取当前用户

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

### 8.3 Feign 调用时 Token 透传

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

### 8.4 下游服务的 SecurityContext 适配

网关验证了 JWT，但下游服务怎么让 Spring Security 认识这个用户？答案是写一个 Filter 从 `X-User-Id` 构建 `SecurityContext`。

**信任链：网关验证 + 下游信任 = 完整闭环**

下游之所以能"跳过验证"，是因为网关在 §8.1 的 `AuthFilter` 中已经完成了全部验证工作。两端代码的协作关系：

```
§8.1 网关 AuthFilter                     §8.4 下游 GatewayAuthFilter
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

② 本节点 §8（微服务 Security）
   掌握：认证放在网关、Token 透传、与单体 Security 的差异

③ 实战：网关 + Security 整合
   网关 AuthFilter 验签 JWT → 写入 X-User-Id
   下游服务用 Spring Security 读取 X-User-Id → 设置 SecurityContext
   → Controller 中 @PreAuthorize 正常工作
```

### 8.5 本节回顾

```
单体安全                             微服务安全
────────                             ────────
Spring Security 直接验 JWT      →    网关统一验签
SecurityContext 在本地管理       →    X-User-Id Header 跨服务传递
每个方法可 @PreAuthorize       →    下游仍可用（需适配 SecurityContext）
```

---

## 9. 延伸阅读：消息驱动 — Spring Cloud Stream + RocketMQ

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

### 10.2 组件选型决策树

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

### 10.3 10 个常见反模式

| #   | 反模式           | 问题                                       | 正确做法                                         |
| --- | ---------------- | ------------------------------------------ | ------------------------------------------------ |
| 1   | 拆得太细         | 一个功能 3 个服务，调试地狱                | 先按业务边界拆（用户/商品/订单），不过早按技术拆 |
| 2   | 共享数据库       | 所有服务连同一个库 → 单体换皮              | 每个服务独立数据库，通过 API 通信                |
| 3   | 分布式事务滥用   | 发通知也用 Seata，性能骤降                 | 能用最终一致性就别用强一致（§7.4）               |
| 4   | 没有熔断         | 一个服务挂了，全链路雪崩                   | 所有 Feign 调用必须有 fallback（§5.4）           |
| 5   | 没有链路追踪     | 出问题不知道看哪个服务的日志               | 接入 Micrometer Tracing + Zipkin（§6）           |
| 6   | 配置硬编码       | Nacos 地址写在 application.yml             | 用 Nacos Config 集中管理（§2.7）                 |
| 7   | 网关做业务逻辑   | Gateway 里写订单校验 → 网关变成新单体      | 网关只做路由 + 认证 + 限流，业务逻辑在服务中     |
| 8   | 没有统一响应格式 | 3 个服务返回 3 种 JSON 格式 → 前端适配地狱 | 参考你已有的 GlobalResponseBodyAdvice 模式统一   |
| 9   | 同步调用链过长   | A → B → C → D，一次请求串行等 4 个服务     | 能并行的并行，能异步的异步                       |
| 10  | 不做幂等         | 网络重试导致订单重复创建                   | 订单号做唯一索引，INSERT 前查重                  |

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
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
```

### 11.2 注解速查

| 注解                              | 位置                    | 作用                                            | 章节 |
| --------------------------------- | ----------------------- | ----------------------------------------------- | ---- |
| （无需 `@EnableDiscoveryClient`） | 启动类                  | Nacos Discovery 自动注册                        | §2.6 |
| `@EnableFeignClients`             | 启动类                  | 扫描 Feign 接口                                 | §3.2 |
| `@FeignClient(name="xxx")`        | 接口                    | 声明远程服务调用                                | §3.2 |
| `@LoadBalanced`                   | RestClient.Builder Bean | RestClient 场景中服务名 → IP 解析；本项目未使用 | §3.0 |
| `@SentinelResource`               | Controller 方法         | 声明限流/降级                                   | §5.3 |
| `@GlobalTransactional`            | Service 方法            | 分布式事务                                      | §7.3 |
| `@SpringBootApplication`          | 启动类                  | Spring Boot 标配                                | —    |
| `@RestController`                 | Controller              | REST API                                        | —    |
| `@Service`                        | Service                 | 业务逻辑                                        | —    |

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

### 11.4 Docker Compose 速查

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

### 11.5 服务端口速查

```
Gateway         :8080     ← 前端唯一入口
user-service    :8081     ← MySQL user_db :3307
product-service :8082     ← MySQL product_db :3308
order-service   :8083     ← MySQL order_db :3309
Nacos           :8848     ← 注册中心 + 配置中心
Nacos Console   :8084     ← 宿主机端口，映射到容器内 :8080；避免占用 Gateway :8080
Sentinel        :8090     ← 流量控制台
Zipkin          :9411     ← 链路追踪 UI
Seata           :8091     ← 分布式事务协调器
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
> 2. **系统学习**（1 天）：按本指南 §0 → §8 顺序阅读。每读完一章，在项目里实践对应的功能。
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
