# `@Cacheable` 与 Caffeine：Spring Boot 本地缓存指南

> 适用版本：Java 17、Spring Boot 4.0.6。
> 面向读者：会使用 Spring Boot 的 Service 与依赖注入，但刚开始接触本地缓存的开发者。
> 学完后：能够说清 `@Cacheable` 与 Caffeine 的分工，让 Caffeine 成为 `@Cacheable` 的底层实现，理解 Caffeine 的淘汰、过期与刷新策略，并在查询、更新、删除与并发场景中做出正确的缓存决策。
> 范围：本文讨论单个应用实例内的本地缓存，包括注解用法进阶、Caffeine 核心策略与缓存一致性问题；Redis 等分布式缓存仅用于说明选型边界。更完整的常见陷阱与上线检查清单见 [spring-cache-caffeine-guide.md](spring-cache-caffeine-guide.md)。

本文的核心结论只有一句：**`@Cacheable` 只定义缓存规则，不决定数据存在哪里；真正选择并操作存储介质的是 Spring 的 `CacheManager`，而 Caffeine 既是它最常见的底层实现之一，也可以被直接独立使用。**

---

## 目录

1. [先建立正确的关系](#1-先建立正确的关系)
2. [依赖与最小配置](#2-依赖与最小配置)
   - [2.2.1 装配链路：三个角色如何连通](#221-装配链路三个角色如何连通)
3. [三种缓存注解：@Cacheable、@CachePut、@CacheEvict](#3-三种缓存注解cacheablecacheputcacheevict)
4. [注解进阶：条件、组合与并发加载](#4-注解进阶条件组合与并发加载)
5. [Caffeine 缓存策略详解](#5-caffeine-缓存策略详解)
6. [直接使用 Caffeine](#6-直接使用-caffeine)
7. [缓存一致性问题与应对](#7-缓存一致性问题与应对)
8. [生产配置、监控与测试](#8-生产配置监控与测试)
9. [如何选择](#9-如何选择)
10. [参考资料](#10-参考资料)

---

## 1. 先建立正确的关系

缓存解决的是“相同数据被频繁、重复地计算或查询”的问题。以查询商品详情为例，数据库查询可能是一次 I/O；在有效期内，如果直接返回内存中的结果，就能减少延迟和数据库压力。

`@Cacheable` **只定义缓存规则，不决定数据存在哪里**：它声明“用什么键缓存这个方法的返回值、何时更新、何时失效”。真正选择并操作存储介质的是 Spring 的 `CacheManager`；Caffeine、Redis 或普通内存 `Map` 都可以成为它的底层实现。

```text
@Cacheable / @CachePut / @CacheEvict
  │ 定义方法结果的读、写、失效规则
  ▼
Spring Cache 抽象
  ▼
CacheManager（决定“存到哪里”）
  ├── ConcurrentMapCacheManager → 当前 JVM 的并发 Map
  ├── CaffeineCacheManager      → 当前 JVM 的 Caffeine 缓存
  └── RedisCacheManager         → Redis 共享缓存
```

### 1.1 不指定缓存提供者时，默认存到哪里？

在 Spring Boot 中，先用 `@EnableCaching` 启用 `@Cacheable` 等注解能力。如果此时没有引入 Spring Boot 可识别的缓存提供者、也没有自定义 `CacheManager`，Spring Boot 会自动配置 `ConcurrentMapCacheManager`（官方文档称其为 simple provider，内部用 `ConcurrentHashMap` 存储）。缓存条目保存在当前 JVM 堆内存的并发 `Map` 中。

它很适合开发、测试或极简场景，但官方明确不建议将其用于生产：它没有 Caffeine 提供的明确容量上限、过期淘汰和统计能力。只引入 `spring-boot-starter-cache` 并不意味着已经在使用 Caffeine。

### 1.2 让 Caffeine 成为 `@Cacheable` 的底层实现

引入 Caffeine 并由 `CaffeineCacheManager` 管理后，`@Cacheable` 仍只负责规则；`CaffeineCacheManager` 才会把 Spring 的读写请求转换为对 Caffeine 的操作：

```text
调用方
  │ 调用 ProductService.findById(id)
  ▼
Spring Cache 抽象（@Cacheable）
  │ 命中：直接返回方法上次的结果
  │ 未命中：调用原方法，并将返回值写入 Cache
  ▼
CaffeineCacheManager / Caffeine
  │ 在当前 JVM 内存中维护 key → value
  ▼
商品仓库、数据库或远程服务
```

两个角色可以这样对比：

| 维度         | `@Cacheable`                                          | Caffeine                                       |
| ------------ | ----------------------------------------------------- | ---------------------------------------------- |
| 本质         | Spring 提供的缓存方法返回值的注解式抽象               | 一个具体的本地缓存库                           |
| 操作对象     | 被 Spring 代理的方法及其返回值                        | 任意 `key → value` 数据                        |
| 调用方式     | 在方法上声明 `@Cacheable`、`@CachePut`、`@CacheEvict` | 在代码中调用 `get`、`put`、`invalidate` 等 API |
| 是否自行存储 | 否，通过 `CacheManager` 委托给具体实现                | 是，数据默认保存在当前 JVM 堆内存              |
| 适合场景     | 大多数“查询方法结果缓存”                              | 需要手动控制读写失效，或缓存非方法结果         |
| 二者关系     | 可使用 Caffeine 作为底层实现                          | 可单独使用，也可支撑 `@Cacheable`              |

因此不要把它们当作只能二选一的技术：`@Cacheable + Caffeine` 是单体应用中很常见的组合。

---

## 2. 依赖与最小配置

### 2.1 Maven 依赖

在 `pom.xml` 的 `<dependencies>` 中添加下面两个依赖：

```xml
<!-- Spring Cache 抽象、@Cacheable / @CachePut / @CacheEvict 等注解 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>

<!-- 具体的高性能本地缓存实现 -->
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

本项目使用 Spring Boot 4.0.6，**不要手动指定 Caffeine 的版本**：交给 Spring Boot 的依赖管理，可以避免 Spring 集成层与 Caffeine 版本不兼容。

`spring-boot-starter-cache` 提供缓存抽象与注解支持；`caffeine` 才提供真正执行淘汰、过期与内存存储的实现。只引入前者时，默认会落到 [1.1](#11-不指定缓存提供者时默认存到哪里) 所述的 `ConcurrentMapCacheManager`。

需要说明的是：只要 Caffeine 出现在类路径上，Spring Boot 也能自动配置 `CaffeineCacheManager`（无需手写 `@Bean`）。但自动配置只提供默认策略；想明确控制容量与过期时间时，应像下一节那样显式声明。

### 2.2 启用 Spring Cache，并指定 Caffeine

下面配置把名为 `product` 的缓存交给 `CaffeineCacheManager` 管理。`@EnableCaching` 负责启用注解驱动的缓存代理。这里显式声明 `CacheManager`，让容量和过期策略清晰可控；它正是上一节所述“存到哪里”的实现选择。

> **说明性片段**：省略了应用启动类与包结构；可放入应用扫描路径内的 `@Configuration` 类。

```java
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfiguration {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("product");
        manager.setCaffeine(Caffeine.newBuilder()
            .initialCapacity(100)
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .recordStats());
        manager.setAllowNullValues(false);
        return manager;
    }
}
```

这组策略的含义是：缓存初始容量 100，最多保存 10,000 个条目；每个条目写入 10 分钟后过期；统计信息会被收集，便于在代码或监控中读取命中率。容量与过期时间必须根据数据量、可接受的陈旧时间和 JVM 堆内存调整，而不是照搬示例值。

将 `allowNullValues` 设为 `false` 后，Spring 不会用内部占位对象缓存 `null`；第 3 节的注解示例也会用 `unless` 显式跳过空结果。Caffeine 的直接 API 不允许 `null` 键和值。

官方建议不要把 `@EnableCaching` 放在主启动类上：那样缓存会成为强制特性，连运行测试套件时也不例外。放在独立的 `@Configuration` 类（如本例）中更合适。

### 2.2.1 装配链路：三个角色如何连通

上面的配置完成后，`@Cacheable` 之所以能"找到"并"使用" `CacheManager`，依赖的是一条由 Spring 自动装配的链路：

```text
┌──────────────────┐       ┌──────────────────────┐       ┌─────────────────────┐
│  @EnableCaching  │──────▶│  CacheInterceptor     │──────▶│  CacheManager Bean  │
│  注册 AOP 切面    │       │  拦截 @Cacheable 方法 │       │  你定义的 Bean       │
└──────────────────┘       └──────────────────────┘       └─────────────────────┘
         ①                        ②                              ③
```

1. **`@EnableCaching`** 向容器注册一个 `CacheInterceptor`（本质是一个 AOP 切面），并触发对 `CacheManager` Bean 的查找。
2. **`CacheInterceptor`** 拦截所有标注了 `@Cacheable`、`@CachePut`、`@CacheEvict` 的**方法调用**——它拦截的是 Service 方法，不是 `CacheManager` 配置本身。
3. 拦截之后，`CacheInterceptor` 按类型找到容器中的 `CacheManager` Bean（本例即 `CaffeineCacheManager`），通过它获取对应名称的 `Cache` 实例，执行查缓存 / 放缓存 / 删缓存的操作。

三者缺一不可：

- **没有 `@EnableCaching`**：容器里有 `CacheManager` Bean，但没有 AOP 切面去拦截 `@Cacheable`，注解完全不生效——方法每次都会直接执行。
- **有 `@EnableCaching` 但没有自定义 `CacheManager` Bean**：Spring Boot 自动配置会创建一个默认实现（有 Caffeine 依赖就用 `CaffeineCacheManager`，否则用 `ConcurrentMapCacheManager`，见 [1.1](#11-不指定缓存提供者时默认存到哪里)）。
- **容器中存在多个 `CacheManager` Bean**：`CacheInterceptor` 不知道该注入哪个，需要用 `@Primary` 标记默认的那个，或在 `@EnableCaching` 的配置类中重写 `cacheManager()` 方法明确指定。

### 2.3 `product` 是缓存名称，不是缓存键

`new CaffeineCacheManager("product")` 中的 `product` 是**缓存名称**（也常称缓存区域或缓存桶）。它预先定义了由这个 `CacheManager` 管理的一块逻辑缓存；它不是商品 ID，也不是 Caffeine 的缓存键。

```text
CaffeineCacheManager("product")
  └── 缓存名称：product
        ├── 键：42
        └── 值：Product(id=42, ...)
```

因此，下面注解中的两个字符串职责不同：

```java
@Cacheable(cacheNames = "product", key = "#productId")
public Product findById(Long productId) {
    // productId 为 42 时，实际写入 product 缓存中的键为 42。
}
```

- `cacheNames = "product"`：从当前 `CacheManager` 选择名为 `product` 的缓存。
- `key = "#productId"`：使用方法参数 `productId` 计算该缓存中的具体键。

因为本节定义的 Bean 是 `CaffeineCacheManager`，Spring 代理会执行 `cacheManager.getCache("product")`，得到由 Caffeine 支撑的缓存，再按 `key` 读写数据。`cacheNames` 本身并不指定 Caffeine；换成 `ConcurrentMapCacheManager` 或 `RedisCacheManager` 时，相同的 `cacheNames` 会落到相应实现中。

---

## 3. 三种缓存注解：`@Cacheable`、`@CachePut`、`@CacheEvict`

Spring Cache 提供三个注解，分别对应缓存的**读、写、删**三种操作。它们不决定数据存在哪里（那是 `CacheManager` 的职责），只定义“什么时候查缓存、什么时候写缓存、什么时候删缓存”。

先看全貌：

| 注解          | 操作   | 方法是否每次都执行 | 典型场景         |
| ------------- | ------ | ------------------ | ---------------- |
| `@Cacheable`  | 读缓存 | 否——命中时跳过方法 | 查询商品详情     |
| `@CachePut`   | 写缓存 | 是——每次都执行     | 更新后刷新缓存   |
| `@CacheEvict` | 删缓存 | 是——默认成功后删   | 删除数据后清缓存 |

> **说明性片段**：以下三个方法的 `Product`、`UpdateProductCommand` 与 `ProductRepository` 为示例领域类型；方法均由外部 Bean 调用。

### 3.1 `@Cacheable`：读缓存——命中就跳过方法

`@Cacheable` 用于查询方法。调用前先查缓存：命中就直接返回缓存值，方法**不执行**；未命中才执行方法，并把返回值写入缓存。

```text
调用 findById(42)
  │
  ├── 用 key=42 查缓存
  │     ├── 命中 → 直接返回缓存中的 Product，方法不执行
  │     └── 未命中 → 执行 findById
  │                    ├── 返回非 null → 写入缓存，再返回
  │                    └── 返回 null → unless 条件成立，不写缓存
  ▼
调用方得到 Product 或 null
```

```java
@Cacheable(
    cacheNames = "product",   // 从 CacheManager 选择名为 product 的缓存
    key = "#productId",       // 用方法参数作为缓存键
    unless = "#result == null" // 方法执行后：空结果不写入缓存
)
public Product findById(Long productId) {
    return productRepository.findById(productId).orElse(null);
}
```

三个属性的职责：

- `cacheNames`：选择 `CacheManager` 中的哪一块缓存。名称拼错会导致读写落到不同缓存。
- `key`：用 SpEL 指定缓存键。不写时 Spring 用默认规则（无参 → `SimpleKey.EMPTY`；单参 → 该参数；多参 → 包含全部参数的 `SimpleKey`）。建议显式写出。
- `unless`：方法执行**后**判断，阻止不符合条件的结果写入缓存。是否缓存空值是业务决定——缓存空值能减少反复查询不存在数据的开销（见 [7.2](#72-缓存穿透查询不存在的数据)），但也可能让新建数据短时间内不可见。

`@Cacheable` 要求方法有返回值（`void` 方法无法缓存）。

### 3.2 `@CachePut`：写缓存——每次都执行方法

`@CachePut` 用于更新或新建方法。**每次都执行方法**，执行完把返回值写入缓存，覆盖旧值。它不会查缓存、也不会跳过方法。

```text
调用 update(command)
  │
  ├── 执行方法（每次都执行！）
  │     └── 返回更新后的 Product
  ├── 用 key=command.productId() 把返回值写入缓存
  ▼
调用方得到最新 Product
```

```java
@CachePut(cacheNames = "product", key = "#command.productId()")
public Product update(UpdateProductCommand command) {
    return productRepository.save(command.toProduct());
}
```

典型场景：更新数据后立即刷新缓存，让下一次 `@Cacheable` 查询拿到最新值。

`@CachePut` 的 `key` 可以用方法参数（如本例的 `#command.productId()`），也可以用返回值 `#result`（适合参数里没有键的场景，例如新建方法返回数据库生成的 ID 时，用 `key = "#result.id()"`）。

### 3.3 `@CacheEvict`：删缓存——移除指定键

`@CacheEvict` 用于删除或失效场景。默认先执行方法，成功后移除缓存条目。

```text
调用 deleteById(42)
  │
  ├── 执行方法（删除数据库记录）
  │     └── 成功
  ├── 用 key=42 移除缓存条目
  ▼
下一次 findById(42) 会缓存未命中，回源查询新数据
```

```java
@CacheEvict(cacheNames = "product", key = "#productId")
public void deleteById(Long productId) {
    productRepository.deleteById(productId);
}
```

`@CacheEvict` 可以用 `void` 方法。两个额外属性：

- `beforeInvocation = true`：在方法执行**前**就删缓存（默认是成功后才删）。适合“无论方法成败都要先失效”的场景，但要意识到方法失败时缓存已经被清空。
- `allEntries = true`：清空整个缓存区域（此时 `key` 被忽略）。适合列表类缓存这种无法精确推算键的场景，但下一次访问会全部重新加载。

### 3.4 不要混用 `@CachePut` 和 `@Cacheable`

不要在同一方法上同时放 `@CachePut` 和 `@Cacheable`：前者强制每次都执行方法，后者会在缓存命中时跳过方法——行为互相矛盾，Spring 官方明确不推荐。

注意：注解能力由 Spring AOP 代理实现，只有**外部 Bean 跨代理调用**才会被拦截；同类自调用（`this.findById(...)`）不会经过代理、缓存不生效。相关陷阱的完整清单见 [spring-cache-caffeine-guide.md](spring-cache-caffeine-guide.md) 第 6 节。

---

## 4. 注解进阶：条件、组合与并发加载

### 4.1 condition 与 unless：一个判断时机，两种职责

`condition` 与 `unless` 都用 SpEL 表达式，但**评估时机不同**：

| 属性        | 评估时机       | 为 `false` 时的行为                                | 能否用 `#result`           |
| ----------- | -------------- | -------------------------------------------------- | -------------------------- |
| `condition` | 方法调用**前** | 完全跳过缓存：不查缓存、每次都调用方法、也不写缓存 | 不能（结果还不存在）       |
| `unless`    | 方法调用**后** | 只否决写入：仍然先查缓存，方法执行后不把结果写进去 | 能（`#result` 代表返回值） |

```java
// 说明性片段：只在商品 ID 为正时参与缓存；空结果不写入。
@Cacheable(
    cacheNames = "product",
    key = "#productId",
    condition = "#productId > 0",
    unless = "#result == null"
)
public Product findById(Long productId) { ... }
```

一个常见误用：在 `condition` 里引用 `#result`——评估时方法还没执行，`#result` 不存在。另一个细节：如果方法返回 `Optional<T>`，`#result` 指向的是 `T` 本身而不是 `Optional`，判空要写 `#result == null`（或安全导航 `#result?.price`），不需要对 `Optional` 再解包。

### 4.2 SpEL 上下文与键表达式

注解里的 `key`、`condition`、`unless` 等属性接受 SpEL 表达式——可以理解为"写在字符串里的小段 Java 代码"。它能访问两类变量：

**① 方法参数（最常用）**

直接用 `#参数名` 引用方法的形参，这是 90% 的场景：

```java
// 用参数 productId 的值作为缓存键
@Cacheable(cacheNames = "product", key = "#productId")
public Product findById(Long productId) { ... }

// 多参数时拼接键
@Cacheable(cacheNames = "product", key = "#shopId + ':' + #productId")
public Product findById(Long shopId, Long productId) { ... }
```

如果编译时没有开启 `-parameters`（参数名不可用），可以改用按索引的 `#p0`、`#p1` 或 `#a0`、`#a1`（含义相同，第一个参数 = `#p0` = `#a0`）。

**② 方法返回值（仅 `unless` 和 `@CachePut` 可用）**

`#result` 代表方法的返回值，只能在方法执行**后**才能访问到，`unless` 是常用场景

```java
// 空结果不缓存
@Cacheable(cacheNames = "product", key = "#productId", unless = "#result == null")
public Product findById(Long productId) { ... }

// 取返回值的属性
@Cacheable(cacheNames = "product", key = "#productId", unless = "#result?.price == null")
public Product findById(Long productId) { ... }
```

**③ 方法元信息（偶尔用）**

当参数不够用时，还可以访问方法本身的元信息：

| 变量                           | 含义                 | 典型用途                                       |
| ------------------------------ | -------------------- | ---------------------------------------------- |
| `#root.methodName`             | 方法名               | 拼接键：`key = "#root.methodName + ':' + #id"` |
| `#root.targetClass.simpleName` | 目标类名             | 自动生成前缀                                   |
| `#root.args[0]`                | 第一个参数（按索引） | 参数名不可用时的备选                           |
| `#root.caches[0].name`         | 当前缓存名           | 日志、调试                                     |

实际上，大部分场景只需要 `#参数名` 和 `#result` 就够了。

**SpEL 里还能做什么**：取对象属性（`#shop.region`）、调用静态方法（`T(java.util.UUID).randomUUID()`）都可以。`key` 与 `keyGenerator` 互斥，同时指定会抛异常；需要可复用的复杂键算法时，实现 `KeyGenerator` 接口并用 `keyGenerator` 引用它。

### 4.3 @CacheConfig 与 @Caching：减少重复、组合操作

一个类里多个方法都用同一个 `cacheNames` 时，可以用类级 `@CacheConfig` 集中声明：

```java
// 说明性片段：类级 cacheNames，方法级只写关键差异。
@Service
@CacheConfig(cacheNames = "product")
public class ProductService {

    @Cacheable(key = "#productId")
    public Product findById(Long productId) { ... }

    @CacheEvict(key = "#productId")
    public void deleteById(Long productId) { ... }
}
```

`@CacheConfig` 可以共享 `cacheNames`、`keyGenerator`、`cacheManager`、`cacheResolver`；方法级的同名属性会覆盖类级。它本身**不开启**任何缓存能力，`@EnableCaching` 仍然必需。

当一个方法需要**多个**缓存操作（例如删一个键、再清另一个缓存）时，用 `@Caching` 组合：

```java
// 说明性片段：删除商品时，同时清理详情缓存与列表缓存。
@Caching(evict = {
    @CacheEvict(cacheNames = "product", key = "#productId"),
    @CacheEvict(cacheNames = "productList", allEntries = true)
})
public void deleteById(Long productId) { ... }
```

`allEntries = true` 会一次性清空整个缓存区域（此时 `key` 被忽略），适合列表类缓存这种无法精确推算键的场景，但要意识到下一次访问会全部重新加载。

### 4.4 sync = true：同一键只计算一次

默认情况下，缓存抽象不做任何加锁：热点键过期的瞬间，并发未命中的线程会**各自**执行一遍方法（击穿，见 [7.1](#71-缓存击穿热点键过期的瞬间)）。`sync = true` 会要求底层缓存实现在值计算期间锁定该条目——只有一个线程执行方法，其余线程阻塞等待结果。

**没有 `sync = true`（默认）**——每个未命中线程都执行方法：

```text
key=42 过期
  │
  ├── 线程 A：未命中 → 执行方法（查数据库）→ 写入缓存
  ├── 线程 B：未命中 → 执行方法（查数据库）→ 写入缓存
  ├── 线程 C：未命中 → 执行方法（查数据库）→ 写入缓存
  └── ...
结果：方法执行 N 次，数据库承受 N 次查询
```

**有 `sync = true`**——只有一个线程执行方法，其余等待：

```text
key=42 过期
  │
  ├── 线程 A：未命中 → 获得锁 → 执行方法 → 写入缓存 → 释放锁
  ├── 线程 B：未命中 → 等待锁... → 锁释放后读缓存 → 返回
  ├── 线程 C：未命中 → 等待锁... → 锁释放后读缓存 → 返回
  └── ...
结果：方法只执行 1 次，数据库承受 1 次查询
```

|                | 无 `sync`                    | 有 `sync = true`             |
| -------------- | ---------------------------- | ---------------------------- |
| 方法执行次数   | N 次（每个未命中线程都执行） | **1 次**                     |
| 其余线程的行为 | 各自查数据库                 | **阻塞等待**第一个线程的结果 |
| 数据库压力     | N 倍                         | 1 倍                         |

```java
@Cacheable(cacheNames = "product", key = "#productId", sync = true)
public Product findById(Long productId) { ... }
```

两点限制：它是**可选能力**，取决于缓存实现（`CaffeineCacheManager` 支持；直接使用 Caffeine 时，`cache.get(key, fn)` 本身就具备这种原子性，见 [6](#6-直接使用-caffeine)）；它只协调**当前 JVM 内**的并发，不是分布式锁，多实例部署仍需外部方案。

---

## 5. Caffeine 缓存策略详解

Caffeine 的构建器把策略分为三类：**容量淘汰**、**时间过期**、**基于引用的回收**；此外还有异步刷新与统计两个正交能力。理解它们，才能给 `setCaffeine(...)` 或 spec 字符串填上正确的参数。

### 5.1 容量淘汰（maximumSize / maximumWeight）

```java
// 说明性片段：按条目数或按权重约束缓存大小。
Caffeine.newBuilder()
    .maximumSize(10_000)               // 最多 10,000 个条目
    .build();

Caffeine.newBuilder()
    .maximumWeight(100_000)
    .weigher((Long id, Product p) -> p.memoryCost()) // 按“权重”计算占用
    .build();
```

**`maximumSize`**：每个条目算 1 个，最多 10,000 个。不管条目是 1KB 还是 1MB，都算“1 个”。超过上限后，Caffeine 使用 Window TinyLFU 策略淘汰“最近不常用”的条目，官方宣称其命中率接近理论最优。

**`maximumWeight` + `weigher`**：当条目大小差异很大时（有的几 KB、有的几 MB），按实际“代价”计量而不是按个数。`weigher` 是一个函数，接收键和值，返回这个条目的重量：

```text
Product A（简单商品）→ memoryCost() = 50
Product B（复杂商品，很多图片）→ memoryCost() = 500
Product C（超大数据）→ memoryCost() = 5000

maximumWeight(100_000) 的缓存里可以放：
  2000 个 A（总重 100,000）
  或 200 个 B（总重 100,000）
  或 20 个 C（总重 100,000）
  或混合搭配，只要总重 ≤ 100,000
```

两个注意点：

- 权重只在条目**创建或更新时**计算一次、之后不变——即使对象后来变大了，缓存里的权重也不会更新。
- `maximumSize` 与 `maximumWeight` 互斥，不能同时设置。

### 5.2 时间过期（expireAfterWrite / expireAfterAccess / expireAfter）

三种过期方式解决不同的问题：

| 方法                   | 计时起点                         | 适合场景                                 |
| ---------------------- | -------------------------------- | ---------------------------------------- |
| `expireAfterWrite(d)`  | 条目被创建或**最近一次替换**之后 | 数据随时间变旧：价格、库存、配置         |
| `expireAfterAccess(d)` | 条目最后一次**读或写**之后       | 与活跃度绑定的数据：会话、购物车         |
| `expireAfter(Expiry)`  | 按条目自定义                     | 过期时间由外部资源决定：如“商品下架时间” |

```java
// 说明性片段：按条目的下架时间决定过期时刻。
Caffeine.newBuilder()
    .expireAfter(new Expiry<Long, Product>() {
        @Override
        public long expireAfterCreate(Long key, Product value, long currentTime) {
            return value.offlineAt().toNanos();  // 首次写入：用商品的下架时间作为过期时长
        }

        @Override
        public long expireAfterUpdate(Long key, Product value, long currentTime, long currentDuration) {
            return currentDuration;  // 更新时：保持原来的过期时长不变
        }

        @Override
        public long expireAfterRead(Long key, Product value, long currentTime, long currentDuration) {
            return currentDuration;  // 读取时：保持原来的过期时长不变
        }
    })
    .build();
```

过期清理是**惰性**的：写入时做周期维护，读取时偶尔触发，因此过期条目不一定立即消失。需要更及时的清理时，可配置调度线程：`Caffeine.scheduler(Scheduler.systemScheduler())`（Java 9+）。测试过期行为不必等真实时钟，注入 `Caffeine.ticker(Ticker)` 即可模拟时间流逝。

### 5.3 基于引用的回收（weak / soft）

`weakKeys()`、`weakValues()` 用弱引用、`softValues()` 用软引用包装条目，让 GC 在内存紧张时回收它们。两个代价必须知道：

- 弱/软引用依赖对象身份（identity），缓存内部会改用 `==` 比较键或值，而不是 `equals()`；
- 官方一般**不推荐** `softValues()`（软引用回收时机不可预测、性能开销大），更建议用可预测的 `maximumSize` 设定容量边界；`AsyncCache` 也不支持弱/软值。

对大多数业务缓存，明确的 `maximumSize` + 过期时间已经足够；引用回收更适合缓存大对象且能接受“条目随时可能被 GC 拿走”的场景。

### 5.4 异步刷新（refreshAfterWrite）

`refreshAfterWrite` 与过期是两回事：条目“到期可刷新”后，**只有被查询时**才会真正发起异步重载，重载期间旧值照常返回（stale-while-revalidate 模式）；而 `expireAfterWrite` 到期后，下一次读取必须同步等待新值。

```java
// 说明性片段：1 分钟后进入可刷新期，查询时后台重载、旧值先行返回；5 分钟仍未刷新的条目过期。
LoadingCache<Long, Product> cache = Caffeine.newBuilder()
    .maximumSize(10_000)
    .expireAfterWrite(Duration.ofMinutes(5))
    .refreshAfterWrite(Duration.ofMinutes(1))
    .build(this::loadFromRepository);
```

几个关键行为：

- 可以同时配置 `refreshAfterWrite` 与 `expireAfterWrite`：刷新**不会**重置过期计时器，进入可刷新期后一直没被查询的条目最终照常过期。
- 实现 `CacheLoader.reload(key, oldValue)` 可以利用旧值计算新值（如带版本号增量更新）。
- 刷新默认在 `ForkJoinPool.commonPool()` 中执行，可用 `Caffeine.executor(...)` 换成自己的线程池。
- 刷新中抛出异常时，旧值保留、异常被记录并吞掉，不会打断读路径。

与 Spring 注解路径的关系：`@Cacheable` 的加载由 Spring 调用方法完成，本指南的注解示例没有启用刷新。若要在注解路径使用异步能力，需要额外配置 `CaffeineCacheManager`（如设置 `CacheLoader`，或开启 `setAsyncCacheMode(true)` 配合 Spring Framework 6.1+ 对 `CompletableFuture` 返回类型的适配），并自行验证行为。

### 5.5 统计与调优

`recordStats()` 开启统计后，`Cache.stats()` 返回快照：

| 指标                   | 含义                     | 调优信号                                   |
| ---------------------- | ------------------------ | ------------------------------------------ |
| `hitRate()`            | 命中次数占请求次数的比例 | 长期很低 → 键设计或过期策略有问题          |
| `evictionCount()`      | 被淘汰的条目数           | 持续增长 → 容量不足，需要加大或分片        |
| `averageLoadPenalty()` | 平均加载耗时             | 反映下游查询成本，可作为是否值得缓存的依据 |

集成方式有两种：拉取式定期读取 `stats()` 快照，或推送式实现自定义 `StatsCounter`。Caffeine 官方推荐与 Micrometer 集成（Spring Boot Actuator 下可直接暴露，见 [8.3](#83-监控)）。

### 5.6 用 spec 字符串一次配齐

不想写构建器链时，可以用 Caffeine 的 spec 字符串，一处配置全部策略：

```text
initialCapacity=100,maximumSize=10_000,expireAfterWrite=10m,recordStats
```

支持的键（与构建器方法一一对应）：`initialCapacity`、`maximumSize`、`maximumWeight`、`expireAfterAccess`、`expireAfterWrite`、`refreshAfterWrite`、`weakKeys`、`weakValues`、`softValues`、`recordStats`。时长用 `d/h/m/s` 后缀（如 `10m`）或 ISO-8601（如 `PT10M`）；`maximumSize` 与 `maximumWeight`、`weakValues` 与 `softValues` 不能同时出现。

它有三个入口：`CaffeineSpec.parse(...)` 直接构建、`CaffeineCacheManager.setCacheSpecification(...)`、以及 Spring Boot 属性 `spring.cache.caffeine.spec`（见 [8.1](#81-spring-boot-属性配置)）。

---

## 6. 直接使用 Caffeine

直接使用 Caffeine API 适合缓存不完全等同于某个 Spring 方法返回值的内容，例如临时计算结果、第三方 SDK 数据或需要由多个步骤共同维护的状态。此时你自己决定何时读、写和失效。

`Caffeine.newBuilder().build()` 会创建**独立的** Caffeine `Cache` 实例，不会读取第 2 节的 `CaffeineCacheManager`，也不会自动使用名为 `product` 的缓存：

```text
CaffeineCacheManager("product")
  └── product 缓存 ← @Cacheable(cacheNames = "product") 使用

Caffeine.newBuilder().build()
  └── 手动创建的独立 Cache ← 直接调用 get / put / invalidate 使用
```

两者即使都以商品 ID 为键，也不是同一份数据；不要让它们同时维护同一业务数据，否则更新与失效会不一致。若需要缓存方法返回值，使用 `@Cacheable`；若需要手动控制缓存流程，单独创建并封装 Caffeine `Cache`。

> **说明性片段**：`Product`、`ProductRepository` 和 `ProductNotFoundException` 是示例领域类型；`ProductRepository#findById` 返回 `Optional<Product>`。

```java
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class DirectProductCache {

    private final ProductRepository productRepository;
    private final Cache<Long, Product> cache = Caffeine.newBuilder()
        .maximumSize(10_000)
        .expireAfterWrite(Duration.ofMinutes(10))
        .recordStats()
        .build();

    public DirectProductCache(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Product findById(Long productId) {
        // 命中时直接返回；未命中时执行 loadRequired，并将结果写入缓存。
        return cache.get(productId, this::loadRequired);
    }

    public void refresh(Product product) {
        // 已拿到新数据时主动覆盖旧值。
        cache.put(product.id(), product);
    }

    public void remove(Long productId) {
        // 删除商品或数据已不可信时清除缓存。
        cache.invalidate(productId);
    }

    private Product loadRequired(Long productId) {
        return productRepository.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));
    }
}
```

这里的 `cache.get(productId, this::loadRequired)` 将“查缓存”和“缓存未命中时加载”合为一次调用，并且**对同一键的并发加载是原子的**：多个线程同时未命中时，加载函数只执行一次——这与注解路径的 `sync = true` 是同一层防护（[4.4](#44-sync--true同一键只计算一次)）。加载函数抛出异常时不会写入缓存。若只想读取而不触发加载，可调用 `cache.getIfPresent(productId)`；若要清空整个缓存，可调用 `cache.invalidateAll()`，但应避免在高流量路径中随意使用。

直接调用 Caffeine API 的优势是控制精确：你可以为不同数据建立不同的 `Cache` 实例、选择何时预热、用 `LoadingCache` 搭配 [5.4](#54-异步刷新refreshafterwrite) 的异步刷新，或在一个操作中维护多个键。代价是缓存代码会进入业务代码，重复逻辑也需要自行维护。

---

## 7. 缓存一致性问题与应对

缓存的三个经典问题都源于同一个事实：**缓存里的数据和真实数据源之间存在时间差**。本地缓存场景下问题同样存在，只是影响范围限于单个实例。

### 7.1 缓存击穿：热点键过期的瞬间

某个高频访问的键（如热卖商品）过期后，恰好涌来大量并发请求，全部未命中、全部打到下游：

```text
key=42 过期
  ▼
1000 个并发请求同时未命中
  ├── 无保护：下游承受 1000 次查询
  └── 有保护：只执行 1 次加载，其余 999 个等待复用
```

应对：注解路径用 `sync = true`（[4.4](#44-sync--true同一键只计算一次)）；直接 API 路径利用 `cache.get(key, fn)` 的原子性（[6](#6-直接使用-caffeine)）。两者都只在本 JVM 内生效；多实例部署时，各实例仍会各自穿透一次，需要分布式协调。

### 7.2 缓存穿透：查询不存在的数据

每次查询的键都“查无此数据”，永远不命中，缓存形同虚设，攻击者或异常流量可以借此持续打到数据库。三层应对：

- **参数校验**：拒绝明显非法的键（如负 ID）。
- **缓存空值**：为“不存在”也写入一个短 TTL 的占位值，让后续同样查询命中占位、不再打库。这与 [3.1](#31-cacheable读缓存命中就跳过方法) 中 `unless = "#result == null"` 是相反的取舍——跳过空值会放大穿透，缓存空值则要承受新建数据在空值过期前不可见的窗口。
- **布隆过滤器**：在缓存前先判定键是否可能存在，拦截绝大多数不存在的键。

### 7.3 缓存雪崩：大量键同时过期

一批键在同一时刻过期（例如启动时统一预热、TTL 又相同），过期瞬间的未命中洪峰集中砸向下游。应对：

- **TTL 加随机扰动**：`expireAfterWrite(10 分钟 + 0~3 分钟随机)`，把过期时刻打散。Caffeine 的固定时长 API 无法直接做到，可在代码中为每次写入随机化，或用 `expireAfter(Expiry)` 按条目生成随机时长。
- **预热与多级缓存**：应用启动时主动加载热点数据；本地缓存前再加一层更长的共享缓存。
- **限流与降级**：下游压力过大时，对未命中加载做限流或返回兜底值。

### 7.4 更新顺序：Cache-Aside 模式

最常见的读路径是 Cache-Aside：查缓存 → 命中返回；未命中查库 → 写缓存 → 返回。写路径的经典做法是**先更新数据库，成功后失效/更新缓存**：

```text
更新操作
  ├── 1. 更新数据库
  └── 2. 成功后 @CacheEvict 移除旧值（或 @CachePut 写入新值）
       └── 下一次查询缓存未命中 → 回源读到新数据
```

`@CacheEvict` 默认“方法成功后清除”正好贴合这个顺序；如果先删缓存再更新数据库，两个操作之间的窗口期内，其他请求会回源读到旧数据并把它写回缓存，造成较长时间的脏数据。`@CacheEvict(beforeInvocation = true)`（先失效、后执行方法）只适合“无论方法成败都要先失效”的明确业务要求。

更要注意的是本地缓存的固有局限：更新一个实例的缓存不会通知其他实例，多实例间必然存在最终一致窗口；对一致性要求高的数据应评估共享缓存，见 [spring-cache-caffeine-guide.md](spring-cache-caffeine-guide.md) 6.4 节。

---

## 8. 生产配置、监控与测试

### 8.1 Spring Boot 属性配置

不写 Java 配置类时，可以用属性完成大部分工作：

```yaml
spring:
  cache:
    type: caffeine # 强制使用 Caffeine，避免多提供者时的不确定性
    cache-names: product,productList # 启动时预创建缓存
    caffeine:
      spec: maximumSize=1000,expireAfterWrite=10m,recordStats
```

Spring Boot 检测到多个缓存提供者时的优先级是固定的（Generic → JCache → Hazelcast → Infinispan → Couchbase → Redis → Caffeine → Cache2k → Simple），用 `spring.cache.type` 显式指定可以消除歧义。Caffeine 的自定义优先级依次是：`spring.cache.caffeine.spec` → `CaffeineSpec` Bean → `Caffeine` Bean。

属性配置的局限：一个 `spec` 作用于自动配置的全部缓存。需要**每个缓存名不同策略**时，回到 Java 配置——要么声明多个 `CaffeineCacheManager` 并用注解的 `cacheManager` 属性选择，要么在一个 `CaffeineCacheManager` 上用 `registerCustomCache(name, cache)` 注册单独策略的缓存。

### 8.2 CacheManagerCustomizer 微调

只想微调自动配置结果时，实现 `CacheManagerCustomizer<T>` 比整体替换更轻量：

```java
// 说明性片段：在自动配置的基础上关闭 null 占位值。
import org.springframework.boot.cache.autoconfigure.CacheManagerCustomizer;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CacheCustomizerConfiguration {

    @Bean
    public CacheManagerCustomizer<CaffeineCacheManager> caffeineCustomizer() {
        return manager -> manager.setAllowNullValues(false);
    }
}
```

注意：自定义器只对**自动配置**出的对应类型 `CacheManager` 生效；如果自己声明了 `CacheManager` Bean，自定义器不会被执行。

### 8.3 监控

开启 `recordStats()` 后，把指标暴露出来才算闭环。Spring Boot Actuator + Micrometer 会自动收集带 `recordStats` 的缓存（Caffeine 官方也推荐 Micrometer 集成），在 `/actuator/metrics` 下可以看到以 `cache.` 开头的指标（命中、未命中、淘汰、容量等，具体名称与标签以运行时导出为准）。

日常关注的四个信号：**命中率**（低 → 键设计或 TTL 有问题）、**淘汰数**（持续增长 → 容量不足）、**平均加载耗时**（反映下游压力）、**JVM 堆内存**（容量配置是否越界）。命中率低的缓存可能只是在制造额外开销。

### 8.4 测试

- **关闭缓存**：集成测试想验证真实数据链路时，加 `@AutoConfigureCache` 把自动配置的 `CacheManager` 换成 no-op 实现，或设置 `spring.cache.type=none`。
- **隔离配置**：把 `@EnableCaching` 与 `CacheManager` 放在独立 `@Configuration` 类（[2.2](#22-启用-spring-cache并指定-caffeine)），切片测试不会强制加载缓存配置。
- **模拟时钟**：验证过期与刷新逻辑时，用 Caffeine 的 `Ticker` 注入假时钟（Guava testlib 提供现成的 `FakeTicker`），不需要真的 `Thread.sleep` 等待 TTL 流逝。

---

## 9. 如何选择

| 你的需求                                         | 推荐方式                             | 原因                                                                   |
| ------------------------------------------------ | ------------------------------------ | ---------------------------------------------------------------------- |
| 只引入 `spring-boot-starter-cache`，未指定提供者 | `ConcurrentMapCacheManager`（默认）  | 数据放在当前 JVM 并发 `Map`；适合开发、测试或极简场景                  |
| 缓存一个查询方法的返回值                         | `@Cacheable + Caffeine`              | 声明简洁，读写路径由 Spring 统一处理                                   |
| 单机生产应用的本地缓存                           | `CaffeineCacheManager`               | 支持容量边界、过期淘汰和统计，参见[第 1 节](#1-先建立正确的关系)       |
| 更新或删除后维护查询缓存                         | `@CachePut` / `@CacheEvict`          | 与查询方法使用相同缓存名和键规则                                       |
| 热点键怕并发穿透                                 | `@Cacheable(sync = true)`            | 同一 JVM 内同一键只加载一次，参见[4.4](#44-sync--true同一键只计算一次) |
| 需要旧值先返回、后台悄悄刷新                     | `LoadingCache` + `refreshAfterWrite` | 注解路径默认不提供该语义，参见[5.4](#54-异步刷新refreshafterwrite)     |
| 手动预热、缓存中间计算结果或任意对象             | 直接使用 Caffeine API                | 不受“方法返回值”模型限制                                               |
| 部署多个应用实例，要求共享数据或统一失效         | Redis 等共享/分布式缓存              | Caffeine 只存在于各自 JVM 中，实例之间不共享                           |

在一个系统中混用两种方式是合理的：面向 CRUD 查询的 Service 使用 `@Cacheable`，面向复杂计算或 SDK 结果的组件直接使用 Caffeine API。不要让两种方式同时维护同一个缓存名/同一组键，除非缓存所有权和失效时机已经被明确设计。

同类自调用绕过代理、缓存键稳定性、内存风险等常见陷阱，以及上线前检查清单，见 [spring-cache-caffeine-guide.md](spring-cache-caffeine-guide.md) 第 6、7 节。

---

## 10. 参考资料

- [Spring Boot 4：Caching](https://docs.spring.io/spring-boot/4.0/reference/io/caching.html)
- [Spring Framework：Cache Abstraction](https://docs.spring.io/spring-framework/reference/integration/cache.html)
- [Spring Framework：Declarative Annotation-based Caching](https://docs.spring.io/spring-framework/reference/integration/cache/annotations.html)
- [Spring Framework：`@Cacheable` API](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/cache/annotation/Cacheable.html)
- [Caffeine Wiki：Eviction](https://github.com/ben-manes/caffeine/wiki/Eviction)
- [Caffeine Wiki：Refresh](https://github.com/ben-manes/caffeine/wiki/Refresh)
- [Caffeine Wiki：Statistics](https://github.com/ben-manes/caffeine/wiki/Statistics)
- [Caffeine Wiki：Specification](https://github.com/ben-manes/caffeine/wiki/Specification)
- [Caffeine API 文档](https://javadoc.io/doc/com.github.ben-manes.caffeine/caffeine/latest/index.html)

## 验证摘要

- 结构：已通过本仓库 `.qoder/skills/guide-writing/scripts/validate_guide.py` 检查（标题层级、目录锚点、代码围栏、未解决占位符）。
- 代码：所有 Java 代码均为说明性片段，未在本项目上下文中编译或启动。
- 来源：Spring 注解语义（`condition`/`unless` 时机、`sync`、`@Caching`、`@CacheConfig`、SpEL 上下文、CompletableFuture 适配）核对自 Spring Framework 官方 Cache Abstraction 参考；Caffeine 策略（淘汰、过期、刷新、统计、spec 语法）核对自 Caffeine 官方 Wiki；Spring Boot 自动配置与属性核对自 Spring Boot 4.0 Caching 参考。
- 未验证：示例未在本项目的应用上下文中编译或启动；监控指标名未在运行时核对（正文已注明以 `/actuator/metrics` 导出为准）；注解路径的异步刷新集成需要自行验证。
