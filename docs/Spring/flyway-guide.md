# Flyway 指南

> 本指南循序渐进介绍 Flyway —— 数据库迁移（Migration）的事实标准。用"电商系统"的建表演进贯穿全文，每步只引入一个新概念。核心隐喻：**Flyway 是数据库的 Git**。
> 基于 Flyway 10.x / Spring Boot 3.x+。

---

## 目录

1. [为什么需要 Flyway](#1-为什么需要-flyway)
2. [核心概念三分钟速通](#2-核心概念三分钟速通)
3. [快速入门：三步让 Flyway 跑起来](#3-快速入门三步让-flyway-跑起来)
   - [3.1 添加依赖](#31-添加依赖)
   - [3.2 创建第一个迁移文件](#32-创建第一个迁移文件)
   - [3.3 启动项目，见证自动执行](#33-启动项目见证自动执行)
4. [迁移命名规范 —— Flyway 的"协议"](#4-迁移命名规范--flyway-的协议)
   - [4.1 版本化迁移（V）](#41-版本化迁移v)
   - [4.2 可重复迁移（R）](#42-可重复迁移r)
   - [4.3 撤销迁移（U）](#43-撤销迁移u)
   - [4.4 版本号策略](#44-版本号策略)
5. [深入 flyway_schema_history —— 状态机的秘密](#5-深入-flyway_schema_history--状态机的秘密)
6. [Spring Boot 配置全解](#6-spring-boot-配置全解)
   - [6.1 基础配置](#61-基础配置)
   - [6.2 环境隔离策略](#62-环境隔离策略)
7. [Java 迁移 —— 当 SQL 不够用时](#7-java-迁移--当-sql-不够用时)
8. [Baseline —— 给已有数据库"补票"](#8-baseline--给已有数据库补票)
9. [修复与排错](#9-修复与排错)
10. [CI/CD 集成 —— 让迁移自动化](#10-cicd-集成--让迁移自动化)
11. [速查清单](#11-速查清单)

---

## 1. 为什么需要 Flyway

### 场景：三个开发者，同一个数据库

想象一下这个场景——你正在开发一个电商系统，团队有三个人：

```
第一天：
  张三：在 user 表加了 age 字段      → ALTER TABLE user ADD COLUMN age INT;
  李四：在 user 表加了 phone 字段    → ALTER TABLE user ADD COLUMN phone VARCHAR(20);
  王五：创建了 order 表              → CREATE TABLE order (...);

第二天，张三要部署到测试环境：
  - "李四，你的 phone 字段 SQL 在哪？"
  - "王五，order 表建好了吗？"
  - "等等，这些 SQL 的执行顺序是什么？哪个先跑？"
  - "糟糕，我本地已经跑过了，测试环境跑重复了怎么办？"
```

### 手动管理 SQL 脚本的四个痛点

```
① 顺序混乱：谁先跑、谁后跑，靠人记忆，部署时漏执行是常态
② 状态不可知：不知道某个 SQL 在某个环境是否已经执行过
③ 回滚困难：执行出错了，不知道从哪开始恢复
④ 版本黑洞：数据库当前"长什么样"没有记录，新人接手一脸懵
```

### Flyway 的答案

```
┌──────────────────────────────────────────────────────────────┐
│                    Flyway = 数据库的 Git                       │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│   Git 管理代码                 Flyway 管理数据库               │
│   ────────────────             ──────────────────             │
│   git commit 快照              V1__xxx.sql 迁移文件            │
│   git log 历史                 flyway_schema_history 表       │
│   git diff 变更                对比 checksum 检测篡改          │
│   只提交变更部分               只执行未运行过的迁移             │
│   团队共享历史                 所有人看到的数据库结构一致        │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

**一句话：** Flyway 不帮你写 SQL，它帮你管理 SQL 的**版本**。每次数据库结构变更，写一个 SQL 文件，Flyway 自动按顺序执行、记录状态、防止重复。

---

## 2. 核心概念三分钟速通

### 迁移（Migration）是什么

一个迁移就是**一次数据库结构变更**。它可以是一个 SQL 文件，也可以是一段 Java 代码。每个迁移有唯一的版本号，Flyway 按版本号从小到大依次执行。

```
┌──────────────────────────────────────────────────────┐
│                   一次完整的迁移生命周期               │
├──────────────────────────────────────────────────────┤
│                                                      │
│  1. Flyway 启动，扫描 classpath:db/migration/         │
│                    ↓                                 │
│  2. 对比：哪些文件还没出现在 flyway_schema_history 中？ │
│                    ↓                                 │
│  3. 按版本号排序，依次执行未执行的迁移                   │
│                    ↓                                 │
│  4. 每执行一条，在 flyway_schema_history 中插入一条记录  │
│                    ↓                                 │
│  5. 所有迁移完成 → 应用启动                             │
│                                                      │
└──────────────────────────────────────────────────────┘
```

### flyway_schema_history 表

Flyway 在数据库中自动创建一张"账本"，记录哪些迁移已经执行过：

```sql
-- Flyway 自动创建和维护的表，你不需要手动操作
CREATE TABLE flyway_schema_history (
    installed_rank INT,           -- 执行顺序（1, 2, 3...）
    version        VARCHAR(50),   -- 迁移版本号（1, 1.1, 2...）
    description    VARCHAR(200),  -- 迁移描述（create_user_table）
    type           VARCHAR(20),   -- 类型：SQL 或 JAVA
    script         VARCHAR(1000), -- 文件名
    checksum       INT,           -- 文件内容的哈希值
    installed_by   VARCHAR(100),  -- 谁执行的
    installed_on   TIMESTAMP,     -- 执行时间
    execution_time INT,           -- 耗时（毫秒）
    success        BOOLEAN        -- 是否成功
);
```

这张表就是 Flyway 的"Git log"——任何时候你都能知道数据库当前处于哪个版本、谁在什么时候做了什么。

---

## 3. 快速入门：三步让 Flyway 跑起来

### 3.1 添加依赖

从 Flyway 10.0 开始，数据库特定支持被拆分为独立模块。使用 MySQL 需要同时引入 `flyway-core` 和 `flyway-mysql`：

```xml
<!-- pom.xml -->
<!-- Flyway 核心 -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
    <!-- 版本由 Spring Boot Parent POM 统一管理，无需手动指定 -->
</dependency>

<!-- MySQL 数据库支持（Flyway 10+ 必须引入） -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-mysql</artifactId>
</dependency>

<!-- 数据库驱动 -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

> **注意：** 如果只引入 `flyway-core` 而不引入 `flyway-mysql`，启动时会报 `No database found to handle jdbc:mysql://...` 错误。
> 其他数据库同理，如 PostgreSQL 需要 `flyway-database-postgresql`，SQL Server 需要 `flyway-sqlserver`。

数据源配置（以 MySQL 为例）：

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ecommerce?useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: ${DB_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

> **注意：** Flyway 依赖 `DataSource`，只要配好了数据源，Flyway 就能自动生效。

### 3.2 创建第一个迁移文件

在 `src/main/resources/db/migration/` 目录下创建迁移文件：

```
src/main/resources/
└── db/
    └── migration/
        ├── V1__create_user_table.sql
        ├── V2__create_product_table.sql
        └── V3__add_email_to_user.sql
```

**V1\_\_create_user_table.sql** —— 第一个迁移：

```sql
-- V1：创建用户表
CREATE TABLE `user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `username`    VARCHAR(50)  NOT NULL                COMMENT '用户名',
    `password`    VARCHAR(255) NOT NULL                COMMENT '密码',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';
```

**V2\_\_create_product_table.sql** —— 第二个迁移：

```sql
-- V2：创建商品表
CREATE TABLE `product` (
    `id`          BIGINT         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`        VARCHAR(200)   NOT NULL                COMMENT '商品名称',
    `price`       DECIMAL(10,2)  NOT NULL                COMMENT '价格',
    `stock`       INT            NOT NULL DEFAULT 0      COMMENT '库存',
    `created_at`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';
```

**V3\_\_add_email_to_user.sql** —— 给已有表加字段：

```sql
-- V3：给用户表添加邮箱字段
ALTER TABLE `user` ADD COLUMN `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱';
```

### 3.3 启动项目，见证自动执行

启动 Spring Boot 应用，日志会显示 Flyway 的执行过程：

```
Flyway Community Edition 10.x.x by Redgate
Database: jdbc:mysql://localhost:3306/ecommerce (MySQL 8.0)
Successfully validated 3 migrations (execution time 00:00.015s)
Creating Schema History table `ecommerce`.`flyway_schema_history` ...
Current version of schema `ecommerce`: << Empty Schema >>
Migrating schema `ecommerce` to version "1 - create user table"
Migrating schema `ecommerce` to version "2 - create product table"
Migrating schema `ecommerce` to version "3 - add email to user"
Successfully applied 3 migrations to schema `ecommerce` (execution time 00:00.120s)
```

此时查看数据库：

```sql
SELECT version, description, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

结果：

```
version | description            | installed_on        | success
────────┼────────────────────────┼─────────────────────┼────────
1       | create user table      | 2026-08-04 10:00:01 | 1
2       | create product table   | 2026-08-04 10:00:01 | 1
3       | add email to user      | 2026-08-04 10:00:01 | 1
```

再次启动应用，Flyway 发现没有新的迁移文件，直接跳过：

```
Current version of schema `ecommerce`: 3
Schema `ecommerce` is up to date. No migration necessary.
```

---

## 4. 迁移命名规范 —— Flyway 的"协议"

Flyway 通过**文件名**来识别迁移的类型、版本和顺序。命名必须严格遵守规范。

### 4.1 版本化迁移（V）

```
格式：V{版本号}__{描述}.sql
       ↑        ↑↑
       大写V    两个下划线
```

规则：

- 版本号唯一，一旦执行就不能再修改文件内容
- Flyway 按版本号**数字排序**从小到大执行
- 同一个版本号只能出现一次
- 适用于**表结构的增量变更**

```
V1__create_user_table.sql        ✅ 正确
V1.1__add_email_column.sql       ✅ 正确（版本号可以是小数）
V20260804__create_order.sql      ✅ 正确（用日期做版本号）
v1__init.sql                     ❌ 错误（小写 v）
V1_create_user.sql               ❌ 错误（只有一个下划线）
V1__init.sql + V1__fix.sql       ❌ 错误（版本号重复）
```

### 4.2 可重复迁移（R）

```
格式：R__{描述}.sql
       ↑↑
       大写R，两个下划线，没有版本号
```

规则：

- 每次文件内容变化，Flyway 都会重新执行
- 没有版本号，每次执行后更新 checksum
- 适用于**需要频繁修改**的数据库对象

```sql
-- R__create_product_view.sql
-- 每次修改这个视图定义，Flyway 都会重新执行
CREATE OR REPLACE VIEW v_product_summary AS
SELECT
    p.id,
    p.name,
    p.price,
    p.stock,
    CASE WHEN p.stock > 0 THEN '有货' ELSE '售罄' END AS status
FROM product p;
```

> **一句话：** V 迁移像 `git commit`——记录一次不可变的历史；R 迁移像"总是保持最新"——每次改了就重新执行。

### 4.3 撤销迁移（U）

```
格式：U{版本号}__{描述}.sql
       ↑
       大写U，与对应的 V 迁移版本号一致
```

撤销迁移是 V 迁移的"反向操作"，需要 Flyway Teams 版才能使用：

```sql
-- V3__add_email_to_user.sql（正向）
ALTER TABLE `user` ADD COLUMN `email` VARCHAR(100);

-- U3__remove_email_from_user.sql（反向）
ALTER TABLE `user` DROP COLUMN `email`;
```

> **社区版替代方案：** 如果没有 Teams 版，采用"前滚"策略——出问题不撤销，而是写一个新的 V 迁移来修复。

### 4.4 版本号策略

两种主流策略，各有优劣：

```
策略              示例                    优点                    缺点
══════════════════════════════════════════════════════════════════════════
递增整数          V1, V2, V3...V47       简洁直观                多人同时开发易冲突
时间戳            V20260804.1            几乎不会冲突             版本号长，不直观
                                          合并分支无压力
```

推荐：**个人项目/小团队用递增整数，多人协作/多分支用时间戳。**

---

## 5. 深入 flyway_schema_history —— 状态机的秘密

### 这张表决定了 Flyway 的所有行为

```
┌─────────────────────────────────────────────────────────────────┐
│              flyway_schema_history 是 Flyway 的大脑              │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Flyway 启动后问自己三个问题，全通过查这张表来回答：               │
│                                                                 │
│  Q1: 这个迁移文件执行过吗？                                      │
│      → SELECT * FROM flyway_schema_history WHERE version = '3'  │
│                                                                 │
│  Q2: 上次执行后，文件内容被改过吗？                               │
│      → 对比文件的 checksum 和表中记录的 checksum                 │
│                                                                 │
│  Q3: 上次执行成功了吗？                                          │
│      → 检查 success 字段                                        │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### checksum 的陷阱

checksum 是 Flyway 最重要的保护机制——它**不是版本号，而是文件内容的哈希**：

```
场景：你已经执行了 V3__add_email.sql，Flyway 记录下 checksum = 12345

后来你不小心改了 V3__add_email.sql 的内容：
  → 新的 checksum = 67890
  → Flyway 发现 12345 ≠ 67890
  → 抛出异常：Migration checksum mismatch
  → 应用启动失败！

为什么？因为 Flyway 在保护你——如果允许"偷偷修改"已执行的迁移，
  测试环境的数据库结构和生产环境就会不一致。这是最危险的数据库问题。
```

**正确做法：** 永远不要修改已提交的迁移文件。要改结构，写一个新的 V 迁移。

---

## 6. Spring Boot 配置全解

### 6.1 基础配置

```yaml
spring:
  flyway:
    # 迁移文件位置（默认值，通常不需要改）
    locations: classpath:db/migration

    # flyway_schema_history 表的名称（默认值）
    table: flyway_schema_history

    # SQL 文件编码
    encoding: UTF-8

    # 是否启用 Flyway（默认 true）
    enabled: true

    # 迁移执行前先校验（默认 true，生产环境强烈建议开启）
    validate-on-migrate: true

    # 发现待执行的迁移，但数据库非空时，是否自动创建 baseline
    # 默认 false（见第 8 节 Baseline 详解）
    baseline-on-migrate: false

    # 迁移失败后，是否允许继续启动（默认 false，强烈不建议改）
    fail-on-missing-locations: true

    # SQL 语句分隔符
    sql-migration-separator: ;

    # 是否将所有迁移语句放在同一个事务中（默认 false）
    # 注意：MySQL 的 DDL 语句会自动提交事务，此配置对 MySQL 的 DDL 无效
    group: false

    # 忽略未来版本（团队协作时可能用到）
    ignore-migration-patterns: "*:ignored"
```

### 6.2 环境隔离策略

不同环境，Flyway 的行为应该不同：

```yaml
# application-dev.yml —— 开发环境：自动迁移
spring:
  flyway:
    enabled: true
    clean-disabled: false        # 允许 flyway:clean（清空数据库，仅开发用！）

# application-prod.yml —— 生产环境：手动审批
spring:
  flyway:
    enabled: true
    clean-disabled: true         # 严禁清空数据库
```

生产环境的典型工作流：

```
┌─────────────────────────────────────────────────────────┐
│                  生产环境迁移流程                         │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  1. 开发写好 V{n}__xxx.sql，提交到代码仓库                │
│                    ↓                                    │
│  2. 测试环境自动执行，验证迁移正确                         │
│                    ↓                                    │
│  3. DBA / 运维 review 迁移 SQL                           │
│                    ↓                                    │
│  4. 生产部署时，Flyway 自动执行（或手动执行）              │
│                    ↓                                    │
│  5. 验证 flyway_schema_history 一切正常                  │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## 7. Java 迁移 —— 当 SQL 不够用时

大部分迁移用 SQL 就能搞定，但有时需要**代码逻辑**：

- 数据清洗：从旧表读取数据，转换后写入新表
- 批量更新：需要逐行计算，SQL 写起来太复杂
- 外部 API 调用：迁移过程中需要调用其他服务

```java
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/**
 * V4：将用户密码从明文迁移为 BCrypt 加密
 *
 * Java 迁移的类名必须遵循 V{版本号}__{描述} 的命名规则，
 * Flyway 通过类名识别版本号和执行顺序
 */
public class V4__encrypt_passwords extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        // 查询所有用户
        ResultSet users = context.getConnection()
                .createStatement()
                .executeQuery("SELECT id, password FROM user");

        PreparedStatement update = context.getConnection()
                .prepareStatement("UPDATE user SET password = ? WHERE id = ?");

        while (users.next()) {
            Long id = users.getLong("id");
            String plainPassword = users.getString("password");

            // 用 BCrypt 加密明文密码（BCrypt 需要引入 spring-security-crypto）
            String encrypted = new org.springframework.security.crypto
                    .bcrypt.BCryptPasswordEncoder()
                    .encode(plainPassword);

            update.setString(1, encrypted);
            update.setLong(2, id);
            update.executeUpdate();
        }
    }
}
```

Java 迁移文件放在 `src/main/java/db/migration/` 下（默认包路径为 `db.migration`）：

```
src/main/java/
└── db/
    └── migration/
        └── V4__encrypt_passwords.java
```

> **注意：** Java 迁移类必须在 Flyway 能扫描到的包路径下。默认扫描 `db.migration` 包，可通过 `locations` 配置修改。

> **原则：** 能用 SQL 解决的，就用 SQL 迁移。SQL 迁移更简单、更可审计、更容易 review。只有确实需要程序逻辑时，才用 Java 迁移。

---

## 8. Baseline —— 给已有数据库"补票"

### 场景

```
你的电商系统已经上线跑了半年，数据库里有几百张表。
现在你想引入 Flyway，但 Flyway 要求从"空数据库"开始。

怎么办？Baseline 就是这个场景的解决方案。
```

### 工作原理

```
┌──────────────────────────────────────────────────────┐
│                   Baseline 工作原理                    │
├──────────────────────────────────────────────────────┤
│                                                      │
│  设置 baseline-version = 1.0                          │
│  设置 baseline-on-migrate = true                      │
│                                                      │
│  Flyway 启动时：                                      │
│    1. 发现数据库非空，且没有 flyway_schema_history 表    │
│    2. 创建 flyway_schema_history 表                   │
│    3. 插入一条 baseline 记录：                          │
│       version = 1.0, type = BASELINE                  │
│    4. 只执行版本号 > 1.0 的迁移                        │
│                                                      │
│  V0.5__xxx.sql  → 跳过（版本号 ≤ 1.0）                │
│  V1.0__xxx.sql  → 跳过（等于 baseline）               │
│  V1.1__xxx.sql  → 执行 ✅                             │
│  V2__xxx.sql    → 执行 ✅                             │
│                                                      │
└──────────────────────────────────────────────────────┘
```

### 配置

```yaml
spring:
  flyway:
    baseline-on-migrate: true # 数据库非空时自动创建 baseline
    baseline-version: 1.0 # 以此为起点，只执行更高版本的迁移
    baseline-description: "现有数据库结构" # 记录在 history 表中的描述
```

假设数据库已有一张 `user` 表，迁移文件如下：

```
V1__create_user_table.sql    → CREATE TABLE user (...)
V2__create_product_table.sql → CREATE TABLE product (...)
```

配置 `baseline-version: 1.0` 后，Flyway 的执行过程：

```
1. 创建 flyway_schema_history 表
2. 插入 baseline 记录：version = 1.0, type = BASELINE
3. V1（版本号 1 ≤ baseline 1.0）→ 跳过，不执行 CREATE TABLE user ✅
4. V2（版本号 2 > baseline 1.0）→ 执行 CREATE TABLE product ✅
```

`flyway_schema_history` 最终状态：

```
installed_rank | version | type     | description           | success
───────────────┼─────────┼──────────┼───────────────────────┼────────
1              | 1.0     | BASELINE | 现有数据库结构          | 1
2              | 2       | SQL      | create product table   | 1
```

> **关键点：** V1 的 `CREATE TABLE user` 根本没有执行，所以不会报"表已存在"的错误。Baseline 本质上就是你对 Flyway 说：**"数据库里已有的东西，等价于我执行到版本 1.0 的状态，你从这之后开始管。"**

---

## 9. 修复与排错

### 错误 1：checksum mismatch

```
错误信息：
Migration checksum mismatch for migration version 3
→ Expected: 12345
→ Actual:   67890
```

**原因：** 你已经执行过 V3，但后来修改了 V3 的文件内容。Flyway 检测到 checksum 不匹配，拒绝启动。

**修复方案：**

```
  1. 用 git checkout 恢复 V3 的原始内容
  2. 新建 V4__xxx.sql，把你想做的额外修改写在 V4 里
  3. 正常启动，Flyway 执行 V4
```

### 错误 2：迁移执行失败

```
错误信息：
Migration V4__add_status_column.sql failed
→ SQL State  : 42S21
→ Error Code : 1060
→ Message    : Duplicate column name 'status'
```

**修复步骤：**

```
1. 不要慌，Flyway 在 flyway_schema_history 中记录了 success = false
2. 修复 SQL 脚本中的错误
3. 手动删除 flyway_schema_history 中失败的记录：
   DELETE FROM flyway_schema_history WHERE success = 0;
4. 重新启动应用
5. Flyway 会重新执行这个迁移
```

### 错误 3：找不到迁移文件

```
错误信息：
Cannot find migration: V3__add_email.sql
```

**原因：** 数据库中记录了 V3 已执行，但文件系统中找不到对应的 SQL 文件。

**修复：**

- 如果文件被误删：从 Git 历史恢复
- 如果文件改名了：改名不行，Flyway 靠文件名匹配。需要手动更新 history 表

### 错误 4：版本号重复

```
错误信息：
Found more than one migration with version 4
→ V4__create_order.sql
→ V4__fix_user.sql
```

**原因：** 两个迁移文件使用了相同的版本号。常见于多人协作时，不同开发者在同一分支都用了 V4。

**修复：**

```
1. 确认哪个 V4 是“正确的”（或两个都需要保留）
2. 将其中一个重命名为更高的版本号，如 V5__fix_user.sql
3. 如果该版本号已经执行过，需要删除 history 表中的旧记录：
   DELETE FROM flyway_schema_history WHERE version = '4';
4. 重新启动应用
```

> **预防：** 多人协作时，使用**时间戳版本号**（如 V20260804.1、V20260804.2）可以从根本上避免冲突。详见第 4.4 节。

### 错误 5：数据库非空但未启用 Baseline

```
错误信息：
Found non-empty schema(s) but no schema history table!
Set baseline-on-migrate or run flyway baseline command.
```

**原因：** 数据库已有表结构，但没有 `flyway_schema_history` 表，且未开启 `baseline-on-migrate`。Flyway 无法判断哪些结构是“已有的”、哪些迁移是“已执行的”，为保护数据安全，拒绝启动。

**修复：**

```yaml
spring:
  flyway:
    baseline-on-migrate: true # 允许 Flyway 在非空库上创建 baseline
    baseline-version: 1.0 # 设为当前数据库结构对应的最高迁移版本号
```

> **注意：** `baseline-version` 必须 ≥ 所有已存在数据库结构对应的最高迁移版本号，否则 Flyway 会尝试重复执行已有的迁移，导致“表已存在”等报错。详见第 8 节。

---

## 10. CI/CD 集成 —— 让迁移自动化

在实际项目中，Flyway 迁移应该集成到 CI/CD 流程中，而不是手动执行。以下是几种常见的集成方式。

### 10.1 应用启动时自动迁移（默认）

Spring Boot 集成 Flyway 后，**应用启动时会自动执行未运行的迁移**。这是最简单的方式，适合大多数场景：

```
部署流程：
  1. 代码合并到 main 分支
  2. CI 构建镜像（包含新的迁移文件）
  3. 部署新镜像 → 应用启动 → Flyway 自动执行迁移
  4. 迁移成功 → 应用正常运行
     迁移失败 → 应用启动失败，不会带病上线
```

> **优点：** 零额外配置，迁移和应用部署绑定，不会出现“代码上了但迁移没跑”的情况。
> **风险：** 如果多个实例同时启动，可能并发执行迁移。可通过配置 `spring.flyway.enabled=false` 关闭非主节点的 Flyway，或使用数据库锁机制。

### 10.2 CI/CD 流水线中独立执行迁移

对于生产环境，更推荐将迁移作为**独立的流水线步骤**，在应用部署之前执行。这样可以做到：迁移失败时不部署应用，DBA（数据库管理员） 可以先 review 迁移 SQL。

**GitHub Actions 示例：**

```yaml
# .github/workflows/deploy.yml
name: Deploy

on:
  push:
    branches: [main]

jobs:
  migrate:
    name: Run Flyway Migration
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Run Flyway Migration
        run: |
          mvn flyway:migrate \
            -Dflyway.url=${{ secrets.DB_URL }} \
            -Dflyway.user=${{ secrets.DB_USER }} \
            -Dflyway.password=${{ secrets.DB_PASSWORD }}

  deploy:
    name: Deploy Application
    needs: migrate # 迁移成功后才部署
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to production
        run: echo "Deploying application..."
```

**GitLab CI 示例：**

```yaml
# .gitlab-ci.yml
flyway-migrate:
  stage: deploy
  image: maven:3.9-eclipse-temurin-17
  script:
    - mvn flyway:migrate
        -Dflyway.url=$DB_URL
        -Dflyway.user=$DB_USER
        -Dflyway.password=$DB_PASSWORD
  only:
    - main

 deploy-app:
  stage: deploy
  needs: [flyway-migrate]   # 迁移成功后才部署
  script:
    - echo "Deploying application..."
```

### 10.3 生产环境迁移的最佳实践

```
┌─────────────────────────────────────────────────────────────┐
│                  生产环境迁移流程                             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  1. 开发者写 V{n}__xxx.sql，提交到代码仓库                    │
│                    ↓                                        │
│  2. CI 流水线自动执行迁移（测试环境数据库）                     │
│     → 验证迁移 SQL 的正确性                                   │
│                    ↓                                        │
│  3. DBA / 运维 review 迁移 SQL                               │
│     → 检查是否有锁表风险、大表 DDL 等                          │
│                    ↓                                        │
│  4. 生产部署流水线：先执行迁移，再部署应用                      │
│     → 迁移失败则中止部署，不会带病上线                          │
│                    ↓                                        │
│  5. 验证 flyway_schema_history 一切正常                      │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**关键原则：**

- **迁移和应用部署分离** —— 迁移失败时不部署应用，避免“代码上了但数据库没准备好”
- **敏感信息用 Secrets 管理** —— 数据库密码永远不要写在流水线配置文件中
- **测试环境先跑一遍** —— 生产迁移前，确保在测试环境已验证通过
- **大表 DDL 要谨慎** —— MySQL 对大表的 `ALTER TABLE` 可能锁表，建议在低峰期执行，或使用 `pt-online-schema-change` 等工具

---

## 11. 速查清单

### 11.1 命名规范速查

```
V{版本号}__{描述}.sql      版本化迁移，执行一次，不可修改
R__{描述}.sql              可重复迁移，内容变化时重新执行
U{版本号}__{描述}.sql      撤销迁移，Teams 版可用
```

### 11.2 配置速查

```yaml
spring:
  flyway:
    enabled: true # 是否启用
    locations: classpath:db/migration # 迁移文件路径
    table: flyway_schema_history # 历史表名
    baseline-on-migrate: false # 非空库自动 baseline
    baseline-version: 1.0 # baseline 版本号（与第 8 节一致）
    validate-on-migrate: true # 执行前校验
    clean-disabled: true # 禁用 clean（生产必须）
    encoding: UTF-8 # 文件编码
    out-of-order: false # 是否允许乱序执行
```

### 11.3 数据库状态判断

```
flyway_schema_history 不存在 → 空数据库，从头执行所有迁移
flyway_schema_history 存在，但版本 < 最新迁移文件 → 有待执行的迁移
flyway_schema_history 存在，版本 = 最新迁移文件 → 数据库已是最新
flyway_schema_history 中存在 success = false → 有失败的迁移，需要修复
checksum 不匹配 → 已执行的迁移文件被修改了，需要检查
```

### 11.4 常见陷阱

```
陷阱                                    现象                              解决方案
══════════════════════════════════════════════════════════════════════════════════
修改已执行的迁移文件                      checksum mismatch 报错             写新迁移，不要改旧的
版本号重复                                Found more than one migration      确保版本号唯一
只有一个下划线（V1_desc.sql）             迁移被忽略，不执行                  使用双下划线 V1__desc.sql
在事务中混合 DDL 和 DML（MySQL）         迁移执行失败                        MySQL 的 DDL 会自动提交
                                                                             事务，注意执行顺序
生产环境未禁用 clean                     误操作清空整个数据库                 设置 clean-disabled: true
多分支同时开发，版本号冲突                 合并时迁移顺序混乱                   使用时间戳版本号
```

### 11.5 Flyway vs Liquibase

```
                 Flyway                          Liquibase
═════════════════════════════════════════════════════════════
迁移格式          纯 SQL                           XML / YAML / JSON / SQL
学习成本          低（会写 SQL 就会用）             较高（需要学 DSL 语法）
回滚支持          Teams 版（付费）                  内置支持（免费）
数据库无关性      弱（SQL 语法随数据库变化）          强（DSL 自动转换方言）
社区活跃度        更活跃                           活跃
适用场景          团队都用同一种数据库                需要支持多种数据库
                 追求简单和 SQL 可控性              需要免费回滚功能
```

### 11.6 选择指南

```
小团队、单一数据库、追求简单           → Flyway 社区版
需要免费回滚、多数据库支持              → Liquibase
需要回滚 + 简单                        → Flyway Teams 版（付费）
```

---

**最后：** Flyway 的本质是**让你的数据库和代码一样，拥有版本历史**。代码有 Git，数据库有 Flyway。每次写 SQL 变更时，问自己一个问题：**"这个迁移，从空数据库开始能跑通吗？"** 如果能，Flyway 就帮你搞定剩下的一切。
