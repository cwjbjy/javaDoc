# pom.xml 指南

## 核心概念

### Maven 是什么？

Maven 类似于前端的 npm/yarn/pnpm，是 Java 生态的项目管理和构建工具。

| 前端工具        | Java 工具      | 作用         |
| --------------- | -------------- | ------------ |
| `package.json`  | `pom.xml`      | 项目配置文件 |
| `npm install`   | `mvn install`  | 安装依赖     |
| `npm run build` | `mvn package`  | 构建项目     |
| `node_modules/` | Maven 本地仓库 | 依赖存储位置 |
| npmjs.com       | Maven Central  | 中央仓库     |

### pom.xml 是什么？

POM = Project Object Model（项目对象模型）

相当于 `package.json`，定义了项目的：

- 基本信息（名称、版本、描述）
- 依赖（类似 `dependencies`）
- 构建配置（类似 `scripts`）
- 插件配置（类似 webpack/vite 配置）

## 如何添加依赖

一些专门为 Spring Boot Starter 定制的库，可在 IDEA 的 pom.xml 文件上选择添加启动器。对于其他第三方插件，需手动添加。

> **Maven 设计理念：** pom.xml 是项目的声明式配置文件，需要手动编辑来管理依赖，不能通过命令行安装。

### 方案一：手动修改 pom.xml

例如添加第三方库：`logstash-logback-encoder`

1. 访问地址（更常用，可以看到被使用次数）：<https://mvnrepository.com/>，或访问官方地址：<https://central.sonatype.com/>
2. 搜索 `net.logstash.logback:logstash-logback-encoder`
3. 在 pom.xml 中添加配置：

```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

4. 安装依赖

### 方案二：使用 IDEA 来管理依赖

1. 打开 `pom.xml` → 右键 → **Generate**（生成）→ **Dependency**（依赖项）
2. 输入关键词 `net.logstash.logback:logstash-logback-encoder` 搜索 Maven Central，选择后自动添加到 `pom.xml`

## 基础结构

### 1. XML 声明和 project 根元素

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <!-- 项目配置内容 -->
</project>
```

要点：

- `modelVersion` 固定为 `4.0.0`（当前 Maven POM 版本）
- XML 命名空间声明可以直接复制使用

### 2. 项目坐标（GAV）

```xml
<groupId>com.macro.mall</groupId>
<artifactId>mall</artifactId>
<version>1.0-SNAPSHOT</version>
<packaging>pom</packaging>
```

前端类比：

```json
{
  "name": "@macro/mall",
  "version": "1.0.0-snapshot"
}
```

**GAV 坐标详解：**

| 元素         | 说明          | 前端类比     | 示例                  |
| ------------ | ------------- | ------------ | --------------------- |
| `groupId`    | 组织/公司域名 | npm scope    | `com.macro.mall`      |
| `artifactId` | 项目名称      | package name | `mall-admin`          |
| `version`    | 版本号        | version      | `1.0-SNAPSHOT`        |
| `packaging`  | 打包方式      | -            | `jar` / `war` / `pom` |

**版本号约定：**

- `1.0-SNAPSHOT`：开发版本（类似 `1.0.0-beta`）
- `1.0.0`：正式发布版本

**packaging 类型：**

- `jar`：Java 应用程序（默认）
- `war`：Web 应用（部署到 Tomcat）
- `pom`：父项目/多模块项目（不生成 jar，仅用于管理）

### 3. 父项目继承

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.14</version>
    <relativePath/>
</parent>
```

前端类比：类似于继承一个基础配置（如 `@vue/cli-service` 或 CRA 的 `react-scripts`）

**作用：**

- 继承 Spring Boot 的依赖版本管理
- 继承默认插件配置
- 继承编译参数和资源处理规则

**relativePath：**

- 空标签表示从 Maven 仓库查找父 POM
- 不为空时表示父 POM 的相对路径，默认为 `../pom.xml`（多模块项目常用）

## 依赖管理

### 1. properties（变量定义）

```xml
<properties>
    <java.version>17</java.version>
    <mysql-connector.version>9.3.0</mysql-connector.version>
    <hutool.version>5.8.40</hutool.version>
</properties>
```

前端类比：类似 `package.json` 中自定义变量或 webpack 的环境变量。

**使用变量：**

```xml
<version>${hutool.version}</version>
```

**内置属性：**

- `${project.version}`：当前项目版本
- `${project.build.finalName}`：构建产物名称
- `${project.basedir}`：项目根目录

### 2. dependencies（直接依赖）

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

前端类比：

```json
{
  "dependencies": {
    "vue": "^3.4.0"
  }
}
```

**optional 详解：**

```xml
<optional>true</optional>
```

可选依赖，不会传递。

**scope 6 种值详解：**

| Maven 标签        | 说明                                                   | 经典场景                                                                                                                                                                                                     |
| ----------------- | ------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `compile`（默认） | 编译+运行+打包（全生命周期）                           | 绝大多数依赖。比如 `spring-boot-starter-web`、`spring-boot-starter-data-jpa`、`jjwt-api`。如果不写 scope，默认就是它                                                                                         |
| `test`            | 仅测试时使用                                           | 单元测试/集成测试。比如 `spring-boot-starter-test`、`junit-jupiter`。这些库只在本地跑 `mvn test` 时用，线上运行时完全不需要                                                                                  |
| `provided`        | 编译、测试，但不打包（运行时由外部容器提供）           | Servlet API。编译时需要 `HttpServletRequest`、`HttpServletResponse` 等类，但部署到 Tomcat / Jetty 时这些类由容器自带的 `servlet-api.jar` 提供。不需要手动加，因为 `spring-boot-starter-web` 已经帮你处理好了 |
| `runtime`         | 运行和测试时（编译时不需要）                           | JDBC 驱动实现。比如 `mysql-connector-j`。你的代码里不会 import 这个驱动的具体类，而是通过 `Class.forName` 或 SPI 机制在运行时动态加载                                                                        |
| `system`          | 同 `provided`，但依赖本地磁盘文件（不在 Maven 仓库中） | 几乎不用                                                                                                                                                                                                     |
| `import`          | 只在 `dependencyManagement` 中使用，不参与打包         | BOM（物料清单）。比如在微服务中引入 `spring-cloud-dependencies`，将所有 Spring Cloud 组件的版本号统一管理                                                                                                    |

**依赖版本号：**

```xml
<!-- 明确指定版本 -->
<version>5.8.40</version>

<!-- 不指定版本（从 parent 或 dependencyManagement 继承） -->
<!-- 省略 <version> 标签 -->
```

### 3. dependencyManagement（依赖版本管理）

这是 Maven 的核心特性，前端没有直接对应物！

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
            <version>5.8.40</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**作用：**

1. 仅声明版本，不实际引入依赖
2. 子模块可以不写 `<version>`，自动使用这里定义的版本
3. 统一管理多模块项目的依赖版本

**对比示例：**

```xml
<!-- 父 POM：仅声明版本 -->
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
            <version>5.8.40</version>
        </dependency>
    </dependencies>
</dependencyManagement>

<!-- 子模块：无需写版本号 -->
<dependencies>
    <dependency>
        <groupId>cn.hutool</groupId>
        <artifactId>hutool-all</artifactId>
        <!-- 版本自动继承为 5.8.40 -->
    </dependency>
</dependencies>
```

**最佳实践：**

- 父 POM 用 `<dependencyManagement>` 统一版本
- 子模块用 `<dependencies>` 声明实际需要的依赖
- 避免版本冲突和重复定义

## 多模块项目

### 1. 父 POM 配置

```xml
<packaging>pom</packaging>

<modules>
    <module>mall-common</module>
    <module>mall-mbg</module>
    <module>mall-admin</module>
</modules>
```

前端类比：类似 monorepo（pnpm workspace 或 yarn workspace）

```json
{
  "workspaces": ["packages/common", "packages/admin", "packages/portal"]
}
```

**项目结构：**

```
mall/
├── pom.xml                 (父 POM, packaging=pom)
├── mall-common/
│   └── pom.xml             (子模块)
├── mall-admin/
│   └── pom.xml             (子模块)
└── mall-portal/
    └── pom.xml             (子模块)
```

### 2. 子模块 POM 配置

```xml
<parent>
    <groupId>com.macro.mall</groupId>
    <artifactId>mall</artifactId>
    <version>1.0-SNAPSHOT</version>
    <relativePath>../pom.xml</relativePath>
</parent>

<artifactId>mall-admin</artifactId>

<!-- groupId 和 version 继承自父 POM -->
```

## 构建配置

### 1. build 和 pluginManagement（构建和插件管理）

#### `<build><plugins>` — 实际执行的插件

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

`maven-compiler-plugin` 放在 `<plugins>` 中：

- 编译是通用的、必需的操作
- 所有模块（包括库模块 `mall-common`、`mall-mbg`）都需要被编译
- 所以在父 POM 中强制执行，所有子模块继承这个编译配置

#### `<build><pluginManagement>` — 仅声明版本，子模块可以继承

```xml
<build>
    <pluginManagement>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
            <plugin>
                <groupId>io.fabric8</groupId>
                <artifactId>docker-maven-plugin</artifactId>
                <version>${docker.maven.plugin.version}</version>
            </plugin>
        </plugins>
    </pluginManagement>
</build>
```

`spring-boot-maven-plugin` 放在 `<pluginManagement>` 中：

- 打包成可执行 jar 不是所有模块都需要
- `mall-common`、`mall-mbg` 是库模块，不需要打包成独立的 jar（只作为依赖被引用）
- 只有 `mall-admin`、`mall-portal`、`mall-search` 这样的应用模块才需要可执行 jar
- 所以在父 POM 中仅声明版本，让需要的模块自己选择使用

在这里 `spring-boot-maven-plugin` 并没有声明版本号，是因为父 POM：

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.14</version>
</parent>
```

`spring-boot-starter-parent` 已经在它的 `pluginManagement` 中声明了 `spring-boot-maven-plugin` 的版本，所以在这个项目中继承了这个版本。

### 2. maven-compiler-plugin 和 spring-boot-maven-plugin

**maven-compiler-plugin：**

- 作用：将 Java 源代码编译成字节码（`.class` 文件）
- 类似于前端的 Babel — 源代码转译工具
- 例：`javac` 命令的包装

**spring-boot-maven-plugin：**

- 作用：打包应用、生成可执行 jar、管理依赖
- 类似于前端的 webpack/Vite — 构建和打包工具
- 例：`mvn package` 时的最终产物是可执行 jar

**完整的编译打包流程对比：**

```
Java              前端
─────────────────────────────────
源代码            TypeScript/ES6+
  ↓                 ↓
maven-compiler-plugin    Babel/TypeScript
  ↓                 ↓
.class 字节码       JavaScript
  ↓                 ↓
spring-boot-maven-plugin   webpack/Vite
  ↓                 ↓
可执行 jar          bundle.js（可在浏览器运行）
```

## Profiles（环境配置）

### 什么是 profiles？

`<profiles>` 允许你定义**多组配置**，通过激活不同的 profile 来切换构建行为。

前端类比：类似于前端的 `.env` 文件机制：

| 前端                           | Maven                    | 作用         |
| ------------------------------ | ------------------------ | ------------ |
| `.env.development`             | `<profile><id>dev</id>`  | 开发环境配置 |
| `.env.production`              | `<profile><id>prod</id>` | 生产环境配置 |
| `VITE_API_URL=xxx`             | `<haborRepository>xxx`   | 环境变量     |
| `npm run build --mode staging` | `mvn package -P staging` | 激活指定环境 |

### 基本语法

```xml
<profiles>
    <profile>
        <id>dev</id>
        <activation>
            <activeByDefault>true</activeByDefault>
        </activation>
        <properties>
            <profileActive>dev</profileActive>
            <selfVersion>1.0.40</selfVersion>
            <haborRepository>dockerhubbs.example.com/finchina-dev/${project.artifactId}</haborRepository>
        </properties>
    </profile>
    <profile>
        <id>uat</id>
        <properties>
            <profileActive>uat</profileActive>
            <selfVersion>1.0.40</selfVersion>
            <haborRepository>dockerhubbs.example.com/qyyjt-uat/${project.artifactId}</haborRepository>
        </properties>
    </profile>
    <profile>
        <id>prod</id>
        <properties>
            <profileActive>prod</profileActive>
            <selfVersion>1.0.40</selfVersion>
            <haborRepository>dockerhubbs.example.com/finchina-prod/${project.artifactId}</haborRepository>
        </properties>
    </profile>
</profiles>
```

激活方式：

```bash
# 激活指定 profile
mvn clean package -P dev
mvn clean package -P uat
mvn clean package -P prod

# 不指定时，activeByDefault=true 的 profile 自动生效（此处为 dev）
```

`<properties>` 中定义的变量可在 pom.xml 其他地方通过 `${变量名}` 引用：

```xml
<!-- 引用 profile 中定义的变量 -->
<version>${selfVersion}</version>
<image>${haborRepository}:${selfVersion}</image>
```

### 实战场景：多环境 Docker 镜像构建

以上面的配置为例，同一份代码通过激活不同的 profile，将 Docker 镜像推送到不同的 Harbor 仓库：

```
                源代码（同一份）
                     │
        ┌────────────┼────────────┐
        ▼            ▼            ▼
  mvn -P dev    mvn -P uat   mvn -P prod
        │            │            │
        ▼            ▼            ▼
 ┌──────────┐  ┌──────────┐  ┌──────────┐
 │   dev    │  │   uat    │  │   prod   │
 │          │  │          │  │          │
 │ version: │  │ version: │  │ version: │
 │ 1.0.40   │  │ 1.0.40   │  │ 1.0.40   │
 │          │  │          │  │          │
 │ 推送到:  │  │ 推送到:  │  │ 推送到:  │
 │finchina- │  │qyyjt-    │  │finchina- │
 │dev/      │  │uat/      │  │prod/     │
 │{artifact}│  │{artifact}│  │{artifact}│
 └──────────┘  └──────────┘  └──────────┘
```

**完整的 CI/CD 流水线（以 dev 环境为例）：**

```
① 代码提交 / 合并到 dev 分支
      │
      ▼
② Jenkins / GitLab CI 触发流水线
      │
      ▼
③ mvn clean package -P dev          ← 激活 dev profile
      │                               读取 profile 中的 properties
      ▼
④ spring-boot-maven-plugin 打出可执行 jar
      │
      ▼
⑤ docker-maven-plugin 构建 Docker 镜像
   镜像标签 = dockerhubbs.example.com/finchina-dev/{artifactId}:1.0.40
      │
      ▼
⑥ docker push → 推送到 Harbor 的 finchina-dev 仓库
      │
      ▼
⑦ Kubernetes / Docker Swarm 从 Harbor 拉取镜像并部署
```

切换到其他环境只需改变 profile 参数：

```bash
# 开发环境 → 推送到 finchina-dev 仓库
mvn clean package -P dev

# 测试环境 → 推送到 qyyjt-uat 仓库
mvn clean package -P uat

# 生产环境 → 推送到 finchina-prod 仓库
mvn clean package -P prod
```

### activation（激活条件）

除了手动 `mvn -P` 指定，profile 还支持**自动激活**：

```xml
<profile>
    <id>dev</id>
    <activation>
        <!-- 方式 1：默认激活（不指定 -P 时生效） -->
        <activeByDefault>true</activeByDefault>

        <!-- 方式 2：JDK 版本匹配时激活 -->
        <jdk>[17,)</jdk>

        <!-- 方式 3：操作系统匹配时激活 -->
        <os>
            <name>Windows</name>
            <family>windows</family>
        </os>

        <!-- 方式 4：系统属性存在/匹配时激活 -->
        <property>
            <name>env</name>
            <value>dev</value>
        </property>

        <!-- 方式 5：文件存在时激活 -->
        <file>
            <exists>src/main/resources/application-dev.yml</exists>
        </file>
    </activation>
</profile>
```

| 激活方式          | 说明                      | 典型场景                       |
| ----------------- | ------------------------- | ------------------------------ |
| `activeByDefault` | 未指定 `-P` 时默认生效    | dev 环境作为本地开发默认配置   |
| `jdk`             | JDK 版本匹配时激活        | JDK 17+ 使用新依赖，JDK 8 兼容 |
| `os`              | 操作系统匹配时激活        | Windows/Linux 使用不同本地路径 |
| `property`        | `-D` 传入的属性匹配时激活 | `mvn -Denv=prod` 激活 prod     |
| `file`            | 指定文件存在时激活        | 检测到配置文件自动切换环境     |

### Maven profiles vs Spring Boot profiles

这是一个容易混淆的概念，两者**同名但完全不同**：

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                 │
│  Maven profiles                    Spring Boot profiles          │
│  ──────────────                    ────────────────────          │
│                                                                 │
│  作用于：构建时                     作用于：运行时                │
│  配置位置：pom.xml                  配置位置：application-{x}.yml│
│  激活方式：mvn -P dev              激活方式：                    │
│            java -jar app.jar --spring.profiles.active=prod      │
│  决定什么：                       决定什么：                     │
│    构建产物形态                      数据库连接串                 │
│    依赖集合                          日志级别                     │
│    插件行为                          第三方服务地址               │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

| 对比维度         | Maven profiles                    | Spring Boot profiles                              |
| ---------------- | --------------------------------- | ------------------------------------------------- |
| 生效时机         | **构建时**（`mvn` 命令执行期间）  | **运行时**（`java -jar` 启动期间）                |
| 配置文件         | `pom.xml`                         | `application-dev.yml` / `application-prod.yml`    |
| 激活命令         | `mvn package -P dev`              | `java -jar app.jar --spring.profiles.active=prod` |
| 决定什么         | 构建产物形态、依赖集合、插件行为  | 数据库连接串、日志级别、第三方服务地址            |
| 前端类比         | `npm run build --mode production` | `process.env.NODE_ENV` 运行时读取                 |
| 产物是否绑定环境 | 是（不同 profile 打出不同的包）   | 否（同一个包，启动时决定行为）                    |

**现代 Spring Boot 项目的推荐做法：**

```
┌─────────────────────────────────────────────────────────────────┐
│                    环境配置职责分离                                │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  pom.xml（Maven profiles）         只管「构建」                  │
│  ┌──────────────────────────────┐                               │
│  │ • Docker 镜像推送地址         │                               │
│  │ • 不同 JDK 版本的依赖适配     │  ← 构建时决定                │
│  │ • CI/CD 构建变体              │                               │
│  │ • 是否启用代码混淆/加固       │                               │
│  └──────────────────────────────┘                               │
│                                                                 │
│  application-{env}.yml（Spring Boot profiles）  只管「运行」     │
│  ┌──────────────────────────────┐                               │
│  │ • 数据库连接串                │                               │
│  │ • Redis 地址                  │  ← 运行时决定                │
│  │ • 日志级别                    │                               │
│  │ • 第三方 API 地址             │                               │
│  └──────────────────────────────┘                               │
│                                                                 │
│  优势：打出一个通用的 jar，部署到任何环境都能通过启动参数切换配置  │
│  java -jar app.jar --spring.profiles.active=prod                │
│  java -jar app.jar --spring.profiles.active=uat                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 替代方案

Maven profiles 不是唯一的选择，根据场景可以选用更合适的方式：

| 方案               | 做法                                    | 优点                 | 缺点             | 适合场景           |
| ------------------ | --------------------------------------- | -------------------- | ---------------- | ------------------ |
| **Maven profiles** | `pom.xml` 中定义多组配置，`mvn -P` 激活 | 集中可读、语义明确   | 配置提交到代码库 | 环境固定、团队协作 |
| **命令行 `-D`**    | `mvn package -Denv=dev -Dregistry=xxx`  | 灵活、无需改 pom.xml | 参数多时命令冗长 | 临时构建、实验     |
| **CI/CD 平台变量** | GitLab CI / Jenkins 中配置环境变量      | 敏感信息安全加密     | 本地开发无法复用 | 纯 CI/CD 场景      |
| **Shell 脚本封装** | `build.sh` 中 `case $ENV` 映射不同配置  | 逻辑集中、可复用     | 多一层抽象       | 构建流程复杂时     |

> **选择建议：** 环境少且固定（如 dev/uat/prod）→ profiles；环境多且经常变动 → CI 平台变量；只是临时测试 → 命令行 `-D`；涉及密码、密钥等敏感信息 → CI 平台变量（profiles 会提交到代码库）。

## 仓库配置

### repositories（依赖仓库）

- 下载项目使用的依赖库（jar 文件）
- 例如：`spring-boot-starter-web`、`hutool-all`、`mysql-connector-j`、`lombok`
- 这些库最终会被打包进 jar 中或在运行时使用，类似于前端 axios 等第三方库会被打包进 `bundle.js`

```xml
<repositories>
    <repository>
        <id>aliyunmaven</id>
        <name>aliyun</name>
        <url>https://maven.aliyun.com/repository/public</url>
    </repository>
    <repository>
        <id>central2</id>
        <name>central2</name>
        <url>https://repo1.maven.org/maven2/</url>
    </repository>
</repositories>
```

先从阿里云镜像中查找，如果没有找到，再从官方的 Maven 中央仓库查找。

### pluginRepositories（插件仓库）

- 下载 Maven 构建插件本身
- 例如：`maven-compiler-plugin`、`spring-boot-maven-plugin`、`docker-maven-plugin`
- 这些是构建工具，不是项目的运行依赖，类似于前端的 webpack、Vite 等不会被打包的第三方库

```xml
<pluginRepositories>
    <pluginRepository>
        <id>aliyunmaven</id>
        <name>aliyun</name>
        <url>https://maven.aliyun.com/repository/public</url>
    </pluginRepository>
    <pluginRepository>
        <id>central2</id>
        <name>central2</name>
        <url>https://repo1.maven.org/maven2/</url>
    </pluginRepository>
</pluginRepositories>
```

先从阿里云镜像中查找，如果没有找到，再从官方的 Maven 中央仓库查找。

## Maven 阶段

| 命令       | 作用                                         |
| ---------- | -------------------------------------------- |
| `clean`    | 清理 `target` 编译目录                       |
| `validate` | 验证项目结构和 pom.xml 是否正确              |
| `compile`  | 编译源代码                                   |
| `test`     | 运行测试                                     |
| `package`  | 打包生成 jar/war                             |
| `verify`   | 对集成测试结果做检查                         |
| `install`  | 将 jar 复制到本地 Maven 仓库，给其他项目引用 |
| `site`     | 生成项目站点文档和报告                       |
| `deploy`   | 部署到远程仓库                               |
