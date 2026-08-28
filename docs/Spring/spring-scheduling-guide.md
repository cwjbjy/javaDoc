# Spring 定时任务指南

> 本指南循序渐进介绍 Spring 定时任务（`@EnableScheduling` + `@Scheduled`）。从"为什么需要定时执行"到"生产级调度线程池配置"，每步只引入一个新概念。
> 基于 Spring Framework 6.x / Spring Boot 3.x。

**读者画像**：有一般编程经验、刚接触 Spring 定时任务的开发者。
**学完你将能够**：写出可用的 `@Scheduled` 任务，选对触发方式，配置合适的调度线程池，并在真实项目中识别定时任务相关代码。
**范围外**：线程池通用概念（核心线程数、队列、拒绝策略）归 [多线程基础](../Java/multithreading-basics.md)；分布式定时任务方案（Quartz、Elastic Job、XXL-JOB 等）超出本指南范围——多实例集群中同一任务会在每台机器重复执行，需要它们保证"集群中只有一个实例执行"。

---

## 目录

1. [入门：@EnableScheduling + @Scheduled](#1-入门enablescheduling-scheduled)
   - [1.1 打开开关：@EnableScheduling](#11-打开开关enablescheduling)
   - [1.2 声明任务：@Scheduled 最简用法](#12-声明任务scheduled-最简用法)
   - [1.3 启动观察与任务方法的约束](#13-启动观察与任务方法的约束)
2. [选型：cron / fixedRate / fixedDelay](#2-选型cron-fixedrate-fixeddelay)
   - [2.1 fixedDelay：从"完成"起算](#21-fixeddelay从完成起算)
   - [2.2 fixedRate：从"开始"起算](#22-fixedrate从开始起算)
   - [2.3 initialDelay：启动延迟与一次性任务](#23-initialdelay启动延迟与一次性任务)
   - [2.4 cron：六段表达式与常用宏](#24-cron六段表达式与常用宏)
   - [2.5 三选一决策表](#25-三选一决策表)
3. [进阶：默认单线程与自定义调度线程池](#3-进阶默认单线程与自定义调度线程池)
   - [3.1 默认调度器只有一个线程](#31-默认调度器只有一个线程)
   - [3.2 方式一：spring.task.scheduling 配置项](#32-方式一springtaskscheduling-配置项)
   - [3.3 方式二：SchedulingConfigurer 编程式配置](#33-方式二schedulingconfigurer-编程式配置)
   - [3.4 两种方式怎么选](#34-两种方式怎么选)

---

## 1. 入门：@EnableScheduling + @Scheduled

### 1.1 打开开关：@EnableScheduling

Spring 容器默认**不会**扫描 `@Scheduled` 注解。要让它生效，需要在一个 `@Configuration` 类（`@SpringBootApplication` 也算）上标 `@EnableScheduling`：

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling   // 打开定时任务开关
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

这个注解做的事情很集中：向容器注册一个 `ScheduledAnnotationBeanPostProcessor`。应用启动时，这个后处理器会扫描所有 Bean 的方法，凡是标了 `@Scheduled` 的，就把它注册到调度器里，按注解声明的规则触发。

```
@EnableScheduling 的工作流程

应用启动
    │
    ▼
注册 ScheduledAnnotationBeanPostProcessor
    │
    ▼
扫描容器中所有 Bean 的方法
    │
    ├── 方法上有 @Scheduled → 注册到调度器，按规则触发
    └── 方法上没有          → 忽略
```

> **关键认知**：`@EnableScheduling` 只是开关。开关本身不会"执行"任何东西——真正被调度的是 `@Scheduled` 方法。只有开关、没有任务方法时，应用正常运行，什么都不会发生，也不会报错。

### 1.2 声明任务：@Scheduled 最简用法

任务是 `@Component` 类里的一个普通方法，加上 `@Scheduled` 即可：

```java
// 示例状态：说明性片段。ProductRepository 为示例领域类型。
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ExpiredOrderCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(ExpiredOrderCleanupTask.class);

    private final ProductRepository productRepository;

    public ExpiredOrderCleanupTask(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // 每 60 秒执行一次：清理过期订单
    @Scheduled(fixedRate = 60_000)
    public void cleanupExpiredOrders() {
        int removed = productRepository.deleteExpiredOrders();
        log.info("清理过期订单 {} 条", removed);
    }
}
```

启动应用后，`cleanupExpiredOrders()` 就会每 60 秒被调用一次。注意两件事：

- **任务方法是普通 Bean 方法**：依赖注入照常生效——构造函数里注入的 `ProductRepository` 直接可用。这正是手写 `ScheduledExecutorService` 做不到的。
- **触发规则在注解上**：想改成 5 分钟一次，改 `fixedRate = 300_000` 即可，业务代码一行不动。

### 1.3 启动观察与任务方法的约束

**任务方法有三条约束**（违反会导致注册失败或行为异常）：

1. **必须是 `void` 返回**：调度器拿到返回值也没地方放。Spring 6 也支持返回响应式类型（如 `Publisher`）的特殊用法，但常规任务一律 `void`。
2. **不能有参数**：调度器触发时不知道传什么给你。任务要用的对象走依赖注入，不走参数。
3. **方法所在的类必须被 Spring 管理**（`@Component` 等），且该类的实例得被后处理器扫描到——纯 `new` 出来的对象无效。

**首次执行时机**：任务不是"启动后立刻跑"。`fixedRate` / `fixedDelay` 任务的首次执行默认在启动后一个周期处（想要立即执行或延迟执行，用 2.3 节的 `initialDelay`）。

**一个方法可以标多个 `@Scheduled`**：`@Scheduled` 是可重复注解，同一方法标两次 = 两套独立触发规则，各自独立执行，互不影响。用之前要想清楚：它们可能在时间上重叠。

> 到这里你已经能让一个任务"按点执行"了。但 `fixedRate` 到底是什么意思？和 `fixedDelay`、`cron` 有什么区别？下一章是选型，也是实际使用中问得最多的地方。

---

## 2. 选型：cron / fixedRate / fixedDelay

`@Scheduled` 有三种触发方式，对应三个应用场景。它们的区别本质上是**计时起点不同**。

```
API             用法示例                              计时起点                典型场景
══════════════════════════════════════════════════════════════════════════════════════════════════
cron            @Scheduled(cron = "0 0 2 * * MON")    按日历时钟触发          每周一、每月 1 号等日历任务
fixedDelay      @Scheduled(fixedDelay = 60_000)        上一次执行"完成"后      耗时不定、必须串行、不允许重叠
fixedRate       @Scheduled(fixedRate = 60_000)         上一次执行"开始"后      心跳、采样等间隔要求稳定的轻量任务
initialDelay    搭配上面三种使用，或单独 = 一次性       启动后延迟指定时长       预热缓存、等依赖初始化
```

### 2.1 fixedDelay：从"完成"起算

```java
// 示例状态：说明性片段。
@Scheduled(fixedDelay = 60_000)   // 上一次执行"完成"后，再等 60 秒
public void sendBatch() {
    // 发一批通知，耗时不固定
}
```

`fixedDelay` 的计时起点是**上一次执行的结束时刻**：

```
fixedDelay 时间轴

执行①(耗时 30s)    等 60s    执行②(耗时 40s)    等 60s    执行③
├──────────┤ └────────────┘ ├──────────┤ └────────────┘ ├──
0          30              90         130             190

→ 相邻两次执行永不重叠
→ 实际周期 = 任务耗时 + 60 秒，周期随耗时浮动
```

**适合场景**：任务耗时不确定、且两次执行**不允许重叠**的操作——比如"发完上一批邮件再发下一批"。因为下一次执行要等上一次**彻底结束**才开始计时，天然串行。

### 2.2 fixedRate：从"开始"起算

```java
// 示例状态：说明性片段。
@Scheduled(fixedRate = 60_000)   // 每 60 秒触发一次，从上一次执行"开始"计时
public void heartbeat() {
    // 心跳上报，耗时极短
}
```

`fixedRate` 的计时起点是**上一次执行的开始时刻**：

```
fixedRate 时间轴（任务耗时 < 周期，正常情况）

执行①   等          执行②   等          执行③
├──────┤ └──────────┘ ├──────┤ └──────────┘ ├──
0      10            60     70            120

→ 相邻两次"开始"时刻间隔恒为 60 秒
→ 只要任务耗时小于周期，行为与 fixedDelay 看起来一样
```

**关键差异在任务超时的时候**。如果任务耗时超过周期：

```
fixedRate 时间轴（任务耗时 80s > 周期 60s）

执行①（80 秒，线程被占满）
├──────────────────────────┤
0                          80
                            ↑ 60 秒时本应触发执行②

→ 单线程调度器：执行②排队，等执行①结束后立即执行
→ 多线程调度器：执行②在另一个线程上与执行①并行，两者重叠运行
```

**适合场景**：执行间隔要求稳定、可预测的"采样型"任务（心跳、指标采集），且任务本身要足够快。**选错的最常见后果**：任务变慢后，单线程调度器里任务开始积压排队，执行时间点整体漂移；多线程调度器里则出现并行重叠，重复处理同一批数据。

### 2.3 initialDelay：启动延迟与一次性任务

`initialDelay` 控制**首次执行**的延迟，可以和其他属性组合：

```java
// 示例状态：说明性片段。
@Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
public void syncFromUpstream() {
    // 等应用启动 30 秒后跑第一次（给下游依赖初始化留时间），之后每 60 秒一次
}
```

单独使用 `initialDelay`（不写 `fixedRate` / `fixedDelay` / `cron`）时，任务是**一次性任务**——启动后延迟一段时间执行一次，之后不再触发：

```java
// 示例状态：说明性片段。
@Scheduled(initialDelay = 10_000)
public void warmUpCache() {
    // 启动 10 秒后预热缓存，只跑这一次
}
```

### 2.4 cron：六段表达式与常用宏

需要"每周一凌晨 2 点""每月 1 号 0 点"这类日历规则时，`fixedRate` / `fixedDelay` 表达不了，用 `cron`：

```java
// 示例状态：说明性片段。
@Scheduled(cron = "0 0 2 * * MON")   // 每周一凌晨 2 点整
public void weeklyReport() {
    // 生成周报
}
```

Spring 的 cron 表达式是**六段**格式（注意：比传统 Unix cron 的五段多一个"秒"，最前面的字段是秒）：

```
┌───────────── 秒 (0-59)
│ ┌───────────── 分 (0-59)
│ │ ┌───────────── 时 (0-23)
│ │ │ ┌───────────── 日 (1-31)
│ │ │ │ ┌───────────── 月 (1-12 或 JAN-DEC)
│ │ │ │ │ ┌───────────── 周 (0-7，0 和 7 都表示周日，或 MON-SUN)
│ │ │ │ │ │
* * * * * *
```

常用的几个例子：

```
表达式                    含义
═══════════════════════════════════════════════════════════
0 0 2 * * *             每天凌晨 2 点整
0 */30 * * * *          每 30 分钟（在每小时的第 0、30 分）
*/5 * * * * *           每 5 秒
0 0 0 1 * *             每月 1 号 0 点
0 0 0 ? * MON           每周一 0 点（? 表示"不指定"，日和周只需指定其一）
```

不想背表达式时，Spring 内置了常用**宏**，直接当 cron 值用：

```java
// 示例状态：说明性片段。
@Scheduled(cron = "@daily")   // 等价于 "0 0 0 * * *"，每天 0 点
public void dailyTask() { }

@Scheduled(cron = "@hourly")  // 等价于 "0 0 * * * *"，每小时整点
public void hourlyTask() { }
```

```
宏            等价表达式          含义
══════════════════════════════════════════════════
@yearly      0 0 0 1 1 *      每年 1 月 1 日 0 点
@monthly     0 0 0 1 * *      每月 1 日 0 点
@weekly      0 0 0 * * 0      每周日 0 点
@daily       0 0 0 * * *      每天 0 点
@hourly      0 0 * * * *      每小时整点
```

**cron 的时区**：cron 按"日历时间"触发，必然依赖时区。未指定时，表达式按 JVM 默认时区（`TimeZone.getDefault()`）解析；也可以显式用 `zone` 属性指定：

```java
// 示例状态：说明性片段。
@Scheduled(cron = "0 0 2 * * *", zone = "Asia/Shanghai")
public void atTwoAm() {
    // 无论服务器部署在哪，都按北京时间凌晨 2 点触发
}
```

```
cron 时区的解析路径

@Scheduled(cron = "0 0 2 * * *")
    │
    ├── 指定了 zone = "Asia/Shanghai" → 按显式时区解析（推荐，与 JVM 设置无关）
    └── 未指定 zone                  → 按 TimeZone.getDefault() 解析
```

**推荐做法**：显式写 `zone = "Asia/Shanghai"`。**不是必须**——如果项目确保永远部署在中国时区服务器、且启动时已设 `TimeZone.setDefault("Asia/Shanghai")`，不写 `zone` 也不会错。但显式写有两个好处：

1. 不依赖 JVM 默认时区的设置顺序，换了部署环境也不会错点；
2. 代码自文档化——看注解就知道任务的业务时区，不用去启动类里翻初始化逻辑。

### 2.5 三选一决策表

```
你的需求                                    选择              原因
══════════════════════════════════════════════════════════════════════════════
固定日历时刻（每周一、每月 1 号）            cron             只有 cron 能表达日历规则
周期执行，两次不允许重叠，耗时不定           fixedDelay       从"完成"起算，天然串行
周期执行，间隔要求稳定（采样、心跳）         fixedRate        从"开始"起算，间隔恒定
启动后延迟执行一次（预热、初始化）           initialDelay     单独使用即一次性任务
```

选错的代价：`fixedRate` 用在耗时长的任务上会出现重叠或积压；`fixedDelay` 用在需要稳定间隔的采样任务上，周期会随耗时漂移；`cron` 写错段数（写成五段）任务根本不会触发。

---

## 3. 进阶：默认单线程与自定义调度线程池

### 3.1 默认调度器只有一个线程

第 1 章的最简用法跑起来没有任何问题，但它藏着一个生产环境最常见的坑：**Spring Boot 自动配置的调度线程池默认只有 1 个线程**。

这意味着：**所有 `@Scheduled` 任务在同一个线程里排队执行**。一个任务阻塞，后面所有任务全部拖延——包括那些 1 秒执行一次的轻量任务：

```java
// 示例状态：说明性片段。演示默认单线程的"串行阻塞"问题。
@Component
public class ProblemDemoTask {

    // 任务 A：耗时任务，一次 2 分钟
    @Scheduled(fixedDelay = 60_000)
    public void slowTask() throws InterruptedException {
        Thread.sleep(120_000);   // 模拟调用慢速下游
    }

    // 任务 B：心跳任务，本应每 10 秒一次
    @Scheduled(fixedRate = 10_000)
    public void heartbeatTask() {
        // 心跳上报
    }
}
```

```
默认单线程调度器的执行状况

线程 [scheduling-1]
├─ 任务A（2 分钟）──────────────────┤
                                    ├─ 任务B（迟到 110 秒）
                                    ├─ 任务B（迟到 100 秒）
                                    └─ ...

→ 任务 B 每 10 秒一次的设计完全失效，全部积压在任务 A 后面
→ 更糟的是：慢任务把线程占死，紧急任务也无法插队
```

**为什么会这样？** Spring Boot 检测到 `@EnableScheduling` 后自动配置一个 `ThreadPoolTaskScheduler`，其 `pool.size` 默认值就是 1（对应配置项 `spring.task.scheduling.pool.size` 的默认值）。单线程保证任务顺序执行，代价就是上面的互相拖累。

解决思路有两个：**调大默认线程池**（3.2），或者**完全自定义调度器**（3.3）。

### 3.2 方式一：spring.task.scheduling 配置项

只想"给调度器多几个线程"时，改配置就够了：

```yaml
# application.yml
spring:
  task:
    scheduling:
      pool:
        size: 4 # 调度线程数，默认 1
      thread-name-prefix: my-sched- # 线程名前缀，默认 "scheduling-"
      shutdown:
        await-termination: true # 应用关闭时等待任务跑完，默认 false
        await-termination-period: 30s # 最多等待时长
```

```
spring.task.scheduling 常用配置键（Spring Boot 3.x）

配置键                                          默认值        作用
══════════════════════════════════════════════════════════════════════════════
spring.task.scheduling.pool.size               1           调度线程池最大线程数
spring.task.scheduling.thread-name-prefix      scheduling- 调度线程名前缀（日志排查时很有用）
spring.task.scheduling.shutdown.await-termination   false  关闭时是否等待任务完成
spring.task.scheduling.shutdown.await-termination-period  —   关闭时最多等待多久
```

改完重启，任务 B 就能在独立的调度线程上准点执行。

**绝大多数项目，只配 `pool.size` 就够了。** 定时任务通常就那么几个（报表、清理、同步），线程池调大到 4~8 个，一个慢任务最多占一个线程，其他任务照常运行——不需要 `SchedulingConfigurer`。升级到 3.3 的硬标准是：需要自定义拒绝策略（如异常时告警而非静默丢弃）、需要 `TaskDecorator` 透传 MDC 或全链路 TraceId、或需要用完全不同的 `TaskScheduler` 实现。

### 3.3 方式二：SchedulingConfigurer 编程式配置

需要自定义线程名规则、拒绝策略，或干脆换成其他 `TaskScheduler` 实现时，实现 `SchedulingConfigurer` 接口，自己创建 `ThreadPoolTaskScheduler`。

**注意**：`poolSize`、`thread-name-prefix`、`shutdown` 这些在 3.2 节的配置项中都能配，不需要在 `SchedulingConfigurer` 里重复。下面示例聚焦配置项**做不到**的事——自定义拒绝策略（线程池满时打告警日志，而非默认 `AbortPolicy` 抛异常到虚空）：

```java
// 示例状态：说明性片段。可放入应用扫描路径内的 @Configuration 类。
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulingConfig implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SchedulingConfig.class);

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

        // poolSize、thread-name-prefix、shutdown 这些用配置项即可（3.2 节），此处不重复

        // 拒绝策略：线程池满时，记录告警日志后丢弃，而非默认 AbortPolicy 抛异常到虚空
        scheduler.setRejectedExecutionHandler((runnable, executor) ->
            log.error("调度线程池已满，任务被丢弃: {}", runnable.toString())
        );

        scheduler.initialize();
        taskRegistrar.setScheduler(scheduler);
    }
}
```

关键点：

- **`initialize()` 必须调用**：`ThreadPoolTaskScheduler` 是 Spring 生命周期 Bean，手动 `new` 出来的实例要显式初始化，否则内部线程池不会创建。
- **`setScheduler` 覆盖自动配置**：显式设置了 `TaskScheduler` 后，`spring.task.scheduling.*` 配置项**仍然生效**（`poolSize` 等基础属性由配置项接管），但自定义的拒绝策略等以代码为准。

### 3.4 两种方式怎么选

```
场景                                      选择
══════════════════════════════════════════════════════════════
只关心线程数量                            配置项（3.2）
需要自定义拒绝策略（告警日志等）         配置项 + SchedulingConfigurer（3.3）
两种同时出现                             配置项管基础属性，SchedulingConfigurer 管代码级定制
```

**一个常见的混淆**：`@Scheduled` 和 `@Async` 用的是**两个不同的线程池**。`@Scheduled` 走 `TaskScheduler`（`spring.task.scheduling.*`），`@Async` 走 `TaskExecutor`（`spring.task.execution.*`）。给 `@Async` 配了 10 个线程，不会让定时任务变快；反之亦然。`@Async` 的详细用法见 [Spring Boot 多线程指南](spring-boot-multithreading-guide.md)，线程池通用概念（核心线程数、队列、拒绝策略）见 [多线程基础](../Java/multithreading-basics.md)。

---
