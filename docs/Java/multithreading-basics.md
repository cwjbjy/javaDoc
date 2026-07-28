# 01-09 多线程基础

> 理解Java多线程编程，掌握线程同步与线程池使用

## 1. 线程基础

### 进程 vs 线程

- **进程**：操作系统分配资源的基本单位，独立的内存空间
- **线程**：进程内的执行单元，共享进程资源

```java
// 获取当前线程
Thread currentThread = Thread.currentThread();
String name = currentThread.getName();  // 线程名
long id = currentThread.getId();        // 线程ID
```

---

## 2. 创建线程的3种方式

### 方式1：继承 Thread 类

```java
public class MyThread extends Thread {
    @Override
    public void run() {
        System.out.println("Thread running: " + getName());
    }
}

// 使用
MyThread thread = new MyThread();
thread.start();  // 启动线程
```

### 方式2：实现 Runnable 接口（推荐）

```java
public class MyRunnable implements Runnable {
    @Override
    public void run() {
        System.out.println("Thread running: " + Thread.currentThread().getName());
    }
}

// 使用
Thread thread = new Thread(new MyRunnable());
thread.start();

// Lambda 写法
Thread thread2 = new Thread(() -> {
    System.out.println("Thread running");
});
thread2.start();
```

### 方式3：实现 Callable 接口（有返回值）

```java
import java.util.concurrent.*;

public class MyCallable implements Callable<String> {
    @Override
    public String call() throws Exception {
        Thread.sleep(1000);
        return "Task completed";
    }
}

// 使用
ExecutorService executor = Executors.newSingleThreadExecutor();
Future<String> future = executor.submit(new MyCallable());

// 获取结果（阻塞）
String result = future.get();
System.out.println(result);

executor.shutdown();
```

---

## 3. 线程状态与控制

### 线程状态

```
NEW（新建）
  ↓ start()
RUNNABLE（可运行）
  ↓ 获得CPU
RUNNING（运行）
  ↓ sleep/wait/join/IO
BLOCKED/WAITING/TIMED_WAITING（阻塞/等待）
  ↓ 完成/通知
TERMINATED（终止）
```

### 线程方法

```java
Thread thread = new Thread(() -> {
    // 线程执行的代码
});

// 启动线程
thread.start();

// 等待线程结束
thread.join();          // 一直等待
thread.join(1000);      // 最多等待1秒

// 线程休眠
Thread.sleep(1000);     // 休眠1秒

// 线程中断
thread.interrupt();     // 中断线程
boolean interrupted = Thread.interrupted();  // 检查是否中断

// 线程优先级（1-10，默认5）
thread.setPriority(Thread.MAX_PRIORITY);  // 10
thread.setPriority(Thread.NORM_PRIORITY); // 5
thread.setPriority(Thread.MIN_PRIORITY);  // 1
```

---

## 4. 线程同步（synchronized）

### synchronized 方法

```java
public class Counter {
    private int count = 0;
    
    // 同步方法（锁住整个对象）
    public synchronized void increment() {
        count++;
    }
    
    public synchronized int getCount() {
        return count;
    }
}

// 使用
Counter counter = new Counter();

Thread t1 = new Thread(() -> {
    for (int i = 0; i < 1000; i++) {
        counter.increment();
    }
});

Thread t2 = new Thread(() -> {
    for (int i = 0; i < 1000; i++) {
        counter.increment();
    }
});

t1.start();
t2.start();
t1.join();
t2.join();

System.out.println(counter.getCount());  // 2000（线程安全）
```

### synchronized 代码块

```java
public class Counter {
    private int count = 0;
    private final Object lock = new Object();
    
    public void increment() {
        synchronized (lock) {  // 锁住指定对象
            count++;
        }
    }
    
    public void decrement() {
        synchronized (lock) {
            count--;
        }
    }
}
```

### 静态同步方法

```java
public class Counter {
    private static int count = 0;
    
    // 锁住整个类
    public static synchronized void increment() {
        count++;
    }
}
```

---

## 5. Lock 锁（更灵活）

### ReentrantLock

```java
import java.util.concurrent.locks.*;

public class Counter {
    private int count = 0;
    private final Lock lock = new ReentrantLock();
    
    public void increment() {
        lock.lock();  // 获取锁
        try {
            count++;
        } finally {
            lock.unlock();  // 必须在finally中释放锁
        }
    }
    
    // 尝试获取锁（非阻塞）
    public void tryIncrement() {
        if (lock.tryLock()) {
            try {
                count++;
            } finally {
                lock.unlock();
            }
        } else {
            System.out.println("无法获取锁");
        }
    }
    
    // 带超时的尝试
    public void tryIncrementWithTimeout() throws InterruptedException {
        if (lock.tryLock(1, TimeUnit.SECONDS)) {
            try {
                count++;
            } finally {
                lock.unlock();
            }
        }
    }
}
```

### ReadWriteLock（读写锁）

```java
public class Cache {
    private Map<String, String> map = new HashMap<>();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();
    
    // 读操作（多个线程可同时读）
    public String get(String key) {
        readLock.lock();
        try {
            return map.get(key);
        } finally {
            readLock.unlock();
        }
    }
    
    // 写操作（独占）
    public void put(String key, String value) {
        writeLock.lock();
        try {
            map.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }
}
```

---

## 6. 线程池（推荐）

### 为什么使用线程池

```java
// ❌ 频繁创建线程（开销大）
for (int i = 0; i < 1000; i++) {
    new Thread(() -> {
        // 执行任务
    }).start();
}

// ✅ 使用线程池（复用线程）
ExecutorService executor = Executors.newFixedThreadPool(10);
for (int i = 0; i < 1000; i++) {
    executor.submit(() -> {
        // 执行任务
    });
}
executor.shutdown();
```

### 线程池类型

```java
import java.util.concurrent.*;

// 1. 固定大小线程池
ExecutorService fixed = Executors.newFixedThreadPool(5);

// 2. 单线程池
ExecutorService single = Executors.newSingleThreadExecutor();

// 3. 缓存线程池（动态增长）
ExecutorService cached = Executors.newCachedThreadPool();

// 4. 定时任务线程池
ScheduledExecutorService scheduled = Executors.newScheduledThreadPool(2);
```

### 线程池使用

```java
ExecutorService executor = Executors.newFixedThreadPool(10);

// 提交任务（无返回值）
executor.execute(() -> {
    System.out.println("Task running");
});

// 提交任务（有返回值）
Future<String> future = executor.submit(() -> {
    Thread.sleep(1000);
    return "Result";
});

// 获取结果
try {
    String result = future.get();  // 阻塞等待
    String result2 = future.get(5, TimeUnit.SECONDS);  // 超时
} catch (InterruptedException | ExecutionException | TimeoutException e) {
    e.printStackTrace();
}

// 关闭线程池
executor.shutdown();           // 等待任务完成后关闭
executor.shutdownNow();        // 立即关闭（中断任务）
executor.awaitTermination(10, TimeUnit.SECONDS);  // 等待关闭
```

### 自定义线程池（推荐）

```java
// ❌ 不推荐使用 Executors（可能OOM）
// ✅ 推荐手动创建 ThreadPoolExecutor

ThreadPoolExecutor executor = new ThreadPoolExecutor(
    5,                      // corePoolSize：核心线程数
    10,                     // maximumPoolSize：最大线程数
    60L,                    // keepAliveTime：空闲线程存活时间
    TimeUnit.SECONDS,       // 时间单位
    new LinkedBlockingQueue<>(100),  // 任务队列
    new ThreadPoolExecutor.CallerRunsPolicy()  // 拒绝策略
);

// 拒绝策略：
// AbortPolicy：抛异常（默认）
// CallerRunsPolicy：调用者线程执行
// DiscardPolicy：丢弃任务
// DiscardOldestPolicy：丢弃最老任务
```

---

## 7. 定时任务

### ScheduledExecutorService

```java
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

// 延迟执行（1秒后执行一次）
scheduler.schedule(() -> {
    System.out.println("Task executed");
}, 1, TimeUnit.SECONDS);

// 固定频率执行（每2秒执行一次）
scheduler.scheduleAtFixedRate(() -> {
    System.out.println("Task executed");
}, 0, 2, TimeUnit.SECONDS);  // 初始延迟0秒，间隔2秒

// 固定延迟执行（任务完成后延迟2秒再执行）
scheduler.scheduleWithFixedDelay(() -> {
    System.out.println("Task executed");
}, 0, 2, TimeUnit.SECONDS);

// 关闭
scheduler.shutdown();
```

---

## 8. 并发工具类

### CountDownLatch（倒计时门闩）

```java
// 等待多个线程完成
CountDownLatch latch = new CountDownLatch(3);

for (int i = 0; i < 3; i++) {
    new Thread(() -> {
        System.out.println("Task " + Thread.currentThread().getName() + " completed");
        latch.countDown();  // 计数减1
    }).start();
}

latch.await();  // 等待计数归零
System.out.println("All tasks completed");
```

### CyclicBarrier（循环栅栏）

```java
// 多个线程互相等待
CyclicBarrier barrier = new CyclicBarrier(3, () -> {
    System.out.println("All threads reached barrier");
});

for (int i = 0; i < 3; i++) {
    new Thread(() -> {
        try {
            System.out.println(Thread.currentThread().getName() + " waiting");
            barrier.await();  // 等待其他线程
            System.out.println(Thread.currentThread().getName() + " continued");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }).start();
}
```

### Semaphore（信号量）

```java
// 限制并发数
Semaphore semaphore = new Semaphore(3);  // 最多3个线程同时执行

for (int i = 0; i < 10; i++) {
    new Thread(() -> {
        try {
            semaphore.acquire();  // 获取许可
            System.out.println(Thread.currentThread().getName() + " running");
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            semaphore.release();  // 释放许可
        }
    }).start();
}
```

---

## 9. 线程安全集合

### ConcurrentHashMap（线程安全的Map）

```java
import java.util.concurrent.ConcurrentHashMap;

// 创建
ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();

// 基本操作
map.put("key1", "value1");
String value = map.get("key1");
map.remove("key1");

// 原子操作
// 如果不存在则添加
map.putIfAbsent("key1", "value1");

// 替换值
map.replace("key1", "newValue");
map.replace("key1", "oldValue", "newValue");  // 只有旧值匹配才替换

// 计算操作
map.compute("key1", (k, v) -> v == null ? "1" : String.valueOf(Integer.parseInt(v) + 1));
map.computeIfAbsent("key1", k -> "defaultValue");
map.computeIfPresent("key1", (k, v) -> v + "_updated");

// 合并操作
map.merge("key1", "1", (oldVal, newVal) -> String.valueOf(Integer.parseInt(oldVal) + Integer.parseInt(newVal)));
```

**使用场景**：
- 高并发读写的缓存
- 多线程共享的配置数据
- 统计计数（替代同步的HashMap）

**性能特点**：
- 读操作无锁（比Hashtable快）
- 写操作采用分段锁（JDK 1.8后改为CAS+synchronized）
- 不允许null键和null值

### CopyOnWriteArrayList（线程安全的List）

```java
import java.util.concurrent.CopyOnWriteArrayList;

CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>();

// 添加元素
list.add("item1");
list.add(0, "item0");
list.addAll(Arrays.asList("item2", "item3"));

// 读取元素
String item = list.get(0);
int size = list.size();

// 遍历（不会抛ConcurrentModificationException）
for (String s : list) {
    System.out.println(s);
}

// 删除元素
list.remove("item1");
list.remove(0);
```

**使用场景**：
- 读多写少的场景（如事件监听器列表）
- 需要遍历时不加锁的场景
- 白名单/黑名单配置

**性能特点**：
- 读操作无锁，性能高
- 写操作复制整个数组，开销大
- 适合小数据量、读多写少

**⚠️ 注意事项**：
```java
// ❌ 不适合频繁写入
for (int i = 0; i < 10000; i++) {
    list.add("item" + i);  // 每次都复制数组
}

// ✅ 批量写入
list.addAll(Arrays.asList(...));  // 只复制一次
```

### CopyOnWriteArraySet（线程安全的Set）

```java
import java.util.concurrent.CopyOnWriteArraySet;

CopyOnWriteArraySet<String> set = new CopyOnWriteArraySet<>();

set.add("item1");
set.add("item2");
set.add("item1");  // 重复元素，不会添加

boolean contains = set.contains("item1");
set.remove("item1");
```

**使用场景**：
- 需要去重的订阅者列表
- 缓存的标签集合

**性能特点**：同CopyOnWriteArrayList

### 阻塞队列（BlockingQueue）

#### LinkedBlockingQueue（链表阻塞队列）

```java
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

// 无界队列（容量Integer.MAX_VALUE）
LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>();

// 有界队列
LinkedBlockingQueue<String> boundedQueue = new LinkedBlockingQueue<>(100);

// 生产者
queue.put("item1");  // 队列满时阻塞等待
boolean added = queue.offer("item2");  // 队列满时返回false
queue.offer("item3", 5, TimeUnit.SECONDS);  // 队列满时等待5秒

// 消费者
String item = queue.take();  // 队列空时阻塞等待
String item2 = queue.poll();  // 队列空时返回null
String item3 = queue.poll(5, TimeUnit.SECONDS);  // 队列空时等待5秒

// 查看但不移除
String peek = queue.peek();
```

**使用场景**：
- 生产者-消费者模式
- 线程池的任务队列
- 异步处理队列

#### ArrayBlockingQueue（数组阻塞队列）

```java
import java.util.concurrent.ArrayBlockingQueue;

// 必须指定容量
ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(100);

// 支持公平模式（按等待时间排队）
ArrayBlockingQueue<String> fairQueue = new ArrayBlockingQueue<>(100, true);

queue.put("item");
String item = queue.take();
```

**对比**：
- `LinkedBlockingQueue`：链表实现，默认无界，吞吐量高
- `ArrayBlockingQueue`：数组实现，必须有界，内存占用小

#### PriorityBlockingQueue（优先级队列）

```java
import java.util.concurrent.PriorityBlockingQueue;

// 自然排序
PriorityBlockingQueue<Integer> queue = new PriorityBlockingQueue<>();
queue.put(3);
queue.put(1);
queue.put(2);
System.out.println(queue.take());  // 输出 1

// 自定义排序
PriorityBlockingQueue<Task> taskQueue = new PriorityBlockingQueue<>(
    10, 
    Comparator.comparingInt(Task::getPriority).reversed()
);
```

**使用场景**：
- 任务调度（按优先级执行）
- 延迟任务处理

#### DelayQueue（延迟队列）

```java
import java.util.concurrent.*;

// 元素必须实现Delayed接口
class DelayedTask implements Delayed {
    private String name;
    private long executeTime;  // 执行时间（毫秒）
    
    public DelayedTask(String name, long delayMs) {
        this.name = name;
        this.executeTime = System.currentTimeMillis() + delayMs;
    }
    
    @Override
    public long getDelay(TimeUnit unit) {
        long diff = executeTime - System.currentTimeMillis();
        return unit.convert(diff, TimeUnit.MILLISECONDS);
    }
    
    @Override
    public int compareTo(Delayed o) {
        return Long.compare(this.executeTime, ((DelayedTask) o).executeTime);
    }
}

// 使用
DelayQueue<DelayedTask> queue = new DelayQueue<>();
queue.put(new DelayedTask("task1", 3000));  // 3秒后执行
queue.put(new DelayedTask("task2", 1000));  // 1秒后执行

DelayedTask task = queue.take();  // 阻塞直到有任务到期
```

**使用场景**：
- 订单超时取消
- 会话过期清理
- 定时任务调度

#### SynchronousQueue（同步队列）

```java
import java.util.concurrent.SynchronousQueue;

SynchronousQueue<String> queue = new SynchronousQueue<>();

// 生产者线程
new Thread(() -> {
    try {
        queue.put("item");  // 阻塞直到有消费者take
        System.out.println("Item delivered");
    } catch (InterruptedException e) {
        e.printStackTrace();
    }
}).start();

// 消费者线程
new Thread(() -> {
    try {
        String item = queue.take();  // 阻塞直到有生产者put
        System.out.println("Received: " + item);
    } catch (InterruptedException e) {
        e.printStackTrace();
    }
}).start();
```

**使用场景**：
- 线程间直接传递数据
- Executors.newCachedThreadPool()的内部实现

### ConcurrentLinkedQueue（非阻塞队列）

```java
import java.util.concurrent.ConcurrentLinkedQueue;

ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();

// 添加元素
queue.offer("item1");
queue.add("item2");

// 获取并移除元素
String item = queue.poll();  // 队列空时返回null

// 查看但不移除
String peek = queue.peek();

// 不支持阻塞操作
```

**使用场景**：
- 不需要阻塞的高并发队列
- 消息缓冲区

**对比**：
- `ConcurrentLinkedQueue`：非阻塞，使用CAS，性能高
- `LinkedBlockingQueue`：阻塞，使用锁，支持等待

### 实战示例

#### 示例1：生产者-消费者（订单处理）

```java
@Component
public class OrderProcessor {
    
    private final BlockingQueue<Order> orderQueue = new LinkedBlockingQueue<>(1000);
    private final ExecutorService consumerPool = Executors.newFixedThreadPool(5);
    
    @PostConstruct
    public void init() {
        // 启动5个消费者线程
        for (int i = 0; i < 5; i++) {
            consumerPool.submit(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Order order = orderQueue.take();
                        processOrder(order);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }
    }
    
    // 生产者：接收订单
    public boolean submitOrder(Order order) {
        try {
            return orderQueue.offer(order, 5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }
    
    // 消费者：处理订单
    private void processOrder(Order order) {
        log.info("Processing order: {}", order.getId());
        // 处理逻辑...
    }
}
```

#### 示例2：延迟任务（订单超时取消）

```java
@Component
public class OrderTimeoutManager {
    
    private final DelayQueue<DelayedOrder> delayQueue = new DelayQueue<>();
    
    static class DelayedOrder implements Delayed {
        private Long orderId;
        private long expireTime;
        
        public DelayedOrder(Long orderId, long delayMs) {
            this.orderId = orderId;
            this.expireTime = System.currentTimeMillis() + delayMs;
        }
        
        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(expireTime - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
        }
        
        @Override
        public int compareTo(Delayed o) {
            return Long.compare(this.expireTime, ((DelayedOrder) o).expireTime);
        }
    }
    
    @PostConstruct
    public void init() {
        // 启动取消任务线程
        new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    DelayedOrder order = delayQueue.take();
                    cancelOrder(order.orderId);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }).start();
    }
    
    // 添加超时订单
    public void addOrderTimeout(Long orderId, long timeoutMs) {
        delayQueue.put(new DelayedOrder(orderId, timeoutMs));
    }
    
    // 取消超时订单
    private void cancelOrder(Long orderId) {
        log.info("Cancelling timeout order: {}", orderId);
        // 取消逻辑...
    }
}
```

#### 示例3：缓存实现（ConcurrentHashMap）

```java
@Component
public class ProductCache {
    
    private final ConcurrentHashMap<Long, Product> cache = new ConcurrentHashMap<>();
    
    @Autowired
    private ProductMapper productMapper;
    
    // 获取商品（缓存不存在则查库）
    public Product getProduct(Long id) {
        return cache.computeIfAbsent(id, k -> {
            log.info("Cache miss, loading from DB: {}", k);
            return productMapper.selectByPrimaryKey(k);
        });
    }
    
    // 更新商品（同时更新缓存）
    public void updateProduct(Product product) {
        productMapper.updateByPrimaryKey(product);
        cache.put(product.getId(), product);
    }
    
    // 删除商品（同时删除缓存）
    public void deleteProduct(Long id) {
        productMapper.deleteByPrimaryKey(id);
        cache.remove(id);
    }
    
    // 批量预加载
    public void preload(List<Long> ids) {
        List<Product> products = productMapper.selectByIds(ids);
        products.forEach(p -> cache.put(p.getId(), p));
    }
    
    // 清空缓存
    public void clear() {
        cache.clear();
    }
}
```

#### 示例4：计数器（ConcurrentHashMap）

```java
@Component
public class PageViewCounter {
    
    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();
    
    // 增加计数
    public long increment(String page) {
        return counters.computeIfAbsent(page, k -> new AtomicLong())
                       .incrementAndGet();
    }
    
    // 获取计数
    public long getCount(String page) {
        AtomicLong counter = counters.get(page);
        return counter != null ? counter.get() : 0;
    }
    
    // 获取所有计数
    public Map<String, Long> getAllCounts() {
        Map<String, Long> result = new HashMap<>();
        counters.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }
    
    // 重置计数
    public void reset(String page) {
        counters.remove(page);
    }
}
```

### 性能对比

| 集合类型 | 读性能 | 写性能 | 适用场景 |
|---------|--------|--------|----------|
| ConcurrentHashMap | 高 | 高 | 高并发读写 |
| CopyOnWriteArrayList | 极高 | 低 | 读多写少 |
| LinkedBlockingQueue | 中 | 中 | 生产消费 |
| ConcurrentLinkedQueue | 高 | 高 | 非阻塞队列 |

### 最佳实践

```java
// ✅ 根据场景选择合适的集合
// 高并发缓存
ConcurrentHashMap<String, Object> cache = new ConcurrentHashMap<>();

// 监听器列表（读多写少）
CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

// 任务队列
LinkedBlockingQueue<Task> taskQueue = new LinkedBlockingQueue<>(1000);

// ❌ 不要在循环中频繁写入CopyOnWriteArrayList
for (int i = 0; i < 10000; i++) {
    list.add("item" + i);  // 每次都复制数组
}

// ✅ 批量操作
List<String> items = new ArrayList<>();
for (int i = 0; i < 10000; i++) {
    items.add("item" + i);
}
list.addAll(items);  // 只复制一次
```

---

## 10. CompletableFuture（异步编程）

### 基本使用

```java
import java.util.concurrent.CompletableFuture;

// 异步执行（无返回值）
CompletableFuture<Void> future1 = CompletableFuture.runAsync(() -> {
    System.out.println("Task running");
});

// 异步执行（有返回值）
CompletableFuture<String> future2 = CompletableFuture.supplyAsync(() -> {
    return "Result";
});

// 获取结果
String result = future2.get();  // 阻塞
String result2 = future2.join();  // 阻塞（不抛受检异常）
```

### 链式调用

```java
CompletableFuture.supplyAsync(() -> {
    return "Hello";
})
.thenApply(s -> s + " World")  // 转换
.thenAccept(System.out::println)  // 消费
.exceptionally(ex -> {  // 异常处理
    System.err.println("Error: " + ex.getMessage());
    return null;
});
```

### 组合多个异步任务

```java
CompletableFuture<String> future1 = CompletableFuture.supplyAsync(() -> "Task1");
CompletableFuture<String> future2 = CompletableFuture.supplyAsync(() -> "Task2");

// allOf：等待所有任务完成
CompletableFuture<Void> allFuture = CompletableFuture.allOf(future1, future2);
allFuture.join();

// anyOf：等待任意一个完成
CompletableFuture<Object> anyFuture = CompletableFuture.anyOf(future1, future2);
Object result = anyFuture.join();
```

---

## 11. 实战示例

### 示例1：并发查询商品

```java
@Service
public class ProductService {
    
    @Autowired
    private ProductMapper productMapper;
    
    private ExecutorService executor = Executors.newFixedThreadPool(10);
    
    public List<Product> batchQuery(List<Long> ids) {
        List<CompletableFuture<Product>> futures = ids.stream()
            .map(id -> CompletableFuture.supplyAsync(() -> {
                return productMapper.selectByPrimaryKey(id);
            }, executor))
            .collect(Collectors.toList());
        
        return futures.stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList());
    }
}
```

### 示例2：定时清理过期数据

```java
@Component
public class CleanupTask {
    
    @Autowired
    private OrderMapper orderMapper;
    
    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    
    @PostConstruct
    public void init() {
        // 每天凌晨2点执行
        long initialDelay = getDelayToNextRun();
        scheduler.scheduleAtFixedRate(() -> {
            cleanup();
        }, initialDelay, TimeUnit.DAYS.toSeconds(1), TimeUnit.SECONDS);
    }
    
    private void cleanup() {
        log.info("开始清理过期订单");
        LocalDateTime expireTime = LocalDateTime.now().minusDays(30);
        int count = orderMapper.deleteExpired(expireTime);
        log.info("清理完成，删除{}条记录", count);
    }
    
    private long getDelayToNextRun() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun = now.withHour(2).withMinute(0).withSecond(0);
        if (now.isAfter(nextRun)) {
            nextRun = nextRun.plusDays(1);
        }
        return Duration.between(now, nextRun).getSeconds();
    }
}
```

---

## 12. 最佳实践

### 1. 优先使用线程池

```java
// ✅ 使用线程池
ExecutorService executor = Executors.newFixedThreadPool(10);
executor.submit(() -> { /* task */ });

// ❌ 频繁创建线程
new Thread(() -> { /* task */ }).start();
```

### 2. 自定义线程池参数

```java
// ✅ 根据业务场景自定义
ThreadPoolExecutor executor = new ThreadPoolExecutor(
    10, 20, 60L, TimeUnit.SECONDS,
    new LinkedBlockingQueue<>(100),
    new ThreadPoolExecutor.CallerRunsPolicy()
);

// ❌ 使用 Executors（可能OOM）
ExecutorService executor = Executors.newFixedThreadPool(10);
```

### 3. 正确关闭线程池

```java
executor.shutdown();  // 等待任务完成
if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
    executor.shutdownNow();  // 强制关闭
}
```

### 4. 避免死锁

```java
// 多个锁按固定顺序获取
synchronized (lock1) {
    synchronized (lock2) {
        // ...
    }
}
```

---

## 下一步

- **[01-10-常用工具类库.md](./01-10-常用工具类库.md)** - 工具类库

---

## 快速参考

```java
// 创建线程
Thread thread = new Thread(() -> {
    System.out.println("Task");
});
thread.start();

// 线程池
ExecutorService executor = Executors.newFixedThreadPool(10);
executor.submit(() -> { /* task */ });
executor.shutdown();

// 同步
public synchronized void method() { }
synchronized (lock) { /* code */ }

// Lock
Lock lock = new ReentrantLock();
lock.lock();
try {
    // code
} finally {
    lock.unlock();
}

// CompletableFuture
CompletableFuture.supplyAsync(() -> "Result")
    .thenApply(s -> s.toUpperCase())
    .thenAccept(System.out::println);
```
