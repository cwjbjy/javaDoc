# Knife4j 增强接口文档指南

> 本指南介绍 Knife4j —— SpringDoc 的增强 UI 层。在已掌握 [SpringDoc OpenAPI 指南](springdoc-openapi-guide.md) 的注解体系后，本指南聚焦 Knife4j 提供了哪些增强功能、如何替换 Swagger UI，以及企业开发中的常用配置。
>
> 适用版本：Knife4j Next 5.2.0（com.baizhukui），Spring Boot 4.x，Java 17+（Jakarta 命名空间）。

---

## 目录

1. [为什么需要 Knife4j](#1-为什么需要-knife4j)
2. [快速上手：从 Swagger UI 到 Knife4j](#2-快速上手从-swagger-ui-到-knife4j)
3. [第一层：离线文档导出](#3-第一层离线文档导出)
4. [第二层：全局参数设置](#4-第二层全局参数设置)
5. [第三层：接口排序与增强配置](#5-第三层接口排序与增强配置)
6. [与 SpringDoc 配置的关系](#6-与-springdoc-配置的关系)
7. [速查清单](#7-速查清单)

---

## 1. 为什么需要 Knife4j

### 先搞清楚 Knife4j 和 SpringDoc 的关系

很多人以为 Knife4j 是 SpringDoc 的"替代品"——**不是**。看这张架构图：

```
┌──────────────────────────────────────────────────────────┐
│                     Knife4j 增强 UI                       │
│  • 离线文档导出（Markdown / Word / HTML）                 │
│  • 全局参数管理（统一注入 token 到所有接口）              │
│  • 接口排序、搜索增强、调试缓存                           │
│  • 中文本地化 (zh_cn)、黑色主题                          │
│  • 生产环境文档屏蔽                                      │
│  访问地址: /doc.html                                     │
├──────────────────────────────────────────────────────────┤
│                SpringDoc OpenAPI 引擎（不动）             │
│  • @Tag / @Operation / @Schema 注解解析                  │
│  • OpenAPI 3.0 JSON 自动生成                             │
│  • GroupedOpenApi 分组                                   │
│  访问地址: /v3/api-docs                                  │
├──────────────────────────────────────────────────────────┤
│                Spring MVC Controller（不动）             │
└──────────────────────────────────────────────────────────┘
```

**核心结论：你之前写的所有 SpringDoc 注解完全不需要改。** Knife4j 只替换了最上面那层 UI，同时提供了 Swagger UI 不具备的增强功能。

### Swagger UI 在企业场景中的不足

```
Swagger UI 能做到的                    Swagger UI 做不到的
─────────────────────                 ────────────────────
✅ 在线查看接口列表                    ❌ 导出文档给产品/测试看
✅ Try it out 在线调试                 ❌ 统一给所有接口加 token
✅ 显示请求/响应 Schema                ❌ 中文界面（翻译不完整）
✅ 按 Tag 分组                         ❌ 生产环境关闭文档
                                       ❌ 自定义排序规则
                                       ❌ 调试历史记录
```

在企业协作中，这些"做不到"恰好是高频需求——产品经理要离线文档做评审，测试要全局 token 调接口，运维要求生产环境不暴露文档。Knife4j 正是为这些场景设计的。

---

## 2. 快速上手：从 Swagger UI 到 Knife4j

### 2.1 替换依赖

**之前**（SpringDoc 指南中的配置）：

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.17</version>
</dependency>
```

**之后**（替换为 Knife4j Next starter）：

```xml
<!-- Knife4j Next 已内置 springdoc-openapi，不要再单独引入 springdoc-openapi-starter-webmvc-ui -->
<dependency>
    <groupId>com.baizhukui</groupId>
    <artifactId>knife4j-openapi3-boot4-spring-boot-starter</artifactId>
    <version>5.2.0</version>
</dependency>
```

> **为什么是 `com.baizhukui` 而不是 `com.github.xiaoymin`？**
>
> 原 Knife4j（`com.github.xiaoymin`）最新版 4.5.0 仅支持到 Spring Boot 3.x，对 Spring Boot 4.x 的适配已停滞。**Knife4j Next**（`com.baizhukui`）是社区维护的 fork，专门跟进 Spring Boot 4.x / Spring Framework 7.x 的兼容性，当前稳定版 **5.2.0**。
>
> 对于 Spring Boot 4.x 项目，必须使用 `knife4j-openapi3-boot4-spring-boot-starter`（注意 artifactId 中有 `boot4`），不能使用 `knife4j-openapi3-jakarta-spring-boot-starter`（后者仅支持 Spring Boot 3.x）。
>
> | Spring Boot 版本  | artifactId                                       |
> | ----------------- | ------------------------------------------------ |
> | 2.x / javax       | `knife4j-openapi3-spring-boot-starter`           |
> | 3.x / Jakarta     | `knife4j-openapi3-jakarta-spring-boot-starter`   |
> | **4.x / Jakarta** | **`knife4j-openapi3-boot4-spring-boot-starter`** |

> ⚠️ **关键：** Knife4j 的 starter 已经传递引入了 `springdoc-openapi`。如果 `pom.xml` 中同时存在 `springdoc-openapi-starter-webmvc-ui`，会导致 jar 冲突。**替换时删除原来的 SpringDoc UI 依赖，只保留 Knife4j。**

### 2.2 访问地址变化

| 之前（SpringDoc）                       | 之后（Knife4j）                     | 说明                 |
| --------------------------------------- | ----------------------------------- | -------------------- |
| `http://localhost:8080/swagger-ui.html` | `http://localhost:8080/doc.html`    | 交互式文档页面       |
| `http://localhost:8080/v3/api-docs`     | `http://localhost:8080/v3/api-docs` | OpenAPI JSON（不变） |

打开 `/doc.html`，你会看到 Knife4j 的黑色主题界面：

```
┌──────────────────────────────────────────────────────────────┐
│  Knife4j 文档                                    [搜索接口]   │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─ 市场管理 ────────────────────────────────────────────┐  │
│  │  POST   /market/addCategory         添加分类           │  │
│  │  DELETE /market/deleteCategory      删除分类           │  │
│  │  PUT    /market/updateCategory      更新分类           │  │
│  │  GET    /market/getAll              获取所有分类       │  │
│  │  ...                                                  │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌─ 订单管理 ────────────────────────────────────────────┐  │
│  │  POST   /order/addOrder             创建订单           │  │
│  │  GET    /order/getOrder             查询订单           │  │
│  │  DELETE /order/deleteOrder          删除订单           │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  [📥 离线文档]  [🌐 全局参数]  [⚙ 个性化设置]               │
└──────────────────────────────────────────────────────────────┘
```

### 2.3 基础配置

在 `application.yml` 中添加 Knife4j 配置：

```yaml
# SpringDoc 配置保持不变
springdoc:
  swagger-ui:
    path: /swagger-ui.html
  api-docs:
    path: /v3/api-docs

# Knife4j 增强配置
knife4j:
  enable: true # 启用 Knife4j 增强功能
  setting:
    language: zh_cn # UI 语言：zh_cn 中文
```

> 只需要 `knife4j.enable: true` 就能启用增强功能。`SpringDocConfig` 中的 `OpenAPI` Bean、`GroupedOpenApi` 分组、`GlobalOperationCustomizer` 全部原样保留。

### 2.4 生产环境关闭文档

企业项目的一个常见需求：开发/测试环境开放文档，生产环境关闭：

```yaml
# application-dev.yml（开发环境）
knife4j:
  enable: true
  setting:
    language: zh_cn

# application-prod.yml（生产环境）
knife4j:
  enable: false     # 关闭 Knife4j，/doc.html 和 /v3/api-docs 都不可访问
```

或者用 SpringDoc 原生的方式：

```yaml
# application-prod.yml
springdoc:
  api-docs:
    enabled: false # 关闭 OpenAPI JSON
```

---

## 3. 第一层：离线文档导出

### 3.1 问题：产品经理和测试怎么看接口？

Swagger UI 只能在线看。但实际工作流中：

- **产品经理**要评审接口设计 → 需要 Markdown 或 Word 文档
- **测试**要写接口测试用例 → 需要导出接口清单
- **前端**要离线查阅 → 需要可离线浏览的 HTML

Knife4j 在 `/doc.html` 页面提供了 **离线文档** 功能，支持四种导出格式。

### 3.2 导出操作

打开 `/doc.html`，在页面左侧底部找到 **"文档管理"** → **"离线文档"**：

```
┌──────────────────────────────────────────┐
│  离线文档导出                            │
│                                          │
│  [Markdown]  ↓ 下载 .md 文件             │
│  [Html]      ↓ 下载 .html 静态页面       │
│  [Word]      ↓ 下载 .doc 文件            │
│  [OpenAPI]   ↓ 下载 .json 原始描述文件   │
└──────────────────────────────────────────┘
```

导出的 Markdown 文档示例：

````markdown
# JavaDoc 菜品订单系统 API

## 市场管理

### 添加分类

**接口地址:** `POST /market/addCategory`

**请求参数:**

| 参数名 | 类型   | 必填 | 说明         |
| ------ | ------ | ---- | ------------ |
| name   | String | 是   | 分类名称     |
| image  | String | 是   | 分类图标 URL |

**响应示例:**

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```
````

```

### 3.3 离线文档的典型使用场景

| 格式 | 适用场景 | 给谁用 |
|------|----------|--------|
| Markdown | 提交到 Git 仓库，随代码版本管理 | 全团队 |
| Word | 接口评审会议、签字确认 | 产品经理、项目经理 |
| HTML | 离线浏览，不需要启动服务 | 前端、测试 |
| OpenAPI JSON | 导入 Postman / Apifox 等工具 | 测试、前端 |

---

## 4. 第二层：全局参数设置

### 4.1 问题：每个接口都要手动填 token

你的项目接入 Spring Security + JWT 后，在 Swagger UI 里调试时：

```

测试接口 A → 点 Authorize → 填 token → 发请求
测试接口 B → 又要点 Authorize → 再填 token → 发请求
测试接口 C → ...

```

Swagger UI 虽然有全局 Authorize 按钮，但 token 不会自动附加到**所有**接口。Knife4j 提供了更灵活的"全局参数"功能。

### 4.2 在页面上设置全局参数

打开 `/doc.html`，点击右下角的 **"全局参数"** 按钮：

```

┌──────────────────────────────────────────────────────┐
│ 全局参数设置 │
│ │
│ 参数名: Authorization │
│ 参数值: Bearer eyJhbGciOiJIUzI1NiJ9... │
│ 参数类型: header ▾ │
│ │
│ 已添加的全局参数: │
│ ┌──────────────────────────────────────────────┐ │
│ │ Authorization = Bearer eyJ... │ │
│ │ X-Tenant-Id = demo-001 │ │
│ └──────────────────────────────────────────────┘ │
│ │
│ [添加] [重置] │
└──────────────────────────────────────────────────────┘

````

添加后，页面上的所有"发送请求"操作都会自动携带这些参数。

> 全局参数对**所有接口**生效，不仅限于某个分组。这在多租户场景（统一加 `X-Tenant-Id` 头）或全链路追踪（统一加 `X-Trace-Id` 头）时特别有用。

### 4.3 代码中预设全局参数

也可以通过配置类预设全局参数，避免每次打开页面都要手动填写：

```java
package com.example.javadoc.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringDocConfig {

    /**
     * 预设全局请求头参数（在 Swagger UI / Knife4j 中自动显示）
     */
    @Bean
    public GlobalOperationCustomizer globalHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            operation.addParametersItem(new Parameter()
                    .in("header")
                    .name("Authorization")
                    .description("JWT Token（格式：Bearer xxx）")
                    .required(false)
                    .example("Bearer eyJhbGciOiJIUzI1NiJ9..."));
            return operation;
        };
    }
}
````

效果：每个接口的调试面板中都会出现一个 `Authorization` 输入框，开发人员只需填入 token，不需要手动添加参数。

---

## 5. 第三层：接口排序与增强配置

### 5.1 接口排序

默认情况下，Swagger UI 和 Knife4j 都按方法定义顺序展示接口。当 Controller 中方法较多时，可以自定义排序规则：

```yaml
knife4j:
  enable: true
  setting:
    language: zh_cn
    enable-swagger-models: true # 是否显示 Schema 模型
    swagger-model-name: 实体类列表 # Schema 区域标题
    enable-document-manage: true # 是否显示"文档管理"菜单
    enable-version: true # 是否显示版本号
    enable-footer: false # 是否显示页脚
    enable-footer-custom: false # 是否自定义页脚
    footer-custom-content: "" # 自定义页脚内容
```

接口排序在代码中通过 `x-order` 扩展属性控制：

```java
@Operation(summary = "获取所有分类", description = "返回所有分类及其包含的菜品列表")
// Knife4j 扩展：数字越小越靠前，默认值为 0
@io.swagger.v3.oas.annotations.extensions.Extension(
        name = "x-order", value = "1")
@GetMapping("/getAll")
public Object getAll() {
    return marketService.getAll();
}
```

### 5.2 启用/禁用增强功能

Knife4j 的增强功能通过 `knife4j.setting` 控制，关键配置项：

| 配置项                                    | 类型    | 默认值 | 说明                               |
| ----------------------------------------- | ------- | ------ | ---------------------------------- |
| `enable-swagger-models`                   | boolean | true   | 是否显示页面底部的 Schema 模型列表 |
| `enable-document-manage`                  | boolean | true   | 是否显示左侧"文档管理"菜单         |
| `enable-version`                          | boolean | false  | 是否在页面顶部显示版本号           |
| `enable-footer`                           | boolean | true   | 是否显示页脚                       |
| `enable-footer-custom`                    | boolean | false  | 是否使用自定义页脚                 |
| `enable-debug`                            | boolean | true   | 是否启用调试功能（Try it out）     |
| `enable-search`                           | boolean | true   | 是否显示全局搜索框                 |
| `enable-host`                             | boolean | false  | 是否显示请求地址下拉选择器         |
| `enable-host-text`                        | string  | —      | 请求地址下拉框的默认文本           |
| `enable-request-cache`                    | boolean | true   | 是否缓存调试请求参数               |
| `enable-filter-multipart-apis`            | boolean | false  | 是否过滤文件上传接口               |
| `enable-filter-multipart-api-method-type` | string  | POST   | 文件上传接口的 HTTP 方法           |
| `enable-group`                            | boolean | true   | 是否显示分组                       |

### 5.3 典型配置场景

**场景一：仅对外展示，关闭调试功能**

```yaml
knife4j:
  enable: true
  setting:
    language: zh_cn
    enable-debug: false # 关闭 Try it out（只读文档）
    enable-document-manage: true # 保留离线导出
```

**场景二：开发环境最大化可用性**

```yaml
knife4j:
  enable: true
  setting:
    language: zh_cn
    enable-request-cache: true # 缓存请求参数（切接口不丢填的值）
    enable-search: true # 全局搜索接口
    enable-swagger-models: true # 显示 Schema 模型
    enable-footer: false # 隐藏页脚，更多可视空间
```

---

## 6. 与 SpringDoc 配置的关系

### 6.1 什么是 Knife4j 的，什么是 SpringDoc 的

一个常见的困惑：`knife4j.setting.xxx` 和 `springdoc.xxx` 到底怎么分工？

```
┌────────────────────────────────────────────────────────────┐
│  配置项                           归属                      │
├────────────────────────────────────────────────────────────┤
│  springdoc.api-docs.path          SpringDoc（引擎层）       │
│  springdoc.swagger-ui.path        SpringDoc（引擎层）       │
│  springdoc.group-configs          SpringDoc（引擎层）       │
│  springdoc.show-actuator          SpringDoc（引擎层）       │
│  OpenAPI Bean                     SpringDoc（引擎层）       │
│  GroupedOpenApi Bean              SpringDoc（引擎层）       │
│  @Tag / @Operation / @Schema       SpringDoc（引擎层）       │
├────────────────────────────────────────────────────────────┤
│  knife4j.enable                   Knife4j（UI 层）          │
│  knife4j.setting.*                Knife4j（UI 层）          │
│  knife4j.cors                     Knife4j（UI 层）          │
│  knife4j.production               Knife4j（UI 层）          │
└────────────────────────────────────────────────────────────┘
```

**记忆规则：**

- 所有 `springdoc.*` 配置 → 控制 OpenAPI JSON 的**生成**（引擎层）
- 所有 `knife4j.*` 配置 → 控制 `/doc.html` 页面的**展示**（UI 层）
- 所有 Java 注解 → 属于 SpringDoc 引擎层

### 6.2 用 Knife4j 后 SpringDocConfig 的变化

**唯一需要改的：删掉 `GlobalOperationCustomizer` 中与 UI 相关的内容。**

Knife4j 替换了 Swagger UI，但 `GlobalOperationCustomizer` 是 SpringDoc 引擎层的功能——它修改的是 OpenAPI JSON，不是 UI。所以保留。但如果你之前在 `GlobalOperationCustomizer` 里做的是"UI 层面的全局参数预设"（如自动注入 token），Knife4j 提供了更好的方式（见 §4.3）。

### 6.3 升级路径总结

```
从 SpringDoc + Swagger UI 升级到 SpringDoc + Knife4j Next

步骤 1: pom.xml
        删除: springdoc-openapi-starter-webmvc-ui
        添加: com.baizhukui:knife4j-openapi3-boot4-spring-boot-starter:5.2.0

步骤 2: application.yml
        添加: knife4j.enable: true
        添加: knife4j.setting.language: zh_cn

步骤 3: SpringDocConfig.java
        不动 —— OpenAPI Bean、GroupedOpenApi、GlobalOperationCustomizer 全部保留

步骤 4: Controller 注解
        不动 —— @Tag、@Operation、@Schema、@ApiResponse 全部不变

步骤 5: 访问地址
        /swagger-ui.html  →  /doc.html
```

---

## 7. 速查清单

### 7.1 Knife4j vs SpringDoc 功能对照

| 功能              | SpringDoc + Swagger UI          | Knife4j                     |
| ----------------- | ------------------------------- | --------------------------- |
| 在线接口文档      | ✅ `/swagger-ui.html`           | ✅ `/doc.html`              |
| OpenAPI JSON      | ✅ `/v3/api-docs`               | ✅ `/v3/api-docs`（同一份） |
| 离线导出 Markdown | ❌                              | ✅                          |
| 离线导出 Word     | ❌                              | ✅                          |
| 离线导出 HTML     | ❌                              | ✅                          |
| 全局参数设置      | ⚠️ 仅 Authorize                 | ✅ 任意参数类型             |
| 中文界面          | ⚠️ 翻译不完整                   | ✅ 原生 zh_cn               |
| 请求缓存          | ❌                              | ✅                          |
| 接口搜索          | ✅                              | ✅（增强版）                |
| 生产环境关闭      | ✅ `springdoc.api-docs.enabled` | ✅ `knife4j.enable: false`  |
| 自定义排序        | ❌                              | ✅ `x-order` 扩展           |
| 黑色主题          | ❌                              | ✅                          |

### 7.2 依赖速查

| 场景                                     | Maven 依赖                                                         |
| ---------------------------------------- | ------------------------------------------------------------------ |
| SpringDoc + Swagger UI                   | `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17`         |
| Knife4j Next（Spring Boot 4.x，Jakarta） | `com.baizhukui:knife4j-openapi3-boot4-spring-boot-starter:5.2.0`   |
| Knife4j Next（Spring Boot 3.x，Jakarta） | `com.baizhukui:knife4j-openapi3-jakarta-spring-boot-starter:5.2.0` |
| Knife4j Next（Spring Boot 2.x，javax）   | `com.baizhukui:knife4j-openapi3-spring-boot-starter:5.2.0`         |

> ⚠️ Knife4j starter 已包含 springdoc-openapi，不要同时引入两种 starter。
>
> **Spring Boot 4.x 用户注意：** artifactId 必须是 `knife4j-openapi3-boot4-spring-boot-starter`（带 `boot4`），不是 `knife4j-openapi3-jakarta-spring-boot-starter`。后者仅支持 Spring Boot 3.x，在 4.x 下会抛出 `NoSuchMethodError: ControllerAdviceBean.<init>(Object)`。

### 7.3 URL 速查

| URL                    | 提供方    | 说明                                     |
| ---------------------- | --------- | ---------------------------------------- |
| `/doc.html`            | Knife4j   | 增强 UI 文档页面                         |
| `/swagger-ui.html`     | SpringDoc | 原 Swagger UI（引入 Knife4j 后仍然可用） |
| `/v3/api-docs`         | SpringDoc | OpenAPI 3.0 JSON                         |
| `/v3/api-docs/{group}` | SpringDoc | 指定分组 JSON                            |

### 7.4 配置速查

```yaml
# 完整的 Knife4j + SpringDoc 配置参考

springdoc:
  api-docs:
    path: /v3/api-docs
    enabled: true # 生产环境设为 false
  swagger-ui:
    path: /swagger-ui.html
  show-actuator: false

knife4j:
  enable: true # 启用 Knife4j（生产环境设为 false）
  setting:
    language: zh_cn # 中文界面
    enable-swagger-models: true
    enable-document-manage: true
    enable-version: false
    enable-debug: true # 生产环境建议 false
    enable-search: true
    enable-request-cache: true
    enable-footer: false
```

### 7.5 常见坑速查

| 陷阱                                               | 后果                                  | 正确做法                                                                           |
| -------------------------------------------------- | ------------------------------------- | ---------------------------------------------------------------------------------- |
| pom.xml 中同时存在 springdoc-ui 和 knife4j         | 启动报 jar 冲突                       | 二选一，删掉 springdoc-openapi-starter-webmvc-ui                                   |
| 只加了 Knife4j 依赖但不配 `knife4j.enable: true`   | 只能用 Swagger UI，Knife4j 增强不生效 | `application.yml` 中加 `knife4j.enable: true`                                      |
| 以为 Knife4j 替代了 SpringDoc，删了 `OpenAPI` Bean | 文档标题和描述丢失                    | SpringDoc 的 `OpenAPI` Bean 保留不动                                               |
| 在 `GlobalOperationCustomizer` 里做 Knife4j 的事   | 配置分散、维护困难                    | UI 层配置放 `knife4j.setting`，引擎层配置放 `springdoc.*`                          |
| Knife4j 版本与 Spring Boot 版本不匹配              | 启动报错或页面空白                    | Spring Boot 4.x → `com.baizhukui:knife4j-openapi3-boot4-spring-boot-starter:5.2.0` |
| 生产环境忘记关闭文档                               | 接口信息对外暴露                      | `knife4j.enable: false` 或 `springdoc.api-docs.enabled: false`                     |

---

> **延伸阅读：**
>
> - [SpringDoc OpenAPI 指南](springdoc-openapi-guide.md) —— 注解体系与 OpenAPI 引擎配置（Knife4j 的基础层）
> - [Spring MVC 指南](spring-mvc-guide.md) —— Controller 注解详解
> - [Spring Boot Validation 指南](spring-validation-guide.md) —— @Valid 校验与 DTO 设计
> - [Spring Security 指南](spring-security-guide.md) —— JWT 鉴权完整方案（配合 Knife4j 全局参数使用）
> - [Knife4j 官方文档](https://doc.xiaominfo.com/)
