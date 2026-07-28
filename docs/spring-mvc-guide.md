# Spring MVC 指南

> 本指南循序渐进介绍 Spring MVC。从"手写 Servlet 的痛点"到"Spring MVC 帮你做了什么"，每步只引入一个新概念。
> 基于 Spring Framework 6.x / Spring Boot 3.x。

---

## 目录

1. [为什么需要 Spring MVC](#1-为什么需要-spring-mvc)
2. [入门三步走](#2-入门三步走)
   - [第一层：最简单的 GET 接口](#21-第一层最简单的-get-接口)
   - [第二层：路径变量和查询参数](#22-第二层路径变量和查询参数)
   - [第三层：接收 JSON 请求体](#23-第三层接收-json-请求体)
3. [请求处理全流程](#3-请求处理全流程)
4. [统一响应与异常处理](#4-统一响应与异常处理)
5. [参数校验与文件上传](#5-参数校验与文件上传)
6. [速查清单](#6-速查清单)

---

## 1. 为什么需要 Spring MVC

### 问题起源

假设你要写一个商品管理系统的后端 API：客户端发 HTTP 请求，服务端返回 JSON 数据。

不用任何框架，只用 Java 标准库（Servlet），代码长这样：

```java
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

// 一个 Servlet 只能处理一类请求
public class ProductListServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        // 手动从请求中提取参数
        String category = req.getParameter("category");

        // 手动拼 JSON（连 Jackson 都没用）
        String json = "[{\"id\":1,\"name\":\"iPhone\"},{\"id\":2,\"name\":\"MacBook\"}]";

        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        PrintWriter out = resp.getWriter();
        out.write(json);
        out.flush();
    }
}
```

还没完——你还需要在 `web.xml` 里注册路由：

```xml
<servlet>
    <servlet-name>productList</servlet-name>
    <servlet-class>com.example.ProductListServlet</servlet-class>
</servlet>
<servlet-mapping>
    <servlet-name>productList</servlet-name>
    <url-pattern>/products</url-pattern>
</servlet-mapping>
```

**问题在哪里？**

- **一个 URL 一个类**：每加一个接口就要新建一个 Servlet 类 + 改 `web.xml`
- **手工作坊式解析**：取参数靠 `req.getParameter()`，取路径变量靠手动切 `req.getRequestURI()`
- **手拼 JSON**：把 Java 对象变成 JSON 字符串、再把请求里的 JSON 变回 Java 对象，全得自己写
- **关注点混杂**：URL 路由、参数提取、JSON 转换、响应格式，全部挤在一个方法里

```
Servlet 时代的工作量

你写的代码量
  │
  │  ████████████████  业务逻辑只占 20%
  │  ████████████████  参数解析 + JSON 转换 + 路由配置占 80%
  │
  └──────────────────→ 功能点数量
```

### Spring MVC 的解决方案

Spring MVC 的核心思想就一句话：**"你只管写业务方法，URL 映射、参数绑定、JSON 转换全部由注解搞定"**。

```
你写的（Servlet 方式）              Spring MVC 帮你做的
───────────────────────────         ────────────────────────
web.xml 里配置路由         ──→      @GetMapping("/products")
req.getParameter("id")     ──→      @RequestParam("id") Long id
手动 Jackson 序列化         ──→      返回对象自动变 JSON
手动 try-catch 异常         ──→      @ExceptionHandler 统一处理
```

> **Spring MVC 把 HTTP 请求的"脏活累活"全部抽象成了注解。你的 Controller 方法从此只关心一件事：接收参数 → 调用业务逻辑 → 返回结果。**

---

## 2. 入门三步走

用一个贯穿场景来演示：**商品管理 API（Product）——查询、创建、搜索商品**。

> 前置知识：`@RestController` 本质是 `@Controller` + `@ResponseBody`，表示"这个类里的方法返回值直接写入 HTTP 响应体"。Spring 容器如何管理 Controller 实例，请参考 [Spring IOC/DI 指南](spring-ioc-di-guide.md)。

### 2.1 第一层：最简单的 GET 接口

需求：客户端访问 `GET /products`，返回所有商品列表。

你只需要写一个方法和一个注解：

```java
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController  // 标记这是一个 REST 控制器
public class ProductController {

    @GetMapping("/products")  // 把 GET /products 映射到这个方法
    public List<Product> listProducts() {
        // 业务逻辑：从数据库查商品
        return List.of(
            new Product(1L, "iPhone 15", 6999.00),
            new Product(2L, "MacBook Pro", 12999.00)
        );
    }
}
```

```java
// Product 使用 record 关键字，自动生成构造器、getter（id()/name()/price()）、equals、hashCode、toString
public record Product(Long id, String name, Double price) {}
```

请求 `GET /products` 后，Spring MVC 自动将 `List<Product>` 转为 JSON 返回：

```json
[
  { "id": 1, "name": "iPhone 15", "price": 6999.0 },
  { "id": 2, "name": "MacBook Pro", "price": 12999.0 }
]
```

**发生了什么？** 你只声明了 `@GetMapping("/products")`，Spring MVC 帮你完成了：URL 匹配 → 方法调用 → 返回值转 JSON。对比 Servlet 时代省掉了 `web.xml` 配置、`req.getParameter()`、手动 JSON 序列化。

> **关键进步**：URL 和方法的映射从"外部 XML 配置"变成了"方法上的一个注解"。一个类可以包含多个方法，每个方法对应一个 URL。

---

### 2.2 第二层：路径变量和查询参数

需求升级：

- `GET /products/1` → 返回 ID 为 1 的商品
- `GET /products?category=phone` → 返回手机分类下的商品

这引入两个新注解：**`@PathVariable`**（从 URL 路径中取值）和 **`@RequestParam`**（从 `?` 后面的查询参数中取值）。

```java
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
public class ProductController {

    // 之前的第一层接口仍然保留
    @GetMapping("/products")
    public List<Product> listProducts() {
        return List.of(
            new Product(1L, "iPhone 15", 6999.00),
            new Product(2L, "MacBook Pro", 12999.00)
        );
    }

    // 第二层新增：路径变量 —— URL 中的 {id} 自动绑定到方法参数
    @GetMapping("/products/{id}")
    public Product getById(@PathVariable("id") Long id) {
        // 根据 ID 查数据库（此处简化为直接返回）
        return new Product(id, "iPhone 15", 6999.00);
    }

    // 第二层新增：查询参数 —— ? 后面的 category=xxx 自动绑定
    @GetMapping("/products/search")
    public List<Product> search(@RequestParam("category") String category) {
        // 根据分类过滤
        if ("phone".equals(category)) {
            return List.of(new Product(1L, "iPhone 15", 6999.00));
        }
        return List.of();
    }
}
```

```
URL 结构拆解

GET /products/1?category=phone
    ──────── ─            ─────
       ↑     ↑              ↑
   基础路径  @PathVariable   @RequestParam
           ("id") → 1       ("category") → "phone"
```

> **注意**：`/products/search` 必须写在 `/products/{id}` **之前**，否则 Spring 会把 `search` 当成 `{id}` 的值去匹配。Spring MVC 按方法定义顺序匹配路由，更具体的路径要放在更前面。

`@PathVariable` 的名称如果和方法参数名一致，可以省略注解里的值：

```java
// 下面两种写法等价
@GetMapping("/products/{id}")
public Product getById(@PathVariable("id") Long id) { ... }

@GetMapping("/products/{id}")
public Product getById(@PathVariable Long id) { ... }  // 参数名和 {id} 一致时可省略
```

**本节回顾**：从"固定 URL"到"带变量的 URL"，你只多写了两个注解——`@PathVariable` 拿路径段，`@RequestParam` 拿查询字符串。

---

### 2.3 第三层：接收 JSON 请求体

需求再升级：客户端通过 `POST /products` 创建商品，请求体是 JSON：

```json
{
  "name": "AirPods Pro",
  "price": 1999.0
}
```

这里引入 **`@PostMapping`** 和 **`@RequestBody`**。前者指定 HTTP 方法为 POST，后者告诉 Spring："把请求体的 JSON 自动转成我指定的 Java 对象"。

```java
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductController {

    // ...前面的方法省略...

    // 第三层新增：POST + 请求体自动反序列化
    @PostMapping("/products")
    public Product createProduct(@RequestBody Product product) {
        // product 已经从 JSON 自动转换好了，直接使用
        System.out.println("收到商品: " + product.name() + ", 价格: " + product.price());

        // 实际项目中：productService.save(product);
        // record 不可变，通过创建新实例来设置 ID
        return new Product(3L, product.name(), product.price());  // 返回的对象自动序列化为 JSON
    }
}
```

```
POST /products 请求处理流程

客户端发 JSON                            Controller 收到
══════════════                           ═══════════════
{                                         Product {
  "name": "AirPods Pro",     ──→           id: null,
  "price": 1999.00                          name: "AirPods Pro",
}                                           price: 1999.00
                                          }

Controller 返回 Product               客户端收到 JSON
══════════════                           ═══════════════
Product {                                {
  id: 3,                    ──→           "id": 3,
  name: "AirPods Pro",                    "name": "AirPods Pro",
  price: 1999.00                          "price": 1999.00
}                                         }
```

> **关键进步**：`@RequestBody` 消除了手写 `Jackson ObjectMapper.readValue()` 的环节。你不再需要关心 JSON 怎么解析——声明参数类型，Spring 自动完成。

### 本节回顾

```
三层递进总结

第一层 @GetMapping            →  固定 URL，无参数，返回 JSON
第二层 + @PathVariable        →  URL 中的变量自动绑定到方法参数
      + @RequestParam         →  ? 后面的查询参数自动绑定
第三层 + @PostMapping         →  指定 HTTP 方法为 POST
      + @RequestBody          →  请求体 JSON 自动转 Java 对象

每一层你只比上一层多学一个概念，场景保持一致。
```

---

## 3. 请求处理全流程

你已经知道怎么用注解了，现在来看看背后的"调度中心"是怎么工作的。

Spring MVC 的核心是 **DispatcherServlet**——一个前端控制器（Front Controller），所有 HTTP 请求都先经过它，再由它分发给对应的 Controller 方法。

```
HTTP 请求处理流程

客户端
  │
  │  GET /products/1
  ▼
┌─────────────────────────────────────────────────────────┐
│ DispatcherServlet（前端控制器——所有请求的统一入口）        │
│                                                         │
│  ① 根据 URL 找到处理器                                   │
│     HandlerMapping                                      │
│     "GET /products/1" → ProductController.getById()     │
│                                                         │
│  ② 调用处理器                                           │
│     HandlerAdapter                                      │
│     把 @PathVariable("id") 解析出来 → 传入方法            │
│                                                         │
│  ③ 处理方法返回值                                       │
│     HttpMessageConverter（Jackson）                      │
│     把 Product 对象 → JSON 字符串                        │
│                                                         │
│  ④ 返回 HTTP 响应                                       │
│     200 OK + JSON body                                  │
└─────────────────────────────────────────────────────────┘
  │
  ▼
客户端收到 JSON
```

**每一步对应你写过的代码：**

| 步骤       | 组件                   | 你写的什么                                                         |
| ---------- | ---------------------- | ------------------------------------------------------------------ |
| ① 找处理器 | `HandlerMapping`       | `@GetMapping("/products/{id}")`——告诉 Spring 哪个 URL 对应哪个方法 |
| ② 调处理器 | `HandlerAdapter`       | `@PathVariable Long id`——告诉 Spring 参数从 URL 哪里取             |
| ③ 转 JSON  | `HttpMessageConverter` | 方法返回 `Product` 对象——自动序列化，你什么都没写                  |
| ④ 发响应   | `DispatcherServlet`    | 你什么都没写——框架自动构造 HTTP 响应                               |

> **DispatcherServlet 就像公司的前台**：所有访客（HTTP 请求）先到前台，前台根据访客要找的人（URL），通知对应员工（Controller 方法），员工处理完后把结果交给前台，前台统一送出。

---

## 4. 统一响应与异常处理

前面每个方法的返回值各不相同：有的返回 `Product`，有的返回 `List<Product>`。真实项目中，你通常希望所有接口有统一的响应格式：

```json
{
    "code": 200,
    "message": "success",
    "data": { ... }
}
```

以及统一的异常处理——不管哪里出错，都返回一致的结构，而不是把堆栈信息暴露给客户端。

### 为什么需要统一格式

没有统一格式时，你的 Controller 可能长这样：

```java
// 每个方法都要手动包装返回值——繁琐且容易不一致
@GetMapping("/products/{id}")
public Map<String, Object> getById(@PathVariable Long id) {
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("code", 200);
    result.put("message", "success");
    result.put("data", productService.findById(id));
    return result;
}
```

每个方法都写一遍 `code`、`message`、`data` 的包装代码？出错了还要在每个方法里 `try-catch`？这显然不是好方案。

### 统一响应包装：ResponseBodyAdvice

**`ResponseBodyAdvice`** 可以在 Controller 方法返回后、写入响应体之前，拦截并修改返回值。结合 `@RestControllerAdvice`，对所有 Controller 生效。

```java
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice  // 对所有 @RestController 生效
public class GlobalResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true;  // 所有返回值都包装
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        // 避免重复包装：如果已经是标准格式，直接返回
        if (body instanceof Map && ((Map<?, ?>) body).containsKey("code")) {
            return body;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("message", "success");
        result.put("data", body);
        return result;
    }
}
```

现在你的 Controller 方法又可以恢复清爽了：

```java
@GetMapping("/products/{id}")
public Product getById(@PathVariable Long id) {
    return productService.findById(id);  // 返回 Product，框架自动包装成 {code, message, data}
}
```

### 统一异常处理：@ExceptionHandler

**`@ExceptionHandler`** 标注的方法会在指定异常抛出时自动调用。同样配合 `@RestControllerAdvice` 全局生效。

```java
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 处理"资源未找到"异常
    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            ProductNotFoundException ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.NOT_FOUND, request, ex.getMessage());
    }

    // 处理参数校验失败异常
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Validation failed");
        return buildResponse(HttpStatus.BAD_REQUEST, request, message);
    }

    // 兜底：处理所有未被上面捕获的异常
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(
            Exception ex, HttpServletRequest request) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, request,
                ex.getClass().getSimpleName() + ": " + ex.getMessage());
    }

    private ResponseEntity<Map<String, Object>> buildResponse(
            HttpStatus status, HttpServletRequest request, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", status.value());
        body.put("timestamp", Instant.now().toString());
        body.put("path", request.getRequestURI());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
```

```
统一异常处理流程

Controller 方法抛异常
  │
  ▼
@ExceptionHandler 拦截
  │
  ├── ProductNotFoundException  →  404 + {"code":404, "message":"商品不存在"}
  ├── MethodArgumentNotValid..  →  400 + {"code":400, "message":"name: 不能为空"}
  └── Exception（兜底）          →  500 + {"code":500, "message":"RuntimeException: ..."}
```

> **关键点**：`@ExceptionHandler` 方法可以返回 `ResponseEntity`，让你精确控制 HTTP 状态码和响应体。更具体的异常处理器优先匹配——`ProductNotFoundException` 比 `Exception` 更具体，会优先使用。

### 本节回顾

```
统一格式的两种武器

ResponseBodyAdvice         @ExceptionHandler
──────────────────         ──────────────────
在正常返回后拦截            在异常抛出时拦截
包装成统一成功格式          包装成统一错误格式
{code, message, data}      {code, timestamp, path, message}

两者配合 → 无论成败，客户端收到的格式始终一致
```

---

## 5. 参数校验与文件上传

### 5.1 用 @Valid 校验请求体

前面的 `@RequestBody` 解决了 JSON → Java 对象的转换，但**怎么保证客户端传来的数据合法**？比如商品名称不能为空、价格必须大于 0。

Spring MVC 集成了 **Bean Validation**（Jakarta Validation），只需在 DTO 字段上加注解，然后在 Controller 参数上加 `@Valid`：

```java
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// DTO：专门接收请求数据的对象，用 record 关键字更简洁（自动生成构造器、getter、equals、hashCode）
public record CreateProductDTO(
    @NotBlank(message = "商品名称不能为空")   // 字符串不能为 null 或空串
    String name,

    @NotNull(message = "价格不能为空")
    @Positive(message = "价格必须大于 0")      // 必须为正数
    Double price
) {}
```

```java
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductController {

    @PostMapping("/products")
    public Product createProduct(@Valid @RequestBody CreateProductDTO dto) {
        // 如果校验不通过，此处根本不会执行——框架直接返回 400
        return productService.create(dto);
    }
}
```

```
校验流程

客户端发请求                  Spring MVC 行为
══════════════                ═══════════════
{ "name": "", "price": -5 }
        │
        ▼
  @Valid 触发校验
        │
        ▼
  name 不满足 @NotBlank  ──→  抛出 MethodArgumentNotValidException
                              @ExceptionHandler 拦截 → 返回 400
                              {"message": "name: 商品名称不能为空; price: 价格必须大于 0"}
```

**常用校验注解**：

| 注解              | 作用                              | 适用类型     |
| ----------------- | --------------------------------- | ------------ |
| `@NotNull`        | 不能为 null                       | 任意类型     |
| `@NotBlank`       | 不能为 null 且去除空格后长度 > 0  | 字符串       |
| `@NotEmpty`       | 不能为 null 且集合/字符串长度 > 0 | 字符串、集合 |
| `@Positive`       | 必须为正数（> 0）                 | 数字         |
| `@Size(min, max)` | 长度或大小在指定范围内            | 字符串、集合 |
| `@Email`          | 必须符合邮箱格式                  | 字符串       |

> 校验失败时抛出的 `MethodArgumentNotValidException` 需要在 `@ExceptionHandler` 中处理（见第 4 节）。

### 5.2 文件上传

Spring MVC 处理文件上传也很简单，使用 **`MultipartFile`** 接收文件，配合 `@RequestParam`：

```java
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@RestController
public class ProductController {

    @PostMapping(value = "/products/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            // 获取原始文件名和扩展名
            String originalFilename = file.getOriginalFilename();
            String ext = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : ".jpg";

            // 生成唯一文件名
            String filename = System.currentTimeMillis() + ext;

            // 保存到磁盘
            File dest = new File("uploads/" + filename);
            file.transferTo(dest);

            return Map.of("url", "/uploads/" + filename);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }
}
```

> **`consumes = MediaType.MULTIPART_FORM_DATA_VALUE`** 是必需的：它告诉 Spring 这个方法只处理 `multipart/form-data` 类型的请求（即文件上传表单）。不加这个的话，普通 JSON 请求也可能误匹配到此方法。

**`MultipartFile` 常用方法**：

| 方法                    | 作用                                   |
| ----------------------- | -------------------------------------- |
| `getOriginalFilename()` | 获取上传文件的原始文件名               |
| `getSize()`             | 获取文件大小（字节）                   |
| `getContentType()`      | 获取文件的 MIME 类型（如 `image/png`） |
| `transferTo(File)`      | 将文件保存到磁盘指定路径               |
| `getBytes()`            | 获取文件的全部字节内容                 |

### 本节回顾

```
@Valid + MultipartFile

@Valid              →  声明式校验，DTO 字段上加注解，参数前加 @Valid
MultipartFile       →  接收上传文件，transferTo() 保存到磁盘
异常处理            →  校验失败抛 MethodArgumentNotValidException
                      @ExceptionHandler 统一拦截并返回 400
```

---

## 6. 速查清单

### 6.1 核心注解速查

| 注解                       | 作用                                                | 示例                               |
| -------------------------- | --------------------------------------------------- | ---------------------------------- |
| `@RestController`          | 标记 REST 控制器，= `@Controller` + `@ResponseBody` | 类上                               |
| `@RequestMapping("/path")` | 类级别的 URL 前缀                                   | `@RequestMapping("/products")`     |
| `@GetMapping("/path")`     | 映射 GET 请求                                       | `@GetMapping("/products/{id}")`    |
| `@PostMapping("/path")`    | 映射 POST 请求                                      | `@PostMapping("/products")`        |
| `@PutMapping("/path")`     | 映射 PUT 请求                                       | `@PutMapping("/products/{id}")`    |
| `@DeleteMapping("/path")`  | 映射 DELETE 请求                                    | `@DeleteMapping("/products/{id}")` |

### 6.2 参数绑定速查

| 注解                   | 取值位置         | 示例 URL                    | 示例代码                                |
| ---------------------- | ---------------- | --------------------------- | --------------------------------------- |
| `@PathVariable`        | URL 路径段       | `/products/1`               | `@PathVariable Long id`                 |
| `@RequestParam`        | `?` 后的查询参数 | `/products?cat=phone`       | `@RequestParam("cat") String cat`       |
| `@RequestBody`         | 请求体（JSON）   | POST body: `{"name":"..."}` | `@RequestBody Product product`          |
| `@RequestParam` (文件) | multipart 表单   | 文件上传表单                | `@RequestParam("file") MultipartFile f` |

### 6.3 响应处理速查

| 方式                 | 适用场景                 | 示例                                               |
| -------------------- | ------------------------ | -------------------------------------------------- |
| 直接返回对象         | 自动 JSON 序列化         | `return product;`                                  |
| `ResponseEntity<T>`  | 需要自定义状态码或响应头 | `return ResponseEntity.status(201).body(product);` |
| `ResponseBodyAdvice` | 全局统一包装响应格式     | 对所有 Controller 自动包装 `{code, data}`          |
| `@ExceptionHandler`  | 全局统一异常处理         | 不同异常返回不同状态码和错误信息                   |

### 6.4 请求处理流程速查

```
浏览器 → DispatcherServlet → HandlerMapping（找谁处理）
                           → HandlerAdapter（调方法、绑参数）
                           → Controller.method()（你写的业务逻辑）
                           → HttpMessageConverter（返回值转 JSON）
                           → 浏览器收到响应
```

### 6.5 Servlet vs Spring MVC 对照

```
Servlet 时代                         Spring MVC 时代
═══════════════                      ═══════════════
web.xml 配置路由                      @GetMapping 一行搞定
req.getParameter() 手动取值           @RequestParam 自动绑定
req.getRequestURI() 手动切路径         @PathVariable 自动提取
new ObjectMapper().readValue()        @RequestBody 自动反序列化
resp.getWriter().write(json)          return 对象，自动序列化
每个 URL 一个 Servlet 类              一个 Controller 类包含多个方法
try-catch 写满每个方法                 @ExceptionHandler 一处定义，全局生效
```
