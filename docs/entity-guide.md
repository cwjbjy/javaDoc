# 实体类（Entity）指南

> 本指南集中介绍 Entity 的概念本身——它是什么、为什么需要、怎么设计。不绑定任何具体 ORM 框架。

---

## 目录

1. [什么是 Entity](#1-什么是-entity)
2. [不同 ORM 框架中的 Entity](#2-不同-orm-框架中的-entity)
3. [字段设计](#3-字段设计)
4. [Entity 的生命周期](#4-entity-的生命周期)
5. [约定与最佳实践](#5-约定与最佳实践)
   - [5.3.1 为什么不能直接返回 Entity](#531-为什么-controller-不能直接返回-entity)
6. [速查清单](#6-速查清单)

---

## 1. 什么是 Entity

### 什么是 POJO

**POJO = Plain Old Java Object**（简单普通 Java 对象）。这个名字强调了"它不依赖任何框架，就是一个纯粹的数据容器"。

```
POJO 的四个特征
─────────────────
① 不继承框架规定的父类（不需要 extends 某个框架基类）
② 不实现框架规定的接口（不需要 implements 某个框架接口）
③ 不使用框架的注解（注解是可选的，不是必需的）
④ 就是一个普通类，包含字段 + getter/setter + 无参构造器
```

> **为什么强调 Plain Old？** 在 EJB（Enterprise Java Bean）时代，实体类必须继承沉重基类、实现多个接口、写大量 XML 配置。当时人们说"POJO"就是在反抗这种复杂性——"我什么都不依赖，我就是个普通 Java 类"。所以这个"Plain Old"不是贬义，而是 Java 社区追求的简洁设计哲学。

最简单的 POJO 长这样：

```java
public class Product {
    private String id;
    private String name;
    private Double price;

    public Product() {}                          // 无参构造器（ORM 框架需要）


    public String getId() { return id; }         // getter × N
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
}
```

> **可读但很啰嗦**：30 行代码，只有 3 个字段。这就是为什么后面需要 Lombok 的 `@Data` ——把它精简到 5 行。

### POJO → Entity

当这个 POJO 被用来**代表数据库中的一条记录**时，它就是 Entity。

```
                POJO
                  │
                  │ 职责变成"代表数据库中的一条记录"
                  ▼
               Entity
                  │
      ┌───────────┼───────────┐
      │           │           │
      ▼           ▼           ▼
   JPA/Hibernate  MongoDB    MyBatis
   加 @Entity    加 @Document  不加注解
   @Table 等     @Id 等      纯 POJO
   (注解驱动)    (注解驱动)    (XML/Mapper 驱动)
```

如何告诉框架"我对应哪个表/集合"？取决于 ORM 框架：

| 框架                | 映射方式                      | Entity 类上的注解                     |
| ------------------- | ----------------------------- | ------------------------------------- |
| JPA / Hibernate     | 注解写在 Entity 类上          | `@Entity`、`@Table`、`@Id`、`@Column` |
| Spring Data MongoDB | 注解写在 Entity 类上          | `@Document`、`@Id`、`@Field`          |
| MyBatis             | 映射写在 XML 或 Mapper 接口中 | **无** — Entity 就是纯 POJO           |

> **核心认知**：Entity 的本质是**职责**（代表数据库中的一条记录），而不是**注解**。注解只是 ORM 框架的一种实现方式。MyBatis 的 Entity 不需要任何注解，就是一个普通 Java 类。

### Entity 的四个特征

```
Entity 必须满足的条件
──────────────────────
① 是一个 Java class（不是接口、不是枚举）
② 有无参构造器（ORM 框架需要用反射创建对象）
③ 有 getter/setter（ORM 框架需要通过 setter 填充从数据库读到的值）
④ 有主键标识（用于唯一标识一条记录）
```

---

## 2. 不同 ORM 框架中的 Entity

同一个概念，三种实现方式。

### 2.1 JPA / Hibernate（注解驱动）

注解写在 Entity 类上，类即映射：

```java
import jakarta.persistence.*;

@Data
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;                        // 自增主键

    @Column(name = "product_name", length = 100)
    private String name;

    @Column(name = "unit_price")
    private Double price;
}
```

### 2.2 Spring Data MongoDB（注解驱动）

注解写在 Entity 类上，类即映射：

```java
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "products")
public class Product {

    @Id
    private String id;                      // MongoDB 的 _id

    private String name;
    private Double price;
}
```

### 2.3 MyBatis（XML / Mapper 驱动）

Entity 就是纯 POJO，无任何 ORM 注解：

```java
@Data
public class Product {
    private Long id;
    private String name;
    private Double price;
}
```

映射写在 XML 或 Mapper 接口中：

```java
// Mapper 接口（数据访问层）
@Mapper
public interface ProductMapper {
    @Select("SELECT * FROM products WHERE id = #{id}")
    Product findById(Long id);

    @Insert("INSERT INTO products(name, price) VALUES(#{name}, #{price})")
    void insert(Product product);
}
```

### 三框架对比

```
                     JPA              MongoDB           MyBatis
                     ───              ───────           ───────
Entity 类上的注解     丰富              丰富               无
映射定义位置          类上注解           类上注解            XML / Mapper 接口
Entity 的可移植性      低（绑定框架）     低（绑定框架）      高（纯 POJO）
上手难度              中                中                 容易（SQL 写在 XML 里）
```

> **没有谁更好，只有谁更适合**。JPA/MongoDB 的注解让映射和类定义在一起，方便维护；MyBatis 的 Entity 完全解耦，可以不加任何依赖地在不同层之间传递。

---

## 3. 字段设计

### 3.1 @Data：告别手写 getter/setter

没有 Lombok 时：

```java
public class Product {
    private Long id;
    private String name;
    private Double price;
    private Integer stock;

    // 30+ 行 getter/setter + toString/equals/hashCode
}
```

有 Lombok：

```java
@Data
public class Product {
    private Long id;
    private String name;
    private Double price;
    private Integer stock;
}
// Lombok 编译时自动生成：getter/setter/toString/equals/hashCode/无参构造器
```

> `@Data` 是 Lombok 最常用的注解。三个项目都用它标注 Entity。

### 3.2 class vs record：为什么 Entity 不用 record

```
record（DTO 常用）                  class + @Data（Entity 常用）
───────────────────                 ───────────────────────────
不可变（字段 final）                  可变（有 setter）
没有无参构造器                        有无参构造器
创建后不能修改                        创建后可逐步 set 值

ORM 框架需要：                        ← 匹配
① 无参构造器创建对象             ✅
② setter 逐步填充字段值          ✅
③ 对象可变，允许 update           ✅
```

> record 的不可变性非常适合 DTO（只读接收请求数据），但 Entity 需要被 ORM 框架创建后调用 setter 填入数据库读到的值，所以用可变类。

### 3.3 常用字段类型

| 类型               | 用途             | 示例                             |
| ------------------ | ---------------- | -------------------------------- |
| `Long` / `String`  | 主键             | `id: "abc123"` 或 `id: 1`        |
| `String`           | 名称、描述、URL  | `name: "冰可乐"`                 |
| `Integer` / `Long` | 数量、计数       | `stock: 100`                     |
| `BigDecimal`       | 金额（精确计算） | `price: 3.50`                    |
| `Boolean`          | 开关、状态       | `isActive: true`                 |
| `LocalDateTime`    | 时间             | `createdAt: 2026-07-07T10:30:00` |
| `enum`             | 枚举状态         | `Status.ACTIVE`                  |
| `List<T>`          | 集合             | `tags: ["热销", "新品"]`         |

> **金额用 `BigDecimal` 不是 `Double`**：`Double` 有浮点精度问题（`0.1 + 0.2 = 0.30000000000000004`），涉及财务计算必须用 `BigDecimal`。

### 3.4 默认值

```java
@Data
public class Product {
    private Long id;
    private String name;

    private Integer stock = 0;                        // 默认库存为 0
    private Boolean isActive = true;                   // 默认上架
    private List<String> tags = new ArrayList<>();    // 默认空列表，避免 NPE
    private LocalDateTime createdAt = LocalDateTime.now();  // 默认当前时间
}
```

> **集合字段给初始值 `new ArrayList<>()`**，否则为 null 时遍历会抛 NullPointerException。

### 3.5 嵌套对象

Entity 可以包含嵌套对象来表达"一对多"或"一对一"的关系：

```java
@Data
public class Order {
    private Long id;
    private String orderNo;

    private List<OrderItem> items = new ArrayList<>();  // 一对多嵌套

    @Data
    public static class OrderItem {
        private Long productId;
        private String productName;
        private Integer quantity;
        private BigDecimal price;
    }
}
```

> 嵌套静态类用 `static` 修饰——它不持有外部类引用，可以被框架独立实例化。

**嵌套 vs 关联**：

```
嵌套（Embedded）                      关联（Relation）
────────────────                      ────────────────
子对象数据在父对象文档/行内             子对象是独立的表/集合
适合："组成"关系（订单和订单明细）       适合："引用"关系（订单和用户）
Entity 类中直接定义静态内部类             Entity 类中用外键字段引用
```

---

## 4. Entity 的生命周期

Entity 从创建到删除，在不同阶段有不同的"状态"。这是 JPA 的标准术语，但概念对任何 ORM 都通用：

```
                    Entity 生命周期
        ┌──────────────────────────────────────────────┐
        │                                              │
        ▼                                              │
  ┌──────────┐  持久化   ┌──────────┐  查询回来  ┌──────────┐
  │  新建态   │ ──────→  │  托管态   │ ←─────────  │  托管态   │
  │ (New)    │          │(Managed) │            │(Managed) │
  │          │          │          │            │          │
  │ new 出来  │          │ 有主键    │            │ 从 DB    │
  │ 无主键    │          │ DB 同步   │            │ 查出来    │
  └──────────┘          └────┬─────┘            └──────────┘
                             │
                             │ 删除
                             ▼
                       ┌──────────┐
                       │  删除态   │
                       │(Removed) │
                       └──────────┘
```

| 状态                  | 说明                               | 特征                             |
| --------------------- | ---------------------------------- | -------------------------------- |
| **新建态**（New）     | `new Product()` 创建，还没保存     | 主键为 null                      |
| **托管态**（Managed） | 已保存到数据库，或被查询回来       | 有主键，框架追踪其变化           |
| **删除态**（Removed） | 已标记删除，事务提交后从数据库移除 | 对象仍在内存，但数据库中将不存在 |

**托管态的核心能力**：处于托管态的 Entity 如果被修改（如 `setPrice(5.0)`），框架会自动检测并生成 UPDATE 语句，不需要手动调用"更新"方法。

> **MyBatis 没有托管态**——所有状态管理由开发者手动控制（显式调用 `insert` / `update` / `delete`），没有自动脏检查。这也是 MyBatis 比 JPA 更"显式"的一个体现。

---

## 5. 约定与最佳实践

### 5.1 命名

```
类名：     Product、OrderItem、UserProfile    （大驼峰，名词）
          Order → OrderEntity                  （如与 DTO 同名，加 Entity 后缀）

字段名：   name、orderNo、isActive             （小驼峰）
          BigDecimal price;                     （金额类型）
          List<String> tags;                     （集合类型）
```

### 5.2 目录结构

```
src/main/java/com/example/demo/module/
├── product/
│   ├── entity/
│   │   └── Product.java              ← 实体类
│   ├── repository/                   ← 数据访问层
│   │   └── ProductRepository.java
│   ├── dto/
│   │   └── CreateProductDTO.java
│   ├── converter/
│   │   └── ProductConverter.java
│   ├── service/
│   │   └── ProductService.java
│   └── controller/
│       └── ProductController.java
```

> `entity/` 和 `repository/` 分开——数据结构与数据访问是两种职责。

### 5.3 Entity 与 DTO 的分工

```
                    请求流入                        响应流出
                       │                               ▲
                       ▼                               │
                  ┌──────────┐                  ┌──────────────┐
                  │   DTO    │                  │   DTO        │
                  │ (record) │                  │  (record)    │
                  └────┬─────┘                  └──────┬───────┘
                       │ Converter                      │ Converter
                       ▼                               ▲
                  ┌──────────┐                  ┌──────────────┐
                  │  Entity  │  ──── 持久化 ──→ │    数据库     │
                  │ (class)  │  ←─── 查询 ──── │               │
                  └──────────┘                  └──────────────┘
```

> **DTO 管接口，Entity 管存储。** 分层的目的是让接口格式和数据库结构可以独立演化，互不污染。

### 5.3.1 为什么 Controller 不能直接返回 Entity

上面 5.3 的架构图里，响应方向走的是 DTO，不是 Entity。这不是"可以这样做"，而是"必须这样做"。三个核心原因：

#### ① 安全性 —— 敏感字段泄露

Entity 包含了数据库存储的**全部字段**。如果 Controller 直接返回 Entity，JSON 序列化会把所有字段都暴露出去——包括不该让前端看到的敏感数据。

```java
// ❌ 直接返回 User Entity
@GetMapping("/user/{id}")
public User getUser(@PathVariable String id) {
    return userRepository.findById(id).orElse(null);
}
```

前端收到的 JSON：

```json
{
  "id": "abc123",
  "username": "zhangsan",
  "password": "$2a$10$xK8f...",
  "isAdmin": true,
  "phone": "13800138000",
  "internalNotes": "VIP 客户"
}
```

- `password`：密码哈希泄露
- `isAdmin`：权限标记泄露
- `phone`：隐私泄露
- `internalNotes`：内部备注泄露

你可以用 `@JsonIgnore` 逐个字段堵洞：

```java
@JsonIgnore  private String password;
@JsonIgnore  private Boolean isAdmin;
@JsonIgnore  private String phone;
@JsonIgnore  private String internalNotes;
```

但 Entity 新增一个敏感字段就要加一次 `@JsonIgnore`——这是一个**每次都会忘的约定**。更根本的办法是用 Response DTO：没写在 DTO 里的字段，天然就不会暴露。

```java
// ✅ 只返回前端需要的数据
public record UserResponse(
    String id,
    String username
) {}
```

```
@JsonIgnore vs Response DTO
───────────────────────────────
@JsonIgnore：被动堵漏（补丁思维，容易遗漏）
Response DTO：主动白名单（"只暴露我声明的字段"）
```

#### ② 解耦 —— API 契约独立演化

数据库和前端是两套节奏：

- 数据库加字段（如 `audit_log`）——前端不应该自动看到
- 前端需要组合数据（用户名 + 头像 URL）——数据库不一定要有对应列

如果 Controller 返回 Entity，数据库任何字段增删改都会直接影响前端——你改了一个 Entity，所有引用它的接口输出都变了。用 Response DTO 做一层隔离：

```
数据库变了 → Entity 变了 → Response DTO 不变（或主动调整）→ 前端不受影响
前端需求变了 → 改 Response DTO 即可 → Entity 不变
```

这是分层架构的核心价值：**每一层的变化不污染另一层**。

#### ③ 性能 —— 序列化陷阱

Entity 之间的关联关系（`@OneToMany`、`@ManyToOne`、嵌套对象）在 JSON 序列化时会触发严重问题：

- **懒加载触发 N+1 查询**：序列化框架访问关联属性时，JPA 会自动发 SQL 查询，一个请求可能变成几十次数据库查询。
- **双向关联导致循环引用**：`Order` 里有 `User`，`User` 里有 `List<Order>`，序列化时互相嵌套 → `StackOverflowError`。
- **查询了不需要的字段**：Entity 的 `@Lob` 大文本字段、BLOB 字段也被一起查出来序列化，浪费带宽和内存。

Response DTO 是"视图投影"——Service 层只查需要的字段、只构建需要的数据结构，没有多余的东西。

---

#### 一句话总结

| 维度   | 直接返回 Entity       | 返回 Response DTO      |
| ------ | --------------------- | ---------------------- |
| 安全性 | 靠 `@JsonIgnore` 堵洞 | 白名单，天然安全       |
| 解耦   | 前端和数据库强绑定    | 两层独立演化           |
| 性能   | 懒加载、循环引用风险  | 按需投影，无序列化陷阱 |

> **规则：Controller 永远不返回 Entity。** 这和你给前端设计的 JSON 格式是同一件事——定义好 Response DTO，就是定义好前端看到的数据契约。

### 5.4 编写清单

```java
// ✅ 一个标准的 Entity 类应该包含：

@Data                                               // Lombok 生成 getter/setter
public class ProductEntity {                        // class（非 record）
    private Long id;                                // 主键
    private String name;                            // 业务字段
    private BigDecimal price;                       // 金额用 BigDecimal
    private Integer stock = 0;                      // 默认值
    private List<Item> items = new ArrayList<>();   // 集合字段给默认值

    @Data
    public static class Item { ... }                // 嵌套类也用 @Data
}
```

### 5.5 常见陷阱

| 陷阱                | 错误写法                     | 正确写法                                                                        |
| ------------------- | ---------------------------- | ------------------------------------------------------------------------------- |
| 用 record 做 Entity | `public record Product(...)` | `public class Product { ... }`                                                  |
| 忘记无参构造器      | 只写了有参构造器             | 用 `@Data`（自动生成）                                                          |
| 集合字段不初始化    | `List<Item> items;`          | `List<Item> items = new ArrayList<>();`                                         |
| 金额用浮点类型      | `Double price;`              | `BigDecimal price;`                                                             |
| Entity 暴露给前端   | Controller 返回 Entity       | Controller 返回 DTO（详见 [5.3.1](#531-为什么-controller-不能直接返回-entity)） |

---

## 6. 速查清单

### 6.1 Entity 概念速查

```
┌──────────────────────────────────────────────────────────────┐
│                  Entity 概念速查                              │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  POJO： 不依赖框架的普通 Java 类                              │
│                                                              │
│  Entity：职责是"代表数据库中的一条记录"的 POJO                 │
│                                                              │
│  四个特征：class、无参构造器、getter/setter、主键              │
│                                                              │
│  实现方式：JPA(注解)、MongoDB(注解)、MyBatis(纯 POJO)         │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 6.2 三框架 Entity 对照

```
┌─────────────┬──────────────────┬────────────────────────────┐
│   框架      │  Entity 注解      │  映射定义位置                │
├─────────────┼──────────────────┼────────────────────────────┤
│  JPA       │ @Entity @Table   │  Entity 类上                │
│            │ @Id @Column ...  │                             │
├─────────────┼──────────────────┼────────────────────────────┤
│  MongoDB   │ @Document @Id    │  Entity 类上                │
│            │ @Field ...       │                             │
├─────────────┼──────────────────┼────────────────────────────┤
│  MyBatis   │ 无               │  XML / Mapper 接口           │
└─────────────┴──────────────────┴────────────────────────────┘
```

### 6.3 字段设计速查

```
┌──────────────────────────────────────────────────────────────┐
│                  字段设计规范                                  │
├────────────────────┬─────────────────────────────────────────┤
│  主键类型           │  Long（自增）/ String（UUID/ObjectId）  │
│  金额               │  BigDecimal（不用 Double/Float）        │
│  时间               │  LocalDateTime                         │
│  默认值             │  Integer stock = 0                     │
│  集合初始化         │  List<X> items = new ArrayList<>()    │
│  嵌套对象           │  static class 定义内部类                 │
│  不可变字段         │  DTO 用 record，Entity 用 class+@Data   │
└────────────────────┴─────────────────────────────────────────┘
```

### 6.4 生命周期速查

```
┌──────────────────────────────────────────────────────────────┐
│                Entity 生命周期                                │
├──────────┬───────────────────────────────────────────────────┤
│  新建态   │  new 出来，主键为 null                              │
│  托管态   │  有主键，框架追踪变化（JPA/MongoDB）/ 手动管理（MyBatis）│
│  删除态   │  标记删除，提交后从数据库移除                         │
└──────────┴───────────────────────────────────────────────────┘
```

---

**最后：** Entity 的核心就一句话——**一个代表数据库记录的普通 Java 类**。它不一定是注解堆砌的，也可能就是一个纯 POJO。理解了这个，再去学 JPA、MongoDB、MyBatis 的具体注解和配置，就只是"用哪种方式告诉框架映射关系"的问题了。
