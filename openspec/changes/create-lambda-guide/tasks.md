## 1. 为什么需要 Lambda（§1）

- [x] 1.1 "传行为"的需求场景：排序/过滤/回调，从命令式 for 循环的重复写起
- [x] 1.2 第一性原理：Java 中方法不是一等公民，不能像 JS 箭头函数那样自由传递（JS 对比示例，利用读者前端背景）
- [x] 1.3 抛出问题：Java 7 怎么把"比较逻辑"传给 `Collections.sort`？

## 2. Java 7 的无奈：匿名内部类（§2）

- [x] 2.1 匿名内部类完整写法：`new Comparator<Integer>() { @Override public int compare... }`
- [x] 2.2 痛点量化：7 行样板代码只为 1 行真实逻辑
- [x] 2.3 引入"样板 vs 意图"的概念，为后续"Lambda 只留意图"铺垫

## 3. Java 8 的答案：Lambda 语法 + 函数式接口（§3）

- [x] 3.1 匿名内部类 → Lambda 的逐行消减对照（`(a, b) -> a - b` 是怎么"瘦身"来的）
- [x] 3.2 语法形态：无参 / 单参省括号 / 多参 / 表达式体 / 块体（示例不重复 01-04 速查，聚焦"为什么可以这样省"）
- [x] 3.3 "信封 + 便利贴"类比：函数式接口 = 信封规格（参数形状 + 返回形状），Lambda = 便利贴内容
- [x] 3.4 严格定义：SAM 规则、`@FunctionalInterface` 的作用（编译器检查，非运行时必需）、默认方法/静态方法不破坏 SAM
- [x] 3.5 标注"类比止于此处"：便利贴可改写，Lambda 捕获变量不可改（预告 §5）

## 4. 本质：目标类型推断（§4）

- [x] 4.1 核心演示：同一个 `s -> s.isEmpty()` 适配 `Predicate<String>` / `Function<String, Boolean>` / 自定义接口
- [x] 4.2 目标类型上下文清单：变量赋值、方法参数、返回值、三元/类型转换
- [x] 4.3 高频内置接口：Function/Predicate/Consumer/Supplier + 派生变体（BiFunction、BinaryOperator、UnaryOperator），说明各自"信封规格"
- [x] 4.4 类型推断失败案例：Lambda 不能脱离上下文裸写（编译器报"目标类型不明确"）

## 5. 本质推论：变量捕获、this、return（§5）

- [x] 5.1 变量捕获：JVM 捕获的是"值副本"→ 推导出 effectively final 规则（编译报错示例 + 绕过方案：数组/AtomicReference 作为反例警示）
- [x] 5.2 `this` 语义：Lambda 不生成新实例，`this` 指向外部类（与匿名类对比）
- [x] 5.3 `return` 规则：表达式体有返回值、块体需显式 `return`；Lambda 的 `return` 只从 Lambda 返回，不从外层方法返回

## 6. 与匿名内部类的边界（§6）

- [x] 6.1 底层对照表：生成 `.class` vs `invokedynamic`、`this` 指向、变量捕获规则、能否有状态（字段/多方法）
- [x] 6.2 何时仍用匿名类：需要多个方法、需要自身状态（字段）、需要显式构造器逻辑
- [x] 6.3 落地收尾：Lambda / 匿名类 / 普通方法三者决策表

## 7. 项目真实代码走读（§7）

- [x] 7.1 `FoodService`：`.filter(f -> f.getId().equals(dto.foodId()))` —— 标注便利贴内容 + `Predicate<Food>` 信封
- [x] 7.2 `MarketService`：`.orElseThrow(() -> new IllegalArgumentException("分类不存在"))` —— `Supplier` 信封
- [x] 7.3 `GlobalExceptionHandler`：`.map(e -> ...).reduce((a, b) -> a + "; " + b)` —— `Function` + `BinaryOperator` 链
- [x] 7.4 `SpringDocConfig`：`return openApi -> { ... }` 多行 Lambda —— 自定义信封（`OpenApiCustomizer`）+ 块体
- [x] 7.5 `WebMvcConfig`：`c -> c.getPackageName() != null && ...` —— 谓词式信封

## 8. 常见误区、速查与互链（§8）

- [x] 8.1 常见误区清单：Lambda 是语法糖 / Lambda 是匿名类简写 / 捕获变量可以改 / `this` 指向 Lambda 自己 / 滥用 Lambda 牺牲可读性
- [x] 8.2 速查清单：语法形态 + 高频函数式接口 + 捕获规则一览
- [x] 8.3 链接 01-04 `lambda-and-stream.md`（语法/Stream 用法速查）
- [x] 8.4 修改 `lambda-and-stream.md` 开头：加一行指向本指南（"想理解原理请见 [lambda-guide.md](./lambda-guide.md)"）

## 9. Capability Spec 与验证

- [x] 9.1 创建 delta spec：`openspec/changes/create-lambda-guide/specs/lambda-guide/spec.md`
- [x] 9.2 运行 guide-writing 的 `validate_guide.py` 校验目录锚点与代码围栏
- [x] 9.3 运行 `openspec validate create-lambda-guide` 通过
