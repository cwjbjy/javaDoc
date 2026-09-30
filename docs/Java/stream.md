# 遍历、Lambda 与 Stream API

> 从传统循环到函数式集合处理——理解每种方式解决了什么问题、引入了什么局限

## 目录

1. [遍历方式的演进](#1-遍历方式的演进)
2. [Lambda 关键补充](#2-lambda-关键补充)
3. [Stream API 基础](#3-stream-api-基础)
4. [Stream 中间操作](#4-stream-中间操作)
5. [Stream 终止操作](#5-stream-终止操作)
6. [Collectors 进阶](#6-collectors-进阶)
7. [数值流与统计](#7-数值流与统计)
8. [实战案例](#8-实战案例)
9. [何时不用 Stream](#9-何时不用-stream)
10. [parallelStream 的真相](#10-parallelstream-的真相)
11. [下一步](#下一步)
12. [快速参考](#快速参考)

> **参见**：Lambda 基础语法与方法引用 → [Lambda.md](./Lambda.md) ｜ Optional 最佳实践 → [Optional.md](./Optional.md)

---

## 1. 遍历方式的演进

同一个需求：**遍历商品列表，打印每个商品的名称**。三种写法，三种思路。

### 传统 for 循环

```java
List<Product> products = getProducts();

for (int i = 0; i < products.size(); i++) {
    Product p = products.get(i);
    System.out.println(p.getName());
}
// 输出:
// iPhone 15
// MacBook Pro
// AirPods
```

| 优势                                   | 局限                             |
| -------------------------------------- | -------------------------------- |
| 可控制索引（跳跃、倒序）               | 样板代码多（初始化、边界、步进） |
| 可 `break` / `continue`                | 容易写错边界条件（`<` vs `<=`）  |
| 可在循环内安全修改集合（配合索引调整） | 对 List 以外的集合不适用         |

### 增强 for 循环（for-each）

```java
List<Product> products = getProducts();

for (Product p : products) {
    System.out.println(p.getName());
}
// 输出:
// iPhone 15
// MacBook Pro
// AirPods
```

| 优势                       | 局限                                                    |
| -------------------------- | ------------------------------------------------------- |
| 简洁，无需管理索引         | 无法获取当前索引                                        |
| 适用于所有 `Iterable` 集合 | 不能 `break` 后知道位置                                 |
| 不会写错边界               | ⚠️ 遍历中修改集合会抛 `ConcurrentModificationException` |

### ⚠️ ConcurrentModificationException 陷阱

```java
List<Product> products = new ArrayList<>(getProducts());

// ❌ 错误：增强 for 中直接 remove
for (Product p : products) {
    if (p.getStock() == 0) {
        products.remove(p);  // 抛出 ConcurrentModificationException
    }
}
```

**原因**：增强 for 底层使用 `Iterator`，`Iterator` 内部记录了 `expectedModCount`。直接调用 `list.remove()` 修改了 `modCount`，下次迭代时 `expectedModCount != modCount`，触发 fail-fast。

```java
// ✅ 正确方式 1：removeIf（会修改原集合）（Java 8+，最简洁，推荐）
products.removeIf(p -> p.getStock() == 0);

// ✅ 正确方式 2：Iterator.remove()（适用于所有集合类型，removeIf 底层就是它）
Iterator<Product> it = products.iterator();
while (it.hasNext()) {
    Product p = it.next();
    if (p.getStock() == 0) {
        it.remove();  // 安全：同步更新 expectedModCount
    }
}

// ✅ 正确方式 3：Stream filter 生成新集合（不修改原集合）
List<Product> inStock = products.stream()
    .filter(p -> p.getStock() > 0)
    .collect(Collectors.toList());
// inStock → 只包含有库存的商品，products 不变
```

> 📖 集合 fail-fast 机制的更多细节参见 [collections-framework.md](./collections-framework.md)

### forEach + Lambda

```java
List<Product> products = getProducts();

products.forEach(p -> System.out.println(p.getName()));
// 输出:
// iPhone 15
// MacBook Pro
// AirPods

// 方法引用（更简洁）
products.forEach(p -> System.out.println(p.getName()));
// 等价于：
// 不能直接写 Product::getName，因为 forEach 需要 Consumer<Product>
// 但可以这样：
products.stream()
    .map(Product::getName)
    .forEach(System.out::println);
// 输出:
// iPhone 15
// MacBook Pro
// AirPods
```

| 优势                          | 局限                      |
| ----------------------------- | ------------------------- |
| 函数式风格，代码简洁          | 无法 `break` / `continue` |
| 可组合（链式调用）            | 受检异常不能直接抛出      |
| 意图更明确（"对每个元素做X"） | 无法获取索引              |
| 为 Stream 打基础              | 不能修改外部局部变量      |

### 从 forEach 到 Stream：为什么还需要 Stream？

`forEach` 只能"对每个元素做一件事"。但真实需求往往是：

```java
// 需求：筛选有库存的商品 → 提取名称 → 转大写 → 取前3个
// 用 forEach 怎么做？需要手动管理中间状态——丑陋且易错

List<String> result = new ArrayList<>();
for (Product p : products) {
    if (p.getStock() > 0) {
        result.add(p.getName().toUpperCase());
        if (result.size() == 3) break;
    }
}
```

**Stream 的解决方案**：把"筛选 → 转换 → 限制 → 收集"表达为一条链：

```java
List<String> result = products.stream()
    .filter(p -> p.getStock() > 0)       // 筛选
    .map(p -> p.getName().toUpperCase()) // 转换
    .limit(3)                            // 限制
    .collect(Collectors.toList());       // 收集
// result → ["IPHONE 15", "MACBOOK PRO", "AIRPODS"]
```

这就是 Stream 的核心价值：**声明式地组合操作，而非命令式地管理状态**。

---

## 2. Lambda 关键补充

> Lambda 基础语法（箭头函数、函数式接口、方法引用四种形式）参见 → [Lambda.md](./Lambda.md)

本节只补充**在 Stream 链中实际会遇到的问题**。

### 变量捕获：effectively final

Lambda 只能捕获 `final` 或 **effectively final**（赋值后不再修改）的局部变量：

```java
// ❌ 编译失败：sum 不是 effectively final
int sum = 0;
products.forEach(p -> sum += p.getStock());  // Error: local variables must be final or effectively final


// ✅ 正确方式：使用 Stream reduce（推荐，无副作用）
int totalStock = products.stream()
    .mapToInt(Product::getStock)
    .sum();
// totalStock → 总库存数
```

**为什么有这个限制？** Lambda 捕获的是变量的**值副本**（对于基本类型）或**引用副本**（对于对象）。如果允许修改局部变量，Lambda 内外的值会不同步，造成混乱。

### 受检异常穿透

常见函数式接口（`Function`、`Predicate`、`Consumer`）的方法签名**不声明 throws**，受检异常无法直接穿透：

```java
// ❌ 编译失败：Function.apply 不声明 throws IOException
Function<String, String> reader = path -> Files.readString(Path.of(path));

// ✅ Lambda 内 try-catch 包装
Function<String, String> reader = path -> {
    try {
        return Files.readString(Path.of(path));
    } catch (IOException e) {
        throw new UncheckedIOException(e);
    }
};
```

### 保留业务上下文的异常处理

数据转换失败时，**不要用统一包装异常掩盖业务语义**。应保留字段名、记录号、原始值：

```java
// ❌ 丢失上下文：哪条记录？哪个字段？原始值是什么？
List<Order> orders = rows.stream()
    .map(row -> parseOrder(row))  // 内部 catch 后抛 RuntimeException("解析失败")
    .collect(Collectors.toList());

// ✅ 保留上下文
List<Order> orders = new ArrayList<>();
for (int i = 0; i < rows.size(); i++) {
    Row row = rows.get(i);
    try {
        orders.add(parseOrder(row));
    } catch (Exception e) {
        log.error("第 {} 行解析失败, orderId={}, amount={}, 原因: {}",
            i + 1, row.getOrderId(), row.getAmount(), e.getMessage());
        // 业务决定：跳过 or 收集错误 or 中断
    }
}
```

---

## 3. Stream API 基础

### 创建 Stream

实际开发中 **90% 以上的 Stream 来自集合**，其他方式了解即可。

```java
// ⭐ 从集合创建（最常用）——集合来源多样：方法返回值、数据库查询、接口入参等
List<String> list = List.of("A", "B", "C");          // Java 9+ 不可变 List
List<String> list2 = new ArrayList<>(List.of("A"));   // 需要可变时
List<User> users = userService.findAll();             // 企业中最常见：数据库查询结果

Stream<String> stream = list.stream();                // 顺序流
Stream<String> parallelStream = list.parallelStream(); // 并行流（慎用，见第 10 节）

// --- 以下了解即可，实际很少用 ---

// 从数组创建
String[] arr = {"A", "B", "C"};
Stream<String> stream2 = Arrays.stream(arr);
Stream<String> stream3 = Stream.of("A", "B", "C");

// 生成 Stream（测试或特殊算法场景）
Stream<Integer> stream4 = Stream.generate(() -> 1).limit(10);       // → 1,1,1,...
Stream<Integer> stream5 = Stream.iterate(0, n -> n + 2).limit(5);  // → 0, 2, 4, 6, 8
IntStream.range(0, 5).boxed();           // → 0, 1, 2, 3, 4
IntStream.rangeClosed(1, 5).boxed();     // → 1, 2, 3, 4, 5

// 空 Stream（边界兜底）
Stream<String> empty = Stream.empty();
```

### Stream 特点

```java
// ⚠️ Stream 只能消费一次
Stream<String> stream = list.stream();
stream.forEach(System.out::println);  // ✅ 正常输出 A B C
stream.forEach(System.out::println);  // ❌ IllegalStateException: stream has already been operated upon or closed

// ⚠️ Stream 不修改原集合
List<String> original = new ArrayList<>(Arrays.asList("a", "b", "c"));
List<String> upper = original.stream()
    .map(String::toUpperCase)
    .collect(Collectors.toList());
// upper → [A, B, C]
// original → [a, b, c]（不变）

// ⚠️ 惰性求值：中间操作不执行，直到遇到终止操作
Stream<String> lazy = list.stream()
    .filter(s -> {
        System.out.println("filtering: " + s);  // 不会打印！
        return s.startsWith("A");
    });
// 只有调用 collect/forEach/count 等终止操作时，filter 才真正执行
```

### Stream 管道模型

```
┌─────────┐     ┌──────────────┐     ┌──────────────┐     ┌─────────────┐
│  数据源  │────▶│  中间操作 1   │────▶│  中间操作 2   │────▶│  终止操作    │
│ (集合)   │     │  (filter)    │     │  (map)       │     │ (collect)   │
└─────────┘     └──────────────┘     └──────────────┘     └─────────────┘
                  返回新 Stream          返回新 Stream         返回最终结果
                  惰性，不执行            惰性，不执行           触发整条链执行
```

---

## 4. Stream 中间操作

中间操作返回新的 Stream，可以链式调用。**惰性求值**——只有终止操作才触发计算。

### filter - 过滤

```java
List<Product> products = getProducts();

// 筛选价格大于100的商品
List<Product> filtered = products.stream()
    .filter(p -> p.getPrice().compareTo(new BigDecimal("100")) > 0)
    .collect(Collectors.toList());
// filtered → 只包含价格 > 100 的商品

// 多条件过滤（等价于 && 连接）
List<Product> result = products.stream()
    .filter(p -> p.getPrice().compareTo(new BigDecimal("100")) > 0)
    .filter(p -> p.getStock() > 0)
    .filter(p -> p.getName().contains("iPhone"))
    .collect(Collectors.toList());
// result → 价格>100 且 有库存 且 名称含"iPhone"的商品
```

### map - 转换

```java
// 提取所有商品名称
List<String> names = products.stream()
    .map(Product::getName)
    .collect(Collectors.toList());
// names → ["iPhone 15", "MacBook Pro", "AirPods"]

// 提取并转大写
List<String> upperNames = products.stream()
    .map(Product::getName)
    .map(String::toUpperCase)
    .collect(Collectors.toList());
// upperNames → ["IPHONE 15", "MACBOOK PRO", "AIRPODS"]

// 复杂转换：Entity → VO
List<ProductVO> vos = products.stream()
    .map(p -> {
        ProductVO vo = new ProductVO();
        vo.setId(p.getId());
        vo.setName(p.getName());
        vo.setPriceStr(p.getPrice().toString());
        return vo;
    })
    .collect(Collectors.toList());
```

### flatMap - 扁平化

```java
// 将多个列表合并为一个
List<List<String>> lists = Arrays.asList(
    Arrays.asList("A", "B"),
    Arrays.asList("C", "D"),
    Arrays.asList("E")
);

List<String> flattened = lists.stream()
    .flatMap(list -> list.stream())
    .collect(Collectors.toList());
// flattened → [A, B, C, D, E]

// 实际场景：订单 → 订单项
List<Order> orders = getOrders();
List<OrderItem> allItems = orders.stream()
    .flatMap(order -> order.getItems().stream())
    .collect(Collectors.toList());
// allItems → 所有订单的所有订单项，平铺为一维列表
```

#### flatMap 处理逗号分隔字段（企业高频场景）

数据库中常见 `"Java,Spring,MySQL"` 格式的标签字段，需要拆分为独立元素：

```java
List<String> tagFields = Arrays.asList("Java,Spring", "MySQL", null, "Redis,,Java", "");

// ❌ 不完整：没有处理 null 和空字符串
List<String> tags1 = tagFields.stream()
    .flatMap(s -> Arrays.stream(s.split(",")))  // split 返回 String[]，需要 Arrays.stream 转为 Stream
    .collect(Collectors.toList());
// → NullPointerException（s 为 null 时）

// ✅ 完整链路
List<String> tags = tagFields.stream()
    .filter(StringUtils::isNotBlank)                          // 1. 过滤 null 和空白
    .flatMap(s -> Arrays.stream(s.split(",")))               // 2. 拆分为独立元素
    .map(String::trim)                                        // 3. 去除前后空格
    .filter(StringUtils::isNotBlank)                          // 4. 过滤拆分后的空字符串（"Redis,,Java" 中间）
    .distinct()                                               // 5. 去重（业务决定是否需要）
    .collect(Collectors.toList());
// tags → [Java, Spring, MySQL, Redis]
```

#### map vs flatMap 的区别

```java
List<String> csvList = Arrays.asList("A,B", "C,D");

// map：一对一转换，结果仍是嵌套结构
List<List<String>> nested = csvList.stream()
    .map(s -> Arrays.asList(s.split(",")))
    .collect(Collectors.toList());
// nested → [[A, B], [C, D]]

// flatMap：一对多转换 + 扁平化，结果是平铺结构
List<String> flat = csvList.stream()
    .flatMap(s -> Arrays.stream(s.split(",")))
    .collect(Collectors.toList());
// flat → [A, B, C, D]
```

> 💡 **选择规则**：转换后每个元素产生**一个**结果用 `map`；产生**多个**结果（需要扁平化）用 `flatMap`。

### distinct - 去重

```java
List<Integer> numbers = Arrays.asList(1, 2, 2, 3, 3, 3);
List<Integer> unique = numbers.stream()
    .distinct()
    .collect(Collectors.toList());
// unique → [1, 2, 3]

// 根据属性去重（需要重写 equals/hashCode，或用 TreeSet 技巧）
List<Product> uniqueProducts = products.stream()
    .filter(distinctByKey(Product::getName))
    .collect(Collectors.toList());

// 辅助方法（保留在工具类中）
private static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
    Set<Object> seen = ConcurrentHashMap.newKeySet();
    return t -> seen.add(keyExtractor.apply(t));
}
```

### sorted - 排序

```java
List<Integer> numbers = Arrays.asList(3, 1, 2);

// 自然排序
List<Integer> sorted = numbers.stream()
    .sorted()
    .collect(Collectors.toList());
// sorted → [1, 2, 3]

// 使用 Comparator（推荐）
List<Product> byPrice = products.stream()
    .sorted(Comparator.comparing(Product::getPrice))
    .collect(Collectors.toList());
// byPrice → 按价格升序

List<Product> byPriceDesc = products.stream()
    .sorted(Comparator.comparing(Product::getPrice).reversed())
    .collect(Collectors.toList());
// byPriceDesc → 按价格降序

// 多字段排序
List<Product> multiSort = products.stream()
    .sorted(Comparator.comparing(Product::getCategory)
            .thenComparing(Product::getPrice))
    .collect(Collectors.toList());
// multiSort → 先按分类升序，同分类内按价格升序
```

### limit / skip - 分页

```java
// 前3个
List<Product> top3 = products.stream()
    .limit(3)
    .collect(Collectors.toList());
// top3 → 列表中前 3 个商品

// 跳过前2个
List<Product> after2 = products.stream()
    .skip(2)
    .collect(Collectors.toList());
// after2 → 从第 3 个开始的所有商品

// 分页：第2页，每页10条
int pageNum = 2, pageSize = 10;
List<Product> page = products.stream()
    .skip((long) (pageNum - 1) * pageSize)
    .limit(pageSize)
    .collect(Collectors.toList());
// page → 第 11~20 个商品
```

### takeWhile / dropWhile - 按条件截取（Java 9+）

`limit` / `skip` 按数量截取，`takeWhile` / `dropWhile` 按条件截取：

```java
// 已排序的成绩列表
List<Integer> scores = List.of(95, 88, 76, 72, 65, 58, 43);

// takeWhile：从头开始取，遇到不满足条件的就停止
List<Integer> passed = scores.stream()
    .takeWhile(s -> s >= 60)
    .collect(Collectors.toList());
// passed → [95, 88, 76, 72, 65]（遇到 58 停止，58 和 43 都不取）

// dropWhile：从头开始丢弃，遇到不满足条件的就停止丢弃
List<Integer> failed = scores.stream()
    .dropWhile(s -> s >= 60)
    .collect(Collectors.toList());
// failed → [58, 43]（丢弃了 95~65，从 58 开始保留）

// 实际场景：取有序数据中满足条件的前缀
List<Order> recentOrders = orders.stream()
    .sorted(Comparator.comparing(Order::getCreateTime).reversed())
    .takeWhile(o -> o.getCreateTime().isAfter(LocalDateTime.now().minusDays(7)))
    .collect(Collectors.toList());
// recentOrders → 最近 7 天内的订单（遇到超过 7 天的就停止）
```

> ⚠️ `takeWhile` / `dropWhile` 依赖**顺序**——它们从头开始逐个检查，遇到第一个不满足条件的元素就停止。如果数据无序，结果可能不符合预期。

### peek - 调试（仅用于调试！）

```java
// peek 不修改元素，只用于调试观察中间状态
List<String> result = list.stream()
    .filter(s -> s.startsWith("A"))
    .peek(s -> System.out.println("After filter: " + s))   // 调试用
    .map(String::toUpperCase)
    .peek(s -> System.out.println("After map: " + s))      // 调试用
    .collect(Collectors.toList());
// 控制台输出:
// After filter: A
// After map: A
// result → [A]
```

> ⚠️ **peek 只用于临时诊断**，不能承载业务逻辑。生产代码中不应依赖 peek 的副作用。参见 [第 9 节](#9-何时不用-stream)。

---

## 5. Stream 终止操作

终止操作触发流的计算，返回最终结果。**执行完终止操作后，Stream 即被消费，不能复用。**

### Collectors 是什么？

在深入 `collect` 之前，先建立心智模型：

```
┌──────────────────────────────────────────────────────────┐
│  stream.collect(Collectors.toList())                     │
│         │                │                               │
│         ▼                ▼                               │
│    终止动作           收集策略                             │
│   "把元素攒起来"    "攒成什么形状"                         │
│                                                          │
│  Collectors = 策略工具箱，提供各种"攒法"：                  │
│  • toList()     → 攒成 List                              │
│  • toSet()      → 攒成 Set                               │
│  • toMap(...)   → 攒成 Map                               │
│  • groupingBy(...) → 按 key 分组攒                        │
│  • joining(...)    → 拼成字符串                           │
│  • counting()      → 计数                                │
└──────────────────────────────────────────────────────────┘
```

### collect - 收集

#### Collector 速查表

| Collector                | 作用               | 结果类型                |
| ------------------------ | ------------------ | ----------------------- |
| `toList()` / `toSet()`   | 收集为 List / Set  | `List` / `Set`          |
| `toMap(k, v)`            | 收集为 Map         | `Map`                   |
| `groupingBy(classifier)` | 按某字段分组       | `Map<K, List<V>>`       |
| `partitioningBy(pred)`   | 按布尔条件二分     | `Map<Boolean, List<V>>` |
| `mapping(f, downstream)` | 分组后再映射元素   | 依下游而定              |
| `joining(delimiter)`     | 拼接字符串         | `String`                |
| `toCollection(supplier)` | 收集为指定集合类型 | 依 supplier 而定        |
| `counting()`             | 分组后计数         | `Long`                  |

#### 基础示例

```java
import java.util.stream.Collectors;

// 基础数据
List<Product> products = getProducts();
List<User> users = List.of(
    new User(1L, "Alice",   "开发", 28),
    new User(2L, "Bob",     "开发", 30),
    new User(3L, "Charlie", "测试", 25),
    new User(4L, "Diana",   "测试", 32),
    new User(5L, "Eve",     "产品", 22)
);

// toList（最常用）
List<String> names = users.stream().map(User::getName).collect(Collectors.toList());
// names = [Alice, Bob, Charlie, Diana, Eve]

// JDK 16+ 可简写：users.stream().map(User::getName).toList()（返回不可变 List）

// toSet（自动去重）
Set<String> depts = users.stream().map(User::getDept).collect(Collectors.toSet());
// depts = [开发, 测试, 产品]

// toMap：id → User
Map<Long, User> byId = users.stream()
    .collect(Collectors.toMap(User::getId, u -> u));
// byId = {1=User(Alice), 2=User(Bob), ...}

// groupingBy：按部门分组
Map<String, List<User>> byDept = users.stream()
    .collect(Collectors.groupingBy(User::getDept));
// byDept = {开发=[Alice, Bob], 测试=[Charlie, Diana], 产品=[Eve]}

// groupingBy + counting：分组计数
Map<String, Long> counts = products.stream()
    .collect(Collectors.groupingBy(Product::getCategory, Collectors.counting()));
// counts = {"手机"=3, "电脑"=2, "配件"=1}

// partitioningBy：按条件二分
Map<Boolean, List<User>> split = users.stream()
    .collect(Collectors.partitioningBy(u -> u.getAge() >= 30));
// split = {true=[Bob, Diana], false=[Alice, Charlie, Eve]}

// mapping：分组后抽取字段
Map<String, List<String>> namesByDept = users.stream()
    .collect(Collectors.groupingBy(User::getDept,
             Collectors.mapping(User::getName, Collectors.toList())));
// namesByDept = {开发=[Alice, Bob], 测试=[Charlie, Diana], 产品=[Eve]}

// joining：拼接字符串
String csv = users.stream().map(User::getName).collect(Collectors.joining(", "));
// csv = "Alice, Bob, Charlie, Diana, Eve"

// joining 带前缀和后缀
String formatted = users.stream().map(User::getName)
    .collect(Collectors.joining(", ", "[", "]"));
// formatted = "[Alice, Bob, Charlie, Diana, Eve]"

// toCollection：指定集合类型（去重且保序）
Set<String> uniqueDepts = users.stream().map(User::getDept)
    .collect(Collectors.toCollection(LinkedHashSet::new));
// uniqueDepts = [开发, 测试, 产品]（保持首次出现顺序）
```

### forEach - 遍历

```java
// forEach（无法提前终止，不保证顺序）
products.stream()
    .filter(p -> p.getStock() > 0)
    .forEach(p -> System.out.println(p.getName()));

// forEachOrdered（保证顺序，并行流也按遭遇顺序处理）
products.parallelStream()
    .forEachOrdered(System.out::println);
```

### reduce - 归约

```java
List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5);

// 求和
Integer sum = numbers.stream()
    .reduce(0, Integer::sum);
// sum → 15

// 求最大值
Optional<Integer> max = numbers.stream()
    .reduce(Integer::max);
// max → Optional[5]

// 求商品总价（BigDecimal）
BigDecimal totalPrice = products.stream()
    .map(Product::getPrice)
    .reduce(BigDecimal.ZERO, BigDecimal::add);
// totalPrice → 所有商品价格之和
```

### count / min / max - 统计

```java
long count = products.stream()
    .filter(p -> p.getStock() > 0)
    .count();
// count → 有库存的商品数量

Optional<Product> cheapest = products.stream()
    .min(Comparator.comparing(Product::getPrice));
// cheapest → Optional[最便宜的商品]

Optional<Product> mostExpensive = products.stream()
    .max(Comparator.comparing(Product::getPrice));
// mostExpensive → Optional[最贵的商品]
```

### anyMatch / allMatch / noneMatch - 匹配

```java
boolean hasExpensive = products.stream()
    .anyMatch(p -> p.getPrice().compareTo(new BigDecimal("1000")) > 0);
// hasExpensive → true（只要有一个满足就返回 true，短路）

boolean allInStock = products.stream()
    .allMatch(p -> p.getStock() > 0);
// allInStock → false（只要有一个不满足就返回 false，短路）

boolean noneExpensive = products.stream()
    .noneMatch(p -> p.getPrice().compareTo(new BigDecimal("10000")) > 0);
// noneExpensive → true（全都不满足才返回 true）
```

### findFirst / findAny - 查找

```java
Optional<Product> first = products.stream()
    .filter(p -> p.getName().contains("iPhone"))
    .findFirst();
// first → Optional[第一个名称含 iPhone 的商品]

Optional<Product> any = products.parallelStream()
    .filter(p -> p.getName().contains("iPhone"))
    .findAny();
// any → Optional[任意一个匹配的商品]（并行流中效率更高）
```

---

## 6. Collectors 进阶

> 各 Collector 的基础用法和速查表见第 5 节 [collect - 收集](#collect---收集)。本节覆盖企业开发中最常用的高阶模式和常见陷阱。

### Stream.toList() vs Collectors.toList()：版本与可变性

```java
List<String> source = new ArrayList<>(Arrays.asList("A", "B", "C"));

// Collectors.toList()（Java 8+）
List<String> list1 = source.stream().collect(Collectors.toList());
list1.add("D");  // ✅ 通常可以（实际返回 ArrayList，但规范不保证！）

// Stream.toList()（Java 16+）
List<String> list2 = source.stream().toList();
list2.add("D");  // ❌ UnsupportedOperationException（不可修改）

// 需要明确可变结果时
List<String> list3 = source.stream()
    .collect(Collectors.toCollection(ArrayList::new));
list3.add("D");  // ✅ 保证可变
```

| 方式                                      | 最低版本 | 可变性                   | 是否保证实现类型  |
| ----------------------------------------- | -------- | ------------------------ | ----------------- |
| `Collectors.toList()`                     | Java 8   | 不保证（实践中通常可变） | ❌ 不保证         |
| `Stream.toList()`                         | Java 16  | **不可变**               | ❌ 不保证         |
| `Collectors.toCollection(ArrayList::new)` | Java 8   | **可变**                 | ✅ 保证 ArrayList |

> ⚠️ **项目兼容提示**：如果项目同时存在 Java 8 和 Java 16+ 模块，不要混用。需要可变 List 时统一用 `Collectors.toCollection(ArrayList::new)`。

### groupingBy 保序分组

默认 `groupingBy` 返回 `HashMap`，**不保证分组顺序**。需要保持遭遇顺序时用 `LinkedHashMap`：

```java
// 默认：HashMap，分组顺序不确定
Map<String, List<Product>> unordered = products.stream()
    .collect(Collectors.groupingBy(Product::getCategory));
// unordered → {"电脑"=[...], "手机"=[...], "配件"=[...]}（顺序随机）

// 保序：LinkedHashMap，按首次出现的 key 顺序
Map<String, List<Product>> ordered = products.stream()
    .collect(Collectors.groupingBy(
        Product::getCategory,
        LinkedHashMap::new,        // 指定 Map 类型
        Collectors.toList()        // 下游收集器
    ));
// ordered → {"手机"=[iPhone, Xiaomi], "电脑"=[MacBook], "配件"=[AirPods]}
//            按商品列表中各分类首次出现的顺序排列
```

### toMap：重复 key 处理（⚠️ 高频异常点）

```java
// ❌ 危险：如果有重复 key，直接抛 IllegalStateException
Map<String, Product> dangerMap = products.stream()
    .collect(Collectors.toMap(Product::getName, p -> p));
// 假设有两个商品都叫 "iPhone 15" → IllegalStateException: Duplicate key iPhone 15

// ✅ 安全：提供 mergeFunction 处理冲突
Map<String, Product> safeMap = products.stream()
    .collect(Collectors.toMap(
        Product::getName,           // keyMapper
        p -> p,                     // valueMapper
        (existing, replacement) -> replacement  // mergeFunction：保留后者
    ));
// safeMap → {"iPhone 15"=后出现的商品, "MacBook Pro"=...}

// ✅ 保留前者
Map<String, Product> keepFirst = products.stream()
    .collect(Collectors.toMap(
        Product::getName,
        p -> p,
        (existing, replacement) -> existing  // 保留前者
    ));

// ✅ 合并逻辑（如：库存累加）
Map<String, Integer> stockByName = products.stream()
    .collect(Collectors.toMap(
        Product::getName,
        Product::getStock,
        Integer::sum  // 重复 key 时库存相加
    ));
// stockByName → {"iPhone 15"=150, "MacBook Pro"=80}

// ✅ 四参数版本：指定 Map 类型（保序）
Map<String, Product> orderedMap = products.stream()
    .collect(Collectors.toMap(
        Product::getName,
        p -> p,
        (existing, replacement) -> existing,
        LinkedHashMap::new  // 保持插入顺序
    ));
```

> ⚠️ **铁律**：使用 `Collectors.toMap` 时，**必须**考虑重复 key 的情况。不提供 mergeFunction 就是埋雷。

> ⚠️ **坑 2：value 为 null 抛 NullPointerException**——`toMap` 不接受 null value。若 value 映射函数可能返回 null，先 `filter` 掉，或改用 `groupingBy` / 手动 `put`：
>
> ```java
> // ❌ 若 User::getManager 可能返回 null → NullPointerException
> Map<String, String> managerByDept = users.stream()
>     .collect(Collectors.toMap(User::getDept, User::getManager));
>
> // ✅ 先过滤掉 null value
> Map<String, String> managerByDept = users.stream()
>     .filter(u -> u.getManager() != null)
>     .collect(Collectors.toMap(User::getDept, User::getManager, (a, b) -> a));
> ```

### collectingAndThen：收集后二次转换

```java
// 收集后包装为不可变 List
List<String> immutable = products.stream()
    .map(Product::getName)
    .collect(Collectors.collectingAndThen(
        Collectors.toList(),
        Collections::unmodifiableList
    ));
// immutable → 不可修改的 List，调用 add/remove 抛 UnsupportedOperationException

// 收集后取 size
int count = products.stream()
    .filter(p -> p.getStock() > 0)
    .collect(Collectors.collectingAndThen(Collectors.toList(), List::size));
// count → 有库存的商品数量

// 收集后排序
List<Product> sorted = products.stream()
    .filter(p -> p.getStock() > 0)
    .collect(Collectors.collectingAndThen(
        Collectors.toList(),
        list -> { list.sort(Comparator.comparing(Product::getPrice)); return list; }
    ));
// sorted → 有库存商品按价格升序
```

### 嵌套 groupingBy：多级业务维度统计

```java
// 按分类 → 再按库存状态分组
Map<String, Map<Boolean, List<Product>>> twoLevel = products.stream()
    .collect(Collectors.groupingBy(
        Product::getCategory,                             // 第一级：分类
        Collectors.partitioningBy(p -> p.getStock() > 0)   // 第二级：有无库存
    ));
// twoLevel → {
//   "手机"={true=[iPhone, Xiaomi], false=[Huawei]},
//   "电脑"={true=[MacBook], false=[]},
//   "配件"={true=[AirPods], false=[Keyboard]}
// }

// 按分类 → 再按价格区间分组计数
Map<String, Map<String, Long>> priceDistribution = products.stream()
    .collect(Collectors.groupingBy(
        Product::getCategory,
        Collectors.groupingBy(
            p -> p.getPrice().compareTo(new BigDecimal("500")) > 0 ? "高价" : "低价",
            Collectors.counting()
        )
    ));
// priceDistribution → {"手机"={"高价"=1, "低价"=2}, "电脑"={"高价"=1, "低价"=0}}
```

---

## 7. 数值流与统计

### 为什么需要数值流？

`Stream<Integer>` 每个元素都是装箱对象（`Integer`），做数学运算需要反复拆箱。**数值流**（`IntStream`、`LongStream`、`DoubleStream`）直接操作基本类型，避免装箱开销，并提供丰富的统计方法。

### mapToInt / mapToLong / mapToDouble

```java
List<Product> products = getProducts();

// 提取库存并求和
int totalStock = products.stream()
    .mapToInt(Product::getStock)
    .sum();
// totalStock → 所有商品库存之和

// 提取评分并求平均
double avgRating = products.stream()
    .mapToDouble(Product::getRating)
    .average()
    .orElse(0.0);
// avgRating → 平均评分（如 4.3），无元素时返回 0.0

// 提取 ID（Long 类型）
List<Long> ids = products.stream()
    .mapToLong(Product::getId)
    .boxed()              // LongStream → Stream<Long>（需要装箱才能 collect）
    .collect(Collectors.toList());
// ids → [1, 2, 3, 4, 5]
```

### sum / average / max / min / summaryStatistics

```java
IntStream scores = IntStream.of(85, 92, 78, 95, 88);

scores.sum();              // → 438
scores.average();          // → OptionalDouble[87.6]
scores.max();              // → OptionalInt[95]
scores.min();              // → OptionalInt[78]
scores.count();            // → 5

// 一次获取所有统计值
IntSummaryStatistics stats = products.stream()
    .mapToInt(Product::getStock)
    .summaryStatistics();
// stats.getCount()   → 商品种类数
// stats.getSum()     → 总库存
// stats.getAverage() → 平均库存
// stats.getMax()     → 最大单品库存
// stats.getMin()     → 最小单品库存
```

### ⚠️ 金额场景：绝对不要用 double

```java

List<BigDecimal> prices = Arrays.asList(
    new BigDecimal("0.1"),
    new BigDecimal("0.2"),
    new BigDecimal("0.3")
);

// ❌ 危险：double 精度丢失
double wrongTotal = prices.stream()
    .mapToDouble(BigDecimal::doubleValue)
    .sum();
// wrongTotal → 0.6000000000000001（不是 0.6！）

// ✅ 正确：BigDecimal + reduce
BigDecimal correctTotal = prices.stream()
    .reduce(BigDecimal.ZERO, BigDecimal::add);
// correctTotal → 0.6（精确）

// ✅ 带业务语义的写法
BigDecimal orderAmount = orderItems.stream()
    .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
    .reduce(BigDecimal.ZERO, BigDecimal::add);
// orderAmount → 订单总金额（单价 × 数量 的累加）
```

> 💡 **规则**：涉及金额、费率、精确计算时，始终使用 `BigDecimal` + `reduce`，不要用 `mapToDouble`。数值流适合**统计类**场景（计数、评分、库存数等对精度不敏感的整数/浮点数）。

---

## 8. 实战案例

### 案例1：商品列表转 VO

```java
List<Product> products = productService.list();

List<ProductVO> vos = products.stream()
    .map(p -> {
        ProductVO vo = new ProductVO();
        BeanUtils.copyProperties(p, vo);
        vo.setPriceStr(p.getPrice().toString());
        return vo;
    })
    .collect(Collectors.toList());
// vos → 与 products 等长的 VO 列表，每个 VO 包含 priceStr 字段
```

### 案例2：分类统计（分组 + 聚合）

```java
List<Product> products = productService.list();

// 各分类的商品数量
Map<String, Long> categoryCount = products.stream()
    .collect(Collectors.groupingBy(Product::getCategory, Collectors.counting()));
// categoryCount → {"手机"=5, "电脑"=3, "配件"=8}

// 各分类的总价
Map<String, BigDecimal> categoryTotal = products.stream()
    .collect(Collectors.groupingBy(
        Product::getCategory,
        Collectors.reducing(BigDecimal.ZERO, Product::getPrice, BigDecimal::add)
    ));
// categoryTotal → {"手机"=25999.00, "电脑"=45999.00, "配件"=2999.00}
```

### 案例3：复杂过滤与排序（分页查询）

```java
// 需求：筛选在售、有库存、价格100-1000的商品，按价格降序，取前10
List<Product> result = products.stream()
    .filter(p -> p.getPublishStatus() == 1)
    .filter(p -> p.getStock() > 0)
    .filter(p -> {
        BigDecimal price = p.getPrice();
        return price.compareTo(new BigDecimal("100")) >= 0
            && price.compareTo(new BigDecimal("1000")) <= 0;
    })
    .sorted(Comparator.comparing(Product::getPrice).reversed())
    .limit(10)
    .collect(Collectors.toList());
// result → 最多 10 个商品，按价格从高到低
```

### 案例4：多级分组 + mapping 组合

```java
// 需求：按分类分组，每组只保留商品名称列表，且分类按首次出现顺序排列
Map<String, List<String>> catalog = products.stream()
    .collect(Collectors.groupingBy(
        Product::getCategory,
        LinkedHashMap::new,
        Collectors.mapping(Product::getName, Collectors.toList())
    ));
// catalog → {"手机"=["iPhone 15", "Xiaomi 14", "Huawei P60"], "电脑"=["MacBook Pro"], "配件"=["AirPods", "Keyboard"]}
```

### 案例5：flatMap 拆分 + 去重 + 保序

```java
// 需求：从所有订单的标签字段（逗号分隔）中提取不重复标签，保持首次出现顺序
List<Order> orders = orderService.list();

List<String> allTags = orders.stream()
    .map(Order::getTags)                                // "VIP,急单" / "普通" / null
    .filter(StringUtils::isNotBlank)                    // 过滤 null 和空白
    .flatMap(tags -> Arrays.stream(tags.split(",")))    // 拆分为独立标签
    .map(String::trim)                                  // 去除空格
    .filter(StringUtils::isNotBlank)                    // 过滤空字符串
    .distinct()                                         // 去重（保持遭遇顺序）
    .collect(Collectors.toList());
// allTags → [VIP, 急单, 普通, 退款]
```

---

## 9. 何时不用 Stream

Stream 不是万能的。以下场景中，**普通循环通常更清晰、更安全**。

### 不要强行 Stream 的场景

| 场景                  | 为什么不适合 Stream         | 用什么替代                 |
| --------------------- | --------------------------- | -------------------------- |
| 构建树形结构          | 需要多轮遍历 + 修改外部 Map | 普通 for + computeIfAbsent |
| 更新多个外部容器      | Stream 鼓励无副作用         | 普通 for 循环              |
| 需要 break / continue | forEach 无法提前终止        | 普通 for 或 findFirst 短路 |
| 复杂异常恢复          | 受检异常 + 错误收集         | 普通 for + try-catch       |
| 需要索引              | Stream 没有内置索引概念     | IntStream.range 或普通 for |
| 调试复杂链路          | 断点不好打                  | 普通 for 或 peek 临时辅助  |

### 副作用反模式

```java
// ❌ 在 map 中修改外部状态
List<String> errors = new ArrayList<>();
List<Order> validOrders = orders.stream()
    .map(o -> {
        if (o.getAmount() == null) {
            errors.add("Order " + o.getId() + " amount is null");  // 副作用！
            return null;
        }
        return o;
    })
    .filter(Objects::nonNull)
    .collect(Collectors.toList());

// ✅ 分离关注点：先验证，再处理
List<Order> invalidOrders = orders.stream()
    .filter(o -> o.getAmount() == null)
    .collect(Collectors.toList());
// invalidOrders → 所有 amount 为 null 的订单

invalidOrders.forEach(o ->
    log.error("Order {} amount is null", o.getId()));

List<Order> validOrders = orders.stream()
    .filter(o -> o.getAmount() != null)
    .collect(Collectors.toList());
// validOrders → 所有 amount 非 null 的订单
```

### peek 的正确用法

```java
// ✅ 临时调试（上线前删除）
List<Product> result = products.stream()
    .filter(p -> p.getStock() > 0)
    .peek(p -> log.debug("通过过滤: {}", p.getName()))  // 仅调试
    .collect(Collectors.toList());

// ❌ 用 peek 承载业务逻辑
List<Product> result = products.stream()
    .peek(p -> p.setUpdateTime(LocalDateTime.now()))  // 业务逻辑不应在 peek 中！
    .peek(p -> auditService.log(p))                  // 外部调用不应在 peek 中！
    .collect(Collectors.toList());
```

### 决策流程图

```
需要处理集合数据？
    │
    ├─ 只需遍历输出？ ────────────────▶ forEach 或增强 for
    │
    ├─ 需要 filter + map + collect？ ──▶ Stream（最佳场景）
    │
    ├─ 需要 break / 修改多个容器？ ────▶ 普通 for 循环
    │
    ├─ 需要精确错误定位？ ─────────────▶ 普通 for + try-catch
    │
    └─ 需要构建复杂数据结构（树）？ ──▶ 普通 for + Map
```

---

## 10. parallelStream 的真相

> ⚠️ 本节替换旧版“数据量大就考虑并行流”的建议。那个建议**危险且不负责任**。

### ForkJoinPool.commonPool() 的共享本质

```java
// parallelStream() 默认使用 ForkJoinPool.commonPool()
// 这个池是 JVM 全局共享的！所有 parallelStream、CompletableFuture（无显式执行器）都用它

products.parallelStream()
    .map(p -> enrichFromRemoteService(p))  // 假设这是 HTTP 调用
    .collect(Collectors.toList());
// ❌ 灾难：HTTP 阻塞占满 commonPool 线程，其他所有并行流和异步任务全部饥饿
```

> 📖 ForkJoinPool 与线程池的关系详见 → [multithreading-basics.md](./multithreading-basics.md) §5

### 四类禁忌场景

| 禁忌                           | 原因                                            | 后果                              |
| ------------------------------ | ----------------------------------------------- | --------------------------------- |
| 阻塞 I/O（HTTP/DB/Redis/文件） | commonPool 线程数 ≈ CPU核数-1，阻塞会耗尽       | 整个应用的并行任务饥饿            |
| ThreadLocal / MDC 依赖         | ForkJoinPool 工作线程不继承调用者的 ThreadLocal | 日志 traceId 丢失、安全上下文丢失 |
| Reactor Context 传播           | 响应式上下文绑定在订阅线程上                    | 上下文传播断裂                    |
| 项目已有自定义线程池           | commonPool 与业务线程池资源竞争                 | 不可预测的延迟抖动                |

### 正确决策路径

```
想要 parallelStream？先回答这些问题：
    │
    ├─ 操作是 CPU 密集型吗？（纯计算、排序、转换）
    │   ├─ 否 → 不要用 parallelStream
    │   └─ 是 ↓
    │
    ├─ 数据量足够大吗？（拆分/合并开销 vs 计算开销）
    │   ├─ 否 → 不要用 parallelStream
    │   └─ 是 ↓
    │
    ├─ 有共享可变状态吗？
    │   ├─ 是 → 不要用 parallelStream
    │   └─ 否 ↓
    │
    ├─ 依赖 ThreadLocal / MDC / 安全上下文吗？
    │   ├─ 是 → 不要用 parallelStream
    │   └─ 否 ↓
    │
    └─ 做过压测验证吗？
        ├─ 否 → 先去压测
        └─ 是，确实更快 → 可以使用
```

> 💡 **没有魔法数字**：“超过 1000 条就并行”是错误的。是否并行取决于**操作性质**（CPU密集 vs I/O密集）和**实测数据**，而非数据条数。

### 如果确实需要并行

```java
// 方式 1：自定义 ForkJoinPool（隔离公共池）
ForkJoinPool customPool = new ForkJoinPool(4);  // 并行度 4
try {
    List<Product> result = customPool.submit(() ->
        products.parallelStream()
            .filter(p -> p.getStock() > 0)
            .sorted(Comparator.comparing(Product::getPrice))
            .collect(Collectors.toList())
    ).get();
} finally {
    customPool.shutdown();
}

// 方式 2：CompletableFuture + 显式执行器（推荐，更灵活）
Executor ioExecutor = getIoTaskExecutor();  // 项目自定义线程池
List<CompletableFuture<Product>> futures = products.stream()
    .map(p -> CompletableFuture.supplyAsync(() -> enrich(p), ioExecutor))
    .collect(Collectors.toList());

List<Product> enriched = futures.stream()
    .map(CompletableFuture::join)
    .collect(Collectors.toList());
// enriched → 所有商品经过远程服务增强后的结果
```

> 📖 CompletableFuture 编排详见 → [multithreading-basics.md](./multithreading-basics.md) §7

---

## 下一步

- **[Lambda.md](./Lambda.md)** - Lambda 基础语法与方法引用
- **[Optional.md](./Optional.md)** - Optional 最佳实践
- **[collections-framework.md](./collections-framework.md)** - 集合框架与选型
- **[multithreading-basics.md](./multithreading-basics.md)** - 多线程与线程池

---

## 快速参考

```java
// ═══════════════════ 遍历方式选择 ═══════════════════
for (int i = 0; i < list.size(); i++)   // 需要索引 / break
for (Item item : list)                   // 简单遍历
list.forEach(item -> process(item));     // 函数式风格

// ═══════════════════ Stream 管道 ═══════════════════
list.stream()
    .filter(p -> p.getPrice() > 100)     // 过滤
    .map(Product::getName)               // 转换
    .flatMap(s -> Arrays.stream(s.split(",")))  // 扁平化
    .distinct()                          // 去重
    .sorted(Comparator.comparing(...))   // 排序
    .limit(10)                           // 限制
    .collect(Collectors.toList());       // 收集

// ═══════════════════ Collectors 速查 ═══════════════════
Collectors.toList()                      // → List（不保证可变性）
Collectors.toCollection(ArrayList::new)  // → 可变 ArrayList
Collectors.toSet()                       // → Set（无序去重）
Collectors.toMap(key, val, merge)         // → Map（必须处理重复 key！）
Collectors.groupingBy(key)               // → Map<K, List<V>>
Collectors.groupingBy(key, LinkedHashMap::new, downstream)  // 保序分组
Collectors.mapping(fn, downstream)       // 分组后转换
Collectors.partitioningBy(predicate)     // → Map<Boolean, List<V>>
Collectors.joining(", ")                 // → String
Collectors.counting()                    // → Long
Collectors.collectingAndThen(dl, fn)     // 收集后二次处理

// ═══════════════════ 数值流 ═══════════════════
.mapToInt(Product::getStock).sum()       // 整数求和
.mapToDouble(Product::getRating).average()  // 浮点平均
.map(Product::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add)  // 金额（BigDecimal）

// ═══════════════════ toList 选择 ═══════════════════
stream.toList()                          // Java 16+，不可变
stream.collect(Collectors.toList())      // Java 8+，不保证可变性
stream.collect(Collectors.toCollection(ArrayList::new))  // 明确可变

// ═══════════════════ 反模式速查 ═══════════════════
// ❌ peek 中做业务逻辑
// ❌ map/filter 中修改外部状态
// ❌ toMap 不提供 mergeFunction
// ❌ 金额用 mapToDouble
// ❌ parallelStream 做 I/O
// ❌ 增强 for 中 list.remove()
```
