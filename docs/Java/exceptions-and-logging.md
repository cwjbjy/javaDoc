# 01-05 异常处理机制

> 理解Java异常体系，掌握异常处理与自定义异常

## 1. 异常体系结构

```
Throwable
├── Error（系统错误，不应捕获）
│   ├── OutOfMemoryError
│   ├── StackOverflowError
│   └── ...
└── Exception（异常，应该处理）
    ├── RuntimeException（运行时异常，非受检）
    │   ├── NullPointerException
    │   ├── IndexOutOfBoundsException
    │   ├── IllegalArgumentException
    │   └── ...
    └── IOException、SQLException...（受检异常，必须处理）
```

---

## 2. 受检异常 vs 非受检异常

### 受检异常（Checked Exception）

```java
// 必须在方法签名声明或捕获
public void readFile(String path) throws IOException {
    FileReader reader = new FileReader(path);  // 可能抛出 IOException
}

// 调用时必须处理
try {
    readFile("data.txt");
} catch (IOException e) {
    e.printStackTrace();
}
```

### 非受检异常（Unchecked Exception）

```java
// 运行时异常，不强制处理
public void divide(int a, int b) {
    int result = a / b;  // 可能抛出 ArithmeticException
}

// 调用时可以不处理（但会导致程序崩溃）
divide(10, 0);  // ArithmeticException: / by zero
```

### 对比

| 类型 | 父类 | 是否强制处理 | 场景 | 示例 |
|------|------|-------------|------|------|
| 受检异常 | `Exception` | ✅ 必须 | 外部因素导致 | `IOException`、`SQLException` |
| 非受检异常 | `RuntimeException` | ❌ 可选 | 编程错误 | `NullPointerException`、`IllegalArgumentException` |

---

## 3. try-catch-finally

### 基本语法

```java
try {
    // 可能抛异常的代码
    Product product = productService.getById(id);
    return product;
} catch (ApiException e) {
    // 捕获业务异常
    log.error("业务错误: {}", e.getMessage());
    throw e;  // 重新抛出
} catch (Exception e) {
    // 捕获所有异常
    log.error("系统错误", e);
    throw new ApiException("获取商品失败");
} finally {
    // 无论是否异常都会执行（常用于关闭资源）
    cleanup();
}
```

### 多个 catch 块

```java
try {
    // ...
} catch (NullPointerException e) {
    // 处理空指针
} catch (IllegalArgumentException e) {
    // 处理非法参数
} catch (RuntimeException e) {
    // 处理其他运行时异常
} catch (Exception e) {
    // 处理所有异常（放最后）
}
```

### Java 7+ 多异常捕获

```java
// 多个异常类型用 | 分隔
try {
    // ...
} catch (IOException | SQLException e) {
    log.error("IO或DB错误", e);
    throw new ApiException("操作失败");
}
```

---

## 4. try-with-resources（自动关闭资源）

### 传统方式（繁琐）

```java
FileInputStream fis = null;
try {
    fis = new FileInputStream("data.txt");
    // 读取文件
} catch (IOException e) {
    e.printStackTrace();
} finally {
    if (fis != null) {
        try {
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
```

### try-with-resources（推荐）

```java
// 自动关闭实现了 AutoCloseable 的资源
try (FileInputStream fis = new FileInputStream("data.txt")) {
    // 读取文件
} catch (IOException e) {
    e.printStackTrace();
}  // fis 自动关闭

// 多个资源
try (FileInputStream fis = new FileInputStream("in.txt");
     FileOutputStream fos = new FileOutputStream("out.txt")) {
    // 操作
} catch (IOException e) {
    e.printStackTrace();
}
```

---

## 5. 抛出异常

### throw - 抛出异常对象

```java
public void setAge(int age) {
    if (age < 0) {
        throw new IllegalArgumentException("年龄不能为负数");
    }
    this.age = age;
}

public Product getById(Long id) {
    Product product = productMapper.selectByPrimaryKey(id);
    if (product == null) {
        throw new ApiException("商品不存在");
    }
    return product;
}
```

### throws - 声明方法可能抛出的异常

```java
// 声明受检异常（调用者必须处理）
public void readFile(String path) throws IOException {
    FileReader reader = new FileReader(path);
    // ...
}

// 多个异常
public void process() throws IOException, SQLException {
    // ...
}

// 不需要声明 RuntimeException
public void validate(String input) {
    if (input == null) {
        throw new IllegalArgumentException("参数不能为空");
    }
}
```

---

## 6. 自定义异常

### 定义异常类

```java
// 业务异常（继承 RuntimeException，不强制处理）
public class ApiException extends RuntimeException {
    private int code;
    
    public ApiException(String message) {
        super(message);
        this.code = 500;
    }
    
    public ApiException(int code, String message) {
        super(message);
        this.code = code;
    }
    
    public ApiException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
    }
    
    public int getCode() {
        return code;
    }
}

// 使用
throw new ApiException("商品不存在");
throw new ApiException(404, "商品不存在");
```

### 本项目的异常（mall-common）

```java
// ApiException - 业务异常
public class ApiException extends RuntimeException {
    private IErrorCode errorCode;
    
    public ApiException(IErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
    
    public ApiException(String message) {
        super(message);
    }
}

// 使用
throw new ApiException(ResultCode.VALIDATE_FAILED);
throw new ApiException("参数错误");
```

---

## 7. 全局异常处理（Spring Boot）

### @ControllerAdvice + @ExceptionHandler

```java
@ControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ApiException.class)
    @ResponseBody
    public CommonResult handleApiException(ApiException e) {
        log.error("业务异常: {}", e.getMessage());
        return CommonResult.failed(e.getMessage());
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public CommonResult handleValidException(MethodArgumentNotValidException e) {
        BindingResult result = e.getBindingResult();
        String message = result.getFieldError().getDefaultMessage();
        return CommonResult.validateFailed(message);
    }
    
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public CommonResult handleException(Exception e) {
        log.error("系统异常", e);
        return CommonResult.failed("系统异常");
    }
}
```

### 本项目的全局异常处理

```java
// mall-common 模块
@ControllerAdvice
public class GlobalExceptionHandler {
    
    @ResponseBody
    @ExceptionHandler(value = ApiException.class)
    public CommonResult handle(ApiException e) {
        if (e.getErrorCode() != null) {
            return CommonResult.failed(e.getErrorCode());
        }
        return CommonResult.failed(e.getMessage());
    }
    
    @ResponseBody
    @ExceptionHandler(value = MethodArgumentNotValidException.class)
    public CommonResult handleValidException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        String message = bindingResult.getAllErrors().get(0).getDefaultMessage();
        return CommonResult.validateFailed(message);
    }
    
    @ResponseBody
    @ExceptionHandler(value = Exception.class)
    public CommonResult handle(Exception e) {
        return CommonResult.failed(e.getMessage());
    }
}
```

---

## 8. 常见异常

### NullPointerException（空指针）

```java
// ❌ 常见场景
String name = null;
name.length();  // NullPointerException

Product product = getProduct();
product.getName();  // 如果 product 为 null

List<String> list = null;
list.size();  // NullPointerException

// ✅ 避免方法
if (name != null) {
    name.length();
}

Optional.ofNullable(product)
    .map(Product::getName)
    .orElse("Unknown");

if (CollUtil.isNotEmpty(list)) {
    list.size();
}
```

### IllegalArgumentException（非法参数）

```java
public void setAge(int age) {
    if (age < 0 || age > 150) {
        throw new IllegalArgumentException("年龄范围: 0-150");
    }
    this.age = age;
}

public void process(@NotNull String input) {
    if (input == null || input.isEmpty()) {
        throw new IllegalArgumentException("参数不能为空");
    }
}
```

### IndexOutOfBoundsException（索引越界）

```java
List<String> list = Arrays.asList("A", "B", "C");
String item = list.get(5);  // IndexOutOfBoundsException

// ✅ 检查索引
if (index >= 0 && index < list.size()) {
    String item = list.get(index);
}
```

### ClassCastException（类型转换）

```java
Object obj = "Hello";
Integer num = (Integer) obj;  // ClassCastException

// ✅ 使用 instanceof
if (obj instanceof Integer) {
    Integer num = (Integer) obj;
}

// Java 14+ 模式匹配
if (obj instanceof Integer num) {
    System.out.println(num);
}
```

### NumberFormatException（数字格式）

```java
String str = "abc";
int num = Integer.parseInt(str);  // NumberFormatException

// ✅ 处理异常
try {
    int num = Integer.parseInt(str);
} catch (NumberFormatException e) {
    num = 0;  // 默认值
}
```

---

## 9. 异常最佳实践

### 1. 不要忽略异常

```java
// ❌ 错误：吞掉异常
try {
    // ...
} catch (Exception e) {
    // 什么都不做
}

// ✅ 正确：至少记录日志
try {
    // ...
} catch (Exception e) {
    log.error("操作失败", e);
    throw new ApiException("操作失败");
}
```

### 2. 不要捕获 Throwable 或 Error

```java
// ❌ 错误
try {
    // ...
} catch (Throwable t) {  // 包括 Error
    // ...
}

// ✅ 正确
try {
    // ...
} catch (Exception e) {  // 只捕获 Exception
    // ...
}
```

### 3. 及早失败（Fail Fast）

```java
// ✅ 方法开头就校验参数
public void createProduct(Product product) {
    if (product == null) {
        throw new IllegalArgumentException("商品不能为空");
    }
    if (StrUtil.isBlank(product.getName())) {
        throw new IllegalArgumentException("商品名称不能为空");
    }
    // 继续处理...
}
```

### 4. 异常信息要详细

```java
// ❌ 信息不足
throw new ApiException("操作失败");

// ✅ 详细信息
throw new ApiException("创建商品失败：商品名称已存在 - " + product.getName());

// ✅ 包含原始异常
try {
    // ...
} catch (SQLException e) {
    throw new ApiException("数据库操作失败：保存商品失败", e);
}
```

### 5. 不要用异常控制流程

```java
// ❌ 错误：用异常控制逻辑
try {
    for (int i = 0; ; i++) {
        list.get(i);
    }
} catch (IndexOutOfBoundsException e) {
    // 遍历结束
}

// ✅ 正确：用正常逻辑
for (int i = 0; i < list.size(); i++) {
    list.get(i);
}
```

### 6. 关闭资源用 try-with-resources

```java
// ✅ 自动关闭
try (InputStream is = new FileInputStream("file.txt")) {
    // 读取
}

// 或手动关闭（finally）
InputStream is = null;
try {
    is = new FileInputStream("file.txt");
    // 读取
} finally {
    if (is != null) {
        try {
            is.close();
        } catch (IOException e) {
            log.error("关闭流失败", e);
        }
    }
}
```

---

## 10. 实战示例

### 示例1：Service 层异常处理

```java
@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductMapper productMapper;
    
    public Product getById(Long id) {
        // 参数校验
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("商品ID无效");
        }
        
        // 查询
        Product product = productMapper.selectByPrimaryKey(id);
        if (product == null) {
            throw new ApiException("商品不存在: id=" + id);
        }
        
        return product;
    }
    
    public int create(Product product) {
        // 参数校验
        validateProduct(product);
        
        // 检查重复
        if (existsByName(product.getName())) {
            throw new ApiException("商品名称已存在: " + product.getName());
        }
        
        // 保存
        try {
            return productMapper.insert(product);
        } catch (Exception e) {
            log.error("保存商品失败", e);
            throw new ApiException("保存商品失败", e);
        }
    }
    
    private void validateProduct(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("商品不能为空");
        }
        if (StrUtil.isBlank(product.getName())) {
            throw new IllegalArgumentException("商品名称不能为空");
        }
        if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("商品价格必须大于0");
        }
    }
}
```

### 示例2：Controller 层异常处理

```java
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {
    private final ProductService productService;
    
    @GetMapping("/{id}")
    public CommonResult<Product> getById(@PathVariable Long id) {
        try {
            Product product = productService.getById(id);
            return CommonResult.success(product);
        } catch (ApiException e) {
            // 业务异常，返回友好提示
            return CommonResult.failed(e.getMessage());
        } catch (Exception e) {
            // 系统异常，记录日志
            log.error("获取商品失败: id={}", id, e);
            return CommonResult.failed("系统错误");
        }
    }
    
    @PostMapping("/create")
    public CommonResult<Integer> create(@Valid @RequestBody Product product) {
        // @Valid 校验失败会抛出 MethodArgumentNotValidException
        // 由全局异常处理器处理
        int count = productService.create(product);
        return CommonResult.success(count);
    }
}
```

### 示例3：自定义业务异常枚举

```java
// 错误码接口
public interface IErrorCode {
    int getCode();
    String getMessage();
}

// 错误码枚举
public enum ResultCode implements IErrorCode {
    SUCCESS(200, "操作成功"),
    FAILED(500, "操作失败"),
    VALIDATE_FAILED(404, "参数检验失败"),
    UNAUTHORIZED(401, "暂未登录或token已经过期"),
    FORBIDDEN(403, "没有相关权限"),
    
    PRODUCT_NOT_FOUND(1001, "商品不存在"),
    PRODUCT_NAME_EXISTS(1002, "商品名称已存在"),
    PRODUCT_OUT_OF_STOCK(1003, "商品库存不足");
    
    private int code;
    private String message;
    
    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
    
    public int getCode() {
        return code;
    }
    
    public String getMessage() {
        return message;
    }
}

// 使用
throw new ApiException(ResultCode.PRODUCT_NOT_FOUND);
throw new ApiException(ResultCode.PRODUCT_OUT_OF_STOCK);
```

---

## 11. 日志记录

### SLF4J + Logback

```java
import lombok.extern.slf4j.Slf4j;

@Slf4j  // Lombok 自动生成 log 对象
@Service
public class ProductService {
    
    public void process() {
        log.trace("TRACE 级别");  // 最详细
        log.debug("DEBUG 级别");  // 调试信息
        log.info("INFO 级别");    // 一般信息
        log.warn("WARN 级别");    // 警告
        log.error("ERROR 级别");  // 错误
        
        // 带参数（推荐，避免字符串拼接）
        log.info("处理商品: id={}, name={}", id, name);
        
        // 记录异常堆栈
        try {
            // ...
        } catch (Exception e) {
            log.error("处理失败", e);  // 第二个参数是 Throwable
        }
    }
}
```

---

## 下一步

- **[01-06-字符串与日期.md](./01-06-字符串与日期.md)** - 字符串与日期
- **[01-07-注解与反射.md](./01-07-注解与反射.md)** - 注解与反射

---

## 快速参考

```java
// try-catch-finally
try {
    // 可能抛异常的代码
} catch (SpecificException e) {
    log.error("错误", e);
    throw new ApiException("操作失败");
} finally {
    // 清理资源
}

// try-with-resources
try (InputStream is = new FileInputStream("file.txt")) {
    // 使用资源
}

// 抛出异常
throw new IllegalArgumentException("参数错误");
throw new ApiException("业务错误");

// 自定义异常
public class ApiException extends RuntimeException {
    public ApiException(String message) {
        super(message);
    }
}

// 全局异常处理
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    @ResponseBody
    public CommonResult handle(ApiException e) {
        return CommonResult.failed(e.getMessage());
    }
}
```
