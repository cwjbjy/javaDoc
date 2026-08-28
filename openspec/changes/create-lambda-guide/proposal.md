## Why

读者（本项目作者）当前诉求："Lambda 在 Java 中很常见，但我目前不理解。" 现有 `docs/Java/lambda-and-stream.md`（01-04，697 行）是**语法速查型**文档：直接罗列 Lambda 语法形态、方法引用与 Stream 操作清单，却未回答"Lambda 是什么、为什么存在、它的类型从哪来"。对尚未建立心智模型的读者，面对 `(a, b) -> a - b` 只能死记语法，遇到新场景（自定义函数式接口、变量捕获编译报错、`this` 指向困惑）就无从推理。

本次探索已结晶出核心心智模型：Lambda = 一段可传递的"行为"，自身没有类型，类型由目标类型（函数式接口）推断；与匿名内部类长得像但底层完全不同（`invokedynamic` vs 生成 `.class`）。需要一篇渐进式理解型指南将其固化，与 01-04 速查文档互补互链。

## What Changes

- **新增** `docs/Java/lambda-guide.md`：渐进式理解型指南（仿 `optional-guide.md` 风格，目标 JDK 17，受众为对 Lambda 尚未建立心智模型的开发者）
- 核心叙事链（每步只引入一个新概念）：
  1. 为什么需要 Lambda —— Java 中"方法不是一等公民"，传行为只能靠对象
  2. Java 7 的无奈 —— 匿名内部类：7 行样板换 1 行逻辑
  3. Java 8 的答案 —— Lambda 语法 + 函数式接口（"信封 + 便利贴"类比）
  4. 本质：目标类型推断 —— 同一个 Lambda 适配不同函数式接口
  5. 本质推论 —— 变量捕获（effectively final）、`this` 语义、`return` 规则，全部从本质推导而非罗列规则
  6. 与匿名内部类的边界 —— 底层实现对照表 + 何时仍用匿名类
  7. 项目真实代码走读 —— `FoodService`/`MarketService`/`GlobalExceptionHandler`/`SpringDocConfig` 中的 Lambda
  8. 常见误区 + 速查 + 与 01-04 互链
- **互链**：`lambda-and-stream.md`（01-04）管"怎么用"（Stream 操作速查），本指南管"为什么"（原理与心智模型）；两篇双向链接
- **证据等级**：全部代码块标注 Illustrative fragment；事实性声明（effectively final 规则、`invokedynamic`、`this` 语义）对照 JDK 17 文档核对，遵循 guide-writing 的 verification 流程

## Capabilities

### New Capabilities

- `lambda-guide`: 一篇渐进式 Lambda 理解型指南，建立"行为传递 + 目标类型推断"心智模型，解释函数式接口的角色与匿名内部类的边界，与 01-04 `lambda-and-stream.md` 互补互链

## Impact

- `docs/Java/lambda-guide.md`: 新增文件，预计 500~800 行
- `docs/Java/lambda-and-stream.md`: 最小互链改动（开头加一行指向新指南，约 1~2 行）
- `openspec/specs/lambda-guide/spec.md`: 新增 capability spec
- 无代码修改、无依赖变更、无向后兼容影响（纯文档交付物）
