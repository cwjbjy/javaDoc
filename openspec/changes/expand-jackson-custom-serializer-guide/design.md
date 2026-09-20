## Context

`docs/Java/jackson-guide.md`（1226 行）当前第 11 节"自定义序列化器与反序列化器"缺认知前提、示例未经验证且 11.3 注册代码在 Jackson 3.x 下无法编译。全文混有 2.x 对比内容，多处 import 包与项目实际的 Jackson 3.1.2 / Spring Boot 4.0.6 不符（已用本地 jar、`javap`、`dependency:tree` 验证，详见 proposal）。

目标读者：使用本项目的开发者（Spring Boot 4.0.6 + Jackson 3.1.2，Java 17），对注解体系已熟悉（读过第 7 节），但从未写过自定义序列化器。

## Goals / Non-Goals

**Goals:**

- 第 11 节按"依赖排序"重写：先建立"默认路径是什么、为什么接管"的心智模型，再讲类体系和 API，最后给两个递进示例（标量 → 复杂类型）与注册方式
- 全文删除 2.x 对比内容，只讲 3.x 现状
- 修正审计发现的全部事实性错误（#2~#8）
- 第 11 节示例在 `target/guide-verification/jackson-custom-serializer` 中真实编译运行，杜绝"示例编不过"

**Non-Goals:**

- 不引入进阶话题：`createContextual()`、`ValueSerializerModifier`、null 值处理、key 序列化器、`@JsonSerializeAs` 等一律不写
- 不改动项目业务代码与 `pom.xml`
- 不动其他 guide 文档（仅修正 jackson-guide.md 内部内容）
- 不新建独立 guide 文件（重写既有章节）

## Decisions

### D1. 第 11 节采用"混合渐进"结构（原样保留参考型骨架，局部按因果链展开）

guide-writing skill 判定：整篇是 reference guide，但"自定义序列化器"是因果链主题（默认路径 → 何时接管 → 如何接管 → 如何注册）。采用 hybrid：11.1→11.2 建立机制（因果），11.3~11.6 为可独立查阅的示例与注册层级（平行）。

替代方案：保持现有"直接上代码"结构——被否，正是本次要修的问题。

### D2. 示例基类选 `StdSerializer`（`tools.jackson.databind.ser.std`）

理由：社区惯例与 Jackson 内部实现都以 `StdSerializer` 为便利基类，它提供 `handledType()` 与 `wrapAndThrow()`；直接继承 `ValueSerializer` 需要自己实现 `handledType()`。现有 Integer 示例改为继承 `StdSerializer`。

替代方案：直接继承 `ValueSerializer`——合法但非惯例，且 11.3 现有示例未说明为何如此选。

### D3. 复杂类型示例选项目域的 `FoodItem`，输出"打平+变形"的形状

示例二用 `FoodItem`（name/describe/image/price 分）而非 Order：字段少、语义贴合项目（市场模块），自定义输出形状动机直观——把 `price`（分）转为 `priceYuan`（元），并省略 `image` 字段演示"自定义形状≠简单改名"。代码展示 `writeStartObject/writeName/writeString/writeNumber/writeEndObject` 完整 token API，与示例一的单值 `writeString` 形成递进。

替代方案：用 `Order`（含嵌套 foods）——嵌套序列化需 `gen.writeObject(field, value)` 委托，超出本变更的"实用起步"深度。

### D4. 注册方式按"字段级 → 类型级 → 全局"三层，全部给 3.x 可编译写法

- 字段级：`@JsonSerialize(using = ...)`（import `tools.jackson.databind.annotation.JsonSerialize`）
- 类型级：`new SimpleModule().addSerializer(Integer.class, ...)` + `mapperBuilder.addModule(module)`（`JsonMapper.Builder` 上没有 `addSerializer`，修正原 11.3 错误）
- 全局（Boot 4）：定义 `JsonMapperBuilderCustomizer` Bean（`org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer`，`customize(JsonMapper.Builder)` 内 `addModule`）

每层标注适用场景："只改个别字段用注解；同一类型全局生效用 SimpleModule；需要与 spring.jackson.\* 配置共存用 Customizer Bean"。

替代方案：推荐 Boot 3 风格的 `Jackson2ObjectMapperBuilderCustomizer`——在 Boot 4.0.6 中该类已不存在（已验证）。

### D5. 删除第 12 节后重新编号：原 13→12、原 14→13

同步修改：目录、所有节内交叉引用锚点（`#8-在-service-中使用-objectmapper` 等，第 8 节之前编号不变）。用 Grep 全量检索 `#\d+-` 锚点确保无遗漏。

### D6. 2.3 保留但缩减为 classpath 边界提示

不对比两套 Jackson 的能力，只陈述事实：logstash-logback-encoder 9.0 与 Spring Boot 同用 3.x；knife4j 5.2.0 自带一套旧版 Jackson（`com.fasterxml.*`）供自身使用。提示：日常开发一律使用 `tools.jackson.*`，IDE 若补全出 `com.fasterxml` 的 databind 类不要使用。

替代方案：彻底删除 2.3——被否，读者会因 IDE 里出现两套包而困惑，边界提示是刚需。

### D7. 验证工程 `target/guide-verification/jackson-custom-serializer`

沿用仓库现有验证工程模式（独立 Maven 工程）：parent 用 `spring-boot-starter-parent:4.0.6`（对齐指南技术栈），依赖 `spring-boot-starter-jackson` + `spring-boot-starter-test`，Java 17。

- `src/main/java`：第 11 节两个示例的序列化器/反序列化器 + `FoodItem` 模型 + 三个注册层级的演示配置类
- `src/test/java`：JUnit 断言示例输出（如 `3800` → `"38.00元"`、FoodItem 自定义形状、`@JsonSerialize` 注解生效、`JsonMapperBuilderCustomizer` Bean 被 Boot 4 自动应用）
- 用 `mvn -f ... test` 运行通过后，指南中示例标注"Verified runnable example"（记录命令与结果）；Boot 自动装配行为在 test 中用 `ApplicationContextRunner`/`@SpringBootTest` 验证

### D8. 证据等级与事实性声明

- 所有 API 签名、import 包名以本地 jar 的 `javap` 输出为准（本会话已核实：`ValueSerializer.serialize` 签名、`StdSerializer` 构造器、`SimpleModule.addSerializer`、`JsonMapperBuilderCustomizer.customize`、异常类位置、`DateTimeFeature` 位于 `cfg` 包、`@JsonNaming` 位于 `tools.jackson.databind.annotation`）
- 指南示例标注证据状态（Illustrative fragment / Verified runnable），按 guide-writing 的 verification 流程交付 Verification summary
- 修改后用 `py -3 .agents/skills/guide-writing/scripts/validate_guide.py` 校验结构

## Risks / Trade-offs

- [删除第 12 节丢失 2.x→3.x 迁移信息] → 用户明确要求全文只讲 3.x；迁移信息仍可从 Jackson 官方文档获取，本指南定位为"3.x 学习资料"而非迁移手册
- [重新编号导致锚点/交叉引用断裂] → Grep 全量检索 `#\d+` 锚点与"第 X 节"引用逐一核对；validate_guide.py 校验目录锚点
- [验证工程引入 Boot 4 全栈测试成本] → 依赖已在本地 m2（项目已构建），离线可跑；测试聚焦序列化输出与注册生效性，不测无关功能
- [`@JsonSerialize` 包名易与 `com.fasterxml` 版本混淆] → 11.2/11.6 显式给出完整 import，14.3 速查表同步修正
