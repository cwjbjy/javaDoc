# Redis Spring Boot 企业级使用指南

> Spring Boot 4.x + Redis 7.x 企业级开发实战

## 技术栈版本

| 组件 | 版本 | 说明 |
|------|------|------|
| Spring Boot | 4.0.7 | 最新稳定版 |
| Spring Boot WebMVC | 4.0.7 | Web 框架，含 Jackson |
| Spring Data Redis | 4.0.6 | 官方 Starter |
| Lettuce | 6.8.x | 默认连接池（推荐） |
| Redis Server | 7.x | 服务端版本 |

---

## 一、项目初始化

### 1.1 添加依赖

> **说明**：Jackson 由 `spring-boot-starter-webmvc` 传递引入，版本由 Spring Boot Parent POM 统一管理，无需单独声明。

```xml
<dependencies>
    <!-- Spring Boot WebMVC（含 Jackson） -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-webmvc</artifactId>
    </dependency>

    <!-- Spring Boot Redis Starter -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>

    <!-- 连接池 - Lettuce已在starter中内置，开启pool功能需额外引入commons-pool2 -->
    <dependency>
        <groupId>org.apache.commons</groupId>
        <artifactId>commons-pool2</artifactId>
    </dependency>

    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

### 1.2 YAML配置

```yaml
spring:
  data:
    redis:
      # 单节点模式
      host: localhost
      port: 6379
      password: ${REDIS_PASSWORD:}  # 环境变量注入，默认空
      database: 0
      timeout: 5s

      # Lettuce连接池配置（推荐）
      lettuce:
        pool:
          enabled: true
          max-active: 16              # 最大连接数
          max-idle: 8                 # 最大空闲连接
          min-idle: 4                 # 最小空闲连接
          max-wait: 5s                # 最大等待时间
          time-between-eviction-runs: 30s  # 空闲连接检测周期
        shutdown-timeout: 200ms

      # 集群模式配置（生产环境）
      # cluster:
      #   nodes:
      #     - 192.168.1.101:6379
      #     - 192.168.1.102:6379
      #     - 192.168.1.103:6379
      #   max-redirects: 3

      # 哨兵模式配置
      # sentinel:
      #   master: mymaster
      #   nodes:
      #     - 192.168.1.101:26379
      #     - 192.168.1.102:26379

# 自定义缓存配置
cache:
  redis:
    default-ttl: 30m              # 默认过期时间
    user-ttl: 60m                 # 用户数据过期时间
    token-ttl: 24h                # Token过期时间
```

### 1.3 Lettuce连接池详解

> **核心问题**：Lettuce 基于 Netty，单个连接即可多路复用（一个连接并发处理多个命令），为什么还需要连接池？

**连接池不是为"并发"而生，而是为"吞吐"而生。**

```
单连接模式（无 commons-pool2）：
  Thread-1 ─┐
  Thread-2 ─┼──→ 共享1个连接 ──→ Redis
  Thread-3 ─┘      ↑
               多路复用，但多线程争用时存在
               "获取连接 → 执行 → 释放" 的排队开销

连接池模式（有 commons-pool2）：
  Thread-1 ──→ 连接1 ──┐
  Thread-2 ──→ 连接2 ──┼──→ Redis
  Thread-3 ──→ 连接3 ──┘
               各线程持独立连接，零争用
```

| 对比维度 | 无连接池（默认） | 有连接池（commons-pool2） |
|---------|----------------|--------------------------|
| 连接数 | 1个 | max-active 个（默认8） |
| 并发模型 | 单连接多路复用 | 多连接 + 多路复用 |
| 适用场景 | 低并发、简单应用 | 高并发、生产环境 |
| 阻塞风险 | 高负载时排队等待 | 连接充足时无等待 |
| 额外依赖 | 无 | commons-pool2 |

**参数说明与推荐值**：

| 参数 | 含义 | 推荐值 | 说明 |
|------|------|--------|------|
| `max-active` | 最大连接数 | 16 | 并非越大越好，Redis 单线程，过大无意义 |
| `max-idle` | 最大空闲连接 | 8 | 略大于平均并发，避免频繁创建销毁 |
| `min-idle` | 最小空闲连接 | 4 | **建议 > 0**，避免突发流量时的冷启动开销 |
| `max-wait` | 等待超时 | 5s | 连接耗尽时最大等待时间，超时抛异常 |
| `time-between-eviction-runs` | 空闲检测间隔 | 30s | 定期回收超出 `max-idle` 的空闲连接 |

**调优公式**（估算 `max-active`）：

```
max-active ≈ 业务QPS × 单次Redis操作平均耗时(s)

示例：
  QPS = 500，平均耗时 = 5ms
  max-active = 500 × 0.005 = 2.5 → 向上取整 = 4

  考虑峰值（2~3倍）：4 × 2 = 8（作为 min-idle）
  考虑峰值 + 缓冲：16（作为 max-active）
```

> **注意事项**：
> - `max-active` 设太大浪费内存，太小导致 `max-wait` 超时
> - `min-idle > 0` 可平稳承接突发流量，默认 0 意味着空闲时池子为空
> - Redis 单线程处理命令，16 个连接通常够用，瓶颈在业务逻辑而非连接数
> - 如果 `max-wait` 频繁超时，优先检查是否有慢查询，而非盲目加大 `max-active`

---

## 二、核心配置类

> **序列化策略**：统一使用 `StringRedisTemplate` + Jackson `ObjectMapper` 手动序列化。字符串比二进制可读、可调试、跨语言兼容，是大型互联网公司的标准实践。`StringRedisTemplate` 由 Boot 自动配置，无需额外定义。

### 2.1 Redis属性配置类

```java
package com.example.redis.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis自定义配置属性
 */
@Getter
@Setter // 通过反射调用 setter 把 YAML 值写进字段
@Component
@ConfigurationProperties(prefix = "cache.redis") //批量读取 application.yml 中的配置
public class RedisCacheProperties {

    /** 默认过期时间 */
    private Duration defaultTtl = Duration.ofMinutes(30);

    /** 用户数据过期时间 */
    private Duration userTtl = Duration.ofMinutes(60);

    /** Token过期时间 */
    private Duration tokenTtl = Duration.ofHours(24);

    /** 空值缓存时间（防穿透） */
    private Duration nullValueTtl = Duration.ofMinutes(5);

    /** 缓存Key前缀 */
    private String keyPrefix = "app:";
}
```

---

## 三、数据访问层设计

### 3.1 基础Redis操作封装

```java
package com.example.redis.repository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Redis基础操作Repository
 * 封装常用操作，提供类型安全的API
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisRepository {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;  // Boot 自带的 jacksonObjectMapper

    // ========================= String操作 =========================

    /**
     * 设置字符串值
     */
    public void set(String key, String value) {
        stringRedisTemplate.opsForValue().set(key, value);
    }

    /**
     * 设置字符串值（带过期时间）
     */
    public void set(String key, String value, Duration ttl) {
        stringRedisTemplate.opsForValue().set(key, value, ttl);
    }

    /**
     * 设置对象（JSON序列化）
     */
    @SneakyThrows
    public <T> void setObject(String key, T value) {
        //将值转为字符串
        String json = objectMapper.writeValueAsString(value);
        stringRedisTemplate.opsForValue().set(key, json);
    }

    /**
     * 设置对象（带过期时间）
     */
    @SneakyThrows
    public <T> void setObject(String key, T value, Duration ttl) {
        String json = objectMapper.writeValueAsString(value);
        stringRedisTemplate.opsForValue().set(key, json, ttl);
    }

    /**
     * 获取字符串值
     */
    public String get(String key) {
        return stringRedisTemplate.opsForValue().get(key);
    }

    /**
     * 获取对象
     */
    public <T> T getObject(String key, Class<T> clazz) {
        String json = stringRedisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JacksonException e) {
            log.error("Redis JSON反序列化失败: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 删除Key
     */
    public Boolean delete(String key) {
        return stringRedisTemplate.delete(key);
    }

    /**
     * 批量删除
     */
    public Long delete(Collection<String> keys) {
        return stringRedisTemplate.delete(keys);
    }

    /**
     * 设置过期时间
     */
    public Boolean expire(String key, Duration ttl) {
        return stringRedisTemplate.expire(key, ttl);
    }

    /**
     * 获取过期时间
     */
    public Long getExpire(String key) {
        return stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    /**
     * 判断Key是否存在
     */
    public Boolean hasKey(String key) {
        return stringRedisTemplate.hasKey(key);
    }

    /**
     * 原子自增
     */
    public Long increment(String key) {
        return stringRedisTemplate.opsForValue().increment(key);
    }

    /**
     * 原子自增（指定增量）
     */
    public Long increment(String key, long delta) {
        return stringRedisTemplate.opsForValue().increment(key, delta);
    }

    // ========================= Hash操作 =========================

    @SneakyThrows
    public void hSet(String key, String field, Object value) {
        String json = objectMapper.writeValueAsString(value);
        stringRedisTemplate.opsForHash().put(key, field, json);
    }

    @SneakyThrows
    public void hSetAll(String key, Map<String, Object> map) {
        Map<String, String> stringMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            stringMap.put(entry.getKey(), objectMapper.writeValueAsString(entry.getValue()));
        }
        stringRedisTemplate.opsForHash().putAll(key, stringMap);
    }

    public String hGet(String key, String field) {
        return (String) stringRedisTemplate.opsForHash().get(key, field);
    }

    public <T> T hGet(String key, String field, Class<T> clazz) {
        String json = (String) stringRedisTemplate.opsForHash().get(key, field);
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, clazz);
        } catch (JacksonException e) {
            log.error("Hash反序列化失败: key={}, field={}", key, field, e);
            return null;
        }
    }

    public Map<String, String> hGetAll(String key) {
        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(key);
        Map<String, String> result = new HashMap<>();
        entries.forEach((k, v) -> result.put((String) k, (String) v));
        return result;
    }

    public Long hDelete(String key, String... fields) {
        return stringRedisTemplate.opsForHash().delete(key, (Object[]) fields);
    }

    // ========================= List操作 =========================

    /**
     * 左侧入队
     */
    public Long lPush(String key, String value) {
        return stringRedisTemplate.opsForList().leftPush(key, value);
    }

    /**
     * 右侧出队
     */
    public String rPop(String key) {
        return stringRedisTemplate.opsForList().rightPop(key);
    }

    /**
     * 获取列表范围
     */
    public List<String> lRange(String key, long start, long end) {
        return stringRedisTemplate.opsForList().range(key, start, end);
    }

    // ========================= Set操作 =========================

    /**
     * 添加Set成员
     */
    public Long sAdd(String key, String... members) {
        return stringRedisTemplate.opsForSet().add(key, members);
    }

    /**
     * 判断是否是Set成员
     */
    public Boolean sIsMember(String key, String member) {
        return stringRedisTemplate.opsForSet().isMember(key, member);
    }

    /**
     * 获取Set所有成员
     */
    public Set<String> sMembers(String key) {
        return stringRedisTemplate.opsForSet().members(key);
    }

    /**
     * 获取Set交集
     */
    public Set<String> sIntersect(String key1, String key2) {
        return stringRedisTemplate.opsForSet().intersect(key1, key2);
    }

    // ========================= ZSet操作 =========================

    /**
     * 添加ZSet成员
     */
    public Boolean zAdd(String key, String member, double score) {
        return stringRedisTemplate.opsForZSet().add(key, member, score);
    }

    /**
     * 获取ZSet排名（降序）
     */
    public Long zReverseRank(String key, String member) {
        return stringRedisTemplate.opsForZSet().reverseRank(key, member);
    }

    /**
     * 获取ZSet范围（降序）
     */
    public Set<String> zReverseRange(String key, long start, long end) {
        return stringRedisTemplate.opsForZSet().reverseRange(key, start, end);
    }

    /**
     * 增加ZSet分数
     */
    public Double zIncrementScore(String key, String member, double delta) {
        return stringRedisTemplate.opsForZSet().incrementScore(key, member, delta);
    }

    // ========================= 批量操作 =========================

    /**
     * Pipeline批量操作
     */
    public List<Object> pipeline(Consumer<StringRedisTemplate> action) {
        return stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            action.accept(stringRedisTemplate);
            return null;
        });
    }
}
```

### 3.2 用户缓存Repository示例

```java
package com.example.redis.repository;

import com.example.redis.config.RedisCacheProperties;
import com.example.redis.entity.User;
import tools.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 用户缓存Repository
 * 封装用户相关的缓存操作，包含防穿透逻辑（空值缓存）
 */
@Slf4j
@Repository
public class UserCacheRepository extends RedisRepository {

    private static final String KEY_PREFIX = "user:";
    private static final String NULL_VALUE = "NULL";

    private final RedisCacheProperties cacheProperties;

    public UserCacheRepository(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            RedisCacheProperties cacheProperties) {
        super(stringRedisTemplate, objectMapper);
        this.cacheProperties = cacheProperties;
    }

    private String buildKey(Long userId) {
        return cacheProperties.getKeyPrefix() + KEY_PREFIX + userId;
    }

    private String buildKey(String username) {
        return cacheProperties.getKeyPrefix() + KEY_PREFIX + "name:" + username;
    }

    /**
     * 获取用户（带缓存穿透防护）
     */
    public Optional<User> getUser(Long userId) {
        String key = buildKey(userId);
        String cached = get(key);

        if (NULL_VALUE.equals(cached)) {
            // 缓存空值，直接返回
            log.debug("缓存命中空值: userId={}", userId);
            return Optional.empty();
        }

        if (cached != null) {
            User user = getObject(key, User.class);
            return Optional.ofNullable(user);
        }

        return Optional.empty();
    }

    /**
     * 设置用户缓存
     */
    public void setUser(User user) {
        String key = buildKey(user.getId());
        setObject(key, user, cacheProperties.getUserTtl());
    }

    /**
     * 设置空值缓存（防穿透）
     */
    public void setNullUser(Long userId) {
        String key = buildKey(userId);
        set(key, NULL_VALUE, cacheProperties.getNullValueTtl());
    }

    /**
     * 删除用户缓存
     */
    public void deleteUser(Long userId) {
        String key = buildKey(userId);
        delete(key);
    }

    /**
     * 批量获取用户（使用multiGet优化）
     */
    public List<User> getUsers(List<Long> userIds) {
        List<String> keys = userIds.stream()
            .map(this::buildKey)
            .toList();

        List<String> jsonList = stringRedisTemplate.opsForValue().multiGet(keys);

        return jsonList.stream()
            .filter(json -> json != null && !NULL_VALUE.equals(json))
            .map(json -> {
                try {
                    return objectMapper.readValue(json, User.class);
                } catch (Exception e) {
                    log.error("反序列化失败", e);
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * 批量设置用户
     */
    @SneakyThrows
    public void setUsers(List<User> users) {
        Map<String, String> map = new HashMap<>();
        for (User user : users) {
            String key = buildKey(user.getId());
            String json = objectMapper.writeValueAsString(user);
            map.put(key, json);
        }

        stringRedisTemplate.opsForValue().multiSet(map);

        // 批量设置过期时间
        for (String key : map.keySet()) {
            expire(key, cacheProperties.getUserTtl());
        }
    }
}
```

---

## 四、业务层设计

### 4.1 用户Service（手动缓存版本）

> `UserMapper` 是 MyBatis 的 Mapper 接口，负责查询数据库，定义详见 MyBatis 项目。

```java
package com.example.redis.service;

import com.example.redis.entity.User;
import com.example.redis.mapper.UserMapper;
import com.example.redis.repository.UserCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 用户Service - 手动缓存管理
 * 适合复杂缓存场景，精细控制缓存逻辑
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserCacheRepository userCacheRepository;
    private final UserMapper userMapper;

    /**
     * 查询用户（Cache Aside + 缓存穿透防护）
     *
     * 防穿透：UserCacheRepository.getUser 内部已处理空值缓存
     * 防击穿：生产环境建议使用 Redisson 分布式锁，详见 {@code Redisson分布式锁详解.md}
     */
    public User getUserById(Long userId) {
        // 1. 查询缓存（含空值防穿透）
        Optional<User> cached = userCacheRepository.getUser(userId);
        if (cached.isPresent()) {
            log.debug("缓存命中: userId={}", userId);
            return cached.get();
        }

        // 2. 查询数据库
        User user = userMapper.selectById(userId);

        // 3. 回写缓存
        if (user != null) {
            userCacheRepository.setUser(user);
        } else {
            userCacheRepository.setNullUser(userId);
        }

        return user;
    }

    /**
     * 更新用户（先更新DB，再删缓存）
     */
    @Transactional
    public void updateUser(User user) {
        // 1. 更新数据库
        userMapper.updateById(user);

        // 2. 删除缓存
        userCacheRepository.deleteUser(user.getId());
        log.debug("删除用户缓存: userId={}", user.getId());
    }

    /**
     * 删除用户
     */
    @Transactional
    public void deleteUser(Long userId) {
        userMapper.deleteById(userId);
        userCacheRepository.deleteUser(userId);
    }
}
```

### 4.2 缓存预热Service

```java
package com.example.redis.service;

import com.example.redis.entity.Dict;
import com.example.redis.mapper.DictMapper;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 字典缓存预热服务
 * 启动时加载字典数据到Redis
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictCacheWarmUpService {

    private final DictMapper dictMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String DICT_KEY_PREFIX = "cache:dict:";

    /**
     * 启动时预热缓存
     */
    @PostConstruct
    public void warmUpOnStartup() {
        log.info("开始预热字典缓存...");
        refreshDictCache();
    }

    /**
     * 定时刷新（每天凌晨2点）
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledRefresh() {
        log.info("定时刷新字典缓存...");
        refreshDictCache();
    }

    @SneakyThrows
    public void refreshDictCache() {
        // 1. 查询所有字典
        List<Dict> dicts = dictMapper.selectAll();

        // 2. 按类型分组
        Map<String, List<Dict>> dictMap = dicts.stream()
            .collect(Collectors.groupingBy(Dict::getDictType));

        // 3. 批量写入Redis
        for (Map.Entry<String, List<Dict>> entry : dictMap.entrySet()) {
            String key = DICT_KEY_PREFIX + entry.getKey();
            String json = objectMapper.writeValueAsString(entry.getValue());

            stringRedisTemplate.opsForValue().set(key, json, Duration.ofDays(7));
        }

        log.info("字典缓存预热完成，共{}种类型", dictMap.size());
    }

    /**
     * 获取字典列表
     */
    @SneakyThrows
    public List<Dict> getDictByType(String dictType) {
        String key = DICT_KEY_PREFIX + dictType;
        String json = stringRedisTemplate.opsForValue().get(key);

        if (json == null) {
            // 缓存不存在，从数据库查询并更新
            List<Dict> dicts = dictMapper.selectByType(dictType);
            if (!dicts.isEmpty()) {
                stringRedisTemplate.opsForValue().set(
                    key,
                    objectMapper.writeValueAsString(dicts),
                    Duration.ofDays(7)
                );
            }
            return dicts;
        }

        return objectMapper.readValue(json,
            objectMapper.getTypeFactory().constructCollectionType(List.class, Dict.class));
    }
}
```

---

## 五、高阶应用

### 5.1 限流器实现

```java
package com.example.redis.component;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collections;

/**
 * Redis滑动窗口限流器
 */
@Component
@RequiredArgsConstructor
public class RateLimiter {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 滑动窗口限流
     * @param key 限流Key
     * @param limit 窗口内最大请求数
     * @param windowSeconds 窗口大小（秒）
     * @return 是否允许通过
     */
    public boolean isAllowed(String key, int limit, int windowSeconds) {
        long now = Instant.now().toEpochMilli();
        long windowStart = now - windowSeconds * 1000;

        // Lua脚本实现原子操作
        String luaScript =
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, ARGV[1]) " +  // 移除窗口外的记录
            "local current = redis.call('ZCARD', KEYS[1]) " +       // 统计当前窗口内数量
            "if current < tonumber(ARGV[2]) then " +
            "    redis.call('ZADD', KEYS[1], ARGV[3], ARGV[3]) " +   // 添加当前请求
            "    redis.call('EXPIRE', KEYS[1], ARGV[4]) " +
            "    return 1 " +
            "else " +
            "    return 0 " +
            "end";

        RedisScript<Long> script = RedisScript.of(luaScript, Long.class);

        Long result = stringRedisTemplate.execute(
            script,
            Collections.singletonList("rate_limit:" + key),
            String.valueOf(windowStart),
            String.valueOf(limit),
            String.valueOf(now),
            String.valueOf(windowSeconds)
        );

        return result != null && result == 1;
    }

    /**
     * 令牌桶限流
     */
    public boolean isAllowedTokenBucket(String key, int rate, int capacity) {
        String luaScript =
            "local key = KEYS[1] " +
            "local rate = tonumber(ARGV[1]) " +
            "local capacity = tonumber(ARGV[2]) " +
            "local now = tonumber(ARGV[3]) " +
            "local requested = 1 " +
            "local fill_time = capacity/rate " +
            "local ttl = math.floor(fill_time*2) " +
            "local last_updated = redis.call('get', key .. ':last_updated') " +
            "local tokens = redis.call('get', key .. ':tokens') " +
            "if last_updated == false then " +
            "    last_updated = now " +
            "    tokens = capacity " +
            "end " +
            "tokens = math.min(capacity, tokens + (now - last_updated) * rate / 1000) " +
            "local allowed = tokens >= requested " +
            "if allowed then " +
            "    tokens = tokens - requested " +
            "end " +
            "redis.call('setex', key .. ':tokens', ttl, tokens) " +
            "redis.call('setex', key .. ':last_updated', ttl, now) " +
            "return allowed and 1 or 0";

        RedisScript<Long> script = RedisScript.of(luaScript, Long.class);

        Long result = stringRedisTemplate.execute(
            script,
            Collections.singletonList("token_bucket:" + key),
            String.valueOf(rate),
            String.valueOf(capacity),
            String.valueOf(System.currentTimeMillis())
        );

        return result != null && result == 1;
    }
}
```

### 5.2 分布式计数器与排行榜

```java
package com.example.redis.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 排行榜服务
 */
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private final StringRedisTemplate stringRedisTemplate;

    private static final String LEADERBOARD_KEY = "leaderboard:daily:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 增加用户分数
     */
    public Double incrementScore(String userId, double score) {
        String key = getTodayKey();
        return stringRedisTemplate.opsForZSet().incrementScore(key, userId, score);
    }

    /**
     * 获取用户排名（从1开始）
     */
    public Long getUserRank(String userId) {
        String key = getTodayKey();
        Long rank = stringRedisTemplate.opsForZSet().reverseRank(key, userId);
        return rank != null ? rank + 1 : null;
    }

    /**
     * 获取用户分数
     */
    public Double getUserScore(String userId) {
        String key = getTodayKey();
        return stringRedisTemplate.opsForZSet().score(key, userId);
    }

    /**
     * 获取Top N
     */
    public Set<LeaderboardEntry> getTopN(int n) {
        String key = getTodayKey();
        Set<ZSetOperations.TypedTuple<String>> tuples =
            stringRedisTemplate.opsForZSet().reverseRangeWithScores(key, 0, n - 1);

        if (tuples == null) {
            return Set.of();
        }

        return tuples.stream()
            .map(t -> new LeaderboardEntry(t.getValue(), t.getScore()))
            .collect(Collectors.toSet());
    }

    /**
     * 获取用户周边排名
     */
    public Set<LeaderboardEntry> getUserNeighborhood(String userId, int range) {
        String key = getTodayKey();
        Long rank = stringRedisTemplate.opsForZSet().reverseRank(key, userId);
        if (rank == null) {
            return Set.of();
        }

        long start = Math.max(0, rank - range);
        long end = rank + range;

        Set<ZSetOperations.TypedTuple<String>> tuples =
            stringRedisTemplate.opsForZSet().reverseRangeWithScores(key, start, end);

        return tuples.stream()
            .map(t -> new LeaderboardEntry(t.getValue(), t.getScore()))
            .collect(Collectors.toSet());
    }

    /**
     * 批量添加分数（Pipeline优化）
     */
    public void batchIncrementScores(Map<String, Double> userScores) {
        String key = getTodayKey();

        stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Map.Entry<String, Double> entry : userScores.entrySet()) {
                connection.zSetCommands().zIncrBy(
                    key.getBytes(),
                    entry.getValue(),
                    entry.getKey().getBytes()
                );
            }
            return null;
        });

        // 设置过期时间（2天）
        stringRedisTemplate.expire(key, Duration.ofDays(2));
    }

    private String getTodayKey() {
        return LEADERBOARD_KEY + LocalDate.now().format(DATE_FORMAT);
    }

    public record LeaderboardEntry(String userId, Double score) {}
}
```

---

## 六、测试示例

### 6.1 Repository测试

```java
package com.example.redis.repository;

import com.example.redis.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class UserCacheRepositoryTest {

    @Autowired
    private UserCacheRepository userCacheRepository;

    @Test
    void testUserCacheOperations() {
        // Given
        Long userId = 1L;
        User user = User.builder()
            .id(userId)
            .username("testuser")
            .email("test@example.com")
            .build();

        // When - 设置缓存
        userCacheRepository.setUser(user);

        // Then - 读取缓存
        Optional<User> cached = userCacheRepository.getUser(userId);
        assertTrue(cached.isPresent());
        assertEquals("testuser", cached.get().getUsername());

        // When - 删除缓存
        userCacheRepository.deleteUser(userId);

        // Then - 缓存不存在
        Optional<User> deleted = userCacheRepository.getUser(userId);
        assertFalse(deleted.isPresent());
    }

    @Test
    void testNullValueCache() {
        // Given - 不存在的用户
        Long userId = 999L;

        // When - 设置空值缓存
        userCacheRepository.setNullUser(userId);

        // Then - 应返回空Optional（不是null）
        Optional<User> result = userCacheRepository.getUser(userId);
        assertFalse(result.isPresent());
    }
}
```

---

## 七、最佳实践总结

### 7.1 Key设计规范

```
# 格式: 业务:模块:标识
user:profile:{userId}
user:session:{token}
order:detail:{orderId}
order:user:{userId}:list
cache:dict:{dictType}
rate_limit:api:{userId}:{api}
leaderboard:daily:{yyyyMMdd}
```

### 7.2 序列化策略

统一使用 `StringRedisTemplate` + Jackson `ObjectMapper` 手动序列化。

| 场景 | 方式 | 原因 |
|------|------|------|
| 写入对象 | `objectMapper.writeValueAsString(obj)` → `stringRedisTemplate.opsForValue().set(key, json)` | 可读、可调试、跨语言兼容 |
| 读取对象 | `stringRedisTemplate.opsForValue().get(key)` → `objectMapper.readValue(json, User.class)` | 类型由调用方控制，无擦除风险 |
| Hash 存对象 | `hSet` 存 JSON 字符串，`hGet(key, field, User.class)` 带类型读取 | 避免 `Object.class` 反序列化为 `LinkedHashMap` |

> **为什么不用 `RedisTemplate` 或 Spring Cache 注解？** `RedisTemplate` 的序列化器绑定全局类型（如 `JacksonJsonRedisSerializer(Object.class)`），反序列化时类信息丢失，对象变 `LinkedHashMap`。Spring Cache 注解隐式序列化，类型不可控，排查困难。`StringRedisTemplate` + 手动 JSON 反序列化把类型交给调用方，每个方法知道自己要什么类型，Redis 中存储的是可读字符串，排查问题直接 `redis-cli` 查看。

### 7.3 过期策略

| 数据类型 | 过期时间 | 说明 |
|---------|---------|------|
| 用户信息 | 60分钟 | 变化较频繁 |
| 订单数据 | 30分钟 | 变化频繁 |
| 商品信息 | 24小时 | 相对稳定 |
| 字典数据 | 7天 | 几乎不变 |
| Token | 24小时 | 安全考虑 |
| 空值防穿透 | 5分钟 | 短期保护 |

### 7.4 生产环境检查清单

- [ ] Redis集群/哨兵模式配置
- [ ] 连接池参数调优
- [ ] 序列化方式确认
- [ ] 缓存穿透防护
- [ ] 缓存击穿防护（使用 Redisson，详见 [Redisson 分布式锁详解](./Redisson分布式锁详解.md)）
- [ ] 缓存雪崩防护（随机过期时间）
- [ ] 慢查询监控
- [ ] 内存使用监控
- [ ] 大Key检测
- [ ] 热Key检测

---

## 参考文档

- [Spring Data Redis 官方文档](https://docs.spring.io/spring-data/redis/docs/current/reference/html/)
- [Redis 官方文档](https://redis.io/documentation)
- [Redisson 分布式锁详解](./Redisson分布式锁详解.md)
