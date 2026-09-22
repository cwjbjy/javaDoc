# Design: create-clickhouse-guide

## Context

`docs/` 已覆盖 Redis、MongoDB、MyBatis 等数据组件指南，风格统一为"问题起源 → 分层递进 → 速查清单"的渐进式结构。guide-writing skill 提供了模板与内容契约。本篇要写 ClickHouse 列式数据库入门主题，读者画像为熟悉 MySQL 但首次接触 OLAP 的开发者。ClickHouse 没有 Spring Data 官方 Starter，Java 生态主要靠 JDBC 驱动，因此集成章节保持简洁（JdbcTemplate 演示）。

## Goals / Non-Goals

**Goals:**

- 产出一篇渐进式指南，读者按顺序阅读即可建立"ClickHouse 如何工作、何时使用、如何接入"的完整心智模型。
- 以 MySQL 为全程对照锚点，每个新概念用"你已经知道 X，ClickHouse 里是 Y"的方式引入。
- 覆盖用户确认的全部章节：OLAP vs OLTP、列式存储原理、安装、表引擎（MergeTree 家族）、数据类型、数据操作（批量写入）、分区与排序键、采样、物化视图（大篇幅）、字典、Spring Boot JDBC 集成、分布式简介、最佳实践速查。
- 版本锚定 ClickHouse 25.x（最新稳定版）。
- 示例诚实分级（Illustrative fragment），不宣称已实际运行。

**Non-Goals:**

- 不深入分布式部署细节（集群搭建、运维调优），仅在分布式简介章节给出概念性理解。
- 不覆盖 ClickHouse 与 Kafka/Flink 等大数据组件的集成。
- 不封装完整的 Repository 层（用户确认简单展示 JdbcTemplate 即可）。
- 不改动任何应用代码，不改动 `src/` 与 `pom.xml`。

## Decisions

- **指南形状：progressive。** ClickHouse 概念是因果链——列式存储 → 表引擎选择 → 分区/排序键设计 → 物化视图预聚合 → 分布式查询。后续章节依赖前面建立的概念，符合 progressive 的判定标准。
- **版本锚点：ClickHouse 25.x。** 使用最新稳定版，语法与特性以官方文档为准。
- **MySQL 对照策略：贯穿全文。** 不是只在开头对比一次，而是每个新概念出现时都给出"MySQL 里你这样写，ClickHouse 里这样写"的对照。
- **物化视图大篇幅讲解。** 用户确认选 A（详细讲解），包含：原理图解（数据如何流入物化视图）、与 MySQL 视图的本质区别、完整建表+查询示例、使用场景选择。
- **Spring Boot 集成保持简洁。** 仅展示 Maven 依赖 + YAML 配置 + JdbcTemplate 基本 CRUD + 批量写入正确姿势，不封装 Repository 层。
- **分布式章节点到为止。** 用 ASCII 图讲解 Shard + Replica + Distributed 引擎的概念，不深入集群搭建和运维。

## Risks / Trade-offs

- [示例未实际运行，ClickHouse 25.x 的语法或 JDBC 驱动版本可能与声明有偏差] → 示例统一标注 Illustrative fragment，不宣称已运行；关键语法对照 ClickHouse 官方文档核实后再落笔。
- [ClickHouse 版本迭代快，部分配置项或函数签名可能在新版本中变化] → 指南标注适用版本（25.x），并在开头声明版本范围。
- [与 Redis/MongoDB 指南风格差异：ClickHouse 指南更偏"数据库本身的使用"而非"框架集成"] → 开头明确声明本指南的定位差异，前六章讲 ClickHouse 本身，第七、八章讲 Java 集成和分布式。
