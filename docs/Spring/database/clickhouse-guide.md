# ClickHouse 入门指南

> 读者画像：熟悉 MySQL，首次接触 OLAP / 列式存储
> 学习目标：理解 ClickHouse 的核心思想、掌握基本使用、能在 Spring Boot 中接入
> 适用版本：ClickHouse 25.x
> 范围外：大数据生态集成（Kafka/Flink）、集群运维调优

## 范围

本指南从 MySQL 用户的视角出发，循序渐进地建立对 ClickHouse 的完整认知。前六章讲 ClickHouse 本身（列式存储、表引擎、数据类型、数据操作、物化视图、字典），第七、八章讲 Java 集成和分布式。每个新概念都用 MySQL 作为对照锚点——你已经知道 X，ClickHouse 里是 Y。

---

## 目录

1. [ClickHouse 是什么](#1-clickhouse-是什么)
2. [安装与基本使用](#2-安装与基本使用)
3. [表引擎](#3-表引擎)
4. [数据类型](#4-数据类型)
5. [数据操作](#5-数据操作)
6. [物化视图](#6-物化视图)
7. [字典](#7-字典)
8. [Spring Boot 集成](#8-spring-boot-集成)
9. [分布式简介](#9-分布式简介)
10. [最佳实践与速查清单](#10-最佳实践与速查清单)

---

## 1. ClickHouse 是什么

### 1.1 OLTP vs OLAP：两种不同的数据库需求

在 MySQL 的世界里，数据库主要干一件事：处理业务事务——用户下单、扣库存、写日志。这类场景叫 **OLTP**（Online Transaction Processing，在线事务处理），特点是每次操作涉及少量数据（一行或几行），但并发高、要求快速响应。

但还有一类完全不同的需求：

```
OLTP（MySQL 擅长的）                 OLAP（ClickHouse 擅长的）
─────────────────────────           ─────────────────────────
一次操作 1~10 行                     一次查询扫描百万~数十亿行
写入频繁，读少                       写入少（批量），读多
响应时间 < 10ms                     响应时间 100ms ~ 几秒
事务支持（ACID）                     无事务（不需要）
数据按行存储                        数据按列存储
```

**典型 OLAP 场景**：

- "最近 30 天，每个商品的日均销量是多少？" → 扫描千万行订单，分组聚合
- "过去一年的用户留存率趋势？" → 扫描上亿行用户行为日志
- "按地区统计每小时的 API 调用量？" → 扫描数十亿行日志

这些查询在 MySQL 上跑可能要几十秒甚至几分钟，但在 ClickHouse 上通常只需几百毫秒。

### 1.2 列式存储：ClickHouse 快的根本原因

MySQL 用 InnoDB 引擎，数据按**行**存储——一行的所有字段连续存放在磁盘上：

```
行式存储（MySQL / InnoDB）
──────────────────────────────────────────────────────────
磁盘页 1:  [id=1, name=可乐, category=饮料, price=3.0, stock=100]
磁盘页 2:  [id=2, name=雪碧, category=饮料, price=3.5, stock=200]
磁盘页 3:  [id=3, name=手机, category=电子, price=999, stock=50]
...
```

ClickHouse 按**列**存储——同一列的所有值连续存放：

```
列式存储（ClickHouse）
──────────────────────────────────────────────────────────
id 列:       [1, 2, 3, 4, 5, ...]         ← 连续存放
name 列:     [可乐, 雪碧, 手机, ...]        ← 连续存放
category 列: [饮料, 饮料, 电子, ...]        ← 连续存放
price 列:    [3.0, 3.5, 999, ...]          ← 连续存放
stock 列:    [100, 200, 50, ...]           ← 连续存放
```

**查询时的差异**——`SELECT avg(price) FROM products WHERE category = '饮料'`：

```
行式存储：必须读整行
  磁盘页 1 → 读整行 → 检查 category → 取 price ✓
  磁盘页 2 → 读整行 → 检查 category → 取 price ✓
  磁盘页 3 → 读整行 → 检查 category → 取 price ✗（不是饮料）
  ...
  浪费：读了 name、stock 等不需要的字段

列式存储：只读需要的列
  category 列 → 只读这一列做过滤 ✓
  price 列    → 只读这一列算均值 ✓
  name 列     → 不读
  stock 列    → 不读
  ...
  节省：IO 量减少 80%+
```

**为什么列式存储对聚合计算特别快？**

1. **IO 少**：只读查询涉及的列，不读整行
2. **CPU 缓存友好**：同类型数据连续存放，CPU 预取命中率高
3. **压缩率高**：同一列数据类型相同、值域相近，压缩比可达 10:1 以上

### 1.3 什么时候该用 ClickHouse

```
适合 ClickHouse 的场景                    不适合的场景
────────────────────────                  ────────────────────────
日志分析（访问日志、操作日志）              频繁单行更新/删除（没有 UPDATE/DELETE）
实时报表 / 数据大屏                        高并发点查（按 id 查单条记录）
用户行为分析（PV/UV/留存）                 事务场景（没有 ACID）
时序数据（监控指标、IoT）                   小数据量（< 100 万行用 MySQL 就够了）
宽表查询（几十上百个字段，只查几个）         Key-Value 存储（用 Redis）
```

> **一句话总结**：MySQL 负责"每次处理少量数据的快速事务"，ClickHouse 负责"每次扫描大量数据的快速分析"。两者互补，不是替代关系。

---

## 2. 安装与基本使用

### 2.1 Docker 一键部署

<!-- Illustrative fragment -->

```bash
# 启动 ClickHouse 服务端（含内置 Web 客户端）
docker run -d --name clickhouse \
  -p 8123:8123 \
  -p 9000:9000 \
  clickhouse/clickhouse-server:25.8

# 启动客户端连接
docker exec -it clickhouse clickhouse-client
```

端口说明：

| 端口 | 用途                                      |
| ---- | ----------------------------------------- |
| 8123 | HTTP 接口（JDBC 驱动用这个）              |
| 9000 | 原生 TCP 接口（clickhouse-client 用这个） |

### 2.2 clickhouse-client 常用命令

进入客户端后，操作方式和 MySQL 客户端很像：

```sql
-- 查看所有数据库（类比 MySQL 的 SHOW DATABASES）
SHOW DATABASES;

-- 切换数据库
USE default;

-- 查看当前数据库的所有表
SHOW TABLES;

-- 查看表结构
DESCRIBE TABLE events;

-- 退出
EXIT;
```

### 2.3 与 MySQL 客户端的体验差异

```
MySQL 客户端                        ClickHouse 客户端
──────────                          ────────────────
每条语句必须以 ; 结尾               同样必须以 ; 结尾
SELECT 结果是表格                   SELECT 结果默认是 TSV 格式（制表符分隔）
                                    加 FORMAT Pretty 可得到表格格式
不支持 \G 查看                      没有等价命令
支持事务 BEGIN/COMMIT               不支持事务
UPDATE/DELETE 是标准操作             UPDATE/DELETE 有但很重（mutation），不推荐日常使用
```

> **提示**：日常开发中可以直接用 `clickhouse-client` 命令行交互，也可以用 Docker 自带的 Web 客户端（浏览器访问 `http://localhost:8123/play`）。

---

## 3. 表引擎

在 MySQL 中，建表就是 `CREATE TABLE ... ENGINE=InnoDB`，引擎几乎不用选。但在 ClickHouse 中，**表引擎是核心概念**——不同引擎决定了数据如何存储、如何合并、如何查询。

### 3.1 MergeTree：基础款（类比 InnoDB）

MergeTree 是 ClickHouse 最重要的引擎，90% 的场景用它就够了。

```sql
-- Illustrative fragment
CREATE TABLE events (
    event_date  Date,           -- 日期类型
    event_type  String,         -- 事件类型
    user_id     UInt64,         -- 用户 ID
    page_url    String          -- 页面 URL
)
ENGINE = MergeTree()            -- 表引擎
PARTITION BY toYYYYMM(event_date)  -- 按月分区
ORDER BY (event_type, user_id)     -- 排序键（类似 MySQL 的聚簇索引）
TTL event_date + INTERVAL 6 MONTH  -- 数据保留 6 个月，过期自动删除
SETTINGS index_granularity = 8192; -- 稀疏索引粒度（默认 8192，一般不改）
```

**与 MySQL 的关键区别**：

```
MySQL                                    ClickHouse MergeTree
──────                                   ────────────────────
PRIMARY KEY = 唯一约束 + 聚簇索引          ORDER BY = 排序键（不要求唯一！）
一行写入后立即持久化                       写入后先进内存，后台合并成数据文件
B-Tree 索引，精确定位                      稀疏索引（每 8192 行一个索引条目）
支持 UPDATE / DELETE                      不推荐 UPDATE / DELETE（代价很高）
```

> **⚠️ 重要区别**：ClickHouse 的 `ORDER BY`（排序键）决定了数据在磁盘上的物理排列顺序，也决定了查询时能高效过滤哪些字段。它**不是唯一约束**——相同排序键值的多行数据都会保留。

**稀疏索引**——ClickHouse 的索引方式和 MySQL 完全不同：

```
MySQL B-Tree 索引：每行都有索引条目
  id=1 → 页1    id=2 → 页1    id=3 → 页2    ...

ClickHouse 稀疏索引：每 8192 行一个索引条目（granule）
  granule 0: 行 1~8192    → [event_type='click', user_id 范围: 1~5000]
  granule 1: 行 8193~16384 → [event_type='click', user_id 范围: 5001~12000]
  granule 2: 行 16385~24576 → [event_type='view', user_id 范围: 1~3000]
  ...

查询 WHERE event_type = 'click' AND user_id = 100：
  → 先看索引：granule 0 和 1 包含 click 类型
  → 跳过 granule 2（view 类型，不匹配）
  → 在 granule 0、1 内逐行扫描

索引很小（全部放内存），但定位精度是"8192 行一组"而非"单行"
```

### 3.2 ReplacingMergeTree：自动去重

**场景**：数据从多个来源写入，同一个 user_id 可能有多条记录，只保留最新版本。

在 MySQL 中你会用 `INSERT ... ON DUPLICATE KEY UPDATE`。ClickHouse 没有这种机制，但 ReplacingMergeTree 在后台合并时**自动保留排序键相同的多行中的最后一条**：

```sql
-- Illustrative fragment
CREATE TABLE user_profiles (
    user_id     UInt64,
    name        String,
    updated_at  DateTime
)
ENGINE = ReplacingMergeTree(updated_at)  -- 指定版本列（可选）
ORDER BY (user_id);
```

```
写入 3 条数据：
  (1, '张三', '2026-01-01 10:00')
  (1, '张三丰', '2026-01-02 10:00')   ← 同一个 user_id，更新的版本
  (2, '李四', '2026-01-01 10:00')

查询 SELECT * FROM user_profiles：
  → 合并前可能看到 3 行（合并还没发生）
  → 合并后自动变成 2 行：
      (1, '张三丰', '2026-01-02 10:00')  ← 保留 updated_at 最大的
      (2, '李四', '2026-01-01 10:00')

⚠️ 合并是后台异步发生的，不是写入时立即去重。
   查询时加 FINAL 关键字可以强制合并后再返回：
   SELECT * FROM user_profiles FINAL
```

### 3.3 SummingMergeTree：自动聚合

**场景**：按维度聚合的增量数据。比如每天写入各商品的销售量，想自动累加。

```sql
-- Illustrative fragment
CREATE TABLE daily_sales (
    date        Date,
    product_id  UInt64,
    quantity    UInt64,
    revenue     Float64
)
ENGINE = SummingMergeTree((quantity, revenue))  -- 指定要累加的列
ORDER BY (date, product_id);
```

```
写入 2 批数据：
  第一批: (2026-01-01, 100, 5, 15.0)
  第二批: (2026-01-01, 100, 3, 9.0)   ← 同一天、同一商品

合并后自动变成：
  (2026-01-01, 100, 8, 24.0)   ← quantity 和 revenue 自动累加
```

### 3.4 其他引擎简介

| 引擎                   | 用途          | 何时使用                                     |
| ---------------------- | ------------- | -------------------------------------------- |
| `CollapsingMergeTree`  | 快速更新/删除 | 需要频繁修改或删除行时（通过 +1/-1 行抵消）  |
| `AggregatingMergeTree` | 预聚合        | 配合聚合函数使用，比 SummingMergeTree 更灵活 |
| `Memory`               | 内存表        | 测试、临时数据（重启丢失）                   |
| `Log`                  | 简单日志      | 小批量写入、不需要索引的场景                 |
| `Distributed`          | 分布式表      | 跨多个节点查询（见第九章）                   |

### 3.5 引擎选型速查

```
你的场景是什么？
│
├── 通用数据存储（日志、事件、指标）
│   └── MergeTree（90% 的情况）
│
├── 同一主键可能写入多次，只保留最新
│   └── ReplacingMergeTree
│
├── 增量数据需要自动累加
│   └── SummingMergeTree
│
├── 需要频繁更新/删除行
│   └── CollapsingMergeTree
│
└── 需要复杂的预聚合（avg、uniq 等）
    └── AggregatingMergeTree
```

---

## 4. 数据类型

### 4.1 基础类型对照

| ClickHouse               | MySQL                                   | 说明                                   |
| ------------------------ | --------------------------------------- | -------------------------------------- |
| `UInt8/16/32/64`         | `TINYINT/SMALLINT/INT/BIGINT`（无符号） | ClickHouse 区分有符号和无符号          |
| `Int8/16/32/64`          | `TINYINT/SMALLINT/INT/BIGINT`           | 有符号整数                             |
| `Float32/Float64`        | `FLOAT/DOUBLE`                          | 浮点数                                 |
| `Decimal32/64/128`       | `DECIMAL`                               | 精确小数                               |
| `String`                 | `VARCHAR/TEXT`                          | ClickHouse 的 String 没有长度限制      |
| `Date`                   | `DATE`                                  | 日期                                   |
| `DateTime`               | `DATETIME`                              | 日期时间                               |
| `DateTime64`             | 无对应                                  | 亚秒精度（毫秒/微秒/纳秒）             |
| `UUID`                   | `UUID`（MySQL 8.0+）                    | 唯一标识                               |
| `Enum8/Enum16`           | `ENUM`                                  | 枚举类型                               |
| `LowCardinality(String)` | 无对应                                  | 字典编码字符串，大幅节省存储和加速查询 |

### 4.2 特色类型

ClickHouse 有几个 MySQL 没有的类型，在分析场景中非常实用：

**Array（数组）**：

```sql
-- Illustrative fragment
CREATE TABLE articles (
    id      UInt64,
    title   String,
    tags    Array(String)     -- 一篇文章可以有多个标签
) ENGINE = MergeTree()
ORDER BY id;

INSERT INTO articles VALUES (1, 'ClickHouse入门', ['数据库', 'OLAP', '入门']);

-- 查询：包含某个标签的文章
SELECT * FROM articles WHERE has(tags, 'OLAP');
```

**Map（键值对）**：

```sql
-- Illustrative fragment
CREATE TABLE user_settings (
    user_id     UInt64,
    settings    Map(String, String)
) ENGINE = MergeTree()
ORDER BY user_id;

INSERT INTO user_settings VALUES (1, {'theme': 'dark', 'lang': 'zh'});

-- 查询：获取某个 key 的值
SELECT settings['theme'] FROM user_settings WHERE user_id = 1;
```

**Tuple（元组）**：

```sql
-- Illustrative fragment
-- 固定长度、每个元素可以是不同类型
SELECT (1, 'hello', 3.14) AS tuple_example;
-- 通过名字或下标访问：tuple_example.1, tuple_example.2
```

### 4.3 LowCardinality：分析场景的利器

```sql
-- Illustrative fragment
CREATE TABLE events (
    event_date  Date,
    event_type  LowCardinality(String),  -- 值种类少但出现次数多
    country     LowCardinality(String),
    user_id     UInt64
) ENGINE = MergeTree()
ORDER BY (event_type, user_id);
```

`LowCardinality` 的原理：把重复出现的字符串替换为整数编号（字典编码）。比如 `event_type` 只有 `click`、`view`、`buy` 三种值，但出现上亿次——用 `LowCardinality` 后，内部只存 0/1/2 三个数字，查询和压缩都更快。

> **使用原则**：值种类少于百万级别的字符串字段，都可以考虑加 `LowCardinality`。

### 4.4 Nullable 的代价

```sql
-- 可以这样写：
CREATE TABLE t (
    name Nullable(String)   -- 允许 NULL
) ENGINE = MergeTree() ORDER BY tuple();

-- ⚠️ 但不推荐！原因：
-- 1. Nullable 列的查询比普通列慢（需要额外处理 NULL 值）
-- 2. 不能作为 ORDER BY 的排序键
-- 3. 大多数聚合函数忽略 NULL（行为可能不符合预期）

-- 推荐做法：用默认值代替 NULL
CREATE TABLE t (
    name String DEFAULT 'unknown'   -- 用默认值代替 NULL
) ENGINE = MergeTree() ORDER BY tuple();
```

---

## 5. 数据操作

### 5.1 INSERT：必须批量

这是 MySQL 用户转到 ClickHouse 最容易踩的坑。

```sql
-- ❌ 错误：逐条 INSERT
INSERT INTO events VALUES ('2026-01-01', 'click', 1001);
INSERT INTO events VALUES ('2026-01-01', 'view', 1002);
INSERT INTO events VALUES ('2026-01-01', 'buy', 1003);
-- 每次 INSERT 都会触发一次磁盘写入，产生大量小数据文件
-- 后台合并压力巨大，性能极差

-- ✅ 正确：一次 INSERT 写入一批（每批 1000~10000 条）
INSERT INTO events VALUES
    ('2026-01-01', 'click', 1001),
    ('2026-01-01', 'view', 1002),
    ('2026-01-01', 'buy', 1003),
    ... -- 1000~10000 条
    ;
```

```
MySQL 的 INSERT                    ClickHouse 的 INSERT
───────────────                    ───────────────────
逐条 INSERT 性能可接受              逐条 INSERT 是反模式
批量 INSERT 更快                    批量 INSERT 是唯一正确方式
                                   推荐：每次 INSERT 至少 1000 行
```

### 5.2 SELECT：与 MySQL 的异同

ClickHouse 的 SELECT 语法和 MySQL 非常相似，大部分查询可以直接写：

```sql
-- 和 MySQL 一样的写法
SELECT event_type, count() AS cnt
FROM events
WHERE event_date >= '2026-01-01'
GROUP BY event_type
ORDER BY cnt DESC
LIMIT 10;

-- ClickHouse 特有的聚合函数
SELECT
    uniq(user_id) AS unique_users,       -- 去重计数（近似值，极快）
    uniqExact(user_id) AS exact_users,   -- 精确去重（较慢）
    quantile(0.5)(response_time) AS p50, -- 中位数
    quantile(0.99)(response_time) AS p99 -- P99
FROM events;

-- ClickHouse 特有的条件聚合
SELECT
    event_type,
    countIf(user_id > 0) AS valid_count,  -- 条件计数
    sumIf(amount, status = 'paid') AS paid_sum  -- 条件求和
FROM events
GROUP BY event_type;
```

> **⚠️ 注意**：`count()` 在 ClickHouse 中不需要 `count(*)` 或 `count(1)`，直接写 `count()` 即可。

### 5.3 分区键（PARTITION BY）

分区是 ClickHouse 组织数据的方式——把一张大表按某个字段拆分成多个物理分区，查询时自动跳过不相关的分区。

```sql
-- Illustrative fragment
CREATE TABLE events (
    event_date  Date,
    event_type  String,
    user_id     UInt64
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_date)   -- 按月分区
ORDER BY (event_type, user_id);
```

```
分区的效果：
──────────────────────────────────────────────────────

events 表（按月分区）
├── 202601/   ← 1 月的数据
├── 202602/   ← 2 月的数据
├── 202603/   ← 3 月的数据
...

查询 WHERE event_date >= '2026-03-01'
  → ClickHouse 只扫描 202603/ 及之后的分区
  → 202601/、202602/ 完全不读（分区裁剪）
```

**分区键选择原则**：

| 场景                    | 推荐分区方式                  | 说明                             |
| ----------------------- | ----------------------------- | -------------------------------- |
| 日志/事件数据           | `toYYYYMM(event_date)` 按月   | 最常见的选择                     |
| 数据量很大              | `toYYYYMMDD(event_date)` 按天 | 数据量大到月分区不够细           |
| 数据量小（< 1000 万行） | `tuple()` 不分区              | 分区本身有开销，数据量小时不需要 |

> **⚠️ 不要按高基数列分区**：比如 `PARTITION BY user_id` 是错误的——每个用户一个分区，分区数量爆炸。分区键应该是低基数的（日期、地区、类型等）。

### 5.4 排序键（ORDER BY / PRIMARY KEY）

排序键决定了数据在磁盘上的物理排列顺序，也决定了查询效率。

```sql
-- Illustrative fragment
-- 排序键 = (event_type, user_id)
-- 数据先按 event_type 排序，同类型内再按 user_id 排序
ORDER BY (event_type, user_id)
```

```
排序键的效果（类比电话簿）：
──────────────────────────────────────────────────────

数据按 (event_type, user_id) 排序后在磁盘上的排列：

  buy,    user_1001
  buy,    user_1002
  buy,    user_1005
  click,  user_1001    ← event_type 有序
  click,  user_1003
  click,  user_1008
  view,   user_1002
  view,   user_1004

查询 WHERE event_type = 'click'：
  → 利用排序，直接定位到 click 区间，跳过 buy 和 view

查询 WHERE user_id = 1003：
  → 无法高效过滤（user_id 不是排序键的第一列）
  → 需要扫描所有数据

查询 WHERE event_type = 'click' AND user_id = 1003：
  → 先定位 click 区间，再在区间内按 user_id 查找
  → 高效 ✓
```

**排序键选择原则**：

1. **查询条件中最常出现的字段放前面**（等值查询 > 范围查询）
2. **低基数字段优先**（如 event_type 只有几种值，放前面分区效果好）
3. **排序键总大小尽量小**（影响索引效率）

> **PRIMARY KEY 与 ORDER BY 的关系**：如果只写了 `ORDER BY`，`PRIMARY KEY` 默认等于 `ORDER BY`。在 ClickHouse 中 `PRIMARY KEY` **不是唯一约束**，它只是定义了索引粒度（和 `ORDER BY` 一样）。

### 5.5 采样（SAMPLE）

当表非常大时，可以用采样来快速获取近似结果：

```sql
-- Illustrative fragment
-- 前提：建表时 ORDER BY 中必须包含 user_id（采样键）
CREATE TABLE events (
    event_date Date,
    user_id    UInt64,
    ...
) ENGINE = MergeTree()
ORDER BY (event_type, user_id);  -- user_id 在排序键中

-- 采样查询：只扫描约 10% 的数据
SELECT user_id, count() * 10 AS approx_count
FROM events
SAMPLE 0.1    -- 采样 10%
GROUP BY user_id;
```

> **注意**：采样返回的是近似结果。结果乘以采样倍率（`* 10`）来估算总量。适合对精度要求不高但需要快速响应的场景。

---

## 6. 物化视图

物化视图是 ClickHouse 最强大的功能之一——**预聚合**。它的核心思想很简单：把聚合计算的结果提前算好、存下来，查询时直接读结果，不需要每次扫描原始数据重新计算。

### 6.1 核心概念

```
没有物化视图：
  SELECT category, count(), sum(amount) FROM orders GROUP BY category;
  → 每次查询都扫描 orders 全表 → 分组 → 聚合 → 返回
  → 数据量大时很慢（百万行要几秒，亿行要几十秒）

有了物化视图：
  聚合结果已经提前算好存在一张表里
  SELECT * FROM daily_category_stats;
  → 直接读取已经算好的结果 → 毫秒级返回
```

物化视图的本质是一张**存了聚合结果的表**，但它不需要你手动维护——每次往源表 INSERT 新数据时，物化视图**自动触发聚合**，把新数据的统计结果累加进去。

### 6.2 工作原理：数据如何流入物化视图

```
                        物化视图的工作流程
═══════════════════════════════════════════════════════════

                    ┌──────────────────────┐
                    │   INSERT INTO events  │
                    │   (1000 行新数据)      │
                    └──────────┬───────────┘
                               │
                    ┌──────────▼───────────┐
                    │     events 表         │
                    │   （原始数据写入）      │
                    └──────────┬───────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
   ┌──────────▼──────┐ ┌──────▼───────┐ ┌──────▼───────┐
   │ 物化视图 A       │ │ 物化视图 B    │ │ 物化视图 C    │
   │ 按天统计         │ │ 按类型统计    │ │ 按用户统计    │
   │ (自动触发)       │ │ (自动触发)    │ │ (自动触发)    │
   └─────────────────┘ └──────────────┘ └──────────────┘
              │                │                │
              ▼                ▼                ▼
         已算好的          已算好的          已算好的
         日统计结果         类型统计结果       用户统计结果

   查询时：直接读物化视图的结果（毫秒级）
   不需要：扫描原始 events 表重新计算
```

**关键点**：

1. 物化视图在 **INSERT 时自动触发**，不是定时任务
2. 新数据写入源表的同时，物化视图**同步更新**
3. 一个源表可以有**多个**物化视图（不同的聚合维度）
4. 物化视图只处理**新增数据**，不重新扫描历史

### 6.3 完整示例：电商订单分析

**第一步：创建源表**

```sql
-- Illustrative fragment
CREATE TABLE orders (
    order_date   Date,
    order_time   DateTime,
    product_id   UInt64,
    category     LowCardinality(String),
    quantity     UInt64,
    amount       Float64
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(order_date)
ORDER BY (category, product_id);
```

**第二步：创建物化视图（按天+分类统计）**

```sql
-- Illustrative fragment
CREATE TABLE daily_category_stats (
    stat_date    Date,
    category     LowCardinality(String),
    order_count  UInt64,
    total_qty    UInt64,
    total_amount Float64
)
ENGINE = SummingMergeTree()
ORDER BY (stat_date, category);

CREATE MATERIALIZED VIEW daily_category_stats_mv
TO daily_category_stats
AS SELECT
    order_date                    AS stat_date,
    category,
    count()                       AS order_count,
    sum(quantity)                 AS total_qty,
    sum(amount)                   AS total_amount
FROM orders
GROUP BY stat_date, category;
```

**第三步：写入数据后自动聚合**

```sql
-- Illustrative fragment
-- 写入原始订单数据
INSERT INTO orders VALUES
    ('2026-01-15', '2026-01-15 10:30:00', 1001, '饮料', 5, 15.0),
    ('2026-01-15', '2026-01-15 11:00:00', 2001, '零食', 3, 9.0),
    ('2026-01-15', '2026-01-15 14:20:00', 1002, '饮料', 2, 6.0);

-- 直接查物化视图（毫秒级返回）
SELECT * FROM daily_category_stats
WHERE stat_date = '2026-01-15';

-- 结果：
-- stat_date   | category | order_count | total_qty | total_amount
-- 2026-01-15  | 饮料     | 2           | 7         | 21.0
-- 2026-01-15  | 零食     | 1           | 3         | 9.0
```

### 6.4 物化视图的两种目标存储方式

```sql
-- 方式一：TO 指定目标表（推荐）
-- 先建好目标表，物化视图把结果写入该表
CREATE TABLE target_table (...) ENGINE = SummingMergeTree() ...;
CREATE MATERIALIZED VIEW mv_name TO target_table AS SELECT ...;

-- 方式二：不指定目标表（引擎内嵌）
-- 物化视图自己就是存储
CREATE MATERIALIZED VIEW mv_name
ENGINE = SummingMergeTree() ORDER BY (...)
AS SELECT ...;
```

> **推荐方式一**（`TO target_table`）：目标表可以独立管理（修改引擎、添加索引、查看数据），物化视图只负责"管道"角色，职责清晰。

### 6.5 使用场景选择

```
什么时候需要物化视图？
│
├── 查询需要扫描大量数据，但结果集很小
│   → 物化视图预聚合，查询从分钟级降到毫秒级
│
├── 同一个聚合查询被频繁执行
│   → 物化视图缓存结果，避免重复计算
│
├── 需要实时更新的报表/大屏
│   → INSERT 时自动更新物化视图，无需定时任务
│
└── 什么时候不需要？
    ├── 数据量小（< 100 万行），直接查就够了
    ├── 查询每次都不同（无法预定义聚合维度）
    └── 需要精确到秒的实时数据（物化视图有微小延迟）
```

### 6.6 物化视图 vs 直接查询的性能对比

```
场景：统计每天的订单量和总金额（源表 1 亿行）

直接查询：
  SELECT order_date, count(), sum(amount)
  FROM orders GROUP BY order_date;
  → 扫描 1 亿行 → 耗时 5~10 秒

物化视图：
  SELECT * FROM daily_category_stats ORDER BY stat_date;
  → 读取已聚合的结果（可能只有几百行） → 耗时 < 10ms

性能差距：1000 倍以上
```

---

## 7. 字典

字典（Dictionary）是 ClickHouse 的另一个高性能特性，用于**加速维度表的关联查询**。

### 7.1 什么是字典

假设你有两张表：`orders`（订单表，存了 product_id）和 `products`（商品维度表，存了 product_name、category）。在 MySQL 中，你需要 JOIN 才能查出订单对应的商品名：

```sql
-- MySQL：JOIN 维度表
SELECT o.order_id, p.product_name, p.category
FROM orders o
JOIN products p ON o.product_id = p.id;
```

ClickHouse 的字典把维度表（`products`）**加载到内存**中。查询时只需要读 `orders` 一张表，商品名和分类通过 `dictGetString` 函数从内存中的字典直接查找，不需要 JOIN：

```sql
-- ClickHouse：只查 orders 表，维度信息从字典取
-- dictGetString('字典名', '字段名', 用于查找的 key)
SELECT
    order_id,
    -- 用 product_id 去字典中查找对应的 product_name
    dictGetString('products_dict', 'product_name', product_id) AS product_name,
    -- 用 product_id 去字典中查找对应的 category
    dictGetString('products_dict', 'category', product_id) AS category
FROM orders;
-- 效果等价于 MySQL 的 JOIN，但不需要 JOIN，速度更快
```

### 7.2 创建字典

```sql
-- Illustrative fragment
CREATE DICTIONARY products_dict (
    product_id   UInt64,
    product_name String,
    category     String
)
PRIMARY KEY product_id
SOURCE(CLICKHOUSE(
    HOST 'localhost'
    PORT 9000
    DB 'default'
    TABLE 'products'
))
LAYOUT(HASHED())                    -- 哈希布局（全量加载到内存）
MIN_LIFETIME MINUTES 10             -- 最小刷新间隔
MAX_LIFETIME MINUTES 60             -- 最大刷新间隔
;
```

### 7.3 字典 vs JOIN

| 对比维度   | JOIN           | 字典（Dictionary）       |
| ---------- | -------------- | ------------------------ |
| 查询速度   | 需要关联计算   | 内存直接查找，极快       |
| 适用数据量 | 任意大小       | 维度表不太大（能放内存） |
| 实时性     | 实时           | 有刷新间隔（分钟级）     |
| 语法复杂度 | 标准 JOIN 语法 | dictGetXxx 函数          |

> **使用原则**：维度表数据量不大（< 百万行）、更新不频繁的场景，优先用字典代替 JOIN。

---

## 8. Spring Boot 集成

ClickHouse 没有 Spring Data 的官方 Starter，Java 生态主要靠 JDBC 驱动。集成方式比 Redis/MongoDB 简单得多——本质上就是"配一个数据源 + 用 JdbcTemplate"。

### 8.1 Maven 依赖

```xml
<!-- ClickHouse JDBC 驱动 -->
<dependency>
    <groupId>com.clickhouse</groupId>
    <artifactId>clickhouse-jdbc</artifactId>
    <version>0.7.0</version>
    <classifier>all</classifier>
</dependency>

<!-- 连接池（推荐） -->
<dependency>
    <groupId>com.zaxxer</groupId>
    <artifactId>HikariCP</artifactId>
</dependency>
```

> **注意**：`classifier` 必须是 `all`，否则缺少依赖会报 ClassNotFoundException。

### 8.2 YAML 配置

```yaml
spring:
  datasource:
    url: jdbc:clickhouse://localhost:8123/default
    driver-class-name: com.clickhouse.jdbc.ClickHouseDriver
    username: default
    password:
    hikari:
      maximum-pool-size: 8
      minimum-idle: 2
      connection-timeout: 5000
```

> **端口注意**：JDBC 用 **8123**（HTTP 端口），不是 9000（TCP 端口）。

### 8.3 JdbcTemplate 基本查询

<!-- Illustrative fragment -->

```java
@Service
@RequiredArgsConstructor
public class EventService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 查询事件统计
     */
    public List<Map<String, Object>> getEventStats() {
        String sql = """
            SELECT event_type, count() AS cnt
            FROM events
            WHERE event_date >= today() - INTERVAL 7 DAY
            GROUP BY event_type
            ORDER BY cnt DESC
            """;
        return jdbcTemplate.queryForList(sql);
    }

    /**
     * 查询物化视图
     */
    public List<Map<String, Object>> getDailyStats(String startDate, String endDate) {
        String sql = """
            SELECT stat_date, category, order_count, total_amount
            FROM daily_category_stats
            WHERE stat_date BETWEEN ? AND ?
            ORDER BY stat_date, category
            """;
        return jdbcTemplate.queryForList(sql, startDate, endDate);
    }
}
```

### 8.4 批量写入的正确姿势

<!-- Illustrative fragment -->

```java
@Service
@RequiredArgsConstructor
public class EventWriter {

    private final JdbcTemplate jdbcTemplate;

    /**
     * ❌ 错误：逐条写入（性能极差）
     */
    public void insertOneByOne(List<Event> events) {
        for (Event event : events) {
            jdbcTemplate.update(
                "INSERT INTO events VALUES (?, ?, ?)",
                event.getEventDate(), event.getEventType(), event.getUserId()
            );
        }
    }

    /**
     * ✅ 正确：批量写入
     */
    public void insertBatch(List<Event> events) {
        String sql = "INSERT INTO events (event_date, event_type, user_id) VALUES (?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, events, events.size(), (ps, event) -> {
            ps.setDate(1, Date.valueOf(event.getEventDate()));
            ps.setString(2, event.getEventType());
            ps.setLong(3, event.getUserId());
        });
    }
}
```

> **批量大小建议**：每批 1000~10000 条。太小（< 100）发挥不了列式压缩优势，太大（> 100000）可能内存压力过大。

---

## 9. 分布式简介

ClickHouse 支持分布式部署——把数据分散到多台机器上，查询时自动路由到所有节点。

### 9.1 核心概念

```
单机 ClickHouse：
  ┌──────────────────────┐
  │   ClickHouse Server  │
  │   ┌──────────────┐   │
  │   │   本地表      │   │
  │   │  MergeTree   │   │
  │   └──────────────┘   │
  └──────────────────────┘

分布式 ClickHouse：
  ┌────────────────────────────────────────────────────┐
  │                 ClickHouse Cluster                  │
  │                                                    │
  │  ┌──────────────────┐     ┌──────────────────┐    │
  │  │     Shard 1      │     │     Shard 2      │    │
  │  │ ┌──────┐┌──────┐ │     │ ┌──────┐┌──────┐ │    │
  │  │ │ 主   ││ 副本 │ │     │ │ 主   ││ 副本 │ │    │
  │  │ │Node1 ││Node2 │ │     │ │Node3 ││Node4 │ │    │
  │  │ └──────┘└──────┘ │     │ └──────┘└──────┘ │    │
  │  └──────────────────┘     └──────────────────┘    │
  │                                                    │
  │  Shard 1 存数据前半部分                              │
  │  Shard 2 存数据后半部分                              │
  │  每个 Shard 有副本保证高可用                           │
  └────────────────────────────────────────────────────┘
```

**三个核心概念**：

| 概念                | 含义                                   | 类比           |
| ------------------- | -------------------------------------- | -------------- |
| **Shard（分片）**   | 数据水平拆分，每个分片存一部分数据     | MySQL 分库     |
| **Replica（副本）** | 每个分片有多份拷贝，某台挂了不影响查询 | MySQL 主从复制 |
| **Distributed 表**  | 逻辑表，写入/查询时自动分发到各分片    | 代理层         |

### 9.2 Distributed 引擎

```sql
-- Illustrative fragment
-- 先在每个 Shard 上创建本地表（MergeTree）
CREATE TABLE events_local ON CLUSTER cluster_name (
    event_date Date,
    event_type String,
    user_id    UInt64
)
ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/events', '{replica}')
PARTITION BY toYYYYMM(event_date)
ORDER BY (event_type, user_id);

-- 然后创建 Distributed 表（逻辑表）
CREATE TABLE events_distributed ON CLUSTER cluster_name AS events_local
ENGINE = Distributed(cluster_name, default, events_local, rand());
--                                                      ↑
--                                               分片键（决定数据写到哪个 Shard）

-- 写入 Distributed 表 → 自动分发到各 Shard
INSERT INTO events_distributed VALUES ...;

-- 查询 Distributed 表 → 自动查询所有 Shard 并合并结果
SELECT event_type, count() FROM events_distributed GROUP BY event_type;
```

### 9.3 数据流向

```
写入流程：
  应用 → INSERT INTO events_distributed
       → Distributed 表按分片键路由
       → Shard 1 的 events_local（写入主节点）
       → Shard 2 的 events_local（写入主节点）
       → 主节点同步到副本

查询流程：
  应用 → SELECT FROM events_distributed
       → Distributed 表向所有 Shard 发送子查询
       → Shard 1 本地查询 → 返回部分结果
       → Shard 2 本地查询 → 返回部分结果
       → Distributed 表合并所有结果 → 返回给应用
```

> **了解即可**：分布式部署涉及集群配置、ZooKeeper/ClickHouse Keeper、副本同步等运维细节，本篇不深入。生产环境通常由运维团队搭建，开发者只需要知道"写 Distributed 表、查 Distributed 表"即可。

---

## 10. 最佳实践与速查清单

### 10.1 表引擎选型

| 场景                       | 推荐引擎               |
| -------------------------- | ---------------------- |
| 通用日志/事件/指标         | `MergeTree`            |
| 同主键多次写入，只保留最新 | `ReplacingMergeTree`   |
| 增量数据自动累加           | `SummingMergeTree`     |
| 频繁更新/删除              | `CollapsingMergeTree`  |
| 复杂预聚合（avg/uniq）     | `AggregatingMergeTree` |

### 10.2 数据类型对照

| 用途     | MySQL                  | ClickHouse                               |
| -------- | ---------------------- | ---------------------------------------- |
| 整数     | `BIGINT UNSIGNED`      | `UInt64`                                 |
| 字符串   | `VARCHAR(255)`         | `String` 或 `LowCardinality(String)`     |
| 精确小数 | `DECIMAL(10,2)`        | `Decimal64(2)`                           |
| 日期     | `DATE`                 | `Date`                                   |
| 日期时间 | `DATETIME`             | `DateTime` 或 `DateTime64(3)`            |
| 枚举     | `ENUM(...)`            | `Enum8(...)` 或 `LowCardinality(String)` |
| 数组     | 无（用 JSON 或关联表） | `Array(T)`                               |
| 键值对   | 无                     | `Map(K, V)`                              |

### 10.3 批量写入要点

```
✅ 每次 INSERT 至少 1000 行
✅ JDBC 使用 batchUpdate()
✅ 批量大小 1000~10000 条/批
✅ 并发写入时控制并发数（建议 ≤ 4 个并发 INSERT）

❌ 逐条 INSERT（最大反模式）
❌ 每秒超过 1 次 INSERT 请求
❌ 单条 INSERT 超过 100000 行（内存压力）
```

### 10.4 排序键设计

```
1. 等值查询字段放最前面
2. 低基数字段优先（如 status、type）
3. 范围查询字段放后面（如 date）
4. 排序键总大小尽量小

示例：
  ORDER BY (tenant_id, event_type, event_date)
  → tenant_id 等值过滤 → event_type 等值过滤 → event_date 范围过滤
```

### 10.5 物化视图用法

```
1. 确定聚合维度（按什么字段分组）
2. 创建目标表（SummingMergeTree / AggregatingMergeTree）
3. 创建物化视图（TO 目标表）
4. 查询时直接读物化视图，不查源表
5. 一个源表可以有多个物化视图（不同维度）
```

### 10.6 常见坑清单

| 坑                         | 说明                                | 正确做法                                     |
| -------------------------- | ----------------------------------- | -------------------------------------------- |
| 逐条 INSERT                | 产生大量小文件，性能极差            | 批量写入（1000+ 行/次）                      |
| UPDATE/DELETE 当日常操作用 | Mutation 操作很重，影响性能         | 用 ReplacingMergeTree 或 CollapsingMergeTree |
| PRIMARY KEY 当唯一约束     | ClickHouse 的 PK 不保证唯一         | 用 ReplacingMergeTree 去重                   |
| 按高基数列分区             | `PARTITION BY user_id` 导致分区爆炸 | 按日期/时间分区                              |
| Nullable 滥用              | 查询变慢、不能作为排序键            | 用默认值代替 NULL                            |
| 排序键选错                 | 查询无法利用索引，全表扫描          | 按查询模式设计排序键                         |
| 不用物化视图               | 每次查询都全表扫描                  | 高频聚合查询用物化视图预计算                 |

---

## 参考文档

- [ClickHouse 官方文档](https://clickhouse.com/docs)
- [ClickHouse JDBC 驱动](https://github.com/ClickHouse/clickhouse-java)
- [ClickHouse 与 MySQL 对比](https://clickhouse.com/docs/en/integrations/mysql)
