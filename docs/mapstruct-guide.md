# MapStruct 对象映射指南

> 本指南介绍 MapStruct 的核心注解 `@Mapper` 与 `@Mapping` 的使用方式。
> 项目使用 MapStruct 1.5.5.Final + Lombok + Spring Boot 4.0.6。

---

## 目录

1. [为什么需要 MapStruct](#1-为什么需要-mapstruct)
2. [@Mapper 注解](#2-mapper-注解)
   - [2.1 componentModel —— 注入模式](#21-componentmodel--注入模式)
   - [2.2 imports —— 导入辅助类](#22-imports--导入辅助类)
   - [2.3 两种 @Mapping 写法](#23-两种-mapping-写法)
3. [@Mapping 属性详解](#3-mapping-属性详解)
   - [3.1 source / target —— 字段名映射](#31-source--target--字段名映射)
   - [3.2 ignore —— 跳过字段](#32-ignore--跳过字段)
   - [3.3 expression —— Java 表达式](#33-expression--java-表达式)
   - [3.4 constant —— 固定值](#34-constant--固定值)
   - [3.5 defaultValue —— 默认值](#35-defaultvalue--默认值)
   - [3.6 dateFormat —— 日期格式化](#36-dateformat--日期格式化)
   - [3.7 numberFormat —— 数字格式化](#37-numberformat--数字格式化)
   - [3.8 qualifiedByName —— 自定义映射方法](#38-qualifiedbyname--自定义映射方法)
4. [嵌套对象与集合映射](#4-嵌套对象与集合映射)
5. [在 Spring Boot 中使用](#5-在-spring-boot-中使用)
6. [工作原理：编译期代码生成](#6-工作原理编译期代码生成)
7. [速查清单](#7-速查清单)

---

## 1. 为什么需要 MapStruct

### 问题的起源

在 Web 应用中，**前端传来的数据格式** 和 **数据库存储的结构** 往往不同：

```
前端传来的 DTO                          数据库中需要存入的 Entity
─────────────                          ─────────────────────────
UserDTO                                UserEntity
  nickName: "小明"                       id: 1            ← 数据库自动生成
  email:   "xm@test.com"                name: "小明"      ← 来自 nickName
                                        email: "xm@test.com"
                                        createdAt: now()   ← DTO 中没有
```

没有工具时，你需要手写大量样板代码：

```java
// 手动转换：15 行代码，全是机械重复的 get/set
UserEntity entity = new UserEntity();
entity.setName(dto.getNickName());      // 字段名不同
entity.setEmail(dto.getEmail());        // 字段名相同
entity.setCreatedAt(new Date());        // DTO 没有这个字段
// id 由数据库生成，不需要设置
```

### MapStruct 的解决方案

MapStruct 的核心是 `@Mapper` 和 `@Mapping` 两个注解。你声明转换规则，MapStruct 在编译期自动生成实现代码：

```java
@Mapper(componentModel = "spring", imports = Date.class)
public interface UserConverter {

    @Mapping(source = "nickName", target = "name")            // 字段名不同
    @Mapping(target = "id", ignore = true)                     // 跳过
    @Mapping(target = "createdAt", expression = "java(new Date())")  // DTO 没有
    UserEntity toEntity(UserDTO dto);
}
// 编译后自动生成 UserConverterImpl，里面就是那些 get/set，但不用你手写
```

> **核心思想**：MapStruct 不改变你的代码逻辑，它只是把重复的 `get`/`set` 从手写变成了自动生成。性能接近手写，零反射开销。

### MapStruct 不是什么

- **不是 BeanUtils.copyProperties**：BeanUtils 在运行时用反射拷贝，MapStruct 在编译期生成代码
- **不是 JSON 序列化工具**：Jackson 处理 JSON ↔ 对象，MapStruct 处理对象 ↔ 对象
- **不能替代业务逻辑**：它只做字段拷贝，复杂逻辑仍需手写

### 两个核心注解的分工

```
┌─────────────────────────────────────────────────────────┐
│                                                         │
│  @Mapper          →  接口级别，控制"怎么生成实现类"      │
│  @Mapping         →  方法级别，控制"单个字段怎么映射"    │
│                                                         │
│  一个 Mapper 接口上有一个 @Mapper，方法上有多个 @Mapping  │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## 2. @Mapper 注解

`@Mapper` 标注在接口上，告诉 MapStruct "为这个接口生成实现类"。它控制生成的实现类以什么方式被 Spring 管理、需要导入哪些辅助类。

### 2.1 componentModel —— 注入模式

决定生成代码如何被 Spring 管理：

```
componentModel = "spring"   → 生成 @Component 类，可 @Autowired / 构造器注入
componentModel = "default"  → 普通类，需手动 new MapperImpl()
componentModel = "jsr330"   → 生成 @Named 注解（JSR-330）
componentModel = "cdi"      → CDI 注入（Jakarta EE）
```

**项目始终用 `"spring"`**，生成 `@Component` 标注的实现类，可直接注入。

### 2.2 imports —— 导入辅助类

expression 中使用非 `java.lang` 包下的类时，必须在此导入：

```java
// ❌ 没有 imports，编译报错
@Mapper(componentModel = "spring")
public interface UserConverter {
    @Mapping(target = "createdAt", expression = "java(new Date())")  // Date 找不到
}

// ✅ 加上 imports
@Mapper(componentModel = "spring", imports = Date.class)
public interface UserConverter {
    @Mapping(target = "createdAt", expression = "java(new Date())")  // Date 可用
}

// 🤷 另一种写法：expression 中写全限定名（不需要 imports）
@Mapper(componentModel = "spring")
public interface UserConverter {
    @Mapping(target = "createdAt", expression = "java(new java.util.Date())")
}
```

> **建议**：用 `imports`，代码更简洁。多个类时：`imports = {Date.class, UUID.class}`。

### 2.3 两种 @Mapping 写法

MapStruct 1.5+ 支持两种写法，效果完全相同：

```java
// 写法 A：多个 @Mapping 堆叠（推荐，更简洁）
@Mapping(target = "id", ignore = true)
@Mapping(target = "createdAt", expression = "java(new Date())")
UserEntity toEntity(UserDTO dto);

// 写法 B：@Mappings 包装（旧写法，同样合法）
@Mappings({
    @Mapping(target = "id", ignore = true),
    @Mapping(target = "createdAt", expression = "java(new Date())")
})
UserEntity toEntity(UserDTO dto);
```

---

## 3. @Mapping 属性详解

`@Mapping` 是 MapStruct 最核心的注解，标注在方法上，控制单个字段的映射方式。

### 属性速查表

| 属性           | 说明                             | 示例                                 |
| -------------- | -------------------------------- | ------------------------------------ |
| `source`       | 源对象属性名，支持嵌套路径       | `source = "user.address.city"`       |
| `target`       | 目标对象属性名                   | `target = "name"`                    |
| `ignore`       | 跳过此字段                       | `ignore = true`                      |
| `expression`   | Java 表达式，用 `java(...)` 包裹 | `expression = "java(new Date())"`    |
| `constant`     | 固定值，无视源属性               | `constant = "ACTIVE"`                |
| `defaultValue` | 源属性为 null 时的默认值         | `defaultValue = "Unknown"`           |
| `dateFormat`   | 日期格式化字符串                 | `dateFormat = "yyyy-MM-dd HH:mm:ss"` |
| `numberFormat` | 数字格式化字符串                 | `numberFormat = "#,##0.00"`          |

### 字段映射的基本原则

在使用具体属性之前，先理解 MapStruct 的默认行为：

```java
// 字段名相同、类型相同 → 自动映射，无需任何注解
UserEntity toEntity(UserDTO dto);
// 生成：entity.setName(dto.getName()); entity.setEmail(dto.getEmail());
```

> **关键规则**：只有字段名不同、类型不同、或者目标字段需要特殊处理时，才需要 `@Mapping` 注解。同名同类型的字段自动映射，不需要重复声明。

### 3.1 source / target —— 字段名映射

源和目标的字段名相同时，自动映射；不同时才需要声明：

```java
@Mapping(source = "userName", target = "name")     // userName → name
@Mapping(source = "orderNo",  target = "orderNumber")  // orderNo → orderNumber
UserDTO toDTO(User user);
```

> **嵌套路径**：`source = "address.city"` 表示取 `user.getAddress().getCity()`。

### 3.2 ignore —— 跳过字段

告诉 MapStruct 不要映射某个字段：

```java
@Mapping(target = "id", ignore = true)       // id 由数据库生成，DTO 没有
@Mapping(target = "createdAt", ignore = true)  // 创建时间由 expression 单独处理
UserEntity toEntity(UserDTO dto);

// 反向转换时也一样
@Mapping(target = "password", ignore = true)  // 密码不返回给前端
UserDTO toDTO(UserEntity entity);
```

> **双向转换**：MapStruct 不限制转换方向。`toEntity` 是 DTO → Entity，`toDTO` 是 Entity → DTO。你可以把两个方向的方法写在同一个 Mapper 接口中，互不影响。

### 3.3 expression —— Java 表达式

用 `java(...)` 包裹任意 Java 代码，直接赋给目标字段：

```java
@Mapper(componentModel = "spring", imports = {Date.class, UUID.class})
public interface UserConverter {

    @Mapping(target = "createdAt", expression = "java(new Date())")    // 当前时间
    @Mapping(target = "token",     expression = "java(UUID.randomUUID().toString())")
    UserEntity toEntity(UserDTO dto);
}
```

> **注意**：如果 expression 里用到的类不是 `java.lang` 包下的，必须在 `@Mapper(imports = ...)` 中导入，否则 MapStruct 生成的代码无法编译。

**expression vs 方法的区别**：

```java
// expression：写在注解里，适合简单表达式
@Mapping(target = "createdAt", expression = "java(new Date())")

// 方法：写在接口里，适合多行逻辑（见 3.8 qualifiedByName）
default Date now() { return new Date(); }
```

### 3.4 constant —— 固定值

不管源属性是什么，目标字段固定为指定值：

```java
@Mapping(target = "status", constant = "ACTIVE")
UserEntity toEntity(UserDTO dto);
// 生成：entity.setStatus("ACTIVE");
```

> **constant 只能写字符串字面量**。如果需要动态值（如 `new Date()`），用 `expression`。

### 3.5 defaultValue —— 默认值

当源属性为 null 时，用默认值代替：

```java
@Mapping(source = "nickName", target = "displayName", defaultValue = "匿名用户")
UserDTO toDTO(UserEntity entity);
// 如果 entity.getNickName() == null → dto.setDisplayName("匿名用户")
// 如果 entity.getNickName() == "小明" → dto.setDisplayName("小明")
```

### 3.6 dateFormat —— 日期格式化

控制 String ↔ Date 之间的转换格式：

```java
@Mapping(target = "birthday", dateFormat = "yyyy-MM-dd")
UserEntity toEntity(UserDTO dto);
// 源字段 String "2000-01-01" → 目标字段 Date 对象
// 反过来：Date 对象 → String "2000-01-01"
```

> **仅作用于 source → target 的映射**。如果目标字段用 `expression` 直接赋值（如 `expression = "java(new Date())"`），不会走 source 映射，`dateFormat` 不生效。

### 3.7 numberFormat —— 数字格式化

控制数字的格式化方式：

```java
@Mapping(source = "price", target = "priceDisplay", numberFormat = "¥#,##0.00")
ProductDTO toDTO(Product product);
// price = 12500 → priceDisplay = "¥12,500.00"
```

**格式模式拆解**（基于 Java `DecimalFormat`）：

| 符号  | 含义                 | 示例           |
| ----- | -------------------- | -------------- |
| `¥`   | 字面字符，原样输出   | 货币前缀       |
| `#`   | 可选数字位，无则省略 | 不补前导零     |
| `,`   | 千分位分隔符         | `12,500`       |
| `0`   | 必填数字位，无则补零 | 保证至少一位   |
| `.00` | 小数点后固定 2 位    | `3.5` → `3.50` |

更多示例：

```java
numberFormat = "#,##0.00"     // 12500 → "12,500.00"（无货币符号）
numberFormat = "0.0"          // 3.14  → "3.1"（保留 1 位小数）
numberFormat = "#,##0%"       // 0.856 → "86%"（百分比，自动 ×100）
```

### 3.8 qualifiedByName —— 自定义映射方法

当 MapStruct 内置转换规则不够用时，用 `qualifiedByName` 指定一个自定义方法。

**适用场景**：枚举转换、数据加密/脱敏、单位换算、字段拼接等。

```java
@Mapper(componentModel = "spring")
public interface UserConverter {

    @Mapping(source = "rawPhone", target = "phone", qualifiedByName = "maskPhone")
    @Mapping(source = "statusCode", target = "statusText", qualifiedByName = "toStatusText")
    UserDTO toDTO(UserEntity entity);

    // 自定义方法：手机号脱敏
    @Named("maskPhone")
    default String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    // 自定义方法：状态码 → 文字
    @Named("toStatusText")
    default String toStatusText(Integer code) {
        return switch (code) {
            case 0 -> "待激活";
            case 1 -> "正常";
            case 2 -> "已禁用";
            default -> "未知";
        };
    }
}
```

**工作流程**：

```
源字段 rawPhone = "13812345678"
        │
        ▼
  @Named("maskPhone") → 调用 maskPhone("13812345678")
        │
        ▼
目标字段 phone = "138****5678"
```

> **关键点**：用 `@Named` 给方法命名，再用 `qualifiedByName` 引用这个名字。方法签名中参数类型必须匹配源字段类型，返回值类型匹配目标字段类型。

---

## 4. 嵌套对象与集合映射

MapStruct 能自动处理嵌套对象和集合类型的映射。

### 4.1 场景

```
UserDTO                               UserEntity
├── name: String                      ├── name: String
├── email: String                     ├── email: String
└── orders: List<OrderDTO>      →     └── orders: List<OrderEntity>
    ├── orderNo                            ├── orderNo
    └── amount                             └── amount
```

### 4.2 代码

```java
@Mapper(componentModel = "spring")
public interface UserConverter {

    // 主转换方法
    UserEntity toEntity(UserDTO dto);

    // 子对象转换方法
    OrderEntity toOrderEntity(OrderDTO dto);
}
// MapStruct 看到 List<OrderDTO> → List<OrderEntity>，自动调用 toOrderEntity()
```

### 4.3 生成的代码（简化）

```java
// UserConverterImpl.java
@Override
public UserEntity toEntity(UserDTO dto) {
    UserEntity entity = new UserEntity();
    entity.setName(dto.getName());
    entity.setEmail(dto.getEmail());

    // 集合自动遍历转换
    List<OrderEntity> orders = new ArrayList<>();
    for (OrderDTO orderDTO : dto.getOrders()) {
        orders.add(toOrderEntity(orderDTO));  // ← 自动调用子对象方法
    }
    entity.setOrders(orders);

    return entity;
}

@Override
public OrderEntity toOrderEntity(OrderDTO dto) {
    OrderEntity entity = new OrderEntity();
    entity.setOrderNo(dto.getOrderNo());
    entity.setAmount(dto.getAmount());
    return entity;
}
```

> **关键规则**：你只需要在 Mapper 接口中定义子对象转换方法，MapStruct 自动在集合映射时调用它。不需要手写循环。

---

## 5. 在 Spring Boot 中使用

### 5.1 添加依赖

```xml
<!-- MapStruct API（编译和运行都需要） -->
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.5.5.Final</version>
</dependency>
```

### 5.2 配置注解处理器

MapStruct 是编译期工具，需要在 `maven-compiler-plugin` 中注册注解处理器。如果同时使用 Lombok，**顺序不能反**：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <!-- ① Lombok 先跑：生成 getter/setter -->
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
            </path>
            <!-- ② MapStruct 后跑：读到 Lombok 生成的 getter/setter -->
            <path>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct-processor</artifactId>
                <version>1.5.5.Final</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

> **为什么顺序重要？** Lombok 先生成 getter/setter → MapStruct 才能读取到属性并生成 `setXxx(getXxx())` 调用。顺序反了会报 `Unknown property` 错误。

### 5.3 在 Service 中注入

`componentModel = "spring"` 让实现类注册为 Spring Bean，像普通 Service 一样注入使用：

```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserConverter userConverter;       // ← Spring 自动注入

    public UserEntity create(UserDTO dto) {
        return userConverter.toEntity(dto);       // 一行完成 DTO → Entity 转换
    }
}
```

### 5.4 项目 mapper 包约定

```
src/main/java/com/example/demo1/module/
├── user/
│   ├── dto/
│   │   └── UserDTO.java
│   ├── entity/
│   │   └── UserEntity.java
│   ├── mapper/
│   │   └── UserMapper.java          ← Mapper 接口放这里
│   ├── controller/
│   │   └── UserController.java
│   └── service/
│       └── UserService.java        ← 注入 Mapper，调用转换方法
```

---

## 6. 工作原理：编译期代码生成

MapStruct 在编译期为你声明的接口生成实现类。

### 生成的代码在哪

```
target/generated-sources/annotations/
  └── com/example/demo1/module/user/mapper/
        └── UserMapperImpl.java       ← 打开看看
```

### 生成的代码长什么样

你写的接口：

```java
@Mapper(componentModel = "spring", imports = Date.class)
public interface UserMapper {

    @Mapping(source = "nickName", target = "name")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", expression = "java(new Date())")
    UserEntity toEntity(UserDTO dto);
}
```

MapStruct 编译生成的 `UserMapperImpl.java`：

```java
@Component
public class UserMapperImpl implements UserMapper {

    @Override
    public UserEntity toEntity(UserDTO dto) {
        if (dto == null) return null;

        UserEntity entity = new UserEntity();

        entity.setName(dto.getNickName());       // source → target
        entity.setEmail(dto.getEmail());         // 同名自动映射
        entity.setCreatedAt(new Date());         // expression
        // id 被 ignore，未映射

        return entity;
    }
}
```

> **建议**：首次使用 MapStruct 后，打开生成的 Impl 类看一遍。你会发现它就是手写 get/set 的翻版——你省掉的代码，它帮你写了。

---

## 7. 速查清单

### 7.1 @Mapper 速查

```
┌──────────────────────────────────────────────────────────────┐
│                    @Mapper 速查                               │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  @Mapper(componentModel = "spring", imports = Date.class)     │
│  ─────────────────────────────────────────────────────────    │
│                                                              │
│  componentModel = "spring"  → 生成 @Component，可注入         │
│  imports = SomeClass.class  → 导入类，供 expression 使用       │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

### 7.2 @Mapping 速查

```
┌──────────────────────────────────────────────────────────────┐
│                    @Mapping 速查                              │
├────────────────────────┬─────────────────────────────────────┤
│  source = "srcName"   │  源字段名（名字不同时用）             │
│  target = "dstName"   │  目标字段名                           │
│  ignore = true        │  跳过此字段                           │
│  expression = "java()"│  Java 表达式赋值                     │
│  constant = "value"   │  固定字符串值                         │
│  defaultValue = "x"   │  源为 null 时的默认值                 │
│  dateFormat = "fmt"   │  日期格式化                          │
│  numberFormat = "fmt" │  数字格式化                          │
│  qualifiedByName      │  指定自定义映射方法                   │
│    = "methodName"     │                                     │
└────────────────────────┴─────────────────────────────────────┘
```

### 7.3 注解 import 速查

```
┌──────────────────────────────┬──────────────────────────────────────────┐
│           注解               │  import 包                                │
├──────────────────────────────┼──────────────────────────────────────────┤
│  @Mapper                    │  org.mapstruct.Mapper                     │
│  @Mapping                   │  org.mapstruct.Mapping                    │
│  @Mappings（可选包装）       │  org.mapstruct.Mappings                   │
│  @Named（自定义方法命名）    │  org.mapstruct.Named                      │
└──────────────────────────────┴──────────────────────────────────────────┘
```

### 7.4 常见映射模式

```
┌──────────────────────────────────────────────────────────────┐
│                常见映射模式速查                               │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  字段名相同、类型相同 → 自动映射，不需要注解                   │
│                                                              │
│  字段名不同 → @Mapping(source = "A", target = "B")            │
│                                                              │
│  目标字段 DTO 没有，跳过 → @Mapping(target = "X", ignore)     │
│                                                              │
│  目标字段需要计算 → @Mapping(target = "X",                    │
│                       expression = "java(...)")               │
│                                                              │
│  目标字段固定值 → @Mapping(target = "X", constant = "value")  │
│                                                              │
│  源为 null 给默认值 → @Mapping(source = "X",                  │
│                         defaultValue = "...")                 │
│                                                              │
│  嵌套对象/集合 → 定义子对象转换方法，自动遍历                  │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

---

**总结**：MapStruct 的用法围绕两个注解展开——`@Mapper` 控制实现类的生成方式，`@Mapping` 控制每个字段的映射规则。字段名相同、类型相同时自动映射，只有名字不同或需要特殊处理时才写 `@Mapping`。`qualifiedByName` 是遇到复杂转换时的"逃生舱"。遇到问题时，打开 `target/generated-sources/annotations/` 下的 Impl 类，一目了然。
