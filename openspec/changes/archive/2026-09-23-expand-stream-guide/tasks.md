## 1. 文档骨架重组与目录更新

- [x] 1.1 修改文档标题为「遍历、Lambda 与 Stream API」，更新顶部描述
- [x] 1.2 重写目录（TOC），反映新章节结构（约 12 个一级章节），Lambda 基础和 Optional 改为"参见"链接
- [x] 1.3 调整既有章节编号（原第 3~8 节顺移），确保锚点与新目录一致
- [x] 1.4 运行 `py -3 .agents/skills/guide-writing/scripts/validate_guide.py docs/Java/stream.md` 校验目录锚点

## 2. 新增：遍历方式演进章节

- [x] 2.1 编写传统 for 循环示例（遍历商品列表打印名称），标注优势（索引控制、可 break/continue）与局限（样板代码多）
- [x] 2.2 编写增强 for 循环示例（同一场景），标注优势（简洁）与局限（无索引、不能安全修改集合）
- [x] 2.3 编写 forEach + Lambda 示例（同一场景），标注优势（函数式风格、可组合）与局限（无法 break/continue、异常处理受限）
- [x] 2.4 编写 ConcurrentModificationException 警告小节：增强 for 中 `list.remove()` 的错误示范 → fail-fast 机制解释（modCount）→ 正确姿势（`Iterator.remove()`、`removeIf()`、Stream `filter`）
- [x] 2.5 编写过渡段落：从 forEach + Lambda 的局限自然引出"Stream 解决了什么"
- [x] 2.6 所有示例配结果注释

## 3. 新增：Lambda 关键补充（变量捕获与异常）

- [x] 3.1 编写 effectively final 规则说明 + Stream 链中累加器反模式示例（`int sum = 0; list.forEach(n -> sum += n)` 编译失败）
- [x] 3.2 编写受检异常穿透问题：`Function.apply` 不声明 throws → 包装模式（try-catch + RuntimeException）→ 自定义 ThrowingFunction 接口
- [x] 3.3 编写保留业务上下文的异常处理模式：数据转换失败时保留字段名、记录号、原始值
- [x] 3.4 添加交叉引用链接到 `Lambda.md`

## 4. 扩充：Stream 中间操作

- [x] 4.1 扩充 flatMap 小节：新增"处理逗号分隔字段"完整链路（过滤空值 → split → 处理空字符串 → trim/distinct 由业务决定）
- [x] 4.2 补充 `map(Arrays::asList)` 与 `flatMap(Arrays::stream)` 的对比示例 + 结果注释
- [x] 4.3 为既有中间操作示例补充结果注释（filter、map、distinct、sorted、limit/skip）

## 5. 重构：Stream 终止操作与 Collectors 体系

- [x] 5.1 在 collect 小节前新增"Collectors 是什么"概念引入框（collect = 动作，Collectors = 策略）
- [x] 5.2 重组 collect 小节：toList → toSet → toMap（基础）→ groupingBy（基础）→ partitioningBy → joining，每个配结果注释
- [x] 5.3 新增 Collectors 进阶子节：`groupingBy(key, LinkedHashMap::new, downstream)` 保序分组
- [x] 5.4 新增 Collectors 进阶子节：`Collectors.mapping(...)` 分组后抽取字段
- [x] 5.5 新增 Collectors 进阶子节：`Collectors.toCollection(LinkedHashSet::new)` 去重且保序
- [x] 5.6 新增 Collectors 进阶子节：`toMap` 四参数版本（keyMapper, valueMapper, mergeFunction, mapSupplier）+ 重复 key 抛异常警告
- [x] 5.7 新增 Collectors 进阶子节：`collectingAndThen` 不可变包装 / 二次转换
- [x] 5.8 新增 Collectors 进阶子节：嵌套 `groupingBy` 多级业务维度统计
- [x] 5.9 新增 `Stream.toList()` vs `Collectors.toList()` 版本差异对比表（Java 16+ vs Java 8、不可变 vs 无保证、`toCollection(ArrayList::new)` 明确可变）

## 6. 新增：数值流与统计

- [x] 6.1 编写 `mapToInt` / `mapToLong` / `mapToDouble` 基础示例 + 结果注释
- [x] 6.2 编写 `sum`、`average`、`summaryStatistics` 示例
- [x] 6.3 编写金额场景警告：double 精度丢失 → BigDecimal + reduce 的正确模式

## 7. 新增：何时不用 Stream（副作用边界）

- [x] 7.1 编写"不要强行 Stream"场景清单：构建树、更新多个外部容器、需要 break/continue、复杂异常恢复
- [x] 7.2 编写副作用反模式示例：在 map/filter/peek 中修改外部可变状态
- [x] 7.3 编写 peek 使用边界：只用于临时诊断，不承载业务逻辑
- [x] 7.4 给出"何时回归普通循环"的决策建议

## 8. 重写：parallelStream 性能建议

- [x] 8.1 删除原第 8 节"大数据集(>1000)考虑并行流"内容
- [x] 8.2 编写 ForkJoinPool.commonPool() 共享本质说明 + 链接 multithreading-basics.md §5
- [x] 8.3 编写四类禁忌场景：阻塞 I/O（HTTP/DB/Redis/文件）、ThreadLocal/MDC 依赖、Reactor Context 传播、项目已有自定义线程池
- [x] 8.4 编写正确决策路径："压测验证 > 拍脑袋"，删除魔法数字
- [x] 8.5 编写"如果确实需要并行"的正确替代：自定义 ForkJoinPool 包装 / CompletableFuture + 显式执行器

## 9. 实战案例更新与结果注释

- [x] 9.1 审查既有实战案例（案例 1~3），补充结果注释
- [x] 9.2 考虑新增 1~2 个企业级实战案例（如：多级分组 + mapping 组合、flatMap 拆分 + 去重 + 保序完整链路）
- [x] 9.3 更新快速参考章节，反映新增 API

## 10. 校验与收尾

- [x] 10.1 运行 `py -3 .agents/skills/guide-writing/scripts/validate_guide.py docs/Java/stream.md`，修复目录锚点与代码围栏问题
- [x] 10.2 用 Grep 复查所有 `#` 内部链接无断裂
- [x] 10.3 用 Grep 复查所有交叉引用链接（Lambda.md、Optional.md、collections-framework.md、multithreading-basics.md）目标文件存在
- [x] 10.4 复查全文结果注释覆盖率：核心 API 首次出现是否都有结果注释
- [x] 10.5 确认文档总行数在 1500~2000 行范围内
- [x] 10.6 对照 spec 的 requirement 逐条自查满足情况
