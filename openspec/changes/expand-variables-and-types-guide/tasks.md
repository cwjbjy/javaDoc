## 1. Scope 重写与开头纠偏

- [x] 1.1 重写第 22 行 Scope：纳入泛型/通配符/擦除、BigDecimal、数值边界、Object/JSON 安全转换、final/volatile/ThreadLocal 变量视角；显式声明 JVM 内存深度、volatile/原子深度、JSON 库 API、TypeReference 用法为"链接外放"
- [x] 1.2 纠正第 60-71 行变量分类表述（#7）：删"栈/堆/方法区"口诀 → 作用域/生命周期/线程共享三维对照；补"布局由 JVM 实现与逃逸分析决定""静态字段≠传统方法区（JDK7+/8+ 堆与 Metaspace）"；链接 `jvm-memory-gc.md §2`
- [x] 1.3 更新/新增顶部全文 TOC，覆盖新增与改造后的全部 H2/H3

## 2. 数值边界节（#5）

- [x] 2.1 新增"数值边界：溢出、精度与整数除法"节：int/long 回绕（注释给可观测结果）、`Math.toIntExact/addExact/multiplyExact`、整数除法截断（`7/2==3`）
- [x] 2.2 新增"`long` 与 JS 安全整数"要点：超 `Number.MAX_SAFE_INTEGER`（2^53-1）丢精度 → 大 ID 字符串协议；序列化转 String 链接 `jackson-guide.md`

## 3. BigDecimal 节（#2）

- [x] 3.1 新增"BigDecimal：精确十进制"节：`new BigDecimal("0.1")`/`valueOf` vs `new BigDecimal(0.1)`（注释给误差）；`compareTo` vs `equals`（scale）；`divide(scale, RoundingMode)`；`ZERO/add/subtract/multiply/setScale`；金额/比例/展示/计算精度分层
- [x] 3.2 修正第 435-445 行 Money 示例：`double amount` → `BigDecimal amount`，构造器与 getter 相应调整

## 4. 泛型、通配符与类型擦除节（#1）

- [x] 4.1 新增"泛型、通配符与类型擦除"节：泛型免除运行期强转（原始 List 反例对照）；`List<?>`/`? extends T`/`? super T` 读写差异；PECS；原始类型风险；擦除直觉后果（运行期同类、不能 `new T[]`、不能 `instanceof List<String>`）
- [x] 4.2 将现有 Diamond Operator（第 374-387 行）折进本节
- [x] 4.3 进阶：擦除后经 `getGenericSuperclass()`/`ParameterizedType` 找回类型的原理 + 最小示例；`TypeReference` 用法链接 `fastjson-guide.md §6`

## 5. 动态值的安全转换节（#4）

- [x] 5.1 新增"动态值的安全转换：Object / JSON"节：Object 来源与类型分发；先 `instanceof` 再转（含 Java 17 pattern matching）；`Number`→BigDecimal/Integer/Long 精度与溢出（`intValue()` 截断风险）；字符串数字/`""`/Java null/JSON null 区别与判空顺序；禁止未检查强转
- [x] 5.2 以 MCP `ProductTools.resolveLimit` 式可空参数为原型示例；库取值 API 链接 `fastjson-guide.md`/`jackson-guide.md`

## 6. 常量、不可变性与共享状态边界（#6）

- [x] 6.1 扩展"常量与不可变性"节：final 只固定引用、非对象不可变（本文全量，防御性拷贝、编译时常量保留）
- [x] 6.2 新增"共享可变状态的边界"：volatile 解决可见性≠原子性、复合更新用 AtomicInteger/AtomicReference 或锁；深度链接 `multithreading-basics.md §3/§6`
- [x] 6.3 收编 ThreadLocal：线程封闭语义、必须 `remove()`（线程池串数据/泄漏）、WebFlux/虚拟线程下不可靠；说明因 multithreading-basics 明确不涵盖故收编本文

## 7. 收尾与校验

- [x] 7.1 全文统一新增代码块证据标注为"示例片段（Illustrative fragment）"，不声称可运行、不标 Verified runnable；需知结果处以注释给出
- [x] 7.2 更新 References（补 JLS 泛型章、BigDecimal、JS 安全整数、Java 17 pattern matching 等来源）
- [x] 7.3 复查交叉链接（jvm-memory-gc / multithreading-basics / fastjson / jackson / collections-framework / optional-guide）均为"本地最小讲解 + 链接"，无重复展开
- [x] 7.4 运行 `py -3 .agents/skills/guide-writing/scripts/validate_guide.py docs/Java/variables-and-types.md` 通过；Grep 复查 TOC 与全部 `#` 锚点无断裂
- [x] 7.5 按 spec 逐条 Requirement 核对实现，运行 `openspec validate expand-variables-and-types-guide` 通过
