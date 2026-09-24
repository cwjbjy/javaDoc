## ADDED Requirements

### Requirement: 重写 Scope 并纳入泛型与 BigDecimal

`docs/Java/variables-and-types.md` 的 Scope 段 SHALL 重写：把**泛型、通配符、类型擦除、BigDecimal、数值边界、`Object`/JSON 动态值安全转换、final/volatile/ThreadLocal 的变量与类型视角**纳入覆盖范围；并 SHALL 显式声明将 JVM 内存布局深度、volatile/原子类并发深度、fastjson/jackson 库 API、泛型反序列化 `TypeReference` 用法**链接外放**至对应既有文档。Scope SHALL NOT 再声明"不涉及泛型"。

#### Scenario: 读者一眼看清本文覆盖与外放边界

- **WHEN** 读者阅读开头的 Scope 段
- **THEN** 能知道泛型/BigDecimal/数值边界/JSON 安全转换已在本文覆盖，而 JVM 内存、并发深度、JSON 库 API 会链接到专门文档

### Requirement: 纠正变量分类的内存分配表述

指南 SHALL 删除"局部变量栈上分配 / 实例变量堆上分配 / 静态变量方法区分配"这一不准确口诀，改为以**作用域、生命周期、线程共享关系**三个维度对照局部/实例/静态变量。指南 SHALL 说明对象的实际布局与分配位置由 **JVM 实现与逃逸分析**决定，且**静态字段不等同于传统意义的"方法区"**（并点明 JDK 7+/JDK 8+ 堆与 Metaspace 的变化）。深入细节 SHALL 链接 `docs/Java/jvm-memory-gc.md`，本文 SHALL NOT 展开。

#### Scenario: 读者不再把应试口诀当语言规则

- **WHEN** 读者查阅局部/实例/静态变量的区别
- **THEN** 得到的是作用域/生命周期/线程共享的准确对照，并知道"栈/堆/方法区"是实现细节而非语言保证，需要深入时跳转 JVM 文档

### Requirement: 数值溢出、整数除法与 JS 安全整数

指南 SHALL 新增"数值边界"内容，覆盖：① int/long 运算溢出的回绕行为（以注释给出可观测结果）；② `Math.toIntExact/addExact/multiplyExact` 在"宁抛异常也不要静默错值"场景的用法；③ 整数除法截断（如 `7/2==3`）及需要小数时的处理；④ JSON 中 `long` 超过 JavaScript `Number.MAX_SAFE_INTEGER`（2^53-1）会丢精度，前后端大 ID SHALL 以**字符串协议**传递（序列化转 String 的处理链接 `docs/Java/jackson-guide.md`）。

#### Scenario: 读者规避溢出与前端精度丢失

- **WHEN** 读者做整数累乘、把 long 收窄为 int，或向前端返回雪花 ID
- **THEN** 知道用 `Math.*Exact` 暴露溢出、注意整数除法截断，并把大 ID 以字符串下发以免 JS 丢精度

### Requirement: BigDecimal 精确十进制计算

指南 SHALL 新增"BigDecimal"独立小节，讲清：① 构造 SHALL 用 `new BigDecimal("0.1")` 或 `BigDecimal.valueOf(0.1)`，SHALL NOT 用 `new BigDecimal(0.1)`（注释给出误差可观测结果）；② `equals` 会比较 scale（`1.0` 与 `1.00` 不 equals），数值相等 SHALL 用 `compareTo`；③ `divide` SHALL 指定 scale 与 `RoundingMode`，否则除不尽抛 `ArithmeticException`；④ `ZERO/add/subtract/multiply/setScale` 的用法；⑤ 金额、比例、展示格式与计算精度 SHALL 分层处理。

#### Scenario: 读者正确处理金额计算

- **WHEN** 读者计算金额、比例或做区间校验
- **THEN** 用字符串构造 BigDecimal、用 `compareTo` 比较数值、`divide` 带 scale 与 RoundingMode，并把展示格式与计算精度分开

### Requirement: 修正 Money 不可变示例为 BigDecimal

指南中"不可变对象的构建"的 `Money` 示例 SHALL 把 `double amount` 改为 `BigDecimal amount`，构造器参数与 getter 返回类型相应调整，以同时体现"不可变"与"精确十进制"，SHALL NOT 再使用 `double` 承载金额。

#### Scenario: 示例与"金额用 BigDecimal"的结论一致

- **WHEN** 读者阅读 Money 不可变类示例
- **THEN** 看到金额字段是 BigDecimal 而非 double，不再被自相矛盾的示例误导

### Requirement: 泛型、通配符与 PECS

指南 SHALL 新增"泛型、通配符与类型擦除"小节，覆盖：① 泛型如何把强制转换前移到编译期、免除运行期 `ClassCastException`（以"原始 `List` 取出即强转"作反例对照）；② `List<?>`、`List<? extends T>`、`List<? super T>` 的读写能力差异；③ **PECS**（Producer Extends, Consumer Super）原则；④ 原始类型 `List`/`Map` 绕过编译期检查的风险；⑤ 类型擦除的直觉后果（运行期 `List<String>` 与 `List<Integer>` 同属 `List`、不能 `new T[]`、不能 `instanceof List<String>`）。现有"Diamond Operator"内容 SHALL 折入本节。

#### Scenario: 读者用对通配符并理解 PECS

- **WHEN** 读者写一个"只读取元素"或"只写入元素"的泛型方法
- **THEN** 依 PECS 选择 `? extends T`（生产者/只读）或 `? super T`（消费者/可写），并知道原始类型会丢失编译期检查

### Requirement: 类型擦除与反射找回类型（进阶）

指南 SHALL 说明类型擦除后仍可经 `getGenericSuperclass()` 得到 `ParameterizedType` 从而在运行期找回泛型实参的**原理**，并给出最小示例（如通过匿名子类保留泛型信息）。`TypeReference` 的**具体反序列化用法** SHALL 链接 `docs/Java/fastjson-guide.md`，本文 SHALL NOT 复制展开。

#### Scenario: 读者理解擦除不等于"信息全丢"

- **WHEN** 读者需要在运行期获得泛型实参类型
- **THEN** 理解可借 `ParameterizedType`/匿名子类保留的类型信息找回，并知道用 `TypeReference` 做反序列化时应查 fastjson 指南

### Requirement: Object 与 JSON 动态值的安全转换

指南 SHALL 新增"动态值的安全转换"小节，以可空 JSON 参数（如 MCP 工具的 `Integer limit`）为原型，覆盖：① `Object` 的来源（JSON 反序列化、`Map<String,Object>`、反射）与类型分发；② SHALL 先 `instanceof` 判断再转换（含 Java 17 的 pattern matching for instanceof）；③ `Number` 转 `BigDecimal`/`Integer`/`Long` 的精度与溢出边界（`intValue()` 静默截断的风险）；④ 字符串数字、空字符串 `""`、Java `null`、JSON `null` 的区别与判空顺序；⑤ SHALL NOT 使用未经检查的强制转换（如 `(List<X>) obj`）。JSON 库的取值 API SHALL 链接 `docs/Java/fastjson-guide.md`/`docs/Java/jackson-guide.md`。

#### Scenario: 读者安全处理动态 JSON 参数

- **WHEN** 读者从 JSON/`Map<String,Object>` 取出一个类型未知的值
- **THEN** 先 `instanceof` 分发再转换、区分 null/JSON-null/空串、收窄数值时防溢出，绝不做未经检查的强转

### Requirement: final、volatile 与共享状态的边界

指南 SHALL 扩展"常量与不可变性"为共享状态边界的内容：① `final` 只固定引用、SHALL NOT 被理解为对象自动不可变（需不可变类 + 防御性拷贝），此为本文全量讲解；② `volatile` 解决可见性、SHALL NOT 被理解为解决 `count++` 等复合操作的原子性，复合更新 SHALL 用 `AtomicInteger`/`AtomicReference` 或锁——并发深度 SHALL 链接 `docs/Java/multithreading-basics.md`，本文 SHALL NOT 展开。

#### Scenario: 读者不误用 final/volatile

- **WHEN** 读者用 `final` 修饰集合字段、或用 `volatile` 修饰计数器
- **THEN** 知道 final 不阻止改内部状态、volatile 不保证自增原子性，并据边界改用不可变类/原子类/锁

### Requirement: ThreadLocal 的线程封闭与清理边界

指南 SHALL 收编 `ThreadLocal`（因 `docs/Java/multithreading-basics.md` 明确不涵盖）：从**变量视角**说明其线程封闭语义（每线程一份、天然隔离），并 SHALL 强调使用后**必须 `remove()`**——尤其在线程池复用场景下否则导致数据串味与内存泄漏；SHALL 指出 WebFlux/虚拟线程等环境下 ThreadLocal 不再可靠。

#### Scenario: 读者在线程池中安全使用 ThreadLocal

- **WHEN** 读者用 ThreadLocal 存放请求上下文并运行在线程池/WebFlux 环境
- **THEN** 在 finally 中 `remove()` 清理，避免线程复用导致的上下文串味与内存泄漏

### Requirement: 示例证据等级与边界链接

本次新增的全部代码块 SHALL 定位为**纯语法演示**，以"示例片段（Illustrative fragment）"引出，SHALL NOT 声称已编译或运行、SHALL NOT 标注 Verified runnable；需要让读者知道结果处以代码注释给出可观测值。凡与既有文档重叠的主题（JVM 内存、volatile/原子、JSON 库 API、TypeReference 反序列化）SHALL 采用"本地最小讲解 + 链接既有文档"，SHALL NOT 复制展开。

#### Scenario: 读者正确理解示例的证据等级

- **WHEN** 读者阅读本次新增的代码示例
- **THEN** 明白这些是说明概念的语法片段（非承诺可运行），且深入主题会链接到对应的既有指南

### Requirement: 全文目录与锚点

指南 SHALL 更新顶部全文目录（TOC），覆盖新增与改造后的全部 H2/H3；TOC 锚点 SHALL 与中文标题匹配且无断裂（经 `validate_guide.py` 校验通过）。

#### Scenario: 读者可跳转到新增小节

- **WHEN** 读者通过顶部 TOC 点击"BigDecimal""泛型""动态值的安全转换"等条目
- **THEN** 能准确跳转到对应小节，无断链
