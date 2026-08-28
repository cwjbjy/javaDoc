## ADDED Requirements

### Requirement: 渐进式叙事结构

指南 SHALL 采用"痛点 → 语法 → 本质"的渐进式叙事（仿 `optional-guide.md`）：先讲 Java 中"方法不是一等公民"的痛点与匿名内部类的样板负担，再给 Lambda 语法与函数式接口概念，最后揭示"目标类型推断"本质；每步只引入一个新概念，SHALL NOT 在一开始罗列语法规则。

#### Scenario: 读者按顺序建立心智模型

- **WHEN** 读者从未理解过 Lambda，从头阅读指南
- **THEN** 在见到第一个 Lambda 语法之前，已理解"为什么需要把行为装进接口"的动机

### Requirement: 本质讲解——行为传递与目标类型推断

指南 SHALL 明确讲清 Lambda 的本质：一段可传递的"行为"，其自身没有类型，类型由目标类型（函数式接口）推断。SHALL 包含核心演示：同一个 Lambda（如 `s -> s.isEmpty()`）适配 `Predicate<String>`、`Function<String, Boolean>` 及自定义函数式接口。

#### Scenario: 读者解释 Lambda 的类型来源

- **WHEN** 读者被问"`(a, b) -> a - b` 的类型是什么"
- **THEN** 能回答"由赋值的函数式接口目标类型决定，Lambda 本身不声明类型"

### Requirement: 函数式接口概念与角色

指南 SHALL 介绍函数式接口（SAM）概念：只有一个抽象方法的接口，作为 Lambda 的"形状锚点"；SHALL 说明 `@FunctionalInterface` 是编译器检查注解而非运行时必需；SHALL 说明默认方法与静态方法不破坏 SAM 规则。SHALL 介绍高频内置接口（Function/Predicate/Consumer/Supplier 及其常见派生变体）的"信封规格"（参数形状 + 返回形状）。

#### Scenario: 读者识别自定义函数式接口

- **WHEN** 读者看到一个只含一个抽象方法的接口
- **THEN** 能识别它可作为 Lambda 的目标类型，并写出对应的 Lambda 实现

### Requirement: 与匿名内部类的边界

指南 SHALL 讲清 Lambda 与匿名内部类的区别：Lambda 不是匿名内部类的简写。SHALL 以对照表形式给出：编译产物（匿名类生成 `.class`，Lambda 使用 `invokedynamic` 不生成 `.class`）、`this` 语义（匿名类指向自身实例，Lambda 指向外部类实例）、变量捕获规则、能否有字段与多方法。SHALL 说明何时仍应使用匿名内部类（需要多方法、需要自身状态、需要显式构造器逻辑）。

#### Scenario: 读者在 Lambda 与匿名类之间做选择

- **WHEN** 读者需要传入"带状态的、含多个方法"的行为
- **THEN** 能识别出 Lambda 不适用，应使用匿名内部类或具名类

### Requirement: 本质推论——变量捕获、this、return

指南 SHALL 从"JVM 捕获变量值副本"推导 effectively final 规则（Lambda 体内不得修改捕获的局部变量），并给出编译报错示例；SHALL 从"Lambda 不生成新实例"推导 `this` 语义；SHALL 区分表达式体（隐式返回值）与块体（需显式 `return`），并说明 Lambda 的 `return` 只从 Lambda 返回、不从外层方法返回。

#### Scenario: 读者预判变量捕获编译错误

- **WHEN** 读者在 Lambda 体内尝试修改外部局部变量
- **THEN** 能预先判断会编译失败，并解释原因（捕获的是值副本）

### Requirement: 项目真实代码走读

指南 SHALL 包含一节项目真实代码走读，至少覆盖 `FoodService`（`filter`）、`MarketService`（`orElseThrow`）、`GlobalExceptionHandler`（`map`/`reduce` 链）、`SpringDocConfig`（多行 Lambda）中的 Lambda 用法；每处 SHALL 标注对应的"便利贴内容"与"信封规格"（目标函数式接口）。

#### Scenario: 读者在熟悉代码中验证心智模型

- **WHEN** 读者读完走读节再看项目代码
- **THEN** 能指出每处 Lambda 的目标类型接口与其行为内容

### Requirement: 与 01-04 lambda-and-stream.md 互补互链

本指南 SHALL NOT 重复 01-04 的 Stream API 操作清单与方法引用详解；两篇 SHALL 双向互链：本指南链接 01-04 作为语法/Stream 用法速查，01-04 开头 SHALL 加一行链接本指南作为原理入口。

#### Scenario: 两篇指南分工无重叠

- **WHEN** 读者分别查阅两篇指南的"语法形态"与"Stream 操作"内容
- **THEN** 语法形态与 Stream 操作只由 01-04 详细拥有，本指南仅引用不展开

### Requirement: 高频易错点与误区清单

指南 SHALL 包含常见误区清单，至少包括："Lambda 是语法糖"、"Lambda 是匿名内部类的简写"、"捕获的变量可以在 Lambda 内修改"、"Lambda 内 `this` 指向 Lambda 自身"、"无节制使用 Lambda 牺牲可读性"；每条 SHALL 给出正确认知。

#### Scenario: 读者识别常见错误说法

- **WHEN** 读者在网络资料中看到上述错误说法
- **THEN** 能依据指南指出其错误并给出正确解释

### Requirement: 范围排除（入门定位）

指南 SHALL NOT 深入 `invokedynamic` 的实现细节（只给"不生成 `.class`、惰性创建、开销低于匿名类"的结论）；SHALL NOT 涉及高阶函数理论（柯里化、偏应用、Monad 等）；SHALL NOT 枚举 `java.util.function` 全部接口（只覆盖高频接口与派生变体）。

#### Scenario: 指南保持入门定位

- **WHEN** 读者通读全篇
- **THEN** 不遇到上述排除主题的展开讲解，篇幅控制在渐进式入门指南定位内
