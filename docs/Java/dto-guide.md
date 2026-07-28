# 数据传输对象（DTO）指南

> 本指南基于 `JavaDoc` 项目实际代码，介绍 DTO 的概念、写法与项目约定。
> 以教学方式讲清概念，同时固化项目中的实际模式。

---

## 目录

1. [什么是 DTO](#1-什么是-dto)
2. [为什么需要 DTO](#2-为什么需要-dto)
3. [record：项目的 DTO 基石](#3-record项目的-dto-基石)
4. [参数校验体系](#4-参数校验体系)
5. [嵌套 DTO 与级联校验](#5-嵌套-dto-与级联校验)
6. [字段映射 @JsonProperty](#6-字段映射-jsonproperty)
7. [可选字段的表达](#7-可选字段的表达)
8. [在 Controller 中使用](#8-在-controller-中使用)
9. [DTO → Entity 转换](#9-dto--entity-转换)
10. [命名规范](#10-命名规范)
11. [项目 DTO 全览](#11-项目-dto-全览)
12. [进阶话题与项目约定总结](#12-进阶话题与项目约定总结)

---

## 1. 什么是 DTO

**DTO = Data Transfer Object（数据传输对象）**。它是一种纯粹的数据载体，定义了前端应该发送什么格式的数据给后端。

在 Web 应用中，前端发送的 JSON 请求体需要被后端接收、校验、处理。DTO 就是这个"接收容器"：

```
前端 JSON 请求  →  DTO（接收 + 校验）  →  Service 处理  →  Entity（存入 MongoDB）
```

项目中最简单的 DTO（`CreateCategoryDTO.java`）：

```java
public record CreateCategoryDTO(
        @NotBlank(message = "缺少名称") String name,
        @NotBlank(message = "缺少图标") String image) {
}
```

前端发送的 JSON 会被 Spring 自动反序列化为这个对象：

```
前端发送：
POST /api/market/addCategory
{ "name": "川菜", "image": "/static/images/market/chuan.jpg" }

        │  Spring 自动反序列化
        ▼

CreateCategoryDTO {
    name  = "川菜"
    image = "/static/images/market/chuan.jpg"
}
```

DTO 只包含数据，不包含业务逻辑。它关注的是"接口层的数据契约"。

---

## 2. 为什么需要 DTO

你可能会问：为什么不直接用数据库实体（Entity）接收请求？三个原因：

### 2.1 安全性 —— 防止前端篡改不该改的字段

```java
// Entity —— Market 实体
@Data
public class Market {
    @Id
    private String id;            // 数据库自动生成，前端不该传
    private String name;
    private String image;
    private List<FoodItem> foods; // 内部数据，前端不该直接塞
}
```

如果直接用 Entity 接收，前端可以构造 `{"id":"xxx", "foods":[...]}` 来篡改内部字段。用 DTO 则只暴露该接口需要的字段：

```java
// DTO —— 只暴露 name 和 image
public record CreateCategoryDTO(
        @NotBlank(message = "缺少名称") String name,
        @NotBlank(message = "缺少图标") String image) {
}
```

### 2.2 校验 —— DTO 挂校验注解，Entity 保持纯净

DTO 上的 `@NotBlank`、`@NotNull` 等校验注解是接口层的契约，与数据库无关。Entity 不该关心"前端传没传这个字段"。

### 2.3 解耦 —— 前端数据结构 ≠ 数据库结构

DTO 可以包含 Entity 里没有的字段。例如 `UpdateFoodDTO` 有一个 `oldImage` 字段，用于删除旧图片文件——这是业务逻辑需要，但数据库里并不存储：

```java
public record UpdateFoodDTO(
        @NotBlank(message = "缺少分类") String categoryId,
        @NotBlank(message = "缺少菜的新分类") String targetCategoryId,
        @NotBlank(message = "缺少食物id") String foodId,
        String name,
        String describe,
        String burden,
        String image,
        String oldImage) {          // ← Entity 里没有，纯粹为了业务逻辑
}
```

---

## 3. record：项目的 DTO 基石

项目里所有 8 个 DTO **全部使用 Java `record`** 定义，而非传统的 `@Data` class。

### 3.1 record 语法速览

```java
// 一行定义完所有字段
public record CreateCategoryDTO(
        @NotBlank(message = "缺少名称") String name,
        @NotBlank(message = "缺少图标") String image) {
}
```

record 自动生成：

- **构造器**：`new CreateCategoryDTO("川菜", "chuan.jpg")`
- **访问器**：`dto.name()`、`dto.image()`（注意：不是 `getName()`）
- **equals / hashCode / toString**
- **不可变性**：字段是 `final` 的，创建后不能修改

### 3.2 record vs @Data class

```
┌──────────────────┬────────────────────┬──────────────────────┐
│      维度        │      record        │    @Data class        │
├──────────────────┼────────────────────┼──────────────────────┤
│  代码量           │ 极少（1 行字段列表） │ 多（字段+getter/setter）│
│  可变性           │ 不可变              │ 可变                  │
│  访问器           │ dto.name()         │ dto.getName()         │
│  赋值             │ 无法赋值            │ dto.setName("xxx")   │
│  适用场景         │ 纯数据传输（DTO）    │ 需要修改的对象（Entity）│
│  与 Lombok 冲突   │ 无                  │ 依赖 Lombok           │
└──────────────────┴────────────────────┴──────────────────────┘
```

### 3.3 项目约定：DTO 用 record，Entity 用 @Data class

```
DTO     ──record──▶  不可变，接收后不修改
Entity  ──@Data───▶  可变，Service 层需要逐步赋值
```

这个分工让两类对象的职责一目了然：**需要改的用 class，不需要改的用 record**。

> **注意：** `LEARNING_GUIDE.md` 第 8 课中 DTO 示例仍使用 `@Data` + class 写法，那是旧版本。项目代码已全面迁移至 `record`，以本指南为准。

---

## 4. 参数校验体系

### 4.1 校验注解"三件套"

项目统一使用 Jakarta Validation（`jakarta.validation.constraints`）做参数校验：

| 注解        | 拒绝什么               | 适用类型         | 项目示例                 |
| ----------- | ---------------------- | ---------------- | ------------------------ |
| `@NotBlank` | null、空字符串、纯空白 | `String`         | `CreateCategoryDTO.name` |
| `@NotEmpty` | null、空集合/空字符串  | `List`、`String` | `AddFoodDTO.foods`       |
| `@NotNull`  | 仅拒绝 null            | `Integer`、对象  | `CreateOrderDTO.num`     |

### 4.2 选择决策树

```
字段类型是 String？
├─ 是，且必填 ──────▶ @NotBlank  （拒绝 null + 空串 + 纯空白）
└─ 是，且可选 ──────▶ 不加注解    （null = 前端没传）

字段类型是 List / 集合？
├─ 是，且必填 ──────▶ @NotEmpty   （拒绝 null + 空列表）
│   └─ 列表元素也要校验？─▶ 再加 @Valid
└─ 是，且可选 ──────▶ 不加注解

字段类型是 Integer / 对象？
├─ 是，且必填 ──────▶ @NotNull    （只拒绝 null）
└─ 是，且可选 ──────▶ 不加注解
```

### 4.3 message 属性 —— 中文校验消息

每个校验注解都带 `message` 属性，校验失败时返回给前端：

```java
@NotBlank(message = "缺少名称") String name,
@NotBlank(message = "缺少图标") String image,
```

这是项目的一贯约定：**所有校验消息用中文，描述缺少什么**。校验失败时，`GlobalExceptionHandler` 会捕获并返回 400 响应：

```
校验失败  →  Spring 抛出 MethodArgumentNotValidException
         →  GlobalExceptionHandler 捕获
         →  返回 {"code": 400, "message": "缺少名称", ...}
```

### 4.4 @Valid 触发机制

DTO 上的校验注解不会自动生效，需要在 Controller 参数前加 `@Valid` 才会触发：

```java
@PostMapping("/addCategory")
public Object addCategory(@Valid @RequestBody CreateCategoryDTO dto) {
    // 如果 name 为空，Spring 在进入方法前就返回 400，不会执行到这里
    return marketService.addCategory(dto.name(), dto.image());
}
```

---

## 5. 嵌套 DTO 与级联校验

### 5.1 @Valid 级联校验

当 DTO 字段是另一个 DTO 的集合时，需要加 `@Valid` 才会校验集合中的每个元素：

```java
// AddFoodDTO —— foods 列表中每个 FoodDTO 也要校验
public record AddFoodDTO(
        @NotBlank(message = "缺少分类") String categoryId,
        @NotEmpty(message = "缺少菜") @Valid List<FoodDTO> foods) {
}
```

```
                    @Valid 的作用
                    ─────────────
AddFoodDTO
  ├─ categoryId:  @NotBlank  ✓ 直接校验
  └─ foods:      @NotEmpty  ✓ 校验列表非空
        └─ @Valid ──▶ 递归校验列表中每个 FoodDTO 的字段
                        ├─ FoodDTO.name:     @NotBlank
                        ├─ FoodDTO.describe: @NotBlank
                        ├─ FoodDTO.burden:   @NotBlank
                        └─ FoodDTO.image:    @NotBlank
```

**忘加 `@Valid` 的后果：** 列表本身会校验非空，但列表里的元素即使全是空字段也不会报错——这是常见的隐蔽 bug。

### 5.2 两种嵌套方式

项目里出现了两种组织嵌套 DTO 的方式：

```
方式 A：引用独立文件（market 模块）
  AddFoodDTO.java ──引用──▶ FoodDTO.java（独立文件）

  适用场景：子 DTO 被多个 DTO 复用，或字段较多

方式 B：内部 record（order 模块）
  CreateOrderDTO.java
    └─ OrderFoodDTO（定义在同一文件内部）

  适用场景：子 DTO 仅被这一个 DTO 使用，字段不多
```

项目中的实际选择：

```java
// 方式 A —— FoodDTO 是独立文件，被 AddFoodDTO 引用
// AddFoodDTO.java
public record AddFoodDTO(
        @NotBlank(message = "缺少分类") String categoryId,
        @NotEmpty(message = "缺少菜") @Valid List<FoodDTO> foods) {
}

// 方式 B —— OrderFoodDTO 是 CreateOrderDTO 的内部 record
// CreateOrderDTO.java
public record CreateOrderDTO(
        @NotBlank(message = "缺少日期") String date,
        @NotNull(message = "缺少数量") Integer num,
        @NotEmpty(message = "缺少菜品") @Valid List<OrderFoodDTO> foods) {

    public record OrderFoodDTO(
            @JsonProperty("_id") @NotBlank(message = "缺少菜名id") String id,
            @NotBlank(message = "缺少菜名") String name,
            // ...
            @NotNull(message = "缺少数量") Integer value) {
    }
}
```

**经验法则：** 如果子 DTO 只被一个父 DTO 使用，放内部更紧凑；如果会被复用，独立成文件更清晰。

---

## 6. 字段映射 @JsonProperty

当前端传的 JSON 字段名与 Java 字段名不一致时，用 `@JsonProperty` 做映射：

```java
// CreateOrderDTO.OrderFoodDTO
public record OrderFoodDTO(
        @JsonProperty("_id") @NotBlank(message = "缺少菜名id") String id,
        //     ↑ JSON 字段名    ↑ Java 字段名
        @NotBlank(message = "缺少菜名") String name,
        // ...
) {}
```

```
前端 JSON：
{ "_id": "abc123", "name": "宫保鸡丁", ... }

        │  @JsonProperty("_id") 映射
        ▼

Java 对象：
OrderFoodDTO { id = "abc123", name = "宫保鸡丁", ... }
```

**为什么需要？** 前端/数据库使用 MongoDB 风格的 `_id`，但 Java 变量名不能以 `_` 开头（不符合命名规范）。`@JsonProperty` 在两者之间架了一座桥。

---

## 7. 可选字段的表达

项目中用一个非常简洁的约定表达可选字段：**不加校验注解 = 可选**。

```java
// FoodDTO —— num 是可选的
public record FoodDTO(
        @NotBlank(message = "缺少菜名") String name,      // 必填
        @NotBlank(message = "缺少描述") String describe,    // 必填
        @NotBlank(message = "缺少配料") String burden,       // 必填
        @NotBlank(message = "缺少图片") String image,        // 必填
        Integer num) {                                      // 可选：无注解
}
```

```java
// UpdateFoodDTO —— 更新时大部分字段可选
public record UpdateFoodDTO(
        @NotBlank(message = "缺少分类") String categoryId,       // 必填
        @NotBlank(message = "缺少菜的新分类") String targetCategoryId, // 必填
        @NotBlank(message = "缺少食物id") String foodId,         // 必填
        String name,          // 可选：null = 不更新
        String describe,      // 可选
        String burden,        // 可选
        String image,         // 可选
        String oldImage) {    // 可选：旧图片路径，用于删除文件
}
```

**约定：** 可选字段的类型用包装类（`Integer` 而非 `int`），这样 `null` 表示"前端没传"。Service 层据此判断是否需要更新该字段。

---

## 8. 在 Controller 中使用

### 8.1 @Valid @RequestBody 的完整流程

```java
// MarketController.java
@PostMapping("/addCategory")
public Object addCategory(@Valid @RequestBody CreateCategoryDTO dto) {
    return marketService.addCategory(dto.name(), dto.image());
}
```

```
前端发送：POST /api/market/addCategory
Content-Type: application/json
{ "name": "川菜", "image": "chuan.jpg" }
  │
  ▼ ① Spring 将 JSON 反序列化为 CreateCategoryDTO
  │
  ▼ ② @Valid 触发校验：name 非空？image 非空？
  │     ├─ 失败 → 返回 400，不进入方法
  │     └─ 通过 → 继续
  │
  ▼ ③ 调用 marketService.addCategory(dto.name(), dto.image())
  │
  ▼ ④ Service 处理，返回 Market 对象
  │
  ▼ ⑤ GlobalResponseBodyAdvice 包装为统一格式
  │
  ▼ 返回前端：
{ "code": 200, "message": "success", "data": {Market对象} }
```

### 8.2 三种参数接收方式

| 方式         | 注解                          | 用途           | 项目示例                                                 |
| ------------ | ----------------------------- | -------------- | -------------------------------------------------------- |
| JSON 请求体  | `@Valid @RequestBody`         | 接收结构化数据 | `addCategory(@Valid @RequestBody CreateCategoryDTO dto)` |
| URL 查询参数 | `@RequestParam`               | 接收简单值     | `findFoods(@RequestParam("text") String text)`           |
| 表单文件     | `@RequestParam MultipartFile` | 上传文件       | `uploadImage(@RequestParam("file") MultipartFile file)`  |

**经验法则：** 参数超过 2 个或需要校验时，用 DTO + `@RequestBody`。简单查询用 `@RequestParam`。

---

## 9. DTO → Entity 转换

DTO 接收到数据后，Service 层负责将其转换为 Entity 存入数据库。项目目前采用**手动逐字段映射**的方式：

```java
// FoodService.addFood —— 手动转换
public Map<String, Object> addFood(AddFoodDTO dto) {
    for (FoodDTO foodDTO : dto.foods()) {
        Market.FoodItem food = new Market.FoodItem();   // 创建 Entity
        food.setId(UUID.randomUUID().toString());        // DTO 没有的字段，Service 补
        food.setName(foodDTO.name());                     // 逐字段赋值
        food.setDescribe(foodDTO.describe());
        food.setBurden(foodDTO.burden());
        food.setImage(foodDTO.image());
        food.setNum(foodDTO.num() != null ? foodDTO.num() : 0); // 可选字段处理

        // 存入 MongoDB ...
    }
}
```

```
DTO                        Entity
────────────               ──────────────────
FoodDTO                    Market.FoodItem
  name        ──────▶        name
  describe   ──────▶        describe
  burden     ──────▶        burden
  image      ──────▶        image
  num?       ──────▶        num（null → 0，Service 补默认值）
  (无)        ──────▶        id（Service 用 UUID 生成）
```

**手动映射的特点：**

- 优点：直观、无学习成本、转换逻辑完全可见
- 缺点：字段多时冗长，新增字段容易遗漏映射

> **进阶提示：** 项目 `pom.xml` 已引入 MapStruct 依赖（`org.mapstruct:mapstruct:1.5.5.Final`），但尚未使用。MapStruct 能通过声明式接口自动生成转换代码，未来字段增多时可考虑引入。

---

## 10. 命名规范

项目 DTO 命名遵循统一格式：**动词 + 实体名 + DTO**

| 前缀     | 含义     | 项目示例                              |
| -------- | -------- | ------------------------------------- |
| `Create` | 新建     | `CreateCategoryDTO`、`CreateOrderDTO` |
| `Update` | 更新     | `UpdateCategoryDTO`、`UpdateFoodDTO`  |
| `Delete` | 删除     | `DeleteFoodDTO`、`DeleteOrderDTO`     |
| `Add`    | 添加子项 | `AddFoodDTO`（向分类添加菜品）        |

不带动词前缀的（如 `FoodDTO`）通常是**嵌套子 DTO**，作为其他 DTO 的列表元素使用，不直接出现在 Controller 参数中。

---

## 11. 项目 DTO 全览

### Market 模块

| DTO                 | 字段                                                                          | 必填校验 | 嵌套                   | 用途                         |
| ------------------- | ----------------------------------------------------------------------------- | -------- | ---------------------- | ---------------------------- |
| `CreateCategoryDTO` | name, image                                                                   | 全部     | 无                     | 创建菜品分类                 |
| `UpdateCategoryDTO` | id, name, image                                                               | 全部     | 无                     | 更新分类信息                 |
| `AddFoodDTO`        | categoryId, foods                                                             | 全部     | `List<FoodDTO>` @Valid | 批量添加菜品                 |
| `FoodDTO`           | name, describe, burden, image, num                                            | 前4个    | 无                     | 菜品数据（嵌套子 DTO）       |
| `UpdateFoodDTO`     | categoryId, targetCategoryId, foodId, name, describe, burden, image, oldImage | 前3个    | 无                     | 更新菜品（支持跨分类移动）   |
| `DeleteFoodDTO`     | categoryId, foodId, image                                                     | 前2个    | 无                     | 删除菜品（image 用于删文件） |

### Order 模块

| DTO              | 字段                                       | 必填校验 | 嵌套                        | 用途                                    |
| ---------------- | ------------------------------------------ | -------- | --------------------------- | --------------------------------------- |
| `CreateOrderDTO` | date, num, foods                           | 全部     | `List<OrderFoodDTO>` @Valid | 创建订单                                |
| `OrderFoodDTO`   | \_id, name, describe, burden, image, value | 全部     | 无                          | 订单菜品（内部 record + @JsonProperty） |
| `DeleteOrderDTO` | id                                         | 全部     | 无                          | 删除订单                                |

---

## 12. 进阶话题与项目约定总结

### 12.1 Response DTO

项目目前**没有 Response DTO**——Service 层直接返回 Entity（如 `Market`），由 `GlobalResponseBodyAdvice` 统一包装为 `{code, message, data}` 格式。

```
当前做法：
  Service 返回 Market Entity  →  GlobalResponseBodyAdvice 包装  →  前端

更严格的做法（项目暂未采用）：
  Service 返回 ResponseDTO  →  包装  →  前端
```

Response DTO 的好处是可以隐藏 Entity 的内部字段（如 `id`）、格式化输出。当前项目规模较小，直接返回 Entity 尚可接受；如果后续接口增多或需要控制输出字段，可以考虑引入。

### 12.2 record 的局限

record 是不可变的，以下场景不能用 record：

- 需要在 Service 层逐步赋值的对象（如 Entity）——用 `@Data` class
- 需要继承父类的对象——record 不能继承（只能实现接口）

项目里的 Entity 全部用 `@Data` class，正是因为 Service 层需要 `food.setName(...)` 这样逐步赋值。

### 12.3 项目约定速查清单

```
┌──────────────────────────────────────────────────────────┐
│                   项目 DTO 约定速查                        │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  ① 定义方式：统一使用 record（不可变）                      │
│                                                          │
│  ② 校验注解：                                             │
│     String  必填 → @NotBlank(message = "缺少xxx")        │
│     List    必填 → @NotEmpty + @Valid（需要级联时）        │
│     Integer 必填 → @NotNull(message = "缺少xxx")         │
│     可选字段 → 不加注解，类型用包装类（Integer 而非 int）   │
│                                                          │
│  ③ 校验消息：统一中文，格式为"缺少xxx"                      │
│                                                          │
│  ④ 触发校验：Controller 参数前加 @Valid                     │
│                                                          │
│  ⑤ 嵌套 DTO：列表字段加 @Valid 级联校验                    │
│     子 DTO 仅被一处使用 → 放内部 record                     │
│     子 DTO 被多处复用 → 独立文件                            │
│                                                          │
│  ⑥ 字段映射：JSON 字段名与 Java 不一致时用 @JsonProperty    │
│                                                          │
│  ⑦ 命名规范：动词(Create/Update/Delete/Add) + 实体 + DTO   │
│                                                          │
│  ⑧ DTO → Entity 转换：Service 层手动映射                    │
│                                                          │
│  ⑨ Entity 用 @Data class（可变），DTO 用 record（不可变）   │
│                                                          │
└──────────────────────────────────────────────────────────┘
```

---

**最后：** DTO 是接口层与业务层之间的边界。定义好 DTO，就是定义好前后端的数据契约。校验注解让这份契约可执行——违反约定直接返回 400，不进入业务逻辑。这是分层架构的第一道防线。
