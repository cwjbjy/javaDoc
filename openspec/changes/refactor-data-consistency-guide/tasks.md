## 1. 重写概念篇 data-consistency-guide.md

- [x] 1.1 重写 §1"先写业务规则，再选技术"：将菜品/分类示例表替换为订单/账户/库存领域的业务规则表，保留概念对照表（原子性、并发控制、事务、唯一约束、幂等性、最终一致性）
- [x] 1.2 重写 §2"为什么先查再保存会丢数据"：用订单状态修改和库存数量累加作为示例，保留时间线交错图，代码示例改为伪代码或 Spring Data 通用 API，移除 `$set`/`$inc`/`MongoTemplate` 等 MongoDB 语法，添加指向 MongoDB 实现篇的链接
- [x] 1.3 重写 §3"写入没有报错，为什么还要检查结果"：保留 matchedCount/modifiedCount 概念表（作为通用概念说明），移除 MongoDB 具体 API 代码，替换为通用伪代码示例
- [x] 1.4 重写 §4"多次写入如何一起成功或失败"：用"订单创建 + 库存扣减"或"转账的借方贷方"替代"跨分类移动菜品"示例，保留事务流程图和注意事项（数据库无关部分），将 `MongoTransactionManager`、副本集要求等迁移到实现篇
- [x] 1.5 重写 §5"唯一性为什么必须交给数据库"：用"账户编号不能重复"替代"分类名唯一"示例，保留"查询不等于约束"的核心论证，将 `@Indexed(unique=true)`、配置键、索引管理迁移到实现篇
- [x] 1.6 重写 §6"原子自增为什么仍然会重复计数"：用"订单提交后重试导致重复扣款"替代原示例，保留幂等键设计流程图，移除 MongoDB 事务内幂等记录的具体实现
- [x] 1.7 重写 §7"数据库和外部资源如何保持一致"：将"图片文件"泛化为"外部资源/文件存储"，保留 outbox 模式设计流程图，移除 `findAndModify`/`findAndRemove` 等 MongoDB 特定细节
- [x] 1.8 删除 §8"对照当前项目检查保护边界"整节
- [x] 1.9 重写 §9"如何测试真正可能出错的地方"：用新领域（订单/账户/库存）重写测试场景表，移除对当前项目测试代码的引用
- [x] 1.10 重写 §10"开发时的选择速查"：保留表格结构，确保内容不绑定特定数据库
- [x] 1.11 重写 §11"延伸阅读"：移除版本核验记录表和"当前项目..."说明，保留 MongoDB 官方文档链接和 Spring 官方文档链接，添加指向 MongoDB 实现篇的链接
- [x] 1.12 在概念篇需要具体实现说明的位置添加指向 `data-consistency-mongodb-guide.md` 的相对路径链接

## 2. 创建 MongoDB 实现篇 data-consistency-mongodb-guide.md

- [x] 2.1 创建文件 `docs/Spring/database/data-consistency-mongodb-guide.md`，编写文档开头：标题、前置阅读要求（链接到概念篇）、适用版本范围说明
- [x] 2.2 编写"局部更新与丢失更新"章节：迁移 `$set`/`$inc` 代码示例、`MongoTemplate` 用法、乐观锁版本号条件更新、`Category.class` 字段映射说明
- [x] 2.3 编写"写入结果检查"章节：迁移 MongoDB 的 `UpdateResult` API 用法和匹配数检查代码
- [x] 2.4 编写"事务配置与使用"章节：迁移 `MongoTransactionManager` 配置、副本集要求、连接串说明、事务内操作注意事项、`TransactionTemplate` 用法
- [x] 2.5 编写"唯一索引管理"章节：迁移 `@Indexed(unique = true)` 注解、`spring.data.mongodb.auto-index-creation` 配置键、`db.<collection>.getIndexes()` 核验命令、已有数据上的索引发布计划
- [x] 2.6 编写"幂等记录的 MongoDB 实现"章节：迁移事务内幂等记录流程的 MongoDB 具体代码
- [x] 2.7 编写"跨资源一致性：数据库与文件"章节：迁移 `findAndModify`/`findAndRemove` 原子删除、图片清理流程、outbox 清理任务的 MongoDB 实现
- [x] 2.8 编写"配置核验清单"章节：提供 MongoDB 连接、副本集、索引、事务能力的通用核验步骤和命令
- [x] 2.9 编写"延伸阅读"章节：添加 MongoDB 官方文档链接（原子性、事务、唯一索引、生产环境注意事项）
- [x] 2.10 检查每个章节开头是否包含指向概念篇对应章节的交叉引用链接

## 3. 交叉引用与收尾

- [x] 3.1 检查 `docs/Spring/database/` 目录下其他文档（如 `spring-data-mongodb-guide.md`）是否有引用旧版章节编号，如有则更新
- [x] 3.2 通读概念篇全文，确认无残留的 MongoDB 特定语法和项目绑定内容
- [x] 3.3 通读实现篇全文，确认所有代码示例与概念篇的交叉引用正确
