# Spec: clickhouse-guide

## ADDED Requirements

### Requirement: 指南定位与读者契约

指南 SHALL 面向熟悉 MySQL 但首次接触 OLAP 的开发者，在开头明确读者画像、学习目标、适用版本（ClickHouse 25.x）与范围外声明。

#### Scenario: 读者契约要素齐全

- **WHEN** 读者打开指南开头
- **THEN** 能看到读者画像（MySQL 用户）、学习目标、适用版本与明确的非目标声明

#### Scenario: 范围外内容有导航

- **WHEN** 指南涉及大数据生态集成或深度运维调优
- **THEN** 指南给出一句话导航说明范围外，而不是展开讲解

### Requirement: 渐进式章节结构

指南 SHALL 按依赖顺序组织章节：OLAP vs OLTP → 列式存储原理 → 安装与基本使用 → 表引擎 → 数据类型 → 数据操作 → 物化视图 → 字典 → Spring Boot 集成 → 分布式简介 → 最佳实践速查。每个后续章节 SHALL 只引入依赖前面概念的新知识。

#### Scenario: 章节顺序遵循依赖链

- **WHEN** 读者按顺序阅读指南
- **THEN** 不会遇到"稍后解释"式的前向引用，每个新概念出现前其前置概念已建立

#### Scenario: 速查清单可独立查阅

- **WHEN** 读者已读完指南需要快速回顾
- **THEN** 速查清单以表格形式汇总表引擎选型、数据类型对照、批量写入要点、物化视图用法

### Requirement: MySQL 对照贯穿全文

指南 SHALL 在每个新概念引入时提供与 MySQL 的对照说明，使用"你已经知道 X（MySQL），ClickHouse 里是 Y"的叙事方式。

#### Scenario: 核心概念有 MySQL 类比

- **WHEN** 指南引入表引擎、分区键、排序键、物化视图等概念
- **THEN** 每个概念都有对应的 MySQL 类比或差异说明

#### Scenario: 差异有明确标注

- **WHEN** ClickHouse 行为与 MySQL 有本质不同（如 INSERT 必须批量、PRIMARY KEY 不等于唯一约束）
- **THEN** 指南用醒目标注（⚠️ 或对比表格）提示差异，避免读者误用 MySQL 经验

### Requirement: 列式存储原理讲解

指南 SHALL 用 ASCII 图解清晰解释列式存储与行式存储的区别，包括：数据物理存储方式、查询时的 IO 差异、聚合计算的性能优势来源。

#### Scenario: 图解直观可理解

- **WHEN** 读者阅读列式存储章节
- **THEN** 能通过 ASCII 图理解"为什么分析查询用列式更快"，不需要额外背景知识

### Requirement: 表引擎覆盖 MergeTree 家族

指南 SHALL 覆盖 MergeTree（基础）、ReplacingMergeTree（去重）、SummingMergeTree（自动聚合）三个核心引擎，并简要提及其他引擎（CollapsingMergeTree、Distributed 等）。

#### Scenario: 引擎选型有判别依据

- **WHEN** 读者面对具体业务场景
- **THEN** 指南提供的对比表能让读者判断该用哪个引擎

### Requirement: 物化视图大篇幅讲解

物化视图章节 SHALL 包含：与 MySQL 视图的本质区别、数据流入原理（ASCII 图解）、完整建表+查询示例、使用场景选择、与普通查询的性能对比说明。

#### Scenario: 原理讲解充分

- **WHEN** 读者阅读物化视图章节
- **THEN** 能理解物化视图"在 INSERT 时自动触发聚合"的机制，以及它与 MySQL VIEW 的根本不同

#### Scenario: 示例完整可参考

- **WHEN** 读者想在自己的项目中使用物化视图
- **THEN** 指南提供完整的建表语句、物化视图创建语句、查询示例

### Requirement: Spring Boot 集成简洁实用

集成章节 SHALL 展示 Maven 依赖、YAML 数据源配置、JdbcTemplate 基本查询与批量写入，不封装 Repository 层。

#### Scenario: 配置可复制

- **WHEN** 读者想在 Spring Boot 项目中接入 ClickHouse
- **THEN** 指南给出可直接复制的依赖声明和配置片段

#### Scenario: 批量写入有正确示范

- **WHEN** 读者需要批量写入数据
- **THEN** 指南展示 `batchUpdate` 或批量 INSERT 的正确用法，并说明逐条 INSERT 的性能问题

### Requirement: 分布式简介概念清晰

分布式章节 SHALL 用 ASCII 图讲解 Shard（分片）、Replica（副本）、Distributed 引擎的概念，让读者有大致了解即可，不深入集群搭建。

#### Scenario: 概念理解

- **WHEN** 读者阅读分布式章节
- **THEN** 能理解"数据分散到多个节点、每个节点有副本、Distributed 表自动路由查询"的基本架构

### Requirement: 写作风格与结构校验

指南 SHALL 遵循 guide-writing skill 的 content contract（示例诚实分级、依赖顺序、术语先定义后使用），保持与项目既有指南一致的文风（中文叙述、代码注释中文、ASCII 图示、分节递进），并通过 `validate_guide.py` 结构校验。

#### Scenario: 示例状态诚实

- **WHEN** 指南展示代码示例
- **THEN** 每个示例标注 Illustrative fragment 或相应状态，未实际运行的不宣称已验证

#### Scenario: 结构校验通过

- **WHEN** 指南文件保存后运行 `validate_guide.py`
- **THEN** 校验器报告通过，目录锚点有效
