# MongoDB 数据一致性实现篇

本篇是 [数据一致性入门](data-consistency-guide.md) 的 MongoDB 实现篇。概念篇中讨论的丢失更新、事务、唯一约束、幂等和跨资源一致性等通用概念，在这里以 Spring Data MongoDB 的具体代码和配置来展示。

**前置阅读**：建议先阅读 [概念篇](data-consistency-guide.md)，了解每个问题"为什么需要解决"，再回到本篇看"在 MongoDB 中怎么实现"。

**适用版本**：本篇代码示例基于 Spring Boot 3+ / Spring Data MongoDB 4.x+。如果你使用的版本不同，部分 API 签名或配置键可能有差异，请以对应版本的官方文档为准进行核对。

## 目录

- [2 局部更新与丢失更新](#2-局部更新与丢失更新)
- [3 写入结果检查](#3-写入结果检查)
- [4 乐观锁与版本号条件更新](#4-乐观锁与版本号条件更新)
- [5 事务配置与使用](#5-事务配置与使用)
- [6 唯一索引管理](#6-唯一索引管理)
- [7 幂等记录的 MongoDB 实现](#7-幂等记录的-mongodb-实现)
- [8 跨资源一致性：数据库与文件](#8-跨资源一致性数据库与文件)
- [9 配置核验清单](#9-配置核验清单)
- [10 延伸阅读](#10-延伸阅读)

## 2 局部更新与丢失更新

> 对应概念篇 [§2 为什么先查再保存会丢数据](data-consistency-guide.md#2-为什么先查再保存会丢数据)。

### 2.1 使用 `$set` 更新指定字段

修改记录中的某个字段时，使用 `$set` 只更新该字段，避免把整个对象保存回去。

```java
// 修改订单状态时，只更新 status 字段，不携带关联的其他数据。
mongoTemplate.updateFirst(
        Query.query(Criteria.where("id").is(orderId)),
        new Update().set("status", newStatus),
        Order.class);
```

### 2.2 使用 `$inc` 在数据库端累加

需要在当前值上累加时，使用 `$inc` 让数据库直接累加，不在 Java 中执行"读出 + 1 + 保存"。

```java
// 在数据库当前值上累加库存数量。
mongoTemplate.updateFirst(
        Query.query(Criteria.where("id").is(accountId)
                .and("inventory.productId").is(productId)),
        new Update().inc("inventory.$.quantity", 1),
        Account.class);
```

### 2.3 字段映射注意事项

带上实体类参数后，Spring Data 可以依据映射元数据把 Java 属性名转换成数据库字段名。例如 Java 属性 `orderCount` 可以通过 `@Field("num")` 映射到数据库字段 `num`。

```java
// 使用实体类参数，Spring Data 自动转换属性名。
mongoTemplate.updateFirst(query, update, Account.class);

// 直接使用 mongosh 命令时，应使用数据库实际字段名。
// db.accounts.updateOne({ _id: ... }, { $inc: { "num": 1 } })
```

MongoDB 对单个文档的更新是原子的，包括该文档内的数组更新。但"先查询，再单独更新"是两个操作，不能因为每一步单独原子，就认为整个过程原子。

## 3 写入结果检查

> 对应概念篇 [§3 写入没有报错，为什么还要检查结果](data-consistency-guide.md#3-写入没有报错为什么还要检查结果)。

MongoDB 的 `UpdateResult` 提供 `getMatchedCount()` 和 `getModifiedCount()` 两个方法。

```java
UpdateResult result = mongoTemplate.updateFirst(
        Query.query(Criteria.where("id").is(orderId)),
        new Update().set("status", newStatus),
        Order.class);

if (result.getMatchedCount() != 1) {
    throw new IllegalArgumentException("订单不存在或更新条件不满足");
}
// result.getModifiedCount() == 0 表示值未变化（相同值更新），通常可视为成功。
```

删除操作应检查 `getDeletedCount()`：

```java
DeleteResult result = mongoTemplate.remove(
        Query.query(Criteria.where("id").is(orderId)),
        Order.class);

if (result.getDeletedCount() != 1) {
    // 根据接口契约决定：报错还是视为成功。
}
```

## 4 乐观锁与版本号条件更新

> 对应概念篇 [§2.3 局部更新也有边界](data-consistency-guide.md#23-局部更新也有边界)。

如果需要拒绝旧页面提交的修改，可以使用版本号进行条件更新：

```java
// 更新条件同时包含 id 和 version。
Query query = Query.query(Criteria.where("id").is(orderId)
        .and("version").is(currentVersion));

// 更新业务字段并递增版本号。
Update update = new Update()
        .set("status", newStatus)
        .inc("version", 1);

UpdateResult result = mongoTemplate.updateFirst(query, update, Order.class);

if (result.getMatchedCount() == 0) {
    // 版本号不匹配，说明已被其他请求修改。
    throw new OptimisticLockingException("数据已被修改，请重新读取");
}
```

版本字段必须真正参与所有相关写路径；只给某个实体加 `@Version` 注解，却让其他自定义更新绕过版本条件，不能构成完整保护。

## 5 事务配置与使用

> 对应概念篇 [§4 多次写入如何一起成功或失败](data-consistency-guide.md#4-多次写入如何一起成功或失败)。

### 5.1 事务管理器配置

声明 `MongoTransactionManager` Bean，使 Spring 能够管理 MongoDB 事务：

```java
@Configuration
public class MongoTransactionConfig {

    @Bean
    MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
}
```

### 5.2 副本集要求

MongoDB 服务端必须采用支持事务的副本集或分片集群。

- 单节点副本集可用于开发验证。
- 独立运行的 standalone 实例**不支持**多文档事务。
- 在连接串写上 `replicaSet=rs0` 不会把服务器变成副本集；反过来，连接串只有一个主机地址，也不能证明服务器是 standalone。

```yaml
spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/mydb?replicaSet=rs0
```

### 5.3 使用 TransactionTemplate

将多个操作放在同一个事务中：

```java
@Autowired
private TransactionTemplate transactionTemplate;

public void transferInventory(String fromId, String toId, String productId, int qty) {
    transactionTemplate.execute(status -> {
        // 从源账户扣减
        UpdateResult fromResult = mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(fromId)
                        .and("inventory.productId").is(productId)),
                new Update().inc("inventory.$.quantity", -qty),
                Account.class);
        if (fromResult.getMatchedCount() != 1) {
            throw new IllegalArgumentException("源账户或商品不存在");
        }

        // 向目标账户追加
        UpdateResult toResult = mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(toId)
                        .and("inventory.productId").is(productId)),
                new Update().inc("inventory.$.quantity", qty),
                Account.class);
        if (toResult.getMatchedCount() != 1) {
            throw new IllegalArgumentException("目标账户或商品不存在");
        }

        return null;
    });
}
```

### 5.4 事务注意事项

- `MongoTransactionManager` 将会话绑定到当前线程；随意切换异步线程或使用独立客户端，可能脱离事务。
- 不要捕获异常后直接返回"成功"。事务回调必须把失败传播出去，或明确标记回滚。
- 事务应尽量短，避免把网络调用、文件 IO 和长时间计算放进事务。
- `updateMulti`、批量 API、Java 循环都不天然提供跨文档整体原子性；需要原子性时必须显式放入事务。
- 如果使用 `@Transactional`，要注意代理调用（同类方法调用不经过代理）和回滚规则。

## 6 唯一索引管理

> 对应概念篇 [§5 唯一性为什么必须交给数据库](data-consistency-guide.md#5-唯一性为什么必须交给数据库)。

### 6.1 声明唯一索引

在实体字段上使用 `@Indexed(unique = true)` 注解：

```java
@Document(collection = "accounts")
public class Account {
    @Id
    private String id;

    @Indexed(unique = true)
    private String accountNo;
    // ...
}
```

### 6.2 自动索引创建配置

Spring Data MongoDB 可以通过配置自动创建注解声明的索引：

```yaml
spring:
  data:
    mongodb:
      auto-index-creation: true  # 开发环境可开启，生产环境建议关闭
```

注意配置键的正确路径：`spring.data.mongodb.auto-index-creation`，不是 `spring.mongodb.auto-index-creation`。

### 6.3 核验索引状态

在 mongosh 中连接到目标数据库后查看实际索引：

```javascript
db.accounts.getIndexes()
```

检查结果是否包含预期的字段索引、正确的键以及 `unique: true`。注意：集合名由 `@Document(collection = "...")` 指定或按命名策略自动生成，不会因为 Java 类名改变就自动变更。

### 6.4 已有数据上的索引发布

建立唯一索引之前，先检查已有数据：

```javascript
// 检查是否有重复值
db.accounts.aggregate([
    { $group: { _id: "$accountNo", count: { $sum: 1 } } },
    { $match: { count: { $gt: 1 } } }
])
```

已有重复数据会导致建索引失败。应先制定数据处理方案，再通过可追踪的迁移步骤发布索引并验证结果。

## 7 幂等记录的 MongoDB 实现

> 对应概念篇 [§6 原子操作为什么仍然会重复计数](data-consistency-guide.md#6-原子操作为什么仍然会重复计数)。

### 7.1 幂等记录实体

```java
@Document(collection = "idempotency_records")
public class IdempotencyRecord {
    @Id
    private String idempotencyKey;     // 幂等键
    private String status;             // PENDING / COMPLETED / FAILED
    private String paramsHash;         // 业务参数摘要
    private Object result;             // 业务结果
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}
```

### 7.2 事务内写入幂等记录

```java
public OrderResult submitOrder(String idempotencyKey, OrderRequest request) {
    return transactionTemplate.execute(status -> {
        // 1. 尝试插入幂等记录（唯一约束处理并发）
        try {
            IdempotencyRecord record = new IdempotencyRecord();
            record.setIdempotencyKey(idempotencyKey);
            record.setStatus("PENDING");
            record.setParamsHash(computeHash(request));
            record.setCreatedAt(LocalDateTime.now());
            mongoTemplate.insert(record);
        } catch (DuplicateKeyException e) {
            // 键已存在，退出事务后读取已有结果
            status.setRollbackOnly();
            return handleExistingRecord(idempotencyKey, request);
        }

        // 2. 执行业务写入
        OrderResult result = createOrder(request);

        // 3. 更新幂等记录为已完成
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(idempotencyKey)),
                new Update().set("status", "COMPLETED")
                        .set("result", result),
                IdempotencyRecord.class);

        return result;
    });
}
```

### 7.3 处理已存在的幂等记录

```java
private OrderResult handleExistingRecord(String key, OrderRequest request) {
    IdempotencyRecord existing = mongoTemplate.findById(key, IdempotencyRecord.class);

    if (existing == null) {
        throw new IllegalStateException("幂等记录异常");
    }

    switch (existing.getStatus()) {
        case "COMPLETED":
            if (!existing.getParamsHash().equals(computeHash(request))) {
                throw new IllegalArgumentException("同一幂等键不允许使用不同参数");
            }
            return (OrderResult) existing.getResult();  // 返回已保存结果
        case "PENDING":
            throw new ConflictException("请求正在处理中，请稍后查询");
        default:
            throw new IllegalStateException("幂等记录状态异常: " + existing.getStatus());
    }
}
```

### 7.4 注意事项

- 幂等记录不能先独立提交，再执行业务操作；必须在同一事务内。
- 发生重复键错误后，应退出失败的事务（`setRollbackOnly()`），再读取已提交结果。
- 记录的 `expiresAt` 必须覆盖业务允许的重试窗口。
- 对于订单等长期业务事件，建议使用永久业务唯一标识。

## 8 跨资源一致性：数据库与文件

> 对应概念篇 [§7 数据库和外部资源如何保持一致](data-consistency-guide.md#7-数据库和外部资源如何保持一致)。

### 8.1 原子取得被删除数据再清理

使用 `findAndModify` 或 `findAndRemove` 原子地取得被删除的数据，再依据真实记录中的文件路径执行清理：

```java
// 原子删除订单并取得修改前的文档
Query query = Query.query(Criteria.where("id").is(orderId));
Order deleted = mongoTemplate.findAndRemove(query, Order.class);

if (deleted != null && deleted.getAttachmentPath() != null) {
    try {
        fileStorage.delete(deleted.getAttachmentPath());
    } catch (IOException e) {
        log.warn("文件删除失败，路径: {}，需要后续清理", deleted.getAttachmentPath(), e);
    }
}
```

这避免了直接相信客户端提供的旧路径，也让数据库删除结果与用于清理的数据来自同一次操作。

### 8.2 Outbox 清理任务

将"需要删除哪个文件"作为待处理任务，与数据库操作在同一事务内提交：

```java
transactionTemplate.execute(status -> {
    // 1. 删除订单
    Order deleted = mongoTemplate.findAndRemove(query, Order.class);

    // 2. 写入待清理任务（与业务变更共同提交）
    if (deleted != null && deleted.getAttachmentPath() != null) {
        CleanupTask task = new CleanupTask();
        task.setObjectKey(deleted.getAttachmentPath());
        task.setStatus("PENDING");
        task.setRetryCount(0);
        task.setCreatedAt(LocalDateTime.now());
        mongoTemplate.insert(task);
    }

    return null;
});
```

后台清理器读取任务并执行：

```java
@Scheduled(fixedDelay = 5000)
public void processCleanupTasks() {
    List<CleanupTask> tasks = mongoTemplate.find(
            Query.query(Criteria.where("status").is("PENDING")
                    .and("createdAt").lt(LocalDateTime.now().minusSeconds(10))),
            CleanupTask.class);

    for (CleanupTask task : tasks) {
        try {
            fileStorage.delete(task.getObjectKey());
            // 文件已不存在也视为完成
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(task.getId())),
                    new Update().set("status", "COMPLETED"),
                    CleanupTask.class);
        } catch (IOException e) {
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(task.getId())),
                    new Update().inc("retryCount", 1),
                    CleanupTask.class);
            // 退避重试，持续失败时告警
        }
    }
}
```

### 8.3 注意事项

- 删除必须幂等：进程在删文件后、标记任务前崩溃，任务会重复执行。
- 任务与业务变更共同提交，避免业务已提交却丢失清理意图。
- "确认无人引用，再删除"有并发窗口；建议使用不可复用的对象键并明确独占所有权。
- 上传新文件后、数据库引用保存前也可能崩溃留下孤儿文件；可以给临时上传设置过期回收机制。

## 9 配置核验清单

部署前，按以下步骤核验 MongoDB 环境是否满足数据一致性要求。

### 9.1 连接与副本集

```javascript
// 在 mongosh 中执行
db.adminCommand({ replSetGetStatus: 1 })
// 确认返回副本集状态，而非 "not running with --replSet" 错误
```

- 连接串中的 `replicaSet` 参数是否与实际副本集名称一致。
- 如果是 standalone 实例，多文档事务不可用。

### 9.2 索引核验

```javascript
// 查看集合上的所有索引
db.<collection>.getIndexes()

// 确认唯一索引存在
// 预期输出包含 { "key": { "accountNo": 1 }, "unique": true, ... }
```

### 9.3 事务能力核验

```javascript
// 在副本集环境中尝试一个简单事务
const session = db.getMongo().startSession()
session.startTransaction()
try {
    session.getDatabase("test").testcol.insertOne({ x: 1 })
    session.commitTransaction()
    print("事务支持正常")
} catch (e) {
    session.abortTransaction()
    print("事务失败: " + e.message)
}
session.endSession()
```

### 9.4 配置键核验

| 用途 | 正确配置键 |
| --- | --- |
| MongoDB 连接 URI | `spring.data.mongodb.uri` |
| 自动创建索引 | `spring.data.mongodb.auto-index-creation` |

确认配置键前缀正确，不要使用 `spring.mongodb.uri` 等错误前缀。

## 10 延伸阅读

- [MongoDB：原子性与事务](https://www.mongodb.com/docs/manual/core/write-operations-atomicity/)
- [MongoDB：唯一索引](https://www.mongodb.com/docs/manual/core/index-unique/)
- [MongoDB：事务生产环境注意事项](https://www.mongodb.com/docs/manual/core/transactions-production-consideration/)
- [Spring Data MongoDB：事务](https://docs.spring.io/spring-data/mongodb/reference/mongodb/client-session-transactions.html)
- [Spring Data MongoDB：索引创建](https://docs.spring.io/spring-data/mongodb/reference/mongodb/mapping/mapping-index-management.html)

相关项目文档：

- [数据一致性概念篇](data-consistency-guide.md)：通用概念与思维框架
- [Spring Data MongoDB 指南](spring-data-mongodb-guide.md)：CRUD 和映射基础
- [Spring 事务指南](../spring-transaction-guide.md)：声明式事务、代理和回滚规则
