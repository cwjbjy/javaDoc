# fastjson2 JSON 处理指南

> 本指南面向需要在 Java 项目中使用 fastjson2 处理 JSON 的开发者，覆盖日常开发中最常用的 API 与行为要点。
> 适用版本：`com.alibaba.fastjson2:fastjson2:2.0.65`（2.x 系列，活跃维护中）。
> 本文与 [jackson-guide.md](jackson-guide.md) 并列；两者风格对比见第 1 章。
> 示例状态：除特别说明外，代码块均为说明 API 的片段，不是可独立运行的完整程序。

---

## 目录

1. [fastjson2 是什么](#1-fastjson2-是什么)
2. [版本与依赖](#2-版本与依赖)
3. [JSON：核心入口](#3-json核心入口)
4. [JSONObject：Map 风格的取值方法](#4-jsonobjectmap-风格的取值方法)
5. [JSONArray：以 List 视角操作 JSON 数组](#5-jsonarray以-list-视角操作-json-数组)
6. [TypeReference：泛型反序列化](#6-typereference泛型反序列化)
7. [@JSONField：字段名映射](#7-jsonfield字段名映射)
8. [特性开关：JSONReader.Feature 与 JSONWriter.Feature](#8-特性开关jsonreaderfeature-与-jsonwriterfeature)
9. [深拷贝惯用法](#9-深拷贝惯用法)
10. [安全：AutoType 默认安全（简要）](#10-安全autotype-默认安全简要)

---

## 1. fastjson2 是什么

**fastjson2** 是阿里巴巴开源的 Java JSON 处理库，是 fastjson 1.x 的重写升级版，在**国内系统中广泛使用**。它与 Jackson 做同一件事——Java 对象与 JSON 字符串互转——但 API 风格截然不同：

| 维度          | fastjson2                                                  | Jackson（见 [jackson-guide.md](jackson-guide.md)） |
| ------------- | ---------------------------------------------------------- | -------------------------------------------------- |
| 核心入口      | `JSON` 静态方法 + `JSONObject`/`JSONArray`                 | `ObjectMapper` 实例                                |
| 中间结构      | `JSONObject`（本质是 `Map`）、`JSONArray`（本质是 `List`） | `JsonNode` 树                                      |
| 泛型反序列化  | `TypeReference` 匿名子类                                   | `TypeReference`（同理）                            |
| 字段映射      | `@JSONField(name=...)`                                     | `@JsonProperty`                                    |
| null 默认行为 | Bean 属性和 Map entry 的 null 值默认都不输出               | 取决于 `ObjectMapper`/Spring Boot 的包含策略       |

**一句话心智模型：** fastjson2 的一切 API 围绕三个类展开——`JSON` 负责转换，`JSONObject`/`JSONArray` 负责以 Map/List 的视角读写 JSON 结构。读懂 fastjson2 的用法，就是识别这三者各司其职。

### 1.1 版本格局：fastjson2 是当前主线

```
                    fastjson 版本格局
                    ═══════════════════════════════════════════

  1.x 系列（2011 ~ 2022）                2.x 系列（2022 ~ 现在）
  ┌─────────────────────────┐          ┌─────────────────────────┐
  │  1.2.24 ... 1.2.83      │          │  fastjson2 (2.0.x)      │
  │  · 1.2.83 = 1.x 终点    │  本指南   │  · 官方维护的主线版本   │
  │  · 仓库已归档只读       │  覆盖───▶ │  · 包名 com.alibaba.    │
  │  · CVE-2026-16723 无补丁│          │    fastjson2（与1.x共存）│
  └─────────────────────────┘          └─────────────────────────┘
```

- fastjson 1.x 的 GitHub 仓库已被官方**归档（read-only）**，不再维护。2026 年 7 月披露的 CVE-2026-16723（影响 1.2.68 ~ 1.2.83，RCE，CRITICAL）**没有补丁**；fastjson2 不受此漏洞影响。
- **新项目应直接使用 fastjson2**；fastjson2 与 1.x 包名不同，可以在同一项目中共存。
- 官方文档：<https://alibaba.github.io/fastjson2/>。

---

## 2. 版本与依赖

Maven 坐标：

```xml
<dependency>
    <groupId>com.alibaba.fastjson2</groupId>
    <artifactId>fastjson2</artifactId>
    <version>2.0.65</version>
</dependency>
```

要点：

- fastjson2 的核心 JSON 功能是单 jar，没有必须随应用部署的传递依赖；支持 JDK 8 及以上。
- 所有类的包名都以 `com.alibaba.fastjson2` 开头：`JSON`、`JSONObject`、`JSONArray`、`TypeReference` 在 `com.alibaba.fastjson2` 下，注解在 `com.alibaba.fastjson2.annotation` 下。
- 本文所有示例均以 fastjson2 2.0.65 的 API 为准；特性开关（第 8 章）在不同 2.0.x 版本间可能有新增，以官方文档为准。

---

## 3. JSON：核心入口

`com.alibaba.fastjson2.JSON` 是**纯静态方法的工具门面**，覆盖三个方向：

```
                 toJSONString(value)                  parseObject(json, Class<T>)
 任意可 JSON 化的值 ─────────────────▶   JSON 字符串   ─────────────────▶  指定类型 T
 (Bean / Map / List /                          │
  JSONObject / JSONArray)                      │ parseObject(json)
        │                                      │ parseArray(json)
        │ toJSON(value)                        ▼
        ▼                                JSONObject / JSONArray
 JSONObject / JSONArray                  （字符串 → 树）
 （对象 → 树）
```

注意图中左侧的输入是**任意可 JSON 化的值**，不只是 Java Bean：`toJSONString` 同样接受 `Map`、`List`、`JSONObject`、`JSONArray`——后两者本身就是 `Map`/`List` 的实现（见第 4、5 章），序列化它们与序列化普通集合没有区别。

> 统一使用 `JSON.toJSONString(...)`。fastjson 1.x 里常见的 `JSONObject.toJSONString(...)` 是继承自 `JSON` 的静态写法；fastjson2 的 `JSONObject` 改为继承 `LinkedHashMap`（不再继承 `JSON`），该静态写法已不存在。

### 3.1 值 → 字符串：toJSONString

```java
String json = JSON.toJSONString(obj);                        // 最常用
String json = JSON.toJSONString(obj, JSONWriter.Feature.WriteNulls); // 带特性开关，见第 8 章
```

典型场景——把入参列表序列化为 HTTP 请求体：

```java
List<String> toolNames = List.of("query_stock", "query_bond");
String requestBody = JSON.toJSONString(toolNames);
// requestBody = ["query_stock","query_bond"]，可直接作为 POST 请求体发送
```

### 3.2 字符串 → 对象：parseObject

```java
MyBean bean = JSON.parseObject(json, MyBean.class);   // 字符串 → 指定类型
JSONObject obj = JSON.parseObject(json);              // 字符串 → 通用树（JSONObject）
```

典型场景——HTTP 响应体转通用树，必要时再转回字符串：

```java
JSONObject response = JSON.parseObject(responseBody);
return response.toString();   // JSONObject 的 toString() 输出 JSON 文本
```

### 3.3 对象 → 树：toJSON（容易混淆的一对）

`toJSON` 与 `toJSONString` 只差一个 "String"，但结果类型完全不同：

```java
String jsonString = JSON.toJSONString(bean);              // 结果是 String，可直接网络传输
JSONObject tree     = (JSONObject) JSON.toJSON(bean);     // 结果是树结构，可继续 getXxx 取值
```

`JSON.toJSON(obj)` 返回该对象的 JSON 树表示：普通 Bean、Map 和集合通常会构建新的 `JSONObject`/`JSONArray`，**不会修改原对象**；若传入值本来就是 `JSONObject`/`JSONArray`，会直接返回原值。常见用法是把列表转成 `JSONArray` 树：`(JSONArray) JSON.toJSON(list)`。

### 3.4 列表反序列化：parseArray

```java
List<MyBean> list = JSON.parseArray(json, MyBean.class);   // 字符串 → List<指定类型>
JSONArray arr = JSON.parseArray(json);                     // 字符串 → 通用数组树
```

典型场景——处理 `{"code":0,"data":[...]}` 风格的响应信封：

```java
JSONObject envelope = JSON.parseObject(resultStr);
List<ToolInfo> tools = envelope.getJSONArray("data").toJavaList(ToolInfo.class);
```

这里展示了一个"以树取树"的链路：`getJSONArray` 取出 `data` 数组树 → `toJavaList` 把树中的每个元素按目标类型转换，得到强类型 `List`，省掉字符串往返。`toJavaList` 的详细说明见 [5.3 节](#53-批量类型转换tojavalist)。

---

## 4. JSONObject：Map 风格的取值方法

```java
public class JSONObject extends LinkedHashMap<String, Object> implements ... {}
```

**JSONObject 本质上就是一个 `Map<String, Object>`**。所有 Map 的操作它都支持，并额外提供了一组类型化的取值方法。这是 fastjson2 与 Jackson 最根本的差异：Jackson 用 `JsonNode` 树，fastjson2 直接给你一个 Map。

### 4.1 常用取值方法

| 方法                                     | 返回类型   | 说明                             |
| ---------------------------------------- | ---------- | -------------------------------- |
| `getString(key)`                         | String     | 取字符串值，key 不存在返回 null  |
| `getIntValue(key)` / `getLongValue(key)` | 基本类型   | 取数值（不存在返回 0，注意区分） |
| `getBooleanValue(key)`                   | boolean    | 取布尔值                         |
| `getJSONObject(key)`                     | JSONObject | 取嵌套对象                       |
| `getJSONArray(key)`                      | JSONArray  | 取嵌套数组                       |
| `containsKey(key)`                       | boolean    | 判断 key 是否存在（Map 方法）    |
| `put(key, value)` / `remove(key)`        | —          | 写入 / 删除（Map 方法）          |

### 4.2 嵌套取值

判断响应是否携带 `data` 字段并取出：

```java
JSONObject response = JSON.parseObject(res);
if (response.containsKey("data")) {
    JSONObject data = response.getJSONObject("data");
    String name = data.getString("name");   // 继续按 key 取值
}
```

把配置字符串解析成 JSONObject 后按 key 取数组也很常见：

```java
JSONObject config = JSON.parseObject(configStr);
JSONArray rules = config.getJSONArray("rules");
```

### 4.3 两个行为要点

**要点 1：默认保序。** `JSONObject` 继承 `LinkedHashMap`，内部保持插入顺序；解析时字段顺序与 JSON 原文一致，`toString()` 输出顺序可控。这是与 fastjson 1.x 的重要差异（1.x 默认 `HashMap` 无序，需要 `Feature.OrderedField`；fastjson2 无需任何配置）。

**要点 2：null 值默认不输出。** 无论普通 Bean 属性还是 `Map`/`JSONObject` entry，值为 null 时默认都会从序列化结果中省略；需要保留时使用 `JSONWriter.Feature.WriteNulls` / `WriteMapNullValue`，见[第 8.1 节](#81-保留-null-值writenulls-与-writemapnullvalue)。

---

## 5. JSONArray：以 List 视角操作 JSON 数组

```java
public class JSONArray extends ArrayList<Object> implements ... {}
```

**JSONArray 本质上就是一个 `List<Object>`**，可以遍历、按下标取值，同时提供类型化的批量转换。

### 5.1 两种 parseArray

```java
JSONArray arr = JSONArray.parseArray(json);                   // ① 无类型：返回 JSONArray 树
List<MyBean> list = JSONArray.parseArray(json, MyBean.class); // ② 带类型：直接得到 List<T>
```

形式 ① 有一个常见的"字符串中转"惯用法——先把 `List<Bean>` 序列化成字符串，再解析成 `JSONArray` 树，以便后续做树形裁剪/字段挑选：

```java
JSONArray arr = JSONArray.parseArray(JSON.toJSONString(list));
// 先 toJSONString 把 List<对象> 变成 JSON 数组字符串，
// 再 parseArray 解析成 JSONArray 树 —— 因为后续要做树形裁剪/字段挑选
```

### 5.2 遍历与元素访问

```java
JSONArray arr = JSONArray.parseArray(json);
for (int i = 0; i < arr.size(); i++) {
    JSONObject item = arr.getJSONObject(i);   // 第 i 个元素按对象取
}
```

字符串数组可以直接反序列化为 `List<String>`：

```java
List<String> codes = JSONArray.parseArray("[\"310000\",\"320000\"]", String.class);
```

> `parseArray(json, String.class)` 反序列化 `["310000","320000"]` 这样的字符串数组，得到 `List<String>`。注意：**不要**用 `List<String>.class` 这种写法（编译不通过），泛型反序列化见下一章。

### 5.3 批量类型转换：toJavaList

当已经拿到 `JSONArray` 树（比如从 `JSONObject.getJSONArray` 取出），需要把它转成强类型 `List<T>` 时，用 `toJavaList`：

```java
JSONArray arr = envelope.getJSONArray("data");     // 先取出数组树
List<ToolInfo> tools = arr.toJavaList(ToolInfo.class); // 再转成强类型 List
```

对比 `parseArray(String, Class)` 是从**字符串**解析，`toJavaList` 是从**树**转换——省掉一次序列化往返。两者目标相同（得到 `List<T>`），但入口不同：

| 场景              | 写法                                    |
| ----------------- | --------------------------------------- |
| 已有 JSON 字符串  | `JSON.parseArray(json, ToolInfo.class)` |
| 已有 JSONArray 树 | `arr.toJavaList(ToolInfo.class)`        |

---

## 6. TypeReference：泛型反序列化

### 6.1 问题：类型擦除

Java 泛型在运行时会擦除。`JSON.parseObject(json, List.class)` 只能得到 `List`（元素是 `JSONObject`），无法一步得到 `List<MyBean>`。`List<MyBean>.class` 这种语法又不存在。

### 6.2 方案：匿名子类携带泛型信息

```java
List<MyBean> list = JSON.parseObject(json, new TypeReference<List<MyBean>>() {});
```

`com.alibaba.fastjson2.TypeReference` 利用匿名子类保留父类的泛型实参（通过反射读取），从而让 fastjson2 在反序列化时知道目标类型是 `List<MyBean>` 而不是裸 `List`。

> **注意：** 对于简单的 `List<MyBean>`，直接用 `JSON.parseArray(json, MyBean.class)` 更方便（见 5.1 节）。`TypeReference` 主要用于 `parseArray` 搞不定的**嵌套泛型**，如 `List<Map<String, List<String>>>`，见下节。

### 6.3 复杂泛型示例

嵌套泛型——`List<Map<String, List<String>>>`：

```java
List<Map<String, List<String>>> data = JSON.parseObject(
        json,
        new TypeReference<List<Map<String, List<String>>>>() {});
```

Map 作为外层——`Map<String, List<String>>`：

```java
Map<String, List<String>> regionMap = JSON.parseObject(
        json,
        new TypeReference<Map<String, List<String>>>() {});
```

> 观察一个细节：`JSON.parseObject` 接受 `TypeReference` 作为第二个参数，而 `JSONArray.parseArray` 的带类型重载只接受 `Class`。因此"带泛型的列表"通常走 `JSON.parseObject(json, new TypeReference<...>(){})` 这一路。

---

## 7. @JSONField：字段名映射

`com.alibaba.fastjson2.annotation.JSONField` 标注在字段或 getter 上，控制序列化/反序列化行为。

### 7.1 name：最常用的属性

上游接口返回的 JSON 用 `company_code` 这类下划线命名，Java 字段按驼峰命名。`name` 声明二者的映射关系，**序列化和反序列化双向生效**：

```java
import com.alibaba.fastjson2.annotation.JSONField;

@JSONField(name = "company_code")
private String companyCode;
```

> fastjson2 默认按字段名**精确匹配**（1.x 默认开启的驼峰/下划线"智能匹配"在 2.x 中默认关闭）。如果确实需要兼容多种命名风格且不想逐个声明 `name`，可开启 `JSONReader.Feature.SupportSmartMatch`，见第 8 章；但显式声明 `name` 始终是更清晰的做法。

### 7.2 其他常用属性速查

| 属性                        | 作用                                 |
| --------------------------- | ------------------------------------ |
| `name`                      | JSON 中的字段名                      |
| `serialize` / `deserialize` | 是否参与序列化 / 反序列化            |
| `format`                    | 日期格式，如 `"yyyy-MM-dd HH:mm:ss"` |
| `ordinal`                   | 序列化时的字段顺序（数值小的在前）   |

> 注意：fastjson2 的 `@JSONField` 与 Jackson 的 `@JsonProperty` 互不识别，双库混用时要么各自声明、要么只让一个库接触该实体类。

---

## 8. 特性开关：JSONReader.Feature 与 JSONWriter.Feature

fastjson2 把"行为开关"拆成两个枚举，方向不同：

| 枚举                                       | 生效方向                  | 常见用法                                                              |
| ------------------------------------------ | ------------------------- | --------------------------------------------------------------------- |
| `com.alibaba.fastjson2.JSONReader.Feature` | **解析**（JSON → 对象）   | `JSON.parseObject(json, clazz, JSONReader.Feature.SupportSmartMatch)` |
| `com.alibaba.fastjson2.JSONWriter.Feature` | **序列化**（对象 → JSON） | `JSON.toJSONString(obj, JSONWriter.Feature.WriteNulls)`               |

官方原则：**fastjson2 中所有特性默认关闭**，需要时显式开启（fastjson 1.x 则有一批默认开启的特性）。多个 Feature 可以并列传入。

### 8.1 保留 null 值：WriteNulls 与 WriteMapNullValue

fastjson2 的默认行为是：

```
序列化普通 Java 对象  →  null 属性默认不输出
序列化 Map/JSONObject →  值为 null 的 entry 默认不输出
```

需要保留 null 时，按场景选择：

```java
JSON.toJSONString(bean, JSONWriter.Feature.WriteNulls);        // 输出对象的 null 字段
JSON.toJSONString(map,  JSONWriter.Feature.WriteMapNullValue); // 输出 Map 中的 null 值
```

典型场景是"序列化往返做克隆"：

```java
String json = JSON.toJSONString(config, JSONWriter.Feature.WriteNulls);
JSONObject dataClone = JSON.parseObject(json);
```

如果不加 `WriteNulls`，`config` 里值为 null 的 key 会在序列化时消失，往返后结构就变了。凡是**先转字符串再解析回来**的场景，都要检查是否需要这个开关。

### 8.2 常用 JSONWriter.Feature 速查

| 开关                     | 作用                                                          |
| ------------------------ | ------------------------------------------------------------- |
| `WriteNulls`             | 输出对象的 null 字段（见上）                                  |
| `WriteMapNullValue`      | 输出 Map 中的 null 值（见上）                                 |
| `WriteNullStringAsEmpty` | null 字符串输出为 `""`                                        |
| `PrettyFormat`           | 美化输出（带缩进换行，便于日志阅读）                          |
| `ReferenceDetection`     | 开启引用检测（fastjson2 默认关闭；开启后重复引用输出 `$ref`） |
| `WriteClassName`         | 输出 `@type` 类型信息（安全相关，见第 10 章）                 |
| `BrowserCompatible`      | 超出 JavaScript 安全整数范围的值输出为字符串                  |

### 8.3 常用 JSONReader.Feature 速查

| 开关                | 作用                                                                 |
| ------------------- | -------------------------------------------------------------------- |
| `SupportSmartMatch` | 智能识别 camel/upper/pascal/snake/kebab 五种命名风格（默认精确匹配） |
| `UseNativeObject`   | 解析结果用 `LinkedHashMap`/`ArrayList` 而非 `JSONObject`/`JSONArray` |
| `FieldBased`        | 基于字段（含 private）而非 getter 反序列化                           |
| `SupportAutoType`   | 开启 AutoType（官方已弃用，不推荐生产使用，见第 10 章）              |

---

## 9. 深拷贝惯用法

### 9.1 实现

利用序列化往返实现深拷贝的常见写法：

```java
/**
 * 使用对象的序列化进而实现深拷贝
 */
public static <T> T clone(T obj) {
    String str = JSON.toJSONString(obj);
    T cloneObj = (T) JSON.parseObject(str, obj.getClass());
    return cloneObj;
}
```

原理：`toJSONString` 先把**能表示为 JSON 的状态**写成字符串，`parseObject` 再按运行时类型重建对象。它常被当作便捷的"深拷贝"，但不是任意 Java 对象图的通用克隆：类型信息、引用关系、不可序列化状态和 null 字段都可能变化。

### 9.2 代价与边界

| 问题           | 说明                                                                                 |
| -------------- | ------------------------------------------------------------------------------------ |
| 性能           | 需要字符串序列化和反射重建；大对象或批量 clone 前应基准测试                          |
| 精度           | 小数默认按 `BigDecimal` 解析；按原字段类型反序列化通常可保留精度，跨类型转换仍需测试 |
| 构造器         | 普通 JavaBean 路径通常需要无参构造；显式 creator 等特殊反序列化路径另当别论          |
| 字段匹配       | 依赖字段名精确匹配（有 `@JSONField(name=...)` 时按映射名走），无匹配字段会静默丢弃   |
| null 字段      | 默认不输出，往返后丢失；需要保留时加 `JSONWriter.Feature.WriteNulls`（见 8.1 节）    |
| 不可序列化成员 | `static` 不属于实例状态；`transient`、内部类、代理类和自定义访问器的行为需单独验证   |

### 9.3 何时用

适合**一次性的配置对象快照**（clone 后修改不影响原对象，如第 8.1 节的 `dataClone`）。频繁深拷贝请考虑 MapStruct（见 [mapstruct-guide.md](mapstruct-guide.md)）或手写转换。

---

## 10. 安全：AutoType 默认安全（简要）

> 本章只给结论与配置片段。反序列化漏洞的完整机制不在本指南范围。

### 10.1 fastjson2 的安全设计

fastjson 1.x 曾因 **AutoType** 机制（反序列化时根据 JSON 里的 `@type` 选择具体类）被曝光多轮漏洞链，最终以 CVE-2026-16723（无补丁的 RCE）收场。fastjson2 重新设计了这套机制：

```
  fastjson 1.x                          fastjson 2.x
  ┌───────────────────────────────┐     ┌───────────────────────────────┐
  │ AutoType 历史上多次被绕过     │     │ AutoType 默认关闭             │
  │ 1.2.83 后无维护、无补丁       │ ──▶ │ 白名单优先设计（AutoType-     │
  │ CVE-2026-16723 影响 1.2.68~83 │     │ BeforeHandler/addAccept）     │
  └───────────────────────────────┘     │ 不受 CVE-2026-16723 影响     │
                                        └───────────────────────────────┘
```

**默认配置下 fastjson2 是安全的**：AutoType 关闭时，JSON 中的 `@type` 信息会被忽略，不会触发任意类加载。

### 10.2 审计入口

审计代码时至少搜索以下位置，确认没有显式放开 AutoType：

- `JSONReader.Feature.SupportAutoType`（官方已标记弃用，不推荐生产使用）
- `JSONFactory.getDefaultObjectReaderProvider().addAutoTypeAccept(...)`（白名单，应只放足够窄的包前缀，如自有 DTO 包）
- `JSONWriter.Feature.WriteClassName`（序列化结果中写出 `@type`）
- JVM 参数或配置文件中与 `fastjson2` 相关的 AutoType 配置

`WriteClassName` 本身只是"写出类型信息"，本身不产生漏洞：

```java
JSON.toJSONString(obj, JSONWriter.Feature.WriteClassName); // 会产生 {"@type":"com.xxx.MyBean",...}
```

但它与解析侧的 AutoType 白名单是配套关系：己方写出的 `@type`，解析侧必须有对应白名单才能读回。攻击者也可以直接在外部 JSON 中放入 `@type`——**解析侧的白名单范围才是真正的安全边界**。

### 10.3 safeMode：最硬的开关

如果需要彻底关闭 AutoType（连白名单也不生效），开启 safeMode：

```
-Dfastjson2.parser.safeMode=true    // JVM 启动参数
```

对解析不可信输入（外部 HTTP 请求、消息队列、第三方回调）的服务，建议叠加通用防护：限制输入大小、优先解析为显式 DTO 类型而非通用树。

---

## 参考资料

- [alibaba/fastjson2（官方）](https://github.com/alibaba/fastjson2)：fastjson2 源码与发布信息，当前主线版本 2.0.65。
- [fastjson2 官方文档](https://alibaba.github.io/fastjson2/)：API 介绍与使用指南。
- [fastjson2 Features 配置（官方）](https://alibaba.github.io/fastjson2/features_cn.html)：`JSONReader.Feature` / `JSONWriter.Feature` 完整清单及 1.x → 2.x 特性变更说明（本文第 8、10 章的 Feature 行为以此为准）。
- [alibaba/fastjson 1.x 仓库（官方，已归档）](https://github.com/alibaba/fastjson)：1.x 已停止维护，仓库说明建议升级 fastjson2。
- [CVE-2026-16723 PoC](https://github.com/EQSTLab/CVE-2026-16723)：fastjson 1.2.68~1.2.83 RCE 漏洞详情（fastjson2 不受影响）。
