# Java 变量与类型系统

> **Audience**：有通用编程经验的开发者，刚接触 Java 或需要系统梳理类型体系。
> **Outcome**：理解 Java 变量的声明、作用域与生命周期；掌握基本类型与引用类型的本质区别；能够正确处理类型转换、自动装箱和常见陷阱；了解类型推断等现代特性。
> **Applicable version**：Java 8+；标注 `Java 10+` 或更高版本号的特性以前述版本为界。

## 目录

- [Scope](#scope)
- [变量：数据的基本容器](#变量数据的基本容器)
- [Java 的类型世界：基本类型与引用类型](#java-的类型世界基本类型与引用类型)
- [基本类型详解](#基本类型详解)
- [引用类型与对象](#引用类型与对象)
- [类型转换与自动提升](#类型转换与自动提升)
- [类型推断与现代变量声明](#类型推断与现代变量声明)
- [常量与不可变性](#常量与不可变性)
- [常见陷阱与最佳实践](#常见陷阱与最佳实践)
- [References](#references)

## Scope

本指南覆盖 Java 类型系统的核心概念：变量声明与初始化、八种基本类型及其包装类、引用类型与 `null`、类型转换规则、自动装箱与拆箱、`final` 常量，以及 Java 10 引入的 `var` 类型推断。不涉及的内容包括泛型、数组的深入操作、自定义类型层次设计、以及 JVM 内存模型 —— 这些属于独立专题。

## 变量：数据的基本容器

Java 是一门**静态类型语言**：每个变量在使用前必须声明其类型，编译器在编译期检查类型一致性。

### 声明与初始化

变量声明包含三个要素：**类型**、**名称**、以及可选的**初始值**。

> Illustrative fragment

```java
// 声明 + 初始化（推荐）
int count = 0;
String name = "Java";

// 先声明，后赋值
double price;
price = 9.99;

// 一行声明多个同类型变量（可读性较差，谨慎使用）
int x = 1, y = 2, z = 3;
```

未初始化的**局部变量**不能被读取 —— 编译器会直接报错。这一规则是 Java 防御性设计的体现：

```java
int result;
System.out.println(result); // 编译错误：variable result might not have been initialized
```

**字段**（成员变量）则不同：如果未显式初始化，JVM 会自动赋予默认值（参见 [基本类型详解](#基本类型详解)）。

### 变量分类

按声明位置，Java 变量分为三类：

```
┌──────────────────────────────────────────────────┐
│                 Java 变量分类                      │
├────────────────┬─────────────────┬───────────────┤
│   局部变量      │    实例变量      │    静态变量    │
│   (方法内)      │   (无 static)   │   (static)    │
├────────────────┼─────────────────┼───────────────┤
│ 必须显式初始化   │ 自动初始化为默认值│ 自动初始化为默认值│
│ 栈上分配        │ 堆上分配         │ 方法区分配      │
│ 线程安全(隔离)   │ 线程共享         │ 线程共享       │
└────────────────┴─────────────────┴───────────────┘
```

> Illustrative fragment

```java
public class VariableDemo {
    // 实例变量
    private int instanceCount;       // 自动初始化为 0
    private String instanceName;     // 自动初始化为 null

    // 静态变量
    private static int classCount;   // 自动初始化为 0

    public void method() {
        // 局部变量
        int localCount = 0;          // 必须显式初始化
        String localName = "hello";  // 必须显式初始化
    }
}
```

### 命名约定

| 变量类型                     | 约定             | 示例                                 |
| ---------------------------- | ---------------- | ------------------------------------ |
| 局部变量 / 实例变量 / 类变量 | camelCase        | `userName`, `maxRetryCount`          |
| 常量 (`static final`)        | UPPER_SNAKE_CASE | `MAX_CONNECTIONS`, `DEFAULT_TIMEOUT` |
| 包名                         | 全小写，点分隔   | `com.example.myapp`                  |

变量名不能以数字开头，不能使用保留关键字；Java 允许使用 `$` 和 `_`，但 `_` 作为单字符标识符从 Java 9 起已禁止。

## Java 的类型世界：基本类型与引用类型

这是 Java 类型系统最根本的分野。

```
                    ┌──────────────┐
                    │  Java 类型    │
                    └──────┬───────┘
           ┌───────────────┴───────────────┐
     ┌─────┴─────┐                   ┌─────┴─────┐
     │  基本类型   │                   │  引用类型   │
     │ Primitive  │                   │ Reference  │
     └─────┬─────┘                   └─────┬─────┘
   ┌───────┼──────────┐                   │
   │       │          │            ┌──────┼──────┐
   │       │          │          class  interface  array
 ┌─┴─┐  ┌──┴──┐  ┌───┴───┐
整型  浮点  字符  布尔
```

二者的本质区别在于**存储内容**：

- **基本类型**的变量直接保存**值本身**。赋值时复制值，两个变量独立。
- **引用类型**的变量保存的是**指向对象的指针**（引用）。赋值时复制引用，两个变量指向同一个对象。

> Illustrative fragment

```java
// 基本类型：值复制，互不影响
int a = 10;
int b = a;
b = 20;
System.out.println(a); // 10 —— a 不受影响

// 引用类型：引用复制，指向同一对象
int[] arr1 = {1, 2, 3};
int[] arr2 = arr1;
arr2[0] = 999;
System.out.println(arr1[0]); // 999 —— arr1 也变了
```

基本类型不具备方法、不能为 `null`、直接存放在栈上（局部变量场景）。引用类型可以调用方法、可以为 `null`、对象本身分配在堆上。

> 这种设计来自 Java 的性能考量：基本类型避免了对象头开销和指针解引用，是数值计算高效的基石。但同时提供了包装类（如 `Integer`）让基本值在需要时可以表现为对象。

## 基本类型详解

Java 定义了八种基本类型，按语义分为四组：

### 整型

| 类型    | 大小    | 范围                        | 默认值 | 典型场景          |
| ------- | ------- | --------------------------- | ------ | ----------------- |
| `byte`  | 8 bits  | \(-128\) ~ \(127\)          | `0`    | 二进制数据、IO 流 |
| `short` | 16 bits | \(-32768\) ~ \(32767\)      | `0`    | 节省内存的大数组  |
| `int`   | 32 bits | \(\pm 2.147 \times 10^9\)   | `0`    | **默认整数类型**  |
| `long`  | 64 bits | \(\pm 9.22 \times 10^{18}\) | `0L`   | 时间戳、大数值    |

> Illustrative fragment

```java
int decimal = 100;            // 十进制
int hex = 0xFF;               // 十六进制（255）
int binary = 0b1010;          // 二进制（10，Java 7+）
long big = 9_000_000_000L;    // long 字面量需要 L 后缀，下划线增强可读性
```

**核心约定**：整数运算默认使用 `int`。`byte` 和 `short` 在做算术运算时会自动提升为 `int`。

### 浮点型

| 类型     | 大小    | 精度           | 默认值 | 典型场景                   |
| -------- | ------- | -------------- | ------ | -------------------------- |
| `float`  | 32 bits | ~7 位有效数字  | `0.0f` | 内存敏感的图形计算         |
| `double` | 64 bits | ~15 位有效数字 | `0.0d` | **默认浮点类型**、科学计算 |

> Illustrative fragment

```java
double pi = 3.141592653589793;    // 默认 double
float half = 0.5f;                // float 字面量必须加 f/F
double scientific = 1.5e-3;       // 科学记数法 = 0.0015

// 浮点精度的经典陷阱
System.out.println(0.1 + 0.2);    // 0.30000000000000004
```

> `0.1 + 0.2 != 0.3` 不是 Java 的 bug，而是 IEEE 754 浮点数表示固有限制。需要精确十进制计算的场景（如货币），应使用 `BigDecimal`，而非 `float` 或 `double`。

### 字符与布尔

| 类型      | 大小         | 范围                                | 默认值     |
| --------- | ------------ | ----------------------------------- | ---------- |
| `char`    | 16 bits      | `'\u0000'` ~ `'\uffff'` (0 ~ 65535) | `'\u0000'` |
| `boolean` | JVM 实现相关 | `true` / `false`                    | `false`    |

`char` 存储 Unicode 字符（UTF-16 编码），单个 `char` 只能表示基本多语言平面（BMP）内的字符。增补字符（如某些 emoji）需要使用 `int` 码点或两个 `char` 代理对：

> Illustrative fragment

```java
char letter = 'A';
char unicode = '\u4e2d';         // '中'
char tab = '\t';                // 转义字符

// 增补字符需用码点表示
int emojiCodePoint = 0x1F600;   // 😀，超出了 char 范围
String emoji = new String(Character.toChars(emojiCodePoint));
```

`boolean` 只有 `true` 和 `false` 两个值。与 C/C++ 不同，**Java 中 `boolean` 不能与整数互转**：

```java
boolean flag = true;
// int n = (int) flag;       // 编译错误
// if (1) { ... }            // 编译错误
```

## 引用类型与对象

### 引用即"遥控器"

可以把引用想象成遥控器，对象本身是电视机：复制引用 = 多一个遥控器控制同一台电视；`null` = 遥控器没配对任何电视，按任何键都会出错。

### 创建对象

> Illustrative fragment

```java
// 使用 new 关键字创建
String text = new String("hello");

// 字符串字面量（最常用，享元模式）
String text2 = "hello";

// 数组是特殊的引用类型
int[] numbers = new int[5];          // 元素自动初始化为 0
String[] names = new String[3];      // 元素自动初始化为 null
```

### null 与 NullPointerException

`null` 是引用类型的"空值"，表示变量没有指向任何对象。在 `null` 引用上调用方法或访问字段会抛出 `NullPointerException`——Java 程序中最高频的运行时异常。

```java
String str = null;
int len = str.length();  // NullPointerException
```

规避策略：

- 方法返回集合时优先返回空集合 `Collections.emptyList()` 而非 `null`
- 使用 `Optional<T>` 表达"可能为空"的语义（详见 [`optional-guide.md`](optional-guide.md)）
- 使用 `Objects.requireNonNull()` 做前置校验
- 善用 IDE 的 `@Nullable` / `@NotNull` 注解辅助静态检查

### String 的特殊性

`String` 是引用类型，但拥有值类型的部分行为——它是**不可变的**。每次"修改"字符串实际上都会创建新对象：

```java
String s = "Hello";
s.toUpperCase();             // 返回新 String，s 本身不变
System.out.println(s);       // "Hello" —— 仍然是 "Hello"

s = s.toUpperCase();         // 重新赋值引用
System.out.println(s);       // "HELLO"
```

由于不可变性，频繁拼接应使用 `StringBuilder`（非线程安全）或 `StringBuffer`（线程安全）。

## 类型转换与自动提升

### 隐式转换（Widening）

从小范围到大范围的转换是安全的，编译器自动处理：

```
byte → short → int → long → float → double
                ↖ char ↗
```

```java
int i = 100;
long l = i;        // 自动：int → long（安全）
double d = i;      // 自动：int → double（安全）
```

### 显式转换（Narrowing Casting）

从大范围到小范围必须显式转换，可能丢失数据：

```java
double pi = 3.14159;
int approx = (int) pi;      // 3 —— 小数部分直接截断，不四舍五入

long big = 9_000_000_000_000L;
int small = (int) big;      // 溢出，结果不可预测
```

### 表达式中的自动提升

混合类型的算术表达式中，Java 按以下规则自动提升：

1. `byte`、`short`、`char` → 先提升为 `int`
2. 若有一个 `long` → 整个表达式提升为 `long`
3. 若有一个 `float` → 整个表达式提升为 `float`
4. 若有一个 `double` → 整个表达式提升为 `double`

```java
byte b1 = 10, b2 = 20;
// byte b3 = b1 + b2;       // 编译错误：b1 + b2 结果是 int
int b3 = b1 + b2;           // 正确

int i = 5;
double d = 2.5;
double result = i + d;      // 7.5 —— int 自动提升为 double
```

### 自动装箱与拆箱（Autoboxing / Unboxing）

Java 编译器在基本类型和其包装类之间自动转换：

| 基本类型  | 包装类      |
| --------- | ----------- |
| `byte`    | `Byte`      |
| `short`   | `Short`     |
| `int`     | `Integer`   |
| `long`    | `Long`      |
| `float`   | `Float`     |
| `double`  | `Double`    |
| `char`    | `Character` |
| `boolean` | `Boolean`   |

```java
// 装箱（boxing）：基本类型 → 包装类
Integer boxed = 42;               // 等价于 Integer.valueOf(42)

// 拆箱（unboxing）：包装类 → 基本类型
int unboxed = boxed;              // 等价于 boxed.intValue()

// 混合运算中自动拆箱
Integer a = 100;
Integer b = 200;
int sum = a + b;                  // a 和 b 自动拆箱为 int 后相加
```

> 自动装箱依赖 `valueOf()` 工厂方法，`Integer.valueOf()` 默认缓存了 \(-128\) ~ \(127\) 范围内的实例。这意味着在此范围内用 `==` 比较可能意外返回 `true`，但超出范围则返回 `false` —— 永远用 `.equals()` 比较包装类的内容。

## 类型推断与现代变量声明

### var（Java 10+）

`var` 允许编译器从初始化表达式中推断变量类型。它是**局部变量类型推断**，并非动态类型——变量在编译后仍然有确定的静态类型。

> Illustrative fragment

```java
// 适用：右侧类型已经很明显
var users = new ArrayList<User>();        // ArrayList<User>
var stream = users.stream();              // Stream<User>
var name = "Java";                        // String

// 不适用：右侧字面量含义模糊
var result = someMethod();                // 类型是什么？可读性下降
var value = 0;                            // int，但也许你期望的是 long 或 double？

// var 只能用于局部变量，不能用于字段、方法参数或返回类型
```

选择建议：当右侧的类型名已经充分说明意图时（尤其在泛型实例化中），`var` 减少冗余；当右侧类型不明显时，显式声明类型更有助于代码阅读。

### Diamond Operator（Java 7+）

泛型实例化时，编译器可推断类型参数：

```java
// 完整写法
Map<String, List<Integer>> map = new HashMap<String, List<Integer>>();

// 菱形语法（Java 7+）
Map<String, List<Integer>> map = new HashMap<>();

// 结合 var（Java 10+）
var map = new HashMap<String, List<Integer>>(); // Map<String, List<Integer>>
```

## 常量与不可变性

### final 变量

`final` 修饰的变量只能赋值一次，之后不可更改：

```java
final int maxRetries = 3;
// maxRetries = 5;              // 编译错误

final String appName;
appName = "MyApp";              // 允许：首次赋值（blank final）
// appName = "Other";           // 编译错误：不能再次赋值
```

`final` 作用于基本类型和引用类型有不同效果：

```java
final int[] values = {1, 2, 3};
// values = new int[]{4, 5};    // 编译错误：不能改变引用指向
values[0] = 999;                 // 允许：引用没变，但对象内容可改
```

> `final` 保护引用的不变，不保护引用对象的内部状态。要保护内部状态，需要不可变类（immutable class）。

### 编译时常量

同时使用 `static final` + 基本类型或 String + 编译期可确定的值 = **编译时常量**。编译器会将其内联到使用位置：

```java
public static final int MAX_SIZE = 100;          // 编译时常量
public static final String APP_VERSION = "1.0";   // 编译时常量
```

### 不可变对象的构建

一个真正不可变的类需要：

1. 类声明为 `final`（防止子类破坏）
2. 所有字段 `private final`
3. 不提供 setter
4. 返回可变字段时做防御性拷贝

> Illustrative fragment

```java
public final class Money {
    private final double amount;

    public Money(double amount) {
        this.amount = amount;
    }

    public double getAmount() {
        return amount;  // 基本类型，直接返回，安全
    }
}
```

Java 14+ 提供了更简洁的 `record`（不在本指南范围），但可将其视为不可变数据载体的推荐方案。

## 常见陷阱与最佳实践

### 1. 包装类的 == 比较

```java
Integer a = 127;
Integer b = 127;
System.out.println(a == b);         // true  —— 走了缓存

Integer c = 200;
Integer d = 200;
System.out.println(c == d);         // false —— 超出缓存范围
System.out.println(c.equals(d));    // true  —— 正确做法
```

**规则**：比较包装类内容永远用 `.equals()`，`==` 比较的是引用地址。

### 2. 拆箱时的 NPE

```java
Integer nullable = null;
int value = nullable;               // NullPointerException —— 自动拆箱 null
```

在混合运算、三元运算符、方法返回类型为基本类型但实际返回包装类 `null` 的场景中尤其隐蔽。

### 3. 浮点数的等值比较

```java
double a = 0.1 + 0.2;
double b = 0.3;
System.out.println(a == b);                    // false
System.out.println(Math.abs(a - b) < 1e-9);    // true —— 用误差范围
```

**规则**：浮点数用 `Math.abs(a - b) < epsilon` 比较近似相等，或用 `BigDecimal`。

### 4. 字符串拼接的性能陷阱

```java
// 反模式：循环中直接拼接（每次创建新 String 对象）
String result = "";
for (int i = 0; i < 10000; i++) {
    result += i;                    // O(n^2) 时间复杂度
}

// 推荐：使用 StringBuilder
StringBuilder sb = new StringBuilder();
for (int i = 0; i < 10000; i++) {
    sb.append(i);
}
String result2 = sb.toString();     // O(n)
```

### 5. 选择基本类型还是包装类

| 场景                         | 推荐                                     |
| ---------------------------- | ---------------------------------------- |
| 循环中的局部变量、数值计算   | 基本类型                                 |
| 集合元素（`List`、`Map` 等） | 包装类（集合只能存对象）                 |
| 数据库实体字段               | 包装类（支持 `null` 表示"无值"）         |
| 方法参数、返回值             | 基本类型优先；语义上确实可为空时用包装类 |

## References

- [The Java Language Specification, Java SE 8 Edition](https://docs.oracle.com/javase/specs/jls/se8/html/) — Chapters 4 (Types, Values, and Variables), 5 (Conversions and Contexts)
- [IEEE 754-2019 Standard for Floating-Point Arithmetic](https://standards.ieee.org/ieee/754/6210/)
- [JEP 286: Local-Variable Type Inference](https://openjdk.org/jeps/286) — `var` (Java 10)

---

## Verification summary

- **Structure**: `validate_guide.py` pending（将在写入后运行）
- **Code**: 所有代码块为 Illustrative fragment，未声明为可运行完整示例；语法和符号经过人工审查
- **Sources**:
  - Java Language Specification (JLS) SE 8 — 类型系统、转换规则、命名约定
  - JEP 286 — `var` 类型推断
  - IEEE 754 — 浮点行为
- **Unverified**:
  - 代码示例未在 Java 编译器下实际编译运行（标记为 Illustrative fragment，不声称可运行）
  - `Integer.valueOf()` 缓存范围为 \(-128\) 到 \(127\) 是标准行为，但未在当前环境实际验证属性配置
