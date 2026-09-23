## ADDED Requirements

### Requirement: 多数据源场景问题引入

指南 SHALL 在第 5 节开头以银行转账场景的扩展（账户库 + 日志库分离）引入问题：当 Spring 容器中存在多个 `DataSource` 和多个 `PlatformTransactionManager` 时，`@Transactional` 默认绑定哪个事务管理器。

### Requirement: 多事务管理器定义示例

指南 SHALL 提供配置类代码示例，展示如何定义两个 `DataSource` Bean 和两个 `PlatformTransactionManager` Bean（使用 `@Bean("name")` 命名方式），并说明每个事务管理器绑定各自的数据源。

### Requirement: @Primary 默认事务管理器

指南 SHALL 讲解 `@Primary` 注解在多事务管理器场景下的作用：标记默认事务管理器，使不指定 `transactionManager` 的 `@Transactional` 自动使用它。SHALL 包含类比说明和决策流程图。

### Requirement: @Transactional 显式指定事务管理器

指南 SHALL 讲解 `@Transactional(transactionManager = "xxx")` 和 `@Transactional("xxx")` 简写两种用法，并提供对比代码示例展示指定与不指定的区别。

### Requirement: 常见注意事项

指南 SHALL 覆盖以下四个注意事项：

1. 忘了 `@Primary` 导致启动报错（`NoUniqueBeanDefinitionException`）
2. `transactionManager` Bean 名称拼写错误导致运行时失败
3. 跨数据源事务的边界提示（Spring 本地事务无法协调多个事务管理器，需分布式事务方案）
4. 事务管理器类型选择（JPA 场景用 `JpaTransactionManager`，MyBatis/JDBC 场景用 `DataSourceTransactionManager`）

### Requirement: 速查清单同步

指南 SHALL 在速查清单章节新增多数据源事务管理器速查表，覆盖 `@Primary` vs 显式指定 vs 类型选择的决策。

### Requirement: 风格一致性

新增章节 SHALL 保持与现有指南一致的渐进式风格：问题驱动 → 代码示例 → ASCII 图可视化 → 决策流程图。延续银行转账贯穿场景。

### Requirement: 章节编号调整

新增第 5 节后，原第 5 节（速查清单）SHALL 重编号为第 6 节，目录 SHALL 同步更新。
