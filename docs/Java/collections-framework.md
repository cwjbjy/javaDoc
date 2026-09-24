# Java 集合框架

> **说明**：本文代码均为**示例片段**（Illustrative fragment，演示语法与用法，未逐一编译运行）；需要让读者知道结果的地方，以代码注释标注可观测结果。版本基线为 **Java 17**。

## 目录

- [如何选择集合](#如何选择集合)
- [List](#list)
  - [ArrayList](#arraylist)
  - [LinkedList](#linkedlist)
  - [CopyOnWriteArrayList](#copyonwritearraylist)
  - [List 创建方式对比](#list-创建方式对比)
- [Set](#set)
  - [HashSet](#hashset)
  - [LinkedHashSet](#linkedhashset)
  - [TreeSet](#treeset)
  - [ConcurrentHashMap.newKeySet()](#concurrenthashmapnewkeyset)
  - [ConcurrentSkipListSet](#concurrentskiplistset)
  - [不可变 Set](#不可变-set)
- [Map](#map)
  - [HashMap](#hashmap)
  - [LinkedHashMap](#linkedhashmap)
  - [TreeMap](#treemap)
  - [ConcurrentHashMap](#concurrenthashmap)
  - [ConcurrentSkipListMap](#concurrentskiplistmap)
  - [不可变 Map](#不可变-map)
- [Queue](#queue)
  - [ArrayDeque](#arraydeque)
  - [LinkedBlockingQueue](#linkedblockingqueue)
- [枚举专用集合](#枚举专用集合)
- [集合工具类](#集合工具类)
- [Arrays 工具方法](#arrays-工具方法)
- [排序](#排序)
- [Stream Collectors 与集合互转](#stream-collectors-与集合互转)

## 如何选择集合

先按"需求 → 推荐实现"定位，再到对应小节看 API 与陷阱：

| 需求                                | 推荐实现                        |
| ----------------------------------- | ------------------------------- |
| 有序可重复列表、随机访问            | `ArrayList`                     |
| 频繁头尾插入删除 / 双端队列         | `LinkedList`                    |
| 读多写少的线程安全列表              | `CopyOnWriteArrayList`          |
| 去重、不关心顺序                    | `HashSet`                       |
| 去重且保持插入顺序                  | `LinkedHashSet`                 |
| 去重且按大小排序、需要范围查询      | `TreeSet`                       |
| 线程安全去重集合                    | `ConcurrentHashMap.newKeySet()` |
| 线程安全且排序的集合                | `ConcurrentSkipListSet`         |
| 键值映射、快速查找                  | `HashMap`                       |
| 键值映射且保持插入顺序 / LRU        | `LinkedHashMap`                 |
| 键值映射且按 key 排序、需要范围查询 | `TreeMap`                       |
| 线程安全键值映射                    | `ConcurrentHashMap`             |
| 线程安全且按 key 排序               | `ConcurrentSkipListMap`         |
| 枚举作键 / 元素                     | `EnumMap` / `EnumSet`           |
| 不可变常量集合                      | `List.of` / `Set.of` / `Map.of` |
| 栈 / 队列（单线程）                 | `ArrayDeque`                    |
| 生产者-消费者阻塞队列               | `LinkedBlockingQueue`           |

## List

特点：有序、可重复

### ArrayList

有了数组，为什么还要有集合？

- **长度可变**：数组长度创建后即固定，集合可随增删动态伸缩；
- **统一而丰富的 API**：数组的插入、删除、查找、排序都要自己写，集合提供了 `add`/`remove`/`contains`/`sort`/`subList` 等现成方法；
- **多样的数据结构**：数组只有"连续存储 + 下标访问"一种形态，集合族提供 List/Set/Map/Queue/Deque 等多种结构，匹配不同访问模式（去重、映射、排队、排序等）。

> 关于"类型"：数组和**泛型集合**通常都只存一种元素类型（`List<String>` 仍是单一类型）。集合的价值不在"能装多种类型"，而在可变长度、统一 API 与丰富结构；泛型则把类型检查提前到编译期。

因此，在实际编码中，除非有明确的性能或内存顾虑，否则优先使用集合。

#### 常用 API

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

#### subList 视图陷阱

`subList(from, to)` 返回的是父列表的**视图**（backed by 原列表），不是独立副本——读写视图都会作用到父列表。三条必须记住的规则：

- **父列表结构性修改后，视图可能失效**：对父列表 `add`/`remove`（改变 size）后再操作子列表，可能抛 `ConcurrentModificationException`；
- **需要长期保存 / 异步传递 / 分批处理时，做防御性拷贝**：用 `new ArrayList<>(source.subList(from, to))` 脱钩；
- **校验下标与分页参数**：保证 `0 <= from <= to <= size()`，对空列表、页码越界、页大小非正数先兜底。

```java
List<Integer> source = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5));

// 分页取第 2 页（每页 2 条）：下标 [from, to)
int page = 2, size = 2;
int from = Math.max(0, (page - 1) * size);
int to = Math.min(source.size(), from + size);      // 夹逼，防止 to 越界
List<Integer> pageView = source.subList(from, to);  // 视图：[2, 3]

// 父列表结构性修改后，继续用旧视图有风险
source.add(6);
// pageView.add(99); // 可能抛 ConcurrentModificationException

// 要独立副本（长期保存 / 传给异步任务 / 分批处理）：
List<Integer> snapshot = new ArrayList<>(source.subList(from, to)); // 与父列表脱钩
```

#### 遍历中安全删除（fail-fast）

**fail-fast（快速失败）** = 一旦发现集合被"绕过迭代器"偷偷修改了，迭代器**立刻崩溃报错**，不带着脏数据继续跑，避免后续出现更难排查的问题。

因此 for 循环里直接 `list.remove(...)` 是错的，应改用 `removeIf`。

```java
List<Integer> list = new ArrayList<>(List.of(1, 2, 3, 4));

// ❌ 错误：for-each 中直接删除，下一步迭代触发 fail-fast
// for (Integer n : list) {
//     if (n % 2 == 0) list.remove(n); // 抛 ConcurrentModificationException
// }

// ✅ 推荐：removeIf（Java 8+，最简洁）
list.removeIf(n -> n % 2 == 0); // 移除偶数，剩下 [1, 3]
```

> `CopyOnWriteArrayList` 是例外：它的迭代器是创建时的**快照**，遍历期间其他线程的修改既不可见、也不会抛 CME（详见上文 CopyOnWriteArrayList 小节）。

#### 装箱陷阱：remove(int) 与 remove(Object)

对 `List<Integer>` 调用 `remove(1)` 时，编译器优先匹配 `remove(int index)`（按下标删除），而不是 `remove(Object)`（按值删除）。要按值删除必须显式装箱。

```java
List<Integer> nums = new ArrayList<>(List.of(10, 20, 30));
nums.remove(1);                    // 命中 remove(int index)：删除下标 1 的元素 20 → [10, 30]

List<Integer> vals = new ArrayList<>(List.of(10, 20, 30));
vals.remove(Integer.valueOf(10));  // 命中 remove(Object)：删除"值 10" → [20, 30]
```

### LinkedList

LinkedList 基于**双向链表**实现，同时实现了 `List` 和 `Deque` 接口，因此既可以作为列表使用，也可以作为栈/队列使用。

| 特性            | 说明                                                                                              |
| --------------- | ------------------------------------------------------------------------------------------------- |
| 底层结构        | 双向链表（每个节点存储 prev、next 指针和元素值）                                                  |
| 随机访问 get(i) | O(n)，需从头/尾遍历到指定位置                                                                     |
| 头部/尾部操作   | O(1)，直接操作头/尾节点指针                                                                       |
| 中间插入/删除   | O(n)（定位）+ O(1)（修改指针），但定位后修改本身很快                                              |
| 内存开销        | 每个元素额外存储两个指针（prev/next），比 ArrayList 的紧凑数组更占内存                            |
| 缓存友好性      | 差（节点在堆上分散分配，CPU 缓存命中率低）                                                        |
| 线程安全        | 否，需要线程安全可用 `Collections.synchronizedList(new LinkedList<>())` 或 `CopyOnWriteArrayList` |
| null 元素       | 允许                                                                                              |

#### 完整 API

**List 接口方法（与 ArrayList 相同）**

| 方法                                  | 说明                   |
| ------------------------------------- | ---------------------- |
| `add(E e)` / `add(int i, E e)`        | 尾部/指定位置插入      |
| `get(int i)`                          | 按下标获取（O(n)）     |
| `set(int i, E e)`                     | 修改指定位置元素       |
| `remove(int i)` / `remove(Object o)`  | 按下标/按值删除        |
| `size()` / `isEmpty()` / `contains()` | 基本操作               |
| `indexOf(Object o)` / `lastIndexOf()` | 查找元素位置（需遍历） |

**Deque 接口方法（LinkedList 特有优势）**

| 方法                | 等价队列方法            | 说明                       |
| ------------------- | ----------------------- | -------------------------- |
| `addFirst(e)`       | `offerFirst(e)`         | 头部插入（栈 push / 队首） |
| `addLast(e)`        | `offerLast(e)`          | 尾部插入（队尾）           |
| `removeFirst()`     | `pollFirst()`           | 头部删除（栈 pop / 出队）  |
| `removeLast()`      | `pollLast()`            | 尾部删除                   |
| `getFirst()`        | `peekFirst()`           | 获取头部（不删除）         |
| `getLast()`         | `peekLast()`            | 获取尾部（不删除）         |
| `push(e)` / `pop()` | 同 addFirst/removeFirst | 栈操作                     |

> **企业开发建议**：需要栈/队列行为时优先用 **ArrayDeque**（数组实现，缓存友好，性能优于 LinkedList）。LinkedList 的实际使用场景很窄，仅在需要频繁在两端操作且元素量大、或明确需要链表结构时才考虑。

```java
import java.util.LinkedList;

// 作为双端队列使用
LinkedList<String> deque = new LinkedList<>();
deque.addFirst("A");  // 头部插入
deque.addLast("B");   // 尾部插入
deque.addFirst("C");  // ["C", "A", "B"]

String first = deque.removeFirst(); // "C"
String last = deque.removeLast();   // "B"

// 作为栈使用
LinkedList<String> stack = new LinkedList<>();
stack.push("top");    // 入栈
String top = stack.pop(); // 出栈
```

### CopyOnWriteArrayList

CopyOnWriteArrayList 是线程安全的 List，采用写时复制策略：每次写操作（add/set/remove）都会复制一份底层数组，读操作则完全不加锁。

- **适用场景**：读多写少，如事件监听器列表、配置缓存、观察者模式；
- **不适用**：元素量大或写操作频繁（复制数组开销高），此时考虑 `Collections.synchronizedList` 或加锁；
- **注意**：迭代器是创建时的快照，遍历期间其他线程的修改不可见，也不会抛 `ConcurrentModificationException`。

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

### List 创建方式对比

创建 List 时，可变性与"是否回写源"差别很大。不可变 List（`List.of` / `List.copyOf` / `stream().toList()`）适合用作常量配置、方法返回值（防止调用方篡改）；可变 List（`new ArrayList<>(...)` / `Arrays.asList(...)`）适合需要后续增删改的场景。

| 创建方式                   | 底层           | 能否增删  | `set` 改          | 允许 null    | 与源的关系       |
| -------------------------- | -------------- | --------- | ----------------- | ------------ | ---------------- |
| `new ArrayList<>(coll)`    | 独立数组       | ✅        | ✅                | ✅           | 完全独立（快照） |
| `Arrays.asList(arr)`       | **包装原数组** | ❌ 抛 UOE | ✅ **会写回 arr** | ✅           | 双向视图         |
| `List.of(...)`             | 不可变         | ❌        | ❌                | ❌ 抛 NPE    | 独立快照         |
| `List.copyOf(coll)`        | 不可变         | ❌        | ❌                | ❌           | 独立快照         |
| `stream().toList()`（16+） | 不可变         | ❌        | ❌                | ✅ 允许 null | 独立             |

> UOE = `UnsupportedOperationException`，NPE = `NullPointerException`。

不可变 List 用法示例：

```java
// 场景 1：定义常量配置（防止被意外修改）
public class Config {
    public static final List<String> VALID_LEVELS = List.of("INFO", "WARN", "ERROR");
    public static final List<String> SUPPORTED_OS = List.of("Windows", "Linux", "macOS");
}

// 场景 2：方法返回不可变集合（防止调用方篡改内部数据）
public List<String> getStatusCodes() {
    List<String> internal = new ArrayList<>(List.of("200", "404", "500"));
    return List.copyOf(internal); // 返回副本，调用方无法修改内部状态
}

// 场景 3：试图修改会抛异常
List<String> immutable = List.of("A", "B", "C");
// immutable.add("D");      // UnsupportedOperationException
// immutable.remove("A");   // UnsupportedOperationException
// immutable.set(0, "X");   // UnsupportedOperationException
```

三个高频坑：

```java
// 坑 1：Arrays.asList 是"定长视图"，增删抛异常，但 set 会写回原数组
Integer[] arr = {1, 2, 3};
List<Integer> fixed = Arrays.asList(arr);
// fixed.add(4);   // UnsupportedOperationException
fixed.set(0, 9);   // ✅ 允许改；此时 arr[0] 也变成了 9

// 坑 2：基本类型数组装箱——得到 size()==1 的 List<int[]>，而非 List<Integer>
int[] raw = {1, 2, 3};
List<int[]> wrong = Arrays.asList(raw);                  // wrong.size() == 1（整个数组当成 1 个元素）
List<Integer> right = Arrays.stream(raw).boxed().toList(); // [1, 2, 3]

// 坑 3：List.of / List.copyOf 不允许 null
// List.of("a", null); // NullPointerException

// 需要"基于现有数据但可增删"时，复制成 ArrayList：
List<Integer> mutable = new ArrayList<>(Arrays.asList(1, 2, 3));
mutable.add(4); // ✅ [1, 2, 3, 4]
```

---

## Set

特点：元素不重复。按遍历顺序分三类：

- **无序**：`HashSet` —— 不保证任何顺序，性能最好（O(1)）；
- **有序（insertion-ordered）**：`LinkedHashSet` —— 按**插入先后**遍历；
- **排序（sorted）**：`TreeSet` / `ConcurrentSkipListSet` —— 按**元素大小**（`Comparable`/`Comparator`）遍历，支持范围查询。

> 关键区分：**"有序"指保持放入的先后，"排序"指按元素大小排列**，二者不是一回事。

### HashSet

HashSet 用于存储不重复的元素，且不保证顺序，常用于需要快速去重、快速包含性检查的场景。

#### 常用 API

| 方法                         | 说明                                          |
| ---------------------------- | --------------------------------------------- |
| `add(E e)`                   | 添加元素，若已存在则返回 false                |
| `remove(Object o)`           | 移除元素，存在则返回 true                     |
| `removeAll(Collection c)`    | 移除当前集合中那些也在 c 中存在的元素（差集） |
| `retainAll(Collection c)`    | 只保留同时存在于当前集合和 c 中的元素（交集） |
| `removeIf(Predicate filter)` | 移除所有满足条件的元素                        |
| `contains(Object o)`         | 判断是否包含该元素                            |
| `addAll(Collection c)`       | 批量添加元素（并集）                          |
| `forEach(Consumer action)`   | 遍历每个元素                                  |
| `size()`                     | 返回元素个数                                  |
| `isEmpty()`                  | 判断是否为空                                  |
| `clear()`                    | 清空所有元素                                  |

```java
// 基本操作
Set<Long> ids = new HashSet<>();
ids.add(1L);
ids.add(2L);
ids.add(1L); // 重复，不会添加
System.out.println(ids.size()); // 2
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

LinkedHashSet 继承自 HashSet，内部额外维护了一个双向链表记录插入顺序，因此遍历顺序与插入顺序一致。常用 API 与 HashSet 完全相同。

**适用场景**：需要去重且必须保持插入顺序，例如 JSON 字段去重、按提交顺序展示的唯一记录。

```java
Set<String> visited = new LinkedHashSet<>();
visited.add("/home");
visited.add("/list");
visited.add("/home"); // 重复，不会添加，也不改变原有顺序

System.out.println(visited); // [/home, /list]
```

### TreeSet

TreeSet 是**排序** Set：基于红黑树，元素按 `Comparable` 自然顺序或构造时传入的 `Comparator` 排列，增删查为 O(log n)。它实现 `NavigableSet`，提供范围查询能力——这是它区别于 `HashSet`/`LinkedHashSet` 的核心价值。**不允许 null**（无法比较）。

**API 与 HashSet 的关系**：TreeSet 包含 HashSet 的所有 API，但**额外**实现了 `NavigableSet` 接口，提供了范围查询方法：

| 方法                            | 说明                          |
| ------------------------------- | ----------------------------- |
| `first()` / `last()`            | 最小 / 最大元素               |
| `floor(e)` / `ceiling(e)`       | <= e 的最大值 / >= e 的最小值 |
| `higher(e)` / `lower(e)`        | 严格 > e / 严格 < e           |
| `subSet(from, to)`              | 范围视图 [from, to)           |
| `headSet(to)` / `tailSet(from)` | < to / >= from                |
| `pollFirst()` / `pollLast()`    | 获取并移除最小 / 最大元素     |

```java
// 自然顺序（元素实现 Comparable）
TreeSet<Integer> nums = new TreeSet<>(List.of(5, 1, 3, 1));
// nums = [1, 3, 5]（自动排序 + 去重）

// 自定义排序：按字符串长度（Comparator.comparingInt 详见下文"排序"节）
TreeSet<String> byLength = new TreeSet<>(Comparator.comparingInt(String::length));
byLength.addAll(List.of("a", "bbb", "cc"));
// byLength = ["a", "cc", "bbb"]（按长度 1, 2, 3 排序）

// NavigableSet 范围查询
nums.first();      // 1（最小）
nums.last();       // 5（最大）
nums.floor(4);     // 3（<= 4 的最大值）
nums.ceiling(4);   // 5（>= 4 的最小值）
nums.higher(3);    // 5（严格 > 3）
nums.lower(3);     // 1（严格 < 3）
nums.subSet(2, 5); // [3]（范围视图 [2, 5)）
nums.headSet(3);   // [1]（< 3）
nums.tailSet(3);   // [3, 5]（>= 3）
```

**适用**：需要去重且始终有序、或频繁做"最接近值 / 范围"查询（如排行榜名次、时间窗口）。仅需去重、不关心顺序时用 `HashSet`（O(1) 更快）。

### ConcurrentHashMap.newKeySet()

用来快速创建一个线程安全的 Set。它的底层直接由 ConcurrentHashMap 支持，因此具备高并发性能。

**原理**：Set 本质是"只有 key、没有业务 value 的 Map"。`newKeySet()` 内部创建了一个 `ConcurrentHashMap`，并为每个加入 Set 的元素放入同一个固定的占位 value（可以把它理解为“存在”标记）。因此，元素只要是 Map 的 key，就表示它在 Set 中：

| Set 操作      | 内部实现                                              |
| ------------- | ----------------------------------------------------- |
| `add(e)`      | `map.put(e, PRESENT)`（PRESENT 是一个固定的占位对象） |
| `remove(e)`   | `map.remove(e)`                                       |
| `contains(e)` | `map.containsKey(e)`                                  |
| `size()`      | `map.size()`                                          |

所以它不是给普通 `HashSet` 外面简单套一把锁；多个线程可同时对不同 key 进行操作，底层通过 CAS、细粒度同步等机制保证单个操作的线程安全。遍历时使用的是**弱一致性迭代器**：不会抛 `ConcurrentModificationException`，但迭代过程中不保证一定看见其他线程刚加入或删除的每一个元素。

**null 处理**：与 `ConcurrentHashMap` 一致，**不允许 null 元素**（`add(null)` 抛 NPE）。

```java
import java.util.*;
import java.util.concurrent.*;

// 8 个线程同时写入：每个线程都写入 0 ~ 49_999。
// 总共发起 400_000 次 add，但 Set 最终只能保留 50_000 个不同元素。
// 以下代码放在声明了 throws Exception 的 main 方法（或自行处理异常）中运行。

// 创建由 ConcurrentHashMap 支撑的线程安全 Set；元素本身就是 Map 的 key。
Set<Integer> processedIds = ConcurrentHashMap.newKeySet();
int threadCount = 8;                  // 并发工作线程数
int idsPerThread = 50_000;            // 每个线程尝试加入的 id 数量

// 固定大小线程池：最多同时运行 8 个提交的任务。
ExecutorService pool = Executors.newFixedThreadPool(threadCount);

// ready 初值为 8：每个任务就绪后减 1；主线程等待它变为 0。
CountDownLatch ready = new CountDownLatch(threadCount);
// start 初值为 1：所有任务先在此等待；主线程 countDown 后统一放行。
CountDownLatch start = new CountDownLatch(1);
// 保存 Future，后续可等待每个任务真正结束，并接收任务中的异常。
List<Future<?>> futures = new ArrayList<>();

for (int t = 0; t < threadCount; t++) {
    futures.add(pool.submit(() -> {
        ready.countDown();             // 报告“我已创建并准备完毕”
        start.await();                 // 如果计数还是 1，就在这里等待
        for (int id = 0; id < idsPerThread; id++) {
            // add 是原子操作：首次加入返回 true；重复加入返回 false，不会产生重复元素。
            processedIds.add(id);
        }
        return null;                   // Callable<Void> 的正常结束标记
    }));
}

ready.await();                         // 主线程确认 8 个任务都到达“起跑线”
start.countDown();                     // 把计数从 1 减到 0，让所有正在 start.await() 等待的工作线程都会被唤醒并继续执行
for (Future<?> future : futures) {
    future.get();                      // 等待该任务结束；任务抛异常时会在这里暴露
}
pool.shutdown();                       // 不再接受新任务；已提交的任务已全部结束

// 400,000 次并发 add 完成后，每个 id 只保留一份，而不是 400,000 份。
System.out.println(processedIds.size());        // 50000
System.out.println(processedIds.contains(123)); // true：任一线程都已加入过 123
```

上例中，8 个线程对相同的 50,000 个 key 并发调用 `add`。尽管调用次数是 400,000，完成后集合大小稳定为 50,000，且每个 id 只存在一份；这就是它作为并发去重 Set 的直接表现。若改用 `HashSet` 并让多个线程这样写，结果不可靠，甚至可能出现结构损坏或异常。

**注意**：单次 `add`、`remove`、`contains` 都是线程安全的，但多个调用拼成的业务步骤不是一个整体原子操作。对于“仅当第一次加入成功时才执行”的需求，不要先 `contains` 再 `add`，直接检查 `add` 的返回值：`if (processedIds.add(id)) { /* 第一次处理该 id */ }`。更复杂的“检查 + 修改”逻辑可改用 `ConcurrentHashMap.compute` / `merge`，或按业务需要加锁。

常用 API 与 HashSet 相同（add / remove / contains / size），常用于并发去重、在线用户集合、已处理任务记录等场景。

### ConcurrentSkipListSet

需要**线程安全 + 排序**的 Set 时用 `ConcurrentSkipListSet`：基于跳表实现，相当于 `TreeSet` 的并发版。元素按 `Comparable` 自然顺序或构造时传入的 `Comparator` 排列，增删查为 O(log n)；同样实现 `NavigableSet`，支持 `first/last/floor/ceiling/higher/lower/subSet` 等范围查询。

```java
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentSkipListSet;

// 并发 + 排序：如实时排行榜、按时间戳排序的事件集合
NavigableSet<Long> timestamps = new ConcurrentSkipListSet<>();
timestamps.add(ts);            // 线程安全，且按 ts 排序
timestamps.first();            // 最小（最早）
timestamps.last();             // 最大（最晚）
timestamps.tailSet(t0);        // >= t0 的子集（范围查询）
```

**选型提示**：只需"并发去重"、不关心顺序时用 `ConcurrentHashMap.newKeySet()`（更快）；既要并发又要**始终有序 / 范围查询**时才用 `ConcurrentSkipListSet`（跳表有多层指针，内存开销更大）。

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

##### 基本操作

```java
Map<String, String> map = new HashMap<>();
map.put("1", "One");
map.put("2", "Two");
map.put("3", "Three");

String value = map.get("1");        // "One"
map.remove("2");                     // 移除 key="2" 的键值对
boolean has = map.containsKey("3"); // true
```

##### 安全取值

```java
// 取不到时返回默认值，避免 NPE
String name = userMap.getOrDefault("name", "未知用户");

// key 不存在时才执行 lambda 计算并存入；已存在则直接返回旧值、lambda 不执行（典型场景：缓存）
// 未命中：cacheMap 无 "user:1001" → 调用 findFromDB 查库 → 存入并返回结果
// 命中：  cacheMap 有 "user:1001" → 直接返回缓存值，findFromDB 不被调用
User user = cacheMap.computeIfAbsent("user:1001", key -> userService.findFromDB(key));
```

##### 遍历

```java
// 方式一：for-each entrySet
for (Map.Entry<String, String> entry : userMap.entrySet()) {
    System.out.println(entry.getKey() + " -> " + entry.getValue());
}

// 方式二：forEach（更简洁）
userMap.forEach((key, value) -> System.out.println(key + " -> " + value));
```

##### 合并与更新

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

##### computeIfAbsent：分组与建树

`computeIfAbsent` 最经典的用法是"**按 key 初始化容器**"：key 不存在时先放一个空容器（如 `ArrayList`）再往里塞元素，省去"先判空 → new → put"的样板。

**分组（一对多）**：

```java
// 把订单按用户分组
Map<Long, List<Order>> ordersByUser = new HashMap<>();
for (Order o : orders) {
    ordersByUser.computeIfAbsent(o.userId(), k -> new ArrayList<>()).add(o);
}
// 等价流式写法：orders.stream().collect(Collectors.groupingBy(Order::userId))
```

**平铺记录 → 父子树（两阶段构建）**：

```java
record Node(long id, long parentId, String name) {}
// TreeNode：树节点，持有原记录与子节点列表
class TreeNode {
    final Node node;
    final List<TreeNode> children = new ArrayList<>();
    TreeNode(Node node) { this.node = node; }
}

List<Node> rows = List.of(
    new Node(1, 0, "根"),
    new Node(2, 1, "子A"),
    new Node(3, 1, "子B"),
    new Node(4, 2, "孙A1"));

// 阶段一：按 parentId 分组——computeIfAbsent 负责"没有列表就 new 一个"
Map<Long, List<Node>> childrenByParent = new HashMap<>();
for (Node n : rows) {
    childrenByParent.computeIfAbsent(n.parentId(), k -> new ArrayList<>()).add(n);
}
// childrenByParent = {0=[根], 1=[子A, 子B], 2=[孙A1]}

// 阶段二：借助 id 索引，把分组结果装配成父子树
Map<Long, TreeNode> index = new HashMap<>();
for (Node n : rows) index.put(n.id(), new TreeNode(n)); // 先全部建节点
TreeNode root = null;
for (Node n : rows) {
    TreeNode cur = index.get(n.id());
    if (n.parentId() == 0) root = cur;                 // parentId=0 视为根
    else index.get(n.parentId()).children.add(cur);    // 挂到父节点
}
// 装配后的树形：
// 根(1)
// ├── 子A(2)
// │   └── 孙A1(4)
// └── 子B(3)
```

> **computeIfAbsent 两个坑**：① mapping 函数内**不要再修改同一个 map**（递归更新可能死循环或抛 `IllegalStateException`）；② mapping 函数**返回 null 时不会建立映射**（下次仍会重算）。

#### 作为 key 的合约：equals / hashCode

当对象被用作 `HashMap` 的 key、`HashSet` 的元素，或 `Stream.distinct()` 的输入时，集合需要回答一个问题：**两个对象是否代表同一个业务对象？** `equals` / `hashCode` 就是这个判断的规则。

两者分工不同，HashMap 会先用快的 `hashCode` 缩小查找范围，再用准确的 `equals` 做最终确认：

| 方法                 | 用来做什么                                                           | 在 HashMap / HashSet 中的作用                                                            |
| -------------------- | -------------------------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `hashCode()`         | 为对象计算一个整数哈希值；它不是唯一身份证，不同对象也可能得到相同值 | 根据哈希值快速定位到可能存放该 key 的桶，减少需要逐个比较的对象数量                      |
| `equals(Object obj)` | 按业务规则判断两个对象是否相等                                       | 在同一桶中确认候选对象是否真的是同一个 key；相等则覆盖 value（Map）或拒绝重复加入（Set） |

例如用订单号作为 key 时，`hashCode()` 像先按订单号把记录送到某个抽屉，`equals()` 像在抽屉里核对订单号是否真的相同。哈希值相同不代表对象一定相等，所以不能只依赖 `hashCode()`。

若不重写这两个方法，继承自 `Object` 的默认语义是“是否为内存中的同一个实例”（近似 `==`）。字段看起来相同、但分别 `new` 出来的对象仍是两个不同的 key：

```java
// 没有重写 equals/hashCode 的普通类：仍使用 Object 的“同一实例”语义。
class User {
    private final Long id;
    private final String name;

    User(Long id, String name) {
        this.id = id;
        this.name = name;
    }
}

User a = new User(1L, "张三");
User b = new User(1L, "张三");

// 未重写 equals/hashCode 时：a.equals(b) 为 false
// 因而把 a、b 放进 HashSet，集合大小会是 2。
```

只有业务上要求“指定字段相同就算同一个 key”时，才需要定义自己的规则。例如订单号相同就应代表同一订单：

```java
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

// 示例片段：OrderKey 是不可变的，且只按 orderId 判断是否为同一订单。
final class OrderKey {
    private final Long orderId;

    OrderKey(Long orderId) {
        this.orderId = orderId;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;                 // 同一实例必然相等
        if (!(obj instanceof OrderKey other)) return false;
        return Objects.equals(this.orderId, other.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId);                 // 与 equals 使用同一字段
    }
}

Map<OrderKey, String> orders = new HashMap<>();
orders.put(new OrderKey(1001L), "已支付");
orders.put(new OrderKey(1001L), "已发货"); // 覆盖同一个业务 key 的 value

System.out.println(orders.size());                    // 1
System.out.println(orders.get(new OrderKey(1001L)));  // 已发货
```

通常不必手写：`String`、`Integer`、`LocalDate` 等 JDK 值对象已经实现了规则；`record` 会基于全部组件自动生成一致的 `equals` / `hashCode`，很适合作为 key。普通类也可让 IDE 生成，或使用 Lombok 的 `@EqualsAndHashCode`。只有需要**只按部分字段**（如只按 `orderId`，不按状态、金额）判断相等时，才应明确选择这些字段并自行配置生成规则。

必须遵守两条约束：

- `a.equals(b)` 为 `true` 时，`a.hashCode() == b.hashCode()` 必须成立；反过来不要求。否则相等对象会落入不同哈希桶，导致查找和去重失效。
- key 放入 `HashMap` / `HashSet` 后，不要修改参与 `equals` / `hashCode` 的字段；否则它可能仍留在旧桶，却按新哈希值查找，形成“存得进、取不出”。因此长期 key 优先使用 `record`、`String`、包装类型或专门的不可变 key 类。

> 同理，`HashSet` 去重与 `list.stream().distinct()` 也依赖 `equals` / `hashCode`：不重写就按对象引用判重，"看起来一样"的元素不会被去掉。

### LinkedHashMap

LinkedHashMap 继承自 HashMap，常用 API 与 HashMap 完全相同，在 HashMap 的基础上内部额外维护了一个双向链表，用于记录元素的**插入顺序**。因此遍历 LinkedHashMap 时，顺序与插入顺序一致。

**适用场景**：缓存实现（LRU 算法）

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

### TreeMap

TreeMap 是**排序** Map：按 key 的自然顺序（`Comparable`）或构造时传入的 `Comparator` 排列，增删查为 O(log n)，实现 `NavigableMap` 提供范围查询。**key 不允许 null**（无法比较），value 可以为 null。

```java
// 按 key 自然顺序排列
TreeMap<String, Integer> scores = new TreeMap<>();
scores.put("bob", 85);
scores.put("alice", 92);
scores.put("cathy", 78);
// 遍历按 key 排序：alice=92, bob=85, cathy=78

// NavigableMap 范围查询
scores.firstKey();               // "alice"（最小 key）
scores.lastKey();                // "cathy"（最大 key）
scores.floorKey("b");            // "alice"（<= "b" 的最大 key）
scores.ceilingKey("b");          // "bob"（>= "b" 的最小 key）
scores.subMap("alice", "cathy"); // {alice=92, bob=85}（范围视图 [from, to)）
scores.headMap("bob");           // {alice=92}（key < "bob"）
scores.tailMap("bob");           // {bob=85, cathy=78}（key >= "bob"）
```

**适用**：需要按 key 有序遍历、或做范围/邻近查询（如按时间戳索引、分数段统计、字典序前缀）。仅做键值查找、不关心顺序时用 `HashMap`（O(1) 更快）。

### ConcurrentHashMap

ConcurrentHashMap 常用 API 与 HashMap 完全相同，区别在于：

- **线程安全**：并发读写可安全执行；针对**同一 key** 的写操作（`putIfAbsent`、`compute*`、`merge` 等）具备原子性。但跨 key 操作、`containsKey` 后再 `put` 等多步复合逻辑不具备整体原子性（见下文"原子性与复合操作陷阱"）
- **不允许 null**：键和值都不允许为 null
- **迭代器 fail-safe**：不会抛出 `ConcurrentModificationException`（弱一致性，见下文"迭代器弱一致"）

#### 原子性与复合操作陷阱

**什么是原子操作**：原子 = 不可分割——一个操作要么整个完成、要么整个没做，中间状态对其他线程不可见，也不会"做到一半被别的线程插队"。

ConcurrentHashMap 对**单个 key** 的写操作（`put` / `putIfAbsent` / `replace` / `compute*` / `merge`）都是原子的：它在锁住该 key 所在的桶（bucket）的范围内完成"读—改—写"，因此同一 key 的并发写会串行化，不同 key 之间仍可并行。

但原子性是"按方法、按单 key"的——**把多个原子方法拼起来的复合逻辑，整体并不原子**。最常见的误区是 check-then-act（先查再改）：

```java
// ❌ 非原子：containsKey 和 put 是两个独立的原子调用，中间可能被其他线程插入
if (!map.containsKey(key)) {
    map.put(key, value);
}
```

```
线程 A                          线程 B
containsKey("k") → false
                                containsKey("k") → false
put("k", vA)
                                put("k", vB)   ← 覆盖 A，A 的写入丢失
结果：{k: vB}（A 却以为自己写成功了）
```

✅ 解法：改用"本身就完成 查+改 的一步式原子方法"，把两步并成一步：

```java
map.putIfAbsent(key, value);                // 不存在才放入
map.computeIfAbsent(key, k -> newValue());  // 不存在才计算并放入
map.merge(key, 1, Integer::sum);            // 按单 key 累加/聚合
```

```
线程 A                          线程 B
putIfAbsent("k", vA)  ← 锁住 k 的桶，"查+写"一步完成
                                putIfAbsent("k", vB) ← 等锁 → 发现已存在 → 放弃
结果：{k: vA}（确定，无丢失）
```

**两个边界**：

- **跨 key 不原子**：原子性只作用于单个 key（锁对应的桶）。同时改两个 key、或"遍历所有 key 做统计"这类跨 key 逻辑不具备整体原子性，需要外部加锁或其他同步手段。

  典型例子是"转账"——从 A 扣款、给 B 入账是两次各自原子的操作，但合起来不原子，中间状态会被其他线程看到：

  ```java
  // ❌ 跨 key 复合操作：扣 A + 加 B 不是一个整体
  balances.merge(from, -amount, Integer::sum); // 第 1 步：A 扣款（本身原子）
  balances.merge(to,   amount, Integer::sum);  // 第 2 步：B 入账（本身原子）
  ```

  ```
  初始：A=100, B=100（总额应为 200）
  线程 T1（A→B 转 100）                 线程 T2（此刻读总额）
  merge(A, -100) → A=0
                                       sum = A + B = 0 + 100 = 100 ❌ 少了 100
  merge(B, +100) → B=200
                                       sum = A + B = 0 + 200 = 200 ✅
  ```

  在第 1、2 步之间，钱已从 A 扣除却还没进 B，此刻读到的总额凭空少一份。✅ ConcurrentHashMap 自身给不了"两个 key 一起改"的原子性，需要对这段逻辑加同一把外部锁（如 `synchronized`），或改用支持多键原子/事务的结构。

- **回调里别做重活**：`compute` / `computeIfAbsent` / `merge` 的回调执行期间持有桶锁——不要做慢 IO、不要递归更新同一个 map、不要依赖复杂副作用，否则拖慢并发甚至死锁。

#### 基本操作

```java
ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
map.put("key1", "value1");
String value = map.get("key1");
map.remove("key1");
```

#### 条件写入（putIfAbsent / replace）

"满足条件才写"的一步式原子方法，正是替代上文 check-then-act 的正解：

```java
// putIfAbsent：key 不存在时才放入
map.putIfAbsent("key1", "value1");

// replace：key 存在时才替换
map.replace("key1", "newValue");
map.replace("key1", "oldValue", "newValue"); // 只有旧值匹配才替换（CAS 语义）
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

#### 迭代器弱一致（fail-safe）

ConcurrentHashMap 的迭代器是**弱一致**的：不抛 `ConcurrentModificationException`，但只反映"创建时某个时间点"的状态，遍历期间的并发写入不保证可见；`size()` / `containsValue()` 在并发下也是近似值。适合"大致浏览"，不适合"精确遍历"。

```java
ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
map.put("a", 1);
map.put("b", 2);

// ✅ 边遍历边改，不会抛 CME（换成 HashMap，同样的循环会立即抛 ConcurrentModificationException）
for (String key : map.keySet()) {
    if (key.equals("a")) {
        map.put("c", 3);   // 遍历途中新增一个 key
    }
    System.out.println(key);
}
// 本次输出是否包含 "c" 不确定：可能是 [a, b]，也可能是 [a, b, c] 或 [a, c, b]
```

**为什么**：迭代器基于创建时的桶状态快照向前推进，遍历途中新增的 `c` 可能落在已扫过的桶里（看不到），也可能落在未扫的桶里（看得到）——这就是"弱一致"：不报错，但也不保证看到最新写入。

> 对比：`HashMap` 的迭代器是 **fail-fast** 的，同样的"遍历中修改"会立刻抛 `ConcurrentModificationException`；`ConcurrentHashMap` 用弱一致换来了"遍历不被并发修改打断"，代价是遍历结果只是近似快照，不能依赖它做精确统计（此时 `size()` 同样是近似值）。

### ConcurrentSkipListMap

需要**线程安全 + 按 key 排序**时用 `ConcurrentSkipListMap`：基于跳表实现，并发读写仍保持 key 有序，O(log n)，相当于 `TreeMap` 的并发版。同样实现 `NavigableMap`，支持 `subMap/headMap/tailMap/floorKey/ceilingKey` 等范围查询。

```java
import java.util.concurrent.ConcurrentSkipListMap;

// 并发 + 排序：如实时排行榜（score → playerId）、按时间戳的范围索引
ConcurrentSkipListMap<Long, String> timeline = new ConcurrentSkipListMap<>();
timeline.put(ts, eventId);   // 线程安全，且按 ts 排序
timeline.firstKey();         // 最早时间戳
timeline.subMap(t1, t2);     // [t1, t2) 范围内的事件（范围查询）
```

> 选择提示：并发但无需排序 → `ConcurrentHashMap`；并发且要按 key 排序/范围查询 → `ConcurrentSkipListMap`。

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
// Map.entry("a", 1) → "a"=1
// Map.entry("b", 2) → "b"=2
// ofEntries 把它们合成一个 Map
Map<String, Integer> big = Map.ofEntries(
    Map.entry("a", 1),
    Map.entry("b", 2));
// big = {a=1, b=2}
```

---

## Queue

### ArrayDeque

ArrayDeque 是基于循环数组实现的双端队列，可作栈可作队列。它不是线程安全的，且不允许 null 元素。

#### 栈操作（后进先出）

| 方法        | 说明                   |
| ----------- | ---------------------- |
| `push(E e)` | 压栈（添加到头部）     |
| `pop()`     | 弹栈（移除并返回头部） |
| `peek()`    | 查看栈顶（不移除）     |

#### 队列操作（先进先出）

| 方法         | 说明                   |
| ------------ | ---------------------- |
| `offer(E e)` | 入队（添加到尾部）     |
| `poll()`     | 出队（移除并返回头部） |
| `peek()`     | 查看队首（不移除）     |

#### 双端队列操作（两端均可操作）

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

### LinkedBlockingQueue

LinkedBlockingQueue 是一个线程安全的队列。容量默认是 `Integer.MAX_VALUE`，因此通常可以认为它是"无界"的，但需根据环境提供合理限制，否则容易造成内存泄露。

内部使用两把独立的 `ReentrantLock`——`putLock`（入队锁）与 `takeLock`（出队锁）——来提高并发度：生产者与消费者可以一定程度并行，但多个生产者之间仍竞争 `putLock`、多个消费者之间仍竞争 `takeLock`。

#### 常用API与阻塞语义

同一件事（入队/出队），`BlockingQueue` 提供了四种"满/空时怎么办"的语义，选错会导致线程卡死或丢任务：

| 方法                                                | 队列满/空时的行为               | 返回/抛出                       |
| --------------------------------------------------- | ------------------------------- | ------------------------------- |
| `put(E e)` / `take()`                               | **一直阻塞**，直到有空位/有元素 | 被中断抛 `InterruptedException` |
| `offer(E e)` / `poll()`                             | **立即返回**，不阻塞            | 满返回 `false` / 空返回 `null`  |
| `offer(E e, timeout, unit)` / `poll(timeout, unit)` | 最多等待 timeout，超时放弃      | 超时返回 `false` / `null`       |
| `add(E e)`                                          | 满时**抛异常**                  | `IllegalStateException`         |
| `size()`                                            | 返回当前元素个数                | `int`（并发下非精确）           |
| `remainingCapacity()`                               | 返回剩余容量                    | `int`                           |

- **生产者-消费者**：用 `put`/`take`，让线程在满时自然等待；
- **需要快速失败或降级**：用 `offer`/`poll`（可带超时），拿不到就走兜底逻辑，不要阻塞主流程。

#### 生产者-消费者模型示例

```java
import java.util.concurrent.LinkedBlockingQueue;

LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>(3);

// 生产者
new Thread(() -> {
    try {
        for (int i = 1; i <= 5; i++) {
            String item = "task-" + i;
            queue.put(item); // 队列满则阻塞等待
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

运行结果：

```
生产者：task-1
生产者：task-2
生产者：task-3 // 队列已满，生产者阻塞
消费者：task-1
生产者：task-4 // 消费后队列有空位，生产者继续
消费者：task-2
```

#### 容量、最大线程数与拒绝策略

阻塞队列的容量不是孤立参数——它和线程池的 `corePoolSize`、`maximumPoolSize`、`RejectedExecutionHandler` 一起决定系统过载时的行为。`ThreadPoolExecutor` 提交任务的决策流：

```
新任务
  │
  ▼
线程数 < corePoolSize ? ──是──▶ 新建线程执行
  │否
  ▼
入队成功（队列未满）? ──是──▶ 排队等待
  │否（队列满）
  ▼
线程数 < maximumPoolSize ? ──是──▶ 新建线程执行
  │否
  ▼
触发拒绝策略 RejectedExecutionHandler
  ├─ AbortPolicy（默认）：抛 RejectedExecutionException
  ├─ CallerRunsPolicy：由提交任务的线程自己执行（形成背压）
  ├─ DiscardPolicy：静默丢弃
  └─ DiscardOldestPolicy：丢弃队首最老任务，再尝试提交
```

**关键结论**：`LinkedBlockingQueue` 默认容量是 `Integer.MAX_VALUE`（近似无界）。无界队列会让"入队"永远成功，于是 `maximumPoolSize` **形同虚设**、线程数停在 core，任务无限堆积最终 **OOM**。因此生产环境务必给队列设置**合理容量**，并配合 `CallerRunsPolicy` 等拒绝策略形成背压。

> 线程池各参数（core/max/keepAlive/线程工厂）与在 Spring Boot 中的配置方式，见 [Java 多线程基础](multithreading-basics.md) 与 [Spring Boot 多线程指南](../Spring/spring-boot-multithreading-guide.md)，本文不展开。

---

## 枚举专用集合

当 key（或元素）是**枚举**时，优先用 `EnumMap`/`EnumSet`：底层用数组（EnumMap）或位向量（EnumSet）实现，比 `HashMap`/`HashSet` 更快、更省内存，迭代顺序即枚举声明顺序。

**EnumMap 常用 API**：

| 方法                                   | 说明                                      |
| -------------------------------------- | ----------------------------------------- |
| `new EnumMap<>(Class<K> keyType)`      | 用枚举 Class 创建（key 类型在构造时固定） |
| `put(K key, V value)` / `get(K key)`   | 放入 / 取出                               |
| `remove(K key)` / `containsKey(K key)` | 移除 / 判断 key 是否存在                  |
| `size()` / `isEmpty()` / `clear()`     | 元素个数 / 是否为空 / 清空                |

> EnumMap 的 key **不允许 null**，但 value 允许 null。迭代顺序即枚举声明顺序。

**EnumSet 常用 API**：

| 方法                                   | 说明                                |
| -------------------------------------- | ----------------------------------- |
| `EnumSet.of(E... elements)`            | 创建包含指定元素的集合              |
| `EnumSet.allOf(Class<E> elementType)`  | 包含所有枚举值的集合                |
| `EnumSet.noneOf(Class<E> elementType)` | 空集合                              |
| `EnumSet.range(E from, E to)`          | 声明顺序区间 `[from, to]`（含两端） |
| `EnumSet.complementOf(EnumSet<E> s)`   | 补集：所有不在 `s` 中的枚举值       |
| `EnumSet.copyOf(Collection<E> c)`      | 从集合复制出 EnumSet                |
| `add(E)` / `remove(E)` / `contains(E)` | 标准 Set 操作                       |

> EnumSet 的位向量实现使得 `contains`、`add`、`remove` 等操作为 O(1)，且内存占用极小（每个枚举值占 1 bit）。不允许 null 元素。

```java
enum Status { NEW, DOING, DONE }

// EnumMap：创建时用 Class 固定 key 类型，key 不允许 null
Map<Status, Integer> countByStatus = new EnumMap<>(Status.class);
countByStatus.put(Status.NEW, 3);
countByStatus.put(Status.DONE, 5);

// EnumSet：状态标志集合，位运算实现，高效
Set<Status> flags = EnumSet.of(Status.NEW, Status.DOING);
Set<Status> all = EnumSet.allOf(Status.class);
Set<Status> range = EnumSet.range(Status.NEW, Status.DONE); // 声明顺序区间
```

> 适用：以枚举做分组统计、状态机标志位、权限集合等。key 类型在创建时即固定，且不允许 null key。

---

## 集合工具类

`Collections` 提供了一系列静态方法，用于对各种集合进行通用操作。它不是集合本身，而是"集合的瑞士军刀"。

### 工厂方法

创建特定用途的集合，常用于方法返回值（避免返回 null）或常量定义。

| 方法                                                       | 说明                          | 典型用途                      |
| ---------------------------------------------------------- | ----------------------------- | ----------------------------- |
| `emptyList()` / `emptySet()` / `emptyMap()`                | 返回空集合（不可变）          | 方法无结果时返回，替代 `null` |
| `singletonList(e)` / `singleton(e)` / `singletonMap(k, v)` | 返回单元素不可变集合          | 需要只包含一个元素的常量集合  |
| `nCopies(n, elem)`                                         | 返回 n 个相同元素的不可变列表 | 初始化固定数量的默认值        |

```java
// 空集合：替代返回 null，避免调用方 NPE
public List<User> findUsers(String query) {
    if (query == null || query.isEmpty()) {
        return Collections.emptyList(); // 而不是 null
    }
    // ...
}

// 单元素集合
List<String> onlyOne = Collections.singletonList("admin");
// onlyOne.add("user"); // UnsupportedOperationException

Set<String> singleRole = Collections.singleton("ADMIN");
// singleRole.add("USER"); // UnsupportedOperationException

Map<String, Object> config = Collections.singletonMap("timeout", 30);
// config.put("retry", 3); // UnsupportedOperationException

// n 个相同元素
List<String> defaults = Collections.nCopies(5, "N/A"); // ["N/A", "N/A", "N/A", "N/A", "N/A"]
```

### 只读包装

返回原集合的**只读视图**，任何修改操作（add/remove/set）都会抛 `UnsupportedOperationException`。

| 方法                           | 说明                       |
| ------------------------------ | -------------------------- |
| `unmodifiableList(list)`       | 返回 List 的只读视图       |
| `unmodifiableSet(set)`         | 返回 Set 的只读视图        |
| `unmodifiableMap(map)`         | 返回 Map 的只读视图        |
| `unmodifiableCollection(coll)` | 返回 Collection 的只读视图 |

> **与 `List.of()` 的区别**：
>
> - `List.of(...)` → 创建**真正不可变**的集合（底层就是常量，原集合不存在）
> - `unmodifiableList(mutableList)` → 返回**只读视图**（底层集合仍可被原引用修改，只是不能通过这个视图修改）

```java
List<String> mutable = new ArrayList<>(List.of("A", "B", "C"));
List<String> readOnly = Collections.unmodifiableList(mutable);

// readOnly.add("D"); // UnsupportedOperationException

// 但原集合仍可改，视图会反映变化
mutable.add("D");
System.out.println(readOnly.size()); // 4，视图反映了变化
```

### 同步包装

`Collections.synchronizedXxx(...)` 返回一个**线程安全的包装视图**：它给每个方法套上同一把锁，使任意单个方法调用互斥。

| 方法                     | 说明                     |
| ------------------------ | ------------------------ |
| `synchronizedList(list)` | 返回线程安全的 List 视图 |
| `synchronizedSet(set)`   | 返回线程安全的 Set 视图  |
| `synchronizedMap(map)`   | 返回线程安全的 Map 视图  |

> **遍历时必须手动加锁**：包装器只保证"单个方法"同步，而一次迭代是多次方法调用的组合，整体并不原子。遍历时若不手动 `synchronized (集合) { ... }`，仍会抛 `ConcurrentModificationException`。

```java
List<String> syncList = Collections.synchronizedList(new ArrayList<>());
// 遍历必须手动同步在 syncList 上
synchronized (syncList) {
    for (String s : syncList) {
        // ...
    }
}
```

> 高并发场景优先用 `java.util.concurrent` 的并发集合（如 `ConcurrentHashMap`、`CopyOnWriteArrayList`），性能远好于"全表一把锁"的同步包装。

### 操作类方法

| 功能     | 方法                                      | 说明                            |
| -------- | ----------------------------------------- | ------------------------------- |
| 集合操作 | `reverse`、`shuffle`、`rotate`、`swap`    | 反转、随机打乱、旋转、交换      |
| 查找     | `max`、`min`、`binarySearch`、`frequency` | 最大/最小值、二分查找、出现次数 |
| 修改     | `fill`、`copy`、`replaceAll`              | 填充、复制、替换                |

```java
import java.util.Collections;

List<Integer> list = new ArrayList<>(List.of(3, 1, 2));

// 集合操作
Collections.reverse(list);              // 反转：[2, 1, 3]
Collections.shuffle(list);              // 随机打乱：顺序随机
Collections.swap(list, 0, 2);           // 交换下标 0 和 2

// 查找
int max = Collections.max(list);        // 最大值
int min = Collections.min(list);        // 最小值
int count = Collections.frequency(list, 2); // 元素 2 出现的次数
int index = Collections.binarySearch(list, 2); // 二分查找（需先排序）

// 修改
Collections.fill(list, 0);              // 全部填充为 0
Collections.replaceAll(list, 0, 1);     // 替换所有 0 为 1
```

---

## Arrays 工具方法

`java.util.Arrays` 提供了一系列操作数组的静态方法，在数组与集合互转、数组拷贝/比较/填充等场景下非常常用。

### 数组与集合互转

| 方法                                  | 说明                       | 注意事项                                   |
| ------------------------------------- | -------------------------- | ------------------------------------------ |
| `Arrays.asList(arr)`                  | 返回数组的**定长列表视图** | 改互相影响；不能 add/remove（UOE）         |
| `Arrays.stream(arr)`                  | 返回数组的 Stream          | 可收集为 List/Set 等                       |
| `list.toArray(new Type[0])`           | 集合转数组                 | 推荐 `new Type[0]` 而非 `new Type[size()]` |
| `new ArrayList<>(Arrays.asList(arr))` | 创建独立的可变列表副本     | 与原数组脱钩                               |

```java
String[] arr = {"A", "B", "C"};

// 数组 → 定长列表视图（改互相影响）
List<String> view = Arrays.asList(arr);
view.set(0, "X");        // arr[0] 也变成 "X"
// view.add("D");         // UnsupportedOperationException

// 数组 → 独立可变列表
List<String> mutable = new ArrayList<>(Arrays.asList(arr));
mutable.add("D");         // 可以 add

// 数组 → Stream → 收集为 List
List<String> list = Arrays.stream(arr).collect(Collectors.toList());

// 基本类型数组的坑
int[] nums = {1, 2, 3};
// Arrays.asList(nums) → 得到 List<int[]>，size()==1，不是 List<Integer>！
// 正确做法：
List<Integer> intList = Arrays.stream(nums).boxed().collect(Collectors.toList());

// 集合 → 数组
String[] out = list.toArray(new String[0]); // 推荐写法
```

### 数组操作

| 方法                            | 说明                               |
| ------------------------------- | ---------------------------------- |
| `Arrays.copyOf(arr, newLen)`    | 拷贝数组，可扩容或截断             |
| `Arrays.equals(a, b)`           | 比较两个数组内容是否相等（浅比较） |
| `Arrays.deepEquals(a, b)`       | 多维数组深比较                     |
| `Arrays.fill(arr, val)`         | 将数组所有元素填充为指定值         |
| `Arrays.sort(arr)`              | 对数组原地排序                     |
| `Arrays.sort(arr, comp)`        | 自定义排序                         |
| `Arrays.binarySearch(arr, key)` | 二分查找（需先排序）               |

```java
int[] src = {3, 1, 4, 1, 5};

// 拷贝/扩容
int[] copy = Arrays.copyOf(src, 3);    // [3, 1, 4]（截断）
int[] expanded = Arrays.copyOf(src, 8); // [3, 1, 4, 1, 5, 0, 0, 0]（扩容补 0）

// 比较
int[] a = {1, 2, 3};
int[] b = {1, 2, 3};
Arrays.equals(a, b);          // true

// 填充
int[] filled = new int[5];
Arrays.fill(filled, 7);       // [7, 7, 7, 7, 7]

// 排序
Arrays.sort(src);             // [1, 1, 3, 4, 5]
Arrays.sort(src, Collections.reverseOrder()); // 降序（仅对象数组）

// 二分查找（需先排序）
int idx = Arrays.binarySearch(src, 3); // 找到返回下标，否则返回负数
```

> **基本类型数组排序**：`Arrays.sort(int[])` 使用双轴快排，对对象数组使用 TimSort（稳定）。基本类型降序需手动实现或用包装类型。

---

## 排序

Java 8+ 对排序 API 做了大幅简化：**`list.sort(comparator)` 取代了 `Collections.sort()`**，同时 `Comparator` 引入了一系列静态工厂方法，让多字段排序变得简洁。

### list.sort vs Collections.sort

| 维度     | `Collections.sort(list, comp)` | `list.sort(comp)` |
| -------- | ------------------------------ | ----------------- |
| 引入版本 | Java 1.2                       | Java 8            |
| 推荐程度 | 可用但冗余                     | **推荐**          |
| 内部实现 | Java 8+ 委托给 list.sort()     | TimSort           |

```java
// ❌ 旧写法
Collections.sort(list, String::compareTo);

// ✅ 新写法
list.sort(String::compareTo);
list.sort(Comparator.naturalOrder());
list.sort(Comparator.reverseOrder());
```

### Comparator 静态工厂方法

**Comparator（比较器）** 是一个函数式接口，定义了两个对象的排序顺序：`int compare(T o1, T o2)`，返回负数 / 零 / 正数表示 o1 < o2 / o1 == o2 / o1 > o2。

**传统写法**需要自己实现比较逻辑：

```java
// 传统 lambda 写法：按年龄排序
users.sort((a, b) -> Integer.compare(a.getAge(), b.getAge()));

// 传统写法：按姓名排序
users.sort((a, b) -> a.getName().compareTo(b.getName()));
```

**Java 8 引入静态工厂方法**后，排序变得**声明式**：你只需告诉系统"按什么字段排序"，不用关心底层比较逻辑。

**核心思想**：`Comparator.comparing(keyExtractor)` = **提取 key，按 key 排序**。

```java
// 按年龄排序：提取 age 字段，按 age 排序
users.sort(Comparator.comparing(User::age));

// 按姓名排序：提取 name 字段，按 name 排序
users.sort(Comparator.comparing(User::name));
```

**comparingInt / comparingLong / comparingDouble** 是基本类型特化版本，避免装箱（`int` → `Integer`），性能更好。当字段是基本类型时优先使用。

这是 Java 8 引入的**排序核心 API**，企业开发高频使用：

| 方法                                                | 说明                                     |
| --------------------------------------------------- | ---------------------------------------- |
| `Comparator.comparing(keyExtractor)`                | 按某字段排序                             |
| `Comparator.comparingInt/Long/Double(extractor)`    | 按基本类型字段排序（避免装箱，性能更好） |
| `Comparator.comparing(keyExtractor, keyComparator)` | 按某字段排序，自定义比较器               |
| `.thenComparing(...)`                               | 次要排序字段（多字段排序）               |
| `.reversed()`                                       | 反转排序顺序                             |
| `.nullsFirst(comparator)`                           | null 排最前                              |
| `.nullsLast(comparator)`                            | null 排最后                              |

```java
record User(String name, int age, String city) {}

List<User> users = List.of(
    new User("Alice", 30, "Beijing"),
    new User("Bob", 25, "Shanghai"),
    new User("Charlie", 30, "Beijing"),
    new User(null, 28, "Shenzhen")
);

// 单字段排序
users.sort(Comparator.comparing(User::age));
// [Bob(25), null(28), Alice(30), Charlie(30)]

// 多字段排序：先按 age，age 相同按 name
users.sort(Comparator.comparing(User::age)
                     .thenComparing(User::name));
// [Bob(25), null(28), Alice(30), Charlie(30)]
// 注意：name 为 null 的排最后（null 默认比任何值大）

// 逆序
users.sort(Comparator.comparing(User::age).reversed());
// [Alice(30), Charlie(30), null(28), Bob(25)]

// null 处理：显式指定 null 排最后
users.sort(Comparator.comparing(User::name,
                                Comparator.nullsLast(String::compareTo)));
// [Alice, Bob, Charlie, null]

// 组合：先按 city，再按 age 降序，name 为 null 排最后
users.sort(Comparator.comparing(User::city)
                     .thenComparing(Comparator.comparing(User::age).reversed())
                     .thenComparing(User::name, Comparator.nullsLast(String::compareTo)));
```

> **要点**：
>
> - `comparing` 提取的字段如果是对象类型且可能为 null，用 `comparing(field, Comparator.nullsLast(...))` 显式处理
> - `reversed()` 是对整个 Comparator 反转，不是对单个字段
> - `thenComparing` 可链式调用多个，实现任意层级的多字段排序

---

## Stream Collectors 与集合互转

企业开发里，"把 List 聚合成 Map / 分组 / 拼接"大多用 Stream 的 `Collectors` 完成，它和前面的 `computeIfAbsent` 分组是"流式 vs 命令式"的两种写法。

| Collector                | 作用              | 结果类型                |
| ------------------------ | ----------------- | ----------------------- |
| `toList()` / `toSet()`   | 收集为 List / Set | `List` / `Set`          |
| `toMap(k, v)`            | 收集为 Map        | `Map`                   |
| `groupingBy(classifier)` | 按某字段分组      | `Map<K, List<V>>`       |
| `partitioningBy(pred)`   | 按布尔条件二分    | `Map<Boolean, List<V>>` |
| `mapping(f, downstream)` | 分组后再映射元素  | 依下游而定              |
| `joining(delimiter)`     | 拼接字符串        | `String`                |

```java
import java.util.stream.Collectors;

// 基础数据
List<User> users = List.of(
    new User(1L, "Alice",   "开发", 28),
    new User(2L, "Bob",     "开发", 30),
    new User(3L, "Charlie", "测试", 25),
    new User(4L, "Diana",   "测试", 32),
    new User(5L, "Eve",     "产品", 22)
);

// toList / toSet
List<String> names = users.stream().map(User::getName).collect(Collectors.toList());
// names = [Alice, Bob, Charlie, Diana, Eve]

// JDK 16+ 可简写：users.stream().map(User::getName).toList()（返回不可变 List）

// toMap：id → User
Map<Long, User> byId = users.stream()
    .collect(Collectors.toMap(User::getId, u -> u));
// byId = {1=User(Alice), 2=User(Bob), 3=User(Charlie), 4=User(Diana), 5=User(Eve)}

// groupingBy：按部门分组（等价于 computeIfAbsent 手写分组）
Map<String, List<User>> byDept = users.stream()
    .collect(Collectors.groupingBy(User::getDept));
// byDept = {开发=[User(Alice), User(Bob)], 测试=[User(Charlie), User(Diana)], 产品=[User(Eve)]}

// partitioningBy：按条件二分（成年 / 未成年）
Map<Boolean, List<User>> split = users.stream()
    .collect(Collectors.partitioningBy(u -> u.getAge() >= 18));
// split = {true=[User(Alice,28), User(Bob,30), User(Charlie,25), User(Diana,32), User(Eve,22)]}（全部 >= 18）

// mapping：分组后只取名字
Map<String, List<String>> namesByDept = users.stream()
    .collect(Collectors.groupingBy(User::getDept,
             Collectors.mapping(User::getName, Collectors.toList())));
// namesByDept = {开发=[Alice, Bob], 测试=[Charlie, Diana], 产品=[Eve]}

// joining：拼接
String csv = users.stream().map(User::getName).collect(Collectors.joining(", "));
// csv = "Alice, Bob, Charlie, Diana, Eve"
```

**`toMap` 的两个坑**（高频事故）：

```java
// 坑 1：key 冲突抛 IllegalStateException——需提供合并函数
Map<String, User> firstByDept = users.stream()
    .collect(Collectors.toMap(User::getDept, u -> u, (a, b) -> a)); // 保留先出现的

// 坑 2：value 为 null 抛 NullPointerException——toMap 不接受 null value
// 若 value 映射函数（如 User::getManager）可能返回 null，先 filter 掉，或改用 groupingBy / 手动 put
```

> Stream / Lambda 的完整用法（中间操作、并行流、自定义 Collector 等）见 [Lambda 与 Stream API](stream.md)，本文只覆盖"与集合互转"这一层。
