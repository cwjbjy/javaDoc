## Context

`docs/Spring/spring-transaction-guide.md` 当前 845 行，结构为：

1. 为什么需要事务管理
2. 入门三步走（2.1 最简用法 / 2.2 rollbackFor / 2.3 传播行为）
3. 事务隔离级别
4. 事务失效的常见陷阱
5. 速查清单

新增章节采用**方案 B**：作为独立第 5 节插入第 4 节与速查清单之间，原第 5 节重编号为第 6 节。

## Decisions

### D1: 章节定位为"进阶场景"而非"入门第四层"

多数据源事务管理依赖读者已掌握单数据源的所有概念（传播行为、隔离级别、失效陷阱），放在入门三步走中会破坏渐进节奏。独立成章让读者在掌握基础后按需进入。

### D2: 延续银行转账场景

现有指南以"银行转账"为贯穿场景。多数据源章节将其自然扩展为"账户库 + 日志库分离"，读者无需切换心智模型。

### D3: 跨数据源事务点到为止

一个方法同时操作两个数据源是分布式事务领域（Seata/Saga/XA）。本指南定位为 Spring 本地事务管理入门，不应展开分布式方案。仅在 5.5 陷阱 3 中用一段话明确边界："Spring 本地事务无法协调两个数据源，需要分布式事务方案"。

### D4: JPA vs MyBatis 事务管理器类型简要提及

现有指南代码示例基于 JPA（`JpaRepository`），但实际项目中 MyBatis 同样常见。多数据源配置中事务管理器类型不同（`JpaTransactionManager` vs `DataSourceTransactionManager`），在 5.5 陷阱 4 中简要提及区别，不深入配置细节。

### D5: 不引入已废弃或超出范围的技术

- `ChainedTransactionManager`：Spring Data 2022+ 已废弃
- JTA：超出本指南范围
- `@EnableTransactionManagement` 多数据源配置：指南已说明 Spring Boot 自动配置

## Risks

- **内容量控制**：多数据源配置本身很复杂（连接池、SqlSessionFactory 等），需严格控制在本指南事务管理的范围内，避免滑入"多数据源配置教程"
- **代码示例准确性**：配置类示例需确保 import 路径和 API 与 Spring Boot 3.x / Spring Framework 6.x 一致
