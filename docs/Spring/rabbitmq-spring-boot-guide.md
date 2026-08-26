# RabbitMQ 与 Spring Boot AMQP 渐进式指南

> 面向读者：已经掌握 Spring Boot、Maven 和基本 Java 开发，希望开始使用消息队列的开发者。  
> 学习结果：理解 RabbitMQ 的核心路由模型，能够使用 `spring-boot-starter-amqp` 声明拓扑、发送和消费 JSON 消息，并知道如何处理确认、重试、死信和重复消费。  
> 适用版本：Java 17、Spring Boot 4.0.6、Spring AMQP 4.0.x、RabbitMQ 4.x。

这篇指南的组织主线是：**RabbitMQ 的可靠性不是一个开关，而是一条由生产者、Broker 和消费者共同完成的链路。**

## 范围

本文覆盖 RabbitMQ 的核心模型、单机开发环境、Spring Boot 中的消息收发以及常用可靠性措施。示例使用"订单创建事件"，通过 AMQP 0-9-1 使用经典队列和 Topic Exchange。集群部署、Quorum Queue 深入运维、跨机房容灾、容量压测、RabbitMQ Streams 和 Spring Cloud Stream 不在本文范围内。

RabbitMQ 用于跨进程、可持久化的异步通信；如果发布者和监听者处于同一个 Spring 应用，且不需要跨进程持久化与独立重试，优先考虑 [Spring 事件机制指南](./spring-event-guide.md)。

## 目录

1. [为什么需要 RabbitMQ](#1-为什么需要-rabbitmq)
2. [消息如何从生产者到达消费者](#2-消息如何从生产者到达消费者)
3. [启动本地 RabbitMQ](#3-启动本地-rabbitmq)
4. [在 Spring Boot 中完成第一次收发](#4-在-spring-boot-中完成第一次收发)
5. [构建可靠投递链路](#5-构建可靠投递链路)
6. [吞吐量与积压](#6-吞吐量与积压)
7. [参考资料](#7-参考资料)

---

## 1. 为什么需要 RabbitMQ

### 1.1 同步调用的限制

假设订单服务创建订单后，需要通知库存、积分和邮件服务。直接同步调用会形成一条越来越长的调用链：

```text
客户端
  └─> 订单服务
        ├─> 库存服务
        ├─> 积分服务
        └─> 邮件服务

总耗时 ≈ 订单处理 + 三个下游调用
任一下游不可用，都可能拖慢或阻断下单
```

引入 RabbitMQ 后，订单服务只负责完成自己的事务并发布“订单已创建”消息。下游服务按照各自速度处理：

```text
订单服务 ──发布──> RabbitMQ ──投递──> 库存消费者
                         ├──────────> 积分消费者
                         └──────────> 邮件消费者
```

RabbitMQ 主要解决以下问题：

- **异步解耦**：生产者不需要知道消费者的地址和实现。
- **削峰填谷**：突发请求先进入队列，消费者按可承受速度处理。
- **故障隔离**：消费者短暂不可用时，消息可以在队列中等待。
- **独立扩缩容**：根据积压量调整消费者数量，而不必复制生产者。

### 1.2 RabbitMQ 不保证业务自动正确

消息进入 RabbitMQ 不等于业务完成。网络可能中断，消费者可能在处理后、确认前崩溃，同一消息也可能再次投递。因此业务通常应以“**至少一次**”为现实前提：消息不能轻易丢，但消费者必须容忍重复。

RabbitMQ 也不是所有异步场景的默认答案：

| 场景                             | 更合适的选择          |
| -------------------------------- | --------------------- |
| 同一进程内的轻量解耦             | Spring 事件、方法调用 |
| 请求必须立即得到业务结果         | 同步 HTTP/RPC         |
| 复杂路由、任务队列、业务事件     | RabbitMQ              |
| 超大规模日志流、长期事件流、回放 | Kafka 等日志型平台    |

---

## 2. 消息如何从生产者到达消费者

### 2.1 一条消息的完整旅程

先看一条具体消息从发出到被消费的完整过程。假设订单服务完成数据库事务后，发布一条 `order.created` 事件：

```text
订单服务
  │  目标交换机: commerce.events + Routing Key: order.created
  ▼
Exchange: commerce.events (Topic)
  │  已有 Binding（路由规则）: 拿 Routing Key order.created 去匹配路由规则：order.*
  ▼
Queue: order.created
  │  暂存消息，等待消费者
  ▼
库存消费者：取出消息，执行业务处理
```

逐步分解：

1. **生产者**发布消息，指定目标交换机 `commerce.events`，附带 Routing Key `order.created`
2. **交换机**收到消息，查找自己已有的绑定，看哪些 Binding 的 pattern 能匹配这个 Routing Key
3. **绑定** `order.*` 匹配了 `order.created`，交换机把消息送进该绑定指向的 `order.created`
4. **队列**暂存消息，等待消费者来取
5. **消费者**从队列取出消息，执行业务处理

RabbitMQ 中生产者不直接把消息塞进队列，而是发布给**交换机（Exchange）**，交换机根据**绑定（Binding）**和**路由键（Routing Key）**决定消息进入哪个**队列（Queue）**。

### 2.2 一对多路由

生产者只发送一次消息。交换机根据 Binding 将消息复制并投递到每个匹配的队列：

```text
                       Binding: order.*
                   ┌──────────────────────→ Queue: order.inventory
                   │                        → 库存消费者
Producer ──→ Exchange│  Binding: order.*
            (1 次)  ├──────────────────────→ Queue: order.points
                   │                        → 积分消费者
                   │  Binding: order.*
                   └──────────────────────→ Queue: order.email
                                            → 邮件消费者
```

订单服务只发布一次消息，三个消费者各自独立收到并处理。如果库存、积分、邮件都要独立收到事件，应为每类消费者建立独立队列，再将这些队列都绑定到同一个交换机。多个消费者共同监听**同一个队列**时是竞争消费，每条消息只会被其中一个消费者处理。

### 2.3 Connection 与 Channel

应用与 RabbitMQ 之间通过网络通信，这里涉及三个基础概念：

**Broker（消息代理）**：就是 RabbitMQ 服务器本身。它接收生产者发来的消息，暂存起来，再投递给消费者。

**Connection（连接）**：应用与 Broker 之间的 TCP 连接，相当于一条真实的网络通道。建立和断开都开销大，应当复用。

**Channel（通道）**：在同一个 TCP 连接内部划分出的**虚拟会话**。可以理解为"一条物理连接上开了多个独立的会话窗口"：

```text
类比：
  一家公司（应用）只有一条电话线（TCP Connection）接到客服中心（Broker）
  但多个员工（Channel）可以同时用分机（虚拟会话）各自通话
  挂断一个分机（关闭 Channel）不影响其他分机和电话线
```

```text
应用程序
    │
    ├─ TCP Connection（一条真实连接）
    │      ├─ Channel 1：发布消息
    │      ├─ Channel 2：消费队列 A
    │      └─ Channel 3：消费队列 B
    │
    └─ RabbitMQ Broker（服务器）
```

> Channel 不是线程池，也不是队列。它是复用同一 TCP 连接的虚拟会话。

### 2.4 五个核心概念

| 概念   | 职责           | 需要记住的边界                   |
| ------ | -------------- | -------------------------------- |
| 生产者 | 发布消息       | 通常只知道交换机和路由键         |
| 交换机 | 路由消息       | 不负责长期保存消息               |
| 绑定   | 定义路由规则   | 同一队列可以有多个绑定           |
| 队列   | 暂存消息       | 消费者从队列获取消息             |
| 消费者 | 处理并确认消息 | 失败策略决定重试、重回队列或死信 |

消息到达队列之前如果没有匹配的路由，默认可能被丢弃。后文会通过 `mandatory` 和 publisher return 让生产者发现这种情况。

### 2.5 四类交换机：同一场景下的行为对比

用同一条 `order.created` 消息，看四种交换机的路由行为差异：

**Direct**：Routing Key 必须完全匹配

```text
Exchange (Direct) ── Binding: order.created ──→ Queue A

消息 Routing Key = order.created → 精确匹配 → 进入 Queue A
消息 Routing Key = order.updated → 不匹配   → 消息被丢弃（或 Return）
```

**Topic**：按点分隔的模式匹配，`*` 匹配一个词段，`#` 匹配零个或多个词段

```text
Exchange (Topic) ── Binding: order.* ──→ Queue A

消息 Routing Key = order.created → * 匹配 created → 进入 Queue A
消息 Routing Key = order.updated → * 匹配 updated → 进入 Queue A
消息 Routing Key = payment.done → 不匹配 order.* → 不进入 Queue A
```

**Fanout**：忽略 Routing Key，广播到所有绑定队列

```text
Exchange (Fanout) ── Binding: (任意) ──→ Queue A
                  ── Binding: (任意) ──→ Queue B

消息 Routing Key = order.created → Queue A 和 Queue B 都收到
```

**Headers**：根据消息头属性匹配，不依赖 Routing Key

```text
Exchange (Headers) ── Binding: x-match=all, format=pdf ──→ Queue A

消息头 format=pdf, lang=zh → 匹配 → 进入 Queue A
消息头 format=csv           → 不匹配 → 不进入 Queue A
```

| 类型    | 路由规则                                                     | 常见用途                     |
| ------- | ------------------------------------------------------------ | ---------------------------- |
| Direct  | Routing Key 必须完全匹配                                     | 明确命令、单一分类           |
| Topic   | 按点分隔的模式匹配，`*` 匹配一个词段，`#` 匹配零个或多个词段 | 领域事件、多维分类           |
| Fanout  | 忽略 Routing Key，广播到所有绑定队列                         | 广播通知                     |
| Headers | 根据消息头匹配                                               | 路由条件不适合表达为字符串时 |

### 2.6 Routing Key 命名约定

本指南采用 `<领域>.<动作>` 格式，例如 `order.created`、`payment.completed`、`inventory.deducted`。这种命名在 Topic Exchange 中可以利用通配符灵活路由：

- `order.*` 匹配 `order.created`、`order.updated`，但不匹配 `order.line_item.added`
- `order.#` 匹配 `order.created`，也匹配 `order.line_item.added`（`#` 跨层级匹配）

---

## 3. 启动本地 RabbitMQ

下面的 Compose 文件仅用于本地学习。生产环境不要使用固定密码，也不要直接暴露管理端口。

> **完整示例，尚未验证**：本轮按要求未启动 Docker。镜像标签、端口和环境变量需要在实际环境中验证。

```yaml
# compose.yaml
services:
  rabbitmq:
    image: rabbitmq:4-management
    container_name: rabbitmq-local
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_USER:-app}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD:-local-dev-only}
    ports:
      - "5672:5672" # AMQP 客户端连接
      - "15672:15672" # Management UI
    volumes:
      - rabbitmq-data:/var/lib/rabbitmq

volumes:
  rabbitmq-data:
```

启动命令：

```powershell
docker compose up -d rabbitmq
docker compose ps rabbitmq
```

第一条启动，第二条状态查看

浏览器访问 `http://localhost:15672`，使用本地示例账号 `app / local-dev-only` 登录。应用连接使用的是 AMQP 端口 `5672`，而不是管理界面的 `15672`。

> RabbitMQ 默认的 `guest` 用户通常只允许从本机连接。容器和远程应用不要依赖 `guest / guest`，应创建独立用户并授予最小范围的 vhost 权限。

---

## 4. 在 Spring Boot 使用

本节给出一组可以组成最小应用的代码：HTTP 请求创建订单后发布一条订单事件，监听器消费后打印日志。

> **完整示例，尚未验证**：代码以 Java 17、Spring Boot 4.0.6 和 Spring AMQP 4.0.x 为目标，但本轮未解析依赖、编译或连接 RabbitMQ。运行前应启动 RabbitMQ 并执行 `mvnw spring-boot:run`，根据实际输出确认。

### 4.1 Maven 依赖

`spring-boot-starter-amqp` 会引入 Spring AMQP、Spring Rabbit 和 RabbitMQ Java Client；版本由 Spring Boot Parent 统一管理，不要为这些传递依赖分别指定版本。

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-amqp</artifactId>
    </dependency>
</dependencies>
```

### 4.2 连接与监听器配置

```yaml
# src/main/resources/application.yml
spring:
  application:
    name: rabbitmq-demo
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER:app}
    password: ${RABBITMQ_PASSWORD:local-dev-only}
    virtual-host: ${RABBITMQ_VHOST:/} # 虚拟主机，用于隔离不同应用的交换机和队列
    connection-timeout: 5s # 连接超时时间
    listener:
      simple:
        acknowledge-mode: auto # 确认模式：auto = 方法正常返回则 ack，抛异常则按重试/拒绝策略处理
        prefetch: 10 # 每次从 Broker 预取消息数，未确认前不再分发新消息
        concurrency: 1 # 初始消费者线程数
        max-concurrency: 4 # 最大消费者线程数，消息积压时会扩容
```

`host` 是 RabbitMQ 服务器的地址，`virtual-host` 是服务器内部的逻辑隔离单元。一个 RabbitMQ 实例可以运行多个 vhost，每个 vhost 有独立的交换机、队列和绑定。不同应用使用不同 vhost 时，即使名称相同也不会冲突。默认 vhost 是 `/`，小型项目通常不需要修改。

这里使用 `AUTO` 模式：Spring 会在监听方法**成功返回后**自动确认消息，而不是在消息到达方法时立即确认。如果监听方法抛出异常，容器按照重回队列、重试或拒绝策略处理。

### 4.3 定义消息契约

事件 ID 用于幂等，订单 ID 是业务标识，`occurredAt` 使用 ISO-8601 字符串以保持示例的序列化依赖简单。

**幂等**指同一个操作执行一次和执行多次结果相同。例如“设置订单状态为已支付”是幂等的，而“订单金额 +100”不是。RabbitMQ 保证至少投递一次，消息可能重复到达，消费者需要用 eventId 去重，确保同一条消息处理多次不会重复执行业务逻辑。

```java
package com.example.rabbitmqdemo.messaging;

public record OrderCreatedEvent(
        String eventId,
        String orderId,
        String occurredAt
) {
}
```

集中管理交换机、队列和路由键名称，避免生产者与消费者各自拼写字符串。

```java
package com.example.rabbitmqdemo.messaging;

public final class MessagingConstants {

    /** 订单业务的主交换机 */
    public static final String ORDER_EXCHANGE = "commerce.events";
    /** 订单创建事件的队列 */
    public static final String ORDER_CREATED_QUEUE = "order.created.queue";
    /** 订单创建事件的路由键 */
    public static final String ORDER_CREATED_ROUTING_KEY = "order.created";

    /** 死信交换机：处理失败的消息会被路由到这里 */
    public static final String ORDER_DLX = "commerce.events.dlx";
    /** 死信队列：存放处理失败的消息 */
    public static final String ORDER_CREATED_DLQ = "order.created.dlq";
    /** 死信路由键 */
    public static final String ORDER_CREATED_DEAD_ROUTING_KEY = "order.created.dead";

    private MessagingConstants() {
    }
}
```

### 4.4 声明交换机、队列和绑定

Spring 应用启动时，`RabbitAdmin` 会把这些 Bean 对应的拓扑声明到 Broker：

```text
Spring 容器中的 Bean（Java 对象）        RabbitMQ 服务器（实际对象）
─────────────────────────────           ────────────────────────
TopicExchange orderExchange   ──声明──>  commerce.events 交换机
Queue orderCreatedQueue       ──声明──>  order.created 队列
Binding orderCreatedBinding   ──声明──>  绑定关系
                ↑
           RabbitAdmin 负责这一步
```

工作流程：

1. 应用启动，获得 RabbitMQ 连接
2. 扫描 Spring 容器中的所有 `Exchange`、`Queue`、`Binding` Bean
3. 把它们声明到 RabbitMQ 服务器（创建真实的交换机、队列、绑定）
4. 如果服务器上已存在同名对象，则跳过（幂等）

注意：如果同名对象已存在但参数不同（如 durable 设置不一致），Broker 会拒绝声明并报错；修改 durable、exclusive、auto-delete 或队列参数时需要制定迁移方案，不能直接覆盖。

```java
package com.example.rabbitmqdemo.config;

import com.example.rabbitmqdemo.messaging.MessagingConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RabbitMqConfiguration {

    /**
     * 订单业务的主交换机（Topic 类型）
     * 参数：名称、是否持久化、是否自动删除
     */
    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(MessagingConstants.ORDER_EXCHANGE, true, false);
    }

    /**
     * 订单创建事件的队列
     * - durable：持久化，RabbitMQ 重启后队列仍在
     * - deadLetterExchange：消息被拒绝后发送到死信交换机
     * - deadLetterRoutingKey：死信消息使用的路由键
     */
    @Bean
    public Queue orderCreatedQueue() {
        return QueueBuilder.durable(MessagingConstants.ORDER_CREATED_QUEUE)
                .deadLetterExchange(MessagingConstants.ORDER_DLX)
                .deadLetterRoutingKey(MessagingConstants.ORDER_CREATED_DEAD_ROUTING_KEY)
                .build();
    }

    /**
     * 将队列绑定到交换机，指定路由键
     * 消息 Routing Key = order.created 时，会进入这个队列
     */
    @Bean
    public Binding orderCreatedBinding(Queue orderCreatedQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(orderCreatedQueue)
                .to(orderExchange)
                .with(MessagingConstants.ORDER_CREATED_ROUTING_KEY);
    }

    /** 死信交换机：接收处理失败的消息 */
    @Bean
    public TopicExchange orderDeadLetterExchange() {
        return new TopicExchange(MessagingConstants.ORDER_DLX, true, false);
    }

    /** 死信队列：存放处理失败的消息，等待人工处理或重试 */
    @Bean
    public Queue orderCreatedDeadLetterQueue() {
        return QueueBuilder.durable(MessagingConstants.ORDER_CREATED_DLQ).build();
    }

    /** 将死信队列绑定到死信交换机 */
    @Bean
    public Binding orderCreatedDeadLetterBinding(
            Queue orderCreatedDeadLetterQueue,
            TopicExchange orderDeadLetterExchange
    ) {
        return BindingBuilder.bind(orderCreatedDeadLetterQueue)
                .to(orderDeadLetterExchange)
                .with(MessagingConstants.ORDER_CREATED_DEAD_ROUTING_KEY);
    }

    /** 消息转换器：使用 Jackson 将对象序列化为 JSON */
    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
```

### 4.5 发布消息

发布前先理解消息如何变成字节。4.4 注册的 `JacksonJsonMessageConverter` 是 Spring AMQP 提供的消息转换器，底层依赖 Jackson 的 `ObjectMapper`，在发送和消费两端完成 Java 对象与 JSON 的自动转换：

```text
发送端：
  Java 对象 ──JacksonJsonMessageConverter──> JSON 字节数组（content_type=application/json）──> Broker

消费端：
  Broker ──> JSON 字节数组 ──JacksonJsonMessageConverter──> Java 对象（根据方法参数类型）
```

> **版本说明**：Spring AMQP 4（对应 Spring Boot 4）使用面向 Jackson 3 的 `JacksonJsonMessageConverter`。旧教程中常见的 `Jackson2JsonMessageConverter` 属于 Jackson 2.x 时代的 API，两者包名和方法签名不同，不应混用。

`RabbitTemplate.convertAndSend()` 的第三个参数传入 Java 对象时，转换器先将其序列化为 JSON 字节数组，并设置消息头 `content_type=application/json`，再交给 Broker 投递。

```java
package com.example.rabbitmqdemo.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public OrderEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(OrderCreatedEvent event) {
        rabbitTemplate.convertAndSend(
                MessagingConstants.ORDER_EXCHANGE,
                MessagingConstants.ORDER_CREATED_ROUTING_KEY,
                event
        );
    }
}
```

### 4.6 在 Controller 中发送消息

消息通常在 HTTP 请求处理过程中发送。例如订单 Controller 创建订单后，调用 Service 完成业务处理，Service 注入 `OrderEventPublisher` 发布事件：

```text
POST /api/orders ──> OrderController ──> OrderService.createOrder()
                                              │
                                              ├─ 保存订单到数据库
                                              │
                                              └─ OrderEventPublisher.publish(event)
                                                     │
                                                     └─ RabbitTemplate.convertAndSend(...)
```

```java
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return ResponseEntity.ok(response);
    }
}
```

```java
@Service
public class OrderService {

    // 数据访问层，用于操作数据库（如 Spring Data JPA、MyBatis），与 RabbitMQ 无关
    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    public OrderService(OrderRepository orderRepository, OrderEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        // 1. 保存订单到数据库
        Order order = new Order(request.orderId(), request.amount());
        orderRepository.save(order);

        // 2. 发布订单创建事件
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID().toString(),   // eventId：用于幂等去重
                order.orderId(),                // orderId：业务订单号
                Instant.now().toString()        // occurredAt：事件发生时间
        );
        eventPublisher.publish(event);

        return new OrderResponse(order.orderId(), "CREATED");
    }
}
```

### 4.7 消费消息并做幂等判断

#### @RabbitListener 注解

`@RabbitListener` 是 Spring AMQP 提供的声明式监听注解。加在方法上后，Spring 会在应用启动时自动创建**监听容器（MessageListenerContainer）**，从指定队列拉取消息并调用该方法。

```java
@RabbitListener(queues = "队列名称")
public void handle(消息类型 event) {
    // 处理消息
}
```

**常用参数**：

| 参数          | 说明                                   |
| ------------- | -------------------------------------- |
| `queues`      | 监听的队列名称，可以是常量             |
| `concurrency` | 覆盖全局配置，单独设置该监听器的并发数 |
| `ackMode`     | 单独设置确认模式（默认继承全局配置）   |

**自动监听的工作原理**：

```text
应用启动
    ↓
@Component 将监听器类注册为 Spring Bean
    ↓
Spring AMQP 扫描到方法上的 @RabbitListener
    ↓
自动创建 MessageListenerContainer（监听容器）
    ↓
容器连接 RabbitMQ，开始从指定队列消费消息
    ↓
消息到达时自动调用监听方法
```

只需满足以下条件即可自动监听，无需手动启动任何监听器：

1. 类上有 `@Component`（或其他 Bean 注解）
2. 方法上有 `@RabbitListener`
3. 依赖中有 `spring-boot-starter-amqp`
4. 配置了 RabbitMQ 连接信息

这就是“声明式监听”的含义——你只需声明“我要监听哪个队列”，框架帮你完成剩下的。

#### 幂等处理

`@RabbitListener` 的方法参数声明了目标类型（如 `OrderCreatedEvent`），转换器读取消息头中的 `content_type`，确认是 JSON 后，将字节数组反序列化为该类型的 Java 对象，再传入监听方法。

**监听器放在哪里？**真实项目中，监听器通常放在 `listener` 或 `consumer` 包中，与 `controller`、`service` 平级。它不是业务逻辑本身，而是接收消息后委托给 Service 处理的基础设施组件，因此用 `@Component` 而非 `@Service`。

```text
src/main/java/com/example/
├── controller/       ← HTTP 控制器
├── service/          ← 业务逻辑
├── listener/         ← RabbitMQ 消费者（OrderCreatedListener 放这里）
└── messaging/        ← 消息契约（事件、Publisher）
```

下面使用内存集合演示幂等判断。它只能用于单进程学习示例；生产环境应使用数据库唯一约束、幂等记录表或具有原子写入能力的共享存储。

```java
package com.example.rabbitmqdemo.messaging;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedListener {

    private static final Logger log = LoggerFactory.getLogger(OrderCreatedListener.class);

    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    @RabbitListener(queues = MessagingConstants.ORDER_CREATED_QUEUE)
    public void handle(OrderCreatedEvent event) {
        if (!processedEventIds.add(event.eventId())) {
            log.info("Ignore duplicated order event: eventId={}", event.eventId());
            return;
        }

        log.info(
                "Order created: eventId={}, orderId={}, occurredAt={}",
                event.eventId(),
                event.orderId(),
                event.occurredAt()
        );
    }
}
```

生产环境中的幂等记录与业务更新应尽量放在同一个本地数据库事务中。若先记录“已处理”再更新业务，业务更新失败时可能永久跳过消息；若先更新业务再记录，进程在两者之间崩溃又可能重复执行。

---

## 5. 构建可靠投递链路

可靠性需要逐段分析：

```text
业务数据库
   │ ① 业务提交与发布之间可能失败
   ▼
Producer
   │ ② 网络或 Broker 接收可能失败
   ▼
Exchange
   │ ③ 可能没有匹配队列
   ▼
Queue
   │ ④ 消费者处理可能失败或重复
   ▼
Consumer / 业务数据库
```

### 5.1 消息发出去了，但真的到了吗？

回顾本章开头的可靠性链路，发布消息时有两个风险点：

```text
Producer
   │ ② 网络或 Broker 接收可能失败  ← Confirm 解决
   ▼
Exchange
   │ ③ 可能没有匹配队列            ← Return 解决
   ▼
Queue
```

#### Confirm：Broker 到底收到没有？

默认情况下，`RabbitTemplate.convertAndSend()` 发完就返回，不关心 Broker 是否真的收到。如果网络闪断或 Broker 宕机，消息可能丢失而你毫不知情。

开启 publisher confirm 后，Broker 会**异步回调**告诉你每条消息的接收结果：

```text
默认：
  Producer ──发消息──> Broker
  （发完就忘，不管结果）

开启 confirm 后：
  Producer ──发消息──> Broker ──异步返回 ack/nack──> Producer
  （确认收到 √  或  确认失败 ✗）
```

但这里有一个问题：Broker 异步返回的是"某条消息"的确认，你怎么知道它对应你发出的哪一条？这就是 `CorrelationData` 的作用——发布时给每条消息贴一个"追踪凭据"，Broker 返回确认时带回同一个凭据：

```text
发布时：
  convertAndSend(exchange, routingKey, message, new CorrelationData("event-001"))
                                                      ↑ 贴上追踪凭据

Broker 回调：
  ConfirmCallback(correlationData, ack, cause)
                    ↑ 同一个凭据回来了
                    → correlationData.getId() 返回 "event-001"，找到对应消息
```

```java
// 发布时传入 CorrelationData
CorrelationData correlationData = new CorrelationData(event.eventId());
rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, event, correlationData);
```

> confirm 回调是异步的，不要依赖发送时的局部变量，要用 `CorrelationData` 携带的标识查找上下文。

#### Return：消息路由到队列了吗？

Confirm 只保证 Broker **接收**了消息，不保证消息能路由到队列。如果 Routing Key 写错了，消息到达交换机后找不到匹配的 Binding，默认会被**静默丢弃**。

开启 publisher return 后，路由失败的消息会退回给生产者：

```text
默认（路由失败）：
  消息 → Exchange → 无匹配 Binding → 静默丢弃 ✗

开启 return 后：
  消息 → Exchange → 无匹配 Binding → 退回生产者，触发回调
```

Return 需要 `mandatory=true` 配合才能生效，否则路由失败时仍然不会触发退回。

#### 配置

两项功能都不是默认开启的，需要显式配置：

> **说明性配置片段**：在第 4.2 节的 `spring.rabbitmq` 下追加。

```yaml
spring:
  rabbitmq:
    publisher-confirm-type: correlated # 开启 confirm
    publisher-returns: true # 开启 return
    template:
      mandatory: true # return 需要此项配合
```

| 配置项                               | 默认    | 解决什么问题                       |
| ------------------------------------ | ------- | ---------------------------------- |
| `publisher-confirm-type: correlated` | `none`  | Broker 是否接收了消息              |
| `publisher-returns: true`            | `false` | 消息是否成功路由到队列             |
| `template.mandatory: true`           | `false` | 路由失败时触发退回（配合 returns） |

**什么时候开启？** 消息不能丢的场景（订单、支付等关键业务）。如果消息丢失可接受（如非关键日志），保持默认关闭即可。

> Confirm 只能证明 Broker 接收了消息，不能证明消费者处理成功。消费者侧的确认见 5.3 节。Return 也依赖 `mandatory=true`；没有开启时，路由失败的消息可能不会通知发布者。

### 5.2 数据库提交和消息发送，先做哪个？

回顾链路图的风险点 ①：

```text
业务数据库
   │ ① 业务提交与发布之间可能失败
   ▼
Producer
```

以订单创建为例：Service 需要同时做两件事——把订单写入数据库、把 `order.created` 事件发送到 RabbitMQ。但这两个操作分属两个独立系统，无法放在同一个事务里。

两种顺序都有问题：

```text
方案 A：先写数据库，再发消息
  DB.insert(order)  ✓ 成功
        │
        │  进程崩溃 ✗
        ▼
  rabbitTemplate.convertAndSend(...)  ← 永远不会执行
  结果：数据库有订单，但下游系统永远不知道 → 少消息

方案 B：先发消息，再写数据库
  rabbitTemplate.convertAndSend(...)  ✓ 成功
        │
        │  数据库回滚 ✗
        ▼
  DB.insert(order)  ← 失败或回滚
  结果：下游系统收到事件，但数据库里没有订单 → 假消息
```

**Transactional Outbox** 是解决这个问题的常见模式：

```text
同一个数据库事务内：
  ├─ INSERT INTO orders (...)          ← 业务数据
  └─ INSERT INTO outbox (event_id, event_type, payload, status='PENDING')  ← 事件记录

事务提交后，独立发布器轮询 outbox 表：
  SELECT * FROM outbox WHERE status = 'PENDING'
        │
        ▼
  rabbitTemplate.convertAndSend(...)   ← 发送消息
        │
        ▼
  UPDATE outbox SET status = 'SENT'    ← 标记已发送
```

关键点：业务数据和事件记录在**同一个数据库事务**中写入，要么都成功，要么都失败，不会出现"一个成功一个失败"的缺口。

> Outbox 发布器仍可能在"发送成功、更新发送状态前"崩溃，导致同一条事件被发送多次，所以消费者幂等依然必需。

> 本指南的重点是 RabbitMQ 消息链路，不展开 Outbox 的完整实现（如 Debezium CDC 替代轮询）。入门阶段理解"为什么需要 Outbox"即可。

### 5.3 有界重试与死信队列

对网络抖动等短暂故障可以重试；参数错误、数据缺失等永久故障反复重试只会占用消费者。一个实用的基础策略是：

```text
消费失败
  ├─ 第 1~3 次：本地有界重试
  └─ 仍失败：reject 且不 requeue
               └─> Dead Letter Exchange
                       └─> Dead Letter Queue
```

> **说明性配置片段**：追加到第 4.2 节的监听器配置。具体间隔要根据业务耗时和下游恢复时间调整。

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        default-requeue-rejected: false # 消费失败后不重回原队列，而是路由到死信队列
        retry:
          enabled: true # 开启本地有界重试
          max-attempts: 3 # 最多尝试 3 次（含首次）
          initial-interval: 1s # 首次重试等待 1 秒
          multiplier: 2 # 每次重试间隔翻倍：1s → 2s
          max-interval: 10s # 重试间隔上限 10 秒，不再增长
```

第 4.4 节已经为主队列配置了 DLX。消息在重试耗尽后被拒绝且不重新入原队列时，可以由 RabbitMQ 路由到 DLQ。需要注意：

- 无限 requeue 会形成高频失败循环，消耗 CPU 和网络。
- DLQ 不是垃圾桶；应监控积压、保留失败原因，并提供审核后的重放流程。
- 不要直接把 DLQ 全量倒回主队列，否则永久错误会再次制造风暴。
- RabbitMQ 官方更推荐在需要动态运维时使用 Policy 配置 DLX；代码中的队列参数适合展示拓扑和固定应用约束，但修改时通常要重建队列。

---

## 6. 吞吐量与积压

### 6.1 Prefetch 与消费者并发

`prefetch` 在 4.2 节的 `spring.rabbitmq.listener.simple` 下配置（示例值为 10）。它控制每个消费者可以持有多少条尚未确认的消息。它的含义是：Broker 一次性最多发给消费者 N 条消息，消费者处理并确认（ack）一条后，Broker 才补发下一条。

```text
prefetch = 3 时：

Broker ──发 3 条──> 消费者（处理中，尚未 ack）
                    处理完第 1 条，ack → Broker 补发第 4 条
                    处理完第 2 条，ack → Broker 补发第 5 条
                    以此类推，消费者手里始终不超过 3 条未确认的消息
```

“尚未确认”指的是：消息已经发给消费者，但消费者还没处理完、还没返回 ack。如果消费者在这时崩溃，这些消息会被重新投递给其他消费者。

值过大时，慢消费者会提前占住大量消息，影响分配公平性并扩大故障后的重复投递；值过小时则可能降低吞吐。

`concurrency` 和 `max-concurrency` 增加同一实例中的消费者数量。横向扩容应用实例也会增加消费者。

这两个值不是拍脑袋定的，核心依据是：

```text
期望吞吐 ≈ 并发消费者数 ÷ 单条消息平均处理耗时
```

- 单条处理 50ms 时，1 个消费者约 20 条/秒；要 100 条/秒就需要约 5 个消费者
- 下游数据库连接池只有 10 个连接时，消费者开到 20 也不会更快，只会排队
- `concurrency` 是启动时就创建的消费者数（保底），`max-concurrency` 是积压时最多扩容到的数量（上限）

调优时同时观察：

- ready 消息数量和增长速度；
- unacked 消息数量；
- 单条处理耗时与失败率；
- 下游数据库、HTTP 服务的承载能力。

不能因为队列积压就无限增加消费者；瓶颈如果在数据库，增加消费者只会把压力继续向下游传递。

---

## 7. 参考资料

- [Spring Boot Reference：AMQP](https://docs.spring.io/spring-boot/reference/messaging/amqp.html)
- [Spring AMQP Reference](https://docs.spring.io/spring-amqp/reference/)
- [RabbitMQ Networking and Default Ports](https://www.rabbitmq.com/docs/networking)
- [RabbitMQ AMQP 0-9-1 Protocol](https://www.rabbitmq.com/amqp-0-9-1-protocol)
- [OASIS AMQP Version 1.0](https://docs.oasis-open.org/amqp/core/v1.0/os/amqp-core-overview-v1.0-os.html)
- [RabbitMQ Tutorials](https://www.rabbitmq.com/tutorials)
- [RabbitMQ Exchanges](https://www.rabbitmq.com/docs/exchanges)
- [RabbitMQ Consumer Acknowledgements and Publisher Confirms](https://www.rabbitmq.com/docs/confirms)
- [RabbitMQ Dead Letter Exchanges](https://www.rabbitmq.com/docs/dlx)
- [RabbitMQ Reliability Guide](https://www.rabbitmq.com/docs/reliability)
