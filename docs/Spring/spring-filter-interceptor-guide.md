# Spring Boot 拦截器与过滤器指南

> 本指南循序渐进介绍 Spring Boot 中的请求拦截机制。"认证在 Filter、授权在 Interceptor"为主线，同一"用户登录 + 接口权限"场景贯穿全文。
>
> 适用版本：Spring Boot 3.x，Java 17+（`jakarta.servlet.*` 命名空间）

---

## 目录

0. [前置概念：Servlet 容器与 Spring IoC 容器](#0-前置概念servlet-容器与-spring-ioc-容器)
1. [第一层：Filter — Servlet 层的请求拦截](#1-第一层filter--servlet-层的请求拦截)
2. [第二层：Interceptor — Spring MVC 层的方法级拦截](#2-第二层interceptor--spring-mvc-层的方法级拦截)
3. [第三层：对比与决策](#3-第三层对比与决策)
4. [实战：常见场景选型](#4-实战常见场景选型)
5. [常见陷阱](#5-常见陷阱)
6. [速查清单](#6-速查清单)

---

## 0. 前置概念：Servlet 容器与 Spring IoC 容器

理解 Filter 和 Interceptor 的关键，不在一行代码，而在**谁管理谁**。

### 两个"管家"

Spring Boot 应用启动时，实际上启动了**两层容器**：

```
┌──────────────────────────────────────────────┐
│            Servlet 容器 (Tomcat)              │
│                                              │
│  管家身份：HTTP 层面的"大堂经理"              │
│  管理对象：Filter、Servlet（含 DispatcherServlet）│
│  生命周期：init() → service() → destroy()     │
│  它看到的是：原始 HTTP 请求/响应              │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │        Spring IoC 容器                  │  │
│  │                                        │  │
│  │  管家身份：业务层面的"部门经理"          │  │
│  │  管理对象：@Component、@Service、       │  │
│  │           @Controller、Interceptor      │  │
│  │  核心能力：@Autowired 依赖注入、AOP     │  │
│  │  它看到的是：已路由的请求 + 业务 Bean   │  │
│  │                                        │  │
│  │  DispatcherServlet 是两层之间的"大门"： │  │
│  │  Servlet 容器交给它 → 它交给 Spring    │  │
│  │  IoC 容器 → 路由到 Controller          │  │
│  └────────────────────────────────────────┘  │
└──────────────────────────────────────────────┘
```

### 为什么这很重要

**"谁管理"直接决定了"能干什么"：**

| 维度             | 由 Servlet 容器管理            | 由 Spring IoC 容器管理                        |
| ---------------- | ------------------------------ | --------------------------------------------- |
| **代表组件**     | Filter                         | Interceptor                                   |
| **依赖注入**     | ❌ 不能用 `@Autowired`         | ✅ 标准 Spring Bean                           |
| **能拿到的信息** | 只有 Request / Response        | Request / Response + HandlerMethod + 方法注解 |
| **执行时机**     | 请求进入 Spring **之前**       | DispatcherServlet 路由匹配**之后**            |
| **适用场景**     | 编码、CORS、认证（全局无差别） | 授权、方法级日志（需要知道"哪个方法"）        |

**核心结论**：Filter 处于 HTTP 层，在请求进入 Spring 生态之前就已经开始工作——所以它能做编码设置、请求体包装这类"底层"操作，但拿不到 Controller 方法信息。Interceptor 处于 Spring MVC 层，请求已经被路由到具体方法——所以它能读注解、做方法级授权，但管不了编码这类"底层"问题。

### 唯一的"桥"：FilterRegistrationBean

Filter 不由 Spring 管理，那它怎么在 Spring Boot 项目中使用？答案是 `FilterRegistrationBean`。

首先需要一个 Filter 实现——纯 POJO，不挂任何 Spring 注解：

```java
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import java.io.IOException;

public class MyFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        // Filter 的业务逻辑写在这里
        chain.doFilter(request, response);  // ← 必须调用，否则请求被拦截
    }
}
```

然后通过 `FilterRegistrationBean` 把它注册到 Servlet 容器：

```java
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<MyFilter> myFilter() {
        FilterRegistrationBean<MyFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new MyFilter());  // 手动 new（Filter 是纯 POJO）
        registration.addUrlPatterns("/*");
        registration.setOrder(1);
        return registration;  // ← 返回的是 Spring Bean，内部包装了 Filter
    }
}
```

- `FilterRegistrationBean` 本身是 **Spring Bean**（`@Bean` 方法返回，受 IoC 容器管理）
- 它内部持有的 Filter 实例是**纯 POJO**（手动 `new`，不受任何容器管理）
- Spring Boot 启动时，会把 `FilterRegistrationBean` 中的 Filter **注册到 Servlet 容器**

> **一句话总结**：`FilterRegistrationBean` 是 Spring 写给 Servlet 容器的"介绍信"——"这个 Filter 是我构造的，请把它加入你的拦截链。"

---

## 1. 第一层：Filter — Servlet 层的请求拦截

### 问题：中文请求参数乱码

前端发了一个 POST 请求，body 里有中文书名：

```json
{ "title": "Java 编程思想" }
```

Controller 收到的却是乱码 `"Java ç¼–ç¨"æ€æƒ³"`。原因：HTTP 请求默认用 `ISO-8859-1` 解码 body，不是 UTF-8。

最直接的想法——在每个 Controller 方法里设置编码：

```java
// BAD：每个方法都写一遍，重复且容易漏
@PostMapping("/books")
public Book createBook(HttpServletRequest request, @RequestBody CreateBookRequest req) {
    request.setCharacterEncoding("UTF-8");  // 重复
    return bookService.create(req);
}

@PostMapping("/orders")
public Order createOrder(HttpServletRequest request, @RequestBody CreateOrderRequest req) {
    request.setCharacterEncoding("UTF-8");  // 又重复
    return orderService.create(req);
}
```

50 个 Controller 就要写 50 遍。更糟的是——如果漏写一个，那个接口就悄悄乱码，测试不一定能发现。

**Filter 能在任何 Controller 执行之前统一设置编码，一处写、全站生效。**

### Filter 是什么

Filter 运行在 Servlet 容器层，比 Spring 的 `DispatcherServlet` 更底层。它拦截的是最原始的 HTTP 请求和响应：

```
浏览器请求
    │
    ▼
┌─────────┐
│ Filter1 │ → doFilter(request, response, chain)
├─────────┤
│ Filter2 │ → 设置编码：request.setCharacterEncoding("UTF-8")
├─────────┤
│   ...   │
├─────────┤
│ DispatcherServlet │ → 路由到 Controller
└─────────┘
```

任何 Controller 收到的 request，都已经过 Filter 预处理。

### Servlet 容器 vs Spring 容器

> 两层容器的关系已在 [前置概念](#0-前置概念servlet-容器与-spring-ioc-容器) 中详细介绍，这里做简要回顾。

Filter 的"管家"是 **Servlet 容器**（Tomcat），而非 Spring IoC 容器。这带来了三个直接后果：

- Filter 生命周期由 Servlet 容器管理 → 不能使用 `@Autowired`
- 需要通过 `FilterRegistrationBean`（Spring Bean）作为"中介"，把 Filter 注册到 Servlet 容器
- 如果 Filter 需要调用 Service，在 `FilterConfig` 中通过构造器手动传入

### FilterRegistrationBean：注册 Filter

`FilterRegistrationBean` 是 Spring Boot 提供的编程式注册工具——Filter 本身是纯 POJO（不需要任何 Spring 注解），所有配置集中在 `FilterConfig` 中：

```java
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import java.io.IOException;

// 纯 POJO，不挂任何 Spring 注解
public class EncodingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        chain.doFilter(request, response);  // ← 放行
    }
}
```

```java
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<EncodingFilter> encodingFilter() {
        FilterRegistrationBean<EncodingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new EncodingFilter());  // 手动 new
        registration.addUrlPatterns("/*");              // 拦截所有请求
        registration.setOrder(1);                       // 最先执行
        return registration;
    }
}
```

> **`FilterRegistrationBean` 是什么？** 它是 Spring Boot 对 Servlet `FilterRegistration` 的封装。Filter 本身仍是标准 `jakarta.servlet.Filter` 接口的实现，`FilterRegistrationBean` 负责把它注册到 Servlet 容器，管理路径映射（`addUrlPatterns`）、执行顺序（`setOrder`）和初始化参数（`addInitParameter`）。
>
> `new EncodingFilter()` → Filter 实例由 `FilterConfig`（Spring Bean）创建，而不是由 Servlet 容器自动扫描——**这是它能接受构造器传参的前提**（见 §5.1）。

### Filter 生命周期

```java
public class MyFilter implements Filter {

    @Override
    public void init(FilterConfig config) {
        // Filter 创建后调用一次。读取 init-param 配置参数
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) {
        // 每次请求都调用。必须调用 chain.doFilter() 否则请求被拦截
    }

    @Override
    public void destroy() {
        // 容器关闭前调用一次。释放资源
    }
}
```

| 方法         | 调用时机                      | 典型用途               |
| ------------ | ----------------------------- | ---------------------- |
| `init()`     | Filter 实例化后，调用**一次** | 读取配置、初始化连接池 |
| `doFilter()` | **每次**请求                  | 编码设置、认证、日志   |
| `destroy()`  | 容器关闭前，调用**一次**      | 释放资源               |

### FilterChain 原理（★ 核心概念）

`FilterChain` 是 Filter 机制的核心——它采用了**责任链（Chain of Responsibility）**设计模式。

**每条 `doFilter()` 都收到同一个 `chain` 对象。调用 `chain.doFilter()` 意味着"我的工作做完了，交给下一个"：**

```
请求 →
    │
    ▼
  EncodingFilter.doFilter(req, resp, chain)
    │
    │ ① request.setCharacterEncoding("UTF-8")    ← 请求预处理
    │
    ├─ chain.doFilter(req, resp) ─┐             ← 交给下一个 Filter
    │                             │
    │                             ▼
    │               AuthFilter.doFilter(req, resp, chain)
    │                             │
    │                             │ ① 提取 Token, 解析用户信息    ← 请求预处理
    │                             │
    │                             ├─ chain.doFilter(req, resp) ─┐
    │                             │                             │
    │                             │              ┌──────────────┘
    │                             │              ▼
    │                             │         DispatcherServlet → Controller
    │                             │              │
    │                             │  ←───────────┘
    │                             │
    │                             │ ② (chain 返回后)               ← 响应后处理
    │   ←─────────────────────────┘
    │
    │ ② (chain 返回后)                                      ← 响应后处理
    ▼
 响应返回客户端
```

**chain.doFilter() 是一个分水岭**——之前是"请求预处理"，之后是"响应后处理"。整个调用过程是一个**嵌套的调用栈**：后注册的 Filter 的"响应后处理"先执行（栈的后进先出）。

**不调用 `chain.doFilter()` = 请求被拦截。** 后续所有 Filter 和 Controller 都不会执行——这是 Filter 的"阻断"能力。

### Filter 链实战：认证

多个 Filter 组成一条链。典型场景：**编码设置**（最底层）→ **认证**（解密 Token、解析用户信息）：

```java
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

// 认证 Filter：提取 Token → 解析用户信息 → 放入 request 属性
public class AuthenticationFilter implements Filter {

    private static final String SECRET_KEY = "your-secret-key";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest  = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String token = httpRequest.getHeader("Authorization");
        if (token == null || token.isBlank()) {
            httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);  // 401
            httpResponse.setContentType("application/json;charset=UTF-8");
            httpResponse.getWriter().write("{\"code\":401,\"message\":\"未登录\"}");
            return;  // ← 不调用 chain.doFilter()，请求到此为止
        }

        // 解析 Token，提取用户身份
        Claims claims = Jwts.parser()
                .setSigningKey(SECRET_KEY)
                .parseSignedClaims(token.replace("Bearer ", ""))
                .getPayload();

        // 将用户信息放入 request 属性，后续的 Filter、Interceptor、Controller 都能读取
        httpRequest.setAttribute("userId",   claims.get("userId", Long.class));
        httpRequest.setAttribute("username", claims.get("username", String.class));
        httpRequest.setAttribute("roles",    claims.get("roles", String.class));

        chain.doFilter(request, response);  // 认证通过，放行
    }
}
```

在 `FilterConfig` 中注册两个 Filter，通过 `setOrder()` 控制顺序：

```java
@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<EncodingFilter> encodingFilter() {
        FilterRegistrationBean<EncodingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new EncodingFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(1);  // ① 先设置编码（最底层，影响所有后续处理）
        return registration;
    }

    @Bean
    public FilterRegistrationBean<AuthenticationFilter> authenticationFilter() {
        FilterRegistrationBean<AuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new AuthenticationFilter());
        registration.addUrlPatterns("/api/**");      // 只拦截 API 请求
        registration.setOrder(2);                    // ② 然后认证
        return registration;
    }
}
```

```
请求 /api/books
    │
    ▼
  setOrder(1) EncodingFilter       → UTF-8 编码（所有请求）
    │
    ▼
  setOrder(2) AuthenticationFilter → Token → 用户信息 → request 属性
    │  Token 无效？ → 401，不调用 chain.doFilter()
    │  Token 有效？ → chain.doFilter() 放行
    ▼
  DispatcherServlet → Controller
```

### 本节回顾

```
Filter 的能力边界：
    ✅ 设置编码（必须在读 request 之前）
    ✅ 认证（提取 Token → 解析用户 → request 属性）
    ✅ CORS 预检（OPTIONS 不经过 DispatcherServlet）
    ✅ 请求体包装（在 Spring 读 body 之前拦截）
    ❌ 方法级授权（Filter 执行时 URL 还没匹配到 Controller 方法）
```

**Filter 解决了"你是谁"（认证）。但"你能做什么"需要知道目标方法是哪个、有什么注解 → Interceptor。**

---

## 2. 第二层：Interceptor — Spring MVC 层的方法级拦截

### 问题：授权需要知道"哪个方法"和"需要什么角色"

Filter 的 `AuthenticationFilter` 已经把用户信息放入了 `request` 属性。现在要控制权限——比如 `DELETE /api/books/{id}` 只允许 `ADMIN` 角色访问。

Filter 做不到这件事：它执行时 URL 还没匹配到 Controller 方法，无法知道目标方法上有没有 `@RequiredRole("ADMIN")` 注解。

Interceptor 运行在 `DispatcherServlet` 完成路由匹配**之后**，能拿到 `HandlerMethod`——即匹配到的 Controller 方法的一切信息：类名、方法名、参数、注解。

### Interceptor 在请求链中的位置

```
请求 →
  FilterChain → DispatcherServlet
                   │
                   ├─ 根据 URL 找到匹配的 Handler（Controller 方法）
                   │
                   ▼
              ┌──────────────┐
              │ Interceptor  │  preHandle()  →  返回 false 则拦截
              ├──────────────┤
              │ Controller   │  执行业务逻辑
              ├──────────────┤
              │ Interceptor  │  postHandle() →  正常返回后
              ├──────────────┤
              │   视图渲染    │
              ├──────────────┤
              │ Interceptor  │  afterCompletion() →  总是执行（含异常）
              └──────────────┘
```

### 定义 @RequiredRole 注解

```java
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// 标记在方法上，声明该方法需要的角色
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiredRole {
    String value();  // 如 "ADMIN"、"USER"
}
```

```java
// Controller 中使用
@RestController
@RequestMapping("/api/books")
public class BookController {

    @GetMapping("/{id}")
    public Book getBook(@PathVariable Long id) {
        return bookService.findById(id);  // 无注解 = 所有已登录用户可访问
    }

    @DeleteMapping("/{id}")
    @RequiredRole("ADMIN")                  // ← 声明：需要 ADMIN 角色
    public void deleteBook(@PathVariable Long id) {
        bookService.delete(id);
    }
}
```

### 实现授权 Interceptor

```java
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;

@Component
public class AuthorizationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        // 只拦截 Controller 方法，不拦截静态资源等
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequiredRole annotation = handlerMethod.getMethodAnnotation(RequiredRole.class);
        if (annotation == null) {
            return true;  // 方法未声明角色要求 → 放行
        }

        // 从 request 属性中获取 AuthenticationFilter 放入的角色列表
        String rolesStr = (String) request.getAttribute("roles");
        List<String> userRoles = rolesStr != null
                ? Arrays.asList(rolesStr.split(","))
                : List.of();

        if (!userRoles.contains(annotation.value())) {
            // ★ 抛出异常 → 由全局异常处理器返回 403
            throw new AccessDeniedException("需要 " + annotation.value() + " 角色");
        }

        return true;
    }
}
```

```java
// 自定义异常，配合 GlobalExceptionHandler
public class AccessDeniedException extends RuntimeException {
    public AccessDeniedException(String message) {
        super(message);
    }
}
```

```java
// 全局异常处理器中处理 403
@ExceptionHandler(AccessDeniedException.class)
public ResponseEntity<Map<String, Object>> handleAccessDenied(
        AccessDeniedException e) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of(
                "code", 403,
                "message", e.getMessage()
            ));
}
```

> **为什么授权返回 403 而不是 401？** `401 Unauthorized` 表示"不知道你是谁"（认证失败）。`403 Forbidden` 表示"知道你是谁，但你没有权限"——语义精确，前端和监控系统可以区分处理。

### 注册 Interceptor

实现 `WebMvcConfigurer`，在 `addInterceptors` 中注册路径匹配：

```java
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthorizationInterceptor authorizationInterceptor;

    // 构造器注入（Interceptor 是 @Component → Spring Bean）
    public WebMvcConfig(AuthorizationInterceptor authorizationInterceptor) {
        this.authorizationInterceptor = authorizationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authorizationInterceptor)
                .addPathPatterns("/api/**")              // 所有 API
                .excludePathPatterns("/api/auth/login",  // 登录接口不需要授权
                                     "/api/public/**");  // 公开接口
    }
}
```

**Intercepor 是 Spring Bean → 可以用 `@Autowired` 注入 Service、用 `excludePathPatterns` 灵活排除路径。**

### 三阶段对比

| 阶段 | 方法              | 执行时机              | 异常时       | 典型用途                            |
| ---- | ----------------- | --------------------- | ------------ | ----------------------------------- |
| ①    | `preHandle`       | Controller 执行**前** | **执行**     | 授权（返回 false 或抛异常阻止访问） |
| ②    | `postHandle`      | Controller 执行**后** | 不执行       | 向 Model 追加数据                   |
| ③    | `afterCompletion` | 视图渲染后，请求完成  | **仍然执行** | 释放资源、记录日志                  |

> **授权在 `preHandle` 中做**——此时已经拿到 `HandlerMethod`，可以读方法注解，不通过直接抛异常，Controller 不会执行。

### 本节回顾

```
请求 /api/books/1（DELETE）
    │
    ├─ Filter:  EncodingFilter    → UTF-8
    ├─ Filter:  AuthenticationFilter → Token → userId/username/roles → request 属性
    │
    ├─ DispatcherServlet → 匹配到 BookController.deleteBook()
    │
    ├─ Interceptor: AuthorizationInterceptor.preHandle()
    │     ├─ 读 @RequiredRole("ADMIN")
    │     ├─ 读 request.getAttribute("roles")
    │     ├─ "ADMIN" 在角色列表中？ → true（放行）
    │     └─ 不在？ → throw AccessDeniedException → 403
    │
    └─ Controller.deleteBook() → 执行业务逻辑
```

**Filter 问"你是谁"（认证），Interceptor 问"你能做什么"（授权）——两者分工明确，不互相替代。**

---

## 3. 第三层：对比与决策

### 能力矩阵

```
                         Filter              Interceptor
                         ──────              ───────────
运行层级                 Servlet 容器         Spring MVC
由谁管理                 Servlet 容器         Spring IoC 容器
能否拿到 URL             ✅                   ✅
能否拿到 HandlerMethod   ❌                   ✅（preHandle 的 handler 参数）
能否读方法注解           ❌                   ✅（getMethodAnnotation）
能否设置字符编码         ✅（最佳实践）        ❌（太晚了）
能否包装请求/响应体      ✅（包装流）          ❌
能否阻止请求             ✅（不调 chain）      ✅（preHandle 返回 false）
能否抛异常给全局处理器   ❌（Filter 层之后）  ✅（抛异常 → @ExceptionHandler）
依赖注入                 ❌（纯 POJO）        ✅（@Autowired）
```

### 核心区别：谁先谁后

```
Filter 的 doFilter()
    ├─ chain.doFilter() 之前 → URL 还没匹配到方法，拿不到 HandlerMethod
    │
    ▼
DispatcherServlet → 根据 URL 找到匹配的 Controller 方法
    │
    ▼
Interceptor 的 preHandle() → handler 参数已包含完整方法信息
    ├─ handler instanceof HandlerMethod → 类名、方法名、注解全都有
```

### 决策树

```
需要拦截请求？
    │
    ├─ 需要在 Spring 介入之前就生效？
    │       → Filter（编码、CORS 预检、请求体包装）
    │
    ├─ 需要知道目标 Controller 方法/注解？
    │       → Interceptor（只有它能拿到 HandlerMethod）
    │
    ├─ 认证（检查 Token、解析用户身份）？
    │       → Filter（全局无差别，不依赖方法匹配）
    │
    ├─ 授权（检查角色/权限）？
    │       → Interceptor（依赖方法注解，403 vs 401 语义准确）
    │
    └─ 与其他框架无关（纯 HTTP 层）？
            → Filter（任何 Servlet 容器都能用）
```

---

## 4. 实战：常见场景选型

### 4.1 认证 → Filter

认证逻辑是全局的、无差别的——"每个 API 请求都要检查 Token"。Filter 在请求进入 Spring 之前就执行，是整个请求链的第一道门。

```java
// AuthenticationFilter（见 §1.6）
// · 提取 Token
// · 解析用户信息
// · 放入 request 属性
// · Token 无效 → 401（不经过 DispatcherServlet，直接返回）
```

> **Filter 做认证的理由**：① 全局无差别 ② 能在 Spring 介入前拦截无效请求 ③ 401 语义准确（"不知道你是谁"）。

### 4.2 授权 → Interceptor

授权需要知道"目标方法是哪个"和"需要什么角色"。Interceptor 的 `preHandle` 接收 `HandlerMethod`，可以读方法注解：

```java
// AuthorizationInterceptor（见 §2.2）
// · 读 @RequiredRole 注解
// · 读 request.getAttribute("roles")（由 AuthenticationFilter 放入）
// · 比对 → 不匹配则抛 AccessDeniedException → 403
```

> **Interceptor 做授权的理由**：① 能读方法注解（`getMethodAnnotation`）② 抛异常 → `@ExceptionHandler` 统一处理 ③ `excludePathPatterns` 灵活排除 ④ 403 语义准确（"知道你是谁，但没权限"）。

### 4.3 认证 + 授权完整链路

```
请求 DELETE /api/books/1
Authorization: Bearer eyJ...
    │
    ▼
┌─ Filter 层 ─────────────────────────────┐
│                                          │
│ ① EncodingFilter                        │
│    request.setCharacterEncoding("UTF-8") │
│                                          │
│ ② AuthenticationFilter                   │
│    解析 Token                            │
│    → userId=123, roles="USER,ADMIN"     │
│    → request.setAttribute(...)          │
│    → chain.doFilter() 放行              │
└──────────────────┬───────────────────────┘
                   │
                   ▼
          DispatcherServlet
           匹配到 BookController.deleteBook()
                   │
                   ▼
┌─ Interceptor 层 ─────────────────────────┐
│                                          │
│ AuthorizationInterceptor.preHandle()     │
│   读 @RequiredRole("ADMIN") → 从        │
│   request 取 roles → 包含 "ADMIN"       │
│   → return true 放行                    │
└──────────────────┬───────────────────────┘
                   │
                   ▼
          Controller.deleteBook()
                  ✅ 200
```

### 4.4 跨域 CORS → Filter

CORS 预检请求（OPTIONS）不经过 `DispatcherServlet`，必须用 Filter：

```java
@Bean
public FilterRegistrationBean<CorsFilter> corsFilter() {
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOriginPatterns(List.of("*"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    source.registerCorsConfiguration("/**", config);

    FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>();
    registration.setFilter(new CorsFilter(source));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
}
```

### 4.5 请求体多次读取 → Filter

Spring 的 `HttpServletRequest` 默认只能读一次 body（流式读取）。需要在 Filter 中包装：

```java
// 纯 POJO，通过 FilterRegistrationBean 注册
public class ContentCachingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain) throws IOException, ServletException {
        ContentCachingRequestWrapper wrapper =
                new ContentCachingRequestWrapper((HttpServletRequest) request);
        chain.doFilter(wrapper, response);
    }
}
```

### 本节回顾

| 场景                         | 选谁        | 理由                                           |
| ---------------------------- | ----------- | ---------------------------------------------- |
| **认证**（Token → 用户身份） | Filter      | 全局无差别，Spring 介入前就能拦截              |
| **授权**（角色/权限检查）    | Interceptor | 读方法注解，403 语义准确，路径匹配灵活         |
| 字符编码                     | Filter      | 必须在读 request 之前设置                      |
| 跨域 CORS                    | Filter      | OPTIONS 预检不经过 DispatcherServlet           |
| 请求体包装                   | Filter      | 必须在 Spring 读 body 之前完成                 |
| 响应体压缩                   | Filter      | 可包装 `HttpServletResponse.getOutputStream()` |

---

## 5. 常见陷阱

### 5.1 Filter 中注入 Spring Bean → 失败

Filter 不由 Spring 管理，直接用 `@Autowired` 永远为空：

```java
// BAD：Filter 是 POJO，@Autowired 不生效
public class AuthFilter implements Filter {
    @Autowired private UserService userService;  // ← 永远是 null！
}
```

**解决**：在 `FilterConfig`（Spring `@Configuration`）中构造器传入：

```java
@Configuration
public class FilterConfig {

    private final UserService userService;  // 构造器注入（Spring Bean）

    public FilterConfig(UserService userService) {
        this.userService = userService;
    }

    @Bean
    public FilterRegistrationBean<AuthFilter> authFilter() {
        AuthFilter filter = new AuthFilter(userService); // 手动传参
        FilterRegistrationBean<AuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/api/**");
        registration.setOrder(1);
        return registration;
    }
}
```

### 5.2 Interceptor 中 Response 已提交

`postHandle` 和 `afterCompletion` 中修改 response header 可能无效——响应已写出：

```java
// BAD：afterCompletion 中 addHeader 不生效
@Override
public void afterCompletion(...) {
    response.addHeader("X-Processed-By", "AuthzInterceptor"); // 无效
}
```

**解决**：需要加 header 在 `preHandle` 中做。

### 5.3 多次读取 Request Body → 流已耗尽

默认的 `HttpServletRequest.getReader()` 只能读一次：

```java
// BAD：Filter 读了 body → Controller 读不到
@Override
public void doFilter(ServletRequest request, ...) {
    String body = request.getReader().lines().collect(Collectors.joining());
    chain.doFilter(request, response);  // Controller 读的是空流
}
```

**解决**：使用 `ContentCachingRequestWrapper` 包装（见 §4.5）。

### 5.4 Filter 中获取不到 HandlerMethod

这是设计如此——Filter 执行时 URL 还没匹配到方法：

```java
// BAD：Filter 中永远拿不到 HandlerMethod
@Override
public void doFilter(ServletRequest request, ...) {
    HandlerMethod handler = ???; // 拿不到
}
```

**解决**：需要方法级信息 → 用 Interceptor。

### 5.5 `afterCompletion` 在 `preHandle` 返回 `false` 时不执行

当某个 Interceptor 的 `preHandle` 返回 `false` 时，当前及之后所有 Interceptor 的 `afterCompletion` 都不会触发——因为请求已被前置 Interceptor 拦截。

---

## 6. 速查清单

### 6.1 请求处理完整流水线

```
┌──────────────────────────────────────────────────────┐
│                  FilterChain                         │
│  · Servlet 容器层                                    │
│  · 能拿到：Request, Response                         │
│  · 拿不到：HandlerMethod, Controller 参数, 方法注解  │
│  · 典型用途：编码设置、认证、CORS、请求体包装        │
└─────────────────────┬────────────────────────────────┘
                      │
                      ▼
              DispatcherServlet（URL → HandlerMethod）
                      │
                      ▼
┌──────────────────────────────────────────────────────┐
│              Interceptor 链                           │
│  · Spring MVC 层                                     │
│  · 能拿到：Request, Response, HandlerMethod, 方法注解│
│  · 典型用途：授权、方法级日志、性能统计              │
└──────────────────────────────────────────────────────┘
```

### 6.2 接口速查

| 接口                                                 | 核心方法                                       | 层级       |
| ---------------------------------------------------- | ---------------------------------------------- | ---------- |
| `jakarta.servlet.Filter`                             | `doFilter(req, resp, chain)`                   | Servlet    |
| `org.springframework.web.servlet.HandlerInterceptor` | `preHandle` / `postHandle` / `afterCompletion` | Spring MVC |

### 6.3 FilterRegistrationBean API

| API                     | 用途                                    |
| ----------------------- | --------------------------------------- |
| `setFilter(instance)`   | 设置 Filter 实例（手动 `new`，纯 POJO） |
| `addUrlPatterns("/*")`  | 拦截路径                                |
| `setOrder(1)`           | 执行顺序（数字越小越先执行）            |
| `addInitParameter(...)` | 初始化参数                              |

### 6.4 Interceptor 三阶段

| 方法              | 执行时机      | 异常时   | 返回 false 效果  | 典型用途       |
| ----------------- | ------------- | -------- | ---------------- | -------------- |
| `preHandle`       | Controller 前 | 执行     | 拦截不执行       | **授权**、鉴权 |
| `postHandle`      | Controller 后 | 不执行   | —                | 追加公共数据   |
| `afterCompletion` | 最终          | **执行** | 前置拦截则不执行 | 清理资源、日志 |

### 6.5 决策速查

| 需求                     | 推荐        | 原因                                    |
| ------------------------ | ----------- | --------------------------------------- |
| **认证**（Token 有效性） | Filter      | 全局无差别，Spring 介入前即可拦截       |
| **授权**（角色/权限）    | Interceptor | 需要方法注解，403 vs 401 语义准确       |
| 字符编码                 | Filter      | 必须在读 request 之前设置               |
| 跨域 CORS                | Filter      | OPTIONS 预检不经过 DispatcherServlet    |
| 请求体包装               | Filter      | 必须在 Spring 读 body 之前包装          |
| 方法级日志               | Interceptor | 可以输出类名.方法名()                   |
| 全链路耗时               | Filter      | 覆盖 Filter 链 + DispatcherServlet 开销 |
| Controller 方法耗时      | Interceptor | 排除 Filter 和序列化开销                |

### 6.6 认证 + 授权分层总览

```
        ┌──────────────────────────────────┐
        │          Filter 层                │
        │                                  │
        │  EncodingFilter                  │
        │    字符编码（UTF-8）              │
        │                                  │
        │  AuthenticationFilter            │
        │    提取 Token → 用户信息          │
        │    → request.setAttribute()       │
        │    失败 → 401（直接写 response）  │
        └──────────────┬───────────────────┘
                       │
                       ▼
        ┌──────────────────────────────────┐
        │        Interceptor 层             │
        │                                  │
        │  AuthorizationInterceptor        │
        │    读 @RequiredRole 注解          │
        │    读 request.getAttribute()      │
        │    比对 → 失败抛异常 → 403        │
        │    成功 → Controller 执行         │
        └──────────────────────────────────┘
```
