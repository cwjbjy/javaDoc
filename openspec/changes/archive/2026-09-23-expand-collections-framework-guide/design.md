## Context

`docs/Java/collections-framework.md`（574 行）是一份 reference guide：List / Set / Map / Queue 四大类平行展开，每类下按实现（ArrayList、CopyOnWriteArrayList、HashSet、LinkedHashSet、HashMap、LinkedHashMap、ConcurrentHashMap、ArrayDeque、LinkedBlockingQueue…）给"简介 + API 表 + 示例"。结构清晰，但只覆盖了"怎么用"，缺"怎么选"和"边界在哪"。

目标读者：使用本项目的 Java 后端开发者（Java 17 / Spring Boot 4.0.6），已会基本增删改查，但在选型、可变性、并发原子性、哈希合约这些地方反复踩坑。

本次为**扩展**：保留既有正确内容与参考型骨架，只做"插入新子节 + 局部改写开头"，不推倒重来。

## Goals / Non-Goals

**Goals:**

- 在文档顶部建立"怎么选"的导航层（选型决策表 + TOC），把散落的对立概念（有序/排序、可变/不可变、单线程/并发）显式化。
- 补齐用户点名的 8 个主题 + 4 个补充主题（A 选型导航 / B Stream Collectors / C fail-fast 安全删除 / D 装箱陷阱 / F EnumMap-EnumSet）。
- 每个新增陷阱都配"错误写法 → 正确写法"或"可观测结果"，让读者看得出后果。
- 全部示例定位为**纯语法演示**（`Illustrative fragment`），不建验证工程、不声称可运行。
- 与既有文档重叠处一律"本地最小讲解 + 链接"。

**Non-Goals:**

- 不建 `target/guide-verification` 验证工程，不编译运行示例（用户决策：纯语法演示）。
- 不纳入未选中的补充点：E 复杂度速查表、G 遗留类专题（Vector/Stack/Hashtable）、H `unmodifiableList` 专题、I null 语义专题（null 仅作为"创建方式对比"的一个坑顺带出现）。
- 不深入未点题的结构：LinkedList 内部、WeakHashMap、PriorityBlockingQueue、IdentityHashMap 等一律不展开。
- 不改写既有正确的 API 表与示例，不动其他文档，不改业务代码。

## Decisions

### D1. 结构：reference 骨架不变，顶部加"选型导航层"（hybrid）

guide-writing 判定本文是 reference guide（平行分类）。本次不改成 progressive，而是在 `# Java 集合框架` 下、`## List` 之前插入两块导航：① **TOC**（覆盖全部 H2/H3，用户决策）；② **"如何选择集合"决策表**（补充 A），用"需求 → 推荐实现"把四大类的常见诉求一表打通。各分支仍保持可独立查阅。

替代方案：把全文重排成"选型 → 用法 → 陷阱"三段渐进——被否，破坏既有参考型查阅习惯，改动面过大。

### D2. 术语主线："有序（insertion-ordered）vs 排序（sorted）"

第 2 点最容易混。决策：在 **Set 章开头**用一张对照讲清 `LinkedHashSet`（按插入顺序，O(1)）与 `TreeSet`（按大小排序，O(log n)，元素须可比较、不允许 null）；**Map 章**同构复述一次（`LinkedHashMap` vs `TreeMap`）。`TreeSet/TreeMap` 作为既有分类的"排序版兄弟"插入，`ConcurrentSkipListSet/Map` 作为"并发 + 排序"再补一层。范围查询方法（`subMap/headMap/tailMap/floor/ceiling`）是它们区别于哈希实现的核心价值，重点给例。

### D3. 证据等级：全部 `Illustrative fragment`（纯语法演示）

用户决策示例为纯语法演示。所有新增代码块统一以"示例片段"引出，只保证语法与符号自洽、能说明概念，**不声称执行结果**。少数需要让读者"知道结果"的地方（第 4 点建树、第 291-292 行缓存、装箱陷阱），用**代码注释**写出可观测结果（如 `// {0=[根], 1=[子A, 子B], 2=[孙A1]}`），而非"运行输出"。

替代方案：建验证工程逐个 `mvn test`——被否，用户明确选纯语法演示，且示例数量多、成本高。

### D4. computeIfAbsent 采用"两阶段建树"，数据自带可观测结果

第 4 点用 `record Node(long id, long parentId, String name)` 的平铺列表（根/子A/子B/孙A1 四条）演示：

- **阶段一 分组**：`childrenByParent.computeIfAbsent(n.parentId(), k -> new ArrayList<>()).add(n)`，结果 `{0=[根], 1=[子A, 子B], 2=[孙A1]}`；
- **阶段二 装配**：自顶向下把每个 parent 的 children 挂上去，得到 `根 → (子A → 孙A1), 子B` 的树。

并顺带补第 291-292 行"缓存版 computeIfAbsent"的命中/未命中语义与两个坑（lambda 内勿改同一 map；返回 null 不建立映射）。这与补充 B 的 `Collectors.groupingBy` 形成"命令式 vs 流式"对照。

### D5. 并发边界用"❌ check-then-act / ✅ 原子方法"对照纠偏

第 6 点的核心是打破"用了 ConcurrentHashMap 就万事大吉"的误解。决策：在 ConcurrentHashMap 节末尾新增"并发集合的边界"子节，用左右对照立起"单方法安全 ≠ 复合操作原子"，明确 `compute` 回调三禁忌（慢 IO / 递归改同 map / 复杂副作用）、迭代器弱一致，并单列 `Collections.synchronizedList` 遍历须手动 `synchronized`。

### D6. 阻塞队列：语义表 + 线程池决策图，细节链接既有文档

第 7 点分两块：① `put/take/offer/poll/add` 的"满/空时行为"对照表（本地讲清，属队列自身语义）；② "容量 × 最大线程数 × 拒绝策略"只给一张 `ThreadPoolExecutor` 决策流 ASCII 图 + 一句"无界队列使 max 失效、堆积致 OOM"的结论，**线程池参数细节链接** `docs/Java/multithreading-basics.md` 与 `docs/Spring/spring-boot-multithreading-guide.md`，不在本文展开。

### D7. 开头表述纠偏的具体改法

第 8 点：删除"类型单一：数组只能存储单一类型的数据，而集合可以存储多种类型的数据"这一条，替换为强调集合真正优势的三条——**可变长度**、**统一而丰富的 API**、**多样的数据结构**；并补一句澄清"泛型集合同样是单元素类型，价值在编译期类型检查与更灵活的类型关系，而非'能装多种类型'"。保留"优先用集合，除非有明确性能/内存顾虑"的结论。

### D8. TOC 与锚点用脚本校验

新增 TOC 后，锚点易与中文标题不匹配。决策：完成后运行 `py -3 .agents/skills/guide-writing/scripts/validate_guide.py docs/Java/collections-framework.md` 校验目录锚点，并用 Grep 复查所有 `#` 链接无断裂。

## Risks / Trade-offs

- [文档进一步变长，查阅负担上升] → 顶部 TOC + 选型决策表提供导航；陷阱类子节标题直接点明后果（如"subList 视图陷阱""装箱陷阱"），便于跳读。
- [纯语法演示不验证，可能出现编不过的示例] → 严格标注 `Illustrative fragment`，只演示概念、不声称运行；符号（record、泛型、方法名）逐个自检，保持与 JDK 17 API 一致。
- [与线程池 / Stream / Comparator 文档重复] → 一律"本地最小讲解 + 链接既有文档"，避免两份内容漂移。
- [新增术语（有序/排序、fail-fast、弱一致）密度高] → 每个术语首次出现即给一句精确定义 + 一个熟悉对照，符合 content-contract 的术语约定。
- [TOC/锚点断裂] → validate_guide.py + Grep 双重复查。
