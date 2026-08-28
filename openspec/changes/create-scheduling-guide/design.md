# Design: create-scheduling-guide

## Context

`docs/Spring/` 已有 20+ 篇指南，其中 `spring-event-guide.md` 确立了"问题起源 → 分层递进 → 速查清单"的渐进式（progressive）风格；guide-writing skill 提供了模板与内容契约。本篇要写 Spring 定时任务主题，参考项目 finchina-data-mcp-server-ex 的实际代码可作为实战素材：启动类标有 `@EnableScheduling` 但全项目无 `@Scheduled` 任务方法；`@PostConstruct` 中设置 `TimeZone` 为 `Asia/Shanghai`；`MultiTaskScheduler` 是业务并发线程池（Future + await），与 Spring 调度无关。

## Goals / Non-Goals

**Goals:**

- 产出一篇渐进式指南，读者按顺序阅读即可建立"定时任务如何工作"的完整心智模型。
- 覆盖用户指定的五个阶段：问题起源、最简用法、`cron`/`fixedRate`/`fixedDelay` 选型、默认单线程问题与自定义调度线程池、实战章节结合参考项目。
- 示例诚实分级（fragment / 未验证完整示例），版本锚定 Spring Boot 3.x / Spring Framework 6.x。

**Non-Goals:**

- 不讲线程池通用基础（`docs/Java/multithreading-basics.md` 已覆盖，链接即可）。
- 不覆盖分布式定时任务方案（Quartz、Elastic Job、XXL-JOB 等），仅在结尾给出一句话导航。
- 不改动任何应用代码，不改动参考项目。

## Decisions

- **指南形状：progressive。** `@Scheduled` 话题是典型因果链——默认单线程会阻塞其他任务 → 因此需要自定义线程池 → 因此引出 `SchedulingConfigurer`。后续章节依赖前面建立的概念，符合 progressive 的判定标准。
- **版本锚点：Spring Boot 3.x（3.5+）/ Spring Framework 6.x。** `spring.task.scheduling.pool.size` 等配置项与 Boot 3 保持一致；`fixedRate` 中 `@Scheduled` 的 `timeUnit` 属性自 Spring 5.3.10 / 6.0 起可用。
- **线程池章节双路径并列：配置项优先，`SchedulingConfigurer` 兜底。** `spring.task.scheduling.*` 是 Boot 官方推荐的最小配置方式；`SchedulingConfigurer` 提供编程式完全控制（自定义 `ThreadPoolTaskScheduler` 或任意 `TaskScheduler` Bean）。用户点名两者都要覆盖。
- **实战章节采用"辨析"叙事：** 以参考项目真实代码为证据，回答三个问题——注解已开为何没有任务执行、`TimeZone` 与 cron 的关系、`MultiTaskScheduler` 为什么不是调度线程池。避免"表扬式"引用，聚焦概念澄清。
- **示例状态：illustrative fragment。** 指南示例是可运行的片段但不会在本次会话中启动 Spring 上下文验证（见 Risks），故不标注为 verified runnable。

## Risks / Trade-offs

- [示例未实际运行，`spring.task.scheduling` 配置键名或 `@Scheduled` 属性行为可能与声明的版本有偏差] → 示例统一标注 Illustrative fragment，不宣称已执行；配置项与注解属性对照 Spring Boot 3.5 / Spring Framework 6.2 官方文档核实后再落笔；交付时在 Verification summary 中列出。
- [与 `multithreading-basics.md` 内容重叠，读者可能两篇都读而感到重复] → 指南 Scope 节明确边界：线程池通用概念（core/max/queue）归该文档；本篇仅解释"调度器线程池"的增量行为（单线程默认、池满时的调度语义）。
- [参考项目细节未来可能变化（例如某天新增了 `@Scheduled` 任务）] → 实战章节标注观察日期与代码位置（文件 + 行号），并说明该观察是"写作时快照"。
