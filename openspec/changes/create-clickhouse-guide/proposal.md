# Proposal: create-clickhouse-guide

## Why

`docs/` 已覆盖 Redis、MongoDB、MyBatis 等数据组件指南，但缺少 OLAP 列式数据库的入门内容。ClickHouse 是当前最流行的开源 OLAP 引擎之一，与项目中已有的 MySQL/Redis 形成互补——MySQL 负责 OLTP（事务），ClickHouse 负责 OLAP（分析查询）。项目目标读者熟悉 MySQL 但对列式存储没有经验，需要一篇从 MySQL 视角切入、循序渐进建立 OLAP 心智模型的指南。

## What Changes

- 新增 `docs/ClickHouse/clickhouse-guide.md`：渐进式（progressive）指南，从"OLAP vs OLTP"讲起，逐步覆盖列式存储原理、表引擎（MergeTree 家族）、数据类型、数据操作（批量写入最佳实践）、分区与排序键、物化视图（大篇幅）、字典、Spring Boot JDBC 集成、分布式简介。
- 全文以 MySQL 为对照锚点，每个新概念用"你已经知道 X，ClickHouse 里是 Y"的方式引入。
- 遵循 guide-writing skill 规范，运行 `validate_guide.py` 做结构校验。

## Capabilities

### New Capabilities

- `clickhouse-guide`: 定义 ClickHouse 入门指南的内容契约——指南定位（MySQL 用户首次接触 OLAP）、渐进式章节结构、示例规范（ClickHouse 25.x）、与既有文档的边界。

### Modified Capabilities

<!-- 无既有 spec 的需求变化 -->

## Impact

- 新增文档：`docs/ClickHouse/clickhouse-guide.md`（新建 `docs/ClickHouse/` 目录）。
- 不引入任何依赖、不改动 `src/` 与 `pom.xml`。
- 与 `docs/Spring/Redis-SpringBoot.md` 存在主题互补性（OLTP vs OLAP），需在指南中声明边界。
