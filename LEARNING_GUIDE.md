# Java & Spring Boot 学习指南（基于 demo1 项目）

> 本指南基于 `demo1` 项目 —— 一个菜谱/订单管理的 REST API 后端。逐层拆解，从零基础到理解整个项目。

---

## 目录

1. [项目概览：它做了什么](#1-项目概览)
2. [第零课：环境准备与项目启动](#2-环境准备)
3. [第一课：Java 基础速览](#3-java-基础)
4. [第二课：Maven 与项目骨架](#4-maven-项目构建)
5. [第三课：Spring Boot 启动流程](#5-spring-boot-启动)
6. [第四课：分层架构概览](#6-分层架构)
7. [第五课：Controller 层 —— 接收请求](#7-controller-层)
8. [第六课：DTO 与参数校验](#8-dto-参数校验)
9. [第七课：Service 层 —— 业务逻辑](#9-service-层)
10. [第八课：Entity 与 MongoDB](#10-entity-数据层)
11. [第九课：全局响应与异常处理](#11-全局响应异常处理)
12. [第十课：配置管理](#12-配置管理)
13. [第十一课：文件上传](#13-文件上传)
14. [第十二课：跨域与静态资源](#14-跨域与静态资源)
15. [学习路线图](#15-学习路线图)

---

## 1. 项目概览

```
┌─────────────────────────────────────────────────────────┐
│                      前端 / 客户端                        │
│              HTTP 请求 → http://localhost:9001/api/...    │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│                   Spring Boot 应用                        │
│                                                         │
│  ┌──────────────────────────────────────────────────┐  │
│  │              /api (context-path)                  │  │
│  │  ┌─────────────┐  ┌─────────────┐                │  │
│  │  │  /market/*   │  │  /order/*   │   Controller  │  │
│  │  └──────┬───────┘  └──────┬──────┘                │  │
│  │         │                 │                       │  │
│  │  ┌──────▼───────┐  ┌──────▼──────┐               │  │
│  │  │ MarketService│  │ OrderService│   Service     │  │
│  │  │ FoodService  │  │             │               │  │
│  │  └──────┬───────┘  └──────┬──────┘               │  │
│  │         │                 │                       │  │
│  │  ┌──────▼───────┐  ┌──────▼──────┐               │  │
│  │  │ MarketRepo   │  │ OrderRepo   │   Repository  │  │
│  │  │ MongoTemplate│  │             │               │  │
│  │  └──────┬───────┘  └──────┬──────┘               │  │
│  └─────────┼─────────────────┼──────────────────────┘  │
│            │                 │                          │
└────────────┼─────────────────┼──────────────────────────┘
             │                 │
             ▼                 ▼
       ┌─────────────────────────────────┐
       │           MongoDB                │
       │  ┌──────────┐  ┌──────────────┐ │
       │  │ markets   │  │   orders     │ │
       │  └──────────┘  └──────────────┘ │
       └─────────────────────────────────┘
```

**两大业务模块：**

| 模块 | 功能 | 核心实体 |
|------|------|----------|
| market（菜单） | 管理菜品分类、添加/编辑/删除菜品、上传图片 | Market（内含 FoodItem 列表） |
| order（订单） | 创建订单、分页查询、删除订单 | Order（内含 OrderFoodItem 列表） |

---

## 2. 环境准备

### 你需要安装什么

| 工具 | 说明 | 验证命令 |
|------|------|----------|
| JDK 17 | Java 开发环境 | `java -version` |
| Maven | 项目构建工具（项目自带 `mvnw`，无需全局安装） | `./mvnw --version` |
| MongoDB | 数据库 | 安装并启动，默认端口 27017 |

### 如何跑起来

```bash
# 1. 确保 MongoDB 正在运行
# 2. 进入项目目录，执行：
./mvnw spring-boot:run

# 服务启动在 http://localhost:9001/api
```

**项目用到的配置文件：**
- `application.yml` — 指定使用 `dev` 环境
- `application-dev.yml` — 开发环境：本地 MongoDB，端口 9001
- `application-prod.yml` — 生产环境：远程 MongoDB

**要学的第一个注解：** `@SpringBootApplication`（见第五课）

---

## 3. Java 基础

> 如果你完全不熟悉 Java，建议先了解以下概念，再回头看项目代码。

### 3.1 类与对象（class）

Java 是面向对象语言，一切代码都在 `class` 里。

```java
// Demo1Application.java —— 项目中最简单的类
public class Demo1Application {
    public static void main(String[] args) {  // 程序入口
        SpringApplication.run(Demo1Application.class, args);
    }
}
```

**关键词速查：**
- `public` — 任何地方都能访问
- `class` — 定义一个类
- `static` — 静态方法，不用 new 就能调用
- `void` — 方法没有返回值
- `String[] args` — 命令行参数数组

### 3.2 接口（interface）

接口定义"能做什么"，但不定义"怎么做"。具体实现写在类里。

```java
// OrderRepository.java —— 接口，只有方法声明，没有方法体
public interface OrderRepository extends MongoRepository<Order, String> {
    // 空的！所有方法都从 MongoRepository 继承
}
```

**对比：** `class` 是"是什么"，`interface` 是"能做什么"。

在service层能够直接使用OrderRepository接口中的方法，是因为Spring 的依赖注入（DI） 和 Spring Data 的代理机制。

流程为：声明接口 → Spring Data 自动生成实现 → 注册为 Bean → 注入到 Service。这就是 Spring Boot "约定优于配置"的设计理念。

### 3.3 注解（Annotation）

以 `@` 开头的标记，给代码附加额外信息。Spring Boot 大量使用注解。

```java
@RestController           // 告诉 Spring：这是一个 REST 控制器
@RequestMapping("/order") // 告诉 Spring：这个控制器处理 /order 路径的请求
@RequiredArgsConstructor  // Lombok 自动生成构造函数
public class OrderController {
    private final OrderService orderService;
    // ...
}
```

**注解的本质：** 用 `@` 标记代替手写配置或重复代码。Spring 启动时会扫描这些标记并自动完成配置。

### 3.4 泛型（Generics）

尖括号里的类型参数，让代码可以处理多种类型。

```java
List<Market> getAll() { ... }                  // 返回 Market 对象的列表
Map<String, Object> result = new LinkedHashMap<>();  // 键是 String，值是任意类型
```

### 3.5 Lambda 与 Stream

项目中大量使用的方法，用于简洁地处理集合。

```java
// OrderService.java:28-37 —— 将 DTO 列表转换为 Entity 列表
List<Order.OrderFoodItem> foods = dto.getFoods().stream()  // 转成流
    .map(f -> {                                             // 对每个元素执行转换
        Order.OrderFoodItem item = new Order.OrderFoodItem();
        item.setId(f.getId());
        item.setName(f.getName());
        return item;
    })
    .toList();  // 收集回 List
```

**解读：** `stream()` → `map(转换)` → `toList()`，相当于："拿出每个元素 → 逐一变换 → 装进新列表"。

### 3.6 内部类（Inner Class）

在这个项目中，`FoodItem` 定义在 `Market` 类内部，而不是单独文件。

```java
@Data
@Document(collection = "markets")
public class Market {
    private String id;
    private List<FoodItem> foods = new ArrayList<>();  // Market 包含多个 FoodItem

    @Data
    public static class FoodItem {  // 内部类：FoodItem 属于 Market
        private String id;
        private String name;
        private Integer num = 0;
    }
}
```

**为什么用内部类？** `FoodItem` 只存在于 `Market` 的 `foods` 数组中，不会单独存在。放在 Market 内部表达这种从属关系。

### 3.7 项目中出现的 Java 概念清单

| 概念 | 出现位置 | 说明 |
|------|----------|------|
| `package` | 每个文件第一行 | 命名空间，类似文件夹 |
| `import` | 文件顶部 | 引入其他类 |
| `extends` | `MarketRepository extends MongoRepository` | 继承 |
| `implements` | `WebMvcConfig implements WebMvcConfigurer` | 实现接口 |
| `@Override` | Service 层各处 | 重写父类方法 |
| `this` | 隐式使用 | 指向当前对象 |
| `new` | 各处 `new LinkedHashMap<>()` | 创建对象 |
| `null` | `if (imageUrl == null)` | 空值判断 |
| `try-catch` | `GlobalExceptionHandler` | 异常处理 |
| `final` | Service 构造参数 | 不可变变量 |

---

## 4. Maven：项目构建

### 4.1 pom.xml 是什么

`pom.xml` 是 Maven 项目的心脏，定义了三件事：
- **项目信息：** 组名、工件名、版本
- **依赖（dependencies）：** 项目引用了哪些外部库
- **构建配置：** 如何编译、打包

### 4.2 解读本项目的 pom.xml

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.6</version>        <!-- Spring Boot 版本 -->
</parent>

<properties>
    <java.version>17</java.version>  <!-- Java 版本 -->
</properties>

<dependencies>
    <!-- 1. MongoDB 数据库支持 -->
    <dependency>
        <artifactId>spring-boot-starter-data-mongodb</artifactId>
    </dependency>

    <!-- 2. Web MVC（REST API 核心） -->
    <dependency>
        <artifactId>spring-boot-starter-webmvc</artifactId>
    </dependency>

    <!-- 4. 热重载（开发时修改代码自动重启） -->
    <dependency>
        <artifactId>spring-boot-devtools</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- 5. Lombok（减少样板代码） -->
    <dependency>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

**每个 Starter 是什么？** Spring Boot 的 Starter 是预打包的依赖集合。`spring-boot-starter-webmvc` 包含了 Spring MVC、内嵌 Tomcat、Jackson（JSON 处理）等。

### 4.3 Maven 常用命令

```bash
./mvnw clean           # 清理之前的构建产物
./mvnw compile         # 只编译
./mvnw test            # 运行测试
./mvnw package         # 打包成 JAR 文件
./mvnw spring-boot:run # 直接运行
```

---

## 5. Spring Boot 启动

### 5.1 入口类

```java
// Demo1Application.java
@SpringBootApplication  // 核心注解：这是一个 Spring Boot 应用
public class Demo1Application {
    public static void main(String[] args) {
        SpringApplication.run(Demo1Application.class, args);
    }
}
```

### 5.2 启动时发生了什么

```
SpringApplication.run()
  │
  ├─ 1. 创建 Spring 容器（ApplicationContext）
  │      └─ 容器 = 一个巨大的 Map<名称, 对象>
  │         所有的 Controller、Service、Repository 都是容器中的 bean
  │
  ├─ 2. 扫描 com.example.demo1 包下所有类
  │      └─ 找到 @RestController → 注册为 HTTP 处理器
  │      └─ 找到 @Service → 注册为业务 Bean
  │      └─ 找到 @Repository → 注册为数据访问 Bean
  │      └─ 找到 @Configuration → 加载配置
  │
  ├─ 3. 自动配置（Auto-Configuration）
  │      └─ 检测到 classpath 有 MongoDB → 自动配置 MongoDB 连接
  │      └─ 
  │      └─ 检测到 application.yml → 读取端口、数据库地址等配置
  │
  └─ 4. 启动内嵌 Tomcat，绑定端口 9001
```

**关键概念 —— 控制反转（IoC）与依赖注入：**

```
传统方式（你自己管理依赖）:
  OrderController controller = new OrderController();
  controller.orderService = new OrderService();  // 手动创建
  controller.orderService.orderRepository = new OrderRepository();  // 手动创建
  // 很麻烦！而且 OrderRepository 还要连接数据库...

Spring 方式（容器管理依赖）:
  // 你只需要声明"我需要什么"
  @RequiredArgsConstructor  // 构造函数注入
  public class OrderController {
      private final OrderService orderService;  // 声明需求
  }
  // Spring 看到 @Service 就知道要创建 OrderService
  // Spring 看到 @Repository 就知道要创建 OrderRepository
  // 自动组装好，你只管用
```

---

## 6. 分层架构

Spring Boot 项目的标准三层（或四层）架构：

```
┌───────────────────────────────────────────────────┐
│  Controller 层（控制器）                            │
│  职责：接收 HTTP 请求，返回 HTTP 响应                │
│  ┌─────────────────────────────────────────────┐  │
│  │  MarketController   │   OrderController      │  │
│  │  @RestController    │   @RestController      │  │
│  │  @RequestMapping    │   @RequestMapping      │  │
│  └──────────┬──────────┴──────────┬────────────┘  │
│             │                     │               │
├─────────────┼─────────────────────┼───────────────┤
│  Service 层（业务逻辑）             │                │
│  职责：处理业务规则，调用数据层       │                │
│  ┌──────────▼──────────┐ ┌────────▼───────────┐  │
│  │  MarketService      │ │  OrderService       │  │
│  │  FoodService        │ │                     │  │
│  │  @Service           │ │  @Service           │  │
│  └──────────┬──────────┘ └────────┬────────────┘  │
│             │                     │               │
├─────────────┼─────────────────────┼───────────────┤
│  Repository 层（数据访问）           │                │
│  职责：操作数据库                    │                │
│  ┌──────────▼──────────┐ ┌────────▼───────────┐  │
│  │  MarketRepository   │ │  OrderRepository    │  │
│  │  MongoTemplate      │ │                     │  │
│  │  @Repository        │ │  @Repository        │  │
│  └──────────┬──────────┘ └────────┬────────────┘  │
│             │                     │               │
└─────────────┼─────────────────────┼───────────────┘
              │                     │
              ▼                     ▼
        ┌─────────────────────────────────┐
        │         MongoDB                  │
        │   markets 集合    orders 集合    │
        └─────────────────────────────────┘
```

**核心原则：单向依赖。** Controller → Service → Repository。绝不能反过来。

---

## 7. Controller 层

### 7.1 核心注解

```java
@RestController              // 标记为 REST 控制器（所有方法返回值直接写入 HTTP 响应体）
@RequestMapping("/order")    // 路径前缀：此类所有接口都以 /order 开头
@RequiredArgsConstructor     // Lombok：自动生成包含 final 字段的构造函数
public class OrderController {

    private final OrderService orderService;  // Spring 自动注入

    @PostMapping("/addOrder")                // 处理 POST 请求到 /api/order/addOrder
    public Object create(@Valid @RequestBody CreateOrderDTO dto) {
        return orderService.create(dto);
    }
}
```

### 7.2 HTTP 方法映射

| 注解 | HTTP 方法 | 本项目中的用途 |
|------|-----------|---------------|
| `@GetMapping` | GET | 查询菜单、查询订单 |
| `@PostMapping` | POST | 新增分类、上传图片 |
| `@PutMapping` | PUT | 编辑分类、添加菜品、更新菜品 |
| `@DeleteMapping` | DELETE | 删除分类、删除菜品、删除订单 |

### 7.3 参数接收

```java
// 1. 从请求体（JSON）获取参数
@PostMapping("/addCategory")
public Object addCategory(@Valid @RequestBody CreateCategoryDTO dto) { ... }
// 前端发送：POST /api/market/addCategory  Body: {"name": "川菜", "image": "xxx.jpg"}

// 2. 从 URL 查询参数获取
@GetMapping("/findFoods")
public Object findFoods(@RequestParam("text") String text) { ... }
// 前端发送：GET /api/market/findFoods?text=辣椒

// 3. 从表单上传文件
@PostMapping("/uploadImage")
public Map<String, Object> uploadImage(@RequestParam("file") MultipartFile file) { ... }
```

**完整请求流程：**

```
前端发送：POST /api/market/addCategory
Content-Type: application/json
{"name": "川菜", "image": "chuan.jpg"}

  │
  ▼
配置中的 context-path: /api
  → 去掉 /api，剩余 /market/addCategory
  │
  ▼
MarketController 的 @RequestMapping("/market")
  → 匹配到 /addCategory
  │
  ▼
@PostMapping("/addCategory") 方法
  → 将 JSON body 自动转换为 CreateCategoryDTO 对象
  → @Valid 触发参数校验（name 和 image 不能为空）
  │
  ▼
调用 marketService.addCategory(dto.getName(), dto.getImage())
  │
  ▼
返回 Market 对象
  → GlobalResponseBodyAdvice 自动包装为：
  {"code": 200, "message": "success", "data": {市场对象...}}
```

---

## 8. DTO 与参数校验

### 8.1 DTO 是什么

**DTO = Data Transfer Object（数据传输对象）**。它定义了前端应该发送什么格式的数据，与数据库 Entity 分离。

```
前端 JSON → DTO（接收） → Service 处理 → Entity（存数据库）
```

### 8.2 示例

```java
// CreateCategoryDTO.java —— 前端创建分类时要发送的数据
@Data                                                    // Lombok: 生成 getter/setter/toString/equals/hashCode
public class CreateCategoryDTO {
    @NotBlank(message = "缺少名称")                        // 校验：不能为空
    private String name;

    @NotBlank(message = "缺少图标")
    private String image;
}
```

**`@Valid` 的作用：** Controller 参数前的 `@Valid` 会触发 DTO 中所有校验注解（`@NotBlank`、`@NotNull`、`@NotEmpty`）。

```java
// Controller 中：
@PostMapping("/addCategory")
public Object addCategory(@Valid @RequestBody CreateCategoryDTO dto) {
    // 如果 name 为空，Spring 会直接返回 400 错误，不会进入这个方法
}
```

**三个常用校验注解的区别：**

| 注解 | 要求 | 何时用 |
|------|------|--------|
| `@NotNull` | 不能是 null | Integer、对象 |
| `@NotEmpty` | 不能是 null 且不能是空集合/空字符串 | List、String |
| `@NotBlank` | 不能是 null 且不能全是空白字符 | String（最常用） |

### 8.3 DTO 中的嵌套校验

```java
// AddFoodDTO.java —— 包含一个 List，每个元素也需要校验
@Data
public class AddFoodDTO {
    @NotEmpty(message = "缺少菜")
    @Valid                          // 这个 @Valid 很重要！
    private List<FoodDTO> foods;    // 告诉 Spring 也要校验列表中每个元素
}
```

---

## 9. Service 层

### 9.1 核心示例

```java
@Service                         // 标记为 Service Bean
@RequiredArgsConstructor         // 自动生成构造函数
public class MarketService {

    private final MarketRepository marketRepository;   // Spring 注入
    private final MongoTemplate mongoTemplate;          // Spring 注入

    public Market addCategory(String name, String image) {
        // 1. 业务校验：名称不能重复
        if (marketRepository.findAll().stream()
                .anyMatch(m -> m.getName().equals(name))) {
            throw new IllegalArgumentException("分类名称已存在");
        }

        // 2. 创建实体对象
        Market market = new Market();
        market.setName(name);
        market.setImage(image);

        // 3. 存入数据库
        return marketRepository.save(market);
    }
}
```

### 9.2 依赖注入原理

```java
// 你写的是：
@Service
@RequiredArgsConstructor
public class MarketService {
    private final MarketRepository marketRepository;
}

// Lombok @RequiredArgsConstructor 实际生成的代码相当于：
@Service
public class MarketService {
    private final MarketRepository marketRepository;

    public MarketService(MarketRepository marketRepository) {  // 构造函数
        this.marketRepository = marketRepository;
    }
}

// Spring 启动时：
// 1. 发现 MarketService 需要 MarketRepository
// 2. 发现 MarketRepository 接口有对应的实现类
// 3. 创建 MarketRepository 实例
// 4. 通过构造函数传给 MarketService
// 这个过程叫"依赖注入"（Dependency Injection）
```

### 9.3 MongoTemplate 的高级操作

当 `MongoRepository` 不能满足需求时，用 `MongoTemplate` 执行更复杂的 MongoDB 操作：

```java
// 模糊搜索：查找 burden（配料）包含某文字的菜品
public List<Market> findFoods(String text) {
    Query query = Query.query(
        Criteria.where("foods.burden").regex(".*" + text + ".*", "i")
    );
    return mongoTemplate.find(query, Market.class);
}

// 向嵌套数组添加元素（$push）
Query query = Query.query(Criteria.where("_id").is(categoryId));
Update update = new Update().push("foods", foodItem);
mongoTemplate.updateFirst(query, update, Market.class);

// 从嵌套数组移除元素（$pull）
Update update = new Update().pull("foods",
    Query.query(Criteria.where("_id").is(foodId)));
mongoTemplate.updateFirst(query, update, Market.class);

// 自增（$inc）
Update update = new Update().inc("foods.$.num", num);
mongoTemplate.updateMulti(query, update, Market.class);
```

---

## 10. Entity 与数据层

### 10.1 Entity 定义

```java
@Data                                                // Lombok: getter/setter/toString
@Document(collection = "markets")                    // MongoDB 集合名（= MySQL 的表名）
public class Market {
    @Id                                              // MongoDB 主键
    private String id;                               // MongoDB 自动生成

    private String name;                             // 分类名称，如"川菜"
    private String image;                            // 分类图标路径
    private List<FoodItem> foods = new ArrayList<>(); // 嵌套文档数组

    @Data
    public static class FoodItem {                   // 嵌套文档
        private String id;
        private String name;
        private String describe;                     // 做法描述
        private String burden;                       // 配料
        private String image;
        private Integer num = 0;                     // 被点次数
    }
}
```

**MongoDB 中的实际存储：**

```json
{
  "_id": "abc123",
  "name": "川菜",
  "image": "/static/images/market/chuan.jpg",
  "foods": [
    {
      "id": "food-uuid-1",
      "name": "宫保鸡丁",
      "describe": "鸡胸肉切丁，配花生...",
      "burden": "辣椒",
      "image": "/static/images/market/gongbao.jpg",
      "num": 15
    },
    {
      "id": "food-uuid-2",
      "name": "麻婆豆腐",
      "describe": "豆腐切块...",
      "burden": "花椒",
      "image": "/static/images/market/mapo.jpg",
      "num": 8
    }
  ]
}
```

**关键设计：** Market 和 foods 的 "一对多" 关系用嵌套文档实现，而不是两个独立的表。这是 MongoDB 的特色用法。

### 10.2 Repository 接口

```java
@Repository
public interface MarketRepository extends MongoRepository<Market, String> {
    // 空的！
}
```

**为什么是空的？** 继承 `MongoRepository<Market, String>` 后自动获得：
- `save(Market)` — 保存/更新
- `findById(String)` — 按 ID 查找
- `findAll()` — 查询全部
- `deleteById(String)` — 按 ID 删除
- `count()` — 统计数量

不需要写任何 SQL 或实现代码。

---

## 11. 全局响应与异常处理

### 11.1 响应包装

```java
// GlobalResponseBodyAdvice.java
@RestControllerAdvice                                     // 拦截所有 Controller 的返回值
public class GlobalResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public Object beforeBodyWrite(Object body, ...) {
        // 如果已经是标准格式，不再包装
        if (body instanceof Map && ((Map<?, ?>) body).containsKey("code")) {
            return body;
        }
        // 统一包装为：{code: 200, message: "success", data: 原始数据}
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "success");
        result.put("data", body);
        return result;
    }
}
```

**效果：**

```
Controller return:  Market对象
      │
      ▼
beforeBodyWrite 拦截
      │
      ▼
实际返回前端:  {"code": 200, "message": "success", "data": {...Market对象...}}
```

### 11.2 异常处理

```java
// GlobalExceptionHandler.java
@RestControllerAdvice                                     // 拦截所有 Controller 的异常
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)    // 指定处理哪类异常
    public ResponseEntity<Map<String, Object>> handle(IllegalArgumentException ex) {
        // 返回统一错误格式
        return buildResponse(HttpStatus.CONFLICT, request, ex.getMessage());
    }

    @ExceptionHandler(Exception.class)                   // 兜底：处理所有未捕获的异常
    public ResponseEntity<Map<String, Object>> handleAll(Exception ex) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, request, ...);
    }

    private ResponseEntity<Map<String, Object>> buildResponse(...) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", status.value());                 // HTTP 状态码，如 409
        body.put("timestamp", Instant.now().toString());  // 发生时间
        body.put("path", request.getRequestURI());        // 请求路径
        body.put("message", message);                     // 错误描述
        return ResponseEntity.status(status).body(body);
    }
}
```

**Service 中抛异常 → Controller 不用 try-catch → GlobalExceptionHandler 自动拦截：**

```
Service:  throw new IllegalArgumentException("分类名称已存在")
    │
    ▼
Spring 捕获异常
    │
    ▼
找到 @ExceptionHandler(IllegalArgumentException.class)
    │
    ▼
调用 handleIllegalArgument()
    │
    ▼
返回:  {"code": 409, "timestamp": "...", "path": "/api/market/...", "message": "分类名称已存在"}
```

---

## 12. 配置管理

### 12.1 多环境配置

```
application.yml          →  指定 spring.profiles.active: dev
application-dev.yml      →  开发环境：本地 MongoDB，端口 9001
application-prod.yml     →  生产环境：远程 MongoDB，端口 9001
```

**为什么要多环境？** 开发时连本地数据库，上线后连远程数据库。切换只需改 `application.yml` 一行：

```yaml
# application.yml
spring:
  application:
    name: demo1
  profiles:
    active: dev   # 改成 prod 就切到生产环境
```

### 12.2 关键配置

```yaml
# application-dev.yml
server:
  port: 9001                        # 服务端口
  servlet:
    context-path: /api              # 所有接口前缀 /api

spring:
  mongodb:
    uri: mongodb://127.0.0.1:27017/market   # 本地 MongoDB

app:
  upload:
    path: static/images/market/     # 自定义：图片上传目录
```

---

## 13. 文件上传

```java
// MarketController.java —— uploadImage 方法
@PostMapping(value = "/uploadImage", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public Map<String, Object> uploadImage(@RequestParam("file") MultipartFile file) {
    // 1. 获取原始文件名，提取扩展名
    String ext = originalFilename.substring(originalFilename.lastIndexOf("."));

    // 2. 用时间戳生成唯一文件名（避免重复）
    String filename = System.currentTimeMillis() + ext;

    // 3. 确保目标目录存在
    File destDir = new File(projectDir, "static/images/market");
    if (!destDir.exists()) destDir.mkdirs();

    // 4. 保存文件
    file.transferTo(new File(destDir, filename));

    // 5. 返回访问路径
    return result;  // "/static/images/market/1716987600000.jpg"
}
```

**配置限制（application-dev.yml）：**
```yaml
spring:
  servlet:
    multipart:
      max-file-size: 10MB     # 单文件最大 10MB
      max-request-size: 10MB  # 整个请求最大 10MB
```

---

## 14. 跨域与静态资源

```java
// WebMvcConfig.java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    // 1. 跨域配置（CORS）
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")           // 所有路径
                .allowedOriginPatterns("*")  // 允许任何来源
                .allowedMethods("*")         // 允许所有 HTTP 方法
                .allowedHeaders("*");        // 允许所有请求头
    }

    // 2. 静态资源映射
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/images/market/**")  // URL 访问路径
                .addResourceLocations("file:static/images/market/");  // 实际磁盘路径
    }
}
```

**CORS 是什么？** 浏览器的安全机制。如果前端运行在 `localhost:3000`，后端在 `localhost:9001`，浏览器默认会拦截跨域请求。以上配置允许跨域。

---

## 15. 学习路线图

### 阶段一：理解代码在做什么（1-2 天）

```
1. 启动项目，使用 Postman 或 curl 调用所有接口
2. 打开每个 Java 文件，对照本指南理解每个注解的作用
3. 画一张自己的架构图
```

### 阶段二：跟踪一次完整请求（1 天）

```
选一个简单接口（比如 GET /market/getAll），梳理完整调用链：

客户端 → GET /api/market/getAll
  → MarketController.getAll()
    → MarketService.getAll()
      → MarketRepository.findAll()
        → MongoDB 查询 → 返回 List<Market>
      ← Market 列表
    ← Market 列表
  ← GlobalResponseBodyAdvice 包装为 {code:200, data: [...]}
→ 返回 JSON 给浏览器
```

### 阶段三：动手修改（2-3 天）

```
练习 1：给 Market 实体加一个新字段（比如 "description"），
        修改 DTO 和 Service，看看需要改哪些地方

练习 2：新增一个简单的接口，比如 "统计所有分类下菜品总数"

练习 3：给 Order 增加一个按日期范围查询的接口
```

### 阶段四：核心技术深入（持续）

| 主题 | 对应知识点 |
|------|-----------|
| Spring IoC 容器 | Bean 生命周期、@Autowired vs 构造函数注入 |
| Spring Data MongoDB | MongoRepository 自定义查询方法、聚合查询 |
| Spring MVC | 拦截器、过滤器、参数解析器 |
| Java Stream API | 深入理解 map/filter/reduce |
| Maven | 多模块项目、自定义构建 |
| 测试 | JUnit、MockMvc、集成测试 |

---

## 附录：项目文件树与说明

```
demo1/
├── pom.xml                              # Maven 配置（依赖、构建）
├── mvnw / mvnw.cmd                      # Maven Wrapper（免安装 Maven）
├── .gitignore                           # Git 忽略规则
│
├── src/main/java/com/example/demo1/
│   ├── Demo1Application.java            # ★ 程序入口
│   │
│   ├── config/
│   │   └── WebMvcConfig.java            # 跨域 + 静态资源配置
│   │
│   ├── core/advice/
│   │   ├── GlobalResponseBodyAdvice.java # 统一响应格式包装
│   │   └── GlobalExceptionHandler.java   # 统一异常处理
│   │
│   └── module/
│       ├── market/                      # 菜单模块
│       │   ├── controller/
│       │   │   └── MarketController.java # 菜单接口
│       │   ├── service/
│       │   │   ├── MarketService.java    # 分类业务逻辑
│       │   │   └── FoodService.java      # 菜品业务逻辑
│       │   ├── dto/                     # 数据传输对象
│       │   │   ├── CreateCategoryDTO.java
│       │   │   ├── UpdateCategoryDTO.java
│       │   │   ├── AddFoodDTO.java
│       │   │   ├── UpdateFoodDTO.java
│       │   │   ├── DeleteFoodDTO.java
│       │   │   └── FoodDTO.java
│       │   └── entity/                  # 数据库实体
│       │       ├── Market.java          # Market 集合
│       │       └── MarketRepository.java # Market 数据访问
│       │
│       └── order/                       # 订单模块
│           ├── controller/
│           │   └── OrderController.java
│           ├── service/
│           │   └── OrderService.java
│           ├── dto/
│           │   ├── CreateOrderDTO.java
│           │   └── DeleteOrderDTO.java
│           └── entity/
│               ├── Order.java
│               └── OrderRepository.java
│
├── src/main/resources/
│   ├── application.yml                  # 主配置（指定环境）
│   ├── application-dev.yml              # 开发环境配置
│   └── application-prod.yml             # 生产环境配置
│
└── static/images/market/                # 上传的菜品图片
```

---

**最后建议：** 学 Java 最好的方式就是改代码。先跑起来，然后大胆修改，崩溃了就看报错信息，修复它。每修好一个 Bug，你就学会了一课。
