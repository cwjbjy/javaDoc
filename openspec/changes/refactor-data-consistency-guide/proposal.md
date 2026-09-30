## Why

当前 `data-consistency-guide.md` 将通用数据一致性概念（丢失更新、事务、幂等、唯一约束、跨资源一致性）与项目特定实现（MongoDB 菜品/分类操作、Spring Boot 4.0.6 版本核验、具体代码文件链接、项目保护边界检查表）紧密耦合。这导致两个问题：(1) 非本项目开发者难以提取通用知识；(2) 项目演进后版本核验和项目检查表迅速过时。需要将指南拆分为"数据库无关的概念层"和"特定数据库的实现篇"，使核心知识长期可用。

## What Changes

- **重构 `data-consistency-guide.md` 为通用概念指南**：保留原有递进结构（业务规则 → 并发 → 事务 → 唯一性 → 幂等 → 跨资源一致性），但将示例领域从"菜品/分类/图片"替换为"订单/账户"等更通用的业务场景
- **移除项目绑定内容**：删除 §8（对照当前项目检查保护边界）、§11 中的版本核验记录表、各章节中"当前项目..."的核验注释、具体代码文件链接
- **将 MongoDB 特定内容拆出**：原文中 MongoDB 专属语法（`$set`、`$inc`、`MongoTemplate`、副本集配置等）迁移到新建的 `docs/Spring/database/data-consistency-mongodb-guide.md` 实现篇
- **新建 MongoDB 实现篇**：以通用概念篇为前置，提供 MongoDB 场景下的具体实现对照，包含代码示例和配置说明
- **更新交叉引用**：概念篇中需要具体实现说明处，添加指向 MongoDB 实现篇的链接

## Capabilities

### New Capabilities
- `data-consistency-mongodb-guide`: MongoDB 场景下的数据一致性实现指南，包含 `$set`/`$inc` 局部更新、事务配置、唯一索引、副本集要求等 MongoDB 特定内容

### Modified Capabilities
<!-- 无需修改现有 spec 的需求。data-consistency-guide 当前没有对应的 spec。 -->

## Impact

- **文档文件**：
  - `docs/Spring/database/data-consistency-guide.md`：大幅重写（替换示例领域、移除项目绑定、精简为通用概念）
  - `docs/Spring/database/data-consistency-mongodb-guide.md`：新建（MongoDB 实现篇）
- **无代码变更**：本次变更仅涉及文档，不修改任何 Java 源码或配置
- **交叉引用**：其他文档（如 `spring-data-mongodb-guide.md`、`spring-transaction-guide.md`）中若引用了旧版章节编号，可能需要更新
