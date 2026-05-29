## 1. 项目基础设施

- [x] 1.1 更新pom.xml: 移除Thymeleaf依赖，新增spring-boot-starter-data-mongodb、springdoc-openapi-starter-webmvc-ui
- [x] 1.2 创建application.yml(dev/prod多环境配置): 端口9001、context-path=/api、MongoDB连接信息
- [x] 1.3 创建基础包结构: config/、core/advice/、module/market/、module/order/(各含controller/service/dto/entity子包)
- [x] 1.4 配置CORS和静态资源映射(WebMvcConfigurer)

## 2. 全局横切关注点(core)

- [x] 2.1 实现GlobalResponseBodyAdvice: ResponseBodyAdvice统一包装成功响应`{code, message, data}`
- [x] 2.2 实现GlobalExceptionHandler: @ExceptionHandler统一异常响应`{statusCode, timestamp, path, message}`，处理ConflictException/NotFoundException/通用异常
- [x] 2.3 移除HelloController(不再需要)

## 3. Market模块 - 数据层

- [x] 3.1 创建Market实体(含内嵌FoodItem)
- [x] 3.2 创建MarketRepository(MongoRepository接口)
- [x] 3.3 创建Market模块所有DTO: CreateCategoryDTO, UpdateCategoryDTO, AddFoodDTO, UpdateFoodDTO, DeleteFoodDTO, FoodDTO

## 4. Market模块 - 业务层

- [x] 4.1 实现CategoryService: addCategory, deleteCategory, updateCategory, getAll
- [x] 4.2 实现FoodService: addFood, deleteFood, updateFoodWithNum, updateFoodWithoutImage, updateFood, findFoods
- [x] 4.3 实现MarketController: 11个API端点，@Tag("菜单")

## 5. Order模块

- [x] 5.1 创建Order实体(含内嵌OrderFoodItem)
- [x] 5.2 创建OrderRepository(MongoRepository接口)
- [x] 5.3 创建Order模块DTO: CreateOrderDTO, DeleteOrderDTO
- [x] 5.4 实现OrderService: create, find(skip/pageSize/total), remove
- [x] 5.5 实现OrderController: 3个API端点，@Tag("订单")

## 6. 文件上传

- [x] 6.1 实现文件上传端点(复用MarketController中已有uploadImage端点)
- [x] 6.2 创建static/images/market/目录
- [x] 6.3 实现图片文件名生成和磁盘存储逻辑

## 7. API文档与验证

- [x] 7.1 配置SpringDoc Swagger: 标题/描述/版本、Swagger UI路径`/api/swagger-ui`
- [x] 7.2 为所有DTO字段添加验证注解(@NotBlank等)
- [x] 7.3 验证应用启动、Swagger UI可访问、全部14个端点可用