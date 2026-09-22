# Tasks: create-clickhouse-guide

## 1. 指南撰写

- [x] 1.1 开头：读者契约、学习目标、适用版本（ClickHouse 25.x）与范围外声明
- [x] 1.2 第一章：ClickHouse 是什么——OLAP vs OLTP 对比、列式存储原理（ASCII 图解）、适用/不适用场景
- [x] 1.3 第二章：安装与基本使用——Docker 部署、clickhouse-client 常用命令、与 MySQL 客户端的体验差异
- [x] 1.4 第三章：表引擎——MergeTree（类比 InnoDB）、ReplacingMergeTree（去重）、SummingMergeTree（自动聚合）、其他引擎简介
- [x] 1.5 第四章：数据类型——基础类型与 MySQL 对比、特色类型（Array/Tuple/Map/Enum）、Nullable 的代价
- [x] 1.6 第五章：数据操作——INSERT 批量写入最佳实践、SELECT 查询语法（MySQL 异同）、分区键、排序键、采样
- [x] 1.7 第六章：物化视图（大篇幅）——与 MySQL 视图的区别、数据流入原理图解、完整建表+查询示例、使用场景选择
- [x] 1.8 第七章：字典（Dictionary）——概念、用途、简单示例
- [x] 1.9 第八章：Spring Boot 集成——Maven 依赖、YAML 配置、JdbcTemplate 基本 CRUD、批量写入正确姿势
- [x] 1.10 第九章：分布式简介——Shard + Replica 架构（ASCII 图）、Distributed 引擎、数据写入与查询流程
- [x] 1.11 第十章：最佳实践与速查清单——表引擎选型表、数据类型对照表、批量写入要点、物化视图用法、常见坑

## 2. 校验与收尾

- [x] 2.1 运行 `py -3 .agents/skills/guide-writing/scripts/validate_guide.py docs/ClickHouse/clickhouse-guide.md` 确保结构校验通过
- [x] 2.2 复核目录锚点、章节编号、前后引用一致
- [x] 2.3 对照 spec 的 Requirement 逐条自查，交付 Verification summary
