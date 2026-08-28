# Proposal: create-scheduling-guide

## Why

`docs/Spring/` 已覆盖事件、缓存、事务、IoC 等主题，但缺少 Spring 定时任务（`@EnableScheduling` + `@Scheduled`）的指南。参考项目 `D:\dzh\finchina-data-mcp-server-ex` 在启动类上标了 `@EnableScheduling` 却没有任何 `@Scheduled` 任务，且其 `MultiTaskScheduler` 自定义线程池常被误认为定时调度——这两个事实正好构成一篇"澄清概念 + 系统入门"指南的切入点。

## What Changes

- 新增 `docs/Spring/spring-scheduling-guide.md`：渐进式（progressive）指南，从"为什么需要定时任务"讲起，逐步覆盖 `@EnableScheduling` + `@Scheduled` 最简用法、`cron` / `fixedRate` / `fixedDelay` 选型、默认单线程调度器问题与自定义调度线程池（`SchedulingConfigurer` 与 `spring.task.scheduling` 配置项）。
- 指南含"实战"章节，结合 finchina-data-mcp-server-ex 真实代码：启动类已标注解但无任务方法、`TimeZone` 设为 `Asia/Shanghai` 与 cron 时区的关系、`MultiTaskScheduler` 是业务并发线程池而非调度线程池。
- 遵循 guide-writing skill 规范与 `spring-event-guide.md` 的写作风格（问题起源 → 分层递进 → 速查清单），并运行 `validate_guide.py` 做结构校验。

## Capabilities

### New Capabilities

- `scheduling-guide`: 定义 Spring 定时任务指南的内容契约——指南定位、渐进式章节结构、示例规范、与参考项目实战章节的结合要求、以及与既有文档（`multithreading-basics.md`、`spring-event-guide.md`）的边界。

### Modified Capabilities

<!-- 无既有 spec 的需求变化 -->

## Impact

- 新增文档：`docs/Spring/spring-scheduling-guide.md`（对 `docs/Spring/` 文档目录的唯一影响，无代码变更）。
- 不引入任何依赖、不改动 `src/` 与 `pom.xml`。
- 与 `docs/Java/multithreading-basics.md` 存在主题相邻性，需在指南中声明边界（线程池基础归该文档，本篇只讲调度增量）。
