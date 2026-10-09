# 01-08 IO与文件操作

> 掌握Java文件读写、对象序列化与常用IO工具

## 1. Files 工具类（推荐）

```java
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

// 创建 Path
Path path = Paths.get("data.txt");
Path path2 = Paths.get("D:", "data", "file.txt");
```

### 文件信息

```java
boolean exists = Files.exists(path);
boolean isFile = Files.isRegularFile(path);
boolean isDir = Files.isDirectory(path);
long size = Files.size(path);
```

### 读取文件

```java
// 读取字符串（Java 11+，推荐）
String content = Files.readString(Paths.get("data.txt"));

// 读取所有行
List<String> lines = Files.readAllLines(Paths.get("data.txt"), StandardCharsets.UTF_8);

// 读取字节
byte[] bytes = Files.readAllBytes(Paths.get("data.txt"));
```

### 写入文件

```java
// 写入字符串（Java 11+，推荐）
Files.writeString(Paths.get("output.txt"), "Hello World");

// 写入所有行
List<String> lines = Arrays.asList("Line 1", "Line 2", "Line 3");
Files.write(Paths.get("output.txt"), lines, StandardCharsets.UTF_8);

// 追加模式
Files.write(Paths.get("output.txt"), lines, StandardOpenOption.APPEND);
```

### 文件操作

```java
// 创建/删除
Files.createFile(path);
Files.delete(path);
Files.deleteIfExists(path);  // 不存在不报错

// 复制/移动
Files.copy(source, target);
Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);  // 覆盖
Files.move(source, target);

// 目录操作
Files.createDirectory(Paths.get("mydir"));
Files.createDirectories(Paths.get("a/b/c"));  // 创建多级

// 列出文件
try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get("."))) {
    for (Path entry : stream) {
        System.out.println(entry.getFileName());
    }
}
```

---

## 2. BufferedReader/Writer（逐行处理）

### 逐行读取

```java
// 指定编码（推荐）
try (BufferedReader br = Files.newBufferedReader(Paths.get("data.txt"), StandardCharsets.UTF_8)) {
    String line;
    while ((line = br.readLine()) != null) {
        System.out.println(line);
    }
}
```

### 逐行写入

```java
// 指定编码（推荐）
try (BufferedWriter bw = Files.newBufferedWriter(Paths.get("output.txt"), StandardCharsets.UTF_8)) {
    bw.write("Line 1");
    bw.newLine();
    bw.write("Line 2");
    bw.newLine();
}
```

---

## 3. Stream 流式处理（大文件）

### 逐行处理

```java
// Stream API（内存效率高，适合大文件）
try (Stream<String> lines = Files.lines(Paths.get("large.txt"))) {
    lines.filter(line -> line.contains("keyword"))
         .forEach(System.out::println);
}

// 统计行数
long count = Files.lines(Paths.get("data.txt")).count();

// 读取前10行
List<String> first10 = Files.lines(Paths.get("data.txt"))
    .limit(10)
    .toList();
```

### 遍历目录树

```java
// 列出所有文件
Files.walk(Paths.get("."))
    .filter(Files::isRegularFile)
    .forEach(System.out::println);

// 查找 .txt 文件
Files.walk(Paths.get("."))
    .filter(path -> path.toString().endsWith(".txt"))
    .forEach(System.out::println);

// 限制深度
Files.walk(Paths.get("."), 2)  // 最多2层
    .forEach(System.out::println);
```

---

## 4. 资源文件读取

### 从 classpath 读取

```java
// ClassLoader 方式
InputStream is = getClass().getClassLoader()
    .getResourceAsStream("config.properties");

// 读取为字符串
try (InputStream is = getClass().getClassLoader()
        .getResourceAsStream("data.txt");
     BufferedReader br = new BufferedReader(
         new InputStreamReader(is, StandardCharsets.UTF_8))) {
    String line;
    while ((line = br.readLine()) != null) {
        System.out.println(line);
    }
}
```

### Spring ResourceLoader

```java
@Service
public class FileService {
    @Autowired
    private ResourceLoader resourceLoader;

    public void readFile() throws IOException {
        Resource resource = resourceLoader.getResource("classpath:data.txt");
        try (InputStream is = resource.getInputStream();
             BufferedReader br = new BufferedReader(
                 new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
            }
        }
    }
}
```

---

## 5. 对象序列化与 Serializable

前面的章节处理的是文本和字节。但有时需要把**整个 Java 对象**变成字节流——写到文件、通过网络发送、存进 Redis 缓存。这就是**序列化（Serialization）**要做的事。

### 5.1 Serializable 标记接口

`java.io.Serializable` 是一个**标记接口（Marker Interface）**——接口里没有任何方法，只是给 JVM 打一个标签："这个类的对象可以被序列化"。

```java
import java.io.Serializable;

// ✅ 实现 Serializable，声明"我可以被序列化"
public class User implements Serializable {
    private Long id;
    private String name;
    private int age;
}

// ❌ 没有实现 Serializable → 序列化时抛 NotSerializableException
public class Product {
    private Long id;
    private String name;
}
```

标记接口的含义：

| 特征                   | 说明                                                            |
| ---------------------- | --------------------------------------------------------------- |
| 没有方法要实现         | 不像 `Comparable` 要实现 `compareTo()`，`Serializable` 是空接口 |
| 是一种"能力声明"       | 告诉 `ObjectOutputStream`："这个对象可以放心地转成字节流"       |
| 父类不会自动继承       | 父类实现了 `Serializable`，子类自动拥有；反过来不行             |
| 所有字段默认参与序列化 | 不想被序列化的字段用 `transient` 排除（§5.4）                   |

> `record` 类型默认实现了 `Serializable`（Java 16+），前提是它的所有组件类型也都是可序列化的。

### 5.2 ObjectOutputStream / ObjectInputStream

Java 原生序列化的核心就两个类：

- `ObjectOutputStream`：对象 → 字节流（**序列化**）
- `ObjectInputStream`：字节流 → 对象（**反序列化**）

```java
import java.io.*;

// ===== 序列化：对象写入文件 =====
User user = new User();
user.setId(1L);
user.setName("Alice");
user.setAge(25);

//ObjectOutputStream 把对象变成字节，交给 FileOutputStream 写到文件。
try (ObjectOutputStream oos = new ObjectOutputStream(
        new FileOutputStream("user.dat"))) {
    oos.writeObject(user);  // 对象 → 字节流 → 文件
}

// ===== 反序列化：从文件读回对象 =====
// FileInputStream 从文件读出原始字节，ObjectInputStream 把字节流还原成 Java 对象
try (ObjectInputStream ois = new ObjectInputStream(
        new FileInputStream("user.dat"))) {
    User readBack = (User) ois.readObject();  // 字节流 → 对象
    System.out.println(readBack.getName());  // Alice
}
```

几个要点：

```java
// 1. 必须用 try-with-resources 确保流关闭
// 2. writeObject() 可以连续写多个对象
try (ObjectOutputStream oos = new ObjectOutputStream(
        new FileOutputStream("users.dat"))) {
    oos.writeObject(user1);
    oos.writeObject(user2);
    oos.writeObject(user3);
}

// 3. 读取时必须按写入顺序逐个读取，并强制类型转换
try (ObjectInputStream ois = new ObjectInputStream(
        new FileInputStream("users.dat"))) {
    User u1 = (User) ois.readObject();
    User u2 = (User) ois.readObject();
    User u3 = (User) ois.readObject();
}

// 4. 写入和读取的顺序必须严格对应——否则 ClassCastException 或 EOFException
```

### 5.3 serialVersionUID：版本控制的钥匙

每个实现了 `Serializable` 的类都有一个 `serialVersionUID`。它是序列化版本的"指纹"——**反序列化时，JVM 会比对字节流中的 UID 和当前类的 UID 是否一致**，不一致就抛 `InvalidClassException`。

```java
// ❌ 不显式声明：JVM 自动生成（基于类结构）
public class User implements Serializable {
    private Long id;
    private String name;
    // JVM 自动生成 UID，比如 7526413942098176541L
    // 一旦你加了个字段、改了个方法签名 → UID 变了 → 旧数据反序列化失败！
}

// ✅ 显式声明：版本可控
public class User implements Serializable {
    private static final long serialVersionUID = 1L;  // 手动指定
    private Long id;
    private String name;
}
```

| 做法                                   | 后果                                                             |
| -------------------------------------- | ---------------------------------------------------------------- |
| 不声明（依赖自动生成）                 | 改任何字段/方法 → UID 变化 → 旧数据全部无法反序列化              |
| 显式声明 `= 1L`                        | 类结构变化后，只要保持向后兼容（加字段有默认值），旧数据仍可读取 |
| 类结构**不兼容**变更（删字段、改类型） | 必须改 UID（如 `= 2L`），旧数据无法读取                          |

**最佳实践**：所有实现 `Serializable` 的类，**始终显式声明 `serialVersionUID`**。

> **常见疑问：服务重启了，UID 变了又怎样？** 问题不在服务本身，而在**服务重启后还要读旧数据**——Redis 缓存、数据库字节列、消息队列积压的消息、持久化的 Session 都不会随服务重启消失。代码是新的，数据是旧的，UID 对不上就读不出来。显式声明 UID 不变，新字段反序列化时自动填 `null`，旧数据就能正常读取。

### 5.4 transient：排除不需要序列化的字段

有些字段不应该被序列化——比如密码、数据库连接、线程锁。用 `transient` 关键字标记：

```java
public class User implements Serializable {
    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;

    private transient String password;      // ✅ 不参与序列化
    private transient Session session;      // ✅ 运行时资源，不该被序列化

    private int age;                        // 正常序列化
}

// 序列化后 password 和 session 的值丢失
// 反序列化回来时：password = null，session = null
```

`transient` 的规则：

| 修饰             | 是否参与序列化 | 典型场景                       |
| ---------------- | -------------- | ------------------------------ |
| 普通字段         | ✅ 参与        | 业务数据                       |
| `transient` 字段 | ❌ 不参与      | 密码、连接、缓存等运行时状态   |
| `static` 字段    | ❌ 不参与      | 静态字段属于类，不属于对象实例 |
| `final` 字段     | ✅ 参与        | 值会被序列化/反序列化          |

### 5.5 反序列化的安全陷阱

Java 原生反序列化有一个严重的安全问题：**反序列化过程中会自动执行代码**。攻击者可以构造恶意字节流，在 `readObject()` 时触发任意代码执行——这就是著名的"反序列化漏洞"（Deserialization Vulnerability）。

```java
// ❌ 危险：直接反序列化不可信来源的数据
try (ObjectInputStream ois = new ObjectInputStream(
        new FileInputStream(untrustedFile))) {
    Object obj = ois.readObject();  // 可能触发恶意代码！
}
```

**安全原则**：

```java
// ✅ 原则1：绝不反序列化不可信来源的数据
// 网络传输、用户上传的文件 → 不要用 Java 原生序列化

// ✅ 原则2：使用 ObjectInputFilter 白名单（Java 9+）
ObjectInputStream ois = new ObjectInputStream(new FileInputStream("data.dat"));
ois.setObjectInputFilter(filterInfo -> {
    // 只允许特定类被反序列化
    Class<?> clazz = filterInfo.serialClass();
    if (clazz == User.class || clazz == null) {
        return ObjectInputFilter.Status.ALLOWED;
    }
    return ObjectInputFilter.Status.REJECTED;
});

// ✅ 原则3：优先使用 JSON 等文本格式（§5.6）
// JSON 反序列化只是数据解析，不会触发代码执行
```

> 简而言之：Java 原生序列化适合**可信环境内部**的短期存储（如本地缓存），不适合跨系统通信或存储不可信数据。

### 5.6 现代替代方案

实际项目中，Java 原生序列化已经很少用于跨系统通信。以下是更常见的选择：

| 方案                     | 格式   | 跨语言     | 安全性        | 性能 | 典型场景                       |
| ------------------------ | ------ | ---------- | ------------- | ---- | ------------------------------ |
| Java 原生序列化          | 二进制 | ❌ 仅 Java | ❌ 有漏洞风险 | 中   | 本地缓存、RMI（遗留系统）      |
| JSON（Jackson/Fastjson） | 文本   | ✅         | ✅ 纯数据解析 | 中   | REST API、Kafka 消息、配置文件 |
| Protobuf                 | 二进制 | ✅         | ✅            | 高   | 微服务 RPC（gRPC）、高频通信   |
| Kryo                     | 二进制 | ❌ 仅 Java | ⚠️ 需配置     | 很高 | Spark/Flink 内部序列化         |

```java
// JSON 方式（推荐）——对象 → JSON 字符串 → 存储/传输
String json = objectMapper.writeValueAsString(user);   // 序列化
User readBack = objectMapper.readValue(json, User.class);  // 反序列化

// 优势：
// ✅ 人类可读（调试方便）
// ✅ 跨语言（JS/Python/Go 都能解析）
// ✅ 安全（不会触发代码执行）
// ✅ 版本兼容性好（加字段不影响旧数据解析）
```

**选型建议**：

- **跨系统通信 / API / 消息队列** → JSON（本项目的 Kafka 指南、微服务指南都用 JSON）
- **高性能内部通信** → Protobuf（gRPC 场景）
- **Java 内部缓存 / 临时持久化** → Java 原生序列化可以接受，但要声明 `serialVersionUID`
- **大数据框架内部** → Kryo（Spark/Flink 默认使用）

---

## 6. 最佳实践

### 1. 优先使用 Files 工具类

```java
// ✅ Files API（简洁）
String content = Files.readString(Paths.get("file.txt"));
Files.writeString(Paths.get("file.txt"), "Hello");
```

### 2. 始终指定字符编码

```java
// ✅ 明确指定UTF-8
Files.readAllLines(Paths.get("file.txt"), StandardCharsets.UTF_8);
Files.newBufferedReader(Paths.get("file.txt"), StandardCharsets.UTF_8);
```

### 3. 大文件使用流式处理

```java
// ✅ Stream API（内存效率高）
try (Stream<String> lines = Files.lines(Paths.get("large.txt"))) {
    lines.forEach(System.out::println);
}

// ❌ 一次性读入内存（可能OOM）
List<String> lines = Files.readAllLines(Paths.get("large.txt"));
```

### 4. 使用 try-with-resources 自动关闭

```java
// ✅ 自动关闭资源
try (BufferedReader br = Files.newBufferedReader(Paths.get("file.txt"))) {
    // 读取
}
```

---

## 7. 常见陷阱

### 陷阱1：路径分隔符

```java
// ❌ 硬编码路径分隔符（Windows: \\ Linux: /）
String path = "data\\file.txt";

// ✅ 使用 Paths（跨平台）
Path path2 = Paths.get("data", "file.txt");
```

### 陷阱2：文件编码

```java
// Windows默认GBK，Linux默认UTF-8
// 必须明确指定编码，避免乱码

// ✅ 指定UTF-8
Files.readAllLines(Paths.get("file.txt"), StandardCharsets.UTF_8);
```

### 陷阱3：serialVersionUID 缺失

```java
// ❌ 不声明 serialVersionUID
public class User implements Serializable {
    private Long id;
    private String name;
    // 加了字段后 UID 变了 → 旧数据反序列化抛 InvalidClassException
}

// ✅ 始终显式声明
public class User implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String name;
}
```

### 陷阱4：反序列化不可信数据

```java
// ❌ 直接反序列化网络/用户上传的数据 → 可能触发远程代码执行
Object obj = new ObjectInputStream(untrustedStream).readObject();

// ✅ 使用 JSON 等纯数据格式，或配置 ObjectInputFilter 白名单
```

---

## 快速参考

```java
// 读取文件（推荐）
String content = Files.readString(Paths.get("file.txt"));
List<String> lines = Files.readAllLines(Paths.get("file.txt"), StandardCharsets.UTF_8);

// 写入文件（推荐）
Files.writeString(Paths.get("file.txt"), "Hello");
Files.write(Paths.get("file.txt"), lines, StandardCharsets.UTF_8);

// 流式读取（大文件）
try (Stream<String> stream = Files.lines(Paths.get("file.txt"))) {
    stream.forEach(System.out::println);
}

// 逐行读写
try (BufferedReader br = Files.newBufferedReader(Paths.get("file.txt"), StandardCharsets.UTF_8);
     BufferedWriter bw = Files.newBufferedWriter(Paths.get("out.txt"), StandardCharsets.UTF_8)) {
    String line;
    while ((line = br.readLine()) != null) {
        bw.write(line);
        bw.newLine();
    }
}

// ===== 对象序列化 =====

// 类定义（必须实现 Serializable + 声明 UID）
public class User implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long id;
    private String name;
    private transient String password;  // 不参与序列化
}

// 序列化：对象 → 文件
try (ObjectOutputStream oos = new ObjectOutputStream(
        new FileOutputStream("user.dat"))) {
    oos.writeObject(user);
}

// 反序列化：文件 → 对象
try (ObjectInputStream ois = new ObjectInputStream(
        new FileInputStream("user.dat"))) {
    User readBack = (User) ois.readObject();
}

// 跨系统通信推荐用 JSON 代替 Java 原生序列化
String json = objectMapper.writeValueAsString(user);
User readBack = objectMapper.readValue(json, User.class);
```
