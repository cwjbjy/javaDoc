# Proposal: create-kubernetes-guide

## Why

`docs/` 已覆盖从 Java 基础、Spring Boot 单体到 Spring Cloud 微服务的完整知识链，但缺少"容器化部署"这一环。`docs/Spring/spring-cloud-microservices-guide.md` 的电商三服务目前仅通过 Docker Compose 在单机部署，其学习路线建议第 4 步明确将"容器化部署"列为生产准备目标，而整库检索显示 Kubernetes 相关内容为 0。Kubernetes 是生产环境部署的事实标准，项目读者需要一篇承接微服务指南、把同一「电商三服务」搬上 k8s 的实战指南。

## What Changes

- 新增 `docs/Kubernetes/kubernetes-guide.md`（新建 `docs/Kubernetes/` 目录）：hybrid 结构指南——前半部分并行讲解 k8s 核心对象（Pod / Deployment / Service / Ingress / ConfigMap / Secret / PV / PVC），后半部分因果推进「电商三服务」部署链。
- 主线部署：原样搬运 user/product/order 三服务 + Nacos + 3×MySQL（沿用微服务指南的镜像与配置基线），服务代码零改动，专注学习 k8s 本身。
- 专设「Nacos vs k8s Service」决策章，讨论哪些 Spring Cloud 组件上 k8s 后可以退休（k8s 原生服务发现、ConfigMap 替代配置中心）。
- RabbitMQ / Kafka / SkyWalking 上 k8s 归入延伸阅读（有状态负载是独立大主题），不在主线展开。
- 本地集群默认 kind（`kind create cluster` + `kind-config.yaml`）；k8s 版本锁定具体大版本，撰写时核实 kind 支持的节点镜像后确定。
- 遵循 guide-writing skill 规范，运行 `validate_guide.py` 做结构校验；沿用仓库既有指南风格（问题驱动、三步接入、生产实践、速查清单、延伸阅读）。

## Capabilities

### New Capabilities

- `kubernetes-guide`: 定义 Kubernetes 部署实战指南的内容契约——指南定位（微服务指南的容器化部署续篇）、hybrid 章节结构、主线迁移范围（三服务 + Nacos + MySQL）、Nacos vs k8s Service 决策章、本地集群与版本锚定、与既有文档的边界。

### Modified Capabilities

<!-- 无既有 spec 的需求变化 -->

## Impact

- 新增文档：`docs/Kubernetes/kubernetes-guide.md`（新建 `docs/Kubernetes/` 目录）。
- 不引入任何依赖、不改动 `src/` 与 `pom.xml`；指南内的 YAML 均为部署清单，服务于本地学习集群。
- 与 `docs/Spring/spring-cloud-microservices-guide.md` 形成姊妹篇关系：k8s 指南复用其电商三服务场景与镜像基线；后续可在该指南「关联阅读」中加互链（可选小改动）。
