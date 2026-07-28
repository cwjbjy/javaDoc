# Spring 事件机制指南

> 本指南循序渐进介绍 Spring 事件机制。从"紧耦合的业务代码"到"事件驱动解耦"，每步只引入一个新概念。
> 基于 Spring Framework 6.x / Spring Boot 3.x。

---

## 目录

1. [为什么需要事件机制](#1-为什么需要事件机制)
2. [入门三步走](#2-入门三步走)
   - [第一层：@EventListener 最简用法](#21-第一层eventlistener-最简用法)
   - [第二层：异步事件 @Async](#22-第二层异步事件-async)
   - [第三层：事务事件 @TransactionalEventListener](#23-第三层事务事件-transactionaleventlistener)
3. [速查清单](#3-速查清单)

---

## 1. 为什么需要事件机制

### 问题起源

假设你在写一个用户注册系统。用户注册成功后，需要做三件事：发欢迎邮件、发新人优惠券、记审计日志。

不用事件机制时，代码大概长这样：

```java
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepo;
    private final EmailService emailService;         // 发邮件
    private final CouponService couponService;       // 发优惠券
    private final AuditLogService auditLogService;   // 记日志

    public UserService(UserRepository userRepo, EmailService emailService,
                       CouponService couponService, AuditLogService auditLogService) {
        this.userRepo = userRepo;
        this.emailService = emailService;
        this.couponService = couponService;
        this.auditLogService = auditLogService;
    }

    public void register(String username, String email) {
        // 1. 核心逻辑：保存用户
        userRepo.save(new User(username, email));

        // 2. 附加操作：发邮件
        emailService.sendWelcome(email, username);
        // 3. 附加操作：发优惠券
        couponService.issueNewUserCoupon(username);
        // 4. 附加操作：记审计日志
        auditLogService.record("用户注册", username);
    }
}
```

**问题在哪里？**

- `UserService` 需要依赖三个和"注册"核心逻辑无关的类——职责混乱
- 需求变更（比如"注册后还要发短信"）就得改 `register()` 方法——违反开闭原则
- 所有操作串行执行，`register()` 的响应时间 = 保存用户 + 发邮件 + 发优惠券 + 记日志的总和
- 单元测试 `register()` 时必须 mock 所有依赖，测试变得臃肿

```
紧耦合的调用链

UserService.register()
    │
    ├── userRepo.save()            ← 核心逻辑
    ├── emailService.sendWelcome() ← 辅助逻辑 A
    ├── couponService.issue()      ← 辅助逻辑 B
    └── auditLogService.record()   ← 辅助逻辑 C

→ register() 知道所有辅助操作的存在
→ 新增辅助操作 = 修改 register()
→ 所有操作阻塞主流程
```

### Spring 事件机制的解决方案

事件机制的核心思想就一句话：**"你只管发布事件，谁关心、怎么处理，你都不用管"**。

```
事件驱动解耦

UserService.register()
    │
    ├── userRepo.save()                            ← 核心逻辑
    └── publisher.publishEvent(注册事件)             ← 发一个事件
                │
                ├── EmailListener.handle()          ← 邮件监听器（独立）
                ├── CouponListener.handle()          ← 优惠券监听器（独立）
                └── AuditLogListener.handle()        ← 审计监听器（独立）

→ register() 只知道"我发了一个事件"
→ 监听器各自独立，新增/删除监听器不影响 register()
→ 监听器可以异步执行，不阻塞主流程
```

`UserService` 从依赖三个类变成只依赖一个 `ApplicationEventPublisher`。新增功能只需加一个监听器，注册逻辑本身不动。

---

## 2. 入门三步走

用一个贯穿场景来演示：**用户注册——注册成功后发欢迎邮件 + 发新人优惠券 + 记审计日志**。

> 前置知识：`@Service`、`@Component` 标记的类会被 Spring 容器管理，实例是 Spring 创建的 Bean。依赖注入和 Bean 管理，请参考 [Spring IOC/DI 指南](spring-ioc-di-guide.md)。
>
> **Spring Boot 自动启用事件**：Spring Boot 项目中，事件机制已自动配置，直接使用 `@EventListener` 即可。如果是纯 Spring Framework 项目（非 Boot），需要确保 Spring 容器正确初始化——事件发布和监听是容器的基础能力，无需额外注解启用。

### 2.1 第一层：@EventListener 最简用法

需求：`register()` 方法保存用户后，自动触发发邮件和记日志，但 `register()` 方法本身不依赖邮件和日志的 Service。

#### 第一步：定义事件类

Spring 4.2+ 支持用**任意对象**作为事件——不需要继承任何父类。如果项目使用 Java 16+（Spring Boot 3.x 要求 Java 17），推荐用 `record` 定义事件——一行代码自动生成构造器、访问器、`equals`/`hashCode`/`toString`：

```java
// record 是不可变数据载体，一行搞定
public record UserRegisterEvent(String username, String email) {}
```

> 如果项目是 Java 15 以下，用传统 POJO（`private final` 字段 + 构造器 + getter）等效。事件命名惯例：`XxxEvent`。事件对象一旦创建就是**不可变的**，避免监听器之间意外修改。

#### 第二步：发布事件

通过 `ApplicationEventPublisher` 把事件广播出去：

```java
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepo;
    private final ApplicationEventPublisher publisher;

    public UserService(UserRepository userRepo, ApplicationEventPublisher publisher) {
        this.userRepo = userRepo;
        this.publisher = publisher;
    }

    public void register(String username, String email) {
        // 核心逻辑：保存用户
        userRepo.save(new User(username, email));

        // 发布事件：广播"用户已注册"
        publisher.publishEvent(new UserRegisterEvent(username, email));

        // register() 不需要知道谁在监听、监听器做了什么
    }
}
```

`ApplicationEventPublisher` 是 Spring 内置的 Bean，直接注入即可。`publishEvent()` 不关心有没有监听器——没有监听器就什么也不发生，不会报错。

#### 第三步：编写监听器

用 `@EventListener` 标记一个方法，Spring 会自动把匹配类型的事件传给它：

```java
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// 监听器 1：发欢迎邮件
@Component
public class EmailListener {

    @EventListener
    public void handleUserRegister(UserRegisterEvent event) {
        System.out.println("发送欢迎邮件给: " + event.email());
        // 实际开发中调用 emailService.sendWelcome(...)
    }
}

// 监听器 2：记审计日志
@Component
public class AuditLogListener {

    @EventListener
    public void handleUserRegister(UserRegisterEvent event) {
        System.out.println("记录审计日志: 用户 " + event.username() + " 已注册");
        // 实际开发中调用 auditLogService.record(...)
    }
}
```

发生了什么？对比一下：

```
紧耦合方式                          事件驱动方式
════════════════                    ════════════════
UserService 依赖 EmailService       UserService 只依赖 Publisher
UserService 依赖 AuditLogService    新增监听器 → 只加新类，不改 UserService
新增操作 → 改 register()            监听器各自独立，可单独测试
所有操作串行阻塞                     监听器默认同步，但可以改成异步（见下节）
```

**执行顺序**：默认情况下，`@EventListener` 按 Spring 容器加载顺序**同步执行**。`publishEvent()` 会逐个调用所有匹配的监听器，全部执行完才返回。也就是说——`register()` 在发完所有邮件、记完所有日志之后才返回。

```
同步执行的调用链

register()
  │
  ├── userRepo.save()
  ├── publisher.publishEvent(事件)
  │       │
  │       ├── EmailListener.handle()       ← 同步等待
  │       ├── AuditLogListener.handle()    ← 同步等待
  │       └── 全部监听器执行完毕
  │
  └── register() 返回
```

> **关键进步**：`UserService.register()` 从"知道所有辅助操作"变成"只知道发了一个事件"。解耦的同时，每个监听器可以独立开发和测试。

---

### 2.2 第二层：异步事件 @Async

第一层的方案已经解耦了，但还有个问题：**监听器是同步的**。发邮件要 2 秒、记日志要 0.5 秒，那 `register()` 就要多等 2.5 秒才能返回。用户体验不好。

需求：监听器异步执行，不阻塞 `register()` 返回。

#### 第一步：启用异步支持

在任意配置类上加 `@EnableAsync`：

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync  // 开启 Spring 的异步方法支持
public class AsyncConfig {
    // Spring Boot 会自动创建默认线程池（见下文注意事项）
}
```

#### 第二步：在监听器上加 @Async

```java
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class EmailListener {

    @Async              // 这个方法在独立线程中执行
    @EventListener
    public void handleUserRegister(UserRegisterEvent event) {
        System.out.println("发送欢迎邮件给: " + event.email());
        // 即使发邮件要 2 秒，也不阻塞 register()
    }
}

@Component
public class CouponListener {

    @Async              // 同样异步执行
    @EventListener
    public void handleUserRegister(UserRegisterEvent event) {
        System.out.println("发放新人优惠券给: " + event.username());
    }
}
```

发生了什么？

```
异步执行的调用链

register()
  │
  ├── userRepo.save()                      ← 核心逻辑
  ├── publisher.publishEvent(事件)
  │       │
  │       ├── EmailListener.handle()       ← 提交到线程池，立刻返回
  │       ├── CouponListener.handle()      ← 提交到线程池，立刻返回
  │       └── AuditLogListener.handle()    ← 同步执行（没加 @Async）
  │
  └── register() 返回                      ← 不等待异步监听器！

后台线程池继续执行：
  ├── [线程-1] 发邮件...（2 秒后完成）
  └── [线程-2] 发优惠券...（1 秒后完成）
```

> **关键进步**：加一个注解，监听器从"阻塞主流程"变成"后台异步执行"。主流程只等同步的监听器（如记日志），异步的先提交、后执行。

#### 异步事件的注意事项

**1. 异常不回传**

异步监听器抛出的异常，`publishEvent()` 看不到——因为它在另一个线程里执行，`register()` 方法早已返回。

```java
@Component
public class EmailListener {

    @Async
    @EventListener
    public void handleUserRegister(UserRegisterEvent event) {
        throw new RuntimeException("邮件发送失败");
        // register() 不会感知到这个异常！
    }
}
```

解决方案：在监听器内部 `try-catch` 并记录日志，或使用 `AsyncUncaughtExceptionHandler` 全局处理。

**2. 默认线程池不适用于生产**

`@EnableAsync` 默认使用 `SimpleAsyncTaskExecutor`——每个任务创建一个新线程，没有上限。高并发时可能导致线程数爆炸。

> **开发 vs 生产**：开发环境用默认线程池（方便调试），生产环境必须配置自定义线程池（控制资源消耗）。

生产环境的线程池配置：

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean("eventExecutor")  // 自定义线程池
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);         // 核心线程数
        executor.setMaxPoolSize(8);          // 最大线程数
        executor.setQueueCapacity(100);      // 队列容量（满了之后抛 RejectedExecutionException）
        executor.setThreadNamePrefix("event-");  // 线程名前缀，方便排查
        executor.initialize();
        return executor;
    }
}
```

然后指定监听器使用这个线程池：

```java
@Async("eventExecutor")  // 使用自定义线程池
@EventListener
public void handleUserRegister(UserRegisterEvent event) { ... }
```

---

### 2.3 第三层：事务事件 @TransactionalEventListener

前两层解决了耦合和阻塞问题，但还有一个隐蔽的坑：**事务回滚时，事件已经发出去了**。

假设 `register()` 加了事务，流程是这样的：

```java
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    @Transactional(rollbackFor = Exception.class)
    public void register(String username, String email) {
        userRepo.save(new User(username, email));   // INSERT 用户

        publisher.publishEvent(new UserRegisterEvent(username, email));
        // ↑ 事务还没提交！数据可能被回滚！

        // 假设这一步抛异常
        if (true) throw new RuntimeException("注册失败");
        // → 事务回滚，用户没保存成功
        // → 但事件已经发出去了！监听器会发邮件给一个不存在的用户！
    }
}
```

```java
@Component
public class EmailListener {

    @EventListener
    public void handleUserRegister(UserRegisterEvent event) {
        // 监听器在事务提交前就执行了
        // 如果事务最终回滚，这封邮件就发错了！
        emailService.sendWelcome(event.email(), event.username());
    }
}
```

**问题**：`publishEvent()` 默认在事务提交**前**就触发监听器。如果事务最终回滚，已经执行的操作（发邮件、发优惠券）无法撤回。

#### 解决方案：@TransactionalEventListener

用 `@TransactionalEventListener` 替换 `@EventListener`，让监听器在事务**提交后**才执行：

```java
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Component
public class EmailListener {

    // 只在事务提交后执行
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserRegister(UserRegisterEvent event) {
        emailService.sendWelcome(event.email(), event.username());
        // 此时事务已提交，数据已落盘，可以放心发邮件
    }
}
```

执行时序对比：

```
@EventListener（普通事件监听器）        @TransactionalEventListener（事务事件监听器）
═════════════════════════════          ═══════════════════════════════════════

事务开始                                事务开始
  │                                      │
  ├── saveUser()                         ├── saveUser()
  ├── publishEvent()                     ├── publishEvent()  ← 事件挂起，不立刻执行
  │     └── 监听器立刻执行 ❌            │
  ├── 可能抛异常 → 回滚                  ├── 抛异常 → 回滚
  └── 事件已执行，无法撤回                │     └── 监听器不执行（事件被丢弃） ✅
                                          │
                                          ├── 正常提交
                                          │     └── 监听器执行 ✅
                                          └── 事务结束
```

#### 四种事务阶段

`TransactionPhase` 枚举提供四个阶段，根据业务需求选择：

```java
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class CouponListener {

    // 事务提交后执行（最常用：发通知、发消息）
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAfterCommit(UserRegisterEvent event) {
        couponService.issueNewUserCoupon(event.username());
    }

    // 事务回滚后执行（补偿操作：清理外部系统资源）
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void onAfterRollback(UserRegisterEvent event) {
        System.out.println("注册失败，清理外部资源: " + event.username());
    }

    // 事务完成（提交或回滚）后执行（清理操作：无论成败都执行）
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
    public void onAfterCompletion(UserRegisterEvent event) {
        System.out.println("事务完成（提交或回滚）: " + event.username());
    }

    // 事务提交前执行（很少用：在提交前做最后校验）
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onBeforeCommit(UserRegisterEvent event) {
        // 在提交前做最后的验证或补充操作
    }
}
```

```
四种阶段的使用场景

BEFORE_COMMIT        → 提交前补充数据、最后校验（很少用到）
AFTER_COMMIT（常用）  → 发邮件、发短信、发 MQ 消息
AFTER_ROLLBACK       → 清理外部资源、记录失败日志
AFTER_COMPLETION     → 无论成败都执行的清理操作
```

#### 如果没有事务呢？

`@TransactionalEventListener` 默认**只在有事务的时候才生效**。如果 `publishEvent()` 不在事务中，监听器不会被触发。

```java
// 没有 @Transactional → 事件不会被 @TransactionalEventListener 监听
public void nonTransactionalMethod() {
    publisher.publishEvent(new UserRegisterEvent("test", "test@example.com"));
    // EmailListener 的 @TransactionalEventListener 不会被触发！
}
```

如果需要"无事务时也执行"，设置 `fallbackExecution = true`：

```java
@TransactionalEventListener(
    phase = TransactionPhase.AFTER_COMMIT,
    fallbackExecution = true  // 无事务时也执行（降级为普通 @EventListener 的行为）
)
public void handleUserRegister(UserRegisterEvent event) {
    // 有事务 → 提交后执行
    // 无事务 → 立刻执行
}
```

### 本节回顾

```
三层递进总结

第一层 @EventListener                       → 解耦：发布者不依赖监听器
第二层 + @Async + @EnableAsync              → 异步：不阻塞主流程
第三层 + @TransactionalEventListener        → 安全：事务提交后才执行

每一层你只比上一层多学一个概念，场景保持一致——用户注册。
```

```
选择决策图

需要解耦吗？
  │
  ├── 是 → 用 @EventListener
  │         │
  │         ├── 需要异步吗？ → 加 @Async
  │         │
  │         └── 事务场景吗？
  │               ├── 需要提交后执行 → @TransactionalEventListener(AFTER_COMMIT)
  │               └── 需要回滚后执行 → @TransactionalEventListener(AFTER_ROLLBACK)
  │
  └── 否 → 直接在方法里调 Service
```

---

## 3. 速查清单

### 3.1 核心注解速查

```
注解                             来源包                               作用
══════════════════════════════════════════════════════════════════════════════
@EventListener                  org.springframework.context.event      标记监听方法
@TransactionalEventListener     org.springframework.transaction.event  事务绑定监听
@Async                          org.springframework.scheduling         异步执行监听方法
@EnableAsync                    org.springframework.scheduling         启用异步支持（加在配置类上）
@Order                          org.springframework.core               指定同类型监听器的执行顺序
```

### 3.2 事务阶段速查

```
TransactionPhase            监听器执行的时机               常用场景
══════════════════════════════════════════════════════════════════════
BEFORE_COMMIT              事务提交前                    数据最终校验、补充操作
AFTER_COMMIT（最常用）      事务提交后                    发邮件、发短信、发 MQ 消息
AFTER_ROLLBACK             事务回滚后                    清理外部资源、记录失败日志
AFTER_COMPLETION           事务完成（提交或回滚）后       无论成败都执行的清理
```

### 3.3 同步 vs 异步 vs 事务事件 对比

```
类型                      注解                                 执行时机            阻塞主流程    事务安全
═══════════════════════════════════════════════════════════════════════════════════════════════
同步事件                  @EventListener                       publishEvent() 时   是           否
异步事件                  @EventListener + @Async              独立线程             否           否
事务事件                  @TransactionalEventListener          事务阶段             取决于配置   是
事务 + 异步事件           @TransactionalEventListener + @Async  事务提交后异步执行   否           是
```

### 3.4 常见陷阱速查

```
陷阱                                  症状                                   解决方案
═════════════════════════════════════════════════════════════════════════════════════════════
异步监听器异常被吞               异常不报错，操作静默失败             监听器内部 try-catch + 日志
@TransactionalEventListener      无事务时不触发监听器               设置 fallbackExecution = true
在无事务上下文中不生效
默认线程池无上限                高并发时创建大量线程                 配置自定义 ThreadPoolTaskExecutor
事件类字段可变                  监听器之间互相影响                   事件类字段用 final，构造后不可变
监听器抛异常阻断后续监听器      一个报错，后面的不执行               每个监听器内部 try-catch
```

### 3.5 用法模板

```java
// === 事件类（用 record 定义，一行搞定）===
public record UserRegisterEvent(String username, String email) {}

// === 发布者 ===
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepo;
    private final ApplicationEventPublisher publisher;

    public UserService(UserRepository userRepo, ApplicationEventPublisher publisher) {
        this.userRepo = userRepo;
        this.publisher = publisher;
    }

    @Transactional(rollbackFor = Exception.class)
    public void register(String username, String email) {
        // 核心逻辑
        userRepo.save(new User(username, email));
        // 发布事件
        publisher.publishEvent(new UserRegisterEvent(username, email));
    }
}

// === 同步监听器（简单场景）===
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AuditLogListener {

    @EventListener
    public void handleUserRegister(UserRegisterEvent event) {
        logService.record("用户注册", event.username());
    }
}

// === 异步事务监听器（生产推荐写法）===
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.event.TransactionPhase;

@Component
public class EmailListener {

    @Async("eventExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleUserRegister(UserRegisterEvent event) {
        try {
            emailService.sendWelcome(event.email(), event.username());
        } catch (Exception e) {
            log.error("发送欢迎邮件失败: {}", event.email(), e);
            // 异步监听器异常不会回传，必须自己处理
        }
    }
}
```
