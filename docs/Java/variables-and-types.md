# 01-01 变量与类型系统

> 理解Java的强类型系统，掌握基本类型与引用类型的区别

## 1. 变量声明对比

### JavaScript/TypeScript
```javascript
// JavaScript - 动态类型
let count = 10;
count = "ten";  // 可以改变类型

// TypeScript - 静态类型
let count: number = 10;
count = "ten";  // 编译错误

const MAX = 100;  // 不可重新赋值
```

### Java
```java
// Java - 强类型，编译时检查
int count = 10;
count = "ten";  // 编译错误

final int MAX = 100;  // final ≈ const
MAX = 200;  // 编译错误
```

**关键差异**：
- Java 没有 `let/const/var` 关键字
- 类型必须显式声明且不能改变
- `final` 表示不可重新赋值

---

## 2. 基本数据类型（8种）

### 整数类型

| 类型 | 字节数 | 范围 | JS对应 | 示例 |
|------|--------|------|--------|------|
| `byte` | 1 | -128 ~ 127 | - | `byte age = 25;` |
| `short` | 2 | -32,768 ~ 32,767 | - | `short year = 2024;` |
| `int` | 4 | -2^31 ~ 2^31-1 | `number` | `int count = 1000;` |
| `long` | 8 | -2^63 ~ 2^63-1 | `bigint` | `long id = 100000L;` |

```java
// 整数字面量
int decimal = 100;        // 十进制
int hex = 0x64;          // 十六进制
int binary = 0b1100100;  // 二进制（Java 7+）
int readable = 1_000_000; // 下划线分隔（Java 7+）

// long 必须加 L 后缀
long bigNumber = 9999999999L;  // 不加L会编译错误
```

### 浮点类型

| 类型 | 字节数 | 精度 | JS对应 | 示例 |
|------|--------|------|--------|------|
| `float` | 4 | 6-7位 | `number` | `float price = 99.9f;` |
| `double` | 8 | 15位 | `number` | `double pi = 3.14159;` |

```java
float price = 99.9f;   // 必须加 f 后缀
double amount = 123.45; // 默认是 double

// ⚠️ 金额不要用 float/double（精度问题）
double result = 0.1 + 0.2;  // 0.30000000000000004

// ✅ 金额用 BigDecimal
BigDecimal price1 = new BigDecimal("0.1");
BigDecimal price2 = new BigDecimal("0.2");
BigDecimal sum = price1.add(price2);  // 0.3
```

### 字符与布尔

| 类型 | 字节数 | 说明 | JS对应 | 示例 |
|------|--------|------|--------|------|
| `char` | 2 | 单个字符（Unicode） | - | `char grade = 'A';` |
| `boolean` | - | true/false | `boolean` | `boolean isPublished = true;` |

```java
char letter = 'A';        // 单引号
char unicode = '\u0041';  // Unicode（也是'A'）
String text = "Hello";    // 双引号是字符串

boolean flag = true;      // 只能是 true 或 false（全小写）
```

---

## 3. 包装类型（引用类型）

每个基本类型都有对应的**包装类**：

| 基本类型 | 包装类 | 说明 |
|---------|--------|------|
| `byte` | `Byte` | |
| `short` | `Short` | |
| `int` | `Integer` | ⭐ 最常用 |
| `long` | `Long` | ⭐ 常用于ID |
| `float` | `Float` | |
| `double` | `Double` | |
| `char` | `Character` | |
| `boolean` | `Boolean` | |

### 为什么需要包装类？

```java
// 1. 集合只能存引用类型
List<int> ids = new ArrayList<>();        // ❌ 编译错误
List<Integer> ids = new ArrayList<>();    // ✅ 正确

// 2. 包装类可以为 null
int count = null;      // ❌ 编译错误
Integer stock = null;  // ✅ 可以表示"未设置"

// 3. 包装类有工具方法
Integer.parseInt("123");        // 字符串转整数
Integer.toString(123);          // 整数转字符串
Integer.MAX_VALUE;              // 最大值常量
```

### 自动装箱与拆箱

```java
// 装箱：基本类型 → 包装类型
int num = 10;
Integer obj = num;  // 自动装箱（Java 5+）
// 等价于：Integer obj = Integer.valueOf(num);

// 拆箱：包装类型 → 基本类型
Integer obj2 = 20;
int num2 = obj2;    // 自动拆箱
// 等价于：int num2 = obj2.intValue();

// ⚠️ 空指针风险
Integer obj3 = null;
int num3 = obj3;    // NullPointerException！
```

### 包装类缓存机制

```java
// Integer 缓存 -128 ~ 127
Integer a = 100;
Integer b = 100;
System.out.println(a == b);  // true（同一对象）

Integer c = 200;
Integer d = 200;
System.out.println(c == d);  // false（不同对象）

// ✅ 比较值用 equals
System.out.println(c.equals(d));  // true
```

---

## 4. 引用类型

### String（字符串）

```java
// String 是引用类型但用法特殊
String name = "iPhone";  // 字面量
String name2 = new String("iPhone");  // new 对象

// 字符串不可变
String str = "Hello";
str = str + " World";  // 创建新对象，原对象不变

// 字符串比较
String s1 = "Hello";
String s2 = "Hello";
s1 == s2;          // true（字符串池）
s1.equals(s2);     // true（值相等）

String s3 = new String("Hello");
s1 == s3;          // false（不同对象）
s1.equals(s3);     // true（值相等）
```

### 数组

```java
// 声明与初始化
int[] arr1 = {1, 2, 3};              // 字面量
int[] arr2 = new int[5];             // 指定长度
String[] names = new String[]{"A", "B"};

// 访问
arr1[0];           // 索引访问
arr1.length;       // 长度（属性，不是方法）

// ⚠️ 数组长度固定，不能扩容
arr1[3] = 4;       // ArrayIndexOutOfBoundsException
```

---

## 5. 类型转换

### 自动类型转换（小 → 大）

```java
// 范围小的类型自动转为范围大的类型
byte → short → int → long → float → double

byte b = 10;
int i = b;      // 自动转换
long l = i;     // 自动转换
double d = l;   // 自动转换
```

### 强制类型转换（大 → 小）

```java
// 可能丢失精度
double d = 9.8;
int i = (int) d;  // 强制转换，i = 9（截断小数）

long l = 100L;
int i2 = (int) l;  // 可能溢出

// ⚠️ 超出范围会溢出
long big = 3000000000L;
int small = (int) big;  // 溢出
```

### 字符串转换

```java
// 字符串 → 数字
String str = "123";
int num = Integer.parseInt(str);
long id = Long.parseLong("1001");
double price = Double.parseDouble("99.9");

// 数字 → 字符串
String s1 = String.valueOf(123);
String s2 = Integer.toString(123);
String s3 = "" + 123;  // 拼接转换（不推荐）
```

---

## 6. final 关键字

```java
// 1. final 变量（不可重新赋值）
final int MAX = 100;
MAX = 200;  // 编译错误

// 2. final 引用（引用不可变，对象内容可变）
final List<String> list = new ArrayList<>();
list.add("A");     // ✅ 可以修改内容
list = new ArrayList<>();  // ❌ 不能重新赋值

// 3. final 方法（不可被重写）
public final void log() { }

// 4. final 类（不可被继承）
public final class Constants { }
```

---

## 7. static 关键字

```java
public class Product {
    // 实例变量（每个对象独立）
    private String name;
    
    // 类变量（所有对象共享）
    private static int count = 0;
    
    // 静态方法（通过类名调用）
    public static int getCount() {
        return count;
    }
}

// 使用
Product.getCount();  // 类名调用
Math.max(1, 2);      // Math 的方法都是静态的
```

---

## 8. var 关键字（Java 10+）

```java
// 局部变量类型推断
var name = "iPhone";        // 推断为 String
var count = 10;             // 推断为 int
var list = new ArrayList<String>();  // 推断为 ArrayList<String>

// ⚠️ 限制
var x;               // ❌ 必须初始化
var y = null;        // ❌ 不能推断为 null
var z = (x) -> x;    // ❌ 不能推断 Lambda
```

---

## 9. 作用域与生命周期

```java
public class Demo {
    // 1. 成员变量（实例变量）- 对象生命周期
    private int instanceVar;
    
    // 2. 类变量（静态变量）- 类加载到卸载
    private static int classVar;
    
    public void method(int param) {  // 3. 参数 - 方法调用期间
        // 4. 局部变量 - 代码块内
        int localVar = 10;
        
        if (true) {
            int blockVar = 20;  // 5. 代码块变量
            System.out.println(blockVar);
        }
        // System.out.println(blockVar);  // ❌ 编译错误
    }
}
```

---

## 10. 实战对比

### TypeScript
```typescript
// 商品价格计算
function calculateTotal(price: number, quantity: number): number {
    return price * quantity;
}

const total = calculateTotal(99.9, 2);
```

### Java
```java
// 商品价格计算（精确）
public BigDecimal calculateTotal(BigDecimal price, Integer quantity) {
    return price.multiply(new BigDecimal(quantity));
}

BigDecimal price = new BigDecimal("99.9");
Integer quantity = 2;
BigDecimal total = calculateTotal(price, quantity);
```

---

## 11. 常见陷阱

### 陷阱1：包装类比较

```java
// ❌ 错误
Integer a = 200;
Integer b = 200;
if (a == b) { }  // false！

// ✅ 正确
if (a.equals(b)) { }  // true
```

### 陷阱2：null 拆箱

```java
// ❌ 危险
Integer stock = getStock();  // 可能返回 null
int count = stock;  // NullPointerException

// ✅ 安全
Integer stock = getStock();
int count = (stock != null) ? stock : 0;
// 或使用 Optional
int count = Optional.ofNullable(stock).orElse(0);
```

### 陷阱3：浮点精度

```java
// ❌ 金额计算
double price = 0.1;
double tax = 0.2;
double total = price + tax;  // 0.30000000000000004

// ✅ 使用 BigDecimal
BigDecimal price = new BigDecimal("0.1");
BigDecimal tax = new BigDecimal("0.2");
BigDecimal total = price.add(tax);  // 0.3
```

---

## 下一步

- **[01-02-面向对象编程.md](./01-02-面向对象编程.md)** - 类、继承、多态
- **[01-03-集合框架详解.md](./01-03-集合框架详解.md)** - List、Set、Map

---

## 快速参考

```java
// 常用类型声明
int count = 0;                           // 整数
long id = 1001L;                         // 长整型（ID）
double rate = 0.15;                      // 浮点数
BigDecimal price = new BigDecimal("99.9"); // 金额
String name = "Product";                 // 字符串
boolean flag = true;                     // 布尔

// 包装类（可为null）
Integer stock = null;
Long userId = 1001L;

// 类型转换
int num = Integer.parseInt("123");       // 字符串转整数
String str = String.valueOf(123);        // 整数转字符串

// 常量
final int MAX_SIZE = 100;
public static final String API_URL = "http://api.example.com";
```
