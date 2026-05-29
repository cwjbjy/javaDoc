## ADDED Requirements

### Requirement: Swagger UI接口文档
系统SHALL通过SpringDoc OpenAPI自动生成API文档并提供Swagger UI界面。

#### Scenario: 访问Swagger UI
- **WHEN** 浏览器访问`/api/swagger-ui`
- **THEN** 系统展示所有API端点的交互式文档界面

#### Scenario: API分组
- **WHEN** Swagger UI加载
- **THEN** Market模块端点标注为"菜单"分组，Order模块端点标注为"订单"分组

### Requirement: OpenAPI JSON端点
系统SHALL暴露OpenAPI规范的JSON描述。

#### Scenario: 获取OpenAPI JSON
- **WHEN** 客户端GET `/v3/api-docs`
- **THEN** 系统返回完整的OpenAPI 3.0规范JSON文档