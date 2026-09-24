## ADDED Requirements

### Requirement: 顶部选型导航与全文目录

`docs/Java/collections-framework.md` SHALL 在一级标题 `# Java 集合框架` 之下、`## List` 之前新增两块导航：① 覆盖全部 H2/H3 的可跳转**目录（TOC）**；② **"如何选择集合"决策表**，以"需求 → 推荐实现"的形式覆盖 List/Set/Map/Queue 的常见诉求（如"去重且保持插入顺序 → LinkedHashSet""并发 + 排序 → ConcurrentSkipListMap""读多写少的线程安全 List → CopyOnWriteArrayList"）。TOC 锚点 SHALL 与中文标题匹配且无断裂。

#### Scenario: 读者先选型再查阅

- **WHEN** 读者带着"我该用哪个集合"的问题打开文档
- **THEN** 能在正文之前通过决策表定位到推荐实现，并可经 TOC 直接跳转到对应小节

### Requirement: 修正"为什么用集合"的不准确表述

指南开头 SHALL 删除"数组只能存储单一类型的数据，而集合可以存储多种类型的数据"这一不准确对比，改为强调集合的真正优势：**可变长度**、**统一而丰富的 API**、**多样的数据结构**；并 SHALL 澄清"泛型集合同样通常是单元素类型，其价值在编译期类型检查，而非能存储多种类型"。SHALL 保留"除非有明确性能/内存顾虑，否则优先使用集合"的结论。

#### Scenario: 读者不被"集合能存多种类型"误导

- **WHEN** 读者阅读开头对数组与集合的对比
- **THEN** 理解到集合的优势是可变长度/统一 API/多样结构，且知道 `List<String>` 这样的泛型集合仍是单一元素类型

### Requirement: List 创建方式对比

指南 SHALL 新增"List 的创建方式对比"小节，用表格对比 `new ArrayList<>(coll)`、`Arrays.asList(arr)`、`List.of(...)`、`List.copyOf(coll)`、`stream().toList()` 五个维度：底层实现、能否增删、`set` 是否回写源、是否允许 null、与源数组/集合的关系。SHALL 明确标注三个高频坑：对 `Arrays.asList` 结果调用 `add/remove` 抛 `UnsupportedOperationException`；`Arrays.asList(int[])` 因装箱得到 `size()==1` 的 `List<int[]>`；`List.of(...)` 含 null 元素抛 `NullPointerException`。

#### Scenario: 读者选对创建方式并规避坑

- **WHEN** 读者需要一个可增删的列表或一个不可变常量列表
- **THEN** 能据表选择 `new ArrayList<>(...)`（可变）或 `List.of/copyOf`（不可变），并知道 `Arrays.asList` 是定长视图、基本类型数组会踩装箱坑

### Requirement: subList 视图陷阱

ArrayList 小节 SHALL 新增"subList 视图陷阱"内容，说明：`subList` 返回的是父列表的**视图**，父列表发生结构性修改后继续操作子列表可能抛 `ConcurrentModificationException`；需要长期保存、异步传递或分批处理时 SHALL 使用 `new ArrayList<>(source.subList(from, to))` 做防御性拷贝；SHALL 给出下标边界、空列表、分页参数（页码/页大小）的校验要点。

#### Scenario: 读者安全地使用 subList 分页

- **WHEN** 读者用 `subList` 对列表分页并可能修改父列表或把子列表传出方法
- **THEN** 知道要先校验边界、并在需要独立副本时用 `new ArrayList<>(subList(...))` 拷贝，避免视图失效异常

### Requirement: 遍历中安全删除与 fail-fast

List 章 SHALL 新增"遍历中安全删除 / fail-fast"内容：解释在 for-each 循环中直接调用 `list.remove` 会因 fail-fast 机制抛 `ConcurrentModificationException` 的原因，并给出正确做法——`Iterator.remove()` 或 `Collection.removeIf(predicate)`。SHALL 指明 `CopyOnWriteArrayList` 迭代器是快照、遍历期间修改不抛 CME 但也不可见。

#### Scenario: 读者不再因边遍历边删除而崩溃

- **WHEN** 读者需要在遍历列表时删除满足条件的元素
- **THEN** 使用 `removeIf` 或 `Iterator.remove`，而不是在 for-each 中直接 `list.remove`

### Requirement: 装箱陷阱 remove(int) 与 remove(Object)

List 章 SHALL 新增"装箱陷阱"内容，说明对 `List<Integer>` 调用 `remove(1)` 匹配的是 `remove(int index)`（按下标删除），而非按值删除；要按值删除 SHALL 使用 `remove(Integer.valueOf(1))`。SHALL 给出可观测结果注释以区分两种写法的差异。

#### Scenario: 读者区分按下标与按值删除

- **WHEN** 读者对 `List<Integer>` 想删除"值为 1"的元素
- **THEN** 写 `remove(Integer.valueOf(1))` 而非 `remove(1)`，并理解后者删的是下标 1 的元素

### Requirement: 有序与排序术语及 TreeSet

Set 章 SHALL 澄清"有序（insertion-ordered，如 `LinkedHashSet`）"与"排序（sorted，如 `TreeSet`）"的区别，并新增 **TreeSet** 小节：说明其基于红黑树、按 `Comparable` 或传入的 `Comparator` 排序、增删查为 O(log n)、**不允许 null**；SHALL 给出 `NavigableSet` 范围方法示例（`first/last/floor/ceiling/higher/lower/subSet/headSet/tailSet`）。

#### Scenario: 读者需要自动排序且可范围查询

- **WHEN** 读者需要一个去重且始终按大小排序、并能取"最接近某值"的结构
- **THEN** 选择 `TreeSet` 并使用 `floor/ceiling/subSet` 等方法，而非依赖 `LinkedHashSet` 的插入顺序

### Requirement: TreeMap 与并发排序结构

Map 章 SHALL 新增 **TreeMap** 小节（按 key 排序、`NavigableMap` 范围方法 `subMap/headMap/tailMap/floorKey/ceilingKey`、key 不允许 null），并 SHALL 简述 **ConcurrentSkipListMap / ConcurrentSkipListSet** 作为"并发 + 排序"的选择（跳表实现、线程安全、适合排行榜或按时间戳的范围索引）。Set 章 SHALL 相应补充 `ConcurrentSkipListSet` 的引用。

#### Scenario: 读者在并发下仍需有序

- **WHEN** 读者需要线程安全且按 key/元素排序、支持范围查询的结构
- **THEN** 选择 `ConcurrentSkipListMap/Set`，而不是 `ConcurrentHashMap`（无序）或 `TreeMap`（非线程安全）

### Requirement: computeIfAbsent 分组与平铺记录建父子树

Map 章 SHALL 新增内容，演示 `computeIfAbsent` 的"按 key 初始化容器"分组模式，并给出**平铺记录 → 父子树**的两阶段构建：以 `record Node(long id, long parentId, String name)` 的一组平铺数据为例，阶段一用 `childrenByParent.computeIfAbsent(n.parentId(), k -> new ArrayList<>()).add(n)` 分组（结果以注释给出，如 `{0=[根], 1=[子A, 子B], 2=[孙A1]}`），阶段二自顶向下装配成树（结果树形以注释给出）。SHALL 为既有"缓存版 computeIfAbsent"示例补充命中/未命中的可观测语义，并 SHALL 指出两个坑：mapping 函数内不得修改同一个 map；mapping 函数返回 null 时不建立映射。

#### Scenario: 读者把平铺数据组装成树

- **WHEN** 读者拿到带 `id/parentId` 的平铺记录列表需要还原层级
- **THEN** 用 `computeIfAbsent` 先按 `parentId` 分组再装配父子关系，并能从注释看出每一步的结果结构

### Requirement: 作为 Map key 的 equals/hashCode 合约

Map 章 SHALL 新增"作为 key 的合约：equals/hashCode"小节，说明当对象被用作 `HashMap` 的 key、`HashSet` 的元素或 `Stream.distinct()` 的输入时：① key 参与哈希计算后 SHALL NOT 再被修改（否则定位错乱）；② `equals` 与 `hashCode` SHALL 保持一致（相等的对象必须有相同 hashCode）；③ SHALL NOT 把可变 DTO 当作长期缓存的 key。SHALL 给出"改写 key 字段后 get 不到"的可观测后果说明。

#### Scenario: 读者用自定义对象做 key

- **WHEN** 读者把自定义对象放入 `HashMap` 作 key 或 `HashSet` 去重
- **THEN** 确保正确重写 `equals/hashCode`、并保证 key 入桶后不可变，避免"存得进、取不出"

### Requirement: 并发集合的边界

ConcurrentHashMap 小节 SHALL 新增/深化"并发集合的边界"内容，明确：① 单个方法线程安全 SHALL NOT 被理解为"读取—判断—写入"复合操作自动原子（用 `❌ if(!containsKey) put` 与 `✅ putIfAbsent/computeIfAbsent/merge` 对照）；② `computeIfAbsent/merge` 适合按单 key 聚合；③ `compute/computeIfAbsent` 的回调中 SHALL NOT 做慢 IO、递归更新同一 map、或依赖复杂副作用；④ 迭代器为弱一致（fail-safe，不抛 CME，但不保证反映最新写入）；⑤ `Collections.synchronizedList` 包装的列表在遍历时 SHALL 手动 `synchronized(list)`，否则仍可能抛 CME。

#### Scenario: 读者避免并发下的复合操作竞态

- **WHEN** 读者在多线程中对 `ConcurrentHashMap` 做"不存在才放入"或计数累加
- **THEN** 使用 `putIfAbsent/computeIfAbsent/merge` 等原子方法，而不是自己写 check-then-act；并知道 `synchronizedList` 遍历要手动加锁

### Requirement: EnumMap 与 EnumSet

指南 SHALL 新增对 **EnumMap / EnumSet** 的简述：二者以枚举为键/元素、底层用位向量或数组实现、性能优于 `HashMap/HashSet`，适用于以枚举为 key 的分组、状态标志集合等场景；SHALL 指出其 key 类型在创建时即固定、不允许 null key。

#### Scenario: 读者以枚举作键

- **WHEN** 读者的 Map key 或 Set 元素是枚举类型
- **THEN** 优先使用 `EnumMap/EnumSet` 以获得更好的性能与类型约束

### Requirement: LinkedBlockingQueue 阻塞与失败语义

LinkedBlockingQueue 小节 SHALL 新增一张"阻塞与失败语义"对照表，覆盖 `put(e)`/`take()`（满或空时**阻塞**，响应中断抛 `InterruptedException`）、`offer(e)`/`poll()`（**立即返回** `false`/`null`，不阻塞）、`offer(e,t,u)`/`poll(t,u)`（等待至多超时时间后返回 `false`/`null`）、`add(e)`（满时抛 `IllegalStateException`）。SHALL 说明"阻塞版适合生产者-消费者、非阻塞/超时版适合需要快速失败或降级的场景"。

#### Scenario: 读者选对入队/出队方法

- **WHEN** 读者在队列满/空时希望"等待"或"立即失败"
- **THEN** 据表选择 `put/take`（等待）或 `offer/poll`（立即失败）或带超时版本，并知道各自的中断与返回语义

### Requirement: 队列容量、最大线程数与拒绝策略的过载行为

指南 SHALL 说明 `LinkedBlockingQueue` 的容量如何与线程池的 `corePoolSize`、`maximumPoolSize`、`RejectedExecutionHandler` 共同决定系统过载行为：给出一张 `ThreadPoolExecutor` 任务提交决策流（ASCII 图）——线程数 < core 则建线程，否则入队；队列满且线程数 < max 则建线程，否则触发拒绝策略；SHALL 明确"无界队列（默认 `Integer.MAX_VALUE`）会使 `maximumPoolSize` 失效、任务无限堆积导致 OOM，因此生产环境应显式设置容量"。线程池参数的深入细节 SHALL 链接既有文档（`docs/Java/multithreading-basics.md`、`docs/Spring/spring-boot-multithreading-guide.md`），本文 SHALL NOT 展开重复。

#### Scenario: 读者理解过载时的行为链

- **WHEN** 读者为线程池配置阻塞队列
- **THEN** 能据决策流说清"任务何时建线程、何时排队、何时被拒绝"，并知道必须给队列设合理容量以避免 OOM

### Requirement: Stream Collectors 与集合互转

指南 SHALL 新增附录"Stream Collectors 与集合互转"，覆盖 `Collectors.toList/toSet/toMap/groupingBy/partitioningBy/mapping` 与集合的相互转换，并与 `computeIfAbsent` 分组形成"命令式 vs 流式"对照；SHALL 重点标注 `toMap` 的两个坑：key 冲突抛 `IllegalStateException`（需提供合并函数）、value 为 null 抛 `NullPointerException`。Stream 的深入用法 SHALL 链接 `docs/Java/stream.md`，本文 SHALL NOT 展开重复。

#### Scenario: 读者用流式方式聚合集合

- **WHEN** 读者需要把 List 按某字段分组或转成 Map
- **THEN** 使用 `groupingBy/toMap` 并规避 key 冲突与 null value 两个坑，需要深入时跳转到 Stream 指南

### Requirement: 示例证据等级与边界链接

本次新增的全部代码块 SHALL 定位为**纯语法演示**，以"示例片段（Illustrative fragment）"引出，SHALL NOT 声称已编译或运行、SHALL NOT 标注为 Verified runnable；需要让读者知道结果处以代码注释给出可观测结果。凡与既有文档重叠的主题（线程池、Stream、Comparator）SHALL 采用"本地最小讲解 + 链接既有文档"，SHALL NOT 复制展开。

#### Scenario: 读者正确理解示例的证据等级

- **WHEN** 读者阅读本次新增的代码示例
- **THEN** 明白这些是说明概念的语法片段（非承诺可运行），且深入主题会链接到对应的既有指南
