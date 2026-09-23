## Tasks

- [x] **Task 1: 更新目录**
  - 在 `docs/Spring/spring-transaction-guide.md` 目录中插入 `5. [多数据源下的事务管理](#5-多数据源下的事务管理)` 条目
  - 将原 `5. [速查清单](#5-速查清单)` 重编号为 `6. [速查清单](#6-速查清单)`
  - 更新速查清单子条目编号（5.1→6.1, 5.2→6.2, ...）

- [x] **Task 2: 编写 5.1 问题引入**
  - 以银行转账场景扩展（账户库 + 日志库分离）引入多数据源问题
  - ASCII 图：两个 DataSource → 两个 TransactionManager → @Transactional 不知道该管谁
  - 说明不指定的后果（与第 4 节陷阱呼应）

- [x] **Task 3: 编写 5.2 定义多个事务管理器**
  - 配置类代码示例：两个 DataSource Bean + 两个 PlatformTransactionManager Bean
  - `@Bean("accountTransactionManager")` + `@Bean("logTransactionManager")` 命名方式
  - 说明每个事务管理器绑定各自数据源

- [x] **Task 4: 编写 5.3 @Primary 标记默认事务管理器**
  - `@Primary` 注解的作用和类比说明
  - 代码示例：`accountTransactionManager` 标记 `@Primary`
  - 决策流程图：不指定 transactionManager → 找 @Primary

- [x] **Task 5: 编写 5.4 @Transactional 指定事务管理器**
  - `@Transactional(transactionManager = "xxx")` 和简写 `@Transactional("xxx")`
  - 对比图：指定 vs 不指定
  - 代码示例：TransferService 和 LogService 分别使用不同事务管理器

- [x] **Task 6: 编写 5.5 常见注意事项**
  - 陷阱 1：忘了 @Primary → NoUniqueBeanDefinitionException
  - 陷阱 2：Bean 名称拼写错误 → 运行时失败
  - 陷阱 3：跨数据源事务边界提示（点到为止）
  - 陷阱 4：事务管理器类型选择（JPA vs MyBatis，简要提及）

- [x] **Task 7: 更新速查清单**
  - 将原第 5 节所有子节编号从 5.x 改为 6.x
  - 新增 6.7 多数据源事务管理器速查表
  - 更新节标题编号

- [x] **Task 8: 验证与审查**
  - 检查全文交叉引用和锚点链接正确
  - 确认代码示例 import 路径与 Spring Boot 3.x / Spring Framework 6.x 一致
  - 确认风格与现有章节一致（问题驱动 → 代码 → ASCII 图 → 决策流程）
