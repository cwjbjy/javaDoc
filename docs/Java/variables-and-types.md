# Java 变量与类型系统

> **Audience**：有通用编程经验的开发者，刚接触 Java 或需要系统梳理类型体系。
> **Outcome**：理解 Java 变量的声明、作用域与生命周期；掌握基本类型与引用类型的本质区别；能识别并处理类型的**运行期边界**——数值溢出、`BigDecimal` 精度、泛型擦除、`Object`/JSON 动态值的安全转换、`final`/`volatile`/`ThreadLocal` 的共享状态陷阱；了解类型推断等现代特性。
> **Applicable version**：Java 8+；标注 `Java 10+` 或更高版本号的特性以前述版本为界。

## 目录

- [Scope](#scope)
- [变量：数据的基本容器](#变量数据的基本容器)
- [Java 的类型世界：基本类型与引用类型](#java-的类型世界基本类型与引用类型)
- [基本类型详解](#基本类型详解)
- [数值边界：溢出、精度与整数除法](#数值边界溢出精度与整数除法)
- [BigDecimal：精确十进制计算](#bigdecimal精确十进制计算)
- [引用类型与对象](#引用类型与对象)
- [泛型、通配符与类型擦除](#泛型通配符与类型擦除)
- [类型转换与自动提升](#类型转换与自动提升)
- [动态值的安全转换：Object 与 JSON](#动态值的安全转换object-与-json)
- [类型推断与现代变量声明](#类型推断与现代变量声明)
- [常量、不可变性与共享状态的边界](#常量不可变性与共享状态的边界)
- [常见陷阱与最佳实践](#常见陷阱与最佳实践)
- [References](#references)

## Scope

本指南覆盖 Java 类型系统的核心概念与其**运行期边界**：变量声明与初始化、八种基本类型及其包装类、数值溢出与精度、`BigDecimal` 精确十进制、引用类型与 `null`、泛型/通配符/类型擦除、类型转换与自动装箱、`Object`/JSON 动态值的安全转换、`var` 类型推断，以及 `final`/`volatile`/`ThreadLocal` 的变量视角。

一条主线贯穿全文：**编译期的类型保证会在运行期的每个边界上失效——泛型被擦除、`Object`/JSON 值类型未知、整数会溢出、`double` 不能精确表示十进制、`final` 不等于不可变、共享变量存在可见性问题——你需要知道在哪里、用什么手段把保证重新建立起来。**

为保持聚焦，以下主题只做"最小本地讲解 + 链接"，深度交给专门文档：JVM 内存布局与 GC → [`jvm-memory-gc.md`](jvm-memory-gc.md)；`volatile`/原子类的并发机制 → [`multithreading-basics.md`](multithreading-basics.md)；JSON 库取值 API → [`fastjson-guide.md`](fastjson-guide.md) / [`jackson-guide.md`](jackson-guide.md)；泛型反序列化 `TypeReference` 用法 → [`fastjson-guide.md`](fastjson-guide.md)；`Optional` 空值语义 → [`optional-guide.md`](optional-guide.md)。数组的深入操作、自定义类型层次设计，以及 **Java 时间日期 API**（`java.time`）不在本文范围，后者将独立成篇。

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

按声明位置，Java 变量分为三类。下表的**作用域、生命周期、线程共享关系**是 Java 语言规则；而"变量/对象具体分配在哪块内存"属于 JVM 实现细节，不应作为语言规则记忆（见表后说明）：

```
┌────────────────────────────────────────────────────┐
│                   Java 变量分类                      │
├────────────────┬─────────────────┬─────────────────┤
│    局部变量      │     实例变量      │     静态变量      │
│   (方法内)       │   (无 static)    │    (static)      │
├────────────────┼─────────────────┼─────────────────┤
│ 作用域：方法/块内 │ 作用域：整个对象   │ 作用域：整个类     │
│ 生命周期：随栈帧  │ 生命周期：随对象   │ 生命周期：随类     │
│ 必须显式初始化    │ 自动初始化为默认值 │ 自动初始化为默认值 │
│ 线程独占(不共享)  │ 线程共享(经对象)   │ 线程共享(全类一份) │
└────────────────┴─────────────────┴─────────────────┘
```

> **关于"栈/堆/方法区"**：很多资料写"局部变量在栈、实例变量在堆、静态变量在方法区"，这是对应试口诀的过度简化，并不准确：① 对象的实际分配位置由 **JVM 实现与逃逸分析**决定——未逃逸的对象可能被标量替换或栈上分配，"对象一定在堆"并不总成立；② **静态字段不等同于传统意义的"方法区"**——自 JDK 7 起字符串常量池与静态字段随 `Class` 对象移入**堆**，自 JDK 8 起类的元数据改由 **Metaspace**（本地内存，非堆）承载，"永久代/方法区"已是历史概念。运行时数据区的准确划分见 [`jvm-memory-gc.md`](jvm-memory-gc.md)。

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

## 数值边界：溢出、精度与整数除法

基本类型的取值范围有限（见 [基本类型详解](#基本类型详解)），一旦运算结果越过边界，Java **不报错、而是静默回绕**——这是数值计算最隐蔽的一类 bug。

### 整数溢出与回绕

> Illustrative fragment

```java
int max = Integer.MAX_VALUE;      // 2147483647
System.out.println(max + 1);      // -2147483648 —— 回绕到最小值，不抛异常

// 更隐蔽：中间结果先按 int 溢出，再赋给 long 也救不回
int a = 1_000_000;
long wrong = a * a;               // int * int 仍是 int，先溢出
System.out.println(wrong);        // -727379968（错误）
long right = (long) a * a;        // 先提升一个操作数为 long 再乘
System.out.println(right);        // 1000000000000（正确）
```

**规则**：`int * int` 的结果仍是 `int`，溢出发生在提升为 `long` **之前**。要得到 `long` 结果，必须让**至少一个操作数**先是 `long`。

### Math.\*Exact：把静默错误变成显式异常

当"算错"比"抛异常"更危险时（如金额分账、库存扣减、分页偏移），用 `Math` 的精确运算方法让溢出立即暴露：

> Illustrative fragment

```java
Math.addExact(Integer.MAX_VALUE, 1);      // 抛 ArithmeticException: integer overflow
Math.multiplyExact(1_000_000, 1_000_000); // 抛 ArithmeticException
Math.toIntExact(3_000_000_000L);          // long 超 int 范围 → 抛 ArithmeticException
```

`Math.toIntExact` 尤其适合把 `long` 收窄为 `int`（如把数据库返回的 `long count` 转成分页参数）——它拒绝静默截断。

### 整数除法截断

两个整数相除，结果仍是整数，**小数部分直接丢弃**（向零取整），不是四舍五入：

> Illustrative fragment

```java
System.out.println(7 / 2);        // 3 —— 不是 3.5
System.out.println(-7 / 2);       // -3 —— 向零取整
System.out.println(1 / 2);        // 0

// 需要小数结果：先转 double，或改用 BigDecimal
double d1 = 7.0 / 2;              // 3.5
double d2 = (double) 7 / 2;       // 3.5 —— 强转要在除法之前
```

### long 与 JavaScript 安全整数

Java 的 `long` 是 64 位，而 JavaScript 的 `number` 是 IEEE 754 双精度，只能**精确表示** \(-(2^{53}-1)\) ~ \(2^{53}-1\)（`Number.MAX_SAFE_INTEGER` = 9007199254740991）。雪花 ID、纳秒时间戳等常超出此范围，直接以数字下发前端会**丢精度**：

> Illustrative fragment

```java
long snowflakeId = 7231920345678901234L;   // > 2^53-1
// 若作为 JSON number 下发，前端 JSON.parse 后末几位会变成 0
```

**协议约定**：超过 JS 安全整数范围的大整数 ID，前后端应约定**以字符串传递**。序列化侧可把字段声明为 `String`，或用 Jackson 的 `@JsonSerialize(using = ToStringSerializer.class)` / 全局 `ObjectMapper` 配置将 `Long` 输出为字符串。具体做法见 [`jackson-guide.md`](jackson-guide.md)。

## BigDecimal：精确十进制计算

`float`/`double` 是二进制浮点，无法精确表示大多数十进制小数（`0.1 + 0.2 != 0.3`，见 [浮点型](#浮点型)）。凡涉及**金额、持股、融资额、比例、统计值、区间校验**，一律用 `java.math.BigDecimal`——它用"未缩放整数 + 标度（scale）"精确表示十进制。

### 构造：字符串或 valueOf，绝不用 double

> Illustrative fragment

```java
new BigDecimal("0.1");        // 精确 0.1 ✅（推荐）
BigDecimal.valueOf(0.1);      // 走 Double.toString，得到 0.1 ✅
new BigDecimal(0.1);          // 0.1000000000000000055511151231257827... ❌
```

`new BigDecimal(double)` 会把 double 的**二进制表示误差**如实搬进来，这是最经典的坑。要么传字符串，要么用 `valueOf`。

### 比较：数值用 compareTo，equals 连 scale 一起比

> Illustrative fragment

```java
BigDecimal x = new BigDecimal("1.0");
BigDecimal y = new BigDecimal("1.00");

x.equals(y);        // false —— equals 同时比较数值与 scale（1 位 vs 2 位）
x.compareTo(y);     // 0 —— 只比数值，1.0 == 1.00
```

**规则**：判断数值相等/大小**永远用 `compareTo`**（返回 `-1/0/1`），用 `equals` 会因 scale 不同而误判。区间校验（如"金额在 [min, max]"）也用 `compareTo`：`v.compareTo(min) >= 0 && v.compareTo(max) <= 0`。

### 运算：不可变，四则都返回新对象

> Illustrative fragment

```java
BigDecimal price = new BigDecimal("19.99");
BigDecimal qty = new BigDecimal("3");
BigDecimal total = price.multiply(qty);           // 59.97

BigDecimal share = total.divide(new BigDecimal("7"), 2, RoundingMode.HALF_UP);
// 59.97 / 7 = 8.567... → 保留 2 位、四舍五入 → 8.57

total.add(BigDecimal.ONE);                         // 返回新对象，total 本身不变
total = total.setScale(2, RoundingMode.HALF_UP);   // setScale 同样返回新对象
```

`BigDecimal` 是**不可变**的：`add/subtract/multiply/divide/setScale` 都不修改自身，而是返回新对象——**忘记接收返回值**是常见错误。常用常量 `BigDecimal.ZERO`/`ONE`/`TEN` 可避免重复构造。

### divide 必须指定 scale 与 RoundingMode

> Illustrative fragment

```java
new BigDecimal("1").divide(new BigDecimal("3"));
// 抛 ArithmeticException: Non-terminating decimal expansion（除不尽）

new BigDecimal("1").divide(new BigDecimal("3"), 4, RoundingMode.HALF_UP);
// 0.3333 —— 明确精度与舍入方式
```

除不尽时（如 1/3），不带 scale 的 `divide` 会抛 `ArithmeticException`。**永远显式给出 scale 与 `RoundingMode`**（`HALF_UP` 四舍五入、`HALF_EVEN` 银行家舍入、`DOWN` 截断等）。

### 分层：计算精度、比例、展示格式各司其职

- **金额**：定点，通常 `scale = 2`、`RoundingMode.HALF_UP`（或按币种/财务规则用 `HALF_EVEN`）。
- **比例/中间量**：用更高 scale（如 6~10 位）参与计算，**最后一步再舍入**到目标精度，避免误差累积。
- **展示格式**：千分位、货币符号、百分号属于**表现层**，用 `DecimalFormat` / `String.format` 处理，不要把格式化后的字符串再拿去参与计算。

> Illustrative fragment

```java
BigDecimal ratio = new BigDecimal("0.123456");
ratio.setScale(2, RoundingMode.HALF_UP).toPlainString();        // "0.12"（计算/存储用）
String.format("%.2f%%", ratio.multiply(new BigDecimal("100"))); // "12.35%"（展示用）
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

## 泛型、通配符与类型擦除

集合（其用法详见 [`collections-framework.md`](collections-framework.md)）、`Optional`、`Stream` 乃至本项目的 MCP 工具返回值（`List<Product>`）都建立在泛型之上。理解泛型的关键是：**它是编译期的类型契约，运行期会被"擦除"**。

### 泛型为何能免除运行期强制转换

没有泛型时，集合只能存 `Object`，取出时必须手动强转，错误被推迟到运行期：

> Illustrative fragment

```java
// 原始类型：编译期不检查，取出即强转，错误延迟到运行期
List rawList = new ArrayList();
rawList.add("hello");
rawList.add(123);                       // 编译通过——什么都能塞
String s = (String) rawList.get(1);     // 运行期抛 ClassCastException

// 泛型：把强转前移到编译期，类型错误当场暴露
List<String> typed = new ArrayList<>();
typed.add("hello");
// typed.add(123);                      // 编译错误：只能放 String
String s2 = typed.get(0);               // 无需强转，编译器已保证类型
```

泛型的价值：① 编译期类型检查，把 `ClassCastException` 消灭在编码阶段；② 取出时**无需显式强转**，代码更干净。

### 通配符：?、? extends、? super

通配符用来表达"我不关心/我需要限定某个具体类型"。三者的**读写能力**不同，这是最容易混淆处：

> Illustrative fragment

```java
// List<?>：元素类型未知。只能读为 Object、取 size/isEmpty，不能 add（除 null）
void printAll(List<?> list) {
    for (Object o : list) System.out.println(o);   // ✅ 读作 Object
    // list.add("x");                               // ❌ 类型未知，无法保证安全
    list.add(null);                                 // ✅ 唯一允许写入的值
}

// List<? extends Number>：某未知子类型（Integer/Double/...）。生产者——只读
double sum(List<? extends Number> src) {
    double total = 0;
    for (Number n : src) total += n.doubleValue();  // ✅ 读作 Number
    // src.add(1);                                  // ❌ 不知具体是 List<Integer> 还是 List<Double>
    return total;
}

// List<? super Integer>：某未知父类型（Integer/Number/Object）。消费者——可写入 Integer
void fill(List<? super Integer> dest) {
    dest.add(1);                                    // ✅ 写入 Integer 安全
    dest.add(2);
    // Integer x = dest.get(0);                     // ❌ 读出只能是 Object
}
```

### PECS：Producer Extends, Consumer Super

选择通配符的口诀——**看这个集合在方法里是"产出数据"还是"接收数据"**：

```
┌──────────────────────────────────────────────────────────┐
│  PECS：Producer Extends, Consumer Super                    │
├──────────────────────────────────────────────────────────┤
│  只从集合【读】数据（它是生产者）  → 用 <? extends T>          │
│  只往集合【写】数据（它是消费者）  → 用 <? super T>            │
│  既要读又要写                     → 用精确类型 <T>，不用通配符 │
└──────────────────────────────────────────────────────────┘
```

JDK 自身就是范例——`Collections.copy(List<? super T> dest, List<? extends T> src)`：目标是消费者（super），源是生产者（extends）。

### 原始类型的风险

`List`、`Map`（不带类型参数）叫**原始类型（raw type）**。它们绕过编译期泛型检查，是泛型出现前的遗留写法：

> Illustrative fragment

```java
Map config = new HashMap();          // 原始类型：编译器放弃类型检查
config.put("timeout", "30");
config.put(1, true);                 // 键类型都不统一，编译也通过
Integer v = (Integer) config.get("timeout");  // 运行期 ClassCastException

Map<String, Object> typed = new HashMap<>();  // ✅ 始终带类型参数
```

**规则**：新代码一律使用参数化类型。看到原始类型通常意味着类型安全被放弃、强转风险被埋进运行期。

### 类型擦除及其后果

Java 泛型是**编译期**特性：编译后类型参数被"擦除"——无界类型参数替换为 `Object`，有界的替换为其上界。因此运行期**看不到** `List<String>` 与 `List<Integer>` 的区别，它们都只是 `List`：

> Illustrative fragment

```java
List<String> a = new ArrayList<>();
List<Integer> b = new ArrayList<>();
System.out.println(a.getClass() == b.getClass());   // true —— 运行期同为 ArrayList

// 擦除带来的限制（下列写法均不合法）：
// if (obj instanceof List<String>) { }        // ❌ 不能对参数化类型做 instanceof
// if (obj instanceof List<?>) { }             // ✅ 只能判断到通配/原始层面
// List<String>[] arr = new List<String>[10];  // ❌ 不能创建泛型数组
// T instance = new T();                        // ❌ 不能实例化类型参数
// 静态成员也不能使用类的类型参数（静态上下文与实例的类型参数无关）
```

擦除是为了兼容 Java 5 之前的老代码（向后兼容的代价）。它解释了"为什么泛型只在编译期保护你"——也正因为运行期类型信息缺失，才需要后文 [动态值的安全转换](#动态值的安全转换object-与-json) 的纪律。

### 进阶：擦除后如何用反射找回类型

擦除并非"信息全丢"。**通过继承保留的泛型实参**可在运行期经反射读回：子类的父类签名（`getGenericSuperclass()`）会保留为 `ParameterizedType`，其中含真实类型参数。

> Illustrative fragment

```java
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

abstract class TypeHolder<T> {
    private final Type type;
    TypeHolder() {
        // 匿名子类"钉住"了泛型实参，getGenericSuperclass() 返回 TypeHolder<String>
        Type superClass = getClass().getGenericSuperclass();
        this.type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
    }
    Type get() { return type; }
}

Type t = new TypeHolder<String>() {}.get();   // 结尾 {} 创建匿名子类
System.out.println(t);                        // class java.lang.String
```

原理：`new TypeHolder<String>() {}` 创建的是**匿名子类**，而"子类 extends 具体化的父类"这一签名会被完整记入 class 文件，故可反射取回。这正是 fastjson/Jackson 的 `TypeReference` 反序列化泛型集合（如 `List<Product>`）的底层机制——**具体用法见 [`fastjson-guide.md`](fastjson-guide.md) 的 TypeReference 一节**，本文不展开。

### Diamond Operator（Java 7+）

泛型实例化时，右侧的类型参数可由编译器从左侧推断，用空尖括号 `<>` 省略：

> Illustrative fragment

```java
// 完整写法
Map<String, List<Integer>> map = new HashMap<String, List<Integer>>();

// 菱形语法（Java 7+）：右侧类型参数省略
Map<String, List<Integer>> map2 = new HashMap<>();

// 结合 var（Java 10+）：类型由初始化表达式推断
var map3 = new HashMap<String, List<Integer>>();   // 推断为 HashMap<String, List<Integer>>
```

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

## 动态值的安全转换：Object 与 JSON

上一节的擦除说明：运行期类型信息可能缺失。当数据来自 **JSON 反序列化、`Map<String, Object>`、反射、MCP 工具的动态参数**时，你拿到的往往是 `Object`——它的真实类型只有运行期才知道。这一节的纪律是：**先探测类型，再转换；收窄数值时防溢出；绝不盲目强转。**

### Object 的来源与类型分发

一个 JSON 值可能是字符串、数字、布尔、`null`、数组（`List`）或对象（`Map`/`JSONObject`）。反序列化到 `Object` 或 `Map<String, Object>` 后，必须按类型分发处理：

> Illustrative fragment

```java
// Java 17 的 pattern matching for instanceof：判断与转换一步完成
Object value = parseJsonField();          // 来源未知

if (value == null) {
    // Java null（键不存在）——按需处理
} else if (value instanceof String s) {
    String str = s;                       // s 已是 String，无需再强转
} else if (value instanceof Number n) {
    BigDecimal num = toBigDecimal(n);     // 见下小节
} else if (value instanceof Boolean b) {
    boolean flag = b;
} else if (value instanceof List<?> list) {
    int size = list.size();               // 元素类型未知，逐个再探测
} else if (value instanceof Map<?, ?> map) {
    Object v = map.get("key");
}
```

`instanceof` 模式匹配（Java 16+ 正式特性）省去了"判断后再 `(String) value` 强转"的重复，也杜绝了忘记判断就直接转换的 `ClassCastException`。

### Number 的精度与溢出边界

JSON 里的数字反序列化后可能是 `Integer`、`Long`、`Double` 或 `BigDecimal`（取决于库和数值大小）。**不要假设它是某一具体类型**，用 `Number` 接收后再按需转换，并警惕收窄丢失：

> Illustrative fragment

```java
Number n = (Number) value;         // 在已确定是 Number 的前提下

// ❌ 静默截断：大数或小数被悄悄改错
int bad = n.intValue();            // 3000000000L → 变成负数；3.99 → 3

// ✅ 需要 int 时，用 toIntExact 让溢出暴露
int good = Math.toIntExact(n.longValue());     // 超 int 范围抛 ArithmeticException

// ✅ 需要精确十进制（金额）时，走 BigDecimal
BigDecimal dec = (n instanceof BigDecimal bd)
        ? bd
        : new BigDecimal(n.toString());        // 用 toString 避免 double 二进制误差
```

`new BigDecimal(n.toString())` 而非 `new BigDecimal(n.doubleValue())`——后者会把 double 的二进制误差带进来（见 [BigDecimal：精确十进制计算](#bigdecimal精确十进制计算)）。

### null、JSON null、空字符串、"null" 字符串

四者语义不同，判空顺序错了就会 NPE 或把"空"当成"有值"：

| 形态            | 含义                                                | 判断方式                            |
| --------------- | --------------------------------------------------- | ----------------------------------- |
| Java `null`     | 键不存在 / 未赋值                                   | `value == null`                     |
| JSON `null`     | 键存在但值为 null（反序列化后通常也是 Java `null`） | `value == null`                     |
| 空字符串 `""`   | 有值，但内容为空                                    | `"".equals(value)` 或 `s.isEmpty()` |
| 字符串 `"null"` | 上游误传的字面量                                    | `"null".equals(value)`              |

> Illustrative fragment

```java
// 字符串数字 vs 空串 vs null 的安全解析（MCP 可选参数的典型形态）
Integer parseLimit(Object raw) {
    if (raw == null) return null;                     // 缺失 → 交由调用方给默认值
    if (raw instanceof Number n) return n.intValue();  // 已是数字
    if (raw instanceof String s) {
        if (s.isBlank()) return null;                 // "" 或空白 → 视为未提供
        try {
            return Integer.valueOf(s.trim());         // "10" → 10
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("limit 必须是整数：" + s, e);
        }
    }
    throw new IllegalArgumentException("limit 类型不支持：" + raw.getClass());
}
```

这正是 MCP 工具处理可选参数的典型形态——`@McpToolParam(required = false) Integer limit` 可能不传、传 null、或传字符串数字，解析后再做范围校验（如 1~50）。**任何情况下都不要 `(Integer) raw` 直接强转**。

### 禁止未经检查的强制转换

> Illustrative fragment

```java
// ❌ 危险：obj 不是 List<String> 时运行期才崩，且泛型参数无法校验
List<String> list = (List<String>) obj;

// ✅ 先探测结构，再逐元素转换
if (obj instanceof List<?> raw) {
    List<String> safe = new ArrayList<>();
    for (Object o : raw) {
        if (o instanceof String s) safe.add(s);
        else if (o != null) safe.add(o.toString());   // 或按业务规则拒绝
    }
}
```

JSON 库（fastjson 的 `JSONObject.getXxx`/`TypeReference`、Jackson 的 `JsonNode`/`ObjectMapper.convertValue`）已封装了大量安全取值逻辑，**优先用库的类型化 API**；本文只讲"当值以 `Object` 出现时的转换纪律"。库 API 详见 [`fastjson-guide.md`](fastjson-guide.md) 与 [`jackson-guide.md`](jackson-guide.md)。

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

> Diamond Operator（`<>`）与泛型实例化的类型推断已并入 [泛型、通配符与类型擦除](#泛型通配符与类型擦除)。

## 常量、不可变性与共享状态的边界

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
    private final BigDecimal amount;   // 金额用 BigDecimal，不用 double

    public Money(BigDecimal amount) {
        // BigDecimal 不可变，可安全持有；若字段是可变对象则需在此防御性拷贝
        this.amount = amount;
    }

    public BigDecimal getAmount() {
        return amount;  // BigDecimal 本身不可变，直接返回即安全
    }
}
```

Java 14+ 提供了更简洁的 `record`（不在本指南范围），但可将其视为不可变数据载体的推荐方案。

### final 只固定引用，不等于对象不可变

上文 [final 变量](#final-变量) 已说明：`final` 修饰引用类型时锁死的是"引用不能再指向别的对象"，而非"对象内部状态不能改"。最常踩的形态是 `final` 集合字段：

> Illustrative fragment

```java
final List<String> list = new ArrayList<>();
list.add("a");              // ✅ 合法：改的是对象内容，不是引用
// list = new ArrayList<>();// ❌ 编译错误：不能让 list 指向新对象
```

要真正不可变，需配合上节"不可变对象的构建"四规则。而当这样的字段被**多线程共享**时，还会引出可见性与原子性两个新维度。

### 共享可变状态的边界：volatile 与原子类

当变量被多线程共享（实例字段、静态字段），"类型正确"不再等于"行为正确"。这里只讲与"变量"直接相关的边界结论，并发机制的完整讲解见 [`multithreading-basics.md`](multithreading-basics.md)。

**`volatile`：只保证可见性与有序性，不保证原子性。** 一个线程写入 `volatile` 变量，其他线程能立刻看到；但 `count++` 这种"读—改—写"复合操作**不是原子的**，多线程下仍会丢更新。

> Illustrative fragment

```java
private volatile boolean running = true;   // ✅ 状态标志：一写多读，volatile 足够
private volatile int count = 0;            // ⚠️ 用它做 count++ 是错的

void increment() { count++; }              // ❌ 读-改-写非原子，多线程丢更新（volatile 也救不了）
```

**复合更新用原子类或锁。** 计数、累加、CAS 式更新，用 `AtomicInteger`/`AtomicLong`/`AtomicReference`（无锁）或 `synchronized`/`Lock`：

> Illustrative fragment

```java
import java.util.concurrent.atomic.AtomicInteger;

private final AtomicInteger count = new AtomicInteger(0);
count.incrementAndGet();                   // ✅ 原子的读-改-写
```

> `final` + 原子类是常见组合：引用 `final`（不换对象），内部值用原子类安全更新。做缓存快照时，也常用 `volatile` 修饰引用、整体替换为新的不可变对象，而非原地修改字段。

### ThreadLocal：线程封闭的变量，必须 remove()

`ThreadLocal<T>` 提供**线程封闭（thread confinement）**的变量：每个线程持有自己独立的一份副本，天然线程安全、无需同步，常用于传递请求上下文（traceId、当前用户、事务/数据源路由）。本项目的 `TraceIdFilter` 就用 SLF4J `MDC`（其底层正是 `ThreadLocal`）存放 traceId。

> Illustrative fragment

```java
private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

try {
    TRACE_ID.set(uuid());          // 当前线程写入
    handle();                      // 同线程内任意调用深度都能读到
} finally {
    TRACE_ID.remove();             // ✅ 必须清理
}
```

**为什么必须 `remove()`**：在**线程池**中线程会被复用。若不清理，上一个请求残留的值会"串"给下一个请求（数据串味），且 `ThreadLocal` 引用的对象无法及时回收（内存泄漏）。因此**凡 `set` 必有 `finally { remove(); }`**——`TraceIdFilter` 正是在 `finally` 中 `MDC.clear()`，其注释直言"防止线程回池后串数据"。

**注意环境边界**：在 **WebFlux（响应式）** 或**虚拟线程**等模型下，一段逻辑可能跨多个线程执行，`ThreadLocal` 不再可靠——应改用响应式上下文（Reactor `Context`）或显式传参。

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
- [JLS §4.5 Parameterized Types / §4.6 Type Erasure](https://docs.oracle.com/javase/specs/jls/se8/html/jls-4.html) — 泛型、通配符与类型擦除的语言规范依据
- [Java Platform SE 17 API — `java.math.BigDecimal`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html) — 构造、`compareTo`、`divide(scale, RoundingMode)`、`setScale`
- [Java Platform SE 17 API — `java.lang.ThreadLocal`](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ThreadLocal.html) — 线程封闭语义与内存泄漏说明
- [JEP 394: Pattern Matching for `instanceof`](https://openjdk.org/jeps/394) — Java 16+ 正式特性（`if (o instanceof String s)`）
- [ECMAScript `Number.MAX_SAFE_INTEGER` (MDN)](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/MAX_SAFE_INTEGER) — \(2^{53}-1\)，JSON 大整数的精度边界
- [IEEE 754-2019 Standard for Floating-Point Arithmetic](https://standards.ieee.org/ieee/754/6210/)
- [JEP 286: Local-Variable Type Inference](https://openjdk.org/jeps/286) — `var` (Java 10)

---

## Verification summary

- **Structure**: `validate_guide.py` PASS（H2 sections: 15，TOC links: 14）；TOC 与正文全部 `#` 锚点均解析通过
- **Code**: 所有代码块为 Illustrative fragment，未声明为可运行完整示例；语法和符号经过人工审查
- **Sources**:
  - Java Language Specification (JLS) SE 8 — 类型系统、转换规则、命名约定
  - JLS §4.5/§4.6 — 参数化类型与类型擦除
  - Java Platform SE 17 API — `java.math.BigDecimal`、`java.lang.ThreadLocal`
  - JEP 394 — pattern matching for `instanceof`（Java 16+）
  - JEP 286 — `var` 类型推断
  - ECMAScript `Number.MAX_SAFE_INTEGER` — JSON 大整数精度边界
  - IEEE 754 — 浮点行为
- **Unverified**:
  - 代码示例未在 Java 编译器下实际编译运行（标记为 Illustrative fragment，不声称可运行）
  - `Integer.valueOf()` 缓存范围为 \(-128\) 到 \(127\) 是标准行为，但未在当前环境实际验证属性配置
