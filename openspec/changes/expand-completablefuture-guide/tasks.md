## 1. 修复 §7 与 §5 的矛盾

- [x] 1.1 对照 JDK 17 javadoc 核对 commonPool 三事实（并行度、守护线程、与 parallelStream 共享），记录到核对笔记
- [x] 1.2 将 §7 权衡小节的 `Executors.newFixedThreadPool(10)` 示例改为有界队列的 `ThreadPoolExecutor`（复用 §5 的构造参数风格），并加一句话指向 §5
- [x] 1.3 复查 §7 全文，确认不再出现 `Executors` 快捷工厂作为生产"正确做法"

## 2. 正式引入 Executor 第二参数

- [ ] 2.1 在"创建异步任务"小节补四签名对照表：`runAsync(Runnable)`、`runAsync(Runnable, Executor)`、`supplyAsync(Supplier)`、`supplyAsync(Supplier, Executor)`
- [ ] 2.2 补 `Executor` 接口最小定义（"§5 的 ThreadPoolExecutor 实现了它"）与 §5 交叉引用
- [ ] 2.3 用核对过的事实改写"默认使用 ForkJoinPool.commonPool()"段落（并行度、守护线程、共享池）
- [ ] 2.4 消除示例中 `myExecutor` 凭空出现的问题——变量给出构造来源或指向 §5

## 3. 新增"回调在哪个线程执行"小节

- [ ] 3.1 在"链式编排"之后新增小节：同步变体在完成上一阶段的线程上执行；`*Async` 变体提交到执行器
- [ ] 3.2 写明 `*Async` 变体默认执行器规则：未显式指定时在 commonPool 上执行（并行度 < 2 时每任务新线程），不存在沿链继承；显式指定用带 Executor 的三参版本（已按 JDK 17 源码核对）
- [ ] 3.3 指出"已完成 Future + 同步回调在当前线程立即执行"的陷阱，复用 completedFuture 示例
- [ ] 3.4 补同步/异步变体选择表（阻塞回调 → `*Async` + 专用池；轻量转换 → 同步版本），与权衡小节呼应

## 4. 扩充异常家族与超时

- [ ] 4.1 在 §7 异常相关内容处补 `exceptionally` / `whenComplete` / `handle` 对照表（触发时机、返回值形态、类比）
- [ ] 4.2 补 `handle` 覆盖成功与失败两条路径的 Illustrative 示例，替换或简化现有 exceptionally 行内示例
- [ ] 4.3 新增 `orTimeout(timeout, unit)` 与 `completeOnTimeout(fallback, timeout, unit)` 语义对照（标注 JDK 9+）
- [ ] 4.4 超时处加一句话反向链接 Spring 指南"超时 ≠ 任务终止"教训；确认 Spring 指南 Scope 已有正向链接

## 5. 高频小 API 补齐

- [ ] 5.1 在链式编排小节补 `thenRun`，补齐链式三兄弟（转换 / 消费 / 只执行动作）
- [ ] 5.2 在 completedFuture 小节附近补 `complete(T)` / `completeExceptionally(Throwable)` 手动完成（适配回调式 API 的场景一句话 + 片段）
- [ ] 5.3 在 anyOf 示例处补一句话指引：类型安全替代 `applyToEither` / `acceptEither`，不展开示例
- [ ] 5.4 复查全文，确认未引入 `delayedExecutor`、`minimalCompletionStage`、`newIncompleteFuture`、`copy` 等低频 API

## 6. 校验与收尾

- [ ] 6.1 运行 `py -3 .agents/skills/guide-writing/scripts/validate_guide.py docs/Java/multithreading-basics.md`，修复目录锚点与代码围栏问题
- [ ] 6.2 复查 §7 新增内容总量（目标 80~120 行）与渐进节奏（问题 → 机制 → 权衡）
- [ ] 6.3 用 grep 复查与 Spring 指南超时段落无重复；核对 §8 场景速查表是否需要同步微调
- [ ] 6.4 对照 spec 的 7 条 requirement 逐条自查场景满足情况
