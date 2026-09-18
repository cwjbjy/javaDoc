# 接口数据对象指南：Request 与 Response

> **读者对象**：有 Java 基础、正在或即将写 Spring Boot 后端的开发者。
> **学完能做**：独立设计接口的入参 Request 与出参 Response，理解它们与 Entity 的分工。
> **适用版本**：Java 17+（`record`）、Spring Boot 3.x（`jakarta.validation`）。
> **范围之外**：具体持久层框架的实体建模、Service 层业务逻辑、前端解析响应。

---

## 目录

1. [三层数据模型：Entity / Request / Response](#1-三层数据模型entity-request-response)
2. [入参 Request 的角色](#2-入参-request-的角色)
3. [record：现代 Request / Response 的默认写法](#3-record现代-request-response-的默认写法)
4. [参数校验体系](#4-参数校验体系)
5. [嵌套 Request 与级联校验](#5-嵌套-request-与级联校验)
6. [字段映射 @JsonProperty](#6-字段映射-jsonproperty)
7. [可选字段的表达](#7-可选字段的表达)
8. [在 Controller 中使用](#8-在-controller-中使用)
9. [Request → Entity 转换](#9-request-entity-转换)
10. [出参对象：Response](#10-出参对象response)
11. [命名规范与速查](#11-命名规范与速查)

---

## 1. 三层数据模型：Entity / Request / Response

一个典型的 Spring Boot 后端会有三类"数据对象"，各司其职。**这是全篇的心智模型**，后续每一节都在填充这张图的某个环节。

```
       ┌───────────────────┐
       │       前端        │
       └───────────────────┘
             │        ▲
    请求 JSON│        │响应 JSON
             ▼        │
       ┌───────────────────────────┐
       │        Controller         │
       │                           │
       │  Request ──┐    ┌── Response
       │        └─── DTO ───┘      │
       └────────────┬────┬─────────┘
                    │    │
               入参 │    │ 出参
                    ▼    │
       ┌───────────────────────────┐
       │         Service           │
       │                           │
       │   Entity ◀── 持久层交互   │
       └───────────────────────────┘
                    │
                    ▼
       ┌───────────────────┐
       │     Database      │
       └───────────────────┘
```

图中 `Request` 和 `Response` 上方的 `DTO` 横线表示：**二者都属于 DTO（数据传输对象）**，只是方向不同——Request 是入参 DTO，Response 是出参 DTO。

三者的职责对照：

| 对象         | 面向          | 生命周期         | 可变性 | 典型写法         |
| ------------ | ------------- | ---------------- | ------ | ---------------- |
| **Request**  | 接口入参      | 单次请求         | 不可变 | `record`         |
| **Response** | 接口出参      | 单次响应         | 不可变 | `record`         |
| **Entity**   | 数据库表/文档 | 跨请求，长期存在 | 可变   | `class` + Lombok |

**核心原则：** Request 和 Response 是"接口契约"，Entity 是"存储结构"。三者不该混用。混用的代价会在第 2 节和第 10 节展开。

> **术语约定：** 后文用 **Request** 指代入参 DTO、**Response** 指出参 DTO，二者统称 **DTO**。DTO 的完整定义见 §2 开头。

---

## 2. 入参 Request 的角色

**DTO = Data Transfer Object（数据传输对象）**。它是一种纯粹的数据载体，用于在系统各层之间传递数据，本身不包含业务逻辑。DTO 关注的是"接口层的数据契约"。

在接口层，DTO 按方向分为两类：

- **Request**（入参 DTO）：定义前端应该发送什么格式的数据给后端；
- **Response**（出参 DTO）：定义后端返回给前端的数据结构（详见 §10）。

本节聚焦入参方向。Spring 会把请求体 JSON 自动反序列化为 Request 实例：

```
前端 JSON 请求  →  Request（接收 + 校验）  →  Service 处理  →  Entity（持久化）
```

_Illustrative fragment:_

```java
public record UserCreateRequest(
        @NotBlank(message = "用户名不能为空") String username,
        @Email(message = "邮箱格式不正确") String email) {
}
```

前端发送：

```
POST /api/users
Content-Type: application/json

{ "username": "alice", "email": "alice@example.com" }
```

Spring 会自动反序列化为 `UserCreateRequest` 实例，字段值一一就位。

### 2.1 为什么不直接用 Entity 接收？

三个理由：

**（1）安全 —— 防止前端篡改不该改的字段**

_Illustrative fragment:_

```java
// Entity —— 数据库实体
@Data
public class User {
    @Id private String id;              // 数据库生成，前端不该传
    private String username;
    private String email;
    private String passwordHash;        // 敏感字段，前端不该直接写
    private Role role;                  // 权限字段，前端不该越权
    private Instant createdAt;
}
```

如果直接用 `User` 接收请求，恶意前端可以构造 `{"role":"ADMIN","id":"xxx"}` 来越权。用 Request 则只暴露该接口需要的字段。

**（2）校验 —— Request 挂校验注解，Entity 保持纯净**

`@NotBlank`、`@Email` 等校验注解是**接口层的契约**，与数据库无关。Entity 不该关心"前端传没传这个字段"。

**（3）解耦 —— 接口结构 ≠ 存储结构**

Request 可以包含 Entity 里没有的字段（例如 `confirmPassword`、`oldPassword` 等业务字段），也可以只暴露 Entity 的一个子集。数据库表结构调整时，接口契约可以保持稳定。

---

## 3. record：现代 Request / Response 的默认写法

Java 16 起，`record` 正式成为标准语法（JEP 395）。它是 Request / Response 的天然载体：**不可变、字段一目了然、无需 getter/setter 样板**。

### 3.1 record 语法速览

_Illustrative fragment:_

```java
public record UserCreateRequest(
        @NotBlank String username,
        @Email String email) {
}
```

编译器自动生成：

- **构造器**：`new UserCreateRequest("alice", "alice@example.com")`
- **访问器**：`req.username()`、`req.email()`（注意：**不是** `getUsername()`）
- **equals / hashCode / toString**
- **不可变性**：字段是 `final` 的，创建后不能修改

### 3.2 record vs Lombok @Data class

```
┌──────────────┬──────────────────────────┬──────────────────────┐
│    维度      │         record           │    @Data class       │
├──────────────┼──────────────────────────┼──────────────────────┤
│ 可变性       │ 不可变                   │ 可变                 │
│ 访问器       │ req.username()           │ req.getUsername()    │
│ 赋值         │ 无法赋值                 │ req.setUsername(...) │
│ 适用场景     │ 纯数据传递（Request/Response）│ 需修改的对象（Entity）│
│ Lombok 依赖  │ 无                       │ 依赖 Lombok          │
└──────────────┴──────────────────────────┴──────────────────────┘
```

### 3.3 约定：Request / Response 用 record，Entity 用 class

```
Request / Response  ──record──▶  不可变，一次构造，只读
Entity              ──class────▶  可变，Service 层可逐步赋值
```

这个分工让两类对象的职责一目了然：**需要改的用 class，不需要改的用 record**。

> **注意：** 一些老教程仍用 `@Data` + `class` 写 Request / Response，那是 Java 14 之前的写法。新项目应默认使用 `record`。

---

## 4. 参数校验体系

Spring Boot 3.x 使用 **Jakarta Validation**（包名 `jakarta.validation.constraints.*`；Spring Boot 2.x 用的是 `javax.validation.*`，二者不兼容）。

### 4.1 校验注解"三件套"

| 注解        | 拒绝什么               | 适用类型         |
| ----------- | ---------------------- | ---------------- |
| `@NotBlank` | null、空字符串、纯空白 | `String`         |
| `@NotEmpty` | null、空集合/空字符串  | `List`、`String` |
| `@NotNull`  | 仅拒绝 null            | 任意引用类型     |

### 4.2 选择决策树

```
字段类型是 String？
├─ 必填 ──────▶ @NotBlank   （拒绝 null + 空串 + 纯空白）
└─ 可选 ──────▶ 不加注解    （null = 前端没传）

字段类型是 List / 集合？
├─ 必填 ──────▶ @NotEmpty   （拒绝 null + 空集合）
│   └─ 元素也要校验？─▶ 再加 @Valid
└─ 可选 ──────▶ 不加注解

字段类型是 Integer / 对象？
├─ 必填 ──────▶ @NotNull    （只拒绝 null）
└─ 可选 ──────▶ 不加注解
```

### 4.3 message 属性 —— 中文校验消息

每个校验注解都支持 `message` 属性，校验失败时会返回给前端：

_Illustrative fragment:_

```java
@NotBlank(message = "用户名不能为空") String username,
@Email(message = "邮箱格式不正确") String email
```

约定：**所有校验消息用中文，明确描述"缺什么/什么不对"**。校验失败时，Spring 抛出 `MethodArgumentNotValidException`，通常由全局异常处理器（`@RestControllerAdvice`）捕获，返回统一格式：

```json
{ "code": 400, "message": "用户名不能为空" }
```

### 4.4 @Valid 触发机制

Request 上的校验注解**不会自动生效**，需要在 Controller 参数前加 `@Valid`：

_Illustrative fragment:_

```java
@PostMapping("/users")
public UserResponse create(@Valid @RequestBody UserCreateRequest req) {
    // 校验失败时，Spring 在进入方法前就返回 400，不会执行到这里
    return userService.create(req);
}
```

---

## 5. 嵌套 Request 与级联校验

### 5.1 @Valid 级联校验

当 Request 字段是另一个 Request 的集合时，需要加 `@Valid` 才会校验集合中的**每个元素**：

_Illustrative fragment:_

```java
public record OrderCreateRequest(
        @NotBlank(message = "订单号不能为空") String orderNo,
        @NotEmpty(message = "订单明细不能为空")
        @Valid                                  // ← 关键：递归校验每个元素
        List<OrderItemRequest> items) {
}

public record OrderItemRequest(
        @NotBlank(message = "商品 ID 不能为空") String productId,
        @NotNull(message = "数量不能为空") @Min(1) Integer quantity) {
}
```

```
                    @Valid 的作用
                    ─────────────
OrderCreateRequest
  ├─ orderNo:  @NotBlank  ✓ 直接校验
  └─ items:    @NotEmpty  ✓ 校验列表非空
        └─ @Valid ──▶ 递归校验列表中每个 OrderItemRequest
                        ├─ productId: @NotBlank
                        └─ quantity:  @NotNull @Min(1)
```

**忘加 `@Valid` 的后果：** 列表本身会校验非空，但列表里的元素即使全是空字段也不会报错——这是常见的隐蔽 bug。

### 5.2 两种嵌套方式

```
方式 A：独立文件
  OrderCreateRequest.java ──引用──▶ OrderItemRequest.java
  适用：子 Request 会被多个 Request 复用，或字段较多

方式 B：内部 record
  OrderCreateRequest.java
    └─ Item（定义在同一文件内部）
  适用：子 Request 仅被这一个 Request 使用，字段不多
```

_Illustrative fragment（方式 B）：_

```java
public record OrderCreateRequest(
        @NotBlank String orderNo,
        @NotEmpty @Valid List<Item> items) {

    public record Item(
            @NotBlank String productId,
            @NotNull @Min(1) Integer quantity) {
    }
}
```

**经验法则：** 只被一个父 Request 使用 → 内部；会被复用 → 独立文件。

---

## 6. 字段映射 @JsonProperty

@JsonProperty 注解由 jackson 提供，而 spring-boot-starter-webmvc 已内置 jackson-databind，因此在接口项目中，可以直接使用该注解

当前端 JSON 字段名与 Java 字段名不一致时，用 `@JsonProperty` 做映射：

_Illustrative fragment:_

```java
public record UserQueryRequest(
        @JsonProperty("user_id") String userId,      // Java 驼峰 ←→ JSON 下划线
        @JsonProperty("full_name") String fullName,
        String email) {
}
```

```
前端发送：
{ "user_id": "u_123", "full_name": "Alice", "email": "..." }

        │  @JsonProperty 映射
        ▼

后端对象：
UserQueryRequest { userId = "u_123", fullName = "Alice", email = "..." }
```

**为什么需要？** 前后端命名风格不一致时（Java 用 camelCase，前端或数据库可能用 snake_case、或 MongoDB 的 `_id` 之类），`@JsonProperty` 架了一座桥。

> **提示：** 全局统一转换可以在 `ObjectMapper` 里配置 `PropertyNamingStrategies.SNAKE_CASE`，不必每个字段都加注解。个别字段特殊时再用 `@JsonProperty` 覆盖。

---

## 7. 可选字段的表达

约定：**不加校验注解 = 可选**。

_Illustrative fragment:_

```java
public record UserUpdateRequest(
        @NotBlank(message = "用户 ID 不能为空") String userId,  // 必填
        String username,     // 可选：null = 不更新
        String email,        // 可选
        String avatarUrl) {  // 可选
}
```

**约定：** 可选字段的类型用**包装类**（`Integer` 而非 `int`、`Boolean` 而非 `boolean`），这样 `null` 才能表示"前端没传"。Service 层据此判断是否需要更新该字段。

```
username == null     →  前端没传，跳过更新
username == ""       →  前端传了空串（这时应加 @NotBlank 或自定义校验来拒绝）
username == "alice"  →  正常更新
```

---

## 8. 在 Controller 中使用

### 8.1 @Valid @RequestBody 的完整流程

_Illustrative fragment:_

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @PostMapping
    public UserResponse create(@Valid @RequestBody UserCreateRequest req) {
        return userService.create(req);
    }
}
```

```
前端发送：POST /api/users
Content-Type: application/json
{ "username": "alice", "email": "alice@example.com" }
  │
  ▼ ① Spring 将 JSON 反序列化为 UserCreateRequest
  │
  ▼ ② @Valid 触发校验：username 非空？email 格式？
  │     ├─ 失败 → 抛 MethodArgumentNotValidException → 400
  │     └─ 通过 → 继续
  │
  ▼ ③ 调用 userService.create(req)
  │
  ▼ ④ Service 处理，返回 UserResponse
  │
  ▼ ⑤ Spring 将 UserResponse 序列化为 JSON
  │
  ▼ 返回前端：
{ "userId": "u_123", "username": "alice", ... }
```

### 8.2 三种参数接收方式

| 方式         | 注解                          | 用途       | 示例                                                |
| ------------ | ----------------------------- | ---------- | --------------------------------------------------- |
| JSON 请求体  | `@Valid @RequestBody`         | 结构化数据 | `create(@Valid @RequestBody UserCreateRequest req)` |
| URL 查询参数 | `@RequestParam`               | 简单值     | `search(@RequestParam("q") String keyword)`         |
| 表单文件     | `@RequestParam MultipartFile` | 上传文件   | `upload(@RequestParam("file") MultipartFile file)`  |

**经验法则：** 参数超过 2 个、需要校验、或字段可能扩展 → 用 Request + `@RequestBody`。简单查询 → `@RequestParam`。

---

## 9. Request → Entity 转换

Request 接收到数据后，Service 层负责将其转换为 Entity 存入数据库。常见两种方式。

### 9.1 手动逐字段映射

_Illustrative fragment:_

```java
public User create(UserCreateRequest req, String rawPassword) {
    User user = new User();                              // 创建一个实体类
    user.setId(UUID.randomUUID().toString());            // Request 没有的字段，Service 补
    user.setUsername(req.username());                     // 逐字段赋值
    user.setEmail(req.email());
    user.setPasswordHash(passwordEncoder.encode(rawPassword));
    user.setCreatedAt(Instant.now());
    return userRepository.save(user);
}
```

```
Request                        Entity
────────────                   ──────────────
UserCreateRequest              User
  username       ──────▶        username
  email          ──────▶        email
  (无)           ──────▶        id            （Service 生成 UUID）
  (无)           ──────▶        passwordHash  （Service 加密后赋值）
  (无)           ──────▶        createdAt     （Service 打时间戳）
```

- **优点**：直观、无学习成本、转换逻辑完全可见
- **缺点**：字段多时冗长，新增字段容易遗漏映射

### 9.2 MapStruct 声明式映射

_Complete example, not yet verified:_

Maven 依赖（版本以官方最新为准，此处示例为 1.5.5.Final）：

```xml
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.5.5.Final</version>
</dependency>
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct-processor</artifactId>
    <version>1.5.5.Final</version>
    <scope>provided</scope>
</dependency>
```

Mapper 接口：

```java
@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "passwordHash", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    User toEntity(UserCreateRequest req);

    UserResponse toResponse(User user);
}
```

编译时 MapStruct 会通过注解处理器生成 `UserMapperImpl`，Spring 会将其注册为 Bean，直接 `@Autowired` 即可使用。

- **优点**：字段少写、类型安全、编译期检查缺失映射
- **缺点**：需要注解处理器；调试时要看生成的 impl 类

**何时选哪个：** 字段少（< 5）用手动；字段多、Entity 与 Request 高度相似、或项目已经引入 MapStruct → 用 MapStruct。

---

## 10. 出参对象：Response

前 9 节讲的都是"入参"这一半。这一节讲另一半：**接口返回给前端的数据结构**。

### 10.1 为什么不直接返回 Entity

_Illustrative fragment（**反例**）：_

```java
@GetMapping("/{id}")
public User get(@PathVariable String id) {   // ← 直接返回 Entity
    return userRepository.findById(id).orElseThrow();
}
```

这样返回的 JSON 会包含：

```json
{
  "id": "u_123",
  "username": "alice",
  "email": "alice@example.com",
  "passwordHash": "$2a$10$...",
  "role": "USER",
  "createdAt": "2026-01-01T00:00:00Z",
  "updatedAt": "2026-09-18T10:00:00Z",
  "deletedFlag": false
}
```

**四个问题：**

1. **安全** —— 敏感字段（密码哈希、内部标志位）泄漏；
2. **耦合** —— 数据库字段一改，前端接口就崩；
3. **冗余** —— 前端只需要 3 个字段，Entity 有 10 个；
4. **文档** —— Swagger / OpenAPI 无法准确描述"接口该返回什么"。

### 10.2 Response 的写法

_Illustrative fragment:_

```java
public record UserResponse(
        @Schema(description = "用户 ID", example = "u_123")
        String userId,

        @Schema(description = "用户名", example = "alice")
        String username,

        @Schema(description = "邮箱", example = "alice@example.com")
        String email,

        @Schema(description = "注册时间")
        Instant createdAt) {
}
```

**要点：**

- 用 `record`，与 Request 保持一致；
- **只暴露前端真正需要的字段**；
- 用 `@Schema`（Swagger / OpenAPI）描述字段，生成漂亮的接口文档；
- 字段名可以用 `userId` 而不是 `id`，避免与其他实体的 `id` 混淆；
- **不要挂校验注解**（`@NotBlank` 等），Response 是出参，无需校验。

### 10.3 Request / Response 配对命名

一个接口通常对应一对：

```
POST /api/users
    ├─ 入参：UserCreateRequest
    └─ 出参：UserResponse

PUT /api/users/{id}
    ├─ 入参：UserUpdateRequest
    └─ 出参：UserResponse

GET /api/users
    ├─ 入参：（@RequestParam 或 UserQueryRequest）
    └─ 出参：UserListResponse
```

Request 和 Response **不必字段对称**——例如 `UserCreateRequest` 有 `password`，`UserResponse` 绝不能有 `passwordHash`。

### 10.4 分页 / 列表响应

_Illustrative fragment:_

```java
public record UserListResponse(
        @Schema(description = "当前页数据")
        List<UserResponse> items,

        @Schema(description = "总条数", example = "128")
        long total,

        @Schema(description = "当前页码", example = "1")
        int page,

        @Schema(description = "每页大小", example = "20")
        int size) {
}
```

分页响应本身就是一个 Response，包装了列表数据 + 分页元信息。

### 10.5 Entity → Response 转换

方向与 §9 相反，写法完全对称：

_Illustrative fragment（手动）：_

```java
public UserResponse toResponse(User user) {
    return new UserResponse(
            user.getId(),
            user.getUsername(),
            user.getEmail(),
            user.getCreatedAt());
}
```

_Illustrative fragment（MapStruct，接 §9.2 的 UserMapper）：_

```java
UserResponse toResponse(User user);
```

MapStruct 会按同名字段自动生成映射代码。字段名不一致时，用 `@Mapping(source = "...", target = "...")` 显式声明。

---

## 11. 命名规范与速查

### 11.1 命名约定

```
入参（Request）
────────────────────────────────
   动词 + 实体 + Request
   ├─ Create：UserCreateRequest
   ├─ Update：UserUpdateRequest
   ├─ Query ：UserQueryRequest（复杂查询条件）
   └─ Delete：UserDeleteRequest

出参（Response）
────────────────────────────────
   实体 + Response
   ├─ 单个：UserResponse
   ├─ 列表：UserListResponse
   └─ 分页：UserPageResponse
```

**要点：**

- **入参带动词前缀**（Create / Update / Query），因为同一实体常有多种入参；
- **出参通常不带动词**（`UserResponse`），因为一个实体的返回结构相对稳定；
- 包结构常见分法：`dto/request/` 和 `dto/response/`。

### 11.2 三层数据模型速查

| 维度           | Entity                      | Request              | Response                  |
| -------------- | --------------------------- | -------------------- | ------------------------- |
| 面向           | 数据库                      | 接口入参             | 接口出参                  |
| 可变性         | 可变                        | 不可变               | 不可变                    |
| 典型写法       | `class` + Lombok `@Data`    | `record`             | `record`                  |
| 常见注解       | `@Id` `@Column` `@Document` | `@NotBlank` `@Valid` | `@Schema` `@JsonProperty` |
| 生命周期       | 长期存在                    | 单次请求             | 单次响应                  |
| 是否含敏感字段 | 可能（密码哈希等）          | 通常不含             | **绝不包含**              |
| 转换方向       | —                           | Request → Entity     | Entity → Response         |
| 转换工具       | —                           | 手动 / MapStruct     | 手动 / MapStruct          |

### 11.3 一个接口的完整数据流

```
   ┌──────────┐
   │   前端   │
   └──────────┘
        │
        │ ① HTTP 请求（JSON）
        ▼
   ┌────────────────────────────────┐
   │          Controller            │
   │                                │
   │  ② @Valid @RequestBody         │
   │     UserCreateRequest          │
   └────────────────────────────────┘
        │
        │ ③ Request 传入
        ▼
   ┌────────────────────────────────┐
   │           Service              │
   │                                │
   │  ④ Request → Entity（手动/Mapper）│
   │  ⑤ 业务处理                    │
   │  ⑥ Entity → Response           │
   └────────────────────────────────┘
        │              │
        │ ⑦ Entity     │ ⑧ Response
        ▼              │
   ┌──────────┐        │
   │Repository│        │
   │    ↓     │        │
   │ Database │        │
   └──────────┘        │
                       ▼
   ┌────────────────────────────────┐
   │          Controller            │
   │  ⑨ Spring 序列化为 JSON        │
   └────────────────────────────────┘
        │
        │ ⑩ HTTP 响应（JSON）
        ▼
   ┌──────────┐
   │   前端   │
   └──────────┘
```

### 11.4 一句话总结

> **Request 管入，Response 管出，Entity 管存。** 三者分工明确：用 `record` 写不可变的接口对象，用 `class` 写可变的持久化对象。DTO 是伞形概念，Request 和 Response 是它在接口层的具体化身。
