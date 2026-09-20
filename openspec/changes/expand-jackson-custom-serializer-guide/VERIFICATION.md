# Verification summary

Change: `expand-jackson-custom-serializer-guide`
目标文档: `docs/Java/jackson-guide.md`（定位 Jackson 3.1.2 / Spring Boot 4.0.6 / Java 17）

## Structure

- `py -3 .agents/skills/guide-writing/scripts/validate_guide.py docs/Java/jackson-guide.md` → **PASS**（H2 sections: 13, TOC links: 13）
- `openspec validate expand-jackson-custom-serializer-guide --json` → **valid: true, 0 issues**

## Code（Verified runnable 示例）

验证工程：`target/guide-verification/jackson-custom-serializer`

- 命令：`& C:\Users\user\.m2\wrapper\dists\apache-maven-3.9.16-bin\...\bin\mvn.cmd -o -f target\guide-verification\jackson-custom-serializer\pom.xml test`
- 结果：**BUILD SUCCESS**，Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
- 环境：Java 17.0.18；parent `spring-boot-starter-parent:4.0.6`；依赖 `spring-boot-starter-jackson` + `spring-boot-starter-test`；离线模式（`-o`，依赖已在本地 m2 仓库）

测试覆盖（`SerializerGuideTest` 6 个 + `JacksonConfigTest` 1 个）：

| 测试                                                                     | 对应指南小节 | 断言                                                                                  |
| ------------------------------------------------------------------------ | ------------ | ------------------------------------------------------------------------------------- |
| `priceSerializerConvertsCentsToYuan`                                     | 11.3 示例一  | `Food(宫保鸡丁, 3800)` 输出含 `"price":"38.00元"`                                     |
| `foodItemSerializerEmitsCustomShape`                                     | 11.4 示例二  | 精确等于 `{"name":...,"describe":...,"price":3800,"priceYuan":"38.00元"}`，无 `image` |
| `flexibleDateDeserializerParsesDashFormat` / `SlashFormat` / `Timestamp` | 11.5         | `"2026-07-07"`、`"2026/07/07"`、时间戳均正确解析为 `Date`                             |
| `simpleModuleRegistersSerializerForType`                                 | 11.6 层级二  | `SimpleModule` + `builder.addModule()` 后 `3800` → `"38.00元"`                        |
| `customizerBeanIsAppliedByBootAutoConfiguration`                         | 11.6 层级三  | `@SpringBootTest` 注入 `JsonMapper` 后 `3800` → `"38.00元"`                           |

## Sources（版本敏感事实的证据来源）

- Jackson 3.1.2 / Spring Boot 4.0.6 本地 Maven 仓库 jar 反编译核实（`javap` / `jar -tf`）：
  - `tools.jackson.databind.ValueSerializer` / `ValueDeserializer` / `SerializationContext` / `DeserializationContext`
  - `tools.jackson.databind.ser.std.StdSerializer`（`handledType()`、`wrapAndThrow()`）
  - `tools.jackson.core.JsonGenerator`（`writeName` / `writeString` / `writeNumber` / `writeStartObject` / `writeEndObject`；**无** `writeFieldName`）
  - `tools.jackson.core.JacksonException` / `StreamReadException` / `UnexpectedEndOfInputException`；`tools.jackson.databind.DatabindException`
  - `tools.jackson.databind.cfg.DateTimeFeature`；`tools.jackson.databind.module.SimpleModule`；`tools.jackson.databind.json.JsonMapper`
  - `org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer`（spring-boot-jackson jar）
- `mvnw dependency:tree`：logstash-logback-encoder **9.0**（基于 Jackson 3.x）；knife4j **5.2.0** 传递引入旧版 Jackson（`com.fasterxml.*`）
- 项目源码：`src/main/java/com/example/javadoc/module/order/dto/request/CreateOrderDTO.java`（含 `@Schema`，12.1 片段与之一致）

## Unverified

none——全部可运行示例均已执行通过；所有包名/类名/方法名均经 jar 反编译核实。
