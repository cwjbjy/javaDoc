# Spring Security 入门指南

> 以 **SecurityConfig** 为中心，一次性展示完整配置。每个配置项标注 🟢有默认 / 🟡可选 / 🔴必配。逐行拆解后，深入认证全链路。
>
> 适用版本：Spring Boot 4.x / Spring Security 7.x（lambda DSL），Java 17+

---

## 目录

1. [SecurityConfig 全景](#1-securityconfig-全景)
2. [SecurityConfig 逐行拆解](#2-securityconfig-逐行拆解)
   - [2.1 SecurityFilterChain — Bean 声明与 URL 规则](#21-securityfilterchain--bean-声明与-url-规则)
   - [2.2 PasswordEncoder — 编码器](#22-passwordencoder--编码器)
   - [2.3 AuthenticationManager — 认证入口](#23-authenticationmanager--认证入口)
   - [2.4 Session 管理](#24-session-管理)
   - [2.5 CORS 配置](#25-cors-配置)
   - [2.6 JWT Filter 注册](#26-jwt-filter-注册)
3. [认证全链路](#3-认证全链路)
   - [3.1 依赖与数据源](#31-依赖与数据源)
   - [3.2 用户实体与数据访问](#32-用户实体与数据访问)
   - [3.3 UserDetailsService — 查用户](#33-userdetailsservice--查用户)
   - [3.4 AuthController — 登录接口](#34-authcontroller--登录接口)
   - [3.5 JwtUtil — Token 生成与验证](#35-jwtutil--token-生成与验证)
   - [3.6 JwtAuthenticationFilter — 每次请求认证](#36-jwtauthenticationfilter--每次请求认证)
   - [3.7 CommandLineRunner — 种子用户](#37-commandlinerunner--种子用户)
4. [授权](#4-授权)
   - [4.1 URL 级别](#41-url-级别)
   - [4.2 方法级别](#42-方法级别)
5. [获取当前用户](#5-获取当前用户)
6. [请求流转](#6-请求流转)
7. [速查清单](#7-速查清单)

---

## 1. SecurityConfig 全景

下面是一个完整的 JWT 无状态认证配置——**你先看全貌，后面逐行拆解**。每个 Bean 标注了 🟢🟡🔴，不需要猜"这个是不是必须的"。

需要先引入依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<!-- JWT 库，详见 §3.1 -->
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

**SecurityConfig.java** — 下面每条注解和注释都有含义，§2 会逐一展开：

```java
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

// 自定义的安全配置类——所有 Spring Security 配置（Filter 链、编码器、认证管理器）
//   都通过这个类中的 @Bean 方法声明，取代 Spring Boot 的默认安全自动配置
@Configuration
@EnableWebSecurity          // 🔴 必须加，否则这个类不被识别为安全配置
public class SecurityConfig {

    // JwtUtil 是自定义的 Token 工具类（§3.5）
    // 这里用构造器注入（而非 @Bean 方法参数），是为了演示：
    //   构造器注入 → 字段可被本类所有 @Bean 方法共享
    //   方法参数注入 → 仅该方法可见（如 userDetailsService 就只在 jwtAuthenticationFilter 中使用）
    // 实际项目中 jwtUtil 只在一个方法使用的话，写成方法参数也完全等效
    private final JwtUtil jwtUtil;
    public SecurityConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    // ═══════════════════════════════════════════════════
    // SecurityFilterChain：核心配置入口  🔴 必配
    // ═══════════════════════════════════════════════════
    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            // jwtFilter 是自定义的 JWT 认证 Filter（§3.6）
            JwtAuthenticationFilter jwtFilter) throws Exception {
        http
            // CORS — 🟡 前后端分离才需要
            .cors(Customizer.withDefaults())
            // 关闭 Session — 🟡 JWT 无状态场景启用
            .sessionManagement(session -> session
                    .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // CSRF — 🟡 JWT 不依赖 Cookie，可安全禁用
            .csrf(csrf -> csrf.disable())
            // URL 授权规则 — 🔴 必须定义
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/api/auth/**").permitAll()
                    .requestMatchers("/api/admin/**").hasRole("ADMIN")
                    .anyRequest().authenticated())
            // JWT Filter — 🔴 用 JWT 必须注册
            .addFilterBefore(jwtFilter,
                    UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // ═══════════════════════════════════════════════════
    // JwtAuthenticationFilter（自定义 Filter，详见 §3.6）🔴 JWT 场景必配
    //   —— 定义为 Bean 后，上方的 filterChain 通过方法参数自动注入，
    //       再由 .addFilterBefore() 注册到 Filter 链
    // ═══════════════════════════════════════════════════
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            UserDetailsService userDetailsService) {
        return new JwtAuthenticationFilter(jwtUtil, userDetailsService);
    }

    // ═══════════════════════════════════════════════════
    // AuthenticationManager  🔴  默认构建的只在内部用，配置之后，Controller中才能获取 AuthenticationManager
    // 方便在登录接口中，把用户提交的用户名密码交给他，他去数据库查用户、验密码
    // ═══════════════════════════════════════════════════
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // ═══════════════════════════════════════════════════
    // PasswordEncoder  🔴 必配（默认是明文 ，配置为 BCryptPasswordEncoder 进行加密）
    // ═══════════════════════════════════════════════════
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ═══════════════════════════════════════════════════
    // CorsConfigurationSource  🟡 前后端分离才需要
    // ═══════════════════════════════════════════════════
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

---

## 2. SecurityConfig 逐行拆解

### 2.1 SecurityFilterChain — Bean 声明与 URL 规则

`SecurityFilterChain` 是 Spring Security 的**核心配置入口**。你在这个 Bean 中声明：哪些 URL 放行、哪些需要认证、要插入哪些自定义 Filter。

> **SecurityFilterChain**：接口，代表一条 Filter 链。`HttpSecurity` 是其构建器——你链式调用的 `.csrf()`、`.authorizeHttpRequests()` 等，本质上都是在配置这条 Filter 链中要加入哪些 Filter。

```java
@Bean
public SecurityFilterChain filterChain(
        HttpSecurity http,
        // jwtFilter 是自定义的 JWT 认证 Filter（§3.6）
        JwtAuthenticationFilter jwtFilter) throws Exception {
    http
        // ...

        // authorizeHttpRequests：定义 URL 访问规则，从上到下匹配，命中即停止
        .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()   // 登录接口放行
                .requestMatchers("/api/admin/**").hasRole("ADMIN") // 需要管理员权限的接口
                .anyRequest().authenticated())                  // 兜底：其余需认证

        // addFilterBefore：在指定 Filter 之前插入自定义 Filter
        // UsernamePasswordAuthenticationFilter：Spring Security 内置的表单登录 Filter
        //   把 JWT Filter 插在它前面，确保 JWT 先处理请求
        .addFilterBefore(jwtFilter,
                UsernamePasswordAuthenticationFilter.class);

    return http.build();
}
```

### 2.2 PasswordEncoder — 编码器

`PasswordEncoder` 是 Spring Security 的**密码加密接口**。有两个方法你需要知道：

- `encode(rawPassword)` — 加密明文密码（注册、种子用户时调用）
- `matches(rawPassword, encodedPassword)` — 验证明文是否匹配密文（登录时 AuthenticationManager 内部自动调用）

> **为什么是 🔴 必配？** Spring Security 的默认 `PasswordEncoder` 是 `NoOpPasswordEncoder`——明文存储、明文比对。相当于没有加密。你必须用 `BCryptPasswordEncoder` 替换它。

```java
// PasswordEncoder：密码加密接口
// BCryptPasswordEncoder：自适应哈希实现
//   —— 每次加密结果不同（内含随机盐），抗彩虹表攻击
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

### 2.3 AuthenticationManager — 认证入口

`AuthenticationManager` 是 Spring Security 的**认证总入口**——简单说：Controller 把用户提交的用户名密码交给他，他去数据库查用户、验密码，匹配就通过，不匹配就报错。调用 `authenticationManager.authenticate(认证令牌)` 时——这里的"令牌"是 `UsernamePasswordAuthenticationToken`（装用户名密码的认证请求对象），**不是** JWT 字符串——内部自动执行：

1. 找到合适的 `AuthenticationProvider`（默认是 `DaoAuthenticationProvider`）
2. Provider 调用你的 `UserDetailsService.loadUserByUsername()` 查用户
3. Provider 调用 `PasswordEncoder.matches()` 验密码
4. 全部通过 → 返回已认证的 `Authentication`

> **为什么是 🔴 默认有？** Spring Security 通过 `AuthenticationConfiguration` 自动构建了一个完整的 `AuthenticationManager`——它知道你的 `UserDetailsService` 和 `PasswordEncoder`，无需你手动组装。
>
> **那为什么还要写这个 Bean？** 默认构建的 Manager 只在**内部**使用（表单登录等）。如果你要在 Controller 中注入 `AuthenticationManager` 来手动调用 `authenticate()`（§3.4 登录接口会这样做），就必须显式暴露为 Bean。

```java
// AuthenticationConfiguration：Spring Security 的认证配置类
//   —— 内部持有构建 AuthenticationManager 所需的所有信息
// 这个 Bean 只是把默认构建的 Manager "暴露"出来，让 Controller 可以注入
@Bean
public AuthenticationManager authenticationManager(
        AuthenticationConfiguration config) throws Exception {
    return config.getAuthenticationManager();
}
```

### 2.4 Session 管理

```java
// SessionCreationPolicy：Session 创建策略枚举
//   STATELESS = 不创建 Session，不从 Session 恢复 SecurityContext
//   —— JWT 的核心配置：服务端不存任何状态，每个请求独立认证
.sessionManagement(session -> session
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```

> **🟡 可选**：默认是 `IF_REQUIRED`（按需创建 Session）。JWT 场景设为 `STATELESS` 是最佳实践——否则每次请求仍会创建无用的 Session 对象，浪费内存。

### 2.5 CORS 配置

前后端分离时，浏览器会拦截跨域请求。Spring Security 需要显式开启 CORS 支持：

```java
// ① 在 SecurityFilterChain 中启用 CORS
.cors(Customizer.withDefaults())
```

这会查找容器中的 `CorsConfigurationSource` Bean：

```java
// CorsConfigurationSource：CORS 配置源接口
// CorsConfiguration：具体跨域规则（允许的源、方法、头部）
// UrlBasedCorsConfigurationSource：按路径匹配的实现
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("http://localhost:5173")); // 前端地址
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);        // 允许携带 Cookie/Authorization 头
    config.setMaxAge(3600L);                // 预检请求缓存 1 小时

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}
```

> 生产环境 `allowedOrigins` 必须设为具体域名，不能是 `*`。

### 2.6 JWT Filter 注册

自定义 Filter 必须显式注册到 Filter 链中，否则不会被调用。

```java
@Bean
public JwtAuthenticationFilter jwtAuthenticationFilter(
        // userDetailsService 注入的是自定义的 DatabaseUserDetailsService（§3.3）
        UserDetailsService userDetailsService) {
    return new JwtAuthenticationFilter(jwtUtil, userDetailsService);
}
```

Spring 注入的是**接口类型** `UserDetailsService`，实际拿到的是**唯一实现了该接口的 Bean**——也就是 `@Service` 注解的 `DatabaseUserDetailsService`。如果项目中有多个 `UserDetailsService` 实现，需要加 `@Qualifier` 指定。

> **为什么 `@Bean` 方法的参数不需要 `@Autowired`？** `@Configuration` 类中的 `@Bean` 方法由 Spring 容器调用——容器在调用时会自动从上下文中查找匹配的 Bean 作为参数传入。这是 Spring 的隐式注入机制：`@Autowired` 用于字段和构造器，`@Bean` 方法的参数由容器自动装配，不需要额外注解。

```java
// addFilterBefore(JwtFilter, UsernamePasswordAuthenticationFilter.class)
//   —— 把 JWT Filter 插在内置表单认证 Filter 之前
//   UsernamePasswordAuthenticationFilter：处理表单登录（/login POST）的 Filter
.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
```

> **为什么必须 `addFilterBefore`？** `UsernamePasswordAuthenticationFilter` 是默认存在的——它会拦截 `/login` POST 请求并触发表单认证。如果不把 JWT Filter 插在它前面，表单登录 Filter 可能会抢先处理，导致 JWT 认证失效。

---

## 3. 认证全链路

上面 SecurityConfig 引用了几个不在配置类中的组件：`UserDetailsService`、`JwtUtil`、`JwtAuthenticationFilter`，以及登录接口 `AuthController`。这一节把它们全部补齐。

### 3.1 依赖与数据源

除了 Security 依赖，还需要 JPA + MySQL 做用户持久化，以及 jjwt 做 Token：

```xml
<!-- JPA + MySQL -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- JWT（三个包必须同时引入） -->
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

`application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mydb
    username: root
    password: your_password
  jpa:
    hibernate:
      ddl-auto: update # 开发环境自动建表，生产改 validate
```

### 3.2 用户实体与数据访问

```java
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

// @Entity：标记为 JPA 实体，映射到数据库表
// @Table(name = "users")：指定表名（默认是类名小写 app_user）
@Entity
@Table(name = "users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)  // 自增主键
    private Long id;

    private String username;
    private String password;       // 存 BCrypt 哈希，不存明文
    private Set<String> roles = new HashSet<>();        // 角色（ADMIN、USER 等）
    private Set<String> permissions = new HashSet<>();  // 细粒度权限（ORDER_DELETE、ORDER_READ 等）
    private boolean enabled = true;  // false = 账户禁用

    public AppUser() {}

    public AppUser(String username, String password,
                   Set<String> roles, Set<String> permissions) {
        this.username = username;
        this.password = password;
        this.roles = roles;
        this.permissions = permissions;
    }

    // getters（省略 setter——实体通常只读）
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public Set<String> getRoles() { return roles; }
    public Set<String> getPermissions() { return permissions; }
    public boolean isEnabled() { return enabled; }
}
```

```java
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

// JpaRepository：Spring Data JPA 的接口
//   —— 自动提供 save()、findById()、findAll()、delete() 等 CRUD 方法
//   —— findByUsername：方法名查询，自动生成 SELECT ... WHERE username = ?
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
}
```

### 3.3 UserDetailsService — 查用户

`UserDetailsService` 是 Spring Security 的接口——你只需要实现 `loadUserByUsername` 方法，告诉 Spring Security "用户数据在哪、怎么查"。注册为 `@Service` 后，Spring Security 自动发现并使用。

```java
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

// UserDetailsService：根据用户名加载用户信息的接口
//   —— 只有一个抽象方法 loadUserByUsername，你实现它即可，数据从哪来（数据库/LDAP/外部API）Spring Security 不关心
@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final AppUserRepository repository;

    public DatabaseUserDetailsService(AppUserRepository repository) {
        this.repository = repository;
    }

    // loadUserByUsername：UserDetailsService 接口的唯一方法
    //   —— 输入用户名，输出 UserDetails
    //   —— 找不到用户时必须抛 UsernameNotFoundException
    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {
        AppUser user = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "用户不存在: " + username));

        // 角色加 ROLE_ 前缀，权限不加 —— 分别对应 hasRole() 和 hasAuthority()
        // forEach(authorities::add)：把角色和权限两路流合并到同一个 ArrayList，
        //   不用 .toList() 是因为 toList() 返回不可变列表，无法追加第二路
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .forEach(authorities::add);
        user.getPermissions().stream()
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);

        // User：Spring Security 提供的 UserDetails 实现
        //   User.builder()：构建器模式，把数据库字段映射为认证所需格式
        return User.builder()
                .username(user.getUsername())
                .password(user.getPassword())        // BCrypt 哈希，不传明文
                .authorities(authorities)            // 权限列表
                .disabled(!user.isEnabled())         // 账户是否禁用
                .build();
    }
}
```

### 3.4 AuthController — 登录接口

登录接口用 record 定义请求/响应体：

```java
public record LoginRequest(String username, String password) {}
public record LoginResponse(String token, String username) {}
```

```java
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    // AuthenticationManager：认证总入口（SecurityConfig §2.3 暴露的 Bean）
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    public AuthController(AuthenticationManager authenticationManager,
                          JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        // UsernamePasswordAuthenticationToken：封装"用户名 + 密码"的认证请求对象
        //  authenticate流程： 调用 loadUserByUsername() 根据用户名获取数据库中的用户信息，再进行密码校验
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(), request.password()));

        // auth.getName() → 返回用户名
        String token = jwtUtil.generateToken(auth.getName());
        return ResponseEntity.ok(new LoginResponse(token, request.username()));
    }

    // BadCredentialsException：Spring Security 的认证失败异常
    //   —— 用户名不存在 或 密码错误 时抛出
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<String> handleBadCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body("用户名或密码错误");
    }
}
```

### 3.5 JwtUtil — Token 生成与验证

```java
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtUtil {

    // 至少 256 位密钥（HMAC-SHA256 要求）。
    // 生产环境从配置中心或环境变量读取，禁止硬编码
    private static final String BASE64_SECRET =
            "dGhpc0lzQVNlY3JldEtleUZvckpXVDMyQnl0ZXNTZWNyZXRLZXk=";
    private static final long EXPIRATION_MS = 24 * 60 * 60 * 1000; // 24 小时

    // 将 Base64 密钥解码为 SecretKey
    //   —— 签名和验签必须用同一把密钥，否则验签失败
    // Keys.hmacShaKeyFor：生成 HMAC-SHA256 算法的密钥对象
    private SecretKey getSigningKey() {
        byte[] keyBytes = Base64.getDecoder().decode(BASE64_SECRET);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /** 为指定用户名生成 JWT Token（登录成功后调用） */
    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)                                    // 主题（存用户名）
                .issuedAt(new Date())                                 // 签发时间
                .expiration(new Date(System.currentTimeMillis()
                        + EXPIRATION_MS))                             // 过期时间
                .signWith(getSigningKey())                            // 签名
                .compact();                                           // 输出字符串
    }

    /** 从 Token 中提取用户名 */
    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /** 验证 Token 是否有效：用户名匹配 + 未过期 */
    public boolean isTokenValid(String token, String username) {
        String extractedUsername = extractUsername(token);
        return extractedUsername.equals(username) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return parseClaims(token).getExpiration().before(new Date());
    }

    // Claims：JWT 的载荷（Payload），包含 subject、expiration 等声明
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())      // 用同一把密钥验签
                .build()
                .parseSignedClaims(token)          // 解析 + 验签
                .getPayload();                     // 拿到载荷
    }
}
```

### 3.6 JwtAuthenticationFilter — 每次请求认证

这个 Filter 在**每次 HTTP 请求**时执行：从 `Authorization` 头提取 Bearer Token → 验证 → 将用户信息写入 `SecurityContextHolder`。这样后续的 Controller 和授权检查才能获取当前用户。

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

// OncePerRequestFilter：Spring 提供的 Filter 基类
//   —— 保证每个请求只执行一次（普通 Filter 可能因转发/包含而重复执行）
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
                                   UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // 没有 Authorization 头或不是 Bearer 格式 → 跳过
        // 登录请求、公开接口本来就不带 Token，交给后续 Filter 判断
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7); // 去掉 "Bearer " 前缀（7 个字符）

        try {
            String username = jwtUtil.extractUsername(token);

            // SecurityContextHolder：基于 ThreadLocal 的安全上下文持有器
            //   —— 每个请求有独立的 SecurityContext，请求结束自动清理
            // getContext().getAuthentication() == null → 当前请求还没认证
            if (username != null
                    && SecurityContextHolder.getContext()
                            .getAuthentication() == null) {

                UserDetails userDetails =
                        userDetailsService.loadUserByUsername(username);

                if (jwtUtil.isTokenValid(token, userDetails.getUsername())) {
                    // 第一个参数：用户信息，第二个参数：null，因为token已经认证，不需要再传密码验证
                    // 第三个参数：传入 UserDetailsService 中通过 authorities(authorities) 设置的用户权限列表
                    // 放到上下文中，方便后续使用 @PreAuthorize 等注解进行权限控制
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails, null,
                                    userDetails.getAuthorities());

                    // WebAuthenticationDetailsSource：构建认证详情
                    //   —— 附加客户端 IP、Session ID 等信息，供审计/日志使用
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request));

                    // 写入安全上下文 → 后续 Filter 和 Controller 都可以取到当前用户
                    SecurityContextHolder.getContext()
                            .setAuthentication(authToken);
                }
            }
        } catch (JwtException e) {
            // JwtException：jjwt 库的异常基类（过期/签名无效/格式错误）
            //   不抛出去——否则异常穿透 Filter 链导致 500
            //   清空上下文 → 放行 → 后续 AuthorizationFilter 发现无认证 → 返回 401
            log.debug("JWT 解析失败: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
```

> **为什么 catch 后不抛异常？** `JwtException` 不是 Spring Security 的异常，`ExceptionTranslationFilter` 不认识它 → 不会被自动转为 401。正确的做法是：catch 住 → 不设置认证 → 放行 → 后续 `AuthorizationFilter` 发现 `SecurityContextHolder` 中没有认证信息 → 自动返回 401。

### 3.7 CommandLineRunner — 种子用户

启动时预置一个用户到数据库，否则 JWT 登录时数据库为空，无法验证任何用户：

```java
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Set;

@Bean
public CommandLineRunner seedUsers(AppUserRepository repository,
                                   PasswordEncoder encoder) {
    // encoder 注入的是 SecurityConfig 中定义的 BCryptPasswordEncoder
    return args -> {
        if (repository.findByUsername("admin").isEmpty()) {
            repository.save(new AppUser(
                    "admin",
                    encoder.encode("admin123"),
                    Set.of("ADMIN"),                              // 角色
                    Set.of("ORDER_DELETE", "ORDER_READ")));       // 权限
        }
        if (repository.findByUsername("user1").isEmpty()) {
            repository.save(new AppUser(
                    "user1",
                    encoder.encode("user123"),
                    Set.of("USER"),                               // 角色：普通用户
                    Set.of("ORDER_READ")));                        // 权限：只能读订单，不能删
        }
    };
}
```

> `findByUsername().isEmpty()` 保证只在首次启动时插入，重启不会重复创建。

---

## 4. 授权

认证回答"你是谁"，授权回答"你能干什么"。Spring Security 提供两层授权——URL 级别（在 Filter 链中拦截）和方法级别（在方法执行前后拦截）。

### 4.1 URL 级别

在 `SecurityFilterChain` 的 `authorizeHttpRequests` 中定义：

```java
http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/**", "/public/**").permitAll()     // 公开
    .requestMatchers("/api/admin/**").hasRole("ADMIN")             // 管理员
    .requestMatchers("/api/reports/**").hasAnyRole("ADMIN", "MANAGER") // 多角色
    .requestMatchers(HttpMethod.GET, "/api/foods/**").permitAll()  // GET 公开
    .requestMatchers(HttpMethod.POST, "/api/foods/**").hasRole("ADMIN") // POST 限管理员
    .anyRequest().authenticated()                                  // 兜底
);
```

> **`hasRole()` 与 `ROLE_` 前缀**：`hasRole("ADMIN")` 内部检查的权限字符串是 `ROLE_ADMIN`。这就是为什么 §3.3 中 `SimpleGrantedAuthority` 要用 `"ROLE_" + role`。如果用 `hasAuthority("ORDER_DELETE")`，则直接匹配 `ORDER_DELETE`，不加 `ROLE_` 前缀。

### 4.2 方法级别

URL 授权是"挡在门外"，方法级授权是"审到房内"。需要先在 `SecurityConfig` 上添加 `@EnableMethodSecurity`：

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity    // 启用方法级授权（@PreAuthorize / @PostAuthorize 生效的前提）
public class SecurityConfig { /* ... */ }
```

#### 角色检查

`hasRole` 控制"谁能进入这个模块"（粗粒度）

```java
import org.springframework.security.access.prepost.PreAuthorize;

// @PreAuthorize：方法执行前检查权限
//   —— 参数是 SpEL 表达式，hasRole('ADMIN') 检查当前用户是否有 ADMIN 角色
//   —— 不满足则抛出 AccessDeniedException → 被异常转换 Filter 转为 403
@PreAuthorize("hasRole('ADMIN')")
@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteFood(@PathVariable String id) {
    return ResponseEntity.noContent().build();
}
```

#### 权限检查

`hasAuthority` 控制"进入模块后能做什么"（细粒度）

```java
// hasAuthority：直接匹配权限字符串，不加 ROLE_ 前缀
//   —— 比 hasRole 更细粒度，适合"增删改查"级别的权限控制
@PreAuthorize("hasAuthority('ORDER_DELETE')")
@DeleteMapping("/orders/{id}")
public ResponseEntity<Void> deleteOrder(@PathVariable String id) {
    return ResponseEntity.noContent().build();
}

// 多个权限满足其一即可
@PreAuthorize("hasAnyAuthority('ORDER_READ', 'ORDER_DELETE')")
@GetMapping("/orders/{id}")
public ResponseEntity<Order> getOrder(@PathVariable String id) {
    // ...
}
```

#### 数据归属检查

权限检查回答"你能做什么操作"（删除、修改），但回答不了"你能操作谁的数据"。比如普通用户 A 和 B 都有 `USER` 角色，权限检查无法阻止 A 查看 B 的订单——这时需要数据归属检查。

SpEL 表达式中可用的变量：`#参数名` 引用方法参数，`authentication` 引用当前认证对象：

以下示例聚焦授权注解本身，省略了 JPA Repository 注入和数据库查询（用 `List.of()` 和硬编码返回值占位）——实际项目中替换为 `orderRepository.findByUserId(userId)` 即可。

```java
import org.springframework.security.access.prepost.PostAuthorize;

// Order 实体（简化示例）
public record Order(Long id, String userId) {}

@Service
public class OrderService {

    // @PreAuthorize：方法执行前检查
    //   #userId → 方法参数 userId
    //   authentication.principal.username → 当前登录用户名
    @PreAuthorize("#userId == authentication.principal.username")
    public List<Order> findOrdersByUser(String userId) {
        // 实际项目：return orderRepository.findByUserId(userId);
        return List.of();
    }

    // @PostAuthorize：方法执行后检查（先查数据，再验证返回值归属）
    //   returnObject → 方法的返回值
    @PostAuthorize("returnObject.userId == authentication.principal.username")
    public Order findOrderById(Long id) {
        // 实际项目：return orderRepository.findById(id).orElseThrow();
        return new Order(id, "demoUser");
    }
}
```

#### 复合条件

```java
// 管理员或本人可查看
@PreAuthorize("hasRole('ADMIN') or #username == authentication.principal.username")
public String getProfile(String username) {
    return "profile of " + username;
}

// 管理员 + 金额限制
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER') and #amount <= 10000")
public void approveRefund(Long orderId, BigDecimal amount) {
    // 退款审批逻辑
}
```

> **`@PreAuthorize` vs `@PostAuthorize`**：能用 `@PreAuthorize` 就别用 `@PostAuthorize`——先拦截更高效。`@PostAuthorize` 只在"必须加载数据后才能判断"的场景使用（如判断 `returnObject.owner == currentUser`）。

---

## 5. 获取当前用户

认证成功后，当前用户信息存储在 `SecurityContextHolder`（基于 `ThreadLocal`，请求结束自动清理）中。有两种方式获取：

```java
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;

@RestController
public class UserController {

    // 方式一：@AuthenticationPrincipal（推荐，仅 Controller 可用）
    //   —— Spring 自动从 SecurityContextHolder 取出 Authentication.getPrincipal()
    @GetMapping("/api/me")
    public ResponseEntity<String> currentUser(
            @AuthenticationPrincipal UserDetails user) {
        return ResponseEntity.ok("当前用户: " + user.getUsername());
    }

    // 方式二：手动从 SecurityContextHolder 获取（任何层级可用）
    //   SecurityContextHolder.getContext() → SecurityContext
    //   SecurityContext.getAuthentication() → Authentication
    //   Authentication.getName() → 用户名
    @GetMapping("/api/profile")
    public ResponseEntity<String> profile() {
        Authentication auth =
                SecurityContextHolder.getContext().getAuthentication();
        return ResponseEntity.ok("当前用户: " + auth.getName());
    }
}
```

> Controller 层推荐 `@AuthenticationPrincipal`，简洁且可测试。Service 层如需当前用户，更建议将用户名作为参数从 Controller 传下来，避免业务层直接依赖 Security 上下文。

---

## 6. 请求流转

整个认证-授权链路在一次登录和一次业务请求中如何运转：

```
① 登录：获取 Token

POST /api/auth/login  { "username": "admin", "password": "admin123" }
    │
    ▼
JwtAuthenticationFilter
    │  （无 Authorization 头 → 跳过，放行）
    ▼
AuthController.login()
    │ → authenticationManager.authenticate()
    │     → Provider → DatabaseUserDetailsService.loadUserByUsername("admin")
    │     → PasswordEncoder.matches("admin123", BCrypt哈希)
    │     → 验证通过，返回 Authentication
    ▼
jwtUtil.generateToken("admin")
    │  （返回 JWT：eyJhbGciOi...）
    ▼
客户端收到 Token，存入 localStorage


② 后续请求：携带 Token

GET /api/foods   Authorization: Bearer eyJhbGciOi...
    │
    ▼
JwtAuthenticationFilter
    │  （提取 Token → 验签 → 查 UserDetails → 设 SecurityContext）
    ▼
AuthorizationFilter
    │  （检查 URL 授权规则 → 放行）
    ▼
DispatcherServlet → FoodController
    │  （@AuthenticationPrincipal 或 SecurityContextHolder 获取用户）
```

验证：

```bash
# ① 登录
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
# → {"token":"eyJhbGciOi...","username":"admin"}

# ② 携带 Token 访问受保护接口
curl http://localhost:8080/api/foods \
  -H "Authorization: Bearer eyJhbGciOi..."
# → 200 OK
```

---

## 7. 速查清单

### 7.1 SecurityFilterChain 配置速查

| 配置项       | Lambda DSL                                                                | 作用                 |
| ------------ | ------------------------------------------------------------------------- | -------------------- |
| 关闭 Session | `.sessionManagement(s -> s.sessionCreationPolicy(STATELESS))`             | JWT 无状态           |
| 关闭 CSRF    | `.csrf(csrf -> csrf.disable())`                                           | JWT 不依赖 Cookie    |
| 公开端点     | `.requestMatchers("/api/auth/**").permitAll()`                            | 登录接口             |
| 角色限制     | `.requestMatchers("/admin/**").hasRole("ADMIN")`                          | 管理员               |
| 方法区分     | `.requestMatchers(GET, "/api/foods/**").permitAll()`                      | 按 HTTP 方法         |
| 兜底规则     | `.anyRequest().authenticated()`                                           | 其余需认证（放最后） |
| 插入 Filter  | `.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)` | JWT 认证             |
| CORS         | `.cors(Customizer.withDefaults())` + `CorsConfigurationSource` Bean       | 跨域                 |

### 7.2 默认值 vs 必配速查

| 组件                    | 默认行为                                            | 是否必配              |
| ----------------------- | --------------------------------------------------- | --------------------- |
| `@EnableWebSecurity`    | —                                                   | 🔴 必配               |
| `SecurityFilterChain`   | 全部锁定 + 表单登录 + Basic                         | 🔴 必配               |
| `PasswordEncoder`       | `NoOpPasswordEncoder`（明文）                       | 🔴 必配为 BCrypt      |
| `AuthenticationManager` | 自动构建（含 UserDetailsService + PasswordEncoder） | 🟢 默认有；注入需暴露 |
| Session                 | `IF_REQUIRED`（创建 Session）                       | 🟡 JWT 改 STATELESS   |
| CSRF                    | 默认启用（基于 Cookie+Session）                     | 🟡 JWT 可关           |
| CORS                    | 默认拒绝跨域                                        | 🟡 前后端分离需配     |

### 7.3 @PreAuthorize 常用表达式

| 表达式                                                               | 含义                                   |
| -------------------------------------------------------------------- | -------------------------------------- |
| `hasRole('ADMIN')`                                                   | 持有 ADMIN 角色                        |
| `hasAnyRole('ADMIN', 'MANAGER')`                                     | 持有任一角色                           |
| `hasAuthority('ORDER_DELETE')`                                       | 持有指定权限（无 `ROLE_` 前缀）        |
| `#userId == authentication.principal.username`                       | 方法参数等于当前用户名                 |
| `returnObject.owner == authentication.name`                          | 返回值属于当前用户（`@PostAuthorize`） |
| `hasRole('ADMIN') or #username == authentication.principal.username` | 管理员或本人                           |
| `@customBean.check(#id)`                                             | 调用自定义 Bean 做权限判断             |

### 7.4 hasRole vs hasAuthority

| 写法                        | 内部检查的字符串     | 用户定义方式                           |
| --------------------------- | -------------------- | -------------------------------------- |
| `hasRole("ADMIN")`          | `ROLE_ADMIN`         | `SimpleGrantedAuthority("ROLE_ADMIN")` |
| `hasAuthority("ADMIN")`     | `ADMIN`              | `SimpleGrantedAuthority("ADMIN")`      |
| `hasAnyRole("A", "B")`      | `ROLE_A` 或 `ROLE_B` | 同上                                   |
| `hasAnyAuthority("A", "B")` | `A` 或 `B`           | 同上                                   |

> 角色用 `hasRole`（粗粒度：ADMIN、USER），权限用 `hasAuthority`（细粒度：ORDER_DELETE、ORDER_READ）。

### 7.5 关键类速查

| 类 / 接口                             | 职责                                  | 何时使用                                 |
| ------------------------------------- | ------------------------------------- | ---------------------------------------- |
| `SecurityFilterChain`                 | Filter 链配置入口                     | 总是——定义 URL 规则和 Filter             |
| `HttpSecurity`                        | 安全配置构建器                        | 在 `SecurityFilterChain` Bean 中链式调用 |
| `UserDetailsService`                  | 查用户接口（重写 loadUserByUsername） | 自定义用户来源                           |
| `UserDetails`                         | 用户信息接口                          | `UserDetailsService` 的返回值            |
| `AuthenticationManager`               | 认证总入口                            | 登录接口调用 `.authenticate()`           |
| `UsernamePasswordAuthenticationToken` | 认证令牌                              | 封装用户名密码 / 已认证信息              |
| `PasswordEncoder`                     | 密码加密/验证                         | 存密码（encode）、验密码（matches）      |
| `BCryptPasswordEncoder`               | 自适应哈希实现                        | 生产环境密码加密首选                     |
| `SecurityContextHolder`               | 安全上下文持有器                      | 获取当前用户                             |
| `OncePerRequestFilter`                | Filter 基类                           | 自定义 Filter（如 JWT）                  |
| `AuthenticationConfiguration`         | 认证配置                              | 获取默认 `AuthenticationManager`         |

---

> 需要深入了解 Filter 链内部顺序、自定义 AuthenticationProvider、异步上下文传递等进阶内容，请参阅 [Spring Security 企业级指南](spring-security-guide.md)。
