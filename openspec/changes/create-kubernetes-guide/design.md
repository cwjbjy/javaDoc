# Design: create-kubernetes-guide

## Context

`docs/` 的知识链已到 Spring Cloud 微服务：`docs/Spring/spring-cloud-microservices-guide.md`（约 2900 行）以「电商三服务」（user/product/order）贯穿全文，基础设施（Nacos 3.0.3、3×MySQL 8.0、Sentinel、SkyWalking、RabbitMQ、Kafka）通过 Docker Compose 单机部署，学习路线第 4 步将"容器化部署"列为生产准备目标。整库检索显示 Kubernetes 内容为 0。本指南承接该场景，把同一套服务搬上 k8s，是姊妹篇而非替代。

仓库指南风格已定型：问题驱动开头 → 核心概念 → 三步接入 → 生产实践 → 本章回顾 → 速查清单 → 延伸阅读，中文叙述、ASCII 图示、版本锚定、示例诚实分级。guide-writing skill 提供了 hybrid 形状的判定标准与内容契约。

## Goals / Non-Goals

**Goals:**

- 产出一篇 hybrid 指南：前半部分并行讲解 k8s 核心对象，后半部分因果推进「电商三服务」部署链，读者按顺序读完即可独立完成一次真实迁移。
- 以 Docker Compose 为全程对照锚点，每个 k8s 概念用"Compose 里你这样写，k8s 里这样写"的方式引入（Compose service ↔ Deployment+Service、volume ↔ PV/PVC、environment ↔ ConfigMap/Secret、ports ↔ Service/Ingress）。
- 主线部署：原样搬运三服务 + Nacos + 3×MySQL，沿用微服务指南的镜像与配置基线（Nacos 3.0.3、MySQL 8.0、Spring Boot 3.5.14），服务代码零改动。
- 专设「Nacos vs k8s Service」决策章，回答"上 k8s 后哪些 Spring Cloud 组件可以退休"。
- 本地集群默认 kind（`kind-config.yaml` 配置即文档）；k8s 版本锁定具体大版本。
- 示例诚实分级，运行 `validate_guide.py` 结构校验。

**Non-Goals:**

- 不在主线展开 RabbitMQ / Kafka / SkyWalking 上 k8s（有状态负载、StatefulSet 是独立大主题），仅延伸阅读导航。
- 不覆盖 Helm 打包、HPA 弹性伸缩、CI/CD 流水线、RBAC 多租户（延伸阅读/导航即可）。
- 不修改三服务的业务代码，不改动 `src/` 与 `pom.xml`。
- 不重讲 Spring Cloud 各组件的原理——引用微服务指南对应章节，保留最小上下文。

## Decisions

- **指南形状：hybrid。** k8s 核心对象（Pod/Deployment/Service/Ingress/ConfigMap/Secret/PV/PVC）是并行类别，适合 reference 式并列讲解；"把服务部署上去"是因果链（Pod → Deployment → Service → Ingress），必须按序推进。与 guide-writing skill 中 k8s 示例的 hybrid 判定一致。
- **章节结构（12 章）：**
  1. 全景图：从 Docker Compose 到 Kubernetes（问题驱动开场，给出 Compose ↔ k8s 对照总表）
  2. Pod：最小部署单元
  3. Deployment：副本、滚动更新、回滚
  4. Service + Ingress：服务暴露与负载均衡
  5. ConfigMap / Secret：配置与密钥
  6. PV / PVC：MySQL 有状态部署
  7. 电商三服务上 k8s（主线部署：三服务 + Nacos + MySQL 逐个 `kubectl apply`）
  8. 十字路口：Nacos vs k8s Service（决策章）
  9. 生产实践：探针、优雅下线、滚动发布策略
  10. 实战决策：什么该上 k8s、什么不该上
  11. 速查清单：kubectl 命令 + YAML 模板速查
  12. 延伸阅读：Helm / HPA / RabbitMQ、Kafka、SkyWalking 上 k8s
- **主线策略：先原样搬运，后讨论演进。** 主线保留 Nacos 注册发现，服务代码零改动，读者专注学习 k8s 对象本身；第 8 章再给出纯 k8s 原生方案（Service + DNS 服务发现、ConfigMap 替代 Nacos Config）的改造路径与取舍，并明确"主线=学习路径、第 8 章=演进路径"，避免读者混淆推荐。
- **对照锚点：Docker Compose。** 读者刚在微服务指南里用过 Compose，这是最省认知成本的迁移锚点；贯穿全文而非只在开头对比一次。
- **本地集群：kind。** 用户是 Compose 用户，Docker 必然在位；kind 零额外 VM、`kind-config.yaml` 配置即文档、销毁重建成本低。备选 minikube（经典但状态易残留）、Docker Desktop 内置 k8s（版本随桌面版走、重置易翻车）均不采用。
- **版本锚定：k8s 锁定具体大版本。** 撰写时核实 kind 当前支持的节点镜像（如 1.32.x）后锁定，YAML API 版本（`apps/v1`、`networking.k8s.io/v1`）以该版本官方文档为准；Spring Boot 3.5.14 / Nacos 3.0.3 / MySQL 8.0 沿用微服务指南基线。
- **部署清单：内嵌指南。** 沿用微服务指南内嵌 `docker-compose.yml` 的风格，YAML 清单以代码块内嵌于对应章节，逐步增量演进（从单个 Pod 到完整三服务），不额外新建 `deploy/` 目录；如需可整体复制，在第 7 章给出汇总清单。
- **写作风格：沿用仓库指南 DNA。** 问题驱动、三步接入、生产实践、本章回顾、速查清单、延伸阅读；示例诚实分级（本地集群未实际验证的标注 Complete example, not yet verified）。

## Risks / Trade-offs

- [k8s 版本迭代快，YAML API 版本或默认行为可能变化] → 指南锁定具体大版本，API 版本与字段以该版本官方文档核实后再落笔，开头声明版本范围。
- [本地集群行为与生产有差异（单节点、无云 LB、Ingress 需额外控制器）] → 指南明确声明"学习集群 vs 生产集群"差异，涉及差异处（如 Ingress、LoadBalancer）给出单节点适配说明。
- [示例 YAML 依赖用户本机 kind 环境，无法在文档编写阶段全部实际验证] → 示例标注 Complete example, not yet verified；关键命令（kind create cluster、kubectl apply 输出）以官方文档核对。
- [主线"原样搬运 Nacos"与第 8 章"原生方案"存在路线分叉，读者可能困惑] → 第 7、8 章开头用定位说明（blockquote）明确两条路线的适用场景与先后关系。
- [Nacos 3.x 健康检查端点与多端口（8848/9848）需映射为 k8s 探针，Compose 的 healthcheck 不能直接照搬] → 第 7 章部署 Nacos 时专门演示 Compose healthcheck → k8s liveness/readinessProbe 的翻译过程，作为探针教学素材。
- [微服务指南后续更新可能导致场景、镜像版本漂移] → 指南开头声明基线来源（微服务指南第 1 章）与锚定版本，后续如漂移由读者对照基线调整。
