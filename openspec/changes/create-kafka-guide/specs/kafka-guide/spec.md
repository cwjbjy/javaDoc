# Spec: kafka-guide

## ADDED Requirements

### Requirement: 指南定位与读者契约

指南 SHALL 面向已掌握 Spring Boot、Maven 和基本 Java 开发的开发者，在开头明确读者画像、学习目标、版本锚定（Java 17、Spring Boot 3.5.14、spring-kafka 由 Boot BOM 管理、`apache/kafka:3.7.0` KRaft）与范围外声明，并声明与 `rabbitmq-spring-boot-guide.md` 的对称姊妹篇关系、与 `spring-cloud-microservices-guide.md` §9 的深度续篇关系。

#### Scenario: 读者契约要素齐全

- **WHEN** 读者打开指南开头
- **THEN** 能看到读者画像、学习目标、版本锚定、范围外声明与姊妹篇/续篇关系说明

#### Scenario: 版本范围有声明

- **WHEN** 读者阅读涉及 Kafka 配置或默认行为的内容
- **THEN** 指南已在前文声明锁定的 Kafka 镜像版本与 Boot 基线，读者不会误用到其他版本的行为

### Requirement: 渐进式 9 章结构与日志模型主线

指南 SHALL 按设计定稿的 9 章结构组织：第 1 章问题驱动开场，第 2 章建立日志模型（Topic / Partition / offset / 追加写 / 消费后保留），第 3~7 章依次展开 KRaft 环境、Spring Boot 收发、分区与顺序、可靠性、消费端 offset 与死信，第 8 章 Kafka vs RabbitMQ 选型，第 9 章延伸阅读。分区、消费者组、可靠性、offset 提交等内容 SHALL 从第 2 章日志模型推导，不出现未定义的前向引用。

#### Scenario: 后续章节依赖日志模型

- **WHEN** 读者按顺序阅读第 5~7 章
- **THEN** 每个概念（分区、消费组、acks、offset）都能回溯到第 2 章建立的日志模型，无跳跃

#### Scenario: 章节可独立复习

- **WHEN** 读者完成学习后需要回顾单个主题（如可靠性）
- **THEN** 可定位到对应章节查阅，章节开头有最短上下文回顾

### Requirement: 交易流水管道场景贯穿

指南 SHALL 以「交易流水管道」场景贯穿全文：订单服务发布交易流水事件，实时对账、风控、搜索索引、离线数仓四个下游独立消费组并行订阅，新增用户画像下游时从自身 offset 从头回放全量流水；场景 SHALL 演示多订阅（多消费组读同一 Topic 互不干扰）与回放（新消费组独立 offset 从头消费）。

#### Scenario: 多订阅有演示

- **WHEN** 指南讲解 Consumer Group
- **THEN** 以四个下游消费组并行订阅同一 Topic 的图示/示例说明组间互不干扰

#### Scenario: 回放有演示

- **WHEN** 指南讲解 offset 与消费后保留
- **THEN** 以新下游（用户画像）从头回放的示例说明 Kafka 的回放能力

#### Scenario: 场景定位声明

- **WHEN** 读者阅读第 1 章场景介绍
- **THEN** 能看到场景服务于演示 Kafka 能力的声明，不误解为金融系统设计教学

### Requirement: KRaft 本地环境

指南 SHALL 使用 `apache/kafka:3.7.0` 镜像以 KRaft 单节点模式（broker+controller 合一、无 Zookeeper）提供本地环境，Compose 配置与微服务指南 §9 基线一致，并给出启动验证命令（topic 创建与 console 收发）；SHALL 明确声明 Kafka 3.x KRaft 模式不再依赖 Zookeeper，不教授 Zookeeper 旧配置。

#### Scenario: 环境可复现

- **WHEN** 读者按第 3 章执行 Compose 启动
- **THEN** 能获得与指南一致的 Kafka 3.7.0 单节点环境，验证命令有明确预期输出

#### Scenario: KRaft 声明明确

- **WHEN** 读者阅读环境章节
- **THEN** 能看到 KRaft 模式与「不再需要 Zookeeper」的说明

### Requirement: Spring Boot 收发与序列化约定

指南 SHALL 覆盖 spring-kafka 依赖引入（版本由 Boot 3.5.14 BOM 管理）、bootstrap-servers 与 producer/consumer 配置、KafkaTemplate 发送与 @KafkaListener 接收；序列化 SHALL 沿用微服务指南 §9 的 JSON 字符串约定（StringSerializer + JSON 库），生产者和消费者各自负责序列化/反序列化，value 控制台直接可读。

#### Scenario: 收发代码可直接运行

- **WHEN** 读者按第 4 章配置项目
- **THEN** 能以最小配置完成一次消息收发，代码块自包含

#### Scenario: 序列化约定一致

- **WHEN** 读者对照微服务指南 §9
- **THEN** 两篇指南的序列化约定一致，无冲突

### Requirement: 顺序与并行——分区与消费者组

指南 SHALL 讲解：消息按 key 哈希进入分区、Kafka 只保证分区内有序、分区数是组内并行上限、同一分区同时只能被组内一个消费者读取、消费者组再均衡；并 SHALL 以交易流水场景给出 key 选择示例（如按订单号分区保证同一订单流水有序）。

#### Scenario: 有序性边界清晰

- **WHEN** 读者阅读顺序相关章节
- **THEN** 能区分「分区内有序」与「全局无序」，理解 key 的作用

#### Scenario: 并行上限有推导

- **WHEN** 读者阅读消费者组并行性内容
- **THEN** 能从分区数推导组内最大并行度，并了解再均衡的影响

### Requirement: 可靠性——acks / ISR / 幂等生产者

指南 SHALL 讲解生产者可靠性链路：acks=all 与 min.insync.replicas 的关系、ISR 副本机制、发送失败重试与重试导致的重复风险、幂等生产者（enable.idempotence）解决重试重复；SHALL 明确 Kafka 业务上仍以「至少一次」为现实前提，消费端必须容忍重复。

#### Scenario: acks 与 ISR 关系清晰

- **WHEN** 读者阅读可靠性章节
- **THEN** 能解释 acks=all 与 min.insync.replicas 如何共同决定可用性与一致性

#### Scenario: 不丢与不重的边界

- **WHEN** 读者设计生产配置
- **THEN** 能区分「不丢消息」（acks/ISR）与「不重复消息」（幂等生产者 + 消费端容忍）的不同层次

### Requirement: 消费端——offset 提交、重复消费与死信

指南 SHALL 讲解 offset 提交语义（自动 vs 手动）、提交时机与重复消费的关系、重复消费的业务容忍模式，以及消费失败的兜底：DefaultErrorHandler 重试与 DeadLetterPublishingRecoverer 死信主题；SHALL 演示将失败消息转入死信主题并人工介入的完整链路。

#### Scenario: 提交语义有对比

- **WHEN** 读者阅读 offset 提交章节
- **THEN** 能以表格/对比理解自动提交与手动提交的取舍与重复消费窗口

#### Scenario: 死信链路完整

- **WHEN** 读者处理消费失败场景
- **THEN** 能按指南配置重试与死信主题，失败消息不再阻塞分区

### Requirement: Kafka vs RabbitMQ 选型与文档导航互链

指南 SHALL 专设选型章，从增量视角（日志模型 vs 队列模型、多订阅与回放、吞吐与可靠投递）对比 Kafka 与 RabbitMQ，导航到微服务指南 §9.5 的完整对照表而不复制；SHALL 与 `rabbitmq-spring-boot-guide.md`、微服务指南 §9、k8s 指南 §12.3 形成导航互链；微服务指南 §9 末尾 SHALL 添加指向本指南的导航链接。

#### Scenario: 选型章不重复对照表

- **WHEN** 读者阅读选型章
- **THEN** 获得增量视角（日志模型、ISR、多订阅回放），完整对照表通过导航到 §9.5 获取

#### Scenario: 互链闭环

- **WHEN** 读者从任一指南进入 Kafka 主题
- **THEN** 能通过导航在微服务指南 §9、kafka 指南、rabbitmq 指南、k8s 指南 §12.3 之间往返

### Requirement: 写作风格与结构校验

指南 SHALL 遵循 guide-writing skill 的 content contract（依赖顺序、术语先定义后使用、导航按需添加），保持仓库既有指南风格（中文叙述、中文注释、ASCII 图示、问题驱动、三步接入、生产实践、本章回顾、速查清单、延伸阅读），并通过 `validate_guide.py` 结构校验。

#### Scenario: 结构校验通过

- **WHEN** 指南文件保存后运行 `validate_guide.py`
- **THEN** 校验器报告通过，目录锚点与章节编号有效

#### Scenario: 术语先定义后使用

- **WHEN** 读者按顺序阅读
- **THEN** 不会遇到「稍后解释」式的前向引用，每个 Kafka 术语出现前其概念已建立或当场定义
