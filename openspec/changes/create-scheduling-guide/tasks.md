# Tasks: create-scheduling-guide

## 1. 素材核实

- [x] 1.1 确认参考项目素材：`JavaMcpDemoApplication` 中 `@EnableScheduling` 与 `TimeZone.setDefault` 的代码位置及行号
- [x] 1.2 确认参考项目无 `@Scheduled` 任务方法（全项目搜索 `@Scheduled`）
- [x] 1.3 确认 `MultiTaskScheduler` / `ThreadPoolConfig` 的用途是业务并发而非定时调度

## 2. 指南撰写

- [x] 2.1 开头：读者契约、学习目标、适用版本（Spring Boot 3.x / Spring Framework 6.x）与范围外声明
- [x] 2.2 第 1 章：为什么需要定时任务（问题起源，紧耦合定时逻辑的痛点）
- [x] 2.3 第 2 章：`@EnableScheduling` + `@Scheduled` 最简用法（注解的作用、最简任务、启动观察）
- [x] 2.4 第 3 章：`cron` / `fixedRate` / `fixedDelay` / `initialDelay` 语义与选型对比（含 ASCII 时序图）
- [x] 2.5 第 4 章：默认单线程调度器的问题演示，以及 `spring.task.scheduling.pool.size` 配置项与 `SchedulingConfigurer` 两种自定义线程池方式
- [x] 2.6 第 5 章：实战辨析——结合参考项目的三个观察（注解开了但无任务、时区 Asia/Shanghai 与 cron `zone`、`MultiTaskScheduler` 不是调度线程池），标注代码位置与观察快照
- [x] 2.7 速查清单：`@Scheduled` 属性选型表、线程池配置键表、常见坑清单
- [x] 2.8 每个示例标注 Illustrative fragment 状态，不宣称已运行

## 3. 校验与收尾

- [x] 3.1 运行 `py -3 .qoder\skills\guide-writing\scripts\validate_guide.py docs\Spring\spring-scheduling-guide.md`（或等价路径）确保结构校验通过
- [x] 3.2 复核目录锚点、章节编号、前后引用一致
- [x] 3.3 对照 spec 的 Requirement 逐条自查，交付 Verification summary
