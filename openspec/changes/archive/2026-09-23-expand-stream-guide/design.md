## Context

`docs/Java/stream.md`（462 行）是项目 Java 学习指南体系的一部分，当前定位为"Lambda 与 Stream API"教程。实际内容从 Stream API 基础（第 3 节）开始，跳过了 Lambda 基础和方法引用（已独立为 `Lambda.md`），直接进入中间操作、终止操作、实战案例和性能注意事项。

目标读者：使用本项目的 Java 后端开发者（Java 17 / Spring Boot），已会基本 CRUD，但在集合处理上反复踩坑——不知道何时用 Stream、何时用循环，Collectors 进阶用法靠搜索引擎拼凑，parallelStream 滥用导致生产事故。

本次为**重构扩展**：保留既有正确内容，重新组织章节结构，补齐前置上下文和企业实战深度。最终文档约 1500~2000 行。

## Goals / Non-Goals

**Goals:**

- 建立从"传统循环"到"Stream 反模式"的完整认知阶梯，让读者在任一水平都能找到切入点。
- 补齐 Collectors 概念引入，确保 `groupingBy`/`toMap` 出现时读者已有心理模型。
- 覆盖企业开发中 7 大高频场景：Collectors 进阶、toList 版本差异、flatMap 拆分链路、Lambda 变量捕获与异常、数值流统计、副作用边界、parallelStream 风险。
- 全文代码示例规范化：核心 API 首次出现必须带结果注释（如 `// [A, B, C]`）。
- 重写危险的性能建议，替换为基于项目实际（自定义线程池、响应式上下文传播）的指导。
- 与既有文档（Lambda.md、Optional.md、collections-framework.md、multithreading-basics.md）形成交叉引用网络，不重复展开。

**Non-Goals:**

- 不建验证工程，不编译运行示例（纯语法演示，与 collections-framework-guide 一致）。
- 不纳入 Optional 完整内容（已有 `Optional.md`，本文仅保留最小引用）。
- 不纳入 Lambda 基础语法和方法引用四种形式（已有 `Lambda.md`，本文仅在遍历演进中自然引出）。
- 不深入 Reactor/RxJava 响应式流（超出本文范围）。
- 不改动其他文档内容（仅新增指向它们的链接）。
- 不改写业务代码。

## Decisions

### D1. 文档定位：方案 B——集合处理完整指南

stream.md 从"Lambda 与 Stream API"重新定位为"集合处理完整指南"，标题改为「遍历、Lambda 与 Stream API」。覆盖从 for 循环到 Stream 反模式的全链路，Lambda.md 保留为语法快速参考。

替代方案 A（stream.md 只聚焦 Stream，遍历演进放 Lambda.md）——被否：用户明确选择方案 B；遍历演进是理解 Stream 动机的最佳入口，放在 stream.md 中读者不用跳转。

### D2. 章节结构：渐进式（progressive guide）

重构后的文档从"渐进式学习"角度组织，每个章节建立在前一章之上：

```
Level 0 ─ 遍历方式演进（for → enhanced for → forEach + Lambda）
Level 1 ─ Lambda 关键补充（变量捕获、受检异常）
Level 2 ─ Stream API 基础 + 中间/终止操作（现有内容微调）
Level 3 ─ Collectors 体系（概念引入 → 基础 → 进阶）
Level 4 ─ 企业实战补充（flatMap 链路、数值流、toList 版本差异）
Level 5 ─ 边界与反模式（副作用、何时不用 Stream、parallelStream 真相）
```

替代方案：保持参考型骨架（按 API 分类平铺）——被否：当前痛点恰恰是"直接扔 API 没有上下文"，渐进式能解决认知断裂。

### D3. Collectors 引入策略：先建心智模型再展开 API

在第 5 节终止操作的 `collect` 小节前，插入一个"Collectors 是什么"概念框：

- `collect()` = 动作（"把流中的元素攒起来"）
- `Collectors` = 策略工具箱（"攒成什么形状"）
- 从 `toList` → `toSet` → `toMap` → `groupingBy` → 进阶，逐步升级

这解决了"直接上来就是 groupingBy 不知道是什么"的问题。

### D4. 结果注释规范：三级密度

- **一级（必须）**：API 首次出现的示例，注释展示完整结果
- **二级（推荐）**：结果有"意外"或容易误解的变体示例
- **三级（省略）**：实战案例中重复模式的后续变体

注释风格统一为 `// → 结果` 或 `// 输出: 结果`，与第 251 行既有风格一致。

### D5. 证据等级：全部 Illustrative fragment（纯语法演示）

与 `expand-collections-framework-guide` 保持一致。所有新增代码块为纯语法演示，只保证语法与符号自洽、能说明概念，不声称可运行。需要让读者"知道结果"的地方用代码注释写出。

### D6. parallelStream 重写策略：风险清单 + 项目上下文

替换原第 8 节"数据量大就考虑并行流"为：

1. ForkJoinPool.commonPool() 的共享本质（链接 multithreading-basics.md §5）
2. 四类禁忌场景（阻塞 I/O、ThreadLocal/MDC、Reactor Context、自定义线程池）
3. 正确决策路径："压测验证 > 拍脑袋"
4. 如果确实需要并行：自定义 ForkJoinPool 包装或 CompletableFuture + 显式执行器

删除"1000 条"这个魔法数字。

### D7. ConcurrentModificationException 放在遍历演进章节

在增强 for 循环小节后立即引入 CME：

- 演示错误写法（增强 for 中 `list.remove()`）
- 解释 fail-fast 机制（modCount）
- 给出正确姿势：`Iterator.remove()`、`removeIf()`、Stream `filter` 替代

这与 collections-framework.md 的"遍历中安全删除"互补——那边偏集合视角，这边偏"为什么 Stream filter 更安全"的动机视角。

### D8. Lambda 变量捕获与异常：精简版放 stream.md，不重复 Lambda.md

Lambda.md 已有基础语法，stream.md 只补充**在 Stream 链中实际会遇到的问题**：

- effectively final 与 Stream 链中的累加器模式（为什么不能用 `int sum = 0; list.forEach(n -> sum += n)`）
- 受检异常穿透（`Function.apply` 不声明 throws，需要包装或自定义函数式接口）
- 保留业务上下文的异常处理模式

### D9. 与既有文档的交叉引用策略

| 主题                 | 本文处理                          | 链接目标                      |
| -------------------- | --------------------------------- | ----------------------------- |
| Lambda 语法/方法引用 | 遍历演进中自然引出，不展开        | `Lambda.md`                   |
| Optional             | 目录中保留引用，不展开            | `Optional.md`                 |
| 集合选型/创建        | 不展开                            | `collections-framework.md`    |
| 线程池/ForkJoinPool  | parallelStream 章节最小讲解       | `multithreading-basics.md`    |
| CompletableFuture    | parallelStream 替代方案一句话提及 | `multithreading-basics.md` §7 |

### D10. 目录锚点与编号

重构后目录预计 12 个一级章节。编号从 1 开始连续，目录中 Lambda 基础和 Optional 改为"参见"链接而非独立编号章节。完成后运行 validate_guide.py 校验锚点。

## Risks / Trade-offs

- [文档长度翻倍（462 → ~1800 行），阅读负担上升] → 渐进式结构 + 顶部 TOC + 每章开头一句话定位，支持跳读。
- [纯语法演示不验证，可能出现编不过的示例] → 严格标注 Illustrative fragment，符号逐个自检，保持与 JDK 17 API 一致。
- [与 Lambda.md / collections-framework.md 内容边界模糊] → D9 交叉引用表明确划分；重叠处"本地最小讲解 + 链接"。
- [parallelStream 重写可能过于保守，吓退读者使用并行] → 保留"如果确实需要并行"的正确路径，不是"永远不要用"。
- [Collectors 进阶章节信息密度高] → 每个 Collector 配一个业务场景 + 结果注释，不堆砌 API 签名。
- [目录锚点断裂] → validate_guide.py + Grep 双重复查。
