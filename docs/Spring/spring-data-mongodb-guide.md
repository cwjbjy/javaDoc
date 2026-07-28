# Spring Data MongoDB 指南

> 本指南循序渐进介绍 Spring Data MongoDB —— Spring 生态中操作 MongoDB 的标准方式。用同一个 `Product` 商品场景贯穿全文，每步只引入一个新概念。
> 基于 Spring Boot 4.x + Spring Data MongoDB 5.x。

---

## 目录

1. [CRUD 与没有框架的痛点](#1-crud-与没有框架的痛点)
2. [连接配置](#2-连接配置)
3. [MongoRepository：一行代码拥有 CRUD](#3-mongorepository一行代码拥有-crud)
4. [方法命名查询：三步升级](#4-方法命名查询三步升级)
5. [@Query：方法命名不够用时](#5-query方法命名不够用时)
6. [分页与排序](#6-分页与排序)
7. [MongoTemplate：万能操作入口](#7-mongotemplate万能操作入口)
8. [聚合管道](#8-聚合管道)
9. [索引管理](#9-索引管理)
10. [审计字段：自动记录创建/更新时间](#10-审计字段自动记录创建更新时间)
11. [事务](#11-事务)
12. [速查清单](#12-速查清单)

---

## 1. CRUD 与没有框架的痛点

### 1.1 CRUD 是什么

所有数据操作都可以归为四类：

```
C — Create   新增一条记录  →  INSERT
R — Read     读取记录      →  SELECT / find
U — Update   修改已有记录   →  UPDATE
D — Delete   删除一条记录  →  DELETE
```

任何一个涉及数据库的业务系统，本质上就是在组合这四个操作。

### 1.2 原生 Driver：四行代码能膨胀到四十行

用 MongoDB 原生 Driver 完成这四件事：

```java
// 用原生 MongoDB Driver 操作数据库
MongoClient client = MongoClients.create("mongodb://localhost:27017");
MongoDatabase db = client.getDatabase("shop");
MongoCollection<Document> collection = db.getCollection("products");

// C — 新增
Document newProduct = new Document("name", "冰可乐")
        .append("price", 3.5)
        .append("stock", 100);
collection.insertOne(newProduct);

// R — 查询
Document found = collection.find(new Document("name", "冰可乐")).first();
String name = found.getString("name");   // Object → String 手动转换
Double price = found.getDouble("price"); // Object → Double 手动转换

// U — 更新
collection.updateOne(
    new Document("_id", found.getObjectId("_id")),
    new Document("$set", new Document("price", 3.0))
);

// D — 删除
collection.deleteOne(new Document("_id", found.getObjectId("_id")));
```

**四个痛点**：

```
① 样板代码多：每步都 new Document()，字段多了极其啰嗦
② 手动类型转换：getString()、getDouble() 每个字段都要手动指定
③ 连接管理：MongoClient 的创建、关闭、连接池都得自己管
④ 没有 Repository 模式：查询逻辑散落在各 Service 中，无法复用
```

### 1.3 Spring Data MongoDB 的目标

```
你写的 5 行接口                     框架自动生成的 100 行实现
─────────────────                  ──────────────────────────
@Repository
public interface ProductRepository  →  连接管理、BSON 映射、
    extends MongoRepository               CRUD 实现、方法名解析、
        <Product, String> {               类型转换……
}
```

> **核心思想**：你声明"做什么"（接口 + 方法名），框架负责"怎么做"（实现、映射、连接）。

---

## 2. 连接配置

先让项目连上 MongoDB。

### 2.1 最简配置

`application.yml`：

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/shop
```

一行配置。Spring Boot 自动创建 `MongoClient`、`MongoTemplate`，扫描所有 `@Repository` 接口。

### 2.2 完整配置

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://user:password@localhost:27017/shop
      # 或分开写：
      # host: localhost
      # port: 27017
      # database: shop
      # username: user
      # password: password
```

### 2.3 URI 格式拆解

```
mongodb://[用户名:密码@]主机[:端口][/数据库][?选项]

示例：
mongodb://localhost:27017/shop                          ← 最简
mongodb://user:pass@localhost:27017/shop                 ← 带认证
mongodb://host1:27017,host2:27017/shop?replicaSet=rs0   ← 副本集
```

### 2.4 连接池

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/shop
      connection-pool:
        min-size: 5
        max-size: 50
        max-wait-time: 2000ms
```

> 配置完成，项目启动后自动连接。现在可以写代码操作数据库了。

---

## 3. MongoRepository：一行代码拥有 CRUD

用同一个 `Product` 商品场景，三步接入。

### 3.1 第一步：定义 Entity

```java
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "products")
public class Product {
    @Id
    private String id;
    private String name;
    private Double price;
    private Integer stock;
}
```

- `@Document`：映射到数据库的 `products` 集合
- `@Id`：主键
- `@Data`：Lombok 生成 getter/setter

### 3.2 第二步：声明 Repository

```java
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends MongoRepository<Product, String> {
}
```

**不写任何方法**。`<Product, String>` 的含义：

```
MongoRepository<Product, String>
                        │       │
                        ▼       ▼
                  Entity 类型   主键类型
```

### 3.3 第三步：在 Service 中使用

```java
@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;

    // C — 新增
    public Product create(Product product) {
        return productRepository.save(product);
    }

    // R — 查询
    public Optional<Product> findById(String id) {
        return productRepository.findById(id);
    }

    public List<Product> findAll() {
        return productRepository.findAll();
    }

    // U — 更新
    public void updatePrice(String id, Double newPrice) {
        Product product = productRepository.findById(id).orElseThrow();
        product.setPrice(newPrice);
        productRepository.save(product);
    }

    // D — 删除
    public void deleteById(String id) {
        productRepository.deleteById(id);
    }
}
```

### 3.4 你什么都没写，但已经拥有了这些方法

继承 `MongoRepository` 后，自动获得：

| 分类   | 方法                                              | 对应操作  |
| ------ | ------------------------------------------------- | --------- |
| Create | `save(entity)`、`saveAll(list)`                   | 新增/更新 |
| Read   | `findById(id)`、`findAll()`、`findAllById(ids)`   | 查询      |
| Update | `save(entity)`（id 已存在时）                     | 更新      |
| Delete | `deleteById(id)`、`delete(entity)`、`deleteAll()` | 删除      |
| 辅助   | `count()`、`existsById(id)`                       | 统计/判断 |

### 3.5 save() 的双重语义

```java
Product p = new Product();     // id == null，新建态
p.setName("冰可乐");
productRepository.save(p);     // → INSERT（没有 _id）

p.setPrice(3.0);
productRepository.save(p);     // → UPDATE（有 _id，已存在）
```

框架根据主键是否为空自动判断是新增还是更新。

### 本节回顾

```
第一步    第二步         第三步
Entity → Repository → Service 中使用
                      save() / findById() / deleteById()
                                         ↓
                                 CRUD 四个操作全部覆盖
                                 （以上全部不需要写实现代码）
```

---

## 4. 方法命名查询：三步升级

Repository 自带的方法只能按主键查。如果想**按商品名查**？——声明一个方法，方法名就是查询。

### 4.1 核心理念

```
你写的方法名                       Spring Data 解析为
findByName(String name)           db.products.find({"name": "冰可乐"})
  │    │
  │    └── 字段名
  └── 查询操作
```

> Spring Data 读你的方法名，拆解出"查什么字段、用什么条件"，自动生成 MongoDB 查询。不用写任何实现。

### 4.2 第一层：等于查询

在 Repository 接口中加一个方法：

```java
@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    // 只加这一行！
    Optional<Product> findByName(String name);
}
```

Service 中使用：

```java
public Product findByName(String name) {
    return productRepository.findByName(name)
            .orElseThrow(() -> new RuntimeException("商品不存在"));
}
```

> `findByName` → 去掉 `findBy` 就是字段名 `name`，参数值用于等于匹配。这就是方法命名查询的全部基础。

### 4.3 第二层：多条件组合

```java
@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    Optional<Product> findByName(String name);  // 已学

    // 新增：按名称和价格查（AND 组合）
    Optional<Product> findByNameAndPrice(String name, Double price);

    // 新增：按名称或 id 查（OR 组合）
    List<Product> findByNameOrPrice(String name, Double price);
}
```

调用 `findByNameAndPrice("冰可乐", 3.5)` → 框架自动生成 `{"name": "冰可乐", "price": 3.5}`。

### 4.4 第三层：高级匹配条件

```java
@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    // 模糊查询：名称包含关键词
    List<Product> findByNameContaining(String keyword);
    // findByNameContaining("可") → {"name": {$regex: "可"}}

    // 区间查询：价格在某个范围
    List<Product> findByPriceBetween(Double min, Double max);
    // findByPriceBetween(1.0, 10.0) → {"price": {$gte: 1.0, $lte: 10.0}}

    // 大于
    List<Product> findByStockGreaterThan(Integer minStock);

    // 小于
    List<Product> findByPriceLessThan(Double maxPrice);

    // 存在性判断（去重校验）
    boolean existsByName(String name);

    // 统计
    long countByPriceGreaterThan(Double price);
}
```

### 4.5 排序与限制

```java
// 排序：按价格升序
List<Product> findByNameContainingOrderByPriceAsc(String keyword);

// 限制条数：只需要前 5 条
List<Product> findTop5ByNameContaining(String keyword);
```

### 4.6 命名规则拆解

```
findTop5By  Name  Containing  OrderBy  Price   Asc
──┬────┬──  ─┬─   ────┬────   ───┬───  ──┬──   ─┬─
  │    │     │        │          │       │       │
查询 限制N条 字段   匹配方式    排序    排序字段  升/降
```

### 本节回顾

```
方法命名查询学习路径
────────────────────

第一层         第二层             第三层
findBy + 字段  → findBy...And...  → findBy...Containing
等于查询        多条件 AND/OR       findAllBy...Between
                                  findBy...GreaterThan
                                    ↑
                            每一步只学一个新关键字
                            （速查表见第 12 节）
```

---

## 5. @Query：方法命名不够用时

方法命名能覆盖 80% 场景。但有时需要写原生 MongoDB 查询——比如嵌套字段匹配、复杂的 `$or` 与 `$and` 组合。

### 5.1 MongoDB 查询操作符速览

`@Query` 中写的是 MongoDB 原生查询语法，以 `$` 开头的都是 **MongoDB 查询操作符**：

| 操作符       | 含义                              | 示例                                      | SQL 类比       |
| ------------ | --------------------------------- | ----------------------------------------- | -------------- |
| `$gt`        | 大于（Greater Than）              | `{price: {$gt: 5}}`                       | `price > 5`    |
| `$gte`       | 大于等于（Greater Than or Equal） | `{price: {$gte: 5}}`                      | `price >= 5`   |
| `$lt`        | 小于（Less Than）                 | `{price: {$lt: 10}}`                      | `price < 10`   |
| `$lte`       | 小于等于（Less Than or Equal）    | `{price: {$lte: 10}}`                     | `price <= 10`  |
| `$in`        | 属于列表                          | `{category: {$in: ["A","B"]}}`            | `IN ('A','B')` |
| `$regex`     | 正则匹配                          | `{name: {$regex: "可"}}`                  | `LIKE '%可%'`  |
| `$options`   | 正则选项（配合 `$regex`）         | `$options: 'i'`（忽略大小写）             | —              |
| `$elemMatch` | 数组内元素匹配                    | `{skus: {$elemMatch: {price: {$lt: 5}}}}` | 子查询         |

> 这些操作符不限于 `@Query`，在 `Criteria.where("price").gt(5)` 中也有对应的 Java API（见第 7 节 MongoTemplate）。

### 5.2 基本用法

```java
@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    // 等价于 db.products.find({"price": {$gt: 5}})
    @Query("{ 'price': { $gt: ?0 } }")
    List<Product> findByPriceGreaterThanFive(Double minPrice);

    // 多参数：?0 = 第一个参数，?1 = 第二个
    @Query("{ 'name': ?0, 'stock': { $gte: ?1 } }")
    List<Product> findByNameAndMinStock(String name, Integer minStock);
}
```

### 5.3 典型场景

```java
// $regex：正则匹配（忽略大小写）
@Query("{ 'name': { $regex: ?0, $options: 'i' } }")
List<Product> searchByName(String keyword);

// $in：属于列表
@Query("{ 'category': { $in: ?0 } }")
List<Product> findByCategories(List<String> categories);

// $elemMatch：数组内元素匹配
@Query("{ 'skus': { $elemMatch: { 'price': { $lt: ?0 } } } }")
List<Product> findProductsWithCheapSkus(Double maxPrice);
```

### 5.4 参数占位符

```
?0 → 第一个参数
?1 → 第二个参数
?n → 第 n+1 个参数（索引从 0 开始）
```

### 5.5 与命名查询的选择

```
场景                                 用哪个
───────────────────────────          ─────────
等于查询（findByName）               方法命名（更简洁）
多条件 + 排序 + 限制（findTop5...）  方法命名（可读性好）
嵌套字段查询                          @Query（命名无法表达）
复杂 $or / $nor                     @Query
正则匹配 + 忽略大小写                 @Query（方法命名不支持 options）
```

---

## 6. 分页与排序

### 6.1 Pageable 分页

Repository 方法参数中加 `Pageable`：

```java
@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    Page<Product> findByStockGreaterThan(Integer minStock, Pageable pageable);
    Slice<Product> findSliceByStockGreaterThan(Integer minStock, Pageable pageable);
}
```

使用：

```java
// 第一页（页码从 0 开始），每页 10 条，按价格降序
Pageable page1 = PageRequest.of(0, 10, Sort.by("price").descending());
Page<Product> firstPage = productRepository.findByStockGreaterThan(0, page1);
firstPage.getContent();      // 第 1 页数据
firstPage.hasNext();         // true（如果还有下一页）

// 第二页：只需要改 PageRequest.of 的第一个参数
Pageable page2 = PageRequest.of(1, 10, Sort.by("price").descending());
Page<Product> secondPage = productRepository.findByStockGreaterThan(0, page2);
secondPage.getContent();     // 第 2 页数据
secondPage.getNumber();      // 1（当前页码）

// Page 包含的元信息
firstPage.getTotalElements();    // 总记录数（Page 特有，额外执行 count() 查询）
firstPage.getTotalPages();       // 总页数（Page 特有）
firstPage.getSize();             // 每页大小
```

> **页码从 0 开始**：`PageRequest.of(0, 10)` 是第一页，`PageRequest.of(1, 10)` 是第二页。

### 6.2 Slice：日常开发更常用的分页

`Page` 每次查询都会额外执行一次 `count()` 来算总条数。数据量大时，`count()` 本身就是一次昂贵的全表扫描。

`Slice` 不执行 `count()`，而是多查一条数据来判断"还有没有下一页"：

```
你请求 10 条 → 框架查 11 条
                ├── 前 10 条：返回给用户
                └── 第 11 条存在 → hasNext() = true（还有下一页）
                第 11 条不存在 → hasNext() = false（最后一页）
```

```java
Slice<Product> slice = productRepository.findSliceByStockGreaterThan(0,
    PageRequest.of(0, 10, Sort.by("price").descending()));

slice.getContent();           // 当前页数据
slice.hasNext();              // 是否有下一页
slice.getNumber();            // 当前页码
slice.getSize();              // 每页大小
// ⚠️ Slice 没有 getTotalElements() 和 getTotalPages()
```

**为什么 Slice 更常用**：

| 场景                      | 用 Page 还是 Slice               |
| ------------------------- | -------------------------------- |
| 列表页底部显示"共 100 页" | Page（需要总页数）               |
| 无限滚动 / "加载更多"按钮 | Slice（只需知道是否有下一页）    |
| 手机端下拉刷新            | Slice                            |
| 后台管理的分页表格        | Page（需要总条数）               |
| 数据量大（百万级以上）    | Slice（省掉 count() 的扫描开销） |

> **简单原则**：如果 UI 只需要"加载更多"按钮而不显示总页数，用 Slice。只有需要展示"第 X 页 / 共 Y 页"时才用 Page。

### 6.3 Sort 直接排序

```java
List<Product> findByStockGreaterThan(Integer minStock, Sort sort);

// 调用：
productRepository.findByStockGreaterThan(0, Sort.by("price").descending());

// 多字段排序：
Sort sort = Sort.by(Sort.Order.desc("price"), Sort.Order.asc("name"));
```

---

## 7. MongoTemplate：万能操作入口

### 7.1 什么时候需要 MongoTemplate

```
MongoRepository                       MongoTemplate
─────────────────                     ──────────────
声明式，适合标准 CRUD                   编程式，适合复杂操作
save() / findById() 就够了            需要部分字段更新（$set）
方法名即查询                           操作数组（$push / $pull）
                                      复杂嵌套字段查询
                                      批量操作
```

### 7.2 Criteria + Query：构造查询条件

还记得第 5 节的 MongoDB 查询操作符吗？`Criteria` 就是它们的 **Java 方法版**。但 `Criteria` 只管"筛选条件"，排序和分页由 `Query` 负责：

```
职责分工
────────────────────────────────────────────

Criteria（"WHERE"）              Query（"LIMIT / ORDER BY / SKIP"）
───────────────────              ─────────────────────────────────
.where("price").gt(5)            .limit(10)          ← 限制条数
.lte(10)                          .skip(20)           ← 跳过条数
.and("name").regex("可","i")      .with(Sort.by(...)) ← 排序
.in("A", "B")                     .fields()           ← 投影（选字段）
.orOperator(...)
```

**另一个常见疑问：这里能用 `@Query` 吗？**

不能。`@Query` 是 Repository 接口的注解，适合**编译时就确定**的查询。`Criteria + Query` 是给 MongoTemplate 用的编程 API，适合**运行时动态决定**的查询——比如上面代码中 `keyword` 为 null 就不加正则条件，`@Query` 的固定字符串做不到。

```
@Query                      Criteria + Query
──────                      ────────────────
写在 Repository 接口上         写在 Service 方法中
查询条件写死在字符串里           条件可以 if 动态拼接
适合：固定查询                  适合：搜索表单、多条件筛选
```

下面看一个完整的动态查询示例：

```java
@Service
@RequiredArgsConstructor
public class ProductService {
    private final MongoTemplate mongoTemplate;

    public List<Product> findProducts(String keyword, Double minPrice, Double maxPrice) {
        //Criteria = WHERE 子句，用来查哪些文档
        Criteria criteria = Criteria.where("price").gte(minPrice).lte(maxPrice);

        if (keyword != null && !keyword.isEmpty()) {
            criteria.and("name").regex(".*" + keyword + ".*", "i");
        }

        //转成Query对象供其查询
        Query query = Query.query(criteria);
        //Query = 整条 SQL 语句，limit/skip/sort 永远都是 Query 的方法
        query.with(Sort.by("price").descending()).limit(10).skip(0);

        return mongoTemplate.find(query, Product.class);
    }
}
```

### 7.3 查询方法一览

```java
// 单条
Product product = mongoTemplate.findOne(query, Product.class);
Product product = mongoTemplate.findById("xxx", Product.class);

// 列表
List<Product> list = mongoTemplate.find(query, Product.class);
List<Product> all = mongoTemplate.findAll(Product.class);

// 计数、判断
long count = mongoTemplate.count(query, Product.class);
boolean exists = mongoTemplate.exists(query, Product.class);
```

### 7.4 Update：原子更新操作

**与 save() 的关键区别**：

```java
// save()：全量替换整个文档
// （但先 findById 查出全量数据，所以结果上只改了 price，其他字段不变）
Product p = productRepository.findById("123").orElseThrow();
p.setPrice(5.0);
productRepository.save(p);

// ⚠️ 如果 new Product() 只设 id 和 price 就 save，会把其他字段全清空
// Product p = new Product(); p.setId("123"); p.setPrice(5.0);
// productRepository.save(p);  ← name、stock、description 全丢了！

// Update：只修改指定字段，没有"覆盖"的风险
Query query = Query.query(Criteria.where("_id").is("123"));
Update update = new Update().set("price", 5.0);
mongoTemplate.updateFirst(query, update, Product.class);
```

### 7.5 常用 Update 操作符

```java
// $set：设置字段值
new Update().set("price", 5.0);

// $inc：原子增减（并发安全）
new Update().inc("stock", -1);      // 库存 -1

// $push：向数组添加
new Update().push("tags", "新品");

// $pull：从数组移除
new Update().pull("tags", "已下架");

// $addToSet：去重添加（已存在则忽略）
new Update().addToSet("tags", "热销");

// 链式组合
new Update()
    .set("price", 5.0)
    .inc("stock", -1)
    .push("tags", "活动商品");
```

### 7.6 Repository vs MongoTemplate 选择指南

```
                                 需要 $set / $push / $inc？
                                /           \
                              否             是
                              /               \
                    单一查询或简单排序？    MongoTemplate
                    /           \
                   是           否
                   /             \
      Repository（方法命名）     复杂条件 / 聚合
                                    \
                                   MongoTemplate
```

---

## 8. 聚合管道

聚合管道是 MongoDB 的数据分析工具。

**什么时候需要它？** `find()` 只能查出"文档原本的样子"。要回答"每个分类平均价格多少""本月销量最高的商品是哪些"——这些需要**分组、统计、变形**的查询，`find()` 做不到。

聚合管道的思路：文档经过多个处理阶段（stage）逐步流转，每个阶段做一件事，前一个阶段的输出是下一个阶段的输入：

```
              聚合管道
┌────┐   ┌─────────┐   ┌─────────┐   ┌──────────┐
│集合│──→│ $match   │──→│ $group   │──→│ $sort    │──→ 结果
└────┘   │（筛选）  │   │（分组）  │   │（排序）  │
         └─────────┘   └─────────┘   └──────────┘
```

### 8.1 示例：统计每个分类的商品数量和均价

在代码层面，`Aggregation` 是一个**管道构建器**——你用它的静态方法创建各个阶段，再串成一条管道，最后交给 `mongoTemplate.aggregate()` 执行：

```
Aggregation.group("category")   →  创建 $group 阶段（"按 category 分组"）
Aggregation.sort(...)           →  创建 $sort 阶段（"按某字段排序"）
Aggregation.newAggregation(.)   →  把阶段串成完整管道
mongoTemplate.aggregate(...)    →  执行管道，返回结果
```

下面统计 products 集合中每个分类各有多少商品、均价多少：

```java
@Service
@RequiredArgsConstructor
public class ProductStatsService {
    private final MongoTemplate mongoTemplate;

    public List<CategoryStats> countByCategory() {
        GroupOperation group = Aggregation.group("category")
                .count().as("count")
                .avg("price").as("avgPrice");

        SortOperation sort = Aggregation.sort(Sort.by(Sort.Direction.DESC, "count"));

        Aggregation aggregation = Aggregation.newAggregation(group, sort);

        AggregationResults<CategoryStats> results = mongoTemplate.aggregate(
                aggregation, "products", CategoryStats.class);

        return results.getMappedResults();
    }
}

// 结果接收类
@Data
public class CategoryStats {
    private String id;        // $group 后的 _id（category 值）
    private long count;
    private double avgPrice;
}
```

### 8.2 常用聚合阶段

```java
// $match：筛选（等价于 find 的条件）
Aggregation.match(Criteria.where("price").gte(5.0));

// $group：分组统计
Aggregation.group("category")
    .count().as("count")
    .sum("stock").as("totalStock")
    .avg("price").as("avgPrice");

// $sort：排序
Aggregation.sort(Sort.by(Sort.Direction.DESC, "avgPrice"));

// $project：投影（选择/重命名/计算字段）
Aggregation.project()
    .andInclude("name", "price")
    .andExpression("price * 1.2").as("taxedPrice");

// $limit / $skip：分页
Aggregation.limit(10);
Aggregation.skip(0);

// $unwind：展开数组
Aggregation.unwind("skus");

// 组合
Aggregation aggregation = Aggregation.newAggregation(
    match, unwind, group, project, sort, skip, limit
);
```

### 8.3 Repository 中定义聚合

简单管道也可以用 `@Aggregation` 注解：

```java
@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    @Aggregation(pipeline = {
        "{ $match: { price: { $gte: ?0 } } }",
        "{ $group: { _id: '$category', count: { $sum: 1 } } }",
        "{ $sort: { count: -1 } }"
    })
    List<CategoryStats> aggregateByCategory(Double minPrice);
}
```

---

## 9. 索引管理

**索引是什么？** 就像书的目录——不翻遍全书也能快速定位到某一章。没有索引时，查询 `findByName("冰可乐")` 需要 MongoDB 逐条扫描集合中每一条文档（全表扫描），数据量一大就慢得难以忍受。

```
无索引：逐条翻                          有索引：直接定位
─────────────                          ─────────────
文档1 → 不是                            索引：name → 文档位置
文档2 → 不是                                ↓
文档3 → 不是                            "冰可乐" → 第 42 条 ← 直接跳到
 ...
文档100万 → 找到了（花了 5 秒）            （毫秒级）
```

**什么字段该加索引？** 经常出现在查询条件、排序、或需要唯一性约束的字段。

**索引不免费**：索引占用磁盘空间，每次写入/更新都要同步更新索引。只在真正需要的字段上加。

### 9.1 @Indexed：单字段索引

```java
@Data
@Document(collection = "products")
public class Product {
    @Id
    private String id;

    @Indexed(unique = true)              // 唯一索引
    private String name;

    @Indexed                              // 普通索引
    private String category;

    @Indexed(expireAfterSeconds = 2592000)  // TTL：30 天后自动删除
    private Date createdAt;
}
```

| 属性                 | 说明                   |
| -------------------- | ---------------------- |
| `unique`             | 唯一索引               |
| `background`         | 后台创建（不阻塞）     |
| `expireAfterSeconds` | TTL 秒数，到期自动删除 |

### 9.2 @CompoundIndex：复合索引

```java
@Document(collection = "products")
@CompoundIndex(name = "idx_cat_price", def = "{ 'category': 1, 'price': -1 }")
//               ↑ 索引名             ↑ 1=升序, -1=降序
public class Product { ... }

// 多个复合索引
@CompoundIndexes({
    @CompoundIndex(name = "idx_cat_price", def = "{ 'category': 1, 'price': -1 }"),
    @CompoundIndex(name = "idx_cat_stock", def = "{ 'category': 1, 'stock': 1 }")
})
public class Product { ... }
```

---

## 10. 审计字段：自动记录创建/更新时间

大部分实体类都需要记录"什么时候创建的""什么时候最后修改的"。每次都手动 `setCreatedAt(LocalDateTime.now())` 又麻烦又容易忘。Spring Data 提供了审计（Auditing）机制：**字段上加注解，框架帮你自动填**。

只需两步：

**第一步：Entity 字段上加注解**

```java
@Data
@Document(collection = "products")
public class Product {
    @Id
    private String id;
    private String name;

    @CreatedDate              // 首次 save() 时自动填入当前时间
    private LocalDateTime createdAt;

    @LastModifiedDate         // 每次 save() 时自动更新为当前时间
    private LocalDateTime updatedAt;
}
```

**第二步：在任意配置类上加 `@EnableMongoAuditing`**

这个注解告诉 Spring："扫描所有 Entity 的 `@CreatedDate` / `@LastModifiedDate` 字段，在调用 `save()` 时自动填充"。

```java
@Configuration
@EnableMongoAuditing          // 开启审计功能
public class MongoConfig {
    // 不需要写任何代码，注解本身足够了
}
```

> `MongoConfig` 放在哪？放在能被 Spring 扫描到的包下即可（如 `config/` 包），或者直接放在启动类同级目录。

之后每次 `productRepository.save(product)`，`createdAt` 和 `updatedAt` 自动填充，不需要手动 `setXxx()`。

---

## 11. 事务

MongoDB 4.0+ 支持多文档事务。

> **副本集是什么？** MongoDB 的事务依赖副本集才能运行。副本集在生产环境中通常是多台服务器（主节点 + 从节点）维护同一份数据，但开发机用**单节点副本集**就够了——一台机器既是主节点又独立运行，同样支持事务：
>
> ```bash
> # 开发机启动单节点副本集（一台机器搞定）
> mongod --replSet rs0 --dbpath /data/db --port 27017
> # 然后在 mongo shell 中初始化：
> rs.initiate()
> ```
>
> 如果没有副本集，`@Transactional` 会报错。生产环境一般已由运维配置好。

### 11.1 声明式事务

```java
@Service
@RequiredArgsConstructor
public class OrderService {
    private final MongoTemplate mongoTemplate;

    @Transactional  // 跨多个集合的原子操作
    public void createOrder(Order order) {
        // 1. 扣减库存
        mongoTemplate.updateFirst(
            Query.query(Criteria.where("_id").is(order.getProductId())),
            new Update().inc("stock", -1), Product.class);

        // 2. 创建订单
        mongoTemplate.save(order);

        // 任一步失败，全部回滚
    }
}
```

### 11.2 事务限制

- 必须副本集（单节点不支持）
- 默认 60 秒超时
- 大批量操作（几千条以上）不推荐用事务——通过内嵌文档设计避免跨集合操作更优

---

## 12. 速查清单

### 12.1 连接配置速查

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://user:pass@host:27017/dbname
```

### 12.2 Repository 速查

```java
@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    // === 自带方法（零代码） ===
    // save(), findById(), findAll(), deleteById(), count(), existsById()

    // === 方法命名查询（三步升级） ===
    Optional<Product> findByName(String name);                        // 等于
    Optional<Product> findByNameAndPrice(String name, Double price);  // AND
    List<Product> findByNameContaining(String keyword);               // 模糊
    List<Product> findByPriceBetween(Double min, Double max);        // 区间
    List<Product> findTop5ByNameContainingOrderByPriceAsc(String k);  // 排序+限制
    boolean existsByName(String name);                                // 存在判断

    // === @Query（方法命名不够用时） ===
    @Query("{ 'price': { $gt: ?0 } }")
    List<Product> findExpensive(Double minPrice);

    // === 分页 ===
    Page<Product> findByStockGreaterThan(Integer min, Pageable pageable);
}
```

### 12.3 方法命名关键字速查

| 关键字                 | MongoDB 操作 | 示例                                 |
| ---------------------- | ------------ | ------------------------------------ |
| `findBy`               | 等于         | `findByName("可乐")`                 |
| `findBy...Containing`  | 模糊包含     | `findByNameContaining("可")`         |
| `findBy...Between`     | 区间         | `findByPriceBetween(1.0, 10.0)`      |
| `findBy...GreaterThan` | 大于         | `findByStockGreaterThan(10)`         |
| `findBy...LessThan`    | 小于         | `findByPriceLessThan(5.0)`           |
| `findBy...In`          | 在列表中     | `findByCategoryIn(List.of("A","B"))` |
| `findBy...IsNull`      | 为 null      | `findByDescriptionIsNull()`          |
| `findBy...And...`      | AND          | `findByNameAndPrice("a", 3.5)`       |
| `findBy...Or...`       | OR           | `findByNameOrPrice("a", 3.5)`        |
| `First` / `Top`        | 限制条数     | `findTop5By...`                      |
| `OrderBy...Asc`        | 升序         | `findBy...OrderByPriceAsc()`         |
| `OrderBy...Desc`       | 降序         | `findBy...OrderByPriceDesc()`        |
| `existsBy`             | 存在判断     | `existsByName("可乐")`               |
| `countBy`              | 统计         | `countByStatus(1)`                   |
| `deleteBy`             | 按条件删     | `deleteByStatus(-1)`                 |

### 12.4 MongoTemplate 速查

```java
// 查询
Query query = Query.query(Criteria.where("price").gte(5.0));
List<Product> list = mongoTemplate.find(query, Product.class);
long count = mongoTemplate.count(query, Product.class);

// 排序 + 分页
query.with(Sort.by("price").descending()).limit(10).skip(0);

// 原子更新
Update update = new Update().set("price", 5.0).inc("stock", -1);
mongoTemplate.updateFirst(query, update, Product.class);
```

### 12.5 Update 操作符速查

```
$set         set("field", value)        设置字段值
$inc         inc("field", delta)        原子增减
$push        push("field", value)       数组末尾添加
$pull        pull("field", value)       数组移除元素
$addToSet    addToSet("field", value)   去重添加
```

### 12.6 选择指南

```
Repository 方法命名 → 80% 场景（简单查询、排序、分页）
@Query              → 复杂查询（嵌套字段、正则、options）
MongoTemplate       → 部分更新（$set/$inc）、聚合管道
```

---

**最后：** Spring Data MongoDB 的本质是**声明代替实现**。声明 Entity（`@Document`），声明 Repository（继承接口），声明方法名（`findBy...`），框架自动完成剩下的一切。MongoTemplate 是复杂场景的"逃生舱"——它让你随时退回到底层 API，不做任何妥协。
