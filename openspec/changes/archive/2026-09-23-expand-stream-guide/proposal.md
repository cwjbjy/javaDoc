## Why

`docs/Java/stream.md`（462 行）当前标题为「Lambda与Stream API」，但实际内容存在以下结构性问题：

1. **缺失前置上下文**：目录声称有「1. Lambda 表达式基础」和「2. 方法引用」，实际内容在 `Lambda.md` 中，stream.md 从第 3 节「Stream API 基础」直接开始。读者在没有遍历方式演进背景的情况下直接面对 Stream，无法理解"为什么需要 Stream"。
2. **Collectors 概念无引入**：文档第 5 节终止操作中直接使用 `Collectors.groupingBy`、`Collectors.toMap` 等高阶 API，但从未解释 Collectors 是什么、collect 与 Collectors 的关系，读者完全靠猜。
3. **性能建议危险**：第 8 节声称"大数据集(>1000)考虑并行流"，未提及 `parallelStream()` 共享 ForkJoinPool.commonPool()、不适合阻塞 I/O、不兼容 ThreadLocal/MDC 等关键风险，可能误导生产实践。
4. **缺乏企业实战深度**：`Collectors.groupingBy` 保序重载、`toMap` 重复 key 异常、`flatMap` 拆分逗号分隔字段、`Stream.toList()` 与 `Collectors.toList()` 的版本/可变性差异、数值流统计、Lambda 变量捕获与受检异常、Stream 副作用边界等企业高频场景均未覆盖。
5. **代码示例缺少结果注释**：大部分示例只有输入没有输出，读者无法验证理解是否正确（第 251 行是少数例外）。

本次变更将 stream.md 重构为"集合处理完整指南"（方案 B），覆盖从传统循环到 Stream 反模式的完整认知阶梯，使其成为项目 Java 后端开发者的一站式参考。

## What Changes

### 新增：遍历方式演进章节（插入第 3 节之前）

- 以同一业务场景（遍历商品列表并打印名称）展示三种写法：传统 `for` 循环 → 增强 `for` 循环 → `forEach + Lambda`
- 每种方式标注优势与局限，形成认知阶梯
- 新增 `ConcurrentModificationException` 警告：增强 for 中修改集合的后果、`Iterator.remove` 与 `removeIf` 的正确姿势
- 自然过渡到 Lambda 和 Stream API

### 新增：Collectors 概念引入（在终止操作 collect 小节前）

- 解释 `collect()` 是动作、`Collectors` 是策略的关系
- 从 `toList`/`toSet` 入门，逐步引出 `groupingBy`/`toMap`
- 确保读者在遇到高阶 Collectors 时已有心理模型

### 新增：Collectors 进阶章节

- `groupingBy(key, LinkedHashMap::new, downstream)`：分组同时保序
- `Collectors.mapping(...)`：分组后抽取字段
- `Collectors.toCollection(LinkedHashSet::new)`：去重且保序
- `toMap(keyMapper, valueMapper, mergeFunction, mapSupplier)`：处理重复 key 并指定 Map 类型
- `collectingAndThen`：收集后不可变包装或二次转换
- 嵌套 `groupingBy`：多级业务维度统计
- 重点强调：`Collectors.toMap` 遇重复 key 抛 `IllegalStateException`，必须由业务决定合并策略

### 新增：Stream.toList() 与 Collectors.toList() 版本差异

- `Stream.toList()`：Java 16+，结果不可修改
- `Collectors.toList()`：Java 8 可用，不保证具体实现类型或可变性
- 需要明确可变结果时：`collect(Collectors.toCollection(ArrayList::new))`

### 扩充：flatMap 处理逗号分隔字段

- 完整链路：先过滤空值 → 拆分 → 可能产生空字符串的处理
- `map(Arrays::asList)` 与 `flatMap(Arrays::stream)` 的区别
- trim、distinct、保序由业务决定

### 新增：Lambda 捕获变量与异常处理

- Lambda 只能捕获 `final` 或 effectively final 的局部变量
- 受检异常不能直接穿透常见函数式接口
- 数据转换失败应保留字段、记录号和原始值上下文，不用统一包装异常掩盖业务语义

### 新增：数值流与统计

- `mapToInt` / `mapToLong` / `mapToDouble`
- `sum`、`average`、`summaryStatistics`
- 金额不应使用 double 聚合，应使用 BigDecimal

### 新增：何时不用 Stream（副作用边界）

- 构建树、更新多个外部容器、需要 break/continue、复杂异常恢复时，普通循环更清晰
- 不要在 `map`、`filter`、`peek` 中修改外部可变状态
- `peek` 只用于临时诊断，不能承载业务逻辑

### 重写：parallelStream 性能建议（替换原第 8 节）

- `parallelStream()` 使用公共 ForkJoinPool，与所有默认并行流共享
- 不适合阻塞 HTTP、数据库、Redis、文件操作
- 不适合依赖 ThreadLocal、MDC、Reactor Context 的流程
- 项目已有自定义线程池和响应式上下文传播，不能因集合较大就改用 `parallelStream()`
- 是否并行化要通过压测验证，而非按数据条数决定

### 全文改造：结果注释规范化

- 核心示例（首次出现的 API）必须加结果注释
- 变体示例：只在结果有"意外"时加注释
- 实战案例：给出输入数据描述 + 输出结构

### 目录重组

- 更新目录编号，反映新章节结构
- 删除目录中指向 Lambda.md/Optional.md 的虚条目（改为交叉引用链接）

## Capabilities

### New Capabilities

- `stream-guide`: `docs/Java/stream.md` 的企业化重构——遍历方式演进与 ConcurrentModificationException、Collectors 概念引入与进阶（groupingBy 保序/mapping/toCollection/toMap 重复 key/collectingAndThen/嵌套分组）、Stream.toList() 版本差异、flatMap 拆分链路、Lambda 变量捕获与受检异常、数值流与统计、Stream 副作用边界、parallelStream 风险重写、结果注释规范化；示例为纯语法演示，重叠内容链接既有文档。

### Modified Capabilities

（无——`openspec/specs/` 下现有 spec 均为项目功能 spec，与本文档无关）

## Impact

- `docs/Java/stream.md`: 从 462 行扩展至约 1500~2000 行；新增遍历演进、Collectors 引入与进阶、数值流、副作用边界等章节；重写第 8 节性能建议；全文补充结果注释；目录重组。
- 无业务代码修改、无依赖变更、无向后兼容影响（纯文档交付物）。
- 交叉链接：`docs/Java/Lambda.md`（Lambda 基础与方法引用）、`docs/Java/Optional.md`（Optional 最佳实践）、`docs/Java/collections-framework.md`（集合选型）、`docs/Java/multithreading-basics.md`（线程池与 ForkJoinPool）。
- 结构校验：完成后运行 `py -3 .agents/skills/guide-writing/scripts/validate_guide.py docs/Java/stream.md`。
