# 空值处理（Optional）指南

> 本指南循序渐进介绍 Java Optional 的用法。从"防御式 null 检查的痛点"到"一条链式调用搞定"，每步只引入一个新概念。
> 基于 Java 8+。Spring Data 中 `findById()` 返回的就是 `Optional`，两者天然契合。

---

## 目录

1. [为什么需要 Optional](#1-为什么需要-optional)
2. [入门三步走](#2-入门三步走)
   - [第一层：安全包装与解包](#21-第一层安全包装与解包)
   - [第二层：有值则用，无值则兜底](#22-第二层有值则用无值则兜底)
   - [第三层：管道式转换](#23-第三层管道式转换)
3. [使用场景与反模式](#3-使用场景与反模式)
4. [速查清单](#4-速查清单)

---

## 1. 为什么需要 Optional

### 问题起源

1965 年，计算机科学家 Tony Hoare 在设计 ALGOL 语言时引入了 **null 引用**。四十年后他公开道歉，称这是"十亿美元的错误"——因为 null 引发的 `NullPointerException`（NPE）是全球开发者最频繁遇到的运行时崩溃。

考虑一个常见场景：从数据库查出用户，取他的地址，再取地址中的城市名。

```java
public class UserService {

    private final UserRepository userRepo;

    public UserService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    // 不用 Optional，用防御式 null 检查
    public String getUserCity(Long userId) {
        User user = userRepo.findById(userId);          // 可能返回 null
        if (user == null) {
            return "未知";
        }
        Address address = user.getAddress();             // 可能返回 null
        if (address == null) {
            return "未知";
        }
        String city = address.getCity();                 // 可能返回 null
        if (city == null) {
            return "未知";
        }
        return city;
    }
}
```

**问题在哪里？**

- **金字塔式嵌套**：每多一层数据，就多一层 `if (xxx == null)`
- **默认值散落各处**：`"未知"` 写了三次，修改时要改多处
- **容易遗漏**：漏掉一个 null 检查，线上直接 NPE
- **业务逻辑被淹没**：真正有价值的代码是 `address.getCity()`，但视觉上全是 null 检查

```
代码分布

  │  ██          业务逻辑（1 行 getCity()）
  │  ████████    null 检查与默认值返回（10+ 行）
  │  ████████████ 外加丢失的检查 = 线上 NPE
  │
  └──────────────→ 维护成本
```

> 本质上，null 的问题不是"有值"或"没有值"，而是**编译器不知道它可能没有值**。你只能靠肉眼找、靠经验猜、靠线上崩溃提醒你。

### Optional 的解决方案

`Optional` 是一个**容器对象**——它要么装着一个非 null 的值，要么就是空的。核心思想就一句话：**"把 '可能没有值' 这个事实，写进类型签名里，让编译器帮你检查"**。

```java
// 老写法：返回值类型上看不出"可能没有"
User findById(Long id);           // 返回 User 或 null —— 纯靠文档/记忆

// Optional 写法：类型本身就说清楚了
Optional<User> findById(Long id); // 返回 Optional<User> —— 一看就知道可能没有
```

```java
import java.util.Optional;

// 用 Optional 改写上面的三层查询
public String getUserCity(Long userId) {
    return Optional.ofNullable(userRepo.findById(userId)) // 包装第一层
            .map(User::getAddress)                         // 取 address
            .map(Address::getCity)                         // 取 city
            .orElse("未知");                               // 任一环节为空 → 返回默认值
}
// 10+ 行 → 4 行，每个 ".map()" 等价于一层 null 检查
```

```
你写的（防御式 null 检查）           Optional 帮你做的
───────────────────────────          ──────────────────
if (user == null) return ...   ──→   Optional.ofNullable() 包装
if (address == null) return ... ──→   .map(User::getAddress) 自动跳过 null
if (city == null) return ...   ──→   .map(Address::getCity)
return city                    ──→   .orElse("未知") 统一兜底
```

> **关键进步**：不再是"到处写 if-null-return"，而是**一条链式调用**。空值在管道中自动短路——任何一个环节返回 null，整条链直接跳到兜底值。

---

## 2. 入门三步走

用一个贯穿场景来演示：**根据用户 ID 查用户 → 取用户地址 → 取城市名**。

> 前置知识：`Optional` 是 `java.util` 下的标准库类，无需任何第三方依赖。Spring Data JPA 的 `CrudRepository.findById()` 返回的就是 `Optional<T>`。

### 2.1 第一层：安全包装与解包

需求：调用 `findById()` 可能返回 null（老 API）或空结果，你需要安全地拿到值。

三个核心方法：

| 方法                  | 作用                        | 何时用               |
| --------------------- | --------------------------- | -------------------- |
| `Optional.ofNullable` | 把值包进容器（可以是 null） | 来源不确定（老 API） |
| `Optional.of`         | 把值包进容器（不能是 null） | 你 100% 确定有值     |
| `Optional.empty`      | 创建一个空容器              | 明确表示"没有"       |

```java
import java.util.Optional;

public class UserService {

    private final UserRepository userRepo;

    public UserService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    // MyBatis（3.5+）：Mapper 直接返回 Optional，和 Spring Data 一样方便
    public void printCityMyBatis(Long userId) {
        Optional<User> opt = userMapper.findById(userId); // 直接返回 Optional
        //          ↑ 查不到记录时返回 Optional.empty()，不会是 null

        if (opt.isPresent()) {                  // isPresent()：容器有值？
            User u = opt.get();                 // get()：拿出来
            System.out.println(u.getAddress().getCity());
        } else {
            System.out.println("未知");
        }
    }

    // Spring Data JPA / MongoDB：findById 直接返回 Optional
    public void printCitySpringData(Long userId) {
        Optional<User> opt = userRepo.findById(userId); // 已经是 Optional，无需手动包装

        if (opt.isPresent()) {
            System.out.println(opt.get().getAddress().getCity());
        } else {
            System.out.println("未知");
        }
    }
}
```

对应的 MyBatis Mapper 接口（3.5+ 起支持返回 `Optional`）：

```java
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {
    // 3.5+ 起支持：直接声明返回 Optional，MyBatis 自动包装
    @Select("SELECT * FROM users WHERE id = #{id}")
    Optional<User> findById(Long id);
}
```

**MyBatis vs Spring Data 的区别**：

```
框架                  findById 返回类型          需要手动包装？
──────────────────────────────────────────────────────────
MyBatis（3.5+）      Optional<User>             否，直接用
Spring Data JPA      Optional<User>             否，直接用
Spring Data MongoDB  Optional<User>             否，直接用
```

MyBatis 自 3.5 起支持 Mapper 方法直接声明返回 `Optional<User>`，MyBatis 自动将查询结果包装为 `Optional`（查不到记录时返回 `Optional.empty()`），体验和 Spring Data 完全一致。

```
工作流程

  findById(id)
       │
       ▼
  ┌──────────────┐
  │  Optional 容器 │
  └──┬───────────┘
     │
     ├── 有值（user 存在）  →  isPresent() = true   →  get() 拿值
     │
     └── 空值（user 不存在）→  isPresent() = false  →  走 else 分支
```

> **关键进步**：`Optional.ofNullable(null)` 不会崩——它只是创建一个空容器。你把"可能崩的操作"变成了"安全取值的操作"。但 `Optional.of(null)` 会立即抛 NPE（这在确定有值时反而是好事——越早崩越好定位）。

#### 本节回顾

```
第一层核心

Optional.ofNullable(x)   →   不确定 x 是不是 null 时用
opt.isPresent()         →   容器有值吗？
opt.get()               →   把值拿出来（空时调 get() 抛 NoSuchElementException）

isPresent + get 是基础，但还不够简洁——下一层让你忘掉 if-else
```

---

### 2.2 第二层：有值则用，无值则兜底

上一层的 `isPresent() + get()` 只是把 null 检查换了个写法，if-else 还在。`Optional` 提供了直接从容器中提取值的方法——不需要手动检查。

```java
import java.util.Optional;

public class UserService {

    private final UserRepository userRepo;

    public UserService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    // orElse：有值则返回，无值则返回参数中的默认值
    public String getCityOrDefault1(Long userId) {
        return userRepo.findById(userId)                // Optional<User>（第一层已讲）
                .map(User::getAddress)                  // 下一层才讲，先放这里
                .map(Address::getCity)
                .orElse("未知");
        //     └── 注意：无论容器是否有值，"未知" 都会被创建！
    }

    // orElseGet：有值则返回，无值则调用函数生成默认值（惰性求值）
    public String getCityOrDefault2(Long userId) {
        return userRepo.findById(userId)                // Optional<User>
                .map(User::getAddress)
                .map(Address::getCity)
                .orElseGet(() -> fetchDefaultCity());   // 只有容器为空时才执行
    }

    // orElseThrow：有值则返回，无值则抛指定异常
    public String getCityOrThrow(Long userId) {
        return userRepo.findById(userId)                // Optional<User>
                .map(User::getAddress)
                .map(Address::getCity)
                .orElseThrow(() -> new RuntimeException("用户或地址信息不完整: " + userId));
        //                    ↑ 传入异常工厂，空时才创建异常对象
    }

    private String fetchDefaultCity() {
        return "默认城市";
    }
}
```

**orElse vs orElseGet 的区别（重要！）**

```java
// orElse 的参数值会立即求值——即使容器中有值，logDefaultCity() 也会执行！
public String badExample(Optional<String> opt) {
    return opt.orElse(logDefaultCity());
    //     └── logDefaultCity() 在这里就执行了！无论 opt 有没有值！
}

// orElseGet 的参数是惰性的——只有容器为空时才执行
public String goodExample(Optional<String> opt) {
    return opt.orElseGet(() -> logDefaultCity());
    //     └── logDefaultCity() 只在 opt 为空时才执行
}
```

```
决策

有默认值且"很便宜"（常量、字面量）？          →  orElse("未知")
默认值需要计算（查库、调接口、拼接字符串）？  →  orElseGet(() -> ...)
没有值就应该报错？                           →  orElseThrow(() -> new ...)
```

> **orElse 的坑**：`orElse()` 的参数在方法调用前就求值了。如果参数中调用了有副作用的方法（写日志、修改状态），即使 Optional 有值，副作用也会发生。**默认值需要"算出来"时，一律用 `orElseGet`**。

#### 本节回顾

```
第二层核心

orElse(T)        →   无论容器有没有值，参数都会立即求值（常量场景）
orElseGet(()→T)  →   空时才调用函数求值（需计算场景）
orElseThrow(()->)→   空时抛异常（不应为空场景）

第一层还要 if-else → 第二层消除了 if-else，一行搞定默认值或异常
```

---

### 2.3 第三层：管道式转换

前两层解决了"怎么拿值"和"没有怎么办"。但真实场景中，你拿到的 User 里还有 Address，Address 里还有 City——你需要**在 Optional 内部的管道中层层转换**。

```java
import java.util.Optional;

public class UserService {

    private final UserRepository userRepo;

    public UserService(UserRepository userRepo) {
        this.userRepo = userRepo;
    }

    // map：对容器内的值做转换，返回新容器
    // Optional<User>  →  Optional<Address>  →  Optional<String>
    public String getCityChained(Long userId) {
        return userRepo.findById(userId)          // Optional<User>
                .map(User::getAddress)            // Optional<Address>， user 为 null → 跳过
                .map(Address::getCity)            // Optional<String>， address 为 null → 跳过
                .orElse("未知");                  // 任一环节为空 → 返回默认值
    }
    // 等价于三层 if-null-return，但不写一个 if
}
```

```
管道短路示意

  findById(id) → Optional<User>
         │
         ▼
    有值？ ──否──┐
         │      │
        是       │
         │      │
         ▼      │
  map(User::getAddress) → Optional<Address>
         │      │
         ▼      │
    有值？ ──否──┤
         │      │
        是       │
         │      │
         ▼      │
  map(Address::getCity) → Optional<String>
         │      │
         ▼      ▼
  orElse("未知") ← 短路跳到这里

  任一环节返回 null → 整条链直接跳到 orElse
```

#### flatMap：当转换函数本身返回 Optional

`map` 的转换函数返回的是普通值（`User → Address`），`map` 把它包成 `Optional<Address>`。但如果转换函数本身返回 `Optional`（比如 `findByEmail` 返回 `Optional<User>`），`map` 会再包一层 → `Optional<Optional<User>>`，嵌套了。

**等等，`findById` 也返回 `Optional<User>`，为什么上面的 `getCityChained` 没有嵌套？**

关键区别：`findById` 是链条的**起点**，不在 `map` 里面；`findByEmail` 是 `map` **内部的转换函数**，会被 `map` 再包一层。

```
findById — 链条起点（不经过 map 包装）：

  userRepo.findById(userId)          // 直接返回 Optional<User>，这就是链条类型
          .map(User::getAddress)     // map 内部是 getAddress()，返回 Address（普通对象）
                                   // map 把 Address 包成 Optional<Address> → 正常

findByEmail — map 内部的转换函数（会被 map 再包一层）：

  Optional.ofNullable(email)         // 起点：Optional<String>
          .map(this::findByEmail)    // findByEmail 返回 Optional<User>
                                   // map 又包一层 → Optional<Optional<User>>  ← 嵌套了！
```

`map` 的规则：**把转换函数的返回值 R 包装成 `Optional<R>`**。

- R 是普通对象（`Address`）→ `Optional<Address>` ← 正常
- R 本身就是 `Optional<User>` → `Optional<Optional<User>>` ← 嵌套

`flatMap` 就是来解决这个问题的：转换函数已经返回 `Optional<R>`，`flatMap` 直接用，不再包。

```java
import java.util.Optional;

public class UserService {

    // 用 map 的翻车现场：得到 Optional<Optional<User>>，丑陋！
    public Optional<String> getCityByEmailBad(String email) {
        return Optional.ofNullable(email)                    // Optional<String>
                .map(this::findByEmail)                       // Optional<Optional<User>>  ← 嵌套了！
                .map(optUser -> optUser.map(User::getAddress)) // 需要手动解包，恶心
                .map(optAddr -> optAddr.map(Address::getCity));
    }

    // 用 flatMap：自动"拍平"，和上面的三层 map 一样流畅
    public Optional<String> getCityByEmailGood(String email) {
        return Optional.ofNullable(email)                    // Optional<String>
                .flatMap(this::findByEmail)                   // Optional<User>         ← 拍平了！
                .map(User::getAddress)                        // Optional<Address>
                .map(Address::getCity);                       // Optional<String>
    }

    // 假设的查找方法
    private Optional<User> findByEmail(String email) {
        // 实际可能是查数据库，这里简化
        return email.contains("@") ?
                Optional.of(new User()) :
                Optional.empty();
    }
}
```

```
map vs flatMap

              map                          flatMap
              ───                          ───────
转换函数返回   普通值（T → R）              Optional（T → Optional<R>）
结果类型      Optional<R>                  Optional<R>（自动解掉一层 Optional）
典型场景      getAddress()、getCity()      findByEmail()（返回 Optional 的方法）

注意：findById() 作为链条起点时不需要 flatMap（它不在 map/flatMap 内部）；
只有当它作为链式调用内部的转换函数时才需要 flatMap。

类比：
  map      = 普通转换（User → Address）
  flatMap  = "拍平"转换（String → Optional<User> → User）
```

> **记忆口诀**：`map` 不改变嵌套层数，`flatMap` 自动解掉一层 Optional。当你的转换函数签名有 `Optional` 返回类型时，用 `flatMap`。

#### filter：条件过滤

`filter` 在管道中加一个"关卡"——条件不满足时，把容器置空，后续链条短路。

```java
public Optional<String> getCityIfActive(Long userId) {
    return userRepo.findById(userId)
            .filter(User::isActive)           // 用户不活跃 → 变成空容器，后续全跳过
            .map(User::getAddress)
            .map(Address::getCity);
}
// 用户 inactive → Optional.empty()
// 用户 active、但没地址 → Optional.empty()
// 全部通过 → Optional.of(city)
```

#### 本节回顾

```
第三层核心

map(Function)       →   转换容器内的值（T → R），null 自动短路
flatMap(Function)   →   转换返回 Optional 的方法（T → Optional<R>），自动拍平
filter(Predicate)   →   条件不满足 → 容器变空，后续链条短路

三者可以任意组合：
  Optional.ofNullable(x)
      .filter(条件)
      .map(转换)
      .flatMap(可能为空的操作)
      .orElse(兜底)

实现了 "多层 null 检查" → "一条链式管道"
```

---

## 3. 使用场景与反模式

Optional 虽好，但用错地方反而更糟。记住一条核心原则：**Optional 是为方法返回值设计的，不是为一切"可能为空"的东西设计的**。

### 3.1 应该用 Optional 的地方

```
✅ 方法的返回值（最主要、最正确的用法）
────────────────────────────────────────
  Optional<User> findById(Long id)      // 数据库可能查不到
  Optional<String> findByEmail(...)     // 邮箱可能没注册

  类型签名本身就说清楚了："调用者注意，结果可能不存在"
```

### 3.2 不应该用 Optional 的地方

#### ❌ 作为类的字段

```java
// 错误 ❌：Optional 做字段
public class User {
    private Optional<String> nickname;  // 不要这样做
    // 问题 1：Optional 不可序列化（没实现 Serializable）
    // 问题 2：字段本来就可以是 null，没必要再包一层
    // 问题 3：JPA/Hibernate 不支持 Optional 字段映射
}

// 正确 ✅：字段就用普通类型
public class User {
    private String nickname;            // null 就 null，字段层面 null 是正常的
    // 返回值时才用 Optional 包装
    public Optional<String> getNickname() {
        return Optional.ofNullable(nickname);
    }
}
```

#### ❌ 作为方法参数

Optional 的设计初衷是方法返回值，不是参数。当参数"可选"时，Java 的惯用做法是**方法重载**——用一个无参重载表达"不传这个参数"，用一个有参重载表达"传了这个参数"。完全不使用 Optional。

```java
// 错误 ❌：Optional 做参数
public void updateUser(Long id, Optional<String> newName) {
    // 调用方被迫这样写：
    //   updateUser(1L, Optional.of("张三"))    ← 啰嗦
    //   updateUser(1L, Optional.empty())       ← 啰嗦
    // 有人可能会传 null——Optional 本身也是 null！
}

// 正确 ✅：用方法重载，不使用 Optional
// 重载一：不传 name → 不更新名字
public void updateUser(Long id) {
    User user = userRepo.findById(id).orElseThrow();
    userRepo.save(user);                    // 只更新其他字段，不碰 name
}

// 重载二：传了 name → 更新名字
public void updateUser(Long id, String newName) {
    User user = userRepo.findById(id).orElseThrow();
    user.setName(newName);                  // newName 一定非 null，语义明确
    userRepo.save(user);
}

// 调用方：
//   updateUser(1L)              ← 不更新名字
//   updateUser(1L, "张三")       ← 更新名字为 "张三"
//   比 Optional.of("张三") 简洁得多
```

两个重载各有明确语义：无参版本 = "不改名字"，有参版本 = "改成新名字"。不需要 Optional，也不需要传 null。

#### ❌ 用于集合

```java
// 错误 ❌：Optional 包装集合
public Optional<List<User>> findAll() {
    // 没查到用户 → 返回 Optional.empty()
    // 查到了 → 返回 Optional.of(列表)
}
// 问题：空集合（new ArrayList<>()）本身就是"没有结果"的明确表达

// 正确 ✅：直接返回空集合
public List<User> findAll() {
    List<User> users = userRepo.findAll();
    return users != null ? users : Collections.emptyList();
    // 调用方直接 for (User u : service.findAll()) —— 不会 NPE
}
```

#### ❌ 调用 get() 不加 isPresent() 检查

```java
// 危险 ❌：直接 get()，空时抛 NoSuchElementException
Optional<User> opt = userRepo.findById(id);
User user = opt.get();  // 如果查不到 → NoSuchElementException，和 NPE 一样糟！

// 安全 ✅：用 orElse / orElseThrow 明确意图
User user = userRepo.findById(id).orElseThrow(() -> new NotFoundException("用户不存在"));
```

```
Optional 反模式速记

  ✅ 返回值                       ❌ 字段
  ✅ 链式调用末端 orElse...        ❌ 方法参数
  ✅ Stream 中的 findFirst()       ❌ 集合的包装类型
  ✅ 明确"可能没有"的语义           ❌ 直接 get() 不检查
```

### 3.3 Optional 与 null 的关系

**引入 Optional 不是为了消灭 null——Java 中 null 永远不会消失。** Optional 的定位是：

```
null 的职责               Optional 的职责
─────────────────        ─────────────────
字段可以为 null           方法返回值用 Optional
内部实现可以 return null   对外 API 用 Optional 表达"可能没有"
参数可以传 null           让调用方明确知道需要处理空的情况

共存原则：内部可以 null，对外用 Optional
```

---

## 4. 速查清单

### 4.1 创建 Optional

```
方法                        用法                              适用场景
════════════════════════════════════════════════════════════════════════
Optional.ofNullable(x)     把可能为 null 的 x 包进容器         最常用，不确定 x 是否为 null
Optional.of(x)             把 x 包进容器（x 不能是 null）      你 100% 确定有值
Optional.empty()           直接创建一个空容器                  明确表示"没有"
```

### 4.2 提取值

```
方法                        有值时                   无值时                    适用场景
═══════════════════════════════════════════════════════════════════════════════
get()                       返回值                  抛 NoSuchElementException  不推荐单独使用
orElse(T)                   返回值                  返回参数 T（立即求值）      默认值很便宜（常量）
orElseGet(Supplier)         返回值                  调用函数生成默认值（惰性）  默认值需计算（查库等）
orElseThrow(Supplier)       返回值                  抛指定异常                 空即异常
ifPresent(Consumer)         执行 Consumer          什么都不做                  有值时执行副作用
ifPresentOrElse(...)        执行 Consumer          执行 Runnable              分支处理（Java 9+）
```

### 4.3 转换与过滤

```
方法                        有值时                   无值时                  作用
═══════════════════════════════════════════════════════════════════════════════
map(Function)               转换后返回新 Optional   返回 empty              转换容器内的值
flatMap(Function)           转换并拍平新 Optional   返回 empty              转换函数返回 Optional 时用
filter(Predicate)           通过则不变，不通过变空   返回 empty              条件过滤
```

### 4.4 反模式速查

```
❌ 错误写法                                     ✅ 正确写法                                    原因
════════════════════════════════════════════════════════════════════════════════════════════════════
class User { Optional<String> name; }          class User { String name; }                   字段可为 null，不需要包
                                                                                             Optional 不可序列化
void save(Optional<User> user)                 void save(User user)                         参数用 Optional 难看
                                                                                             可能传 null 更糟
Optional<List<User>> findAll()                 List<User> findAll()                         空集合就是"无结果"
opt.get()（无检查）                              opt.orElseThrow(...)                         get() 空时也抛异常
opt.orElse(expensive())                        opt.orElseGet(() -> expensive())             orElse 参数立即求值
```

### 4.5 决策流程

```
拿到一个"可能为空"的值
    │
    ├── 它是方法返回值？ ──── 是 ────→ 用 Optional<T> 做返回类型
    │       │
    │       ├── 内部已有 Optional？ ──→ 直接用
    │       └── 内部是普通值？ ──→ Optional.ofNullable() 包装
    │
    ├── 它是字段？ ────→ 用普通类型（String、Integer...），不用 Optional
    │
    ├── 它是方法参数？ ──→ 用普通类型 + 方法重载，不用 Optional
    │
    └── 它是集合？ ────→ 返回空集合，不用 Optional 包装

提取值时：
    │
    ├── 有默认常量？ ────→ .orElse("默认")
    ├── 默认值需计算？ ──→ .orElseGet(() -> ...)
    └── 空即异常？ ────→ .orElseThrow(() -> new ...)

需要转换时：
    │
    ├── 转换返回普通值？ ──→ .map(...)
    └── 转换返回 Optional？ ──→ .flatMap(...)
```

---

**最后：** Optional 的核心就一句话——**把"可能没有"写进类型签名**。当方法返回 `Optional<User>` 而不是 `User` 时，调用者不看文档就知道需要处理空的情况。它不是 null 的替代品，而是你**对调用者的承诺**——"我把空值情况明确告诉你了，请妥善处理"。
