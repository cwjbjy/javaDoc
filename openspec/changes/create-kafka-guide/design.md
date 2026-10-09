# Design: create-kafka-guide

## Context

`docs/` 的知识链已有 RabbitMQ 深度指南（`docs/Spring/rabbitmq-spring-boot-guide.md`，917 行，组织主线「可靠性是一条由生产者、Broker、消费者共同完成的链路」）。Kafka 在 `docs/Spring/spring-cloud-microservices-guide.md` §9 有约 230 行速成覆盖：核心概念（Topic/Partition/Consumer Group）、三步接入（`apache/kafka:3.7.0` KRaft Compose、spring-kafka 依赖、JSON 字符串序列化约定）、KafkaTemplate 收发、手动 ack、RabbitMQ vs Kafka 对照表。`docs/Kubernetes/kubernetes-guide.md` §12.3 将「Kafka 上 k8s」归入延伸阅读（有状态负载是独立大主题）。

仓库指南风格已定型：问题驱动开头 → 核心概念 → 三步接入 → 生产实践 → 本章回顾 → 速查清单 → 延伸阅读，中文叙述、ASCII 图示、版本锚定、示例诚实分级。guide-writing skill 提供了 progressive 形状的判定标准与内容契约。

## Goals / Non-Goals

**Goals:**

- 产出一篇 progressive 渐进式指南，9 章按因果推进：读者按顺序读完即可把「Kafka 是消息队列」升级为「Kafka 是可回放的分布式提交日志」的完整心智模型。
- 以「交易流水管道」场景贯穿全文：订单服务发布交易流水事件，实时对账、风控、搜索索引、离线数仓四个下游独立消费组并行订阅；新增用户画像下游时从头回放，演示多订阅与回放。
- 深度覆盖 §9 无法展开的主题：分区与顺序（key 定分区、分区内有序、分区数 = 并行上限）、Consumer Group 再均衡、acks/ISR/min.insync.replicas、重试与幂等生产者、offset 提交语义（自动 vs 手动）、重复消费与死信（DefaultErrorHandler + DeadLetterPublishingRecoverer）。
- 技术栈锚定 Java 17、Spring Boot 3.5.14（spring-kafka 由 Boot BOM 管理）、`apache/kafka:3.7.0` KRaft 单节点，与微服务指南 §9 基线一致，示例可直接复用于微服务项目。
- 示例诚实分级，运行 `validate_guide.py` 结构校验。

**Non-Goals:**

- 不覆盖 Kafka Streams / KSQL、Kafka Connect、Spring Cloud Stream（独立大主题，延伸阅读导航）。
- 不展开 Kafka 事务 / exactly-once（事务语义是独立主题，延伸阅读导航）。
- 不重复微服务指南 §9 的三步接入与 RabbitMQ vs Kafka 对照表——导航互链，仅做增量视角（日志模型 vs 队列模型）。
- 不覆盖集群运维、跨机房容灾、大规模调优；本地学习集群为主，生产差异在相关章节标注。
- 不修改 `src/` 与 `pom.xml`，不改动微服务指南 §9 主线内容（仅末尾加导航链接）。

## Decisions

- **指南形状：progressive。** 分区、消费者组、可靠性、offset 提交都从日志模型推导，后续章节依赖前文建立的概念，与 guide-writing skill 的 progressive 判定一致；与 RabbitMQ 指南同款形状，保持姊妹篇对称。
- **组织主线：「Kafka 的核心不是队列，而是可回放的分布式提交日志」。** 与 RabbitMQ 指南「可靠性是一条链路」形成互补而非重复：吞吐来自顺序追加写，顺序来自分区，回放与多订阅来自消费组独立 offset。
- **章节结构（9 章）：**
  1. 为什么需要 Kafka（问题驱动：交易流水管道场景）
  2. 日志模型：一条消息的完整旅程（Topic / Partition / offset / 追加写 / 消费后保留）
  3. 启动本地 Kafka（KRaft 单节点 Compose，对齐 §9 配置）
  4. 在 Spring Boot 中完成第一次收发（依赖、配置、KafkaTemplate、@KafkaListener）
  5. 顺序与并行：分区与消费者组（key 定分区、分区内有序、并行上限、再均衡）
  6. 可靠性：从 acks 到幂等生产者（acks=all、ISR、min.insync.replicas、重试、幂等）
  7. 消费端：offset 提交、重复消费与死信（提交语义、容忍重复、DefaultErrorHandler + 死信）
  8. Kafka vs RabbitMQ 选型（日志模型 vs 队列模型增量视角，导航到 §9.5 对照表）
  9. 延伸阅读与参考资料（Kafka Streams / Connect / Spring Cloud Stream / 事务、k8s §12.3 导航）
- **场景：交易流水管道。** 订单服务发交易流水事件，四个下游独立消费组并行订阅（实时对账、风控、搜索索引、离线数仓），新下游用户画像从头回放。场景刻意避开 RabbitMQ 指南的「可靠投递订单事件」与 §9 的「用户行为日志」，突出 Kafka 的多订阅 + 回放差异优势；与仓库既有场景（订单、用户行为）同域，术语零学习成本。
- **与 §9 的边界：速成 vs 深度。** §9 保持三步接入职责不动；新指南深度展开时引用而非复制 §9 的 Compose 配置与 JSON 序列化约定（保持基线一致），§9 末尾添加指向新指南的导航链接。
- **版本锚定：** Java 17、Spring Boot 3.5.14、`apache/kafka:3.7.0`（KRaft，沿用 §9 镜像）；spring-kafka 版本由 Boot BOM 管理，撰写时核实具体小版本后写入指南开头。
- **序列化约定：与 §9 一致。** value 用 JSON 字符串（StringSerializer + JSON 库），生产者和消费者各自负责序列化/反序列化，排查时控制台直接可读；不引入 JsonSerializer/Avro，避免超出主线。
- **写作风格：沿用仓库指南 DNA。** 问题驱动、三步接入、生产实践、本章回顾、速查清单、延伸阅读；示例诚实分级（未实际验证的标注 Complete example, not yet verified）。

## Risks / Trade-offs

- [spring-kafka 版本随 Boot BOM 变动] → 撰写时核实 Boot 3.5.14 管理的 spring-kafka 版本并写入开头声明，指南只使用稳定 API。
- [与 §9 内容重复风险] → 边界规则「§9 速成、新指南深度」；重复处导航不复制，选型章只做增量视角（日志模型、ISR）。
- [示例依赖本地 Docker 与 Kafka 3.7.0 镜像，编写阶段无法全部实际运行] → 示例标注 Complete example, not yet verified；关键命令与配置以官方文档核对。
- [KRaft 与早期 Zookeeper 模式差异可能让读者困惑] → 第 3 章明确声明 Kafka 3.x KRaft 模式不再需要 Zookeeper，不教 Zookeeper 旧配置。
- [微服务指南后续更新导致 §9 基线漂移] → 指南开头声明基线来源（微服务指南 §9）与锚定版本，后续如漂移由读者对照基线调整。
- [交易流水场景涉及金融语义（对账、风控），读者可能误解指南在教金融系统设计] → 场景仅作消息流示例，第 1 章声明场景服务于演示 Kafka 能力，不涉及业务正确性设计。
