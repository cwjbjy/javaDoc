# Proposal: create-kafka-guide

## Why

`docs/` 知识链已覆盖 Java 基础、Spring Boot 单体到 Spring Cloud 微服务，且 RabbitMQ 已有独立深度指南（`docs/Spring/rabbitmq-spring-boot-guide.md`，917 行）。但 Kafka 只有 `docs/Spring/spring-cloud-microservices-guide.md` §9 的速成章节（约 230 行，三步接入级），无法承载分区与顺序、Consumer Group 再均衡、acks/ISR、幂等生产者、offset 提交语义、死信等深度主题。Kafka 是生产环境数据管道的事实标准，项目读者需要一篇与 RabbitMQ 指南对称的独立深度指南，把 §9 的「接入」升级为「理解」。

## What Changes

- 新增 `docs/Spring/kafka-spring-boot-guide.md`：progressive 渐进式指南，组织主线「Kafka 的核心不是队列，而是可回放的分布式提交日志」，9 章结构（为什么需要 → 日志模型 → KRaft 启动 → Spring Boot 收发 → 分区与顺序 → acks/ISR/幂等可靠性 → offset 提交与死信 → Kafka vs RabbitMQ 选型 → 延伸阅读）。
- 示例场景「交易流水管道」：订单服务发布交易流水事件，实时对账、风控、搜索索引、离线数仓四个下游独立消费组并行订阅，新下游（用户画像）上线时从自身 offset 从头回放，演示 Kafka 的多订阅与回放能力。
- 技术栈锚定：Java 17、Spring Boot 3.5.14（spring-kafka 由 Boot BOM 管理）、`apache/kafka:3.7.0`（KRaft 单节点），与微服务指南 §9 基线一致。
- 与微服务指南 §9 互链：§9 保持「三步接入」速成职责，末尾添加指向新指南的导航（小改动）；Kafka Streams / Kafka Connect / Spring Cloud Stream / 事务 exactly-once 归入延伸阅读导航。
- 遵循 guide-writing skill 规范，运行 `validate_guide.py` 做结构校验；沿用仓库既有指南风格（问题驱动、三步接入、生产实践、速查清单、延伸阅读）。

## Capabilities

### New Capabilities

- `kafka-guide`: 定义 Kafka 深度指南的内容契约——指南定位（RabbitMQ 指南的对称姊妹篇、微服务指南 §9 的深度续篇）、渐进式 9 章结构、交易流水管道场景、日志模型组织主线、KRaft 本地环境与版本锚定、与既有文档的边界。

### Modified Capabilities

<!-- 无既有 spec 的需求变化 -->

## Impact

- 新增文档：`docs/Spring/kafka-spring-boot-guide.md`。
- 微服务指南 `docs/Spring/spring-cloud-microservices-guide.md` §9 末尾添加一行导航链接（小改动）。
- 不引入任何依赖、不改动 `src/` 与 `pom.xml`；指南内的 Compose 配置服务于本地学习环境。
- 与 `docs/Spring/rabbitmq-spring-boot-guide.md` 形成对称姊妹篇；与 `docs/Kubernetes/kubernetes-guide.md` §12.3（Kafka 上 k8s）形成延伸阅读互链。
