## Why

`docs/Java/multithreading-basics.md` §7（CompletableFuture）把 `supplyAsync`/`runAsync` 的第二个参数（`Executor`）当作事后警告一笔带过，从未正式介绍；而指定线程池恰恰是生产环境最常见的用法（下游 Spring 指南按能力归属选型：服务层固定后台能力用 `@Async`，Controller/协调层临时扇出用 `supplyAsync(task, ioTaskExecutor)`）。同一节还遗漏了与执行器直接相关的 `*Async` 回调线程模型、异常家族（`handle`/`whenComplete`）和超时能力（`orTimeout`/`completeOnTimeout`），且 §7 的示例代码与 §5"生产环境禁用 Executors 快捷工厂"的结论自相矛盾。这些问题让读者在真实项目中无法正确选择执行器和编排异步链。

## What Changes

- **扩充** `docs/Java/multithreading-basics.md` §7，保持现有"问题 → 机制 → 权衡"节奏，新增约 2~3 个小节：
  - 正式引入 `Executor` 第二参数：四签名表（`runAsync`/`supplyAsync` × 默认/显式执行器）、`Executor` 定义与 §5 交叉引用、commonPool 三个事实（并行度 = 核数 − 1、守护线程、与 `parallelStream` 共享）
  - 新增"回调在哪个线程执行"小节：同步变体 vs `*Async` 变体的执行位置规则（`*Async` 默认 commonPool，无沿链继承）、"已完成 Future + 同步回调在当前线程立即执行"陷阱
  - 扩充异常与超时：`exceptionally` / `handle` / `whenComplete` 对照表，`orTimeout` / `completeOnTimeout`（JDK 9+，在 JDK 17 范围内）
  - 小补丁：`thenRun`（补齐链式三兄弟）、`complete(T)`/`completeExceptionally` 手动完成、`anyOf` 返回 `Object` 的类型安全替代 `applyToEither`/`acceptEither`
- **修复** §7 与 §5 的连贯性矛盾：§7 的"正确做法"示例改用有界队列的 `ThreadPoolExecutor`（与 §5 建议一致），不再使用 `Executors.newFixedThreadPool`
- **明确不引入**：`delayedExecutor`、`minimalCompletionStage`、`newIncompleteFuture`、`copy` 等低频 API（入门指南按重要性而非 API 数量分配篇幅）
- **文档边界**：超时 API 的语义由本指南（Java 层）拥有，Spring 指南保留 `@Async` 语境下"超时 ≠ 任务终止"的教训，两篇互链不重复

## Capabilities

### New Capabilities

- `java-multithreading-guide`: `docs/Java/multithreading-basics.md` 内容需求——§7 必须正式介绍执行器参数、回调线程模型、异常与超时能力，并与 §5 的线程池结论保持一致

### Modified Capabilities

<!-- 无现有 capability 涉及本指南；openspec/specs/ 下均为菜谱 API 相关 spec -->

## Impact

- `docs/Java/multithreading-basics.md`: 修改 §7（约 663–813 行区域），预计新增 80~120 行；§5 与 §8 可能各有一处交叉引用调整
- `openspec/specs/java-multithreading-guide/spec.md`: 新增 capability spec
- 无代码修改、无依赖变更、无向后兼容影响（纯文档交付物）
- 下游 `docs/Spring/spring-boot-multithreading-guide.md` 引用本指南，内容不冲突（边界在 design.md 中界定）
