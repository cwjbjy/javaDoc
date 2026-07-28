# Spring Boot 统一异常处理指南

> 本指南循序渐进介绍 Spring Boot 统一异常处理机制。从局部拦截到全局处理，再到自定义异常体系，每步只引入一个新概念，同一"在线书店"场景贯穿全文。
>
> 适用版本：Spring Boot 3.5+，Java 17+（`jakarta.*` 命名空间，原 `javax.*` 已迁移）

---

## 目录

1. [第一层：局部异常处理 @ExceptionHandler](#1-第一层局部异常处理-exceptionhandler)
2. [第二层：全局异常处理 @RestControllerAdvice](#2-第二层全局异常处理-restcontrolleradvice)
3. [自定义异常体系](#3-自定义异常体系)
4. [常见内置异常处理](#4-常见内置异常处理)
5. [ResponseBodyAdvice：自动包装成功响应](#5-responsebodyadvice自动包装成功响应)
6. [速查清单](#6-速查清单)

---

## 1. 第一层：局部异常处理 @ExceptionHandler

### 问题：散落的 try-catch

书店应用里有两个接口——查书和查评论，它们都需要处理"书不存在"的情况：

```java
// BAD：每个方法自己 try-catch，处理逻辑完全相同却重复两遍
// 注意：bookService / reviewService 省略注入声明，焦点在 try-catch 结构上
@RestController
@RequestMapping("/books")
public class BookController {

    @GetMapping("/{id}")
    public ResponseEntity<Book> getBook(@PathVariable Long id) {
        try {
            Book book = bookService.findById(id);     // ← Service 层抛 BookNotFoundException
            return ResponseEntity.ok(book);
        } catch (BookNotFoundException e) {
            return ResponseEntity.status(404).body(null); // ← 重复
        }
    }

    @GetMapping("/{id}/reviews")
    public ResponseEntity<List<Review>> getReviews(@PathVariable Long id) {
        try {
            List<Review> reviews = reviewService.findByBookId(id); // ← Service 层抛
            return ResponseEntity.ok(reviews);
        } catch (BookNotFoundException e) {
            return ResponseEntity.status(404).body(null); // ← 完全相同的 catch，复制粘贴
        }
    }
}
```

两个方法里出现了完全相同的 catch 块。错误信息格式要改时，每个方法都要改一遍。

上例中的 `BookNotFoundException` 定义非常简单——就是一个普通的自定义异常：

```java
// 最简单的自定义异常，继承 RuntimeException 即可
public class BookNotFoundException extends RuntimeException {
    public BookNotFoundException(Long id) {
        super("Book with id " + id + " not found");
    }
}
```

Service 层在查不到数据时抛出它：

```java
@Service
public class BookService {
    public Book findById(Long id) {
        return bookRepository.findById(id)
            .orElseThrow(() -> new BookNotFoundException(id));
    }
}
```

现在来看看 `@ExceptionHandler` 如何消除 Controller 中的重复。

### 用 @ExceptionHandler 消除重复

`@ExceptionHandler` 可以在 Controller 内部声明一个专门的异常处理方法。同一 Controller 内所有方法抛出的指定异常，都会被它统一拦截：

```java
// GOOD：异常由 Service 层抛出，Controller 不用 try-catch，全部交给 @ExceptionHandler
@RestController
@RequestMapping("/books")
public class BookController {

    private final BookService bookService;
    private final ReviewService reviewService;

    // 构造器注入（Spring 4.3+ 单构造器场景可省略 @Autowired）
    public BookController(BookService bookService, ReviewService reviewService) {
        this.bookService = bookService;
        this.reviewService = reviewService;
    }

    @GetMapping("/{id}")
    public Book getBook(@PathVariable Long id) {
        return bookService.findById(id);    // Service 层抛异常 → 自动路由到下面 handler
    }

    @GetMapping("/{id}/reviews")
    public List<Review> getReviews(@PathVariable Long id) {
        return reviewService.findByBookId(id); // 同上，Service 抛出 → 自动路由
    }

    // 在同一个 Controller 内，专门处理 BookNotFoundException
    @ExceptionHandler(BookNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> handleBookNotFound(BookNotFoundException e) {
        return Map.of("error", e.getMessage());
        // 两个方法抛出的 BookNotFoundException 都落这里，改一处全生效
    }
}
```

`@ExceptionHandler` 方法的参数是要拦截的异常类型，Spring 在该 Controller 范围内自动路由匹配的异常。

### 本节回顾

```
请求 → Controller 方法 → 抛 BookNotFoundException
                                  ↓
               同 Controller 内的 @ExceptionHandler
                                  ↓
                           返回 404 错误响应
```

> **局限**：`@ExceptionHandler` 只对当前 Controller 生效。如果 50 个 Controller 都要处理 `BookNotFoundException`，还是要复制 50 份。→ 第二层解决这个问题。

---

## 2. 第二层：全局异常处理 @RestControllerAdvice

### 新概念：@RestControllerAdvice

`@RestControllerAdvice` = `@ControllerAdvice` + `@ResponseBody`。

- **`@ControllerAdvice`**：让这个类能"增强"所有 Controller（AOP 横切关注点，无需修改每个 Controller）
- **`@ResponseBody`**：让返回值自动序列化为 JSON

把所有 `@ExceptionHandler` 方法集中到这个类里，一次性覆盖整个应用：

```java
// 全局异常处理器：覆盖所有 Controller，写一次到处生效
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 处理所有 Controller 抛出的 BookNotFoundException
    @ExceptionHandler(BookNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleBookNotFound(BookNotFoundException e) {
        return buildResponse(HttpStatus.NOT_FOUND, e.getMessage());
    }

    // 处理所有 Controller 抛出的 IllegalArgumentException
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        return buildResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // 统一构建方法：一处定义数据结构，所有 handler 复用
    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code",    status.value());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
```

Controller 完全不需要关心异常处理：

```java
// Controller 只专注业务逻辑，异常直接往上抛
@RestController
@RequestMapping("/books")
public class BookController {

    @GetMapping("/{id}")
    public Book getBook(@PathVariable Long id) {
        return bookService.findById(id);    // 异常自动被 GlobalExceptionHandler 捕获
    }
}
```

### 异常路由优先级

Controller 内的 `@ExceptionHandler` 和全局 `@RestControllerAdvice` 都能处理同一异常时，**Controller 内的优先**：

```
抛出异常
    ↓
当前 Controller 内有 @ExceptionHandler？
    ├─ YES → 局部处理（优先级更高）
    └─ NO  → 找 @RestControllerAdvice
                 ↓
             有精确匹配的 @ExceptionHandler？
                 ├─ YES → 全局处理
                 └─ NO  → 向父类匹配 → 直到 Exception 兜底 → Spring 默认处理（500）
```

### 本节回顾

```
BookController    OrderController    UserController
      │                 │                 │
      └─────────────────┴─────────────────┘
                        │  任意异常上浮
                        ▼
             GlobalExceptionHandler
             (@RestControllerAdvice)
                        │
               统一格式响应给客户端
```

---

## 3. 自定义异常体系

> **演进预告**：前面直接用 `BookNotFoundException extends RuntimeException`，简单场景够用。但业务复杂后，每种异常写一个 handler 会爆炸，且错误码格式参差不齐。本节引入一套结构化的异常体系：所有业务异常共用一个基类、统一定义错误码，只需要**一个 handler 方法**处理全部业务异常。

### 继承谁？

Java 异常的继承树：

```
Throwable
├── Error（JVM 级别，如 OutOfMemoryError，不捕获处理）
└── Exception
    ├── IOException（受检异常：必须 try-catch 或 throws 声明）
    └── RuntimeException（非受检异常：不强制处理，自动向上传播）
        ├── NullPointerException
        ├── IllegalArgumentException
        └── ← 自定义业务异常推荐继承这里
```

**Web 应用推荐继承 `RuntimeException`**：不需要在方法签名写 `throws`，异常自然传播到全局处理器。

### 设计基础异常类

业务异常需要携带两个信息：**错误码**（供程序判断分支）和 **HTTP 状态码**（供处理器设置响应状态）：

```java
import org.springframework.http.HttpStatus;

// 所有业务异常的公共基类
public class BusinessException extends RuntimeException {

    private final String     code;       // 业务错误码，如 "BOOK_NOT_FOUND"
    private final HttpStatus httpStatus; // 对应的 HTTP 状态码

    public BusinessException(String code, String message, HttpStatus httpStatus) {
        super(message);
        this.code       = code;
        this.httpStatus = httpStatus;
    }

    public String     getCode()      { return code; }
    public HttpStatus getHttpStatus(){ return httpStatus; }
}
```

### 按业务场景定义子类

每种具体业务场景继承基类，在构造方法里预设好错误码和状态码：

```java
// 资源不存在 → 404（前面 BookNotFoundException 的结构化版本）
public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String resourceName, Object id) {
        super(
            resourceName.toUpperCase() + "_NOT_FOUND",
            resourceName + " with id " + id + " not found",
            HttpStatus.NOT_FOUND
        );
    }
}

// 业务规则冲突（如库存不足、重复下单）→ 409
public class BusinessConflictException extends BusinessException {
    public BusinessConflictException(String code, String message) {
        super(code, message, HttpStatus.CONFLICT);
    }
}

// 无权限操作 → 403
public class ForbiddenException extends BusinessException {
    public ForbiddenException(String message) {
        super("FORBIDDEN", message, HttpStatus.FORBIDDEN);
    }
}
```

### 全局处理器只需一个 handler

有了统一的基类，所有业务异常的处理逻辑合并为一个 handler：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 一个 handler 覆盖 BusinessException 及其所有子类
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusinessException(BusinessException e) {
        return buildResponse(e.getHttpStatus(), e.getCode(), e.getMessage());
    }

    // §2 版本：code 取 HTTP 状态码数值
    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code",    status.value());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    // 业务异常版本：code 由异常对象携带（如 "BOOK_NOT_FOUND"）
    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code",    code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
```

Service 层直接抛，不再关心 HTTP 细节：

```java
@Service
public class BookService {

    public Book findById(Long id) {
        return bookRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Book", id));
        // → 抛出后由 GlobalExceptionHandler 捕获
        // → 响应：HTTP 404 + {"code":"BOOK_NOT_FOUND","message":"Book with id 99 not found"}
    }

    public void placeOrder(Long bookId, int quantity) {
        Book book = findById(bookId);
        if (book.getStock() < quantity) {
            throw new BusinessConflictException(
                "INSUFFICIENT_STOCK",
                "库存不足，当前库存：" + book.getStock()
            );
            // → 响应：HTTP 409 + {"code":"INSUFFICIENT_STOCK","message":"库存不足，当前库存：3"}
        }
    }
}
```

### 本节回顾

```
BusinessException（基类：code + httpStatus）
├── ResourceNotFoundException    → 404
├── BusinessConflictException    → 409
└── ForbiddenException           → 403
            │
            │  全部被一个 handler 捕获
            ▼
GlobalExceptionHandler.handleBusinessException()
            │
   ResponseEntity.status(e.getHttpStatus()).body(...)
```

---

## 4. 常见内置异常处理

框架本身会抛出多种内置异常，需要在全局处理器里逐一处理，否则客户端收到的是 Spring 默认的不友好格式。

> **请求处理的两个阶段**：同为 400，`HttpMessageNotReadableException` 和 `MethodArgumentNotValidException` 容易混淆，但它们触发时机完全不同：
>
> ```
> 请求进入
>     │
>     ▼
> ① 反序列化：JSON → DTO 对象
>     ├─ JSON 格式非法 / 类型无法转换 → HttpMessageNotReadableException（§4.3）
>     └─ 反序列化成功，得到 DTO 对象
>           │
>           ▼
> ② 校验：@Valid 检查 DTO 字段
>     ├─ 校验失败 → MethodArgumentNotValidException（§4.1）
>     └─ 校验通过 → 进入 Controller 方法
> ```
>
> 关键区别：前者拿不到 DTO（JSON 都没解析出来），只能返回"格式错误"；后者拿到了 DTO，可以定位到具体哪个字段不符、逐字段返回错误详情。

### 4.1 参数校验失败：MethodArgumentNotValidException

Controller 方法参数加了 `@Valid` 注解，Bean Validation 校验不通过时抛出此异常。

触发端（Controller 必须有 `@Valid`）：

```java
// DTO：字段上挂校验注解（Spring Boot 3.x 使用 jakarta.validation.*）
public class CreateBookRequest {

    @NotBlank(message = "书名不能为空")     // jakarta.validation.constraints.NotBlank
    private String title;

    @Min(value = 0, message = "价格不能为负数") // jakarta.validation.constraints.Min
    private BigDecimal price;

    // getter / setter ...
}

// Controller：@Valid 触发校验 ← 不加这个注解，校验不会执行
@PostMapping("/books")
public Book createBook(@Valid @RequestBody CreateBookRequest request) { // jakarta.validation.Valid
    return bookService.create(request);
}
```

> **提示**：`@Valid` 触发校验 → 失败抛 `MethodArgumentNotValidException` → 落入下面的 handler 返回 400。`@NotBlank`、`@Min` 等校验注解体系是独立主题，这里只需记住链路。

全局处理器提取字段错误（沿用 §2 的 `buildResponse` 模式）：

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
    String errorMsg = e.getBindingResult()
        .getFieldErrors()
        .stream()
        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
        .collect(Collectors.joining("; "));
    return buildResponse(HttpStatus.BAD_REQUEST, errorMsg);
}
```

响应示例：

```json
{
  "code": 400,
  "message": "title: 书名不能为空; price: 价格不能为负数"
}
```

### 4.2 路由不存在：NoHandlerFoundException / NoResourceFoundException

Spring Boot 3.5 中，404 场景分两类：

- **`NoHandlerFoundException`**：请求路径没有匹配的 `@RequestMapping` 处理器
- **`NoResourceFoundException`**（Spring Framework 6.2+ 新增）：路径命中了 `ResourceHttpRequestHandler`，但对应静态资源文件不存在

只需一条配置即可让两者都抛出异常（`spring.mvc.throw-exception-if-no-handler-found` 在 Spring Boot 3.4 已废弃，3.5 无需再配）：

```yaml
# application.yml
spring:
  web:
    resources:
      add-mappings: false # 关闭静态资源默认映射，让 404 场景抛出异常
```

全局处理器同时捕获两者：

```java
// 请求路径无匹配 Handler（路由不存在）
@ExceptionHandler(NoHandlerFoundException.class)
public ResponseEntity<Map<String, Object>> handleNoHandler(NoHandlerFoundException e) {
    return buildResponse(HttpStatus.NOT_FOUND,
        "接口 [" + e.getHttpMethod() + " " + e.getRequestURL() + "] 不存在");
}

// 静态资源路径无匹配文件（Spring Framework 6.2+）
@ExceptionHandler(NoResourceFoundException.class)
public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException e) {
    return buildResponse(HttpStatus.NOT_FOUND,
        "资源 [" + e.getHttpMethod() + " " + e.getResourcePath() + "] 不存在");
}
```

### 4.3 反序列化失败：HttpMessageNotReadableException

请求体 JSON 无法解析为目标 DTO 类型时抛出，发生在 `@Valid` 校验**之前**（见本节开头流程图）。常见触发场景：

- JSON 语法错误（缺少引号、逗号、花括号不匹配）
- 字段类型不匹配（如 `"price": "abc"` 期望 `BigDecimal`）
- 请求体为空或 `Content-Type` 不正确

> **与 §4.1 的区别**：此异常拿不到 DTO 对象，无法给出字段级错误详情，只能返回"请求体格式错误"。

```java
@ExceptionHandler(HttpMessageNotReadableException.class)
public ResponseEntity<Map<String, Object>> handleMessageNotReadable(HttpMessageNotReadableException e) {
    return buildResponse(HttpStatus.BAD_REQUEST,
        "请求体格式错误，请检查 JSON 格式");
    // 注意：不要把 e.getMessage() 直接返给客户端，可能暴露内部实现细节
}
```

### 本节回顾

```
POST /books（@Valid 校验失败）
    → MethodArgumentNotValidException → 400 + 字段错误列表

GET /not-exist-path（无匹配 Handler）
    → NoHandlerFoundException → 404 + 路由不存在提示

GET /static/missing.png（静态资源文件不存在，Spring Framework 6.2+）
    → NoResourceFoundException → 404 + 资源不存在提示

POST /books（JSON 格式错误）
    → HttpMessageNotReadableException → 400 + 格式错误提示
```

---

## 5. ResponseBodyAdvice：自动包装成功响应

### 问题：成功/失败格式不一致

§2~§4 已统一错误响应格式（`buildResponse()` → `{code, message, timestamp, path}`），但成功响应仍是 Controller 返回的裸对象：

| 场景               | 响应                                                          |
| ------------------ | ------------------------------------------------------------- |
| 成功 GET /books/1  | `{"id":1,"title":"Java 编程思想"}`                            |
| 失败 GET /books/99 | `{"code":404,"message":"...","timestamp":"...","path":"..."}` |

前端仍需根据 HTTP 状态码分两套逻辑处理。`ResponseBodyAdvice` 解决这个问题——在成功响应写出前自动包一层统一格式。

### ResponseBodyAdvice 实现

```java
@RestControllerAdvice
public class GlobalResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        //只有被 @RestController 注解标注的类（即纯 JSON 接口），才需要进入 beforeBodyWrite 进行包装
        return returnType.getContainingClass().isAnnotationPresent(RestController.class);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
            MediaType selectedContentType, Class selectedConverterType,
            ServerHttpRequest request, ServerHttpResponse response) {

        // 已是 Map 且含 code 字段 → 异常处理器返回的，不重复包装
        if (body instanceof Map && ((Map<?, ?>) body).containsKey("code")) {
            return body;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code",    200);
        result.put("message", "success");
        result.put("data",    body);
        return result;
    }
}
```

> **`supports()` 白名单**：建议限定 `@RestController`，避免包装 Swagger JSON、Actuator 健康检查等内部端点。
>
> **String 返回值陷阱**：若 Controller 返回 `String`，`beforeBodyWrite` 中包装为 `Map` 会导致类型转换异常（`StringHttpMessageConverter` 无法处理 `Map` 对象）。解决方案：① 始终返回对象而非 String ② 在 Advice 中单独处理 String 类型，手动用 Jackson 序列化。

Controller 回归纯粹——只管业务：

```java
@RestController
@RequestMapping("/books")
public class BookController {

    @GetMapping("/{id}")
    public Book getBook(@PathVariable Long id) {
        return bookService.findById(id);  // 自动包装为统一格式
    }

    @PostMapping
    public Book createBook(@Valid @RequestBody CreateBookRequest request) {
        return bookService.create(request);  // 自动包装
    }
}
```

### 本节回顾

```
                    前端
                     │
          ┌──────────┴──────────┐
          │                     │
     成功响应                 失败响应
  ResponseBodyAdvice      GlobalExceptionHandler
  自动包装 Map             buildResponse() 手动构建
          │                     │
          └──────────┬──────────┘
                     │
     { code, message, ... }
     （格式统一，code 字段标识成功/失败）
```

---

## 6. 速查清单

### 6.1 常见内置异常速查

```
┌─────────────────────────────────────────┬──────────────────────────────────┬────────┐
│ 异常类                                   │ 触发场景                          │ 状态码 │
├─────────────────────────────────────────┼──────────────────────────────────┼────────┤
│ MethodArgumentNotValidException         │ @Valid 对 @RequestBody 校验失败   │  400   │
│ ConstraintViolationException            │ @Validated 对方法参数校验失败     │  400   │
│ HttpMessageNotReadableException         │ JSON 格式非法 / 类型不匹配        │  400   │
│ MissingServletRequestParameterException │ 缺少必填 @RequestParam            │  400   │
│ MethodArgumentTypeMismatchException     │ 路径变量类型不匹配（如传 "abc" 给Long）│ 400 │
│ NoHandlerFoundException                 │ 路由不存在（无匹配 Handler）       │  404   │
│ NoResourceFoundException（6.2+）        │ 静态资源文件不存在                │  404   │
│ HttpRequestMethodNotSupportedException  │ HTTP 方法不对（POST 请求 GET 接口）│  405   │
│ BusinessException（自定义基类）          │ 业务规则违反                      │ 由异常携带│
│ Exception（兜底）                        │ 未预期异常                        │  500   │
└─────────────────────────────────────────┴──────────────────────────────────┴────────┘
```

### 6.2 自定义异常层次结构

```
BusinessException（基类）
├── 字段：code（String）+ httpStatus（HttpStatus）
├── 继承：RuntimeException（无需 throws 声明）
│
├── ResourceNotFoundException   → 404  （资源不存在）
├── BusinessConflictException   → 409  （库存不足、重复操作等）
├── ForbiddenException          → 403  （无权限）
└── （按业务需要扩展更多子类）
```

### 6.3 @ExceptionHandler 方法签名速查

```java
// 场景1：需要自定义 HTTP 状态码（通过 buildResponse 统一构建）
@ExceptionHandler(BusinessException.class)
public ResponseEntity<Map<String, Object>> handle(
        BusinessException e, HttpServletRequest request) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("code",    e.getCode());
    body.put("message", e.getMessage());
    return ResponseEntity.status(e.getHttpStatus()).body(body);
}

// 场景2：固定 HTTP 状态码（注入 HttpServletRequest 获取 path）
@ExceptionHandler(XxxException.class)
public ResponseEntity<Map<String, Object>> handle(
        XxxException e, HttpServletRequest request) {
    return buildResponse(HttpStatus.BAD_REQUEST, request, e.getMessage());
}

// 场景3：同一方法处理多种异常
@ExceptionHandler({AException.class, BException.class})
public ResponseEntity<Map<String, Object>> handle(
        Exception e, HttpServletRequest request) { ... }

// 场景4：兜底（捕获所有未处理异常）
@ExceptionHandler(Exception.class)
public ResponseEntity<Map<String, Object>> handleAll(
        Exception e, HttpServletRequest request) { ... }
```

### 6.4 处理顺序与优先级

```
Controller 内 @ExceptionHandler（最高优先级）
       ↓ 未匹配
@RestControllerAdvice 中精确类型匹配
       ↓ 未匹配
@RestControllerAdvice 中父类匹配（如 BusinessException 基类）
       ↓ 未匹配
@RestControllerAdvice 中 Exception 兜底
       ↓ 未匹配
Spring 默认处理 → /error 端点 → Whitelabel Error Page
```

### 6.5 统一响应格式

```
┌────────────┬─────────────┬──────────────────────────────────────────────────────┐
│ 字段        │ 类型         │ 说明                                                  │
├────────────┼─────────────┼──────────────────────────────────────────────────────┤
│ code       │ int         │ 成功为 200；失败为对应 HTTP 状态码（404、400 等）        │
│ message    │ String      │ 人类可读的描述，可直接展示给用户                        │
│ data       │ Object      │ 成功时携带业务数据；失败时无此字段                       │
│ timestamp  │ String      │ 失败响应附带，ISO-8601 时间戳，用于生产排查              │
│ path       │ String      │ 失败响应附带，请求路径，用于定位问题接口                 │
└────────────┴─────────────┴──────────────────────────────────────────────────────┘
```

### 6.6 404 异常必要配置（Spring Boot 3.5）

```yaml
# application.yml
# 只需一条配置，throw-exception-if-no-handler-found 在 Spring Boot 3.4 已废弃，无需再加
spring:
  web:
    resources:
      add-mappings: false # 关闭静态资源默认映射，404 场景才能抛出异常
```

> **升级注意**：从 Spring Boot 2.x 迁移时，可删除 `spring.mvc.throw-exception-if-no-handler-found: true`，仅保留上方配置即可。

### 6.7 ResponseBodyAdvice 自动包装速查

```java
// 实现要点
@RestControllerAdvice
public class GlobalResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        //只有被 @RestController 注解标注的类（即纯 JSON 接口），才需要进入 beforeBodyWrite 进行包装
        return returnType.getContainingClass().isAnnotationPresent(RestController.class);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType, Class selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        // 已是 Map 且含 code → 异常处理器返回的，跳过
        if (body instanceof Map && ((Map<?, ?>) body).containsKey("code")) {
            return body;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code",    200);
        result.put("message", "success");
        result.put("data",    body);
        return result;
    }
}
```

```
┌───────────────────────────┬───────────────────────────────────────────────────────┐
│ 注意事项                    │ 说明                                                  │
├───────────────────────────┼───────────────────────────────────────────────────────┤
│ 避免双重包装               │ 判断 body instanceof Map && containsKey("code")        │
│ 限定拦截范围               │ supports() 白名单，避免包装 Swagger/Actuator 等内部端点   │
│ String 返回值陷阱           │ StringHttpMessageConverter 无法处理 Map，建议不返回 String │
│ 与 @ExceptionHandler 协作  │ Advice 拦截成功响应，Handler 处理失败响应，分工明确        │
└───────────────────────────┴───────────────────────────────────────────────────────┘
```
