## ADDED Requirements

### Requirement: MongoDB 实现篇须以概念篇为前置
MongoDB 实现篇 SHALL 在每个章节开头引用 `data-consistency-guide.md` 中对应的概念章节，明确说明"本节是概念 X 在 MongoDB 中的具体实现"。

#### Scenario: 读者从概念篇跳转到实现篇
- **WHEN** 读者阅读概念篇中"丢失更新"章节并点击"在 MongoDB 中实现"链接
- **THEN** 跳转到 MongoDB 实现篇的"局部更新与丢失更新"章节，该章节开头注明"本节对应概念篇 §2"

#### Scenario: 读者直接阅读实现篇
- **WHEN** 读者直接打开 MongoDB 实现篇
- **THEN** 文档开头注明前置阅读要求，提供概念篇的链接

### Requirement: 实现篇须覆盖原文所有 MongoDB 特定内容
MongoDB 实现篇 SHALL 包含从原文迁移的所有 MongoDB 特定内容，包括：`$set`/`$inc` 局部更新代码示例、`MongoTemplate` 用法、乐观锁版本号条件更新、`MongoTransactionManager` 配置与副本集要求、`@Indexed(unique = true)` 与自动索引配置键、幂等记录的 MongoDB 事务内实现、`findAndModify`/`findAndRemove` 原子删除与图片清理、outbox 清理任务的 MongoDB 实现。

#### Scenario: 原文 MongoDB 代码示例在实现篇中可找到
- **WHEN** 对照原文 §2.2 的 `mongoTemplate.updateFirst(... $set ...)` 代码
- **THEN** 在实现篇"局部更新"章节中找到等价或改进的 MongoDB 代码示例

#### Scenario: 原文事务配置在实现篇中可找到
- **WHEN** 对照原文 §4.3 的副本集要求和 `MongoTransactionManager` 说明
- **THEN** 在实现篇"事务配置"章节中找到完整的配置说明和注意事项

### Requirement: 实现篇须包含配置核验指引
MongoDB 实现篇 SHALL 提供配置核验指引，指导读者验证 MongoDB 连接、副本集、索引和事务能力是否正确配置，但不绑定特定项目的配置值。

#### Scenario: 读者核验索引配置
- **WHEN** 读者需要确认唯一索引是否生效
- **THEN** 实现篇提供 `db.<collection>.getIndexes()` 等核验命令和预期输出说明

#### Scenario: 读者核验事务支持
- **WHEN** 读者需要确认 MongoDB 部署是否支持多文档事务
- **THEN** 实现篇说明副本集 vs standalone 的判断方法，以及连接串中 `replicaSet` 参数的含义

### Requirement: 实现篇须包含版本适配说明
MongoDB 实现篇 SHALL 注明代码示例基于的 Spring Data MongoDB 和 Spring Boot 版本范围，并在 API 可能因版本变化时提供核对指引。

#### Scenario: 读者使用不同版本
- **WHEN** 读者使用的 Spring Boot 版本与文档标注的范围不同
- **THEN** 文档提供核对配置键和 API 签名的官方文档链接
