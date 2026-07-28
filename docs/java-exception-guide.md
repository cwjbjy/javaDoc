# Java 异常机制指南

> 本指南系统介绍 Java 异常机制的完整知识体系。先建立全景认知，再逐类深入。
> 基于 Java 17+（Jakarta EE 9+）。

---

## 目录

1. [全景图](#1-全景图)
2. [分类详解](#2-分类详解)
   - [2.1 try-catch-finally：捕获与处理](#21-try-catch-finally捕获与处理)
   - [2.2 受检异常与非受检异常](#22-受检异常与非受检异常)
   - [2.3 throw 与 throws：制造与声明](#23-throw-与-throws制造与声明)
   - [2.4 常见非受检异常详解](#24-常见非受检异常详解)
   - [2.5 常见受检异常](#25-常见受检异常)
   - [2.6 Error：JVM 级别的灾难](#26-errorjvm-级别的灾难)
3. [自定义异常](#3-自定义异常)
   - [3.1 为什么要自定义异常](#31-为什么要自定义异常)
   - [3.2 继承谁：RuntimeException 还是 Exception](#32-继承谁runtimeexception-还是-exception)
   - [3.3 异常体系设计](#33-异常体系设计)
   - [3.4 异常信息设计](#34-异常信息设计)
4. [进阶概念](#4-进阶概念)
   - [4.1 异常链（包装异常）](#41-异常链包装异常)
   - [4.2 try-with-resources（自动关闭资源）](#42-try-with-resources自动关闭资源)
   - [4.3 多异常捕获（multi-catch）](#43-多异常捕获multi-catch)
5. [实战决策指南](#5-实战决策指南)
6. [速查清单](#6-速查清单)

---

## 1. 全景图

### 为什么需要异常机制

写一个文件读取功能：打开文件 → 读内容 → 关闭文件。每步都可能出错——文件不存在、没有权限、磁盘满了。

不用异常机制（如 C 语言的错误码方式），错误码与正常返回值混在一起，调用者可以忽略错误码，程序默默带着错误继续跑。更糟的是，错误传播需要逐层手动传递，错误信息只有一个数字。

```
错误码方式                              异常机制
══════════════                          ══════════
返回 0 / -1 / -2 / -3                   正常返回内容，异常抛出
调用方可以忽略返回值                     调用方要么 catch，要么继续 throws
编译器完全不提醒                         受检异常：编译器强制处理
错误信息：只有一个 -1                     错误信息：异常类型 + 消息 + 堆栈全有
深层错误逐层手动传递                     异常自动沿调用栈向上冒泡
```

> Java 异常机制的核心思想：**把"正常路径"和"异常路径"分开。** 正常逻辑走返回值，出错逻辑走异常——编译器帮你确保异常不会被遗忘。

### 异常体系全景

```
                      Throwable（所有异常和错误的父类）
                     /           \
                   /               \
          Error                      Exception
    （系统级严重错误）              （应用程序异常）
          │                              │
     VirtualMachineError           ┌──────┴────────┐
     OutOfMemoryError              │                │
     StackOverflowError    RuntimeException        其他 Exception（受检异常）
     ...                   （非受检 / unchecked）     │
                                │                IOException
                           NullPointerException   SQLException
                           IllegalArgumentException  FileNotFoundException
                           IndexOutOfBoundsException  ...
                           ArithmeticException（除零等）
                           ClassCastException（类型转换错误）
                           ...
```

### 关键字全景

```
throw                → 方法体内，抛出异常对象（制造异常）
throws               → 方法签名上，声明可能抛出的异常类型
try-catch-finally    → 捕获并处理异常，finally 总是执行
try-with-resources   → 自动关闭资源（替代 finally）
multi-catch          → 一个 catch 捕获多种异常（catch (A | B e)）
getCause()           → 获取异常链中的根因异常
```

> 以上两张图就是本文的导航地图。第 2 节逐一详解每个分支，第 3 节讲自定义异常，第 4 节讲进阶用法。

---

## 2. 分类详解

### 2.1 try-catch-finally：捕获与处理

需求：读取文件时，如果文件不存在就提示用户，但无论成功与否都要关闭文件句柄。

```java
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class ConfigLoader {

    public String load(String filePath) {
        BufferedReader reader = null;
        try {
            // ① try 块：可能抛出异常的代码
            reader = new BufferedReader(new FileReader(filePath));
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();  // 正常路径

        } catch (IOException e) {
            // ② catch 块：处理 IOException（文件不存在、读取出错等）
            System.out.println("读取配置文件失败: " + e.getMessage());
            return null;  // 异常路径

        } finally {
            // ③ finally 块：无论成功还是异常，都会执行
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // 关闭失败通常不再处理
                }
            }
        }
    }
}
```

```
try-catch-finally 执行流程

       try 块
         │
         ├── 没有异常 ──→ 执行完 ──→ finally 块 ──→ 方法结束
         │
         └── 抛出异常
               │
               ▼
         catch 块匹配？
               │
               ├── 匹配成功 ──→ 执行 catch ──→ finally 块 ──→ 方法结束
               │
               └── 匹配失败 ──→ 异常继续向上抛
                       │
                       ▼
                 finally 块（还是执行！）──→ 异常继续向上
```

**三个块的角色：**

| 块        | 何时执行                   | 典型用途                     |
| --------- | -------------------------- | ---------------------------- |
| `try`     | 正常进入                   | 放可能出错的业务代码         |
| `catch`   | try 中抛出了匹配的异常     | 处理异常（恢复/记录）        |
| `finally` | **总是执行**——无论异常与否 | 释放资源（关闭文件、连接等） |

> **关键认识**：`finally` 是最可靠的"收尾"位置。即使 `catch` 里又抛了异常、甚至 `try` 里有 `return`——`finally` 照样执行。只有 `System.exit()` 能阻止它。

---

### 2.2 受检异常与非受检异常

这是 Java 异常机制中**最重要也最容易被误解**的概念。**核心区别：编译器是否强制处理。**

```java
// ===== 非受检异常（Unchecked Exception）=====
// 继承自 RuntimeException → 编译器不强制 try-catch 或 throws

public void readConfig() {
    String value = null;
    int length = value.length();  // NullPointerException
    // 编译通过！但运行时会崩
}

// ===== 受检异常（Checked Exception）=========
// 不继承 RuntimeException → 编译器强制 try-catch 或 throws

public void readFile(String path) {
    BufferedReader reader = new BufferedReader(new FileReader(path));
    // 编译报错 ❌！FileReader 构造器 throws FileNotFoundException
    // 必须 try-catch 或加 throws
}

// 修复方式一：加 throws，把责任交给调用者
public void readFile(String path) throws IOException {
    BufferedReader reader = new BufferedReader(new FileReader(path));
    // 编译通过 ✅
}

// 修复方式二：try-catch，自己处理
public void readFile(String path) {
    try {
        BufferedReader reader = new BufferedReader(new FileReader(path));
    } catch (IOException e) {
        System.out.println("文件打开失败");
    }
    // 编译通过 ✅
}
```

**为什么要分两种？—— 设计哲学。**

| 对比维度     | 非受检异常（RuntimeException）               | 受检异常（Exception 其他子类）            |
| ------------ | -------------------------------------------- | ----------------------------------------- |
| 典型场景     | 程序 bug（逻辑错误、空指针、越界）           | 外部环境问题（IO 失败、网络断、SQL 异常） |
| 能否预防     | **理论上可以预防**——加 null 检查、校验索引等 | **无法预防**——磁盘满了、网络断了不可控    |
| 编译器行为   | 不强制处理                                   | 强制 try-catch 或 throws                  |
| 恢复期望     | 通常不应恢复（bug 应该修代码）               | 有时可以恢复（重试、降级、提示用户）      |
| 方法签名影响 | 不需要 `throws` 声明                         | 必须在签名中声明 `throws`                 |
| 设计者意图   | "这是你的 bug，修代码去"                     | "环境可能出问题，你最好有预案"            |

```java
// 非受检异常：可以预防，应该修代码
public String getUserName(User user) {
    return user.getName();  // NullPointerException
    // 预防方式：调用前检查 user != null
}

// 受检异常：无法预防，应该有预案
public String fetchFromApi(String url) throws IOException {
    URL api = new URL(url);
    HttpURLConnection conn = (HttpURLConnection) api.openConnection();  // throws IOException
    // 网络断了、超时了——你无法预防，只能设计重试/降级逻辑
    return readResponse(conn);
}
```

> 自定义异常应该继承谁？→ 详见[第 3 节：自定义异常](#3-自定义异常)。

---

### 2.3 throw 与 throws：制造与声明

上一节看到受检异常必须用 `throws` 声明——现在来详细讲解 `throw` 和 `throws` 这两个最容易混淆的关键字。`throw` 是一个动作：在此刻、在此地，抛出异常。`throws` 是一个声明：我可能会抛出异常，调用者请准备好处理。

```java
import java.io.IOException;

public class ConfigLoader {

    /**
     * throw：抛出自己检测到的错误
     */
    public int parseInt(String value) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("配置值不能为空");  // 主动抛出
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("配置值不是数字: " + value, e);  // 包装后抛出
        }
    }

    /**
     * throws：声明自己可能抛出的异常
     */
    public String load(String filePath) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filePath));
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }
        reader.close();
        return content.toString();
        // 如果 FileReader 构造失败或 readLine() 出错，
        // IOException 会自动向上冒泡到调用 load() 的方法
    }
}
```

**throw vs throws —— 最容易混淆的两个关键字：**

```
throw                         throws
══════════                    ══════════
在方法体内部使用               在方法签名上使用
后面跟 异常对象               后面跟 异常类型
是动作：主动抛出异常           是声明：可能抛出异常
throw new XxxException()      void load() throws IOException

记忆口诀：throw → 扔出去（动词），throws → 可能扔（声明）
```

**调用链上的异常传播：**

```java
public class App {

    public static void main(String[] args) {
        ConfigLoader loader = new ConfigLoader();

        try {
            String content = loader.load("/data/config.txt");  // load() 声明了 throws IOException
            System.out.println(content);
        } catch (IOException e) {
            // 必须处理！因为 IOException 是受检异常
            System.out.println("配置加载失败: " + e.getMessage());
        }
    }
}
```

```
异常传播路径（冒泡）

main()
  │  try-catch IOException
  ▼
load()  ─── throws IOException
  │
  ▼
new FileReader()  ─── 抛出 IOException
  │
  ▼
异常沿调用栈逐层向上 —— 直到某个 catch 捕获，或到达 main 导致程序终止
```

> **关键认识**：`throw` 是制造异常，`throws` 是转交责任。方法里抛出异常后，当前方法立即终止——不执行后续代码，直接跳到调用者的 catch 或继续冒泡。

---

### 2.4 常见非受检异常详解

以下每个异常都是 `RuntimeException` 的子类，编译器不强制处理，但触发条件明确，应该通过修代码预防。

#### NullPointerException

最常见也最容易避免的异常。对 `null` 引用调用方法、访问字段时触发。

```java
public void example() {
    String value = null;
    int length = value.length();  // NullPointerException
    // 预防方式：if (value != null) { ... }
    // 或使用 Optional（参考 optional-guide）
}
```

#### ClassCastException

强转类型不兼容时触发。常见于 Object 向具体类型强转、泛型擦除遗留问题。

```java
public void castExample(Object obj) {
    String s = (String) obj;  // ClassCastException（如果 obj 实际是 Integer 等非 String 类型）
    // 编译通过！但运行时 obj 不是 String → 抛 ClassCastException
    // 预防方式：if (obj instanceof String) { ... }
}
```

#### ArithmeticException

算术运算异常，最经典的是整数除零。

```java
public void divideExample(int a, int b) {
    int result = a / b;  // ArithmeticException（当 b = 0 时：/ by zero）
    // 编译通过！但运行时除数为 0 → 抛 ArithmeticException
    // 注意：浮点数除零不会抛异常（返回 Infinity 或 NaN），只有整数除零才会
    // 预防方式：if (b != 0) { ... }
}
```

#### IndexOutOfBoundsException

索引越界，有两个常见子类：`ArrayIndexOutOfBoundsException` 和 `StringIndexOutOfBoundsException`。

```java
public void processData(int[] arr, int index) {
    int val = arr[index];  // ArrayIndexOutOfBoundsException
    // 编译通过！没有 throws，没有 try-catch
    // 预防方式：if (index >= 0 && index < arr.length) { ... }
}

public void charAtExample(String text, int index) {
    char c = text.charAt(index);  // StringIndexOutOfBoundsException
    // 预防方式：if (index >= 0 && index < text.length()) { ... }
}
```

#### IllegalArgumentException

参数不合法，通常是主动检测后通过 `throw` 抛出。

```java
public void setAge(int age) {
    if (age < 0 || age > 150) {
        throw new IllegalArgumentException("年龄必须在 0-150 之间: " + age);
    }
    this.age = age;
}
```

#### NumberFormatException

`IllegalArgumentException` 的子类，字符串转数字失败时触发。`Integer.parseInt()`、`Long.parseLong()` 等方法都会抛出。

```java
public int parseAge(String input) {
    int age = Integer.parseInt(input);  // NumberFormatException（input 为 "abc" 时）
    // 触发条件明确：字符串内容不是有效数字
    // 预防方式：先校验 input.matches("\\d+")，或用 try-catch 包装
}
```

> **注意**：`NumberFormatException` 是 `IllegalArgumentException` 的子类，`catch (IllegalArgumentException e)` 也能捕获它。

> **小结**：非受检异常的共同特点——理论上都可以通过加判断预防。它们是"程序 bug"，应该修代码而不是 catch。

---

### 2.5 常见受检异常

受检异常不继承 `RuntimeException`，编译器强制 `try-catch` 或 `throws`。主要出现在与外部环境交互的场景。

#### IOException

最基础的 IO 异常，几乎所有文件/网络操作都会声明 `throws IOException`。

```java
public String readFile(String path) throws IOException {
    // FileReader 构造、readLine() 都可能抛 IOException
    try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
        return reader.readLine();
    }
    // 文件不存在 → FileNotFoundException（IOException 的子类）
    // 没有读取权限 → IOException
    // 磁盘满了 → IOException
}
```

#### SQLException

数据库操作异常。JDBC API 的核心异常，所有数据库操作都可能抛出。

```java
public void queryUser(Connection conn) throws SQLException {
    // Connection、Statement、ResultSet 的方法都声明 throws SQLException
    try (Statement stmt = conn.createStatement();
         ResultSet rs = stmt.executeQuery("SELECT * FROM users")) {
        while (rs.next()) {
            System.out.println(rs.getString("name"));
        }
    }
    // 数据库连接断开 → SQLException
    // SQL 语法错误 → SQLException
    // 违反约束 → SQLException 的子类（如 BatchUpdateException）
}
```

> **注意**：Spring Data 会把 `SQLException` 包装成非受检的 `DataAccessException`，你通常不需要直接处理 `SQLException`。

#### FileNotFoundException

`IOException` 的子类，专门表示文件不存在。`FileReader` 构造器声明 `throws FileNotFoundException`。

```java
public void loadFile(String path) throws IOException {
    // FileReader 构造器声明 throws FileNotFoundException
    try (FileReader reader = new FileReader(path)) {
        // 读取文件内容...
    }
    // 文件不存在时 → FileNotFoundException（IOException 的子类）
}
```

> **提示**：`FileNotFoundException` 是 `IOException` 的子类。如果想单独处理"文件不存在"和"其他 IO 错误"，可以 `catch (FileNotFoundException e)` 先捕获，再 `catch (IOException e)` 兜底——子类必须在父类前面。

> **小结**：受检异常的共同特点——无法通过修代码预防（磁盘可能满、网络可能断、文件可能被删）。正确的做法是设计恢复逻辑（重试、降级、提示用户）。

---

### 2.6 Error：JVM 级别的灾难

`Error` 和 `Exception` 同级，都继承自 `Throwable`，但它代表 **JVM 层面的严重问题**——不是你的代码能处理的：

```java
// Error 的子类示例——这些不是你该 catch 的
StackOverflowError      // 递归太深，栈溢出 —— 改递归为迭代
OutOfMemoryError        // 堆内存不足 —— 加大堆或查内存泄漏
NoClassDefFoundError    // 类找不到 —— 修 classpath
VirtualMachineError     // JVM 崩溃 —— 重启
```

```
Throwable 三兄弟的正确态度

Error           →   不用 catch，你也恢复不了（JVM 级别灾难）
受检异常         →   必须处理：要么 catch 要么 throws（编译器强制）
非受检异常       →   可以 catch，但更应该在开发阶段修掉 bug
```

---

## 3. 自定义异常

### 3.1 为什么要自定义异常

JDK 内置异常覆盖了通用场景，但在业务代码中语义不够精确：

```java
// ❌ 用 IllegalArgumentException，消息要自己拼，调用方只能靠字符串判断
throw new IllegalArgumentException("分类名「" + name + "」已存在");

// ✅ 自定义异常，类型本身就是语义，调用方可以精确 catch
throw new CategoryAlreadyExistsException(name);
```

自定义异常的三个价值：

| 价值     | 说明                                                     |
| -------- | -------------------------------------------------------- |
| 语义化   | 异常类型本身就是文档，看类名就知道出了什么错             |
| 精确捕获 | 调用方可以 `catch (CategoryAlreadyExistsException e)`    |
| 统一处理 | 继承同一基类，`catch (BusinessException e)` 一次搞定所有 |

### 3.2 继承谁：RuntimeException 还是 Exception

```java
// ✅ 推荐：自定义业务异常继承 RuntimeException（非受检）
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}

public class BusinessConflictException extends RuntimeException {
    public BusinessConflictException(String message) {
        super(message);
    }
}

// ❌ 不推荐：继承 Exception（会变成受检异常，所有调用链都要加 throws）
public class ResourceNotFoundException extends Exception { ... }
```

> **实践共识**：现代 Java 开发中，自定义业务异常几乎都继承 `RuntimeException`。
>
> - 受检异常主要出现在 JDK 核心库（IO、网络、数据库等与外部环境交互的场景）
> - Spring 的 `@Transactional` 默认只回滚 `RuntimeException`
> - Spring Framework 自身也几乎只用非受检异常（如 `DataAccessException`）

### 3.3 异常体系设计

实际项目中通常设计一个**基类** + 多个**具体异常**，便于统一处理：

```java
/**
 * 基础业务异常 —— 所有自定义异常的父类
 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * 资源未找到异常
 */
public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String resource, String id) {
        super(resource + " 不存在，ID: " + id);
    }
}

/**
 * 数据冲突异常
 */
public class BusinessConflictException extends BusinessException {
    public BusinessConflictException(String message) {
        super(message);
    }
}
```

为什么要设计基类？

- **统一 catch**：`catch (BusinessException e)` 一次捕获所有业务异常
- **统一扩展**：未来想给所有业务异常加错误码，只改基类即可

### 3.4 异常信息设计

```java
public class OrderException extends RuntimeException {
    private final String errorCode;  // 可选：错误码，便于前端区分处理

    public OrderException(String errorCode, String message) {
        super(message);               // ① message：清晰描述 + 关键上下文
        this.errorCode = errorCode;
    }

    public OrderException(String message, Throwable cause) {
        super(message, cause);        // ② cause：包装底层异常时必须传入根因
        this.errorCode = "ORDER_ERROR";
    }

    public String getErrorCode() {
        return errorCode;
    }
}
```

| 信息    | 作用                     | 何时使用                        |
| ------- | ------------------------ | ------------------------------- |
| message | 错误描述，包含关键上下文 | 始终提供                        |
| cause   | 保留底层异常的完整堆栈   | 包装其他异常时（参考第 4.1 节） |
| 错误码  | 前端根据错误码做不同处理 | 需要前端区分场景时              |

> **关键认识**：异常消息要让人一看就懂——"分类名「热菜」已存在"远好于"出错了"。包装异常时一定要传 `cause`，否则排查时丢失根因。

---

## 4. 进阶概念

### 4.1 异常链（包装异常）

底层抛出的异常（如 `IOException`）对上层调用者没有意义——Service 层不应该暴露 `SQLException`，Controller 层更不应该。**异常链**让你把底层异常包装成有业务语义的上层异常，同时保留根因信息。

```java
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileStorageService {

    public String readConfigFile(String path) {
        try {
            return Files.readString(Path.of(path));

        } catch (IOException e) {
            // ① 包装：底层 IOException → 业务语义的 FileOperationException
            // ② 把原始异常 e 作为 cause 传入，保留完整堆栈
            throw new FileOperationException("读取配置文件失败: " + path, e);
            //                                           ↑ 业务消息         ↑ 根因
        }
    }
}

// 自定义异常：构造器接收 cause
public class FileOperationException extends RuntimeException {
    public FileOperationException(String message, Throwable cause) {
        super(message, cause);  // 把 cause 传给父类 RuntimeException
    }
}
```

```java
// 调用方可以通过 getCause() 追溯根因
public class App {
    public static void main(String[] args) {
        FileStorageService service = new FileStorageService();
        try {
            service.readConfigFile("/data/config.txt");
        } catch (FileOperationException e) {
            System.out.println("业务错误: " + e.getMessage());  // "读取配置文件失败: /data/config.txt"
            System.out.println("根因: " + e.getCause());        // IOException: No such file
            // 打日志时把整个异常栈打出来，包含所有层级
            e.printStackTrace();
        }
    }
}
```

```
异常链的层级

Controller 层      FileOperationException: "读取配置文件失败"
                         ↑ cause
Service 层          FileOperationException: "读取配置文件失败: /data/config.txt"
                         ↑ cause
底层                IOException: "No such file or directory"

每一层都是同一个异常链上的节点，排查时从最底层开始找根因
```

> **关键认识**：异常链让每层只暴露与本层相关的异常类型，同时保留了完整的错误追踪信息。如果只用 `throw new XxxException("失败")` 不传 cause，排查时只能看到"失败"，不知道到底哪里出的问题。

### 4.2 try-with-resources（自动关闭资源）

第 2.1 节的 `finally` 关闭资源需要 5+ 行样板代码。Java 7 引入了 **try-with-resources**，自动关闭实现 `AutoCloseable` 接口的资源：

```java
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class ConfigLoader {

    // 老写法：手动在 finally 里关闭，繁琐且容易遗漏
    public String loadOldWay(String filePath) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(filePath));
            // ... 读取逻辑 ...
        } catch (IOException e) {
            return null;
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (IOException ignored) { }
            }
        }
        return null;
    }

    // 新写法：try-with-resources，自动关闭 ✅
    public String loadNewWay(String filePath) {
        // 资源声明在 try 的 () 中，代码块结束后自动 close()
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();
        } catch (IOException e) {
            System.out.println("读取失败: " + e.getMessage());
            return null;
        }
        // reader.close() 自动调用，无需 finally！
    }

    // 多个资源：用 ; 分隔，关闭顺序与打开顺序相反
    public void copyFile(String src, String dest) {
        try (FileReader in = new FileReader(src);
             FileWriter out = new FileWriter(dest)) {
            // 先关闭 out（后打开），再关闭 in（先打开）
            int c;
            while ((c = in.read()) != -1) {
                out.write(c);
            }
        } catch (IOException e) {
            System.out.println("文件复制失败: " + e.getMessage());
        }
    }
}
```

```
try-with-resources vs 传统 finally

老写法（7 行清理代码）              新写法（0 行清理代码）
══════════════════════              ══════════════════════
BufferedReader r = null;            try (BufferedReader r =
try {                                    new BufferedReader(...)) {
    r = new BufferedReader(...);        // 业务逻辑
    // 业务逻辑                     } catch (IOException e) {
} catch (IOException e) {               // 异常处理
    // 异常处理                    }
} finally {                        // r.close() 自动调用
    if (r != null) {
        try { r.close(); }
        catch (...) { }
    }
}
```

**哪些类支持 try-with-resources？** 所有实现了 `AutoCloseable`（Java 7+）或 `Closeable` 的类：

| 类别   | 示例                                                                  |
| ------ | --------------------------------------------------------------------- |
| IO 流  | `FileReader`、`BufferedReader`、`FileInputStream`、`FileOutputStream` |
| 数据库 | `Connection`、`Statement`、`ResultSet`                                |
| 网络   | `Socket`、`ServerSocket`                                              |
| Spring | `ConfigurableApplicationContext`（Spring 容器本身）                   |

> **实践建议**：永远用 try-with-resources 替代手动 finally 关闭资源。代码少、不会漏、关闭顺序也自动处理。

### 4.3 多异常捕获（multi-catch）

Java 7 起，一个 `catch` 可以捕获多种异常类型，用 `|` 分隔：

```java
public class DataProcessor {

    // 老写法：多个 catch 块写重复代码
    public void processOld(String data) {
        try {
            int value = Integer.parseInt(data);
            String text = getFromCache(value);
            System.out.println(text.toUpperCase());
        } catch (NumberFormatException e) {
            System.out.println("数据格式错误: " + e.getMessage());
        } catch (CacheException e) {
            System.out.println("缓存读取失败: " + e.getMessage());
        } catch (NullPointerException e) {
            System.out.println("数据为空: " + e.getMessage());
        }
        // 三种异常，三种 catch，三行重复的 System.out.println 结构
    }

    // 新写法：一个 catch 捕获多种异常
    public void processNew(String data) {
        try {
            int value = Integer.parseInt(data);
            String text = getFromCache(value);
            System.out.println(text.toUpperCase());
        } catch (NumberFormatException | CacheException e) {
            // 多种异常 → 统一处理
            System.out.println("数据处理失败: " + e.getMessage());
        }
    }

    // 也可以混合使用：分优先级捕获
    public void processMixed(String data) {
        try {
            int value = Integer.parseInt(data);
            String text = getFromCache(value);
        } catch (NumberFormatException e) {
            // NumberFormatException 单独处理（特殊逻辑）
            System.out.println("请确保输入的是数字");
        } catch (CacheException | NullPointerException e) {
            // CacheException 和 NullPointerException 统一处理
            System.out.println("系统内部错误，请稍后重试");
        }
    }
}
```

> **注意**：multi-catch 中的异常类型不能有继承关系——`catch (Exception | RuntimeException e)` 编译不通过，因为 `RuntimeException` 是 `Exception` 的子类。

---

## 5. 实战决策指南

### 5.1 选受检还是非受检？

```
你的异常应该选什么父类？

      使用场景
         │
         ├── 调用方几乎总能恢复？ ────→ 受检异常（非常罕见）
         │   例如：网络超时 → 重试
         │
         ├── 调用方通常无法恢复？ ────→ 非受检异常（大多数场景）
         │   例如：ID 找不到资源、名称重复、参数不合法
         │
         └── 不确定？ ────→ 非受检异常
              90% 的情况下这就是正确答案

Spring 团队的选择：整个 Spring Framework 几乎只用非受检异常。
DataAccessException、NoSuchBeanDefinitionException 等都继承 RuntimeException。
```

### 5.2 什么时候 catch，什么时候 throws？

```
异常处理决策树

你的方法遇到了异常
    │
    ├── 你能处理它吗？（能恢复 / 有降级方案 / 知道该返回什么）
    │       │
    │       ├── 能 → catch 并处理
    │       │   例如：重试、返回默认值、提示用户、记录后继续
    │       │
    │       └── 不能 → throws（让调用者处理）
    │
    ├── 这是你的模块边界吗？（Service → Controller）
    │       │
    │       ├── 是 → 包装成上层理解的异常再抛
    │       │   Service 层抛 FileOperationException 而不是 IOException
    │       │
    │       └── 否 → 继续向上抛
    │
    └── 这是最外层吗？（Controller 或 main）
            │
            ├── 是 → 一定要 catch！
            │   Spring 项目：@ExceptionHandler 统一兜底
            └── 否 → 交给你上层的调用者决定
```

### 5.3 异常处理的反模式

```
❌ 反模式 1：空的 catch 块（吞异常）

try {
    importantOperation();
} catch (Exception e) {
    // 什么都不做 —— 异常被无声吞掉，线上排查地狱
}

✅ 至少记日志：
try {
    importantOperation();
} catch (Exception e) {
    log.error("操作失败", e);
    throw e;  // 或者重新抛出
}


❌ 反模式 2：catch Exception 却只处理 RuntimeException

try {
    someMethod();
} catch (Exception e) {
    System.out.println(e.getMessage());  // IOException 来了也这样？数据库连接断了也这样？
}

✅ 精确捕获：
try {
    someMethod();
} catch (IOException e) {
    // 针对 IO 的处理
} catch (SQLException e) {
    // 针对数据库的处理
}


❌ 反模式 3：把异常当流程控制用

// 用异常处理正常业务逻辑 —— 性能差、语义错
public boolean isNumeric(String s) {
    try {
        Integer.parseInt(s);  // 只为了判断"能不能转"
        return true;
    } catch (NumberFormatException e) {
        return false;  // 用异常控制分支！
    }
}

✅ 用条件判断代替：
public boolean isNumeric(String s) {
    return s != null && s.matches("\\d+");
}


❌ 反模式 4：在 finally 中 return 或抛异常

try {
    return doSomething();  // 准备返回 "result"
} finally {
    return "finally";  // finally 中的 return 覆盖了上面的返回值！
}

✅ finally 只做清理，不放 return 或 throw
```

### 5.4 各层异常处理策略

```
分层异常策略

Controller 层  →  不 catch，异常由 @ExceptionHandler 统一处理
                   （参考 Spring Boot 统一异常处理指南）

Service 层     →  抛自定义业务异常（继承 RuntimeException）
                   需要时包装底层异常（保留 cause）

Repository 层  →  抛 Spring Data 内置异常（如 DataAccessException）
                   或包装为自定义异常

工具类         →  抛明确的非受检异常 + 清晰的异常消息
                   如果是 IO 操作，可以抛出受检异常并声明 throws
```

---

## 6. 速查清单

### 6.1 异常体系速查

```
Throwable
├── Error                    ← JVM 灾难，不管
│   ├── OutOfMemoryError
│   ├── StackOverflowError
│   └── ...
│
└── Exception
    ├── RuntimeException     ← 非受检（unchecked）：不强制处理
    │   ├── NullPointerException
    │   ├── IllegalArgumentException
    │   │   └── NumberFormatException
    │   ├── IndexOutOfBoundsException
    │   ├── IllegalStateException
    │   ├── ArithmeticException
    │   ├── ClassCastException
    │   └── 你的自定义异常 ✅
    │
    └── 其他 Exception       ← 受检（checked）：强制 try-catch 或 throws
        ├── IOException
        ├── SQLException
        ├── FileNotFoundException
        └── ...
```

### 6.2 关键字速查

| 关键字 / 机制        | 位置           | 作用                                             | 示例                                     |
| -------------------- | -------------- | ------------------------------------------------ | ---------------------------------------- |
| `throw`              | 方法体内       | 抛出异常（制造异常）                             | `throw new XxxException("消息")`         |
| `throws`             | 方法签名       | 声明可能抛出的异常类型（转交责任）               | `void load() throws IOException`         |
| `try-catch`          | 方法体内       | 捕获并处理异常                                   | `try { ... } catch (XxxException e) { }` |
| `finally`            | try-catch 之后 | 无论是否异常都执行的清理代码                     | `finally { resource.close(); }`          |
| `try-with-resources` | 替代 finally   | 自动关闭实现 `AutoCloseable` 的资源              | `try (FileReader r = ...) { ... }`       |
| `multi-catch`        | catch 块       | 一个 catch 捕获多种异常                          | `catch (Xxx1 \| Xxx2 e)`                 |
| `getCause()`         | 异常对象       | 获取异常链中的根因异常                           | `e.getCause()`                           |
| `printStackTrace()`  | 异常对象       | 打印完整异常链（开发调试用，生产建议用日志框架） | `e.printStackTrace()`                    |

### 6.3 受检 vs 非受检 速查

```
对比维度              非受检（unchecked）         受检（checked）
═══════════════════════════════════════════════════════════════════
父类                  RuntimeException           Exception（非 RuntimeException）
编译器               不强制处理                  强制 try-catch 或 throws
典型场景             程序 bug                   外部环境问题
代表类               NPE、ClassCast、Arithmetic  IOException、SQLException
自定义异常           继承 RuntimeException ✅    继承 Exception（不推荐）
能否预防             能（修代码）                不能（环境不可控）
方法签名影响         不需要 throws               必须有 throws
Spring 事务回滚      默认回滚                    默认不回滚
```

### 6.4 自定义异常模板

> 详细说明见[第 3 节](#3-自定义异常)。

```java
/**
 * 基础业务异常 —— 所有自定义异常的父类（可选，便于统一 catch）
 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * 资源未找到异常
 */
public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String resource, String id) {
        super(resource + " 不存在，ID: " + id);
    }
}

/**
 * 数据冲突异常
 */
public class BusinessConflictException extends BusinessException {
    public BusinessConflictException(String message) {
        super(message);
    }
}
```

### 6.5 最佳实践清单

```
✅ 推荐                                           ❌ 避免
════════════════════════════════════════════════════════════════
自定义异常继承 RuntimeException                      自定义异常继承 Exception
try-with-resources 管理资源                         手动 finally 关闭资源
catch 精确的异常类型                                catch (Exception e) 一刀切
包装异常时传 cause                                 包装异常时丢失根因
Service 层抛语义异常                                Controller 层 try-catch 处理
finally 只做资源清理                                finally 中 return 或 throw
异常消息清晰（"分类名已存在"）                      异常消息模糊（"出错了"）
条件判断代替异常做流程控制                           用 try-catch 做正常分支判断
```

### 6.6 决策流程

```
1. 写自定义异常？
   └── 继承 RuntimeException（非受检）✅

2. 方法里遇到异常，catch 还是 throws？
   ├── 能处理（有兜底方案） → catch
   ├── 不能处理 → throws（抛给调用者）
   └── 模块边界 → catch，包装后重新抛（保留 cause）

3. 需要关闭资源？
   └── try-with-resources ✅（不是 finally）

4. Controller / main 方法里？
   └── 必须 catch！用 @ExceptionHandler 统一兜底
```
