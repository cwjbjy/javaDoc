## 1. 导航层与开头纠偏

- [x] 1.1 在 `# Java 集合框架` 下、`## List` 前新增**全文目录（TOC）**，覆盖全部 H2/H3，锚点与中文标题匹配
- [x] 1.2 新增**"如何选择集合"决策表**（补充 A）：以"需求 → 推荐实现"覆盖 List/Set/Map/Queue 常见诉求
- [x] 1.3 修正 ArrayList 开头"为什么用集合"表述（第 8 点）：删除"数组只能存单一类型/集合可存多种类型"，改为"可变长度 + 统一 API + 多样数据结构"，并澄清泛型集合仍是单元素类型

## 2. List 章扩展

- [x] 2.1 新增"List 的创建方式对比"节（第 1 点）：`new ArrayList<>` / `Arrays.asList` / `List.of` / `List.copyOf` / `stream().toList()` 五维对比表 + 三大坑（定长视图 UOE、`int[]` 装箱 `size()==1`、`List.of` 含 null 抛 NPE）
- [x] 2.2 ArrayList 下新增"subList 视图陷阱"（第 3 点）：结构性修改后 CME、防御性拷贝 `new ArrayList<>(subList(...))`、下标/空列表/分页参数校验
- [x] 2.3 List 章新增"遍历中安全删除 / fail-fast"（补充 C）：for-each + `remove` 抛 CME 原因，正确姿势 `Iterator.remove` / `removeIf`；点明 `CopyOnWriteArrayList` 快照语义
- [x] 2.4 List 章新增"装箱陷阱"（补充 D）：`List<Integer>.remove(1)` 按下标 vs `remove(Integer.valueOf(1))` 按值，配可观测结果注释

## 3. Set 章扩展

- [x] 3.1 Set 章开头新增"有序（insertion-ordered）vs 排序（sorted）"术语对照（第 2 点）
- [x] 3.2 新增 **TreeSet** 节（第 2 点）：红黑树、`Comparable`/`Comparator`、O(log n)、不允许 null、`NavigableSet` 范围方法示例（`first/last/floor/ceiling/subSet/headSet/tailSet`）
- [x] 3.3 新增 **ConcurrentSkipListSet** 简述（第 2 点）：并发 + 排序场景

## 4. Map 章扩展

- [x] 4.1 HashMap 下新增"computeIfAbsent 分组 + 平铺→父子树两阶段"（第 4 点）：`record Node(id,parentId,name)` 数据示例，阶段一分组（注释给 `{0=[根],1=[子A,子B],2=[孙A1]}`）、阶段二装配（注释给树形结果）
- [x] 4.2 为第 291-292 行"缓存版 computeIfAbsent"补命中/未命中可观测语义，并加两个坑（lambda 内勿改同 map、返回 null 不建映射）
- [x] 4.3 HashMap 下新增"作为 key 的合约：equals/hashCode"（第 5 点）：哈希后不可变、`equals`/`hashCode` 一致、勿用可变 DTO 作长期缓存 key，配"改字段后 get 不到"的后果说明
- [x] 4.4 新增 **TreeMap** 节（第 2 点）：按 key 排序、`NavigableMap` 范围方法（`subMap/headMap/tailMap/floorKey/ceilingKey`）、key 不允许 null
- [x] 4.5 新增 **ConcurrentSkipListMap** 节（第 2 点）：并发 + 排序（排行榜、时间序列索引）
- [x] 4.6 ConcurrentHashMap 下深化"并发集合的边界"（第 6 点）：`❌ check-then-act` vs `✅ putIfAbsent/computeIfAbsent/merge`、`compute` 回调三禁忌、迭代器弱一致、`Collections.synchronizedList` 遍历须手动 `synchronized`
- [x] 4.7 新增 **EnumMap / EnumSet** 简述（补充 F）：枚举键、数组/位向量底层、性能优于哈希实现、创建时固定 key 类型且不允许 null key

## 5. Queue 章扩展

- [x] 5.1 LinkedBlockingQueue 新增"阻塞与失败语义表"（第 7 点）：`put/take`（阻塞+中断）、`offer/poll`（立即失败）、`offer/poll(timeout)`（超时）、`add`（满抛 `IllegalStateException`）
- [x] 5.2 LinkedBlockingQueue 新增"容量 × 最大线程数 × 拒绝策略"（第 7 点）：`ThreadPoolExecutor` 决策流 ASCII 图 + "无界队列使 max 失效致 OOM"结论；线程池细节链接 `multithreading-basics.md` 与 `spring-boot-multithreading-guide.md`

## 6. 附录与收尾

- [x] 6.1 新增附录"Stream Collectors 与集合互转"（补充 B）：`toList/toSet/toMap/groupingBy/partitioningBy/mapping` + `computeIfAbsent` 命令式对照；标注 `toMap` 两个坑（key 冲突抛 `IllegalStateException`、value 为 null 抛 `NullPointerException`）；深入链接 `stream.md`
- [x] 6.2 全文统一新增代码块的证据标注为"示例片段（Illustrative fragment）"，不声称可运行、不标 Verified runnable；需知结果处以注释给出
- [x] 6.3 复查交叉链接（线程池 / Stream / Comparator）均为"本地最小讲解 + 链接"，无重复展开
- [x] 6.4 运行 `py -3 .agents/skills/guide-writing/scripts/validate_guide.py docs/Java/collections-framework.md` 通过；Grep 复查 TOC 与全部 `#` 锚点无断裂
- [x] 6.5 按 spec 逐条 Requirement 核对实现，运行 `openspec validate expand-collections-framework-guide` 通过
