# Spec: kubernetes-guide

## ADDED Requirements

### Requirement: 指南定位与读者契约

指南 SHALL 面向已完成 Spring Cloud 微服务指南、熟悉 Docker Compose 的开发者，在开头明确读者画像、学习目标、版本锚定（k8s 具体大版本 + Spring Boot 3.5.14 / Nacos 3.0.3 / MySQL 8.0 基线）与范围外声明，并声明与 `spring-cloud-microservices-guide.md` 的姊妹篇关系。

#### Scenario: 读者契约要素齐全

- **WHEN** 读者打开指南开头
- **THEN** 能看到读者画像、学习目标、版本锚定、范围外声明与姊妹篇关系说明

#### Scenario: 版本范围有声明

- **WHEN** 读者阅读涉及 YAML API 版本或默认行为的内容
- **THEN** 指南已在前文声明锁定的 k8s 大版本，读者不会误用到其他版本的行为

### Requirement: Hybrid 章节结构

指南 SHALL 按设计定稿的 12 章结构组织：第 1 章全景图（Compose 到 k8s），第 2~6 章并行讲解核心对象（Pod、Deployment、Service+Ingress、ConfigMap/Secret、PV/PVC），第 7 章因果推进电商三服务部署链，第 8 章 Nacos vs k8s Service 决策，第 9 章生产实践（探针/优雅下线/滚动发布），第 10 章实战决策，第 11 章速查清单，第 12 章延伸阅读。核心对象章节 SHALL 各自独立成篇、可单独查阅，部署章节 SHALL 依赖前面建立的对象概念。

#### Scenario: 核心对象可并行查阅

- **WHEN** 读者只想查某类对象的用法（如 Service 暴露方式）
- **THEN** 可跳过其他对象章节直接阅读该章，不遇到未定义的前向引用

#### Scenario: 部署链按因果推进

- **WHEN** 读者按第 7 章顺序执行部署
- **THEN** 每个清单只依赖前面章节已讲解的对象，不出现跳跃

#### Scenario: 速查清单可独立使用

- **WHEN** 读者完成学习后需要快速回顾
- **THEN** 第 11 章以表格/模板形式汇总 kubectl 常用命令与 YAML 模板骨架

### Requirement: Docker Compose 对照贯穿全文

指南 SHALL 以 Docker Compose 为全程对照锚点，每个 k8s 核心概念引入时给出"Compose 里你这样写，k8s 里这样写"的对照，至少覆盖：Compose service ↔ Deployment+Service、named volume ↔ PV/PVC、environment ↔ ConfigMap/Secret、ports 映射 ↔ Service/Ingress、healthcheck ↔ liveness/readinessProbe。

#### Scenario: 概念有 Compose 对照

- **WHEN** 指南引入 Deployment、Service、ConfigMap、PV 等对象
- **THEN** 每个对象都有对应的 Compose 写法对照或差异说明

#### Scenario: 探针与 healthcheck 对照清晰

- **WHEN** 指南讲解 k8s 探针
- **THEN** 展示微服务指南中 Nacos 的 Compose healthcheck 如何翻译为 liveness/readinessProbe

### Requirement: 主线迁移范围与原样搬运

主线部署 SHALL 覆盖 user/product/order 三服务 + Nacos + 3×MySQL 的迁移，沿用微服务指南的镜像与配置基线（Nacos 3.0.3、MySQL 8.0、Spring Boot 3.5.14），服务业务代码零改动；RabbitMQ、Kafka、SkyWalking 上 k8s 不在主线展开。

#### Scenario: 主线清单完整可执行

- **WHEN** 读者按第 7 章逐个 `kubectl apply`
- **THEN** 能获得三服务 + Nacos + MySQL 全部部署清单，且清单不要求修改服务业务代码

#### Scenario: MQ 与可观测性仅导航

- **WHEN** 指南涉及 RabbitMQ / Kafka / SkyWalking 上 k8s
- **THEN** 仅在第 12 章延伸阅读给出导航与范围外说明，不展开 StatefulSet 等主线外内容

### Requirement: Nacos vs k8s Service 决策章

指南 SHALL 专设一章对比"保留 Nacos"与"k8s 原生服务发现（Service + DNS）"两条路线，覆盖：主线学习路径（保留 Nacos）的定位说明、原生方案的改造路径、取舍对比（代码改动量、组件精简、运维复杂度），并回答哪些 Spring Cloud 组件上 k8s 后可以退休（含 ConfigMap/Secret 替代 Nacos Config 的边界）。

#### Scenario: 两条路线定位清晰

- **WHEN** 读者进入决策章
- **THEN** 能用定位说明区分"主线=学习路径"与"本章=演进路径"，不混淆推荐

#### Scenario: 取舍有对比依据

- **WHEN** 读者面对"是否去掉 Nacos"的选型
- **THEN** 决策章提供对比表（改动量、收益、风险）供读者判断

### Requirement: 本地集群与版本锚定

指南 SHALL 默认使用 kind 作为本地集群，提供可直接使用的 `kind-config.yaml` 与创建/销毁命令；k8s 锁定具体大版本，YAML API 版本与字段以该版本官方文档为准；涉及与生产集群差异处（单节点、无云 LB、Ingress 控制器）给出适配说明。

#### Scenario: kind 集群可复现

- **WHEN** 读者按指南执行 `kind create cluster` 及配置
- **THEN** 能获得与指南一致的本地集群环境，命令输出有明确预期

#### Scenario: 生产差异有声明

- **WHEN** 指南涉及 Ingress、LoadBalancer 等与生产环境行为不同的对象
- **THEN** 给出"学习集群 vs 生产集群"的差异说明与单节点适配方案

### Requirement: 部署清单组织与示例诚实分级

部署清单 SHALL 以代码块内嵌于对应章节、逐步增量演进（从最小对象到完整三服务），第 7 章给出可整体复制的汇总清单；所有示例 SHALL 按 guide-writing content contract 诚实分级，未在本地集群实际验证的标注为未验证状态，不宣称已运行。

#### Scenario: 清单增量演进

- **WHEN** 读者按章节顺序学习 YAML
- **THEN** 每个清单在前一版基础上只增加一个对象的理解量，最终第 7 章获得完整清单

#### Scenario: 示例状态诚实

- **WHEN** 指南展示 YAML 与 kubectl 命令
- **THEN** 每个示例标注证据状态，未实际运行的明确标注，不宣称已验证

### Requirement: 写作风格与结构校验

指南 SHALL 遵循 guide-writing skill 的 content contract（依赖顺序、术语先定义后使用、导航按需添加），保持仓库既有指南风格（中文叙述、中文注释、ASCII 图示、问题驱动、三步接入、生产实践、本章回顾），并通过 `validate_guide.py` 结构校验。

#### Scenario: 结构校验通过

- **WHEN** 指南文件保存后运行 `validate_guide.py`
- **THEN** 校验器报告通过，目录锚点与章节编号有效

#### Scenario: 术语先定义后使用

- **WHEN** 读者按顺序阅读
- **THEN** 不会遇到"稍后解释"式的前向引用，每个 k8s 术语出现前其概念已建立或当场定义
