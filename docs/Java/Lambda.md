## 1. Lambda 表达式基础

### JavaScript 箭头函数对比

```javascript
// JavaScript 箭头函数
const add = (a, b) => a + b;
const greet = (name) => `Hello ${name}`;
const log = () => console.log("Hi");

// 数组方法
products.filter((p) => p.price > 100);
products.map((p) => p.name);
products.forEach((p) => console.log(p));
```

### Java Lambda 表达式

```java
// Lambda 语法：(参数) -> 表达式/代码块

// 1. 无参数
Runnable task = () -> System.out.println("Hi");

// 2. 单个参数（可省略括号）
Consumer<String> greet = name -> System.out.println("Hello " + name);
Consumer<String> greet2 = (name) -> System.out.println("Hello " + name);

// 3. 多个参数
Comparator<Integer> comparator = (a, b) -> a - b;

// 4. 多行代码块
Comparator<String> comparator2 = (s1, s2) -> {
    System.out.println("Comparing...");
    return s1.length() - s2.length();
};

// 使用
task.run();
greet.accept("John");
```

### 函数式接口

Lambda 只能用于**函数式接口**（只有一个抽象方法的接口）：

```java
// 自定义函数式接口
@FunctionalInterface
public interface Calculator {
    int calculate(int a, int b);
}

// 使用 Lambda
Calculator add = (a, b) -> a + b;
Calculator multiply = (a, b) -> a * b;

System.out.println(add.calculate(2, 3));       // 5
System.out.println(multiply.calculate(2, 3));  // 6
```

### 常用内置函数式接口

| 接口                  | 方法                | 说明            | 示例                 |
| --------------------- | ------------------- | --------------- | -------------------- |
| `Function<T, R>`      | `R apply(T t)`      | 输入T，返回R    | `s -> s.length()`    |
| `Predicate<T>`        | `boolean test(T t)` | 输入T，返回布尔 | `n -> n > 0`         |
| `Consumer<T>`         | `void accept(T t)`  | 输入T，无返回   | `s -> print(s)`      |
| `Supplier<T>`         | `T get()`           | 无输入，返回T   | `() -> new Object()` |
| `BiFunction<T, U, R>` | `R apply(T t, U u)` | 输入T和U，返回R | `(a, b) -> a + b`    |

```java
// Function：转换
Function<String, Integer> toLength = s -> s.length();
Integer len = toLength.apply("Hello");  // 5

// Predicate：判断
Predicate<Integer> isPositive = n -> n > 0;
boolean result = isPositive.test(10);  // true

// Consumer：消费
Consumer<String> printer = s -> System.out.println(s);
printer.accept("Hello");  // 输出 Hello

// Supplier：提供
Supplier<String> supplier = () -> "Default";
String value = supplier.get();  // "Default"
```

---

## 2. 方法引用（::）

当 Lambda 的方法体只是"把参数原样转交给一个现成的方法"时，可以用方法引用再压缩一层。它和 Lambda 一样是**目标类型化表达式**——必须赋给函数式接口才有形状，只是实现体直接指向一个已有的方法。

### 先看什么是"纯转交"

```java
List<String> names = Arrays.asList("Alice", "Bob", "Charlie");

names.forEach(name -> System.out.println(name));
```

这个 Lambda 什么额外的事都没做：拿到 `name`，原封不动交给 `System.out.println`。这种 `x -> 某方法(x)` 的形状叫**纯转交**，见到它可以写成：

```java
names.forEach(System.out::println);   // 与上一行完全等价
```

`::` 可以读作"用那个方法当实现"——左边是方法的家（类或对象），右边是方法名。

**参数也一并省略了**——这是方法引用比 Lambda 再省一层的地方：

```java
names.forEach(name -> System.out.println(name));  // name 出现两次：左边声明，右边转交
names.forEach(System.out::println);               // name 一个字都没有
```

Lambda 写法里 `name` 要写两处：箭头左边**声明**它、箭头右边**交给** `println`。方法引用把两处全省了——方法体就是原样转交，参数怎么走没有歧义，编译器自己能推出来。但省略的只是**你写的字**，不是参数本身：`forEach` 每取出一个元素，`println` 就自动收到它，运行时行为完全一样。

### 四种形式（按"方法从哪来"分）

```java
// 1. 静态方法引用：类名::静态方法
Function<String, Integer> parser1 = s -> Integer.parseInt(s);
Function<String, Integer> parser2 = Integer::parseInt;          // 简化

// 2. 实例方法引用·绑定对象（示例中为 product）：对象::实例方法
Product product = new Product();
Supplier<String> getName1 = () -> product.getName();
Supplier<String> getName2 = product::getName;                   // 简化

// 3. 实例方法引用·未绑定对象（示例为String，调用时由第一个参数充当）：类名::实例方法（见下面专门讲解）
Function<String, Integer> getLength1 = s -> s.length();
Function<String, Integer> getLength2 = String::length;          // 简化

// 4. 构造方法引用：类名::new（把"调构造器"也当作一种方法）
Supplier<Product> creator1 = () -> new Product();
Supplier<Product> creator2 = Product::new;                      // 简化

Function<String, Product> creator3 = name -> new Product(name);
Function<String, Product> creator4 = Product::new;              // 简化
```

### 什么时候不能用方法引用

判断标准只有一条：Lambda 的方法体是不是纯转交——拿到参数、原样调用、**一个字都不加**。只要加了任何额外逻辑，就必须老老实实写 Lambda：

```java
// ✗ 不能缩写：方法体里有字符串拼接（不是原样转交）
name -> System.out.println("Hi " + name)

// ✗ 不能缩写：返回值上还做了比较（不是原样转交）
s -> s.length() > 10

// ✓ 纯转交，可缩写为 String::isEmpty
s -> s.isEmpty()
```

### 四种形式速查表

| 形式            | 写法                  | 等价 Lambda                  | 典型场景        |
| --------------- | --------------------- | ---------------------------- | --------------- |
| 静态方法        | `Integer::parseInt`   | `s -> Integer.parseInt(s)`   | `map` 转换      |
| 实例方法·绑定   | `System.out::println` | `s -> System.out.println(s)` | `forEach` 输出  |
| 实例方法·未绑定 | `String::length`      | `s -> s.length()`            | `map`、`sorted` |
| 构造方法        | `Product::new`        | `name -> new Product(name)`  | 批量创建对象    |
| 数组构造        | `String[]::new`       | `len -> new String[len]`     | `toArray`       |

### 实际应用

```java
List<String> names = Arrays.asList("Alice", "Bob", "Charlie");

// 输出（绑定实例方法）
names.forEach(System.out::println);

// 转换（未绑定实例方法）
List<Integer> lengths = names.stream()
    .map(String::length)                     // 等价 map(name -> name.length())
    .collect(Collectors.toList());

// 排序（未绑定实例方法）
names.sort(Comparator.comparing(String::length));

// 批量创建对象（构造方法引用）
List<Product> products = names.stream()
    .map(Product::new)                       // 等价 map(name -> new Product(name))
    .collect(Collectors.toList());

// 收集成数组（数组构造引用：告诉 toArray 怎么建数组）
String[] arr = names.toArray(String[]::new);
```

---
