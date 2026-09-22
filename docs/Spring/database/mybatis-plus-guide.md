# MyBatis-Plus 指南

> 本指南循序渐进介绍 MyBatis-Plus——从"MyBatis 每次 CRUD 都要写 XML"到"零 SQL 完成单表操作，复杂查询仍能手写 SQL"，每步只引入一个新概念。
> 适用于 MyBatis-Plus 3.5.17 / Spring Boot 4.x / Java 17+。
> 假设你已了解 MyBatis 基础（Mapper 接口、XML 映射、动态 SQL），可参考 [MyBatis 指南](mybatis-guide.md)。
> 想了解代码生成器自动产出实体 / Mapper / Service / Controller？见 [第 8 节：代码生成器](#8-代码生成器)。

---

## 目录

1. [为什么需要 MyBatis-Plus](#1-为什么需要-mybatis-plus)
2. [快速入门：从零 SQL 的 CRUD 开始](#2-快速入门从零-sql-的-crud-开始)
   - [2.1 依赖与配置](#21-依赖与配置)
   - [2.2 实体注解：告诉 MP 表与类的映射关系](#22-实体注解告诉-mp-表与类的映射关系)
   - [2.3 BaseMapper：继承即拥有 CRUD](#23-basemapper继承即拥有-crud)
3. [Service 层封装：IService 与 ServiceImpl](#3-service-层封装iservice-与-serviceimpl)
   - [3.1 链式查询：lambdaQuery()](#31-链式查询lambdaquery)
   - [3.2 链式更新：lambdaUpdate()](#32-链式更新lambdaupdate)
   - [3.3 IService 提供的通用方法](#33-iservice-提供的通用方法)
4. [条件构造器：手动使用 Wrapper](#4-条件构造器手动使用-wrapper)
   - [4.1 LambdaQueryWrapper](#41-lambdaquerywrapper)
   - [4.2 LambdaUpdateWrapper 与 update() 方法](#42-lambdaupdatewrapper-与-update-方法)
   - [4.3 常用条件方法速查](#43-常用条件方法速查)
5. [分页插件：告别手写 LIMIT](#5-分页插件告别手写-limit)
6. [常用功能](#6-常用功能)
   - [6.1 逻辑删除：@TableLogic](#61-逻辑删除tablelogic)
   - [6.2 自动填充：MetaObjectHandler](#62-自动填充metaobjecthandler)
   - [6.3 主键策略：IdType](#63-主键策略idtype)
7. [与原生 MyBatis 混用](#7-与原生-mybatis-混用)
8. [代码生成器](#8-代码生成器)
   - [8.1 依赖](#81-依赖)
   - [8.2 快速生成：FastAutoGenerator](#82-快速生成fastautogenerator)
   - [8.3 配置详解](#83-配置详解)
   - [8.4 自定义模板](#84-自定义模板)
9. [速查清单](#9-速查清单)

---

## 1. 为什么需要 MyBatis-Plus

在 [MyBatis 指南](mybatis-guide.md) 中，你已经看到了 MyBatis 如何通过 Mapper + XML 消除 JDBC 的样板代码。但回顾一下 `UserMapper.xml`——即使是最简单的"根据 ID 查用户"，你仍然需要：

```xml
<!-- 每个实体、每个 CRUD 方法，都要写一遍 -->
<select id="selectById" resultMap="BaseResultMap">
    SELECT id, user_name, age, email, create_time FROM user WHERE id = #{id}
</select>

<insert id="insert" useGeneratedKeys="true" keyProperty="id">
    INSERT INTO user (user_name, age, email, create_time)
    VALUES (#{userName}, #{age}, #{email}, #{createTime})
</insert>

<update id="update">
    UPDATE user SET user_name = #{userName}, age = #{age}, email = #{email}
    WHERE id = #{id}
</update>

<delete id="deleteById">
    DELETE FROM user WHERE id = #{id}
</delete>
```

**问题在哪里？**

- 这些 SQL 是**纯机械劳动**——每张表的结构不同，但 SQL 模式完全一样
- 新增一张表就要新建实体、Mapper 接口、XML 文件，写一遍 `SELECT ... FROM ... WHERE ...`
- 条件查询要写动态 SQL（`<where>` + `<if>`），每个查询接口都重复
- 分页要手写 `LIMIT #{offset}, #{size}`，还要再写一个 `COUNT(*)` 查询

```
MyBatis 解决了什么                    MyBatis-Plus 还要解决什么
═══════════════════════              ═══════════════════════════════
JDBC 样板代码（连接、ResultSet）       单表 CRUD 的重复 SQL
SQL 与 Java 代码分离                   条件查询的动态 WHERE 拼接
动态 SQL 标签                          分页（LIMIT + COUNT）
复杂查询的精确控制                     逻辑删除、自动填充、主键策略
                                      代码生成（实体/Mapper/Service）
```

MyBatis-Plus（简称 MP）的定位：**在 MyBatis 基础上只做增强，不做改变**。它不替换 MyBatis，而是在此基础上添加了一层——自动 CRUD 解决单表操作，条件构造器解决动态 WHERE，插件解决分页和通用操作。遇到复杂查询，你仍然可以写原生 XML SQL。

```
                你写 SQL 的量            框架帮你做的事

MyBatis         全部自己写               参数绑定、结果映射、连接管理
MyBatis-Plus    只写复杂查询             以上 + 自动 CRUD、条件构造、
                                         分页、逻辑删除、代码生成
JPA/Hibernate   框架生成（不可控）        ORM、自动 SQL 生成
```

核心思想一句话：**"简单场景零 SQL，复杂场景手写 SQL，两者共存于同一个项目"**。

---

## 2. 快速入门：从零 SQL 的 CRUD 开始

沿用 MyBatis 指南的用户管理场景，看看 MyBatis-Plus 如何让 CRUD 变得极简。

### 2.1 依赖与配置

在 `pom.xml` 中添加 MyBatis-Plus Spring Boot 4 Starter（从 3.5.13 起支持 Spring Boot 4）：

> **Illustrative fragment** —— 展示 Maven 依赖声明，坐标基于 MyBatis-Plus 3.5.17 官方文档。

```xml
<!-- MyBatis-Plus Spring Boot 4 Starter -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot4-starter</artifactId>
    <version>3.5.17</version>
</dependency>

<!-- MySQL 驱动 -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

> **注意 Starter 命名区别**：
>
> | Spring Boot 版本 | Starter 坐标                        |
> | ---------------- | ----------------------------------- |
> | 2.x              | `mybatis-plus-boot-starter`         |
> | 3.x              | `mybatis-plus-spring-boot3-starter` |
> | 4.x              | `mybatis-plus-spring-boot4-starter` |
>
> 不要混用——Spring Boot 4 项目用 `mybatis-plus-spring-boot3-starter` 会启动失败。

在 `application.yml` 中配置数据源和 MyBatis-Plus：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mydb?useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver

# MyBatis-Plus 配置
mybatis-plus:
  configuration:
    # 下划线转驼峰（与 MyBatis 相同）
    map-underscore-to-camel-case: true
    # 打印 SQL 日志（开发环境开启）
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      # 全局主键策略：雪花算法（默认）
      id-type: assign_id
      # 全局逻辑删除字段值
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0
```

> 与纯 MyBatis 配置对比：`mybatis:` 变成了 `mybatis-plus:`，新增了 `global-config` 节点用于配置全局数据库策略。MyBatis 的 `mapper-locations`、`type-aliases-package` 等配置仍然可用。

### 2.2 实体注解：告诉 MP 表与类的映射关系

MyBatis 用 XML `<resultMap>` 定义列与属性的映射。MyBatis-Plus 用注解直接标注在实体类上：

> **Illustrative fragment** —— 展示实体注解的用法。

```java
package com.example.javadoc.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data                        // Lombok：自动生成 getter / setter / toString 等
@TableName("user")           // 对应数据库表名
public class User {

    @TableId(type = IdType.AUTO)  // 主键，自增
    private Long id;

    private String userName;      // 自动映射到 user_name（开启了驼峰转换）
    private Integer age;
    private String email;
    private LocalDateTime createTime;
}
```

对比 MyBatis 方式——即使开启驼峰转换简化了字段映射，你仍然需要写 XML 文件定义每个 SQL：

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper
        PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<!-- namespace 必须指向 Mapper 接口的全限定名 -->
<mapper namespace="com.example.javadoc.module.user.mapper.UserMapper">

    <!-- 根据 ID 查询：开启 map-underscore-to-camel-case 后可直接用 resultType -->
    <select id="selectById" resultType="User">
        SELECT id, user_name, age, email, create_time
        FROM user WHERE id = #{id}
    </select>

    <!-- 插入用户 -->
    <insert id="insert" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO user (user_name, age, email, create_time)
        VALUES (#{userName}, #{age}, #{email}, #{createTime})
    </insert>

</mapper>
```

```java
// MyBatis 方式：Mapper 接口只定义方法签名，SQL 全在 XML 里
public interface UserMapper {
    User selectById(@Param("id") Long id);
    int insert(User user);
}
```

```java
// MyBatis 方式：纯 POJO，没有任何注解（映射关系全在 XML 里）
public class User {
    private Long id;
    private String userName;
    private Integer age;
    private String email;
    private LocalDateTime createTime;
    // 省略 getter / setter
}
```

```text
MyBatis 方式                              MyBatis-Plus 方式
══════════════════════════════            ══════════════════════════════
XML <resultMap> 定义列 → 属性映射         @TableName + 驼峰自动转换
XML <insert> useGeneratedKeys             @TableId(type = IdType.AUTO)
每个字段都要在 resultMap 中声明            字段自动映射，无需声明
```

**常用实体注解：**

| 注解                         | 作用                   | 示例                                                  |
| ---------------------------- | ---------------------- | ----------------------------------------------------- |
| `@TableName`                 | 指定表名               | `@TableName("t_user")`                                |
| `@TableId`                   | 指定主键字段及主键策略 | `@TableId(type = IdType.AUTO)`                        |
| `@TableField`                | 指定字段映射细节       | `@TableField("user_name")`                            |
| `@TableField(exist = false)` | 标记非数据库字段       | `@TableField(exist = false) private String fullName;` |
| `@TableLogic`                | 标记逻辑删除字段       | `@TableLogic private Integer deleted;`                |
| `@Version`                   | 标记乐观锁版本号字段   | `@Version private Integer version;`                   |

> `@TableField(exist = false)` 很实用——实体类中可以有不映射到数据库的临时字段（如计算属性、关联对象），MP 在生成 SQL 时会自动忽略它们。

### 2.3 BaseMapper：继承即拥有 CRUD

这是 MyBatis-Plus 最核心的简化——Mapper 接口只需继承 `BaseMapper<T>`，立刻获得 17 个 CRUD 方法，**不需要写任何 SQL，也不需要 XML 文件**：

> **Illustrative fragment** —— 展示 BaseMapper 的继承关系和可用方法。

```java
package com.example.javadoc.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.javadoc.module.user.entity.User;

// 只需继承 BaseMapper<User>，无需任何 XML 或注解 SQL
public interface UserMapper extends BaseMapper<User> {
    // 空接口——已经拥有全部 CRUD 方法
    // 一般与 IService 和 ServiceImpl 结合使用
}
```

```java
// 在 Spring Boot 启动类上扫描 Mapper
@MapperScan("com.example.javadoc.module.**.mapper")
@SpringBootApplication
public class JavaDocApplication { ... }
```

---

## 3. Service 层封装：IService 与 ServiceImpl

`BaseMapper` 解决了 Mapper 层的 CRUD。实际项目中，Service 层同样采用**接口 + 实现类**的配对模式——`IService<T>` 与 `ServiceImpl<M, T>`：

```
IService<User>             ←→  ServiceImpl<UserMapper, User>
（接口：声明"能做什么"）         （实现：提供"怎么做"）
      │                                │
      │ extends                        │ extends + implements
      ▼                                ▼
UserService                      UserServiceImpl
```

它们**必须成对出现**：`IService` 定义接口契约，`ServiceImpl` 提供全部通用 CRUD 实现。继承后，单表操作中"根据 ID 查/删/改"、"列表查询"、"批量操作"、"分页"等通用方法**你都不用再自己写了**：

```java
// 接口：继承 IService
public interface UserService extends IService<User> {
    // 自定义业务方法（通用 CRUD 不用声明，已继承）
    List<User> getActiveUsers();
}

// 实现类：继承 ServiceImpl，实现自己的接口
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    // ---- 以下方法来自 IService，不用自己写 ----
    // getById(id), save(entity), saveBatch(list),
    // updateById(entity), removeById(id), page(page, wrapper) ...

    // ---- 自定义业务方法 ----
}
```

内置方法直接在 Service 中调用，不需要通过 Mapper：

**内置方法的使用**——`IService` 提供的方法可以直接使用，不需要自己实现：

```java
// 在 Controller 中：通过注入的 userService 调用
@RestController
public class UserController {
    @Autowired
    private UserService userService;

    public List<User> allUsers() {
        return userService.list();     // 查询全部（内部调用 mapper.selectList(null)）
    }

    public void addUser(User user) {
        userService.save(user);        // 插入（内部调用 mapper.insert()）
    }

    public void deleteUser(Long id) {
        userService.removeById(id);    // 删除（内部调用 mapper.deleteById()）
    }
}
```

`IService` 的方法本质上是 `BaseMapper` 方法的 Service 层封装，调用链路如下：

```
Controller / ServiceImpl
        │
        │  调用 IService 方法
        ▼
   userService.list()
        │
        │  内部实现
        ▼
   mapper.selectList(null)
        │
        │  MyBatis 执行
        ▼
     SQL: SELECT * FROM user
```

使用 `IService` 后，你只需要记住 IService 的方法，不需要记 BaseMapper 的方法名：

```
你想做什么              IService 方法               不用记 BaseMapper 方法
════════════════════════════════════════════════════════════════════════════
插入                  save(entity)               ~~mapper.insert(entity)~~
根据 ID 查            getById(id)                ~~mapper.selectById(id)~~
查询全部              list()                     ~~mapper.selectList(null)~~
条件查询              list(wrapper)              ~~mapper.selectList(wrapper)~~
更新                  updateById(entity)         ~~mapper.updateById(entity)~~
删除                  removeById(id)             ~~mapper.deleteById(id)~~
```

只有在**不使用 ServiceImpl**（见 [第 4 节](#4-条件构造器手动使用-wrapper)）时，才需要直接调用 Mapper 方法。

### 3.1 链式查询：lambdaQuery()

`lambdaQuery()` 是 `IService` 提供的方法，继承 `ServiceImpl` 后直接可用，无需额外 import。完整示例：

```java
package com.example.javadoc.module.user.service.impl;

import com.example.javadoc.module.user.entity.User;
import com.example.javadoc.module.user.service.UserService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    @Override
    public List<User> getActiveUsers() {
        // lambdaQuery() 来自 IService，直接调用
        return lambdaQuery()
                .gt(User::getAge, 18)
                .eq(User::getStatus, 1)
                .orderByDesc(User::getCreateTime)
                .list();
    }
}
```

**多条件动态查询**——`condition` 参数为 `false` 时该条件不生效，替代了 MyBatis 的 `<if>` 标签：

```java
public List<User> search(String userName, Integer minAge, Integer maxAge) {
    return lambdaQuery()
            .like(userName != null && !userName.isBlank(),
                  User::getUserName, userName)
            .ge(minAge != null, User::getAge, minAge)
            .le(maxAge != null, User::getAge, maxAge)
            .orderByDesc(User::getCreateTime)
            .list();
}
```

**常用条件方法**——`lambdaQuery()` 和 `lambdaUpdate()` 都支持以下方法：

```
方法                  SQL 等价                    示例
════════════════════════════════════════════════════════════════
eq(column, val)       = ?                         eq(User::getAge, 25)
ne(column, val)       != ?                        ne(User::getStatus, 0)
gt / ge / lt / le     > / >= / < / <=             gt(User::getAge, 18)
between               BETWEEN ? AND ?             between(User::getAge, 18, 60)
like                  LIKE '%val%'                like(User::getName, "张")
likeLeft / likeRight  LIKE '%val' / 'val%'        likeRight(...)
in                    IN (?, ?, ...)              in(User::getStatus, List.of(1,2))
isNull / isNotNull    IS NULL / IS NOT NULL       isNull(User::getEmail)
orderByAsc / Desc     ORDER BY col ASC/DESC       orderByDesc(User::getCreateTime)
groupBy               GROUP BY col                groupBy(User::getStatus)
select                SELECT col1, col2           select(User::getId, User::getName)
```

> 所有方法都有带 `boolean condition` 参数的重载版本——`condition` 为 `false` 时该条件不生效，这是实现动态查询的关键（见上方 `search()` 示例）。

### 3.2 链式更新：lambdaUpdate()

同样在 `UserServiceImpl` 内部使用，条件、赋值、执行全在一条链上完成：

```java
public void deactivateInactiveUsers() {
    // 将年龄 > 30 且状态为活跃的用户，状态改为不活跃
    lambdaUpdate()
            .gt(User::getAge, 30)
            .eq(User::getStatus, 1)
            .set(User::getStatus, 0)
            .update();
    // 生成 SQL：UPDATE user SET status = 0 WHERE age > 30 AND status = 1
}
```

### 3.3 IService 提供的通用方法

以下方法全部由 `IService` 内置实现，继承后**直接使用，不需要自己写**：

```
IService 内置方法（继承即用，不用自己写）
═══════════════════════════════════════════════════════════════════

单条操作
  getById(id)              根据 ID 查询
  getOne(wrapper)          根据条件查询一条
  save(entity)             插入
  saveOrUpdate(entity)     插入或更新
  updateById(entity)       根据 ID 更新
  removeById(id)           根据 ID 删除

列表操作
  list()                   查询全部
  list(wrapper)            条件查询
  listByIds(ids)           多个 ID 查询

批量操作
  saveBatch(list)          批量插入
  saveOrUpdateBatch(list)  批量插入或更新
  updateBatchById(list)    批量更新
  removeBatchByIds(ids)    批量删除

链式调用
  lambdaQuery()            Lambda 链式查询（见 3.1）
  lambdaUpdate()           Lambda 链式更新（见 3.2）

统计 & 分页
  count()                  总记录数
  count(wrapper)           条件计数
  page(page, wrapper)      分页查询
```

> 以上覆盖了单表 CRUD 的全部通用场景。你只需要在 Service 中编写**真正的业务逻辑**——关联查询、统计报表、事务编排等。
>
> `lambdaQuery()` / `lambdaUpdate()` 底层依赖 `LambdaQueryWrapper` / `LambdaUpdateWrapper` 构建条件（见 [第 4 节](#4-条件构造器手动使用-wrapper)）。

---

## 5. 分页插件：告别手写 LIMIT

MyBatis 方式实现分页需要两步：写一个 `COUNT(*)` 查总数，再写一个 `LIMIT offset, size` 查数据。MyBatis-Plus 提供了**分页插件**，自动完成这两步。

### 配置分页插件

在配置类中注册 `PaginationInnerInterceptor`：

> **Illustrative fragment** —— 展示分页插件的配置方式。

```java
package com.example.javadoc.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyBatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 注册分页插件，指定数据库类型
        interceptor.addInnerInterceptor(
            new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

> `MybatisPlusInterceptor` 是 MyBatis-Plus 的插件机制入口。分页只是其中一个插件，后续还会看到逻辑删除、乐观锁等也以插件形式注册。

### 使用分页

```java
// Service 中调用（需要 IService，见第 3 节）
public IPage<User> pageQuery(int pageNum, int pageSize, String userName) {
    LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
    wrapper.like(userName != null && !userName.isBlank(),
                 User::getUserName, userName);
    wrapper.orderByDesc(User::getCreateTime);

    // page() 自动执行 COUNT + LIMIT
    return this.page(new Page<>(pageNum, pageSize), wrapper);
}
```

```
分页流程
═══════════════════════════════════════════════════════════════════

  你的代码                          插件自动完成
  ────────                          ────────────

  new Page<>(1, 10)
  + selectPage(page, wrapper)
          │
          ▼
  ┌───────────────┐         ┌──────────────────────────┐
  │  原始查询      │────────▶│ 1. SELECT COUNT(*)       │ ← 自动加
  │  SELECT *     │         │    FROM user WHERE ...    │
  │  FROM user    │         │                          │
  │  WHERE ...    │         │ 2. SELECT *              │ ← 自动加 LIMIT
  │               │         │    FROM user WHERE ...   │
  │               │         │    LIMIT 0, 10           │
  └───────────────┘         └──────────────────────────┘
          │
          ▼
  IPage<User>
    ├── records: List<User>    ← 当前页数据
    ├── total: 156             ← 总记录数（COUNT 结果）
    ├── pages: 16              ← 总页数
    ├── current: 1             ← 当前页码
    └── size: 10               ← 每页大小
```

在 Controller 中使用：

```java
@GetMapping("/page")
public IPage<User> page(@RequestParam(defaultValue = "1") int pageNum,
                         @RequestParam(defaultValue = "10") int pageSize,
                         @RequestParam(required = false) String userName) {
    return userService.pageQuery(pageNum, pageSize, userName);
}
```

> 分页插件会根据 `DbType` 自动适配不同数据库的分页语法（MySQL 用 `LIMIT`，Oracle 用 `ROWNUM`，PostgreSQL 用 `LIMIT OFFSET`），你不需要修改任何代码就能切换数据库。

---

## 6. 常用功能

### 6.1 逻辑删除：@TableLogic

实际项目中很少真正 `DELETE` 数据——更多是用一个 `deleted` 字段标记"已删除"。MyBatis 方式需要手动在每个查询加 `WHERE deleted = 0`，在每个删除操作改为 `UPDATE SET deleted = 1`。

MyBatis-Plus 用 `@TableLogic` 注解一行搞定：

> **Illustrative fragment** —— 展示逻辑删除的配置和使用。

```java
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userName;
    private Integer age;

    @TableLogic   // 标记为逻辑删除字段
    private Integer deleted;  // 0=未删除，1=已删除
}
```

配置后，`BaseMapper` 的行为自动改变：

```
操作              实际执行的 SQL
════════════════════════════════════════════════════════════

selectById(1)     SELECT * FROM user WHERE id = 1 AND deleted = 0
                  ↑ 自动追加

deleteById(1)     UPDATE user SET deleted = 1 WHERE id = 1 AND deleted = 0
                  ↑ DELETE 变成了 UPDATE

selectList(null)  SELECT * FROM user WHERE deleted = 0
                  ↑ 自动追加
```

> 逻辑删除是**全局配置**——在 `application.yml` 中设置了 `logic-delete-field` 后，所有实体的 `deleteById`、`selectList` 等方法都会自动应用逻辑删除，无需逐个标注 `@TableLogic`。但如果只想对特定字段生效，可以在实体上显式标注。

### 6.2 自动填充：MetaObjectHandler

很多表都有 `create_time`、`update_time`、`create_by`、`update_by` 这类审计字段——每次插入/更新都要手动赋值。MyBatis-Plus 提供了自动填充机制：

> **Illustrative fragment** —— 展示自动填充的实现方式。

```java
// 第一步：在实体字段上标注填充策略
@TableName("user")
public class User {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String userName;

    @TableField(fill = FieldFill.INSERT)          // 插入时自动填充
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)   // 插入和更新时自动填充
    private LocalDateTime updateTime;
}
```

```java
// 第二步：实现 MetaObjectHandler 并注册为 Spring Bean（@Component）
package com.example.javadoc.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;

@Component
public class MyMetaObjectHandler implements MetaObjectHandler {

    // 插入时填充
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime::now,
                              LocalDateTime.class);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime::now,
                              LocalDateTime.class);
    }

    // 更新时填充
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime::now,
                              LocalDateTime.class);
    }
}
```

```
填充策略                  触发时机
════════════════════════════════════════
FieldFill.DEFAULT         不填充（默认）
FieldFill.INSERT          仅 insert 时填充
FieldFill.UPDATE          仅 update 时填充
FieldFill.INSERT_UPDATE   insert 和 update 时都填充
```

> `strictInsertFill` / `strictUpdateFill` 只在字段值为 `null` 时才填充——如果调用方已经手动赋值，自动填充不会覆盖。

### 6.3 主键策略：IdType

MyBatis-Plus 支持多种主键生成策略：

```
IdType 值                  策略                          适用场景
═══════════════════════════════════════════════════════════════════
IdType.AUTO                数据库自增                    MySQL AUTO_INCREMENT
IdType.ASSIGN_ID           雪花算法（默认）              分布式系统，Long 类型
IdType.ASSIGN_UUID         UUID（无横线）               String 类型主键
IdType.INPUT               手动输入                     调用方指定 ID
IdType.NONE                跟随全局配置                 未指定时使用全局策略
```

```java
// 方式一：注解指定
@TableId(type = IdType.AUTO)
private Long id;

// 方式二：全局配置（application.yml）
// mybatis-plus.global-config.db-config.id-type: assign_id
```

> **默认策略是 `ASSIGN_ID`（雪花算法）**——即使你不配置，MyBatis-Plus 也会自动生成一个 19 位的 Long 型 ID。这对于分布式系统非常实用，避免了数据库自增 ID 的瓶颈和分库分表问题。

---

## 7. 与原生 MyBatis 混用

MyBatis-Plus 不替换 MyBatis。`lambdaQuery()` / `LambdaQueryWrapper` 能处理单表的全部条件查询，但遇到**多表 JOIN**、存储过程、数据库特定语法等超出 Wrapper 表达能力的场景时，你仍然可以用原生 MyBatis 的 XML 方式——两种方式可以在同一个 Mapper 接口中共存：

> **Illustrative fragment** —— 展示 MyBatis-Plus 与原生 MyBatis 的混用方式。

```java
public interface UserMapper extends BaseMapper<User> {

    // ① MyBatis-Plus 自动提供的方法——无需 XML
    // selectById, insert, selectList(wrapper), ...

    // ② 多表 JOIN 等超出 Wrapper 表达能力的查询——用 XML 写 SQL
    /** 查询用户及其订单总数（多表 JOIN + 子查询） */
    IPage<UserOrderStat> selectUserOrderStats(
            IPage<?> page,
            @Param("userName") String userName);
}
```

```xml
<!-- UserMapper.xml —— 只写自定义方法的 SQL -->
<mapper namespace="com.example.javadoc.module.user.mapper.UserMapper">

    <select id="selectUserOrderStats"
            resultType="com.example.javadoc.module.user.dto.UserOrderStat">
        SELECT u.id,
               u.user_name,
               COUNT(o.id) AS order_count,
               COALESCE(SUM(o.total_amount), 0) AS total_spent
        FROM user u
        LEFT JOIN `order` o ON u.id = o.user_id
        <where>
            <if test="userName != null and userName != ''">
                AND u.user_name LIKE CONCAT('%', #{userName}, '%')
            </if>
        </where>
        GROUP BY u.id, u.user_name
        ORDER BY total_spent DESC
    </select>

</mapper>
```

> **关键点**：自定义方法的分页也能自动工作——只要第一个参数是 `IPage` 类型，分页插件就会自动拦截并改写 SQL。这就是本节 `IPage<UserOrderStat> selectUserOrderStats(IPage<?> page, ...)` 能自动分页的原因。

---

## 8. 代码生成器

MyBatis-Plus Generator 可以根据数据库表结构自动生成实体类、Mapper 接口、Service 接口、Service 实现类、Controller 等代码。新增一张表时，几秒钟就能产出完整的 CRUD 骨架。

### 8.1 依赖

> **Illustrative fragment** —— 展示代码生成器的 Maven 依赖。

```xml
<!-- MyBatis-Plus 代码生成器 -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-generator</artifactId>
    <version>3.5.17</version>
</dependency>

<!-- 模板引擎（二选一） -->
<dependency>
    <groupId>org.freemarker</groupId>
    <artifactId>freemarker</artifactId>
</dependency>
<!-- 或 Velocity（默认引擎） -->
<dependency>
    <groupId>org.apache.velocity</groupId>
    <artifactId>velocity-engine-core</artifactId>
    <version>2.4.1</version>
</dependency>
```

> 代码生成器是**开发时工具**，不需要加到生产环境。通常写一个带 `main` 方法的类，运行一次即可。

### 8.2 快速生成：FastAutoGenerator

使用构建器模式配置生成器：

> **Illustrative fragment** —— 展示代码生成器的完整配置示例。

```java
package com.example.javadoc.generator;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.engine.FreemarkerTemplateEngine;

public class CodeGenerator {

    public static void main(String[] args) {
        FastAutoGenerator.create(
                "jdbc:mysql://localhost:3306/mydb?useSSL=false&serverTimezone=Asia/Shanghai",
                "root",
                "your_password")
            // 全局配置
            .globalConfig(builder -> builder
                .author("your_name")
                .outputDir("src/main/java")
                .commentDate("yyyy-MM-dd")
            )
            // 包配置
            .packageConfig(builder -> builder
                .parent("com.example.javadoc.module")
                .entity("entity")
                .mapper("mapper")
                .service("service")
                .serviceImpl("service.impl")
                .controller("controller")
            )
            // 策略配置
            .strategyConfig(builder -> builder
                // 指定要生成的表
                .addInclude("user", "order")
                // 过滤表前缀
                .addTablePrefix("t_")
                // 实体策略
                .entityBuilder()
                    .enableLombok()
                    .enableTableFieldAnnotation()
                // Controller 策略
                .controllerBuilder()
                    .enableRestStyle()
                // Service 策略
                .serviceBuilder()
                    .formatServiceFileName("%sService")
            )
            // 使用 Freemarker 引擎
            .templateEngine(new FreemarkerTemplateEngine())
            .execute();
    }
}
```

运行后生成的目录结构：

```
src/main/java/com/example/javadoc/module/
├── entity/
│   ├── User.java              ← @Data + @TableName
│   └── Order.java
├── mapper/
│   ├── UserMapper.java        ← extends BaseMapper<User>
│   └── OrderMapper.java
├── service/
│   ├── UserService.java       ← extends IService<User>
│   ├── OrderService.java
│   └── impl/
│       ├── UserServiceImpl.java   ← extends ServiceImpl<..., User>
│       └── OrderServiceImpl.java
└── controller/
    ├── UserController.java    ← @RestController
    └── OrderController.java

src/main/resources/mapper/
├── UserMapper.xml             ← 空的 XML 骨架（用于自定义 SQL）
└── OrderMapper.xml
```

### 8.3 配置详解

代码生成器的配置分为四个模块：

```
FastAutoGenerator 配置结构
═══════════════════════════════════════════════════════════════════

┌─ globalConfig ─────────────────────────────────────────────────┐
│  作者、输出目录、注释日期格式、是否开启 Swagger 注解           │
└────────────────────────────────────────────────────────────────┘

┌─ packageConfig ────────────────────────────────────────────────┐
│  父包名、各层子包名（entity/mapper/service/controller）         │
│  XML 输出路径（pathInfo）                                       │
└────────────────────────────────────────────────────────────────┘

┌─ strategyConfig ───────────────────────────────────────────────┐
│  要生成的表名（addInclude）                                     │
│  表前缀过滤（addTablePrefix）                                  │
│  实体策略：Lombok、注解、父类、字段类型                        │
│  Mapper 策略：是否生成 @Mapper 注解                             │
│  Service 策略：文件命名格式                                    │
│  Controller 策略：REST 风格、路由前缀                           │
└────────────────────────────────────────────────────────────────┘

┌─ templateEngine ───────────────────────────────────────────────┐
│  Velocity（默认）或 Freemarker                                 │
└────────────────────────────────────────────────────────────────┘
```

**常用策略配置：**

```java
.strategyConfig(builder -> builder
    // 指定表
    .addInclude("user", "order", "order_item")
    .addTablePrefix("t_")           // t_user → User

    // 实体配置
    .entityBuilder()
        .enableLombok()              // 生成 @Data
        .enableTableFieldAnnotation() // 生成 @TableName, @TableField
        .enableChainModel()          // 启用链式 setter

    // Mapper 配置
    .mapperBuilder()
        .enableMapperAnnotation()    // 生成 @Mapper

    // Service 配置
    .serviceBuilder()
        .formatServiceFileName("%sService")       // UserService
        .formatServiceImplFileName("%sServiceImpl") // UserServiceImpl

    // Controller 配置
    .controllerBuilder()
        .enableRestStyle()           // 生成 @RestController
        .enableHyphenStyle()         // URL 用连字符：/user-info
)
```

### 8.4 自定义模板

如果默认生成的代码不符合团队规范，可以用自定义模板替换。在 `src/main/resources/templates/` 下放置 Freemarker 模板文件：

```
templates/
├── controller.java.ftl     ← Controller 模板
├── service.java.ftl        ← Service 接口模板
├── serviceImpl.java.ftl    ← Service 实现模板
├── mapper.java.ftl         ← Mapper 接口模板
├── mapper.xml.ftl          ← Mapper XML 模板
└── entity.java.ftl         ← 实体类模板
```

```java
// 指定自定义模板路径
.templateConfig(builder -> builder
    .entity("templates/entity.java")
    .mapper("templates/mapper.java")
    .service("templates/service.java")
    .controller("templates/controller.java")
)
```

> 自定义模板让你可以在生成代码中加入团队特有的基类继承、通用方法、注解风格等，避免每次生成后还要手动修改。

---

## 9. 速查清单

### 9.1 实体注解速查

```
注解                         作用                     常用属性
════════════════════════════════════════════════════════════════════
@TableName("table")          指定表名                 value
@TableId(type = IdType.X)    指定主键及策略           type (AUTO/ASSIGN_ID/INPUT)
@TableField("column")        指定字段映射             value, exist, fill
@TableField(exist = false)   非数据库字段              exist = false
@TableLogic                  逻辑删除字段             —
@Version                     乐观锁版本号字段          —
```

### 9.2 BaseMapper 方法速查

```
方法                                返回值          说明
════════════════════════════════════════════════════════════════
insert(entity)                      int             插入一条
deleteById(id)                      int             按 ID 删除
delete(wrapper)                     int             按条件删除
deleteBatchIds(ids)                 int             批量 ID 删除
updateById(entity)                  int             按 ID 更新
update(entity, wrapper)             int             按条件更新
selectById(id)                      T               按 ID 查询
selectBatchIds(ids)                 List<T>         批量 ID 查询
selectOne(wrapper)                  T               条件查询单条
selectList(wrapper)                 List<T>         条件查询列表
selectCount(wrapper)                Long            条件计数
selectPage(page, wrapper)           IPage<T>        分页查询
```

### 9.3 Wrapper 条件速查

```
方法                    SQL                     示例
════════════════════════════════════════════════════════════════
eq                      =                       eq(User::getAge, 25)
ne                      !=                      ne(User::getStatus, 0)
gt / ge / lt / le       > / >= / < / <=         gt(User::getAge, 18)
between                 BETWEEN ... AND ...     between(User::getAge, 18, 30)
like                    LIKE '%val%'            like(User::getName, "张")
likeLeft / likeRight    LIKE '%val' / 'val%'    likeRight(User::getName, "张")
in                      IN (...)                in(User::getStatus, List.of(1,2))
isNull / isNotNull      IS NULL / IS NOT NULL   isNull(User::getEmail)
orderByAsc / Desc       ORDER BY ... ASC/DESC   orderByDesc(User::getCreateTime)
groupBy                 GROUP BY ...            groupBy(User::getStatus)
select                  SELECT 指定列           select(User::getId, User::getName)
```

### 9.4 常见陷阱

```
陷阱                                  现象                          解决方案
══════════════════════════════════════════════════════════════════════════════
Starter 版本与 Spring Boot 不匹配     启动失败 / NoSuchMethodError    Boot 4 用 mybatis-plus-spring-boot4-starter
实体类缺少 @TableName                 MP 无法确定表名               加 @TableName 或配置 table-prefix
LambdaQueryWrapper 传了 null 条件     生成的 SQL 缺少条件            condition 参数用布尔表达式控制
分页插件未注册                       selectPage 返回空或报错        配置 MybatisPlusInterceptor + PaginationInnerInterceptor
逻辑删除后查不到数据                  SELECT 自动追加 deleted = 0    确认字段值和 global-config 配置一致
自动填充不生效                       字段值仍为 null                确认 @TableField(fill=...) + MetaObjectHandler 已注册
BaseMapper 与自定义 XML 方法冲突     方法签名覆盖                   自定义方法用不同方法名
```

---

## References

- [MyBatis-Plus 官方文档](https://baomidou.com/) — 安装、CRUD、条件构造器、插件配置完整参考
- [MyBatis-Plus 安装指南](https://baomidou.com/en/getting-started/install/) — Spring Boot 2/3/4 Starter 坐标与版本对应
- [MyBatis-Plus 代码生成器](https://baomidou.com/en/guides/new-code-generator/) — FastAutoGenerator 配置与模板引擎
- [MyBatis-Plus GitHub](https://github.com/baomidou/mybatis-plus) — 源码与 Issue
- [MyBatis 指南](mybatis-guide.md) — MyBatis 基础：XML 映射、动态 SQL、关联查询
