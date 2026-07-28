# 01-04 Lambda与Stream API

> 掌握Java函数式编程，用Stream优雅处理集合操作

## 1. Lambda 表达式基础

### JavaScript 箭头函数对比

```javascript
// JavaScript 箭头函数
const add = (a, b) => a + b;
const greet = name => `Hello ${name}`;
const log = () => console.log("Hi");

// 数组方法
products.filter(p => p.price > 100);
products.map(p => p.name);
products.forEach(p => console.log(p));
```

### Java Lambda 表达式

```java
// Lambda 语法：(参数) -> 表达式/代码块

// 1. 无参数
Runnable task = () -> System.out.println("Hi");

// 2. 单个参数（可省略括号）
Consumer<String> greet = name -> System.out.println("Hello " + name);
Consumer<String> greet2 = (name) -> System.out.println("Hello " + name);

// 3. 多个参数
Comparator<Integer> comparator = (a, b) -> a - b;

// 4. 多行代码块
Comparator<String> comparator2 = (s1, s2) -> {
    System.out.println("Comparing...");
    return s1.length() - s2.length();
};

// 使用
task.run();
greet.accept("John");
```

### 函数式接口

Lambda 只能用于**函数式接口**（只有一个抽象方法的接口）：

```java
// 自定义函数式接口
@FunctionalInterface
public interface Calculator {
    int calculate(int a, int b);
}

// 使用 Lambda
Calculator add = (a, b) -> a + b;
Calculator multiply = (a, b) -> a * b;

System.out.println(add.calculate(2, 3));       // 5
System.out.println(multiply.calculate(2, 3));  // 6
```

### 常用内置函数式接口

| 接口 | 方法 | 说明 | 示例 |
|------|------|------|------|
| `Function<T, R>` | `R apply(T t)` | 输入T，返回R | `s -> s.length()` |
| `Predicate<T>` | `boolean test(T t)` | 输入T，返回布尔 | `n -> n > 0` |
| `Consumer<T>` | `void accept(T t)` | 输入T，无返回 | `s -> print(s)` |
| `Supplier<T>` | `T get()` | 无输入，返回T | `() -> new Object()` |
| `BiFunction<T, U, R>` | `R apply(T t, U u)` | 输入T和U，返回R | `(a, b) -> a + b` |

```java
// Function：转换
Function<String, Integer> toLength = s -> s.length();
Integer len = toLength.apply("Hello");  // 5

// Predicate：判断
Predicate<Integer> isPositive = n -> n > 0;
boolean result = isPositive.test(10);  // true

// Consumer：消费
Consumer<String> printer = s -> System.out.println(s);
printer.accept("Hello");  // 输出 Hello

// Supplier：提供
Supplier<String> supplier = () -> "Default";
String value = supplier.get();  // "Default"
```

---

## 2. 方法引用（::）

方法引用是 Lambda 的简化写法：

### 语法对照

```java
// 1. 静态方法引用：类名::静态方法
Function<String, Integer> parser1 = s -> Integer.parseInt(s);
Function<String, Integer> parser2 = Integer::parseInt;  // 简化

// 2. 实例方法引用（绑定对象）：对象::实例方法
Product product = new Product();
Supplier<String> getName1 = () -> product.getName();
Supplier<String> getName2 = product::getName;  // 简化

// 3. 实例方法引用（未绑定）：类名::实例方法
Function<String, Integer> getLength1 = s -> s.length();
Function<String, Integer> getLength2 = String::length;  // 简化

// 4. 构造方法引用：类名::new
Supplier<Product> creator1 = () -> new Product();
Supplier<Product> creator2 = Product::new;  // 简化

Function<String, Product> creator3 = name -> new Product(name);
Function<String, Product> creator4 = Product::new;  // 简化
```

### 实际应用

```java
List<String> names = Arrays.asList("Alice", "Bob", "Charlie");

// Lambda 写法
names.forEach(name -> System.out.println(name));

// 方法引用（更简洁）
names.forEach(System.out::println);

// 转换
List<Integer> lengths = names.stream()
    .map(name -> name.length())  // Lambda
    .collect(Collectors.toList());

List<Integer> lengths2 = names.stream()
    .map(String::length)  // 方法引用
    .collect(Collectors.toList());
```

---

## 3. Stream API 基础

### 创建 Stream

```java
// 1. 从集合创建
List<String> list = Arrays.asList("A", "B", "C");
Stream<String> stream1 = list.stream();

// 2. 从数组创建
String[] arr = {"A", "B", "C"};
Stream<String> stream2 = Arrays.stream(arr);
Stream<String> stream3 = Stream.of("A", "B", "C");

// 3. 生成 Stream
Stream<Integer> stream4 = Stream.generate(() -> 1).limit(10);  // 10个1
Stream<Integer> stream5 = Stream.iterate(0, n -> n + 2).limit(5);  // 0,2,4,6,8
```

### Stream 特点

```java
// ⚠️ Stream 只能使用一次
Stream<String> stream = list.stream();
stream.forEach(System.out::println);  // ✅
stream.forEach(System.out::println);  // ❌ IllegalStateException

// ⚠️ Stream 不修改原集合
List<String> list = new ArrayList<>(Arrays.asList("a", "b", "c"));
list.stream()
    .map(String::toUpperCase)
    .forEach(System.out::println);  // 输出 A B C
System.out.println(list);  // [a, b, c]（原集合不变）
```

---

## 4. Stream 中间操作

中间操作返回新的 Stream，可以链式调用。

### filter - 过滤

```java
// 筛选价格大于100的商品
List<Product> products = getProducts();

List<Product> filtered = products.stream()
    .filter(p -> p.getPrice().compareTo(new BigDecimal("100")) > 0)
    .collect(Collectors.toList());

// 多条件过滤
List<Product> result = products.stream()
    .filter(p -> p.getPrice().compareTo(new BigDecimal("100")) > 0)
    .filter(p -> p.getStock() > 0)
    .filter(p -> p.getName().contains("iPhone"))
    .collect(Collectors.toList());
```

### map - 转换

```java
// 提取所有商品名称
List<String> names = products.stream()
    .map(Product::getName)
    .collect(Collectors.toList());

// 提取并转大写
List<String> upperNames = products.stream()
    .map(Product::getName)
    .map(String::toUpperCase)
    .collect(Collectors.toList());

// 复杂转换
List<ProductVO> vos = products.stream()
    .map(p -> {
        ProductVO vo = new ProductVO();
        vo.setId(p.getId());
        vo.setName(p.getName());
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

// map 返回 Stream<List<String>>
// flatMap 返回 Stream<String>
List<String> flattened = lists.stream()
    .flatMap(list -> list.stream())
    .collect(Collectors.toList());  // [A, B, C, D, E]

// 实际场景：订单 → 订单项
List<Order> orders = getOrders();
List<OrderItem> allItems = orders.stream()
    .flatMap(order -> order.getItems().stream())
    .collect(Collectors.toList());
```

### distinct - 去重

```java
List<Integer> numbers = Arrays.asList(1, 2, 2, 3, 3, 3);
List<Integer> unique = numbers.stream()
    .distinct()
    .collect(Collectors.toList());  // [1, 2, 3]

// 根据属性去重（需要重写 equals/hashCode）
List<Product> uniqueProducts = products.stream()
    .distinct()  // 根据 Product 的 equals 方法
    .collect(Collectors.toList());
```

### sorted - 排序

```java
List<Integer> numbers = Arrays.asList(3, 1, 2);

// 自然排序
List<Integer> sorted = numbers.stream()
    .sorted()
    .collect(Collectors.toList());  // [1, 2, 3]

// 自定义排序
List<Product> sortedProducts = products.stream()
    .sorted((p1, p2) -> p1.getPrice().compareTo(p2.getPrice()))  // 价格升序
    .collect(Collectors.toList());

// 使用 Comparator（推荐）
List<Product> sorted2 = products.stream()
    .sorted(Comparator.comparing(Product::getPrice))  // 价格升序
    .collect(Collectors.toList());

List<Product> sorted3 = products.stream()
    .sorted(Comparator.comparing(Product::getPrice).reversed())  // 价格降序
    .collect(Collectors.toList());

// 多字段排序
List<Product> sorted4 = products.stream()
    .sorted(Comparator.comparing(Product::getCategory)
            .thenComparing(Product::getPrice))
    .collect(Collectors.toList());
```

### limit / skip - 限制

```java
// 前3个
List<Product> top3 = products.stream()
    .limit(3)
    .collect(Collectors.toList());

// 跳过前2个
List<Product> after2 = products.stream()
    .skip(2)
    .collect(Collectors.toList());

// 分页：第2页，每页10条
int pageNum = 2;
int pageSize = 10;
List<Product> page = products.stream()
    .skip((pageNum - 1) * pageSize)
    .limit(pageSize)
    .collect(Collectors.toList());
```

### peek - 调试

```java
// peek 不修改元素，只用于调试
List<String> result = list.stream()
    .filter(s -> s.startsWith("A"))
    .peek(s -> System.out.println("After filter: " + s))
    .map(String::toUpperCase)
    .peek(s -> System.out.println("After map: " + s))
    .collect(Collectors.toList());
```

---

## 5. Stream 终止操作

终止操作触发流的计算，返回最终结果。

### collect - 收集

```java
// 转 List
List<String> list = stream.collect(Collectors.toList());

// 转 Set
Set<String> set = stream.collect(Collectors.toSet());

// 转 Map
Map<Long, Product> map = products.stream()
    .collect(Collectors.toMap(Product::getId, p -> p));

// 转 Map（处理key冲突）
Map<Long, Product> map2 = products.stream()
    .collect(Collectors.toMap(
        Product::getId,
        p -> p,
        (p1, p2) -> p1  // key冲突时保留第一个
    ));

// 分组
Map<String, List<Product>> grouped = products.stream()
    .collect(Collectors.groupingBy(Product::getCategory));

// 分组计数
Map<String, Long> counts = products.stream()
    .collect(Collectors.groupingBy(
        Product::getCategory,
        Collectors.counting()
    ));

// 分区（true/false）
Map<Boolean, List<Product>> partitioned = products.stream()
    .collect(Collectors.partitioningBy(
        p -> p.getPrice().compareTo(new BigDecimal("100")) > 0
    ));

// 拼接字符串
String joined = names.stream()
    .collect(Collectors.joining(", "));  // "A, B, C"

String joined2 = names.stream()
    .collect(Collectors.joining(", ", "[", "]"));  // "[A, B, C]"
```

### forEach - 遍历

```java
// forEach（无法提前终止）
products.stream()
    .filter(p -> p.getStock() > 0)
    .forEach(p -> System.out.println(p.getName()));

// forEachOrdered（保证顺序，并行流也按顺序）
products.parallelStream()
    .forEachOrdered(System.out::println);
```

### reduce - 归约

```java
// 求和
List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5);
Integer sum = numbers.stream()
    .reduce(0, (a, b) -> a + b);  // 15

Integer sum2 = numbers.stream()
    .reduce(0, Integer::sum);  // 15

// 求最大值
Optional<Integer> max = numbers.stream()
    .reduce((a, b) -> a > b ? a : b);

Optional<Integer> max2 = numbers.stream()
    .reduce(Integer::max);

// 求商品总价
BigDecimal totalPrice = products.stream()
    .map(Product::getPrice)
    .reduce(BigDecimal.ZERO, BigDecimal::add);
```

### count / min / max - 统计

```java
// 统计数量
long count = products.stream()
    .filter(p -> p.getStock() > 0)
    .count();

// 最小值
Optional<Product> cheapest = products.stream()
    .min(Comparator.comparing(Product::getPrice));

// 最大值
Optional<Product> mostExpensive = products.stream()
    .max(Comparator.comparing(Product::getPrice));
```

### anyMatch / allMatch / noneMatch - 匹配

```java
// 是否有任意元素满足条件
boolean hasExpensive = products.stream()
    .anyMatch(p -> p.getPrice().compareTo(new BigDecimal("1000")) > 0);

// 是否所有元素满足条件
boolean allInStock = products.stream()
    .allMatch(p -> p.getStock() > 0);

// 是否没有元素满足条件
boolean noneExpensive = products.stream()
    .noneMatch(p -> p.getPrice().compareTo(new BigDecimal("10000")) > 0);
```

### findFirst / findAny - 查找

```java
// 查找第一个
Optional<Product> first = products.stream()
    .filter(p -> p.getName().contains("iPhone"))
    .findFirst();

// 查找任意一个（并行流效率更高）
Optional<Product> any = products.parallelStream()
    .filter(p -> p.getName().contains("iPhone"))
    .findAny();
```

---

## 6. Optional 最佳实践

### 创建 Optional

```java
// 1. of - 值不能为 null
Optional<String> opt1 = Optional.of("Hello");
// Optional<String> opt2 = Optional.of(null);  // NullPointerException

// 2. ofNullable - 值可以为 null
Optional<String> opt3 = Optional.ofNullable("Hello");
Optional<String> opt4 = Optional.ofNullable(null);  // ✅ 空 Optional

// 3. empty - 空 Optional
Optional<String> opt5 = Optional.empty();
```

### 获取值

```java
Optional<Product> productOpt = productService.findById(1L);

// 1. get - 不安全（可能 NoSuchElementException）
// Product p = productOpt.get();  // ❌ 不推荐

// 2. orElse - 提供默认值
Product p2 = productOpt.orElse(new Product());

// 3. orElseGet - 提供默认值（懒加载）
Product p3 = productOpt.orElseGet(() -> new Product());

// 4. orElseThrow - 抛出异常
Product p4 = productOpt.orElseThrow(() -> new ApiException("商品不存在"));

// 5. isPresent + get（不推荐）
if (productOpt.isPresent()) {
    Product p = productOpt.get();
}

// 6. ifPresent - 有值时执行（推荐）
productOpt.ifPresent(p -> System.out.println(p.getName()));

// 7. ifPresentOrElse（Java 9+）
productOpt.ifPresentOrElse(
    p -> System.out.println(p.getName()),
    () -> System.out.println("Not found")
);
```

### 转换与过滤

```java
Optional<Product> productOpt = productService.findById(1L);

// map - 转换
Optional<String> nameOpt = productOpt.map(Product::getName);
String name = nameOpt.orElse("Unknown");

// 链式调用
String name2 = productService.findById(1L)
    .map(Product::getName)
    .map(String::toUpperCase)
    .orElse("UNKNOWN");

// flatMap - 避免 Optional<Optional<T>>
Optional<String> name3 = productOpt
    .flatMap(p -> Optional.ofNullable(p.getName()));

// filter - 过滤
Optional<Product> expensiveProduct = productOpt
    .filter(p -> p.getPrice().compareTo(new BigDecimal("100")) > 0);
```

### 实战场景

```java
// 场景1：Service层返回
public Optional<Product> findById(Long id) {
    Product product = productMapper.selectByPrimaryKey(id);
    return Optional.ofNullable(product);
}

// 场景2：Controller层处理
public CommonResult<Product> getProduct(Long id) {
    return productService.findById(id)
        .map(CommonResult::success)
        .orElse(CommonResult.failed("商品不存在"));
}

// 场景3：避免多层 null 检查
// ❌ 传统写法
String city = null;
if (user != null) {
    Address address = user.getAddress();
    if (address != null) {
        city = address.getCity();
    }
}

// ✅ Optional 写法
String city = Optional.ofNullable(user)
    .map(User::getAddress)
    .map(Address::getCity)
    .orElse("Unknown");
```

---

## 7. 实战案例

### 案例1：商品列表转 VO

```java
// 需求：Product → ProductVO
List<Product> products = productService.list();

List<ProductVO> vos = products.stream()
    .map(p -> {
        ProductVO vo = new ProductVO();
        BeanUtils.copyProperties(p, vo);
        vo.setPriceStr(p.getPrice().toString());
        return vo;
    })
    .collect(Collectors.toList());
```

### 案例2：分类统计

```java
// 需求：统计各分类的商品数量和总价
List<Product> products = productService.list();

Map<String, Long> categoryCount = products.stream()
    .collect(Collectors.groupingBy(
        Product::getCategory,
        Collectors.counting()
    ));

Map<String, BigDecimal> categoryTotal = products.stream()
    .collect(Collectors.groupingBy(
        Product::getCategory,
        Collectors.reducing(
            BigDecimal.ZERO,
            Product::getPrice,
            BigDecimal::add
        )
    ));
```

### 案例3：复杂过滤与排序

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
```

---

## 8. 性能注意事项

```java
// ⚠️ 小数据集（<1000）：Stream 可能比传统循环慢
List<Integer> small = Arrays.asList(1, 2, 3, 4, 5);

// 传统 for 更快
int sum1 = 0;
for (int n : small) {
    sum1 += n;
}

// Stream（代码简洁但稍慢）
int sum2 = small.stream().mapToInt(Integer::intValue).sum();

// ✅ 大数据集（>1000）：考虑并行流
List<Integer> large = getLargeList();
int sum3 = large.parallelStream()
    .mapToInt(Integer::intValue)
    .sum();
```

---

## 下一步

- **[01-05-异常处理机制.md](./01-05-异常处理机制.md)** - 异常处理
- **[01-06-字符串与日期.md](./01-06-字符串与日期.md)** - 字符串与日期

---

## 快速参考

```java
// Lambda 表达式
(a, b) -> a + b
name -> System.out.println(name)
() -> new Product()

// 方法引用
System.out::println
String::length
Product::new

// Stream 常用操作
list.stream()
    .filter(p -> p.getPrice() > 100)     // 过滤
    .map(Product::getName)               // 转换
    .distinct()                          // 去重
    .sorted(Comparator.comparing(...))   // 排序
    .limit(10)                           // 限制
    .collect(Collectors.toList());       // 收集

// Optional 常用操作
Optional.ofNullable(obj)
    .map(User::getName)
    .orElse("Unknown");
```
