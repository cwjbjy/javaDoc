## Context

当前项目是Spring Boot 4.0.6空白模板(Java 17)，仅有一个`HelloController`示例端点。需要参照Nest.js `cookbook_api`项目实现完整的菜谱管理REST API。Nest.js项目使用MongoDB + Mongoose(嵌入式文档模型)、YAML多环境配置、Swagger文档、Multer文件上传，以及全局拦截器/过滤器实现统一响应格式。

目标是在Spring Boot生态中找到对应的技术方案，保持与Nest.js项目相同的API契约和数据模型。

## Goals / Non-Goals

**Goals:**
- 完整复刻Nest.js项目的14个API端点，保持完全相同的请求/响应格式
- 使用Spring Data MongoDB保持同样的文档数据模型(Market嵌入Foods, Order嵌入FoodItems)
- 统一成功响应`{code: 200, message: 'success', data: ...}`
- 统一异常响应`{statusCode, timestamp, path, message}`
- 多环境YAML配置(dev/prod)
- Swagger UI API文档
- 文件上传到静态资源目录，删除时清理磁盘文件

**Non-Goals:**
- 不做认证/授权(与Nest.js项目一致)
- 不做前端页面(纯REST API)
- 不修改MongoDB数据模型结构
- 不新增API端点(严格1:1映射)
- 不做数据迁移或数据库变更

## Decisions

### 1. Spring Data MongoDB (而非JPA + MySQL)

**选型**: `spring-boot-starter-data-mongodb`

**理由**: Nest.js项目使用MongoDB的嵌入式文档模型(Market.foods是子文档数组, Order.foods也是嵌入数组)。这是MongoDB的核心优势场景。使用JPA需要将嵌入式文档拆为独立表+外键关联，既破坏数据模型一致性，也增加不必要的复杂度。

### 2. 包结构: core + module 两层

```
com.example.demo1
├── config/              # 配置类(CORS, 静态资源映射)
├── core/                # 全局横切关注点
│   └── advice/          # @RestControllerAdvice
│       ├── GlobalResponseBodyAdvice   # ResponseBodyAdvice包装统一响应
│       └── GlobalExceptionHandler     # @ExceptionHandler统一异常处理
└── module/
    ├── market/          # 菜单模块
    │   ├── controller/
    │   ├── service/
    │   ├── dto/
    │   └── entity/
    └── order/           # 订单模块
        ├── controller/
        ├── service/
        ├── dto/
        └── entity/
```

**理由**: 与Nest.js项目的`core/module`二层结构对应，便于理解和维护。`HelloController`将被移除。

### 3. 统一响应包装: ResponseBodyAdvice

**选型**: 实现`ResponseBodyAdvice<Object>`接口

**理由**: Spring的`ResponseBodyAdvice`是Nest.js `GlobalInterceptor`的最直接对应物——拦截所有`@ResponseBody`返回值，在序列化前统一包装为`{code, message, data}`。配置`@RestControllerAdvice` + `implements ResponseBodyAdvice`即可全局生效。

### 4. 统一异常处理: @RestControllerAdvice + @ExceptionHandler

**选型**: 单一个`GlobalExceptionHandler`类

**理由**: Nest.js有两个过滤器(HttpExceptionFilter + AllExceptionsFilter)，Spring可以用一个`@RestControllerAdvice`类中的多个`@ExceptionHandler`方法实现相同效果：
- `@ExceptionHandler(Exception.class)` → 对应AllExceptionsFilter (500)
- `@ExceptionHandler(ResponseStatusException.class)` 或具体业务异常 → 对应HttpExceptionFilter
- 返回格式统一为`{statusCode, timestamp, path, message}`

### 5. API文档: SpringDoc OpenAPI

**选型**: `springdoc-openapi-starter-webmvc-ui`

**理由**: SpringDoc是Spring Boot生态的Swagger标准实现，注解驱动(`@Tag`, `@Operation`, `@Parameter`)，与Nest.js的`@nestjs/swagger`装饰器模式对应。

### 6. 文件上传: Spring MultipartFile + 静态资源映射

**选型**: `MultipartFile`接收上传 + 自定义静态资源映射

**理由**:
- Nest.js用Multer `diskStorage` 存到 `public/images/market/`，Spring用`MultipartFile.transferTo()`即可
- 文件名策略: `System.currentTimeMillis() + 原扩展名`，与Nest.js一致
- 静态资源访问: 配置`WebMvcConfigurer.addResourceHandlers`将`/static/**`映射到文件系统路径
- 图片清理: 用`java.nio.file.Files.deleteIfExists()`

### 7. MongoDB数据模型

直接映射Nest.js的Mongoose Schema到Spring Data MongoDB的`@Document`:

```
Market {
  id (ObjectId)
  name: String
  image: String
  foods: List<FoodItem>    ← @Embedded
    - id (ObjectId)
    - name, describe, burden, image: String
    - num: Integer (default 0)
}

Order {
  id (ObjectId)
  date: String
  createdAt: Date
  num: Integer
  foods: List<OrderFoodItem>   ← @Embedded
    - foodId: String (ref)
    - name, describe, burden, image: String
    - value: Integer
}
```

## Risks / Trade-offs

- **[文件路径]** Nest.js用`join(__dirname, '../../..', 'public/images/market')`在dist目录下定位。Spring Boot工作目录是项目根目录，直接用相对路径即可。
- **[端口]** Nest.js默认9001，Spring Boot默认8080。配置application.yml覆盖为9001保持一致。
- **[API前缀]** Nest.js配置了`api`全局前缀(`/api/market/...`)。Spring Boot可用`server.servlet.context-path=/api`或在Controller上配置基础路径。
- **[CORS]** Nest.js全局启用了CORS。Spring Boot通过`WebMvcConfigurer.addCorsMappings`配置。

## Open Questions

- `HelloController`是否保留？建议移除，仅保留菜谱相关API。