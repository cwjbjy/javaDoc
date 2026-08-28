# fastjson 1.2.83 JSON 处理指南

> 本指南面向需要**读懂和维护使用 fastjson 1.2.83 的既有项目**的开发者，以参考项目 `finchina-data-mcp-server-ex`（`D:\dzh\finchina-data-mcp-server-ex`）的真实用法为主线。
> 适用版本：`com.alibaba:fastjson:1.2.83`（1.x 系列的最终版本）。
> 本文与 [jackson-guide.md](jackson-guide.md) 并列；本项目自身的 JSON 处理仍以 Jackson 3.1.2 为准，fastjson 仅用于理解外部参考项目。

---

## 目录

1. [fastjson 是什么](#1-fastjson-是什么)
2. [版本与依赖](#2-版本与依赖)
3. [JSON：核心入口](#3-json核心入口)
4. [JSONObject：以 Map 视角操作 JSON](#4-jsonobject以-map-视角操作-json)
5. [JSONArray：以 List 视角操作 JSON 数组](#5-jsonarray以-list-视角操作-json-数组)
6. [TypeReference：泛型反序列化](#6-typereference泛型反序列化)
7. [@JSONField：字段名映射](#7-jsonfield字段名映射)
8. [特性开关：Feature 与 SerializerFeature](#8-特性开关feature-与-serializerfeature)
9. [深拷贝惯用法](#9-深拷贝惯用法)
10. [与 Jackson 的边界](#10-与-jackson-的边界)
11. [安全：AutoType 与 safeMode（简要）](#11-安全autotype-与-safemode简要)
12. [速查清单](#12-速查清单)

---

## 1. fastjson 是什么

**fastjson** 是阿里巴巴开源的 Java JSON 处理库，在**国内遗留系统中广泛使用**。它与 Jackson 做同一件事——Java 对象与 JSON 字符串互转——但 API 风格截然不同：

| 维度          | fastjson 1.2.83                                            | Jackson（见 [jackson-guide.md](jackson-guide.md)） |
| ------------- | ---------------------------------------------------------- | -------------------------------------------------- |
| 核心入口      | `JSON` 静态方法 + `JSONObject`/`JSONArray`                 | `ObjectMapper` 实例                                |
| 中间结构      | `JSONObject`（本质是 `Map`）、`JSONArray`（本质是 `List`） | `JsonNode` 树                                      |
| 泛型反序列化  | `TypeReference` 匿名子类                                   | `TypeReference`（同理）                            |
| 字段映射      | `@JSONField(name=...)`                                     | `@JsonProperty`                                    |
| null 默认行为 | 对象 null 字段默认输出；Map 的 null 值默认**不**输出       | 默认都不输出                                       |

**一句话心智模型：** fastjson 的一切 API 围绕三个类展开——`JSON` 负责转换，`JSONObject`/`JSONArray` 负责以 Map/List 的视角读写 JSON 结构。读懂项目里的 fastjson 用法，就是识别这三者各司其职。

### 1.1 版本格局：1.2.83 是 1.x 的终点站

```
                    fastjson 版本格局
                    ═══════════════════════════════════════════

  1.x 系列（2011 ~ 2022）                2.x 系列（2022 ~ 现在）
  ┌─────────────────────────┐          ┌─────────────────────────┐
  │  1.2.24 ... 1.2.83      │          │  fastjson2 (2.0.x)      │
  │  · 1.2.83 = 1.x 终点    │  官方推荐 │  · 官方推荐迁移目标     │
  │  · 2022 年发布后停维护  │ ────────▶ │  · 性能/安全全面重写    │
  │  · AutoType 默认关闭    │  迁移路径  │  · 包名 com.alibaba.    │
  │  · 仍有已知绕过链研究   │          │    fastjson2（与1.x共存） │
  └─────────────────────────┘          └─────────────────────────┘
```

- fastjson 1.x 的 GitHub 仓库已于 2026 年 7 月被官方**归档（read-only）**，1.2.83 就是 1.x 的最终形态。
- **新项目不应再引入 1.2.83**，官方推荐 fastjson2；但大量遗留系统仍锁死在 1.2.83，这正是本指南存在的意义。
- 官方还提供特殊坐标 `com.alibaba:fastjson:1.2.83_noneautotype`：编译期彻底移除 AutoType 代码，见[第 11 章](#11-安全autotype-与-safemode简要)。

---

## 2. 版本与依赖

参考项目 `finchina-data-mcp-server-ex` 的 `pom.xml` 直接依赖 fastjson 1.2.83：

```xml
<dependency>
    <groupId>com.alibaba</groupId>
    <artifactId>fastjson</artifactId>
    <version>1.2.83</version>
</dependency>
```

要点：

- fastjson 1.2.83 是**零依赖**的单 jar（`fastjson-1.2.83.jar`），引入后即可使用，无需任何配置。
- 它同时支持 JDK 6~17，对老项目非常友好。
- 本指南所有实战代码均来自 `finchina-data-mcp-server-ex`，该项目同时混用 fastjson 与 Jackson（分工见[第 10 章](#10-与-jackson-的边界)）。

---

## 3. JSON：核心入口

`com.alibaba.fastjson.JSON` 是**纯静态方法的工具门面**，覆盖三个方向：

```
              toJSONString(obj)                  parseObject(json, Class)
 Java 对象  ───────────────────▶  JSON 字符串  ───────────────────▶  Java 对象
    │                                  │
    │  toJSON(obj)                     │  parseObject(json)
    ▼                                  ▼
 JSONObject / JSONArray           JSONObject / JSONArray
 （对象 → 树）                     （字符串 → 树）
```

### 3.1 对象 → 字符串：toJSONString

```java
String json = JSON.toJSONString(obj);          // 最常用
String json = JSON.toJSONString(obj, SerializerFeature.WriteMapNullValue); // 带特性开关，见第 8 章
```

参考项目实战（`MCPInfoUtils.java`，删节）：把入参列表序列化为请求体，用 `HttpClientUtil` 发 POST：

```java
String resultStr = HttpClientUtil.doPostJSONStr(tool_info_api, JSONObject.toJSONString(mcpNameList));
```

> `JSONObject.toJSONString(...)` 是继承自 `JSON` 的静态方法，与 `JSON.toJSONString(...)` **完全等价**。参考项目里两者混用，语义无差别。

### 3.2 字符串 → 对象：parseObject

```java
MyBean bean = JSON.parseObject(json, MyBean.class);      // 字符串 → 指定类型
JSONObject obj = JSONObject.parseObject(json);            // 字符串 → 通用树（JSONObject）
```

参考项目实战（`L2ToolDirectCallFilter.java`，删节）：HTTP 响应体转通用树：

```java
JSONObject rpcResponse = JSON.parseObject(responseBody);
return rpcResponse.toJSONString();
```

### 3.3 对象 → 树：toJSON（容易混淆的一对）

`toJSON` 与 `toJSONString` 只差一个 "String"，但结果类型完全不同：

```java
String jsonString = JSON.toJSONString(bean);   // 结果是 String，可直接网络传输
JSONObject tree    = (JSONObject) JSON.toJSON(bean);  // 结果是树结构，可继续 getXxx 取值
```

`JSON.toJSON(obj)` 把 Java 对象**原地转成树**（JSONObject/JSONArray），不经过字符串中转。当你拿到对象后想按 key 取值，用它比"转字符串再 parseObject"更直接。参考项目 `RedisUtils.java` 中就有 `JSONArray.toJSON(t)` 的用法（列表 → JSONArray 树）。

### 3.4 列表反序列化：parseArray

```java
List<MyBean> list = JSON.parseArray(json, MyBean.class);          // 字符串 → List<指定类型>
JSONArray arr = JSON.parseArray(json);                            // 字符串 → 通用数组树
```

参考项目实战（`MCPInfoUtils.java`，删节）：

```java
JSONObject resultJson = ConvertUtil.getSimpleJSONObjectWithNull2(resultStr);
List<McpToolInfoAgentLevel2AllBean> toolInfoBean =
        JSONObject.parseArray(resultJson.getString(Constance.DATA), McpToolInfoAgentLevel2AllBean.class);
```

这里展示了一个典型链路：`getString` 取出响应里 `data` 字段的字符串 → `parseArray` 一次性反序列化成强类型 `List`。

---

## 4. JSONObject：以 Map 视角操作 JSON

```java
public class JSONObject extends JSON implements Map<String, Object>, ... {}
```

**JSONObject 本质上就是一个 `Map<String, Object>`**。所有 Map 的操作它都支持，并额外提供了一组类型化的取值方法。这是 fastjson 与 Jackson 最根本的差异：Jackson 用 `JsonNode` 树，fastjson 直接给你一个 Map。

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

### 4.2 嵌套取值实战

参考项目 `MCPSpecificAspect.java`（删节）——判断响应是否携带 `data` 字段，并取出：

```java
if (StringUtils.isNotEmpty(res) && JSONObject.parseObject(res).containsKey(Constance.DATA)) {
    JSONObject data = JSONObject.parseObject(res).getJSONObject(Constance.DATA);
    // ... 后续用 data.getString(...) 等继续取值
}
```

参考项目 `CodeTypeEnum.java`（删节）——把配置字符串解析成 JSONObject 后按 key 取列表：

```java
JSONObject stockBondInfoJson = JSONObject.parseObject(stockBondInfoStr);
// ... stockBondInfoJson.getJSONArray(...) 取数组
```

### 4.3 两个易踩的坑

**坑 1：内部默认无序。** `JSONObject` 默认用 `HashMap` 存数据，`toString()` 输出的字段顺序不保证与 JSON 原文一致。需要保序时，在解析时指定 `Feature.OrderedField`，见[第 8.1 节](#81-featureorderedfield保持字段顺序)。

**坑 2：Map 的 null 值默认不输出。** 对 `JSONObject` 做 `toJSONString` 时，值为 null 的 entry 会被丢弃；需要保留时加 `SerializerFeature.WriteMapNullValue`，见[第 8.2 节](#82-serializerfeaturewritemapnullvalue保留-null-值)。

---

## 5. JSONArray：以 List 视角操作 JSON 数组

```java
public class JSONArray extends JSON implements List<Object>, ... {}
```

**JSONArray 本质上就是一个 `List<Object>`**，可以遍历、按下标取值，同时提供类型化的批量转换。

### 5.1 两种 parseArray

```java
JSONArray arr = JSONArray.parseArray(json);              // ① 无类型：返回 JSONArray 树
List<MyBean> list = JSONArray.parseArray(json, MyBean.class); // ② 带类型：直接得到 List<T>
```

参考项目里两种都有：第 3.4 节的例子是形式 ②；`BasicStatisticsService.java`（删节）则有形式 ① 的"字符串中转"惯用法：

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

参考项目 `BaseRegionFilterServiceImpl.java`（删节）中的用法：

```java
List<String> regionCodeList =
        JSONArray.parseArray(JSONObject.toJSONString(dataArr.get(dataArr.size()-1)), String.class);
```

> `parseArray(json, String.class)` 反序列化 `["310000","320000"]` 这样的字符串数组，得到 `List<String>`。注意：**不要**用 `List<String>.class` 这种写法（编译不通过），泛型反序列化见下一章。

---

## 6. TypeReference：泛型反序列化

### 6.1 问题：类型擦除

Java 泛型在运行时会擦除。`JSON.parseObject(json, List.class)` 只能得到 `List`（元素是 `JSONObject`），无法一步得到 `List<MyBean>`。`List<MyBean>.class` 这种语法又不存在。

### 6.2 方案：匿名子类携带泛型信息

```java
List<MyBean> list = JSON.parseObject(json, new TypeReference<List<MyBean>>() {});
```

`TypeReference` 利用匿名子类保留父类的泛型实参（通过反射读取），从而让 fastjson 在反序列化时知道目标类型是 `List<MyBean>` 而不是裸 `List`。

### 6.3 参考项目实战：嵌套泛型

`MCPParamToolUtils.java`（删节）——最典型的复杂泛型场景：

```java
List<Map<String, List<String>>> data = JSON.parseObject(
        JSON.toJSONString(dataArr),
        new TypeReference<List<Map<String, List<String>>>>() {});
```

`DictPlateAreaMapUtil.java`（删节）——Map 作为外层：

```java
eCORCodeToRegionCodeChildMap.put(
        entry.getKey(),
        JSONObject.parseObject(entry.getValue(), new TypeReference<Map<String, List<String>>>() {}));
```

> 观察一个细节：`JSON.parseObject` 接受 `TypeReference` 作为第二个参数，而 `JSONArray.parseArray` 的带类型重载只接受 `Class`。因此"带泛型的列表"通常走 `JSON.parseObject(json, new TypeReference<...>(){})` 这一路。

---

## 7. @JSONField：字段名映射

`com.alibaba.fastjson.annotation.JSONField` 标注在字段或 getter 上，控制序列化/反序列化行为。

### 7.1 name：最常用的属性

参考项目 `entity/judicial/CreditAbnormalBean.java`（删节）：

```java
import com.alibaba.fastjson.annotation.JSONField;

@JSONField(name = "Fc_company_code")
private String fcCompanyCode;
```

上游接口返回的 JSON 用 `Fc_company_code` 这类下划线/大写命名，Java 字段按驼峰命名。`name` 声明二者的映射关系，**序列化和反序列化双向生效**。

### 7.2 其他常用属性速查

| 属性                        | 作用                                 |
| --------------------------- | ------------------------------------ |
| `name`                      | JSON 中的字段名                      |
| `serialize` / `deserialize` | 是否参与序列化 / 反序列化            |
| `format`                    | 日期格式，如 `"yyyy-MM-dd HH:mm:ss"` |
| `ordinal`                   | 序列化时的字段顺序（数值小的在前）   |

> 注意：fastjson 1.x 的 `@JSONField` 与 Jackson 的 `@JsonProperty` 互不识别，双库混用时要么各自声明、要么只让一个库接触该实体类。

---

## 8. 特性开关：Feature 与 SerializerFeature

fastjson 把"行为开关"拆成两个枚举，方向不同：

| 枚举                                                | 生效方向                  | 常见用法                                                      |
| --------------------------------------------------- | ------------------------- | ------------------------------------------------------------- |
| `com.alibaba.fastjson.parser.Feature`               | **解析**（JSON → 对象）   | `JSON.parseObject(json, clazz, Feature.OrderedField)`         |
| `com.alibaba.fastjson.serializer.SerializerFeature` | **序列化**（对象 → JSON） | `JSON.toJSONString(obj, SerializerFeature.WriteMapNullValue)` |

### 8.1 Feature.OrderedField：保持字段顺序

参考项目 `MCPInfoUtils.java`（删节）——项目注释直接说明了原因：

```java
// 使用Feature.OrderedField保持字段顺序，确保sort排序结果不被Fastjson的HashMap/SortField打乱
JSONObject object = JSONObject.parseObject(generateSchemaJson(nestedSchemaList, false), Feature.OrderedField);
```

不加 `OrderedField` 时，解析出的 `JSONObject` 内部是 `HashMap`，`toJSONString` 输出顺序不可控；加了之后内部改用有序结构（`LinkedHashMap`），字段按 JSON 原文顺序输出。**对外输出 schema/字段描述时顺序即契约**，所以这里必须显式开启。

### 8.2 SerializerFeature.WriteMapNullValue：保留 null 值

参考项目 `AIToolsManager.java`（删节）：

```java
JSONObject dataClone = JSONObject.parseObject(
        JSONObject.toJSONString(config.getOrgJson(), SerializerFeature.WriteMapNullValue));
```

fastjson 有一个容易踩的**不对称默认**：

```
序列化普通 Java 对象  →  null 字段默认输出
序列化 Map/JSONObject →  值为 null 的 entry 默认不输出（丢弃！）
```

上面代码的意图是"序列化往返做一次克隆"，如果不加 `WriteMapNullValue`，JSONObject 里值为 null 的 key 会在 `toJSONString` 时消失，往返后结构就变了。凡是**先转字符串再解析回来**的场景，都要检查是否需要这个开关。

### 8.3 其他常用 SerializerFeature 速查

| 开关                             | 作用                                                  |
| -------------------------------- | ----------------------------------------------------- |
| `WriteMapNullValue`              | Map 的 null 值也输出（见上）                          |
| `WriteNullStringAsEmpty`         | null 字符串输出为 `""`                                |
| `PrettyFormat`                   | 美化输出（带缩进换行，便于日志阅读）                  |
| `WriteDateUseDateFormat`         | 日期按全局 `JSON.DEFFAULT_DATE_FORMAT` 输出           |
| `DisableCircularReferenceDetect` | 关闭循环引用检测（默认开启，检测到循环会输出 `$ref`） |

---

## 9. 深拷贝惯用法

### 9.1 参考项目的实现

`utils/CloneUtils.java`（全文，25 行）：

```java
/**
 * 使用对象的序列化进而实现深拷贝
 */
public static <T> T clone(T obj) {
    String str = JSONObject.toJSONString(obj);
    T cloneObj = (T) JSONObject.parseObject(str, obj.getClass());
    return cloneObj;
}
```

原理：`toJSONString` 把对象连同嵌套引用**全部展开**成字符串 → `parseObject` 按原类型重建一个全新对象。天然深拷贝，不必逐字段 copy。

### 9.2 代价与边界

| 问题           | 说明                                                                               |
| -------------- | ---------------------------------------------------------------------------------- |
| 性能           | 字符串序列化 + 反射重建，比手写 copy 慢一个数量级；大对象批量 clone 不划算         |
| 精度           | `BigDecimal` 默认按字符串输出、无损；但需确认项目没改全局日期/数字格式             |
| 构造器         | 目标类型必须有**无参构造**（fastjson 反射实例化）                                  |
| 字段匹配       | 依赖字段名精确匹配（有 `@JSONField(name=...)` 时按映射名走），无匹配字段会静默丢弃 |
| 不可序列化成员 | `transient`、`static` 字段不参与，内部类/代理类可能失败                            |

### 9.3 何时用

适合**一次性的配置对象快照**（clone 后修改不影响原对象，如第 8.2 节的 `dataClone`）。频繁深拷贝请考虑 MapStruct（见 [mapstruct-guide.md](mapstruct-guide.md)）或手写转换。

---

## 10. 与 Jackson 的边界

参考项目是**双库混用**的典型。`MCPParamToolUtils.java` 开头同时引入两套：

```java
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
```

实际分工模式（`BaseIndexServiceImpl.java` 等处可见）：

```
 对象/列表                JSON 字符串                 树形裁剪/字段挑选
┌──────────┐  fastjson   ┌──────────┐   Jackson     ┌──────────┐
│ Java 对象 │ ──────────▶ │ 字符串    │ ────────────▶ │ JsonNode │
│ List/Map │ toJSONString│  (交换层) │ parse → 树    │ 树结构    │
└──────────┘             └──────────┘               └──────────┘
   fastjson 的舒适区        Jackson 的舒适区（transformJsonByOutputHeadList 等按输出头裁剪字段）
```

分工原则：

- **fastjson 管"字符串 ↔ 对象"**：`toJSONString`、`parseObject`、`parseArray`、`TypeReference`。
- **Jackson 管"树结构裁剪"**：`JsonNode` 按输出头挑选字段、重排。
- 两者以 **JSON 字符串为交换媒介**，互不感知对方的对象模型，共存无冲突。

在本项目（demo1）内，JSON 处理统一以 [jackson-guide.md](jackson-guide.md) 的 Jackson 3.1.2 为准；本指南仅用于阅读上述参考项目。

---

## 11. 安全：AutoType 与 safeMode（简要）

> 本章只给结论与配置片段。fastjson 反序列化漏洞的完整机制不在本指南范围。

### 11.1 一句话背景

fastjson 曾因 **AutoType** 机制（反序列化时按 JSON 里的 `@type` 字段任意加载类）被曝光多轮 RCE 漏洞链。安全演进路线：

```
1.2.24 及以前      1.2.25 起          1.2.68 起           1.2.83
  autotype         黑名单 + 默认受限   safeMode 开关        1.x 终点：
  全开放    ──▶    （白名单三选一） ──▶ （完全禁用）   ──▶   AutoType 默认关闭
```

官方 wiki 明确：1.2.83 中 AutoType **默认关闭**，且提供了彻底移除 AutoType 代码的特供坐标 `com.alibaba:fastjson:1.2.83_noneautotype`。

### 11.2 怎么判断代码是否触发 AutoType

看序列化侧是否用了 `SerializerFeature.WriteClassName`：

```java
JSON.toJSONString(obj, SerializerFeature.WriteClassName); // 会产生 {"@type":"com.xxx.MyBean",...}
```

`@type` 就是 AutoType 的触发信号。**参考项目里没有 `WriteClassName` 用法**，所有 `parseObject` 都指定了明确的目标类型（`clazz` 或 `TypeReference`），不依赖 AutoType，风险面很低。

### 11.3 维护 1.2.83 项目的三个动作

1. **开启 safeMode（最推荐）**，三种方式任选其一（官方 wiki）：

```java
ParserConfig.getGlobalInstance().setSafeMode(true);   // 代码方式
```

```
-Dfastjson.parser.safeMode=true                        // JVM 启动参数
```

```
fastjson.parser.safeMode=true                          // 类路径 fastjson.properties
```

safeMode 开启后**完全禁用 AutoType**，白名单也不生效——这是最硬的开关。

2. **如业务必须用 AutoType**，用白名单而非全开：`ParserConfig.getGlobalInstance().addAccept("com.yourcompany.")` 只放行自己包（三选一：代码 / JVM 参数 / `fastjson.properties`）。

3. **长期规划迁移 fastjson2**（包名 `com.alibaba.fastjson2`，与 1.x 可共存，官方提供兼容模式），1.x 已停止维护。

---

## 12. 速查清单

```
转换
  JSON.toJSONString(obj [, SerializerFeature...])        → String
  JSON.parseObject(json)                                  → JSONObject
  JSON.parseObject(json, Clazz.class)                     → Clazz
  JSON.parseObject(json, new TypeReference<T>() {})       → 泛型 T
  JSON.parseArray(json [, Clazz.class])                   → JSONArray / List<T>
  JSON.toJSON(obj)                                        → JSONObject / JSONArray 树

JSONObject（本质 Map<String,Object>）
  getString / getIntValue / getBooleanValue / getJSONObject / getJSONArray
  containsKey / put / remove / keySet

JSONArray（本质 List<Object>）
  getJSONObject(i) / getString(i) / size / 遍历

注解
  @JSONField(name="db_col")           字段名映射
  @JSONField(serialize=false)         排除字段

特性开关
  Feature.OrderedField                       解析保序
  SerializerFeature.WriteMapNullValue       Map null 值输出
  SerializerFeature.PrettyFormat            美化输出
  SerializerFeature.WriteDateUseDateFormat  按默认格式输出日期

安全
  ParserConfig.getGlobalInstance().setSafeMode(true)
  -Dfastjson.parser.safeMode=true
  特供版坐标：com.alibaba:fastjson:1.2.83_noneautotype
```

---

## 参考资料

- [alibaba/fastjson GitHub Wiki（官方）](https://github.com/alibaba/fastjson/wiki)：AutoType 配置（enable_autotype）、safeMode 配置（fastjson_safemode）；本指南第 11 章的配置方式与版本事实均出自此处。
- 参考项目源码：`D:\dzh\finchina-data-mcp-server-ex`（本指南所有"实战"片段来源，个别片段有删节）。
