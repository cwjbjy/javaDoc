# Spec: scheduling-guide

## ADDED Requirements

### Requirement: 指南定位与读者契约

指南 SHALL 面向有一般编程经验、刚接触 Spring 定时任务的开发者，在开头明确读者画像、学习目标、假定知识与范围外内容，并声明适用版本为 Spring Boot 3.x / Spring Framework 6.x。

#### Scenario: 读者契约要素齐全

- **WHEN** 读者打开指南开头
- **THEN** 能看到读者画像、学习目标、适用版本与明确的非目标声明

#### Scenario: 范围外内容有导航

- **WHEN** 指南涉及线程池通用概念或分布式调度
- **THEN** 指南链接到既有文档（如 `multithreading-basics.md`）或给出一句话导航，而不是展开讲解

### Requirement: 渐进式章节结构

指南 SHALL 按依赖顺序组织章节：问题起源 → `@EnableScheduling` + `@Scheduled` 最简用法 → `cron` / `fixedRate` / `fixedDelay` 选型 → 默认单线程调度器问题与自定义调度线程池 → 实战辨析 → 速查清单。每个后续章节 SHALL 只引入依赖前面概念的新知识。

#### Scenario: 章节顺序遵循依赖链

- **WHEN** 读者按顺序阅读指南
- **THEN** 不会遇到"稍后解释"式的前向引用，每个新概念出现前其前置概念已建立

#### Scenario: 速查清单可独立查阅

- **WHEN** 读者已读完指南需要快速回顾
- **THEN** 速查清单以表格或要点形式汇总 `@Scheduled` 属性选型、线程池配置键与常见坑

### Requirement: 核心机制讲解准确性

指南 SHALL 准确讲解 `@EnableScheduling` 的作用（注册 `ScheduledAnnotationBeanPostProcessor` 使 `@Scheduled` 生效）、`@Scheduled` 的 `cron` / `fixedRate` / `fixedDelay` / `initialDelay` 语义（含 `fixedDelay` 与 `fixedRate` 的区别：前者从上次执行完成起算、后者按固定周期触发可重叠排队）、以及默认调度器的单线程行为（一个任务阻塞会拖累所有任务）。

#### Scenario: 选型对比有判别依据

- **WHEN** 读者面对一个具体定时场景
- **THEN** 指南提供的对比能让读者判断该用 `cron`、`fixedRate` 还是 `fixedDelay`，并说明选错会有什么可观察后果

#### Scenario: 注解与任务的关系明确

- **WHEN** 读者看到只有 `@EnableScheduling` 而无 `@Scheduled` 方法的代码
- **THEN** 读者能判断：应用不会执行任何定时任务，但注解本身无害

### Requirement: 自定义调度线程池双路径

指南 SHALL 覆盖两种自定义调度线程池方式：`spring.task.scheduling.pool.size` 等配置项（Spring Boot 自动配置 `ThreadPoolTaskScheduler`）与 `SchedulingConfigurer` 编程式配置，并说明各自适用场景与优先级（显式 Bean 优先于配置项）。

#### Scenario: 配置项路径可复制

- **WHEN** 读者只想扩大调度线程数
- **THEN** 指南给出 `application.yml` 中 `spring.task.scheduling.pool.size` 的最小配置片段及默认值对照

#### Scenario: 编程式路径可复制

- **WHEN** 读者需要自定义线程名、拒绝策略等细粒度控制
- **THEN** 指南给出 `SchedulingConfigurer` 的完整片段，包含自定义 `ThreadPoolTaskScheduler` 的关键参数说明

### Requirement: 实战章节结合参考项目真实代码

指南 SHALL 在实战章节引用 finchina-data-mcp-server-ex 的真实代码：启动类 `JavaMcpDemoApplication` 已标 `@EnableScheduling` 但项目内无 `@Scheduled` 任务方法；`@PostConstruct` 中 `TimeZone.setDefault("Asia/Shanghai")` 与 cron 的 `zone` 属性的关系；`MultiTaskScheduler` / `ThreadPoolConfig` 是业务并发线程池（Future + await）而非调度线程池。

#### Scenario: 读者能分清两类"调度"

- **WHEN** 读者在参考项目中看到 `@EnableScheduling` 与 `MultiTaskScheduler` 并存
- **THEN** 指南能让读者解释：前者开启 Spring 定时任务能力（当前无任务生效），后者是业务并发执行工具，两者互不依赖

#### Scenario: 时区问题有结论

- **WHEN** 读者部署在国内时区环境使用 cron 表达式
- **THEN** 指南说明 JVM 默认时区与 cron `zone` 属性的关系及推荐做法，并以参考项目的 `Asia/Shanghai` 设置为例

#### Scenario: 引用可追溯

- **WHEN** 读者想核对实战章节的代码引用
- **THEN** 指南标注引用代码的文件路径与写作时观察快照说明

### Requirement: 写作风格与结构校验

指南 SHALL 遵循 guide-writing skill 的 content contract（示例诚实分级、依赖顺序、术语先定义后使用、导航清晰），保持与 `spring-event-guide.md` 一致的文风（中文叙述、代码注释中文、ASCII 图示、分节递进），并通过 `validate_guide.py` 结构校验。

#### Scenario: 示例状态诚实

- **WHEN** 指南展示代码示例
- **THEN** 每个示例标注 Illustrative fragment 或相应状态，未实际运行的不宣称已验证

#### Scenario: 结构校验通过

- **WHEN** 指南文件保存后运行 `py -3 scripts/validate_guide.py`
- **THEN** 校验器报告通过，目录锚点有效
