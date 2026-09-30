# Tasks: create-kubernetes-guide

## 1. 前置核实与准备

- [x] 1.1 核实 kind 当前支持的 k8s 节点镜像版本，锁定指南 k8s 大版本（如 1.32.x）并写入开头
- [x] 1.2 核对微服务指南基线（Nacos 3.0.3、MySQL 8.0、Spring Boot 3.5.14、端口与配置），确保部署清单与之对齐

## 2. 指南撰写（第 1~6 章：全景 + 核心对象）

- [x] 2.1 开头：读者契约、学习目标、版本锚定、范围外声明、与微服务指南的姊妹篇关系
- [x] 2.2 第 1 章：全景图——"为什么需要 k8s"问题驱动、Compose ↔ k8s 对照总表、kind 集群搭建（`kind-config.yaml`）
- [x] 2.3 第 2 章：Pod——最小部署单元，含首个最小 YAML 清单
- [x] 2.4 第 3 章：Deployment——副本、滚动更新、回滚
- [x] 2.5 第 4 章：Service + Ingress——服务暴露与负载均衡（含单节点 Ingress 适配说明）
- [x] 2.6 第 5 章：ConfigMap / Secret——配置与密钥
- [x] 2.7 第 6 章：PV / PVC——MySQL 有状态部署

## 3. 指南撰写（第 7~9 章：部署主线 + 决策 + 生产实践）

- [x] 3.1 第 7 章：电商三服务上 k8s——三服务 + Nacos + 3×MySQL 逐个 `kubectl apply`，Nacos Compose healthcheck → 探针翻译演示，末尾给汇总清单
- [x] 3.2 第 8 章：十字路口——Nacos vs k8s Service 决策章（两条路线定位、对比表、哪些 Spring Cloud 组件可退休）
- [x] 3.3 第 9 章：生产实践——探针、优雅下线、滚动发布策略

## 4. 指南撰写（第 10~12 章：决策 + 速查 + 延伸）

- [x] 4.1 第 10 章：实战决策——什么该上 k8s、什么不该上
- [x] 4.2 第 11 章：速查清单——kubectl 常用命令 + YAML 模板骨架
- [x] 4.3 第 12 章：延伸阅读——Helm / HPA / RabbitMQ、Kafka、SkyWalking 上 k8s 导航与范围外说明

## 5. 校验与收尾

- [x] 5.1 运行 `py -3 .agents/skills/guide-writing/scripts/validate_guide.py docs/Kubernetes/kubernetes-guide.md` 确保结构校验通过
- [x] 5.2 复核目录锚点、章节编号、前后引用一致
- [x] 5.3 对照 spec 的 Requirement 逐条自查，交付 Verification summary（示例证据状态如实分级）
