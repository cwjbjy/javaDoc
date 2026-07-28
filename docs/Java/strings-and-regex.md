# 01-06 字符串与日期

> 掌握Java字符串操作与日期时间处理

## 1. String 基础

### String 的特性

```java
// 1. 不可变（Immutable）
String str = "Hello";
str.toUpperCase();  // 返回新字符串
System.out.println(str);  // 仍然是 "Hello"

String str2 = str.toUpperCase();  // 必须接收返回值
System.out.println(str2);  // "HELLO"

// 2. 字符串池（String Pool）
String s1 = "Hello";
String s2 = "Hello";
System.out.println(s1 == s2);  // true（同一对象）

String s3 = new String("Hello");
System.out.println(s1 == s3);  // false（不同对象）
System.out.println(s1.equals(s3));  // true（值相等）
```

---

## 2. String 常用方法

### 长度与判空

```java
String str = "Hello";

// 长度
int len = str.length();  // 5

// 判空
boolean empty1 = str.isEmpty();  // false（""为true）
boolean empty2 = str.isBlank();  // false（Java 11+，""和"  "都为true）

// Hutool 工具类（本项目常用）
import cn.hutool.core.util.StrUtil;

StrUtil.isEmpty(str);      // null 或 "" 为 true
StrUtil.isNotEmpty(str);   // 非空串
StrUtil.isBlank(str);      // null、""、"  " 为 true
StrUtil.isNotBlank(str);   // 非空白（推荐）
```

### 字符访问

```java
String str = "Hello";

// 获取字符
char ch = str.charAt(0);  // 'H'

// 转字符数组
char[] chars = str.toCharArray();  // ['H', 'e', 'l', 'l', 'o']

// 遍历
for (int i = 0; i < str.length(); i++) {
    char c = str.charAt(i);
}

for (char c : str.toCharArray()) {
    System.out.println(c);
}
```

### 查找与包含

```java
String str = "Hello World";

// 包含
boolean contains = str.contains("World");  // true

// 查找位置
int index1 = str.indexOf("o");      // 4（第一次出现）
int index2 = str.indexOf("o", 5);   // 7（从索引5开始查找）
int index3 = str.lastIndexOf("o");  // 7（最后一次出现）
int index4 = str.indexOf("xyz");    // -1（不存在）

// 开头/结尾
boolean starts = str.startsWith("Hello");  // true
boolean ends = str.endsWith("World");      // true
```

### 截取

```java
String str = "Hello World";

// 截取子串
String sub1 = str.substring(6);      // "World"（从索引6到末尾）
String sub2 = str.substring(0, 5);   // "Hello"（[0, 5)，不含索引5）
String sub3 = str.substring(6, 11);  // "World"
```

### 替换

```java
String str = "Hello World World";

// 替换首次匹配
String s1 = str.replace("World", "Java");  // "Hello Java Java"

// 替换所有匹配
String s2 = str.replaceAll("World", "Java");  // "Hello Java Java"

// 替换首次匹配（正则）
String s3 = str.replaceFirst("World", "Java");  // "Hello Java World"

// 删除空格
String s4 = "  Hello  ".trim();       // "Hello"（两端空格）
String s5 = "  Hello  ".strip();      // "Hello"（Java 11+，Unicode空白）
String s6 = "  Hello  ".stripLeading();   // "Hello  "（开头）
String s7 = "  Hello  ".stripTrailing();  // "  Hello"（末尾）
```

### 分割与拼接

```java
// 分割
String str = "A,B,C";
String[] parts = str.split(",");  // ["A", "B", "C"]

String str2 = "A, B , C";
String[] parts2 = str2.split(",\\s*");  // 正则：逗号+可选空格

// 拼接
String joined = String.join(", ", "A", "B", "C");  // "A, B, C"
String joined2 = String.join("-", parts);  // "A-B-C"

// Stream 拼接
List<String> list = Arrays.asList("A", "B", "C");
String result = list.stream()
    .collect(Collectors.joining(", "));  // "A, B, C"

String result2 = list.stream()
    .collect(Collectors.joining(", ", "[", "]"));  // "[A, B, C]"
```

### 大小写转换

```java
String str = "Hello World";

String upper = str.toUpperCase();   // "HELLO WORLD"
String lower = str.toLowerCase();   // "hello world"

// 忽略大小写比较
boolean equal = str.equalsIgnoreCase("hello world");  // true
```

### 格式化

```java
// String.format（类似 printf）
String msg1 = String.format("Name: %s, Age: %d", "John", 25);
String msg2 = String.format("Price: %.2f", 99.999);  // "Price: 100.00"

// 占位符
// %s - 字符串
// %d - 整数
// %f - 浮点数
// %.2f - 保留2位小数

// Java 15+ 文本块（多行字符串）
String json = """
    {
        "name": "John",
        "age": 25
    }
    """;
```

---

## 3. StringBuilder（可变字符串）

### StringBuilder vs String

```java
// ❌ String 拼接（低效，每次创建新对象）
String result = "";
for (int i = 0; i < 1000; i++) {
    result += i;  // 创建1000个String对象
}

// ✅ StringBuilder（高效，可变）
StringBuilder sb = new StringBuilder();
for (int i = 0; i < 1000; i++) {
    sb.append(i);  // 修改同一对象
}
String result2 = sb.toString();
```

### StringBuilder 常用方法

```java
StringBuilder sb = new StringBuilder();

// 追加
sb.append("Hello");
sb.append(" ");
sb.append("World");

// 插入
sb.insert(5, ",");  // "Hello, World"

// 删除
sb.delete(5, 6);    // "Hello World"（删除索引[5,6)）
sb.deleteCharAt(5); // "HelloWorld"（删除索引5的字符）

// 替换
sb.replace(0, 5, "Hi");  // "Hi World"

// 反转
sb.reverse();  // "dlroW iH"

// 转字符串
String result = sb.toString();
```

### StringBuffer（线程安全，但慢）

```java
// StringBuilder - 单线程（推荐）
StringBuilder sb = new StringBuilder();

// StringBuffer - 多线程（少用）
StringBuffer buffer = new StringBuffer();
```

---

## 4. 正则表达式

### 基本匹配

```java
import java.util.regex.*;

String text = "My phone is 13812345678";

// matches - 完全匹配
boolean isPhone = "13812345678".matches("1[3-9]\\d{9}");  // true

// Pattern + Matcher
Pattern pattern = Pattern.compile("1[3-9]\\d{9}");
Matcher matcher = pattern.matcher(text);

// find - 查找
if (matcher.find()) {
    String phone = matcher.group();  // "13812345678"
}

// replaceAll - 替换
String masked = text.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
// "My phone is 138****5678"
```

### 常用正则

```java
// 手机号
String phoneRegex = "1[3-9]\\d{9}";

// 邮箱
String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$";

// 身份证
String idCardRegex = "\\d{17}[\\dXx]";

// URL
String urlRegex = "https?://[\\w\\-]+(\\.[\\w\\-]+)+([\\w\\-.,@?^=%&:/~+#]*[\\w\\-@?^=%&/~+#])?";

// 中文
String chineseRegex = "[\\u4e00-\\u9fa5]+";

// 数字
String numberRegex = "\\d+";          // 整数
String decimalRegex = "\\d+\\.\\d+";  // 小数
```

### 分组与提取

```java
String text = "Name: John, Age: 25";
Pattern pattern = Pattern.compile("Name: (\\w+), Age: (\\d+)");
Matcher matcher = pattern.matcher(text);

if (matcher.find()) {
    String name = matcher.group(1);  // "John"
    String age = matcher.group(2);   // "25"
}
```

---

## 5. 日期时间（Java 8+ API）

### LocalDate（日期）

```java
import java.time.*;

// 获取当前日期
LocalDate today = LocalDate.now();  // 2024-03-15

// 指定日期
LocalDate date1 = LocalDate.of(2024, 3, 15);
LocalDate date2 = LocalDate.of(2024, Month.MARCH, 15);

// 解析字符串
LocalDate date3 = LocalDate.parse("2024-03-15");

// 获取字段
int year = today.getYear();         // 2024
int month = today.getMonthValue();  // 3
int day = today.getDayOfMonth();    // 15
DayOfWeek dayOfWeek = today.getDayOfWeek();  // FRIDAY

// 日期运算
LocalDate tomorrow = today.plusDays(1);
LocalDate nextWeek = today.plusWeeks(1);
LocalDate nextMonth = today.plusMonths(1);
LocalDate nextYear = today.plusYears(1);

LocalDate yesterday = today.minusDays(1);

// 日期比较
boolean isBefore = date1.isBefore(date2);
boolean isAfter = date1.isAfter(date2);
boolean isEqual = date1.isEqual(date2);
```

### LocalTime（时间）

```java
// 获取当前时间
LocalTime now = LocalTime.now();  // 14:30:45.123

// 指定时间
LocalTime time1 = LocalTime.of(14, 30);        // 14:30
LocalTime time2 = LocalTime.of(14, 30, 45);    // 14:30:45

// 解析字符串
LocalTime time3 = LocalTime.parse("14:30:45");

// 时间运算
LocalTime later = now.plusHours(2);
LocalTime earlier = now.minusMinutes(30);
```

### LocalDateTime（日期+时间）

```java
// 获取当前日期时间
LocalDateTime now = LocalDateTime.now();  // 2024-03-15T14:30:45.123

// 指定日期时间
LocalDateTime dt1 = LocalDateTime.of(2024, 3, 15, 14, 30);
LocalDateTime dt2 = LocalDateTime.of(
    LocalDate.of(2024, 3, 15),
    LocalTime.of(14, 30)
);

// 解析字符串
LocalDateTime dt3 = LocalDateTime.parse("2024-03-15T14:30:45");

// 转换
LocalDate date = now.toLocalDate();
LocalTime time = now.toLocalTime();

// 日期时间运算
LocalDateTime future = now.plusDays(7).plusHours(2);
```

### 格式化与解析

```java
import java.time.format.DateTimeFormatter;

LocalDateTime now = LocalDateTime.now();

// 格式化
DateTimeFormatter formatter1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
String str1 = now.format(formatter1);  // "2024-03-15 14:30:45"

DateTimeFormatter formatter2 = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm");
String str2 = now.format(formatter2);  // "2024年03月15日 14:30"

// 解析
String dateStr = "2024-03-15 14:30:45";
LocalDateTime parsed = LocalDateTime.parse(dateStr, formatter1);

// 常用格式
DateTimeFormatter.ISO_LOCAL_DATE;       // 2024-03-15
DateTimeFormatter.ISO_LOCAL_TIME;       // 14:30:45.123
DateTimeFormatter.ISO_LOCAL_DATE_TIME;  // 2024-03-15T14:30:45.123
```

### Period 与 Duration（时间间隔）

```java
// Period - 日期间隔
LocalDate start = LocalDate.of(2024, 1, 1);
LocalDate end = LocalDate.of(2024, 3, 15);

Period period = Period.between(start, end);
int years = period.getYears();
int months = period.getMonths();
int days = period.getDays();

// Duration - 时间间隔
LocalTime time1 = LocalTime.of(9, 0);
LocalTime time2 = LocalTime.of(17, 30);

Duration duration = Duration.between(time1, time2);
long hours = duration.toHours();      // 8
long minutes = duration.toMinutes();  // 510
```

### Instant（时间戳）

```java
// 当前时间戳（UTC）
Instant now = Instant.now();

// 时间戳（秒）
long epochSecond = now.getEpochSecond();

// 时间戳（毫秒）
long epochMilli = now.toEpochMilli();

// 从时间戳创建
Instant instant = Instant.ofEpochMilli(1710494445000L);

// 转换
LocalDateTime dateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
```

### ZonedDateTime（时区）

```java
// 带时区的日期时间
ZonedDateTime now = ZonedDateTime.now();  // 2024-03-15T14:30:45.123+08:00[Asia/Shanghai]

// 指定时区
ZonedDateTime tokyo = ZonedDateTime.now(ZoneId.of("Asia/Tokyo"));
ZonedDateTime nyc = ZonedDateTime.now(ZoneId.of("America/New_York"));

// 时区转换
ZonedDateTime converted = now.withZoneSameInstant(ZoneId.of("America/New_York"));
```

---

## 6. 旧日期API（Date/Calendar）

### Date（不推荐，但常见）

```java
import java.util.Date;

// 当前时间
Date now = new Date();

// 时间戳
long timestamp = now.getTime();
Date date = new Date(1710494445000L);

// Date ↔ LocalDateTime
LocalDateTime ldt = LocalDateTime.ofInstant(now.toInstant(), ZoneId.systemDefault());
Date date2 = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant());
```

### SimpleDateFormat（线程不安全）

```java
import java.text.SimpleDateFormat;

SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

// 格式化
Date now = new Date();
String str = sdf.format(now);  // "2024-03-15 14:30:45"

// 解析
Date parsed = sdf.parse("2024-03-15 14:30:45");

// ⚠️ SimpleDateFormat 线程不安全
// ✅ 使用 DateTimeFormatter（线程安全）
```

---

## 7. 实战示例

### 示例1：日期范围查询

```java
// 查询最近7天的订单
LocalDateTime now = LocalDateTime.now();
LocalDateTime weekAgo = now.minusDays(7);

List<Order> orders = orderMapper.selectByDateRange(weekAgo, now);

// SQL: WHERE create_time BETWEEN ? AND ?
```

### 示例2：格式化订单创建时间

```java
@Data
public class OrderVO {
    private Long id;
    private LocalDateTime createTime;
    
    public String getCreateTimeStr() {
        if (createTime == null) {
            return "";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        return createTime.format(formatter);
    }
}
```

### 示例3：计算年龄

```java
public int calculateAge(LocalDate birthDate) {
    if (birthDate == null) {
        return 0;
    }
    LocalDate today = LocalDate.now();
    Period period = Period.between(birthDate, today);
    return period.getYears();
}

// 使用
LocalDate birthDate = LocalDate.of(1990, 5, 15);
int age = calculateAge(birthDate);  // 33（假设今年2024年）
```

### 示例4：判断是否营业时间

```java
public boolean isBusinessHours() {
    LocalTime now = LocalTime.now();
    LocalTime start = LocalTime.of(9, 0);   // 9:00
    LocalTime end = LocalTime.of(18, 0);    // 18:00
    
    return now.isAfter(start) && now.isBefore(end);
}
```

---

## 8. Hutool 日期工具

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

// 日期运算
Date tomorrow = DateUtil.offsetDay(now, 1);
Date nextWeek = DateUtil.offsetWeek(now, 1);

// 日期比较
boolean isSameDay = DateUtil.isSameDay(date1, date2);

// 时间差
long between = DateUtil.between(date1, date2, DateUnit.DAY);
```

---

## 下一步

- **[01-07-注解与反射.md](./01-07-注解与反射.md)** - 注解与反射
- **[01-08-IO与文件操作.md](./01-08-IO与文件操作.md)** - IO与文件

---

## 快速参考

```java
// String
String str = "Hello";
str.length();
str.isEmpty();
str.contains("ell");
str.substring(0, 3);
str.replace("H", "h");
str.split(",");
String.join(", ", "A", "B");

// StringBuilder
StringBuilder sb = new StringBuilder();
sb.append("Hello").append(" ").append("World");
String result = sb.toString();

// LocalDate
LocalDate today = LocalDate.now();
LocalDate date = LocalDate.of(2024, 3, 15);
date.plusDays(7);

// LocalDateTime
LocalDateTime now = LocalDateTime.now();
DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
String str = now.format(formatter);
LocalDateTime parsed = LocalDateTime.parse(str, formatter);
```
