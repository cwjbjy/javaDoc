# Jackson 指南

> **作用**：处理 JSON 与 Java 对象之间的转换。常用于 DTO，即前端传过来的 JSON 变成 Java 对象，或者 Java 对象返回给前端变成 JSON。

> **当前版本**：3.x。在 Jackson 2.x 中，因为 Jackson 不支持 Java 8 的新时间类型，需手动安装 `jackson-datatype-jsr310` 插件与手动禁用时间戳 `disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)`。在 3.x 中，上述配置已被内置。

## 安装

`spring-boot-starter-webmvc` 已内置 `jackson-databind`。

例如注解 `@RequestBody` 告诉 Spring 这个方法的参数应该从 HTTP 请求的 body（请求体）中获取数据。Spring 收到请求后，会根据 `@RequestBody` 的标记，自动调用合适的 `HttpMessageConverter` 将请求体（如 JSON、XML 等）转换成 Java 对象。例如请求头 `Content-Type: application/json` 时，会调用 `MappingJackson2HttpMessageConverter`，而它底层依赖 Jackson 来完成 JSON 的序列化/反序列化。

对于非 Web 项目，但依然需要使用 Jackson 的功能，可以手动在 `pom.xml` 中添加以下依赖：

```xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

## 使用场景

- **处理 RESTful API 请求与响应**：这是最常见的场景。Spring Boot 会自动使用 `jackson-databind` 来完成。
- **操作 Redis 中间件**：当需要将 Java 对象存入 Redis 时，通常需要先将其序列化为 JSON 字符串。
- **在微服务间进行数据交换**：在微服务架构中，服务间常通过 HTTP 或消息队列进行通信，数据格式多为 JSON。`jackson-databind` 负责在服务提供方将 Java 对象序列化为 JSON，在服务消费方将 JSON 反序列化为 Java 对象。

## 序列化与反序列化

使用 Spring Boot 搭建的 Web 项目中，Controller 中的 JSON 转换是自动的，但在 Service / Repository 中如果需要手动序列化（如 Redis 缓存、调用第三方 API），直接注入 Spring Boot 配置好的 `ObjectMapper`：

```java
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SomeService {

    private final ObjectMapper objectMapper;  // Spring Boot 自动注入

    public String toJson(Object obj) {
        return objectMapper.writeValueAsString(obj);
    }

    public <T> T fromJson(String json, Class<T> clazz) {
        return objectMapper.readValue(json, clazz);
    }

}
```

> 以下示例中 `objectMapper` 指通过 Spring Boot 注入的 `ObjectMapper`。

### 对象转 JSON 字符串

能处理各种复杂的 Java 对象，包含 `List`、`Map` 等类型。

```java
String json = objectMapper.writeValueAsString(user);
```

### JSON 字符串转对象

```java
String json = "{\"name\":\"张三\",\"age\":25}";

// 将 JSON 字符串解析成一个 User 类型的 Java 对象
User user = objectMapper.readValue(json, User.class);
// user.getName() → "张三"
```

### 类型引用处理泛型集合

```java
// 想反序列化成一个 List<User>
String json = "[{\"name\":\"张三\"},{\"name\":\"李四\"}]";

// ❌ 错误写法：这样拿不到 List<User>
List<User> list = objectMapper.readValue(json, List.class);
// 实际拿到的是 List<LinkedHashMap>，不是 List<User>

// ✅ 正确写法：用 TypeReference
List<User> list = objectMapper.readValue(json, new TypeReference<List<User>>() {});
```

> Java 泛型在编译后会擦除，运行时只有 `List.class`，Jackson 不知道里面是什么类型。`TypeReference` 通过匿名子类保留了泛型信息。

`TypeReference` 同样处理 `Map`：

```java
String json = "{\"a\":{\"name\":\"张三\"},\"b\":{\"name\":\"李四\"}}";
Map<String, User> map = objectMapper.readValue(json, new TypeReference<Map<String, User>>() {});
```

### JSON 转 JsonNode 树

当 JSON 结构不确定或只需要访问其中某几个字段时，可以用 `JsonNode` 将 JSON 字符串（或字节流）解析为一棵 `JsonNode` 树，以动态、无类型的方式遍历和提取数据。

```java
import tools.jackson.databind.JsonNode;

String json = """
        {
            "code": 200,
            "message": "success",
            "data": {
                "name": "川菜",
                "foods": [
                    {"name": "宫保鸡丁", "price": 38},
                    {"name": "麻婆豆腐", "price": 28}
                ]
            }
        }
        """;

JsonNode root = objectMapper.readTree(json);

// 访问字段
int code = root.get("code").asInt();          // 200
String message = root.get("message").asText(); // "success"
String name = root.at("/data/name").asText();  // "川菜"（JSON Pointer 语法）

// 访问数组
JsonNode foods = root.at("/data/foods");
for (JsonNode food : foods) {
    String foodName = food.get("name").asText();
    int price = food.get("price").asInt();
}

// 检查字段是否存在
boolean hasPrice = root.at("/data/foods/0/price").has("value");
// 也可以用 path() 安全访问（不存在时返回 MissingNode 而非 null）
String safe = root.path("nonexistent").path("field").asText(""); // ""
```

**JsonNode 类型判断：**

| 方法                   | 说明                        |
| ---------------------- | --------------------------- |
| `node.isObject()`      | 是 JSON 对象 `{}`           |
| `node.isArray()`       | 是 JSON 数组 `[]`           |
| `node.isTextual()`     | 是字符串                    |
| `node.isInt()`         | 是整数                      |
| `node.isBoolean()`     | 是布尔值                    |
| `node.isNull()`        | 是 `null`                   |
| `node.isMissingNode()` | 字段不存在（区别于 `null`） |

## 注解

### @JsonProperty

将 JSON 字段名与 Java 字段名做映射（双向：序列化 + 反序列化）。

```java
import com.fasterxml.jackson.annotation.JsonProperty;

// 项目实际案例：CreateOrderDTO.OrderFoodDTO
public record OrderFoodDTO(
        @JsonProperty("_id") String id,  // JSON: "_id" ↔ Java: "id"
        String name,
        String describe,
        String burden,
        String image,
        Integer value) {
}
```

> **项目中的使用场景**：前端使用 MongoDB 风格的 `_id` 作为字段名，但 Java 命名规范不允许变量以 `_` 开头。`@JsonProperty` 在两者之间架桥。

### @JsonFormat

控制日期/数字等类型的序列化格式。

```java
import com.fasterxml.jackson.annotation.JsonFormat;

public class Order {

    // 日期格式化
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date createdAt;

    // 日期格式化（仅日期）
    @JsonFormat(pattern = "yyyy-MM-dd")
    private String date;

}

// 输出: {"createdAt":"2026-07-07 14:30:00", "date":"2026-07-07"}
// 而非默认的: {"createdAt":"2026-07-07T06:30:00.000+00:00", ...}
```

### @JsonIgnore

返回用户信息时，不能把密码或加密盐返回给前端。

```java
import com.fasterxml.jackson.annotation.JsonIgnore;

public class User {
    private String username;

    @JsonIgnore
    private String password;    // 序列化和反序列化时，完全忽略该字段

}
```

### @JsonIgnoreProperties

在类级别或字段级别忽略多个属性。为了兼容旧字段，防止 JSON 数据中有但实体类没有的情况。

Jackson 3.x 默认忽略 JSON 中的未知字段，但可以通过 `@JsonIgnoreProperties` 批量控制序列化时排除不想输出的字段。

```java
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 类级别：忽略 JSON 中的未知字段
@JsonIgnoreProperties({"password"})
public class Category {
    private String name;
    private String image;
}

// 字段级别：忽略嵌套对象的某些字段
public class Order {
    @JsonIgnoreProperties({"internalId", "cost"})
    private List<FoodItem> foods;
}
```

### @JsonAlias

允许 JSON 中使用多个字段名映射到同一个 Java 字段（仅反序列化）。

```java
import com.fasterxml.jackson.annotation.JsonAlias;

public record FoodDTO(
        @JsonAlias({"_id", "foodId"}) String id,  // 接受 _id 或 foodId
        String name) {
}

// 以下两种 JSON 都能正确反序列化：
// {"id":"abc","name":"宫保鸡丁"}    ← 主名
// {"_id":"abc","name":"宫保鸡丁"}   ← 别名 1
// {"foodId":"abc","name":"宫保鸡丁"} ← 别名 2
```

## 配置

Spring Boot 通过 `application.yml` 中的 `spring.jackson.*` 属性配置 Jackson 行为：

```yaml
spring:
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss # 日期格式（全局）
    time-zone: Asia/Shanghai # 时区
    default-property-inclusion: non_null # null 字段不输出

    # 序列化特性
    serialization:
      indent-output: true # 格式化输出（调试用）
      write-dates-as-timestamps: false # 日期输出为 ISO 字符串（3.x 默认值）
      fail-on-empty-beans: false # 空对象不报错

    # 反序列化特性
    deserialization:
      fail-on-unknown-properties: false # 未知字段不报错（3.x 默认值）
      fail-on-trailing-tokens: true # 尾部多余内容报错（3.x 默认值）
      accept-single-value-as-array: true # 单值可当数组处理

    # 映射器特性
    mapper:
      accept-case-insensitive-properties: true # 忽略属性名大小写
      default-view-inclusion: false
```
