# SpringDoc OpenAPI 接口文档指南

> 本指南循序渐进介绍 SpringDoc OpenAPI。从"前后端联调时互相猜接口"到"打开 Swagger UI 就能看到所有接口和模型"，每步只引入一个新概念。
> 基于本项目 `demo1` 实际代码（MarketController / OrderController），适用版本：Spring Boot 4.x，springdoc-openapi 2.8.x，Java 17+（Jakarta 命名空间）。

---

## 目录

1. [为什么需要接口文档](#1-为什么需要接口文档)
2. [快速上手：第一页 Swagger UI](#2-快速上手第一页-swagger-ui)
3. [第一层：@Tag 与 @Operation —— 给接口起名字](#3-第一层tag-与-operation--给接口起名字)
4. [第二层：@Schema —— 给 DTO 字段写说明](#4-第二层schema--给-dto-字段写说明)
5. [第三层：@Parameter 与 @ApiResponse —— 细化参数和返回值](#5-第三层parameter-与-apiresponse--细化参数和返回值)
6. [第四层：与统一响应格式集成](#6-第四层与统一响应格式集成)
7. [第五层：API 分组](#7-第五层api-分组)
8. [Security 鉴权集成点](#8-security-鉴权集成点)
9. [速查清单](#9-速查清单)

---

## 1. 为什么需要接口文档

### 问题起源

假设你是后端，刚写完菜品管理的 CRUD 接口。前端同事来找你了：

> "创建菜品的接口路径是什么？请求体长什么样？"
> "`/market/addFood` 对吧——等等，categoryId 是 String 还是 Integer？foods 是数组吗？"
> "上传图片返回的 url 字段叫什么？"

你把接口信息写在 Confluence / 飞书文档里。两个月后，接口改了三个字段，文档忘了更新。新来的前端对着过时的文档调了半天，跑来问你："你文档写的 `price` 字段怎么不存在？"

```
团队协作中的接口文档困境

你写的代码                    wiki 上的文档              前端看到的
══════════                    ════════════              ══════════
MarketController.java         "创建菜品接口"
├─ POST /market/addFood       ├─ 路径：/api/addFood  ✗   路径对不上
├─ @RequestBody AddFoodDTO    ├─ 参数：name, price  ✗   字段已改名
│   ├─ categoryId: String     │   （无类型信息）     ✗   不知道是 String
│   └─ foods: List<FoodDTO>   ├─ 返回：{data: ...}  ✗   实际是 {code, message, data}
└─ 返回 {code, message, data} └─ 最后更新：三个月前   ✗   已过时

结论：手动维护的文档 = 注定过时的文档
```

**问题在哪里？**

- 代码是唯一的真相源（Single Source of Truth），但文档却是手写的副本——副本迟早和原件不一致
- 接口数量一多（十个、二十个），人工维护文档的成本指数级上升
- 新人接手项目时，没有地方能"一眼看到所有接口"

### SpringDoc 的解决方案

SpringDoc 的核心思想：**"代码即文档——你在 Controller 上写的注解，就是接口文档的数据源"**。

```
传统方式                             SpringDoc 方式
════════                             ════════════
手写 wiki 文档                       注解写在代码里
   │                                    │
   ├─ 写完代码后手动更新              ├─ 写代码时顺手写注解
   ├─ 字段改名要改两处                ├─ 字段改名 → 注解跟着走
   ├─ 没有交互能力                    ├─ Swagger UI 可在线调试
   └─ 迟早过时                        └─ 代码和文档永不脱节
```

SpringDoc 读取你的 Spring MVC 注解（`@GetMapping`、`@RequestParam`、`@RequestBody` 等），自动生成符合 **OpenAPI 3.0** 规范的 JSON 描述文件，然后渲染成交互式 Swagger UI 页面——你可以在页面上直接"试用"每个接口。

---

## 2. 快速上手：第一页 Swagger UI

### 2.1 添加依赖

在 `pom.xml` 中加入：

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.17</version>
</dependency>
```

> `springdoc-openapi-starter-webmvc-ui` 包含了 Swagger UI 页面，无需额外引入前端资源。如果你的项目用的是 WebFlux，换成 `springdoc-openapi-starter-webflux-ui`。

**零配置即可运行**——添加依赖后启动项目，SpringDoc 自动扫描所有 `@RestController` 并生成文档。

### 2.2 访问文档

启动项目后，访问以下两个地址：

| URL                                     | 内容                                |
| --------------------------------------- | ----------------------------------- |
| `http://localhost:8080/swagger-ui.html` | Swagger UI 交互式页面（可在线调试） |
| `http://localhost:8080/v3/api-docs`     | OpenAPI 3.0 JSON（给工具消费）      |

打开 `http://localhost:8080/swagger-ui.html`，你会看到：

```
┌─────────────────────────────────────────────────────┐
│  Swagger UI                                         │
├─────────────────────────────────────────────────────┤
│                                                     │
│  OpenAPI definition    [ v3/api-docs  ▾ ]           │
│                                                     │
│  ── market-controller ──────────────────────────    │
│  POST   /market/addCategory         添加分类         │
│  DELETE /market/deleteCategory      删除分类         │
│  PUT    /market/updateCategory      更新分类         │
│  PUT    /market/addFood             添加菜品         │
│  DELETE /market/deleteFood          删除菜品         │
│  PUT    /market/updateFood          更新菜品         │
│  GET    /market/getAll              获取所有分类     │
│  GET    /market/findFoods           搜索菜品         │
│  POST   /market/uploadImage         上传图片         │
│                                                     │
│  ── order-controller ───────────────────────────    │
│  POST   /order/addOrder             创建订单         │
│  GET    /order/getOrder             查询订单         │
│  DELETE /order/deleteOrder          删除订单         │
│                                                     │
└─────────────────────────────────────────────────────┘
```

点开任意一个接口，可以看到请求参数、请求体结构和"Try it out"按钮——**可以直接在页面上发请求调试**。

### 2.3 发生了什么

```
SpringDoc 的自动生成流程

你的 Controller 代码                 SpringDoc 读取后生成
═══════════════════                 ════════════════════
@RestController
@RequestMapping("/market")    ──→   tag: "market-controller"
public class MarketController {
                                     ↓
    @PostMapping("/addCategory") ──→  POST /market/addCategory
    @DeleteMapping("/delete...") ──→  DELETE /market/deleteCategory
    @GetMapping("/getAll")      ──→  GET /market/getAll
    ...
}
```

SpringDoc 从你的注解中自动提取了三样东西：

| 提取自                                       | 用于生成                        |
| -------------------------------------------- | ------------------------------- |
| `@RequestMapping` + `@GetMapping` 等         | 接口路径和 HTTP 方法            |
| 方法参数上的 `@RequestBody`、`@RequestParam` | 请求参数结构                    |
| 类名 `MarketController`                      | 分组标签（`market-controller`） |

**这就是"零配置可用"的含义**——你不需要写任何 SpringDoc 专用注解，现有的 Spring MVC 注解已经足够生成一份可用的接口文档。接下来几层要做的，是让这份文档从"能用"变成"好用"。

---

## 3. 第一层：@Tag 与 @Operation —— 给接口起名字

### 3.1 问题：默认名字不够友好

上面的 Swagger UI 中，分组标签是 `market-controller`（类名转小写加连字符），接口摘要也是空白的。对于看文档的人来说，`market-controller` 不如"市场管理"直观，没有描述的接口看起来像个黑盒。

### 3.2 @Tag：给 Controller 起中文名

`@Tag` 注解放在 Controller 类上，给这个分组起一个人类可读的名字：

```java
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "市场管理", description = "菜品分类、菜品 CRUD 与图片上传")
@RestController
@RequestMapping("/market")
@RequiredArgsConstructor
public class MarketController {
    // ...
}
```

```java
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "订单管理", description = "订单的创建、查询与删除")
@RestController
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {
    // ...
}
```

效果：Swagger UI 中 `market-controller` → **市场管理**，分组名变得一目了然。

### 3.3 @Operation：给每个接口写摘要

`@Operation` 注解放在方法上，给接口添加摘要（summary）和详细描述（description）：

```java
import io.swagger.v3.oas.annotations.Operation;

@Tag(name = "市场管理", description = "菜品分类、菜品 CRUD 与图片上传")
@RestController
@RequestMapping("/market")
@RequiredArgsConstructor
public class MarketController {

    @Operation(summary = "添加分类", description = "创建新的菜品分类，需要名称和图标")
    @PostMapping("/addCategory")
    public CategoryResponse addCategory(@Valid @RequestBody CreateCategoryDTO dto) {
        return marketService.addCategory(dto);
    }

    @Operation(summary = "获取所有分类", description = "返回所有分类及其包含的菜品列表")
    @GetMapping("/getAll")
    public List<CategoryResponse> getAll() {
        return marketService.getAll();
    }

    @Operation(summary = "搜索菜品", description = "根据关键词模糊搜索所有分类下的菜品")
    @GetMapping("/findFoods")
    public List<CategoryResponse> findFoods(@RequestParam("text") String text) {
        return marketService.findFoods(text);
    }

    @Operation(summary = "上传图片", description = "上传菜品或分类图片，返回图片访问 URL")
    @PostMapping(value = "/uploadImage", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String uploadImage(@RequestParam("file") MultipartFile file) {
        // ...
    }
}
```

```
效果对比

之前                              之后
════                              ════
POST /market/addCategory          POST /market/addCategory  添加分类
  （空白描述）                      创建新的菜品分类，需要名称和图标

GET /market/findFoods             GET /market/findFoods  搜索菜品
  （空白描述）                      根据关键词模糊搜索所有分类下的菜品
```

### 本节回顾

```
@Tag       → 类上   →  分组名从 "market-controller" 变成 "市场管理"
@Operation → 方法上 →  接口摘要从空白变成 "添加分类"、"搜索菜品"

两个注解，让文档从"机器可读"变成了"人类可读"
```

---

## 4. 第二层：@Schema —— 给 DTO 字段写说明

### 4.1 问题：DTO 字段缺少语义

现在接口有了标题和描述，但 Swagger UI 中点开 `POST /market/addFood`，请求体的 Schema 是这样的：

```json
{
  "categoryId": "string",
  "foods": [
    {
      "burden": "string",
      "describe": "string",
      "image": "string",
      "name": "string",
      "num": 0
    }
  ]
}
```

前端看到 `burden` 是什么？`num` 的取值范围是多少？`image` 是 URL 还是 base64？这些信息全部缺失——**DTO 字段的语义需要用 @Schema 来补充**。

### 4.2 @Schema 基础用法

`@Schema` 可以放在字段上（对于本项目使用的 `record`，放在构造器参数上）：

```java
import io.swagger.v3.oas.annotations.media.Schema;

public record FoodDTO(
        @Schema(description = "菜品 ID（MongoDB 的 _id）", example = "507f1f77bcf86cd799439011")
        @JsonProperty("_id")
        String id,

        @Schema(description = "菜品名称", example = "宫保鸡丁")
        String name,

        @Schema(description = "菜品描述", example = "经典川菜，花生与鸡丁爆炒")
        String describe,

        @Schema(description = "配料说明", example = "鸡胸肉、花生、干辣椒、花椒")
        String burden,

        @Schema(description = "菜品图片完整 URL", example = "/static/images/market/1775884077982.jpg")
        String image,

        @Schema(description = "菜品数量（≥ 0）", example = "10", minimum = "0")
        Integer num
) {}
```

```java
import io.swagger.v3.oas.annotations.media.Schema;

public record AddFoodDTO(
        @Schema(description = "所属分类 ID", example = "507f1f77bcf86cd799439011")
        @NotBlank(message = "缺少分类")
        String categoryId,

        @Schema(description = "要添加的菜品列表")
        @NotEmpty(message = "缺少菜")
        @Valid
        List<FoodDTO> foods
) {}
```

效果：Swagger UI 的 Schema 区域会显示每个字段的 description 和 example 值，前端不用再猜字段含义。

Entity 类也可以加 `@Schema`，让文档中展示完整的领域模型：

```java
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Schema(description = "市场分类")
@Document(collection = "markets")
public class Market {

    @Schema(description = "分类 ID", example = "507f1f77bcf86cd799439011")
    @Id
    private String id;

    @Schema(description = "分类名称", example = "热菜")
    private String name;

    @Schema(description = "分类图标 URL", example = "/static/images/market/gbyd.png")
    private String image;

    @Schema(description = "该分类下的菜品列表")
    private List<FoodItem> foods = new ArrayList<>();

    @Data
    @Schema(description = "菜品子文档")
    public static class FoodItem {

        @Schema(description = "菜品 ID", example = "abc123")
        private String id;

        @Schema(description = "菜品名称", example = "鱼香肉丝")
        private String name;

        @Schema(description = "菜品描述")
        private String describe;

        @Schema(description = "配料")
        private String burden;

        @Schema(description = "图片 URL")
        private String image;

        @Schema(description = "菜品数量", example = "5")
        private Integer num = 0;
    }
}
```

### 4.4 @Schema 关键属性速查

| 属性                  | 作用                           | 示例                                |
| --------------------- | ------------------------------ | ----------------------------------- |
| `description`         | 字段说明                       | `@Schema(description = "菜品名称")` |
| `example`             | 示例值（显示在 Swagger UI 中） | `@Schema(example = "宫保鸡丁")`     |
| `requiredMode`        | 标记必填（显示红色 \* 号）     | `@Schema(requiredMode = REQUIRED)`  |
| `minimum` / `maximum` | 数值范围                       | `@Schema(minimum = "0")`            |
| `hidden`              | 从文档中隐藏                   | `@Schema(hidden = true)`            |
| `defaultValue`        | 默认值                         | `@Schema(defaultValue = "0")`       |

> 对于 `record` 构造器参数，`requiredMode` 通常不需要手动设置——如果你的字段标注了 `@NotBlank` / `@NotNull` 等 Jakarta Validation 注解，SpringDoc 会自动识别为必填。

### 本节回顾

```
@Schema 让 Swagger UI 的 Schema 区域从"只有类型名"变成"有描述、有示例、有约束"

之前：{ "burden": "string" }              ← "burden 是啥？"
之后：{ "burden": "配料说明，如：鸡胸肉" }  ← 一目了然
```

---

## 5. 第三层：@Parameter 与 @ApiResponse —— 细化参数和返回值

### 5.1 问题：@RequestParam / @PathVariable 缺少描述

前面的 `@Operation` 给接口写了摘要，但方法参数上的 `@RequestParam`、`@PathVariable` 在 Swagger UI 中仍然只有一个名字，没有说明：

```
GET /market/findFoods  搜索菜品
  Parameters:
    text: string    ← text 是什么？必填吗？
```

同理，Swagger UI 默认只显示 200 响应，不会告诉你"校验失败会返回 400"、"分类不存在会返回 409"——**这些语义需要用 @Parameter 和 @ApiResponse 来补全**。

### 5.2 @Parameter：描述请求参数

```java
import io.swagger.v3.oas.annotations.Parameter;

@Operation(summary = "搜索菜品", description = "根据关键词模糊搜索所有分类下的菜品")
@GetMapping("/findFoods")
public List<CategoryResponse> findFoods(
        @Parameter(description = "搜索关键词（匹配菜名）", required = true, example = "鸡")
        @RequestParam("text") String text) {
    return marketService.findFoods(text);
}
```

```java
@Operation(summary = "删除分类", description = "按 ID 删除分类，该分类下的菜品也会被删除")
@DeleteMapping("/deleteCategory")
public String deleteCategory(
        @Parameter(description = "分类 ID", required = true, example = "507f1f77bcf86cd799439011")
        @RequestParam("id") String id) {
    return marketService.deleteCategory(id);
}
```

`@PathVariable` 的用法完全相同：

```java
@Operation(summary = "获取分类详情", description = "根据 ID 获取单个分类及其包含的菜品")
@GetMapping("/getCategory/{id}")
public CategoryResponse getCategory(
        @Parameter(description = "分类 ID", required = true, example = "507f1f77bcf86cd799439011")
        @PathVariable("id") String id) {
    return marketService.getCategory(id);
}
```

效果：Swagger UI 中参数旁边多了 `description`、`required` 标识和 `example` 值。

### 5.3 @ApiResponse：描述各种响应

```java
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import com.example.demo1.module.market.dto.response.CategoryResponse;

@Operation(summary = "添加分类", description = "创建新的菜品分类，需要名称和图标")
@ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "分类创建成功"),
        @ApiResponse(responseCode = "400", description = "参数校验失败（名称或图标为空）"),
        @ApiResponse(responseCode = "409", description = "分类已存在或名称重复")
})
@PostMapping("/addCategory")
public CategoryResponse addCategory(@Valid @RequestBody CreateCategoryDTO dto) {
    return marketService.addCategory(dto);
}
```

`@ApiResponses` 是容器注解，里面放多个 `@ApiResponse`。每个 `@ApiResponse` 用 `responseCode` 标记 HTTP 状态码，`description` 说明这种响应代表什么情况。

> SpringDoc 支持 `@ApiResponse` 和 `@ApiResponses` 两种写法，只有一个响应时可以直接用 `@ApiResponse`；多个时需要用 `@ApiResponses` 包裹。

### 5.4 标注文件上传接口

文件上传接口的 `@Parameter` 写法稍有不同——参数来源不是 JSON body，而是 `multipart/form-data`：

```java
@Operation(summary = "上传图片", description = "上传菜品或分类图片，最大 10MB，返回图片访问 URL")
@ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "上传成功"),
        @ApiResponse(responseCode = "413", description = "文件超过 10MB 限制")
})
@PostMapping(value = "/uploadImage", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public String uploadImage(
        @Parameter(description = "图片文件（支持 jpg/png/gif）", required = true)
        @RequestParam("file") MultipartFile file) {
    // ...
}
```

### 本节回顾

```
@Parameter    → 给 @RequestParam / @PathVariable 加描述、示例、必填标记
@ApiResponse  → 标注接口可能返回的各种 HTTP 状态码及其含义

之前：前端不知道 deleteCategory 的 id 是什么格式
之后：Swagger UI 显示 "分类 ID（MongoDB 的 _id），必填，示例：507f..."
```

---

## 6. 第四层：与统一响应格式集成

### 6.1 问题：Advice 包装了响应，但 SpringDoc 看不见

你的项目通过 [GlobalResponseBodyAdvice](file:///d:/javaProject/demo1/src/main/java/com/example/demo1/core/advice/GlobalResponseBodyAdvice.java) 统一包装了所有成功响应：

```
Controller 返回 CategoryResponse
        ↓
GlobalResponseBodyAdvice.beforeBodyWrite() 拦截
        ↓
实际发出: { "code": 200, "message": "success", "data": CategoryResponse }
```

客户端收到的 JSON 始终是 `{code, message, data}` 三层结构。但 SpringDoc 只分析 Controller 方法的**返回类型**——它看到 `CategoryResponse` 就展示 `CategoryResponse` 的 Schema，完全不知道外面还包了一层 `code` 和 `message`。

**前端在 Swagger UI 里看到的 Schema 和实际收到的 JSON 对不上。**

### 6.2 最佳实践：全局注册包装 Schema（推荐）

与其在每个接口上手动写 `content = @Content(...)`，不如用一个 `OpenApiCustomizer` Bean 告诉 SpringDoc：**所有成功响应外面都包了一层 `{code, message, data}`**。一次配置，全项目生效。

在 `config` 包下创建（或扩展）`SpringDocConfig`：

```java
package com.example.demo1.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringDocConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("demo1 菜品订单系统 API")
                        .description("基于 Spring Boot 4.x + MongoDB 的菜品管理和订单系统")
                        .version("1.0.0"));
    }

    /**
     * 全局注册统一成功响应格式 {code, message, data}
     *
     * 原理：遍历 OpenAPI 规范中所有接口的 200 响应，
     * 将原始 Schema 包裹进 {code, message, data} 结构。
     * SpringDoc 的 SchemaResolver 已经解析好了原始 DTO，
     * 这里只是在外面包一层。
     */
    @Bean
    public OpenApiCustomizer responseWrapperCustomizer() {
        return openApi -> {
            openApi.getPaths().forEach((path, pathItem) -> {
                pathItem.readOperations().forEach(operation -> {
                    ApiResponses responses = operation.getResponses();
                    // 只处理已有的 200 响应（没有 @ApiResponse 的接口不处理）
                    ApiResponse okResponse = responses.get("200");
                    if (okResponse == null) return;

                    // 通过 $ref 引用原始 Schema，再包进外层结构
                    Content originalContent = okResponse.getContent();
                    Schema<?> originalSchema = originalContent != null
                            ? originalContent.get("application/json").getSchema()
                            : new Schema<>().type("object");

                    Schema<?> wrappedSchema = new Schema<>()
                            .type("object")
                            .addProperty("code", new Schema<>().type("integer").example(200))
                            .addProperty("message", new Schema<>().type("string").example("success"))
                            .addProperty("data", originalSchema);

                    okResponse.setContent(new Content().addMediaType("application/json",
                            new MediaType().schema(wrappedSchema)));
                });
            });
        };
    }
}
```

效果：Swagger UI 中每个接口的 200 响应 Schema 自动变成：

```json
{
  "code": 200,
  "message": "success",
  "data": { … }   ← 这里是 Controller 真实返回的 DTO 结构，自动同步
}
```

> **为什么这是最佳实践**：DTO 字段变了（比如 `CategoryResponse` 新增了字段），Swagger Schema 自动同步——不需要改任何 `@ApiResponse` 注解。一次配置，终身受益。

### 6.3 全局注册错误响应

成功响应需要包裹 `{code, message, data}`，而你的 [GlobalExceptionHandler](file:///d:/javaProject/demo1/src/main/java/com/example/demo1/core/advice/GlobalExceptionHandler.java) 在异常时返回的是另一种格式：

```json
{ "code": 409, "timestamp": "…", "path": "/market/…", "message": "分类已存在" }
```

同样的思路——不在每个接口上手写 JSON，而是**先在 `components/schemas` 中注册 `ErrorResponse` 的 Schema，再通过 `$ref` 引用**。

继续在 `SpringDocConfig` 中追加两个 Bean：

```java
// ① 将 ErrorResponse 注册到 OpenAPI 的全局 Schema 仓库中
@Bean
public OpenApiCustomizer errorSchemaCustomizer() {
    return openApi -> {
        openApi.getComponents().addSchemas("ErrorResponse",
            new Schema<>()
                .type("object")
                .addProperty("code", new Schema<>().type("integer").example(400))
                .addProperty("message", new Schema<>().type("string").example("参数校验失败"))
                .addProperty("timestamp", new Schema<>().type("string").example("2026-07-24T10:30:00Z"))
                .addProperty("path", new Schema<>().type("string").example("/market/addCategory"))
        );
    };
}

// ② 为所有接口统一追加 400 / 500，通过 $ref 引用上面的 Schema
@Bean
public GlobalOperationCustomizer globalErrorResponseCustomizer() {
    return (operation, handlerMethod) -> {
        ApiResponses responses = operation.getResponses();

        responses.addApiResponse("400", new ApiResponse()
                .description("参数校验失败")
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(
                                new Schema<>().$ref("#/components/schemas/ErrorResponse")))));

        responses.addApiResponse("500", new ApiResponse()
                .description("服务器内部错误")
                .content(new Content().addMediaType("application/json",
                        new MediaType().schema(
                                new Schema<>().$ref("#/components/schemas/ErrorResponse")))));

        return operation;
    };
}
```

现在 Swagger UI 中每个接口的 400/500 响应都会展示 `ErrorResponse` 的完整结构（`code`、`message`、`timestamp`、`path`），而不是一个写死的 JSON 字符串。

> 两条规则的一致性：成功响应用 `OpenApiCustomizer` 自动包裹 Schema，错误响应用 `OpenApiCustomizer` 注册 Schema + `$ref` 引用——**全程没有手写 `example`，全由 SpringDoc 自动生成和维护**。
>
> 各接口独有的业务错误码（如 409）仍在 Controller 上用 `@ApiResponse` 单独声明——"通用的全局管，特殊的局部管"。

### 本节回顾

```
统一响应格式与 SpringDoc 的集成有三个层面：

1. 成功响应包装  →  OpenApiCustomizer 遍历所有 200 响应，包裹为 {code, message, data}
2. 公共错误格式  →  OpenApiCustomizer 注册 ErrorResponse Schema + GlobalOperationCustomizer 用 $ref 添加 400/500
3. 业务错误码    →  在 Controller 上 @ApiResponse 逐接口声明（如 409）

核心原则：全局定义 Schema，按需引用 $ref。DTO 变了，Schema 自动同步。
```

---

## 7. 第五层：API 分组

### 7.1 问题：所有接口混在一起

现在项目的接口都列在同一个 Swagger UI 页面中。当接口数量增长到几十个时，所有人都挤在一个页面找自己需要的接口——订单模块的人要翻过市场模块的二十个接口才能找到订单的。

### 7.2 GroupedOpenApi：按模块分组

在 `SpringDocConfig` 中定义分组，每个分组对应一个模块：

```java
package com.example.demo1.config;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringDocConfig {

    @Bean
    public GroupedOpenApi marketApi() {
        return GroupedOpenApi.builder()
                .group("市场管理")                    // Swagger UI 下拉框中的分组名
                .pathsToMatch("/market/**")          // 匹配 /market 下的所有路径
                .build();
    }

    @Bean
    public GroupedOpenApi orderApi() {
        return GroupedOpenApi.builder()
                .group("订单管理")
                .pathsToMatch("/order/**")
                .build();
    }
}
```

效果：Swagger UI 右上角出现一个下拉选择器：

```
┌─────────────────────────────────────────────────┐
│  OpenAPI definition    [ 市场管理  ▾ ]          │
│                         ├─ 市场管理              │
│                         └─ 订单管理              │
├─────────────────────────────────────────────────┤
│                                                 │
│  POST   /market/addCategory     添加分类         │
│  DELETE /market/deleteCategory  删除分类         │
│  ...                                           │
│                                                 │
└─────────────────────────────────────────────────┘
```

选择"订单管理"后只显示 `/order/**` 的接口，选择"市场管理"只显示 `/market/**`。前端同事只需要看自己对接的模块。

### 7.3 分组策略建议

| 分组方式       | 适用场景                   | 示例                                                |
| -------------- | -------------------------- | --------------------------------------------------- |
| 按模块（推荐） | 多人协作，每人负责不同模块 | `pathsToMatch("/market/**")`                        |
| 按版本         | 多版本 API 共存            | `pathsToMatch("/api/v1/**")`                        |
| 按包路径       | 按 Java 包名分组           | `packagesToScan("com.example.demo1.module.market")` |

`GroupedOpenApi` 支持两种匹配方式：`pathsToMatch`（按 URL 路径）和 `packagesToScan`（按 Java 包）。本项目推荐用 `pathsToMatch`——因为它和 `@RequestMapping` 直接对应，更直观。

### 本节回顾

```
GroupedOpenApi 让 Swagger UI 按模块分开展示

之前：所有接口混在一个长列表里
之后：右上角下拉切换"市场管理"/"订单管理"，每个人只看自己关心的
```

---

## 8. Security 鉴权集成点

### 8.1 场景说明

当前项目尚未集成 Spring Security / JWT 鉴权。但多数企业项目最终会加——当你的接口需要 `Authorization: Bearer <token>` 请求头时，Swagger UI 需要知道怎么传 token，否则所有接口都会因为"未登录"而调不通。

SpringDoc 提供了 Security 配置，可以在 Swagger UI 页面上添加一个"Authorize"按钮，让用户填入 JWT token，之后所有请求自动携带。

### 8.2 配置 SecurityScheme（预留）

```java
package com.example.demo1.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringDocConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        // 定义 JWT Bearer 鉴权方案
        SecurityScheme jwtScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)       // HTTP 鉴权
                .scheme("bearer")                      // Bearer 令牌
                .bearerFormat("JWT")                   // JWT 格式
                .in(SecurityScheme.In.HEADER)          // 放在请求头
                .name("Authorization");                // 请求头名

        return new OpenAPI()
                .info(new Info()
                        .title("demo1 菜品订单系统 API")
                        .description("基于 Spring Boot 4.x + MongoDB 的菜品管理和订单系统")
                        .version("1.0.0"))
                // 注册 SecurityScheme
                .components(new Components().addSecuritySchemes("JWT", jwtScheme))
                // 全局应用（所有接口都需要鉴权）
                .addSecurityItem(new SecurityRequirement().addList("JWT"));
    }
}
```

效果：Swagger UI 右上角出现 🔒 **Authorize** 按钮，点击后弹出对话框：

```
┌─────────────────────────────────────────────┐
│  Available authorizations                   │
│                                             │
│  JWT (http, Bearer)                         │
│  ┌─────────────────────────────────────┐    │
│  │ Value: eyJhbGciOiJIUzI1NiJ9...      │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  [Authorize]  [Close]                       │
└─────────────────────────────────────────────┘
```

填入 token 后点 Authorize，之后的"Try it out"请求都会自动带上 `Authorization: Bearer eyJ...` 请求头。

### 8.3 部分接口不需要鉴权

如果只有部分接口需要 JWT（比如登录接口本身不需要），用 `@SecurityRequirement` 注解精确控制：

```java
// 全局不要求鉴权（去掉 .addSecurityItem 那行），在需要鉴权的接口上加注解

@Operation(summary = "创建订单")
@SecurityRequirement(name = "JWT")  // ← 只有这个接口需要 JWT
@PostMapping("/addOrder")
public OrderResponse create(@Valid @RequestBody CreateOrderDTO dto) {
    return orderService.create(dto);
}
```

### 本节回顾

```
SecurityScheme 配置 = 告诉 Swagger UI "你的接口用 JWT Bearer 鉴权"

1. 定义 SecurityScheme（Bearer + JWT + Authorization 头）
2. 注册到 OpenAPI.components
3. 全局应用或按接口用 @SecurityRequirement 控制

Swagger UI 出现 Authorize 按钮 → 填 token → 所有请求自动带 Authorization 头
```

---

## 9. 速查清单

### 9.1 注解速查

| 注解                   | 位置              | 作用                     | 关键属性                                 |
| ---------------------- | ----------------- | ------------------------ | ---------------------------------------- |
| `@Tag`                 | Controller 类     | 分组名称和描述           | `name`, `description`                    |
| `@Operation`           | Controller 方法   | 接口摘要和描述           | `summary`, `description`                 |
| `@Schema`              | DTO / Entity 字段 | 字段说明和示例           | `description`, `example`, `requiredMode` |
| `@Parameter`           | 方法参数          | 参数说明                 | `description`, `required`, `example`     |
| `@ApiResponse`         | Controller 方法   | 响应状态码描述           | `responseCode`, `description`, `content` |
| `@ApiResponses`        | Controller 方法   | 多个 @ApiResponse 的容器 | `value = {@ApiResponse(...), ...}`       |
| `@SecurityRequirement` | 类 / 方法         | 标记需要鉴权             | `name = "JWT"`                           |
| `@Hidden`              | 类 / 方法 / 字段  | 从文档中隐藏             | —                                        |

### 9.2 配置速查

| 配置方式                    | 作用                 | 代码位置                                    |
| --------------------------- | -------------------- | ------------------------------------------- |
| `OpenAPI.info()`            | API 标题、描述、版本 | `@Bean OpenAPI`                             |
| `GroupedOpenApi`            | 按路径/包分组        | `@Bean GroupedOpenApi`                      |
| `GlobalOperationCustomizer` | 全局添加响应/参数    | `@Bean GlobalOperationCustomizer`           |
| `SecurityScheme`            | 定义鉴权方案         | `OpenAPI.components().addSecuritySchemes()` |

### 9.3 URL 速查

| URL                      | 说明                                                  |
| ------------------------ | ----------------------------------------------------- |
| `/swagger-ui.html`       | Swagger UI 交互式页面                                 |
| `/v3/api-docs`           | OpenAPI 3.0 JSON（默认分组）                          |
| `/v3/api-docs/{group}`   | 指定分组的 OpenAPI JSON（如 `/v3/api-docs/市场管理`） |
| `/swagger-ui/index.html` | Swagger UI 备用路径                                   |

### 9.4 application.yml 配置项速查

```yaml
springdoc:
  swagger-ui:
    path: /swagger-ui.html # Swagger UI 路径（默认值）
    tags-sorter: alpha # 标签排序：alpha=字母序
    operations-sorter: method # 接口排序：method=HTTP方法序
  api-docs:
    path: /v3/api-docs # OpenAPI JSON 路径（默认值）
  show-actuator: false # 是否显示 Actuator 端点
```

### 9.5 常见坑速查

| 陷阱                                     | 后果                          | 正确做法                                                            |
| ---------------------------------------- | ----------------------------- | ------------------------------------------------------------------- |
| SpringDoc 依赖加了但没重启               | Swagger UI 404                | 添加依赖后必须重启应用                                              |
| `@Schema` 放在 `record` 的 getter 上     | 注解无效                      | `record` 的 `@Schema` 放在构造器参数上                              |
| `requiredMode` 手写但与 `@NotBlank` 冲突 | 冗余且可能不一致              | 有 Jakarta Validation 注解时不手写 requiredMode                     |
| `GroupedOpenApi` 路径写错                | 分组为空                      | `pathsToMatch` 的值要与 `@RequestMapping` 一致（含前缀 `/`）        |
| 返回类型用 `Object` 而非具体类型         | Schema 显示为 `object`        | Controller 方法返回具体类型，或通过 `@ApiResponse.content` 手动描述 |
| 文件上传接口忘记 `consumes`              | Swagger UI 里显示成 JSON body | 加 `consumes = MediaType.MULTIPART_FORM_DATA_VALUE`                 |

---

> **延伸阅读：**
>
> - [Knife4j 增强接口文档指南](knife4j-guide.md) —— 增强 UI（离线导出 / 全局参数 / 中文界面），在企业项目中更常用
> - [Spring MVC 指南](spring-mvc-guide.md) —— Controller 注解详解
> - [Spring Boot Validation 指南](spring-validation-guide.md) —— @Valid 校验与 DTO 设计
> - [DTO 指南](dto-guide.md) —— 项目 DTO 设计约定
> - [Spring Security 指南](spring-security-guide.md) —— JWT 鉴权完整方案
> - [SpringDoc 官方文档](https://springdoc.org/)
> - [OpenAPI 3.0 规范](https://spec.openapis.org/oas/v3.0.3)
