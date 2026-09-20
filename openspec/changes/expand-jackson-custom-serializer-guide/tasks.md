## 1. 验证工程（先行：示例先编译运行，再写进指南）

- [x] 1.1 创建 `target/guide-verification/jackson-custom-serializer` Maven 工程：pom.xml（parent `spring-boot-starter-parent:4.0.6`，依赖 `spring-boot-starter-jackson`、`spring-boot-starter-test`，Java 17）
- [x] 1.2 实现示例一 `PriceSerializer extends StdSerializer<Integer>`（分→元）与字段注解绑定模型
- [x] 1.3 实现示例二 `FoodItem` 模型 + `FoodItemSerializer`（`writeStartObject`/`writeName`/`writeString`/`writeNumber`/`writeEndObject` 自定义形状）
- [x] 1.4 实现 `FlexibleDateDeserializer extends ValueDeserializer<Date>`（多格式 + 时间戳）
- [x] 1.5 实现三个注册层级演示：`@JsonSerialize` 注解类、`SimpleModule.addSerializer` + `builder.addModule`、`JsonMapperBuilderCustomizer` Bean（`org.springframework.boot.jackson.autoconfigure`）
- [x] 1.6 编写 JUnit 测试：断言分转元输出、FoodItem 自定义形状（含省略 image）、多格式日期解析、Boot 4 自动应用 Customizer Bean
- [x] 1.7 运行 `mvn test` 全部通过，记录命令与结果

## 2. 第 11 节重写

- [x] 2.1 新增 11.1 前提介绍：默认 BeanSerializer 反射路径、三个自定义动机（格式转换/数据变形/自定义输出形状）、"注解优先"决策边界（链接第 7 节）
- [x] 2.2 新增 11.2 类体系：`ValueSerializer` 抽象基类 + `serialize(T, JsonGenerator, SerializationContext)` 三参数逐个讲解 + `StdSerializer`（`tools.jackson.databind.ser.std`）便利基类
- [x] 2.3 改写 11.3 示例一：Integer 价格示例改为继承 `StdSerializer`，代码与验证工程一致，标注 Verified runnable
- [x] 2.4 新增 11.4 示例二：FoodItem 复杂类型 token API 示例，与验证工程一致，标注 Verified runnable
- [x] 2.5 改写 11.5 自定义反序列化器：补 `deserialize(JsonParser, DeserializationContext)` 契约讲解与 unchecked 异常说明
- [x] 2.6 改写 11.6 注册三个层级：删除 `builder.addSerializer` 错误写法；给出注解 / `SimpleModule` / `JsonMapperBuilderCustomizer` 三层可编译写法与适用场景；删除原 11.4 的 2.x→3.x 类名对照表

## 3. 全文 3.x 化

- [x] 3.1 删除第 12 节（"Jackson 3.x 关键变化 vs 2.x"），原 13→12、14→13 重新编号，目录同步更新
- [x] 3.2 全文 Grep 检索 `#\d+-` 锚点与"第 X 节"引用，逐一核对更新，确保无断裂
- [x] 3.3 2.2 重写为纯 3.x 包与 import 说明（保留 `@JsonSerialize/@JsonDeserialize/@JsonNaming` 位于 `tools.jackson.databind.annotation` 的事实，去掉 2.x 对比框架）
- [x] 3.4 2.3 缩减为 classpath 边界提示（logstash 9.0 同用 3.x；knife4j 5.2.0 自带旧版 Jackson；一律使用 `tools.jackson.*`）
- [x] 3.5 4.4 重写为只讲 3.x 异常（unchecked `JacksonException`/`DatabindException`/`StreamReadException`/`UnexpectedEndOfInputException`），删除 2.x 对照列
- [x] 3.6 5.4 / 9.1 / 9.3 / 9.4 删除 2.x 对比句，直接陈述 3.x 行为
- [x] 3.7 结尾段落去掉"已迁移"表述，直接陈述当前 import 约定

## 4. 审计错误修正

- [x] 4.1 14.3 速查表：`JacksonException` 改 `tools.jackson.core.JacksonException`；`DateTimeFeature` 改 `tools.jackson.databind.cfg.DateTimeFeature`；补 `StdSerializer`、`SimpleModule`、`JsonMapperBuilderCustomizer` 行
- [x] 4.2 `@JsonNaming` 标注已移到 `tools.jackson.databind.annotation`（7.10 注释、14.2/14.3 同步）
- [x] 4.3 13.1~13.5 的 `file:///` 链接改为真实路径（`d:/javaProject/demo1/src/main/java/...`，含 `dto/request` 子包）
- [x] 4.4 13.1 代码片段与 `CreateOrderDTO.java` 源码同步（补 `@Schema` 注解）
- [x] 4.5 7.4 删除对 `String` 字段使用 `@JsonFormat(pattern=...)` 的误导示例

## 5. 校验与收尾

- [x] 5.1 运行 `py -3 .agents/skills/guide-writing/scripts/validate_guide.py docs/Java/jackson-guide.md` 通过
- [x] 5.2 全文 Grep 复查：无 2.x 教学内容残留、无断裂锚点、无 `builder.addSerializer` 等错误 API
- [x] 5.3 按 spec 逐条 requirement 核对实现（openspec validate 通过）
- [x] 5.4 记录 Verification summary（验证工程运行命令与结果、证据来源清单，见 `VERIFICATION.md`）
