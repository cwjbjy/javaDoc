# 在 Spring Boot 3.5 中使用多线程

> Audience: 已掌握 Java 多线程基础和 Spring Boot IoC/DI，希望把异步任务用于真实服务的开发者
> Outcome: 能为 Spring Boot 应用配置有界线程池，正确使用 `@Async`，处理代理、异常、事务、上下文、关闭与测试边界
> Applicable version: Spring Boot 3.5.x（示例验证于 3.5.16）、Spring Framework 6.2.x、JDK 17

## Scope

这篇指南解决的不是“怎样创建线程”，而是“怎样把并发任务交给 Spring 管理，并明确资源与业务边界”。阅读前应先掌握 [Java 多线程基础](../Java/multithreading-basics.md) 中的线程池、拒绝策略、`CompletableFuture` 与协作式中断。

**涵盖**：Spring Boot 线程池自动配置、`ThreadPoolTaskExecutor`、`@Async`、返回值与异常、事务和 `ThreadLocal` 上下文、优雅关闭、测试与选型。

**不涵盖**：锁和 JMM 的基础原理、WebFlux 响应式编程、JDK 21 虚拟线程、分布式任务调度、消息队列的具体实现。

> **核心认知**：`@Async` 不是“让方法变快”的魔法；它是在调用者和工作线程之间建立一个异步边界。边界两侧的线程、事务、异常和上下文都必须分别设计。

## 目录

- [1. 从手动线程切换到 Spring 托管任务](#1-从手动线程切换到-spring-托管任务)
- [2. 用自动配置完成第一个异步调用](#2-用自动配置完成第一个异步调用)
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

当容器中没有自定义 `Executor` Bean 时，Spring Boot 3.5 会自动配置一个 `AsyncTaskExecutor`。在 JDK 17 下，它是 `ThreadPoolTaskExecutor`；官方 3.5 文档给出的默认核心线程数为 8。

这和纯 Spring Framework 找不到执行器时的后备行为不是一回事。判断实际行为时必须区分：

| 环境 | 应关注的执行器来源 |
|---|---|
| Spring Boot 3.5，未定义自定义 `Executor` | Boot 自动配置的 `AsyncTaskExecutor` |
| 定义了一个自定义 `Executor` | Boot 自动配置通常退让，由自定义 Bean 参与选择 |
| 定义多个 `Executor` | 用 Bean 名、`@Primary`、`@Async("...")` 或 `AsyncConfigurer` 明确选择 |
| Spring MVC/WebFlux 的异步请求处理 | 需要名为 `applicationTaskExecutor` 的 `AsyncTaskExecutor` |

本指南的业务异步任务会使用显式命名的执行器，避免它与 MVC 异步请求、WebSocket、JPA 启动或其他框架集成争抢同一个池。

---

## 2. 用自动配置完成第一个异步调用

当应用只有一种后台任务时，可以先使用 Boot 自动配置的执行器，通过配置文件限制容量。

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

### 第二步：配置默认执行器

> Illustrative fragment：属性已按 Spring Boot 3.5 官方属性表检查。

```yaml
spring:
  task:
    execution:
      thread-name-prefix: "app-async-"
      pool:
        core-size: 4
        max-size: 8
        queue-capacity: 100
        keep-alive: "30s"
      shutdown:
        await-termination: true
        await-termination-period: "20s"
```

`queue-capacity` 不是一个无关紧要的缓冲参数。`ThreadPoolExecutor` 的典型分配顺序是：

1. 先创建线程到 `core-size`；
2. 核心线程都忙时，任务先进入队列；
3. 只有队列满后，线程数才继续增长到 `max-size`；
4. 线程数和队列都到上限后，执行拒绝策略。

因此，使用无界队列时，`max-size` 实际不会参与扩容。生产配置应从可接受的排队时间和内存上限反推队列容量，而不是随意填写一个大数字。

### 第三步：声明异步方法

> Illustrative fragment：省略 `ReportResult` 和持久化实现。

```java
import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    @Async
    public CompletableFuture<ReportResult> generate(String reportId) {
        ReportResult result = buildReport(reportId);
        return CompletableFuture.completedFuture(result);
    }

    private ReportResult buildReport(String reportId) {
        // 查询数据并生成报告
        return new ReportResult(reportId);
    }
}
```

调用者拿到的是尚未必完成的 `CompletableFuture`：

> Illustrative fragment：省略调用类定义。

```java
CompletableFuture<ReportResult> future = reportService.generate("report-42");

future.thenAccept(result -> {
    // 在结果可用后继续处理
});
```

如果调用后立刻 `join()`，调用线程仍会阻塞，异步边界就只剩下线程切换成本。只有调用者能继续做别的工作、返回一个异步响应，或接受“稍后完成”的语义时，`@Async` 才有价值。

---

## 3. 为不同任务配置有界线程池

### 为什么要拆分线程池

把所有任务塞进同一个池会产生故障耦合：报表任务积压，可能让邮件、审计或缓存刷新也排不上队。应按资源特征和可靠性要求拆分，而不是按 Service 类数量拆分。

常见分组：

| 线程池 | 任务特征 | 容量关注点 |
|---|---|---|
| `ioTaskExecutor` | HTTP、数据库、文件等阻塞 I/O | 外部连接池容量、超时、排队时间 |
| `cpuTaskExecutor` | 压缩、加密、复杂计算 | CPU 核数、上下文切换 |
| `notificationExecutor` | 邮件、短信等可独立降级任务 | 限流、重试、是否允许丢失 |

### 已验证可运行示例

下面是一个最小 Maven 示例。它配置命名的有界线程池，执行异步报表任务，并用 Spring 上下文测试验证任务运行在 `io-task-` 线程上。

#### Maven 依赖

> Verified runnable example：依赖已解析，完整测试命令见本节末尾。

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.5.16</version>
        <relativePath/>
    </parent>

    <groupId>example.guide</groupId>
    <artifactId>spring-boot-multithreading-guide-example</artifactId>
    <version>1.0.0-SNAPSHOT</version>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

#### 应用和线程池配置

```java
package example.guide;

import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AsyncGuideApplication {
}
```

```java
package example.guide;

import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfiguration {

    @Bean("ioTaskExecutor")
    public ThreadPoolTaskExecutor ioTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(30);
        executor.setThreadNamePrefix("io-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        return executor;
    }
}
```

这里不需要手动调用 `initialize()`。作为 Bean 返回后，Spring 会执行 `ThreadPoolTaskExecutor` 的生命周期初始化和销毁回调。

`CallerRunsPolicy` 会让提交任务的线程亲自执行被拒绝的任务，形成反压，但它也可能让 HTTP 请求线程突然变慢。只有当“让上游变慢”比“拒绝任务”更符合业务语义时才使用它；关键任务更适合显式失败、持久化重试或消息队列。

#### 异步服务

```java
package example.guide;

public record ReportResult(String reportId, String threadName) {
}
```

```java
package example.guide;

import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    @Async("ioTaskExecutor")
    public CompletableFuture<ReportResult> generate(String reportId) {
        ReportResult result = new ReportResult(
                reportId,
                Thread.currentThread().getName()
        );
        return CompletableFuture.completedFuture(result);
    }
}
```

#### 集成测试

```java
package example.guide;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = AsyncGuideApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class ReportServiceTest {

    @Autowired
    private ReportService reportService;

    @Test
    void runsOnTheNamedExecutor() {
        String callerThread = Thread.currentThread().getName();

        ReportResult result = reportService.generate("report-42").join();

        assertThat(result.reportId()).isEqualTo("report-42");
        assertThat(result.threadName())
                .startsWith("io-task-")
                .isNotEqualTo(callerThread);
    }
}
```

验证命令：

```text
.\mvnw.cmd -s .\target\guide-verification\spring-boot-multithreading\settings.xml -f .\target\guide-verification\spring-boot-multithreading\pom.xml test
```

验证结果：Spring Boot 3.5.16 在 Java 17.0.18 上启动测试上下文；`Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`，构建成功。

---

## 4. 理解 @Async 的代理边界

### 真正提交任务的是代理

Spring Framework 6.2 默认用 `proxy` 模式处理 `@Async`。容器注入给调用者的并不是裸对象，而是带拦截逻辑的代理：

```text
OrderController
      |
      | 1. 调用代理上的 public 方法
      v
ReportService 代理
      |
      | 2. 将调用封装成任务并提交
      v
ioTaskExecutor
      |
      | 3. 工作线程调用目标对象
      v
ReportService 目标方法
```

### 最常见的失效：同类自调用

> Illustrative fragment：展示错误边界。

```java
@Service
public class ReportService {

    public void start(String reportId) {
        generate(reportId); // this.generate(...)，没有经过 Spring 代理
    }

    @Async("ioTaskExecutor")
    public CompletableFuture<ReportResult> generate(String reportId) {
        // 实际仍在调用 start(...) 的线程执行
        return CompletableFuture.completedFuture(build(reportId));
    }
}
```

修复方法是把异步方法放到另一个 Bean，让调用跨越 Bean 边界：

> Illustrative fragment：省略具体结果类型。

```java
@Service
public class ReportCoordinator {

    private final ReportWorker reportWorker;

    public ReportCoordinator(ReportWorker reportWorker) {
        this.reportWorker = reportWorker;
    }

    public CompletableFuture<ReportResult> start(String reportId) {
        return reportWorker.generate(reportId);
    }
}

@Service
public class ReportWorker {

    @Async("ioTaskExecutor")
    public CompletableFuture<ReportResult> generate(String reportId) {
        return CompletableFuture.completedFuture(build(reportId));
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

### 优先返回 CompletableFuture

`@Async` 方法可以返回 `void`、`Future` 或 `CompletableFuture`。业务任务优先使用 `CompletableFuture<T>`，因为调用者可以观察成功、失败并继续组合。

> Illustrative fragment：省略依赖和业务类型。

```java
@Async("ioTaskExecutor")
public CompletableFuture<ReportResult> generate(String reportId) {
    try {
        ReportResult result = buildReport(reportId);
        return CompletableFuture.completedFuture(result);
    } catch (RuntimeException exception) {
        return CompletableFuture.failedFuture(exception);
    }
}
```

调用者必须决定失败如何进入业务流程：

```java
reportService.generate(reportId)
        .thenAccept(this::saveResult)
        .exceptionally(exception -> {
            log.error("生成报告失败，reportId={}", reportId, exception);
            return null;
        });
```

### void 方法的异常不会回传给调用者

`void` 异步方法没有返回通道。其未捕获异常默认只会被记录，调用线程既不能捕获，也不能据此回滚。可以通过 `AsyncUncaughtExceptionHandler` 统一记录，但它不能恢复已经丢失的同步调用语义。

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

如果失败会影响业务状态，不要只依赖日志：返回 `CompletableFuture`、持久化任务状态，或改用带确认与重试机制的消息队列。

### 超时不是强制终止

JDK 17 可以用 `orTimeout` 限制等待时间：

> Illustrative fragment。

```java
CompletableFuture<ReportResult> future = reportService.generate(reportId)
        .orTimeout(5, TimeUnit.SECONDS);
```

超时只改变调用者观察到的 `CompletableFuture` 状态，底层任务不一定停止。取消和 `shutdownNow()` 也依赖中断协作；阻塞 I/O 应在 HTTP、数据库或文件客户端自身配置超时，循环任务应检查中断标志。

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
    public CompletableFuture<Void> rebuild(String reportId) {
        transactionService.rebuildInNewTransaction(reportId);
        return CompletableFuture.completedFuture(null);
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

不要把延迟加载的 JPA Entity、打开的 `InputStream`、`HttpServletRequest` 或可变集合直接交给异步线程。优先传递 ID、不可变 record 或值对象，并在工作线程中重新加载需要的数据。

如果需求是“主事务提交后再异步执行”，应使用 `@TransactionalEventListener(phase = AFTER_COMMIT)` 或 Outbox 模式。事件写法和事务阶段详见 [Spring 事件指南](spring-event-guide.md)。

### ThreadLocal 上下文也不会自动传播

MDC、请求信息、Locale 和安全上下文常依赖 `ThreadLocal`。线程池会复用线程，因此既不能假定上下文自动存在，也不能忘记清理旧上下文。

可以用 `TaskDecorator` 显式复制 MDC：

> Illustrative fragment：依赖 SLF4J MDC；省略与执行器 Bean 的组合。

```java
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

public final class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable task) {
        Map<String, String> callerContext = MDC.getCopyOfContextMap();

        return () -> {
            Map<String, String> workerContext = MDC.getCopyOfContextMap();
            try {
                if (callerContext == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(callerContext);
                }
                task.run();
            } finally {
                if (workerContext == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(workerContext);
                }
            }
        };
    }
}
```

把它设置到 `ThreadPoolTaskExecutor`：

```java
executor.setTaskDecorator(new MdcTaskDecorator());
```

Spring Security 上下文应使用 Spring Security 提供的委托执行器或显式传递所需身份数据。不要把所有 `ThreadLocal` 盲目复制到后台线程；只传播任务真正需要且允许传播的信息。

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

| 问题 | 观察信号 |
|---|---|
| 线程是否一直满载 | active count 与 pool size |
| 队列是否持续积压 | queue size、剩余容量、等待时间 |
| 是否发生拒绝 | 拒绝次数、提交方错误或回退次数 |
| 任务是否变慢或失败 | 任务耗时、超时数、异常数、业务状态 |

线程名前缀必须能区分任务池，例如 `io-task-`、`cpu-task-`。日志只记录“异步失败”没有价值，还要包含任务类型、业务 ID、执行器名和异常。

### 测试行为，不测试 sleep

异步测试不要用“睡 2 秒应该完成”作为断言。它慢、容易抖动，而且不能证明任务使用了正确的执行器。

优先验证：

1. Spring 上下文能创建执行器和异步 Bean；
2. 返回的 `CompletableFuture` 能得到预期结果或异常；
3. 执行线程名匹配指定池，且不同于调用线程；
4. 拒绝策略、超时和关闭行为有独立的边界测试；
5. fire-and-forget 任务用 `CountDownLatch` 或可观测状态协调，而不是固定睡眠。

本指南第 3 节的测试同时覆盖了 Bean 创建、代理拦截和命名执行器选择。

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

| 需求 | 推荐方案 | 不应依赖 |
|---|---|---|
| 单体内短时、可重试、允许进程退出时丢失的后台工作 | 命名的 `@Async` + 有界执行器 | `new Thread` |
| 一个请求内并行组合多个独立 I/O | `CompletableFuture` + 专用 I/O 池 + 客户端超时 | 公共 `ForkJoinPool` |
| 固定时间触发本地任务 | `@Scheduled` + 明确的调度池 | 默认单线程承载多个长任务 |
| 主事务提交后再执行 | `@TransactionalEventListener(AFTER_COMMIT)`；高可靠场景用 Outbox | 直接继承调用者事务 |
| 任务不能丢、需要重试/削峰/跨服务 | 消息队列或持久化任务系统 | 进程内线程池 |
| CPU 密集计算 | 独立小型 CPU 池，接近可用核心数 | 与阻塞 I/O 共用大池 |
| 需要 Spring MVC 异步请求执行器 | 名为 `applicationTaskExecutor` 的 `AsyncTaskExecutor` | 随意命名的普通 `Executor` |

### 上线前检查

- [ ] 每个 `@Async` 方法都经过另一个 Spring Bean 调用，没有同类自调用。
- [ ] 多线程池场景为 `@Async` 显式指定了执行器名称。
- [ ] 核心线程数、最大线程数、队列容量和拒绝策略都有业务依据。
- [ ] 外部 HTTP、数据库和文件 I/O 配置了自身超时。
- [ ] 失败可被调用者、日志、指标或持久化任务状态观察到。
- [ ] 明确了调用者事务不会传播到工作线程。
- [ ] 只传递 ID 或不可变数据，不跨线程共享请求对象和打开的资源。
- [ ] MDC、安全上下文等需要的 `ThreadLocal` 被显式传播并在 `finally` 中清理。
- [ ] 应用关闭等待时间与部署平台的终止宽限期匹配。
- [ ] 对不可丢任务使用了消息队列、Outbox 或持久化调度，而不是内存线程池。
- [ ] 集成测试验证了 Spring 代理和实际执行线程，而不只是 Java 代码能编译。

## References

- [Spring Boot 3.5 — Task Execution and Scheduling](https://docs.spring.io/spring-boot/3.5/reference/features/task-execution-and-scheduling.html)
- [Spring Boot 3.5 — Common Application Properties](https://docs.spring.io/spring-boot/3.5/appendix/application-properties/index.html)
- [Spring Framework 6.2 — Task Execution and Scheduling](https://docs.spring.io/spring-framework/reference/6.2/integration/scheduling.html)
- [Spring Framework 6.2 API — ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Spring Framework 6.2 API — TaskDecorator](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/core/task/TaskDecorator.html)
- [Spring Framework 6.2 API — TransactionSynchronizationManager](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/transaction/support/TransactionSynchronizationManager.html)
- [Java 多线程基础](../Java/multithreading-basics.md)
