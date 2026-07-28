# logstash-logback-encoder 指南

> 本指南循序渐进介绍 logstash-logback-encoder 的使用。从"Spring Boot 默认日志是怎么来的"到"生产可用的 JSON 日志配置"，每步只引入一个新概念，同一"订单服务"场景贯穿全文。
>
> 适用版本：logstash-logback-encoder 9.0，Spring Boot 4.x，Java 17+

---

## 目录

0. [理解日志体系](#0-理解日志体系)
1. [入门三步走](#1-入门三步走)
   - [1.1 第一层：最简配置 — 控制台输出 JSON](#11-第一层最简配置--控制台输出-json)
   - [1.2 第二层：+ MDC — 注入 traceId](#12-第二层-mdc--注入-traceid)
   - [1.3 第三层：+ 生产配置 — 异步 + 环境区分](#13-第三层-生产配置--异步--环境区分)
2. [进阶：LoggingEventCompositeJsonEncoder](#2-进阶loggingeventcompositejsonencoder)
3. [速查清单](#3-速查清单)

---

## 0. 理解日志体系

在讲 logstash-logback-encoder 之前，先看清它在整个日志栈中处于什么位置——否则容易误以为它是个独立的日志框架。

### 三层关系：SLF4J → Logback → logstash-logback-encoder

```
┌──────────────────────────────────────────────────────────────┐
│                      你的 Java 代码                           │
│                                                              │
│   @Slf4j                          ← Lombok 编译期帮你生成    │
│   public class OrderService {                                 │
│       log.info("用户下单成功");    ← 调用 SLF4J API          │
│   }                                                          │
│                                                              │
│   代码只依赖 SLF4J 接口，不感知底层是谁在干活                     │
└────────────────────────┬─────────────────────────────────────┘
                         │ 调用
                         ▼
┌──────────────────────────────────────────────────────────────┐
│                 SLF4J（Simple Logging Facade）                │
│                                                              │
│  日志门面 — 只定义接口，不干活                                   │
│  org.slf4j.Logger / org.slf4j.LoggerFactory                  │
│                                                              │
│  类比：USB 接口标准 — 只规定插头形状，不负责充电                    │
└────────────────────────┬─────────────────────────────────────┘
                         │ 桥接到实现
                         ▼
┌──────────────────────────────────────────────────────────────┐
│                Logback（日志实现框架）                          │
│                                                              │
│  真正干活的 — ch.qos.logback.*                                │
│                                                              │
│  ✅ Spring Boot 自带！                                        │
│     spring-boot-starter-webmvc                               │
│       → spring-boot-starter                                  │
│         → spring-boot-starter-logging                        │
│           → logback-classic (自动引入，无需手动加依赖)         │
│                                                              │
│  类比：USB 充电器 — 真正充电的硬件                                │
└────────────────────────┬─────────────────────────────────────┘
                         │ Encoder 决定输出格式
                         ▼
┌──────────────────────────────────────────────────────────────┐
│           logstash-logback-encoder（Encoder 插件）            │
│                                                              │
│  Logback 生态中的一个 Encoder — 把日志事件编码为 JSON            │
│                                                              │
│  不是独立框架！是"Logback 的轮胎"— 可以换，不影响发动机            │
│                                                              │
│  在 logback-spring.xml 中声明：                                │
│    <encoder class="net.logstash.logback.encoder.              │
│                    LogstashEncoder"/>                        │
│                                                              │
│  效果：原来每行一条文本 → 每行一个 JSON 对象                       │
└──────────────────────────────────────────────────────────────┘
```

**一句话总结**：SLF4J 是接口，Logback 是实现，logstash-logback-encoder 是 Logback 的一个 Encoder 插件——负责"把日志事件序列化成 JSON"这一件事。

### 引入 logstash-logback-encoder

Logback 是 Spring Boot 自带的，但 **logstash-logback-encoder 不是**——它是第三方 Encoder 插件，需要手动添加 Maven 依赖。好在 dependency 很轻：它只依赖 `logback-classic`（已由 Spring Boot 提供）和 Jackson 3.x（自动传递引入），不需要额外配置。

在 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>9.0</version>
</dependency>
```

> **版本说明**：9.0 是当前最新稳定版（已迁移到 Jackson 3.x），支持 Java 17+ 和 Spring Boot 4.x。如果用 Spring Boot 3.x，选 7.x（Jackson 2.x）或 8.x（Jackson 2.x）版本。

加完依赖后，classpath 上就有了 `net.logstash.logback.encoder.LogstashEncoder` 这个类。接下来只需在 `logback-spring.xml` 中声明它，日志输出就会从文本变成 JSON。

### Spring Boot 默认日志：零配置即可用

新建一个 Spring Boot 项目，不需要添加任何日志依赖或配置——因为 `spring-boot-starter-webmvc` 的依赖链已经自动引入了 `logback-classic`。启动项目后，直接在代码里写 `log.info()` 就能看到控制台输出：

```
2026-07-28 10:30:15.123  INFO 12345 --- [nio-8080-exec-1] com.example.OrderService : 用户 12345 下单成功
```

这就是 Spring Boot 的默认日志格式（PatternLayoutEncoder），包含时间、级别、PID、线程、Logger 名、消息。你可以在 `application.yml` 中调整日志级别：

```yaml
logging:
  level:
    com.example: DEBUG # 自己的包开 DEBUG
    org.springframework: WARN
```

但默认格式是**自由文本**——grep 可以搜关键词，但没法做 `amount > 50 AND userId = 12345` 这种条件筛选。这就是 logstash-logback-encoder 要解决的问题：不改 Java 代码，只改 Encoder，把输出从文本变成 JSON。

### Lombok @Slf4j：省去样板代码

每次写日志都要声明 Logger：

```java
// 不用 Lombok：每个类都要手写这一行
private static final org.slf4j.Logger log =
    org.slf4j.LoggerFactory.getLogger(OrderService.class);

// 用 Lombok：一个注解搞定
@Slf4j
public class OrderService {
    public void placeOrder() {
        log.info("用户下单成功");  // Lombok 编译期自动生成上面的 Logger 声明
    }
}
```

`@Slf4j` 生成的 Logger 是 **SLF4J 的 Logger**（`org.slf4j.Logger`），底层走什么实现由 classpath 决定——Spring Boot 环境下自动走 Logback。

> 本指南所有示例统一使用 `@Slf4j` + `log.info()`，这也是项目中最常见的写法。

---

## 1. 入门三步走

§0 讲过，Spring Boot 默认用 Logback 输出文本格式日志——给人类看很方便，但机器难以解析。logstash-logback-encoder 做的事很单纯：**把 Logback 的 Encoder 从默认的 PatternLayoutEncoder 换成 LogstashEncoder，日志就从"文本行"变成了"JSON 对象"**——Java 代码零改动。下面分三层递进，从最简配置到生产可用。

### 1.1 第一层：最简配置 — 控制台输出 JSON

先从最简单的场景开始：不改业务代码，只加一个 `logback-spring.xml`，看效果。

**编写 logback-spring.xml**

在 `src/main/resources/` 下创建 `logback-spring.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- 控制台输出，使用 LogstashEncoder 把日志变成 JSON -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
    </appender>

    <root level="INFO">
    <!-- 把上面定义的 CONSOLE appender 挂到 root logger 上  -->
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

**原先的日志长这样**（Spring Boot 默认格式）：

```
2026-07-28 10:30:15.123  INFO 12345 --- [nio-8080-exec-1] com.example.OrderService : 用户 12345 下单成功
```

**配置后变成这样**（一条 JSON）：

```json
{
  "@timestamp": "2026-07-28T10:30:15.123+08:00",
  "@version": "1",
  "message": "用户 12345 下单成功",
  "logger_name": "com.example.OrderService",
  "thread_name": "http-nio-8080-exec-1",
  "level": "INFO",
  "level_value": 20000
}
```

每个字段的含义：

```
字段              含义
══════════        ════════════════════
@timestamp       ISO-8601 时间戳（精确到毫秒）
@version         编码器版本号（固定 "1"）
message          日志消息文本
logger_name      打印日志的类名
thread_name      线程名
level            日志级别字符串（INFO / WARN / ERROR）
level_value      日志级别数值（20000 = INFO，30000 = WARN，40000 = ERROR）
```

**发生了什么？** LogstashEncoder 把 Logback 每次日志调用时的上下文信息（时间、线程、级别、Logger 名、消息）打包成一个 JSON 对象输出。这些信息原先散落在文本行的不同位置，现在被规整成了固定的 key-value。

### 1.2 第二层：+ MDC — 注入 traceId

第一层每条日志是独立的 JSON，但一个请求通常会打印多条日志——下单请求在 Controller、Service、Repository 各打一条。没有关联字段，这三条日志在 Kibana（一个 Web 搜索界面，用于日志搜索） 里无法串联。

#### 新概念：MDC（Mapped Diagnostic Context）

MDC 是 Logback 提供的一个"线程局部 Map"：

```
MDC = 一个 Map<String, String>，绑定在当前线程上
      你在 Filter 里 put("traceId", "abc-123")
      → 本线程内所有后续 log.info() 自动带上 traceId 字段
      → 一次请求的几十条日志，靠同一个 traceId 串联成一个调用链
```

类比：MDC 就像快递单号——同一包裹的揽件、运输、派送记录，靠同一个单号就能串联。

#### 第一步：写一个 Filter 注入 traceId

```java
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

// 每次 HTTP 请求进入时：生成 traceId 放入 MDC
// 请求结束时：清理 MDC，防止内存泄漏
@Component
public class TraceIdFilter implements Filter {

    private static final String TRACE_ID_KEY = "traceId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        // 优先读取请求头中的 traceId（通常由前端或网关在请求链起始处生成），没有则自己生成
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String traceId = httpRequest.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }

        try {
            MDC.put(TRACE_ID_KEY, traceId);
            chain.doFilter(request, response);
        } finally {
            MDC.clear();  // 线程回池前必须清理，否则下个请求会串数据
        }
    }
}
```

> **外部主题提示**：Filter 的完整机制（执行顺序、与 Interceptor 的区别）见 [Spring Filter / Interceptor 指南](spring-filter-interceptor-guide.md)。这里只需知道：Filter 在每个请求的最外层，是注入 traceId 的最佳位置。

#### 第二步：配置中加入静态字段

在 `logback-spring.xml` 中用 `customFields` 添加不随请求变化的全局字段——微服务名、环境名（多服务共用 ELK 时，靠这两个字段筛选特定服务的特定环境日志）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder">
            <!-- 静态字段：每条日志都携带 -->
            <customFields>{"appName":"order-service","env":"dev"}</customFields>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

#### 效果：一次请求的三条日志自动串联

```
请求进入（TraceIdFilter 注入 traceId = "a1b2c3d4"）

Controller:  log.info("收到下单请求")
             → {"@timestamp":"...","message":"收到下单请求","traceId":"a1b2c3d4","appName":"order-service","env":"dev",...}

Service:     log.info("用户 {} 下单成功", userId)
             → {"@timestamp":"...","message":"用户 12345 下单成功","traceId":"a1b2c3d4","appName":"order-service","env":"dev",...}

Repository:  log.info("订单已入库, orderId={}", orderId)
             → {"@timestamp":"...","message":"订单已入库, orderId=ORD-001","traceId":"a1b2c3d4","appName":"order-service","env":"dev",...}
```

三条日志靠同一个 `traceId` 串联，靠 `appName` 区分微服务（多服务共用一套 ELK 时，据此筛选某个服务的日志），靠 `env` 区分环境。

> **为什么各层 traceId 相同？** 同一个 HTTP 请求内，Filter → Controller → Service → Repository 运行在同一个线程上，MDC 是线程绑定的，所有 `log.info()` 读的是同一个 MDC Map——所以 traceId 自然相同。不同请求走不同线程，traceId 才会不同。

> **LogstashEncoder 的 MDC 处理**：默认 `includeMdc = true`，即 MDC 中的所有 key 自动出现在 JSON 根级别。你 `put("traceId", "xxx")` → JSON 中自动出现 `"traceId":"xxx"`——不需要额外配置。

### 1.3 第三层：+ 生产配置 — 异步 + 环境区分

第二层的配置在开发环境够用，但上生产有三个问题：

| 问题                   | 影响                             | 解决方案                     |
| ---------------------- | -------------------------------- | ---------------------------- |
| 日志写入是同步的       | 磁盘 I/O 阻塞业务线程            | AsyncAppender 异步写入       |
| 开发和生产用同一份配置 | dev 看 JSON 费眼，prod 需要 JSON | springProfile 环境区分       |
| 日志全打到控制台       | 重启就丢，无法回溯               | RollingFileAppender 按天归档 |

#### 最终配置：完整 logback-spring.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- 引入 Spring Boot 默认属性（LOG_FILE 等） -->
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <!-- ==================== dev 环境：控制台人类可读 ==================== -->
    <springProfile name="dev">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <!-- 开发环境用 Spring Boot 默认格式，人类友好 -->
                <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %highlight(%-5level) %X{traceId} %magenta([%thread]) %cyan(%logger{36}) - %msg%n</pattern>
            </encoder>
        </appender>

        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>

    <!-- ==================== prod 环境：JSON + 异步 + 文件滚动 ==================== -->
    <springProfile name="prod">
        <!-- 异步缓冲：先入队列，后台线程批量写磁盘 -->
        <appender name="ASYNC_FILE" class="ch.qos.logback.classic.AsyncAppender">
            <!-- 队列满时丢弃 TRACE/DEBUG/INFO，保留 WARN/ERROR -->
            <discardingThreshold>0</discardingThreshold>
            <!-- 队列最大容量 -->
            <queueSize>512</queueSize>
            <!-- 应用关闭时等待队列排空 -->
            <neverBlock>true</neverBlock>
            <!-- 包装同步文件 Appender -->
            <appender-ref ref="FILE"/>
        </appender>

        <!-- 同步文件输出：按天滚动，保留 30 天 -->
        <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
            <file>${LOG_FILE:-logs/order-service}.log</file>
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <customFields>{"appName":"order-service","env":"prod"}</customFields>
            </encoder>
            <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
                <!-- 每天滚动，文件名带日期；超过 100MB 也滚动 -->
                <fileNamePattern>${LOG_FILE:-logs/order-service}.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
                <maxFileSize>100MB</maxFileSize>
                <!-- 保留 30 天 -->
                <maxHistory>30</maxHistory>
                <!-- 所有归档文件总大小上限 -->
                <totalSizeCap>5GB</totalSizeCap>
            </rollingPolicy>
        </appender>

        <root level="INFO">
            <appender-ref ref="ASYNC_FILE"/>
        </root>
    </springProfile>
</configuration>
```

**关键点解析**：

```
                           请求线程                         后台线程
                              │                               │
                     log.info("...")                          │
                              │                               │
                              ▼                               │
                    ┌─────────────────┐                       │
                    │  AsyncAppender  │                       │
                    │   (内存队列)     │                       │
                    │  queueSize=512  │                       │
                    └────────┬────────┘                       │
                             │ 队列有数据时                      │
                             ▼                               │
                    ┌─────────────────┐     ┌─────────────────┐
                    │  RollingFile    │ ──▶ │  order-service  │
                    │  Appender       │     │  .log            │
                    │  (按天+大小滚动) │     │                  │
                    └─────────────────┘     └─────────────────┘

  业务线程只把日志事件丢进队列就返回（微秒级），磁盘 I/O 由后台线程处理。
```

- **`discardingThreshold: 0`**：队列满时不丢弃任何日志（默认会丢 INFO 级别）。生产环境日志宁慢勿丢。
- **`neverBlock: true`**：应用关闭时等队列排空再退出，避免丢失最后几条日志。
- **`SizeAndTimeBasedRollingPolicy`**：每天滚动 + 单文件超过 100MB 也滚动，哪个条件先满足都触发。

### 本节回顾

```
第一层：ConsoleAppender + LogstashEncoder  →  日志从文本变成 JSON（零代码改动）
                     │
                     ▼
第二层：MDC (Filter 注入 traceId) + customFields  →  每次请求的日志可串联，知道"是谁、在哪个服务"
                     │
                     ▼
第三层：AsyncAppender + springProfile + RollingFileAppender  →  生产可用：不阻塞、不丢数据、可回溯
```

---

## 2. 进阶：LoggingEventCompositeJsonEncoder

`LogstashEncoder` 输出固定结构的 JSON——足够 90% 场景。剩下 10% 需要精细控制时（改字段名、去掉不用的字段、调整字段顺序），用 `LoggingEventCompositeJsonEncoder`。

### 两个 Encoder 的分工

```
┌─────────────────────────────────────┬────────────────────────────────────────┐
│          LogstashEncoder            │     LoggingEventCompositeJsonEncoder   │
├─────────────────────────────────────┼────────────────────────────────────────┤
│ 输出固定 JSON 结构                   │ 每个字段由 Provider 控制                │
│ 只需一行 <encoder> 声明              │ 需要逐个声明需要的 Provider             │
│ 覆盖 90% 日常场景                    │ 覆盖剩下 10% 精细定制场景               │
│ 自动包含：timestamp, message,        │ 显式选择：只输出你声明的那些字段        │
│   logger_name, thread_name,          │                                       │
│   level, level_value, MDC 全部       │                                       │
└─────────────────────────────────────┴────────────────────────────────────────┘
```

### 定制示例：改名 + 精简字段

把默认的 `@timestamp` 改成 `timestamp`，把 `message` 改成 `msg`，去掉 `thread_name` 和 `@version`：

```xml
<encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
    <providers>
        <!-- 时间戳：改名 + 指定时区 -->
        <timestamp>
            <fieldName>timestamp</fieldName>
            <timeZone>Asia/Shanghai</timeZone>
        </timestamp>
        <!-- 日志级别 -->
        <logLevel>
            <fieldName>level</fieldName>
        </logLevel>
        <!-- Logger 名 -->
        <loggerName>
            <fieldName>logger</fieldName>
        </loggerName>
        <!-- 日志消息：改名 -->
        <message>
            <fieldName>msg</fieldName>
        </message>
        <!-- 异常堆栈 -->
        <stackTrace/>
        <!-- MDC 所有字段（含 traceId） -->
        <mdc/>
        <!-- 全局静态字段（appName、env） -->
        <customFields>{"appName":"order-service","env":"prod"}</customFields>
    </providers>
</encoder>
```

输出效果：

```json
{
  "timestamp": "2026-07-28T10:30:15.123+08:00",
  "level": "INFO",
  "logger": "com.example.OrderService",
  "msg": "用户 12345 下单成功",
  "traceId": "a1b2c3d4",
  "appName": "order-service",
  "env": "prod"
}
```

对比默认 `LogstashEncoder` 的输出——`thread_name`、`level_value`、`@version` 都去掉了，字段名更符合团队习惯。

### 常用 Provider 一览

`LoggingEventCompositeJsonEncoder` 通过 `<providers>` 下的子元素声明需要的字段，每个子元素就是一个 Provider：

```
Provider           作用                      对应字段
──────────         ──────────────────        ────────────
<timestamp>        时间戳（支持改名、时区）     @timestamp
<logLevel>         日志级别（字符串+数值）      level / level_value
<loggerName>       Logger 名                  logger_name
<message>          日志消息文本                 message
<threadName>       线程名                      thread_name
<stackTrace>       异常堆栈                    stack_trace
<mdc>              MDC 所有字段                动态 key
<customFields>     全局静态字段                你指定的 key
<context>          Logback Context 属性       你指定的 key
```

> **注意**：`LoggingEventCompositeJsonEncoder` 只输出你声明了的 Provider。如果忘了声明 `<message>`，JSON 里就没有消息内容——字段是白名单模式，不是黑名单。

---

## 3. 速查清单

### 3.1 logback-spring.xml 模板速查

**dev 环境**（控制台人类可读 + MDC）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <springProfile name="dev">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} %highlight(%-5level) %magenta([%thread]) %cyan(%logger{36}) %X{traceId} - %msg%n</pattern>
                <!--                                ↑ %X{traceId} 在文本格式中打印 MDC 变量            -->
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>
</configuration>
```

**prod 环境**（JSON + 异步 + 文件滚动）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <springProfile name="prod">
        <appender name="ASYNC_FILE" class="ch.qos.logback.classic.AsyncAppender">
            <discardingThreshold>0</discardingThreshold>
            <queueSize>512</queueSize>
            <neverBlock>true</neverBlock>
            <appender-ref ref="FILE"/>
        </appender>

        <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
            <file>${LOG_FILE:-logs/app}.log</file>
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <customFields>{"appName":"myapp","env":"prod"}</customFields>
            </encoder>
            <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
                <fileNamePattern>${LOG_FILE:-logs/app}.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
                <maxFileSize>100MB</maxFileSize>
                <maxHistory>30</maxHistory>
                <totalSizeCap>5GB</totalSizeCap>
            </rollingPolicy>
        </appender>

        <root level="INFO">
            <appender-ref ref="ASYNC_FILE"/>
        </root>
    </springProfile>
</configuration>
```

### 3.2 两个 Encoder 决策速查

```
┌──────────────────────────────────────────────────────────────────┐
│                 Encoder 选型决策                                  │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  需要自定义字段名、去掉某些字段、调整输出结构？                     │
│      ├─ YES → LoggingEventCompositeJsonEncoder (§2)             │
│      └─ NO  → LogstashEncoder (§1)                              │
│                                                                  │
│  LogstashEncoder 默认输出的字段：                                  │
│      @timestamp, @version, message, logger_name,                │
│      thread_name, level, level_value                            │
│      + MDC 全部字段（自动）                                       │
│      + customFields（手动声明）                                   │
│                                                                  │
│  LoggingEventCompositeJsonEncoder：                               │
│      只输出你声明的 Provider（白名单模式）                         │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

### 3.3 MDC Filter 模板速查

```java
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
public class TraceIdFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String traceId = httpRequest.getHeader("X-Trace-Id");
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
        }
        try {
            MDC.put("traceId", traceId);
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

### 3.4 常见反模式

```
┌──────────────────────────────────────────────────────────────────────┐
│                         常见坑与正确做法                               │
├─────────────────────────────────────────────┬────────────────────────┤
│  坑                                         │  正确做法               │
├─────────────────────────────────────────────┼────────────────────────┤
│  只在 Filter 入口 MDC.put()，没 MDC.clear()  │  finally 中 clear()    │
│  → 线程回池后复用，下个请求串了上个人的 traceId│  线程池场景必做          │
├─────────────────────────────────────────────┼────────────────────────┤
│  prod 环境直接用同步 ConsoleAppender         │  AsyncAppender 包装     │
│  → 每次 log.info() 等磁盘写完才返回          │  业务线程丢队列即返回    │
├─────────────────────────────────────────────┼────────────────────────┤
│  队列满时丢弃日志（默认行为）                 │  discardingThreshold=0  │
│  → 高并发时丢失关键日志                       │  宁慢勿丢               │
├─────────────────────────────────────────────┼────────────────────────┤
│  开发环境也用 JSON 输出                      │  springProfile 区分     │
│  → 本地调试时读 JSON 费眼                    │  dev=文本, prod=JSON    │
├─────────────────────────────────────────────┼────────────────────────┤
│  只用 LogstashEncoder 不设 customFields      │  customFields 注入      │
│  → 多服务日志混在同一个 ES 索引中无法区分      │  appName + env          │
├─────────────────────────────────────────────┼────────────────────────┤
│  LoggingEventCompositeJsonEncoder 忘声明      │ 检查 providers 白名单   │
│  <message> → 日志没内容，排查无从下手         │ 至少保留 timestamp +    │
│                                             │  message + level        │
└─────────────────────────────────────────────┴────────────────────────┘
```

### 3.5 与 Jackson 的关系

logstash-logback-encoder 内部使用 Jackson 做 JSON 序列化。

```
9.0 以前（v7.x / v8.x）：                      9.0 以后：
═══════════════════════                      ═════════

Jackson 2.x ← logstash 日志编码               Jackson 3.x ← 共用
Jackson 3.x ← Spring Boot HTTP 序列化          tools.jackson.core
com.fasterxml.jackson.core
|                                              Spring Boot 和 logstash
| 两套并行，包名不同不冲突                         共用同一个 Jackson 版本，
|                                              classpath 更干净

9.0 统一到 Jackson 3.x，不再有两套 Jackson
共存的问题。
```

> **9.0 迁移提示**：如果你从 v8.0 升级到 v9.0，`pom.xml` 中不再需要单独处理 Jackson 版本冲突——logstash-logback-encoder 和 Spring Boot 现在使用同一个 `tools.jackson.core`（Jackson 3.x）。

> Jackson 的完整用法（注解、配置、序列化/反序列化）见 [Jackson JSON 处理指南](jackson-guide.md)。

---

> **延伸阅读：**
>
> - [Jackson JSON 处理指南](jackson-guide.md) —— logstash-logback-encoder 内部使用 Jackson 3.x 做 JSON 序列化
> - [Spring Filter / Interceptor 指南](spring-filter-interceptor-guide.md) —— MDC 注入 traceId 的 Filter 机制详解
> - [logstash-logback-encoder 官方文档](https://github.com/logfellow/logstash-logback-encoder)
