## Context

项目 `docs/Java/` 下存在两类文档：(a) 01-XX 编号的语法速查型（如 `lambda-and-stream.md`，697 行，罗列语法形态与 Stream 操作清单）；(b) `xx-guide.md` 渐进理解型（如 `optional-guide.md`：目录 + 从痛点出发 + 层层递进，每步只引入一个新概念）。读者诉求"Lambda 不理解"，说明 (a) 类文档对未建立心智模型的读者不够——速查型文档假设读者已懂"为什么"，只教"怎么写"。

本次探索已结晶出核心心智模型：

- Lambda = 一段可传递的"行为"（Java 中方法不是一等公民，Java 8 之前只能把行为包进匿名内部类对象）
- Lambda 表达式自身没有类型，类型由**目标类型**（函数式接口）推断：同一个 `s -> s.isEmpty()` 可适配任何"入 String 出 boolean"的函数式接口
- 函数式接口 = "信封规格"（参数形状 + 返回形状），Lambda = "便利贴内容"
- Lambda 不是匿名内部类的简写：底层是 `invokedynamic`（不生成 `.class`、`this` 指向外部类、只捕获 effectively final 变量）

项目真实代码已有 5 处 Lambda（`FoodService`、`MarketService`、`WebMvcConfig`、`SpringDocConfig`、`GlobalExceptionHandler`），可作为"你已经在用，只是没意识到"的走读素材。

现有 in-progress 变更 `expand-completablefuture-guide`、`create-microservices-guide` 与本文档无内容交集（多线程/微服务主题），互不影响。

## Goals / Non-Goals

**Goals:**

- 读者读完能回答"Lambda 到底是什么"：一段可传递的行为，类型由目标类型推断
- 建立"信封（函数式接口）+ 便利贴（行为）"心智模型，解释为什么必须有函数式接口这个中介
- 从本质推导语法规则（变量捕获、`this`、`return`），而不是罗列规则让读者死记
- 讲清与匿名内部类的边界：用途相近、底层不同，以及何时仍需匿名类
- 用项目真实代码走读建立熟悉感
- 与 01-04 `lambda-and-stream.md` 双向互链，分工明确不重复

**Non-Goals:**

- 不重复 Stream API 操作清单（`filter`/`map`/`collect` 等归属 01-04）
- 不深入 JVM `invokedynamic` 实现细节（只给结论：不生成 `.class`、惰性创建、开销低于匿名类）
- 不涉及高阶函数理论（柯里化、偏应用、Monad 等）
- 不枚举 `java.util.function` 全部 43 个接口（只讲 Function/Predicate/Consumer/Supplier + 派生变体）
- 不修改项目业务代码（纯文档交付物）

## Decisions

### 1. 新建独立文档，而非扩充 01-04

- 01-04 定位是语法速查，"渐进叙事 + 类比 + 原理推导"插入开头会破坏其"快速查阅"节奏；项目已有先例（`optional-guide.md` 与 `collections-framework.md` 并存，理解型与速查型分工）
- 备选方案：在 01-04 开头加"为什么需要 Lambda"章节——否决，读者诉求是完整心智模型，一节装不下"本质 + 推论 + 边界"的叙事弧

### 2. 叙事顺序：痛点 → 语法 → 本质（与 optional-guide.md 同构）

- §1 先讲"传行为"的痛点与 Java 方法不是一等公民（含 JS 箭头函数对比，利用读者前端背景）
- §2 匿名内部类演示痛点之痛（7 行样板换 1 行逻辑），让读者产生"确实该简化"的共鸣后再给答案
- §3~§4 给 Lambda 语法 + 函数式接口 + 目标类型推断（核心"啊哈时刻"：同一个 Lambda 适配多个接口）
- §5~§6 本质推论与边界（变量捕获、`this`、与匿名类对照表）
- 备选方案：先给语法再补原理（速查型顺序）——否决，未建立"为什么"之前读者会退回死记

### 3. 核心类比："信封（函数式接口）+ 便利贴（行为）"，并标注类比边界

- 信封规定参数形状与返回形状；便利贴内容自动成为信封唯一方法的实现
- 类比后紧跟严格定义：SAM（Single Abstract Method）规则、`@FunctionalInterface` 的作用（编译器检查而非运行时必需）
- 明确标注"类比止于此处"：便利贴可改写，Lambda 捕获的变量不可改（effectively final）——防止类比误导

### 4. 本质推论从"JVM 捕获值副本"推导，而非罗列规则

- effectively final 规则 → 推导自"JVM 把捕获变量作为值副本传入"，改副本无意义故编译期禁止
- `this` 语义 → 推导自"Lambda 不生成新类实例，`this` 仍是外部类的 this"
- `return` 规则 → 推导自"表达式体有返回值、块体需要显式 return"
- 事实源：JDK 17 语言规范（JLS §15.27.2/§15.27.4）与 javadoc，实施时核对

### 5. 项目真实代码走读单独成节（§7）

- 走读 `FoodService`（filter）、`MarketService`（orElseThrow）、`GlobalExceptionHandler`（map/reduce 链）、`SpringDocConfig`（多行 Lambda 自定义 OpenAPI），每处标注"便利贴内容 + 信封规格"，让读者在熟悉代码中验证心智模型
- 备选方案：真实代码散落各节作为示例——否决，集中走读形成"毕业检验"效应

### 6. 与 01-04 的分工边界与互链

- 01-04 拥有：Lambda 语法五形态速查、方法引用四种形态、Stream 全部操作、Optional 最佳实践
- 本指南拥有：为什么需要 Lambda、函数式接口概念与角色、目标类型推断、变量捕获/`this`/`return` 原理、与匿名类的边界、常见误区
- 互链方式：本指南 §8 链接 01-04（"语法与 Stream 用法速查请见……"）；01-04 开头加一行链接本指南（"想理解原理请见……"）

### 7. 证据等级与验证

- 全部代码块标注 Illustrative fragment（与项目既有指南一致，不声称可运行）
- 实施后运行 guide-writing 的 `validate_guide.py` 校验目录锚点与代码围栏，并运行 `openspec validate create-lambda-guide`

## Risks / Trade-offs

- [与 01-04 内容重叠] → 决策 6 的分工边界；实施后用 grep 复查两篇的"语法形态"与"Stream 操作"段落，确保单点拥有
- [类比（信封/便利贴）误导读者] → 决策 3：类比后紧跟严格定义 + "类比止于此处"标注
- [事实性错误（如声称 Lambda 是语法糖、或声称匿名类变量捕获规则与 Lambda 相同）] → 实施时对照 JLS §15.27 与 JDK 17 javadoc 核对；此两类错误是同类教程最常见翻车点
- [篇幅失控，偏离"理解型"定位] → 明确排除项（Non-Goals）；每节写完自问"读者会不会因此对本质的理解更深"
- [指南间链接漂移] → 互链用相对路径 + 文件名，不用行号锚点

## Migration Plan

纯文档交付物，无部署与回滚问题。实施顺序：先写 §1~§4（核心心智模型）→ §5~§6（推论与边界）→ §7 走读（对照真实代码）→ §8 速查与互链 → 在 01-04 开头加最小互链 → 运行 `validate_guide.py` 与 `openspec validate create-lambda-guide`。

## Open Questions

- 指南文件名是否加编号前缀（如 `01-04b`）？（倾向：否，保持 `lambda-guide.md` 与 `optional-guide.md`/`dto-guide.md` 的命名惯例一致）
- 是否在 §8 加入一张"该用 Lambda / 该用匿名类 / 该用普通方法"的决策表？（倾向：是，作为 §6 边界的落地收尾）
