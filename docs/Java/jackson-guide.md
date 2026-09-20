# Jackson JSON 处理指南

> 本指南基于 `JavaDoc` 项目实际环境，介绍 Jackson JSON 库的概念、常用注解与对象/JSON 互转。
> 项目使用 Spring Boot 4.0.6 + Jackson 3.1.2 (LTS)。

---

## 目录

1. [Jackson 是什么](#1-jackson-是什么)
2. [项目版本与依赖](#2-项目版本与依赖)
3. [Spring Boot 中的 Jackson](#3-spring-boot-中的-jackson)
4. [对象 → JSON（序列化）](#4-对象-json序列化)
5. [JSON → 对象（反序列化）](#5-json-对象反序列化)
6. [JsonNode：动态 JSON 处理](#6-jsonnode动态-json-处理)
7. [常用注解大全](#7-常用注解大全)
8. [在 Service 中使用 ObjectMapper](#8-在-service-中使用-objectmapper)
9. [日期与时间处理](#9-日期与时间处理)
10. [配置项 spring.jackson.\*](#10-配置项-springjackson)
11. [自定义序列化器与反序列化器](#11-自定义序列化器与反序列化器)
12. [项目实际使用案例](#12-项目实际使用案例)
13. [速查清单](#13-速查清单)

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

### 2.2 包名与 import 速记

Jackson 3.x 各模块的包名如下，日常开发按这张表 import：

| 模块                                  | Jackson 3.x 包名                    |
| ------------------------------------- | ----------------------------------- |
| jackson-databind（核心）              | `tools.jackson.databind`            |
| jackson-core（流式 API）              | `tools.jackson.core`                |
| jackson-annotations（注解）           | `com.fasterxml.jackson.annotation`  |
| `@JsonSerialize` / `@JsonDeserialize` | `tools.jackson.databind.annotation` |
| `@JsonNaming`                         | `tools.jackson.databind.annotation` |

**关键结论：**

- `@JsonProperty`、`@JsonIgnore`、`@JsonFormat` 等常用注解的 import 是 `com.fasterxml.jackson.annotation.*`
- `@JsonSerialize`、`@JsonDeserialize`、`@JsonNaming` 这类"绑定自定义实现"的注解位于 `tools.jackson.databind.annotation`
- `ObjectMapper` / `JsonMapper` 等核心类的 import 是 `tools.jackson.databind.*`

### 2.3 classpath 边界提醒

项目 classpath 上同时存在两套 Jackson 包，但只有一套是给你用的：

- **`tools.jackson.*`（Jackson 3.1.2）**：Spring Boot HTTP 序列化用它；日志编码也用同一套（logstash-logback-encoder 9.0 基于 Jackson 3.x）。
- **`com.fasterxml.*`（旧版 Jackson）**：来自 knife4j 5.2.0 的传递依赖，仅供 knife4j 内部使用。

日常开发一律使用 `tools.jackson.*`。IDE 补全如果出现 `com.fasterxml` 下的 databind 类（如 `ObjectMapper`），不要使用。

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

Jackson 3.x 的异常体系全部是 **unchecked**（继承 `RuntimeException`），不需要 `try-catch` 或 `throws` 声明：

```java
import tools.jackson.core.JacksonException;

// 正常使用：不需要声明或捕获
String json = mapper.writeValueAsString(cat);

// 需要时也可以显式捕获
try {
    String json = mapper.writeValueAsString(cat);
} catch (JacksonException e) {
    // 处理异常
}
```

常用异常类：

| 异常                            | 包                       | 触发场景                |
| ------------------------------- | ------------------------ | ----------------------- |
| `JacksonException`              | `tools.jackson.core`     | 所有 Jackson 异常的基类 |
| `DatabindException`             | `tools.jackson.databind` | 类型不匹配、映射失败    |
| `StreamReadException`           | `tools.jackson.core`     | JSON 语法错误           |
| `UnexpectedEndOfInputException` | `tools.jackson.core`     | JSON 提前结束           |

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

> **注意：** 如果需要严格模式（未知字段报错），通过 `spring.jackson.deserialization.fail-on-unknown-properties: true` 开启。

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
}

// 输出: {"createdAt":"2026-07-07 14:30:00"}
// 而非默认的: {"createdAt":"2026-07-07T06:30:00.000+00:00"}
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

当反序列化需要通过构造器创建对象时使用。`@JsonCreator` 标注构造器，`@JsonProperty` 映射 JSON 字段名到构造器参数。

**record 场景**：Jackson 自动检测规范构造器，只需在参数上加 `@JsonProperty` 做字段名映射：

```java
import com.fasterxml.jackson.annotation.JsonProperty;

// record 无需显式写 @JsonCreator，Jackson 自动识别规范构造器
public record Category(
        @JsonProperty("category_name") String name,    // JSON 用 "category_name"
        @JsonProperty("category_image") String image) {}
```

**普通 class 场景**：必须用 `@JsonCreator` 显式标注构造器，否则 Jackson 不知道用哪个构造器反序列化：

```java
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Category {
    private String name;
    private String image;

    @JsonCreator
    public Category(@JsonProperty("category_name") String name,
                    @JsonProperty("category_image") String image) {
        this.name = name;
        this.image = image;
    }

    // getter / setter 省略
}

// JSON: {"category_name":"川菜","category_image":"chuan.jpg"}
// → Category(name="川菜", image="chuan.jpg")
```

> **注意**：如果构造器参数名与 JSON 字段名完全一致，且项目编译时开启了 `-parameters` 选项（Spring Boot 默认开启），可以省略 `@JsonProperty`，Jackson 会自动按参数名匹配。

### 7.8 @JsonPropertyOrder — 控制字段顺序

```java
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"id", "name", "image", "foods"})
public class Market {
    private String id;
    private List<FoodItem> foods;  // 声明在前，但输出在最后
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

> **import 包提醒：** 上表除 `@JsonNaming` 外的注解都来自 `com.fasterxml.jackson.annotation.*`；`@JsonNaming`、`@JsonSerialize`、`@JsonDeserialize` 位于 `tools.jackson.databind.annotation`。

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

Jackson 3.x 默认把日期输出为 ISO-8601 字符串（`WRITE_DATES_AS_TIMESTAMPS` 默认 `false`），而不是数字时间戳：

```
Date 输出:  "2026-07-07T06:30:00.000+00:00"
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

Jackson 3.x **内置**了对 `java.time` 包的支持，无需注册额外模块：

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
Jackson 3.x 默认时区是 UTC（注意不是 JVM 本地时区）。

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

默认情况下，Jackson 对普通 Bean 走"反射路径"：为每个类生成 `BeanSerializer`，逐个读取 getter/字段，写出同名 JSON 属性：

```
Food { name = "宫保鸡丁", price = 3800 }
      │ BeanSerializer（反射）
      ▼
{ "name" : "宫保鸡丁", "price" : 3800 }
```

当输出格式与字段存储形式不一致、或需要改变整个 JSON 结构时，Jackson 3.x 允许你接管转换过程：**自定义序列化器**负责"对象 → JSON"，**自定义反序列化器**负责"JSON → 对象"。

### 11.1 什么时候需要自定义

写自定义序列化器之前，先确认注解确实解决不了。典型动机有三个：

| 动机           | 说明                   | 例子                      |
| -------------- | ---------------------- | ------------------------- |
| 格式转换       | 同一个值换一种表示     | 日期存 `Date`、输出字符串 |
| 数据变形       | 存储值与输出值不同构   | 价格存"分"，接口输出"元"  |
| 自定义输出形状 | 字段增删改名、结构调整 | 给老客户端输出兼容格式    |

**决策边界（注解优先、自定义兜底）：** 改名、忽略字段、日期格式这类单字段问题，先用 [第 7 节](#7-常用注解大全) 的 `@JsonProperty`、`@JsonIgnore`、`@JsonFormat` 解决；只有当注解无法覆盖——需要跨字段计算、或输出结构与 Java 对象结构完全不同——才编写自定义序列化器。

### 11.2 序列化器类体系

Jackson 3.x 中，自定义序列化器继承抽象基类 `ValueSerializer<T>`（`tools.jackson.databind` 包）。它唯一的抽象方法是：

```java
void serialize(T value, JsonGenerator gen, SerializationContext ctxt)
```

三个参数各自负责一件事：

| 参数    | 类型                                          | 作用                                                   |
| ------- | --------------------------------------------- | ------------------------------------------------------ |
| `value` | `T`                                           | 待序列化的值：字段绑定时是字段值，类级绑定时是整个对象 |
| `gen`   | `tools.jackson.core.JsonGenerator`            | 流式写出器，用 `writeXxx` 方法逐 token 写出 JSON       |
| `ctxt`  | `tools.jackson.databind.SerializationContext` | 上下文，用于读全局配置或委托序列化其他对象             |

实际项目中更常用便利基类 `StdSerializer<T>`（`tools.jackson.databind.ser.std` 包），它继承了 `ValueSerializer`，帮你实现了两块模板代码：

| `StdSerializer` 提供的方法 | 作用                                                                                                                          |
| -------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| `handledType()`            | 告诉 Jackson "这个序列化器处理哪种类型"。`StdSerializer` 在构造器里通过 `super(Integer.class)` 存下来，Jackson 靠它做类型匹配 |
| `wrapAndThrow()`           | 序列化出异常时，包装异常并附加上下文（哪个字段、哪个类出的错），让报错信息更有意义                                            |

对比一下两种写法的差异——直接用 `ValueSerializer` 的等价写法：

```java
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.ValueSerializer;

public class PriceSerializer extends ValueSerializer<Integer> {

    @Override
    public Class<Integer> handledType() {
        return Integer.class;  // 必须自己实现：告诉 Jackson 我处理 Integer
    }

    @Override
    public void serialize(Integer priceInCents, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeString(String.format("%.2f元", priceInCents / 100.0));
    }
}
```

继承 `StdSerializer` 的写法（与 11.3 节示例一致）：

```java
public class PriceSerializer extends StdSerializer<Integer> {

    public PriceSerializer() {
        super(Integer.class);  // 一行搞定 handledType()
    }

    @Override
    public void serialize(Integer priceInCents, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeString(String.format("%.2f元", priceInCents / 100.0));
    }
}
```

> 结论：`StdSerializer` 就是"贴心版"的 `ValueSerializer`——构造器传类型省掉 `handledType()`，内置 `wrapAndThrow()` 省掉异常包装。简单场景下两者差异不大，但 `StdSerializer` 是项目中的惯用选择。

> `serialize` 声明的 `throws JacksonException`（`tools.jackson.core` 包）是 unchecked 异常，重写时不必写 `throws`，可以直接抛出运行时异常。

### 11.3 示例一：把"分"输出为"元"

`Food` 的 `price` 字段存的是"分"（整数 3800），接口要求输出"元"字符串 `"38.00元"`。这是典型的"数据变形"，字段级自定义即可。

**第一步：** 写序列化器，继承 `StdSerializer<Integer>`：

```java
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

// 自定义序列化器：把价格（分）转为（元）字符串
public class PriceSerializer extends StdSerializer<Integer> {

    public PriceSerializer() {
        super(Integer.class);
    }

    @Override
    public void serialize(Integer priceInCents, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeString(String.format("%.2f元", priceInCents / 100.0));
    }
}
```

**第二步：** 用 `@JsonSerialize` 把序列化器绑到字段上（注意包：`tools.jackson.databind.annotation`）：

```java
import tools.jackson.databind.annotation.JsonSerialize;

public class Food {
    private String name;

    @JsonSerialize(using = PriceSerializer.class)
    private Integer price;   // 3800 → "38.00元"
}
```

序列化结果：

```json
{ "name": "宫保鸡丁", "price": "38.00元" }
```

> **证据等级：Verified runnable**——代码与验证工程 `target/guide-verification/jackson-custom-serializer` 一致；`mvn -o test` 通过（7 个测试，0 失败），断言 `writeValueAsString(new Food("宫保鸡丁", 3800))` 包含 `"price":"38.00元"`。

### 11.4 示例二：自定义输出形状（对象级 token API）

`FoodItem` 有 `name`、`describe`、`image`、`price`（分）四个字段，而目标接口需要不同的输出结构：`image` 不输出，`price` 既要保留原始分值、又要补充换算后的 `priceYuan` 元字符串。输出结构与 Java 对象结构不同，需要类级序列化器接管整个对象。

```java
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

// 自定义输出形状：省略 image，price（分）同时输出原始值与 priceYuan（元）
public class FoodItemSerializer extends StdSerializer<FoodItem> {

    public FoodItemSerializer() {
        super(FoodItem.class);
    }

    @Override
    public void serialize(FoodItem item, JsonGenerator gen, SerializationContext ctxt) {
        gen.writeStartObject();

        gen.writeName("name");
        gen.writeString(item.getName());

        gen.writeName("describe");
        gen.writeString(item.getDescribe());

        gen.writeName("price");
        gen.writeNumber(item.getPrice());

        gen.writeName("priceYuan");
        gen.writeString(String.format("%.2f元", item.getPrice() / 100.0));

        gen.writeEndObject();
    }
}
```

类级绑定——`@JsonSerialize` 直接标在类上，整个对象的序列化都交给它：

```java
import tools.jackson.databind.annotation.JsonSerialize;

@JsonSerialize(using = FoodItemSerializer.class)
public record FoodItem(String name, String describe, String image, Integer price) {}
```

序列化结果：

```json
{
  "name": "宫保鸡丁",
  "describe": "经典川菜，鸡肉丁与花生米爆炒",
  "price": 3800,
  "priceYuan": "38.00元"
}
```

一个 JSON 对象本质上是一串 token，序列化器的工作就是按顺序把每个 token 交给 `gen`。常用方法如下：

**结构 token（手动搭骨架）：**

| 方法                                      | 写出的内容            |
| ----------------------------------------- | --------------------- |
| `writeStartObject()` / `writeEndObject()` | `{` / `}`             |
| `writeName("price")`                      | 字段名                |
| `writeStartArray()` / `writeEndArray()`   | `[` / `]`（嵌套数组） |

**值 token（写出具体值）：**

| 方法                                    | 写出的内容              |
| --------------------------------------- | ----------------------- |
| `writeString(...)` / `writeNumber(...)` | 字符串 / 数字值         |
| `writeBoolean(...)` / `writeNull()`     | 布尔 / null             |
| `writeBinary(byte[])`                   | Base64 编码的二进制数据 |

**快捷方法（字段名 + 值一步到位）：**

| 方法                                 | 等价于                                                                            |
| ------------------------------------ | --------------------------------------------------------------------------------- |
| `writeStringField("name", value)`    | `writeName("name")` + `writeString(value)`                                        |
| `writeNumberField("price", value)`   | `writeName("price")` + `writeNumber(value)`                                       |
| `writeBooleanField("active", value)` | `writeName("active")` + `writeBoolean(value)`                                     |
| `writeObjectField("nested", obj)`    | `writeName("nested")` + 用 `obj` 的注册序列化器序列化（**委托嵌套对象时最常用**） |
| `writeObject(obj)`                   | 直接序列化任意对象（不写字段名，用于数组元素等场景）                              |
| `writeRaw(String)`                   | 写出原始字符串，不做 JSON 转义                                                    |

本示例中没有任何方法写出 `image`，于是它就"消失"了——这正是自定义输出形状的典型用法。

> **证据等级：Verified runnable**——代码与验证工程一致；断言 `writeValueAsString(new FoodItem("宫保鸡丁", "经典川菜，鸡肉丁与花生米爆炒", "chuan.jpg", 3800))` 精确等于上方的 JSON。

### 11.5 自定义反序列化器

对称地，反序列化继承 `ValueDeserializer<T>`（`tools.jackson.databind` 包），唯一抽象方法：

```java
T deserialize(JsonParser p, DeserializationContext ctxt)
```

- `p`（`tools.jackson.core.JsonParser`）：当前位置的读取器，`getString()` 读出字符串值、`getLong()` 读出数字等；
- `ctxt`（`tools.jackson.databind.DeserializationContext`）：上下文，一般用于委托解析其他类型。

示例：`Order.createdAt` 字段可能收到三种格式的日期，逐个尝试：

```java
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

// 自定义反序列化器：灵活解析日期字段（支持多种格式）
public class FlexibleDateDeserializer extends ValueDeserializer<Date> {

    @Override
    public Date deserialize(JsonParser p, DeserializationContext ctxt) {
        String dateStr = p.getString();

        if (dateStr.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return parseDate(dateStr, "yyyy-MM-dd");
        } else if (dateStr.matches("\\d{4}/\\d{2}/\\d{2}")) {
            return parseDate(dateStr, "yyyy/MM/dd");
        } else if (dateStr.matches("\\d+")) {
            return new Date(Long.parseLong(dateStr));  // 时间戳
        }
        throw new IllegalArgumentException("无法解析日期: " + dateStr);
    }

    private Date parseDate(String str, String pattern) {
        try {
            return new SimpleDateFormat(pattern).parse(str);
        } catch (ParseException e) {
            throw new IllegalArgumentException("无法解析日期: " + str, e);
        }
    }
}
```

字段绑定：

```java
import tools.jackson.databind.annotation.JsonDeserialize;

public class Order {
    @JsonDeserialize(using = FlexibleDateDeserializer.class)
    private Date createdAt;
}
```

> `deserialize` 声明的 `throws JacksonException`（`tools.jackson.core` 包）是 unchecked 异常：解析失败时直接抛出运行时异常（如 `IllegalArgumentException`）即可终止解析，无需声明或包装。

### 11.6 注册到 Jackson：三个层级

写好的序列化器/反序列化器要"告诉" Jackson 才能生效。按作用范围从小到大，有三个注册层级。

**层级一：注解（字段/类级）**

前面三个示例都在用：`@JsonSerialize` / `@JsonDeserialize`（`tools.jackson.databind.annotation` 包）直接标在字段或类上，只影响该字段/该类，零配置、侵入最小。个别字段需要特殊处理时优先用它。

**层级三：Spring Boot 4 全局注册**

Web 项目最常用：定义 `JsonMapperBuilderCustomizer` Bean（`org.springframework.boot.jackson.autoconfigure` 包），Spring Boot 自动配置会把 customizer 应用到自己的 `JsonMapper.Builder`，与 `spring.jackson.*` 配置共存，全局生效：

```java
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer priceSerializerCustomizer() {
        SimpleModule module = new SimpleModule()
                .addSerializer(Integer.class, new PriceSerializer());
        return (JsonMapper.Builder builder) -> builder.addModule(module);
    }
}
```

三个层级的适用场景：

| 层级 | 注册方式                              | 作用范围          | 适用场景             |
| ---- | ------------------------------------- | ----------------- | -------------------- |
| 一   | `@JsonSerialize` / `@JsonDeserialize` | 单个字段 / 单个类 | 个别字段需要特殊处理 |
| 三   | `JsonMapperBuilderCustomizer` Bean    | 整个应用          | Web 项目全局生效     |

> **证据等级：Verified runnable**——层级二、三均在验证工程运行通过；层级三由 `@SpringBootTest` 注入 `JsonMapper` 后断言 `writeValueAsString(3800)` 返回 `"38.00元"`，证明 Boot 自动配置确实应用了 customizer Bean。

---

## 12. 项目实际使用案例

### 12.1 唯一的 Jackson 注解：@JsonProperty

项目中唯一使用的 Jackson 注解在 [CreateOrderDTO.java](d:/javaProject/demo1/src/main/java/com/example/javadoc/module/order/dto/request/CreateOrderDTO.java)：

```java
public record CreateOrderDTO(
        @NotBlank(message = "缺少日期")
        @Schema(description = "订单日期", example = "2026-07-27")
        String date,
        @NotNull(message = "缺少数量")
        @Schema(description = "订单菜品总数", example = "3")
        Integer num,
        @NotEmpty(message = "缺少菜品")
        @Schema(description = "订单菜品列表")
        @Valid List<OrderFoodDTO> foods) {

    public record OrderFoodDTO(
            @JsonProperty("_id")
            @NotBlank(message = "缺少菜名id")
            //     ↑ 前端传 "_id"，Java 用 "id"
            @Schema(description = "菜品 ID", example = "a1b2c3d4e5f6a7b8c9d0e1f2")
            String id,
            @NotBlank(message = "缺少菜名")
            @Schema(description = "菜品名称", example = "宫保鸡丁")
            String name,
            @NotBlank(message = "缺少描述")
            @Schema(description = "菜品描述", example = "经典川菜，鸡肉丁与花生米爆炒")
            String describe,
            @NotBlank(message = "缺少配料")
            @Schema(description = "主要配料", example = "鸡胸肉、花生、干辣椒")
            String burden,
            @NotBlank(message = "缺少图片")
            @Schema(description = "菜品图片 URL")
            String image,
            @NotNull(message = "缺少数量")
            @Schema(description = "该菜品下单数量", example = "2")
            Integer value) {
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

### 12.2 隐式序列化：GlobalResponseBodyAdvice

[GlobalResponseBodyAdvice.java](d:/javaProject/demo1/src/main/java/com/example/javadoc/core/advice/GlobalResponseBodyAdvice.java) 将返回值包装为统一格式，Jackson 自动序列化：

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

### 12.3 隐式序列化：GlobalExceptionHandler

[GlobalExceptionHandler.java](d:/javaProject/demo1/src/main/java/com/example/javadoc/core/advice/GlobalExceptionHandler.java) 返回错误响应，同样由 Jackson 序列化：

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

### 12.4 隐式反序列化：@RequestBody

所有 `@RequestBody` 参数都由 Jackson 自动反序列化：

```java
// MarketController.java
@PostMapping("/addCategory")
public Object addCategory(@Valid @RequestBody CreateCategoryDTO dto) {
    //                        ↑ Jackson 将 JSON 反序列化为 DTO
    return marketService.addCategory(dto.name(), dto.image());
}
```

### 12.5 Map<String, Object> 接收松散 JSON

[MarketController.java](d:/javaProject/demo1/src/main/java/com/example/javadoc/module/market/controller/MarketController.java) 中有一个用 Map 接收 JSON 的接口：

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

## 13. 速查清单

### 13.1 对象 ↔ JSON 互转速查

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

### 13.2 常用注解速查

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
│    包: tools.jackson   │                                    │
│    .databind.annotation│                                    │
│  @JsonSerialize        │  绑定自定义序列化器                 │
│    包: tools.jackson   │                                    │
│    .databind.annotation│                                    │
│  @JsonDeserialize      │  绑定自定义反序列化器               │
│    包: tools.jackson   │                                    │
│    .databind.annotation│                                    │
└────────────────────────┴─────────────────────────────────────┘
```

### 13.3 import 包速查

```
┌──────────────────────────────────────┬───────────────────────────────────────────┐
│                内容                  │  Jackson 3.x import 包                    │
├──────────────────────────────────────┼───────────────────────────────────────────┤
│  JsonMapper                         │  tools.jackson.databind.json.JsonMapper      │
│  ObjectMapper (基类)                 │  tools.jackson.databind.ObjectMapper       │
│  ObjectReader / ObjectWriter        │  tools.jackson.databind.ObjectReader/Writer│
│  JsonNode                           │  tools.jackson.databind.JsonNode           │
│  TypeReference                      │  tools.jackson.core.type.TypeReference     │
│  SerializationFeature               │  tools.jackson.databind.SerializationFeature│
│  DeserializationFeature             │  tools.jackson.databind.DeserializationFeature│
│  MapperFeature                      │  tools.jackson.databind.MapperFeature      │
│  DateTimeFeature                    │  tools.jackson.databind.cfg.DateTimeFeature│
│  ValueSerializer / ValueDeserializer│  tools.jackson.databind.ValueSerializer   │
│  StdSerializer                      │  tools.jackson.databind.ser.std.StdSerializer│
│  SimpleModule                       │  tools.jackson.databind.module.SimpleModule │
│  JacksonException                   │  tools.jackson.core.JacksonException       │
│  DatabindException                  │  tools.jackson.databind.DatabindException  │
│  JsonMapperBuilderCustomizer        │  org.springframework.boot.jackson.          │
│                                     │  autoconfigure.JsonMapperBuilderCustomizer │
│                                     │                                           │
│  @JsonProperty                      │  com.fasterxml.jackson.annotation.JsonProperty│
│  @JsonIgnore                        │  com.fasterxml.jackson.annotation.JsonIgnore│
│  @JsonFormat                        │  com.fasterxml.jackson.annotation.JsonFormat│
│  @JsonInclude                       │  com.fasterxml.jackson.annotation.JsonInclude│
│  @JsonAlias                         │  com.fasterxml.jackson.annotation.JsonAlias│
│                                     │                                           │
│  @JsonSerialize                     │  tools.jackson.databind.annotation.JsonSerialize│
│  @JsonDeserialize                   │  tools.jackson.databind.annotation.JsonDeserialize│
│  @JsonNaming                        │  tools.jackson.databind.annotation.JsonNaming│
└──────────────────────────────────────┴───────────────────────────────────────────┘
```

### 13.4 项目约定速查

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

**最后：** Jackson 是 Spring Boot 的隐形基础设施——绝大多数时候你在"用它但不知道在用它"。理解它的注解体系和默认行为，能让你在遇到 JSON 序列化/反序列化问题时快速定位和解决。项目当前使用 Jackson 3.1.2 LTS：核心类是 `tools.jackson.*` 包，常用注解在 `com.fasterxml.jackson.annotation.*`（`@JsonSerialize`/`@JsonDeserialize` 等绑定注解在 `tools.jackson.databind.annotation`），日常开发按此 import 即可。
