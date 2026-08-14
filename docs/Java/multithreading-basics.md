# Java 多线程基础

> Audience: 有 Java 基础语法经验、首次接触多线程的开发者
> Outcome: 能根据场景正确选用 synchronized / volatile / Lock / 线程池 / 原子类 / CompletableFuture，理解每条链路的代价和边界
> Applicable version: JDK 17

## Scope

多线程编程的入口问题不是"有哪些 API"，而是"我的数据被多个线程同时访问时，怎样保证正确"。这篇指南沿着这个问题的递进链条展开：从最基础的互斥（synchronized）出发，到更轻量的可见性保证（volatile），再到更灵活的控制（Lock），然后解决线程本身的创建成本（线程池），接着用无锁方案替代锁（原子类），最后用声明式编排替代阻塞等待（CompletableFuture）。

**涵盖**: 上述链条中的关键机制、使用场景、选型权衡。
**不涵盖**: `Future` 接口（JDK 8 起由 `CompletableFuture` 取代，不单独介绍）、响应式编程、JMM 形式化定义、`ThreadLocal`、ForkJoinPool 自定义。

## 目录

- [1. 为什么需要多线程](#1-为什么需要多线程)
- [2. 互斥：让共享数据安全](#2-互斥让共享数据安全)
- [3. 可见性：比锁更轻的保证](#3-可见性比锁更轻的保证)
- [4. 显式锁：当 synchronized 不够用](#4-显式锁当-synchronized-不够用)
- [5. 线程池：管理线程的生命周期](#5-线程池管理线程的生命周期)
- [6. 原子类：不用锁也能安全计数](#6-原子类不用锁也能安全计数)
- [7. CompletableFuture：异步编排](#7-completablefuture异步编排)
- [8. 场景速查与下一步](#8-场景速查与下一步)

---

## 1. 为什么需要多线程

### 问题：单线程不够快

一个 HTTP 请求从接收、查数据库、调第三方服务到返回结果，大量时间花在等待上。如果一次只处理一个请求，CPU 大部分时间空闲，吞吐量极低。

多线程让一个进程内同时运行多个执行路径。当线程 A 等待数据库响应时，操作系统可以切换到线程 B 去处理下一个请求——CPU 不再空转。

```
进程: 操作系统分配资源的单位（独立内存空间）
线程: 进程内的执行单元（共享堆和方法区，独享栈）
```

以 Chrome 浏览器为例：每个标签页是一个独立的渲染进程（崩溃不互相影响），每个进程内又有主线程（解析 HTML/CSS、布局、绘制）、合成线程、工作线程。这种层次结构让 UI 保持流畅的同时，还能并行处理网络请求和脚本执行。

### 创建线程

创建线程就是一件事：把你要执行的代码用 lambda 传给 `Thread`，然后调 `start()`。

> Illustrative fragment

```java
// lambda 里的代码会在新线程中执行
new Thread(() -> {
    System.out.println("在新线程中执行");
}).start();
```

`start()` 做了两件事：在操作系统层面创建新线程，然后在线程内执行你传入的 lambda。如果直接调用 `thread.run()`，代码会在当前线程执行，不会创建新线程。

> 不推荐继承 `Thread` 重写 `run()`。Java 单继承，继承了 `Thread` 就不能再继承其他类——耦合太重。

### 线程的代价

Java 线程是 1:1 映射到操作系统内核线程的——这不是轻量协程。每个线程默认分配约 1 MB 栈空间，创建和上下文切换都有成本。作为对比：

|          | Java 传统线程 | Go goroutine         | JDK 21 虚拟线程         |
| -------- | ------------- | -------------------- | ----------------------- |
| 栈空间   | ~1 MB 固定    | ~2 KB 起步，动态增长 | 由 JVM 管理，按需分配   |
| 创建上限 | 几千          | 百万级               | 百万级                  |
| 调度者   | OS 内核       | Go 运行时            | JVM（复用少量 OS 线程） |

在 JDK 17 下我们仍使用传统线程，所以需要合理控制线程数量：

| 任务类型                   | 建议线程数   | 原因                                          |
| -------------------------- | ------------ | --------------------------------------------- |
| CPU 密集型（计算、加密）   | CPU 核数 + 1 | 某线程因缺页中断时，备用线程顶上              |
| I/O 密集型（网络、数据库） | CPU 核数 × 2 | 等待 I/O 时线程阻塞不占 CPU，少数线程即可覆盖 |

### 线程控制方法

在线程间协调执行顺序时，用三个基本方法就够了：

> Illustrative fragment

```java
// sleep: 暂停当前线程（不释放已持有的锁）
Thread.sleep(2000);

// join: 等待目标线程执行完毕
Thread downloadThread = new Thread(() -> downloadFile());
downloadThread.start();
downloadThread.join();           // 主线程阻塞，等下载完成
downloadThread.join(5000);       // 带超时：最多等 5 秒

// interrupt: 协作式中断 —— 设置标志位，由目标线程自行检查
Thread worker = new Thread(() -> {
    while (!Thread.currentThread().isInterrupted()) { // 检查中断标志
        System.out.println("工作中...");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            break;               // 收到中断信号，退出
        }
    }
});
worker.start();
Thread.sleep(3000); // 主线程等3秒
worker.interrupt();              // 发出中断请求
```

**关键认知**：`interrupt()` 不会强制终止线程——它只设置一个标志位。线程需要主动检查 `isInterrupted()` 或在 `sleep`/`wait`/`join` 中捕获 `InterruptedException` 来响应。这是 Java 并发设计的核心哲学：**协作而非强制**。

> 现在有了多个线程，它们同时访问同一个对象的数据会怎样？下一节解决这个问题。

---

## 2. 互斥：让共享数据安全

### 问题：数据竞争

两个线程同时对一个 `int count` 执行 `count++`。`count++` 看似一步，实际拆成三步：读 → 加 → 写。线程交错执行时：

```
线程 A: 读 count=0  → 加得 1  →  写回 1
线程 B:    读 count=0  → 加得 1  →  写回 1（覆盖！）
结果：count=1，但应该是 2。
```

这种"读写改"不被整体保护的竞态条件，是多线程 bug 的最大来源。

### 机制：synchronized

`synchronized` 保证同一时刻只有一个线程进入被保护的代码块。它用对象的内置锁（monitor）实现互斥。Java 中每个对象都关联一个 monitor，线程进入 `synchronized` 块前必须先获取它。

锁的是**对象**，不是代码。三种形式锁的是不同的对象：

> Illustrative fragment

```java
public class Counter {
    private int count = 0;
    private final Object lock = new Object();

    // 形式一：锁实例方法 —— 锁 this
    public synchronized void increment() {
        count++;    // 等价于 synchronized(this) { count++; }
    }

    // 形式二：锁静态方法 —— 锁 Counter.class
    public static synchronized void reset() {
        // 等价于 synchronized(Counter.class) { ... }
    }

    // 形式三：锁指定对象 —— 最精细
    public void decrement() {
        synchronized (lock) {
            count--;
        }
    }
}
```

**实例锁和静态锁是两把不同的锁**，它们之间不互斥。一个线程持有 `this` 锁执行 `increment()` 的同时，另一个线程可以持有 `Counter.class` 锁执行 `reset()`——因为它们锁的是不同对象。

### 验证：线程安全计数器

> Illustrative fragment

```java
Counter counter = new Counter();

Thread t1 = new Thread(() -> {
    for (int i = 0; i < 1000; i++) counter.increment();
});
Thread t2 = new Thread(() -> {
    for (int i = 0; i < 1000; i++) counter.increment();
});

t1.start(); t2.start();
t1.join();  t2.join();
System.out.println(counter.getCount());   // 2000 —— 始终正确
```

### 权衡

`synchronized` 的优势是简单——没有 `lock()`/`unlock()` 配对问题，JVM 会自动优化（偏向锁、轻量级锁、锁粗化）。代价是它只有两种状态：获取到锁或阻塞等待。你不能说"试一下，拿不到就算了"，也不能设置超时。当这些需求出现时，看 §4 的 `Lock`。

> synchronized 解决了"同时改"的问题。但如果一个线程只写、另一个只读，真的需要锁吗？下一节介绍更轻的方案。

---

## 3. 可见性：比锁更轻的保证

### 问题：读到的可能是过时值

现代 CPU 有多级缓存。线程 A 修改变量后，新值可能只存在于 CPU 缓存或寄存器中，尚未写回主内存。线程 B 在另一个核心上读同一变量时，可能读到的是自己缓存中的旧值——这就是**可见性**问题。

`synchronized` 能解决可见性（进入和退出 monitor 都会刷新缓存），但它的互斥语义太重了。如果一个变量只需要"写后立即可读"，不需要排斥并发写，有没有更轻的方案？

### 机制：volatile

`volatile` 修饰的变量享有两条保证：

1. **可见性**：写操作立即刷新到主内存，读操作强制从主内存读取。
2. **禁止指令重排序**：编译器不会把 `volatile` 变量的读写重排到内存屏障的另一侧。

但它**不保证原子性**。这是理解 `volatile` 的关键限制。

### volatile 不能替代 synchronized 的场景

回到 `count++` 的例子——如果用 `volatile`：

```
volatile int count = 0;

线程 A: 读 count=0（volatile 保证读到最新值 ✓）
线程 B: 读 count=0（A 还没写回，读到 0 也正确 ✓）
线程 A: 加得 1，写回（volatile 保证立即刷新 ✓）
线程 B: 用旧值 0 加得 1，写回 1（覆盖！✗）
// 两次 +1，结果只有 1
```

`volatile` 保证了**每一次读写**的可见性，但阻止不了"读-改-写"三步之间的线程交错。这是它和 `synchronized` 的本质区别。

### volatile 的正确场景：状态标志

> Illustrative fragment

```java
public class Worker implements Runnable {
    private volatile boolean running = true;  // 多个线程可见

    @Override
    public void run() {
        while (running) {
            doWork();               // running 变化时，下一轮循环立即看到
        }
    }

    public void stop() {
        running = false;            // 写操作立即对其他线程可见
    }
}
```

这个场景满足 volatile 的全部前提：写入不依赖当前值，多个线程只读不写，只有一个写线程。

### 权衡

|      | synchronized                                  | volatile                 |
| ---- | --------------------------------------------- | ------------------------ |
| 保证 | 原子性 + 可见性 + 有序性                      | 可见性 + 有序性          |
| 开销 | 较重（获取/释放 monitor）                     | 很轻（内存屏障）         |
| 适用 | 复合操作（check-then-act, read-modify-write） | 独立读写（标志位、开关） |

**选择法则**：如果写入依赖当前值（`count++`、`if (flag) flag = false`），用 `synchronized` 或原子类；如果只是独立的读写（状态标志、配置开关），用 `volatile`。

> volatile 解决了"写完要立即可见"的问题，但没有解决"拿不到锁时想放弃"的问题。下一节介绍显式锁。

---

## 4. 显式锁：当 synchronized 不够用

### 问题：synchronized 的三种局限

`synchronized` 好用，但有三件事它做不到：

1. **非阻塞尝试**：线程要么拿到锁，要么无限期阻塞。不能说"试一下，拿不到就做别的"。
2. **超时放弃**：没有"等 500 毫秒还拿不到就算了"。
3. **读写分离**：读操作之间不需要互斥，但 `synchronized` 会强制所有操作串行。

`java.util.concurrent.locks.Lock` 接口正是为解决这些局限而生。

### ReentrantLock：可中断、可超时、可尝试

"可重入"意味着同一线程可以多次获取同一把锁而不会死锁——每次 `lock()` 内部计数 +1，每次 `unlock()` 计数 -1，计数归零才真正释放。

> Illustrative fragment

```java
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class TaskQueue {
    private final Lock lock = new ReentrantLock();

    public void process() {
        lock.lock();                // 阻塞获取（等效 synchronized）
        try {
            doWork();
        } finally {
            lock.unlock();          // 必须在 finally 中，防止死锁
        }
    }
}
```

`synchronized` 做不到的操作：

> Illustrative fragment

```java
// 非阻塞尝试
if (lock.tryLock()) {
    try { doWork(); }
    finally { lock.unlock(); }
} else {
    handleBusy();                   // 拿不到锁时执行备选逻辑
}

// 带超时：最多等 1 秒
if (lock.tryLock(1, TimeUnit.SECONDS)) {
    try { doWork(); }
    finally { lock.unlock(); }
}

// 可中断：等锁期间响应 interrupt()
try {
    lock.lockInterruptibly();       // 收到中断信号就抛异常，否则尝试获取锁，如果拿不到？→ 阻塞等待，但保持对中断信号的响应
    try { doWork(); }
    finally { lock.unlock(); }
} catch (InterruptedException e) {
    // 等锁时被中断，可以优雅退出
}
```

### ReentrantReadWriteLock：读写分离

在缓存场景中，大多数操作是读取，极少写入。`ReentrantReadWriteLock` 维护一对锁：

- **读锁（共享）**：多个线程可同时持有，只有没有写锁时才授予
- **写锁（独占）**：只有一个线程持有，排斥所有读写

> Illustrative fragment

```java
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ConfigCache {
    private final Map<String, String> cache = new HashMap<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();

    // 读操作（多个线程可同时读）
    public String get(String key) {
        rwLock.readLock().lock();
        try { return cache.get(key); }
        finally { rwLock.readLock().unlock(); }
    }

    // 写操作（独占）
    public void put(String key, String value) {
        rwLock.writeLock().lock();
        try { cache.put(key, value); }
        finally { rwLock.writeLock().unlock(); }
    }
}
```

### 权衡

|                | synchronized         | Lock                                 |
| -------------- | -------------------- | ------------------------------------ |
| 加解锁         | 隐式，JVM 管理       | 显式，必须 finally unlock            |
| tryLock / 超时 | 不支持               | 支持                                 |
| 读-写分离      | 不支持               | ReentrantReadWriteLock               |
| 公平性         | 非公平（不保证顺序） | 可选公平模式                         |
| 使用建议       | 简单场景首选         | 需要 tryLock / 超时 / 读写分离时使用 |

**原则**：优先用 `synchronized`。只有当明确需要 `tryLock`、超时、或读写分离时，才升级到 `Lock`。代码简洁本身也是安全性——忘了 `unlock()` 是真实的 bug。

> 现在你有了保护共享数据的工具。但每次创建新线程执行任务太昂贵了——下一节解决线程的复用问题。

---

## 5. 线程池：管理线程的生命周期

### 问题：创建线程的成本

每次 `new Thread().start()` 都在请求操作系统分配内核线程——分配约 1 MB 栈空间、初始化线程上下文、加入调度队列。任务执行完线程就被销毁，下次再来还得重复。如果每个 HTTP 请求都新建线程，服务在请求量上来之前就会因为线程创建开销而崩溃。

线程池的思路很简单：提前创建一批线程放着，任务来了分配一个，用完归还，不销毁。

### 为什么不推荐 Executors 快捷工厂

JDK 提供了四类快捷方法：

| 方法                                  | 队列                       | 风险               |
| ------------------------------------- | -------------------------- | ------------------ |
| `Executors.newFixedThreadPool(n)`     | 无界 LinkedBlockingQueue   | 任务积压耗尽堆内存 |
| `Executors.newCachedThreadPool()`     | 无界 SynchronousQueue 行为 | 线程数无限增长     |
| `Executors.newSingleThreadExecutor()` | 无界 LinkedBlockingQueue   | 任务积压耗尽堆内存 |
| `Executors.newScheduledThreadPool(n)` | 无界 DelayedWorkQueue      | 任务积压耗尽堆内存 |

它们的共同问题：**内部队列容量为 `Integer.MAX_VALUE`**（约 21 亿）。当任务提交速度快于处理速度时，任务对象在堆上无限堆积，直到 `OutOfMemoryError`。

> 这些快捷方法适合演示和一次性脚本，不适合生产环境。生产环境中用 `ThreadPoolExecutor` 显式控制所有参数。

### 核心机制：ThreadPoolExecutor

线程池的任务分配逻辑：

```
提交任务
  │
  ├─ 当前线程数 < corePoolSize? ──是──▶ 创建新线程（即使有空闲线程）
  │
  ├─ 队列未满? ──是──▶ 入队等待
  │
  ├─ 当前线程数 < maximumPoolSize? ──是──▶ 创建新线程
  │
  └─ 以上都不满足 ──▶ 执行拒绝策略
```

理解这个流程的关键：**线程池优先增长到 corePoolSize，然后优先使用队列，队列满了才继续增长到 maximumPoolSize**。所以队列容量直接决定了缓冲能力。

> Illustrative fragment

```java
import java.util.concurrent.*;

ThreadPoolExecutor executor = new ThreadPoolExecutor(
    5,                                  // corePoolSize: 平时保留的线程数
    10,                                 // maximumPoolSize: 峰值上限
    60L, TimeUnit.SECONDS,              // 非核心线程空闲 60s 后回收
    new LinkedBlockingQueue<>(100),     // 有界队列：最多积压 100 个任务
    new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略
);
```

四种拒绝策略的选择：

| 策略                  | 行为                            | 适用             |
| --------------------- | ------------------------------- | ---------------- |
| `AbortPolicy`（默认） | 抛出 RejectedExecutionException | 关键任务         |
| `CallerRunsPolicy`    | 由提交任务的线程自己执行        | 利用调用者做缓冲 |
| `DiscardPolicy`       | 静默丢弃                        | 日志等非关键任务 |
| `DiscardOldestPolicy` | 丢弃队列头部（最旧的）          | 优先保证最新数据 |

#### 提交任务：execute vs submit

线程池接收任务的入口有两个核心方法，选错可能让你丢失异常信息：

| 方法                         | 参数                  | 返回值      | 异常处理                                                                |
| ---------------------------- | --------------------- | ----------- | ----------------------------------------------------------------------- |
| `execute(Runnable)`          | Runnable              | void        | 未捕获的异常由线程的 `UncaughtExceptionHandler` 处理，默认打印到 stderr |
| `submit(Callable<T>)`        | Callable              | `Future<T>` | 异常被包装进 Future，调用 `future.get()` 时以 `ExecutionException` 抛出 |
| `submit(Runnable)`           | Runnable              | `Future<?>` | 同上                                                                    |
| `submit(Runnable, T result)` | Runnable + 预设返回值 | `Future<T>` | 同上，任务成功时 `get()` 返回预设值                                     |

**关键区别**：`execute` 提交的任务如果抛出异常，异常会直接扩散到线程的 `UncaughtExceptionHandler`；`submit` 会把异常吞掉，必须通过 `future.get()` 才能捕获。

> Illustrative fragment

```java
// execute: 无返回值，异常直接暴露
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    5, 10, 60L, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(100),
    new ThreadPoolExecutor.AbortPolicy()
);

executor.execute(() -> {
    System.out.println("执行任务，不需返回值");
});

// submit + Callable: 有返回值，异常包装在 Future 中
Future<Integer> future = executor.submit(() -> {
    Thread.sleep(1000);
    return 42;
});

// 阻塞等待结果，抛异常时会包装为 ExecutionException
Integer result = future.get();

// submit + Runnable: 只关心任务是否完成
Future<?> done = executor.submit(() -> doWork());
done.get();  // 完成返回 null，异常则抛 ExecutionException
```

`Future.get()` 支持超时等待，避免无限阻塞：

```java
try {
    Integer result = future.get(5, TimeUnit.SECONDS);  // 最多等 5 秒
} catch (TimeoutException e) {
    future.cancel(true);       // 超时取消，内部调用 interrupt()
}
```

#### 批量任务：invokeAll 与 invokeAny

`ThreadPoolExecutor` 继承自 `AbstractExecutorService`，还提供了两个批量方法：

| 方法                                 | 行为                                                 |
| ------------------------------------ | ---------------------------------------------------- |
| `invokeAll(Collection<Callable<T>>)` | 提交所有任务，**等全部完成**后返回 `List<Future<T>>` |
| `invokeAny(Collection<Callable<T>>)` | 提交所有任务，**任一完成**即返回其结果，其余取消     |

```java
// invokeAll: 并行计算各月营收，等全部完成后汇总
List<Callable<Integer>> tasks = Arrays.asList(
    () -> calculateMonthlyRevenue(1),   // 1 月
    () -> calculateMonthlyRevenue(2),   // 2 月
    () -> calculateMonthlyRevenue(3)    // 3 月
);
List<Future<Integer>> results = executor.invokeAll(tasks);
for (Future<Integer> f : results) {
    total += f.get();
}

// invokeAny: 从多个数据源取，谁先返回就用谁
String data = executor.invokeAny(Arrays.asList(
    () -> fetchFromPrimaryDB(),         // 主数据库
    () -> fetchFromCache(),             // 缓存
    () -> fetchFromBackupAPI()          // 备用接口
));
```

#### 线程池的关闭：shutdown vs shutdownNow

线程池不会自动关闭——即使你忘了调用 `shutdown()`，JVM 也不会退出（因为线程池的工作线程是非守护线程）。关闭的核心流程如下：

| 方法                        | 行为                                                                      |
| --------------------------- | ------------------------------------------------------------------------- |
| `shutdown()`                | 平缓关闭：拒绝新任务，但**等待已提交的（运行中的 + 队列里的）全部执行完** |
| `shutdownNow()`             | 强制关闭：拒绝新任务，**中断所有正在运行的线程**，返回队列中未执行的任务  |
| `awaitTermination(n, unit)` | 阻塞当前线程，等待线程池完成关闭，超时返回 `false`                        |
| `isShutdown()`              | 是否已调用过 `shutdown()` 或 `shutdownNow()`                              |
| `isTerminated()`            | `shutdown()` 后所有任务是否已执行完毕                                     |

`shutdown()` vs `shutdownNow()` 的区别：

```
shutdown():
  接收新任务 → 拒绝 ✓
  运行中的任务 → 继续执行 ✓
  队列中的任务 → 继续执行 ✓

shutdownNow():
  接收新任务 → 拒绝 ✓
  运行中的任务 → 调用 interrupt() 中断 ✗
  队列中的任务 → 丢弃，作为 List 返回 ✗
```

**注意**：`shutdownNow()` 通过 `interrupt()` 来中断线程，所以运行的**任务代码必须响应中断**（检查 `isInterrupted()` 或捕获 `InterruptedException`），否则线程不会真正停止。回到 §1 中 interrupt 的理念：协作而非强制。

> Illustrative fragment

```java
// 标准关闭流程：先尝试优雅关闭，超时再强制
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    5, 10, 60L, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(100),
    new ThreadPoolExecutor.AbortPolicy()
);

// 提交任务...
executor.execute(() -> doWork());

// 步骤一：拒绝新任务，等待已提交的执行完
executor.shutdown();

// 步骤二：最多等 60 秒
if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
    // 步骤三：超时，强制终止
    List<Runnable> abandoned = executor.shutdownNow();
    // 步骤四：再给 10 秒善后
    if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
        // 极端情况：任务不响应中断，线程僵死
    }
}
```

实际项目中这个关闭逻辑通常放在 `@PreDestroy` 或 Spring 的 `DisposableBean.destroy()` 中，由框架在应用停止时自动调用。

### 权衡：线程数量怎么定

这取决于任务是 CPU 密集型还是 I/O 密集型（见 §1）。但在实践中，更可靠的做法是**从保守值开始，用监控数据调整**——比如先设为核心数，观察 CPU 利用率和任务等待时间，再逐步上调。盲目使用公式比不设上限更危险。

> JDK 21 的虚拟线程改变了这个范式——虚拟线程极轻量，你甚至可以为每个任务创建一个而无需池化。但在 JDK 17 下，`ThreadPoolExecutor` 仍然是标准答案。

> 线程池解决了"线程复用"。但如果只是对一个计数器做 +1 操作，整个线程池 + Lock 的组合实在太重了。下一节用更轻的方式解决。

---

## 6. 原子类：不用锁也能安全计数

### 问题：锁对简单操作来说太重了

一个请求计数器，只做 `count++`。如果用 `synchronized`，每次 +1 都要获取 monitor、上下文切换、释放 monitor。在每秒百万次计数的场景中，锁竞争会成为瓶颈。

`java.util.concurrent.atomic` 包用 **CAS（Compare-And-Swap）** 替代锁。CAS 是现代 CPU 提供的原子指令，逻辑很直接：

> "内存位置 V 的值是 A 吗？是的话更新为 B，不是的话告诉我实际值。失败就重试。"

整个过程没有锁、没有线程阻塞、没有上下文切换——只在 CPU 指令层面自旋重试。

### AtomicInteger

> Illustrative fragment

```java
import java.util.concurrent.atomic.AtomicInteger;

public class RequestCounter {
    private final AtomicInteger count = new AtomicInteger(0);

    public int record() {
        return count.incrementAndGet();   // 原子 +1，无锁
    }

    public int get() {
        return count.get();               // volatile 语义，读到最新值
    }
}
```

常用操作：

| 方法                                      | 说明                     |
| ----------------------------------------- | ------------------------ |
| `incrementAndGet()` / `decrementAndGet()` | ±1 返回新值              |
| `getAndAdd(int delta)`                    | 增加 delta 返回旧值      |
| `compareAndSet(expect, update)`           | 等于 expect 才更新       |
| `updateAndGet(IntUnaryOperator)`          | 用 lambda 做任意原子更新 |

### LongAdder：高并发写场景的更优选择

`AtomicLong` 在高并发写时有一个瓶颈：所有线程竞争同一个变量，CAS 失败就重试，重试越多浪费越多。`LongAdder` 将单一值分散到多个 Cell 上——线程 A 更新 Cell[0]，线程 B 更新 Cell[1]，竞争大幅降低。读取时再汇总所有 Cell。

> Illustrative fragment

```java
import java.util.concurrent.atomic.LongAdder;

public class HitCounter {
    private final LongAdder hits = new LongAdder();

    public void hit() {
        hits.increment();           // 比 AtomicLong.incrementAndGet() 更快
    }

    public long total() {
        return hits.sum();          // 汇总（读少用）
    }
}
```

### 权衡

```
场景 → 工具:
  counter++ 且需要立即读值  →  AtomicInteger / AtomicLong
  高并发写、偶尔读总数       →  LongAdder
  引用类型 CAS               →  AtomicReference
  boolean 标志的 CAS         →  AtomicBoolean
```

**原子类不是 `synchronized` 的替代品**。它只解决单个变量的原子更新；保护一段代码（多个变量、条件判断 + 更新）仍然是 `synchronized` 或 `Lock` 的领地。

> 现在你知道如何安全地共享和更新数据。但前面的所有例子都在"等"——`join()` 等线程、`future.get()` 等结果、`lock.lock()` 等锁。等待意味着线程闲置，资源浪费。下一节用异步编排消除等待。

---

## 7. CompletableFuture：异步编排

### 问题：阻塞等待浪费线程

调用远程服务时，线程发出 HTTP 请求后就阻塞等待响应——这段时间它不能干任何事，但占用着约 1 MB 栈空间和 OS 调度资源。如果有 100 个并发请求各等 200ms，需要 100 个线程全部阻塞在那里。

异步编程的思路是：发起请求后**立即释放线程**，响应回来后用回调处理结果。`CompletableFuture`（JDK 8）把这个模式做成了声明式 API，使用体验和 JavaScript 的 Promise 非常接近：

```
JS:  fetch(url).then(res => res.json()).then(data => render(data))
Java: supplyAsync(() -> callApi()).thenApply(this::parse).thenAccept(this::render)
```

> `CompletableFuture` 实现了 `Future` 接口，但 `Future` 只提供阻塞的 `get()`，不支持回调和链式组合。JDK 8+ 中异步编程直接从 `CompletableFuture` 开始即可，不需要单独学习 `Future`。

### 创建异步任务

> Illustrative fragment

```java
import java.util.concurrent.CompletableFuture;

// 无返回值（类似 Runnable）
CompletableFuture<Void> log = CompletableFuture.runAsync(() -> {
    writeLog("请求已处理");
});

// 有返回值（类似 Callable）
CompletableFuture<String> user = CompletableFuture.supplyAsync(() -> {
    return userService.getById(userId);     // 返回类型自动推断
});
```

#### 第二个参数：指定执行线程池

`runAsync` 和 `supplyAsync` 都有一个带第二个参数 `Executor` 的重载，用来指定任务在哪个线程池上执行：

| 方法                                 | 任务执行位置                |
| ------------------------------------ | --------------------------- |
| `runAsync(Runnable)`                 | `ForkJoinPool.commonPool()` |
| `runAsync(Runnable, Executor)`       | 指定线程池                  |
| `supplyAsync(Supplier<T>)`           | `ForkJoinPool.commonPool()` |
| `supplyAsync(Supplier<T>, Executor)` | 指定线程池                  |

`Executor` 是一个极简接口（只有一个 `execute(Runnable)` 方法）——§5 构造的 `ThreadPoolExecutor` 就实现了它，那里学到的有界队列配置可以直接复用：

```java
CompletableFuture.supplyAsync(() -> callApi(), ioPool);   // ioPool：§5 的有界 ThreadPoolExecutor
```

不传第二个参数时，默认使用 `ForkJoinPool.commonPool()`。这个公共池有三个特点值得记住：

1. **并行度小**：约等于 CPU 核数 − 1（最少 1 个）——它是为短小计算任务设计的，不是为阻塞 I/O 设计的
2. **线程是守护线程**：当 JVM 里只剩 commonPool 的工作线程时，进程会直接退出，没跑完的异步任务随之消失
3. **全局共享**：`parallelStream()` 和整个 JVM 里所有未指定线程池的 `CompletableFuture` 都挤在这一个池里，阻塞任务会拖累所有人

所以生产环境里，涉及阻塞操作（数据库、HTTP）的异步任务一律传入 §5 那样配置的有界 `ThreadPoolExecutor`；commonPool 只留给计算密集的短任务。

### completedFuture：包装已有结果

有时结果已经算好了，不需要再启动异步任务，但调用方期望的是一个 `CompletableFuture`——例如在 Spring `@Async` 方法中作为返回值。

> Illustrative fragment

```java
// 创建一个已经完成的 Future，内部直接持有结果
CompletableFuture<String> done = CompletableFuture.completedFuture("hello");

done.isDone();  // true —— 无需等待
done.join();    // "hello" —— 立即返回，不阻塞
```

`completedFuture()` 和 `supplyAsync()` 的核心区别：

| 方法                        | 行为                                                  |
| --------------------------- | ----------------------------------------------------- |
| `supplyAsync(() -> work())` | 将任务提交到线程池，异步执行，返回**未完成**的 Future |
| `completedFuture(result)`   | 不执行任何代码，返回**已完成**、已持有结果的 Future   |

典型场景是 `@Async` 方法——代码已在异步线程上执行完毕，只需把结果包装成 `CompletableFuture` 返回给调用方继续链式编排：

```java
@Async
public CompletableFuture<ReportResult> generate(String reportId) {
    ReportResult result = buildReport(reportId);       // 已在异步线程执行完毕
    return CompletableFuture.completedFuture(result);  // 包装即可，无需再 supplyAsync
}
```

> JDK 9 还提供了 `failedFuture(Throwable)`，返回一个已完成但以异常结束的 Future，适合在异常处理中保持链式风格。

### 获取结果：join()

```java
// join() — 终止操作，阻塞等待 CompletableFuture 完成，返回计算结果
String s = future.join();
```

`join()` 返回 `T` 而非 `CompletableFuture<T>`，是**终止操作**——不能在其后链式调用 `exceptionally()`。正确做法是把 `exceptionally()` 放在 `join()` 之前，让它把异常完成转为正常完成，`join()` 就能拿到兜底值：

```java
String result = future
    .exceptionally(ex -> {          // 先处理异常：将失败转为兜底值
        log.error("任务失败", ex);
        return "default";
    })
    .join();                        // 再获取结果：此时不会抛异常
```

如果不用 `exceptionally()` 兜底，`join()` 遇到异常完成时会抛出 `CompletionException`，此时只能在调用处 try-catch。

> **何时需要 `join()`？** `join()` 的唯一作用是阻塞当前线程，把异步结果"拉回"同步世界——`String result = future.join()`。如果你不需要阻塞，完全可以用 `thenAccept(result -> { ... })` 在回调中消费结果，全程异步，连 `join()` 都不需要。

> **注意**：`CompletableFuture.join()` 与 `Thread.join()` 同名但完全不同。`Thread.join()` 等待一个**线程**执行完毕，返回 `void`，抛 `InterruptedException`（受检）；`CompletableFuture.join()` 等待一个**异步任务**的计算结果，返回 `T`，抛 `CompletionException`（非受检）。前者是底层线程协调，后者是高层任务编排。

### 链式编排：thenApply / thenCompose / thenAccept

这是 `CompletableFuture` 区别于传统阻塞编程的核心能力。每一步是对上一步结果的转换，整个链条描述了"先做什么、再做什么"的依赖关系，框架负责在线程间调度。

> Illustrative fragment

```java
CompletableFuture
    .supplyAsync(() -> orderService.findById(orderId))      // 1. 查订单
    .thenApply(order -> pricingService.calculate(order))    // 2. 算价格（同步转换）
    .thenCompose(price -> paymentService.payAsync(price))   // 3. 支付（返回新 CF，扁平化）
    .thenAccept(receipt -> notifyService.send(receipt))     // 4. 通知（终结操作）
    .exceptionally(ex -> {                                   // 5. 异常恢复
        log.error("订单处理失败", ex);
        return null;
    });
```

**`thenApply` vs `thenCompose`**：如果回调返回普通值，用 `thenApply`；如果回调返回另一个 `CompletableFuture`，用 `thenCompose`，否则你会得到 `CompletableFuture<CompletableFuture<T>>` 的嵌套。

### 组合多个任务

> Illustrative fragment

```java
var userFuture = CompletableFuture.supplyAsync(() -> getUser());
var orderFuture = CompletableFuture.supplyAsync(() -> getOrders());

// 两者都完成后再组合结果
var summary = userFuture.thenCombine(orderFuture, (user, orders) ->
    "用户 %s, 共 %d 笔订单".formatted(user.name(), orders.size())
);

// allOf: 等待全部完成（返回 CompletableFuture<Void>）
CompletableFuture.allOf(userFuture, orderFuture).join();

// anyOf: 任意一个完成即可（返回 CompletableFuture<Object>）
Object first = CompletableFuture.anyOf(userFuture, orderFuture).join();
```

### 权衡

使用 `CompletableFuture` 最大的陷阱不是 API 用错，而是**忘记指定线程池**。默认的 `ForkJoinPool.commonPool()` 是全局共享的——如果你的异步任务里有阻塞操作（数据库查询、HTTP 调用），会耗尽公共池的线程，拖慢整个 JVM 里所有其他 `CompletableFuture`。

正确做法：给阻塞型任务专用线程池（参数含义见 §5，不要用 `Executors` 快捷工厂）：

```java
// 为阻塞型异步任务创建专用线程池
ThreadPoolExecutor ioPool = new ThreadPoolExecutor(
    2, 4, 60L, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(100),
    new ThreadPoolExecutor.CallerRunsPolicy()
);
CompletableFuture.supplyAsync(() -> dbQuery(), ioPool);
```

另外，`CompletableFuture` 适合"一个请求需要组合多个远程调用"的场景。如果业务逻辑本身是同步的（比如单表 CRUD），引入异步反而增加心智负担，不值得。

> 到这里，你已经走完了从互斥到异步编排的整条链。最后一节把各工具放到一起对比，帮你建立快速选型直觉。

---

## 8. 场景速查与下一步

### 工具选择

| 你的场景                  | 用这个                              | 原因                           |
| ------------------------- | ----------------------------------- | ------------------------------ |
| 保护一段代码不被并发执行  | `synchronized`                      | 最简单，JVM 会自动优化         |
| 开关 / 标志位，多线程可见 | `volatile`                          | 最轻量的可见性保证             |
| 需要 tryLock、超时放弃    | `ReentrantLock`                     | `synchronized` 不支持          |
| 读多写少的缓存            | `ReentrantReadWriteLock`            | 读操作可并行                   |
| 生产环境管理线程          | `ThreadPoolExecutor`                | 有界队列防 OOM                 |
| 高并发计数器              | `LongAdder`                         | 分散热点，比 `AtomicLong` 更快 |
| 单个变量 CAS 更新         | `AtomicInteger` / `AtomicReference` | 精确 CAS 控制                  |
| 编排多个异步调用          | `CompletableFuture`                 | 声明式链式，替代阻塞等待       |

### 学习路径

这篇指南覆盖的是 JDK 17 下的多线程基础——它们是理解更高级并发工具的前提。建议先熟练这条链（synchronized → volatile → Lock → 线程池 → 原子类 → CompletableFuture），再进入以下方向：

- **JMM 深入**：happens-before 规则、final 字段语义——理解编译器重排序如何影响你的代码
- **ForkJoinPool**：工作窃取算法，`parallelStream()` 的底层引擎
- **JDK 21+ 虚拟线程**：`Thread.startVirtualThread()`，高吞吐 I/O 的新范式——当你理解了传统线程的代价，才能真正理解虚拟线程的价值
- **结构化并发**（JDK 21 incubator）：管理虚拟线程的生命周期边界
- **响应式编程**（WebFlux / RxJava）：背压和事件流——区别于 `CompletableFuture` 的请求-响应模式

> **原则**：当前这条链就是你的锚点。虚拟线程和响应式是这条链的延伸，不是替代。

---

## References

- [The Java Language Specification, Java SE 17 Edition — Chapter 17: Threads and Locks](https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html)
- [java.util.concurrent 包文档 (Java 17)](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html)
- [JEP 444: Virtual Threads](https://openjdk.org/jeps/444) — JDK 21 虚拟线程规范
- Bloch, J. _Effective Java_, 3rd Edition — Item 78–84 并发章节
