# Spring IOC 与 DI 指南

> 本指南循序渐进介绍控制反转（IOC）与依赖注入（DI）。从"没有 Spring 时怎么写"到"Spring 帮你做了什么"，每步只引入一个新概念。
> 基于 Spring Framework 6.x / Spring Boot 3.x。

---

## 目录

1. [为什么需要 IOC/DI](#1-为什么需要-iocdi)
2. [入门三步走](#2-入门三步走)
   - [第一层：手动装配（纯 Java）](#21-第一层手动装配纯-java)
   - [第二层：引入 Spring 容器](#22-第二层引入-spring-容器)
   - [第三层：三种注入方式对比](#23-第三层三种注入方式对比)
3. [进阶概念](#3-进阶概念)
   - [用 @Configuration + @Bean 管理第三方类](#31-用-configuration--bean-管理第三方类)
   - [多个实现类时如何选择](#32-多个实现类时如何选择)
4. [速查清单](#4-速查清单)

---

## 1. 为什么需要 IOC/DI

### 问题起源

假设你在写一个订单系统。`OrderService` 处理订单逻辑，需要调用 `OrderRepository` 存数据库，还需要 `EmailService` 发邮件通知。

不用任何框架时，你的代码大概长这样：

```java
// OrderService 自己负责"找帮手"
public class OrderService {

    private OrderRepository orderRepo;   // 存数据库
    private EmailService emailService;   // 发邮件

    public OrderService() {
        // 所有依赖都自己 new 出来
        orderRepo = new OrderRepository("jdbc:mysql://localhost:3306/shop");
        emailService = new EmailService("smtp.mail.com", 587);
    }

    public void placeOrder(Order order) {
        orderRepo.save(order);
        emailService.send(order.getEmail(), "订单确认", "您的订单已创建");
    }
}
```

**问题在哪里？**

- 数据库地址、SMTP 配置硬编码在构造函数里，换个环境就要改代码。
- `OrderService` 不仅要管业务逻辑，还得管"谁来帮我存数据、谁来帮我发邮件"——职责混乱。
- 单元测试几乎没法写：你想测 `placeOrder()` 逻辑，结果它真实连数据库、真实发邮件。

更致命的是，每个用到 `OrderRepository` 的类都要自己 `new` 一个：

```java
public class ReportService {
    private OrderRepository orderRepo = new OrderRepository("jdbc:mysql://localhost:3306/shop");
    // ...
}

public class AdminController {
    private OrderRepository orderRepo = new OrderRepository("jdbc:mysql://localhost:3306/shop");
    // ...
}
```

连接信息变了？改 N 处。想换成 MongoDB？每一个 `new` 的地方都要改。

> **这就是"手动 new 地狱"：你负责创建对象，而对象的创建细节散落各处，牵一发而动全身。**

### IOC 的解决方案

控制反转（Inversion of Control）的核心思想就一句话：**"你不要自己 new 对象，由容器来给你"**。

```
你写的（传统方式）            Spring 做的（IOC）
─────────────────────        ─────────────────────
OrderService                 Spring 容器
  ├── new OrderRepo()  →       ├── 创建 OrderRepo 实例
  ├── new EmailService()       ├── 创建 EmailService 实例
  └── 业务逻辑                  └── 把两个实例注入 OrderService

控制权在你手里                控制权在容器手里（反转了！）
```

**依赖注入（DI）** 是 IOC 的一种实现方式：容器不光帮你创建对象，还把对象所"依赖"的其他对象**注入**进去。

翻译成人话：

| 概念            | 大白话                               |
| --------------- | ------------------------------------ |
| IOC（控制反转） | "你别管对象怎么来的，我给你"         |
| DI（依赖注入）  | "你要的帮手，我帮你塞进去"           |
| Spring 容器     | 那个负责创建和管理所有对象的"大管家" |

---

## 2. 入门三步走

用一个贯穿场景来演示：**订单服务（OrderService）依赖仓库（OrderRepository）存数据**。

### 2.1 第一层：手动装配（纯 Java）

不用 Spring，但用**构造器传参**代替内部 `new`——这是 DI 思想的萌芽：

```java
// ===== 仓库接口 =====
public interface OrderRepository {
    void save(Order order);
}

// ===== 仓库实现（连 MySQL） =====
public class MySqlOrderRepository implements OrderRepository {
    private String url;

    public MySqlOrderRepository(String url) {
        this.url = url;
    }

    @Override
    public void save(Order order) {
        System.out.println("存入 MySQL: " + url);
    }
}

// ===== 业务服务：依赖通过构造器传入 =====
public class OrderService {

    private final OrderRepository repo;  // 依赖接口，不依赖具体实现

    // 构造器接收依赖——这就是"注入"
    public OrderService(OrderRepository repo) {
        this.repo = repo;
    }

    public void placeOrder(Order order) {
        repo.save(order);
    }
}

// ===== 启动时手动组装 =====
public class Main {
    public static void main(String[] args) {
        // 手动创建所有依赖，然后一层层传递
        OrderRepository repo = new MySqlOrderRepository("jdbc:mysql://localhost:3306/shop");
        OrderService service = new OrderService(repo);

        service.placeOrder(new Order());
    }
}
```

**这已经比最初好多了**：`OrderService` 不关心 `repo` 到底连哪种数据库，测试时传一个假的 `OrderRepository` 就行。但组装工作仍然由 `main()` 手工完成——如果有 50 个类，`main()` 会变成组装流水线。

> **关键进步**：面向接口编程 + 构造器注入。依赖从"我自己 new"变成了"从外面传给我"。

---

### 2.2 第二层：引入 Spring 容器

把组装工作交给 Spring。只需告诉 Spring 两件事：

- **"哪些类归你管？"** → 加 `@Component`（或 `@Service`、`@Repository`）
- **"你需要谁？"** → 加 `@Autowired`

```java
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

// ===== 告诉 Spring："这个类归你管" =====
@Repository  // 等价于 @Component，语义更明确（数据访问层）
public class MySqlOrderRepository implements OrderRepository {

    // Spring 管理后，配置从外部注入
    private final String url;

    // @Value读 application.yml 文件中的配置
    public MySqlOrderRepository(@Value("${db.url}") String url) {
        this.url = url;
    }

    @Override
    public void save(Order order) {
        System.out.println("存入 MySQL: " + url);
    }
}

// ===== 告诉 Spring："这个类也归你管，而且它需要一个 OrderRepository" =====
@Service  // 等价于 @Component，语义更明确（业务层）
public class OrderService {

    private final OrderRepository repo;

    // @Autowired 可省略（Spring 4.3+（2016/6/10发布），只有一个构造器时自动注入）
    public OrderService(OrderRepository repo) {
        this.repo = repo;
    }

    public void placeOrder(Order order) {
        repo.save(order);
    }
}
```

Spring 启动时做了什么？用图表示：

```
Spring 启动流程
═══════════════════════════════════════════════════════════════

1. 扫描 classpath，找到所有带 @Component 的类
   │
   ├── 发现 MySqlOrderRepository（@Repository 本质是 @Component）
   └── 发现 OrderService（@Service 本质是 @Component）

2. 分析依赖关系
   │
   └── OrderService 构造器需要 OrderRepository
       └── 只有一个实现类 MySqlOrderRepository → 用它

3. 按顺序创建实例
   │
   ├── 先创建 MySqlOrderRepository（没有依赖，直接 new）
   └── 再创建 OrderService（把上一步的 repo 传入构造器）

4. 容器管理所有 Bean，随时可取用
```

> **你的代码从"主动要"变成了"被动等"——这就是控制反转。**

---

### 2.3 第三层：三种注入方式对比

Spring 支持三种注入方式，但**构造器注入是首选**。下面用同一个场景对比：

#### 构造器注入（推荐 ✅）

```java
@Service
public class OrderService {

    private final OrderRepository repo;
    private final EmailService emailService;

    // @Autowired 可省略（Spring 4.3+，只有一个构造器时自动注入）
    public OrderService(OrderRepository repo, EmailService emailService) {
        this.repo = repo;
        this.emailService = emailService;
    }
}
```

可使用第三方包 Lombok 的 @RequiredArgsConstructor 注解简化为

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository repo;
    private final EmailService emailService;
}
```

**优点**：

- 依赖不可变（`final`），对象创建后不会被篡改
- 依赖不全会编译报错，不会出现 NPE
- 测试友好：`new OrderService(mockRepo, mockEmail)` 即可

---

#### Setter 注入

```java
@Service
public class OrderService {

    private OrderRepository repo;
    private EmailService emailService;

    @Autowired
    public void setRepo(OrderRepository repo) {
        this.repo = repo;
    }

    @Autowired
    public void setEmailService(EmailService emailService) {
        this.emailService = emailService;
    }
}
```

**缺点**：

- 依赖可变，可能在运行时被修改
- 可能忘记调用 setter，导致 NPE
- 适用于**可选依赖**的场景

---

#### 字段注入（不推荐 ❌）

```java
@Service
public class OrderService {

    @Autowired
    private OrderRepository repo;

    @Autowired
    private EmailService emailService;
}
```

**看起来最简洁，为什么不用？**

- 单元测试无法直接注入 mock：必须靠反射或启动 Spring 容器
- 依赖隐藏：看类定义看不出它需要哪些依赖
- 违反单一职责的信号被掩盖：构造器参数太多会"刺眼"，字段再多也"无感"

```
三种注入方式对比

        写法简洁度      不可变性       测试友好度      推荐度
───────────────────────────────────────────────────────────
构造器      中           ✅ final        ✅ 直接 new    ⭐⭐⭐⭐⭐
Setter     中           ❌ 可变         ⚠️ 需手动设    ⭐⭐
字段        高           ❌ 可变         ❌ 需反射      ⭐
```

---

### 本节回顾

```
没有 Spring                有 Spring（IOC 容器）
══════════════             ═══════════════════
你写 new Xxx()   ──→       @Component 标注
你传参数         ──→       @Autowired 注入
你管组装顺序     ──→       容器自动分析依赖图

DI 的本质：你要的不是"自己去找工具"，而是"工具被送到你手上"
```

---

## 3. 进阶概念

第二层讲的是"你自己写的类"怎么交给 Spring。但如果依赖的是**第三方类**（比如 `RestTemplate`、`DataSource`），你不能跑到第三方 jar 里加 `@Component`，怎么办？

### 3.1 用 @Configuration + @Bean 管理第三方类

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration  // 告诉 Spring："这是一个配置类，里面有 @Bean 方法"
public class AppConfig {

    @Bean  // 方法的返回值会被注册到 Spring 容器
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public DataSource dataSource(
            @Value("${db.url}") String url,
            @Value("${db.username}") String username,
            @Value("${db.password}") String password) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        return ds;
    }
}
```

```
@Component  vs  @Bean

@Component                         @Bean
──────────────────────────────     ────────────────────────────
加在你自己写的类上                   加在 @Configuration 类的方法上
Spring 自动发现 + 自动创建          你手动写创建逻辑，Spring 托管结果
适合：Service、Controller、Repo     适合：第三方类、需要复杂初始化的对象

配合使用：@Component 类里可以 @Autowired 注入 @Bean 产生的实例
```

---

### 3.2 多个实现类时如何选择

当 `OrderRepository` 有两个实现时，Spring 不知道该注入哪个，启动直接报错：

```java
@Repository
public class MySqlOrderRepository implements OrderRepository { ... }

@Repository
public class MongoOrderRepository implements OrderRepository { ... }

// OrderService 里：
@Autowired
public OrderService(OrderRepository repo) { ... }  // 💥 报错！有两个候选人
```

**解决方案：@Primary 与 @Qualifier**

```java
// 方案一：指定一个"默认首选"
@Repository
@Primary  // "没特别说明的话，就用我"
public class MySqlOrderRepository implements OrderRepository { ... }

@Repository
public class MongoOrderRepository implements OrderRepository { ... }

// OrderService 不需要改动，默认注入 @Primary 的那个
```

```java
// 方案二：按名字精确指定（和 @Primary 可配合使用）
@Service
public class OrderService {

    private final OrderRepository repo;

    @Autowired
    public OrderService(@Qualifier("mongoOrderRepository") OrderRepository repo) {
        this.repo = repo;  // 明确要 Mongo 那个
    }
}
```

> **@Qualifier 的值默认是类名首字母小写**（`MongoOrderRepository` → `mongoOrderRepository`），也可以通过 `@Repository("自定义名")` 指定。

```
选择策略速记

一个实现类            → 不用任何额外注解，Spring 自动匹配
多个实现类 + 有默认   → @Primary 标在默认实现上
多个实现类 + 按需选   → @Qualifier("bean名称") 精确指定
```

---

## 4. 速查清单

### 4.1 核心概念速查

| 术语 | 一句话解释                             |
| ---- | -------------------------------------- |
| IOC  | 对象的创建权从"你自己"转移到"容器"     |
| DI   | 容器把你需要的依赖自动塞进来           |
| Bean | 被 Spring 容器管理的对象               |
| 容器 | 负责创建、装配、管理 Bean 的运行时环境 |

### 4.2 注解速查

| 注解                              | 作用                         | 用在                    |
| --------------------------------- | ---------------------------- | ----------------------- |
| `@Component`                      | 标记一个类为 Spring Bean     | 通用                    |
| `@Service`                        | 同 @Component，语义：业务层  | Service 类              |
| `@Repository`                     | 同 @Component，语义：数据层  | Repository 类           |
| `@Controller` / `@RestController` | 同 @Component，语义：控制层  | Controller 类           |
| `@Autowired`                      | 自动注入依赖                 | 构造器 / Setter / 字段  |
| `@Configuration`                  | 标记配置类                   | 配置类                  |
| `@Bean`                           | 方法返回值注册为 Bean        | @Configuration 类的方法 |
| `@Primary`                        | 多个同类型 Bean 时的默认选择 | 实现类                  |
| `@Qualifier`                      | 按名称精确指定注入哪个 Bean  | 构造器参数              |
| `@Value`                          | 注入配置文件中的值           | 字段 / 构造器参数       |

### 4.3 注入方式速查

| 方式        | 语法                                | 推荐度     | 适用场景                   |
| ----------- | ----------------------------------- | ---------- | -------------------------- |
| 构造器注入  | 构造器参数 + `@Autowired`（可省略） | ⭐⭐⭐⭐⭐ | **首选，99% 场景**         |
| Setter 注入 | Setter 方法 + `@Autowired`          | ⭐⭐       | 可选依赖                   |
| 字段注入    | 字段 + `@Autowired`                 | ⭐         | 快速原型（不推荐生产代码） |

### 4.4 依赖选择速查

| 场景                         | 方案                       |
| ---------------------------- | -------------------------- |
| 只有一个实现类               | 不做任何额外配置           |
| 多个实现，需要默认选一个     | `@Primary`                 |
| 多个实现，不同位置用不同实现 | `@Qualifier("beanName")`   |
| 需要精细控制 Bean 创建过程   | `@Configuration` + `@Bean` |

### 4.5 DI 思维转变

```
传统思维                           IOC 思维
─────────────────────              ─────────────────────
"我需要什么，我自己造"              "我需要什么，声明出来，等容器给"
类控制自己的依赖                    容器控制所有依赖
强耦合，难以替换                    面向接口，轻松替换实现
测试需要真实环境                    测试只需 mock 依赖
```
