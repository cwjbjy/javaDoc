## Why

`docs/Spring/spring-transaction-guide.md` 全文围绕"单数据源 + 单事务管理器"展开，从未提及当项目存在多个 `DataSource` 时 `@Transactional` 默认绑定哪个 `PlatformTransactionManager`。多数据源是生产系统中的常见架构（账户库与日志库分离、读写分离、微服务本地多库等），读者如果直接沿用指南中的 `@Transactional` 写法，可能静默地使用了错误的事务管理器——事务不生效、数据不回滚，且无报错提示。本变更在第 4 节（事务失效陷阱）与第 5 节（速查清单）之间插入独立章节"多数据源下的事务管理"，以渐进式风格讲解多数据源场景下事务管理器的选择与指定方式。

## What Changes

### 新增第 5 节：多数据源下的事务管理

在 `docs/Spring/spring-transaction-guide.md` 现有第 4 节之后、速查清单之前，插入独立章节。保持指南的"问题驱动 → 代码示例 → ASCII 图 → 决策流程"风格，延续银行转账场景（账户库 + 日志库分离）。

#### 5.1 问题：@Transactional 管哪个？

- 场景引入：银行系统扩展——账户数据在 MySQL（`accountDS`），操作日志在另一个 MySQL（`logDS`）
- ASCII 图：Spring 容器中有两个 `DataSource` → 两个 `PlatformTransactionManager` → `@Transactional` 不知道该管谁
- 不指定的后果：可能绑定到"错误"的事务管理器，事务静默失效（与第 4 节"五大陷阱"呼应）

#### 5.2 定义多个事务管理器

- 配置类示例：两个 `DataSource` Bean + 两个 `PlatformTransactionManager` Bean
- `@Bean("accountTransactionManager")` + `@Bean("logTransactionManager")` 命名方式
- 说明：每个事务管理器绑定各自的数据源，互不干扰

#### 5.3 @Primary：标记默认事务管理器

- `@Primary` 的作用：当容器中存在多个同类型 Bean 时，标记"默认"那个
- 类比：`@Primary` 就像"默认路由"——不指定走哪条路时，走默认
- 代码示例：`accountTransactionManager` 标记 `@Primary`
- 决策流程图：`@Transactional` 不指定 `transactionManager` → 找 `@Primary` 标记的

#### 5.4 @Transactional 指定事务管理器

- `@Transactional(transactionManager = "logTransactionManager")` 显式指定
- `@Transactional("logTransactionManager")` 简写（`value` 属性）
- 对比图：指定 vs 不指定 → 走不同数据源
- 代码示例：`TransferService` 用 `accountTransactionManager`，`LogService` 用 `logTransactionManager`

#### 5.5 常见注意事项

- **陷阱 1：忘了 @Primary**：多个 `PlatformTransactionManager` 无 `@Primary`，启动报 `NoUniqueBeanDefinitionException`
- **陷阱 2：Bean 名称写错**：`transactionManager = "acountTransactionManager"`（拼写错误），运行时才报错，无编译期检查
- **陷阱 3：跨数据源事务**：一个方法同时操作两个数据源时，Spring 本地事务无法协调两个事务管理器——点到为止，提示"需要分布式事务方案（如 Seata/Saga）"，不展开
- **陷阱 4：事务管理器类型选错**：JPA 场景用 `JpaTransactionManager`，MyBatis/JDBC 场景用 `DataSourceTransactionManager`——简要提及区别，不深入配置

### 速查清单同步更新

- 在第 5 节（原速查清单，重编号为第 6 节）中新增：
  - `5.7`（→ `6.7`）多数据源事务管理器速查表（`@Primary` vs 显式指定 vs 类型选择）
- 目录更新：新增第 5 节条目，原第 5 节重编号为第 6 节

### 明确不引入的内容

- 分布式事务方案（Seata、Saga、XA）：仅在 5.5 陷阱 3 中一句话提示边界，不展开
- `@EnableTransactionManagement` 多数据源配置细节：指南已说明 Spring Boot 自动配置，不重复
- `ChainedTransactionManager`：已在 Spring Data 2022+ 中废弃，不引入
- JTA（Java Transaction API）：超出本指南范围

## Capabilities

### New Capabilities

- `multi-datasource-transaction-guide`: `docs/Spring/spring-transaction-guide.md` 多数据源事务管理章节——必须覆盖多事务管理器定义、`@Primary` 默认标记、`@Transactional` 显式指定、常见注意事项（含跨数据源边界提示和事务管理器类型选择）

### Modified Capabilities

（无——现有 openspec/specs 下均为项目功能 spec，与本指南无关）

## Impact

- `docs/Spring/spring-transaction-guide.md`: 在第 4 节与速查清单之间插入新第 5 节（约 200~280 行），原第 5 节重编号为第 6 节并新增多数据源速查条目；目录同步更新
- `openspec/specs/multi-datasource-transaction-guide/spec.md`: 新增 capability spec
- 无业务代码修改、无依赖变更、无向后兼容影响（纯文档交付物）
