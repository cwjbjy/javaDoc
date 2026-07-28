# 01-10 常用工具类库

> 掌握Hutool、Apache Commons、Guava等常用工具库

## 1. Hutool（本项目使用）

Hutool是国产工具类库，简单易用，功能全面。

### 添加依赖

```xml
<dependency>
    <groupId>cn.hutool</groupId>
    <artifactId>hutool-all</artifactId>
    <version>5.8.16</version>
</dependency>
```

### 字符串工具（StrUtil）

```java
import cn.hutool.core.util.StrUtil;

// 判空
StrUtil.isEmpty(str);           // null 或 "" 为 true
StrUtil.isNotEmpty(str);
StrUtil.isBlank(str);           // null、""、"  " 为 true
StrUtil.isNotBlank(str);        // 最常用

// 默认值
String result = StrUtil.blankToDefault(str, "default");

// 格式化
String msg = StrUtil.format("Hello {}, age {}", "John", 25);
// "Hello John, age 25"

// 移除前后缀
StrUtil.removePrefix("prefix_name", "prefix_");  // "name"
StrUtil.removeSuffix("name_suffix", "_suffix");  // "name"

// 驼峰与下划线转换
StrUtil.toCamelCase("user_name");   // "userName"
StrUtil.toUnderlineCase("userName"); // "user_name"

// 首字母大小写
StrUtil.upperFirst("hello");  // "Hello"
StrUtil.lowerFirst("Hello");  // "hello"

// 子串
StrUtil.sub("Hello World", 0, 5);  // "Hello"
StrUtil.subBefore("a.b.c", ".", false);  // "a"（第一个.之前）
StrUtil.subAfter("a.b.c", ".", true);    // "c"（最后一个.之后）
```

### 对象工具（ObjectUtil）

```java
import cn.hutool.core.util.ObjectUtil;

// 判空
ObjectUtil.isEmpty(obj);        // null、""、空集合 为 true
ObjectUtil.isNotEmpty(obj);

// 默认值
String result = ObjectUtil.defaultIfNull(obj, "default");

// 比较
boolean equal = ObjectUtil.equal(obj1, obj2);  // null安全的equals
```

### 集合工具（CollUtil）

```java
import cn.hutool.core.util.CollUtil;

// 判空
CollUtil.isEmpty(list);
CollUtil.isNotEmpty(list);

// 创建集合
List<String> list = CollUtil.newArrayList("A", "B", "C");
Set<String> set = CollUtil.newHashSet("A", "B", "C");
Map<String, String> map = CollUtil.newHashMap();

// 转换
String[] array = {"A", "B", "C"};
List<String> list2 = CollUtil.toList(array);

// 分组
List<Product> products = getProducts();
Map<String, List<Product>> grouped = CollUtil.groupBy(products, Product::getCategory);

// 查找
Product first = CollUtil.findOne(products, p -> p.getPrice() > 100);

// 过滤
List<Product> filtered = CollUtil.filter(products, p -> p.getStock() > 0);

// 分页
List<Product> page = CollUtil.page(0, 10, products);  // 第1页，10条
```

### 日期工具（DateUtil）

```java
import cn.hutool.core.date.DateUtil;

// 当前时间
Date now = DateUtil.date();
String nowStr = DateUtil.now();  // "2024-03-15 14:30:45"
String today = DateUtil.today(); // "2024-03-15"

// 格式化
String formatted = DateUtil.format(now, "yyyy-MM-dd HH:mm:ss");

// 解析
Date parsed = DateUtil.parse("2024-03-15 14:30:45");
Date parsed2 = DateUtil.parse("2024-03-15", "yyyy-MM-dd");

// 日期运算
Date tomorrow = DateUtil.offsetDay(now, 1);
Date nextWeek = DateUtil.offsetWeek(now, 1);
Date nextMonth = DateUtil.offsetMonth(now, 1);

// 日期范围
List<DateTime> range = DateUtil.rangeToList(startDate, endDate, DateField.DAY_OF_YEAR);

// 时间差
long between = DateUtil.between(date1, date2, DateUnit.DAY);  // 相差天数

// 判断
boolean isSameDay = DateUtil.isSameDay(date1, date2);
```

### JSON工具（JSONUtil）

```java
import cn.hutool.json.JSONUtil;

// 对象 → JSON
Product product = new Product();
String json = JSONUtil.toJsonStr(product);
String prettyJson = JSONUtil.toJsonPrettyStr(product);  // 格式化

// JSON → 对象
Product product2 = JSONUtil.toBean(json, Product.class);

// JSON → List
String listJson = "[{\"id\":1},{\"id\":2}]";
List<Product> list = JSONUtil.toList(listJson, Product.class);

// 判断
boolean isJson = JSONUtil.isJson(str);
```

### 文件工具（FileUtil）

```java
import cn.hutool.core.io.FileUtil;

// 读取
String content = FileUtil.readUtf8String("file.txt");
List<String> lines = FileUtil.readUtf8Lines("file.txt");
byte[] bytes = FileUtil.readBytes("file.txt");

// 写入
FileUtil.writeUtf8String("Hello", "file.txt");
FileUtil.writeUtf8Lines(lines, "file.txt");
FileUtil.appendUtf8String("\nNew Line", "file.txt");

// 复制/移动/删除
FileUtil.copy("source.txt", "target.txt", true);
FileUtil.move(new File("source.txt"), new File("target.txt"), true);
FileUtil.del("file.txt");

// 文件信息
String name = FileUtil.getName("path/to/file.txt");  // "file.txt"
String extName = FileUtil.extName("file.txt");       // "txt"
String mainName = FileUtil.mainName("file.txt");     // "file"
```

### HTTP工具（HttpUtil）

```java
import cn.hutool.http.HttpUtil;

// GET请求
String response = HttpUtil.get("https://api.example.com/data");

// POST请求
Map<String, Object> params = new HashMap<>();
params.put("name", "John");
String response2 = HttpUtil.post("https://api.example.com/create", params);

// 下载文件
HttpUtil.downloadFile("https://example.com/file.pdf", "local.pdf");

// 超时设置
String response3 = HttpUtil.createGet("https://api.example.com/data")
    .timeout(5000)
    .execute()
    .body();
```

### 加密工具（SecureUtil）

```java
import cn.hutool.crypto.SecureUtil;

// MD5
String md5 = SecureUtil.md5("password");

// SHA-256
String sha256 = SecureUtil.sha256("password");

// AES加密
String key = "1234567890123456";  // 16字节密钥
String encrypted = SecureUtil.aes(key.getBytes()).encryptHex("Hello");
String decrypted = SecureUtil.aes(key.getBytes()).decryptStr(encrypted);

// RSA加密
RSA rsa = new RSA();
String privateKey = rsa.getPrivateKeyBase64();
String publicKey = rsa.getPublicKeyBase64();
byte[] encrypted2 = rsa.encrypt("Hello", KeyType.PublicKey);
byte[] decrypted2 = rsa.decrypt(encrypted2, KeyType.PrivateKey);
```

### 其他工具

```java
// 数字工具（NumberUtil）
import cn.hutool.core.util.NumberUtil;
boolean isNumber = NumberUtil.isNumber("123");
int sum = NumberUtil.add(1, 2, 3);
BigDecimal result = NumberUtil.add("0.1", "0.2");  // 0.3（精确）

// 随机工具（RandomUtil）
import cn.hutool.core.util.RandomUtil;
int randomInt = RandomUtil.randomInt(1, 100);
String randomStr = RandomUtil.randomString(10);

// ID生成器（IdUtil）
import cn.hutool.core.util.IdUtil;
String uuid = IdUtil.fastUUID();
long snowflakeId = IdUtil.getSnowflake().nextId();

// 反射工具（ReflectUtil）
import cn.hutool.core.util.ReflectUtil;
Field field = ReflectUtil.getField(Product.class, "name");
Object value = ReflectUtil.getFieldValue(product, "name");
ReflectUtil.setFieldValue(product, "name", "iPhone");
```

---

## 2. Apache Commons

### Commons Lang3（常用工具）

```xml
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-lang3</artifactId>
    <version>3.12.0</version>
</dependency>
```

```java
import org.apache.commons.lang3.*;

// 字符串工具（StringUtils）
StringUtils.isEmpty(str);
StringUtils.isNotEmpty(str);
StringUtils.isBlank(str);
StringUtils.isNotBlank(str);
StringUtils.defaultIfBlank(str, "default");
StringUtils.join(list, ", ");
StringUtils.split("a,b,c", ",");

// 数组工具（ArrayUtils）
boolean contains = ArrayUtils.contains(array, "element");
int[] reversed = ArrayUtils.reverse(array);
int[] subArray = ArrayUtils.subarray(array, 0, 5);

// 随机工具（RandomStringUtils）
String randomStr = RandomStringUtils.randomAlphanumeric(10);
String randomNum = RandomStringUtils.randomNumeric(6);

// 对象工具（ObjectUtils）
Object result = ObjectUtils.defaultIfNull(obj, defaultObj);
```

### Commons Collections4（集合工具）

```xml
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-collections4</artifactId>
    <version>4.4</version>
</dependency>
```

```java
import org.apache.commons.collections4.*;

// 集合工具（CollectionUtils）
boolean isEmpty = CollectionUtils.isEmpty(collection);
Collection<String> intersection = CollectionUtils.intersection(list1, list2);
Collection<String> union = CollectionUtils.union(list1, list2);

// Map工具（MapUtils）
boolean isEmpty2 = MapUtils.isEmpty(map);
String value = MapUtils.getString(map, "key", "default");
```

### Commons IO（IO工具）

```xml
<dependency>
    <groupId>commons-io</groupId>
    <artifactId>commons-io</artifactId>
    <version>2.11.0</version>
</dependency>
```

```java
import org.apache.commons.io.*;

// 文件工具（FileUtils）
String content = FileUtils.readFileToString(file, "UTF-8");
List<String> lines = FileUtils.readLines(file, "UTF-8");
FileUtils.writeStringToFile(file, content, "UTF-8");
FileUtils.copyFile(srcFile, destFile);
FileUtils.deleteQuietly(file);

// IO工具（IOUtils）
String content2 = IOUtils.toString(inputStream, "UTF-8");
List<String> lines2 = IOUtils.readLines(inputStream, "UTF-8");
IOUtils.copy(inputStream, outputStream);
```

---

## 3. Guava（Google工具库）

```xml
<dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
    <version>31.1-jre</version>
</dependency>
```

### 集合工具

```java
import com.google.common.collect.*;

// 不可变集合
ImmutableList<String> list = ImmutableList.of("A", "B", "C");
ImmutableSet<String> set = ImmutableSet.of("A", "B", "C");
ImmutableMap<String, Integer> map = ImmutableMap.of("A", 1, "B", 2);

// Multimap（一个key对应多个value）
Multimap<String, String> multimap = ArrayListMultimap.create();
multimap.put("fruit", "apple");
multimap.put("fruit", "banana");
Collection<String> fruits = multimap.get("fruit");  // [apple, banana]

// BiMap（双向Map）
BiMap<String, Integer> biMap = HashBiMap.create();
biMap.put("A", 1);
biMap.put("B", 2);
Integer value = biMap.get("A");  // 1
String key = biMap.inverse().get(1);  // "A"

// Table（双键Map）
Table<String, String, Integer> table = HashBasedTable.create();
table.put("row1", "col1", 1);
table.put("row1", "col2", 2);
Integer value2 = table.get("row1", "col1");  // 1
```

### 字符串工具

```java
import com.google.common.base.*;

// Joiner（拼接）
String joined = Joiner.on(", ").join("A", "B", "C");  // "A, B, C"
String joined2 = Joiner.on(", ").skipNulls().join("A", null, "B");  // "A, B"

// Splitter（分割）
List<String> parts = Splitter.on(",").trimResults().splitToList("a, b, c");

// Strings
String padded = Strings.padStart("7", 3, '0');  // "007"
String repeated = Strings.repeat("ab", 3);       // "ababab"
```

### 缓存

```java
import com.google.common.cache.*;

// 本地缓存
LoadingCache<String, String> cache = CacheBuilder.newBuilder()
    .maximumSize(1000)  // 最大容量
    .expireAfterWrite(10, TimeUnit.MINUTES)  // 写入后10分钟过期
    .build(new CacheLoader<String, String>() {
        @Override
        public String load(String key) {
            return loadFromDB(key);  // 缓存加载逻辑
        }
    });

// 使用
String value = cache.get("key");
cache.put("key", "value");
cache.invalidate("key");  // 清除
```

---

## 4. Jackson（JSON处理）

### 对象与JSON互转

```java
import com.fasterxml.jackson.databind.ObjectMapper;

ObjectMapper mapper = new ObjectMapper();

// 对象 → JSON
Product product = new Product();
String json = mapper.writeValueAsString(product);

// JSON → 对象
Product product2 = mapper.readValue(json, Product.class);

// JSON → List
String listJson = "[{\"id\":1},{\"id\":2}]";
List<Product> list = mapper.readValue(listJson, 
    new TypeReference<List<Product>>() {});

// JSON → Map
Map<String, Object> map = mapper.readValue(json, 
    new TypeReference<Map<String, Object>>() {});
```

### 配置

```java
ObjectMapper mapper = new ObjectMapper();

// 忽略未知属性
mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

// null不序列化
mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

// 日期格式
mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

// 驼峰转下划线
mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
```

---

## 5. 实战示例

### 示例1：批量导出CSV

```java
@Service
public class ExportService {
    
    public void exportProducts(List<Product> products, String filePath) {
        List<String> lines = products.stream()
            .map(p -> StrUtil.format("{},{},{},{}",
                p.getId(), p.getName(), p.getPrice(), p.getStock()))
            .collect(Collectors.toList());
        
        // 添加表头
        lines.add(0, "ID,名称,价格,库存");
        
        FileUtil.writeUtf8Lines(lines, filePath);
    }
}
```

### 示例2：HTTP调用第三方API

```java
@Service
public class ThirdPartyService {
    
    public Map<String, Object> callApi(String url, Map<String, Object> params) {
        try {
            String response = HttpUtil.post(url, params, 5000);
            return JSONUtil.toBean(response, Map.class);
        } catch (Exception e) {
            log.error("调用API失败", e);
            throw new ApiException("调用第三方API失败");
        }
    }
}
```

### 示例3：本地缓存

```java
@Component
public class CacheService {
    
    private LoadingCache<Long, Product> productCache = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build(new CacheLoader<Long, Product>() {
            @Override
            public Product load(Long id) {
                return productMapper.selectByPrimaryKey(id);
            }
        });
    
    public Product getProduct(Long id) {
        try {
            return productCache.get(id);
        } catch (ExecutionException e) {
            throw new ApiException("获取商品失败");
        }
    }
    
    public void invalidate(Long id) {
        productCache.invalidate(id);
    }
}
```

---

## 6. 选择建议

| 场景 | 推荐工具库 | 理由 |
|------|----------|------|
| 国内项目 | Hutool | 中文文档、API简洁 |
| 国际项目 | Apache Commons | 成熟稳定 |
| 复杂集合操作 | Guava | 功能强大 |
| JSON处理 | Jackson | Spring默认 |
| 本地缓存 | Guava Cache | 轻量级 |

---

## 快速参考

```java
// Hutool - 字符串
StrUtil.isNotBlank(str)
StrUtil.format("Hello {}", name)

// Hutool - 集合
CollUtil.isNotEmpty(list)
CollUtil.newArrayList("A", "B")

// Hutool - 日期
DateUtil.now()
DateUtil.offsetDay(now, 1)

// Hutool - JSON
JSONUtil.toJsonStr(obj)
JSONUtil.toBean(json, Product.class)

// Hutool - 文件
FileUtil.readUtf8String("file.txt")
FileUtil.writeUtf8String("Hello", "file.txt")

// Apache Commons
StringUtils.isNotBlank(str)
CollectionUtils.isEmpty(list)
FileUtils.readFileToString(file, "UTF-8")

// Guava
ImmutableList.of("A", "B", "C")
Joiner.on(", ").join(list)
```

---

## 完成！

恭喜你完成Java基础学习！现在你已经掌握：

✅ **01-01** 变量与类型系统  
✅ **01-02** 面向对象编程  
✅ **01-03** 集合框架详解  
✅ **01-04** Lambda与Stream  
✅ **01-05** 异常处理机制  
✅ **01-06** 字符串与日期  
✅ **01-07** 注解与反射  
✅ **01-08** IO与文件操作  
✅ **01-09** 多线程基础  
✅ **01-10** 常用工具类库  

### 下一步学习

继续学习项目相关内容：
- **02-Maven与项目结构.md** - 理解项目构建
- **03-Spring Boot核心概念.md** - 掌握框架核心
- **04-数据库操作.md** - MyBatis与数据库
- **05-接口开发实战.md** - 完整接口开发
