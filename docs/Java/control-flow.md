# 01-02 面向对象编程

> 理解Java的面向对象特性：封装、继承、多态

## 1. 类与对象基础

### JavaScript/TypeScript
```typescript
class Product {
    constructor(
        public id: number,
        public name: string,
        private _price: number
    ) {}
    
    get price() {
        return this._price;
    }
    
    getInfo() {
        return `${this.name}: $${this.price}`;
    }
}

const product = new Product(1, "iPhone", 999);
```

### Java
```java
public class Product {
    // 私有属性（封装）
    private Long id;
    private String name;
    private BigDecimal price;
    
    // 构造函数
    public Product() {}  // 无参构造
    
    public Product(Long id, String name, BigDecimal price) {
        this.id = id;
        this.name = name;
        this.price = price;
    }
    
    // Getter/Setter
    public BigDecimal getPrice() {
        return price;
    }
    
    public void setPrice(BigDecimal price) {
        this.price = price;
    }
    
    // 业务方法
    public String getInfo() {
        return name + ": $" + price;
    }
}

// 使用
Product product = new Product(1L, "iPhone", new BigDecimal("999"));
```

---

## 2. Lombok 简化写法（本项目使用）

### 常用注解

```java
import lombok.*;

// @Data = @Getter + @Setter + @ToString + @EqualsAndHashCode + @RequiredArgsConstructor
@Data
public class Product {
    private Long id;
    private String name;
    private BigDecimal price;
}

// 使用
Product p = new Product();
p.setName("iPhone");
String name = p.getName();
```

### Lombok 注解对照表

| 注解 | 功能 | 示例 |
|------|------|------|
| `@Getter` | 生成所有 getter 方法 | `product.getName()` |
| `@Setter` | 生成所有 setter 方法 | `product.setName("iPhone")` |
| `@ToString` | 生成 toString() 方法 | `product.toString()` |
| `@EqualsAndHashCode` | 生成 equals/hashCode | `p1.equals(p2)` |
| `@NoArgsConstructor` | 生成无参构造函数 | `new Product()` |
| `@AllArgsConstructor` | 生成全参构造函数 | `new Product(1L, "iPhone", ...)` |
| `@RequiredArgsConstructor` | 生成 final 字段构造 | 用于依赖注入 |
| `@Data` | 上述注解组合 | 最常用 |
| `@Builder` | 生成建造者模式 | `Product.builder().name("iPhone").build()` |

### @RequiredArgsConstructor（依赖注入推荐）

```java
// 旧写法：字段注入
@Controller
public class ProductController {
    @Autowired
    private ProductService productService;  // ❌ 不推荐
}

// 新写法：构造函数注入
@Controller
@RequiredArgsConstructor  // Lombok 自动生成构造函数
public class ProductController {
    private final ProductService productService;  // ✅ 推荐
}

// 等价于手写：
public class ProductController {
    private final ProductService productService;
    
    public ProductController(ProductService productService) {
        this.productService = productService;
    }
}
```

---

## 3. 访问修饰符

| 修饰符 | 类内部 | 同包 | 子类 | 任何地方 |
|--------|--------|------|------|----------|
| `private` | ✅ | ❌ | ❌ | ❌ |
| 默认（无修饰符） | ✅ | ✅ | ❌ | ❌ |
| `protected` | ✅ | ✅ | ✅ | ❌ |
| `public` | ✅ | ✅ | ✅ | ✅ |

```java
public class Product {
    private Long id;              // 只能类内部访问
    String name;                  // 同包可访问（不推荐）
    protected BigDecimal cost;    // 子类可访问
    public BigDecimal price;      // 任何地方可访问
}
```

**最佳实践**：
- 属性：`private`
- 方法：根据需要选择 `public`/`private`/`protected`
- 类：`public` 或默认

---

## 4. 继承（extends）

### 单继承

```java
// 父类
public class Product {
    private Long id;
    private String name;
    
    public void display() {
        System.out.println("Product: " + name);
    }
}

// 子类
public class Phone extends Product {
    private String brand;  // 子类特有属性
    
    // 重写父类方法
    @Override
    public void display() {
        super.display();  // 调用父类方法
        System.out.println("Brand: " + brand);
    }
    
    // 子类特有方法
    public void call() {
        System.out.println("Calling...");
    }
}

// 使用
Phone phone = new Phone();
phone.setName("iPhone 15");  // 继承自父类
phone.setBrand("Apple");     // 子类属性
phone.display();             // 重写的方法
phone.call();                // 子类方法
```

### 构造函数调用顺序

```java
public class Product {
    public Product() {
        System.out.println("Product 构造");
    }
}

public class Phone extends Product {
    public Phone() {
        super();  // 隐式调用父类构造（可省略）
        System.out.println("Phone 构造");
    }
}

// 输出：
// Product 构造
// Phone 构造
```

---

## 5. 多态（Polymorphism）

### 向上转型

```java
// 父类引用指向子类对象
Product product = new Phone();  // ✅ 向上转型
product.display();  // 调用 Phone 的 display()（动态绑定）
// product.call();  // ❌ 编译错误（Product 没有 call 方法）

// 向下转型（需要强制转换）
if (product instanceof Phone) {
    Phone phone = (Phone) product;  // 强制转换
    phone.call();  // ✅ 现在可以调用了
}

// Java 14+ 模式匹配
if (product instanceof Phone phone) {
    phone.call();  // 自动转换
}
```

### 方法重写（@Override）

```java
public class Product {
    public BigDecimal getPrice() {
        return price;
    }
}

public class DiscountProduct extends Product {
    private BigDecimal discount;
    
    @Override  // 注解表示重写（可检测错误）
    public BigDecimal getPrice() {
        return super.getPrice().multiply(discount);
    }
}
```

**重写规则**：
- 方法名、参数列表必须相同
- 返回值类型相同或是子类型
- 访问权限不能更严格（`public` → `protected` ❌）
- 不能重写 `private`/`static`/`final` 方法

---

## 6. 抽象类（abstract）

```java
// 抽象类（不能实例化）
public abstract class BaseProduct {
    private String name;
    
    // 普通方法（有实现）
    public void setName(String name) {
        this.name = name;
    }
    
    // 抽象方法（无实现，子类必须重写）
    public abstract BigDecimal calculatePrice();
    
    public abstract String getCategory();
}

// 具体类
public class Phone extends BaseProduct {
    @Override
    public BigDecimal calculatePrice() {
        return new BigDecimal("999");
    }
    
    @Override
    public String getCategory() {
        return "Electronics";
    }
}

// 使用
// BaseProduct p = new BaseProduct();  // ❌ 抽象类不能实例化
BaseProduct p = new Phone();  // ✅ 通过子类实例化
```

---

## 7. 接口（interface）

### 基本语法

```java
// 接口定义
public interface Chargeable {
    // 常量（默认 public static final）
    int MAX_VOLTAGE = 220;
    
    // 抽象方法（默认 public abstract）
    void charge();
    
    // 默认方法（Java 8+，有实现）
    default void showStatus() {
        System.out.println("Charging...");
    }
    
    // 静态方法（Java 8+）
    static int getMaxVoltage() {
        return MAX_VOLTAGE;
    }
}

// 实现接口
public class Phone implements Chargeable {
    @Override
    public void charge() {
        System.out.println("Phone charging");
    }
}

// 使用
Chargeable device = new Phone();
device.charge();
device.showStatus();  // 调用默认方法
```

### 多接口实现

```java
public interface Callable {
    void call();
}

public interface Chargeable {
    void charge();
}

// 实现多个接口（用逗号分隔）
public class Phone implements Callable, Chargeable {
    @Override
    public void call() {
        System.out.println("Calling...");
    }
    
    @Override
    public void charge() {
        System.out.println("Charging...");
    }
}
```

---

## 8. 接口 vs 抽象类

| 特性 | 接口（interface） | 抽象类（abstract class） |
|------|------------------|-------------------------|
| 多继承 | ✅ 可实现多个接口 | ❌ 只能继承一个类 |
| 构造函数 | ❌ 没有 | ✅ 有 |
| 字段 | 只能是常量 | 可以有实例变量 |
| 方法 | 抽象方法 + 默认方法 | 抽象方法 + 普通方法 |
| 使用场景 | 定义能力（行为规范） | 提取公共代码 |

```java
// 接口：定义"能力"
public interface Flyable {
    void fly();
}

// 抽象类：提取公共代码
public abstract class Animal {
    private String name;
    
    public void eat() {  // 通用实现
        System.out.println("Eating...");
    }
    
    public abstract void move();  // 子类各自实现
}

// 组合使用
public class Bird extends Animal implements Flyable {
    @Override
    public void move() {
        fly();  // 通过飞行移动
    }
    
    @Override
    public void fly() {
        System.out.println("Bird flying");
    }
}
```

---

## 9. 内部类

### 成员内部类

```java
public class Outer {
    private String name = "Outer";
    
    // 成员内部类
    public class Inner {
        public void display() {
            System.out.println(name);  // 可访问外部类私有成员
        }
    }
}

// 使用
Outer outer = new Outer();
Outer.Inner inner = outer.new Inner();
inner.display();
```

### 静态内部类

```java
public class Outer {
    private static String name = "Outer";
    
    // 静态内部类
    public static class StaticInner {
        public void display() {
            System.out.println(name);  // 只能访问外部类静态成员
        }
    }
}

// 使用
Outer.StaticInner inner = new Outer.StaticInner();
inner.display();
```

### 匿名内部类

```java
// 接口
public interface Comparator<T> {
    int compare(T o1, T o2);
}

// 匿名内部类（旧写法）
Comparator<Integer> comparator = new Comparator<Integer>() {
    @Override
    public int compare(Integer o1, Integer o2) {
        return o1 - o2;
    }
};

// Lambda 表达式（新写法，推荐）
Comparator<Integer> comparator2 = (o1, o2) -> o1 - o2;
```

---

## 10. Object 类常用方法

所有类都继承自 `Object`，以下方法常被重写：

```java
public class Product {
    private Long id;
    private String name;
    
    // 1. toString() - 对象的字符串表示
    @Override
    public String toString() {
        return "Product{id=" + id + ", name=" + name + "}";
    }
    
    // 2. equals() - 判断两个对象是否相等
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Product product = (Product) obj;
        return Objects.equals(id, product.id);
    }
    
    // 3. hashCode() - 对象的哈希码
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

// Lombok 自动生成
@Data  // 包含 toString、equals、hashCode
public class Product {
    private Long id;
    private String name;
}
```

---

## 11. 实战示例：商品类设计

```java
// 抽象基类
@Data
public abstract class BaseProduct {
    private Long id;
    private String name;
    private BigDecimal price;
    private LocalDateTime createTime;
    
    // 抽象方法：子类必须实现
    public abstract String getCategory();
    
    // 通用方法
    public BigDecimal getFinalPrice() {
        return price;
    }
}

// 手机类
@Data
@EqualsAndHashCode(callSuper = true)
public class Phone extends BaseProduct {
    private String brand;
    private String model;
    
    @Override
    public String getCategory() {
        return "Electronics";
    }
}

// 折扣商品
@Data
@EqualsAndHashCode(callSuper = true)
public class DiscountProduct extends BaseProduct {
    private BigDecimal discount;  // 折扣率
    
    @Override
    public String getCategory() {
        return "Discount";
    }
    
    @Override
    public BigDecimal getFinalPrice() {
        return getPrice().multiply(discount);
    }
}

// 使用
BaseProduct phone = new Phone();
phone.setName("iPhone 15");
phone.setPrice(new BigDecimal("999"));

BaseProduct discountProduct = new DiscountProduct();
discountProduct.setPrice(new BigDecimal("100"));
((DiscountProduct) discountProduct).setDiscount(new BigDecimal("0.8"));

System.out.println(phone.getFinalPrice());          // 999
System.out.println(discountProduct.getFinalPrice()); // 80
```

---

## 12. 常见陷阱

### 陷阱1：重写 equals 必须重写 hashCode

```java
@Data
public class Product {
    private Long id;
    
    // ❌ 只重写 equals
    @Override
    public boolean equals(Object obj) {
        // ...
    }
    // hashCode 未重写，HashMap 会出错！
}

// ✅ 使用 Lombok 自动生成
@Data  // 自动生成 equals 和 hashCode
public class Product {
    private Long id;
}
```

### 陷阱2：构造函数调用顺序

```java
public class Parent {
    public Parent() {
        init();  // 调用 init
    }
    
    public void init() {
        System.out.println("Parent init");
    }
}

public class Child extends Parent {
    private int value = 10;
    
    @Override
    public void init() {
        System.out.println("Child init: " + value);  // value 还未初始化！
    }
}

new Child();
// 输出：Child init: 0（不是10！）
```

---

## 下一步

- **[01-03-集合框架详解.md](./01-03-集合框架详解.md)** - List、Set、Map
- **[01-04-Lambda与Stream.md](./01-04-Lambda与Stream.md)** - 函数式编程

---

## 快速参考

```java
// 类定义
@Data
public class Product {
    private Long id;
    private String name;
}

// 继承
public class Phone extends Product {
    private String brand;
}

// 接口
public interface Service {
    void save(Product product);
}

// 实现接口
public class ProductService implements Service {
    @Override
    public void save(Product product) {
        // ...
    }
}

// 依赖注入（推荐）
@Service
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;
}
```
