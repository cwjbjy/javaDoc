## Why

`docs/Java/jackson-guide.md` 第 11 节（自定义序列化器与反序列化器，814-884 行）是"跳到答案"式的写法：没有交代默认序列化路径是什么、什么时候需要接管它，直接抛出 `ValueSerializer` 代码；且 11.3 的注册示例 `builder.addSerializer(...)` 在 Jackson 3.x 中根本不存在，编译不过。同时全文多处内容与项目实际的 Jackson 3.1.2 / Spring Boot 4.0.6 不符（已用本地 jar 与 `dependency:tree` 逐一验证），并混有大量 2.x 对比内容。本次变更将第 11 节重写为"按依赖排序"的渐进式内容，并把全文定位为**只讲 3.x** 的指南。

## What Changes

### 第 11 节重写（只讲 3.x，不引入 2.x 内容）

- **新增 11.1 前提介绍**：默认序列化路径（反射 BeanSerializer）+ 三个典型动机（格式转换 / 数据变形 / 自定义输出形状）+ "注解优先、自定义兜底"决策边界（衔接第 7 节）
- **新增 11.2 类体系讲解**：`ValueSerializer` 是 3.x 唯一抽象基类（唯一抽象方法 `serialize(T, JsonGenerator, SerializationContext)`），`StdSerializer`（`tools.jackson.databind.ser.std`）是提供 `handledType()`、`wrapAndThrow()` 的常用便利基类；逐个讲解 `serialize()` 三个参数的含义
- **11.3 示例一（保留改造）**：现有 Integer 价格（分→元）示例，改为继承 `StdSerializer`
- **11.4 示例二（新增）**：复杂类型示例——`FoodItem`（name/describe/image/price 分）自定义输出形状，展示 `writeStartObject/writeName/writeString/writeNumber/writeEndObject` token 级 API
- **11.5 自定义反序列化器**：补 `deserialize()` 前提讲解（`ValueDeserializer`）
- **11.6 注册方式三个层级（修正编译错误）**：字段级 `@JsonSerialize`（`tools.jackson.databind.annotation`）/ 类型级 `SimpleModule.addSerializer` + `builder.addModule()` / Boot 4 全局 `JsonMapperBuilderCustomizer`（`org.springframework.boot.jackson.autoconfigure`）
- 删除现有 11.4 的 2.x→3.x 类名对照表

### 全文去除 2.x 对比内容，只讲 3.x

- **删除第 12 节**（"Jackson 3.x 关键变化 vs 2.x"），原 13、14 节顺延为 12、13；同步更新目录与全部节内交叉引用锚点
- **2.2 重写**为纯 3.x 包与 import 说明（去掉"从 com.fasterxml 改为"的对比框架，保留 `@JsonSerialize/@JsonDeserialize/@JsonNaming` 位于 `tools.jackson.databind.annotation` 的事实）
- **2.3 缩减**为 classpath 边界提示：logstash-logback-encoder 9.0 与 Spring Boot 同用 Jackson 3.x；knife4j 5.2.0 自带一套旧版 Jackson 供其自身使用，日常开发一律使用 `tools.jackson.*`
- **4.4 重写**：只讲 3.x 异常（unchecked `JacksonException`、`DatabindException`、`StreamReadException`、`UnexpectedEndOfInputException`），删除 2.x 对照列
- **5.4 / 9.1 / 9.3 / 9.4**：删除"2.x 默认是 X / 2.x 需要注册模块"等对比句，直接陈述 3.x 行为
- **结尾段落**：去掉"已迁移"表述，直接陈述当前 import 约定

### 审计发现的错误修正

- 14.3 import 速查表：`JacksonException` 改 `tools.jackson.core.JacksonException`；`DateTimeFeature` 改 `tools.jackson.databind.cfg.DateTimeFeature`；补 `StdSerializer`、`SimpleModule`、`JsonMapperBuilderCustomizer` 行；`@JsonNaming` 标注已移到 `tools.jackson.databind.annotation`（7.10 注释同步）
- 13.1~13.5 的 `file:///` 链接改为真实路径（`d:/javaProject/demo1/.../dto/request/...`）；13.1 代码片段与源码同步（补 `@Schema` 注解）
- 7.4 删除对 `String` 字段使用 `@JsonFormat(pattern=...)` 的误导示例（pattern 对 String 无效）

### 验证

- 在 `target/guide-verification/` 下新建 `jackson-custom-serializer` 验证工程（沿用现有 fastjson / spring-boot-multithreading 模式），编译并运行第 11 节全部示例，防止再次出现"示例编不过"

## Capabilities

### New Capabilities

- `jackson-custom-serializer-guide`: `docs/Java/jackson-guide.md` 中自定义序列化器/反序列化器章节（第 11 节）的渐进式重写，以及全文 3.x 定位与事实性错误修正，含可编译验证的示例

### Modified Capabilities

（无——现有 openspec/specs 下均为项目功能 spec，与本文档无关）

## Impact

- `docs/Java/jackson-guide.md`: 第 11 节整体重写、第 12 节删除、13/14 节重新编号、2.2/2.3/4.4/5.4/7.4/9.x/14.2/14.3 局部改写，预计净增约 150~250 行
- `target/guide-verification/jackson-custom-serializer/`: 新增验证工程（编译运行第 11 节示例）
- `openspec/specs/jackson-custom-serializer-guide/spec.md`: 新增 capability spec
- 无业务代码修改、无依赖变更、无向后兼容影响（纯文档交付物）
