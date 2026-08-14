## ADDED Requirements

### Requirement: 执行器参数正式介绍

指南的 CompletableFuture 小节 SHALL 把 `runAsync`/`supplyAsync` 的第二个参数 `Executor` 作为正式知识点介绍，而不是仅作为事后警告。介绍内容 SHALL 包括：四签名对照表（`runAsync`/`supplyAsync` × 默认执行器/显式执行器）、`Executor` 接口的最小定义与 §5（线程池）的交叉引用、`ForkJoinPool.commonPool()` 的三个事实（并行度约为 CPU 核数 − 1 且最少为 1、工作线程为守护线程、与 `parallelStream()` 共享同一池）。

#### Scenario: 读者定位执行器参数

- **WHEN** 读者阅读"创建异步任务"小节
- **THEN** 能说出 `supplyAsync(Supplier, Executor)` 的第二个参数含义、不传时的默认行为，以及如何复用 §5 构造的有界 `ThreadPoolExecutor`

#### Scenario: 显式执行器示例有来源

- **WHEN** 示例中出现自定义执行器变量（如 `myExecutor`）
- **THEN** 该变量在邻近代码中有构造来源，或通过交叉引用指向 §5 的构造方式，不凭空出现

### Requirement: 回调线程模型（\*Async 变体）

指南 SHALL 新增"回调在哪个线程执行"的内容：同步变体（`thenApply`/`thenAccept`/`thenRun` 等）在完成上一阶段的线程上执行（若上一阶段已完成则在当前线程同步执行）；`*Async` 变体把回调提交到执行器执行，未显式指定时默认在 `ForkJoinPool.commonPool()` 上执行（其并行度不足 2 时降级为每个任务新建线程），不存在沿链继承；每个 `*Async` 方法都有带 `Executor` 参数的重载可显式指定。指南 SHALL 指出"已完成 Future + 同步回调"在调用者线程立即执行这一陷阱。

#### Scenario: 读者区分同步与异步变体

- **WHEN** 读者需要决定回调用 `thenApply` 还是 `thenApplyAsync`
- **THEN** 能依据"回调是否阻塞 / 是否想让回调离开当前线程"作出选择

#### Scenario: 已完成 Future 的陷阱被指出

- **WHEN** 指南讲解 `completedFuture(...)` 后链式调用同步回调
- **THEN** 明确指出回调在当前线程立即执行，不经过任何线程池

### Requirement: 异常处理家族完整对照

指南 SHALL 以对照表形式介绍 `exceptionally`、`whenComplete`、`handle`：触发时机（仅异常 / 两条路径 / 两条路径）、返回值形态（同类型 / 透传原结果 / 可转换类型），并给出 `handle` 覆盖成功与失败两条路径的示例。

#### Scenario: 读者选择异常处理方法

- **WHEN** 读者面对"异常兜底 + 成功后处理"的编排需求
- **THEN** 能依据对照表选择 `handle` 或 `exceptionally` + `whenComplete` 的组合，并说明理由

### Requirement: 超时能力与文档边界

指南 SHALL 介绍 `orTimeout(timeout, unit)` 与 `completeOnTimeout(fallback, timeout, unit)`（JDK 9+，指南目标版本 JDK 17 范围内）的语义差异，并链接到 Spring 多线程指南中"超时 ≠ 任务终止"的教训；Spring 指南 SHALL 保留 `@Async` 语境下的超时内容且不重复 Java 层的 API 语义。

#### Scenario: 读者为远程调用设置超时

- **WHEN** 读者编排远程调用并需要超时兜底
- **THEN** 能区分"超时后异常结束"（`orTimeout`）与"超时后返回兜底值"（`completeOnTimeout`）并正确选用

#### Scenario: 两篇指南不重复

- **WHEN** 读者先后阅读 Java 指南与 Spring 指南的超时内容
- **THEN** Java 指南拥有 API 语义，Spring 指南拥有 `@Async` 语境教训，两者互链且无明显重复段落

### Requirement: 高频小 API 补齐

指南 SHALL 补齐以下高频点：`thenRun`（与 `thenApply`/`thenAccept` 并列的链式三兄弟）、`complete(T)`/`completeExceptionally(Throwable)` 手动完成（适配回调式 API）、`anyOf` 返回 `Object` 需强转的问题及类型安全替代 `applyToEither`/`acceptEither` 的一句话指引。

#### Scenario: 读者补齐链式方法认知

- **WHEN** 读者需要"只执行动作、不消费结果"的链式步骤
- **THEN** 能选择 `thenRun` 而非 `thenAccept` + 空 lambda

#### Scenario: 读者适配回调式 API

- **WHEN** 读者需要把回调式第三方 API 桥接为 `CompletableFuture`
- **THEN** 能找到 `complete`/`completeExceptionally` 的用法说明

### Requirement: 与 §5 线程池结论一致

指南 §7 中所有生产环境示例 SHALL 与 §5"生产环境禁用 `Executors` 快捷工厂、显式使用有界队列的 `ThreadPoolExecutor`"的结论保持一致，不得出现 `Executors.newFixedThreadPool(...)` 作为"正确做法"的示例。

#### Scenario: 读者按 §5 结论校验 §7 示例

- **WHEN** 读者依次学习 §5 与 §7 的线程池示例
- **THEN** 两处结论一致，无相互矛盾的示例代码

### Requirement: 低频 API 明确排除

指南 SHALL NOT 介绍 `delayedExecutor`、`minimalCompletionStage`、`newIncompleteFuture`、`copy` 等低频 API；这些 API 不出现于指南正文。

#### Scenario: 指南保持入门定位

- **WHEN** 读者通读 §7
- **THEN** 不遇到上述低频 API 名称（References 除外），篇幅控制在入门指南定位内
