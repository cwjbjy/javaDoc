# Kafka 与 Spring Boot 渐进式指南

> 面向读者：已经掌握 Spring Boot、Maven 和基本 Java 开发，希望开始使用 Kafka 构建数据管道的开发者。  
> 学习结果：理解 Kafka 的分布式日志模型，能够使用 spring-kafka 收发 JSON 消息，并知道如何处理分区与顺序、可靠性（acks/ISR/幂等生产者）与消费端 offset 提交/死信。  
> 适用版本：Java 17、Spring Boot 3.5.14、spring-kafka 3.3.16（由 Boot BOM 管理）、Apache Kafka 3.7.0（KRaft 模式）。

这篇指南的组织主线是：**Kafka 的核心不是"队列"，而是"可回放的分布式提交日志"——吞吐、顺序、回放、多订阅者，全都从日志结构推导出来。**

## 范围

本文覆盖 Kafka 的核心日志模型、单机 KRaft 开发环境、Spring Boot 中的消息收发，以及顺序与并行、可靠性、消费端 offset 提交与死信等生产实践。示例使用「交易流水管道」场景：订单服务发布交易流水事件，四个下游系统以独立消费组并行订阅，新下游上线时从头回放全量流水。

集群运维、跨机房容灾、Kafka Streams / Kafka Connect、Spring Cloud Stream、事务 exactly-once 不在本文范围内（§9 延伸阅读导航）。

Kafka 用于高吞吐、可回放、多订阅的数据管道；如果消息必须单条可靠投递、路由灵活，优先考虑 [RabbitMQ 与 Spring Boot AMQP 渐进式指南](./rabbitmq-spring-boot-guide.md)；同一 Spring 应用内的轻量解耦用 [Spring 事件机制指南](./spring-event-guide.md)。

## 目录

1. [为什么需要 Kafka](#1-为什么需要-kafka)
2. [日志模型：一条消息的完整旅程](#2-日志模型一条消息的完整旅程)
3. [启动本地 Kafka](#3-启动本地-kafka)
4. [在 Spring Boot 中完成第一次收发](#4-在-spring-boot-中完成第一次收发)
5. [顺序与并行：分区与消费者组](#5-顺序与并行分区与消费者组)
6. [可靠性：从 acks 到幂等生产者](#6-可靠性从-acks-到幂等生产者)
7. [消费端：offset 提交、重复消费与死信](#7-消费端offset-提交重复消费与死信)
8. [Kafka vs RabbitMQ 选型](#8-kafka-vs-rabbitmq-选型)
9. [延伸阅读与参考资料](#9-延伸阅读与参考资料)

---

## 1. 为什么需要 Kafka

### 1.1 一个具体难题：交易流水要发给四个下游

订单服务每产生一笔交易，都会生成一条**交易流水事件**（订单号、金额、状态、时间）。问题来了：实时对账、风控、搜索索引、离线数仓四个下游系统，每个都要拿到一份完整流水。

最直觉的做法是同步调用——订单服务依次通知四个下游：

```text
订单服务 ──▶ 实时对账 ──▶ 风控 ──▶ 搜索索引 ──▶ 离线数仓
总耗时 ≈ 四个下游耗时之和
任一下游变慢或不可用 → 下单流程被拖住
```

调用链越来越长，任何一个下游抖动都会反噬下单主流程。把"通知下游"这件事从主流程里摘出来，交给一个中间系统暂存、下游自己按节奏取——这就是消息队列要解决的问题。Kafka 是其中一种实现，它的特别之处在于：消息发出去之后**不会消失**，而是按时间保留，谁想读、什么时候读、读多少遍，都由读的人决定。

引入 Kafka 之后，场景变成：

```text
订单服务 ──发布「交易流水」──▶ Kafka ──▶ 实时对账（独立读取一份）
                                  ├──▶ 风控（独立读取一份）
                                  ├──▶ 搜索索引（独立读取一份）
                                  └──▶ 离线数仓（独立读取一份）
```

四个下游各读各的，互不干扰；任何一方处理慢或临时挂掉，都不影响其他三方，也不影响订单服务本身。"独立读取一份"在 Kafka 里怎么实现，§2.3 的消费组会给出答案。

> 本场景只用于演示 Kafka 的消息流能力（多订阅、回放、顺序、吞吐），不涉及对账或风控的业务正确性设计。

### 1.2 这个场景的三个难题

把 1.1 的场景拆开，会发现它同时压着三个难题：

**难题一：多方订阅。** 四个下游都要**完整的一份**，而不是把一条消息分给四个人（那种叫竞争消费，[RabbitMQ 指南 §2.2](./rabbitmq-spring-boot-guide.md#22-一对多路由) 讲过）。同步调用做不到"发一次、四方各拿一份还互不干扰"。

**难题二：历史回放。** 第五个下游「用户画像」上线时，需要**从头消费全量历史流水**重建画像。同步调用无法回答"过去一个月发生了什么"——消息发出去就没了；只有保留历史消息的平台才支持回放。

**难题三：高吞吐。** 大促期间交易流水峰值每秒数万条。接收端必须"吞得下、存得住、扛得动"，让下游按自己的速度消化。

Kafka 对这三个难题各有对应机制，§2 会逐一展开：

| 难题     | Kafka 的机制                           | 详见       |
| -------- | -------------------------------------- | ---------- |
| 多方订阅 | 消费组：每组独立读取完整一份           | §2.3       |
| 历史回放 | 消息消费后不删除 + 消费组自己记 offset | §2.3、§5.5 |
| 高吞吐   | 顺序追加写 + 分区并行                  | §2.4、§5.3 |

### 1.3 Kafka 解决什么，不解决什么

- **异步解耦**：生产者不需要知道消费者的地址和实现。
- **削峰填谷**：突发流量先进 Topic，下游按自己的速度消费。
- **多方订阅 + 回放**：多个消费组独立读取同一份数据；新消费组可以回头重读历史。
- **水平扩展**：分区越多，组内并行消费能力越强。

Kafka 不保证业务自动正确：消息可能重复投递（§6.5、§7.3），业务要以「**至少一次**」为现实前提——消息不能轻易丢，但消费者必须容忍重复。

Kafka 也不是所有异步场景的默认答案：

| 场景                             | 更合适的选择          |
| -------------------------------- | --------------------- |
| 同一进程内的轻量解耦             | Spring 事件、方法调用 |
| 请求必须立即得到业务结果         | 同步 HTTP/RPC         |
| 单条可靠投递、复杂路由、延迟消息 | RabbitMQ              |
| 海量数据管道、多方订阅、历史回放 | Kafka                 |

---

## 2. 日志模型：一条消息的完整旅程

### 2.1 四个核心概念：从服务器到行号

理解 Kafka 只需要四个词，从大到小串起来：

**Broker（服务器）**：Kafka 的服务进程，负责存消息、收发消息。本地学习环境只有一个 Broker；生产环境是多 Broker 集群（§6.3 的副本机制依赖多 Broker）。

**Topic（主题）**：消息的逻辑分类，相当于"一类日志的名字"。本指南的 `trade-flow` 就是一个 Topic——所有交易流水都归到这个名字下。Topic 只负责分类，不直接存数据。

**Partition（分区）**：一个 Topic 的数据被切成多份，每份就是一个分区。

为什么要切？一本日志只能一个人从头到尾顺序写、顺序读；拆成 3 本，就能 3 个抄写员**同时写**、3 个读者**同时读**——这就是 Kafka 并行的来源。分区是 Kafka 真正存数据、做并行的最小单位：

```text
Topic: trade-flow（逻辑名称，不直接存数据）
  ├── Partition 0：[msg][msg][msg]...   ← 独立存储、独立读写
  ├── Partition 1：[msg][msg][msg]...   ← 3 个分区 = 最多 3 路并行
  └── Partition 2：[msg][msg][msg]...
```

**offset（偏移量）**：每个分区**各自**给消息编号，从 0 开始递增——相当于每本日志自己的行号，而不是整个 Topic 的全局编号。"读到 Partition 1 的 offset 37"就是"读到第 2 本日志的第 38 行"：

```text
Partition 0：offset  0       1       2       3
            [msg A] [msg B] [msg C] [msg D] ...
                                     ↑ 新消息追加到末尾

Partition 1：offset  0       1       2       ← 各自从 0 开始，不是全局编号
            [msg E] [msg F] [msg G] ...
```

因为只能往末尾追加、不能往中间插，offset 才会严格递增，写入也才是纯顺序写——这是 §2.4 高吞吐的根源。

四个概念串起来：**Broker 存 Topic；Topic 切成多个 Partition 以便并行读写；每个 Partition 内部消息按 offset 顺序编号，新消息只追加到末尾。**

### 2.2 一条消息的旅程

概念就位，看一条交易流水消息从发出到被消费的完整过程。订单服务完成数据库事务后，发布一条消息到 `trade-flow`：

```text
订单服务（生产者）
  │  ① 指定 Topic: trade-flow + Key: order-1001
  ▼
Kafka
  │  ② 按 key 哈希：hash("order-1001") % 3 = 1 → 进 Partition 1
  ▼
Partition 1
  │  ③ 消息追加到末尾，拿到 offset 37
  ▼
「实时对账」的消费者
     ④ 从自己记录的 offset 位置读到这条消息
     ⑤ 处理完成后提交 offset 38（书签前移）
```

每一步都在用 §2.1 的概念：①② 决定消息**进哪个分区**（key 的作用 §5.1 展开），③ 是日志模型的"只追加"，④⑤ 是消费组自己记书签（§2.3 展开）。

### 2.3 消费组：多方订阅与回放

§2.2 里"实时对账"读消息时，风控、搜索索引、离线数仓也在读**同一个 Topic**。它们怎么互不干扰？答案是 **Consumer Group（消费组）**：

- 一组共同消费某个 Topic 的消费者，叫一个消费组；
- **每个消费组自己记一份 offset**——组 A 读到 100，不影响组 B 从头开始读；
- 组内分摊分区：一个分区同一时刻只被组内一个消费者读取（§5.3 展开）。

```text
Topic: trade-flow
─────────────────────────────────────────────
消费组「实时对账」  自己记 offset → 各自推进，互不干扰
消费组「风控」      自己记 offset → 处理慢也不拖累别人
消费组「搜索索引」  自己记 offset
消费组「离线数仓」  自己记 offset
─────────────────────────────────────────────
新消费组「用户画像」上线：offset 从 0 开始 → 回放全部历史流水
```

§1.2 的两个难题在这里同时被解决：**多方订阅** = 多个消费组各读一份；**历史回放** = 新消费组把 offset 拨回 0 从头读。完整的 offset 演示见 §5.5。

### 2.4 为什么"日志"是理解 Kafka 的钥匙

回头看 §2.1 的分区内部结构：消息只能**追加到末尾**，不修改、不删除已有消息（按保留期统一过期）。这就是"分布式提交日志"——Kafka 的底层存储模型像**只追加的日志文件**。

这个模型推导出 Kafka 的三大能力，正好对应 §1.2 的三个难题：

| 能力           | 怎么从日志模型推导                            | 解决的难题 |
| -------------- | --------------------------------------------- | ---------- |
| 吞吐高         | 顺序追加写是最快的磁盘操作之一，无需随机寻址  | 高吞吐     |
| 可回放         | 消息消费后不删除，offset 可以拨回任意位置重读 | 历史回放   |
| 多订阅互不干扰 | offset 由每个消费组自己记录，组间天然隔离     | 多方订阅   |

一句话总结本章主线：**Kafka 的核心不是"队列"，而是"可回放的分布式提交日志"**——吞吐、顺序、回放、多订阅者，全都从日志结构推导出来。后面章节讲的所有机制（分区与顺序 §5、可靠性 §6、offset 提交 §7），都是这条主线的展开。

### 2.5 对照 RabbitMQ 的队列模型

| 维度     | RabbitMQ              | Kafka                            |
| -------- | --------------------- | -------------------------------- |
| 消息去哪 | Queue                 | Topic（逻辑）→ Partition（物理） |
| 消费语义 | 消费后删除            | 消费后保留，按保留期过期         |
| 谁来定位 | 队列总是从头给        | 每个消费组自己记 offset，可回拨  |
| 并行扩容 | 多消费者抢一个队列    | 分区数 = 组内最大并行度          |
| 多方订阅 | 多队列绑一个 Exchange | 多 Consumer Group 读同一 Topic   |

> 这张对照表的完整版（含吞吐、路由、确认机制对比）见微服务指南 [§9.5 RabbitMQ vs Kafka](spring-cloud-microservices-guide.md#95-rabbitmq-vs-kafka为什么很多团队两个都用)；本指南 §8 从日志模型视角补充选型决策。

---

## 3. 启动本地 Kafka

### 3.1 KRaft：不再需要 Zookeeper

Kafka 3.x 引入了 **KRaft** 模式：Kafka 用自己的 Raft 协议管理元数据，**不再依赖 Zookeeper**。Kafka 3.7 的 KRaft 已是生产可用状态，单机学习环境只需要一个"身兼 broker 和 controller 两职"的容器。本节配置与微服务指南 §9.2 基线一致；老教程里的 Zookeeper 配置可以全部忽略。

### 3.2 Compose 启动单节点 Kafka

下面的 Compose 文件仅用于本地学习。生产环境不要使用固定 `CLUSTER_ID`，也不要单副本部署。

> **完整示例，尚未验证**：本轮按要求未启动 Docker。镜像标签、环境变量和 CLI 命令路径需要在实际环境中验证。

```yaml
# compose.yaml
services:
  kafka:
    image: apache/kafka:3.7.0
    container_name: kafka
    ports:
      - "9092:9092" # 客户端连接端口
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller # 一个节点兼任 broker 和 controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1 # 单节点环境，副本数只能为 1
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      CLUSTER_ID: MkU3OEVBNTcwNTJENDM2Qk
```

启动命令：

```powershell
docker compose up -d kafka
docker compose ps kafka
```

第一条启动，第二条查看状态（STATE 应为 Up）。应用连接使用 `PLAINTEXT://localhost:9092`。

### 3.3 验证：建 Topic、控制台收发

进入容器用 Kafka 自带的 CLI 验证环境。先创建一个 3 分区的 Topic（分区数的选择在 §5.3 展开）：

```powershell
docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --create --topic trade-flow --partitions 3 --replication-factor 1
```

控制台生产者（输入即发送，Ctrl+C 退出）与控制台消费者（从头读）：

```powershell
docker exec -it kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic trade-flow
docker exec -it kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic trade-flow --from-beginning
```

预期效果：在生产者终端输入一行文本，消费者终端立即打印出来。

---

## 4. 在 Spring Boot 中完成第一次收发

本节给出一组可以组成最小应用的代码：发布一条交易流水 JSON 消息，监听器消费后打印日志。序列化沿用微服务指南 §9 的约定：**value 用 JSON 字符串**，任何语言都能解析，排查问题时控制台直接可读。

> **完整示例，尚未验证**：代码以 Java 17、Spring Boot 3.5.14 和 spring-kafka 3.3.16 为目标，但本轮未解析依赖、编译或连接 Kafka。运行前应启动 Kafka 并执行 `mvnw spring-boot:run`，根据实际输出确认。

### 4.1 Maven 依赖

`spring-kafka` 会引入 kafka-clients；版本由 Spring Boot 3.5.14 的 BOM 统一管理（当前为 3.3.16），不要为它单独指定版本。

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>
</dependencies>
```

### 4.2 连接与收发配置

```yaml
# src/main/resources/application.yml
spring:
  kafka:
    bootstrap-servers: localhost:9092 # Broker 地址，多个用逗号分隔
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      # acks 与幂等生产者默认已是最安全的配置（Kafka 3.0 起），§6 展开
    consumer:
      group-id: reconciliation-group # 消费组：实时对账
      auto-offset-reset: earliest # 无已提交 offset 时从最早消息读起（回放的基础，§5.5）
      enable-auto-commit: false # 关闭自动提交，改用手动提交（§7）
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
```

`auto-offset-reset` 只在消费组**没有已提交 offset** 时生效：`earliest` 从头读，`latest` 只读新消息。新消费组想要回放历史，就用 `earliest`（§5.5）。

### 4.3 定义消息契约

事件 ID 用于幂等去重（§7.3），订单号是业务标识，`occurredAt` 使用 ISO-8601 字符串以保持示例的序列化依赖简单。

```java
package com.example.kafkademo.messaging;

public record TradeFlowEvent(
        String eventId,
        String orderId,
        String amount,
        String status,
        String occurredAt
) {
}
```

集中管理 Topic 名，避免生产者与消费者各自拼写字符串。

```java
package com.example.kafkademo.messaging;

public final class MessagingConstants {

    /** 交易流水 Topic */
    public static final String TRADE_FLOW_TOPIC = "trade-flow";

    /** 死信 Topic：消费失败重试耗尽后转投这里（§7.4） */
    public static final String TRADE_FLOW_DLT = "trade-flow.DLT";

    private MessagingConstants() {
    }
}
```

### 4.4 发送消息：KafkaTemplate

Kafka 客户端只认字节，示例中生产者负责把对象序列化为 JSON 字符串：

```java
package com.example.kafkademo.messaging;

import com.alibaba.fastjson2.JSON; // 任意 JSON 库均可，此处以 fastjson2 为例
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class TradeFlowPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public TradeFlowPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(TradeFlowEvent event) {
        String json = JSON.toJSONString(event);
        // send(topic, key, value)：key 决定分区——同一订单的流水总是进同一分区，保序（§5.1）
        kafkaTemplate.send(
                MessagingConstants.TRADE_FLOW_TOPIC,
                event.orderId(),
                json
        );
    }
}
```

> **key 的作用**：`send(topic, key, value)` 中的 key 决定消息进哪个分区（`hash(key) % 分区数`）。用订单号做 key，就能保证"同一订单的流水在分区内严格有序"——Kafka 只保证**分区内有序**，不保证全局有序（§5.2）。

### 4.5 接收消息：@KafkaListener

手动提交 offset 需要把容器工厂的 ack 模式设为 MANUAL（§7.2 详解两种提交方式的取舍）：

```java
package com.example.kafkademo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3); // 并发线程数，§5.3 解释为什么与分区数相关
        factory.getContainerProperties()
                .setAckMode(ContainerProperties.AckMode.MANUAL); // 处理成功后手动提交 offset
        return factory;
    }
}
```

消费者：

```java
package com.example.kafkademo.messaging;

import com.alibaba.fastjson2.JSON; // 任意 JSON 库均可，此处以 fastjson2 为例
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationListener {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationListener.class);

    @KafkaListener(topics = MessagingConstants.TRADE_FLOW_TOPIC, groupId = "reconciliation-group")
    public void onTradeFlow(ConsumerRecord<String, String> record, Acknowledgment ack) {
        TradeFlowEvent event = JSON.parseObject(record.value(), TradeFlowEvent.class);
        log.info("Reconcile trade flow: orderId={}, amount={}", event.orderId(), event.amount());
        ack.acknowledge(); // 处理成功 → 手动提交 offset（§7.2）
    }
}
```

> **手动 ack 的本质**：offset 就是"消费进度书签"。处理成功才提交 = 书签前移；处理失败不提交 = 书签不动，消息下次还会被读到。这和 RabbitMQ 的手动 ack 是同一思想，只是 RabbitMQ 确认的是"单条消息"，Kafka 提交的是"分区位置"。处理失败不提交的代价与兜底见 §7.4。

---

## 5. 顺序与并行：分区与消费者组

### 5.1 key 决定分区

`send(topic, key, value)` 中的 key 是分区的**决定因素**：Kafka 计算 `hash(key) % 分区数`，把消息放进对应分区。同一个 key 的所有消息永远进同一个分区：

```text
Topic: trade-flow（3 个分区）

order-1001 → hash % 3 = 1 → Partition 1：唯一归属
order-1002 → hash % 3 = 0 → Partition 0：唯一归属
order-1001 → hash % 3 = 1 → Partition 1：与上一条同分区，追加在其后
```

**有 key**：同一 key 严格保序。**无 key**：Kafka 3.x 默认使用粘性分区器，尽量把消息批量粘在同一个分区上，减少批量发送的拆散，但不保证顺序。

选择 key 的准则：

- 需要某类消息保序（如同一订单的流水），就用能标识该类消息的字段做 key；
- key 的分布要尽量均匀，否则会出现"热分区"——流量集中到少数分区，并行度被浪费。

### 5.2 分区内有序，全局无序

Kafka 只保证**分区内有序**，不保证全局有序：

```text
Partition 0: [A1][A2][A3]       ← 分区内有序
Partition 1: [B1][B2]           ← 分区内有序
Partition 2: [C1][C2][C3]       ← 分区内有序

跨分区：A1 和 B1 谁先到？没有保证——三个分区并行写入、并行消费
```

只要场景能接受"同一订单的流水有序"而不是"所有流水全局有序"，3 个分区就能并行吞吐；要求全局有序时只能退化为单分区，牺牲并行度。这是 Kafka 使用中最高频的取舍，也是面试高频考点。

### 5.3 分区数是组内并行上限

一个分区同一时刻只能被**组内一个**消费者读取。因此消费组的并行度上限就是它订阅的分区数：

```text
Topic: trade-flow（3 个分区）+ 消费组 A（4 个消费者）

Partition 0 → Consumer A1
Partition 1 → Consumer A2
Partition 2 → Consumer A3
Consumer A4 → 空闲（没有分区可分）

规则：3 个分区最多支撑 3 个消费者并行；想更快？加分区
```

两个推论：

- **消费者比分区多没有用**：多余消费者空闲。`setConcurrency(3)` 与 3 个分区正好匹配。
- **分区数创建后只增不减**：分区是存储单元，缩减分区要重建 Topic 并迁移数据。创建 Topic 时就要想清楚量级——这是"分区分多少个"被称为 Kafka 第一难题的原因。

### 5.4 再均衡：成员变化的代价

组内成员**加入或离开**（应用重启、扩容缩容、心跳超时被踢出）时，Kafka 触发**再均衡（rebalance）**：组暂停消费，把分区在成员之间重新分配，再恢复消费：

```text
再均衡前：Consumer A1 → P0/P1, Consumer A2 → P2
    │  Consumer A2 宕机
    ▼
再均衡：暂停整个消费组 → 重新分配 → Consumer A1 → P0/P1/P2 → 恢复
```

再均衡期间**整个组暂停消费**，高频再均衡（比如消费者频繁重启、处理超时导致心跳丢失）会明显拉低吞吐。生产上的常见对策是保持实例数稳定、为慢消费单独调大轮询间隔，而不是频繁扩缩容。

### 5.5 四个下游与新下游回放

回到交易流水场景，多订阅与回放是同一个机制的两面——**每个消费组独立记 offset**：

```text
Topic: trade-flow
─────────────────────────────────────────────────────────────
Partition 0: [msg0][msg1][msg2] ...
Partition 1: [msg0][msg1][msg2] ...
Partition 2: [msg0][msg1][msg2] ...
─────────────────────────────────────────────────────────────
消费组「实时对账」  offset: P0=102, P1=99, P2=120   ← 各自推进，互不干扰
消费组「风控」      offset: P0=88,  P1=79, P2=101
消费组「搜索索引」  offset: P0=95,  P1=90, P2=110
消费组「离线数仓」  offset: P0=60,  P1=55, P2=80    ← 处理慢，不影响其他组

新消费组「用户画像」offset: P0=0,   P1=0,  P2=0     ← 从头回放全部历史
```

「用户画像」组能从头读，靠的是两点：新组没有已提交 offset，且配置了 `auto-offset-reset: earliest`（§4.2）。已有消费组想回放，则用 Kafka 自带的 `kafka-consumer-groups.sh` 把 offset 回拨到指定位置——这正是日志模型"消息消费后不删除"的价值：**算错了，回放重跑**。

---

## 6. 可靠性：从 acks 到幂等生产者

### 6.1 生产者的可靠性链路

消息从业务代码到"真正落盘"要经过多段，每一段都可能失败：

```text
业务数据库
   │ ① 业务提交与发送之间可能失败
   ▼
Producer ──发送──> Broker Leader
   │                │ ② Leader 落盘之前宕机，消息可能丢
   │                ▼
   │            ISR 副本（follower 同步）
   │                │ ③ 副本没跟上就确认，Leader 宕机后消息可能丢
   ▼                ▼
收到 ack ────────── 生产者认为发送成功
```

本节解决 ②③ 两个环节：**确认策略（acks）** 与 **副本同步（ISR）**。环节 ① 与 RabbitMQ 指南 §5.2 讨论的"数据库提交与消息发送的先后"是同一个问题，处理思想相同（Transactional Outbox）。

### 6.2 acks 三档：0、1、all

`acks` 决定"Broker 何时给生产者回确认"：

| acks 值 | 含义                     | 风险                                |
| ------- | ------------------------ | ----------------------------------- |
| 0       | 发出即成功，不等任何确认 | Broker 收到没有都不知道，最容易丢   |
| 1       | Leader 落盘后确认        | Leader 宕机且副本未同步，消息可能丢 |
| all     | 所有 ISR 副本落盘后确认  | 延迟略高，但最安全                  |

```text
acks=1 的丢失场景：
  Producer ──msg──> Leader（落盘，回 ack）
                       │
                       ├─ follower 还没同步完成
                       ▼
                   Leader 宕机，msg 随 Leader 一起丢失
```

> 生产环境对"不能丢"的数据一律用 `acks=all`。好消息是：Kafka 3.0 起启用幂等生产者后 `acks` 默认就是 `all`（§6.4），默认配置已经站在最安全档。

### 6.3 ISR 与 min.insync.replicas

**ISR（In-Sync Replicas）** 是与 Leader 保持同步的副本集合。副本落后太多或失联，会被踢出 ISR；追平后重新加入。`acks=all` 只等 **ISR 内**的副本确认，而不是所有副本——这避免了"慢副本拖死整个写入"。

**min.insync.replicas** 是"最少几个副本确认才算成功"的下限：

```text
副本数 3，min.insync.replicas = 2：
  Leader + 至少 1 个 follower 确认 → 写入成功
  最多允许 1 台 Broker 宕机

副本数 3，min.insync.replicas = 3：
  全部副本确认才算成功 → 任何一台宕机，写入直接失败（可用性换一致性）
```

两个参数的组合是可用性与一致性的经典权衡：下限过低，宕机时可能"确认了但没落够"；下限过高，宕机时写入直接报错。单节点学习环境 `replication-factor=1`，这些参数无实际意义；但它们是多 Broker 生产集群的必配项。

### 6.4 重试的代价与幂等生产者

网络抖动时，生产者自动重试是必要的；但重试带来新问题——**Broker 可能已经写入成功，只是确认丢了**，重发就会把同一条消息写两遍：

```text
Producer ──msg──> Leader（写入成功）
                   │
                   ├─ ack 在网络中丢失
                   ▼
Producer 收不到确认 → 重发 msg → Leader 再写一次
结果：同一条消息在分区里出现两份
```

**幂等生产者**解决这个问题：Broker 为每个生产者分配 `producer ID`，消息带自增**序列号**。Broker 收到序列号重复的消息时，只回确认、不再写入：

```text
消息序列号：msg(seq=7) → 写入成功，ack 丢失
重发 msg(seq=7) → Broker 发现 seq=7 已存在 → 只回 ack，不重复写入
```

> **版本说明（KIP-679）**：Kafka 3.0 起 `enable.idempotence` 默认 `true`，且启用后 `acks` 默认 `all`、`retries` 默认 `Integer.MAX_VALUE`。因此 Kafka 3.7.0 + spring-kafka 3.3.x 的默认生产配置已经是"最安全档"，旧教程里手工配置 `enable-idempotence: true` 的写法已不再是必需项。

幂等有一个边界：**只在单次生产者会话内有效**。生产者进程重启后拿到新的 `producer ID`，重发旧消息时序列号重新计数，Broker 无法识别跨会话重复。所以即使生产者幂等，"至少一次 + 消费端容忍重复"（§6.5、§7.3）仍然是业务的现实前提。

### 6.5 至少一次的边界

把生产侧的机制拼起来：

```text
acks=all + min.insync.replicas  → 消息落盘有保障（不丢）
幂等生产者                        → 单次会话内不重复（不重）
```

但跨会话重发、消费端"提交 offset 与业务落库之间崩溃"仍会制造重复（§7.2）。因此 Kafka 业务上的现实语义是**至少一次**：消息不能轻易丢，消费者必须容忍重复。要达成跨生产者、跨消费的端到端 exactly-once，需要 Kafka 事务（KIP-447），这是独立大主题，§9 延伸阅读导航。

---

## 7. 消费端：offset 提交、重复消费与死信

### 7.1 offset 提交：位置记录在哪里

消费组每处理完一批消息，就把"读到哪了"提交给 Kafka，存在内部 Topic `__consumer_offsets` 里：

```text
消费组「实时对账」提交的 offset：
  trade-flow / Partition 0 → 102
  trade-flow / Partition 1 → 99
  trade-flow / Partition 2 → 120

组内任何一个消费者重启或再均衡后，就从这些位置继续读
```

关键认知：**提交 ≠ 处理完成**。提交的是"下次从哪开始读"，提交时机决定了崩溃后消息重复多少。

### 7.2 自动提交 vs 手动提交

| 维度     | 自动提交（默认）                                   | 手动提交（MANUAL）                     |
| -------- | -------------------------------------------------- | -------------------------------------- |
| 机制     | 每隔 `auto-commit-interval`（默认 5 秒）提交一次   | 业务处理成功后调用 `ack.acknowledge()` |
| 重复窗口 | 大：崩溃后从上次提交位置重读，期间处理过的消息重来 | 小：只重复"已处理但未提交"的最后几条   |
| 适用场景 | 日志收集等重复可接受的场景                         | 交易、对账等希望重复越少越好的场景     |

手动提交时，`MANUAL` 与 `MANUAL_IMMEDIATE` 的差别：`MANUAL` 把本次轮询的所有确认攒到一批提交（吞吐略好），`MANUAL_IMMEDIATE` 每条处理完立即提交（重复窗口更小）。§4.5 的工厂用的就是 `MANUAL`。

```text
手动提交下的重复场景：
  处理 msg(offset=37) 成功 → 提交前进程崩溃
  重启后从 offset=37 重新读 → 同一条消息处理两遍
```

所以"手动提交"只是把重复窗口压到最小，无法消灭重复。**业务必须能容忍重复**（§7.3）。

### 7.3 容忍重复：幂等键

至少一次语义下，重复是常态而不是异常。消费者用 `eventId` 做幂等键：同一条消息处理过就不再处理：

```java
package com.example.kafkademo.messaging;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationListener {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationListener.class);

    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    @KafkaListener(topics = MessagingConstants.TRADE_FLOW_TOPIC, groupId = "reconciliation-group")
    public void onTradeFlow(ConsumerRecord<String, String> record, Acknowledgment ack) {
        TradeFlowEvent event = JSON.parseObject(record.value(), TradeFlowEvent.class);
        if (!processedEventIds.add(event.eventId())) {
            log.info("Ignore duplicated trade flow: eventId={}", event.eventId());
            ack.acknowledge();
            return;
        }
        log.info("Reconcile trade flow: orderId={}, amount={}", event.orderId(), event.amount());
        ack.acknowledge();
    }
}
```

这里用内存集合演示，只能用于单进程学习示例；生产环境应使用数据库唯一约束或幂等记录表，并把"幂等记录 + 业务更新"放在同一个本地数据库事务中（思想与 RabbitMQ 指南 §4.7 一致）。

### 7.4 失败兜底：重试与死信主题

手动提交带来一个新问题：**处理失败不提交 → offset 卡住 → 同一分区的后续消息全部阻塞**（Kafka 按分区顺序消费）。而参数错误、数据缺失这类永久故障，重试多少次都不会成功。

生产标准做法：**有界重试 + 死信主题**。在 §4.5 的工厂上追加错误处理器：

> **完整示例，尚未验证**：以下代码在 §4.5 工厂基础上追加失败兜底，依赖 spring-kafka 3.3.16 的 `DefaultErrorHandler` 与 `DeadLetterPublishingRecoverer`，本轮未编译运行。

```java
package com.example.kafkademo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3);
        factory.getContainerProperties()
                .setAckMode(ContainerProperties.AckMode.MANUAL);

        // 失败兜底：重试 3 次（间隔 1 秒），仍失败转投死信 Topic，offset 正常提交
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        factory.setCommonErrorHandler(new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3)));
        return factory;
    }
}
```

对应的监听器不再吞掉异常，而是让业务失败向上抛出：

```java
@KafkaListener(topics = MessagingConstants.TRADE_FLOW_TOPIC, groupId = "reconciliation-group")
public void onTradeFlow(ConsumerRecord<String, String> record, Acknowledgment ack) {
    TradeFlowEvent event = JSON.parseObject(record.value(), TradeFlowEvent.class);
    reconcile(event);      // 业务处理，失败抛异常
    ack.acknowledge();     // 处理成功 → 手动提交 offset
}
```

处理流程变成：

```text
消费 msg
  ├─ 处理成功 → ack → offset 前移
  └─ 处理失败 → DefaultErrorHandler 重试 3 次（每次间隔 1 秒）
                 ├─ 重试成功 → 继续
                 └─ 仍失败 → DeadLetterPublishingRecoverer 转投 trade-flow.DLT
                              → 原消息 offset 提交 → 分区消费不阻塞
```

`DeadLetterPublishingRecoverer` 默认把失败消息发到"原 Topic 名 + `.DLT`"的主题（即 §4.3 声明的 `trade-flow.DLT`），死信消息保留原始消息头和分区信息，便于排查。

### 7.5 死信不是垃圾桶

- 死信主题要**监控积压**：有死信说明有真实故障，越积越多说明故障在扩大。
- 保留失败原因：业务异常、消息内容都要能查到，否则无法定位。
- 审核后重放：修复代码后，把死信消息审核后导回原 Topic，而不是全量倒回——永久错误会再次制造风暴。
- 死信本身也是普通 Topic，可以配一个低并发的人工处理消费组，或直接查库分析。

### 7.6 配置速查

| 配置项                        | 默认值               | 作用                               |
| ----------------------------- | -------------------- | ---------------------------------- |
| `producer.enable.idempotence` | true（Kafka 3.0 起） | 幂等去重，防重试重复               |
| `producer.acks`               | all（幂等开启时）    | 等所有 ISR 副本确认                |
| `producer.retries`            | Integer.MAX_VALUE    | 幂等开启时默认无限重试，由幂等兜底 |
| `consumer.enable-auto-commit` | true                 | 自动提交；手动提交时设为 false     |
| `consumer.auto-offset-reset`  | latest               | 无 offset 时 earliest 从头读       |
| `listener.AckMode`            | BATCH（容器默认）    | MANUAL = 业务成功后手动提交        |
| `DefaultErrorHandler`         | 无 recoverer         | 有界重试 + 死信转投兜底            |

---

## 8. Kafka vs RabbitMQ 选型

### 8.1 日志模型 vs 队列模型

读完两边指南后，两者的差异可以用四个增量维度概括（完整对照表见微服务指南 [§9.5](spring-cloud-microservices-guide.md#95-rabbitmq-vs-kafka为什么很多团队两个都用)）：

| 维度         | RabbitMQ（队列模型）              | Kafka（日志模型）               |
| ------------ | --------------------------------- | ------------------------------- |
| 消息生命周期 | 消费即删                          | 保留期内可反复消费（回放）      |
| 订阅模型     | 队列 + 绑定，一份消息给一个消费者 | 消费组独立 offset，多组各读一份 |
| 顺序与并行   | 队列无序，多消费者抢占            | 分区内有序，分区数 = 并行上限   |
| 确认模型     | 单条 ack/nack，语义丰富           | 按 offset 批量提交              |

这两个模型不是谁替代谁，而是服务不同的目标：队列模型把"可靠送达一条消息"做到极致，日志模型把"让数据流过多方且可重放"做到极致。

### 8.2 选型决策表

| 场景                             | 选择        | 理由                          |
| -------------------------------- | ----------- | ----------------------------- |
| 订单通知、任务派发、延迟消息     | RabbitMQ    | 单条可靠投递、延迟插件成熟    |
| 日志收集、行为埋点、数据同步管道 | Kafka       | 高吞吐、多方订阅、可回放      |
| 新下游需要全量历史数据           | Kafka       | 新消费组 offset 拨回 0 即回放 |
| 消息需要复杂路由规则             | RabbitMQ    | Exchange + Binding 路由灵活   |
| 同一进程内解耦                   | Spring 事件 | 无网络开销，最简单            |
| 同一订单/实体的消息要严格有序    | Kafka       | 用实体 ID 做 key，分区内保序  |

一句话总结：**业务事件用 RabbitMQ，数据管道用 Kafka**——要的是"必须送到"，选队列模型；要的是"量大、要分析、可回放"，选日志模型。

### 8.3 两个都用的常见组合

真实生产系统里两个 MQ 经常各司其职，甚至首尾相接成一条管道——海量原始数据先进 Kafka（吞得下、可回放、算错了重跑），计算服务过滤匹配后，把需要"可靠送达用户"的结果转投 RabbitMQ。这条"数据接力管道"的完整图示见微服务指南 [§9.5](spring-cloud-microservices-guide.md#95-rabbitmq-vs-kafka为什么很多团队两个都用)。

---

## 9. 延伸阅读与参考资料

### 9.1 延伸阅读

本章内容是重要但非本指南主线的主题，学有余力或工作中遇到时再深入：

- **三步接入速成**：微服务指南 [§9 消息队列 — Kafka](spring-cloud-microservices-guide.md#9-消息队列--kafka)，在「电商三服务」场景里快速跑通 Kafka，含完整对照表。
- **Kafka 上 k8s**：Kubernetes 指南 [§12.3 RabbitMQ、Kafka 上 k8s](../Kubernetes/kubernetes-guide.md#123-rabbitmqkafka-上-k8s)——有状态负载、StatefulSet 与 Strimzi/Helm 起步建议。
- **另一端的深度**：[RabbitMQ 与 Spring Boot AMQP 渐进式指南](./rabbitmq-spring-boot-guide.md)，选型对照的另一半。
- **Kafka Streams / KSQL**：基于 Topic 的流式计算（过滤、聚合、窗口），是独立的大主题。
- **Kafka Connect**：用连接器生态把数据库、文件系统与 Topic 对接（如 CDC 数据同步）。
- **Spring Cloud Stream**：消息中间件之上的统一编程抽象层，可切换底层 MQ 实现。
- **事务 exactly-once**：KIP-447 引入的跨分区事务，用于"读-处理-写"链条的端到端精确一次语义。
- **多 Broker 集群**：§6.3 的副本机制在单机环境无法验证，集群搭建、分区再分配、容量规划是运维主题。

### 9.2 参考资料

- [Spring for Apache Kafka Reference（3.3.x）](https://docs.spring.io/spring-kafka/reference/)
- [Spring Boot Reference：Messaging / Kafka](https://docs.spring.io/spring-boot/reference/messaging/kafka.html)
- [Spring Boot 3.5.14 依赖坐标附录（spring-kafka 3.3.16 出处）](https://docs.spring.io/spring-boot/3.5.14/appendix/dependency-versions/coordinates.html)
- [Apache Kafka 3.7 Documentation（KRaft、producer/consumer 配置）](https://kafka.apache.org/37/documentation.html)
- [KIP-679：Producer will enable the strongest delivery guarantee by default](https://cwiki.apache.org/confluence/display/KAFKA/KIP-679%3A+Producer+will+enable+the+strongest+delivery+guarantee+by+default)
- [KIP-500：Replace ZooKeeper with a Self-Managed Metadata Quorum（KRaft）](https://cwiki.apache.org/confluence/display/KAFKA/KIP-500%3A+Replace+ZooKeeper+with+a+Self-Managed+Metadata+Quorum)
