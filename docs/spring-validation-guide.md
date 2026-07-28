# Spring Boot Validation 参数校验指南

> 本指南循序渐进介绍 `spring-boot-starter-validation` 的参数校验能力。从"没有校验时手写 if"到声明式注解校验，再到自定义约束，每步只引入一个新概念。基于本项目 `JavaDoc` 实际代码与约定。
>
> 适用版本：Spring Boot 4.x，Java 17+（`jakarta.validation.*` 命名空间）

---

## 目录

1. [为什么需要参数校验](#1-为什么需要参数校验)
2. [第一层：快速上手 —— 内置校验注解](#2-第一层快速上手--内置校验注解)
3. [第二层：@Valid 触发校验](#3-第二层valid-触发校验)
4. [第三层：嵌套校验与级联 @Valid](#4-第三层嵌套校验与级联-valid)
5. [第四层：自定义校验注解](#5-第四层自定义校验注解)
6. [第五层：校验失败处理](#6-第五层校验失败处理)
7. [校验注解速查表](#7-校验注解速查表)

---

## 1. 为什么需要参数校验

### 问题起源

假设你在写菜品管理接口。前端传过来的数据可能是任何东西——空字符串、负数价格、超长菜名、甚至 null。没有校验时，你必须在每个 Controller 方法里手写检查：

```java
// BAD：手动 if 校验，每个接口都要重复
@PostMapping("/addFood")
public Object addFood(@RequestBody Map<String, Object> body) {
    String name = (String) body.get("name");
    if (name == null || name.isBlank()) {
        throw new IllegalArgumentException("缺少菜名");
    }
    String image = (String) body.get("image");
    if (image == null || image.isBlank()) {
        throw new IllegalArgumentException("缺少图片");
    }
    Integer num = (Integer) body.get("num");
    if (num == null || num < 0) {
        throw new IllegalArgumentException("数量不合法");
    }
    // ... 真正的业务逻辑
}
```

**问题在哪里？**

- 校验代码比业务代码还多，淹没了核心逻辑。
- 每个接口都要重复类似的判断，且校验规则散落各处。
- 校验逻辑和业务逻辑耦合在一起，修改校验规则就要改业务代码。
- 错误消息格式不统一，有的抛异常，有的返回 null。

### Validation 的解决方案

Bean Validation 的核心思想：**"用注解声明校验规则，由框架自动执行"**。

```java
// GOOD：用注解声明规则
public record AddFoodDTO(
        @NotBlank(message = "缺少菜名") String name,
        @NotBlank(message = "缺少图片") String image,
        @Min(value = 0, message = "数量不能为负") Integer num
) {}
```

一行注解替代十几行 if。规则写在字段旁边，一眼就知道校验了什么。框架在进入 Controller 方法前就完成校验，方法体内只写业务逻辑。

---

## 2. 第一层：快速上手 —— 内置校验注解

### 2.1 依赖

> ⚠️ **重要：** 自 Spring Boot 2.3 起，`spring-boot-starter-webmvc` **不再**自动传递引入 `spring-boot-starter-validation`，必须**显式声明**。

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

本项目 `pom.xml` 中二者都已声明，缺一不可：

```xml
<!-- WebMVC 不携带 validation，需要各自显式声明 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

> Spring Boot 4.x 默认使用 Jakarta Validation 3.x，包路径为 `jakarta.validation.*`（旧版 `javax.validation.*` 已废弃）。

### 2.2 常用注解一览

Jakarta Validation 提供了 20+ 个内置注解，以下是项目中最常用的：

```java
import jakarta.validation.constraints.*;

public class ValidationDemo {

    // ── 非空校验 ──
    @NotNull            // 拒绝 null，允许空字符串
    private String notNullField;

    @NotEmpty            // 拒绝 null + 空字符串/空集合
    private List<String> notEmptyList;

    @NotBlank             // 拒绝 null + 空字符串 + 纯空白（如 "   "）
    private String notBlankField;

    // ── 数字范围 ──
    @Min(0)              // 最小值（含）
    @Max(999)             // 最大值（含）
    private Integer count;

    @Positive             // 正数（> 0）
    @PositiveOrZero       // 非负数（≥ 0）
    @Negative             // 负数（< 0）
    @NegativeOrZero       // 非正数（≤ 0）
    private Integer score;

    @DecimalMin("0.0")    // BigDecimal 最小值
    @DecimalMax("100.0")  // BigDecimal 最大值
    private BigDecimal price;

    // ── 字符串模式 ──
    @Size(min = 2, max = 50)  // 字符串/集合长度范围
    private String username;

    @Pattern(regexp = "^1[3-9]\\d{9}$") // 正则匹配
    private String phone;

    @Email               // 邮箱格式（宽松匹配，不验证域名存在）
    private String email;

    // ── 时间 ──
    @Past                // 必须是过去时间
    @PastOrPresent        // 过去或现在
    @Future                // 必须是未来时间
    @FutureOrPresent       // 未来或现在
    private LocalDateTime createTime;

    // ── 布尔 ──
    @AssertTrue          // 必须为 true
    @AssertFalse          // 必须为 false
    private Boolean agreed;
}
```

### 2.3 本项目实际使用模式

项目中所有 DTO 使用 `record` 定义，校验注解挂在构造器参数上：

```java
// src/main/java/com/example/javadoc/module/market/dto/CreateCategoryDTO.java
public record CreateCategoryDTO(
        @NotBlank(message = "缺少名称") String name,
        @NotBlank(message = "缺少图标") String image) {
}
```

**项目约定：**

| 字段类型     | 必填时用    | 可选时   |
| ------------ | ----------- | -------- |
| `String`     | `@NotBlank` | 不加注解 |
| `List<T>`    | `@NotEmpty` | 不加注解 |
| `Integer` 等 | `@NotNull`  | 不加注解 |
| `Integer` 等 | `@NotNull`  | 不加注解 |

---

## 3. 第二层：@Valid 触发校验

### 3.1 核心原理

DTO 上的校验注解**不会自动生效**。必须在 Controller 方法参数前加 `@Valid`，Spring 才会在绑定参数后、调用方法前执行校验：

```java
// @Valid 是触发开关，不加则校验注解形同虚设
@PostMapping("/addCategory")
public Object addCategory(@Valid @RequestBody CreateCategoryDTO dto) {
    // 如果校验失败，这行代码永远不会执行
    return marketService.addCategory(dto.name(), dto.image());
}
```

### 3.2 执行流程

```
请求到达
  │
  ├─ 1. Spring 将 JSON 反序列化为 CreateCategoryDTO
  │
  ├─ 2. 发现 @Valid，触发 Validator
  │      ├─ name = null → @NotBlank 校验失败
  │      └─ image = null → @NotBlank 校验失败
  │
  ├─ 3. 校验失败 → 抛出 MethodArgumentNotValidException
  │
  ├─ 4. GlobalExceptionHandler 捕获，返回 400
  │      {"code": 400, "message": "name: 缺少名称; image: 缺少图标"}
  │
  └─ 5. Controller 方法体不被调用
```

---

## 4. 第三层：嵌套校验与级联 @Valid

### 4.1 问题场景

`AddFoodDTO` 包含一个 `List<FoodDTO>`。`@NotEmpty` 只校验列表非空，但列表里每个 `FoodDTO` 的字段也需要校验：

```java
public record AddFoodDTO(
        @NotBlank(message = "缺少分类") String categoryId,
        @NotEmpty(message = "缺少菜") @Valid List<FoodDTO> foods) {
        //                             ↑ @Valid 是关键
}
```

没有 `@Valid` 时：

```json
// 这个请求会通过校验！因为列表非空，但元素内容未校验
{
  "categoryId": "abc",
  "foods": [
    { "name": "", "image": "" } // ← 空字段也会通过
  ]
}
```

加上 `@Valid` 后，校验器会递归进入每个 `FoodDTO`，检查其字段上的 `@NotBlank` 等注解。

### 4.2 项目中的实际示例

```java
// CreateOrderDTO —— 嵌套内部 record
public record CreateOrderDTO(
        @NotBlank(message = "缺少日期") String date,
        @NotNull(message = "缺少数量") Integer num,
        @NotEmpty(message = "缺少菜品") @Valid List<OrderFoodDTO> foods) {

    public record OrderFoodDTO(
            @JsonProperty("_id") @NotBlank(message = "缺少菜名id") String id,
            @NotBlank(message = "缺少菜名") String name,
            @NotNull(message = "缺少数量") Integer value) {
    }
}
```

```
校验执行顺序：

CreateOrderDTO
  ├─ date:    @NotBlank ✓
  ├─ num:     @NotNull  ✓
  └─ foods:   @NotEmpty ✓
        └─ @Valid ──▶ 遍历每个 OrderFoodDTO
              ├─ id:    @NotBlank ✓
              ├─ name:  @NotBlank ✓
              └─ value: @NotNull  ✓
```

> ⚠️ **常见坑：** 忘记在嵌套集合上加 `@Valid`，导致子对象校验完全跳过。这是最隐蔽的校验 bug。

### 4.3 嵌套对象的 @Valid

不仅是集合，单个嵌套对象也需要 `@Valid`：

```java
public record OrderDTO(
        @NotBlank String orderId,
        @Valid AddressDTO address  // ← 必须加 @Valid
) {}
```

---

## 5. 第四层：自定义校验注解

### 6.1 当内置注解不够用时

假设有一个业务需求：菜名不能包含特殊字符（只允许中文、字母、数字、空格）。`@Pattern` 可以做到，但正则表达式散落在多个 DTO 中不易维护。更好的做法是定义一个 `@ValidFoodName`。

### 6.2 两步实现自定义约束

**第一步：定义注解**

```java
package com.example.javadoc.core.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = FoodNameValidator.class)  // ← 绑定校验器
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidFoodName {

    // 这三个属性是规范要求的，必须提供
    String message() default "菜名包含非法字符";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
```

**第二步：实现校验器**

```java
package com.example.javadoc.core.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class FoodNameValidator implements ConstraintValidator<ValidFoodName, String> {

    private static final String PATTERN = "^[\\u4e00-\\u9fa5a-zA-Z0-9 ]+$";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // null 值交给 @NotNull / @NotBlank 处理，这里只校验格式
        if (value == null) {
            return true;
        }
        return value.matches(PATTERN);
    }
}
```

### 6.3 使用自定义注解

```java
public record AddFoodDTO(
        @NotBlank(message = "缺少菜名")
        @ValidFoodName(message = "菜名只能包含中文、英文、数字和空格")  // ← 一行搞定
        String name,
        // ...
) {}
```

### 6.4 自定义校验的设计原则

1. **null 值不判错**：留给 `@NotNull` / `@NotBlank` 负责，校验器只关注格式/逻辑
2. **message 有默认值**：注解定义默认消息，使用时可按需覆盖
3. **保持校验器无状态**：不要在校验器里注入 Spring Bean（除非用特殊处理）

---

## 6. 第五层：校验失败处理

### 6.1 校验异常

| 校验位置   | 触发注解 | 失败异常                          | HTTP 状态建议 |
| ---------- | -------- | --------------------------------- | ------------- |
| Controller | `@Valid` | `MethodArgumentNotValidException` | 400           |

### 6.2 Controller 校验异常处理（项目已实现）

```java
// src/main/java/com/example/javadoc/core/advice/GlobalExceptionHandler.java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<Map<String, Object>> handleValidation(
        MethodArgumentNotValidException ex, HttpServletRequest request) {

    // 提取所有字段错误，拼接为 "field1: msg1; field2: msg2"
    String message = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .reduce((a, b) -> a + "; " + b)
            .orElse("Validation failed");

    return buildResponse(HttpStatus.BAD_REQUEST, request, message);
}
```

校验失败时的响应格式：

```json
{
  "code": 400,
  "timestamp": "2026-07-21T10:30:00Z",
  "path": "/api/market/addCategory",
  "message": "name: 缺少名称; image: 缺少图标"
}
```

### 6.3 校验失败 vs 业务异常的区分

```
参数格式错误   →  校验失败  →  400 Bad Request    （"你传的数据不对"）
业务规则违反   →  业务异常  →  409 / 422 / ...     （"数据格式对，但逻辑不通"）
系统内部错误   →  运行时异常 →  500 Internal Error  （"服务器内部问题"）
```

校验框架负责"数据格式对不对"，不负责"业务逻辑通不通"。

---

## 7. 校验注解速查表

### 7.1 Bean Validation 内置注解

| 注解               | 作用                       | 适用类型                                                                              |
| ------------------ | -------------------------- | ------------------------------------------------------------------------------------- |
| `@Null`            | 必须为 null                | 任意                                                                                  |
| `@NotNull`         | 不能为 null                | 任意                                                                                  |
| `@NotEmpty`        | 不能为 null 且 size > 0    | `CharSequence`、`Collection`、`Map`、数组                                             |
| `@NotBlank`        | 不能为 null 且 trimmed > 0 | `CharSequence`                                                                        |
| `@AssertTrue`      | 必须为 true                | `boolean` / `Boolean`                                                                 |
| `@AssertFalse`     | 必须为 false               | `boolean` / `Boolean`                                                                 |
| `@Min(value)`      | 必须 ≥ value（数值）       | `BigDecimal`、`BigInteger`、`byte`、`short`、`int`、`long` 及其包装类                 |
| `@Max(value)`      | 必须 ≤ value（数值）       | 同上                                                                                  |
| `@DecimalMin`      | 必须 ≥ value（字符串形式） | `BigDecimal`、`BigInteger`、`CharSequence`、`byte`、`short`、`int`、`long` 及其包装类 |
| `@DecimalMax`      | 必须 ≤ value（字符串形式） | 同上                                                                                  |
| `@Positive`        | 必须 > 0                   | 数值类型 + 包装类                                                                     |
| `@PositiveOrZero`  | 必须 ≥ 0                   | 同上                                                                                  |
| `@Negative`        | 必须 < 0                   | 同上                                                                                  |
| `@NegativeOrZero`  | 必须 ≤ 0                   | 同上                                                                                  |
| `@Size(min, max)`  | size 在 [min, max] 范围内  | `CharSequence`、`Collection`、`Map`、数组                                             |
| `@Digits(i, f)`    | 整数 i 位 + 小数 f 位      | `BigDecimal`、`BigInteger`、`CharSequence`、`byte`、`short`、`int`、`long` 及其包装类 |
| `@Pattern(regexp)` | 匹配正则表达式             | `CharSequence`                                                                        |
| `@Email`           | 邮箱格式（宽松）           | `CharSequence`                                                                        |
| `@Past`            | 必须是过去时间             | `Date`、`Calendar`、`Instant`、`LocalDate`、`LocalDateTime` 等                        |
| `@PastOrPresent`   | 过去或现在                 | 同上                                                                                  |
| `@Future`          | 必须是未来时间             | 同上                                                                                  |
| `@FutureOrPresent` | 未来或现在                 | 同上                                                                                  |

### 7.2 Hibernate Validator 扩展注解

`spring-boot-starter-validation` 内置 Hibernate Validator，提供了额外注解：

| 注解                | 作用                             |
| ------------------- | -------------------------------- |
| `@URL`              | 校验是否为合法 URL               |
| `@Length`           | 字符串长度（推荐用 `@Size`）     |
| `@Range`            | 数值范围（推荐用 `@Min`/`@Max`） |
| `@LuhnCheck`        | Luhn 算法（银行卡号校验）        |
| `@CreditCardNumber` | 信用卡号格式校验                 |

### 7.3 项目注解使用决策树

```
需要校验什么？

├─ 非空 —— 字段类型？
│   ├─ String  →  @NotBlank（拒绝 null + "" + "  "）
│   ├─ List/Map/数组  →  @NotEmpty（拒绝 null + 空集合）
│   └─ Integer/对象   →  @NotNull（只拒绝 null）
│
├─ 格式 —— 校验什么？
│   ├─ 邮箱  →  @Email
│   ├─ 手机号  →  @Pattern(regexp = "...")
│   ├─ URL  →  @URL
│   └─ 自定义格式  →  自定义 @Constraint
│
├─ 范围 —— 校验什么？
│   ├─ 字符串长度  →  @Size(min, max)
│   ├─ 数值大小    →  @Min / @Max 或 @Positive / @Negative
│   └─ 小数精度    →  @Digits(integer, fraction)
│
└─ 内置注解不够用？
    └─ 自定义 @Constraint + ConstraintValidator
```

### 7.4 陷阱速查

| 陷阱                              | 后果                                | 正确做法                         |
| --------------------------------- | ----------------------------------- | -------------------------------- |
| 忘写 `@Valid`                     | DTO 上的注解全部不生效              | Controller 参数前必须加 `@Valid` |
| 嵌套对象不加 `@Valid`             | 子对象字段忽略校验                  | 嵌套集合/对象前加 `@Valid`       |
| `@NotBlank` 用于 Integer          | 编译错误（注解只允许 CharSequence） | Integer 用 `@NotNull`            |
| `@NotNull` 用于 String 但需要非空 | 空字符串 `""` 能通过                | String 用 `@NotBlank`            |

---

> **延伸阅读：**
>
> - [DTO 指南](dto-guide.md) —— 项目 DTO 设计约定与 record 使用
> - [Spring Boot 统一异常处理指南](spring-exception-guide.md) —— 全局异常处理机制
> - [Jakarta Bean Validation 规范](https://beanvalidation.org/)
