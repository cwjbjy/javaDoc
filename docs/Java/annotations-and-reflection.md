# 01-07 注解与反射

> 理解Java注解机制与反射原理，掌握Spring常用注解

## 1. 注解基础

### 什么是注解

注解（Annotation）是Java的元数据，用于为代码提供额外信息。

```java
// 注解语法：@注解名
@Override  // 标记方法重写
public String toString() {
    return "Product";
}

@Deprecated  // 标记已过时
public void oldMethod() {
    // ...
}

@SuppressWarnings("unchecked")  // 抑制编译警告
public void method() {
    List list = new ArrayList();
}
```

---

## 2. 内置注解

### @Override

```java
// 标记方法重写父类/接口方法
public class Product {
    @Override
    public String toString() {
        return "Product";
    }
    
    // @Override  // 编译错误（没有重写任何方法）
    // public void customMethod() { }
}
```

### @Deprecated

```java
// 标记已过时的类/方法
@Deprecated
public void oldMethod() {
    // 不推荐使用的方法
}

// 调用时会有删除线警告
// oldMethod();
```

### @SuppressWarnings

```java
// 抑制编译警告
@SuppressWarnings("unchecked")  // 抑制未检查警告
public void method1() {
    List list = new ArrayList();  // 原始类型警告被抑制
}

@SuppressWarnings({"unchecked", "deprecation"})  // 抑制多个警告
public void method2() {
    // ...
}

@SuppressWarnings("all")  // 抑制所有警告（不推荐）
public void method3() {
    // ...
}
```

### @FunctionalInterface（Java 8+）

```java
// 标记函数式接口（只有一个抽象方法）
@FunctionalInterface
public interface Calculator {
    int calculate(int a, int b);
    
    // int calculate2(int a, int b);  // 编译错误（多个抽象方法）
}

// 使用Lambda
Calculator add = (a, b) -> a + b;
```

---

## 3. 元注解（定义注解的注解）

### @Target - 注解使用位置

```java
import java.lang.annotation.*;

// 指定注解可以用在哪里
@Target(ElementType.METHOD)  // 只能用在方法上
public @interface MyAnnotation {
}

// 多个位置
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface MyAnnotation2 {
}

// ElementType枚举值：
// TYPE - 类、接口、枚举
// FIELD - 字段
// METHOD - 方法
// PARAMETER - 参数
// CONSTRUCTOR - 构造函数
// LOCAL_VARIABLE - 局部变量
// ANNOTATION_TYPE - 注解
// PACKAGE - 包
```

### @Retention - 注解保留时间

```java
// 指定注解保留到什么阶段
@Retention(RetentionPolicy.RUNTIME)  // 运行时可通过反射访问
public @interface MyAnnotation {
}

// RetentionPolicy枚举值：
// SOURCE - 源代码阶段，编译后丢弃（如 @Override）
// CLASS - 字节码阶段，运行时不可见（默认）
// RUNTIME - 运行时可见，可通过反射访问
```

### @Documented - 生成文档

```java
// 注解是否出现在Javadoc中
@Documented
public @interface MyAnnotation {
}
```

### @Inherited - 可继承

```java
// 子类是否继承父类的注解
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MyAnnotation {
}

@MyAnnotation
class Parent {
}

class Child extends Parent {
    // 自动继承 @MyAnnotation
}
```

---

## 4. 自定义注解

### 基本语法

```java
import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Log {
    // 注解属性（看起来像方法）
    String value() default "";          // 默认属性
    String description() default "";
    int level() default 1;
}

// 使用
@Log(value = "保存商品", description = "新增商品", level = 2)
public void save(Product product) {
    // ...
}

// 只有value时可以省略属性名
@Log("保存商品")
public void save(Product product) {
    // ...
}
```

### 注解属性类型

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MyAnnotation {
    // 支持的类型
    String stringValue();
    int intValue();
    boolean boolValue();
    Class<?> classValue();
    MyEnum enumValue();
    String[] arrayValue();
    OtherAnnotation annotationValue();
}

enum MyEnum {
    A, B, C
}

@interface OtherAnnotation {
    String value();
}

// 使用
@MyAnnotation(
    stringValue = "test",
    intValue = 123,
    boolValue = true,
    classValue = String.class,
    enumValue = MyEnum.A,
    arrayValue = {"A", "B"},
    annotationValue = @OtherAnnotation(value = "inner")
)
public void method() {
}
```

---

## 5. Spring 常用注解

### 依赖注入

```java
// @Autowired - 自动装配
@Service
public class ProductService {
    @Autowired  // 字段注入（不推荐）
    private ProductMapper productMapper;
}

// @RequiredArgsConstructor - 构造函数注入（推荐）
@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductMapper productMapper;  // final字段
    // Lombok自动生成构造函数
}

// @Resource - JSR-250标准（按名称注入）
@Service
public class ProductService {
    @Resource(name = "productMapper")
    private ProductMapper productMapper;
}

// @Qualifier - 指定Bean名称
@Service
public class ProductService {
    @Autowired
    @Qualifier("productMapperImpl")
    private ProductMapper productMapper;
}
```

### 组件注解

```java
// @Component - 通用组件
@Component
public class MyComponent {
}

// @Service - 业务层
@Service
public class ProductService {
}

// @Repository - 数据访问层
@Repository
public class ProductDao {
}

// @Controller - 控制器（返回视图）
@Controller
public class ProductController {
}

// @RestController - REST控制器（返回JSON）
@RestController  // = @Controller + @ResponseBody
public class ProductController {
}

// @Configuration - 配置类
@Configuration
public class AppConfig {
    @Bean
    public MyService myService() {
        return new MyService();
    }
}
```

### Web请求映射

```java
// @RequestMapping - 请求映射
@RestController
@RequestMapping("/product")  // 类级别
public class ProductController {
    
    @RequestMapping(value = "/list", method = RequestMethod.GET)
    public List<Product> list() {
        return productService.list();
    }
    
    // 简化写法
    @GetMapping("/list")  // = @RequestMapping(method = GET)
    public List<Product> list() { }
    
    @PostMapping("/create")
    public int create(@RequestBody Product product) { }
    
    @PutMapping("/update/{id}")
    public int update(@PathVariable Long id, @RequestBody Product product) { }
    
    @DeleteMapping("/delete/{id}")
    public int delete(@PathVariable Long id) { }
}
```

### 请求参数

```java
@RestController
@RequestMapping("/product")
public class ProductController {
    
    // @PathVariable - 路径变量
    @GetMapping("/{id}")
    public Product getById(@PathVariable Long id) {
        return productService.getById(id);
    }
    
    // @RequestParam - 查询参数
    @GetMapping("/list")
    public List<Product> list(
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "1") Integer pageNum,
        @RequestParam(defaultValue = "10") Integer pageSize
    ) {
        return productService.list(keyword, pageNum, pageSize);
    }
    
    // @RequestBody - 请求体（JSON）
    @PostMapping("/create")
    public int create(@RequestBody Product product) {
        return productService.create(product);
    }
    
    // @RequestHeader - 请求头
    @GetMapping("/info")
    public String info(@RequestHeader("Authorization") String token) {
        return "Token: " + token;
    }
}
```

### 参数校验

```java
// 实体类
@Data
public class Product {
    @NotNull(message = "商品ID不能为空")
    private Long id;
    
    @NotBlank(message = "商品名称不能为空")
    @Length(min = 2, max = 50, message = "商品名称长度2-50")
    private String name;
    
    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于0")
    private BigDecimal price;
    
    @Min(value = 0, message = "库存不能为负")
    private Integer stock;
    
    @Email(message = "邮箱格式错误")
    private String email;
}

// Controller
@PostMapping("/create")
public CommonResult create(@Valid @RequestBody Product product) {
    // @Valid 触发校验，失败抛出 MethodArgumentNotValidException
    return CommonResult.success(productService.create(product));
}

// 常用校验注解：
// @NotNull - 不能为null
// @NotEmpty - 不能为null或空（字符串、集合）
// @NotBlank - 不能为null或空白（字符串）
// @Size(min, max) - 集合/数组长度
// @Length(min, max) - 字符串长度
// @Min / @Max - 数值范围
// @DecimalMin / @DecimalMax - 小数范围
// @Pattern(regexp) - 正则匹配
// @Email - 邮箱格式
// @Past / @Future - 日期过去/未来
```

---

## 6. Lombok 注解

```java
// @Data - 生成getter/setter/toString/equals/hashCode
@Data
public class Product {
    private Long id;
    private String name;
}

// @Getter/@Setter - 单独生成
@Getter
@Setter
public class Product {
    private Long id;
}

// @NoArgsConstructor - 无参构造
// @AllArgsConstructor - 全参构造
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    private Long id;
    private String name;
}

// @RequiredArgsConstructor - final字段构造
@RequiredArgsConstructor
public class ProductService {
    private final ProductMapper productMapper;
}

// @Builder - 建造者模式
@Builder
@Data
public class Product {
    private Long id;
    private String name;
    private BigDecimal price;
}

// 使用
Product product = Product.builder()
    .id(1L)
    .name("iPhone")
    .price(new BigDecimal("999"))
    .build();

// @Slf4j - 自动生成log对象
@Slf4j
@Service
public class ProductService {
    public void save() {
        log.info("保存商品");
    }
}

// @ToString - 生成toString
@ToString
public class Product {
    private Long id;
    private String name;
}

// @EqualsAndHashCode - 生成equals和hashCode
@EqualsAndHashCode
public class Product {
    private Long id;
}
```

---

## 7. 反射（Reflection）

### 什么是反射

反射是Java在运行时获取类信息、调用方法、访问字段的能力。

### 获取Class对象

```java
// 方式1：类名.class
Class<Product> clazz1 = Product.class;

// 方式2：对象.getClass()
Product product = new Product();
Class<?> clazz2 = product.getClass();

// 方式3：Class.forName()
Class<?> clazz3 = Class.forName("com.macro.mall.model.Product");

// 三种方式获取的是同一个Class对象
System.out.println(clazz1 == clazz2);  // true
```

### 创建实例

```java
// 无参构造
Class<Product> clazz = Product.class;
Product product = clazz.newInstance();  // 已过时

// 推荐写法
Product product2 = clazz.getDeclaredConstructor().newInstance();

// 有参构造
Constructor<Product> constructor = clazz.getConstructor(Long.class, String.class);
Product product3 = constructor.newInstance(1L, "iPhone");
```

### 访问字段

```java
Class<Product> clazz = Product.class;
Product product = clazz.getDeclaredConstructor().newInstance();

// 获取public字段
Field field1 = clazz.getField("name");

// 获取所有字段（包括private）
Field field2 = clazz.getDeclaredField("name");
field2.setAccessible(true);  // 访问private字段

// 设置值
field2.set(product, "iPhone");

// 获取值
String name = (String) field2.get(product);
```

### 调用方法

```java
Class<Product> clazz = Product.class;
Product product = clazz.getDeclaredConstructor().newInstance();

// 获取方法
Method method = clazz.getMethod("setName", String.class);

// 调用方法
method.invoke(product, "iPhone");

// 获取private方法
Method privateMethod = clazz.getDeclaredMethod("privateMethod");
privateMethod.setAccessible(true);
privateMethod.invoke(product);
```

### 读取注解

```java
// 定义注解
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Log {
    String value();
    int level() default 1;
}

// 使用注解
public class ProductService {
    @Log(value = "保存商品", level = 2)
    public void save(Product product) {
        // ...
    }
}

// 通过反射读取
Class<ProductService> clazz = ProductService.class;
Method method = clazz.getMethod("save", Product.class);

// 判断是否有注解
if (method.isAnnotationPresent(Log.class)) {
    // 获取注解
    Log log = method.getAnnotation(Log.class);
    System.out.println(log.value());  // "保存商品"
    System.out.println(log.level());  // 2
}
```

---

## 8. 实战示例：自定义日志注解

### 定义注解

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {
    String value() default "";  // 操作描述
    String module() default "";  // 模块名
}
```

### 使用注解

```java
@RestController
@RequestMapping("/product")
public class ProductController {
    
    @PostMapping("/create")
    @OperationLog(value = "创建商品", module = "商品管理")
    public CommonResult create(@RequestBody Product product) {
        return CommonResult.success(productService.create(product));
    }
    
    @DeleteMapping("/delete/{id}")
    @OperationLog(value = "删除商品", module = "商品管理")
    public CommonResult delete(@PathVariable Long id) {
        return CommonResult.success(productService.delete(id));
    }
}
```

### AOP切面处理

```java
@Aspect
@Component
@Slf4j
public class OperationLogAspect {
    
    @Pointcut("@annotation(com.macro.mall.common.annotation.OperationLog)")
    public void logPointcut() {
    }
    
    @Around("logPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        
        // 获取注解
        OperationLog annotation = method.getAnnotation(OperationLog.class);
        String operation = annotation.value();
        String module = annotation.module();
        
        // 记录开始
        log.info("【{}】{} - 开始", module, operation);
        long startTime = System.currentTimeMillis();
        
        try {
            // 执行方法
            Object result = joinPoint.proceed();
            
            // 记录成功
            long duration = System.currentTimeMillis() - startTime;
            log.info("【{}】{} - 成功，耗时{}ms", module, operation, duration);
            
            return result;
        } catch (Throwable e) {
            // 记录失败
            log.error("【{}】{} - 失败：{}", module, operation, e.getMessage());
            throw e;
        }
    }
}
```

---

## 9. Spring注解原理简述

### 依赖注入原理

```java
// Spring容器启动时：
// 1. 扫描 @Component/@Service/@Repository/@Controller 注解
// 2. 创建Bean实例，放入IoC容器
// 3. 处理 @Autowired，注入依赖

// 伪代码
Map<String, Object> iocContainer = new HashMap<>();

// 创建Bean
ProductService service = new ProductService();
iocContainer.put("productService", service);

// 注入依赖
Field field = service.getClass().getDeclaredField("productMapper");
if (field.isAnnotationPresent(Autowired.class)) {
    Object bean = iocContainer.get("productMapper");
    field.setAccessible(true);
    field.set(service, bean);
}
```

---

## 10. 常见陷阱

### 陷阱1：@Autowired 循环依赖

```java
// ❌ 循环依赖
@Service
public class ServiceA {
    @Autowired
    private ServiceB serviceB;
}

@Service
public class ServiceB {
    @Autowired
    private ServiceA serviceA;
}

// ✅ 使用 @Lazy 延迟加载
@Service
public class ServiceA {
    @Autowired
    @Lazy
    private ServiceB serviceB;
}
```

### 陷阱2：反射性能开销

```java
// ❌ 频繁使用反射（慢）
for (int i = 0; i < 10000; i++) {
    Method method = clazz.getMethod("getName");
    method.invoke(product);
}

// ✅ 缓存Method对象
Method method = clazz.getMethod("getName");
for (int i = 0; i < 10000; i++) {
    method.invoke(product);
}
```

---

## 下一步

- **[01-08-IO与文件操作.md](./01-08-IO与文件操作.md)** - IO与文件
- **[01-09-多线程基础.md](./01-09-多线程基础.md)** - 多线程

---

## 快速参考

```java
// 自定义注解
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface MyAnnotation {
    String value() default "";
}

// Spring常用注解
@Service / @Controller / @Repository / @Component
@Autowired / @RequiredArgsConstructor
@RequestMapping / @GetMapping / @PostMapping
@PathVariable / @RequestParam / @RequestBody
@Valid / @NotNull / @NotBlank

// Lombok注解
@Data / @Getter / @Setter
@NoArgsConstructor / @AllArgsConstructor / @RequiredArgsConstructor
@Builder / @Slf4j

// 反射
Class<?> clazz = Product.class;
Object obj = clazz.getDeclaredConstructor().newInstance();
Field field = clazz.getDeclaredField("name");
field.setAccessible(true);
field.set(obj, "value");
```
