# Java 集合框架

容器类是指那些专门用来存储和管理其他对象的类。它们就像现实中的"盒子""柜子"或"容器"，可以放东西、取东西、查看里面有什么。
在 Java 中，容器类主要指的是集合框架。

> 本指南以 JDK 17+ 为基线，只收录日常开发中常用的集合及其 API；文末提供选型速查表。

---

## List

**特点：有序、可重复**

### ArrayList

#### 有了数组，为什么还要有集合的出现？

- **类型单一**：数组只能存储单一类型的数据，而集合可以存储多种类型的数据；
- **长度固定**：数组的长度是固定的，而集合的长度是可变的；
- **操作不丰富**：数组的存储操作比较复杂，插入、删除、查找、排序等都需要自己写代码。而集合的存储操作比较简单，有现成的 `add`、`remove`、`contains` 方法；

因此，在实际编码中，除非有明确的性能或内存顾虑，否则优先使用集合。

```java
import java.util.ArrayList;
import java.util.List;

public class User {
    static void main() {
        List<String> students = new ArrayList<>();
        students.add("张三");
        students.add("李四");
        students.add("王五");
        students.remove("李四");

        boolean hasZhang = students.contains("张三");
        int size = students.size();

        System.out.println(hasZhang);
        System.out.println(size);
    }
}
```

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
List<Integer> list = new ArrayList<>(Arrays.asList(3, 1, 4, 1, 5));

// 条件删除：移除所有偶数
list.removeIf(n -> n % 2 == 0);

// 原地排序
list.sort(Comparator.naturalOrder());

// 每个元素翻倍
list.replaceAll(n -> n * 2);

// 视图操作：只操作前 2 个元素
List<Integer> head = list.subList(0, 2);
head.clear(); // 原 list 的前 2 个元素也被删除

// 转成指定类型数组
String[] arr = List.of("A", "B").toArray(String[]::new);
```

---

### LinkedList

LinkedList 是一个双向链表，同时实现了 `List` 和 `Deque`（双端队列）接口，既可以当 List 用，也可以当队列/栈用。

#### ArrayList vs LinkedList

| 特性          | ArrayList | LinkedList   |
| ------------- | --------- | ------------ |
| 底层结构      | 动态数组  | 双向链表     |
| 随机访问      | O(1) ⚡快 | O(n) 慢      |
| 头部插入/删除 | O(n) 慢   | O(1) ⚡快    |
| 尾部插入/删除 | O(1) ⚡快 | O(1) ⚡快    |
| 内存占用      | 连续空间  | 额外指针开销 |
| 使用场景      | 查询多    | 增删多       |

> **特殊场景**：频繁头部操作用 LinkedList，其余场景使用 ArrayList。

#### Deque / Queue 常用 API

LinkedList 作为 `List` 的常用 API 与 ArrayList 相同，此外还拥有双端队列/栈操作方法：

| 方法                               | 说明                                        |
| ---------------------------------- | ------------------------------------------- |
| `addFirst(E e)` / `addLast(E e)`   | 在头部 / 尾部添加元素                       |
| `removeFirst()` / `removeLast()`   | 移除并返回头部 / 尾部元素（空时抛异常）     |
| `getFirst()` / `getLast()`         | 获取头部 / 尾部元素（不移除）               |
| `offer(E e)` / `poll()` / `peek()` | 队列入队 / 出队 / 查看队首（空时返回 null） |
| `push(E e)` / `pop()` / `peek()`   | 栈压入 / 弹出 / 查看栈顶                    |

```java
LinkedList<String> deque = new LinkedList<>();

// 当队列用：先进先出
deque.offer("task-1");
deque.offer("task-2");
String task = deque.poll(); // "task-1"

// 当栈用：后进先出
deque.push("A");
deque.push("B");
String top = deque.pop(); // "B"
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

## Set

### HashSet

HashSet 用于存储不重复的元素，且不保证顺序，常用于需要快速去重、快速包含性检查的场景。

```java
Set<Long> ids = new HashSet<>();
ids.add(1L);
ids.add(2L);
ids.add(1L); // 重复，不会添加

System.out.println(ids.size());        // 2
System.out.println(ids.contains(1L));  // true
```

#### 常用方法

| 方法                       | 说明                           |
| -------------------------- | ------------------------------ |
| `add(E e)`                 | 添加元素，若已存在则返回 false |
| `remove(Object o)`         | 移除元素，存在则返回 true      |
| `contains(Object o)`       | 判断是否包含该元素             |
| `addAll(Collection c)`     | 批量添加元素                   |
| `forEach(Consumer action)` | 遍历每个元素                   |
| `size()`                   | 返回元素个数                   |
| `isEmpty()`                | 判断是否为空                   |
| `clear()`                  | 清空所有元素                   |
| `iterator()`               | 返回迭代器（元素顺序不固定）   |

#### HashSet vs LinkedHashSet vs TreeSet

| 类型          | 底层结构    | 有序性   | 性能     | 使用场景       |
| ------------- | ----------- | -------- | -------- | -------------- |
| HashSet       | 哈希表      | 无序     | O(1)     | 去重、查找     |
| LinkedHashSet | 哈希表+链表 | 插入顺序 | O(1)     | 需保持插入顺序 |
| TreeSet       | 红黑树      | 自然排序 | O(log n) | 需排序         |

```java
// HashSet - 无序
Set<Integer> hashSet = new HashSet<>();
hashSet.add(3);
hashSet.add(1);
hashSet.add(2);
System.out.println(hashSet); // [1, 2, 3] 或其他顺序

// LinkedHashSet - 插入顺序
Set<Integer> linkedSet = new LinkedHashSet<>();
linkedSet.add(3);
linkedSet.add(1);
linkedSet.add(2);
System.out.println(linkedSet); // [3, 1, 2]

// TreeSet - 自然排序
Set<Integer> treeSet = new TreeSet<>();
treeSet.add(3);
treeSet.add(1);
treeSet.add(2);
System.out.println(treeSet); // [1, 2, 3]
```

---

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

### Set 常见用途

```java
// 1. 去重
List<Long> ids = Arrays.asList(1L, 2L, 2L, 3L);
Set<Long> uniqueIds = new HashSet<>(ids); // [1, 2, 3]

// 2. 判断是否存在（快速）
Set<String> bannedWords = new HashSet<>(Arrays.asList("spam", "ad"));
if (bannedWords.contains(word)) {
    // 过滤
}

// 3. 集合运算
Set<String> set1 = new HashSet<>(Arrays.asList("A", "B", "C"));
Set<String> set2 = new HashSet<>(Arrays.asList("B", "C", "D"));

// 交集
set1.retainAll(set2); // [B, C]

// 并集
set1.addAll(set2); // [A, B, C, D]

// 差集
set1.removeAll(set2); // [A]
```

---

## Map

### HashMap

哈希表（Hash Table）是一种数据结构，它利用哈希函数将键映射到特定的值，使用键快速检索数据。存储的数据结构是无序的。

#### 1. 存取数据

**`put(key, value)`**

```java
HashMap<String, String> map = new HashMap<>();
map.put("1", "One");
map.put("2", "Two");
map.put("3", "Three");
```

**`putIfAbsent(key, value)`** — 不存在时才放入

```java
// key 不存在时放入并返回 null；已存在时返回旧值
map.putIfAbsent("1", "一");
```

**`get(key)`** — 取值

```java
String value = map.get("1");
```

**`getOrDefault(key, defaultValue)`** — 安全取值

取不到值时返回默认值，避免 NPE。

```java
// 安全取值（避免 NPE）
String name = userMap.getOrDefault("name", "未知用户");
```

**`computeIfAbsent(key, mappingFunction)`** — 缓存取值

当 Key 不存在时，执行函数计算 Value 并存入。

```java
// 实际场景：从缓存中取数据，若无则查数据库并缓存
User user = cacheMap.computeIfAbsent("user:1001", key -> userService.findFromDB(key));
```

#### 2. 删除数据

**`remove(key)`**

```java
HashMap<String, String> map = new HashMap<>();
map.put("1", "One");
map.remove("1");
```

#### 3. 判断存在

**`containsKey(key)` / `containsValue(value)`**

```java
HashMap<String, String> map = new HashMap<>();
map.put("1", "One");
if (map.containsKey("1")) {
    System.out.println("map contains key 1");
}
```

#### 4. 遍历数据

**`entrySet`** — 遍历所有键值对，返回值类型 `Set<Map.Entry<K, V>>`

> 方法返回的是原 Map 的视图，修改视图会直接影响原 Map。

```java
Map<String, String> userMap = redisRepository.hGetAll("user:1001");

// 方式一：传统迭代（最常用）
for (Map.Entry<String, String> entry : userMap.entrySet()) {
    String field = entry.getKey();   // 比如 "name"
    String value = entry.getValue(); // 比如 "\"张三\""
    System.out.println(field + " -> " + value);
}

// 方式二：Lambda（更推荐）
userMap.entrySet().forEach(entry -> {
    System.out.println(entry.getKey() + " -> " + entry.getValue());
});
```

**`forEach`** — 直接遍历键值对

```java
userMap.forEach((key, value) -> System.out.println(key + " -> " + value));
```

**`keySet`** — 遍历所有 Key，返回值类型 `Set<K>`

```java
HashMap<String, Integer> scores = new HashMap<>();
scores.put("张三", 95);
scores.put("李四", 88);
scores.put("王五", 92);
scores.put("赵六", 78);

Set<String> keys = scores.keySet();
```

**`values`** — 遍历所有 Value，返回值类型 `Collection<V>`

```java
Collection<Integer> values = scores.values();
```

#### 5. 批量合并

**`putAll()`** — 将另一个 Map 的所有键值对复制进来。

```java
HashMap<String, String> target = new HashMap<>();
target.put("name", "张三");

HashMap<String, String> source = new HashMap<>();
source.put("name", "李四");
source.put("age", "25");

target.putAll(source);
System.out.println(target); // 输出：{name=李四, age=25}
// 注意："name" 的值从 "张三" 被覆盖为 "李四"
```

#### 6. 条件更新

**`replace(key, value)`**

```java
HashMap<String, Integer> map = new HashMap<>();
map.put("苹果", 5);
map.put("香蕉", 3);
map.put("橙子", 7);

System.out.println("替换前: " + map);

// 将"苹果"的值从 5 替换为 10
Integer oldValue = map.replace("苹果", 10);
```

**`replaceAll(BiFunction)`** — 对所有值批量转换

```java
// 所有商品价格打 8 折
prices.replaceAll((name, price) -> price * 0.8);
```

**`merge(key, value, remappingFunction)`** — 合并新旧值

key 不存在时直接放入给定值；已存在时用函数合并新旧值。典型场景：计数、分组累加。

```java
Map<String, Integer> wordCount = new HashMap<>();
for (String word : words) {
    // 不存在则置 1，已存在则 +1
    wordCount.merge(word, 1, Integer::sum);
}
```

---

### LinkedHashMap

LinkedHashMap 继承自 HashMap，但在 HashMap 的基础上内部维护了一个双向链表，用于记录元素的插入顺序。因此当需要保持插入顺序用 LinkedHashMap。

```java
import java.util.*;

public class LinkedHashMapDemo {
    public static void main(String[] args) {
        // 默认构造器，按插入顺序
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
        map.put("张三", 85);
        map.put("李四", 92);
        map.put("王五", 78);

        // 遍历顺序与插入顺序一致
        // 返回所有 Entry 的 Set 视图
        for (Map.Entry<String, Integer> entry : map.entrySet()) {
            System.out.println(entry.getKey() + " -> " + entry.getValue());
        }
    }
}
```

---

### ConcurrentHashMap

ConcurrentHashMap 是 Java 并发包中最常用的线程安全 Map。不允许 null 键和 null 值。

```java
import java.util.concurrent.ConcurrentHashMap;

// 创建
ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();

// 基本操作
map.put("key1", "value1");
String value = map.get("key1");
map.remove("key1");

// 原子操作
// 如果不存在则添加
map.putIfAbsent("key1", "value1");

// 替换值
map.replace("key1", "newValue");
map.replace("key1", "oldValue", "newValue"); // 只有旧值匹配才替换
```

#### 计算操作

**1. `compute`** — 总是执行计算函数，无论 key 是否存在

- 参数：`(key, BiFunction<key, oldValue, newValue>)`
- 如果返回 null，则删除该 key；否则更新为返回值

```java
map.compute("key1", (k, v) -> {
    // 如果v为null，说明key不存在，返回1
    if (v == null) {
        return "1"; // 初始值
    } else {
        return String.valueOf(Integer.parseInt(v) + 1); // 递增
    }
});
// 典型场景：计数器（无论key是否存在都能工作）
```

**2. `computeIfAbsent`** — 仅当 key 不存在时才执行计算

- 参数：`(key, Function<key, value>)`
- 如果 key 已存在，直接返回旧值；否则执行函数并存入

```java
map.computeIfAbsent("key1", k -> {
    // 这个函数只在key不存在时调用
    return "defaultValue"; // 或从数据库查询等耗时操作
});
// 典型场景：懒加载缓存（避免重复计算）
```

**3. `computeIfPresent`** — 仅当 key 存在时才执行计算

- 参数：`(key, BiFunction<key, oldValue, newValue>)`
- 如果 key 不存在，什么都不做；如果返回 null，则删除该 key

```java
map.computeIfPresent("key1", (k, v) -> {
    // v一定不为null（因为key存在）
    return v + "_updated"; // 或返回null来删除
});
// 典型场景：更新已有值（确保key存在才操作）
```

#### 合并操作

```java
map.merge("key1", "1", (oldVal, newVal) ->
    String.valueOf(Integer.parseInt(oldVal) + Integer.parseInt(newVal)));
```

#### newKeySet() — 线程安全的 Set

`ConcurrentHashMap.newKeySet()` 返回一个由 ConcurrentHashMap 支撑的线程安全 Set，是需要并发 Set 时的首选（详见 Set 章节）：

```java
Set<String> set = ConcurrentHashMap.newKeySet();
```

---

## Queue

### ArrayDeque

ArrayDeque 是基于循环数组实现的双端队列，**用作栈或普通队列时性能优于 LinkedList**（无节点对象开销，缓存友好）。它不是线程安全的，且不允许 null 元素。

- 当**栈**用：`push` / `pop` / `peek`，替代遗留的 `Stack`；
- 当**队列**用：`offer` / `poll` / `peek`；
- 当**双端队列**用：`offerFirst` / `offerLast` / `pollFirst` / `pollLast`。

#### 常用 API

| 方法分类 | 头部（先进先出的"队首" / 栈顶）     | 尾部                              |
| -------- | ----------------------------------- | --------------------------------- |
| 添加     | `addFirst(E e)` / `offerFirst(E e)` | `addLast(E e)` / `offerLast(E e)` |
| 移除     | `removeFirst()` / `pollFirst()`     | `removeLast()` / `pollLast()`     |
| 查看     | `getFirst()` / `peekFirst()`        | `getLast()` / `peekLast()`        |
| 栈操作   | `push(E e)` / `pop()` / `peek()`    | —                                 |
| 队列操作 | `poll()` / `peek()`（作用于头部）   | `offer(E e)`（作用于尾部）        |

> `add*`/`remove*`/`get*` 失败时抛异常；`offer*`/`poll*`/`peek*` 失败时返回 null 或 false，推荐使用后者。

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

### PriorityQueue

PriorityQueue 是基于二叉堆实现的优先级队列：每次 `poll` 取出的不是最早进入的元素，而是**优先级最高**（最小）的元素。默认按自然顺序排序，也可传入 Comparator 自定义。不是线程安全的，不允许 null 元素。

典型场景：Top-K 问题、任务调度（按优先级执行）、合并 K 个有序序列、Dijkstra 算法。

#### 常用 API

| 方法                      | 说明                                          |
| ------------------------- | --------------------------------------------- |
| `offer(E e)` / `add(E e)` | 入队，按优先级自动调整位置                    |
| `poll()`                  | 移除并返回优先级最高的元素，空时返回 null     |
| `peek()`                  | 查看优先级最高的元素（不移除），空时返回 null |
| `size()` / `isEmpty()`    | 元素个数 / 是否为空                           |
| `clear()`                 | 清空                                          |

> 注意：`iterator()` 遍历**不保证**按优先级顺序输出，要按序取出必须循环 `poll()`。

```java
import java.util.PriorityQueue;

// 默认小顶堆：数字越小优先级越高
PriorityQueue<Integer> pq = new PriorityQueue<>();
pq.offer(5);
pq.offer(1);
pq.offer(3);
System.out.println(pq.poll()); // 1
System.out.println(pq.poll()); // 3

// 自定义优先级：按任务紧急程度排序
record Task(String name, int priority) {}
PriorityQueue<Task> tasks = new PriorityQueue<>(
        Comparator.comparingInt(Task::priority).reversed());
tasks.offer(new Task("普通报表", 1));
tasks.offer(new Task("线上故障", 9));
System.out.println(tasks.poll().name()); // 线上故障
```

---

### LinkedBlockingQueue

LinkedBlockingQueue 是一个阻塞队列，遵循先进先出原则，类似于浏览器的事件队列（Event Loop）。提供了可选的容量限制，容量默认是 `Integer.MAX_VALUE`，因此通常可以认为它是"无界"的，但需根据环境提供合理限制，否则容易造成内存泄露。

LinkedBlockingQueue 内部使用两个独立的锁来提高并发度：读锁和写锁分离，生产和消费可以一定程度并行。

#### 常用方法

| 方法分类   | 方法                                              | 行为                                        |
| ---------- | ------------------------------------------------- | ------------------------------------------- |
| **阻塞**   | `void put(E e)`                                   | 插入元素，队列满时阻塞直到有空位            |
|            | `E take()`                                        | 取出元素，队列空时阻塞直到有元素            |
| **非阻塞** | `boolean offer(E e)`                              | 队列未满则插入并返回 true，否则返回 false   |
|            | `E poll()`                                        | 队列非空则取出并返回头部元素，否则返回 null |
| **超时**   | `boolean offer(E e, long timeout, TimeUnit unit)` | 在指定时间内等待有空位，超时返回 false      |
|            | `E poll(long timeout, TimeUnit unit)`             | 在指定时间内等待有元素，超时返回 null       |
| **辅助**   | `int size()`                                      | 返回当前元素个数（非精确，因为并发）        |
|            | `int remainingCapacity()`                         | 返回剩余容量                                |

#### 生产者-消费者模型示例

```java
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class LinkedBlockingQueueDemo {
    // 创建容量为 3 的阻塞队列
    private static final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>(3);

    static class Producer implements Runnable {
        @Override
        public void run() {
            try {
                for (int i = 1; i <= 5; i++) {
                    String item = "task-" + i;
                    queue.put(item);  // 队列满则阻塞等待
                    System.out.println(Thread.currentThread().getName() + " 生产：" + item);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    static class Consumer implements Runnable {
        @Override
        public void run() {
            try {
                while (true) {
                    String item = queue.take(); // 队列空则阻塞等待
                    System.out.println(Thread.currentThread().getName() + " 消费：" + item);
                    Thread.sleep(500); // 模拟消费耗时
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args) {
        new Thread(new Producer(), "生产者").start();
        new Thread(new Consumer(), "消费者").start();
    }
}
```

运行结果（部分示例）：

```
生产者 生产：task-1
生产者 生产：task-2
生产者 生产：task-3  // 队列已满，生产者阻塞
消费者 消费：task-1
生产者 生产：task-4  // 消费后队列有空位，生产者继续
消费者 消费：task-2
...
```

---

## 不可变集合

不可变集合创建后不能增删改，适合用作常量配置、方法返回值（防止调用方篡改）、缓存 key 等场景。

| 创建方式                                                                          | 说明                                                 |
| --------------------------------------------------------------------------------- | ---------------------------------------------------- |
| `List.of(e1, e2, ...)` / `Set.of(...)` / `Map.of(k1, v1, ...)`                    | 直接创建不可变集合，元素不允许 null                  |
| `List.copyOf(collection)` / `Set.copyOf(...)` / `Map.copyOf(...)`                 | 从已有集合复制出不可变副本                           |
| `Collectors.toUnmodifiableList()` / `toUnmodifiableSet()` / `toUnmodifiableMap()` | Stream 收集为不可变集合                              |
| `Collections.unmodifiableList(list)` 等                                           | 只读视图：原集合被修改时视图跟着变，注意不是真不可变 |

```java
// 直接创建
List<String> levels = List.of("INFO", "WARN", "ERROR");
Set<String> roles = Set.of("admin", "user");
Map<String, Integer> config = Map.of("timeout", 30, "retry", 3);
// levels.add("DEBUG"); // UnsupportedOperationException

// 从已有集合复制
List<String> copy = List.copyOf(mutableList);

// Stream 收集
Map<Long, String> idToName = users.stream()
        .collect(Collectors.toUnmodifiableMap(User::getId, User::getName));
```

---

## 集合工具类

### Collections 工具类

```java
import java.util.Collections;

List<Integer> list = new ArrayList<>(Arrays.asList(3, 1, 2));

// 排序
Collections.sort(list);      // [1, 2, 3]
Collections.reverse(list);   // [3, 2, 1]

// 查找
int max = Collections.max(list);            // 3
int min = Collections.min(list);            // 1
int index = Collections.binarySearch(list, 2); // 二分查找（需先排序）

// 填充/替换
Collections.fill(list, 0);            // [0, 0, 0]
Collections.replaceAll(list, 0, 1);   // 替换所有0为1

// 空集合/单元素集合（返回不可变的共享实例，避免 new）
List<String> empty = Collections.emptyList();
Map<String, String> emptyMap = Collections.emptyMap();
List<String> single = Collections.singletonList("A");

// 线程安全包装（所有操作加锁，并发高时优先用 ConcurrentHashMap/CopyOnWriteArrayList）
List<String> syncList = Collections.synchronizedList(new ArrayList<>());
Map<String, String> syncMap = Collections.synchronizedMap(new HashMap<>());

// 只读视图
List<String> readOnly = Collections.unmodifiableList(list);
// readOnly.add("A"); // UnsupportedOperationException
```

### Arrays 工具类

```java
import java.util.Arrays;

// 数组 → List（固定长度，不能 add/remove，只能 set）
String[] arr = {"A", "B", "C"};
List<String> list = Arrays.asList(arr);

// 数组 → 可变 List / Stream
List<String> mutable = new ArrayList<>(Arrays.asList(arr));
List<String> upper = Arrays.stream(arr)
        .map(String::toUpperCase)
        .toList();

// 复制数组
String[] copy = Arrays.copyOf(arr, 2);           // ["A", "B"]
String[] copy2 = Arrays.copyOfRange(arr, 1, 3);  // ["B", "C"]

// 排序
int[] nums = {3, 1, 2};
Arrays.sort(nums); // [1, 2, 3]

// 二分查找（数组必须有序）
int index = Arrays.binarySearch(nums, 2); // 1

// 填充
Arrays.fill(nums, 0); // [0, 0, 0]

// 比较
int[] arr1 = {1, 2, 3};
int[] arr2 = {1, 2, 3};
boolean equal = Arrays.equals(arr1, arr2); // true

// 转字符串
String str = Arrays.toString(arr1); // "[1, 2, 3]"
```

---

## 集合初始化的多种方式

```java
// 1. 传统方式（适合大部分场景）
List<String> list1 = new ArrayList<>();
list1.add("A");
list1.add("B");

// 2. 转为可变列表（快速创建可变列表并初始化值）
List<String> list3 = new ArrayList<>(Arrays.asList("A", "B"));
list3.add("C"); // ✅ 可以添加

// 3. Arrays.asList（固定长度，不能add/remove，只能set）
List<String> list2 = Arrays.asList("A", "B", "C");
// list2.add("D"); // UnsupportedOperationException

// 4. List.of / Set.of / Map.of（不可变）
List<String> list4 = List.of("A", "B", "C");
Set<String> set = Set.of("A", "B", "C");
Map<String, String> map = Map.of("key1", "value1", "key2", "value2");
// list4.add("D"); // UnsupportedOperationException

// 5. Stream
List<String> list5 = Stream.of("A", "B", "C")
        .collect(Collectors.toList());
// 或直接收集为不可变列表
List<String> list6 = Stream.of("A", "B", "C").toList();
```

---

## 选型速查表

| 需求                      | 推荐集合                        | 备注                     |
| ------------------------- | ------------------------------- | ------------------------ |
| 通用的有序列表            | `ArrayList`                     | 默认首选，随机访问快     |
| 频繁头部插入/删除         | `LinkedList`                    | 其余场景仍优先 ArrayList |
| 线程安全 List（读多写少） | `CopyOnWriteArrayList`          | 监听器、缓存场景         |
| 线程安全 List（读写均衡） | `Collections.synchronizedList`  | 所有操作加锁             |
| 去重、快速判断存在        | `HashSet`                       | 无序                     |
| 去重且保持插入顺序        | `LinkedHashSet`                 | 遍历顺序 = 插入顺序      |
| 线程安全 Set              | `ConcurrentHashMap.newKeySet()` | 并发去重首选             |
| 键值对存取                | `HashMap`                       | 默认首选，无序           |
| 键值对且保持插入顺序      | `LinkedHashMap`                 | JSON 序列化、LRU 基础    |
| 线程安全 Map              | `ConcurrentHashMap`             | 不允许 null 键值         |
| 栈 / 普通队列（单线程）   | `ArrayDeque`                    | 比 LinkedList 更快       |
| 按优先级取元素            | `PriorityQueue`                 | Top-K、任务调度          |
| 生产者-消费者（跨线程）   | `LinkedBlockingQueue`           | 记得设置容量上限         |
| 常量/返回给调用方的集合   | `List.of` / `Map.copyOf` 等     | 不可变，防篡改           |
