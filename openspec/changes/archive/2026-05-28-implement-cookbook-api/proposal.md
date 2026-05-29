## Why

当前Spring Boot项目是空白初始模板，需要参照已有的Nest.js菜谱管理API项目(`cookbook_api`)实现相同的REST API功能。目标是将Nest.js + MongoDB的菜谱管理系统完整迁移到Spring Boot技术栈，使用Spring Data MongoDB保持文档模型一致性。

## What Changes

- **移除** Thymeleaf依赖，转为纯REST API项目
- **新增** Spring Data MongoDB依赖，连接MongoDB数据库
- **新增** 全局统一响应包装(`{code, message, data}`) 和 全局异常处理
- **新增** Market模块(菜单分类+菜品管理) — 11个API端点
- **新增** Order模块(订单管理) — 3个API端点
- **新增** 文件上传功能(图片存储到`static/images/market/`)
- **新增** SpringDoc OpenAPI(Swagger) API文档
- **新增** application.yml多环境配置(dev/prod)

## Capabilities

### New Capabilities

- `market-management`: 菜单分类CRUD、菜品CRUD、按食材搜索菜品、菜品被点数量批量更新、跨分类移动菜品
- `order-management`: 订单创建、分页查询(按创建时间倒序)、订单删除
- `file-upload`: 图片文件上传到静态资源目录，返回访问URL；分类/菜品删除时清理对应图片文件
- `global-response-format`: 成功响应统一包裹为`{code, message, data}`，异常响应统一格式`{statusCode, timestamp, path, message}`
- `api-documentation`: 基于SpringDoc OpenAPI自动生成Swagger UI接口文档

### Modified Capabilities

<!-- 无现有capabilities，本项目为从零构建 -->

## Impact

- `pom.xml`: 移除Thymeleaf依赖，新增spring-boot-starter-data-mongodb、springdoc-openapi-starter-webmvc-ui
- `src/main/java/com/example/demo1/`: 按core/module拆分包结构
- `src/main/resources/`: application.properties → application.yml(多环境)，新增`static/images/market/`静态资源目录
- 无向后兼容影响(项目当前仅有一个HelloController，可保留或移除)