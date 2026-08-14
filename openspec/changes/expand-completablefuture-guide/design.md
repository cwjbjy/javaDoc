## Context

`docs/Java/multithreading-basics.md` 是 progressive 型入门指南（目标 JDK 17，受众为首次接触多线程的开发者），沿"问题 → 机制 → 权衡"链条展开。§7（CompletableFuture）现状：执行器参数只以一句话带过 + 权衡警告出现，`myExecutor` 变量无来源；§7 的"正确做法"示例使用 `Executors.newFixedThreadPool(10)`，与 §5"生产环境禁用快捷工厂"矛盾；`*Async` 变体、`handle`/`whenComplete`、`orTimeout`/`completeOnTimeout` 全部缺失。

下游 `docs/Spring/spring-boot-multithreading-guide.md` 按能力归属选型：服务层固定后台能力用 `@Async`（含返回 `CompletableFuture` 的形态），Controller/协调层临时扇出用 `supplyAsync(task, ioTaskExecutor)`，且 §5 用 `orTimeout` 讲了"超时 ≠ 任务终止"。本次改动必须与这两处保持边界清晰。

## Goals / Non-Goals

**Goals:**

- §7 正式引入 `Executor` 第二参数，读者能自己构造并传入有界线程池
- 读者理解"回调在哪个线程执行"，能区分同步变体与 `*Async` 变体
- 异常家族（`exceptionally`/`handle`/`whenComplete`）与超时（`orTimeout`/`completeOnTimeout`）补齐
- 消除 §5/§7 示例矛盾，保持全篇连贯
- 高频小 API（`thenRun`、`complete`/`completeExceptionally`、either 家族指引）补齐
- 保持入门定位：新增 80~120 行，不改变 §7 在目录中的位置（仅 H2 内小节变化）

**Non-Goals:**

- 不深入 `Future` 接口、不引入响应式编程、不涉及 JDK 21 虚拟线程（已有 Scope 声明）
- 不介绍低频 API：`delayedExecutor`、`minimalCompletionStage`、`newIncompleteFuture`、`copy`
- 不改写 §1–§6、§8 的既有结构，仅做必要的交叉引用调整
- 不修改 Spring 指南正文（本次只在必要时为互链做一处最小标注，或留待后续 change）

## Decisions

### 1. 结构落点：扩充现有小节 + 新增一个小节，而非新增独立大节

- "创建异步任务"小节内补四签名表 + `Executor` 最小定义 + commonPool 三事实，并引用 §5 构造有界池——因为执行器参数是"创建"动作的一部分
- 在"链式编排"之后新增小节"回调在哪个线程执行"——依赖链要求读者先见过 `thenApply` 等回调，再解释执行位置
- "join()" 之后、"链式编排"之后（或异常兜底示例处）扩充异常对照表；超时紧跟异常内容，复用"兜底值"心智
- 权衡小节保留"最大陷阱"结论，但示例改为有界 `ThreadPoolExecutor`，与 §5 一致

备选方案：新增独立"§7.5 进阶"大节装所有新内容——否决，因为执行器参数与创建/链式强耦合，拆开会破坏 progressive 节奏。

### 2. 边界切法：Java 指南拥有 API 语义，Spring 指南拥有框架语境教训

- `orTimeout`/`completeOnTimeout` 的 API 语义（签名、差异、兜底值）由本指南介绍
- Spring 指南 §5 的"超时不是强制终止"（底层任务不一定停止）是 `@Async` 语境教训，保留不搬移
- 两篇通过既有链接（Spring 指南 Scope 已链到本指南）互达；本指南超时处反向链接 Spring 指南教训一句话

### 3. `*Async` 默认执行器的表述精度（实施时已按源码修正）

核对 JDK 17 源码（`CompletableFuture.java`）确认：`thenApplyAsync(fn)` 等价于 `uniApplyStage(defaultExecutor(), fn)`，`defaultExecutor()` 恒返回 `ASYNC_POOL`（commonPool；其并行度 < 2 时降级为每任务新线程），**不存在沿链继承**——`supplyAsync(task, ioPool)` 的后代 `*Async` 不会自动使用 ioPool。指南写为：`*Async` 变体未显式指定执行器时默认在 commonPool 上执行；需要专用池时使用带 `Executor` 参数的三参版本。此结论以 JDK 17 源码为事实源。

### 4. 证据等级：全部保持 Illustrative fragment

与全篇既有风格一致：所有新增代码块标注为 Illustrative fragment，不声称可运行。事实性声明（commonPool 并行度、守护线程、orTimeout 为 JDK 9+）实施时对照 JDK 17 javadoc 核对，并遵循 guide-writing 的 verification 流程（`validate_guide.py` 校验结构）。

### 5. 篇幅控制：表格优先于散文

异常对照表、签名表、同步/异步变体对照表承担主要信息量，散文只写"为什么"。新增总量控制在 80~120 行，防止入门指南膨胀。

## Risks / Trade-offs

- [指南膨胀，偏离入门定位] → 低频 API 明确排除（spec 有 SHALL NOT 条款）；每处新增必须回答"读者会不会因此做出不同决策"
- [与 Spring 指南重复超时内容] → 决策 2 的边界切法 + 双链互达；实施后用 grep 复查两篇的超时段落
- [`*Async` 默认执行器规则写错误导读者] → 决策 3 已按 JDK 17 源码修正：默认恒为 commonPool，无沿链继承
- [`anyOf` 强转补充 either 家族可能稀释主线] → 只给一句话指引（"需要类型安全用 `applyToEither`/`acceptEither`"），不展开示例
- [行号锚点漂移] → 引用时用 § 号而非行号；下游 Spring 指南链接的是文件级相对路径，不受影响

## Migration Plan

纯文档改动，无部署与回滚问题。实施顺序：先改 §7 示例矛盾（最小修复），再按"创建 → 回调线程 → 异常/超时 → 小 API"顺序扩充，最后跑 `validate_guide.py` 校验目录锚点与代码围栏。

## Open Questions

- 是否需要同步给 Spring 指南加一句指向本指南超时小节的链接？（倾向：是，但可作为本次最小改动之一，待实施时确认）
- `applyToEither`/`acceptEither` 是否值得一句以上？（倾向：否，维持一句话指引）
