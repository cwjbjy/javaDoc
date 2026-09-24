## Context

`docs/Java/variables-and-types.md`（532 行）是一份 reference guide：从变量声明、八种基本类型、引用类型与 null、类型转换与装箱、`var`/Diamond、`final` 与不可变性，到常见陷阱，平行铺开。结构清晰，但**主动把泛型与 JVM 内存排除在 Scope 外**（第 22 行），且金额精度只有一句带过、`Money` 示例用了 `double`。

目标读者：使用本项目的 Java 后端开发者（Java 17 / Spring Boot 4.0.6），已掌握基本语法，但在**运行期类型边界**（泛型擦除、`Object`/JSON 动态值、数值溢出、金额精度、共享可变状态）反复踩坑——这些正是 MCP 动态工具调用、参数校验、JSON 转参、金融/统计数据处理每天要面对的。

本次为**扩展**：保留既有正确内容与参考型骨架，只做"插入新节 + 局部改写（Scope/变量分类/Money/常量节）"，不推倒重来。**Java 时间 API 不在本次范围**。

## Goals / Non-Goals

**Goals:**

- 用一条组织洞察串起 6 个新增/改造点：**编译期类型保证会在运行期边界失效，需知道在哪、用什么重建**。
- 补齐用户点名的 #1 泛型（含进阶反射）、#2 BigDecimal、#4 Object/JSON 安全转换、#5 数值边界、#6 final/volatile/ThreadLocal、#7 JVM 内存表述纠错。
- 每个陷阱配"错误写法 → 正确写法"或"注释版可观测结果"，让读者看得出后果。
- 全部示例为**纯语法演示**（`Illustrative fragment`），不建验证工程。
- 与既有文档重叠处一律"本地最小讲解 + 链接"。

**Non-Goals:**

- **不纳入 Java 时间 API（#3）**：移出本次，将来独立 `create-datetime-guide`。
- 不建 `target/guide-verification` 验证工程、不编译运行示例（用户决策：纯语法演示）。
- 不写 JVM 内存/GC 长文（链接 `jvm-memory-gc.md`）、不展开 volatile/原子类并发细节（链接 `multithreading-basics.md`）、不复述 fastjson/jackson 库 API（链接对应指南）。
- 不深入数组操作、自定义类型层次设计、record/sealed 专题。
- 不改既有正确的 API 表述与示例（除 Money 的 double→BigDecimal 修正），不动其他文档，不改业务代码。

## Decisions

### D1. 结构：reference 骨架不变，按"边界失效→重建"补齐

保持参考型平行结构，仅在既有章节间**插入 4 个新 H2**（数值边界、BigDecimal、泛型、动态值安全转换）并**改造 3 处**（Scope、变量分类、常量节）。依赖顺序：基本类型 → 数值边界 → BigDecimal（浮点问题的解法）→ 引用类型 → 泛型（参数化引用类型）→ 类型转换 → 动态值转换（Object 的转回）→ 类型推断 → 共享状态边界。顶部更新全文 TOC。

替代方案：重排成 progressive 三段——被否，破坏既有查阅习惯，改动面过大。

### D2. Scope 边界切分：纳入什么、链接外放什么

新 Scope 明确**纳入**泛型/通配符/擦除、BigDecimal、数值边界、Object/JSON 安全转换、final/volatile/ThreadLocal 的**类型与变量视角**；明确**链接外放**：JVM 内存布局深度 → `jvm-memory-gc.md`；volatile/原子类并发深度 → `multithreading-basics.md`；JSON 库取值 API → `fastjson-guide.md`/`jackson-guide.md`；泛型反序列化 `TypeReference` 用法 → `fastjson-guide.md §6`；`Optional` 空值语义 → `optional-guide.md`。每处保留"读懂本文所需的最小本地定义 + 触发条件"，深度交给 owner。

### D3. #7 JVM 内存表述的具体改法

删除第 60-71 行表格中"栈上分配 / 堆上分配 / 方法区分配"三行口诀，替换为**作用域、生命周期、线程共享关系**三维对照（局部：方法内、线程独占；实例：对象存续期、随对象共享；静态：类存续期、全类共享）。补一句：具体对象布局与分配位置由 **JVM 实现与逃逸分析**决定（标量替换/栈上分配可能使"对象一定在堆"不成立）；**静态字段不等同于传统"方法区"**——JDK 7 起字符串常量池/静态字段移入堆，JDK 8 起类元数据在 Metaspace（本地内存）。深度链接 `jvm-memory-gc.md §2`。

### D4. #1 泛型深度：到 PECS + 擦除后果 + 反射原理为止

讲清：① 泛型把强转前移到编译期，免除运行期 `ClassCastException`（用"无泛型的 `List` 取出即强转"对照）；② `List<?>`（只读通配）、`List<? extends T>`（上界、生产者、不能写入非 null）、`List<? super T>`（下界、消费者、可写入 T）；③ **PECS**；④ 原始类型 `List`/`Map` 绕过编译期检查的风险；⑤ 擦除的直觉后果（`List<String>` 与 `List<Integer>` 运行期同类、不能 `new T[]`、不能 `instanceof List<String>`）。**进阶**：擦除后仍可经 `getGenericSuperclass()` 得到 `ParameterizedType` 找回实参——只讲**原理与最小示例**；`TypeReference` 的**使用**链接 `fastjson-guide.md §6`，避免与其漂移。Diamond Operator（第 374-387 行）折进本节。

### D5. #2 BigDecimal：构造、比较、除法、分层

四条硬规则：① 构造用 `new BigDecimal("0.1")` 或 `BigDecimal.valueOf(0.1)`，**不用** `new BigDecimal(0.1)`（注释给出 `0.1000000000000000055511151231257827...`）；② `equals` 连 scale 一起比（`1.0` ≠ `1.00`），数值相等用 `compareTo`；③ `divide` 必须给 scale 与 `RoundingMode`，否则除不尽抛 `ArithmeticException`；④ 金额（定点、scale=2、HALF_UP）、比例（更高精度中间量）、展示格式（`DecimalFormat`/`String.format`）与计算精度**分层**。`Money` 示例 `double amount` → `BigDecimal amount`。

### D6. #4 Object/JSON 安全转换：分发 + 边界，不复述库 API

以 MCP `ProductTools.resolveLimit` 式的可空参数为原型：① `Object` 来源（JSON 反序列化、`Map<String,Object>`、反射）与类型分发；② **先 `instanceof` 再转**（Java 17 `if (o instanceof String s)` 模式匹配）；③ `Number` → `BigDecimal`/`Integer`/`Long` 的精度与溢出边界（`intValue()` 静默截断，用 `Math.toIntExact` 或先 `longValue` 校验）；④ 字符串数字、`""`、Java `null`、JSON `null` 四者区别与判空顺序；⑤ **禁止未经检查的 `(List<X>) obj` 强转**。取值 API（`JSONObject.getXxx`、`JsonNode`）链接 fastjson/jackson。

### D7. #5 数值边界：溢出、Exact、整数除法、JS 安全整数

int/long 回绕示例（注释给可观测结果）；`Math.toIntExact/addExact/multiplyExact` 何时用（宁抛异常不要静默错值）；整数除法截断（`7/2==3`、需要小数先转 double/BigDecimal）；JSON 中 `long` 超过 `Number.MAX_SAFE_INTEGER`（2^53-1）前端丢精度 → **大 ID 以字符串协议传递**（序列化时转 String 或全局配置，链接 `jackson-guide.md`）。

### D8. #6 final/volatile/ThreadLocal：本文只留"变量与类型视角"

`final` 全量留本文（引用不变 ≠ 对象不可变，防御性拷贝，编译时常量）。`volatile`/原子类只讲**边界结论**（可见性 ≠ 原子性；复合更新用原子类/锁）+ 链接 `multithreading-basics.md §3/§6`。**ThreadLocal 收编本文**：因 `multithreading-basics.md` 第 12 行明确不涵盖，而它本质是"线程封闭的变量"，属变量范畴——讲线程封闭语义、**必须 `remove()`**（线程池复用致数据串味/内存泄漏）、WebFlux/虚拟线程下不可靠。

### D9. 证据等级：全 Illustrative fragment + 注释版可观测结果

所有新增代码块以"示例片段"引出，只保证语法与符号自洽、不声称执行；需要让读者知道结果处（`0.1+0.2`、`new BigDecimal(0.1)`、溢出回绕、`compareTo`/`equals` 差异）用**代码注释**写出可观测值，对齐 JDK 17 API。

### D10. TOC 与锚点用脚本校验

更新 TOC 后运行 `py -3 .agents/skills/guide-writing/scripts/validate_guide.py docs/Java/variables-and-types.md` 校验锚点，并 Grep 复查所有 `#` 链接无断裂。

## Risks / Trade-offs

- [文档从 532 → ~1000 行，查阅负担上升] → 顶部 TOC + 陷阱类标题直接点明后果（如"原始类型的风险""volatile 不保证原子性""ThreadLocal 必须 remove"），便于跳读。
- [纯语法演示不验证，可能出编不过的示例] → 严格标注 `Illustrative fragment`；泛型/反射/BigDecimal/pattern-matching 符号逐个自检，对齐 JDK 17。
- [与 jvm-memory-gc / multithreading-basics / fastjson / jackson 重复] → 一律"本地最小讲解 + 链接"，深度交给 owner，避免漂移。
- [#1 进阶 ParameterizedType 偏"反射专题"] → 本文只讲"为什么擦除后还能找回"的原理与最小示例，`TypeReference` 使用链接 fastjson §6。
- [新增术语密度高（PECS、擦除、上界/下界、Metaspace、线程封闭）] → 每术语首现即给一句精确定义 + 一个熟悉对照，符合 content-contract。
- [ThreadLocal 收编可能与将来多线程文档调整冲突] → 本文限定"变量视角 + 泄漏边界"，若日后 `multithreading-basics.md` 增设 ThreadLocal，则以链接互指、不重复展开。
