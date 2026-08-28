# Java 集合框架

容器类是指那些专门用来存储和管理其他对象的类。它们就像现实中的"盒子""柜子"或"容器"，可以放东西、取东西、查看里面有什么。
在 Java 中，容器类主要指的是集合框架。

> 本指南以 JDK 17+ 为基线，只收录日常开发中常用的集合及其 API；文末提供选型速查表。

**本指南结构**：

- **List**：ArrayList、CopyOnWriteArrayList、不可变 List
- **Set**：HashSet、LinkedHashSet、并发 Set（`ConcurrentHashMap.newKeySet()`）、不可变 Set
- **Map**：HashMap、LinkedHashMap、ConcurrentHashMap、不可变 Map
- **Queue**：ArrayDeque、LinkedBlockingQueue
- **横切主题**：集合工具类（Collections）、集合初始化
- **选型速查表**：按需求快速定位合适的集合

---

## List

**特点：有序、可重复**

### ArrayList

#### 有了数组，为什么还要有集合的出现？

- **类型单一**：数组只能存储单一类型的数据，而集合可以存储多种类型的数据；
- **长度固定**：数组的长度是固定的，而集合的长度是可变的；
- **操作不丰富**：数组的存储操作比较复杂，插入、删除、查找、排序等都需要自己写代码。而集合的存储操作比较简单，有现成的 `add`、`remove`、`contains` 方法；

因此，在实际编码中，除非有明确的性能或内存顾虑，否则优先使用集合。完整演示见下方「ArrayList 常用 API」的示例代码。

#### ArrayList 常用 API

| 方法                             | 说明                                              |
| -------------------------------- | ------------------------------------------------- |
| `add(E e)`                       | 在 List 集合尾部添加元素                          |
| `add(int index, E element)`      | 在指定位置添加元素                                |
| `addAll(Collection c)`           | 将另一个集合的所有元素追加到尾部                  |
| `remove(int index)`              | 根据索引位置移除元素                              |
| `remove(Object o)`               | 根据元素内容移除元素                              |
| `removeIf(Predicate filter)`     | 移除所有满足条件的元素                            |
| `get(int index)`                 | 根据索引位置获取元素                              |
| `set(int index, E element)`      | 根据索引位置修改元素                              |
| `replaceAll(UnaryOperator op)`   | 对每个元素执行操作并原地替换                      |
| `indexOf(Object o)`              | 返回元素索引位置                                  |
| `contains(Object o)`             | 是否包含某元素                                    |
| `subList(int from, int to)`      | 返回 `[from, to)` 范围的视图，修改视图影响原 List |
| `sort(Comparator c)`             | 按比较器原地排序                                  |
| `forEach(Consumer action)`       | 遍历每个元素                                      |
| `size()`                         | 返回 List 集合大小                                |
| `clear()`                        | 清空 List 集合元素                                |
| `isEmpty()`                      | 判断 List 集合是否为空                            |
| `toArray()`                      | 将 List 集合转换成数组                            |
| `toArray(IntFunction generator)` | 转换成指定类型的数组，如 `toArray(String[]::new)` |

```java
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ArrayListDemo {
    public static void main(String[] args) {
        // 基本操作：添加、删除、判断
        List<String> students = new ArrayList<>();
        students.add("张三");
        students.add("李四");
        students.add("王五");
        students.remove("李四");

        boolean hasZhang = students.contains("张三");
        int size = students.size();
        System.out.println(hasZhang); // true
        System.out.println(size);     // 2

        // 条件删除：移除所有偶数
        List<Integer> list = new ArrayList<>(List.of(3, 1, 4, 1, 5));
        list.removeIf(n -> n % 2 == 0);

        // 原地排序
        list.sort(Comparator.naturalOrder());

        // 每个元素翻倍
        list.replaceAll(n -> n * 2);

        // 视图操作：只操作前 2 个元素
        List<Integer> head = list.subList(0, 2);
        head.clear(); // 原 list 的前 2 个元素也被删除

        // 转成指定类型数组（List.of 详见下文「不可变 List」）
        String[] arr = List.of("A", "B").toArray(String[]::new);
    }
}
```

---

### CopyOnWriteArrayList

CopyOnWriteArrayList 是线程安全的 List，采用**写时复制**策略：每次写操作（add/set/remove）都会复制一份底层数组，读操作则完全不加锁。

- **适用场景**：读多写少，如事件监听器列表、配置缓存、观察者模式；
- **不适用**：元素量大或写操作频繁（复制数组开销高），此时考虑 `Collections.synchronizedList` 或加锁；
- **注意**：迭代器是创建时的快照，遍历期间其他线程的修改不可见，也不会抛 `ConcurrentModificationException`。

#### 常用 API

常用 API 与 ArrayList 基本一致，这里只列出差异和特有方法：

| 方法                                      | 说明                       |
| ----------------------------------------- | -------------------------- |
| `add(E e)` / `add(int index, E e)`        | 线程安全地添加元素         |
| `addIfAbsent(E e)`                        | 元素不存在时才添加（特有） |
| `set(int index, E element)`               | 线程安全地修改元素         |
| `remove(int index)` / `remove(Object o)`  | 线程安全地移除元素         |
| `get(int index)` / `size()` / `isEmpty()` | 读操作不加锁               |

```java
import java.util.concurrent.CopyOnWriteArrayList;

// 典型场景：监听器列表，注册少、通知多
CopyOnWriteArrayList<EventListener> listeners = new CopyOnWriteArrayList<>();

// 多线程并发注册，无需额外加锁
listeners.add(listener);
listeners.addIfAbsent(listener); // 避免重复注册

// 遍历时不会被其他线程的修改干扰
for (EventListener l : listeners) {
    l.onEvent(event);
}
```

---

### 不可变 List

不可变 List 创建后不能增删改，适合用作常量配置、方法返回值（防止调用方篡改）等场景。

| 创建方式                  | 说明                                |
| ------------------------- | ----------------------------------- |
| `List.of(e1, e2, ...)`    | 直接创建不可变列表，元素不允许 null |
| `List.copyOf(collection)` | 从已有集合复制出不可变副本          |

```java
List<String> levels = List.of("INFO", "WARN", "ERROR");
// levels.add("DEBUG"); // UnsupportedOperationException

// 从已有集合复制
List<String> copy = List.copyOf(mutableList);

// Stream 收集为不可变列表（JDK 16+）
List<String> names = users.stream()
        .map(User::getName)
        .toList();
```

---

## Set

### HashSet

HashSet 用于存储不重复的元素，且不保证顺序，常用于需要快速去重、快速包含性检查的场景。

#### 常用方法

| 方法                         | 说明                           |
| ---------------------------- | ------------------------------ |
| `add(E e)`                   | 添加元素，若已存在则返回 false |
| `remove(Object o)`           | 移除元素，存在则返回 true      |
| `removeIf(Predicate filter)` | 移除所有满足条件的元素         |
| `contains(Object o)`         | 判断是否包含该元素             |
| `addAll(Collection c)`       | 批量添加元素                   |
| `forEach(Consumer action)`   | 遍历每个元素                   |
| `size()`                     | 返回元素个数                   |
| `isEmpty()`                  | 判断是否为空                   |
| `clear()`                    | 清空所有元素                   |

#### 常见用途

```java
// 基本操作
Set<Long> ids = new HashSet<>();
ids.add(1L);
ids.add(2L);
ids.add(1L); // 重复，不会添加
System.out.println(ids.size());       // 2
System.out.println(ids.contains(1L)); // true

// 1. 去重
List<Long> list = List.of(1L, 2L, 2L, 3L);
Set<Long> uniqueIds = new HashSet<>(list); // [1, 2, 3]

// 2. 判断是否存在（快速）
Set<String> bannedWords = new HashSet<>(List.of("spam", "ad"));
if (bannedWords.contains(word)) {
    // 过滤
}

// 3. 集合运算
Set<String> set1 = new HashSet<>(List.of("A", "B", "C"));
Set<String> set2 = new HashSet<>(List.of("B", "C", "D"));

// 交集
set1.retainAll(set2); // [B, C]

// 并集
set1.addAll(set2); // [A, B, C, D]

// 差集
set1.removeAll(set2); // [A]
```

### LinkedHashSet

LinkedHashSet 继承自 HashSet，内部额外维护了一个双向链表记录插入顺序，因此**遍历顺序与插入顺序一致**。常用 API 与 HashSet 完全相同。

适用场景：需要去重且必须保持插入顺序，例如 JSON 字段去重、按提交顺序展示的唯一记录。

```java
Set<String> visited = new LinkedHashSet<>();
visited.add("/home");
visited.add("/list");
visited.add("/home"); // 重复，不会添加，也不改变原有顺序

System.out.println(visited); // [/home, /list]
```

---

### 并发 Set：ConcurrentHashMap.newKeySet()

需要线程安全的 Set 时，首选 `ConcurrentHashMap.newKeySet()`：它基于 ConcurrentHashMap 实现，所有操作线程安全，性能远好于 `Collections.synchronizedSet` 包装。

```java
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// 创建一个线程安全的 Set
Set<Long> onlineUserIds = ConcurrentHashMap.newKeySet();

// 多线程并发调用，无需额外加锁
onlineUserIds.add(userId);       // 上线
onlineUserIds.remove(userId);    // 下线
boolean online = onlineUserIds.contains(userId);
int count = onlineUserIds.size();
```

常用 API 与 HashSet 相同（`add` / `remove` / `contains` / `size` / `iterator`），常用于并发去重、在线用户集合、已处理任务记录等场景。

---

### 不可变 Set

| 创建方式                 | 说明                                            |
| ------------------------ | ----------------------------------------------- |
| `Set.of(e1, e2, ...)`    | 直接创建不可变集合，元素不允许 null、不允许重复 |
| `Set.copyOf(collection)` | 从已有集合复制出不可变副本                      |

```java
Set<String> roles = Set.of("admin", "user");
// roles.add("guest"); // UnsupportedOperationException

Set<String> copy = Set.copyOf(existingSet);
```

---

## Map

### HashMap

哈希表（Hash Table）是一种数据结构，它利用哈希函数将键映射到特定的值，使用键快速检索数据。存储的数据结构是无序的。

#### 常用 API

| 方法                                                  | 说明                                   |
| ----------------------------------------------------- | -------------------------------------- |
| `put(K key, V value)`                                 | 存入键值对，相同 key 会覆盖旧值        |
| `putIfAbsent(K key, V value)`                         | key 不存在时才放入，已存在则不覆盖     |
| `get(Object key)`                                     | 根据 key 取值，不存在返回 null         |
| `getOrDefault(Object key, V default)`                 | 取不到时返回默认值，避免 NPE           |
| `remove(Object key)`                                  | 根据 key 移除键值对                    |
| `containsKey(Object key)`                             | 是否包含指定 key                       |
| `containsValue(Object value)`                         | 是否包含指定 value                     |
| `entrySet()`                                          | 返回所有键值对的 Set 视图              |
| `forEach(BiConsumer action)`                          | 遍历所有键值对                         |
| `keySet()`                                            | 返回所有 key 的 Set 视图               |
| `values()`                                            | 返回所有 value 的 Collection 视图      |
| `putAll(Map m)`                                       | 批量放入另一个 Map 的所有键值对        |
| `replace(K key, V value)`                             | 替换已存在 key 的值                    |
| `replaceAll(BiFunction function)`                     | 对所有值批量转换                       |
| `merge(K key, V value, BiFunction remappingFunction)` | key 不存在直接放入，已存在则合并新旧值 |
| `computeIfAbsent(K key, Function mappingFunction)`    | key 不存在时计算 value 并存入          |
| `size()`                                              | 返回键值对数量                         |
| `isEmpty()`                                           | 判断是否为空                           |
| `clear()`                                             | 清空所有键值对                         |

#### 基本操作

```java
Map<String, String> map = new HashMap<>();
map.put("1", "One");
map.put("2", "Two");
map.put("3", "Three");

String value = map.get("1");            // "One"
map.remove("2");                        // 移除 key="2" 的键值对
boolean has = map.containsKey("3");     // true
```

#### 安全取值

```java
// 取不到时返回默认值，避免 NPE
String name = userMap.getOrDefault("name", "未知用户");

// key 不存在时计算并存入（典型场景：缓存）
User user = cacheMap.computeIfAbsent("user:1001", key -> userService.findFromDB(key));
```

#### 遍历

```java
// 方式一：for-each entrySet（最常用）
for (Map.Entry<String, String> entry : userMap.entrySet()) {
    System.out.println(entry.getKey() + " -> " + entry.getValue());
}

// 方式二：forEach（更简洁）
userMap.forEach((key, value) -> System.out.println(key + " -> " + value));
```

#### 合并与更新

```java
// putAll：批量合并（相同 key 会被覆盖）
Map<String, String> target = new HashMap<>(Map.of("name", "张三"));
Map<String, String> source = Map.of("name", "李四", "age", "25");
target.putAll(source); // {name=李四, age=25}

// replace：替换已存在 key 的值
map.replace("苹果", 10);

// replaceAll：批量转换所有值
prices.replaceAll((name, price) -> price * 0.8); // 所有商品打 8 折

// merge：key 不存在直接放入，已存在则合并（典型场景：计数、分组累加）
Map<String, Integer> wordCount = new HashMap<>();
for (String word : words) {
    wordCount.merge(word, 1, Integer::sum); // 不存在则置 1，已存在则 +1
}
```

---

### LinkedHashMap

LinkedHashMap 继承自 HashMap，在 HashMap 的基础上内部额外维护了一个双向链表，用于记录元素的**插入顺序**。因此遍历 LinkedHashMap 时，顺序与插入顺序一致。

**适用场景**：

- 需要保持插入顺序（如配置项、JSON 字段顺序）
- 缓存实现（LRU 算法）
- 需要可预测的遍历顺序

#### 与 HashMap 的区别

```java
// HashMap：无序
Map<String, Integer> hashMap = new HashMap<>();
hashMap.put("C", 3);
hashMap.put("A", 1);
hashMap.put("B", 2);
System.out.println(hashMap); // {A=1, B=2, C=3} 或其他顺序

// LinkedHashMap：按插入顺序
Map<String, Integer> linkedMap = new LinkedHashMap<>();
linkedMap.put("C", 3);
linkedMap.put("A", 1);
linkedMap.put("B", 2);
System.out.println(linkedMap); // {C=3, A=1, B=2}
```

#### 常见用途

```java
// 1. 保持配置项顺序
Map<String, String> config = new LinkedHashMap<>();
config.put("host", "localhost");
config.put("port", "8080");
config.put("timeout", "30");
// 遍历时顺序与插入时一致

// 2. JSON 字段顺序保持（配合 Jackson 等库）
Map<String, Object> json = new LinkedHashMap<>();
json.put("id", 1);
json.put("name", "张三");
json.put("age", 25);
// 序列化后字段顺序与 put 顺序一致

// 3. LRU 缓存（访问顺序模式）
// 构造器参数：initialCapacity, loadFactor, accessOrder(true=按访问顺序)
Map<Integer, String> lruCache = new LinkedHashMap<>(16, 0.75f, true);
lruCache.put(1, "A");
lruCache.put(2, "B");
lruCache.get(1); // 访问 key=1，它会被移到链表末尾
// 此时遍历顺序：{2=B, 1=A}（1 被访问后移到最后）
```

常用 API 与 HashMap 完全相同，无需额外学习。

---

### ConcurrentHashMap

ConcurrentHashMap 是 Java 并发包中最常用的线程安全 Map。不允许 null 键和 null 值。需要并发 Set 时可用其 `newKeySet()`，见 Set 章节。

**常用 API 与 HashMap 完全相同**，区别在于：

- **线程安全**：所有操作都是原子的
- **不允许 null**：键和值都不允许为 null
- **迭代器 fail-safe**：不会抛出 `ConcurrentModificationException`

#### 基本操作

```java
ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
map.put("key1", "value1");
String value = map.get("key1");
map.remove("key1");
```

#### 原子操作

```java
// putIfAbsent：key 不存在时才放入（线程安全）
map.putIfAbsent("key1", "value1");

// replace：原子替换
map.replace("key1", "newValue");
map.replace("key1", "oldValue", "newValue"); // 只有旧值匹配才替换
```

#### 计算操作

```java
// compute：总是执行计算（无论 key 是否存在）
map.compute("counter", (k, v) -> {
    return v == null ? 1 : v + 1; // 值不存在置 1，否则加 1
});

// computeIfAbsent：key 不存在时才计算（典型场景：懒加载缓存）
map.computeIfAbsent("user:1001", key -> userService.findFromDB(key));

// computeIfPresent：key 存在时才计算（典型场景：更新已有值）
map.computeIfPresent("key1", (k, v) -> v + "_updated");
```

#### 合并操作

```java
// merge：key 不存在直接放入，已存在则合并（典型场景：计数、累加）
map.merge("counter", 1, Integer::sum); // 不存在置 1，已存在则 +1
```

---

### 不可变 Map

| 创建方式                              | 说明                                                    |
| ------------------------------------- | ------------------------------------------------------- |
| `Map.of(k1, v1, k2, v2, ...)`         | 直接创建不可变 Map（最多 10 组键值对），键值不允许 null |
| `Map.ofEntries(Map.entry(k, v), ...)` | 超过 10 组键值对时使用                                  |
| `Map.copyOf(map)`                     | 从已有 Map 复制出不可变副本                             |

```java
Map<String, Integer> config = Map.of("timeout", 30, "retry", 3);
// config.put("timeout", 60); // UnsupportedOperationException

// 每个 Map.entry 打包一组键值对：
//   Map.entry("a", 1)  →  "a"=1
//   Map.entry("b", 2)  →  "b"=2
// ofEntries 把它们合成一个 Map
Map<String, Integer> big = Map.ofEntries(
        Map.entry("a", 1),
        Map.entry("b", 2));
// big = {a=1, b=2}
```

---

## Queue

### ArrayDeque

ArrayDeque 是基于循环数组实现的双端队列。它不是线程安全的，且不允许 null 元素。

#### 常用 API

**栈操作**（后进先出）

| 方法        | 说明                   |
| ----------- | ---------------------- |
| `push(E e)` | 压栈（添加到头部）     |
| `pop()`     | 弹栈（移除并返回头部） |
| `peek()`    | 查看栈顶（不移除）     |

**队列操作**（先进先出）

| 方法         | 说明                   |
| ------------ | ---------------------- |
| `offer(E e)` | 入队（添加到尾部）     |
| `poll()`     | 出队（移除并返回头部） |
| `peek()`     | 查看队首（不移除）     |

**双端队列操作**（两端均可操作）

| 方法                                 | 说明                      |
| ------------------------------------ | ------------------------- |
| `offerFirst(E e)` / `offerLast(E e)` | 在头部 / 尾部添加         |
| `pollFirst()` / `pollLast()`         | 移除并返回头部 / 尾部元素 |
| `peekFirst()` / `peekLast()`         | 查看头部 / 尾部元素       |

```java
import java.util.ArrayDeque;
import java.util.Deque;

// 当栈用：后进先出
Deque<String> stack = new ArrayDeque<>();
stack.push("A");
stack.push("B");
System.out.println(stack.pop()); // "B"

// 当队列用：先进先出
Deque<String> queue = new ArrayDeque<>();
queue.offer("task-1");
queue.offer("task-2");
System.out.println(queue.poll()); // "task-1"
```

---

### LinkedBlockingQueue

LinkedBlockingQueue 是一个**线程安全**的队列。容量默认是 `Integer.MAX_VALUE`，因此通常可以认为它是“无界”的，但需根据环境提供合理限制，否则容易造成内存泄露。

内部使用两个独立的锁（读锁和写锁分离）来提高并发度，生产和消费可以一定程度并行。

#### 常用方法

| 方法                                      | 说明                                   |
| ----------------------------------------- | -------------------------------------- |
| `put(E e)`                                | 插入元素，队列满时阻塞直到有空位       |
| `take()`                                  | 取出元素，队列空时阻塞直到有元素       |
| `offer(E e)`                              | 插入元素，队列满时返回 false（不阻塞） |
| `poll()`                                  | 取出元素，队列空时返回 null（不阻塞）  |
| `offer(E e, long timeout, TimeUnit unit)` | 在指定时间内等待有空位，超时返回 false |
| `poll(long timeout, TimeUnit unit)`       | 在指定时间内等待有元素，超时返回 null  |
| `size()`                                  | 返回当前元素个数（并发下非精确）       |
| `remainingCapacity()`                     | 返回剩余容量                           |

#### 生产者-消费者模型示例

```java
import java.util.concurrent.LinkedBlockingQueue;

LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>(3);

// 生产者
new Thread(() -> {
    try {
        for (int i = 1; i <= 5; i++) {
            String item = "task-" + i;
            queue.put(item);  // 队列满则阻塞等待
            System.out.println("生产者：" + item);
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}).start();

// 消费者
new Thread(() -> {
    try {
        while (true) {
            String item = queue.take(); // 队列空则阻塞等待
            System.out.println("消费者：" + item);
            Thread.sleep(500); // 模拟消费耗时
        }
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    }
}).start();
```

运行结果（部分示例）：

```
生产者：task-1
生产者：task-2
生产者：task-3  // 队列已满，生产者阻塞
消费者：task-1
生产者：task-4  // 消费后队列有空位，生产者继续
消费者：task-2
...
```

---

## 集合工具类

### Collections 工具类

Collections 是一个**工具类**，提供了一系列静态方法，用于对各种集合进行通用操作。它不是集合本身，而是"集合的瑞士军刀"。

主要功能分类：

| 功能         | 方法示例                                  | 说明                            |
| ------------ | ----------------------------------------- | ------------------------------- |
| **集合操作** | `reverse`、`shuffle`、`rotate`、`swap`    | 反转、随机打乱、旋转、交换      |
| **查找**     | `max`、`min`、`binarySearch`、`frequency` | 最大/最小值、二分查找、出现次数 |
| **修改**     | `fill`、`copy`、`replaceAll`、`nCopies`   | 填充、复制、替换、生成重复列表  |

```java
import java.util.Collections;

List<Integer> list = new ArrayList<>(List.of(3, 1, 2));

// 集合操作
Collections.reverse(list);              // 反转：[2, 1, 3]
Collections.shuffle(list);              // 随机打乱：顺序随机

// 查找
int max = Collections.max(list);        // 最大值：3
int min = Collections.min(list);        // 最小值：1
int count = Collections.frequency(list, 2); // 元素 2 出现的次数
int index = Collections.binarySearch(list, 2); // 二分查找（需先排序）

// 修改
Collections.fill(list, 0);              // 全部填充为 0：[0, 0, 0]
Collections.replaceAll(list, 0, 1);     // 替换所有 0 为 1
```

---

## 选型速查表

| 需求                      | 推荐集合                        | 备注                  |
| ------------------------- | ------------------------------- | --------------------- |
| 通用的有序列表            | `ArrayList`                     | 默认首选，随机访问快  |
| 线程安全 List（读多写少） | `CopyOnWriteArrayList`          | 监听器、缓存场景      |
| 线程安全 List（读写均衡） | `Collections.synchronizedList`  | 所有操作加锁          |
| 去重、快速判断存在        | `HashSet`                       | 无序                  |
| 去重且保持插入顺序        | `LinkedHashSet`                 | 遍历顺序 = 插入顺序   |
| 线程安全 Set              | `ConcurrentHashMap.newKeySet()` | 并发去重首选          |
| 键值对存取                | `HashMap`                       | 默认首选，无序        |
| 键值对且保持插入顺序      | `LinkedHashMap`                 | JSON 序列化、LRU 基础 |
| 线程安全 Map              | `ConcurrentHashMap`             | 不允许 null 键值      |
| 栈 / 普通队列（单线程）   | `ArrayDeque`                    | 推荐首选              |
| 生产者-消费者（跨线程）   | `LinkedBlockingQueue`           | 记得设置容量上限      |
| 常量/返回给调用方的集合   | `List.of` / `Map.copyOf` 等     | 不可变，防篡改        |
