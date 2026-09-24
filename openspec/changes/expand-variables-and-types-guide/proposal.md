## Why

`docs/Java/variables-and-types.md`（532 行）是一份聚焦的 reference guide，但存在三处与企业实战脱节的缺口：

1. **主动排除了最贴近项目的主题**：第 22 行 Scope 明确"不涉及泛型、JVM 内存模型"。然而本仓库真实代码大量依赖泛型与动态类型——MCP 验证工程 `target/guide-verification/product-mcp-server/ProductTools.java` 用 `List<Product>` 返回、`@McpToolParam(required=false) Integer limit`（可空装箱参数）、`long id`，并在 `resolveLimit` 里做 null 检查 + 1~50 边界校验；market/order 模块遍布 `Map<String,Object>`、`List<JSONObject>`。泛型、通配符、类型擦除、`Object`/JSON 安全转换正是这些代码正确性的基础。
2. **金额精度只有一句带过**：全文仅在第 189 行提"货币用 BigDecimal"，而第 435-445 行的 `Money` 不可变类示例竟用 `double amount`——与其结论自相矛盾。金额、比例、统计值、区间校验需要 BigDecimal 单独成节。
3. **基本类型边界与内存表述不够准**：缺 int/long 溢出、整数除法、`long` 超 JavaScript 安全整数（ID 应以字符串协议传递）等数值边界；第 60-71 行"局部变量栈上分配 / 实例变量堆上分配 / 静态变量方法区分配"是应试口诀，不准确也不应作为语言规则记忆。

本次为**扩展**：保留既有正确内容与 reference 骨架，把"编译期类型保证会在运行期边界失效、需重新建立"这条暗线补全，同时严格尊重既有文档边界（JVM 深度、volatile/原子深度、JSON 库 API 一律链接外放，不复制）。**Java 时间 API（#3）移出本次**，将来独立成篇（`create-datetime-guide`）。

## What Changes

### Scope 重写与开头纠偏

- **重写 Scope（第 22 行）**：把泛型、通配符、类型擦除、BigDecimal、数值边界、`Object`/JSON 安全转换**纳入**；把 JVM 内存布局深度、volatile/原子类并发深度、fastjson/jackson 库 API **显式声明为"链接外放"**。
- **纠正变量分类表述（#7）**：删除第 60-71 行"栈上分配 / 堆上分配 / 方法区分配"口诀，改为讲清局部/实例/静态变量的**作用域、生命周期、线程共享关系**；说明对象布局与分配位置由 JVM 实现与逃逸分析决定，静态字段不等同于传统"方法区"（JDK 7+ 随 Class 对象移入堆、JDK 8+ 类元数据在 Metaspace）；深度链接 `jvm-memory-gc.md`。

### 数值边界（#5）

- 新增"数值边界：溢出、精度与整数除法"节：int/long 运算溢出与回绕、`Math.toIntExact/addExact/multiplyExact` 的适用场景、整数除法截断、浮点累加误差。
- 新增"`long` 与 JavaScript 安全整数"要点：JSON 中 `long` 超过 `Number.MAX_SAFE_INTEGER`（2^53-1）会丢精度，前后端协议中大 ID 应以**字符串**传递。

### BigDecimal（#2）

- 新增"BigDecimal：精确十进制"节：`new BigDecimal("0.1")` / `BigDecimal.valueOf(0.1)` vs `new BigDecimal(0.1)`；`equals` 比较 scale、数值比较用 `compareTo`；`divide` 必须指定 scale 与 `RoundingMode`；`ZERO/add/subtract/multiply/setScale`；金额、比例、展示格式与计算精度**分层处理**。
- **修正 Money 示例（第 435-445 行）**：`double amount` → `BigDecimal amount`，构造器与 getter 相应调整，体现不可变 + 精确十进制。

### 泛型、通配符与类型擦除（#1）

- 新增"泛型、通配符与类型擦除"节：泛型为何把强转前移到编译期、免除运行期 `ClassCastException`；`List<?>` / `List<? extends T>` / `List<? super T>` 的读写能力差异；**PECS**（Producer Extends, Consumer Super）；原始类型 `List`/`Map` 的风险；类型擦除的直觉后果。
- **进阶**：擦除后如何靠反射 `ParameterizedType` / `getGenericSuperclass()` 找回类型信息（只讲原理）；`TypeReference` 的**用法**链接 `fastjson-guide.md §6`，不复制。
- 将现有"Diamond Operator（第 374-387 行）"折进本节。

### 动态值的安全转换：Object / JSON（#4）

- 新增节：`Object` 的来源与类型分发；`instanceof` 后再转换（含 Java 17 pattern matching for instanceof）；`Number` → `BigDecimal`/`Integer`/`Long` 的精度与溢出边界；字符串数字、空字符串 `""`、Java `null`、JSON `null` 的区别；**禁止未经检查的强制转换**。库取值 API 链接 `fastjson-guide.md`/`jackson-guide.md`。

### 常量、不可变性与共享状态的边界（#6）

- 扩展"常量与不可变性"节：`final` 只固定引用、不让对象自动不可变（本文全量）。
- 新增"共享可变状态的边界"：`volatile` 解决可见性、**不解决 `count++` 原子性**；复合更新用 `AtomicInteger`/`AtomicReference` 或锁——深度链接 `multithreading-basics.md §3/§6`。
- **收编 ThreadLocal**（`multithreading-basics.md` 明确不涵盖）：线程封闭语义、**必须 `remove()`**（线程池复用致数据串味/内存泄漏）、WebFlux/虚拟线程下不再可靠。

### 证据等级与边界（贯穿全文）

- 全部新增代码块标注 `Illustrative fragment`（纯语法演示），不建验证工程、不声称可运行；需要"可观测结果"处以代码注释写出。
- 与既有文档重叠处（JVM 内存、volatile/原子、JSON 库、泛型反序列化）一律"本地最小讲解 + 链接"。
- 顶部新增/更新全文 TOC，锚点与中文标题匹配。

## Capabilities

### New Capabilities

- `variables-and-types-guide`: `docs/Java/variables-and-types.md` 的企业化扩展——Scope 重写、变量分类内存表述纠错、数值溢出与 JS 安全整数、BigDecimal 精确十进制（含 Money 修正）、泛型/通配符/PECS/类型擦除与反射进阶、Object/JSON 动态值安全转换、final/volatile/ThreadLocal 共享状态边界；示例为纯语法演示，重叠内容链接既有文档。

### Modified Capabilities

（无——`openspec/specs/` 下现有 api-documentation、file-upload、global-response-format、market-management、order-management 均为项目功能 spec，与本文档无关）

## Impact

- `docs/Java/variables-and-types.md`：Scope 重写；变量分类表述纠错；新增 4 个 H2 节（数值边界、BigDecimal、泛型、动态值安全转换）+ 改造"常量与不可变性"节；修正 Money 示例；更新 TOC 与 References。预计净增约 450~550 行，不删除既有正确内容。
- 无业务代码修改、无依赖变更、无向后兼容影响（纯文档交付物）。
- 交叉链接（仅新增指向、不改动这些文件）：`docs/Java/jvm-memory-gc.md`、`docs/Java/multithreading-basics.md`、`docs/Java/fastjson-guide.md`、`docs/Java/jackson-guide.md`、`docs/Java/collections-framework.md`、`docs/Java/optional-guide.md`。
- **不含** Java 时间 API（#3）——移出本次，将来独立 `create-datetime-guide`。
- 结构校验：完成后运行 `py -3 .agents/skills/guide-writing/scripts/validate_guide.py docs/Java/variables-and-types.md` 与 `openspec validate expand-variables-and-types-guide`。
