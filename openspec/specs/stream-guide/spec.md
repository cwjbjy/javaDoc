## ADDED Requirements

### Requirement: 遍历方式演进与认知阶梯

指南 SHALL 在 Stream API 内容之前提供遍历方式演进章节，以同一业务场景（遍历商品列表并打印名称）依次展示：传统 `for` 循环、增强 `for` 循环、`forEach + Lambda` 三种写法。每种写法 SHALL 标注其优势与局限，并形成从命令式到函数式的认知阶梯，最终自然过渡到 Stream API。

#### Scenario: 读者理解为什么需要 Stream

- **WHEN** 读者从第 1 章开始阅读
- **THEN** 能通过三种遍历方式的对比，说出每种方式解决了什么问题、引入了什么局限，以及 Stream 如何进一步解决这些局限

### Requirement: ConcurrentModificationException 警告

指南 SHALL 在增强 for 循环小节后引入 `ConcurrentModificationException`：演示增强 for 中 `list.remove()` 的错误写法、解释 fail-fast 机制（modCount）、给出正确姿势（`Iterator.remove()`、`removeIf()`、Stream `filter` 替代）。

#### Scenario: 读者避免遍历中修改集合的陷阱

- **WHEN** 读者需要在遍历过程中删除元素
- **THEN** 能识别增强 for 中直接 remove 会抛 CME，并选择 Iterator.remove / removeIf / Stream filter 中的合适方案

### Requirement: Collectors 概念引入

指南 SHALL 在使用 `Collectors.groupingBy`、`Collectors.toMap` 等高阶 API 之前，提供 Collectors 概念引入：解释 `collect()` 是终止动作、`Collectors` 是收集策略的关系，从 `toList`/`toSet` 入门逐步引出高阶 Collector。

#### Scenario: 读者首次遇到 groupingBy 时已有心理模型

- **WHEN** 读者阅读到 `Collectors.groupingBy` 示例
- **THEN** 已理解 Collectors 是"告诉 Stream 怎么把元素攒成结果"的策略工具箱，不会困惑"groupingBy 从哪来的"

### Requirement: Collectors 进阶覆盖企业高频模式

指南 SHALL 覆盖以下 Collectors 进阶用法，每个配业务场景示例和结果注释：

- `groupingBy(key, LinkedHashMap::new, downstream)`：分组同时保序
- `Collectors.mapping(...)`：分组后抽取字段
- `Collectors.toCollection(LinkedHashSet::new)`：去重且保序
- `toMap(keyMapper, valueMapper, mergeFunction, mapSupplier)`：处理重复 key 并指定 Map 类型
- `collectingAndThen`：收集后不可变包装或二次转换
- 嵌套 `groupingBy`：多级业务维度统计

指南 SHALL 重点强调 `Collectors.toMap` 遇重复 key 抛 `IllegalStateException`，必须由业务决定保留前者、后者还是合并。

#### Scenario: 读者处理分组保序需求

- **WHEN** 读者需要按某字段分组且保持原始顺序
- **THEN** 能使用 `groupingBy(key, LinkedHashMap::new, downstream)` 而非事后排序

#### Scenario: 读者处理 toMap 重复 key

- **WHEN** 读者使用 `Collectors.toMap` 且数据源可能有重复 key
- **THEN** 知道必须提供 mergeFunction，否则会抛 IllegalStateException，并能根据业务选择保留策略

### Requirement: Stream.toList() 与 Collectors.toList() 版本差异

指南 SHALL 对比说明：

- `Stream.toList()`：Java 16+，结果不可修改（unmodifiable）
- `Collectors.toList()`：Java 8 可用，不保证具体实现类型或可变性契约
- 需要明确可变结果时：`collect(Collectors.toCollection(ArrayList::new))`

#### Scenario: 读者选择正确的收集方式

- **WHEN** 读者需要收集 Stream 结果为 List
- **THEN** 能根据目标 Java 版本和是否需要修改结果列表，选择 `Stream.toList()` / `Collectors.toList()` / `Collectors.toCollection(ArrayList::new)`

### Requirement: flatMap 处理逗号分隔字段完整链路

指南 SHALL 补充 flatMap 处理逗号分隔字段的完整链路：先过滤空值 → split 拆分 → 处理拆分后可能产生的空字符串 → 是否 trim/distinct/保序由业务决定。SHALL 对比 `map(Arrays::asList)` 与 `flatMap(Arrays::stream)` 的区别。

#### Scenario: 读者处理逗号分隔的数据库字段

- **WHEN** 读者需要将 "A,B,C" 格式的字段拆分为独立元素列表
- **THEN** 能编写完整的 filter → flatMap → split → 后处理链路，并知道需要处理空值和空字符串

### Requirement: Lambda 变量捕获与受检异常

指南 SHALL 补充：

- Lambda 只能捕获 `final` 或 effectively final 的局部变量，演示 Stream 链中累加器反模式
- 受检异常不能直接穿透常见函数式接口（`Function.apply` 不声明 throws），给出包装模式
- 数据转换失败时应保留字段名、记录号和原始值上下文，不用统一包装异常掩盖业务语义

#### Scenario: 读者在 Stream 中需要累加

- **WHEN** 读者试图在 forEach Lambda 中修改外部局部变量
- **THEN** 理解编译失败原因（effectively final），并知道应使用 reduce / collect / 数值流替代

#### Scenario: 读者在 Stream 中遇到受检异常

- **WHEN** 读者在 map 中调用抛受检异常的方法
- **THEN** 能选择合适的包装模式，且异常信息保留业务上下文

### Requirement: 数值流与统计

指南 SHALL 新增数值流章节：`mapToInt` / `mapToLong` / `mapToDouble` 基础用法、`sum`/`average`/`summaryStatistics` 统计操作。SHALL 警告金额场景不应使用 double 聚合，应使用 BigDecimal + reduce。

#### Scenario: 读者处理金额聚合

- **WHEN** 读者需要对商品价格求和
- **THEN** 知道不能用 `mapToDouble(...).sum()`（精度丢失），应使用 `map(Product::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add)`

### Requirement: Stream 副作用边界

指南 SHALL 新增"何时不用 Stream"章节，覆盖：

- 构建树、更新多个外部容器、需要 break/continue、复杂异常恢复时，普通循环通常更清晰
- 不要在 `map`、`filter`、`peek` 中修改外部可变状态
- `peek` 只用于临时诊断，不能承载业务逻辑

#### Scenario: 读者决定用 Stream 还是循环

- **WHEN** 读者面对需要 break/continue 或修改多个外部容器的逻辑
- **THEN** 能判断此场景不适合 Stream，选择普通循环

#### Scenario: 读者正确使用 peek

- **WHEN** 读者在 Stream 链中使用 peek
- **THEN** 仅用于调试日志输出，不在 peek 中执行业务逻辑或修改外部状态

### Requirement: parallelStream 风险重写

指南 SHALL 替换原有"数据量大就考虑并行流"建议，改为：

- `parallelStream()` 使用公共 `ForkJoinPool.commonPool()`，与所有默认并行流共享
- 不适合阻塞 HTTP、数据库、Redis、文件操作
- 不适合依赖 ThreadLocal、MDC、Reactor Context 的流程
- 项目已有自定义线程池和响应式上下文传播，不能因集合较大就改用 `parallelStream()`
- 是否并行化要通过压测验证，而非按数据条数决定
- 如果确实需要并行：给出自定义 ForkJoinPool 包装或 CompletableFuture + 显式执行器的替代方案

指南 SHALL NOT 包含"超过 N 条数据就用 parallelStream"类型的魔法数字建议。

#### Scenario: 读者面对大集合处理

- **WHEN** 读者有 10000 条数据需要处理
- **THEN** 不会条件反射地使用 parallelStream，而是评估操作是否 CPU 密集、是否有阻塞、是否依赖线程上下文，最终通过压测决定

#### Scenario: 读者在 Spring Boot 项目中考虑并行

- **WHEN** 读者在项目中使用 ThreadLocal（如 MDC 日志追踪）或响应式上下文
- **THEN** 知道 parallelStream 会丢失这些上下文，选择 CompletableFuture + 自定义执行器

### Requirement: 结果注释规范化

指南中所有核心 API 首次出现的示例 SHALL 配有结果注释（如 `// → [A, B, C]` 或 `// 输出: ...`），让读者无需运行即可验证理解。变体示例在结果有"意外"时 SHALL 加注释。

#### Scenario: 读者阅读新 API 示例

- **WHEN** 读者首次看到某个 Stream/Collectors API 的示例
- **THEN** 能通过结果注释直接看到输入 → 输出的对应关系，无需心智执行代码

### Requirement: 交叉引用不重复展开

指南 SHALL 与以下文档形成交叉引用而非重复内容：

- `Lambda.md`：Lambda 基础语法、方法引用四种形式
- `Optional.md`：Optional 完整用法
- `collections-framework.md`：集合选型与创建
- `multithreading-basics.md`：线程池、ForkJoinPool、CompletableFuture

重叠处 SHALL 采用"本地最小讲解 + 链接"模式。

#### Scenario: 读者需要深入 Lambda 语法

- **WHEN** 读者在遍历演进章节中对 Lambda 语法产生疑问
- **THEN** 能找到指向 Lambda.md 的明确链接，而非在 stream.md 中重复展开
