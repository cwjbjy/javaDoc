# Spring Security 企业级指南

> 本指南系统介绍 Spring Security 的完整知识体系。先建立全景认知（SecurityFilterChain 在 Servlet 容器中的位置），再逐类深入认证、授权、Filter 链内部机制。
>
> 适用版本：Spring Boot 4.x / Spring Security 7.x（lambda DSL），Java 17+

---

## 目录

0. [前置概念：Spring Security = Filter 链](#0-前置概念spring-security--filter-链)
1. [全景图：Spring Security 完整架构](#1-全景图spring-security-完整架构)
2. [认证（Authentication）](#2-认证authentication)
   - [2.0 认证核心模型](#20-认证核心模型)
   - [2.1 JWT 无状态认证](#21-jwt-无状态认证)
   - [2.2 自定义 AuthenticationProvider](#22-自定义-authenticationprovider)
3. [授权（Authorization）](#3-授权authorization)
   - [3.1 URL 级别](#31-url-级别)
   - [3.2 方法级别](#32-方法级别)
4. [深入 Filter 链](#4-深入-filter-链)
   - [4.1 Filter 顺序与插入位置](#41-filter-顺序与插入位置)
   - [4.2 自定义 Filter](#42-自定义-filter)
5. [安全上下文传递](#5-安全上下文传递)
6. [实战决策](#6-实战决策)
   - [6.1 认证方案决策树](#61-认证方案决策树)
   - [6.2 常见反模式](#62-常见反模式)
7. [速查清单](#7-速查清单)

---

## 0. 前置概念：Spring Security = Filter 链

理解 Spring Security 的关键，不在于配置怎么写，而在于**它运行在哪一层**。

```
HTTP 请求
    │
    ▼
┌──────────────────────────────────────────┐
│          Servlet 容器 (Tomcat)            │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │    Spring Security Filter 链        │  │  ← Security 就是一堆 Filter
│  │  ┌──────────────────────────────┐  │  │
│  │  │ 安全上下文持久化              │  │  │
│  │  ├──────────────────────────────┤  │  │
│  │  │ 认证（JWT / OAuth2 / ...）     │  │  │
│  │  ├──────────────────────────────┤  │  │
│  │  │ 异常转换（401 / 403）        │  │  │
│  │  ├──────────────────────────────┤  │  │
│  │  │ 授权拦截（最后一道防线）      │  │  │
│  │  └──────────────────────────────┘  │  │
│  └────────────────────────────────────┘  │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │        DispatcherServlet           │  │
│  │  ┌──────────────────────────────┐  │  │
│  │  │     Spring IoC 容器           │  │  │
│  │  │  Interceptor → Controller    │  │  │
│  │  └──────────────────────────────┘  │  │
│  └────────────────────────────────────┘  │
└──────────────────────────────────────────┘
```

### Security Filter 链如何挂入 Servlet 容器

上图中"Spring Security Filter 链"并非直接注册到 Servlet 容器——中间有两层委托：

```
Servlet 容器
  │
  └─ DelegatingFilterProxy          ← Spring Boot 使用 FilterRegistrationBean 注册的 Filter（桥接 Servlet ↔ Spring）
       │
       └─ FilterChainProxy           ← 根据请求路径，动态选择匹配的 SecurityFilterChain
            │
            └─ SecurityFilterChain   ← 配置的那条 Filter 链（@Bean SecurityFilterChain）
                 │                     SecurityFilterChain = 一组有序 Filter + URL 匹配规则
                 ├─ SecurityContextHolderFilter
                 ├─ JwtAuthenticationFilter（自定义）
                 ├─ ExceptionTranslationFilter
                 └─ AuthorizationFilter
```

FilterChainProxy 是 Spring Security 创建的一个 Spring Bean，会被 DelegatingFilterProxy 通过名字约定在运行时查找它。它负责接收所有请求，动态选择匹配的安全策略（SecurityFilterChain），并驱动整个 Spring Security 过滤器链按照既定顺序运行。在平时的业务开发中，极少需要直接操作它。

---

## 1. 全景图：Spring Security 完整架构

§0 建立了"Spring Security = Filter 链"的认知。本节展示这条链的完整结构——有哪些功能层、每层做什么、它们如何协作完成认证与授权。这张图就是本文的导航地图。

### Filter 链中的四大功能层

```
HTTP 请求
    │
    ▼
┌──────────────────────────────────────────────────────────────┐
│              Spring Security Filter 链                        │
│                                                              │
│  ① 安全上下文层                                               │
│     SecurityContextHolderFilter                              │
│     加载/保存当前请求的 SecurityContext（§5 详解）            │
│                                                              │
│  ② 认证层                                                     │
│     JwtAuthenticationFilter（自定义）                         │
│     从请求中提取凭证 → 验证 → 存入 SecurityContextHolder       │
│     认证层内部使用核心模型（§2.0）：                          │
│       AuthenticationManager → Provider → UserDetailsService │
│     （§2 详解）                                                │
│                                                              │
│  ③ 异常转换层                                                 │
│     ExceptionTranslationFilter                               │
│     认证失败 → 401，授权失败 → 403                             │
│                                                              │
│  ④ 授权层                                                     │
│     AuthorizationFilter                                      │
│     URL 级别授权（§3.1）                                      │
│     + 方法级 @PreAuthorize（§3.2，在 Controller 层执行）       │
│                                                              │
│                   ↓                                          │
│            DispatcherServlet → Controller                    │
└──────────────────────────────────────────────────────────────┘
```

---

## 2. 认证（Authentication）

认证回答"你是谁"。现代企业应用以 **JWT 无状态认证** 为主流方案，相比传统的 Session 表单登录，JWT 具有更好的扩展性（服务端无需维护会话状态）和更适合前后端分离架构的特性。Spring Security 的认证流程始终围绕同一套核心模型。

### 2.0 认证核心模型

§1 全景图提到认证层内部使用"核心模型"。在深入 JWT 实现之前，先理解这 6 个组件各自扮演什么角色——它们贯穿后续所有认证方案。

| 组件                       | 职责                                                                                                                                           | 一句话类比                   |
| -------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------- |
| **AuthenticationManager**  | 认证的总入口。不执行认证，只分发给合适的 `AuthenticationProvider`                                                                              | 前台：接待并分流，不亲自处理 |
| **AuthenticationProvider** | 实际执行认证逻辑（验密码、查账户状态）。一个系统可有多个 Provider                                                                              | 验证员：拿到凭证后实际处理   |
| **UserDetailsService**     | 根据用户名加载用户信息，只有一个方法 `loadUserByUsername()`。数据可以来自数据库、内存、LDAP——Provider 不关心来源                               | 档案室：提供数据，不判断对错 |
| **UserDetails**            | 从数据源查出的用户对象（用户名、密码哈希、权限列表 `getAuthorities()`）                                                                        | 档案袋：装着用户信息         |
| **Authentication**         | 认证信息的"通行证"对象。认证前存凭证（用户名+密码），认证后存结果（UserDetails + 权限）                                                        | 工牌：申请时填表，通过后发牌 |
| **SecurityContextHolder**  | 存放当前请求认证信息的容器（基于 `ThreadLocal`）。认证成功后，`Authentication` 存入这里，后续 Filter、Controller、Service 随时可以获取当前用户 | 工牌挂绳：随时可取当前身份   |

> **关键解耦**：Manager（调度）→ Provider（执行）→ UserDetailsService（数据源），三层各自独立。认证逻辑（怎么验）与数据来源（从哪查）完全分离。

它们协作的完整流程：

```
用户提交凭证（用户名 / 密码 / Token）
    │
    ▼
AuthenticationManager        ← 接收凭证，分发给合适的 Provider
    │
    ▼
AuthenticationProvider       ← 调用 UserDetailsService 查用户、验密码
    │ → UserDetailsService.loadUserByUsername()
    │   （返回 UserDetails）
    ▼
Authentication               ← 认证成功，包装为 Authentication（含 Principal + Authorities）
    │
    ▼
SecurityContextHolder         ← 存入上下文：.getContext().setAuthentication(...)
```

### 核心模型与 JWT 的关系

这个模型对 JWT 认证同样适用，但**用在两个不同时机**：

```
              登录时（§2.1 AuthController）           后续请求（§2.1 JwtAuthenticationFilter）
              ──────────────────────────────          ──────────────────────────────────
使用的组件    Manager → Provider → UDS               直接解析 Token → 构建 Authentication
              （完整三层调用）                         （绕过 Manager / Provider）
              │                                       │
原因          需要验证用户名密码                        Token 本身就是"已认证"的证明
              → 验证通过后签发 Token                   → 只需解析 + 验签，不需要再验密码
```

### 2.1 JWT 无状态认证

前后端分离架构下，服务端不维护 Session，每次请求通过 JWT（JSON Web Token）自证身份。

以下是完整的、可运行的 JWT 认证方案。它包含两个部分：**登录接口**（走核心模型三层调用，签发 Token）和**请求 Filter**（绕过核心模型，直接解析 Token 设置上下文）。

#### 依赖

在 `pom.xml` 的 `spring-boot-starter-security` 基础上添加 JWT 库：

```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

#### JwtUtil：Token 的生成与解析

Token 工具类封装了 JWT 的创建、验证和提取操作：

```java
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

public class JwtUtil {

    // 至少 256 位密钥（HMAC-SHA256）。生产环境从配置中心读取，禁止硬编码
    private static final String BASE64_SECRET = "dGhpc0lzQVNlY3JldEtleUZvckpXVDMyQnl0ZXNTZWNyZXRLZXk=";
    private static final long EXPIRATION_MS = 24 * 60 * 60 * 1000; // 24 小时

    private SecretKey getSigningKey() {
        byte[] keyBytes = Base64.getDecoder().decode(BASE64_SECRET);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /** 为指定用户名生成 JWT Token */
    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(getSigningKey())
                .compact();
    }

    /** 从 Token 中提取用户名 */
    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /** 验证 Token 是否有效 */
    public boolean isTokenValid(String token, String username) {
        String extractedUsername = extractUsername(token);
        return extractedUsername.equals(username) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return parseClaims(token).getExpiration().before(new Date());
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
```

#### JwtAuthenticationFilter：每次请求都执行的"守门人"

JWT Filter 继承 `OncePerRequestFilter`——保证每个请求只经过一次，从请求头提取 Token 并设置安全上下文：

```java
import io.jsonwebtoken.JwtException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        // 没有 Authorization 头或不是 Bearer 格式 → 跳过（交给后续 Filter 处理）
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            String username = jwtUtil.extractUsername(token);

            // 用户名有效且当前 SecurityContext 尚未设置认证信息
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtUtil.isTokenValid(token, userDetails.getUsername())) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (JwtException e) {
            // Token 过期 / 签名无效 / 格式错误 → 不设置认证信息，清空上下文
            // 放行给后续 Filter：AuthorizationFilter 发现无认证 → ExceptionTranslationFilter 返回 401
            log.debug("JWT 解析失败: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
```

> **为什么 catch 后继续放行？** Token 解析失败（过期、签名无效、格式错误）时，`JwtException` 不是 Spring Security 的 `AuthenticationException`，不会被 `ExceptionTranslationFilter` 自动转为 401。如果不 catch，异常会穿透整个 Filter 链导致 500。正确做法：catch 住异常 → 不设置认证信息 → 继续放行 → `AuthorizationFilter` 发现请求未认证 → `ExceptionTranslationFilter` 返回 401。

#### SecurityConfig：JWT 模式的安全配置

注册 JWT Filter，禁用 Session，暴露登录接口为公开端点：

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // 启用 §3.2 的方法级授权
public class SecurityConfig {

    private final JwtUtil jwtUtil;

    public SecurityConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtAuthenticationFilter jwtFilter) throws Exception {
        http
            // CORS：前后端分离必须开启，否则浏览器跨域请求被拦截
            .cors(Customizer.withDefaults())
            // 无状态：不创建 Session，不从 Session 恢复 SecurityContext
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 关闭 CSRF（API 通常不需要，因为不基于 Cookie 传递凭证）
            .csrf(csrf -> csrf.disable())
            // URL 规则
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            // 在 UsernamePasswordAuthenticationFilter 之前插入 JWT Filter
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // 将 JWT Filter 注册为 Bean，通过方法参数注入 UserDetailsService
    // —— 避免 SecurityConfig 构造器直接注入 UserDetailsService（与同类的 @Bean 定义形成循环依赖）
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            UserDetailsService userDetailsService) {
        return new JwtAuthenticationFilter(jwtUtil, userDetailsService);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        var admin = User.builder()
                .username("admin")
                .password(passwordEncoder().encode("admin123"))
                .roles("ADMIN")
                .build();
        var user = User.builder()
                .username("user")
                .password(passwordEncoder().encode("user123"))
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(admin, user);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // CORS 配置：.cors(Customizer.withDefaults()) 会自动找到这个 Bean
    // 生产环境应将 allowedOrigins 设为具体前端域名，禁止用 "*"
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173")); // 前端开发地址
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true); // 如果需要携带 Cookie
        config.setMaxAge(3600L); // 预检请求缓存 1 小时

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

> **`addFilterBefore` 是关键**：JWT Filter 必须在 `UsernamePasswordAuthenticationFilter` 之前执行，否则表单登录的默认逻辑会抢先处理请求。

> **CORS 必须显式配置**：`.cors(Customizer.withDefaults())` 启用 Spring Security 的 CORS 支持，它会查找容器中的 `CorsConfigurationSource` Bean。没有这个 Bean，浏览器的跨域请求会被安全框架拦截——即使 Controller 能处理，浏览器也收不到响应。

#### 登录 DTO

用 Java 17 的 `record` 定义请求和响应体——简洁且不可变：

```java
public record LoginRequest(String username, String password) {}

public record LoginResponse(String token, String username) {}
```

#### AuthController：登录接口

登录接口使用上面的 DTO，验证用户名密码后返回 JWT。这里的 `authenticationManager.authenticate()` 走的就是 §2.0 的完整三层调用（Manager → Provider → UserDetailsService）：

```java
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    public AuthController(AuthenticationManager authenticationManager, JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        // authenticate() 内部调用 Provider → UserDetailsService 验证用户
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(), request.password()));
        String token = jwtUtil.generateToken(auth.getName());
        return ResponseEntity.ok(new LoginResponse(token, request.username()));
    }

    // 认证失败（用户名不存在 / 密码错误）→ 401
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<String> handleBadCredentials(BadCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("用户名或密码错误");
    }
}
```

#### JWT 模式的请求流转

```
POST /api/auth/login（username=admin, password=admin123）
    │
    ▼
AuthController.login() → authenticationManager.authenticate()
    │ （Manager → Provider → UserDetailsService，验证通过）
    ▼
jwtUtil.generateToken("admin")
    │ （返回 JWT：eyJhbGciOi...）
    ▼
客户端收到 Token，存入 localStorage

    后续请求：
                GET /api/orders/42
                Authorization: Bearer eyJhbGciOi...

                    │
                    ▼
                JwtAuthenticationFilter
                    │ （解析 JWT → 查 UserDetails → 设置 SecurityContext）
                    ▼
                AuthorizationFilter（授权检查 → 放行）
                    ▼
                Controller.orders(42)
```

> **JWT 适合**：前后端分离、移动端 API、微服务间调用。不适合需要服务端主动踢出用户的场景（JWT 签发后无法撤销——需要用黑名单或短过期 + 刷新 Token 解决，属于进阶话题）。

### 2.2 自定义 AuthenticationProvider

当认证不只是"查用户名密码"——比如还要验证用户状态、尝试多种认证方式——就需要自定义 `AuthenticationProvider`。

一个按业务状态决定是否允许登录的 Provider：

```java
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

public class CustomAuthenticationProvider implements AuthenticationProvider {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    public CustomAuthenticationProvider(UserDetailsService userDetailsService,
                                        PasswordEncoder passwordEncoder) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {
        String username = authentication.getName();
        String password = authentication.getCredentials().toString();

        UserDetails user = userDetailsService.loadUserByUsername(username);

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BadCredentialsException("密码错误");
        }
        if (!user.isEnabled()) {
            throw new LockedException("账户已被禁用");
        }

        return new UsernamePasswordAuthenticationToken(
                user, password, user.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
```

注册到 Security 配置中——将自定义 Provider 声明为 `@Bean`，Spring Security 会自动拾取所有 `AuthenticationProvider` Bean 并注册到默认的 `AuthenticationManager`：

```java
@Bean
public CustomAuthenticationProvider customAuthenticationProvider(
        UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
    return new CustomAuthenticationProvider(userDetailsService, passwordEncoder);
}

@Bean
public AuthenticationManager authenticationManager(
        AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
}
```

> **自定义 Provider 适合**：多级认证（先本地后 LDAP）、账户状态校验、验证码校验。

---

## 3. 授权（Authorization）

认证回答"你是谁"，授权回答"你能干什么"。Spring Security 提供两层授权——URL 级别（粗粒度）和方法级别（细粒度）。

### 3.1 URL 级别

在 `SecurityFilterChain` 中通过 `authorizeHttpRequests` 定义 URL 访问规则。规则**从上到下匹配，命中即停止**：

```java
http.authorizeHttpRequests(auth -> auth
    // 公开端点：所有人可访问
    .requestMatchers("/api/auth/**", "/public/**").permitAll()

    // 管理员专属
    .requestMatchers("/api/admin/**").hasRole("ADMIN")

    // 多种角色均可
    .requestMatchers("/api/reports/**").hasAnyRole("ADMIN", "MANAGER")

    // 按 HTTP 方法区分
    .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
    .requestMatchers(HttpMethod.POST, "/api/products/**").hasRole("ADMIN")

    // IP 限制
    .requestMatchers("/api/internal/**").hasIpAddress("192.168.1.0/24")

    // 兜底：其余全部需要认证
    .anyRequest().authenticated()
);
```

> **规则顺序就是优先级**。把 `permitAll()` 写在 `anyRequest().authenticated()` 之后，公开端点就失效了。原则：**具体规则在前，通用规则在后**。

> **`hasRole()` 与 `hasAuthority()` 的区别**：`hasRole('ADMIN')` 内部自动添加 `ROLE_` 前缀，实际检查的是 `ROLE_ADMIN` 权限。`hasAuthority('ADMIN')` 不添加前缀，直接检查 `ADMIN`。`User.builder().roles("ADMIN")` 设置的权限是 `ROLE_ADMIN`，所以配合 `hasRole('ADMIN')` 使用。如果需要细粒度权限（如 `ORDER_DELETE`），用 `User.builder().authorities("ORDER_DELETE")` + `hasAuthority('ORDER_DELETE')`。

### 3.2 方法级别

URL 授权是"挡在门外"，方法级授权是"审到房内"。用 `@EnableMethodSecurity` 开启后，可以在任意方法上加注解控制访问。

#### 角色检查

判断当前用户是否持有指定角色：

```java
// Controller 层：按角色控制方法
@RestController
@RequestMapping("/api/users")
public class UserController {

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

#### 数据归属检查

判断"当前用户只能操作自己的数据"——SpEL 表达式中的 `#` 引用方法参数，`authentication` 引用当前安全上下文：

```java
// Service 层：按数据所有权控制
@Service
public class OrderService {

    // 仅允许用户查询自己的订单：URL 中的 userId 必须等于当前登录用户
    @PreAuthorize("#userId == authentication.principal.username")
    public List<Order> findOrdersByUser(String userId) {
        return orderRepository.findByUserId(userId);
    }

    // 或基于对象的权限（返回对象需要先加载，@PostAuthorize 在方法返回后执行）
    @PostAuthorize("returnObject.userId == authentication.principal.username")
    public Order findOrderById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("订单不存在"));
    }
}
```

#### 自定义权限表达式

当简单的角色检查不够时，可以用 SpEL 组合复杂条件：

```java
// 角色 + 数据归属复合检查
@PreAuthorize("hasRole('ADMIN') or #username == authentication.principal.username")
public UserProfile getProfile(String username) { ... }

// 多条件组合
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') and #amount <= 10000")
public void approveRefund(Long orderId, BigDecimal amount) { ... }
```

> **`@PreAuthorize` 在方法执行前拦截**，适合"是否有权"的判断。**`@PostAuthorize` 在方法执行后过滤**，适合"返回的数据是否属于当前用户"的判断。能用 `@PreAuthorize` 就别用 `@PostAuthorize`——先拦截更高效。

---

## 4. 深入 Filter 链

### 4.1 Filter 顺序与插入位置

Spring Security 内部的 Filter 链有严格的顺序。下面是主要 Filter 的默认顺序（数字越小越先执行）：

| 顺序 | Filter                                 | 职责                                                                    |
| ---- | -------------------------------------- | ----------------------------------------------------------------------- |
| 700  | `SecurityContextHolderFilter`          | 从请求中加载 SecurityContext（STATELESS 模式下为空操作）                |
| 2100 | `UsernamePasswordAuthenticationFilter` | 处理表单登录 POST                                                       |
| 4000 | `ExceptionTranslationFilter`           | 将 `AuthenticationException` 转为 401、`AccessDeniedException` 转为 403 |
| 4200 | `AuthorizationFilter`                  | URL 级别的授权检查（最后一道防线）                                      |

> 完整列表参见 Spring Security 源码中的 `FilterOrderRegistration` 类（位于 `org.springframework.security.config.annotation.web.builders` 包，定义了所有内置 Filter 的默认顺序值）。以上 4 个是你日常开发中最常打交道的。顺序值随版本变化，此处为 Spring Security 7.x 的值。

**自定义 Filter 必须插入正确的位置**：

```java
// ✅ JWT 认证：在表单认证之前（我们用 Token，不需要表单）
http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

// ✅ 日志 Filter：在其他认证 Filter 之后（拿得到用户名）
http.addFilterAfter(loggingFilter, UsernamePasswordAuthenticationFilter.class);

// ✅ 在指定 Filter 的同一位置插入（需先禁用内置 Filter，否则抛异常）
http.addFilterAt(customAuthFilter, UsernamePasswordAuthenticationFilter.class);
```

错误示范：

```java
// ❌ BAD：把 JWT Filter 加在 UsernamePasswordAuthenticationFilter 之后
//    表单 Filter 会发现没有 username/password 参数，直接抛异常
http.addFilterAfter(jwtFilter, UsernamePasswordAuthenticationFilter.class);
```

> **调试技巧**：在 `application.yml` 中设置 `logging.level.org.springframework.security: DEBUG`，应用启动时会打印完整的 Filter 链顺序，方便排查 Filter 插入位置是否正确。

### 4.2 自定义 Filter

除了 JWT 认证，常见的自定义 Filter 场景包括请求日志、自定义请求头校验。下面是一个请求计时日志 Filter：

```java
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class RequestTimingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestTimingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long start = System.currentTimeMillis();
        filterChain.doFilter(request, response);
        long duration = System.currentTimeMillis() - start;
        log.info("{} {} — {}ms", request.getMethod(),
                request.getRequestURI(), duration);
    }
}
```

注册到 Security 配置中——插在 SecurityContextHolderFilter 之前，记录整条 Security Filter 链的完整耗时：

```java
http.addFilterBefore(new RequestTimingFilter(), SecurityContextHolderFilter.class);
```

---

## 5. 安全上下文传递

`SecurityContextHolder` 是 Spring Security 的"当前用户"存储中心。Controller 或 Service 中获取当前用户的三种方式：

```java
// 方式一：从 SecurityContextHolder 获取（任何层级可用）
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
String username = auth.getName();

// 方式二：Controller 参数注入（仅 Controller 层）
// @AuthenticationPrincipal 自动将 SecurityContextHolder 中当前用户的
// Authentication.getPrincipal() 注入方法参数（无需手动从 SecurityContextHolder 取）
@GetMapping("/profile")
public ResponseEntity<UserProfile> profile(@AuthenticationPrincipal UserDetails user) {
    return ResponseEntity.ok(userService.getProfile(user.getUsername()));
}

// 方式三：通过 HttpServletRequest
@GetMapping("/info")
public ResponseEntity<String> info(HttpServletRequest request) {
    Principal principal = request.getUserPrincipal();
    return ResponseEntity.ok(principal.getName());
}
```

### 异步场景下的上下文传递

Spring Security 默认使用 `ThreadLocal` 存储 `SecurityContext`。ThreadLocal 只在“当前线程”生效，异步方法运行在**不同线程**上时，默认拿不到上下文：

```java
// ❌ BAD：@Async 在新线程执行，SecurityContext 丢失
@Async
public void sendNotification() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    // auth == null → 拿不到当前用户
}
```

解决方案——设置 `SecurityContextHolder` 的策略为可继承模式：

```java
// 在 @PostConstruct 或配置类中设置
@Configuration
public class SecurityContextConfig {

    //执行一次性初始化
    @PostConstruct
    public void enableInheritableThreadLocal() {
        SecurityContextHolder.setStrategyName(
                SecurityContextHolder.MODE_INHERITABLETHREADLOCAL);
    }
}
```

> **`MODE_INHERITABLETHREADLOCAL` 仅在 `@Async` 的**直接**子线程中生效**。如果使用线程池复用线程（大多数情况），推荐用 Spring Security 的 `DelegatingSecurityContextAsyncTaskExecutor`（一个线程池包装器，提交每个任务时自动将当前 `SecurityContext` 复制到工作线程），确保每次提交任务时显式传递上下文。

---

## 6. 实战决策

### 6.1 认证方案决策树

现代应用普遍采用 JWT 无状态认证，但在特定场景下仍需了解不同方案的特点：

```
你的应用架构是？
    │
    ├── 前后端分离（Vue / React + REST API）
    │   └── 使用 JWT 无状态认证（§2.1）—— 当前主流方案
    │
    └── 微服务 / 网关架构
        └── 使用 JWT + 网关统一认证，下游服务只验签不查库
```

```
你需要服务端主动踢出用户？
    │
    ├── 是 → 使用短过期时间（如 15 分钟）+ 刷新 Token 机制，
    │        或维护 Token 黑名单（Redis）实现服务端控制
    │
    └── 否 → 标准 JWT 方案即可，Token 过期前一直有效
```

### 6.2 常见反模式

| 反模式                                                        | 问题                                                                       | 正确做法                                                                  |
| ------------------------------------------------------------- | -------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| **SecurityConfig 中写死用户名密码**                           | 每次改密码要重启应用，且密码暴露在代码仓库                                 | 用 `UserDetailsService` 从数据库加载，密码用 `BCryptPasswordEncoder` 加密 |
| **`.anyRequest().permitAll()` 排在 `.hasRole("ADMIN")` 前面** | 规则从上到下匹配——`permitAll()` 命中了全部请求，管理员规则永远不生效       | 具体规则在前，`anyRequest()` 在最后                                       |
| **JWT Secret 硬编码 + 长度不足**                              | 密钥泄露 = 所有 Token 可被伪造。长度不足 256 位 = 暴力破解风险             | 从环境变量或配置中心读取，使用随机生成的 256+ 位密钥                      |
| **每个 Controller 方法里手动校验权限**                        | 重复代码、容易遗漏                                                         | 用 `@PreAuthorize` 声明式授权（§3.2）                                     |
| **Filter 顺序错误**                                           | JWT Filter 在表单 Filter 之后 → 表单 Filter 抛异常，JWT 永远不执行         | 用 `addFilterBefore` 插入到正确的内置 Filter 之前（§4.1）                 |
| **禁用 CSRF 时不加思考**                                      | 纯 API 可以禁，但如果有管理后台页面（Cookie 认证），禁掉 CSRF 就是安全隐患 | API 端禁 CSRF，页面端保留 CSRF                                            |
| **`.authenticated()` 替代 `.hasRole()`**                      | 只要登录就能访问管理员接口 → 任何用户都能删数据                            | 管理接口用 `.hasRole("ADMIN")`，而非 `.authenticated()`                   |

---

## 7. 速查清单

### 7.1 SecurityFilterChain 配置速查

| 配置项            | Lambda DSL 写法                                                                                  | 作用                                            |
| ----------------- | ------------------------------------------------------------------------------------------------ | ----------------------------------------------- |
| 关闭 Session      | `.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))`              | 无状态 API                                      |
| 关闭 CSRF         | `.csrf(csrf -> csrf.disable())`                                                                  | 纯 REST API（JWT 不依赖 Cookie，天然免疫 CSRF） |
| 公开端点          | `.requestMatchers("/api/auth/**").permitAll()`                                                   | 登录/注册接口                                   |
| 角色限制          | `.requestMatchers("/admin/**").hasRole("ADMIN")`                                                 | 仅管理员                                        |
| 无状态 JWT        | `.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))` + JWT Filter | 现代前后端分离标准方案                          |
| 注销              | `.logout(l -> l.logoutUrl("/logout"))`                                                           | 可选：配合 Token 黑名单                         |
| 插入自定义 Filter | `.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)`                        | JWT 认证                                        |

### 7.2 @PreAuthorize 常用表达式速查

| 表达式                                      | 含义                                             |
| ------------------------------------------- | ------------------------------------------------ |
| `hasRole('ADMIN')`                          | 当前用户持有 ADMIN 角色（内部检查 `ROLE_ADMIN`） |
| `hasAnyRole('ADMIN', 'MANAGER')`            | 持有任一角色即可                                 |
| `hasAuthority('ORDER_DELETE')`              | 持有指定权限（细粒度，无 `ROLE_` 前缀）          |
| `#id == authentication.principal.id`        | 方法参数等于当前用户 ID                          |
| `returnObject.owner == authentication.name` | 返回值属于当前用户                               |
| `@customPermission.check(#id)`              | 调用自定义 Bean 的方法做权限判断                 |

### 7.3 PasswordEncoder 速查

| 编码器                      | 特点                                             | 适用场景          |
| --------------------------- | ------------------------------------------------ | ----------------- |
| `BCryptPasswordEncoder`     | 自适应哈希，抗暴力破解，Spring Security 默认     | 生产环境首选      |
| `Pbkdf2PasswordEncoder`     | FIPS 认证，额外配置迭代次数                      | 政府 / 合规要求   |
| `NoOpPasswordEncoder`       | 明文存储（已废弃）                               | 仅测试            |
| `DelegatingPasswordEncoder` | 前缀区分编码方式（`{bcrypt}...`、`{pbkdf2}...`） | 多编码并存 / 迁移 |

### 7.4 关键 Filter 顺序速查

| 顺序 | Filter                                 | 自定义 Filter 插入位置建议                            |
| ---- | -------------------------------------- | ----------------------------------------------------- |
| 最先 | `SecurityContextHolderFilter`          | 日志、请求计时 → `addFilterBefore`                    |
| 中间 | `UsernamePasswordAuthenticationFilter` | JWT 认证 → `addFilterBefore`；审计 → `addFilterAfter` |
| 中间 | `ExceptionTranslationFilter`           | 自定义异常处理 → `addFilterAfter`                     |
| 最后 | `AuthorizationFilter`                  | 不应在此之后加认证/授权逻辑                           |

---

> **接下来**：读完本指南，建议下一步阅读 [Spring Filter/Interceptor 指南](spring-filter-interceptor-guide.md) 深入了解 Servlet Filter 与 Spring Interceptor 的完整对比——两者与本指南的 Security Filter 链处于不同层级，理解它们的协作关系能帮你做出更精准的架构决策。
