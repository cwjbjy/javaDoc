# Spring 事务管理（@Transactional）指南

> 本指南循序渐进介绍 Spring 声明式事务管理（@Transactional）。从"手动管理事务的痛点"到"一个注解搞定"，每步只引入一个新概念。
> 基于 Spring Framework 6.x / Spring Boot 3.x。

---

## 目录

1. [为什么需要事务管理](#1-为什么需要事务管理)
2. [入门三步走](#2-入门三步走)
   - [第一层：@Transactional 最简用法](#21-第一层transactional-最简用法)
   - [第二层：rollbackFor 控制回滚](#22-第二层rollbackfor-控制回滚)
   - [第三层：事务传播行为](#23-第三层事务传播行为)
3. [事务隔离级别](#3-事务隔离级别)
4. [事务失效的常见陷阱](#4-事务失效的常见陷阱)
5. [多数据源下的事务管理](#5-多数据源下的事务管理)
6. [速查清单](#6-速查清单)

---

## 1. 为什么需要事务管理

### 问题起源

假设你在写一个银行转账系统：从账户 A 扣 1000 元，给账户 B 加 1000 元。这两步必须**要么全成功，要么全失败**——如果扣款成功但加款失败，钱就"蒸发"了。

不用任何框架，只用 JDBC 手动管理事务，代码长这样：

```java
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class TransferService {

    public void transfer(Long fromId, Long toId, Double amount) {
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/bank", "root", "123456");
            conn.setAutoCommit(false);  // 关闭自动提交，开启事务

            // 第一步：从 A 扣款
            PreparedStatement ps1 = conn.prepareStatement(
                "UPDATE account SET balance = balance - ? WHERE id = ?");
            ps1.setDouble(1, amount);
            ps1.setLong(2, fromId);
            ps1.executeUpdate();

            // 第二步：给 B 加款
            PreparedStatement ps2 = conn.prepareStatement(
                "UPDATE account SET balance = balance + ? WHERE id = ?");
            ps2.setDouble(1, amount);
            ps2.setLong(2, toId);
            ps2.executeUpdate();

            conn.commit();  // 两步都成功 → 提交
        } catch (Exception e) {
            if (conn != null) {
                try {
                    conn.rollback();  // 出错 → 回滚
                } catch (SQLException ex) {
                    ex.printStackTrace();
                }
            }
            throw new RuntimeException("转账失败", e);
        } finally {
            if (conn != null) {
                try { conn.close(); } catch (SQLException e) { e.printStackTrace(); }
            }
        }
    }
}
```

**问题在哪里？**

- **样板代码淹没业务逻辑**：真正有价值的是两行 UPDATE，但你写了 30 行事务管理代码
- **容易遗漏**：忘了 `setAutoCommit(false)`？事务不生效。忘了 `rollback()`？数据不一致。忘了 `close()`？连接泄漏
- **无法组合**：如果转账后还要记录日志、发通知，每个操作都要事务，它们怎么合并成一个大事务？

```
手动事务的代码分布

你写的代码量
  │
  │  ██        业务逻辑（2 行 UPDATE）
  │  ████████  事务管理（commit / rollback / close / try-catch）
  │
  └──────────────→ 维护成本
```

### Spring 的解决方案

Spring 声明式事务的核心思想就一句话：**"你只管写业务逻辑，事务的开启、提交、回滚全部由框架代劳"**。

```
你写的（JDBC 手动方式）           Spring @Transactional 帮你做的
──────────────────────           ────────────────────────────────
conn.setAutoCommit(false)  ──→   方法执行前自动开启事务
conn.commit()              ──→   方法正常返回后自动提交
conn.rollback()           ──→   方法抛异常时自动回滚
conn.close()              ──→   连接由连接池管理，你不用管
```

### 事务的 ACID 特性

在继续之前，先理解事务的四个基本特性——**ACID**：

| 字母 | 名称   | 含义                         | 类比                   |
| ---- | ------ | ---------------------------- | ---------------------- |
| A    | 原子性 | 要么全部成功，要么全部回滚   | "要么全做，要么不做"   |
| C    | 一致性 | 事务前后数据必须处于合法状态 | "转账前后总金额不变"   |
| I    | 隔离性 | 并发事务之间互不干扰         | "两人同时转账互不影响" |
| D    | 持久性 | 事务提交后数据永久保存       | "提交了就不可撤销"     |

> 本指南第 2 节聚焦 A（原子性）——怎么保证"全做或全不做"；第 3 节聚焦 I（隔离性）——并发时怎么互不干扰。C 和 D 由数据库引擎保障，开发者通常不需直接处理。

---

## 2. 入门三步走

用一个贯穿场景来演示：**银行转账——从账户 A 扣钱，给账户 B 加钱**。

> 前置知识：`@Service` 标记的类会被 Spring 容器管理，实例是 Spring 创建的 Bean。依赖注入和 Bean 管理，请参考 [Spring IOC/DI 指南](spring-ioc-di-guide.md)。
>
> **Spring Boot 自动启用事务**：Spring Boot 项目中，事务管理器已自动配置，直接使用 `@Transactional` 即可。如果是纯 Spring Framework 项目（非 Boot），需要在配置类上加 `@EnableTransactionManagement` 来启用事务支持。

### 2.1 第一层：@Transactional 最简用法

需求：`transfer()` 方法执行两步数据库操作，要么全成功要么全回滚。

只需在方法上加一个注解：

```java
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private final AccountRepository accountRepo;

    // 构造器注入（@Autowired 可省略，Spring 4.3+ 单构造器自动注入）
    public TransferService(AccountRepository accountRepo) {
        this.accountRepo = accountRepo;
    }

    // 一个注解搞定事务管理
    @Transactional
    public void transfer(Long fromId, Long toId, Double amount) {
        // 第一步：从 A 扣款
        accountRepo.debit(fromId, amount);

        // 第二步：给 B 加款
        accountRepo.credit(toId, amount);

        // 如果这一行抛异常，上面的两步操作全部回滚
        // 如果正常执行完，事务自动提交
    }
}
```

`AccountRepository` 用 Spring Data JPA 定义，数据库操作直接通过方法调用完成：

```java
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, Long> {

    // 自定义扣款方法
    @Modifying
    @Query("UPDATE Account a SET a.balance = a.balance - ?2 WHERE a.id = ?1")
    void debit(Long id, Double amount);

    // 自定义加款方法
    @Modifying
    @Query("UPDATE Account a SET a.balance = a.balance + ?2 WHERE a.id = ?1")
    void credit(Long id, Double amount);
}
```

发生了什么？对比一下：

```
JDBC 手动事务                         @Transactional
════════════════                      ═══════════════
conn.setAutoCommit(false)     →        （框架自动开启）
try {                        →        void transfer(...) {
    // 业务逻辑                        // 业务逻辑
    conn.commit();            →        }（正常结束 → 自动提交）
} catch (Exception e) {
    conn.rollback();          →        （抛异常 → 自动回滚）
} finally {
    conn.close();             →        （连接池管理）
}
```

> **关键进步**：30 行事务管理代码缩减为一个注解。你的方法只剩业务逻辑——扣款 + 加款。

---

### 2.2 第二层：rollbackFor 控制回滚

`@Transactional` 默认只对 **RuntimeException**（运行时异常）和 **Error** 回滚。如果你抛出的是 **checked exception**（受检异常），事务**不会回滚**！

```java
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    // 场景一：抛 RuntimeException → 默认回滚 ✅
    @Transactional
    public void transfer1(Long fromId, Long toId, Double amount) {
        accountRepo.debit(fromId, amount);
        accountRepo.credit(toId, amount);
        throw new RuntimeException("模拟出错");  // 事务回滚 ✅
    }

    // 场景二：抛 checked exception → 默认不回滚 ❌（数据已提交！）
    @Transactional
    public void transfer2(Long fromId, Long toId, Double amount) throws Exception {
        accountRepo.debit(fromId, amount);
        accountRepo.credit(toId, amount);
        throw new Exception("模拟出错");  // 事务不回滚 ❌ 钱已转走！
    }

    // 场景三：指定 rollbackFor → 回滚 ✅
    @Transactional(rollbackFor = Exception.class)  // 告诉 Spring：Exception 也回滚
    public void transfer3(Long fromId, Long toId, Double amount) throws Exception {
        accountRepo.debit(fromId, amount);
        accountRepo.credit(toId, amount);
        throw new Exception("模拟出错");  // 事务回滚 ✅
    }
}
```

**什么是 checked exception？** Java 把异常分为两类：

```
Java 异常体系
                    Throwable
                   /          \
                 Error       Exception（checked）
                              |
                    ┌─────────┴──────────┐
                    |                    |
            RuntimeException        其他 Exception（checked）
            （unchecked）         （编译器强制 try-catch 或 throws）
```

- **unchecked（RuntimeException）**：`NullPointerException`、`IllegalArgumentException` 等。编译器不强制处理，通常是程序 bug。
- **checked（Exception 子类但非 RuntimeException）**：`IOException`、`SQLException` 等。编译器强制你 `try-catch` 或 `throws`，通常是外部环境问题。

Spring 默认只回滚 unchecked 异常的设计哲学是：**checked 异常通常是可预期的业务情况（如余额不足），不应回滚；unchecked 异常通常是意外错误，应当回滚**。但实际开发中，大部分团队选择 `rollbackFor = Exception.class` 来覆盖所有异常。

```
回滚规则决策图

方法抛异常
    │
    ├── 是 RuntimeException 或 Error？
    │       ├── 是 → 回滚（默认行为）
    │       └── 否（checked exception）
    │               ├── 没配 rollbackFor → 不回滚（危险！）
    │               └── 配了 rollbackFor = Exception.class → 回滚（安全 ✅）
    │
    └── 方法正常返回 → 提交
```

> **实践建议**：养成习惯，`@Transactional` 都加上 `rollbackFor = Exception.class`，避免 checked 异常不回滚的坑。

---

### 2.3 第三层：事务传播行为

到目前为止，每个 `@Transactional` 方法都是独立的。但如果一个事务方法**调用了另一个事务方法**呢？

```java
@Service
public class TransferService {

    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long fromId, Long toId, Double amount) {
        accountRepo.debit(fromId, amount);
        accountRepo.credit(toId, amount);

        // 调用另一个事务方法：记录转账日志
        logService.recordTransfer(fromId, toId, amount);  // 这里也有 @Transactional
    }
}
```

**问题**：`transfer()` 已经开了事务，`recordTransfer()` 也标注了 `@Transactional`。是复用外层事务，还是开一个新事务？如果日志记录失败，要不要回滚转账？

这就是**事务传播行为（Propagation）**要回答的问题：当一个事务方法被另一个事务方法调用时，事务如何传播？

Spring 提供了七种传播行为，最常用的是前两种：

#### PROPAGATION_REQUIRED（默认）

**含义**：有事务就加入，没有就新建。

```
调用链：transfer() → recordTransfer()

情况一：transfer() 已开事务
  ┌─ transfer 事务 ─────────────────────────────┐
  │  debit()                                    │
  │  credit()                                   │
  │  ┌─ recordTransfer ──→ 加入当前事务 ──────┐  │
  │  │  saveLog()                            │  │
  │  └───────────────────────────────────────┘  │
  └──────────── commit / rollback ──────────────┘
  → 日志失败 → 整个转账回滚

情况二：transfer() 没有事务（假设没加 @Transactional）
  ┌─ recordTransfer 新建事务 ──┐
  │  saveLog()                 │
  └── commit / rollback ───────┘
```

```java
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LogService {

    // PROPAGATION_REQUIRED 是默认值，可以省略
    @Transactional(propagation = Propagation.REQUIRED)
    public void recordTransfer(Long fromId, Long toId, Double amount) {
        // 日志和转账在同一个事务中
        // 日志失败 → 转账也回滚
        logRepo.save(new TransferLog(fromId, toId, amount));
    }
}
```

#### PROPAGATION_REQUIRES_NEW

**含义**：无论外层有没有事务，都新开一个独立事务。外层事务被**挂起**，等内层事务结束后再恢复。

```
调用链：transfer() → recordTransfer()

  ┌─ transfer 事务（挂起）──────────────────────┐
  │                                            │
  │  debit()                                   │
  │  credit()                                  │
  │                                            │
  │  ┌─ recordTransfer 新事务 ──────────┐      │
  │  │  saveLog()                       │      │
  │  └─── commit / rollback ────────────┘      │
  │                                            │
  │  （transfer 事务恢复）                     │
  └──────────── commit / rollback ─────────────┘

  → 日志失败 → 只回滚日志，转账不受影响
  → 转账失败 → 日志已提交，不回滚
```

```java
@Service
public class LogService {

    // 无论外层有没有事务，都新开独立事务
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordTransfer(Long fromId, Long toId, Double amount) {
        // 日志在自己的事务中
        // 日志失败 → 只回滚日志，转账不受影响
        logRepo.save(new TransferLog(fromId, toId, amount));
    }
}
```

**什么时候用 REQUIRES_NEW？** 当子操作不应该影响主事务时。比如：

- 转账日志：即使转账失败，日志也要记录（审计需求）
- 发送通知邮件：即使转账成功但邮件失败，不应回滚转账

> **注意**：`REQUIRES_NEW` 会占用**两个数据库连接**——外层事务的连接被挂起但未释放，内层事务需要新连接。如果连接池较小，可能成为瓶颈。

### 本节回顾

```
三层递进总结

第一层 @Transactional                    →  一个注解，方法级事务
第二层 + rollbackFor = Exception.class   →  所有异常都回滚
第三层 + propagation = REQUIRED          →  有事务就加入（默认）
      + propagation = REQUIRES_NEW       →  总是新建独立事务

每一层你只比上一层多学一个概念，场景保持一致。
```

---

## 3. 事务隔离级别

上一节解决了 ACID 的 A（原子性）。现在来看 I（隔离性）——当多个事务**并发**执行时，会发生什么问题。

### 并发事务的问题

假设两个事务同时操作同一个账户：

#### 脏读（Dirty Read）

事务 A 修改了数据但**还没提交**，事务 B 就读到了这个未提交的值。如果 A 回滚了，B 读到的是"不存在"的数据。

```
时间线    事务 A（转账）              事务 B（查余额）
  T1      UPDATE balance - 1000
  T2      （未提交）                  SELECT balance → 4000 ← 脏读！
  T3      ROLLBACK（余额恢复 5000）
  T4                                  B 以为余额是 4000，实际是 5000
```

> **定义**：脏读 = 读到了其他事务**未提交**的修改。SQL 中没有直接对应的语法，隔离级别 `READ_UNCOMMITTED` 允许脏读。

#### 不可重复读（Non-Repeatable Read）

事务 B 在同一个事务中**两次读取同一行**，结果不同——因为事务 A 在中间修改并提交了。

```
时间线    事务 A（取款）              事务 B（查余额）
  T1                                  SELECT balance → 5000
  T2      UPDATE balance - 1000
  T3      COMMIT
  T4                                  SELECT balance → 4000 ← 不一样！
                                      B 在同一事务中读两次，结果不同
```

> **定义**：不可重复读 = 同一事务中两次读同一行，结果不同（被别人修改了）。SQL 类比：`SELECT ... WHERE id = 1` 两次结果不同。

#### 幻读（Phantom Read）

事务 B 在同一个事务中**两次执行同范围查询**，结果集行数不同——因为事务 A 在中间插入或删除了新行。

```
时间线    事务 A（新增账户）          事务 B（统计账户数）
  T1                                  SELECT COUNT(*) → 100
  T2      INSERT INTO account ...
  T3      COMMIT
  T4                                  SELECT COUNT(*) → 101 ← 多了一行！
                                      B 在同一事务中查两次，行数不同
```

> **定义**：幻读 = 同一事务中两次范围查询，结果集行数不同（被别人插入/删除了）。SQL 类比：`SELECT COUNT(*)` 两次结果不同。和不可重复读的区别：不可重复读针对**同一行的值**变化，幻读针对**结果集行数**变化。

### 四种隔离级别

数据库通过**隔离级别**来控制这些并发问题的可见性。隔离级别越高，数据越安全，但并发性能越低：

```
隔离级别              脏读      不可重复读   幻读      性能
═══════════════════════════════════════════════════════════
READ_UNCOMMITTED       可能       可能        可能      最高
  （读未提交）
READ_COMMITTED         不可能     可能        可能      高
  （读已提交）           ← PostgreSQL、Oracle 默认
REPEATABLE_READ        不可能     不可能      可能      中
  （可重复读）           ← MySQL InnoDB 默认
SERIALIZABLE           不可能     不可能      不可能    最低
  （串行化）             ← 事务排队执行，无并发
```

> **开发 vs 生产**：开发环境可以用较低的隔离级别（性能好），生产环境根据业务需要选择。大部分场景 `READ_COMMITTED` 或 `REPEATABLE_READ` 就够了。MySQL 的 InnoDB 引擎通过 **MVCC**（Multi-Version Concurrency Control，多版本并发控制）在 `REPEATABLE_READ` 级别下也能避免幻读，比标准 SQL 更强。
>
> **MVCC 是什么？** 数据库为每行数据维护多个版本（快照），读操作读快照、写操作创建新版本，从而实现"读写不互锁"。SQL 类比：`SELECT` 不加锁也能看到一致的数据。这是数据库内部机制，开发者只需选择隔离级别，MVCC 由数据库自动处理。

### 在 @Transactional 中指定隔离级别

```java
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    // 指定隔离级别为 READ_COMMITTED
    @Transactional(
        isolation = Isolation.READ_COMMITTED,
        rollbackFor = Exception.class
    )
    public void updateBalance(Long id, Double amount) {
        // 此方法内所有数据库操作都在 READ_COMMITTED 隔离级别下执行
        accountRepo.debit(id, amount);
    }
}
```

```java
import org.springframework.transaction.annotation.Isolation;

// Isolation 是枚举，取值对应四种隔离级别
Isolation.DEFAULT              // 使用数据库默认设置（最常用）
Isolation.READ_UNCOMMITTED     // 读未提交
Isolation.READ_COMMITTED       // 读已提交
Isolation.REPEATABLE_READ      // 可重复读
Isolation.SERIALIZABLE         // 串行化
```

> **实践建议**：大多数场景用 `Isolation.DEFAULT`（让数据库决定）即可。只有遇到具体的并发问题时（如查到脏数据），才显式指定更高级别。

### 本节回顾

```
隔离级别决策

有问题吗？               没有 → DEFAULT（数据库默认）
    │
    │ 有
    ▼
读到别人没提交的数据？    是 → READ_COMMITTED 或更高
    │
    │ 否
    ▼
同一事务读两次结果不同？  是 → REPEATABLE_READ
    │
    │ 否
    ▼
同一事务查两次行数不同？  是 → SERIALIZABLE（或用 MySQL InnoDB 默认级别）
```

---

## 4. 事务失效的常见陷阱

`@Transactional` 用起来很简单，但有几种情况会**静默失效**——注解加了，但事务没生效，数据也不回滚。这是最容易踩的坑。

### 4.1 自调用（同一个类内部方法调用）

**这是最常见的陷阱。** Spring 事务基于 **AOP 代理**，但同类内部的方法调用不经过代理，事务注解失效。

**什么是 AOP 代理？** AOP（Aspect-Oriented Programming，面向切面编程）是 Spring 的核心机制之一。简单来说，Spring 为每个带 `@Transactional` 的 Bean 创建了一个"替身"（代理对象）。外部调用时，先经过替身（替身负责开事务），再调用真正的对象。但对象内部自己调用自己的方法时，是直接调用，不经过替身。

```
外部调用（经过代理）：

  调用方 ──→ 代理对象 ──→ 开启事务 ──→ 真正的 TransferService
                                              │
                                              ▼
                                           transfer()
                                              │
                                           commit/rollback

内部自调用（不经过代理）：

  调用方 ──→ 代理对象 ──→ 开启事务 ──→ 真正的 TransferService
                                              │
                                              ▼
                                           transfer()
                                              │
                                              ├── this.debit()   ← 直接调用自己，不经过代理！
                                              └── this.credit()  ← 事务注解不生效！
```

```java
@Service
public class TransferService {

    // 危险写法 ❌：debit() 的 @Transactional 不生效
    @Transactional(rollbackFor = Exception.class)
    public void debit(Long id, Double amount) {
        accountRepo.debit(id, amount);
    }

    // transfer() 内部直接调用 debit() —— 不经过代理，事务注解失效
    public void transfer(Long fromId, Long toId, Double amount) {
        debit(fromId, amount);  // this.debit()，不经过 Spring 代理！
        credit(toId, amount);   // 同理
    }
}
```

**安全写法**：把事务方法放到不同的类中，让调用经过代理。

```java
// AccountService.java —— 事务方法在独立的类中
@Service
public class AccountService {

    @Transactional(rollbackFor = Exception.class)
    public void debit(Long id, Double amount) {
        accountRepo.debit(id, amount);
    }
}

// TransferService.java —— 通过注入的 Bean 调用，经过代理 ✅
@Service
public class TransferService {

    private final AccountService accountService;

    public TransferService(AccountService accountService) {
        this.accountService = accountService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long fromId, Long toId, Double amount) {
        accountService.debit(fromId, amount);  // 经过代理，事务生效 ✅
        accountService.credit(toId, amount);
    }
}
```

### 4.2 异常被 catch 吞掉

`@Transactional` 通过方法抛出异常来触发回滚。如果你在方法内部 `try-catch` 把异常吞了，Spring 看不到异常，就不会回滚。

```java
@Service
public class TransferService {

    // 危险写法 ❌：异常被吞，不回滚
    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long fromId, Long toId, Double amount) {
        try {
            accountRepo.debit(fromId, amount);
            accountRepo.credit(toId, amount);
            // 模拟出错
            if (true) throw new RuntimeException("出错了");
        } catch (Exception e) {
            // 异常被吞！Spring 看不到异常 → 不回滚 → 钱已转走！
            System.out.println("出错了: " + e.getMessage());
        }
    }

    // 安全写法 ✅：catch 后重新抛出
    @Transactional(rollbackFor = Exception.class)
    public void transferSafe(Long fromId, Long toId, Double amount) {
        try {
            accountRepo.debit(fromId, amount);
            accountRepo.credit(toId, amount);
        } catch (Exception e) {
            // 记录日志后重新抛出
            System.out.println("出错了: " + e.getMessage());
            throw e;  // 重新抛出！Spring 看到异常 → 回滚 ✅
        }
    }
}
```

```
异常处理对事务的影响

吞掉异常                        重新抛出异常
═══════════════                ═══════════════
try { ... } catch { log }      try { ... } catch { log; throw }
        │                              │
        ▼                              ▼
方法正常返回                    方法抛出异常
        │                              │
        ▼                              ▼
Spring：没异常 → 提交 ❌        Spring：有异常 → 回滚 ✅
```

### 4.3 方法访问修饰符问题

Spring AOP 代理只能拦截 **public** 方法。`private`、`protected`、包级私有方法上的 `@Transactional` 不生效。

```java
@Service
public class TransferService {

    // 不生效 ❌：private 方法，代理无法拦截
    @Transactional(rollbackFor = Exception.class)
    private void debit(Long id, Double amount) { ... }

    // 生效 ✅：public 方法
    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long fromId, Long toId, Double amount) { ... }
}
```

### 4.4 对象不是 Spring 管理的 Bean

`@Transactional` 依赖 Spring 的 AOP 代理。如果你自己 `new` 出来的对象，不是 Spring 容器管理的 Bean，代理不存在，事务不生效。

```java
// 危险写法 ❌：自己 new 的对象不是 Spring Bean
public class TransferController {

    public void doTransfer() {
        TransferService service = new TransferService();  // 自己 new！
        service.transfer(1L, 2L, 1000.0);  // 不是 Bean，没有代理 → 事务不生效
    }
}

// 安全写法 ✅：通过依赖注入获取 Spring 管理的 Bean
@RestController
public class TransferController {

    private final TransferService service;  // Spring 注入的 Bean

    public TransferController(TransferService service) {
        this.service = service;
    }

    @PostMapping("/transfer")
    public String doTransfer() {
        service.transfer(1L, 2L, 1000.0);  // 经过代理 → 事务生效 ✅
        return "success";
    }
}
```

### 4.5 数据库引擎不支持事务

这个最隐蔽。如果 MySQL 表使用的是 **MyISAM** 引擎（不支持事务），那么无论你怎么加 `@Transactional`，数据库层面就无法回滚。

> **MyISAM vs InnoDB**：MySQL 有多种存储引擎。**MyISAM** 是老引擎，不支持事务和外键，但查询速度快；**InnoDB** 支持事务（ACID）、行级锁、外键，是 MySQL 5.5+ 的默认引擎。Spring 事务的底层依赖数据库引擎的事务支持——如果引擎不支持事务，Spring 也无能为力。
>
> **区分开发/生产**：开发环境一般用 InnoDB（Spring Boot 默认配置）。但如果你接手了老项目，或有历史遗留的 MyISAM 表，需要检查并修改。

```sql
-- 检查表引擎
SHOW TABLE STATUS WHERE Name = 'account';
-- 如果 Engine 列是 MyISAM → 事务不生效

-- 修改为 InnoDB
ALTER TABLE account ENGINE = InnoDB;
```

### 本节回顾

```
事务失效的五大陷阱

1. 自调用          → this.method() 不经过代理 → 拆到不同类
2. 异常被吞        → catch 后不 throw        → catch 后重新抛出
3. 非 public 方法  → 代理只拦截 public         → 改为 public
4. 非 Spring Bean → 自己 new 的对象           → 用依赖注入
5. MyISAM 引擎     → 数据库不支持事务           → 换成 InnoDB

共同特征：不报错、不提示，静默失效——最难排查的 bug 类型
```

---

## 5. 多数据源下的事务管理

到目前为止，所有示例都假设项目只有**一个数据库**。但实际生产中，一个应用连接多个数据库很常见：账户数据在 MySQL，操作日志在另一个 MySQL；或者读写分离、微服务本地多库等。

这就带来一个新问题：Spring 容器中有多个 `DataSource`，每个都需要自己的事务管理器。`@Transactional` 到底管哪个？

### 5.1 问题：@Transactional 管哪个？

回顾前面的内容：`@Transactional` 底层依赖一个 `PlatformTransactionManager` 来开启、提交、回滚事务。单数据源时，Spring Boot 自动配置了一个，你不用操心。

但多数据源时，容器里会出现**多个** `PlatformTransactionManager`：

```
多数据源场景

  Spring 容器
  │
  ├── DataSource A（账户库）──→ TransactionManager A
  │                                    │
  ├── DataSource B（日志库）──→ TransactionManager B
  │                                    │
  └── @Transactional ──→ 该用 A 还是 B？❓
```

`@Transactional` 不知道用哪个事务管理器时，Spring 会尝试找一个"默认"的。如果找不到，**启动直接报错**；如果找错了，事务**静默地管了错误的数据源**——和第 4 节的五大陷阱一样，不报错、不提示。

### 5.2 定义多个事务管理器

多数据源时，需要在配置类中为每个数据源定义独立的 `PlatformTransactionManager` Bean，并用 `@Bean("名称")` 给每个管理器命名：

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {

    // 数据源 A：账户库
    @Bean("accountDataSource")
    public DataSource accountDataSource() {
        // 配置账户库的连接信息（HikariCP 等连接池）
        // ...
    }

    // 数据源 B：日志库
    @Bean("logDataSource")
    public DataSource logDataSource() {
        // 配置日志库的连接信息
        // ...
    }

    // 事务管理器 A：绑定账户库
    @Bean("accountTransactionManager")
    public PlatformTransactionManager accountTransactionManager() {
        return new DataSourceTransactionManager(accountDataSource());
    }

    // 事务管理器 B：绑定日志库
    @Bean("logTransactionManager")
    public PlatformTransactionManager logTransactionManager() {
        return new DataSourceTransactionManager(logDataSource());
    }
}
```

每个事务管理器绑定各自的数据源，互不干扰：

```
事务管理器与数据源的绑定关系

  accountTransactionManager ──→ accountDataSource（账户库）
        │
        └── 管理 account 表的增删改查事务

  logTransactionManager ──→ logDataSource（日志库）
        │
        └── 管理 log 表的增删改查事务
```

> **注意**：多数据源配置需要关闭 Spring Boot 的自动配置（`@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})`），否则 Spring Boot 会自动创建单数据源配置，和你的手动配置冲突。

### 5.3 @Primary：标记默认事务管理器

现在容器里有两个 `PlatformTransactionManager`。当 `@Transactional` 不指定用哪个时，Spring 怎么选？

答案是：找标记了 **`@Primary`** 的那个。`@Primary` 的含义是"当容器中存在多个同类型的 Bean 时，优先使用我"。类比：**`@Primary` 就像"默认路由"**——你不指定走哪条路时，就走默认那条。

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class DataSourceConfig {

    @Bean("accountTransactionManager")
    @Primary  // ← 标记为默认事务管理器
    public PlatformTransactionManager accountTransactionManager() {
        return new DataSourceTransactionManager(accountDataSource());
    }

    @Bean("logTransactionManager")
    public PlatformTransactionManager logTransactionManager() {
        return new DataSourceTransactionManager(logDataSource());
    }
}
```

加了 `@Primary` 后，`@Transactional` 的选择逻辑变成：

```
@Transactional 的事务管理器选择

  @Transactional
      │
      ├── 指定了 transactionManager？
      │       ├── 是 → 用指定的那个
      │       └── 否 → 找 @Primary 标记的
      │                   ├── 有 @Primary → 用它（默认） ✅
      │                   └── 没有 @Primary → 启动报错 ❌
      │                       NoUniqueBeanDefinitionException
```

> **实践建议**：多数据源项目中，一定要给"主业务"的事务管理器加 `@Primary`。大部分 Service 只操作主库，不指定 `transactionManager` 时自动走默认的，省去每个方法都手动指定的麻烦。

### 5.4 @Transactional 指定事务管理器

对于不使用默认事务管理器的 Service，通过 `@Transactional` 的 `transactionManager` 属性显式指定：

```java
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    // 使用默认事务管理器（@Primary 标记的 accountTransactionManager）
    @Transactional(rollbackFor = Exception.class)
    public void transfer(Long fromId, Long toId, Double amount) {
        accountRepo.debit(fromId, amount);  // 操作账户库
        accountRepo.credit(toId, amount);
    }
}

@Service
public class LogService {

    // 显式指定使用日志库的事务管理器
    @Transactional(
        transactionManager = "logTransactionManager",
        rollbackFor = Exception.class
    )
    public void recordLog(String action) {
        logRepo.save(new OperationLog(action));  // 操作日志库
    }
}
```

`transactionManager` 是 `@Transactional` 的属性。因为它是 `value` 属性（第一个属性），可以简写为：

```java
// 完整写法
@Transactional(transactionManager = "logTransactionManager")

// 简写（省略属性名，效果完全一样）
@Transactional("logTransactionManager")
```

对比一下两种 Service 的事务走向：

```
指定 vs 不指定事务管理器

  TransferService                          LogService
  @Transactional                           @Transactional("logTransactionManager")
       │                                        │
       ▼                                        ▼
  找 @Primary                              直接用 logTransactionManager
       │                                        │
       ▼                                        ▼
  accountTransactionManager                logDataSource
       │                                        │
       ▼                                        ▼
  accountDataSource                        log 表
       │
       ▼
  account 表
```

### 5.5 常见注意事项

#### 陷阱 1：忘了 @Primary

多个 `PlatformTransactionManager` 都没有标记 `@Primary`，`@Transactional` 不指定名称时，Spring 不知道该用哪个，启动直接报错：

```
NoUniqueBeanDefinitionException: No qualifying bean of type
'PlatformTransactionManager' available: expected single matching bean
but found 2: accountTransactionManager, logTransactionManager
```

**解决**：给主业务的事务管理器加 `@Primary`，或者每个 `@Transactional` 都显式指定 `transactionManager`。

#### 陷阱 2：Bean 名称拼写错误

```java
// 危险写法 ❌：名称拼错了！
@Transactional(transactionManager = "acountTransactionManager")  // 少了个 "c"
public void transfer(...) { ... }
```

编译器不会报错，运行时 Spring 找不到这个名称的 Bean 才报 `NoSuchBeanDefinitionException`。

**解决**：定义 Bean 名称时复制粘贴，不要手打；或者用常量类集中管理事务管理器名称。

#### 陷阱 3：跨数据源事务

一个方法同时操作两个数据源时，Spring 本地事务**无法**协调两个事务管理器：

```java
// 危险写法 ❌：一个事务管理器管不了两个数据源
@Transactional("accountTransactionManager")
public void transferAndLog(Long fromId, Long toId, Double amount) {
    accountRepo.debit(fromId, amount);   // 账户库 → 受 accountTransactionManager 管理
    logRepo.save(new TransferLog(...));  // 日志库 → 不受当前事务管理器管理！
}
```

`@Transactional` 只能管一个事务管理器对应的数据源。对另一个数据源的操作**不在同一个事务中**——账户扣款失败回滚，但日志可能已经提交了。

> **边界提示**：跨数据源的原子性需要**分布式事务**方案（如 Seata、Saga 模式等），超出本指南范围。如果业务要求两个库"要么全成功要么全失败"，需要引入专门的分布式事务框架。

#### 陷阱 4：事务管理器类型选错

事务管理器类型必须和数据访问技术匹配：

```
数据访问技术              事务管理器类型
══════════════════════════════════════════════
JPA（Spring Data JPA）   JpaTransactionManager
MyBatis / JDBC           DataSourceTransactionManager
```

用错了类型，事务行为可能不正确——比如 JPA 的 `EntityManager` 绑定在 `JpaTransactionManager` 上，用 `DataSourceTransactionManager` 管理不到 JPA 的事务。

### 本节回顾

```
多数据源事务管理决策

有几个数据源？
    │
    ├── 一个 → 不用管，Spring Boot 自动配置（回到第 2 节）
    │
    └── 多个 → 定义多个 TransactionManager
                │
                ├── 哪个是主业务？ → 加 @Primary
                │
                ├── 非主业务的 Service → @Transactional("xxxTransactionManager")
                │
                └── 一个方法操作两个库？ → 本地事务做不到，需要分布式事务方案
```

---

## 6. 速查清单

### 6.1 @Transactional 属性速查

```
属性                   默认值                       作用
═════════════════════════════════════════════════════════════════
propagation           REQUIRED                     事务传播行为
isolation             DEFAULT                      事务隔离级别
timeout               -1（不超时）                  超时时间（秒）
readOnly              false                        是否只读事务
rollbackFor           RuntimeException.class       触发回滚的异常类型
noRollbackFor         {}                           不触发回滚的异常类型
```

### 6.2 传播行为完整对照

```
传播行为              外层有事务时         外层无事务时         典型场景
═══════════════════════════════════════════════════════════════════
REQUIRED（默认）      加入外层事务         新建事务             90% 场景
REQUIRES_NEW         挂起外层，新建       新建事务             日志/通知（独立提交）
NESTED               创建保存点（子事务）  新建事务             部分回滚
SUPPORTS             加入外层事务         非事务执行          查询方法（有无事务都行）
NOT_SUPPORTED        挂起外层，非事务执行  非事务执行          批量查询（不需要事务开销）
NEVER                抛异常               非事务执行          确保无事务时执行
MANDATORY            加入外层事务         抛异常              确保必须在事务中执行
```

> **保存点（Savepoint）**：NESTED 传播行为使用数据库的保存点机制。保存点是事务中的一个"标记点"，可以回滚到这个点而不回滚整个事务。SQL 类比：`SAVEPOINT sp1; ... ROLLBACK TO sp1;`。MySQL InnoDB 支持保存点。

### 6.3 隔离级别速查

```
隔离级别              脏读      不可重复读   幻读      性能
══════════════════════════════════════════════════════════════
DEFAULT              用数据库默认设置                               最常用
READ_UNCOMMITTED     ✗ 可能    ✓ 可能      ✓ 可能      最高
READ_COMMITTED       ✓ 安全    ✗ 可能      ✓ 可能      高
REPEATABLE_READ      ✓ 安全    ✓ 安全      ✗ 可能*     中
SERIALIZABLE         ✓ 安全    ✓ 安全      ✓ 安全      最低

* MySQL InnoDB 的 REPEATABLE_READ 通过 MVCC 也能避免幻读，比标准 SQL 更强
```

### 6.4 失效场景速查

```
陷阱                  症状                           解决方案
═════════════════════════════════════════════════════════════════
自调用               内部调用 @Transactional 方法    拆到不同的类
                     不报错，事务不生效
异常被吞             catch 后不 throw，事务提交       catch 后重新 throw
private 方法        不报错，事务不生效               改为 public
非 Spring Bean      new 出来的对象无代理              用 @Autowired 注入
MyISAM 引擎         不报错，回滚不生效               ALTER TABLE 改 InnoDB
```

### 6.5 回滚规则速查

```
异常类型                           默认行为      rollbackFor = Exception.class
═══════════════════════════════════════════════════════════════════
RuntimeException                   回滚 ✅       回滚 ✅
Error                              回滚 ✅       回滚 ✅
checked Exception（如 IOException） 不回滚 ❌    回滚 ✅

推荐写法：始终使用 @Transactional(rollbackFor = Exception.class)
```

### 6.6 用法模板

```java
// 标准写法（推荐）
@Transactional(
    propagation = Propagation.REQUIRED,        // 默认值，可省略
    isolation = Isolation.DEFAULT,              // 默认值，可省略
    timeout = 30,                               // 超时 30 秒
    readOnly = false,                            // 非只读
    rollbackFor = Exception.class               // 所有异常都回滚（重要！）
)
public void businessMethod() {
    // 业务逻辑
}

// 只读查询的简化写法
@Transactional(readOnly = true)                 // 只读事务，性能更好
public List<Product> findAll() {
    return productRepo.findAll();
}

// 最常用写法（省略一切，只保留 rollbackFor）
@Transactional(rollbackFor = Exception.class)
public void businessMethod() {
    // 业务逻辑
}
```

### 6.7 多数据源事务管理器速查

```
场景                              做法
═════════════════════════════════════════════════════════════════
单数据源                          不用管，Spring Boot 自动配置
多数据源，有主有次                 主库事务管理器加 @Primary，其余显式指定
多数据源，无主次                   每个 @Transactional 都指定 transactionManager
一个方法操作两个库                 本地事务做不到，需要分布式事务方案
JPA 项目                          JpaTransactionManager
MyBatis / JDBC 项目              DataSourceTransactionManager
```
