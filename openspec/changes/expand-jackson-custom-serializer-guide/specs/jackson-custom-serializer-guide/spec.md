## ADDED Requirements

### Requirement: 第 11 节渐进式结构（前提先行）

`docs/Java/jackson-guide.md` 第 11 节 SHALL 按"依赖排序"重写：先讲默认序列化路径与"何时需要自定义"（前提），再讲类体系与 `serialize()` 契约（机制），最后给示例与注册方式（应用）。SHALL NOT 在解释动机之前直接出现 `ValueSerializer` 代码。

#### Scenario: 读者按顺序建立心智模型

- **WHEN** 从未写过自定义序列化器的读者从头阅读第 11 节
- **THEN** 在见到第一段自定义序列化器代码之前，已理解"Jackson 默认如何序列化"和"什么需求迫使自己接管"

### Requirement: 前提介绍——默认路径、三个动机与决策边界

第 11 节 SHALL 说明默认序列化路径：Jackson 为普通 Bean 生成基于反射的 `BeanSerializer`（读 getter/字段并写出对应 JSON）；SHALL 列出三个典型自定义动机（格式转换、数据变形、自定义输出形状）；SHALL 给出"注解优先、自定义兜底"的决策边界，并链接第 7 节（注解）说明哪些需求用 `@JsonFormat`/`@JsonInclude`/`@JsonProperty` 即可解决。

#### Scenario: 读者判断是否需要自定义序列化器

- **WHEN** 读者遇到"输出格式与字段不一致"的需求
- **THEN** 能先判断注解是否可解决，只有注解覆盖不了的变形需求才转向自定义序列化器

### Requirement: ValueSerializer 与 StdSerializer 类体系讲解

第 11 节 SHALL 介绍 Jackson 3.x 的序列化器类体系：`ValueSerializer<T>`（`tools.jackson.databind`）是抽象基类，唯一抽象方法是 `serialize(T value, JsonGenerator gen, SerializationContext ctxt)`；`StdSerializer<T>`（`tools.jackson.databind.ser.std`）继承 `ValueSerializer`，提供 `handledType()` 与 `wrapAndThrow()` 便利方法，是编写自定义序列化器的常用基类。SHALL 逐个解释 `serialize()` 三个参数：`value`（待序列化值）、`gen`（逐 token 写入 JSON 的写出器）、`ctxt`（上下文服务，一般只在需要配置/其他序列化器时使用）。

#### Scenario: 读者理解 serialize() 契约

- **WHEN** 读者阅读示例代码中的 `serialize` 方法
- **THEN** 能说出三个参数各自的作用，并知道自己的逻辑应该通过 `gen` 写出、而不是拼接 JSON 字符串

### Requirement: 示例一——标量类型自定义（分转元）

第 11 节 SHALL 保留 Integer 价格示例（价格存分、输出"元"字符串）：`PriceSerializer extends StdSerializer<Integer>`，`serialize` 中用 `gen.writeString(String.format("%.2f元", value / 100.0))`，3800 输出 `"38.00元"`；SHALL 给出 `@JsonSerialize(using = PriceSerializer.class)` 的字段绑定示例，import 为 `tools.jackson.databind.annotation.JsonSerialize`。

#### Scenario: 读者完成第一个标量序列化器

- **WHEN** 读者照示例实现并运行
- **THEN** 序列化 `Integer` 3800 得到 JSON 字符串 `"38.00元"`（带引号，是 JSON 字符串而非数字）

### Requirement: 示例二——复杂类型自定义输出形状

第 11 节 SHALL 新增复杂类型示例：`FoodItem`（name/describe/image/price 分），自定义序列化器将其输出为自定义形状——`price`（分）转为 `priceYuan`（元字符串）、省略 `image` 字段；SHALL 演示对象级 token API：`writeStartObject` / `writeName` / `writeString` / `writeNumber` / `writeEndObject`。

#### Scenario: 读者写出对象级 JSON

- **WHEN** 读者照示例二实现 FoodItem 序列化器并运行
- **THEN** 输出 JSON 对象包含 `name`、`describe`、`priceYuan` 字段且不含 `image` 字段，`priceYuan` 值为按分换算的元字符串

### Requirement: 自定义反序列化器（ValueDeserializer）

第 11 节 SHALL 讲解 `ValueDeserializer<T>`（`tools.jackson.databind`）的 `deserialize(JsonParser p, DeserializationContext ctxt)` 契约：从 `p` 读取 token 并返回 Java 对象；SHALL 保留"灵活解析日期（多格式 + 时间戳）"示例并补充参数讲解，异常抛出 SHALL 说明 3.x 异常是 unchecked（`tools.jackson.core.JacksonException` 体系）。

#### Scenario: 读者实现多格式日期反序列化

- **WHEN** 读者照示例实现并分别输入 `"2026-07-07"`、`"2026/07/07"`、`"1781234567890"`
- **THEN** 三种输入均正确解析为 `Date`，其他格式抛出带说明的异常

### Requirement: 注册方式三个层级（3.x 可编译写法）

第 11 节 SHALL 给出三个注册层级，全部使用 Jackson 3.x / Spring Boot 4.0.6 下的真实可编译 API：

1. 字段级：`@JsonSerialize` / `@JsonDeserialize` 注解（`tools.jackson.databind.annotation` 包）
2. 类型级：`SimpleModule`（`tools.jackson.databind.module.SimpleModule`）的 `addSerializer(Class, ValueSerializer)` / `addDeserializer(Class, ValueDeserializer)`，通过 `JsonMapper.Builder.addModule(...)` 注册
3. 全局（Spring Boot 4）：定义 `JsonMapperBuilderCustomizer` Bean（`org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer`，`customize(JsonMapper.Builder)` 中调用 `addModule`）

SHALL NOT 出现 `builder.addSerializer(...)` / `builder.addDeserializer(...)` 等 3.x 不存在的 API；SHALL NOT 提及 `Jackson2ObjectMapperBuilderCustomizer`。每层 SHALL 标注适用场景（个别字段用注解；同一类型全局生效用 SimpleModule；需要与 `spring.jackson.*` 配置共存的用 Customizer Bean）。

#### Scenario: 三个层级的示例均可编译运行

- **WHEN** 读者把任一层级的代码放入本指南声明的 Spring Boot 4.0.6 + Jackson 3.1.2 环境
- **THEN** 代码编译通过，且该层级的序列化器生效

### Requirement: 全文只讲 3.x（删除 2.x 对比内容）

指南 SHALL 删除第 12 节（"Jackson 3.x 关键变化 vs 2.x"）及全文所有 2.x 对比内容；原第 13、14 节 SHALL 重新编号为 12、13，目录与所有节内交叉引用锚点 SHALL 同步更新且无断裂；2.2 SHALL 改为纯 3.x 包/import 说明；4.4 SHALL 只讲 3.x 异常（unchecked `JacksonException`、`DatabindException`、`StreamReadException`、`UnexpectedEndOfInputException`），不含 2.x 对照列；5.4 / 9.1 / 9.3 / 9.4 中的 2.x 对比句 SHALL 删除；结尾段落 SHALL 直接陈述当前 import 约定。指南 SHALL NOT 出现以教学为目的的 2.x API/默认值讲解（`JsonSerializer`、`com.fasterxml.jackson.databind` 等）。

#### Scenario: 读者通读全篇不遇到 2.x 教学内容

- **WHEN** 读者从头到尾阅读修改后的指南
- **THEN** 不遇到任何 2.x 与 3.x 的对比教学内容；所有 import 与 API 均为 3.x（`tools.jackson.*` 或 `com.fasterxml.jackson.annotation.*` 注解包）

### Requirement: 2.3 缩减为 classpath 边界提示

2.3 SHALL 保留但缩减为 classpath 边界提示：logstash-logback-encoder 9.0 与 Spring Boot 同用 Jackson 3.x；knife4j 5.2.0 自带一套旧版 Jackson（`com.fasterxml.*`）供其自身使用；日常开发 SHALL 使用 `tools.jackson.*`，SHALL 说明 IDE 补全出的 `com.fasterxml` databind 类（如 `ObjectMapper`）不要使用。SHALL NOT 对比两套 Jackson 的功能或讲解旧版 API。

#### Scenario: 读者不因两套包名而困惑

- **WHEN** 读者在 IDE 中看到 `com.fasterxml` 与 `tools.jackson` 两套 Jackson 包
- **THEN** 能依据本节判断：一律使用 `tools.jackson.*`，`com.fasterxml` 的 databind 类来自 knife4j 的传递依赖、与本指南无关

### Requirement: 修正审计发现的事实性错误

指南 SHALL 修正以下已核实错误：14.3 速查表中 `JacksonException` 的包改为 `tools.jackson.core.JacksonException`；`DateTimeFeature` 的包改为 `tools.jackson.databind.cfg.DateTimeFeature`；`@JsonNaming` 标注为已移到 `tools.jackson.databind.annotation`（7.10 注释同步）；14.3 SHALL 补充 `StdSerializer`（`tools.jackson.databind.ser.std`）、`SimpleModule`（`tools.jackson.databind.module`）、`JsonMapperBuilderCustomizer`（`org.springframework.boot.jackson.autoconfigure`）条目；13.1~13.5 的 `file:///` 链接 SHALL 改为真实路径（`d:/javaProject/demo1/src/main/java/...`，含 `dto/request` 子包）；13.1 代码片段 SHALL 与源码同步（含 `@Schema` 注解）；7.4 SHALL 删除对 `String` 字段使用 `@JsonFormat(pattern=...)` 的示例（pattern 对 String 无效）。

#### Scenario: 速查表与 jar 实际内容一致

- **WHEN** 读者按 14.3 速查表输入 import
- **THEN** 全部 import 路径与 Jackson 3.1.2 / Spring Boot 4.0.6 实际 jar 内容一致，可直接编译

### Requirement: 验证工程编译运行示例

本次变更 SHALL 在 `target/guide-verification/jackson-custom-serializer` 下新建独立 Maven 验证工程（parent `spring-boot-starter-parent:4.0.6`，依赖 `spring-boot-starter-jackson` 与 `spring-boot-starter-test`，Java 17）：`src/main/java` 包含第 11 节全部示例类与三个注册层级的演示配置；`src/test/java` 用 JUnit 断言示例输出（分转元、FoodItem 自定义形状、注解生效、`JsonMapperBuilderCustomizer` Bean 被 Boot 4 自动应用）；工程 SHALL 通过 `mvn test` 后，指南示例方可标注 Verified runnable，并记录运行命令与结果。

#### Scenario: 指南示例在验证工程中全部编译运行通过

- **WHEN** 在验证工程目录执行 `mvn test`
- **THEN** 全部测试通过，指南第 11 节代码示例与验证工程源码一致

### Requirement: 范围排除（入门定位）

第 11 节 SHALL NOT 涉及以下进阶话题：`createContextual()`（上下文序列化器）、`ValueSerializerModifier`、null 值的自定义处理、key 序列化器（`addKeySerializer`）、`@JsonSerializeAs`/`@JsonDeserializeAs`、`JsonNode` 工厂构造等。

#### Scenario: 第 11 节保持实用起步定位

- **WHEN** 读者通读第 11 节
- **THEN** 不遇到上述排除主题，全部内容围绕"能动手写、能看懂别人代码"展开
