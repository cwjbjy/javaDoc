# Jackson JSON 处理指南

> 本指南基于 `JavaDoc` 项目实际环境，介绍 Jackson JSON 库的概念、常用注解与对象/JSON 互转。
> 项目使用 Spring Boot 4.0.6 + Jackson 3.1.2 (LTS)。

---

## 目录

1. [Jackson 是什么](#1-jackson-是什么)
2. [项目版本与依赖](#2-项目版本与依赖)
3. [Spring Boot 中的 Jackson](#3-spring-boot-中的-jackson)
4. [对象 → JSON（序列化）](#4-对象--json序列化)
5. [JSON → 对象（反序列化）](#5-json--对象反序列化)
6. [JsonNode：动态 JSON 处理](#6-jsonnode动态-json-处理)
7. [常用注解大全](#7-常用注解大全)
8. [在 Service 中使用 ObjectMapper](#8-在-service-中使用-objectmapper)
9. [日期与时间处理](#9-日期与时间处理)
10. [配置项 spring.jackson.\*](#10-配置项-springjackson)
11. [自定义序列化器与反序列化器](#11-自定义序列化器与反序列化器)
12. [Jackson 3.x 关键变化（vs 2.x）](#12-jackson-3x-关键变化vs-2x)
13. [项目实际使用案例](#13-项目实际使用案例)
14. [速查清单](#14-速查清单)

---

## 1. Jackson 是什么

**Jackson** 是 Java 生态中最流行的 JSON 处理库。它负责在 **Java 对象** 和 **JSON 字符串** 之间互相转换：

```
        序列化 (Serialization)
Java 对象  ──────────────────────▶  JSON 字符串
  {                                 {"name":"川菜",
   name = "川菜",          ◀────────  "image":"chuan.jpg"}
   image = "chuan.jpg"
  }                  反序列化 (Deserialization)
```

在 Spring Boot Web 应用中，Jackson 是隐形的"翻译官"：

```
前端 HTTP 请求（JSON）                    后端 HTTP 响应（JSON）
       │                                       ▲
       ▼                                       │
  Jackson 反序列化                        Jackson 序列化
  JSON → Java DTO/对象                    Java 对象 → JSON
       │                                       ▲
       ▼                                       │
  ┌─────────────────────────────────────────────┐
  │              Controller / Service           │
  └─────────────────────────────────────────────┘
```

你通常不需要手动调用 Jackson——Spring Boot 自动完成了这些工作。但理解 Jackson 的注解和配置机制，对于控制 JSON 的序列化行为至关重要。

---

## 2. 项目版本与依赖

### 2.1 版本信息

项目通过 `pom.xml` 中的 `spring-boot-starter-webmvc` 间接引入 Jackson：

```
spring-boot-starter-webmvc (4.0.6)
  └─ spring-boot-starter-jackson (4.0.6)
       └─ spring-boot-jackson (4.0.6)
            └─ tools.jackson.core:jackson-databind:3.1.2   ← 核心
                 └─ tools.jackson.core:jackson-core:3.1.2   ← 流式 API
                 └─ com.fasterxml.jackson.core:jackson-annotations:2.21  ← 注解
```

> **Jackson 3.1.2** 是 3.x 线的第一个 LTS 版本（约 2 年支持周期）。

### 2.2 包名变化要点

Jackson 3.x 最大的变化是 **Java 包名从 `com.fasterxml.jackson` 改为 `tools.jackson`**，但有一个重要例外：

```
┌─────────────────────────────────┬───────────────────────────────┐
│           模块                  │   Jackson 3.x 包名            │
├─────────────────────────────────┼───────────────────────────────┤
│  jackson-databind (核心)        │  tools.jackson.databind       │
│  jackson-core (流式 API)       │  tools.jackson.core           │
│  jackson-annotations (注解)    │  com.fasterxml.jackson.annotation  ← 不变!
│  @JsonSerialize/@JsonDeserialize│  tools.jackson.databind.annotation  ← 移动了!
└─────────────────────────────────┴───────────────────────────────┘
```

**关键结论：**

- `@JsonProperty`、`@JsonIgnore`、`@JsonFormat` 等常用注解的 import 仍是 `com.fasterxml.jackson.annotation.*`
- `ObjectMapper` / `JsonMapper` 的 import 变为 `tools.jackson.databind.*`

### 2.3 项目中的两套 Jackson

```
项目 classpath 上同时存在两套 Jackson：

① Jackson 3.1.2 (tools.jackson.core)     ← Spring Boot HTTP 序列化用这个
   来源: spring-boot-starter-jackson

② Jackson 2.21.2 (com.fasterxml.jackson.core)  ← logstash 日志编码用这个
   来源: logstash-logback-encoder:8.0
```

两套 Jackson **不冲突**：Spring Boot 自动配置使用 3.x 版本处理 HTTP 请求/响应，logstash 使用 2.x 版本编码日志。日常开发只需要关注 3.x。

---

## 3. Spring Boot 中的 Jackson

### 3.1 自动配置：你不需要创建 ObjectMapper

Spring Boot 自动配置了一个 `ObjectMapper` Bean（实际类型是 `JsonMapper`），并注册为 HTTP 消息转换器：

```
┌──────────────────────────────────────────────────────────┐
│                  Spring Boot 自动配置                     │
│                                                          │
│  ① 创建 ObjectMapper Bean（自动配置各项 Feature 默认值） │
│                                                          │
│  ② 注册为 HTTP 消息转换器                                │
│     （HTTP 请求 ↔ JSON 自动转换）                        │
│                                                          │
│  ③ @RequestBody → 反序列化 JSON 为 Java 对象             │
│  ④ 返回值 → 序列化 Java 对象为 JSON 响应                 │
│                                                          │
│  ⑤ 应用 spring.jackson.* 配置项                          │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

在 Controller 中，Jackson 的序列化/反序列化是**全自动**的，不需要任何 Jackson 代码：

```java
@PostMapping("/addCategory")
public Object addCategory(@Valid @RequestBody CreateCategoryDTO dto) {
    //     ↑ Spring 自动将请求体 JSON 反序列化为 DTO
    return marketService.addCategory(dto.name(), dto.image());
    //     ↑ 返回值自动序列化为 JSON 响应
}
```

```
请求流程：
  前端 POST {"name":"川菜","image":"chuan.jpg"}
    │
    ▼ JacksonHttpMessageConverter 自动反序列化
    │
  CreateCategoryDTO{name="川菜", image="chuan.jpg"}
    │
    ▼ @Valid 校验通过后进入 Controller
    │
  Service 处理，返回 Market 对象
    │
    ▼ GlobalResponseBodyAdvice 包装为 {code, message, data}
    │
    ▼ JacksonHttpMessageConverter 自动序列化
    │
  前端收到 {"code":200,"message":"success","data":{...}}
```

### 3.2 ObjectMapper 核心 API

Spring Boot 配置的 `ObjectMapper` 提供以下核心方法（后续章节详细介绍）：

```
┌──────────────────────────────────────────────────────────┐
│                    ObjectMapper                          │
│              (Spring Boot 自动配置，线程安全)             │
│                                                          │
│   writeValueAsString(obj)     →  对象转 JSON 字符串       │
│   readValue(json, Class)      →  JSON 字符串转对象       │
│   writeValueAsBytes(obj)      →  对象转 JSON 字节数组     │
│   readTree(json)              →  JSON 转 JsonNode 树     │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

### 3.3 数据流全景

```
              writeValueAsString()                    readValue()
Java 对象  ───────────────────────▶  JSON 字符串  ───────────────▶  Java 对象
                                    │
                                    │  readTree()
                                    ▼
                               JsonNode（树模型）
                               （灵活访问任意节点）
```

---

## 4. 对象 → JSON（序列化）

### 4.1 基本用法

> 以下示例中 `mapper` 指通过 Spring Boot 注入的 `ObjectMapper`（参见 [第 8 节](#8-在-service-中使用-objectmapper)）。

```java
// 准备一个 Java 对象
public record Category(String name, String image) {}

Category cat = new Category("川菜", "/static/images/market/chuan.jpg");

// 序列化为 JSON 字符串
String json = mapper.writeValueAsString(cat);
// 结果: {"name":"川菜","image":"/static/images/market/chuan.jpg"}
```

### 4.2 序列化为字节数组 / 文件

```java
// 序列化为 byte[]
byte[] bytes = mapper.writeValueAsBytes(cat);

// 序列化写入文件
mapper.writeValue(new File("category.json"), cat);

// 序列化写入 OutputStream
mapper.writeValue(response.getOutputStream(), cat);
```

### 4.3 集合与嵌套对象

```java
List<Category> list = List.of(
    new Category("川菜", "chuan.jpg"),
    new Category("粤菜", "yue.jpg")
);

String json = mapper.writeValueAsString(list);
// 结果: [{"name":"川菜","image":"chuan.jpg"},{"name":"粤菜","image":"yue.jpg"}]

Map<String, Object> map = new LinkedHashMap<>();
map.put("code", 200);
map.put("data", list);

String json2 = mapper.writeValueAsString(map);
// 结果: {"code":200,"data":[{"name":"川菜",...},{"name":"粤菜",...}]}
```

> **提示：** 项目中 `GlobalResponseBodyAdvice` 就是把返回值包装成 `Map<String, Object>`（包含 code/message/data），然后由 Spring Boot 自动调用 Jackson 序列化为 JSON 响应。

### 4.4 异常处理

Jackson 3.x 的异常是 **unchecked**（继承 `RuntimeException`），不需要 `try-catch` 或 `throws` 声明：

```java
// Jackson 2.x：必须处理 IOException
// String json = mapper.writeValueAsString(cat);  // throws JsonProcessingException

// Jackson 3.x：异常是 unchecked，不需要声明
String json = mapper.writeValueAsString(cat);  // 不需要 try-catch

// 如果你想处理异常，仍然可以 try-catch
try {
    String json = mapper.writeValueAsString(cat);
} catch (JacksonException e) {  // 注意：是 JacksonException，不是 JsonProcessingException
    // 处理异常
}
```

异常类名变化：

```
Jackson 2.x                          →  Jackson 3.x
─────────────────────────────────       ─────────────────────────
JsonProcessingException               →  JacksonException (继承 RuntimeException)
JsonMappingException                 →  DatabindException
JsonParseException                   →  StreamReadException
JsonEOFException                     →  UnexpectedEndOfInputException
```

---

## 5. JSON → 对象（反序列化）

### 5.1 基本用法

> 以下示例中 `mapper` 指通过 Spring Boot 注入的 `ObjectMapper`（参见 [第 8 节](#8-在-service-中使用-objectmapper)）。

```java
String json = """
    {"name":"川菜","image":"/static/images/market/chuan.jpg"}
    """;

// 反序列化为 record / class
Category cat = mapper.readValue(json, Category.class);
// cat.name() = "川菜"
// cat.image() = "/static/images/market/chuan.jpg"
```

### 5.2 反序列化集合

Jackson 3.x 提供了类型引用来处理泛型集合：

```java
import tools.jackson.core.type.TypeReference;

String json = """
    [{"name":"川菜","image":"chuan.jpg"},{"name":"粤菜","image":"yue.jpg"}]
    """;

// 反序列化为 List<Category>
List<Category> list = mapper.readValue(json, new TypeReference<List<Category>>() {});

// 反序列化为 Map<String, Object>
String json2 = "{\"code\":200,\"data\":{\"name\":\"川菜\"}}";
Map<String, Object> map = mapper.readValue(json2, new TypeReference<Map<String, Object>>() {});
```

### 5.3 从不同来源读取

```java
// 从字符串
Category cat1 = mapper.readValue(json, Category.class);

// 从 byte[]
Category cat2 = mapper.readValue(bytes, Category.class);

// 从文件
Category cat3 = mapper.readValue(new File("category.json"), Category.class);

// 从 InputStream
Category cat4 = mapper.readValue(inputStream, Category.class);

// 从 URL
Category cat5 = mapper.readValue(new URL("http://..."), Category.class);
```

### 5.4 未知字段处理

Jackson 3.x 默认**忽略** JSON 中的未知字段（`FAIL_ON_UNKNOWN_PROPERTIES` 默认为 `false`）：

```java
// JSON 中有 Java 类没有的 "extra" 字段
String json = """
    {"name":"川菜","image":"chuan.jpg","extra":"不需要的字段"}
    """;

// Jackson 3.x：默认忽略 extra，不会报错
Category cat = mapper.readValue(json, Category.class);
// cat.name() = "川菜"
// cat.image() = "chuan.jpg"
// "extra" 被静默丢弃
```

> **注意：** Jackson 2.x 中 `FAIL_ON_UNKNOWN_PROPERTIES` 默认是 `true`（会报错）。3.x 改为 `false`（忽略）。如果需要严格模式，可以通过配置开启。

---

## 6. JsonNode：动态 JSON 处理

当 JSON 结构不确定或你只需要访问其中某几个字段时，不需要定义完整的 Java 类，可以用 `JsonNode`：

> 以下示例中 `mapper` 指通过 Spring Boot 注入的 `ObjectMapper`（参见 [第 8 节](#8-在-service-中使用-objectmapper)）。

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

JsonNode root = mapper.readTree(json);

// 访问字段
int code = root.get("code").asInt();                    // 200
String message = root.get("message").asText();           // "success"
String name = root.at("/data/name").asText();            // "川菜"（JSON Pointer 语法）

// 访问数组
JsonNode foods = root.at("/data/foods");
for (JsonNode food : foods) {
    String foodName = food.get("name").asText();
    int price = food.get("price").asInt();
}

// 检查字段是否存在
boolean hasPrice = root.at("/data/foods/0/price").has("value");
// 也可以用 path() 安全访问（不存在时返回 MissingNode 而非 null）
String safe = root.path("nonexistent").path("field").asText("");  // ""
```

```
JsonNode 类型判断：

  node.isObject()    → 是 JSON 对象 {}
  node.isArray()     → 是 JSON 数组 []
  node.isTextual()   → 是字符串
  node.isInt()       → 是整数
  node.isBoolean()   → 是布尔值
  node.isNull()      → 是 null
  node.isMissingNode() → 字段不存在（区别于 null）
```

---

## 7. 常用注解大全

> **重要：** `@JsonProperty`、`@JsonIgnore`、`@JsonFormat` 等注解的 import 包仍是 `com.fasterxml.jackson.annotation.*`（Jackson 3.x 没有改变注解包）。

### 7.1 @JsonProperty — 字段名映射

将 JSON 字段名与 Java 字段名做映射（双向：序列化 + 反序列化）。

```java
import com.fasterxml.jackson.annotation.JsonProperty;

// 项目实际案例：CreateOrderDTO.OrderFoodDTO
public record OrderFoodDTO(
        @JsonProperty("_id") String id,    // JSON: "_id" ↔ Java: "id"
        String name,
        String describe,
        String burden,
        String image,
        Integer value) {
}
```

```
反序列化:  {"_id":"abc","name":"宫保鸡丁"}  →  OrderFoodDTO{id="abc", name="宫保鸡丁"}
序列化:    OrderFoodDTO{id="abc",...}     →  {"_id":"abc",...}
```

**项目中的使用场景：** 前端使用 MongoDB 风格的 `_id` 作为字段名，但 Java 命名规范不允许变量以 `_` 开头。`@JsonProperty` 在两者之间架桥。

### 7.2 @JsonIgnore — 忽略字段

让 Jackson 在序列化/反序列化时完全忽略该字段。

```java
import com.fasterxml.jackson.annotation.JsonIgnore;

public class User {
    private String username;

    @JsonIgnore
    private String password;    // 不会出现在 JSON 中，也不会从 JSON 读取

    @JsonIgnore
    private transient cache;   // 临时缓存，不序列化
}
```

### 7.3 @JsonIgnoreProperties — 批量忽略

在类级别或字段级别忽略多个属性。

```java
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 类级别：忽略 JSON 中的未知字段（等价于全局配置）
@JsonIgnoreProperties(ignoreUnknown = true)
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

### 7.4 @JsonFormat — 格式化

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

### 7.5 @JsonInclude — 控制空值输出

控制何时将字段包含在 JSON 输出中。

```java
import com.fasterxml.jackson.annotation.JsonInclude;

// 类级别：null 字段不出现在 JSON 中
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Category {
    private String name;
    private String image;    // 如果为 null，JSON 中不包含此字段
}

// 字段级别：单独控制
public class UpdateFoodDTO {
    private String name;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)  // null 和空字符串都不输出
    private String image;
}
```

```
Include 选项：

  Include.ALWAYS          始终包含（默认）
  Include.NON_NULL         非 null 才包含
  Include.NON_EMPTY        非空才包含（null、空字符串、空集合）
  Include.NON_DEFAULT      值与默认值不同才包含
  Include.NON_ABSENT       非 null 且非 Optional.empty() 才包含
```

### 7.6 @JsonAlias — 反序列化别名

允许 JSON 中使用多个字段名映射到同一个 Java 字段（仅反序列化）。

```java
import com.fasterxml.jackson.annotation.JsonAlias;

public record FoodDTO(
        @JsonAlias({"_id", "foodId"}) String id,  // 接受 _id 或 foodId
        String name) {
}

// 以下两种 JSON 都能正确反序列化：
// {"id":"abc","name":"宫保鸡丁"}       ← 主名
// {"_id":"abc","name":"宫保鸡丁"}       ← 别名 1
// {"foodId":"abc","name":"宫保鸡丁"}    ← 别名 2
```

### 7.7 @JsonCreator / @JsonProperty — 构造器绑定

当 record 的构造器参数名与 JSON 字段名不完全对应时使用。

```java
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

// record 的标准写法（Jackson 3.x 自动检测构造器参数名）
public record Category(String name, String image) {}

// 如果需要自定义映射
public record Category(
        @JsonProperty("category_name") String name,    // JSON 用 "category_name"
        @JsonProperty("category_image") String image) {}
```

### 7.8 @JsonPropertyOrder — 控制字段顺序

```java
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"id", "name", "image", "foods"})
public class Market {
    private String id;
    private List<FoodItem> foods;  // 声明在后，但输出在最后
    private String name;
    private String image;
}

// 输出: {"id":"...","name":"...","image":"...","foods":[...]}
```

### 7.9 @JsonRootName — 包装根节点

```java
import com.fasterxml.jackson.annotation.JsonRootName;

@JsonRootName("category")
public class Category {
    private String name;
    private String image;
}

// 需要启用 SerializationFeature.WRAP_ROOT_VALUE
// 输出: {"category":{"name":"川菜","image":"chuan.jpg"}}
```

### 7.10 注解速查表

```
┌────────────────────────┬──────────────────────────────┬──────────────┐
│        注解            │          作用                │  方向         │
├────────────────────────┼──────────────────────────────┼──────────────┤
│  @JsonProperty         │  字段名映射                  │  双向         │
│  @JsonIgnore           │  忽略单个字段                │  双向         │
│  @JsonIgnoreProperties │  批量忽略/忽略未知           │  双向         │
│  @JsonFormat           │  日期/数字格式               │  序列化       │
│  @JsonInclude          │  控制空值输出                │  序列化       │
│  @JsonAlias            │  反序列化别名                │  反序列化     │
│  @JsonPropertyOrder    │  控制字段输出顺序            │  序列化       │
│  @JsonRootName         │  包装根节点名                │  序列化       │
│  @JsonCreator          │  指定反序列化构造器          │  反序列化     │
│  @JsonNaming           │  命名策略（驼峰↔下划线等）  │  双向         │
└────────────────────────┴──────────────────────────────┴──────────────┘
```

> **import 包提醒：** 以上注解全部来自 `com.fasterxml.jackson.annotation.*`，在 Jackson 3.x 中没有改变。但 `@JsonSerialize` 和 `@JsonDeserialize` 例外，它们移到了 `tools.jackson.databind.annotation`。

---

## 8. 在 Service 中使用 ObjectMapper

### 8.1 注入 ObjectMapper

Controller 中的 JSON 转换是自动的，但在 Service / Repository 中如果需要手动序列化（如 Redis 缓存、调用第三方 API），直接注入 Spring Boot 配置好的 `ObjectMapper`：

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

> **不要 `new ObjectMapper()`**：Spring Boot 已经创建并配置好了 ObjectMapper Bean（包含日期格式、模块注册等自动配置），直接注入即可。手动 new 出来的实例不会包含这些配置。

### 8.2 典型场景：Redis 缓存序列化

项目中 Redis 缓存需要在 Service 层手动序列化/反序列化对象，注入 `ObjectMapper` 是标准做法：

```java
@Repository
@RequiredArgsConstructor
public class RedisRepository {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;  // 注入 Spring Boot 配置的实例

    // 写入：对象 → JSON 字符串 → Redis
    public <T> void setObject(String key, T value) {
        String json = objectMapper.writeValueAsString(value);
        stringRedisTemplate.opsForValue().set(key, json);
    }

    // 读取：Redis → JSON 字符串 → 对象
    public <T> T getObject(String key, Class<T> clazz) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) return null;
        return objectMapper.readValue(json, clazz);
    }
}
```

### 8.3 何时需要手动使用

以下场景需要手动调用 ObjectMapper（而非依赖 Spring Boot 自动转换）：

| 场景           | 原因                                          |
| -------------- | --------------------------------------------- |
| Redis 缓存读写 | Redis 存取的是字符串，需要手动序列化/反序列化 |
| 调用第三方 API | 需要解析返回的 JSON 响应                      |
| 序列化到文件   | 不走 HTTP 消息转换器                          |
| 单元测试       | 构造 JSON 输入或验证输出                      |

> **注意：** 即使在这些场景下，也通过注入 `ObjectMapper` 使用 Spring Boot 配置好的实例，不要手动创建。

---

## 9. 日期与时间处理

### 9.1 Jackson 3.x 的日期默认行为

Jackson 3.x 有一个重要的默认值变化：

```
                    Jackson 2.x             Jackson 3.x
WRITE_DATES_AS_TIMESTAMPS    true (时间戳)     false (ISO 字符串)  ← 变了!

Date 输出:     1781234567890          →  "2026-07-07T06:30:00.000+00:00"
```

项目中的 `Order` 实体有 `Date createdAt` 字段：

```java
// Order.java
public class Order {
    private String id;
    private String date;
    private Date createdAt;     // ← java.util.Date
    // ...
}
```

在 Jackson 3.x 下，`createdAt` 默认序列化为 ISO-8601 字符串：

```json
{
  "id": "abc123",
  "createdAt": "2026-07-07T14:30:00.000+0800",
  ...
}
```

### 9.2 @JsonFormat 控制日期格式

```java
import com.fasterxml.jackson.annotation.JsonFormat;

public class Order {

    // 输出: "2026-07-07 14:30:00"
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date createdAt;

    // 输出: "2026-07-07"
    @JsonFormat(pattern = "yyyy-MM-dd")
    private Date birthday;
}
```

### 9.3 Java 8 时间类型

Jackson 3.x **内置**了对 `java.time` 包的支持（2.x 需要单独注册 `jackson-datatype-jsr310` 模块）：

```java
import java.time.*;
import java.time.format.DateTimeFormatter;

public class Order {
    private LocalDateTime createdAt;      // "2026-07-07T14:30:00"
    private LocalDate birthday;           // "2026-07-07"
    private Instant timestamp;            // "2026-07-07T06:30:00Z"
    private Duration timeout;             // "PT30S" (ISO-8601 持续时间)
    private ZonedDateTime meetingTime;    // "2026-07-07T14:30:00+08:00[Asia/Shanghai]"
}
```

> **项目案例：** `GlobalExceptionHandler` 中使用了 `Instant.now().toString()` 手动格式化时间戳。如果改为直接返回 `Instant` 对象，Jackson 3.x 会自动序列化为 ISO-8601 格式字符串。

### 9.4 时区

```
Jackson 3.x 默认时区: UTC（不是 JVM 默认时区！）

如果需要使用本地时区：
  ① 全局配置: spring.jackson.time-zone: Asia/Shanghai
  ② 字段配置: @JsonFormat(timezone = "Asia/Shanghai")
```

---

## 10. 配置项 spring.jackson.\*

Spring Boot 通过 `application.yml` 中的 `spring.jackson.*` 属性配置 Jackson 行为：

```yaml
spring:
  jackson:
    # 日期格式（全局）
    date-format: yyyy-MM-dd HH:mm:ss
    # 时区
    time-zone: Asia/Shanghai
    # 默认序列化包含策略
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

> **项目现状：** 当前 `application-dev.yml` 和 `application-prod.yml` 中没有配置任何 `spring.jackson.*` 属性，全部使用 Jackson 3.x 的默认值。

---

## 11. 自定义序列化器与反序列化器

当内置功能无法满足需求时，可以编写自定义的序列化器/反序列化器。

### 11.1 自定义序列化器

```java
import tools.jackson.databind.ValueSerializer;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;

// 自定义：把价格（分）转为（元）
public class PriceSerializer extends ValueSerializer<Integer> {

    @Override
    public void serialize(Integer priceInCents, JsonGenerator gen, SerializationContext ctx) {
        // 3800 分 → "38.00元"
        gen.writeString(String.format("%.2f元", priceInCents / 100.0));
    }
}
```

```java
// 使用 @JsonSerialize 绑定（注意：3.x 包名变了！）
import tools.jackson.databind.annotation.JsonSerialize;

public class FoodItem {
    private String name;

    @JsonSerialize(using = PriceSerializer.class)
    private Integer price;    // 输出: "38.00元" 而非 3800
}
```

> **注意：** `@JsonSerialize` 在 Jackson 3.x 中从 `com.fasterxml.jackson.databind.annotation` 移到了 `tools.jackson.databind.annotation`。

### 11.2 自定义反序列化器

```java
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;

// 自定义：灵活解析日期字段（支持多种格式）
public class FlexibleDateDeserializer extends ValueDeserializer<Date> {

    @Override
    public Date deserialize(JsonParser p, DeserializationContext ctx) {
        String dateStr = p.getString();

        // 尝试多种格式
        if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return parseDate(dateStr, "yyyy-MM-dd");
        } else if (dateStr.matches("\\d{4}/\\d{2}/\\d{2}")) {
            return parseDate(dateStr, "yyyy/MM/dd");
        } else if (dateStr.matches("\\d+")) {
            return new Date(Long.parseLong(dateStr));  // 时间戳
        }
        throw new IllegalArgumentException("无法解析日期: " + dateStr);
    }
}
```

```java
import tools.jackson.databind.annotation.JsonDeserialize;

public class Order {
    @JsonDeserialize(using = FlexibleDateDeserializer.class)
    private Date createdAt;
}
```

### 11.3 在 Spring Boot 中注册

在 Spring Boot 中，不需要手动创建 `JsonMapper` 并注册序列化器。推荐通过 `@Bean` 方式自定义 `ObjectMapper`，Spring Boot 会自动应用：

```java
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper(JsonMapper.Builder builder) {
        return builder
                .addSerializer(Integer.class, new PriceSerializer())
                .addDeserializer(Date.class, new FlexibleDateDeserializer())
                .build();
    }
}
```

> 或者更简单地：如果只需要针对个别字段使用自定义序列化器，直接用 `@JsonSerialize` / `@JsonDeserialize` 注解即可，不需要全局注册。

### 11.4 Jackson 2.x → 3.x 类名对照

```
Jackson 2.x                              →  Jackson 3.x
─────────────────────────────────────       ──────────────────────────────
com.fasterxml.jackson.databind
  JsonSerializer<T>                       →  tools.jackson.databind.ValueSerializer<T>
  JsonDeserializer<T>                     →  tools.jackson.databind.ValueDeserializer<T>
  SerializerProvider                      →  tools.jackson.databind.SerializationContext
  DeserializationContext                  →  (同名，包变了)
  Module                                  →  tools.jackson.databind.JacksonModule

com.fasterxml.jackson.databind.annotation
  @JsonSerialize                          →  tools.jackson.databind.annotation.@JsonSerialize
  @JsonDeserialize                        →  tools.jackson.databind.annotation.@JsonDeserialize
```

---

## 12. Jackson 3.x 关键变化（vs 2.x）

### 12.1 变化全景图

```
┌──────────────────────────────────────────────────────────────────┐
│                   Jackson 3.x 核心变化                           │
├──────────────────┬───────────────────────┬───────────────────────┤
│       维度       │     Jackson 2.x       │     Jackson 3.x       │
├──────────────────┼───────────────────────┼───────────────────────┤
│  Java 基线       │  Java 8               │  Java 17              │
│  核心包名        │  com.fasterxml.jackson │  tools.jackson        │
│  注解包名        │  com.fasterxml.jackson │  com.fasterxml.jackson │ ← 不变!
│                  │  .annotation          │  .annotation           │
│  ObjectMapper    │  可变（可直接配置）    │  不可变（Builder 模式）│
│  推荐入口类      │  ObjectMapper          │  JsonMapper           │
│  异常类型        │  checked (IOException) │  unchecked (Runtime)  │
│  日期默认输出    │  时间戳 (1734456789000)│  ISO 字符串           │
│  未知字段处理    │  报错 (FAIL_ON=true)   │  忽略 (FAIL_ON=false) │
│  尾部多余内容    │  不检查                │  检查 (FAIL_ON=true)  │
│  java.time 支持  │  需注册 jsr310 模块    │  内置支持             │
│  Optional 支持   │  需注册 jdk8 模块      │  内置支持             │
│  默认时区        │  JVM 默认时区          │  UTC                  │
│  属性排序        │  声明顺序              │  字母顺序 (默认)      │
└──────────────────┴───────────────────────┴───────────────────────┘
```

### 12.2 默认配置变化

以下默认值的变化最可能影响项目行为：

| 配置项                           | 2.x 默认 | 3.x 默认    | 影响                            |
| -------------------------------- | -------- | ----------- | ------------------------------- |
| `WRITE_DATES_AS_TIMESTAMPS`      | `true`   | **`false`** | 日期输出为 ISO 字符串而非时间戳 |
| `FAIL_ON_UNKNOWN_PROPERTIES`     | `true`   | **`false`** | 未知字段不报错（更宽松）        |
| `FAIL_ON_TRAILING_TOKENS`        | `false`  | **`true`**  | JSON 尾部有多余内容会报错       |
| `FAIL_ON_NULL_FOR_PRIMITIVES`    | `false`  | **`true`**  | 基本类型收到 null 会报错        |
| `SORT_PROPERTIES_ALPHABETICALLY` | `false`  | **`true`**  | 属性按字母排序输出              |
| `READ_ENUMS_USING_TO_STRING`     | `false`  | **`true`**  | 枚举用 toString() 反序列化      |
| `ALLOW_FINAL_FIELDS_AS_MUTATORS` | `true`   | **`false`** | 不再通过反射修改 final 字段     |

### 12.3 兼容旧版行为

如果需要恢复 Jackson 2.x 的默认行为，可以在 `application.yml` 中逐项配置，或通过 `@Bean` 覆盖：

```java
@Bean
public ObjectMapper objectMapper() {
    return JsonMapper.builderWithJackson2Defaults().build();
}
```

---

## 13. 项目实际使用案例

### 13.1 唯一的 Jackson 注解：@JsonProperty

项目中唯一使用的 Jackson 注解在 [CreateOrderDTO.java](file:///d:/javaProject/JavaDoc/src/main/java/com/example/javadoc/module/order/dto/CreateOrderDTO.java)：

```java
public record CreateOrderDTO(
        @NotBlank(message = "缺少日期") String date,
        @NotNull(message = "缺少数量") Integer num,
        @NotEmpty(message = "缺少菜品") @Valid List<OrderFoodDTO> foods) {

    public record OrderFoodDTO(
            @JsonProperty("_id") @NotBlank(message = "缺少菜名id") String id,
            //     ↑ 前端传 "_id"，Java 用 "id"
            @NotBlank(message = "缺少菜名") String name,
            @NotBlank(message = "缺少描述") String describe,
            @NotBlank(message = "缺少配料") String burden,
            @NotBlank(message = "缺少图片") String image,
            @NotNull(message = "缺少数量") Integer value) {
    }
}
```

```
前端发送的 JSON：
{
  "date": "2026-07-07",
  "num": 2,
  "foods": [
    { "_id": "abc123", "name": "宫保鸡丁", "describe": "...", "burden": "...", "image": "...", "value": 2 }
  ]
}

        │  Spring Boot 自动调用 Jackson 反序列化
        │  @JsonProperty("_id") 将 JSON "_id" 映射为 Java "id"
        ▼

CreateOrderDTO {
  date = "2026-07-07",
  num = 2,
  foods = [
    OrderFoodDTO { id = "abc123", name = "宫保鸡丁", ... }
  ]
}
```

### 13.2 隐式序列化：GlobalResponseBodyAdvice

[GlobalResponseBodyAdvice.java](file:///d:/javaProject/JavaDoc/src/main/java/com/example/javadoc/core/advice/GlobalResponseBodyAdvice.java) 将返回值包装为统一格式，Jackson 自动序列化：

```java
@Override
public Object beforeBodyWrite(Object body, ...) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("code", 200);
    result.put("message", "success");
    result.put("data", body);
    return result;
    //     ↑ Jackson 自动将这个 Map 序列化为 JSON
}
```

```
Controller 返回 Market Entity
  │
  ▼ GlobalResponseBodyAdvice 包装
  │
Map{code=200, message="success", data=Market{id, name, image, foods}}
  │
  ▼ Jackson 自动序列化
  │
{"code":200,"message":"success","data":{"id":"...","name":"川菜","image":"...","foods":[...]}}
```

### 13.3 隐式序列化：GlobalExceptionHandler

[GlobalExceptionHandler.java](file:///d:/javaProject/JavaDoc/src/main/java/com/example/javadoc/core/advice/GlobalExceptionHandler.java) 返回错误响应，同样由 Jackson 序列化：

```java
private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, ...) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("code", status.value());
    body.put("timestamp", Instant.now().toString());  // ← 手动格式化，也可交给 Jackson
    body.put("path", request.getRequestURI());
    body.put("message", message);
    return ResponseEntity.status(status).body(body);
    //                                       ↑ Jackson 自动序列化
}
```

### 13.4 隐式反序列化：@RequestBody

所有 `@RequestBody` 参数都由 Jackson 自动反序列化：

```java
// MarketController.java
@PostMapping("/addCategory")
public Object addCategory(@Valid @RequestBody CreateCategoryDTO dto) {
    //                        ↑ Jackson 将 JSON 反序列化为 DTO
    return marketService.addCategory(dto.name(), dto.image());
}
```

### 13.5 Map<String, Object> 接收松散 JSON

[MarketController.java](file:///d:/javaProject/JavaDoc/src/main/java/com/example/javadoc/module/market/controller/MarketController.java) 中有一个用 Map 接收 JSON 的接口：

```java
@PutMapping("/updateFoodWithNum")
public Object updateFoodWithNum(@RequestBody Map<String, Object> body) {
    @SuppressWarnings("unchecked")
    List<String> foodIds = (List<String>) body.get("foodIds");
    int num = body.containsKey("increment") ? ((Number) body.get("increment")).intValue() : 1;
    return foodService.updateFoodWithNum(foodIds, num);
}
```

```
这里没有定义 DTO，直接用 Map 接收 JSON：
  Jackson 将 {"foodIds":["a","b"],"increment":2}
  反序列化为 Map{foodIds=List["a","b"], increment=2}

适用场景：字段少且不需要校验时可以这样做，但不如 DTO 类型安全。
```

---

## 14. 速查清单

### 14.1 对象 ↔ JSON 互转速查

```
┌──────────────────────────────────────────────────────────────┐
│                  对象 ↔ JSON 互转速查                         │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ① 对象 → JSON 字符串                                        │
│     mapper.writeValueAsString(obj)                          │
│                                                              │
│  ② JSON 字符串 → 对象                                        │
│     mapper.readValue(json, MyClass.class)                   │
│                                                              │
│  ③ JSON 字符串 → List<T>                                    │
│     mapper.readValue(json, new TypeReference<List<T>>(){}) │
│                                                              │
│  ④ JSON 字符串 → Map                                        │
│     mapper.readValue(json, new TypeReference<Map<K,V>>(){}) │
│                                                              │
│  ⑤ JSON 字符串 → JsonNode（动态访问）                        │
│     mapper.readTree(json)                                   │
│                                                              │
│  ⑥ 对象 → 文件                                               │
│     mapper.writeValue(new File("out.json"), obj)            │
│                                                              │
│  ⑦ 文件 → 对象                                               │
│     mapper.readValue(new File("in.json"), MyClass.class)    │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 14.2 常用注解速查

```
┌──────────────────────────────────────────────────────────────┐
│                    常用注解速查                               │
├────────────────────────┬─────────────────────────────────────┤
│  @JsonProperty("x")   │  JSON 字段名 ↔ Java 字段名映射       │
│  @JsonIgnore          │  完全忽略此字段                      │
│  @JsonIgnoreProperties │  类级别忽略未知字段 / 批量忽略      │
│  @JsonFormat           │  日期/数字格式 + 时区                │
│  @JsonInclude          │  控制空值输出（NON_NULL 等）        │
│  @JsonAlias            │  反序列化别名（接受多种字段名）     │
│  @JsonPropertyOrder    │  控制字段输出顺序                   │
│  @JsonRootName         │  包装根节点                         │
│  @JsonNaming           │  命名策略（驼峰↔下划线等）         │
│                        │                                     │
│  @JsonSerialize (3.x)  │  绑定自定义序列化器                 │
│    包: tools.jackson   │  (import 路径变了!)                 │
│    .databind.annotation│                                     │
│  @JsonDeserialize (3.x)│  绑定自定义反序列化器               │
│    包: tools.jackson   │  (import 路径变了!)                 │
│    .databind.annotation│                                     │
└────────────────────────┴─────────────────────────────────────┘
```

### 14.3 import 包速查

```
┌──────────────────────────────────────┬───────────────────────────────────────────┐
│                内容                  │  Jackson 3.x import 包                    │
├──────────────────────────────────────┼───────────────────────────────────────────┤
│  JsonMapper                         │  tools.jackson.databind.JsonMapper         │
│  ObjectMapper (基类)                 │  tools.jackson.databind.ObjectMapper       │
│  ObjectReader / ObjectWriter        │  tools.jackson.databind.ObjectReader/Writer│
│  JsonNode                           │  tools.jackson.databind.JsonNode           │
│  TypeReference                      │  tools.jackson.core.type.TypeReference     │
│  SerializationFeature               │  tools.jackson.databind.SerializationFeature│
│  DeserializationFeature             │  tools.jackson.databind.DeserializationFeature│
│  MapperFeature                      │  tools.jackson.databind.MapperFeature      │
│  DateTimeFeature                    │  tools.jackson.databind.DateTimeFeature    │
│  ValueSerializer / ValueDeserializer│  tools.jackson.databind.ValueSerializer   │
│  JacksonException                   │  tools.jackson.databind.JacksonException   │
│  DatabindException                  │  tools.jackson.databind.DatabindException  │
│                                     │                                           │
│  @JsonProperty  ← 不变!             │  com.fasterxml.jackson.annotation.JsonProperty│
│  @JsonIgnore   ← 不变!              │  com.fasterxml.jackson.annotation.JsonIgnore│
│  @JsonFormat   ← 不变!              │  com.fasterxml.jackson.annotation.JsonFormat│
│  @JsonInclude  ← 不变!              │  com.fasterxml.jackson.annotation.JsonInclude│
│  @JsonAlias    ← 不变!              │  com.fasterxml.jackson.annotation.JsonAlias│
│                                     │                                           │
│  @JsonSerialize ← 移动了!            │  tools.jackson.databind.annotation.JsonSerialize│
│  @JsonDeserialize ← 移动了!         │  tools.jackson.databind.annotation.JsonDeserialize│
└──────────────────────────────────────┴───────────────────────────────────────────┘
```

### 14.4 项目约定速查

```
┌──────────────────────────────────────────────────────────┐
│                项目 Jackson 使用约定                      │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  ① 不手动创建 ObjectMapper — Spring Boot 自动配置          │
│                                                          │
│  ② 不配置 spring.jackson.* — 使用 3.x 默认值             │
│                                                          │
│  ③ 注解仅用 @JsonProperty 做字段名映射                    │
│     import: com.fasterxml.jackson.annotation.JsonProperty│
│                                                          │
│  ④ 日期默认输出为 ISO-8601 字符串（3.x 默认）             │
│     需要自定义格式时用 @JsonFormat                        │
│                                                          │
│  ⑤ 未知字段默认忽略（3.x 默认，不会报错）                 │
│                                                          │
│  ⑥ @RequestBody → Jackson 自动反序列化                   │
│     返回值 → Jackson 自动序列化                           │
│                                                          │
│  ⑦ Service 中如需手动使用: 注入 ObjectMapper Bean        │
│     不要 new ObjectMapper() / JsonMapper.builder()        │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

---

**最后：** Jackson 是 Spring Boot 的隐形基础设施——绝大多数时候你在"用它但不知道在用它"。理解它的注解体系和默认行为，能让你在遇到 JSON 序列化/反序列化问题时快速定位和解决。项目当前使用 Jackson 3.1.2 LTS，注解包未变（`com.fasterxml.jackson.annotation.*`），核心包已迁移到 `tools.jackson.*`，日常开发只需关注注解和配置项即可。
