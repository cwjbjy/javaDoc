# Tasks: create-kafka-guide

## 1. 前置核实与准备

- [x] 1.1 核实 Spring Boot 3.5.14 BOM 管理的 spring-kafka 具体版本（官方依赖坐标页确认 3.3.16），锁定指南版本声明
- [x] 1.2 核对微服务指南 §9 基线（apache/kafka:3.7.0 KRaft Compose 配置、JSON 字符串序列化约定、MANUAL ack 容器工厂），确保指南与之对齐
- [x] 1.3 核对 rabbitmq-spring-boot-guide.md 的章节结构与写作风格，保持姊妹篇对称

## 2. 指南撰写（第 1~4 章：开场 + 日志模型 + 环境 + 收发）

- [x] 2.1 开头：读者契约、学习目标、版本锚定、范围外声明、姊妹篇/续篇关系
- [x] 2.2 第 1 章：为什么需要 Kafka——交易流水管道场景问题驱动（四下游消费组 + 新下游回放）
- [x] 2.3 第 2 章：日志模型——一条消息的完整旅程（Topic / Partition / offset / 追加写 / 消费后保留）
- [x] 2.4 第 3 章：启动本地 Kafka——KRaft 单节点 Compose（对齐 §9 配置）+ 启动验证
- [x] 2.5 第 4 章：Spring Boot 第一次收发——依赖、配置、KafkaTemplate、@KafkaListener
- [x] 2.6 第 1~2 章按读者反馈重组（修复"图示先于概念"反模式）：1.1 同步调用痛点先行、场景图去掉未定义的"消费组 A~D"术语；1.2 新增难题→机制→章节对照表；2.1 四个核心概念 bottom-up（Broker→Topic→Partition→offset）；2.2 旅程图在概念就位后呈现并逐步回扣；2.3 消费组独立成节（兑现多方订阅+回放承诺）；2.4 三大能力改为与难题对应的推导表；2.5 保留 RabbitMQ 对照

## 3. 指南撰写（第 5~7 章：顺序 + 可靠性 + 消费端）

- [x] 3.1 第 5 章：顺序与并行——分区与消费者组（key 定分区、分区内有序、并行上限、再均衡）
- [x] 3.2 第 6 章：可靠性——acks=all、ISR、min.insync.replicas、重试与幂等生产者
- [x] 3.3 第 7 章：消费端——offset 提交语义、重复消费、DefaultErrorHandler + 死信主题

## 4. 指南撰写（第 8~9 章：选型 + 延伸）

- [x] 4.1 第 8 章：Kafka vs RabbitMQ 选型——日志模型 vs 队列模型增量视角，导航到 §9.5 对照表
- [x] 4.2 第 9 章：延伸阅读与参考资料——Kafka Streams / Connect / Spring Cloud Stream / 事务、k8s 指南 §12.3 导航

## 5. 导航互链

- [x] 5.1 在微服务指南 §9 末尾添加指向本指南的导航链接（小改动）
- [x] 5.2 复核与 rabbitmq-spring-boot-guide.md、k8s 指南 §12.3 的互链闭环（三处出链已补全）

## 6. 校验与收尾

- [x] 6.1 运行 `py -3 .agents/skills/guide-writing/scripts/validate_guide.py docs/Spring/kafka-spring-boot-guide.md` 确保结构校验通过
- [x] 6.2 复核目录锚点、章节编号、前后引用一致
- [x] 6.3 对照 spec 的 Requirement 逐条自查，交付 Verification summary（示例证据状态如实分级）
