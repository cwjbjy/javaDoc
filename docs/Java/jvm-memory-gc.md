# 01-11 JVM 内存模型与垃圾回收（GC）

> Audience：有一年以上 Java 开发经验的工程师，需要理解 JVM 内存管理和 GC 工作原理
> Outcome：理解 JVM 运行时内存布局，掌握 GC 核心算法和收集器选型，能阅读 GC 日志并完成基本调优
> Applicable version：JDK 17（LTS）为主，辅以 JDK 21 关键差异标注

## Scope

本指南覆盖 JVM 运行时数据区（重点是堆）、垃圾回收的判定原理与基础算法、HotSpot 主流 GC 收集器的演进与选型，以及常见调优参数和 GC 日志的阅读方法。不涉及 JIT 编译、类加载机制、字节码层面的 GC 行为，也不覆盖 `finalize()`（JDK 9 已弃用，JDK 18 已彻底移除）。

<!-- TOC -->

## 目录

- [1. 为什么需要 GC？](#1-为什么需要-gc)
- [2. JVM 运行时数据区](#2-jvm-运行时数据区)
- [3. 堆内存的分代模型](#3-堆内存的分代模型)
- [4. 判断对象生死：可达性分析](#4-判断对象生死可达性分析)
- [5. 三种基础 GC 算法](#5-三种基础-gc-算法)
- [6. HotSpot GC 收集器](#6-hotspot-gc-收集器)
- [7. GC 调优实战](#7-gc-调优实战)
- [8. 常见 GC 问题与排查思路](#8-常见-gc-问题与排查思路)
- [快速参考](#快速参考)
- [References](#references)

---

## 1. 为什么需要 GC？

在 C/C++ 中，开发者通过 `malloc` / `free` 或 `new` / `delete` 手动管理内存。这种模式给了开发者完全的控制权，但代价高昂：

- **忘记释放** → 内存泄漏，长时间运行后 OOM
- **提前释放** → 悬空指针（dangling pointer），访问已释放内存导致未定义行为
- **重复释放** → 双重释放（double free），破坏内存分配器状态

Java 的选择是将内存回收的责任交给虚拟机本身——**不再由开发者决定"何时释放"，而是由 JVM 判断"哪些对象不再需要"。** 这就是垃圾回收（Garbage Collection, GC）的核心思想。

> **组织洞察**：GC 不是魔法——它是"标记垃圾 → 回收空间"这一简单原理在不同代际、不同场景下的工程优化。理解分代假说和可达性分析，就理解了为什么有那么多收集器。

GC 的代价是**运行时开销**：JVM 需要额外的 CPU 时间来执行垃圾回收，在某些时刻还需要暂停应用线程（Stop-The-World, STW）。这也是为什么 GC 调优的目标不是"消除 GC"，而是"将 GC 对业务的影响降到可接受的水平"。

---

## 2. JVM 运行时数据区

在理解 GC 之前，必须先知道 JVM 内存长什么样。JDK 17 的 JVM 运行时数据区（Runtime Data Areas）分为五大区域：

```
┌─────────────────────────────────────────────────────────┐
│                  JVM Runtime Data Areas                  │
├──────────────────────┬──────────────────────────────────┤
│     线程私有          │          线程共享                  │
├──────────────────────┼──────────────────────────────────┤
│                      │                                  │
│   ┌──────────────┐   │   ┌──────────────────────────┐   │
│   │ 程序计数器     │   │   │          Heap             │   │
│   │ (PC Register) │   │   │      ⭐ GC 主战场         │   │
│   └──────────────┘   │   │                          │   │
│                      │   │  Young Gen + Old Gen      │   │
│   ┌──────────────┐   │   │                          │   │
│   │  虚拟机栈      │   │   └──────────────────────────┘   │
│   │  (JVM Stack)  │   │                                  │
│   │              │   │   ┌──────────────────────────┐   │
│   │  栈帧 × N    │   │   │       Metaspace           │   │
│   │  ┌────────┐  │   │   │   (JDK 8+ 替代 PermGen)   │   │
│   │  │局部变量表│  │   │   │                          │   │
│   │  │操作数栈 │  │   │   │  类元数据、方法信息、       │   │
│   │  │动态链接 │  │   │   │  运行时常量池              │   │
│   │  │返回地址 │  │   │   └──────────────────────────┘   │
│   │  └────────┘  │   │                                  │
│   └──────────────┘   │                                  │
│                      │                                  │
│   ┌──────────────┐   │                                  │
│   │  本地方法栈    │   │                                  │
│   │ (Native Stack)│   │                                  │
│   └──────────────┘   │                                  │
│                      │                                  │
└──────────────────────┴──────────────────────────────────┘
```

| 区域           | 线程 | 内容                                                       | GC 是否管理            |
| -------------- | ---- | ---------------------------------------------------------- | ---------------------- |
| **程序计数器** | 私有 | 当前线程执行的字节码行号                                   | 否                     |
| **虚拟机栈**   | 私有 | 栈帧（局部变量表、操作数栈等），方法调用时创建，返回时销毁 | 否                     |
| **本地方法栈** | 私有 | Native 方法的调用状态                                      | 否                     |
| **堆 (Heap)**  | 共享 | 所有对象实例和数组                                         | **是，GC 主战场**      |
| **Metaspace**  | 共享 | 类的元数据、方法信息（使用本地内存，默认无上限）           | 否（但类卸载时会回收） |

**对于 GC 的关键认知**：GC 只管理堆内存。虚拟机栈上的引用变量本身会随着栈帧弹出而自动销毁，但**引用指向的堆中对象**需要通过 GC 来判断是否可以被回收。

---

## 3. 堆内存的分代模型

### 3.1 分代假说（Generational Hypothesis）

JVM 将堆分为新生代和老年代，基于两个关键观察：

- **弱分代假说**：绝大多数对象都是"朝生夕死"的——创建后很快就不再被引用
- **强分代假说**：熬过多次 GC 的对象倾向于存活更久

基于这两个假说，GC 对不同代采用不同的回收策略，从而在整体上获得更高的效率。

### 3.2 堆结构全景

```
┌─────────────────────────────────────────────────────────────┐
│                         JVM Heap                             │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌────────────── Young Generation ──────────────────┐       │
│  │                                                  │       │
│  │   ┌───────────────┐ ┌──────────┐ ┌──────────┐   │       │
│  │   │     Eden       │ │Survivor 0│ │Survivor 1│   │       │
│  │   │   (默认 8/10)   │ │ (1/10)   │ │ (1/10)   │   │       │
│  │   │               │ │          │ │          │   │       │
│  │   └───────┬───────┘ └────┬─────┘ └────┬─────┘   │       │
│  │           │              │            │         │       │
│  │           │  Minor GC：Eden + S0 → S1 (或反之)   │       │
│  │           │                                     │       │
│  └───────────┼─────────────────────────────────────┘       │
│              │  对象熬过阈值（默认 15 次 GC）后晋升              │
│              ▼                                              │
│  ┌──────────────────────────────────────────────┐          │
│  │              Old Generation                  │          │
│  │                                              │          │
│  │     Major GC / Mixed GC 发生在这里             │          │
│  │     Full GC 会同时回收 Old + Young + Metaspace │          │
│  │                                              │          │
│  └──────────────────────────────────────────────┘          │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**Young 和 Old 的默认比例**：`-XX:NewRatio=2`，即 Young : Old = 1 : 2。Eden 和两个 Survivor 的比例由 `-XX:SurvivorRatio=8` 控制，即 Eden : S0 : S1 = 8 : 1 : 1。

### 3.3 对象从生到死的旅程

```
创建对象
   │
   ▼
┌──────────────────────────────────────────────────────────────┐
│  1. 优先在 Eden 分配                                          │
│     - 大对象（可通过 -XX:PretenureSizeThreshold 指定）         │
│       直接进入 Old Gen                                        │
│     - TLAB (Thread Local Allocation Buffer)                   │
│       每个线程在 Eden 中的私有分配缓冲区，避免锁竞争              │
└──────────────┬───────────────────────────────────────────────┘
               │ Eden 满了，触发 Minor GC
               ▼
┌──────────────────────────────────────────────────────────────┐
│  2. Minor GC：复制存活对象到 Survivor                          │
│     - Eden 中存活的对象 → S0/S1                                │
│     - 上一次 GC 后在 Survivor 中的存活对象 → 另一个 Survivor    │
│     - 每次 GC 后，Survivor 中对象的"年龄" +1                   │
└──────────────┬───────────────────────────────────────────────┘
               │ 年龄 ≥ MaxTenuringThreshold（默认 15）
               ▼
┌──────────────────────────────────────────────────────────────┐
│  3. 晋升到 Old Generation                                     │
│     - 动态年龄判断：如果 Survivor 中某一年龄段的对象总大小       │
│       超过 Survivor 空间的一半，则该年龄段及以上对象直接晋升     │
└──────────────────────────────────────────────────────────────┘
               │ Old Gen 满了（或碎片过多）
               ▼
┌──────────────────────────────────────────────────────────────┐
│  4. Major GC / Full GC：清理 Old Generation                   │
│     - Old Gen 空间回收                                        │
│     - 如果 GC 后仍空间不足 → OutOfMemoryError: Java heap space  │
└──────────────────────────────────────────────────────────────┘
```

---

## 4. 判断对象生死：可达性分析

### 4.1 为什么不用引用计数？

引用计数法（Reference Counting）给每个对象维护一个被引用的次数，当计数为 0 时回收。**JVM 不采用这种方式**，因为无法解决循环引用：

```
┌──────────┐          ┌──────────┐
│  objA    │─────────▶│  objB    │
│ refCount │          │ refCount │
│   = 1    │◀─────────│   = 1    │
└──────────┘          └──────────┘

外部引用断开后，objA 和 objB 互相引用，refCount 永远 ≥ 1，
但这两个对象实际上已经不可达 → 内存泄漏
```

### 4.2 可达性分析（Reachability Analysis）

JVM 通过一组称为 **GC Roots** 的根对象作为起点，沿着引用链向下搜索，能被搜索到的对象就是**存活**的，反之则是**可回收的**。

```
                    GC Roots
                        │
           ┌────────────┼────────────┐
           ▼            ▼            ▼
        ┌──────┐    ┌──────┐    ┌──────┐
        │ Obj1 │    │ Obj2 │    │ Obj3 │   ← 可达（存活）
        └──┬───┘    └──────┘    └──────┘
           │
           ▼
        ┌──────┐    ┌──────┐
        │ Obj4 │   ─│ Obj5 │            ← 不可达（可回收）
        └──────┘    └──────┘
```

**GC Roots 包含以下四类**：

| GC Root 类型         | 说明                           | 示例                                            |
| -------------------- | ------------------------------ | ----------------------------------------------- |
| 虚拟机栈中引用的对象 | 当前正在执行的方法中的局部变量 | 方法内的 `Product p = new Product()`            |
| 静态属性引用的对象   | 类的 `static` 字段             | `public static final Cache cache = new Cache()` |
| 常量引用的对象       | 运行时常量池中的引用           | 字符串常量池中的字符串                          |
| JNI 引用的对象       | Native 方法中的全局引用        | Native 代码持有的 Java 对象                     |

### 4.3 四种引用类型

从 JDK 1.2 开始，Java 引入了四种引用类型，给 GC 行为提供了更精细的控制：

| 引用类型             | GC 行为                | 典型用途                            |
| -------------------- | ---------------------- | ----------------------------------- |
| **强引用 (Strong)**  | 绝不回收               | 默认的 `Object obj = new Object()`  |
| **软引用 (Soft)**    | 内存不足时回收         | 缓存，`SoftReference<T>`            |
| **弱引用 (Weak)**    | 下一次 GC 即回收       | `WeakHashMap`、`ThreadLocal` 的 key |
| **虚引用 (Phantom)** | 随时可回收，仅用于跟踪 | 对象销毁后的清理通知                |

> **Illustrative fragment**：以下展示了四种引用的基本使用，不是完整程序。

```java
import java.lang.ref.*;

// 强引用——绝不会被 GC 回收
Product product = new Product("iPhone");

// 软引用——仅在 OOM 前回收，适合做缓存
SoftReference<Product> softRef = new SoftReference<>(new Product("cache"));

// 弱引用——下一次 GC 就回收
WeakReference<Product> weakRef = new WeakReference<>(new Product("temp"));

// 虚引用——get() 永远返回 null，配合 ReferenceQueue 使用
ReferenceQueue<Product> queue = new ReferenceQueue<>();
PhantomReference<Product> phantomRef = new PhantomReference<>(new Product("cleanup"), queue);
```

---

## 5. 三种基础 GC 算法

所有 GC 收集器都是以下三种基础算法的组合或变体。

### 5.1 标记-清除（Mark-Sweep）

```
回收前：                      回收后：
┌──┬──┬──┬──┬──┬──┐         ┌──┬──┬────┬──┬────┐
│ A│ B│ C│ D│ E│ F│         │ A│ C│    │ F│    │
└──┴──┴──┴──┴──┴──┘         └──┴──┴────┴──┴────┘
存活: A, C, F               碎片！B、D、E 的空间散布
```

- **过程**：标记所有存活对象 → 清除未标记对象
- **优点**：简单，不需要移动存活对象
- **缺点**：产生内存碎片，碎片过多时大对象分配可能失败，触发 Full GC
- **适用**：老年代的基础算法（CMS 使用）

### 5.2 标记-复制（Mark-Copy）

```
回收前（From 区）：            回收后（To 区）：
┌──┬──┬──┬──┬──┬──┐         ┌──┬──┬──────────┐
│ A│ B│ C│ D│ E│ F│         │ A│ C│          │
└──┴──┴──┴──┴──┴──┘         └──┴──┴──────────┘
存活: A, C                  紧凑排列，无碎片
                            但浪费了一半空间
```

- **过程**：将存活对象复制到另一块区域，原区域整体清空
- **优点**：没有碎片，分配新对象只需移动指针（指针碰撞）
- **缺点**：可用内存减半；如果存活对象很多，复制开销大
- **适用**：新生代（大多数对象死亡，存活少 → 复制成本低）

> **这解释了为什么 Survivor 有两个**：S0 和 S1 其中一个始终为空，作为 Minor GC 时的目标空间。这也是为什么它们被称为 From 区和 To 区。

### 5.3 标记-整理（Mark-Compact）

```
回收前：                      回收后：
┌──┬──┬──┬──┬──┬──┐         ┌──┬──┬──────────┐
│ A│ B│ C│ D│ E│ F│         │ A│ C│ F│       │
└──┴──┴──┴──┴──┴──┘         └──┴──┴──┴───────┘
存活: A, C, F               存活对象向一端移动，无碎片
```

- **过程**：标记存活对象 → 将所有存活对象移动到一端 → 清空边界外内存
- **优点**：消除碎片，不需要两倍空间
- **缺点**：移动对象需要更新所有引用（Stop-The-World 时间更长）
- **适用**：老年代（Parallel Old、Serial Old 使用）

### 5.4 算法对比

| 算法      | 碎片 | 空间效率  | 时间开销       | 适用代        |
| --------- | ---- | --------- | -------------- | ------------- |
| 标记-清除 | 有   | 高        | 中             | 老年代（CMS） |
| 标记-复制 | 无   | 低（50%） | 低（存活少时） | 新生代        |
| 标记-整理 | 无   | 高        | 高（需移动）   | 老年代        |

---

## 6. HotSpot GC 收集器

GC 收集器是上述算法的具体实现。以下按演进顺序介绍，重点放在 JDK 17 的默认收集器和 JDK 21 的低延迟收集器。

### 6.1 收集器全景

```
                    ┌─────────────────────────┐
                    │    Young Generation      │
                    │                         │
  Serial            │  ┌───────┐  ┌───────┐   │         ┌──────────┐
  (单线程)           ──▶│ Copy  │──│   —   │   │────────▶│ Serial   │
                    │  └───────┘  └───────┘   │         │ Old      │
                    │                         │         │(MSC)     │
                    │  ┌───────┐  ┌───────┐   │         └──────────┘
  Parallel          │  │Parallel│  │Parallel│   │         ┌──────────┐
  (吞吐量优先)        ──▶│ Scavenge│──│ Scavenge│──▶──────│Parallel  │
                    │  └───────┘  └───────┘   │         │Old       │
                    │                         │         └──────────┘
                    │  ┌───────┐  ┌───────┐   │         ┌──────────┐
  CMS (JDK 9废弃)   │  │  Par  │  │  Par  │   │────────▶│CMS       │
  (低延迟)           ──▶│  New  │──│  New  │──▶         │(MarkSwp) │
                    │  └───────┘  └───────┘   │         └──────────┘
                    │                         │
                    │  ┌───────────────────┐  │         ┌──────────┐
  G1 ★JDK 17默认    │  │     G1 (分区式)     │  │────────▶│  G1      │
  (均衡)            ──▶│  Young+Mixed GC    │──▶         │(MarkSwp  │
                    │  │   + Humongous     │  │         │ +Copy)   │
                    │  └───────────────────┘  │         └──────────┘
                    │                         │
                    │  ┌───────────────────┐  │
  ZGC (JDK 11+)     │  │    ZGC (染色指针)   │  │         不分代
                    ──▶│   全并发 <1ms STW  │──▶        (JDK 21 引入
                    │  └───────────────────┘  │         分代 ZGC)
                    └─────────────────────────┘
```

> **JDK 17 重要变化**：JDK 9 将 CMS 标记为 Deprecated，JDK 14 正式移除 CMS。JDK 15 中 ZGC 和 Shenandoah 转为 Production 状态。

### 6.2 G1（Garbage First）—— JDK 17 默认收集器

**核心思想**：不再将堆严格划分为连续的新生代和老年代，而是将堆划分为大小相等的 **Region**（默认约 2048 个）。每个 Region 可以被标记为 Eden、Survivor、Old 或 Humongous（大对象）。

```
┌─────────────────────────────────────────────────────────────┐
│                        G1 Heap                               │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌────┬────┬────┬────┬────┬────┬────┬────┬────┬────┐       │
│  │ E  │ E  │ S  │ O  │ E  │ O  │ O  │ H  │ E  │Free│ ...   │
│  └────┴────┴────┴────┴────┴────┴────┴────┴────┴────┘       │
│                                                             │
│  E = Eden    S = Survivor    O = Old    H = Humongous       │
│                                                             │
│  GC 时优先回收"收益最高"的 Region（垃圾最多的） → Garbage First   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**G1 的 GC 阶段**：

| 阶段               | 类型                               | STW      | 说明                                               |
| ------------------ | ---------------------------------- | -------- | -------------------------------------------------- |
| Young GC           | 只回收 Young Region                | 是       | 将 Eden 存活对象复制到 Survivor，触发时机同分代 GC |
| Concurrent Marking | 并发标记 Old Region 的存活对象     | 否       | 与应用线程并发执行                                 |
| Remark             | 处理并发标记中的变化               | 是（短） | 最终标记阶段                                       |
| Mixed GC           | 回收部分 Old Region + Young Region | 是       | 回收垃圾比例高的 Old Region，可多次执行            |
| Full GC            | 回收整个堆                         | 是       | 当 Mixed GC 无法跟上分配速度时触发，应尽量避免     |

**G1 适用的场景**：堆大小 4 GB ~ 64 GB，要求 GC 暂停可预测（通过 `-XX:MaxGCPauseMillis` 设定目标）。

### 6.3 ZGC —— 超低延迟收集器

ZGC 从 JDK 11 引入（实验性），JDK 15 转为 Production。核心特点是**并发执行几乎所有 GC 阶段**，暂停时间控制在 **1ms 以内**，且不随堆大小增加而增长。

**核心技术**：

- **染色指针（Colored Pointers）**：在 64 位指针中嵌入 GC 状态信息，无需额外元数据
- **读屏障（Load Barrier）**：读取对象引用时检查指针颜色，在并发移动对象时保证正确性
- **并发整理**：在应用运行时移动对象，无需长时间 STW

**JDK 17 vs JDK 21**：

| 特性       | JDK 17                 | JDK 21                        |
| ---------- | ---------------------- | ----------------------------- |
| 默认模式   | 单代（不分 Young/Old） | **分代 ZGC**（默认启用）      |
| 适用堆大小 | 128 MB ~ 16 TB         | 同左                          |
| 分代支持   | 实验性                 | 默认开启 `-XX:+ZGenerational` |

分代 ZGC 通过将对象按年龄分代管理，在新对象分配和回收频率上更高效，尤其优化了吞吐量。

**ZGC 适用的场景**：堆 > 16 GB，要求极低延迟（< 1 ms 暂停），对吞吐量要求不那么极端的场景。

### 6.4 收集器选择速查

| 场景                            | 推荐收集器     | JVM 参数                |
| ------------------------------- | -------------- | ----------------------- |
| 小堆、单核、简单应用            | **Serial**     | `-XX:+UseSerialGC`      |
| 批处理、后台任务、吞吐量优先    | **Parallel**   | `-XX:+UseParallelGC`    |
| 大多数服务端应用（4~64 GB 堆）  | **G1** ★ 默认  | 无需指定（JDK 17 默认） |
| 大堆（> 16 GB）、极低延迟       | **ZGC**        | `-XX:+UseZGC`           |
| 大堆、低延迟（需非 Oracle JDK） | **Shenandoah** | `-XX:+UseShenandoahGC`  |

> **Illustrative fragment**：以下参数展示了如何在不同场景下指定 GC 收集器和关键参数。

```bash
# G1（JDK 17 默认，通常不需要显式指定）
java -Xms4g -Xmx4g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -jar app.jar

# ZGC（JDK 17+，极低延迟场景）
java -Xms16g -Xmx16g -XX:+UseZGC -jar app.jar

# JDK 21：启用分代 ZGC（默认即分代，显式指定为）
java -Xms16g -Xmx16g -XX:+UseZGC -XX:+ZGenerational -jar app.jar
```

---

## 7. GC 调优实战

### 7.1 调优三指标

GC 调优始终在三个指标之间权衡——你无法同时优化全部：

```
                     吞吐量
                    (Throughput)
                       ╱ ╲
                      ╱   ╲
                     ╱     ╲
                    ╱  选   ╲
                   ╱   两   ╲
                  ╱    个   ╲
                 ╱           ╲
                ╱─────────────╲
         内存占用              暂停时间
        (Footprint)          (Latency)
```

- **吞吐量**：应用线程时间 / 总时间（应用 + GC），目标通常是 > 99%
- **暂停时间**：单次 GC 造成的 STW 时长，目标通常 < 100 ms（G1）或 < 1 ms（ZGC）
- **内存占用**：堆大小和 GC 相关的额外开销

### 7.2 关键 JVM 参数

| 参数                          | 说明                       | 默认值（JDK 17）               |
| ----------------------------- | -------------------------- | ------------------------------ |
| `-Xms`                        | 初始堆大小                 | 物理内存的 1/64                |
| `-Xmx`                        | 最大堆大小                 | 物理内存的 1/4                 |
| `-Xmn`                        | 新生代大小（仅分代收集器） | —                              |
| `-XX:NewRatio`                | Old/Young 比例             | 2（Old 是 Young 的 2 倍）      |
| `-XX:SurvivorRatio`           | Eden/Survivor 比例         | 8                              |
| `-XX:MaxTenuringThreshold`    | 晋升老年代的最大年龄       | 15（G1 为 15，Parallel 为 15） |
| `-XX:MaxGCPauseMillis`        | 期望的最大 GC 暂停时间     | 200 ms（G1）                   |
| `-XX:G1HeapRegionSize`        | G1 Region 大小             | 堆大小 / 2048（1~32 MB）       |
| `-XX:+UseStringDeduplication` | 开启字符串去重             | false（G1 可开启）             |

> **Illustrative fragment**：一个典型 Spring Boot 服务端应用的 JVM 配置示例。

```bash
java \
  -Xms4g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+PrintGCDetails \
  -XX:+PrintGCDateStamps \
  -Xlog:gc*:file=gc.log:time,level,tags \
  -jar app.jar
```

### 7.3 GC 日志解读

从 JDK 9 开始，GC 日志统一使用 `-Xlog` 参数。以下是 JDK 17 下 G1 的一段典型 GC 日志：

> **Illustrative fragment**：以下日志片段来自 JDK 17 + G1 的典型输出，解释关键字段。

```text
[2026-01-15T10:30:15.123+0800][info][gc,start     ] GC(42) Pause Young (Normal) (G1 Evacuation Pause)
[2026-01-15T10:30:15.145+0800][info][gc             ] GC(42) Pause Young (Normal) (G1 Evacuation Pause) 450M->320M(1024M) 22.541ms
[2026-01-15T10:30:15.146+0800][info][gc,cpu         ] GC(42) User=0.08s Sys=0.01s Real=0.02s
```

**日志字段解读**：

| 字段                              | 含义                                                  |
| --------------------------------- | ----------------------------------------------------- |
| `GC(42)`                          | 第 42 次 GC                                           |
| `Pause Young (Normal)`            | 新生代 GC，Normal 表示常规触发                        |
| `G1 Evacuation Pause`             | G1 的"疏散"阶段——将存活对象复制到新 Region            |
| `450M->320M(1024M)`               | GC 前堆占用 450 MB → GC 后 320 MB（堆总大小 1024 MB） |
| `22.541ms`                        | 本次 GC 暂停耗时                                      |
| `User=0.08s Sys=0.01s Real=0.02s` | CPU 用户态/内核态耗时 vs 实际耗时                     |

**GC 日志中需要警惕的信号**：

```
# ⚠️ Full GC——应尽量避免
Pause Full (G1 Compaction Pause)

# ⚠️ 晋升失败——Survivor 或 Old 空间不足
To-space exhausted

# ⚠️ Humongous 分配——大对象触发 GC
Pause Young (Concurrent Start) (G1 Humongous Allocation)
```

### 7.4 调优流程

```
   ┌──────────────────────────────────────────────────────────┐
   │  GC 调优决策流程                                          │
   ├──────────────────────────────────────────────────────────┤
   │                                                          │
   │  ┌──────────┐    ┌──────────┐    ┌──────────┐            │
   │  │ 1. 定目标 │───▶│ 2. 选收集器│───▶│ 3. 调堆大小│           │
   │  └──────────┘    └──────────┘    └──────────┘            │
   │       │               │               │                  │
   │  吞吐量 >99%?      G1 优先          Xms = Xmx            │
   │  暂停 <200ms?     大堆选 ZGC      避免动态扩缩容          │
   │       │               │               │                  │
   │       ▼               ▼               ▼                  │
   │  ┌──────────┐    ┌──────────┐    ┌──────────┐            │
   │  │ 4. 调暂停 │───▶│ 5. 调晋升 │───▶│ 6. 观察  │            │
   │  └──────────┘    └──────────┘    └──────────┘            │
   │       │               │               │                  │
   │  MaxGCPause-       SurvivorRatio      GC 日志            │
   │  Millis 调小       MaxTenuring-    无 Full GC 即可        │
   │  (可能牺牲吞吐)     Threshold                             │
   │                                                          │
   └──────────────────────────────────────────────────────────┘
```

> **核心原则**：先确保没有 Full GC，再优化 Minor/Mixed GC 的暂停时间。日常调优的 80% 都可以通过调整堆大小 `-Xms` / `-Xmx` 解决。

---

## 8. 常见 GC 问题与排查思路

### 8.1 GC 频繁导致吞吐量下降

**症状**：GC 日志中 Minor GC 间隔非常短（几秒一次），应用 RT 抖动。

**排查**：

```bash
# 统计 GC 频率和耗时
jstat -gc <pid> 1000
# 输出示例：
# S0C    S1C    S0U    S1U      EC       EU        OC         OU
# 0.0   5120.0  0.0   5120.0 43008.0   2048.0   171008.0   85440.0
```

**常见原因和解决**：

| 原因         | 排查方法                     | 解决方案                   |
| ------------ | ---------------------------- | -------------------------- |
| 堆太小       | 查看 `-Xms` / `-Xmx`         | 增大堆内存                 |
| 新生代太小   | `jstat -gc` 看 Eden 填充速度 | 调大 `-Xmn` 或 NewRatio    |
| 短命对象过多 | GC 日志查看晋升情况          | 优化代码，减少临时对象创建 |

### 8.2 Full GC 频繁

**症状**：GC 日志中出现 `Pause Full`，每次暂停时间长（数百 ms 到几秒）。

**常见原因**：

```
Full GC 频繁的原因树
│
├─ 老年代持续增长
│  ├─ 内存泄漏（对象被无意中持有）
│  │  └─ 排查：heap dump → MAT/JProfiler 分析
│  │
│  ├─ 晋升过快（Survivor 太小，对象过早进入 Old）
│  │  └─ 解决：调大 SurvivorRatio、调大 MaxTenuringThreshold
│  │
│  └─ 大对象直接进入 Old Gen
│     └─ 排查：GC 日志搜索 "Humongous"
│
└─ Metaspace 不足
   ├─ 动态加载过多类（如动态代理、Groovy 脚本）
   └─ 解决：增大 -XX:MaxMetaspaceSize
```

### 8.3 OutOfMemoryError 排查

```java
// OOM 的常见变体
java.lang.OutOfMemoryError: Java heap space       // 堆空间不足
java.lang.OutOfMemoryError: Metaspace              // 元空间不足
java.lang.OutOfMemoryError: GC overhead limit exceeded  // GC 占用过多 CPU
```

**排查步骤**：

```bash
# 1. 启动时加 OOM 自动 dump
java -XX:+HeapDumpOnOutOfMemoryError \
     -XX:HeapDumpPath=/path/to/dump.hprof \
     -jar app.jar

# 2. 手动生成 heap dump
jmap -dump:format=b,file=dump.hprof <pid>

# 3. 查看当前堆使用情况
jmap -heap <pid>

# 4. 查看占用最多的类
jmap -histo:live <pid> | head -20
```

> **Illustrative fragment**：以上命令展示排查流程，不是可运行示例。需替换 `<pid>` 为实际进程 ID。

---

## 快速参考

### GC 收集器选型

| 收集器     | 目标暂停     | 堆大小       | 适用场景             | JDK 17 可用        |
| ---------- | ------------ | ------------ | -------------------- | ------------------ |
| Serial     | 几十 ms      | < 1 GB       | 客户端、嵌入式       | ✅（不推荐服务端） |
| Parallel   | 几百 ms      | 1~64 GB      | 批处理、吞吐量优先   | ✅                 |
| **G1** ★   | **< 200 ms** | **4~64 GB**  | **大多数服务端应用** | **✅ 默认**        |
| ZGC        | < 1 ms       | 128 MB~16 TB | 大堆、极低延迟       | ✅                 |
| Shenandoah | < 10 ms      | 4 GB~        | 大堆、低延迟         | ✅                 |

### 常用 JVM 参数速查

```bash
# 堆大小
-Xms2g -Xmx2g                      # 初始和最大堆（生产环境建议设为相等）

# G1（JDK 17 默认，显式指定参考值）
-XX:+UseG1GC                       # 启用 G1
-XX:MaxGCPauseMillis=200           # 期望最大暂停时间
-XX:G1HeapRegionSize=4m            # Region 大小（必要时调整）
-XX:+UseStringDeduplication        # 开启字符串去重

# ZGC
-XX:+UseZGC                        # 启用 ZGC（JDK 17+）
# JDK 21 中分代 ZGC 默认开启，无需额外参数

# GC 日志
-Xlog:gc*:file=gc.log:time,level,tags  # 统一 GC 日志（JDK 9+）

# OOM 诊断
-XX:+HeapDumpOnOutOfMemoryError    # OOM 时自动生成 heap dump
-XX:HeapDumpPath=./dump.hprof      # heap dump 路径

# Metaspace
-XX:MaxMetaspaceSize=256m          # 限制元空间大小
```

### GC 类型速查

| GC 类型          | 作用区域                | 触发时机            | STW | 典型耗时（G1） |
| ---------------- | ----------------------- | ------------------- | --- | -------------- |
| Minor / Young GC | Young Gen               | Eden 满了           | 是  | 10~50 ms       |
| Mixed GC（G1）   | Young + 部分 Old Region | 并发标记完成后      | 是  | 50~200 ms      |
| Major GC         | Old Gen                 | Old Gen 满了        | 是  | —              |
| Full GC          | 整个堆 + Metaspace      | 晋升失败 / 空间不足 | 是  | 数百 ms ~ 数秒 |

---

## References

- [JEP 248: Make G1 the Default Garbage Collector](https://openjdk.org/jeps/248) — JDK 9 中 G1 成为默认收集器
- [JEP 333: ZGC: A Scalable Low-Latency Garbage Collector](https://openjdk.org/jeps/333) — ZGC 引入（JDK 11，实验性）
- [JEP 377: ZGC: A Scalable Low-Latency Garbage Collector (Production)](https://openjdk.org/jeps/377) — ZGC 转为 Production（JDK 15）
- [JEP 439: Generational ZGC](https://openjdk.org/jeps/439) — JDK 21 分代 ZGC 默认启用
- [JEP 189: Shenandoah: A Low-Pause-Time Garbage Collector](https://openjdk.org/jeps/189) — Shenandoah 引入
- [JDK 17 Documentation — Garbage Collection Tuning Guide](https://docs.oracle.com/en/java/javase/17/gctuning/) — Oracle 官方 GC 调优指南
- [The Garbage Collection Handbook (2nd Edition)](https://gchandbook.org/) — Richard Jones 等人的权威著作，系统性地覆盖了 GC 理论和实现
