# MyBatis 指南

> 本指南循序渐进介绍 MyBatis —— 从"为什么不用 JDBC 直接写 SQL"到"用 MyBatis 精确控制每一条 SQL"，每步只引入一个新概念。
> 适用于 MyBatis 3.x / Spring Boot 3.x。假设你已了解 Spring IOC/DI 基础（可参考 [Spring IOC/DI 指南](spring-ioc-di-guide.md)）。

---

## 目录

1. [为什么需要 MyBatis](#1-为什么需要-mybatis)
2. [快速入门：从零搭建第一个 Mapper](#2-快速入门从零搭建第一个-mapper)
   - [2.1 依赖与配置](#21-依赖与配置)
   - [2.2 定义实体与 Mapper 接口](#22-定义实体与-mapper-接口)
   - [2.3 编写 XML 映射文件](#23-编写-xml-映射文件)
   - [2.4 扫描 Mapper 并运行](#24-扫描-mapper-并运行)
3. [SQL 映射基础](#3-sql-映射基础)
   - [3.1 #{} 与 ${}：参数占位的两条路](#31-与-参数占位的两条路)
   - [3.2 传递多个参数](#32-传递多个参数)
   - [3.3 结果映射：resultType 与 resultMap](#33-结果映射resulttype-与-resultmap)
   - [3.4 注解方式替代 XML](#34-注解方式替代-xml)
4. [动态 SQL：让 SQL 根据条件变化](#4-动态-sql让-sql-根据条件变化)
5. [关联查询：一对一与一对多](#5-关联查询一对一与一对多)
6. [MyBatis vs JPA：如何选择](#6-mybatis-vs-jpa如何选择)
7. [速查清单](#7-速查清单)

---

## 1. 为什么需要 MyBatis

### JDBC 的痛点

用纯 JDBC 操作数据库，即使是最简单的"根据 ID 查用户"，也需要写大量样板代码：

> **Illustrative fragment** —— 展示 JDBC 样板代码的结构，省略了异常处理和资源关闭的完整逻辑。

```java
// JDBC 方式：查一个用户，90% 是样板代码
String sql = "SELECT id, name, age FROM user WHERE id = ?";
Connection conn = null;
PreparedStatement ps = null;
ResultSet rs = null;
try {
    conn = dataSource.getConnection();
    ps = conn.prepareStatement(sql);
    ps.setLong(1, userId);
    rs = ps.executeQuery();
    if (rs.next()) {
        User user = new User();
        user.setId(rs.getLong("id"));
        user.setName(rs.getString("name"));
        user.setAge(rs.getInt("age"));
        // ... 返回 user
    }
} catch (SQLException e) { /* ... */ }
finally { /* 关闭 rs, ps, conn */ }
```

问题一目了然：

- **连接管理、try-catch-finally、结果集遍历** 都是重复劳动
- **字段名硬编码**（`"id"`, `"name"`），重构时容易遗漏
- **SQL 与 Java 代码耦合**，改一行 SQL 要重新编译

### JPA 的局限

Spring Data JPA 大幅简化了数据访问，但它也有自己的边界：

```
JPA 的强项                     JPA 的弱项
══════════════════════        ══════════════════════════
简单 CRUD（自动生成 SQL）      复杂关联查询（多表 JOIN）
命名规范查询                   动态条件拼接（可选筛选）
对象关系映射（ORM）            原生 SQL 优化（强制索引）
                              存储过程调用
                              结果集不是实体（报表、统计）
```

JPA 的核心思路是 **"你写对象，框架生成 SQL"**。这在简单场景下很高效，但当 SQL 变复杂时——比如五表 JOIN、动态 WHERE 条件、分页 + 排序 + 统计——JPA 自动生成的 SQL 往往不是你想要的，调试和优化也很困难。

### MyBatis 的定位

MyBatis 走了一条中间路线：

```
                SQL 控制权           样板代码量

JDBC 原生      你完全控制（累）      最多
MyBatis        你写 SQL（爽）       框架消除
JPA/Hibernate  框架生成（不可控）    最少
```

核心思想一句话：**"SQL 由你写，但参数设置、结果映射、连接管理全部由框架代劳"**。

MyBatis 不隐藏 SQL——恰恰相反，它让你直面 SQL，同时消除 `PreparedStatement.setXxx()`、`ResultSet.getString()` 这类机械劳动。你需要的是一个能把 **SQL 模板** 和 **Java 方法** 关联起来的桥梁——这就是 Mapper。

---

## 2. 快速入门：从零搭建第一个 Mapper

用一个贯穿场景来演示：**用户管理系统——根据 ID 查用户、按姓名模糊搜索**。

> 前置知识：Spring Boot 项目依赖通过 Maven/Gradle 管理，启动类用 `@SpringBootApplication` 标注。可参考 [Spring IOC/DI 指南](spring-ioc-di-guide.md)。

### 2.1 依赖与配置

在 `pom.xml` 中添加 MyBatis 和 MySQL 驱动：

> **Illustrative fragment** —— 展示 Maven 依赖声明，坐标基于 MyBatis Spring Boot Starter 3.x 最新稳定版。

```xml
<!-- MyBatis Spring Boot Starter -->
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>3.0.4</version>
</dependency>

<!-- MySQL 驱动（或 PostgreSQL、Oracle 等） -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

在 `application.yml` 中配置数据源：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mydb?useSSL=false&serverTimezone=Asia/Shanghai
    username: root
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver

# MyBatis 配置
mybatis:
  # XML 映射文件的位置（classpath 下的路径）
  mapper-locations: classpath:mapper/*.xml
  # 实体类别名包（在 XML 中可直接用类名，不用全限定名）
  type-aliases-package: com.example.javadoc.module.user.entity
  configuration:
    # 下划线转驼峰：数据库字段 user_name → Java 属性 userName
    map-underscore-to-camel-case: true
    # 打印 SQL 日志（开发环境开启，生产环境关闭）
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```

> `map-underscore-to-camel-case: true` 是最常用的配置之一：数据库列名习惯用 `user_name`，Java 属性名习惯用 `userName`。开启后，MyBatis 自动完成映射，不用在每个字段上加 `@Column` 或写 `resultMap`。

### 2.2 定义实体与 Mapper 接口

实体类：

```java
package com.example.javadoc.module.user.entity;

import java.time.LocalDateTime;

public class User {
    private Long id;
    private String userName;
    private Integer age;
    private String email;
    private LocalDateTime createTime;

    // 省略 getter / setter（实际项目可用 Lombok @Data）
}
```

Mapper 接口——只定义方法签名，不写实现类：

```java
package com.example.javadoc.module.user.mapper;

import com.example.javadoc.module.user.entity.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface UserMapper {

    /** 根据主键查询 */
    User selectById(@Param("id") Long id);

    /** 按姓名模糊搜索 */
    List<User> selectByName(@Param("name") String name);

    /** 插入用户，返回受影响行数 */
    int insert(User user);

    /** 更新用户 */
    int update(User user);

    /** 删除用户 */
    int deleteById(@Param("id") Long id);
}
```

> **关键点**：方法名任意，不是 JPA 的命名规范。方法名只用于找到对应的 XML `<select>` / `<insert>` 等标签（通过 `id` 匹配），不承担查询语义。

### 2.3 编写 XML 映射文件

在 `src/main/resources/mapper/` 下创建 `UserMapper.xml`（文件名通常与接口名一致）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<!-- namespace 必须指向 Mapper 接口的全限定名 -->
<mapper namespace="com.example.javadoc.module.user.mapper.UserMapper">

    <!-- 结果映射：定义数据库列与 Java 属性的对应关系 -->
    <resultMap id="BaseResultMap" type="User">
        <id column="id" property="id"/>
        <result column="user_name" property="userName"/>
        <result column="age" property="age"/>
        <result column="email" property="email"/>
        <result column="create_time" property="createTime"/>
    </resultMap>

    <!-- 根据 ID 查询 -->
    <select id="selectById" resultMap="BaseResultMap">
        SELECT id, user_name, age, email, create_time
        FROM user
        WHERE id = #{id}
    </select>

    <!-- 按姓名模糊搜索 -->
    <select id="selectByName" resultMap="BaseResultMap">
        SELECT id, user_name, age, email, create_time
        FROM user
        WHERE user_name LIKE CONCAT('%', #{name}, '%')
    </select>

    <!-- 插入用户：useGeneratedKeys 获取自增主键 -->
    <insert id="insert" useGeneratedKeys="true" keyProperty="id">
        INSERT INTO user (user_name, age, email, create_time)
        VALUES (#{userName}, #{age}, #{email}, #{createTime})
    </insert>

    <!-- 更新用户 -->
    <update id="update">
        UPDATE user
        SET user_name = #{userName},
            age = #{age},
            email = #{email}
        WHERE id = #{id}
    </update>

    <!-- 删除用户 -->
    <delete id="deleteById">
        DELETE FROM user WHERE id = #{id}
    </delete>

</mapper>
```

发生了什么？对比 JDBC 方式：

```
JDBC 手动方式                          MyBatis Mapper
══════════════════════════════        ══════════════════════════════
conn.prepareStatement(sql)      →     <select id="selectById"> ... SQL 放在 XML 中
ps.setLong(1, userId)           →     #{id} 占位，框架自动设参
rs = ps.executeQuery()          →     框架执行
user.setId(rs.getLong("id"))    →     resultMap 自动映射
user.setName(rs.getString(...)) →
rs.close(); ps.close();         →     框架管理
conn.close();                   →     连接池管理
```

你写的是蓝色部分（**SQL + resultMap**），余下全是框架的工作。

### 2.4 扫描 Mapper 并运行

在 Spring Boot 启动类或配置类上添加 `@MapperScan`：

```java
package com.example.javadoc;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.javadoc.module.**.mapper")
public class JavaDocApplication {
    public static void main(String[] args) {
        SpringApplication.run(JavaDocApplication.class, args);
    }
}
```

`@MapperScan` 会扫描指定包路径下所有接口，自动生成代理实现类（底层通过 `SqlSession.getMapper()` 实现）。此后你可以在任何 Service 中注入 Mapper：

```java
@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public User getById(Long id) {
        return userMapper.selectById(id);  // 像调用本地方法一样操作数据库
    }
}
```

---

## 3. SQL 映射基础

上一节跑通了基本流程，但你可能注意到了几个 `<select>` 里出现的东西——`#{id}`、`resultMap`、`@Param`。这一节逐一解释。

### 3.1 `#{}` 与 `${}`：参数占位的两条路

MyBatis 提供了两种参数占位方式，行为截然不同：

| 占位符   | 机制                                      | SQL 注入防护 | 典型用途                        |
| -------- | ----------------------------------------- | ------------ | ------------------------------- |
| `#{xxx}` | **预编译占位符**（PreparedStatement `?`） | ✅ 安全      | 参数值（WHERE 条件、INSERT 值） |
| `${xxx}` | **字符串拼接**（直接替换）                | ❌ 危险      | 动态表名、ORDER BY 字段名       |

```xml
<!-- #{id} → 预编译：SELECT ... WHERE id = ?，参数值作为 ? 传入 -->
<select id="selectById" resultMap="BaseResultMap">
    SELECT * FROM user WHERE id = #{id}
</select>

<!-- ${column} → 字符串拼接：SELECT * FROM user ORDER BY user_name DESC -->
<!-- 注意：column 的值必须是硬编码的安全值，绝不能是用户输入！ -->
<select id="selectAll" resultMap="BaseResultMap">
    SELECT * FROM user ORDER BY ${column} ${direction}
</select>
```

> **铁律**：能用 `#{}` 就用 `#{}`。只有表名、字段名、ORDER BY 排序方向等**不可能用预编译参数表示**的极少情况才用 `${}`，并且要确保值来自代码常量而非用户输入。

### 3.2 传递多个参数

当方法有多个参数时，MyBatis 需要知道每个占位符对应哪个参数。有三种方式：

**方式一：`@Param` 注解（推荐，最清晰）**

```java
// Mapper 接口
User selectByEmailAndAge(@Param("email") String email,
                         @Param("age") Integer age);
```

```xml
<select id="selectByEmailAndAge" resultMap="BaseResultMap">
    SELECT * FROM user
    WHERE email = #{email} AND age = #{age}
</select>
```

**方式二：用实体对象传参**

```java
// Mapper 接口
List<User> selectByCondition(User condition);
```

```xml
<select id="selectByCondition" resultMap="BaseResultMap">
    SELECT * FROM user
    WHERE user_name LIKE CONCAT('%', #{userName}, '%')
      AND age = #{age}
</select>
```

```java
// 调用时
User condition = new User();
condition.setUserName("张");
condition.setAge(25);
List<User> users = userMapper.selectByCondition(condition);
```

**方式三：不写 `@Param`（有限支持）**

Java 8+ 编译时可以保留参数名（需 `-parameters` 编译选项），MyBatis 可以直接用参数名。但依赖编译选项不够可靠，**推荐始终使用 `@Param`**。

### 3.3 结果映射：`resultType` 与 `resultMap`

MyBatis 提供两种指定返回类型的方式：

| 方式         | 适用场景                               | 示例                                          |
| ------------ | -------------------------------------- | --------------------------------------------- |
| `resultType` | 列名与属性名**完全一致**时             | `<select id="..." resultType="User">`         |
| `resultMap`  | 列名与属性名**不一致**，或需要关联映射 | `<select id="..." resultMap="BaseResultMap">` |

**resultType 的快捷方式：**

```xml
<!-- 当数据库列名与 Java 属性名完全一致，且开启了 map-underscore-to-camel-case 时 -->
<select id="selectById" resultType="User">
    SELECT id, user_name, age, email FROM user WHERE id = #{id}
</select>
```

`resultType="User"` 会触发 MyBatis 的**自动映射**：列名 → 属性名（下划线自动转驼峰）。但当列名和属性名不能完全对应（比如关联查询、嵌套对象），就得上 `resultMap`。

**resultMap 的完整写法：**

```xml
<resultMap id="UserResultMap" type="User">
    <!-- id：主键列，优化性能 -->
    <id column="id" property="id"/>
    <!-- result：普通列 -->
    <result column="user_name" property="userName"/>
    <result column="age" property="age"/>
    <result column="email" property="email"/>
    <!-- 类型处理器：Java LocalDateTime ↔ 数据库 TIMESTAMP -->
    <result column="create_time" property="createTime"
            javaType="java.time.LocalDateTime" jdbcType="TIMESTAMP"/>
</resultMap>
```

`<id>` 对性能有实际影响：MyBatis 用它判断两行是否为同一对象（类似 `equals`），用于缓存和嵌套映射的去重。

### 3.4 注解方式替代 XML

如果 SQL 比较简单，可以用注解写在 Mapper 接口上，省去 XML 文件：

```java
import org.apache.ibatis.annotations.*;

public interface UserMapper {

    @Select("SELECT id, user_name, age, email, create_time " +
            "FROM user WHERE id = #{id}")
    User selectById(@Param("id") Long id);

    @Insert("INSERT INTO user (user_name, age, email, create_time) " +
            "VALUES (#{userName}, #{age}, #{email}, #{createTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Update("UPDATE user SET user_name = #{userName}, " +
            "age = #{age}, email = #{email} WHERE id = #{id}")
    int update(User user);

    @Delete("DELETE FROM user WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
}
```

```
XML 方式 vs 注解方式

XML 方式：
  ✅ SQL 与 Java 代码完全分离
  ✅ 支持动态 SQL（if, foreach 等）
  ✅ 复杂关联映射更清晰
  ❌ 文件多、跳转不如注解方便

注解方式：
  ✅ 代码和 SQL 在一起，所见即所得
  ✅ 简单 CRUD 非常便捷
  ❌ 动态 SQL 写起来很痛苦（@SelectProvider 等方式）
  ❌ SQL 较长时注解变得难以阅读
```

> **实践建议**：简单 CRUD 用注解，涉及动态 SQL、多表关联的场景用 XML。一个项目中可以混用——同一个 Mapper 接口的部分方法用注解，部分方法用 XML。

---

## 4. 动态 SQL：让 SQL 根据条件变化

最典型的场景：**用户列表查询，支持按姓名、年龄、邮箱组合筛选，有空字段就忽略**。

纯 JDBC 方式需要手动拼接 SQL 字符串，代码冗长且容易出错。MyBatis 提供了一组 XML 标签来构建动态 SQL：

### 核心标签

```xml
<select id="selectByCondition" resultMap="BaseResultMap">
    SELECT id, user_name, age, email, create_time
    FROM user
    <!-- where 标签：自动处理第一个 AND/OR，无匹配条件时省略 WHERE -->
    <where>
        <!-- if：条件为真时才包含 -->
        <if test="userName != null and userName != ''">
            AND user_name LIKE CONCAT('%', #{userName}, '%')
        </if>
        <if test="age != null">
            AND age = #{age}
        </if>
        <if test="email != null and email != ''">
            AND email = #{email}
        </if>
    </where>
    ORDER BY create_time DESC
</select>
```

`<where>` 标签的两个智能行为：

1. 如果内部没有匹配的条件，自动**省略** `WHERE` 关键字
2. 自动**去掉**第一个 `AND` 或 `OR`（所以你不用纠结"第一个条件要不要加 AND"）

### 更多动态标签

**`<foreach>`：处理 IN 查询和批量操作**

```xml
<!-- 批量 ID 查询 -->
<select id="selectByIds" resultMap="BaseResultMap">
    SELECT * FROM user
    WHERE id IN
    <foreach collection="ids" item="id" open="(" separator="," close=")">
        #{id}
    </foreach>
</select>
```

```java
// Mapper 接口
List<User> selectByIds(@Param("ids") List<Long> ids);

// 调用
List<User> users = userMapper.selectByIds(List.of(1L, 2L, 3L));
// 生成 SQL：SELECT * FROM user WHERE id IN (?, ?, ?)
```

```xml
<!-- 批量插入 -->
<insert id="insertBatch">
    INSERT INTO user (user_name, age, email) VALUES
    <foreach collection="users" item="user" separator=",">
        (#{user.userName}, #{user.age}, #{user.email})
    </foreach>
</insert>
```

**`<choose>` / `<when>` / `<otherwise>`：相当于 Java 的 switch-case**

```xml
<select id="selectByCondition" resultMap="BaseResultMap">
    SELECT * FROM user
    <where>
        <choose>
            <when test="userName != null and userName != ''">
                AND user_name LIKE CONCAT('%', #{userName}, '%')
            </when>
            <when test="email != null and email != ''">
                AND email = #{email}
            </when>
            <otherwise>
                AND age &gt; 0    <!-- 兜底条件 -->
            </otherwise>
        </choose>
    </where>
</select>
```

> `&gt;` 是 `>` 的 XML 转义。在 `<select>` 中写 `<` / `>` 需要用 `&lt;` / `&gt;`，或使用 `<![CDATA[ ... ]]>` 包裹。

**`<set>`：动态 UPDATE，自动处理逗号**

```xml
<update id="updateSelective">
    UPDATE user
    <set>
        <if test="userName != null and userName != ''">
            user_name = #{userName},
        </if>
        <if test="age != null">
            age = #{age},
        </if>
        <if test="email != null and email != ''">
            email = #{email},
        </if>
    </set>
    WHERE id = #{id}
</update>
```

`<set>` 的行为：

- 没有匹配的 `<if>` → 不生成 `SET`（配合 `<where>` 使用，整体为空也不会出错）
- 自动去掉末尾多余的逗号

**`<sql>` 与 `<include>`：复用 SQL 片段**

```xml
<!-- 定义公共列 -->
<sql id="BaseColumns">
    id, user_name, age, email, create_time
</sql>

<!-- 引用公共列 -->
<select id="selectById" resultMap="BaseResultMap">
    SELECT <include refid="BaseColumns"/>
    FROM user
    WHERE id = #{id}
</select>

<select id="selectAll" resultMap="BaseResultMap">
    SELECT <include refid="BaseColumns"/>
    FROM user
    ORDER BY create_time DESC
</select>
```

---

## 5. 关联查询：一对一与一对多

数据库表有关联（`JOIN`），Java 对象有嵌套。MyBatis 如何把 JOIN 结果映射到嵌套对象？答案是 `<resultMap>` 中的 `<association>` 和 `<collection>`。

### 场景定义

```
user 表                    order 表
┌──────────────┐          ┌──────────────────┐
│ id (PK)      │←───1:N──│ id (PK)           │
│ user_name    │          │ user_id (FK)      │
│ age          │          │ order_no          │
│ email        │          │ total_amount      │
└──────────────┘          │ create_time       │
                          └──────────────────┘
```

### 5.1 一对一：`<association>`

查询订单并同时查出下单用户：

```java
// Order 实体包含 User 属性
public class Order {
    private Long id;
    private String orderNo;
    private Double totalAmount;
    private User user;        // 一对一：一个订单对应一个用户
    private LocalDateTime createTime;
}

public interface OrderMapper {
    Order selectByIdWithUser(@Param("id") Long id);
}
```

```xml
<resultMap id="OrderWithUserMap" type="Order">
    <id column="id" property="id"/>
    <result column="order_no" property="orderNo"/>
    <result column="total_amount" property="totalAmount"/>
    <result column="create_time" property="createTime"/>

    <!-- association：一对一关联 -->
    <association property="user" javaType="User">
        <id column="user_id" property="id"/>
        <result column="user_name" property="userName"/>
        <result column="email" property="email"/>
    </association>
</resultMap>

<select id="selectByIdWithUser" resultMap="OrderWithUserMap">
    SELECT o.id, o.order_no, o.total_amount, o.create_time,
           u.id AS user_id, u.user_name, u.email
    FROM `order` o
    LEFT JOIN user u ON o.user_id = u.id
    WHERE o.id = #{id}
</select>
```

> 注意用 `AS user_id` 等别名区分两张表的 `id` 列，否则会互相覆盖。

### 5.2 一对多：`<collection>`

查询用户并同时查出他的所有订单：

```java
public class User {
    private Long id;
    private String userName;
    private List<Order> orders;   // 一对多：一个用户可以有多个订单
    // ...
}

public interface UserMapper {
    User selectByIdWithOrders(@Param("id") Long id);
}
```

```xml
<resultMap id="UserWithOrdersMap" type="User">
    <id column="id" property="id"/>
    <result column="user_name" property="userName"/>
    <result column="age" property="age"/>

    <!-- collection：一对多关联 -->
    <collection property="orders" ofType="Order">
        <id column="order_id" property="id"/>
        <result column="order_no" property="orderNo"/>
        <result column="total_amount" property="totalAmount"/>
    </collection>
</resultMap>

<select id="selectByIdWithOrders" resultMap="UserWithOrdersMap">
    SELECT u.id, u.user_name, u.age,
           o.id AS order_id, o.order_no, o.total_amount
    FROM user u
    LEFT JOIN `order` o ON u.id = o.user_id
    WHERE u.id = #{id}
</select>
```

> `ofType="Order"` 告诉 MyBatis 集合的元素类型；`javaType`（集合本身的类型）通常省略，MyBatis 推断为 `List`。

### 5.3 分步查询 vs 联合查询

以上都是一条 SQL（JOIN）搞定。但有时拆成两步更合适：

```xml
<!-- 方式一：联合查询（一条 SQL，全部数据一次返回） -->
<!-- 上面已演示，适合数据量小、关联不深的情况 -->

<!-- 方式二：分步查询（N+1 查询） -->
<resultMap id="UserWithOrdersMap" type="User">
    <id column="id" property="id"/>
    <result column="user_name" property="userName"/>
    <!-- column 传给子查询的参数，select 指向另一个查询 -->
    <collection property="orders"
                column="id"
                select="com.example.javadoc.module.order.mapper.OrderMapper.selectByUserId"/>
</resultMap>

<select id="selectByIdWithOrders" resultMap="UserWithOrdersMap">
    SELECT id, user_name, age FROM user WHERE id = #{id}
</select>
```

```xml
<!-- OrderMapper.xml -->
<select id="selectByUserId" resultMap="OrderResultMap">
    SELECT * FROM `order` WHERE user_id = #{userId}
</select>
```

```
联合查询 vs 分步查询

联合查询（一条 SQL）：
  ✅ 一次数据库往返，性能好
  ❌ SQL 复杂，别名容易冲突
  ❌ 一对多时结果集膨胀（N 条订单 × M 个用户 = N×M 行）

分步查询（N+1）：
  ✅ SQL 简单，每个查询独立
  ✅ 可利用 MyBatis 一级/二级缓存
  ❌ N+1 问题：查 10 个用户会执行 11 条 SQL（1 次查用户 + 10 次查订单）
```

> **N+1 问题**：分步查询的典型性能陷阱。查询 N 条主记录后，每条记录触发一次子查询，总共 N+1 条 SQL。可以通过**延迟加载**（`fetchType="lazy"`）缓解——只在实际访问 `orders` 属性时才执行子查询。在 `application.yml` 中开启：
>
> ```yaml
> mybatis:
>   configuration:
>     lazy-loading-enabled: true
>     aggressive-lazy-loading: false
> ```

---

## 6. MyBatis vs JPA：如何选择

你已经看到了两种持久化方式——JPA 的"写对象，框架生成 SQL"和 MyBatis 的"写 SQL，框架做映射"。下表帮你决策：

| 维度           | JPA                                  | MyBatis                               |
| -------------- | ------------------------------------ | ------------------------------------- |
| SQL 控制力     | 低（自动生成，难以干预）             | **高（你写的每一条 SQL 都精确可控）** |
| 简单 CRUD 效率 | **极高（命名方法即查询）**           | 中（需手写 SQL 或注解）               |
| 复杂查询       | 差（JPQL/Criteria API 学习曲线陡峭） | **优（手写 SQL，优化空间大）**        |
| 动态查询       | Specification / QueryDSL             | **动态 SQL 标签，直观清晰**           |
| 数据库移植性   | **好（JPA 抽象了 SQL 方言）**        | 差（SQL 手写，换数据库需重写）        |
| 学习曲线       | 中（ORM 概念 + JPQL）                | **低（会 SQL 就会 MyBatis）**         |
| 报表 / 统计    | 弱                                   | **强（原生 SQL 直接写）**             |
| 存储过程       | 弱                                   | **强（直接调用）**                    |

**选择建议：**

```
你的项目特征                         推荐

SQL 场景以简单 CRUD 为主         →  JPA
SQL 精确控制 > 开发效率           →  MyBatis
需同时支持简单 CRUD 和复杂查询     →  MyBatis-Plus（MyBatis 增强）
数据库可能切换（MySQL → PgSQL）   →  JPA
已有大量 SQL 脚本需要复用         →  MyBatis
团队 SQL 能力强                   →  MyBatis
```

> **MyBatis-Plus** 是 MyBatis 的增强工具，在 MyBatis 基础上提供了类似 JPA 的便捷 CRUD（`BaseMapper<T>` 自动生成常见方法）和条件构造器（`LambdaQueryWrapper`），同时保留了 MyBatis 原生 SQL 的能力。如果你想要"简单场景自动 CRUD + 复杂场景手写 SQL"，MyBatis-Plus 是首选。其用法类似于：
>
> ```java
> // MyBatis-Plus 自动 CRUD，无需写任何 SQL
> userMapper.selectById(1L);
> userMapper.selectList(new LambdaQueryWrapper<User>().eq(User::getAge, 25));
> ```

---

## 7. 速查清单

### 7.1 配置文件速查

```yaml
mybatis:
  mapper-locations: classpath:mapper/*.xml # XML 文件路径
  type-aliases-package: com.example.entity # 别名包
  configuration:
    map-underscore-to-camel-case: true # 下划线 → 驼峰
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl # SQL 日志
    lazy-loading-enabled: true # 延迟加载
    aggressive-lazy-loading: false # 按需加载
```

### 7.2 XML 映射速查

```
元素              用途                        关键属性
══════════════════════════════════════════════════════════════
<select>         查询                        id, resultType / resultMap
<insert>         插入                        id, useGeneratedKeys, keyProperty
<update>         更新                        id
<delete>         删除                        id
<resultMap>      结果映射                    id, type
  <id>           主键列                      column, property
  <result>       普通列                      column, property
  <association>  一对一嵌套                  property, javaType
  <collection>   一对多嵌套                  property, ofType
<sql>            可复用 SQL 片段             id
<include>        引用 SQL 片段               refid
```

### 7.3 动态 SQL 标签速查

```
标签              作用                              示例
════════════════════════════════════════════════════════════
<where>           自动处理 WHERE + 首个 AND/OR        搭配 <if> 使用
<set>             自动处理 SET + 末尾逗号             搭配 <if> 使用
<if>              条件判断                           test="name != null"
<foreach>         遍历集合                           collection="ids"
<choose>          多选一（switch）                   内含 <when> / <otherwise>
<trim>            自定义前缀/后缀裁剪                 更灵活的 <where> / <set>
```

### 7.4 常用注解速查

```java
@Select("SELECT ...")          // 查询
@Insert("INSERT ...")          // 插入
@Update("UPDATE ...")          // 更新
@Delete("DELETE ...")          // 删除
@Results({@Result(...)})      // 结果映射（注解版 resultMap）
@Param("name")                 // 参数命名
@Options(useGeneratedKeys=true, keyProperty="id")  // 自增主键
@MapperScan("com.example.**.mapper")                // 扫描 Mapper 包
```

### 7.5 参数传递方式

```java
// 方式一：@Param（推荐）
User selectByEmailAndAge(@Param("email") String email,
                         @Param("age") Integer age);

// 方式二：实体对象
List<User> selectByCondition(User condition);

// 方式三：Map（不推荐，类型不安全）
List<User> selectByMap(Map<String, Object> params);
```

### 7.6 常见陷阱

```
陷阱                              现象                            解决方案
══════════════════════════════════════════════════════════════════════════
#{} 与 ${} 混用                   SQL 注入风险                   参数值用 #{}，表名/字段名才用 ${}
Mapper XML namespace 不匹配       找不到 Mapper                    namespace 必须等于接口全限定名
列名与属性名不一致                 查询结果为 null                 开启 map-underscore-to-camel-case
                                                                  或写 <resultMap>
@Param 忘记加                    参数绑定失败（arg0, param1）     多参数时始终加 @Param
N+1 查询                         性能急剧下降                    优先用 JOIN；必须分步时开启
                                                                  延迟加载 + 批量查询
resultType 与 resultMap 混用      复杂映射失效                    JOIN 查询 / 嵌套对象用 resultMap
```

---

## References

- [MyBatis 官方文档](https://mybatis.org/mybatis-3/) — SQL 映射、动态 SQL、配置完整参考
- [MyBatis Spring Boot Starter](https://mybatis.org/spring-boot-starter/) — Spring Boot 集成官方文档
- [MyBatis-Plus](https://baomidou.com/) — MyBatis 增强工具
