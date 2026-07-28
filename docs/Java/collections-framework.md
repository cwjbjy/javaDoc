# 01-03 集合框架详解

> 理解Java集合体系，掌握List、Set、Map的使用与选择

## 1. 集合框架总览

```
Collection (接口)
├── List (有序、可重复)
│   ├── ArrayList (数组实现，查询快)
│   ├── LinkedList (链表实现，增删快)
│   └── Vector (线程安全，已过时)
├── Set (无序、不重复)
│   ├── HashSet (哈希表，无序)
│   ├── LinkedHashSet (哈希表+链表，插入顺序)
│   └── TreeSet (红黑树，自然排序)
└── Queue (队列)
    ├── LinkedList (双端队列)
    ├── PriorityQueue (优先队列)
    └── ArrayDeque (数组双端队列)

Map (接口，键值对)
├── HashMap (哈希表，无序)
├── LinkedHashMap (哈希表+链表，插入顺序)
├── TreeMap (红黑树，key自然排序)
└── Hashtable (线程安全，已过时)
```

---

## 2. List（列表）

### JavaScript/TypeScript 数组对比

```typescript
// JavaScript Array
const products = [product1, product2];
products.push(product3);      // 添加
products[0];                  // 访问
products.length;              // 长度
products.splice(0, 1);        // 删除
```

### Java ArrayList

```java
import java.util.*;

// 创建 List
List<Product> products = new ArrayList<>();

// 添加元素
products.add(product1);                  // 尾部添加
products.add(0, product2);               // 指定位置插入

// 访问元素
Product first = products.get(0);         // 索引访问
int size = products.size();              // 长度

// 删除元素
products.remove(0);                      // 按索引删除
products.remove(product1);               // 按对象删除
products.clear();                        // 清空

// 查询
boolean exists = products.contains(product1);  // 是否包含
int index = products.indexOf(product1);        // 查找索引（-1表示不存在）

// 判空
if (products.isEmpty()) { }
if (products.size() > 0) { }
```

### ArrayList vs LinkedList

| 特性 | ArrayList | LinkedList |
|------|-----------|-----------|
| 底层结构 | 动态数组 | 双向链表 |
| 随机访问 | O(1) ⚡快 | O(n) 慢 |
| 头部插入/删除 | O(n) 慢 | O(1) ⚡快 |
| 尾部插入/删除 | O(1) ⚡快 | O(1) ⚡快 |
| 内存占用 | 连续空间 | 额外指针开销 |
| 使用场景 | 查询多 | 增删多 |

```java
// 推荐：默认用 ArrayList（90% 场景）
List<Product> products = new ArrayList<>();

// 特殊场景：频繁头部操作用 LinkedList
List<Task> taskQueue = new LinkedList<>();
taskQueue.add(0, task);  // 头部插入频繁
```

---

## 3. 遍历 List 的 5 种方式

```java
List<String> list = Arrays.asList("A", "B", "C");

// 1. for 循环（最基础）
for (int i = 0; i < list.size(); i++) {
    System.out.println(list.get(i));
}

// 2. 增强 for 循环（推荐，可读性好）
for (String item : list) {
    System.out.println(item);
}

// 3. Iterator 迭代器（可在遍历时删除）
Iterator<String> iterator = list.iterator();
while (iterator.hasNext()) {
    String item = iterator.next();
    if (item.equals("B")) {
        iterator.remove();  // 安全删除
    }
}

// 4. forEach + Lambda（Java 8+，推荐）
list.forEach(item -> System.out.println(item));
list.forEach(System.out::println);  // 方法引用

// 5. Stream（复杂操作推荐）
list.stream()
    .filter(item -> item.startsWith("A"))
    .forEach(System.out::println);
```

---

## 4. Set（集合）

### 特点：不重复、无序（HashSet）

```java
Set<Long> ids = new HashSet<>();
ids.add(1L);
ids.add(2L);
ids.add(1L);  // 重复，不会添加

System.out.println(ids.size());  // 2
System.out.println(ids.contains(1L));  // true
```

### HashSet vs LinkedHashSet vs TreeSet

| 类型 | 底层结构 | 有序性 | 性能 | 使用场景 |
|------|----------|--------|------|----------|
| `HashSet` | 哈希表 | 无序 | O(1) | 去重、查找 |
| `LinkedHashSet` | 哈希表+链表 | 插入顺序 | O(1) | 需保持插入顺序 |
| `TreeSet` | 红黑树 | 自然排序 | O(log n) | 需排序 |

```java
// HashSet - 无序
Set<Integer> hashSet = new HashSet<>();
hashSet.add(3);
hashSet.add(1);
hashSet.add(2);
System.out.println(hashSet);  // [1, 2, 3] 或其他顺序

// LinkedHashSet - 插入顺序
Set<Integer> linkedSet = new LinkedHashSet<>();
linkedSet.add(3);
linkedSet.add(1);
linkedSet.add(2);
System.out.println(linkedSet);  // [3, 1, 2]

// TreeSet - 自然排序
Set<Integer> treeSet = new TreeSet<>();
treeSet.add(3);
treeSet.add(1);
treeSet.add(2);
System.out.println(treeSet);  // [1, 2, 3]
```

### Set 常见用途

```java
// 1. 去重
List<Long> ids = Arrays.asList(1L, 2L, 2L, 3L);
Set<Long> uniqueIds = new HashSet<>(ids);  // [1, 2, 3]

// 2. 判断是否存在（快速）
Set<String> bannedWords = new HashSet<>(Arrays.asList("spam", "ad"));
if (bannedWords.contains(word)) {
    // 过滤
}

// 3. 集合运算
Set<String> set1 = new HashSet<>(Arrays.asList("A", "B", "C"));
Set<String> set2 = new HashSet<>(Arrays.asList("B", "C", "D"));

// 交集
set1.retainAll(set2);  // [B, C]

// 并集
set1.addAll(set2);     // [A, B, C, D]

// 差集
set1.removeAll(set2);  // [A]
```

---

## 5. Map（映射）

### JavaScript/TypeScript 对象/Map对比

```typescript
// JavaScript Object
const config = {
    pageSize: 10,
    pageNum: 1
};
config.pageSize;  // 访问

// JavaScript Map
const map = new Map<string, number>();
map.set("total", 100);
map.get("total");
```

### Java HashMap

```java
import java.util.*;

// 创建 Map
Map<String, Integer> config = new HashMap<>();

// 添加/更新
config.put("pageSize", 10);
config.put("pageNum", 1);
config.put("pageSize", 20);  // 更新（key相同）

// 访问
Integer pageSize = config.get("pageSize");  // 20
Integer total = config.get("total");        // null（不存在）
Integer total2 = config.getOrDefault("total", 0);  // 0（默认值）

// 判断
boolean exists = config.containsKey("pageSize");     // true
boolean hasValue = config.containsValue(20);         // true

// 删除
config.remove("pageSize");

// 遍历
for (Map.Entry<String, Integer> entry : config.entrySet()) {
    System.out.println(entry.getKey() + ": " + entry.getValue());
}

// Lambda 遍历（推荐）
config.forEach((key, value) -> {
    System.out.println(key + ": " + value);
});
```

### HashMap vs LinkedHashMap vs TreeMap

| 类型 | 底层结构 | 有序性 | 性能 | 使用场景 |
|------|----------|--------|------|----------|
| `HashMap` | 哈希表 | 无序 | O(1) | 默认选择 |
| `LinkedHashMap` | 哈希表+链表 | 插入顺序 | O(1) | 需保持插入顺序 |
| `TreeMap` | 红黑树 | key自然排序 | O(log n) | 需按key排序 |

```java
// HashMap - 无序
Map<String, Integer> hashMap = new HashMap<>();
hashMap.put("c", 3);
hashMap.put("a", 1);
hashMap.put("b", 2);
System.out.println(hashMap);  // {a=1, b=2, c=3} 或其他顺序

// LinkedHashMap - 插入顺序
Map<String, Integer> linkedMap = new LinkedHashMap<>();
linkedMap.put("c", 3);
linkedMap.put("a", 1);
linkedMap.put("b", 2);
System.out.println(linkedMap);  // {c=3, a=1, b=2}

// TreeMap - 按key排序
Map<String, Integer> treeMap = new TreeMap<>();
treeMap.put("c", 3);
treeMap.put("a", 1);
treeMap.put("b", 2);
System.out.println(treeMap);  // {a=1, b=2, c=3}
```

---

## 6. 泛型（Generics）

### 基本语法

```java
// 泛型类
public class Box<T> {
    private T value;
    
    public void set(T value) {
        this.value = value;
    }
    
    public T get() {
        return value;
    }
}

// 使用
Box<String> stringBox = new Box<>();
stringBox.set("Hello");
String value = stringBox.get();  // 不需要强制转换

Box<Integer> intBox = new Box<>();
intBox.set(123);
Integer num = intBox.get();
```

### 泛型方法

```java
// 泛型方法
public <T> T findById(Long id, Class<T> clazz) {
    // ...
    return result;
}

// 使用
Product product = findById(1L, Product.class);
```

### 泛型通配符

```java
// ? 表示任意类型
public void print(List<?> list) {
    for (Object obj : list) {
        System.out.println(obj);
    }
}

// ? extends T 表示 T 或 T 的子类（上界）
public void processProducts(List<? extends Product> products) {
    for (Product p : products) {
        System.out.println(p.getName());
    }
}

// ? super T 表示 T 或 T 的父类（下界）
public void addProducts(List<? super Product> list) {
    list.add(new Product());
}
```

---

## 7. 集合工具类

### Collections 工具类

```java
import java.util.Collections;

List<Integer> list = new ArrayList<>(Arrays.asList(3, 1, 2));

// 排序
Collections.sort(list);  // [1, 2, 3]
Collections.reverse(list);  // [3, 2, 1]

// 查找
int max = Collections.max(list);  // 3
int min = Collections.min(list);  // 1
int index = Collections.binarySearch(list, 2);  // 二分查找

// 填充/替换
Collections.fill(list, 0);  // [0, 0, 0]
Collections.replaceAll(list, 0, 1);  // 替换所有0为1

// 不可变集合
List<String> immutableList = Collections.unmodifiableList(list);
// immutableList.add("A");  // UnsupportedOperationException
```

### Arrays 工具类

```java
import java.util.Arrays;

// 数组 → List
String[] arr = {"A", "B", "C"};
List<String> list = Arrays.asList(arr);

// 排序
int[] nums = {3, 1, 2};
Arrays.sort(nums);  // [1, 2, 3]

// 二分查找（数组必须有序）
int index = Arrays.binarySearch(nums, 2);  // 1

// 填充
Arrays.fill(nums, 0);  // [0, 0, 0]

// 比较
int[] arr1 = {1, 2, 3};
int[] arr2 = {1, 2, 3};
boolean equal = Arrays.equals(arr1, arr2);  // true

// 转字符串
String str = Arrays.toString(arr1);  // "[1, 2, 3]"
```

---

## 8. 集合初始化的多种方式

```java
// 1. 传统方式
List<String> list1 = new ArrayList<>();
list1.add("A");
list1.add("B");

// 2. Arrays.asList（固定长度）
List<String> list2 = Arrays.asList("A", "B", "C");
// list2.add("D");  // UnsupportedOperationException

// 3. 转为可变列表
List<String> list3 = new ArrayList<>(Arrays.asList("A", "B"));
list3.add("C");  // ✅ 可以添加

// 4. Java 9+ List.of（不可变）
List<String> list4 = List.of("A", "B", "C");
// list4.add("D");  // UnsupportedOperationException

// 5. Stream（Java 8+）
List<String> list5 = Stream.of("A", "B", "C")
    .collect(Collectors.toList());

// 6. 双大括号初始化（不推荐，性能差）
List<String> list6 = new ArrayList<String>() {{
    add("A");
    add("B");
}};
```

---

## 9. 性能对比与选择

### List 选择

```java
// ✅ 默认选择 ArrayList（90%场景）
List<Product> products = new ArrayList<>();

// ✅ 频繁头部插入/删除用 LinkedList
List<Task> queue = new LinkedList<>();

// ❌ 不要用 Vector（已过时）
```

### Set 选择

```java
// ✅ 默认选择 HashSet（去重、查找）
Set<Long> ids = new HashSet<>();

// ✅ 需要保持插入顺序用 LinkedHashSet
Set<String> tags = new LinkedHashSet<>();

// ✅ 需要排序用 TreeSet
Set<Integer> sortedNumbers = new TreeSet<>();
```

### Map 选择

```java
// ✅ 默认选择 HashMap
Map<String, Object> config = new HashMap<>();

// ✅ 需要保持插入顺序用 LinkedHashMap
Map<String, String> params = new LinkedHashMap<>();

// ✅ 需要按key排序用 TreeMap
Map<LocalDate, BigDecimal> sales = new TreeMap<>();

// ❌ 不要用 Hashtable（已过时）
```

---

## 10. 实战示例

### 示例1：商品去重

```java
// 根据商品ID去重
List<Product> products = getProducts();

// 方式1：HashSet（简单场景）
Set<Long> seenIds = new HashSet<>();
List<Product> uniqueProducts = new ArrayList<>();
for (Product p : products) {
    if (seenIds.add(p.getId())) {  // add返回false表示已存在
        uniqueProducts.add(p);
    }
}

// 方式2：Stream（推荐）
List<Product> uniqueProducts2 = products.stream()
    .collect(Collectors.toMap(
        Product::getId,
        p -> p,
        (p1, p2) -> p1  // key冲突时保留第一个
    ))
    .values()
    .stream()
    .collect(Collectors.toList());
```

### 示例2：分组统计

```java
// 按分类统计商品数量
List<Product> products = getProducts();

Map<String, Integer> categoryCount = new HashMap<>();
for (Product p : products) {
    String category = p.getCategory();
    categoryCount.put(category, categoryCount.getOrDefault(category, 0) + 1);
}

// Stream 方式（推荐）
Map<String, Long> categoryCount2 = products.stream()
    .collect(Collectors.groupingBy(
        Product::getCategory,
        Collectors.counting()
    ));
```

### 示例3：构建索引

```java
// 商品列表转 Map（ID → Product）
List<Product> products = getProducts();

Map<Long, Product> productMap = new HashMap<>();
for (Product p : products) {
    productMap.put(p.getId(), p);
}

// Stream 方式（推荐）
Map<Long, Product> productMap2 = products.stream()
    .collect(Collectors.toMap(Product::getId, p -> p));
```

---

## 11. 常见陷阱

### 陷阱1：Arrays.asList 的限制

```java
// ❌ 不能增删
List<String> list = Arrays.asList("A", "B");
list.add("C");  // UnsupportedOperationException

// ✅ 转为 ArrayList
List<String> list2 = new ArrayList<>(Arrays.asList("A", "B"));
list2.add("C");  // ✅
```

### 陷阱2：ConcurrentModificationException

```java
// ❌ 遍历时直接删除
List<String> list = new ArrayList<>(Arrays.asList("A", "B", "C"));
for (String item : list) {
    if (item.equals("B")) {
        list.remove(item);  // ConcurrentModificationException
    }
}

// ✅ 使用 Iterator
Iterator<String> it = list.iterator();
while (it.hasNext()) {
    if (it.next().equals("B")) {
        it.remove();  // 安全删除
    }
}

// ✅ 使用 removeIf（Java 8+，推荐）
list.removeIf(item -> item.equals("B"));
```

### 陷阱3：HashMap key 必须正确重写 equals/hashCode

```java
@Data  // Lombok 自动生成
public class Product {
    private Long id;
    private String name;
}

Map<Product, Integer> map = new HashMap<>();
Product p1 = new Product();
p1.setId(1L);
map.put(p1, 100);

Product p2 = new Product();
p2.setId(1L);
System.out.println(map.get(p2));  // 100（因为 equals/hashCode 正确）
```

---

## 下一步

- **[01-04-Lambda与Stream.md](./01-04-Lambda与Stream.md)** - 函数式编程
- **[01-05-异常处理机制.md](./01-05-异常处理机制.md)** - 异常处理

---

## 快速参考

```java
// List（默认用 ArrayList）
List<Product> products = new ArrayList<>();
products.add(product);
products.get(0);
products.remove(0);
products.size();

// Set（去重）
Set<Long> ids = new HashSet<>();
ids.add(1L);
ids.contains(1L);

// Map（键值对）
Map<Long, Product> productMap = new HashMap<>();
productMap.put(1L, product);
productMap.get(1L);
productMap.containsKey(1L);

// 遍历（推荐 forEach）
list.forEach(item -> System.out.println(item));
map.forEach((k, v) -> System.out.println(k + ": " + v));

// 初始化
List<String> list = Arrays.asList("A", "B", "C");
List<String> mutableList = new ArrayList<>(list);
```
