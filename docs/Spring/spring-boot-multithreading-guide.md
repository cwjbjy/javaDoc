# 在 Spring Boot 3.5 中使用多线程

> Audience: 已掌握 Java 多线程基础和 Spring Boot IoC/DI，希望把异步任务用于真实服务的开发者
> Outcome: 能为 Spring Boot 应用配置有界线程池，正确使用 `@Async`，处理代理、异常、事务、上下文、关闭与测试边界
> Applicable version: Spring Boot 3.5.x、Spring Framework 6.2.x、JDK 17

## Scope

这篇指南解决的不是“怎样创建线程”，而是“怎样把并发任务交给 Spring 管理，并明确资源与业务边界”。阅读前应先掌握 [Java 多线程基础](../Java/multithreading-basics.md) 中的线程池、拒绝策略、`CompletableFuture` 与协作式中断。

**涵盖**：Spring Boot 线程池自动配置、`ThreadPoolTaskExecutor`、`@Async`、返回值与异常、事务和 `ThreadLocal` 上下文、优雅关闭、测试与选型。

**不涵盖**：锁和 JMM 的基础原理、WebFlux 响应式编程、JDK 21 虚拟线程、分布式任务调度、消息队列的具体实现。

> **核心认知**：`@Async` 不是“让方法变快”的魔法；它是在调用者和工作线程之间建立一个异步边界。边界两侧的线程、事务、异常和上下文都必须分别设计。

## 目录

- [1. 从手动线程切换到 Spring 托管任务](#1-从手动线程切换到-spring-托管任务)
- [2. 第一个生产级异步调用：命名线程池 + `@Async`](#2-第一个生产级异步调用命名线程池-async)
- [3. 为不同任务配置有界线程池](#3-为不同任务配置有界线程池)
- [4. 理解 @Async 的代理边界](#4-理解-async-的代理边界)
- [5. 设计返回值、异常、超时与取消](#5-设计返回值异常超时与取消)
- [6. 处理事务和线程上下文](#6-处理事务和线程上下文)
- [7. 关闭、观测与测试](#7-关闭观测与测试)
- [8. 场景选型与生产检查清单](#8-场景选型与生产检查清单)

---

## 1. 从手动线程切换到 Spring 托管任务

### 问题：`new Thread(...)` 脱离了容器

下面的写法能启动线程，但 Spring 不知道这个线程何时创建、用多少资源、何时关闭，也无法统一注入线程名、拒绝策略和上下文传播规则。

> Illustrative fragment：省略了任务实现，只展示不推荐的线程创建方式。

```java
new Thread(() -> reportService.generateReport(reportId)).start();
```

在 Spring Boot 应用中，业务代码通常只描述“提交什么任务”，执行资源则由容器管理：

```text
HTTP 请求线程
    |
    | 调用 Spring 代理
    v
TaskExecutor.submit(task)
    |
    +--> 有空闲线程：立即执行
    |
    +--> 无空闲线程：进入有界队列
    |
    +--> 线程和队列都满：执行拒绝策略
```

Spring 的核心抽象是 `TaskExecutor`。`ThreadPoolTaskExecutor` 是常用实现，内部委托给 JDK 的 `ThreadPoolExecutor`，同时参与 Spring Bean 生命周期。

### Spring Boot 3.5 在 JDK 17 下的默认行为

在 JDK 17 下，如果容器中没有用户定义的 `Executor`，Spring Boot 3.5 会自动配置名为 `applicationTaskExecutor` 的 `ThreadPoolTaskExecutor`；官方 3.5 文档给出的默认核心线程数为 8。设置 `spring.task.execution.mode=force` 时，即使已有用户 `Executor`，Boot 也会创建该应用执行器。

这和纯 Spring Framework 找不到执行器时的后备行为不是一回事。判断实际行为时必须区分：

| 环境                                   | 应关注的执行器来源                                                                                     |
| -------------------------------------- | ------------------------------------------------------------------------------------------------------ |
| Spring Boot 3.5，未定义用户 `Executor` | Boot 自动配置的 `applicationTaskExecutor`                                                              |
| 定义了用户 `Executor`                  | 默认自动配置退让；不要假定这个 Bean 必然成为 `@Async` 的执行器                                         |
| `@Async` 未指定执行器                  | 使用唯一 `TaskExecutor`、名为 `taskExecutor` 的 `Executor`，否则落到框架回退执行器；生产中避免依赖回退 |
| 定义多个执行器                         | 用 `@Async("beanName")` 或 `AsyncConfigurer` 明确选择；不要把 `@Primary` 当作任务路由策略              |
| Spring MVC/WebFlux 的异步请求处理      | 需要名为 `applicationTaskExecutor` 的 `AsyncTaskExecutor`                                              |

本指南的业务异步任务会使用显式命名的执行器，避免它与 MVC 异步请求、WebSocket、JPA 启动或其他框架集成争抢同一个池。

---

## 2. 第一个生产级异步调用：命名线程池 + `@Async`

生产代码不应把业务任务隐式交给默认执行器。先定义一个有名称、有容量边界的线程池，再让 `@Async` 显式选择它。下面以阻塞 I/O 类任务为例；容量必须根据外部连接池、超时和可接受排队时间调整。

### 第一步：启用异步方法

> Illustrative fragment：这是完整配置类，但尚未与具体业务方法组成独立应用。

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfiguration {
}
```

### 第二步：定义命名的有界线程池

> Illustrative fragment：将下面的 `@Bean` 方法加入上一步的 `AsyncConfiguration`。参数是示例值，必须按实际负载与下游容量调整。

```java
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Bean("ioTaskExecutor")
ThreadPoolTaskExecutor ioTaskExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(4);
    executor.setMaxPoolSize(8);
    executor.setQueueCapacity(100);
    executor.setKeepAliveSeconds(30);
    executor.setThreadNamePrefix("io-task-");
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy()); // 队列满时由调用线程执行，形成反压
    executor.setWaitForTasksToCompleteOnShutdown(true);  // 优雅关闭：等待已提交任务完成
    executor.setAwaitTerminationSeconds(20);              // 等待终止的超时时间（秒）
    return executor;
}
```

作为 Bean 返回后，Spring 会负责 `ThreadPoolTaskExecutor` 的初始化和关闭，无需手动调用 `initialize()`。

### 第三步：显式选择线程池

最常见的形态是 `void`——fire-and-forget 任务（通知、邮件、日志、缓存刷新）不产生结果，也不需要结果通道：

> Illustrative fragment：省略邮件发送实现。

```java
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    @Async("ioTaskExecutor")
    public void sendEmail(String to, String subject) {
        // 邮件发送——调用方不关心结果，也不等待
    }
}
```

调用方立即返回，不等待发送完成：

> Illustrative fragment：省略调用类定义。

```java
notificationService.sendEmail("user@example.com", "报表已生成");
// 立即继续执行，不等邮件发完
```

`void` 形态没有结果通道：调用方拿不到返回值，也观察不到异常（异常处理见第 5 章）。当调用方需要结果时，`@Async` 方法可以返回 `CompletableFuture<T>`，也可以直接用 `CompletableFuture.supplyAsync` 提交——按能力归属选择，取舍见[第 5 章]

> **默认执行器何时适用？** `applicationTaskExecutor` 适合单一、低风险的后台任务或原型阶段。即使使用它，也应通过 `spring.task.execution.*` 明确容量、队列、线程名和关闭策略；有多个资源特征不同的任务时，使用命名专用线程池。

---

## 3. 为不同任务配置有界线程池

### 为什么要拆分线程池

把所有任务塞进同一个池会产生故障耦合：报表任务积压，可能让邮件、审计或缓存刷新也排不上队。应按资源特征和可靠性要求拆分，而不是按 Service 类数量拆分。

常见分组：

| 线程池                 | 任务特征                     | 容量关注点                     |
| ---------------------- | ---------------------------- | ------------------------------ |
| `ioTaskExecutor`       | HTTP、数据库、文件等阻塞 I/O | 外部连接池容量、超时、排队时间 |
| `cpuTaskExecutor`      | 压缩、加密、复杂计算         | CPU 核数、上下文切换           |
| `notificationExecutor` | 邮件、短信等可独立降级任务   | 限流、重试、是否允许丢失       |

#### 为不同任务增加专用池

第 2 节已经定义了 `ioTaskExecutor`。需要增加 CPU 或通知任务时，沿用相同结构，使用不同 Bean 名、线程名前缀、容量和拒绝策略；不要让多个不同资源特征的任务共用同一个池。

关键任务更适合显式失败、持久化重试或消息队列，而不是依赖 `CallerRunsPolicy` 兜底。

#### 异步服务：声明式与组合式两种写法

声明式：给方法加 `@Async` 并指定执行器，适合服务层的固定后台能力（fire-and-forget 任务）：

> Illustrative fragment：省略通知发送实现。

```java
package example.guide;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    @Async("notificationExecutor")
    public void sendEmail(String to, String subject) {
        // 邮件发送，不产生结果
    }
}
```

组合式：不经过 AOP 代理，直接注入执行器并用 `CompletableFuture.supplyAsync` 提交，适合 Controller/协调层的临时扇出与多调用组合：

> Illustrative fragment：省略 `ReportResult` 类型定义与构建实现。

```java
package example.guide;

import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    private final ThreadPoolTaskExecutor ioTaskExecutor;

    public ReportService(
            @Qualifier("ioTaskExecutor") ThreadPoolTaskExecutor ioTaskExecutor
    ) {
        this.ioTaskExecutor = ioTaskExecutor;
    }

    public CompletableFuture<ReportResult> generate(String reportId) {
        return CompletableFuture.supplyAsync(
                () -> buildReport(reportId),
                ioTaskExecutor
        );
    }

    private ReportResult buildReport(String reportId) {
        return new ReportResult(reportId, Thread.currentThread().getName());
    }
}
```

两种写法的完整取舍见[第 5 章](#5-设计返回值异常超时与取消)。

---

## 4. 理解 @Async 的代理边界

### 真正提交任务的是代理

Spring Framework 6.2 默认用 `proxy` 模式处理 `@Async`。容器注入给调用者的并不是裸对象，而是带拦截逻辑的代理

### 最常见的失效：同类自调用

> Illustrative fragment：展示错误边界。

```java
@Service
public class ReportService {

    public void start(String reportId) {
        generate(reportId); // this.generate(...)，没有经过 Spring 代理
    }

    @Async("ioTaskExecutor")
    public void generate(String reportId) {
        // 实际仍在调用 start(...) 的线程执行
    }
}
```

修复方法是把异步方法放到另一个 Bean，让调用跨越 Bean 边界：

> Illustrative fragment：省略具体实现。

```java
@Service
public class ReportCoordinator {

    private final ReportWorker reportWorker;

    public ReportCoordinator(ReportWorker reportWorker) {
        this.reportWorker = reportWorker;
    }

    public void start(String reportId) {
        reportWorker.generate(reportId);
    }
}

@Service
public class ReportWorker {

    @Async("ioTaskExecutor")
    public void generate(String reportId) {
        // 由代理提交到 ioTaskExecutor，异步执行
    }
}
```

### 代理边界检查

- 优先把 `@Async` 放在由其他 Bean 调用的 `public` 方法上。
- 不要在同类中通过 `this` 调用异步方法。
- 不要把 `@Async` 用在 `@PostConstruct` 生命周期回调上；初始化 Bean 如需异步，应由另一个 Bean 在容器就绪后调用。
- 不要自己 `new ReportWorker()`；手动创建的对象不受 Spring 代理。
- 多个执行器并存时，始终写明 `@Async("beanName")`，避免选择规则随配置变化。

---

## 5. 设计返回值、异常、超时与取消

### 先问：异步能力属于哪一层？

`@Async` 方法与 `CompletableFuture.supplyAsync` 对调用方**效果等同**——都立即返回 `Future`、都在指定执行器上执行、异常都沿 `Future` 传播。真正的区别在能力归属与机制：

```text
异步能力属于哪一层？
│
├─ 服务层固定后台能力 ──> @Async（声明式，执行器集中路由）
│             ├─ 不需要结果 ──> void
│             │                 ├─ fire-and-forget：通知、邮件、日志、缓存刷新
│             │                 └─ 异常只有 AsyncUncaughtExceptionHandler 可见
│             │
│             └─ 需要结果 ──> CompletableFuture<T>
│                               ├─ body 在工作线程同步执行，completedFuture 包装结果
│                               └─ 有自调用陷阱（见第 4 章）
│
└─ Controller/协调层临时扇出 ──> CompletableFuture.supplyAsync(λ, executor)
                ├─ 显式提交，无代理、无自调用陷阱
                └─ 天然支持链式编排，allOf/anyOf 组合多个调用
```

### 形态一：@Async void + AsyncUncaughtExceptionHandler

`void` 异步方法没有返回通道。如果发生异常可以通过 `AsyncUncaughtExceptionHandler` 记录日志，只适合纯通知、日志、发邮件等"尽力而为"的任务。

> Illustrative fragment：只展示异常处理器形状。

```java
import java.lang.reflect.Method;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;

public final class LoggingAsyncExceptionHandler
        implements AsyncUncaughtExceptionHandler {

    @Override
    public void handleUncaughtException(
            Throwable exception,
            Method method,
            Object... parameters
    ) {
        // 记录 method、parameters、业务标识和完整异常
    }
}
```

如果失败会影响业务状态，不要只依赖日志：改用返回结果的形态、持久化任务状态，或使用带确认与重试机制的消息队列。

### 形态二：@Async CompletableFuture<T>

服务层需要返回结果时，`@Async` 方法声明返回 `CompletableFuture<T>`。方法体在工作线程上同步执行，`completedFuture` 只是把结果包装成方法签名要求的返回类型

> Illustrative fragment：省略依赖和业务类型。

```java
@Async("ioTaskExecutor")
public CompletableFuture<ReportResult> generate(String reportId) {
    ReportResult result = buildReport(reportId);
    return CompletableFuture.completedFuture(result);
}
```

调用方经由 Spring 代理调用该方法时，代理会在**提交任务后立即**返回一个尚未完成的 `CompletableFuture`；不会等待 `buildReport` 执行结束。目标方法在工作线程中于稍后创建 `completedFuture(result)`，该结果再完成调用方先前拿到的 Future。只有同类自调用、手动 `new` 对象等绕过代理的调用才会同步阻塞。

如果 `buildReport` 抛出异常，Spring 代理会自动把异常完成到返回的 Future 上，无需手动 `failedFuture` 包装。调用者拿到真正的 `CompletableFuture`，可以继续组合并在末尾统一兜底（示例见形态三）。

### 形态三：CompletableFuture.supplyAsync(λ, executor)

Controller/协调层需要临时扇出时，不经过 `@Async` 和代理，直接把任务提交给注入的执行器。没有自调用陷阱，天然支持链式编排：

> Illustrative fragment：省略执行器注入（见第 3 章）和 `buildReport` 实现。

```java
public CompletableFuture<ReportResult> generate(String reportId) {
    return CompletableFuture.supplyAsync(
            () -> buildReport(reportId),
            ioTaskExecutor
    );
}
```

调用者可以继续组合并在末尾统一兜底：

```java
reportService.generate(reportId)
        .thenAccept(this::saveResult)
        .exceptionally(exception -> {
            log.error("生成报告失败，reportId={}", reportId, exception);
            return null;
        });
```

异常同样通过 `exceptionally`/`handle` 在链上处理。

### 超时不是强制终止

JDK 17 的 `orTimeout` 为 `CompletableFuture` 增加一个**完成期限**：如果 Future 在期限内尚未完成，它会以 `TimeoutException` 异常完成。

> Illustrative fragment：省略 `TimeUnit` 导入和 `reportService` 定义。

```java
CompletableFuture<ReportResult> future = reportService.generate(reportId)
        .orTimeout(5, TimeUnit.SECONDS);
```

这里限制的是调用方能等待结果多久，而不是 `generate` 实际执行多久。可以把它理解为两条彼此独立的时间线：

```text
工作线程：  开始生成报告 ------------------------> 任务可能继续执行并结束
调用线程：  取得 Future ---- 等待最多 5 秒 -----> Future 以 TimeoutException 完成
```

超时后，依赖这个 Future 的普通成功分支不会执行；异常分支会收到异常。对于 HTTP 响应、页面聚合等“超过期限就返回失败或降级结果”的场景，这正是需要的行为：

> Illustrative fragment：`handle` 中的 `exception` 可能包装原始异常；生产代码应保留完整异常日志。

```java
reportService.generate(reportId)
        .orTimeout(5, TimeUnit.SECONDS)
        .handle((result, exception) -> {
            if (exception != null) {
                log.warn("报告生成超时或失败，reportId={}", reportId, exception);
                return ReportResult.pending(reportId);
            }
            return result;
        });
```

#### `orTimeout` 与 `completeOnTimeout` 的选择

两者都不会替你停止底层任务，但向后续链路表达的结果不同：

| 目标 | 方法 | 到期后的 Future 状态 |
|---|---|---|
| 调用方必须知道“没有在期限内完成” | `orTimeout(5, SECONDS)` | 异常完成，原因为 `TimeoutException` |
| 允许使用明确的降级值继续流程 | `completeOnTimeout(fallback, 5, SECONDS)` | 正常完成，结果为 `fallback` |

使用 `completeOnTimeout` 前要确认降级值不会被误认为真实数据。例如“报告仍在生成中”可以作为明确状态；用空对象、空列表或 `null` 隐藏超时，通常会让调用方无法区分“确实没有数据”和“没有及时拿到数据”。

#### 它还会影响谁

`orTimeout` 和 `completeOnTimeout` 都是对**当前 Future 本身**设定完成结果，并返回同一个 Future。若这个 Future 被多个调用方共享，其中一个调用方加上的超时也会改变其他调用方看到的结果。

如果每个调用方有不同期限，应先派生自己的阶段，再对派生阶段设置超时：

> Illustrative fragment：省略 `Function` 导入。

```java
CompletableFuture<ReportResult> shared = reportService.generate(reportId);

CompletableFuture<ReportResult> responseDeadline = shared
        .thenApply(Function.identity())
        .orTimeout(5, TimeUnit.SECONDS);
```

`responseDeadline` 超时不会反向完成或取消 `shared`；其他调用方仍可按自己的期限等待同一个报告任务。

#### 为什么 `cancel(true)` 也不是万能解法

不要把 `CompletableFuture.cancel(true)` 当作可靠的强制中断工具。对 `CompletableFuture` 而言，取消首先是让 Future 以取消状态完成；它不能保证提交任务的工作线程会被中断，更不能让已经发出的 HTTP 请求、数据库查询或文件 I/O 自动撤销。

真正需要限制资源占用时，应在任务边界建立协作式取消：

1. 为 HTTP 客户端、数据库查询和远程调用配置各自的连接、读取或查询超时；
2. 对可中断的阻塞操作，正确处理 `InterruptedException`，恢复中断标记或结束任务；
3. 对长循环或批处理定期检查取消标记／中断状态，并在安全点退出；
4. 将“已超时但后台仍在处理”的任务记录为可观察状态，避免重复提交相同的昂贵工作。

因此，`orTimeout` 解决的是**调用方等待上限**；资源超时、任务取消和幂等性需要由底层操作与业务协议共同保证。JDK 17 API 对 [`orTimeout`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html#orTimeout(long,java.util.concurrent.TimeUnit))、[`completeOnTimeout`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html#completeOnTimeout(T,long,java.util.concurrent.TimeUnit)) 和 [`cancel`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/CompletableFuture.html#cancel(boolean)) 的语义以此为准。

---

## 6. 处理事务和线程上下文

### 调用者事务不会自动传播

Spring 的常规事务资源按线程绑定。`@Async` 切换线程后，工作线程不会继承调用线程的数据库连接和事务状态：

```text
请求线程                         工作线程
@Transactional 开始
    |
    +-- 调用 @Async -----------> 新线程：没有调用者事务
    |
@Transactional 提交             需要时另开自己的事务
```

更清晰的结构是：异步入口只负责切换线程，工作线程再调用另一个事务 Bean。

> Illustrative fragment：省略 Repository 定义。

```java
@Service
public class ReportAsyncWorker {

    private final ReportTransactionService transactionService;

    public ReportAsyncWorker(
            ReportTransactionService transactionService
    ) {
        this.transactionService = transactionService;
    }

    @Async("ioTaskExecutor")
    public void rebuild(String reportId) {
        transactionService.rebuildInNewTransaction(reportId);
    }
}

@Service
public class ReportTransactionService {

    @Transactional
    public void rebuildInNewTransaction(String reportId) {
        // 事务在工作线程中开始和结束
    }
}
```

### ThreadLocal 上下文也不会自动传播

MDC、请求信息、Locale 和安全上下文常依赖 `ThreadLocal`。

> **MDC（Mapped Diagnostic Context）** 是日志框架提供的上下文机制，用 `ThreadLocal` 存储键值对，自动附加到每条日志。典型用途是在请求入口写入 `traceId`，让同一次请求的所有日志都能关联追踪。

`ThreadLocal` 是线程私有的，**不会跨线程传播**——异步线程拿不到主线程的值：

```java
// 主线程（HTTP 请求线程）
MDC.put("traceId", "abc-123");

@Async
public void doWork() {
    log.info("处理中");  // ❌ 日志里没有 traceId——工作线程的 MDC 是空的
}
```

更糟的是，线程池**复用线程**，工作线程可能残留上一个任务的 `ThreadLocal` 值，导致数据错乱。因此既不能假定上下文自动存在，也不能忘记清理旧上下文。

**Tracing 依赖不等于线程上下文自动传播**

如果主要需求是 `traceId` 传播，通常需要 Actuator 与 tracing bridge（版本由 Spring Boot BOM 管理）：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
```

Tracing 启用且当前线程具有追踪上下文时，`traceId` 会写入 MDC，日志中可以通过 `%X{traceId}` 输出：

```xml
<!-- logback-spring.xml -->
<pattern>%d{HH:mm:ss.SSS} [%thread] [%X{traceId}] %-5level %logger{36} - %msg%n</pattern>
```

但 bridge 依赖本身不会为任意线程池或 JDK 公共池安装上下文传播。真正的边界是任务提交到哪个执行器：

| 任务提交方式                                                   | `traceId` 是否传播      | 原因                                                           |
| -------------------------------------------------------------- | ----------------------- | -------------------------------------------------------------- |
| `@Async("ioTaskExecutor")`，且该池设置了上下文 `TaskDecorator` | 是                      | 装饰器在提交时捕获上下文，在工作线程执行时恢复，并在结束后清理 |
| `@Async("ioTaskExecutor")`，但手写线程池未设置装饰器           | 否                      | `@Async` 只负责提交任务，不复制 MDC 或追踪上下文               |
| `CompletableFuture.supplyAsync(...)`，不传执行器               | 否                      | 使用 JDK 公共 `ForkJoinPool`，绕过 Spring 的执行器装饰器       |
| `CompletableFuture.supplyAsync(..., ioTaskExecutor)`           | 取决于 `ioTaskExecutor` | 显式传入的执行器也必须已配置上下文装饰器                       |

`TaskDecorator` 可以理解为线程池的任务包装器：提交时复制必要上下文，执行时恢复，并在 `finally` 中清理，避免线程复用时把上一个请求的数据泄漏给下一个任务。对于 Boot 自动配置的 `applicationTaskExecutor`，只有容器中存在唯一可用的 `TaskDecorator` Bean 时，`TaskExecutorBuilder` 才会把它应用到该执行器；手写的 `ThreadPoolTaskExecutor` 必须自行调用 `setTaskDecorator(...)`。

> Illustrative fragment：具体装饰器取决于所启用的 tracing/context-propagation 方案；下面只展示安装位置。

```java
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Bean("ioTaskExecutor")
ThreadPoolTaskExecutor ioTaskExecutor(TaskDecorator taskDecorator) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setTaskDecorator(taskDecorator);
    // 其余线程池参数省略
    return executor;
}
```

**其他 `ThreadLocal` 上下文**

- **SecurityContext**：使用 Spring Security 提供的 `DelegatingSecurityContextExecutor`，并确保它就是 `@Async` 实际选中的执行器；或显式传递用户信息作为方法参数
- **Locale / 租户 ID 等**：推荐作为方法参数显式传递，比隐式传播更清晰、更安全
- **不要盲目复制所有 `ThreadLocal`**：只传播任务真正需要的信息

---

## 7. 关闭、观测与测试

### 优雅关闭要同时设计“接收”和“等待”

线程池 Bean 会随 Spring 容器关闭，但是否等待队列任务完成取决于配置。自动配置执行器可以使用：

```yaml
spring:
  task:
    execution:
      shutdown:
        await-termination: true
        await-termination-period: "20s"
```

自定义 `ThreadPoolTaskExecutor` 则使用：

```java
executor.setWaitForTasksToCompleteOnShutdown(true);
executor.setAwaitTerminationSeconds(20);
```

等待时间必须小于部署平台给应用的终止宽限期。即使开启等待，进程崩溃、强制终止或机器掉电仍会丢失内存中的任务，所以它不能替代持久化队列。

### 观测至少回答四个问题

| 问题               | 观察信号                           |
| ------------------ | ---------------------------------- |
| 线程是否一直满载   | active count 与 pool size          |
| 队列是否持续积压   | queue size、剩余容量、等待时间     |
| 是否发生拒绝       | 拒绝次数、提交方错误或回退次数     |
| 任务是否变慢或失败 | 任务耗时、超时数、异常数、业务状态 |

线程名前缀必须能区分任务池，例如 `io-task-`、`cpu-task-`。日志只记录“异步失败”没有价值，还要包含任务类型、业务 ID、执行器名和异常。

### 测试行为，不测试 sleep

异步测试不要用“睡 2 秒应该完成”作为断言。它慢、容易抖动，而且不能证明任务使用了正确的执行器。

优先验证：

1. Spring 上下文能创建执行器和异步 Bean；
2. 有返回值的方法，其 `CompletableFuture` 能得到预期结果或异常；
3. 执行线程名匹配指定池，且不同于调用线程；
4. 拒绝策略、超时和关闭行为有独立的边界测试；
5. fire-and-forget 任务用 `CountDownLatch` 或可观测状态协调，而不是固定睡眠。

### @Scheduled 和 @Async 不是同一件事

`@Scheduled` 解决“什么时候触发”，`@Async` 解决“由哪个线程异步执行”。在 JDK 17 下，Spring Boot 3.5 自动配置的 `ThreadPoolTaskScheduler` 默认只有一个线程；多个长任务会互相等待。

可以通过下面的属性调整调度线程：

```yaml
spring:
  task:
    scheduling:
      thread-name-prefix: "scheduling-"
      pool:
        size: 2
```

如果定时任务必须保证集群中只执行一次、支持补偿或保存运行历史，应使用分布式调度器，而不是只增加本地线程数。

---

## 8. 场景选型与生产检查清单

### 场景速查

| 需求                                                   | 推荐方案                                                                                         | 不应依赖                       |
| ------------------------------------------------------ | ------------------------------------------------------------------------------------------------ | ------------------------------ |
| 服务层固定后台能力（短时、可重试、允许进程退出时丢失） | 命名的 `@Async` + 有界执行器；需要结果时返回 `CompletableFuture<T>`                              | `new Thread`                   |
| 一个请求内并行组合多个独立 I/O（协调层临时扇出）       | `CompletableFuture.supplyAsync` + 专用 I/O 池 + 客户端超时                                       | 公共 `ForkJoinPool`            |
| 固定时间触发本地任务                                   | `@Scheduled` + 明确的调度池                                                                      | 默认单线程承载多个长任务       |
| 主事务提交后再执行                                     | `@TransactionalEventListener(AFTER_COMMIT)`；需异步时再叠加 `@Async("...")`；高可靠场景用 Outbox | 把事务阶段误认为异步或可靠投递 |
| 任务不能丢、需要重试/削峰/跨服务                       | 消息队列或持久化任务系统                                                                         | 进程内线程池                   |
| CPU 密集计算                                           | 独立小型 CPU 池，接近可用核心数                                                                  | 与阻塞 I/O 共用大池            |
| 需要 Spring MVC 异步请求执行器                         | 名为 `applicationTaskExecutor` 的 `AsyncTaskExecutor`                                            | 随意命名的普通 `Executor`      |

### 上线前检查

- [ ] 每个 `@Async` 方法都经过另一个 Spring Bean 调用，没有同类自调用。
- [ ] 多线程池场景为 `@Async` 显式指定了执行器名称。
- [ ] 核心线程数、最大线程数、队列容量和拒绝策略都有业务依据。
- [ ] 外部 HTTP、数据库和文件 I/O 配置了自身超时。
- [ ] 失败可被调用者、日志、指标或持久化任务状态观察到。
- [ ] 明确了调用者事务不会传播到工作线程。
- [ ] 只传递 ID 或不可变数据，不跨线程共享请求对象和打开的资源。
- [ ] MDC 等需要的 `ThreadLocal` 只通过已配置上下文装饰器的执行器传播，并在任务结束时清理。
- [ ] 应用关闭等待时间与部署平台的终止宽限期匹配。
- [ ] 对不可丢任务使用了消息队列、Outbox 或持久化调度，而不是内存线程池。
- [ ] 集成测试验证了 Spring 代理和实际执行线程，而不只是 Java 代码能编译。

## References

- [Spring Boot 3.5 — Task Execution and Scheduling](https://docs.spring.io/spring-boot/3.5/reference/features/task-execution-and-scheduling.html)
- [Spring Boot 3.5 — Common Application Properties](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/index.html)
- [Spring Framework 6.2 — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html)
- [Spring Framework 6.2 API — ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework 6.2 API — TransactionSynchronizationManager](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/transaction/support/TransactionSynchronizationManager.html)
- [Java 多线程基础](../Java/multithreading-basics.md)
