# 01-08 IO与文件操作

> 掌握Java文件读写与常用IO工具

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

## 5. 最佳实践

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

## 6. 常见陷阱

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
```
