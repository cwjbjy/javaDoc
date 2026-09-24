## 6. Optional 最佳实践

### 创建 Optional

```java
// 1. of - 值不能为 null
Optional<String> opt1 = Optional.of("Hello");
// Optional<String> opt2 = Optional.of(null);  // NullPointerException

// 2. ofNullable - 值可以为 null
Optional<String> opt3 = Optional.ofNullable("Hello");
Optional<String> opt4 = Optional.ofNullable(null);  // ✅ 空 Optional

// 3. empty - 空 Optional
Optional<String> opt5 = Optional.empty();
```

### 获取值

```java
Optional<Product> productOpt = productService.findById(1L);

// 1. get - 不安全（可能 NoSuchElementException）
// Product p = productOpt.get();  // ❌ 不推荐

// 2. orElse - 提供默认值
Product p2 = productOpt.orElse(new Product());

// 3. orElseGet - 提供默认值（懒加载）
Product p3 = productOpt.orElseGet(() -> new Product());

// 4. orElseThrow - 抛出异常
Product p4 = productOpt.orElseThrow(() -> new ApiException("商品不存在"));

// 5. isPresent + get（不推荐）
if (productOpt.isPresent()) {
    Product p = productOpt.get();
}

// 6. ifPresent - 有值时执行（推荐）
productOpt.ifPresent(p -> System.out.println(p.getName()));

// 7. ifPresentOrElse（Java 9+）
productOpt.ifPresentOrElse(
    p -> System.out.println(p.getName()),
    () -> System.out.println("Not found")
);
```

### 转换与过滤

```java
Optional<Product> productOpt = productService.findById(1L);

// map - 转换
Optional<String> nameOpt = productOpt.map(Product::getName);
String name = nameOpt.orElse("Unknown");

// 链式调用
String name2 = productService.findById(1L)
    .map(Product::getName)
    .map(String::toUpperCase)
    .orElse("UNKNOWN");

// flatMap - 避免 Optional<Optional<T>>
Optional<String> name3 = productOpt
    .flatMap(p -> Optional.ofNullable(p.getName()));

// filter - 过滤
Optional<Product> expensiveProduct = productOpt
    .filter(p -> p.getPrice().compareTo(new BigDecimal("100")) > 0);
```

### 实战场景

```java
// 场景1：Service层返回
public Optional<Product> findById(Long id) {
    Product product = productMapper.selectByPrimaryKey(id);
    return Optional.ofNullable(product);
}

// 场景2：Controller层处理
public CommonResult<Product> getProduct(Long id) {
    return productService.findById(id)
        .map(CommonResult::success)
        .orElse(CommonResult.failed("商品不存在"));
}

// 场景3：避免多层 null 检查
// ❌ 传统写法
String city = null;
if (user != null) {
    Address address = user.getAddress();
    if (address != null) {
        city = address.getCity();
    }
}

// ✅ Optional 写法
String city = Optional.ofNullable(user)
    .map(User::getAddress)
    .map(Address::getCity)
    .orElse("Unknown");
```

---
