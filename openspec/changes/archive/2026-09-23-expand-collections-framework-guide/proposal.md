## Why

`docs/Java/collections-framework.md`（574 行）目前是一份"能查 API"的参考型指南，但缺少企业开发中最容易踩坑、也最常被问到的三类内容：**怎么选**（没有选型导航，"有序/排序""可变/不可变""单线程/并发"三组对立概念散落各处）、**创建的差异**（`Arrays.asList`/`List.of`/`new ArrayList<>` 的可变性与回写语义没有对比）、**边界与陷阱**（subList 视图失效、复合操作非原子、`equals/hashCode` 合约、装箱删除、阻塞队列语义均未覆盖）。同时开头"数组只能存单一类型、集合可存多种类型"的表述不准确（泛型集合同样是单元素类型）。本次变更在保持参考型骨架的前提下补齐上述内容，使其更贴近"企业常用 API 教程"。

## What Changes

### 纠偏与导航

- **修正开头表述（第 8 点）**：删除"数组只能存单一类型、集合可存多种类型"的错误对比，改为强调集合的真正优势——**可变长度 + 统一丰富的 API + 多样的数据结构**；类型层面澄清"泛型集合同样是单元素类型，但编译期做类型检查"。
- **新增顶部"如何选择集合"决策表（补充 A）**：以"需求 → 推荐实现"的形式给出 List/Set/Map/Queue 的选型导航。
- **新增全文目录 TOC（用户决策）**：可跳转锚点，覆盖全部 H2/H3。

### List 章

- **新增"List 的创建方式对比"节（第 1 点）**：`new ArrayList<>(coll)` / `Arrays.asList(arr)` / `List.of(...)` / `List.copyOf(coll)` / `stream().toList()` 的可变性、`set` 是否回写、null 许可、与源关系对比表；配三大坑（定长视图 `add` 抛 `UnsupportedOperationException`、`int[]` 装箱得到 `size()==1`、`List.of` 含 null 抛 `NullPointerException`）。
- **ArrayList 下新增"subList 视图陷阱"（第 3 点）**：父列表结构性修改后子列表可能抛 `ConcurrentModificationException`；长期保存/异步传递/分批处理须用 `new ArrayList<>(source.subList(...))` 防御性拷贝；下标边界、空列表、分页参数校验。
- **ArrayList 下新增"遍历中安全删除 / fail-fast"（补充 C）**：for-each 中 `list.remove` 抛 CME 的原因，正确姿势 `Iterator.remove` 与 `removeIf`。
- **ArrayList 下新增"装箱陷阱"（补充 D）**：`List<Integer>.remove(1)` 删的是下标 1 而非值 1，删值需 `remove(Integer.valueOf(1))`。

### Set 章

- **新增"有序 vs 排序"术语澄清（第 2 点）**：insertion-ordered（LinkedHashSet）与 sorted（TreeSet）的区别。
- **新增 TreeSet 节（第 2 点）**：`Comparable`/`Comparator`、`NavigableSet` 范围方法（`first/last/floor/ceiling/subSet`）、不允许 null、O(log n)。
- **新增 ConcurrentSkipListSet 简述（第 2 点）**：并发 + 排序场景。

### Map 章

- **HashMap 下新增"computeIfAbsent 分组 + 平铺→父子树两阶段构建"（第 4 点）**：给出可观测结果的 `Node(id,parentId,name)` 数据示例，演示"按 key 初始化容器"分组与自顶向下装配；为第 291-292 行缓存示例补命中/未命中的可观测结果；点出"lambda 内勿改同一 map""返回 null 不建立映射"两个坑。
- **HashMap 下新增"作为 key 的合约：equals/hashCode"（第 5 点）**：key 参与哈希后不可变、`equals` 与 `hashCode` 必须一致、不要把可变 DTO 当长期缓存 key；覆盖 `HashMap` key / `HashSet` 元素 / `distinct()` 三个场景。
- **新增 TreeMap 节（第 2 点）**：`NavigableMap` 范围方法（`subMap/headMap/tailMap/floorKey/ceilingKey`）。
- **ConcurrentHashMap 下深化"并发集合的边界"（第 6 点）**：单个操作线程安全 ≠ "读取—判断—写入"复合操作原子（用 `putIfAbsent/computeIfAbsent/merge` 替代）；`computeIfAbsent/merge` 适合单 key 聚合；`compute` 回调禁忌（慢 IO、递归更新同一 map、复杂副作用）；迭代器弱一致（fail-safe）；`Collections.synchronizedList` 遍历仍须手动 `synchronized`。
- **新增 ConcurrentSkipListMap 节（第 2 点）**：并发 + 排序（排行榜、时间序列索引）。
- **新增 EnumMap/EnumSet 简述（补充 F）**：枚举键的数组底层高性能实现。

### Queue 章

- **LinkedBlockingQueue 新增"阻塞与失败语义表"（第 7 点）**：`put/take`（阻塞）、`offer/poll`（立即失败返回 false/null）、`offer/poll(timeout)`（超时）、`add`（满抛 `IllegalStateException`）的对照。
- **LinkedBlockingQueue 新增"容量 × 最大线程数 × 拒绝策略"（第 7 点）**：`ThreadPoolExecutor` 决策流 ASCII 图 + "无界队列使 max 失效、任务堆积致 OOM"结论；线程池细节**链接**既有文档，本地只保留最小讲解。

### 附录

- **新增"Stream Collectors 与集合互转"（补充 B）**：`toList/toSet/toMap/groupingBy/partitioningBy/mapping`；重点标注 `toMap` 的两个坑（重复 key 抛 `IllegalStateException`、value 为 null 抛 `NullPointerException`）；深入用法**链接** `stream.md`。

### 证据等级与边界（贯穿全文）

- 全部新增代码块按**纯语法演示**处理，统一标注 `Illustrative fragment`（示例片段），不建 `target/guide-verification` 验证工程、不声称可运行。
- 与既有文档重叠处（线程池、Stream、Comparator）一律"本地最小讲解 + 链接"，不复制展开。

## Capabilities

### New Capabilities

- `collections-framework-guide`: `docs/Java/collections-framework.md` 的企业化扩展——选型导航与目录、List 创建方式对比、有序/排序结构（TreeSet/TreeMap/ConcurrentSkipList\*）、subList 与 fail-fast 与装箱陷阱、computeIfAbsent 分组建树、equals/hashCode 合约、并发集合边界、阻塞队列语义与线程池过载、Stream Collectors 互转、EnumMap/EnumSet；示例为纯语法演示，重叠内容链接既有文档。

### Modified Capabilities

（无——`openspec/specs/` 下现有 api-documentation、file-upload、global-response-format、market-management、order-management 均为项目功能 spec，与本文档无关）

## Impact

- `docs/Java/collections-framework.md`: 开头表述修正；新增顶部选型决策表与全文 TOC；List/Set/Map/Queue 各章新增或深化多个子节；新增两个附录节。预计净增约 300~450 行，不删除既有正确内容。
- 无业务代码修改、无依赖变更、无向后兼容影响（纯文档交付物）。
- 交叉链接：`docs/Java/multithreading-basics.md`、`docs/Spring/spring-boot-multithreading-guide.md`、`docs/Java/stream.md`（仅新增指向它们的链接，不改动这些文件）。
- 结构校验：完成后运行 `py -3 .agents/skills/guide-writing/scripts/validate_guide.py docs/Java/collections-framework.md`。
