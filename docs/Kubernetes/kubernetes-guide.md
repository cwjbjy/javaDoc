# Kubernetes 部署实战指南（电商三服务上 k8s）

> 本指南承接 [Spring Cloud 微服务企业级指南](../Spring/spring-cloud-microservices-guide.md)，把同一「电商三服务」（user / product / order + Gateway）从 Docker Compose 搬上 Kubernetes。先并行掌握 k8s 核心对象（Pod、Deployment、Service、ConfigMap、PV），再因果推进完整迁移，最后直面「Nacos 还是 k8s Service」的架构十字路口。
>
> 当前项目基线：Kubernetes 1.36.x（kind 节点镜像 `kindest/node:v1.36.5`）/ Spring Boot 3.5.14 / Spring Cloud 2025.0.0 / Spring Cloud Alibaba 2025.0.0.0 / Nacos 3.0.3 / MySQL 8.0 / Java 17。本指南的 YAML 清单均以 k8s 1.36 官方文档为准；若你使用其他小版本，`apps/v1`、`networking.k8s.io/v1` 等 API 版本仍然一致。
>
> 面向读者：已完成 Spring Cloud 微服务指南、熟悉 Docker Compose 的开发者。本指南以 Compose 为全程对照锚点——每个 k8s 概念都用"Compose 里你这样写，k8s 里这样写"的方式引入。
>
> **范围外声明**：Helm 打包、HPA 弹性伸缩、CI/CD、RBAC 多租户不在主线展开（§12 导航）；RabbitMQ / Kafka / SkyWalking 上 k8s 归入延伸阅读（§12.3、§12.4）；本指南针对本地学习集群，生产集群差异在相关章节标注。
>
> **环境要求**：Docker Desktop（含 Compose）、[kind](https://kind.sigs.k8s.io/)、[kubectl](https://kubernetes.io/zh-cn/docs/tasks/tools/)。三者均在本指南内给出安装与验证命令。
>
> **示例证据状态**：除特别标注外，本指南的 YAML 清单与命令均为"完整示例，未在本机 kind 集群实际运行"（Complete example, not yet verified）。写作时已对照 k8s 1.36 官方文档核实 API 版本与字段名。

---

## 目录

1. [全景图：从 Docker Compose 到 Kubernetes](#1-全景图从-docker-compose-到-kubernetes)
   - [1.1 问题：单机部署有什么短板](#11-问题单机部署有什么短板)
   - [1.2 一张图看懂 k8s 集群](#12-一张图看懂-k8s-集群)
   - [1.3 Compose 与 k8s 对照总表](#13-compose-与-k8s-对照总表)
   - [1.4 kind 三步搭建本地集群](#14-kind-三步搭建本地集群)
   - [1.5 第一个 kubectl 命令](#15-第一个-kubectl-命令)
   - [1.6 本章回顾](#16-本章回顾)
2. [Pod：最小部署单元](#2-pod最小部署单元)
   - [2.0 问题：容器能不能直接部署](#20-问题容器能不能直接部署)
   - [2.1 Pod 是什么](#21-pod-是什么)
   - [2.2 第一个 YAML：运行 nginx Pod](#22-第一个-yaml运行-nginx-pod)
   - [2.3 kubectl 常用命令](#23-kubectl-常用命令)
   - [2.4 生产实践：Pod 是牛不是宠物](#24-生产实践pod-是牛不是宠物)
   - [2.5 本章回顾](#25-本章回顾)
3. [Deployment：副本、滚动更新、回滚](#3-deployment副本滚动更新回滚)
   - [3.0 问题：Pod 挂了谁管](#30-问题pod-挂了谁管)
   - [3.1 声明式：期望状态驱动](#31-声明式期望状态驱动)
   - [3.2 第一个 Deployment](#32-第一个-deployment)
   - [3.3 滚动更新与回滚](#33-滚动更新与回滚)
   - [3.4 生产实践：副本数与资源配额](#34-生产实践副本数与资源配额)
   - [3.5 本章回顾](#35-本章回顾)
4. [Service 与 Ingress：服务暴露与负载均衡](#4-service-与-ingress服务暴露与负载均衡)
   - [4.0 问题：Pod IP 是临时的](#40-问题pod-ip-是临时的)
   - [4.1 Service 的三种类型](#41-service-的三种类型)
   - [4.2 DNS：服务名即地址](#42-dns服务名即地址)
   - [4.3 第一个 Service：ClusterIP](#43-第一个-serviceclusterip)
   - [4.4 Ingress：统一入口](#44-ingress统一入口)
   - [4.5 学习集群适配：kind 下如何访问](#45-学习集群适配kind-下如何访问)
   - [4.6 本章回顾](#46-本章回顾)
5. [ConfigMap 与 Secret：配置与密钥](#5-configmap-与-secret配置与密钥)
   - [5.0 问题：配置写死在镜像里](#50-问题配置写死在镜像里)
   - [5.1 ConfigMap：环境变量与挂载](#51-configmap环境变量与挂载)
   - [5.2 Secret：数据库密码](#52-secret数据库密码)
   - [5.3 使用方式：env 与 volume](#53-使用方式env-与-volume)
   - [5.4 生产实践：Secret 不是加密](#54-生产实践secret-不是加密)
   - [5.5 本章回顾](#55-本章回顾)
6. [PV 与 PVC：MySQL 有状态部署](#6-pv-与-pvcmysql-有状态部署)
   - [6.0 问题：MySQL 重启数据会丢吗](#60-问题mysql-重启数据会丢吗)
   - [6.1 存储模型：PV、PVC、StorageClass](#61-存储模型pvpvcstorageclass)
   - [6.2 kind 的本地存储方案](#62-kind-的本地存储方案)
   - [6.3 MySQL 部署实践](#63-mysql-部署实践)
   - [6.4 生产实践：StatefulSet 与数据库上 k8s 的争议](#64-生产实践statefulset-与数据库上-k8s-的争议)
   - [6.5 本章回顾](#65-本章回顾)
7. [电商三服务上 k8s](#7-电商三服务上-k8s)
   - [7.0 主线路线说明](#70-主线路线说明)
   - [7.1 整体规划：命名空间与镜像清单](#71-整体规划命名空间与镜像清单)
   - [7.2 基础设施：三个 MySQL 与 Nacos](#72-基础设施三个-mysql-与-nacos)
   - [7.3 三服务与网关部署](#73-三服务与网关部署)
   - [7.4 验证：注册、路由与调用](#74-验证注册路由与调用)
   - [7.5 汇总清单](#75-汇总清单)
   - [7.6 本章回顾](#76-本章回顾)
8. [十字路口：Nacos 还是 k8s Service](#8-十字路口nacos-还是-k8s-service)
   - [8.0 本章定位说明](#80-本章定位说明)
   - [8.1 上 k8s 后有什么变化](#81-上-k8s-后有什么变化)
   - [8.2 路线 A：保留 Nacos](#82-路线-a保留-nacos)
   - [8.3 路线 B：k8s 原生服务发现](#83-路线-bk8s-原生服务发现)
   - [8.4 对比表与选型建议](#84-对比表与选型建议)
   - [8.5 哪些 Spring Cloud 组件可以退休](#85-哪些-spring-cloud-组件可以退休)
   - [8.6 本章回顾](#86-本章回顾)
9. [生产实践：探针、优雅下线与滚动发布](#9-生产实践探针优雅下线与滚动发布)
   - [9.0 问题：k8s 怎么知道服务好了](#90-问题k8s-怎么知道服务好了)
   - [9.1 探针三件套](#91-探针三件套)
   - [9.2 Spring Boot 侧配合](#92-spring-boot-侧配合)
   - [9.3 滚动发布策略](#93-滚动发布策略)
   - [9.4 演练：滚动更新与回滚](#94-演练滚动更新与回滚)
   - [9.5 本章回顾](#95-本章回顾)
10. [实战决策：什么该上 k8s](#10-实战决策什么该上-k8s)
    - [10.1 上 k8s 的理由与代价](#101-上-k8s-的理由与代价)
    - [10.2 决策清单](#102-决策清单)
    - [10.3 常见误区](#103-常见误区)
11. [速查清单](#11-速查清单)
    - [11.1 kubectl 命令速查](#111-kubectl-命令速查)
    - [11.2 YAML 模板骨架速查](#112-yaml-模板骨架速查)
    - [11.3 Compose 与 k8s 对照速查](#113-compose-与-k8s-对照速查)
    - [11.4 常见故障排查速查](#114-常见故障排查速查)
12. [延伸阅读](#12-延伸阅读)
    - [12.1 Helm：包管理](#121-helm包管理)
    - [12.2 HPA：弹性伸缩](#122-hpa弹性伸缩)
    - [12.3 RabbitMQ、Kafka 上 k8s](#123-rabbitmqkafka-上-k8s)
    - [12.4 SkyWalking 上 k8s](#124-skywalking-上-k8s)
    - [12.5 关联阅读](#125-关联阅读)

---

## 1. 全景图：从 Docker Compose 到 Kubernetes

### 1.1 问题：单机部署有什么短板

在微服务指南里，整个电商系统是这样跑起来的：

```text
docker compose up -d
  → Nacos + 3×MySQL 启动
  → nacos-init 导入配置
  → 依次启动 gateway / user / product / order
  → 浏览器访问 http://localhost:8080
```

这套流程在**学习阶段**堪称完美：一条命令、零心智负担。但把它直接搬到生产环境，短板会逐一暴露：

```
Compose 单机部署的短板
════════════════════════════════════════════════════════════

① 无自愈       容器进程挂了，只能靠 restart: always 在同一台机器重试；
                机器宕机，整个系统下线，没有任何东西替你"把服务拉起来"

② 无弹性       双 11 要扩 10 个 order-service？docker compose scale 只是
                单机多开进程，CPU/内存天花板就是这一台机器

③ 发布粗糙     滚动更新、金丝雀、自动回滚在 Compose 里都要自己写脚本；
                服务更新时总有短暂不可用

④ 配置漂移     三个月后，测试环境 Compose 文件和生产环境的已经长得完全
                不一样——"在我机器上是好的"成为口头禅

⑤ 资源争抢     MySQL 和 order-service 跑在同一台机器上抢 CPU，
                一个慢查询能把整机拖垮
```

这些短板指向同一个结论：**缺一个"分布式操作系统"**。Compose 管理的是"一台机器上的容器"，生产需要管理的是"一群机器上的容器"。这就是 Kubernetes 的位置——它是集群的调度中枢，你只管声明"我想要 3 个 order-service 在跑"，它负责让这个状态**始终成立**。

> **前端视角**：Compose 像一个人手工部署前端到一台 Nginx 服务器；k8s 像一个 CI/CD 平台 + 负载均衡 + 监控告警 + 自动重启的全家桶。区别是后者对你是"声明"而不是"操作"。

### 1.2 一张图看懂 k8s 集群

在深入任何 YAML 之前，先看全景。一个 k8s 集群分两层：

```
┌──────────────────────── 控制平面（Control Plane） ────────────────────────┐
│                                                                            │
│   ┌──────────────┐   ┌───────────────┐   ┌────────────────┐               │
│   │  API Server  │   │  Scheduler    │   │ Controller Mgr │  ← 大脑       │
│   │  一切入口     │   │ 决定放哪台机器 │   │ 维持期望状态    │               │
│   └──────┬───────┘   └───────────────┘   └────────────────┘               │
│          │                                                                 │
│   ┌──────▼───────┐                                                         │
│   │     etcd     │  集群的"记忆"：所有对象都存在这里                        │
│   └──────────────┘                                                         │
└────────────────────────────────────────────────────────────────────────────┘
                              ▲
                              │ kubectl（你手里的遥控器）
                              │
┌──────────────────────── 工作节点（Worker Nodes） ─────────────────────────┐
│                                                                            │
│   ┌───────────────────────────────────────────────────────────────┐       │
│   │                        Node（一台机器）                         │       │
│   │                                                               │       │
│   │    kubelet（监工：保证该节点上的 Pod 状态正确）                   │       │
│   │                                                               │       │
│   │    ┌─────────┐   ┌─────────┐   ┌─────────┐                   │       │
│   │    │   Pod   │   │   Pod   │   │   Pod   │   ← 最小部署单元    │       │
│   │    │ ┌─────┐ │   │ ┌─────┐ │   │ ┌─────┐ │                   │       │
│   │    │ │容器  │ │   │ │容器  │ │   │ │容器  │ │                   │       │
│   │    │ └─────┘ │   │ └─────┘ │   │ └─────┘ │                   │       │
│   │    └─────────┘   └─────────┘   └─────────┘                   │       │
│   └───────────────────────────────────────────────────────────────┘       │
│                                                                            │
└────────────────────────────────────────────────────────────────────────────┘
```

记忆口诀：**etcd 是记忆，API Server 是嘴，Scheduler 是腿，Controller 是手，kubelet 是驻厂监工**。学习阶段你不需要操作控制平面的任何组件——kind 会替你搭好，你只通过 `kubectl` 和集群对话。

### 1.3 Compose 与 k8s 对照总表

这是本指南最重要的表。微服务指南里的 `docker-compose.yml` 每一段，在 k8s 里都有对应物：

| Compose 写法 | k8s 对应物 | 说明 |
|---|---|---|
| `services: order-service:` | Deployment（+ Service） | 一个服务 = 一个 Deployment 管副本 + 一个 Service 管访问 |
| `container_name:` | Pod 名 | 由 Deployment 自动生成 |
| `image:` | `spec.template.spec.containers[].image` | 概念一致 |
| `environment:` | ConfigMap / Secret | 配置外置，进容器的方式是 env 或文件挂载 |
| `ports: "8083:8083"` | Service / Ingress | k8s 端口分容器端口、Service 端口、对外入口三层 |
| `volumes:`（命名卷） | PV / PVC | 申请存储 → 绑定存储，解耦"谁要"和"哪来" |
| `depends_on:` + healthcheck | initContainer + 探针 | 启动顺序在 k8s 用不同机制表达（§7.2、§9.1） |
| `healthcheck:` | liveness/readinessProbe | 检查方式从"命令"升级为"探测类型"（§9.1） |
| `restart: always` | 内建自愈 | Deployment 天然保证副本数，无需声明 |
| `docker compose scale` | `spec.replicas` / HPA | 手动是 replicas，自动是 HPA（§12.2） |
| `docker compose up` | `kubectl apply -f` | 都是"让集群达到文件声明的状态" |
| 网络：服务名互 ping | k8s DNS | 服务名即域名，机制不同、体验一致（§4.2） |

> **关键区别**：Compose 的配置是"**过程**"（先启动谁、再启动谁）；k8s 的清单是"**期望状态**"（我要 3 个副本、要能访问、要存储）。至于怎么达到，是 k8s 的事。这个心智转换是第 3 章的主题。

### 1.4 kind 三步搭建本地集群

kind（Kubernetes in Docker）把 k8s 节点跑在 Docker 容器里。你已经是 Compose 用户，Docker 在位，kind 是最省事的本地集群方案：零额外虚拟机、配置即文档、一条命令销毁重建。

**第一步：安装 kind 与 kubectl**

```bash
# Windows（PowerShell 管理员，二选一）
winget install Kubernetes.kind
winget install Kubernetes.kubectl
# 或 macOS / Linux
brew install kind kubectl
```

验证安装：

```bash
kind version   # kind v0.2x.x
kubectl version --client
```

**第二步：用配置文件创建集群**

保存为 `kind-config.yaml`：

```yaml
# kind-config.yaml —— 学习用单节点集群（配置即文档）
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane # 单节点：控制平面与工作负载同一节点，够学习用
```

```bash
# 创建集群（k8s 版本锁定 1.36，与本指南基线一致）
kind create cluster --name ecommerce --config kind-config.yaml --image kindest/node:v1.36.5
```

> `--image` 显式锁定节点镜像版本。不指定时 kind 使用自带默认版本，可能与本指南基线漂移——学习时建议始终锁定。kind 官方镜像列表见 [kind 发布页](https://github.com/kubernetes-sigs/kind/releases)。

**第三步：验证集群健康**

```bash
kubectl cluster-info          # 能看到 control plane 地址
kubectl get nodes             # STATUS 应为 Ready
```

```
NAME                     STATUS   ROLES           AGE   VERSION
ecommerce-control-plane  Ready    control-plane   1m    v1.36.5
```

随时可以销毁重建（数据会丢，学习阶段这反而是优点）：

```bash
kind delete cluster --name ecommerce
```

### 1.5 第一个 kubectl 命令

`kubectl` 是你与集群对话的唯一渠道——没有 GUI 依赖，所有操作都是一条命令。先建立三个最常用的动作直觉：

```bash
kubectl get pods -A        # 查看所有命名空间下的 Pod（-A = all namespaces）
kubectl get namespaces     # 查看命名空间
kubectl api-resources      # 查看集群支持的所有资源类型
```

```
$ kubectl get pods -A
NAMESPACE            NAME                                         READY   STATUS
kube-system          coredns-...                                  1/1     Running
kube-system          etcd-ecommerce-control-plane                 1/1     Running
kube-system          kube-apiserver-ecommerce-control-plane       1/1     Running
kube-system          kube-controller-manager-...                  1/1     Running
kube-system          kube-proxy-...                               1/1     Running
kube-system          kube-scheduler-ecommerce-control-plane       1/1     Running
local-path-storage   local-path-provisioner-...                   1/1     Running
```

对照 §1.2 的全景图：`kube-system` 命名空间里跑的就是控制平面组件——在 kind 里它们也以 Pod 的形式存在（"驻厂监工"和"大脑"都以容器运行）。`local-path-storage` 是 kind 预置的存储服务（§6.2 会用到）。

命名空间（namespace）先记住一点：**它是集群内的隔离单位**，类似"文件夹"。Compose 的 `-p` 项目前缀是它最近的亲戚。本指南后续会创建 `ecommerce` 命名空间装电商系统。

### 1.6 本章回顾

- Compose 管"一台机器"，k8s 管"一群机器"；上 k8s 是为了自愈、弹性、发布与资源治理。
- 集群 = 控制平面（etcd/API Server/Scheduler/Controller）+ 工作节点（kubelet + Pod）。
- Compose 每个配置段都有 k8s 对应物，对照总表是后续所有章节的索引。
- kind 三步：安装 → `kind create cluster`（锁定 1.36）→ `kubectl get nodes` 验证。
- `kubectl` 是唯一入口；命名空间是隔离单位。

> **接下来**：§2 认识最小部署单元 Pod——它是所有 YAML 的起点。

---

## 2. Pod：最小部署单元

### 2.0 问题：容器能不能直接部署

Compose 里你部署的最小单位是"服务"（一个镜像 + 一套配置）。k8s 里，**容器不能直接部署**——必须包在 Pod 里。为什么多此一举？看两个真实场景：

```
场景 A：日志采集                        场景 B：本地共享
┌──────────────────┐                   ┌──────────────────┐
│  order-service   │                   │  order-service   │
│  ┌────────────┐  │                   │  ┌────────────┐  │
│  │ 业务容器    │──┼──写日志──▶ 挂载卷  │  │ 业务容器    │──┼──写文件──▶ 共享卷
│  └────────────┘  │                   │  └────────────┘  │
│  ┌────────────┐  │                   │  ┌────────────┐  │
│  │ 日志采集容器 │──┼──读日志──▶ 同一卷 │  │ 监控 Sidecar │──┼──读文件──▶ 同一卷
│  └────────────┘  │                   │  └────────────┘  │
└──────────────────┘                   └──────────────────┘
两个容器必须：同机部署、共享存储        两个容器必须：同机部署、共享 localhost 网络
```

如果一个容器只能单独调度，这些"天生一对"的容器就可能被拆到两台机器上。Pod 就是为此设计：**一组必须同生共死的容器 + 共享的资源（网络、存储）**，是调度的最小原子单位。

### 2.1 Pod 是什么

Pod 的结构拆开看：

```
┌────────────────────────── Pod ──────────────────────────┐
│                                                         │
│   ┌───────────────────────────────────────────────────┐ │
│   │         共享网络：一个 IP、共享 localhost、        │ │
│   │         共享端口空间（容器间用 localhost 互访）     │ │
│   └───────────────────────────────────────────────────┘ │
│                                                         │
│   ┌──────────────┐   ┌──────────────┐   ┌────────────┐  │
│   │  容器 A       │   │  容器 B       │   │  pause 容器 │  │
│   │ （业务主容器） │   │ （Sidecar 辅助）│   │ 占住网络命名空间│
│   └──────────────┘   └──────────────┘   └────────────┘  │
│                                                         │
│   共享卷（volume）：所有容器挂载同一个卷读写同一份数据      │
└─────────────────────────────────────────────────────────┘
```

三个要点：

1. **一个 Pod 一个 IP**：Pod 内容器共享 IP 与端口空间，`localhost` 就能互访——这正是场景 B 的答案。
2. **pause 容器**：Pod 创建时先起一个极小的 `pause` 容器占住网络命名空间，业务容器加入其中。`kubectl get pods` 看到的 READY 1/1 里的第二个 1 指"总容器数"，业务容器就是 1（pause 不计数）。
3. **共享卷**：Pod 声明一个卷，所有容器挂载它——场景 A 的答案。

> **Compose 对照**：Compose 里没有 Pod 的直接对应物。最接近的说法：Pod ≈ "一个 service 里共享 network 与 volumes 的多个 container 的集合"。绝大多数时候一个 Pod 只有一个容器——先按"Pod = 容器的信封"理解即可。

### 2.2 第一个 YAML：运行 nginx Pod

k8s 一切皆 YAML。先写最小的完整 Pod，认识清单的五个基本字段：

```yaml
# pod-nginx.yaml —— 最小的完整 Pod 清单
apiVersion: v1          # ① API 版本：v1 是核心资源组
kind: Pod               # ② 资源类型：我要创建什么
metadata:               # ③ 元数据：名字 + 标签
  name: nginx-demo      #    名字在命名空间内唯一
  labels:               #    标签：k8s 的"贴纸"，后面 Service 靠它找人
    app: nginx-demo
spec:                   # ④ 规格：期望状态（对比 §1.3 的"期望状态"心智）
  containers:           # ⑤ 容器列表
    - name: nginx
      image: nginx:1.27 #    Compose 里的 image:
      ports:
        - containerPort: 80 # 容器监听端口（仅声明，不做映射）
```

应用到集群：

```bash
kubectl apply -f pod-nginx.yaml
kubectl get pods
```

```
NAME         READY   STATUS    RESTARTS   AGE
nginx-demo   1/1     Running   0          12s
```

解剖一个细节：Compose 里 `ports: "8080:80"` 是"宿主机 8080 → 容器 80"的**映射动作**；k8s 里 `containerPort: 80` 只是**声明**"这个容器监听 80"——对外如何暴露由 Service（§4）决定。这个差异正是"过程 vs 期望状态"的缩影。

### 2.3 kubectl 常用命令

围绕 Pod 的日常命令，本指南后续会反复使用：

```bash
kubectl get pods                    # 列表 + 状态
kubectl get pods -o wide            # 加 IP、所在节点
kubectl describe pod nginx-demo     # 事件与详情——排查第一入口
kubectl logs nginx-demo             # 看日志（多容器加 -c 容器名）
kubectl exec -it nginx-demo -- sh   # 进容器执行命令（相当于 docker exec）
kubectl delete pod nginx-demo       # 删除单个 Pod
kubectl delete -f pod-nginx.yaml    # 按文件删除（apply 的逆操作）
```

两个与 Compose 直觉对应的命令：

```bash
kubectl logs nginx-demo -f          # 相当于 docker logs -f
kubectl exec -it nginx-demo -- sh   # 相当于 docker exec -it ... sh
```

> **提示**：`kubectl explain pod.spec.containers` 可以直接在命令行查字段含义——遇到不确定的字段，先 explain，比翻网页快。

### 2.4 生产实践：Pod 是牛不是宠物

运维圈有句老话：**牛是牛群，宠物是独苗（Cattle, not Pets）**。

```
宠物模式（Pet）                    牛群模式（Cattle）
────────────────                   ────────────────
给每个实例起名字、手工照顾          按编号命名：order-7f8c9b-4x2qk
坏了 → 修好它                      坏了 → 杀掉，补一头新的
Compose 的 container_name 手感      k8s 的默认世界观
```

所以生产上有三条纪律：

1. **绝不直接创建裸 Pod**。Pod 是"牛"，你手工 `kubectl apply` 的 Pod 一旦挂了不会自动重生——它就是一只没人管的宠物。批量养牛需要 Deployment（§3）。
2. **不要 `docker exec` 进生产容器改东西**。容器是即抛的，下次重建一切归零。要改就改清单重新 apply。
3. **数据不能只放在容器文件系统里**。Pod 重建后文件系统是新的一张白纸——持久数据必须用卷（§6）。

### 2.5 本章回顾

- Pod 是 k8s 调度的最小原子单位：一组共享网络与存储的容器。
- YAML 五段式：apiVersion / kind / metadata / spec / 容器列表。
- `containerPort` 是声明不是映射；对外暴露是 Service 的职责。
- kubectl 常用命令：get / describe / logs / exec / apply / delete。
- 生产纪律：把 Pod 当牛不当宠物，裸 Pod 只用于学习。

> **接下来**：§3 用 Deployment 开始"养牛"——副本、自愈、滚动更新一次到位。

---

## 3. Deployment：副本、滚动更新、回滚

### 3.0 问题：Pod 挂了谁管

把 §2 的 nginx Pod 删掉会发生什么？

```bash
kubectl delete pod nginx-demo
kubectl get pods        # 空空如也
```

Pod 死了就是死了。Compose 用 `restart: always` 解决进程崩溃，但解决不了"机器宕机后谁来启动"——而 k8s 的答案是：**别养单只，声明一群**。Deployment 是一个"牧场主"控制器：你声明"我要 2 头牛"，它时刻巡逻，少了补、多了杀、病了换。

### 3.1 声明式：期望状态驱动

k8s 与 Compose 最根本的心智差异，在这一节收拢：

```
命令式（Imperative）                  声明式（Declarative）
───────────────────────             ──────────────────────────
docker run ...                       kubectl apply -f deploy.yaml
docker stop ...                      └─ 清单写着：replicas: 2
docker rm ...                              镜像 demo/user-service:1.0.0
↓                                    ↓
你一步步"操作"系统                   你声明"期望状态"，k8s 持续调谐：
                                      实际副本 2？✔ 保持
                                      副本掉了 1？→ 自动补 1 个新 Pod
                                      改 replicas: 5？→ 自动扩到 5
```

k8s 内部有一个**调谐循环（Reconcile Loop）**：控制器不断比对"期望状态"（清单）与"实际状态"（集群），发现偏差就修正。所以 `kubectl apply` 语义是"**让集群与文件一致**"——第一次是创建，第二次是更新，第三次没变化则什么都不做。这也是同一命令可以反复执行的原因。

### 3.2 第一个 Deployment

把 nginx Pod 升级成"2 副本的牛群"：

```yaml
# deploy-user-service.yaml —— 用电商 user-service 镜像演示（假设已构建，§7 会讲镜像）
apiVersion: apps/v1           # Deployment 属于 apps 组
kind: Deployment
metadata:
  name: user-service
  labels:
    app: user-service
spec:
  replicas: 2                 # 期望副本数（Compose: docker compose scale）
  selector:                   # 管辖范围：管理带哪些标签的 Pod
    matchLabels:
      app: user-service
  template:                   # Pod 模板：新 Pod 照此创建（就是 §2 的 Pod spec）
    metadata:
      labels:
        app: user-service     # 必须被 selector 匹配，否则 Deployment 不认
    spec:
      containers:
        - name: user-service
          image: demo/user-service:1.0.0
          ports:
            - containerPort: 8081
```

```bash
kubectl apply -f deploy-user-service.yaml
kubectl get pods            # 2 个 Running
kubectl get deploy          # DESIRED=2 CURRENT=2 READY=2
```

现在做自愈实验——手动删掉一个 Pod，观察"牧场主"的反应：

```bash
kubectl delete pod <user-service 任意一个 Pod 名>
kubectl get pods -w        # -w 持续观察：旧 Pod 变 Terminating，新 Pod 立刻被创建
```

```
NAME                            READY   STATUS
user-service-6f8c9b-4x2qk      1/1     Running
user-service-6f8c9b-4x2qk      1/1     Terminating   ← 被删
user-service-6f8c9b-8k3md      1/1     Running       ← 新牛补位
```

> **Compose 对照**：`restart: always` 只能"原地重启进程"；Deployment 是"死了换新的"，而且不依赖所在机器——只要集群还有资源，副本数永远被补满。

### 3.3 滚动更新与回滚

更新镜像版本（模拟一次发布）：

```bash
kubectl set image deploy/user-service user-service=demo/user-service:1.1.0
kubectl rollout status deploy/user-service     # 观察发布进度
```

```
Waiting for deployment "user-service" rollout to finish: 1 out of 2 new replicas updated...
deployment "user-service" successfully rolled out
```

默认策略是**滚动更新（RollingUpdate）**：先起新 Pod、健康后逐步替换旧 Pod，全程不中断服务。发布坏了？一条命令回滚：

```bash
kubectl rollout undo deploy/user-service        # 回到上一个版本
kubectl rollout history deploy/user-service     # 查看发布历史
```

对照一下成本：Compose 里要完成"滚动更新 + 失败回滚"，你需要自己写脚本；k8s 里是两条内建命令。这是"期望状态驱动"的红利。

### 3.4 生产实践：副本数与资源配额

**副本数怎么定？** 经验基线：核心服务 ≥2（保证单 Pod 故障不影响可用），有状态服务（如单实例 MySQL）在 k8s 上副本数需要专门设计（§6.4）。副本数的精细调控是 HPA 的领域（§12.2）。

**资源配额必须写**——这是 Compose 用户最容易忽略的纪律。不写 requests/limits，一个服务内存泄漏就能吃光节点：

```yaml
spec:
  template:
    spec:
      containers:
        - name: user-service
          image: demo/user-service:1.0.0
          resources:
            requests:        # 调度承诺：申请多少才安排到节点（保证能跑）
              cpu: "250m"     # 0.25 核
              memory: "512Mi"
            limits:           # 使用上限：超了怎么办（CPU 限流，内存 OOM 杀掉重启）
              cpu: "1000m"
              memory: "1Gi"
```

```
requests = 预约：Scheduler 按此挑选节点，保证资源到位
limits   = 上限：超出后 CPU 被限流；内存超出 → OOMKilled → 重启
```

> **记忆口诀**：requests 是"我至少要多少"，limits 是"我最多能要多少"。生产环境两者都要写——只有 requests 的服务会被同节点邻居抢 CPU；只有 limits 的服务可能被调度到资源不足的节点。

### 3.5 本章回顾

- Deployment 是"牧场主"控制器：声明副本数，自愈、自扩、自替换。
- 声明式 vs 命令式：apply 的语义是"让集群与文件一致"，可重复执行。
- 滚动更新与 `rollout undo` 是发布与回滚的标准动作。
- requests/limits 是生产必填项：预约 vs 上限。
- selector 与 template 的标签必须匹配——这是 Deployment 最常见的配置错误。

> **接下来**：§4 解决"怎么访问这群牛"——Service 与 Ingress 登场。

---

## 4. Service 与 Ingress：服务暴露与负载均衡

### 4.0 问题：Pod IP 是临时的

在 §2 我们学过：每个 Pod 有一个 IP。试着在集群里直接用它：

```bash
kubectl get pods -o wide        # 记下 user-service 某个 Pod 的 IP，如 10.244.0.17
kubectl delete pod <该 Pod>     # 模拟故障
kubectl get pods -o wide        # 新 Pod 的 IP 变成了 10.244.0.19
```

两个致命问题：**IP 会变**（每次重建都换），**副本有多个**（到底调哪个？）。Compose 用服务名 + 内置 DNS + 负载均衡把这事藏起来了——k8s 的答案就是 Service：**给一组 Pod 一个稳定身份（虚拟 IP + DNS 名）**。

```
                    ┌─────────────────────────────┐
                    │         Service             │
                    │   名字: user-service        │
                    │   虚拟 IP: 10.96.0.5（不变） │
                    └──────────┬──────────────────┘
                               │ selector: app=user-service
                 ┌─────────────┼─────────────┐
                 ▼             ▼             ▼
           ┌──────────┐ ┌──────────┐ ┌──────────┐
           │ Pod A    │ │ Pod B    │ │ Pod C    │
           │ 10.244.0.17 │ 10.244.0.19 │ 10.244.0.21 │
           └──────────┘ └──────────┘ └──────────┘
           调用方永远只找 Service，Service 负责转发到健康的 Pod
```

### 4.1 Service 的三种类型

| 类型 | 作用 | 访问范围 | 对照 Compose |
|---|---|---|---|
| ClusterIP（默认） | 集群内部虚拟 IP | 仅集群内 | 服务名互访（默认网络） |
| NodePort | 在每台节点开一个端口转发进来 | 节点 IP:端口 | `ports: "30080:80"` 映射 |
| LoadBalancer | 对接云厂商 LB | 公网 | 云托管环境才有对应物 |

```
ClusterIP        NodePort                 LoadBalancer
─────────        ────────                 ────────────
集群内互通        ┌──────────┐             公网 LB
（微服务间调用）   │ 30080    │ ──▶ Service ──▶ 云厂商创建
                 │ 节点IP:30080 直达       elastic LB
                 └──────────┘
```

学习集群（kind 单节点）里三者的实用结论：**集群内部互调用 ClusterIP；你从宿主机访问用 `kubectl port-forward` 或 NodePort；LoadBalancer 在本地没有云厂商实现，先跳过**（§4.5 展开）。

### 4.2 DNS：服务名即地址

k8s 内建 DNS（CoreDNS，§1.5 里 kube-system 中的 coredns）。规则：

```text
<服务名>.<命名空间>.svc.cluster.local
user-service.ecommerce.svc.cluster.local   ← 完整域名
user-service.ecommerce                     ← 同集群内常用简写
user-service                               ← 同一命名空间内最简写法
```

```
Compose：   order-service 调 http://product-service:8082   （服务名直接可用）
k8s：       order-service 调 http://product-service:8082   （同命名空间，写法一致！）
```

体验几乎零差异——这是 §8 讨论"Nacos 还有没有必要"的重要伏笔：**k8s 已经把"服务名 → 地址"这件事内建了**。

### 4.3 第一个 Service：ClusterIP

给 §3 的 user-service 加一个 Service：

```yaml
# svc-user-service.yaml —— 给 Deployment 一个稳定身份
apiVersion: v1
kind: Service
metadata:
  name: user-service          # 这个名字将成为 DNS 名
spec:
  type: ClusterIP             # 默认值，可省略
  selector:
    app: user-service         # 找带此标签的 Pod（与 Deployment 的 selector 呼应）
  ports:
    - port: 8081              # Service 自己的端口（调用方连这个）
      targetPort: 8081        # 转发到 Pod 的容器端口（containerPort）
```

```bash
kubectl apply -f svc-user-service.yaml
kubectl get svc               # CLUSTER-IP 一列就是虚拟 IP
```

```
NAME           TYPE        CLUSTER-IP    EXTERNAL-IP   PORT(S)
user-service   ClusterIP   10.96.0.5     <none>        8081/TCP
```

三个端口概念是 Compose 用户最容易绕晕的地方，一次分清：

```text
containerPort : 容器进程监听的端口（Pod 内）          ← 声明"我监听 8081"
targetPort    : Service 转发到 Pod 的哪个端口          ← 通常等于 containerPort
port          : Service 对外提供的端口（调用方视角）    ← 客户端连 8081
```

> **注意**：k8s 里没有"宿主机端口"这一层——容器端口不需要映射到宿主机。端口只在 Pod 网络内开放，是否对外暴露完全由 Service 类型决定。这再次印证 §2.2 的结论：**声明（containerPort）与暴露（Service）是分离的**。

### 4.4 Ingress：统一入口

微服务指南里，Gateway 是电商系统的唯一入口：`http://localhost:8080` 一个地址分发所有请求。k8s 中 Service 是"服务级"暴露，**入口级**暴露（七层路由：按域名、路径分发）由 Ingress 负责：

```text
                         ┌──────────────────────────┐
    http://demo.local    │        Ingress           │
    /api/orders/** ────▶│  (七层路由规则 + 控制器)   │
                         └──────┬─────────┬─────────┘
                                │         │
                     /api/users │         │ /api/products
                                ▼         ▼
                          user-service  product-service
                          (Service:8081)(Service:8082)
```

对照关系一句话：**Ingress ≈ 集群的 Nginx/Spring Cloud Gateway**（只做路由转发，不做业务）。但注意 Ingress 本身只是"规则"，真正干活的是控制器（如 ingress-nginx）——这一点和"Compose 配置即服务"完全不同。

```yaml
# ingress-demo.yaml —— 学习用域名路由示例（kind 需先装 ingress-nginx，§4.5）
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ecommerce
spec:
  rules:
    - host: demo.local                      # 域名路由
      http:
        paths:
          - path: /api/users                # 路径路由
            pathType: Prefix                 # 前缀匹配（1.36 必填）
            backend:
              service:
                name: user-service
                port:
                  number: 8081
```

> **前端视角**：Ingress 就是部署在集群边缘的 Nginx `server` 块——按 `Host` 和 `location` 转发。Gateway 是"应用层"网关（认证、限流），Ingress 是"集群层"入口，两者职责不同、可以共存。

### 4.5 学习集群适配：kind 下如何访问

kind 单节点集群没有云 LB，也默认没装 Ingress 控制器。学习阶段三条实用路径：

**路径 1：`kubectl port-forward`（最推荐）**——把本地端口直连到集群内 Pod/Service，无需任何配置：

```bash
# 本地 8083 ↔ 集群内 order-service:8083
kubectl port-forward svc/order-service 8083:8083
# 另开一个终端：curl http://localhost:8083/api/orders
```

> 原理与 SSH 隧道类似；适合调试，不适合作为"正式访问入口"。

**路径 2：NodePort**——Service 改为 NodePort 类型，在宿主机用 `节点IP:300xx` 访问：

```yaml
spec:
  type: NodePort
  selector:
    app: order-service
  ports:
    - port: 8083
      targetPort: 8083
      nodePort: 30083        # 30000~32767 范围，每台节点都监听
```

```bash
kubectl get nodes -o wide    # 找到节点 INTERNAL-IP
curl http://<节点IP>:30083/api/orders
```

> kind 的节点是 Docker 容器，节点 IP 对宿主机不一定直接可达。若访问不通，最稳妥的仍是路径 1。NodePort 在这里主要用来理解"类型差异"。

**路径 3：安装 ingress-nginx 后使用 Ingress**——最接近生产的体验，但需要给 kind 集群加额外配置：

```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
# 然后配好 §4.4 的 Ingress + hosts 文件（demo.local → 127.0.0.1）
```

> 路径 3 依赖网络拉取外部清单，且版本与 1.36 的兼容性需现场核对；本指南主线（§7）统一使用路径 1 验证。

### 4.6 本章回顾

- Service 给一组 Pod 稳定身份：虚拟 IP + DNS 名，靠 selector 找 Pod。
- 三种类型：ClusterIP（内部）/ NodePort（节点端口）/ LoadBalancer（云 LB）。
- 端口三概念：containerPort（声明）、targetPort（转发）、port（对外）。
- Ingress 是七层统一入口，需要控制器配合；≈ 集群级 Nginx/Gateway。
- kind 学习集群访问：port-forward 优先，NodePort 理解用，Ingress 可选。

> **接下来**：§5 处理配置——Compose 的 environment 在 k8s 里的正确姿势。

---

## 5. ConfigMap 与 Secret：配置与密钥

### 5.0 问题：配置写死在镜像里

微服务指南里的配置流是：本地 `application.yml` 只写"连接配置中心"，真正的运行配置（端口、数据源、路由）在 Nacos。到了 k8s，问题更尖锐：**如果把 MySQL 地址写死进镜像**，换一个环境就要重新构建镜像——镜像应该只含代码，不含环境。k8s 的答案是两个对象：ConfigMap（普通配置）和 Secret（敏感配置）。

```
Compose                         k8s
────────                        ────
environment:                    ConfigMap / Secret
  MYSQL_HOST: mysql-user        ├─ 以环境变量注入
  MYSQL_PASSWORD: root123       └─ 或以文件挂载到容器路径
```

### 5.1 ConfigMap：环境变量与挂载

ConfigMap 是"名值对 + 小文件"的集合。以数据库连接配置为例：

```yaml
# configmap-user-service.yaml —— 非敏感的运行配置
apiVersion: v1
kind: ConfigMap
metadata:
  name: user-service-config
data:                        # data 段放名值对
  db-host: "mysql-user"      # k8s 内 DNS 名（§4.2）——不再是 localhost！
  db-port: "3306"            # 集群内直接连容器端口，无需宿主端口 3307
  db-name: "user_db"
```

```bash
kubectl apply -f configmap-user-service.yaml
kubectl get configmap
```

> **注意一个迁移差异**：Compose 里三个 MySQL 靠宿主端口 3307/3308/3309 区分；k8s 集群内**每个 Service 有自己的 DNS 名**，不需要端口错开——三个 MySQL 都监听 3306，靠名字区分（§6.3、§7.2 会落地）。

### 5.2 Secret：数据库密码

密码、Token、证书放 ConfigMap 不合适——Secret 是它们的专用对象（base64 编码存储）：

```yaml
# secret-mysql.yaml —— 敏感配置
apiVersion: v1
kind: Secret
metadata:
  name: mysql-secret
type: Opaque                  # 通用键值类型
stringData:                   # stringData：明文书写，k8s 自动转 base64
  mysql-root-password: "root123"   # 生产环境请换成真密码，且不要提交 git
```

```bash
kubectl apply -f secret-mysql.yaml
kubectl get secret mysql-secret
```

`stringData` 可以让你**明文书写**、k8s 存成 base64；也可以直接用 `data` 段写编码后的值。学习中用 `stringData` 更不容易出错。

### 5.3 使用方式：env 与 volume

**方式 1：注入环境变量**（最像 Compose 的 environment）：

```yaml
spec:
  template:
    spec:
      containers:
        - name: user-service
          image: demo/user-service:1.0.0
          env:
            - name: DB_HOST              # 容器内环境变量名
              valueFrom:
                configMapKeyRef:         # 从 ConfigMap 取
                  name: user-service-config
                  key: db-host
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:            # 从 Secret 取
                  name: mysql-secret
                  key: mysql-root-password
```

**方式 2：挂载为文件**（配置中心类应用的最爱——Nacos 导入脚本、配置文件整份挂载）：

```yaml
spec:
  template:
    spec:
      containers:
        - name: app
          image: demo/user-service:1.0.0
          volumeMounts:
            - name: app-config
              mountPath: /etc/config     # 挂载点
              readOnly: true
      volumes:
        - name: app-config
          configMap:
            name: user-service-config    # 每个 key 变成一个文件
```

```
env 注入      变量写死在启动参数里，改配置要重启 Pod（Compose 手感，简单直观）
volume 挂载   文件实时可读，改 ConfigMap 后一段时间内自动同步（无需重建镜像）
```

> **经验**：少量键值用 env；整份配置文件用 volume。Spring Boot 应用若要从挂载文件读取 `application.yml`，可用 `spring.config.import=file:/etc/config/` 引入。

### 5.4 生产实践：Secret 不是加密

三个常见的错误认知，一次澄清：

1. **base64 不是加密**：`echo cm9vdDEyMw== | base64 -d` 一秒还原。Secret 的价值在于**权限隔离**（配合 RBAC 只授权需要的服务）和**审计轨迹**，不是数学意义上的保密。
2. **Secret 默认不落盘加密**：etcd 里的 Secret 默认明文（base64）存储，生产集群应开启 etcd 加密；云厂商托管集群一般默认开启。
3. **不要提交 git**：`stringData` 里的真实密码进了版本库，RBAC 形同虚设。生产用 External Secrets Operator、云密钥管理服务或 Sealed Secrets 等方案，本指南学习阶段仅作演示。

### 5.5 本章回顾

- ConfigMap 放普通配置，Secret 放敏感配置——都用"对象"管理，注入靠 env 或 volume。
- stringData 明文书写、自动编码；学习用它，生产用密钥管理服务。
- 少量键值 → env；整份配置 → volume 挂载。
- Secret 只是 base64 + 权限隔离，不是加密；开启 etcd 加密、不提交 git 是生产底线。

> **接下来**：§6 处理数据持久化——MySQL 上 k8s 的第一课：PV/PVC。

---

## 6. PV 与 PVC：MySQL 有状态部署

### 6.0 问题：MySQL 重启数据会丢吗

做个小实验（在 §3 的集群上）：

```bash
kubectl get pods -o wide | grep mysql    # 假设还没有 MySQL，先看下面的推理
```

Pod 是"牛"（§2.4）：MySQL 的 Pod 一旦重建，容器文件系统是全新白纸——**所有数据清空**。Compose 里你用命名卷解决：

```yaml
# Compose 写法
volumes:
  mysql-user-data:            # 声明一个命名卷
services:
  mysql-user:
    volumes:
      - mysql-user-data:/var/lib/mysql   # 挂到数据目录
```

k8s 的对应物就是 PV（PersistentVolume，持久卷）与 PVC（PersistentVolumeClaim，持久卷声明）——但比 Compose 多了一层"解耦"，这是理解本章的钥匙。

### 6.1 存储模型：PV、PVC、StorageClass

```
Compose 命名卷：       "我要一块存储"（服务直接绑定卷）
k8s：                 "我要一块存储"（PVC 申请） ←→ "这里有一块存储"（PV 供给）
                       └──────────── 由 StorageClass 自动撮合 ────────────┘
```

三个对象的分工：

```
┌──────────────────────────────────────────────────────────────┐
│  PVC（申请方）：谁要多少——1Gi、可读写，像"租房需求"             │
│       │                                                      │
│       ▼                                                      │
│  StorageClass（中介）：标准型/SSD 型——按模板自动开 PV，像"中介"  │
│       │                                                      │
│       ▼                                                      │
│  PV（供给方）：实际存储在哪、多大——hostPath/NFS/云盘，像"房源"   │
└──────────────────────────────────────────────────────────────┘
```

关键心智：**Pod 只认 PVC**。Pod 重建多少次，PVC 还在；数据在哪块盘上、什么类型，Pod 完全不关心。这正是"声明 vs 供给"解耦在存储上的体现——运维换云盘、换 NFS，业务清单一个字不用改。

### 6.2 kind 的本地存储方案

kind 预置了 `standard` StorageClass（§1.5 里 kube-system 中的 `local-path-provisioner`）——它自动把存储建在**节点容器的本地磁盘**上：

```bash
kubectl get storageclass
```

```
NAME                 PROVISIONER             RECLAIMPOLICY
standard (default)   rancher.io/local-path   Delete
```

学习结论：**在 kind 里直接用 `standard`，PVC 一申请，PV 自动出现**。注意两个学习集群特有的坑：

1. **数据在 Docker 容器里**：`kind delete cluster` 后数据随之消失（这反而是学习优势：一键恢复干净环境）。
2. **单节点无高可用**：节点容器坏了数据就没了——生产环境存储方案（云盘/NFS/Longhorn）不在此列。

### 6.3 MySQL 部署实践

把微服务指南里的 `mysql-user` 搬上 k8s——这是"Compose → k8s"翻译练习的完整示范：

```yaml
# pvc-mysql-user.yaml —— 第一步：申请存储（PVC）
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: mysql-user-pvc
spec:
  accessModes:
    - ReadWriteOnce          # 单节点读写（单副本 MySQL 的标准姿势）
  resources:
    requests:
      storage: 1Gi           # 学习够用；生产按数据量规划
  # 不指定 storageClassName → 用默认 standard（§6.2）
```

```yaml
# deploy-mysql-user.yaml —— 第二步：Deployment + Service + 挂载 PVC
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mysql-user
spec:
  replicas: 1                # 单副本：MySQL 不能像无状态服务那样随便多开
  selector:
    matchLabels:
      app: mysql-user
  template:
    metadata:
      labels:
        app: mysql-user
    spec:
      containers:
        - name: mysql
          image: mysql:8.0    # 镜像与微服务指南一致
          env:                # 环境变量从 §5 的对象注入
            - name: MYSQL_ROOT_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: mysql-secret
                  key: mysql-root-password
            - name: MYSQL_DATABASE
              value: "user_db"
          ports:
            - containerPort: 3306     # 集群内统一 3306，不再需要 3307！
          volumeMounts:
            - name: mysql-data
              mountPath: /var/lib/mysql   # 挂到 MySQL 数据目录（Compose 同名路径）
      volumes:
        - name: mysql-data
          persistentVolumeClaim:
            claimName: mysql-user-pvc     # Pod 只认 PVC（§6.1 的心智）
---
apiVersion: v1
kind: Service
metadata:
  name: mysql-user                 # 其他服务用这个 DNS 名连接
spec:
  selector:
    app: mysql-user
  ports:
    - port: 3306
      targetPort: 3306
```

```bash
kubectl apply -f pvc-mysql-user.yaml
kubectl apply -f deploy-mysql-user.yaml
kubectl get pvc,pods
```

验证持久化——重建 Pod，数据仍在：

```bash
kubectl delete pod -l app=mysql-user     # 杀掉 MySQL Pod
kubectl get pods -w                      # 等新 Pod Running
kubectl exec -it deploy/mysql-user -- mysql -uroot -proot123 -e "show databases;"
# 能看到 user_db —— 数据在 PVC 里，Pod 重建不影响
```

> **Compose 对照完成**：`volumes: mysql-user-data`（命名卷）→ PVC+PV（申请与供给分离）；`"3307:3306"`（宿主端口错开）→ 三个 MySQL 各有 DNS 名，全部 3306；`MYSQL_ROOT_PASSWORD`（内联）→ Secret 注入。微服务指南 §1.4 的 Compose 文件至此全部有了 k8s 对应物。

### 6.4 生产实践：StatefulSet 与数据库上 k8s 的争议

**先泼冷水**：生产环境的 MySQL 是否放 k8s 里，业界仍在争论。支持方看中统一的编排体验；反对方担心数据丢失风险与运维复杂度。务实结论：

```
适合上 k8s 的数据库姿势                保守姿势
─────────────────────               ────────────
云托管（RDS 等）✔ 强烈推荐            MySQL 留在 VM / 云托管
自建且团队懂 k8s 存储 ✔ 可尝试        数据库团队独立运维
学习/测试环境 ✔ 随便上                生产核心库 ⚠ 谨慎
```

**StatefulSet**：即便自建，生产也不会用本节这种"单副本 Deployment + PVC"——StatefulSet 为有状态应用提供了**稳定标识**（`mysql-0`、`mysql-1` 固定名字而非随机后缀）、**有序扩缩容**、**每个副本独立 PVC**：

```
Deployment + PVC：     重建后名字随机、无固定身份 → 单副本凑合，多副本乱套
StatefulSet：           mysql-0 / mysql-1 身份稳定 → 主从复制方案的地基
```

RabbitMQ、Kafka 这类有状态中间件同理（§12.3）。StatefulSet 的完整展开超出本指南主线——它是有状态负载上 k8s 的第一块专业台阶。

### 6.5 本章回顾

- Pod 文件系统是即抛的；持久数据必须走 PVC/PV。
- 分工：PVC 申请、StorageClass 撮合、PV 供给；Pod 只认 PVC。
- kind 用预置 `standard`（local-path）存储即可，数据在节点容器内。
- MySQL 上 k8s：单副本 Deployment + PVC 是学习姿势；生产多副本是 StatefulSet 领域，且核心库是否上 k8s 需谨慎决策。
- Compose 命名卷 → PVC/PV、宿主端口错开 → DNS 名区分——迁移的两个关键翻译完成。

> **接下来**：§7 把所有零件组装起来——电商三服务整体上 k8s。

---

## 7. 电商三服务上 k8s

### 7.0 主线路线说明

> **定位说明（先读）**：本章是主线——**原样搬运**。目标是把微服务指南的电商系统在 k8s 里跑起来，**服务业务代码零改动**，Nacos 继续担任注册与配置中心。所有调整只发生在部署清单和 Nacos 中保存的配置数据上。
>
> 至于"上了 k8s 还要不要 Nacos"这类架构演进问题，是 §8 的主题。学习路径建议：先按本章把系统搬上去、跑通，再读 §8 思考演进——不要一边学 k8s 一边改架构。

### 7.1 整体规划：命名空间与镜像清单

**第一步：创建命名空间**，把整个电商系统与 kube-system 等系统组件隔开：

```yaml
# 00-namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: ecommerce
```

```bash
kubectl apply -f 00-namespace.yaml
kubectl get namespaces
```

后续所有清单都带 `namespace: ecommerce`。同一命名空间内，服务用短 DNS 名互访（§4.2）。

**第二步：构建服务镜像**。微服务项目里每个服务需要一个 Dockerfile。以 order-service 为例（其余服务同理）：

```dockerfile
# order-service/Dockerfile —— Spring Boot 多阶段构建
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
docker build -t demo/order-service:1.0.0 .
docker build -t demo/gateway:1.0.0 .        # gateway 同理（8080）
docker build -t demo/user-service:1.0.0 .   # user 同理（8081）
docker build -t demo/product-service:1.0.0 . # product 同理（8082）
```

> **kind 加载镜像**：kind 的节点是 Docker 容器，看不到宿主机的本地镜像。构建后需要加载进集群：
>
> ```bash
> kind load docker-image demo/order-service:1.0.0 demo/gateway:1.0.0 \
>   demo/user-service:1.0.0 demo/product-service:1.0.0 --name ecommerce
> ```

**镜像与端口清单**（微服务指南基线的对照）：

| 服务 | 镜像 | 容器端口 | k8s DNS 名 | Compose 宿主端口 |
|---|---|---|---|---|
| gateway | demo/gateway:1.0.0 | 8080 | gateway | 8080 |
| user-service | demo/user-service:1.0.0 | 8081 | user-service | 8081 |
| product-service | demo/product-service:1.0.0 | 8082 | product-service | 8082 |
| order-service | demo/order-service:1.0.0 | 8083 | order-service | 8083 |
| Nacos | nacos/nacos-server:v3.0.3 | 8848/9848 | nacos | 8848/9848/8084 |
| mysql-user | mysql:8.0 | 3306 | mysql-user | 3307 |
| mysql-product | mysql:8.0 | 3306 | mysql-product | 3308 |
| mysql-order | mysql:8.0 | 3306 | mysql-order | 3309 |

### 7.2 基础设施：三个 MySQL 与 Nacos

**三个 MySQL**：§6.3 的清单复制三份，改名字即可。注意配置数据（Nacos 中的 dataSource URL）从"宿主端口"改成"DNS 名"：

```text
Compose（微服务指南 §1.4/§1.5）          k8s（本章）
─────────────────────────             ─────────────────────
jdbc:mysql://localhost:3309/order_db   jdbc:mysql://mysql-order:3306/order_db
jdbc:mysql://localhost:3308/product_db jdbc:mysql://mysql-product:3306/product_db
jdbc:mysql://localhost:3307/user_db    jdbc:mysql://mysql-user:3306/user_db
```

```bash
kubectl apply -f 05-secret-mysql.yaml
kubectl apply -f 06-pvc-mysql-user.yaml -f 07-deploy-mysql-user.yaml
kubectl apply -f 08-pvc-mysql-product.yaml -f 09-deploy-mysql-product.yaml
kubectl apply -f 10-pvc-mysql-order.yaml -f 11-deploy-mysql-order.yaml
kubectl get pods -n ecommerce          # 三个 mysql-* 均 Running
```

**Nacos**：把 Compose 的 nacos 服务翻译成 Deployment + Service。注意环境变量、端口与探针：

```yaml
# 12-deploy-nacos.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nacos
  namespace: ecommerce
spec:
  replicas: 1                # 学习用 standalone 单实例
  selector:
    matchLabels:
      app: nacos
  template:
    metadata:
      labels:
        app: nacos
    spec:
      containers:
        - name: nacos
          image: nacos/nacos-server:v3.0.3    # 与微服务指南一致
          env:
            - name: MODE
              value: "standalone"
            - name: NACOS_AUTH_ENABLE
              value: "false"
            # Nacos 3.x 启动脚本要求以下三项非空（微服务指南 §1.4 同款）
            - name: NACOS_AUTH_TOKEN
              value: "bG9jYWwtbGVhcm5pbmctbmFjb3MtdG9rZW4tMjAyNi0wOC0wNQ=="
            - name: NACOS_AUTH_IDENTITY_KEY
              value: "serverIdentity"
            - name: NACOS_AUTH_IDENTITY_VALUE
              value: "local-learning"
          ports:
            - containerPort: 8848   # HTTP API
            - containerPort: 9848   # gRPC（Nacos 2.x 引入，3.x 沿用）
            - containerPort: 8080   # 控制台
---
apiVersion: v1
kind: Service
metadata:
  name: nacos
  namespace: ecommerce
spec:
  selector:
    app: nacos
  ports:
    - name: http
      port: 8848
      targetPort: 8848
    - name: grpc
      port: 9848
      targetPort: 9848
    - name: console
      port: 8080
      targetPort: 8080
```

**Compose healthcheck → k8s 探针的翻译**（§9.1 的前置体验）。Compose 里：

```yaml
healthcheck:
  test: ["CMD-SHELL", "curl -fsS http://localhost:8848/nacos/v1/ns/operator/metrics >/dev/null || exit 1"]
  interval: 10s
  timeout: 5s
  retries: 18
```

在 k8s 里，这个"命令式检查"变成 Deployment 的 readinessProbe（详细原理 §9.1）：

```yaml
          readinessProbe:            # k8s 用 HTTP GET 探测，不必依赖容器内 curl
            httpGet:
              path: /nacos/v1/ns/operator/metrics
              port: 8848
            initialDelaySeconds: 20  # Nacos 启动慢，先等 20 秒
            periodSeconds: 10        # 相当于 interval
            timeoutSeconds: 5        # 相当于 timeout
            failureThreshold: 18     # 相当于 retries
```

加上探针后，Pod 的 READY 由探测结果决定——`kubectl get pods` 看到 1/1 才是真的"可用"。

**配置导入**：Compose 里的 `nacos-init`（等 Nacos 健康后执行导入脚本）在 k8s 里翻译为**一次性 Job**：

```yaml
# 13-job-nacos-init.yaml —— 对照 Compose 的 nacos-init 服务
apiVersion: batch/v1
kind: Job
metadata:
  name: nacos-init
  namespace: ecommerce
spec:
  template:
    spec:
      restartPolicy: Never
      initContainers:                       # 启动顺序：等 Nacos 就绪
        - name: wait-nacos
          image: curlimages/curl:8.12.1
          command:
            - sh
            - -c
            - |
              until curl -fsS http://nacos:8848/nacos/v1/ns/operator/metrics; do
                echo "waiting for nacos..."; sleep 5
              done
      containers:
        - name: import
          image: curlimages/curl:8.12.1
          command: ["/bin/sh", "/configs/import.sh"]
          volumeMounts:
            - name: nacos-config
              mountPath: /configs
              readOnly: true
      volumes:
        - name: nacos-config
          configMap:
            name: nacos-config-files      # §5.3 的 volume 挂载方式
```

`nacos-config-files` 这个 ConfigMap 把微服务项目 `infra/nacos/` 下的 YAML（已按 §7.2 改成 DNS 名）收纳为文件。创建方式：

```bash
kubectl create configmap nacos-config-files -n ecommerce \
  --from-file=infra/nacos/import.sh \
  --from-file=infra/nacos/gateway-service.yaml \
  --from-file=infra/nacos/user-service.yaml \
  --from-file=infra/nacos/product-service.yaml \
  --from-file=infra/nacos/order-service.yaml
```

> `depends_on: condition: service_healthy` 在 k8s 没有直接对应物，拆成"initContainer 轮询 + 就绪探针"是最接近的翻译。Job 跑完显示 `Completed`，等价于 Compose 里 nacos-init 的 `Exited (0)`。

### 7.3 三服务与网关部署

四个应用清单结构完全一致（§3.2 的 Deployment + §4.3 的 Service）。以 order-service 为例：

```yaml
# 14-deploy-order-service.yaml —— 其余三个服务照此改名字即可
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
  namespace: ecommerce
  labels:
    app: order-service
spec:
  replicas: 2                       # 无状态服务，生产姿势从 2 副本开始
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service
    spec:
      containers:
        - name: order-service
          image: demo/order-service:1.0.0
          ports:
            - containerPort: 8083
          env:
            # 关键调整：Nacos 地址从 localhost 改为集群内 DNS 名。
            # 只改部署配置，不动业务代码（本地 application.yml 里的值被覆盖）
            - name: SPRING_CLOUD_NACOS_SERVER_ADDR
              value: "nacos:8848"
          resources:                 # §3.4 的生产纪律
            requests:
              cpu: "250m"
              memory: "512Mi"
            limits:
              cpu: "1000m"
              memory: "1Gi"
---
apiVersion: v1
kind: Service
metadata:
  name: order-service
  namespace: ecommerce
spec:
  selector:
    app: order-service
  ports:
    - port: 8083
      targetPort: 8083
```

```bash
kubectl apply -f 14-deploy-order-service.yaml
kubectl apply -f 15-deploy-product-service.yaml   # 8082，replicas: 2
kubectl apply -f 16-deploy-user-service.yaml      # 8081，replicas: 2
kubectl apply -f 17-deploy-gateway.yaml           # 8080，replicas: 2
kubectl get pods -n ecommerce
```

```
NAME                               READY   STATUS    RESTARTS   AGE
gateway-7f8c9b-4x2qk               1/1     Running   0          2m
nacos-6d4f8b-9k3md                 1/1     Running   0          5m
mysql-order-5c8f7b-2p9xk           1/1     Running   0          6m
mysql-product-6b9c0d-3q8xl         1/1     Running   0          6m
mysql-user-7d0e1f-4r7ym            1/1     Running   0          6m
order-service-8f1g2h-5s6zn         1/1     Running   0          2m
order-service-8f1g2h-7t8ao         1/1     Running   0          2m
product-service-9g2h3i-6u9bp       1/1     Running   0          2m
user-service-0h3i4j-8v0cq          1/1     Running   0          2m
```

> **为什么是 env 覆盖而不是改代码**：Spring Boot 的 relaxed binding 允许用环境变量覆盖任何 `spring.cloud.nacos.server-addr` 之类的属性。业务代码、镜像、Nacos 中的业务配置全部原样——只有"连接配置中心的地址"这一条部署信息随环境而变，这正是 §5 讲的环境外置。

### 7.4 验证：注册、路由与调用

**第一步：确认注册到 Nacos**。把 Nacos 控制台端口转发到本地（沿用 Compose 的 8084 习惯）：

```bash
kubectl port-forward -n ecommerce svc/nacos 8084:8080
# 浏览器打开 http://localhost:8084/nacos
# 服务列表应看到：gateway、user-service、product-service、order-service
```

```
Compose：  http://localhost:8084 → Nacos 控制台（宿主映射 8084:8080）
k8s：      kubectl port-forward svc/nacos 8084:8080 → 同样打开 8084
```

**第二步：通过 Gateway 走一次完整链路**（微服务指南 §1.1 的调用链）：

```bash
kubectl port-forward -n ecommerce svc/gateway 8080:8080
# 另开终端：
curl http://localhost:8080/api/orders     # Gateway → order-service（经 Nacos 发现）
```

链路与 Compose 时代完全一致：

```text
浏览器 → Gateway(:8080) → Nacos 查到 order-service 实例
                        → Feign 调 product-service / user-service
                        → 各自 MySQL（DNS 名访问）
```

**第三步：体验自愈**。杀掉一个 order-service Pod，观察"牛群"补位、服务不中断：

```bash
kubectl delete pod -n ecommerce -l app=order-service
kubectl get pods -n ecommerce -w           # 新 Pod 立刻补上
# 此时再 curl，仍正常返回 —— Deployment 自愈 + Nacos 心跳摘除旧实例
```

### 7.5 汇总清单

把 §7.2~§7.3 的清单合并为一个多文档 YAML，放在 `k8s/ecommerce.yaml`，一条命令部署整套系统：

```yaml
# k8s/ecommerce.yaml —— 电商系统完整清单（§7 汇总，可直接整体复制）
# 部署：kubectl apply -f ecommerce.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: ecommerce
---
apiVersion: v1
kind: Secret
metadata:
  name: mysql-secret
  namespace: ecommerce
type: Opaque
stringData:
  mysql-root-password: "root123"
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: mysql-user-pvc
  namespace: ecommerce
spec:
  accessModes: [ReadWriteOnce]
  resources:
    requests:
      storage: 1Gi
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: mysql-product-pvc
  namespace: ecommerce
spec:
  accessModes: [ReadWriteOnce]
  resources:
    requests:
      storage: 1Gi
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: mysql-order-pvc
  namespace: ecommerce
spec:
  accessModes: [ReadWriteOnce]
  resources:
    requests:
      storage: 1Gi
---
# ========== MySQL ×3（以 mysql-order 为例，另两个结构相同） ==========
apiVersion: apps/v1
kind: Deployment
metadata:
  name: mysql-order
  namespace: ecommerce
spec:
  replicas: 1
  selector:
    matchLabels:
      app: mysql-order
  template:
    metadata:
      labels:
        app: mysql-order
    spec:
      containers:
        - name: mysql
          image: mysql:8.0
          env:
            - name: MYSQL_ROOT_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: mysql-secret
                  key: mysql-root-password
            - name: MYSQL_DATABASE
              value: "order_db"
          ports:
            - containerPort: 3306
          volumeMounts:
            - name: mysql-data
              mountPath: /var/lib/mysql
      volumes:
        - name: mysql-data
          persistentVolumeClaim:
            claimName: mysql-order-pvc
---
apiVersion: v1
kind: Service
metadata:
  name: mysql-order
  namespace: ecommerce
spec:
  selector:
    app: mysql-order
  ports:
    - port: 3306
      targetPort: 3306
---
# ========== Nacos ==========
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nacos
  namespace: ecommerce
spec:
  replicas: 1
  selector:
    matchLabels:
      app: nacos
  template:
    metadata:
      labels:
        app: nacos
    spec:
      containers:
        - name: nacos
          image: nacos/nacos-server:v3.0.3
          env:
            - name: MODE
              value: "standalone"
            - name: NACOS_AUTH_ENABLE
              value: "false"
            - name: NACOS_AUTH_TOKEN
              value: "bG9jYWwtbGVhcm5pbmctbmFjb3MtdG9rZW4tMjAyNi0wOC0wNQ=="
            - name: NACOS_AUTH_IDENTITY_KEY
              value: "serverIdentity"
            - name: NACOS_AUTH_IDENTITY_VALUE
              value: "local-learning"
          ports:
            - containerPort: 8848
            - containerPort: 9848
            - containerPort: 8080
          readinessProbe:
            httpGet:
              path: /nacos/v1/ns/operator/metrics
              port: 8848
            initialDelaySeconds: 20
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 18
---
apiVersion: v1
kind: Service
metadata:
  name: nacos
  namespace: ecommerce
spec:
  selector:
    app: nacos
  ports:
    - name: http
      port: 8848
      targetPort: 8848
    - name: grpc
      port: 9848
      targetPort: 9848
    - name: console
      port: 8080
      targetPort: 8080
---
# ========== order-service（gateway/user/product 结构相同，改名字与端口） ==========
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
  namespace: ecommerce
spec:
  replicas: 2
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service
    spec:
      containers:
        - name: order-service
          image: demo/order-service:1.0.0
          ports:
            - containerPort: 8083
          env:
            - name: SPRING_CLOUD_NACOS_SERVER_ADDR
              value: "nacos:8848"
---
apiVersion: v1
kind: Service
metadata:
  name: order-service
  namespace: ecommerce
spec:
  selector:
    app: order-service
  ports:
    - port: 8083
      targetPort: 8083
```

> 汇总清单为了"一张纸看懂全局"做了压缩（省略了 resources、MySQL 另两个实例等），完整版以 §7.2、§7.3 的分步清单为准。Nacos 配置导入的 Job（§7.2）与镜像加载（§7.1）仍需单独执行。

### 7.6 本章回顾

- 迁移策略：代码零改动，只有两类东西变了——部署清单（新增）与 Nacos 里的配置数据（localhost:端口 → DNS 名）。
- Compose 的每个机制都有翻译：depends_on+healthcheck → initContainer+就绪探针；nacos-init → Job；宿主端口错开 → DNS 名区分。
- 镜像进 kind 集群要 `kind load docker-image`；Spring Boot 用环境变量覆盖 Nacos 地址。
- 验证三步：Nacos 控制台看注册 → Gateway 走链路 → 杀 Pod 验自愈。
- 完整清单合并为 `k8s/ecommerce.yaml`，一条命令部署。

> **接下来**：§8 直面那个绕不开的问题——k8s 已经能服务发现了，Nacos 还要不要留？

---

## 8. 十字路口：Nacos 还是 k8s Service

### 8.0 本章定位说明

> **定位说明**：§7 是**学习路径**——原样搬运、代码零改动。本章是**演进路径**——当你在生产真正落地 k8s 时，需要回答：哪些 Spring Cloud 组件与 k8s 的能力重叠，哪些值得"退休"。两条路线没有绝对对错，本章给你的是判断框架。

### 8.1 上 k8s 后有什么变化

把 §1.3 的对照表反过来看，会发现一件有意思的事：**很多当初用 Spring Cloud 解决的问题，k8s 自己就内建了**：

| 当初 Spring Cloud 解决的问题 | k8s 的内建能力 | 重叠度 |
|---|---|---|
| 服务发现（Nacos Discovery：实例在哪） | Service + CoreDNS（§4.2） | 高 |
| 客户端负载均衡（LoadBalancer：调哪个实例） | Service 的 kube-proxy 负载均衡 | 高 |
| 配置中心（Nacos Config：集中管理配置） | ConfigMap / Secret（§5） | 中 |
| 健康检查与实例摘除（Nacos 心跳） | liveness/readiness 探针（§9.1） | 中 |
| 网关统一入口（Gateway 路由） | Ingress（七层路由部分） | 中 |
| 流量控制、熔断降级（Sentinel） | 无内建对应物 | 无 |
| 链路追踪（SkyWalking） | 无内建对应物 | 无 |

结论先摆出来：**重叠的是"基础设施三件套"（发现/负载均衡/配置），不重叠的是"应用治理"（限流/熔断/追踪）**。要不要退休，本质是判断重叠部分的取舍。

### 8.2 路线 A：保留 Nacos

就是 §7 的现状。什么时候选它：

```
保留 Nacos 的理由
─────────────────────────────────────────────────────
① 团队熟悉度     Nacos 用得好好的，迁移到 k8s 已经是一次大动作，
                 再顺手换掉服务发现，风险叠加
② 混合部署      部分服务在 k8s 内、部分还在 VM/其他环境 —— 跨环境
                 的服务发现只有 Nacos 这类中间件能统一
③ 配置管理体验   Nacos Config 的动态推送、历史版本、灰度发布，
                 ConfigMap 目前给不了（见 §8.5）
④ 控制台        可视化服务列表、实例详情、配置管理界面，运维友好
```

代价也很直白：**多养一个中间件**。Nacos 也要升级、备份、监控——如果它提供的价值 k8s 已经内建，这部分的运维成本就是冗余的。

### 8.3 路线 B：k8s 原生服务发现

**核心动作**：把 Feign 的调用目标从"服务名（Nacos 发现）"改成"k8s DNS 名（Service）"。三步改造：

**第一步：去掉 Nacos Discovery 依赖**，改为 k8s 原生方案。Spring 官方提供了适配层 [spring-cloud-kubernetes](https://spring.io/projects/spring-cloud-kubernetes)，DiscoveryClient 直接读 k8s API，业务代码不用改：

```xml
<!-- 替换 spring-cloud-starter-alibaba-nacos-discovery -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-kubernetes-client-discovery</artifactId>
</dependency>
```

**第二步：配置文件把 Nacos 地址换成 k8s 上下文**：

```yaml
spring:
  cloud:
    kubernetes:
      discovery:
        enabled: true        # 服务发现走 k8s API
```

Feign 代码 `@FeignClient("product-service")` 原样可用——`product-service` 从"Nacos 服务名"变成了"k8s DNS 名"（§4.2）。

**第三步：删除 Nacos 部署**：

```bash
kubectl delete -f 12-deploy-nacos.yaml -f 13-job-nacos-init.yaml
```

> **更极简的路径**：连 spring-cloud-kubernetes 都不用——Feign 的 URL 直接写 `http://product-service:8082`。但这样失去了"逻辑服务名"的抽象，服务改端口要改调用方。适配层是更平衡的选择。

**负载均衡谁负责？** k8s Service 本身就在 kube-proxy 层做轮询转发——每个请求随机（默认 iptables 概率）落到某个健康 Pod。Spring Cloud LoadBalancer 的精细策略（权重、区域感知）会失去，换来的是零依赖。

### 8.4 对比表与选型建议

| 维度 | 路线 A：保留 Nacos | 路线 B：k8s 原生 |
|---|---|---|
| 代码改动 | 零（§7 已验证） | 换一个依赖 + 几行配置 |
| 组件数量 | +Nacos（要运维） | 组件最少 |
| 跨环境一致性 | 好（VM/k8s 混合统一） | 仅限 k8s 内 |
| 配置管理 | 动态推送、版本、灰度 | ConfigMap 静态为主 |
| 运维复杂度 | 中间件运维成本 | 依赖 k8s 平台成熟度 |
| 负载均衡策略 | 可精细控制 | 简单轮询 |
| 团队学习成本 | 低（延续现状） | 需要理解 k8s 网络 |

**选型建议（务实版）**：

```
新系统、纯 k8s 环境         → 路线 B 优先：少一个中间件，架构最简
存量系统迁 k8s             → 先路线 A 落地，稳定后逐步演进（一次只动一件事）
VM/k8s 混合、多语言        → 路线 A：Nacos 的跨环境能力不可替代
配置管理是核心诉求         → 路线 A 保留 Nacos Config；或用外部配置平台
```

> **渐进式建议**：先按 §7 的路线 A 上线，让迁移风险最小化；等系统在 k8s 里稳定运行 1~2 个版本后，再把"去 Nacos"作为一个独立变更评估——迁移和演进分开做，不要在同一次变更里完成。

### 8.5 哪些 Spring Cloud 组件可以退休

把微服务指南的组件清单逐一过堂：

```
组件              上 k8s 后的建议        理由
────────────      ──────────────────    ──────────────────────────────
Nacos Discovery   可退休（演进）          k8s Service + DNS 内建
Nacos Config      部分可退（谨慎）        ConfigMap/Secret 覆盖静态配置；
                                         动态推送/版本管理仍是 Nacos 强项
OpenFeign         保留                    RPC 抽象与熔断集成，k8s 不提供
Spring Cloud      可退休                  k8s Service 自带负载均衡
LoadBalancer
Gateway           保留（分工调整）         认证、限流、路由动态化仍是应用层职责；
                                         纯七层转发可下沉 Ingress（§4.4）
Sentinel          保留                    k8s 无限流/熔断内建能力
SkyWalking        保留                    可观测性独立于 k8s
Seata             保留（按需）            分布式事务与应用强相关
```

判断框架一句话：**k8s 做"基础设施"（发现/路由/存储/调度），Spring Cloud 做"应用治理"（限流/熔断/追踪/事务）**。重叠的中间件才有"退休"问题，不重叠的没有。

### 8.6 本章回顾

- k8s 内建了服务发现、负载均衡、配置、健康检查——与 Spring Cloud 基础设施三件套高度重叠。
- 路线 A（保留 Nacos）：零改动、跨环境、配置体验好，代价是多养中间件；路线 B（k8s 原生）：架构最简，适配层是 spring-cloud-kubernetes。
- 选型核心：纯 k8s 新系统走 B；存量系统先 A 后演进；混合部署必须 A。
- 退休清单：Nacos Discovery、LoadBalancer 可退；Sentinel/SkyWalking/Feign 保留；Gateway 分工调整。
- 迁移与演进分开做：先跑通（§7），再瘦身（§8）。

> **接下来**：§9 补上生产级部署的最后一块拼图——探针、优雅下线与滚动发布策略。

---

## 9. 生产实践：探针、优雅下线与滚动发布

### 9.0 问题：k8s 怎么知道服务"好了"

在 §7.4 我们看到 Pod READY 1/1 后才算可用。但"容器进程在跑"≠"服务可以接流量"——Spring Boot 可能还在启动（连接数据库、注册 Nacos），也可能活着却已经僵死。Compose 用 `healthcheck` 回答这个问题，k8s 的回答是**探针（Probe）**，而且是三种。

### 9.1 探针三件套

```
┌────────────────────────────────────────────────────────────────┐
│                     Pod 生命周期与探针                           │
│                                                                │
│  启动 ──▶ startupProbe 成功 ──▶ readinessProbe 决定能否接流量    │
│    │              │                 │                          │
│    │              │ 失败：重启容器    │ 成功：加入 Service 后端    │
│    │              ▼                 │ 失败：从 Service 摘除     │
│    │         livenessProbe ◀────────┘（存活检查，一直伴随）      │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

| 探针 | 回答的问题 | 失败后果 | 对照 Compose |
|---|---|---|---|
| startupProbe | "启动完成了吗" | 重启容器 | healthcheck 的前半段 |
| livenessProbe | "还活着吗（是否僵死）" | 重启容器 | healthcheck 的持续检查 |
| readinessProbe | "可以接流量吗" | 摘除流量，不重启 | healthcheck + depends_on |

三种探测方式：`httpGet`（HTTP 状态码，最常用）、`tcpSocket`（端口通不通）、`exec`（容器内跑命令，最像 Compose 的 CMD-SHELL）。

以 Spring Boot 应用为例（httpGet 版）：

```yaml
          startupProbe:               # 启动慢的应用先给足耐心
            httpGet:
              path: /actuator/health/liveness
              port: 8083
            failureThreshold: 30      # 最多失败 30 次（约 5 分钟）才判失败
            periodSeconds: 10
          livenessProbe:              # 僵死检查：失败就重启
            httpGet:
              path: /actuator/health/liveness
              port: 8083
            periodSeconds: 10
          readinessProbe:             # 流量闸门：就绪才进 Service 后端
            httpGet:
              path: /actuator/health/readiness
              port: 8083
            initialDelaySeconds: 5
            periodSeconds: 5
```

> **§7.2 的回响**：Nacos 的 readinessProbe 直接探测 `/nacos/v1/ns/operator/metrics`，就是 Compose healthcheck 那条命令的"HTTP 化"。学会了这一章，回看 §7.2 就明白为什么那样写。

### 9.2 Spring Boot 侧配合

探针是 k8s 伸出的手，Spring Boot 要接住它。三件事：

**① 打开健康分组**。Spring Boot 2.3+ 把健康端点拆成 liveness / readiness 两组：

```properties
# application.yml（或 Nacos 中的配置）
management.endpoint.health.probes.enabled=true
management.health.livenessstate.enabled=true
management.health.readinessstate.enabled=true
```

`/actuator/health/liveness` 只报"JVM 是否活着"；`/actuator/health/readiness` 会检查**数据库、Nacos、MQ 等外部依赖**——readiness 失败说明"我还活着但暂时没法干活"，这正是"摘流量不重启"语义的来源。

> 依赖 `spring-boot-starter-actuator`（Spring Cloud 工程通常已传递引入；没有则补上）。

**② 优雅下线**。k8s 发来终止信号时（滚动更新、缩容），要让正在处理的请求跑完：

```properties
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s
```

配合 k8s 侧的 `terminationGracePeriodSeconds: 30`（默认 30 秒，Deployment spec 下与容器同级）。两者要匹配：Spring 的优雅窗口不能超过 k8s 给的宽限时间。

**③ 优雅上线的时序**。完整流程一次看全：

```text
新 Pod 启动
  → startupProbe 通过（启动完成）
  → readinessProbe 通过（外部依赖就绪，已注册到 Nacos）
  → Pod 加入 Service 后端 → 开始接流量
旧 Pod 收到 SIGTERM
  → readinessProbe 先失败 → 立即摘除流量
  → Spring Boot graceful shutdown 排空在途请求
  → 容器退出 → Pod 删除
```

> 这一步是对 §7.4"杀 Pod 验自愈"的升级理解：当时是"硬杀"，生产是"先摘流量、再排空、再退出"——探针让这个流程全程自动化。

### 9.3 滚动发布策略

§3.3 的滚动更新用的是默认参数。生产要调的就是这几个旋钮：

```yaml
spec:
  replicas: 2
  strategy:                    # 发布策略
    type: RollingUpdate        # 默认滚动更新（另一个是 Recreate：先全删再全建）
    rollingUpdate:
      maxSurge: 1              # 发布时最多多出几个 Pod（峰值 = replicas + maxSurge）
      maxUnavailable: 0        # 发布时最多允许几个 Pod 不可用（0 = 一个都不能少）
  minReadySeconds: 10          # 新 Pod 就绪后观察 10 秒再继续，防"假就绪"
```

```
maxSurge=1, maxUnavailable=0（生产稳妥配置）：
  副本 2 → 先加 1 个新 Pod（3 个在跑）→ 新 Pod 就绪 → 删 1 个旧 Pod → 再加 1 个新 → 删最后一个旧
  全程可用容量 ≥ 2，绝无空窗
```

两个延伸概念（本指南导航，深水区自寻资料）：**蓝绿发布**（备一套完整环境，流量整体切换，回滚即切回）、**金丝雀发布**（先放 5% 流量给新版本，观察后逐步加大）。k8s 原生只提供滚动更新，蓝绿/金丝雀需要额外工具（Argo Rollouts、服务网格）——学习滚动更新的旋钮是理解一切发布策略的地基。

### 9.4 演练：滚动更新与回滚

在 §7 的集群上做一次完整演练：

```bash
# ① 构建 1.1.0 新镜像并加载
docker build -t demo/order-service:1.1.0 .
kind load docker-image demo/order-service:1.1.0 --name ecommerce

# ② 触发滚动更新，并全程观察
kubectl set image -n ecommerce deploy/order-service order-service=demo/order-service:1.1.0
kubectl rollout status -n ecommerce deploy/order-service
kubectl get pods -n ecommerce -w        # 观察新旧 Pod 交替（§9.3 的时序图）

# ③ 发现新版本有问题，回滚
kubectl rollout undo -n ecommerce deploy/order-service
kubectl rollout status -n ecommerce deploy/order-service

# ④ 查看历史
kubectl rollout history -n ecommerce deploy/order-service
```

> **演练要点**：发布期间持续 `curl` Gateway 接口，应始终有响应——这是 `maxUnavailable: 0` 的直观效果。回滚不需要重新构建旧镜像：Deployment 保存着发布历史，旧 Pod 模板一键还原。

### 9.5 本章回顾

- 三探针分工：startup 启动闸门、liveness 存活检查（失败重启）、readiness 流量闸门（失败摘除）。
- Spring Boot 侧：开启 probes 健康分组、`server.shutdown=graceful`、宽限时间匹配。
- 生产发布旋钮：`maxSurge` / `maxUnavailable` / `minReadySeconds`；蓝绿与金丝雀是滚动更新的延伸。
- 演练闭环：set image → 观察 → undo → history。

> **接下来**：§10 换一个视角——不是"怎么用"，而是"什么该上 k8s"。

---

## 10. 实战决策：什么该上 k8s

### 10.1 上 k8s 的理由与代价

学完前九章，你已经有能力把系统搬上去。最后一章要回答的恰恰相反：**该不该搬**。

```
上 k8s 的理由                          上 k8s 的代价
─────────────                         ─────────────
自愈：服务挂了自动补                  学习成本：YAML、网络、存储都要新心智
弹性：副本数一条命令/自动伸缩          复杂度：集群本身要升级、备份、监控
发布：滚动更新/回滚内建               排错变难：一层容器 + 一层网络
资源治理：requests/limits 防争抢      边界：存储、网络、安全都要专门方案
可移植：清单即文档，环境可重建        人才：团队需要有人真的懂
```

一句话总结：**k8s 解决的是"规模带来的运维问题"**。3 个服务用 Compose 完全够；30 个服务、多环境、多人协作时，k8s 的成本才开始回本。

### 10.2 决策清单

把系统搬上 k8s 之前，逐项打勾：

```
□ 服务数量 ≥ 10，或增长趋势明确
□ 有滚动发布/快速回滚的真实需求（发布频率高）
□ 多个环境要一致（dev/test/prod 配置漂移已是痛点）
□ 团队有或愿意培养 k8s 运维能力（至少 1 人能排障）
□ 存储方案已明确（数据库、MQ 放不放 k8s 已有结论，§6.4）
□ 可观测性已跟上（日志采集、监控、追踪，否则上去了也是黑盒）
```

**什么不适合上 k8s**：

```
✗ 单体 + 低频发布（1 个月 1 次）  → Compose/VM 够了
✗ 核心数据库直接自建在 k8s        → 先从云托管开始（§6.4）
✗ 团队没人懂 k8s 且短期不招人     → 出了事故没人救
✗ 强依赖宿主机的特殊应用          → 如需要 GPU 直通、特权模式的遗留系统
```

### 10.3 常见误区

| 误区 | 事实 |
|---|---|
| "上了 k8s 系统就高可用了" | k8s 只提供机制；副本数、探针、存储、跨可用区都要自己配 |
| "k8s 是银弹，所有服务都上" | 数据库、MQ、批处理任务各有各的坑（§6.4、§12.3） |
| "Compose 能跑，k8s 肯定也能跑" | 端口、存储、健康检查、启动顺序都要重新翻译（§7 全程演示） |
| "探针可有可无" | 没有探针，滚动发布就是在裸奔——"假就绪"直接放流量 |
| "Secret 是加密的" | base64 而已（§5.4），安全靠 RBAC 与加密方案 |

> **避坑心得**：本指南的 §5~§9 每一章的"生产实践"小节，都是对某一个误区的正面回应。回看目录里的"生产实践"标题，就是一份浓缩的避坑清单。

---

## 11. 速查清单

### 11.1 kubectl 命令速查

```bash
# 查看
kubectl get pods -n ecommerce            # Pod 列表
kubectl get pods -o wide                 # 带 IP 与节点
kubectl get deploy,svc,pvc -n ecommerce  # 多资源一起查
kubectl describe pod <名> -n ecommerce   # 事件与详情（排查第一入口）
kubectl logs <pod> -n ecommerce -f       # 日志跟踪

# 操作
kubectl apply -f <file.yaml>             # 创建/更新（声明式，可重复执行）
kubectl delete -f <file.yaml>            # 按文件删除
kubectl scale deploy/<名> --replicas=5   # 手动扩缩容
kubectl set image deploy/<名> <容器>=<镜像>  # 滚动更新
kubectl rollout undo deploy/<名>         # 回滚
kubectl rollout history deploy/<名>      # 发布历史
kubectl exec -it <pod> -- sh             # 进容器（≈ docker exec）
kubectl port-forward svc/<名> 8080:8080  # 本地端口转发（学习集群访问主力）
kubectl explain pod.spec.containers      # 查字段说明
```

### 11.2 YAML 模板骨架速查

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: <服务名>
  namespace: ecommerce
  labels:
    app: <服务名>
spec:
  replicas: 2
  selector:
    matchLabels:
      app: <服务名>
  template:
    metadata:
      labels:
        app: <服务名>
    spec:
      containers:
        - name: <容器名>
          image: <镜像:版本>
          ports:
            - containerPort: <容器端口>
          env:                              # 环境变量（§5.3）
            - name: <变量名>
              value: <值>
          resources:                        # 生产必填（§3.4）
            requests: { cpu: 250m, memory: 512Mi }
            limits: { cpu: 1000m, memory: 1Gi }
          readinessProbe:                   # 探针（§9.1）
            httpGet:
              path: /actuator/health/readiness
              port: <容器端口>
---
apiVersion: v1
kind: Service
metadata:
  name: <服务名>
  namespace: ecommerce
spec:
  selector:
    app: <服务名>
  ports:
    - port: <对外端口>
      targetPort: <容器端口>
```

### 11.3 Compose 与 k8s 对照速查

| Compose | k8s | 关键差异 |
|---|---|---|
| `services: xxx` | Deployment + Service | 一个服务拆两个对象 |
| `image:` | `containers[].image` | 一致 |
| `environment:` | ConfigMap / Secret + env | 配置外置 |
| `ports: "8083:8083"` | containerPort + Service | 声明与暴露分离 |
| `volumes:`（命名卷） | PVC + PV | 申请与供给分离 |
| `depends_on` + healthcheck | initContainer + readinessProbe | 顺序靠"等待就绪" |
| `healthcheck` | 三种 Probe | 命令式 → 探测式 |
| `restart: always` | Deployment 自愈 | 内建，无需声明 |
| `scale` | replicas / HPA | 手动 / 自动 |
| 服务名互访 | k8s DNS（§4.2） | 体验一致，机制不同 |
| `up -d` | `kubectl apply -f` | 都朝期望状态收敛 |

### 11.4 常见故障排查速查

| 症状 | 先查 | 常见原因 |
|---|---|---|
| Pod Pending | `describe pod` 看 Events | 资源不足、PVC 没绑上、镜像拉取失败 |
| Pod CrashLoopBackOff | `logs` + `describe` | 启动即崩：配置错、探针路径错、依赖连不上 |
| Pod Running 但 READY 0/1 | `describe` 看 readiness 探测 | 探针失败：端口错、endpoint 错、依赖未就绪 |
| 服务间调不通 | 查 Service 的 selector 与 Pod 标签 | 标签不匹配（最常见）；端口写错 |
| DNS 解析失败 | 检查是否同命名空间 | 跨命名空间要用全名 `xxx.svc.cluster.local` |
| 数据丢了 | 查 Pod 是否用了 PVC | 裸容器文件系统即抛（§6.0） |
| 滚动更新卡住 | `rollout status` 看进度 | 新 Pod 一直不就绪（探针假失败）或镜像拉不下来 |

---

## 12. 延伸阅读

> 本章是重要但非主线的内容导航。学有余力或工作中遇到时再深入。

### 12.1 Helm：包管理

清单文件一多，管理方式就会撞墙：20 个服务的 YAML 怎么版本化？环境差异（dev 用 1 副本、prod 用 10 副本）怎么表达？**Helm** 是 k8s 的包管理器（≈ 系统里的 apt，≈ Compose 的"模板化"）：把清单打包成 Chart（模板 + 默认值），一条命令按环境参数部署：

```bash
helm install ecommerce ./ecommerce-chart -f values-prod.yaml
helm upgrade ecommerce ./ecommerce-chart --set replicas=5
helm rollback ecommerce 2
```

学习路径：先把手写 YAML 练熟（本指南主线），再学 Helm 模板化——本指南第 7 章的清单就是最好的"待模板化"素材。

### 12.2 HPA：弹性伸缩

§3.4 的 replicas 是"手动旋钮"，**HPA（Horizontal Pod Autoscaler）**是"自动旋钮"：按 CPU/内存/自定义指标自动调整副本数：

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: order-service
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: order-service
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 60   # 平均 CPU 超 60% 就扩容
```

前置条件：metrics-server 组件、所有容器写了 resources.requests（§3.4 又闭环了——requests 是 HPA 计算的依据）。

### 12.3 RabbitMQ、Kafka 上 k8s

有状态中间件（MQ、ES、Kafka）上 k8s 比无状态服务难一个量级，难点集中在三点：**数据持久化**（PVC 与副本的对应关系）、**稳定身份**（Kafka 的 broker ID、RabbitMQ 的节点名不能随机）、**集群网络**（节点间通信）。对应工具是 §6.4 预告的 StatefulSet + Headless Service（无虚拟 IP 的 Service，给每个 Pod 固定 DNS 名）。

```
Kafka on k8s 的最小认知地图：
  StatefulSet：kafka-0 / kafka-1 / kafka-2 身份稳定
  Headless Service：kafka-0.kafka.ecommerce.svc.cluster.local 直达每个 Pod
  PVC 模板：每个 broker 独立数据卷
```

学习建议：先用官方 Helm Chart 或 Operator（如 Strimzi）起步，不要手写 StatefulSet——这些项目把上述难点都封装好了。本指南只求你在架构图上认出它们的位置。

### 12.4 SkyWalking 上 k8s

微服务指南 §6 的 SkyWalking（OAP + UI）搬上 k8s 的两个要点：**探针注入**最简单的方式是把 agent 打进镜像（构建阶段拷贝 skywalking-agent.jar，JVM 参数加 `-javaagent`）；**后端依赖**上 OAP 默认 H2 内存存储（重启即失），生产要接 ES。学习环境保持"agent 在镜像 + OAP 单副本"即可，链路数据不持久化也无妨。

### 12.5 关联阅读

> **学习路线建议**：
>
> 1. **快速体验**（1 小时）：kind 建集群（§1.4）→ 部署 nginx Pod（§2.2）→ 升级成 Deployment 并杀 Pod 看自愈（§3.2）。
> 2. **系统学习**（半天）：按 §1 → §6 顺序读核心对象，每章动一次手；然后完整执行 §7 迁移。
> 3. **进阶深入**（1 周）：读完 §8 决策章，给服务配上 §9 的探针与优雅下线，做一次滚动发布 + 回滚演练。
> 4. **生产准备**（持续）：Helm 化清单（§12.1）、HPA 弹性（§12.2）、明确数据库与 MQ 的部署策略（§6.4、§12.3）、日志与监控接入。

- [Spring Cloud 微服务企业级指南](../Spring/spring-cloud-microservices-guide.md) — 本指南的场景、镜像与配置基线来源（姊妹篇）
- [Docker Compose 部署章节](../Spring/spring-cloud-microservices-guide.md#14-docker-compose-一键部署) — §7 迁移的出发点，对照阅读效果最佳
- [Kubernetes 官方文档（中文）](https://kubernetes.io/zh-cn/docs/home/) — 概念与字段的权威来源
- [kind 官方文档](https://kind.sigs.k8s.io/) — 本地集群与节点镜像版本列表
