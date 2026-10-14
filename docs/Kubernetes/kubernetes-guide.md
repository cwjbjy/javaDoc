# Kubernetes 部署实战指南

本指南是一份 Kubernetes 入门到实战的完整教程。从核心概念（Pod、Deployment、Service、ConfigMap）出发，通过 nginx 示例逐步构建多服务应用，最终掌握生产环境的探针、滚动发布等关键实践。

**项目基线**：Kubernetes 1.36.x（kind 节点镜像 `kindest/node:v1.36.4`）。本指南的 YAML 清单均以 k8s 1.36 官方文档为准；若你使用其他小版本，`apps/v1`、`networking.k8s.io/v1` 等 API 版本仍然一致。

**面向读者**：熟悉 Docker 基础、希望系统学习 Kubernetes 的开发者。

**范围外声明**：Helm 打包、HPA 弹性伸缩、CI/CD、RBAC 不在主线展开（§11 导航）；本指南针对本地学习集群，生产集群差异在相关章节标注。

**环境要求**：Docker Desktop、[kind](https://kind.sigs.k8s.io/)、[kubectl](https://kubernetes.io/zh-cn/docs/tasks/tools/)。三者均在本指南内给出安装与验证命令。

**示例证据状态**：除特别标注外，本指南的 YAML 清单与命令均为"完整示例，未在本机 kind 集群实际运行"。写作时已对照 k8s 1.36 官方文档核实 API 版本与字段名。

---

## 目录

1. [全景图：从 Docker Compose 到 Kubernetes](#1-全景图从-docker-compose-到-kubernetes)
   - [1.1 kind 三步搭建本地集群](#11-kind-三步搭建本地集群)
   - [1.2 安装 Kubernetes Dashboard](#12-安装-kubernetes-dashboard)
   - [1.3 kubectl 基础命令](#13-kubectl-基础命令)
2. [Pod：最小部署单元](#2-pod最小部署单元)
   - [2.0 问题：容器能不能直接部署](#20-问题容器能不能直接部署)
   - [2.1 Pod 是什么](#21-pod-是什么)
   - [2.2 第一个 YAML：运行 nginx Pod](#22-第一个-yaml运行-nginx-pod)
   - [2.3 kubectl 常用命令](#23-kubectl-常用命令)
   - [2.4 生产实践](#24-生产实践)
3. [Deployment：副本、滚动更新、回滚](#3-deployment副本滚动更新回滚)
   - [3.0 问题：Pod 挂了谁管](#30-问题pod-挂了谁管)
   - [3.1 声明式：期望状态驱动](#31-声明式期望状态驱动)
   - [3.2 第一个 Deployment](#32-第一个-deployment)
   - [3.3 滚动更新与回滚](#33-滚动更新与回滚)
   - [3.4 生产实践：副本数与资源配额](#34-生产实践副本数与资源配额)
4. [Service 与 Ingress：服务暴露与负载均衡](#4-service-与-ingress服务暴露与负载均衡)
   - [4.0 为什么需要 Service](#40-为什么需要-service)
   - [4.1 第一个 Service：ClusterIP](#41-第一个-serviceclusterip)
   - [4.2 Ingress：统一外部入口](#42-ingress统一外部入口)
   - [4.3 验证 Ingress](#43-验证-ingress)
5. [ConfigMap 与 Secret：配置与密钥](#5-configmap-与-secret配置与密钥)
   - [5.0 问题：配置写死在镜像里](#50-问题配置写死在镜像里)
   - [5.1 ConfigMap：应用配置](#51-configmap应用配置)
   - [5.2 Secret：敏感配置](#52-secret敏感配置)
   - [5.3 使用方式：env 与 volume](#53-使用方式env-与-volume)
   - [5.4 生产实践：Secret 不是加密](#54-生产实践secret-不是加密)
6. [多服务部署实战](#6-多服务部署实战)
   - [6.0 本章目标](#60-本章目标)
   - [6.1 架构规划](#61-架构规划)
   - [6.2 创建命名空间](#62-创建命名空间)
   - [6.3 部署 frontend 服务](#63-部署-frontend-服务)
   - [6.4 部署 api 和 backend 服务](#64-部署-api-和-backend-服务)
   - [6.5 配置 Ingress 统一入口](#65-配置-ingress-统一入口)
   - [6.6 完整验证流程](#66-完整验证流程)
   - [6.7 汇总清单](#67-汇总清单)
7. [十字路口：Spring Cloud 还是 k8s 原生](#7-十字路口spring-cloud-还是-k8s-原生)
   - [7.1 上 k8s 后有什么变化](#71-上-k8s-后有什么变化)
   - [7.2 选型建议](#72-选型建议)
   - [7.3 哪些 Spring Cloud 组件可以退休](#73-哪些-spring-cloud-组件可以退休)
8. [生产实践：探针与滚动发布](#8-生产实践探针与滚动发布)
   - [8.1 探针三件套](#81-探针三件套)
   - [8.2 滚动发布策略](#82-滚动发布策略)
   - [8.3 演练：滚动更新与回滚](#83-演练滚动更新与回滚)
9. [实战决策：什么该上 k8s](#9-实战决策什么该上-k8s)
   - [9.1 上 k8s 的理由与代价](#91-上-k8s-的理由与代价)
   - [9.2 决策清单](#92-决策清单)
   - [9.3 常见误区](#93-常见误区)
10. [速查清单](#10-速查清单)
    - [10.1 kubectl 命令速查](#101-kubectl-命令速查)
    - [10.2 YAML 模板骨架速查](#102-yaml-模板骨架速查)
    - [10.3 Compose 与 k8s 对照速查](#103-compose-与-k8s-对照速查)
    - [10.4 常见故障排查速查](#104-常见故障排查速查)
11. [延伸阅读](#11-延伸阅读)
    - [11.1 Helm：包管理](#111-helm包管理)
    - [11.2 HPA：弹性伸缩](#112-hpa弹性伸缩)
    - [11.3 关联阅读](#113-关联阅读)

---

## 1. Kubernetes

### 1.1 kind 三步搭建本地集群

**两个工具**：

- **kind**（Kubernetes in Docker）：在 Docker 容器里跑 k8s 节点，本地建集群最省事
- **kubectl**：命令行工具，所有集群操作（部署、查看、删除）都通过它

**第一步：安装 kind 与 kubectl**

```bash
# Windows（PowerShell 管理员）
winget install Kubernetes.kind
winget install Kubernetes.kubectl
# 或 macOS / Linux
brew install kind kubectl
```

验证：

```bash
kind version
kubectl version --client
```

**第二步：创建集群**

保存为 `kind-config.yaml`：

```yaml
kind: Cluster # 资源类型：集群
apiVersion: kind.x-k8s.io/v1alpha4 # kind 配置 API 版本
nodes:
  - role: control-plane # 节点角色：控制平面（另一个是 worker）
    extraPortMappings:
      - containerPort: 80 # 集群内部端口
        hostPort: 80 # 宿主机（你的电脑）端口
        protocol: TCP
      - containerPort: 443
        hostPort: 443
        protocol: TCP
```

**为什么要配置端口映射？**

Kind 集群运行在 Docker 容器中，默认情况下集群内部的服务无法从宿主机访问。配置 `extraPortMappings` 后：

- 宿主机的 80/443 端口会映射到集群内部的 80/443 端口
- 这样 Ingress Controller 就能通过 `http://localhost` 或 `http://demo.local` 访问
- 如果不配置，后续 §4.3 和 §6.5 的 Ingress 验证将无法从本地访问

**Windows 用户注意**：创建集群前需要停止 IIS 服务，否则 80 端口会被占用：

```powershell
# 检查 80 端口是否被占用
netstat -ano | findstr :80

# 如果显示有进程占用 80 端口，停止 IIS 服务（需要管理员权限）
iisreset /stop

# 或者停止 WWW 服务
Stop-Service W3SVC
```

**为什么要停止 IIS？**

Kind 创建集群时需要将宿主机的 80 端口映射到集群内部。如果 IIS（Internet Information Services）正在运行，它会占用 80 端口，导致 Kind 无法绑定端口而报错：

```
Error response from daemon: ports are not available: exposing port TCP 0.0.0.0:80
```

停止 IIS 后，Kind 才能成功创建集群并映射端口。如果后续需要 IIS，可以手动启动：`iisreset /start`

```bash
kind create cluster --name ecommerce --config kind-config.yaml --image kindest/node:v1.36.4
```

- `--name ecommerce`：集群名称，多集群时用于区分
- `--config kind-config.yaml`：指定集群配置
- `--image kindest/node:v1.36.4`：节点镜像，决定 k8s 版本。kind 会为每个节点启动一个 Docker 容器，容器内跑的就是这个镜像（内含 k8s 组件）

`kindest/node` 是 kind 官方维护的节点镜像，内置 kubelet、kubeadm、containerd 等。锁定版本避免与指南基线漂移。

**第三步：验证**

```bash
kubectl cluster-info
kubectl get nodes
```

```
NAME                     STATUS   ROLES           AGE   VERSION
ecommerce-control-plane  Ready    control-plane   1m    v1.36.4
```

**kind 仅用于学习**。生产环境用云托管（EKS/GKE/AKS）或 kubeadm 在真实服务器搭建多节点高可用集群，架构与 kind 差异较大，本指南不涉及。

Kind — 容器模拟节点（本地开发用）

```
┌────────────── 你的电脑 ──────────────┐
│ │
│ ┌──────────┐ ┌──────────┐ │
│ │ Docker │ │ Docker │ ... │ ← 每个"节点"是一个容器
│ │ 容器 │ │ 容器 │ │
│ │ (master) │ │ (worker) │ │
│ └──────────┘ └──────────┘ │
│ │
│ 一个集群，所有节点跑在同一台机器上 │
└──────────────────────────────────────┘
```

kubeadm — 真实机器组集群（生产环境用）

```
┌──────────────────────────────────────────────┐
│              一个集群，多台真实机器             │
│                                              │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │ 物理机/VM │  │ 物理机/VM │  │ 物理机/VM │   │
│  │ master-1 │  │ master-2 │  │ worker-1 │   │  ← 高可用：多个 master
│  └──────────┘  └──────────┘  └──────────┘   │
│                                              │
│  ┌──────────┐  ┌──────────┐                 │
│  │ 物理机/VM │  │ 物理机/VM │                 │
│  │ worker-2 │  │ worker-3 │                 │
│  └──────────┘  └──────────┘                 │
└──────────────────────────────────────────────┘
```

销毁集群：

```bash
kind delete cluster --name ecommerce
```

### 1.2 安装 Kubernetes Dashboard

Dashboard 提供 Web UI 可视化查看集群状态。部署后集群中会运行两个组件：

| 组件                        | 作用                                                                                                                                           |
| --------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------- |
| `kubernetes-dashboard`      | Dashboard **主组件**，提供 Web 界面用于查看和管理集群资源（Pod、Deployment、Service、Node 等），支持创建/编辑/删除操作及 RBAC 认证             |
| `dashboard-metrics-scraper` | **指标采集器**，定期从 Metrics Server 拉取 CPU、内存等实时数据并缓存，为 Dashboard 提供资源用量图表（如 Pod 的 CPU/内存折线图、Node 用量排行） |

两者是解耦设计：Dashboard 负责展示和操作，metrics-scraper 负责数据采集。如果不需要实时指标图表，可以只部署 Dashboard。

**第一步：部署 Dashboard**

**国内网络问题**：直接部署可能因无法拉取 Docker Hub 镜像导致 Pod 处于 `CrashLoopBackOff` 或 `ImagePullBackOff` 状态。需要下载部署文件并替换为国内镜像源。

下载部署文件：

```powershell
# Windows PowerShell
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/kubernetes/dashboard/v2.7.0/aio/deploy/recommended.yaml" -OutFile "recommended.yaml"
```

```bash
# Linux / macOS
curl -O https://raw.githubusercontent.com/kubernetes/dashboard/v2.7.0/aio/deploy/recommended.yaml
```

编辑 `recommended.yaml`，将镜像地址替换为阿里云镜像源：

| 原始镜像                              | 替换为                                                           |
| ------------------------------------- | ---------------------------------------------------------------- |
| `kubernetesui/dashboard:v2.7.0`       | `registry.aliyuncs.com/google_containers/dashboard:v2.7.0`       |
| `kubernetesui/metrics-scraper:v1.0.8` | `registry.aliyuncs.com/google_containers/metrics-scraper:v1.0.8` |

部署修改后的文件：

```bash
kubectl apply -f recommended.yaml
```

验证部署：

```bash
kubectl get pods -n kubernetes-dashboard
```

预期输出（两个 Pod 均为 `Running` 状态）：

```
NAME                                         READY   STATUS    RESTARTS   AGE
dashboard-metrics-scraper-6c78786c6f-xxxxx   1/1     Running   0          1m
kubernetes-dashboard-9b55494f9-xxxxx         1/1     Running   0          1m
```

这里需要多等待一会，如果STATUS状态还未Running，则过段时间再试下 kubectl get pods -n kubernetes-dashboard

**第二步：创建管理员账户**

Kubernetes 的 RBAC 模型将**身份**和**权限**分离，需要两个配置配合使用：

| 配置                 | 作用                                                    | 类比                 |
| -------------------- | ------------------------------------------------------- | -------------------- |
| `ServiceAccount`     | 创建服务账户（定义"你是谁"）                            | 办一张工牌           |
| `ClusterRoleBinding` | 将 `cluster-admin` 角色绑定到该账户（定义"你能干什么"） | 给工牌授予管理员权限 |

保存为 `dashboard-admin.yaml`：

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: admin-user
  namespace: kubernetes-dashboard
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: admin-user
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: cluster-admin
subjects:
  - kind: ServiceAccount
    name: admin-user
    namespace: kubernetes-dashboard
```

```bash
kubectl apply -f dashboard-admin.yaml
```

**第三步：创建永久 Token（解决 24 小时过期问题）**

保存为 `dashboard-token.yaml`：

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: admin-user
  namespace: kubernetes-dashboard
  annotations:
    kubernetes.io/service-account.name: admin-user
type: kubernetes.io/service-account-token
```

```bash
kubectl apply -f dashboard-token.yaml
```

获取 Token：

Linux / macOS：

```bash
kubectl get secret admin-user -n kubernetes-dashboard -o jsonpath="{.data.token}" | base64 -d
```

Windows PowerShell：

```powershell
[System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String((kubectl get secret admin-user -n kubernetes-dashboard -o jsonpath="{.data.token}")))
```

复制输出的 Token，登录时使用。

**第四步：启动代理**

```bash
kubectl proxy
```

保持此终端运行。

**第五步：浏览器访问**

打开：

```
http://localhost:8001/api/v1/namespaces/kubernetes-dashboard/services/https:kubernetes-dashboard:/proxy/
```

选择 "Token" 登录方式，粘贴上一步获取的 Token。

**安全提示**：此配置仅用于学习集群。生产环境请使用 RBAC + OIDC，不要暴露 Dashboard 到公网。

### 1.3 kubectl 基础命令

`kubectl` 是操作集群的命令行工具。常用命令：

```bash
kubectl get pods -A        # 查看所有命名空间的 Pod
kubectl get namespaces     # 查看命名空间
kubectl api-resources      # 查看所有资源类型
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

`kube-system` 命名空间运行控制平面组件。`local-path-storage` 是 kind 预置的存储服务（提供本地持久卷）。

命名空间（namespace）是集群内的隔离单位，类似"文件夹"。本指南后续会创建 `ecommerce` 命名空间。

---

## 2. Pod：最小部署单元

### 2.0 问题：容器能不能直接部署

k8s 里容器不能直接部署，必须包在 Pod 里。原因：有些容器需要紧密协作（共享网络、存储），必须部署在同一台机器上。

典型场景：

- 业务容器 + 日志采集容器（共享卷）
- 业务容器 + 监控 Sidecar（共享 localhost）

Pod 是 K8s **最小部署单元**，可包含 1 个或多个容器，共享网络和存储。

### 2.1 Pod 是什么

Pod 结构：

```
┌────────────────────────── Pod ──────────────────────────┐
│                                                         │
│   共享网络：一个 IP、共享 localhost、共享端口空间         │
│                                                         │
│   ┌──────────────┐   ┌──────────────┐   ┌────────────┐  │
│   │  容器 A       │   │  容器 B       │   │  pause 容器 │  │
│   │ （业务主容器） │   │ （Sidecar）   │   │ 占住网络   │  │
│   └──────────────┘   └──────────────┘   └────────────┘  │
│                                                         │
│   共享卷：所有容器挂载同一个卷读写同一份数据              │
└─────────────────────────────────────────────────────────┘
```

要点：

1. **一个 Pod 一个 IP**：容器间用 `localhost` 互访
2. **pause 容器**：K8s 先启动 pause 容器创建网络命名空间并获得 IP，业务容器加入其中。即使业务容器崩溃重启，pause 容器不受影响，Pod 的 IP 始终稳定
3. **共享卷**：Pod 声明卷，所有容器挂载它
4. **Sidecar（边车）**：与主容器部署在同一 Pod 中的辅助容器，为主容器提供额外能力（日志采集、监控、代理等）。类比摩托车 + 边车，边车不驱动但提供额外功能

Compose 里没有 Pod 的直接对应物。绝大多数时候一个 Pod 只有一个容器。

### 2.2 第一个 YAML：运行 nginx Pod

最小 Pod 清单：

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: nginx-demo
  labels:
    app: nginx-demo
spec:
  containers:
    - name: nginx
      image: nginx:1.27
      ports:
        - containerPort: 80
```

`containerPort` 只是声明容器监听端口，对外暴露由 Service（§4）决定。

**国内网络问题**：直接 `apply` 可能因无法拉取 Docker Hub 镜像导致 `ImagePullBackOff`。解决方法：在宿主机拉取镜像后手动导入到 Kind 节点。

```bash
# 1. 宿主机拉取镜像（Docker Desktop 配置代理后可正常拉取）
docker pull nginx:1.27

# 2. 导出为 tar 文件
docker save -o nginx.tar nginx:1.27

# 3. 拷贝到 Kind 节点容器（注意：不要拷贝到 /tmp，Kind 节点中 /tmp 是 tmpfs 挂载点，文件不可见）
docker cp nginx.tar ecommerce-control-plane:/nginx.tar

# 4. 进入 Kind 节点容器
docker exec -it ecommerce-control-plane bash

# 5. 导入镜像到 containerd 的 k8s.io 命名空间
ctr -n k8s.io images import /nginx.tar

# 6. 验证镜像已导入
ctr -n k8s.io images ls | grep nginx

# 7. 退出节点容器
exit
```

应用：

```bash
kubectl apply -f pod-nginx.yaml
kubectl get pods
```

```
NAME         READY   STATUS    RESTARTS   AGE
nginx-demo   1/1     Running   0          12s
```

删除 Pod 有两种方式：

```bash
# 方式一：通过文件删除（推荐，精确匹配 YAML 中定义的资源）
kubectl delete -f pod-nginx.yaml

# 方式二：通过资源名删除
kubectl delete pod nginx-demo
```

两种命令都只删除集群中的资源，本地 `pod-nginx.yaml` 文件不受影响。

### 2.3 kubectl 常用命令

```bash
kubectl get pods                    # 列表 + 状态
kubectl get pods -o wide            # 加 IP、所在节点
kubectl describe pod nginx-demo     # 事件与详情
kubectl logs nginx-demo             # 看日志
kubectl exec -it nginx-demo -- sh   # 进容器
kubectl delete pod nginx-demo       # 删除 Pod
kubectl delete -f pod-nginx.yaml    # 按文件删除
```

`kubectl explain pod.spec.containers` 可查字段含义。

### 2.4 生产实践

- **不要直接创建裸 Pod**：挂了不会自动重生，用 Deployment（§3）
- **不要进生产容器改东西**：容器是即抛的，改清单重新 apply
- **数据不能只放容器文件系统**：Pod 重建后数据丢失，需要持久卷（PV/PVC）

**接下来**：§3 Deployment——副本、自愈、滚动更新。

---

## 3. Deployment：副本、滚动更新、回滚

### 3.0 问题：Pod 挂了谁管

```bash
kubectl delete pod nginx-demo
kubectl get pods        # 空空如也
```

Pod 死了就是死了。Deployment 解决自愈问题：声明副本数，少了补、多了杀、病了换。

### 3.1 声明式：期望状态驱动

```
命令式（Compose）                    声明式（k8s）
─────────────────                   ─────────────────
docker run/stop/rm                  kubectl apply -f deploy.yaml
你一步步"操作"系统                   清单写着 replicas: 2
                                    k8s 持续调谐：
                                      实际 2？✔ 保持
                                      掉了 1？→ 自动补
                                      改 5？→ 自动扩
```

k8s 内部有**调谐循环（Reconcile Loop）**：控制器不断比对期望状态与实际状态，发现偏差就修正。`kubectl apply` 语义是"让集群与文件一致"。

### 3.2 第一个 Deployment

保存为 `deployment-nginx.yaml`：

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-deployment
  labels: # ← Deployment 自身的标签（给 Deployment 打标签）
    app: nginx #   用于筛选 Deployment，不影响 Pod 创建
spec:
  replicas: 2
  selector:
    matchLabels:
      app: nginx # ← 选择器：管理哪些 Pod？（必须和 template.labels 一致）
  template: # ← Pod 模板（Blueprint）
    metadata:
      labels: # ← 生成的 Pod 的标签（给 Pod 打标签）
        app: nginx #   必须包含 selector 的所有标签，可额外添加
    spec:
      containers:
        - name: nginx # ← 容器名（和标签无关，仅用于 kubectl logs/exec -c 指定容器）
          image: nginx:1.27
          ports:
            - containerPort: 80
```

Deployment 不是管理已存在的 Pod，而是根据 template 自己创建新的 Pod：

```bash
kubectl apply -f deployment-nginx.yaml
kubectl get pods            # 2 个 Running
kubectl get deploy          # DESIRED=2 CURRENT=2 READY=2
```

预期输出：

```
NAME                                READY   STATUS    RESTARTS   AGE
nginx-deployment-xxxxx-aaaaa        1/1     Running   0          10s
nginx-deployment-xxxxx-bbbbb        1/1     Running   0          10s
```

自愈实验——删掉一个 Pod：

```bash
kubectl delete pod <Pod 名>
kubectl get pods -w        # 观察：旧 Pod Terminating，新 Pod 立刻创建
```

Deployment 是"死了换新的"，不依赖所在机器——只要集群有资源，副本数永远被补满。

### 3.3 滚动更新与回滚

更新镜像版本：

```bash
kubectl set image deploy/nginx-deployment nginx=nginx:1.28
kubectl rollout status deploy/nginx-deployment
```

**国内用户注意**：`nginx:1.28` 也需要从 Docker Hub 拉取，直接执行会报 `ImagePullBackOff`。需要先按 §2.2 的方式手动导入新镜像：

```bash
# 宿主机拉取并导出
docker pull nginx:1.28
docker save -o nginx-1.28.tar nginx:1.28

# 拷贝到 Kind 节点并导入
docker cp nginx-1.28.tar ecommerce-control-plane:/nginx-1.28.tar
docker exec -it ecommerce-control-plane bash
ctr -n k8s.io images import /nginx-1.28.tar
exit

# 再执行滚动更新
kubectl set image deploy/nginx-deployment nginx=nginx:1.28
```

默认策略是**滚动更新（RollingUpdate）**：先起新 Pod、健康后逐步替换旧 Pod，全程不中断服务。

回滚：

```bash
# 回退到上一个版本
kubectl rollout undo deploy/nginx-deployment

# 回退到指定版本
kubectl rollout undo deploy/nginx-deployment --to-revision=2

# 查看所有历史版本
kubectl rollout history deploy/nginx-deployment
```

回滚原理——Deployment 会保留每个版本的 ReplicaSet：

```
滚动更新前：
  Deployment → ReplicaSet #1（nginx:1.27）← 镜像已导入

滚动更新后：
  Deployment → ReplicaSet #2（nginx:1.28）← 新镜像
               ReplicaSet #1（nginx:1.27）← 保留着，没删

回滚后：
  Deployment → ReplicaSet #1（nginx:1.27）← 直接用旧的，不需要重新拉取
```

一次 `rollout undo` 默认回退一个版本。用 `--to-revision=N` 可跳到任意历史版本。回滚不需要重新导入镜像，因为旧版本的 ReplicaSet 和镜像都还在集群中。

### 3.4 生产实践：副本数与资源配额

**副本数**：核心服务 ≥2（保证单 Pod 故障不影响可用）。

**资源配额必须写**：加到 deployment-nginx.yaml 的 containers 下面

```yaml
resources:
  requests: # 预留资源，够用才能申请到
    cpu: "250m"
    memory: "512Mi"
  limits: # 使用上限：CPU 限流，内存 OOM 杀掉重启
    cpu: "1000m"
    memory: "1Gi"
```

- requests = 预约（Scheduler 按此挑选节点）
- limits = 上限（超出后 CPU 限流；内存超出 → OOMKilled → 重启）

**接下来**：§4 Service 与 Ingress——服务暴露与负载均衡。

---

## 4. Service 与 Ingress：服务暴露与负载均衡

### 4.0 为什么需要 Service

Pod IP 是临时的——Pod 删除重建后 IP 会变，且同一服务有多个副本。Service 给一组 Pod 提供**固定的内部地址**（虚拟 IP + DNS 名），解决服务发现问题。

```
Service = 集群内部的"前台接待台"

三大作用：
  ├── 1. 提供稳定地址（最重要）
  │      Pod IP 会变，但 Service DNS 永远不变
  │      例：nginx-service:80（Pod 怎么重启，地址不变）
  │
  ├── 2. 负载均衡
  │      自动分发请求到后端的多个 Pod 副本
  │      例：frontend 有 2 个 Pod，Service 轮询分发流量
  │
  └── 3. Pod 间通信
         服务之间通过 Service DNS 互相访问
         例：api Pod 通过 frontend-service 访问 frontend
```

```
                    ┌─────────────────────────────┐
                    │         Service             │
                    │   名字: nginx-service       │
                    │   虚拟 IP: 10.96.0.5（不变） │
                    └──────────┬──────────────────┘
                               │ selector: app=nginx
                 ┌─────────────┼─────────────┐
                 ▼             ▼             ▼
           ┌──────────┐ ┌──────────┐ ┌──────────┐
           │ Pod #1   │ │ Pod #2   │ │ Pod #3   │
           └──────────┘ └──────────┘ └──────────┘
```

### 4.1 第一个 Service：ClusterIP

将 Service 的 `selector` 指向 §3.2 Deployment 创建的 nginx Pod（标签 `app: nginx`）：

```yaml
apiVersion: v1
kind: Service
metadata:
  name: nginx-service
spec:
  type: ClusterIP
  selector:
    app: nginx # ← 匹配 Deployment 创建的 Pod 标签
  ports:
    - port: 80 # Service 对外端口
      targetPort: 80 # 转发到 Pod 的端口（= containerPort）
```

```bash
kubectl apply -f svc-nginx-service.yaml
kubectl get svc
```

提示：可通过 kubectl delete svc user-service 删掉已创建的 Service

```
NAME            TYPE        CLUSTER-IP    EXTERNAL-IP   PORT(S)
nginx-service   ClusterIP   10.96.0.5     <none>        80/TCP
kubernetes      ClusterIP   10.96.0.1     <none>        443/TCP
```

此时集群中的完整链路：

```
集群内其他 Pod 访问 http://nginx-service:80
        │
        ▼
  Service（selector: app=nginx）
        │ 负载均衡
        ├── nginx Pod #1（nginx-deployment-xxxxx-aaaaa）
        └── nginx Pod #2（nginx-deployment-xxxxx-bbbbb）
```

**关联回顾**：Deployment 创建并管理 2 个 Pod，Service 通过标签找到这些 Pod 并转发流量。两者通过 `app: nginx` 标签关联。

端口概念：

- `containerPort`：容器监听端口（Pod 中声明，如 nginx 监听 `80`）
- `targetPort`：Service 转发到 Pod 的端口（需与 containerPort 一致）
- `port`：Service 对外提供的端口（集群内其他服务访问时用）

### 4.2 Ingress：统一外部入口

Ingress 是七层路由（按域名、路径分发），将**外部请求**路由到集群内部的 Service。

```
外部请求（浏览器）
  http://demo.local/
        │
        ▼
  ┌──────────────┐
  │  Ingress      │  ← 集群的大门（按路径分发）
  └──────┬───────┘
         │ /
         ▼
  ┌──────────────┐
  │  Service      │  ← 集群内部的接待台（按标签选 Pod）
  │  nginx-service│
  └──────┬───────┘
         ▼
  ┌──────────────┐
  │  nginx Pod    │  ← 实际处理请求
  └──────────────┘
```

保存为 `ingress-ecommerce.yaml`：

```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ecommerce
spec:
  rules:
    - host: demo.local
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: nginx-service # ← 指向上面创建的 Service
                port:
                  number: 80
```

### 4.3 验证 Ingress

**Ingress 资源 vs Ingress Controller**

| 概念                   | 类型         | 说明                                                      |
| ---------------------- | ------------ | --------------------------------------------------------- |
| **Ingress**            | k8s 内置资源 | 和 Deployment、Service 一样，只是 YAML 配置，定义路由规则 |
| **Ingress Controller** | 独立软件     | 实际执行路由规则的程序（反向代理），需要单独安装          |

类比：Ingress 资源 = 交通规则，Ingress Controller = 交警。没有交警，规则只是纸上的文字。

对比其他 k8s 资源：Deployment 由 kubelet（内置）执行，Service 由 kube-proxy（内置）执行，Ingress 需要 Ingress Controller（**需安装**）执行。

**第一步：安装 Ingress Controller**

**国内网络用户**：Controller 镜像来自 `registry.k8s.io`，直接 `kubectl apply` 可能拉取失败。建议手动导入镜像（方式与 §2.2 导入 nginx 相同）：

```bash
# 1. 宿主机拉取并导出镜像
docker pull registry.k8s.io/ingress-nginx/controller:v1.12.1
docker save -o ingress-nginx.tar registry.k8s.io/ingress-nginx/controller:v1.12.1

# 2. 拷贝到 Kind 节点并导入
docker cp ingress-nginx.tar ecommerce-control-plane:/ingress-nginx.tar
docker exec -it ecommerce-control-plane bash
ctr -n k8s.io images import /ingress-nginx.tar
exit

# 3. 部署 Ingress Controller（也需要走代理，但这里电脑走代理能拉取下来，上面镜像是走代理也拉不下来）
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
```

**deploy.yaml 的作用**：

这个 YAML 文件包含 Ingress Controller 的完整部署配置：

- **Deployment**：运行 Ingress Controller Pod（nginx 反向代理）
- **Service**：暴露 80/443 端口，接收外部流量
- **ConfigMap**：nginx 配置文件
- **RBAC**：权限控制（让 Controller 能读取 Ingress 资源）
- **Namespace**：隔离在 `ingress-nginx` 命名空间

执行 `kubectl apply` 后，Ingress Controller 启动并开始监听 Ingress 规则。

**第二步：等待 Controller 就绪**

```bash
kubectl wait --namespace ingress-nginx --for=condition=ready pod --selector=app.kubernetes.io/component=controller --timeout=90s
```

**第三步：应用 Ingress 配置**

```bash
kubectl apply -f ingress-ecommerce.yaml
```

**第四步：配置 hosts 文件**

将 `demo.local` 指向本地 `127.0.0.1`，使浏览器能解析该域名：

- **Windows**：以管理员身份打开记事本 → 打开 `C:\Windows\System32\drivers\etc\hosts` → 末尾添加：
  ```
  127.0.0.1 demo.local
  ```
- **Linux / macOS**：
  ```bash
  echo "127.0.0.1 demo.local" | sudo tee -a /etc/hosts
  ```

**刷新 DNS 缓存**（修改 hosts 后立即生效）：

```powershell
# Windows
ipconfig /flushdns
```

```bash
# Linux（根据发行版选择）
sudo systemctl restart nscd
# 或
sudo systemd-resolve --flush-caches

# macOS
sudo dscacheutil -flushcache
sudo killall -HUP mDNSResponder
```

验证 hosts 生效：

```bash
# Linux / macOS
ping demo.local
# 应返回 127.0.0.1
```

```powershell
# Windows PowerShell
ping demo.local
# 应返回 127.0.0.1
```

**第五步：访问验证**

```powershell
# Linux / macOS:
curl http://demo.local
# Windows PowerShell:
curl.exe http://demo.local
```

预期返回 nginx 欢迎页面 HTML。如果看到 `404` 说明 Ingress 路由未生效，检查 Controller 是否就绪；如果看到 `503` 说明 Service 未匹配到 Pod，检查 `selector` 标签。

---

## 5. ConfigMap 与 Secret：配置与密钥

### 5.0 问题：配置写死在镜像里

镜像应该只含代码，不含环境配置。k8s 用 ConfigMap（普通配置）和 Secret（敏感配置）解决。

```
Compose                         k8s
────────                        ────
environment:                    ConfigMap / Secret
  MYSQL_HOST: mysql-user        ├─ 以环境变量注入
  MYSQL_PASSWORD: root123       └─ 或以文件挂载
```

### 5.1 ConfigMap：应用配置

以 nginx 为例，用 ConfigMap 自定义 nginx 的欢迎页面内容。

保存为 `configmap-nginx.yaml`：

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: nginx-config
data:
  index.html: |
    <html>
    <body>
      <h1>Hello from Kubernetes ConfigMap!</h1>
    </body>
    </html>
```

```bash
kubectl apply -f configmap-nginx.yaml
kubectl get configmap
```

验证 ConfigMap 内容：

```bash
kubectl describe configmap nginx-config
```

预期输出中能看到 `index.html` 的完整 HTML 内容。

### 5.2 Secret：敏感配置

Secret 的安全性不在于"加密"，而在于：
权限隔离：通过 RBAC 控制谁能访问
审计追踪：可以监控谁访问了 Secret
可选加密：生产环境可以开启 etcd 加密

Secret 用于存放密码、密钥等敏感信息。以应用连接外部数据库的密码为例：

保存为 `secret-mysql.yaml`：

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: mysql-secret
type: Opaque
stringData:
  mysql-root-password: "root123"
```

```bash
kubectl apply -f secret-mysql.yaml
kubectl get secret mysql-secret
```

`stringData` 明文书写，k8s 自动转 base64。

验证 Secret 内容：

```bash
# Linux / macOS
kubectl get secret mysql-secret -o jsonpath="{.data.mysql-root-password}" | base64 -d
# 输出: root123
```

```powershell
# Windows PowerShell
[System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String((kubectl get secret mysql-secret -o jsonpath="{.data.mysql-root-password}")))
# 输出: root123
```

### 5.3 使用方式：env 与 volume

ConfigMap 和 Secret 有两种使用方式：注入环境变量或挂载为文件。

**方式 1：挂载为文件（推荐用于配置文件）**

将 ConfigMap 挂载到 nginx 容器的 `/usr/share/nginx/html` 目录，替换默认欢迎页。

需要在 Pod `spec` 中添加两部分配置：

```yaml
spec:
  containers:
    - name: nginx
      # ... 其他配置 ...
      volumeMounts: # ← 在容器内声明挂载点
        - name: html-content
          mountPath: /usr/share/nginx/html
  volumes: # ← 与 containers 同级，声明卷来源
    - name: html-content
      configMap:
        name: nginx-config
```

完整 Deployment 示例：

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-deployment
spec:
  replicas: 2
  selector:
    matchLabels:
      app: nginx
  template:
    metadata:
      labels:
        app: nginx
    spec:
      containers:
        - name: nginx
          image: nginx:1.27
          ports:
            - containerPort: 80
          volumeMounts:
            - name: html-content
              mountPath: /usr/share/nginx/html
      volumes:
        - name: html-content
          configMap:
            name: nginx-config
```

```bash
# 1. 更新 Deployment（挂载 ConfigMap）
kubectl apply -f deployment-nginx.yaml

# 2. 等待 Pod 重启
kubectl get pods -l app=nginx

# 3. 端口转发（保持此终端运行，不要关闭）
kubectl port-forward svc/nginx-service 8080:80
# 显示以下信息表示成功：
# Forwarding from 127.0.0.1:8080 -> 80
# Forwarding from [::1]:8080 -> 80
```

**另开一个 PowerShell 窗口**执行访问命令：

```powershell
# Linux / macOS:
curl http://localhost:8080
# Windows PowerShell:
curl.exe http://localhost:8080
```

预期返回：

```html
<html>
  <body>
    <h1>Hello from Kubernetes ConfigMap!</h1>
  </body>
</html>
```

如果仍然显示 nginx 默认欢迎页，说明 ConfigMap 未正确挂载。用 `kubectl describe pod <Pod名>` 检查 volumes 配置。

**方式 2：注入环境变量（适用于应用配置）**

将 ConfigMap 或 Secret 的值注入为容器的环境变量。应用启动时读取这些变量。

**第一步：创建用于 env 注入的 ConfigMap 和 Secret**

```yaml
# ConfigMap：应用配置
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
data:
  app-env: "production"
  log-level: "info"
---
# Secret：敏感信息
apiVersion: v1
kind: Secret
metadata:
  name: app-secret
type: Opaque
stringData:
  api-key: "sk-xxxx-xxxx-xxxx"
```

```bash
kubectl apply -f app-config.yaml
kubectl get configmap app-config
kubectl get secret app-secret
```

**第二步：Deployment 中引用**

`env` 配置加在 **Deployment 的容器 spec 中**：

```yaml
# Deployment：注入环境变量（以 nginx 为例）
apiVersion: apps/v1
kind: Deployment
metadata:
  name: nginx-deployment
spec:
  replicas: 2
  selector:
    matchLabels:
      app: nginx
  template:
    metadata:
      labels:
        app: nginx
    spec:
      containers:
        - name: nginx
          image: nginx:1.27
          ports:
            - containerPort: 80
          env: # ← 注入环境变量
            - name: APP_ENV
              valueFrom:
                configMapKeyRef:
                  name: app-config # 引用上面创建的 ConfigMap
                  key: app-env
            - name: API_KEY
              valueFrom:
                secretKeyRef:
                  name: app-secret # 引用上面创建的 Secret
                  key: api-key
```

**ConfigMap vs Secret**：两者用法完全相同，区别在于：

- ConfigMap：存放非敏感配置（环境变量、特性开关）
- Secret：存放敏感信息（密码、API Key），k8s 提供额外保护

**env 注入 vs volume 挂载的区别**：

| 方式        | 访问方式             | 更新机制         | 适用场景     |
| ----------- | -------------------- | ---------------- | ------------ |
| env 注入    | `$APP_ENV` 环境变量  | 需重启 Pod       | 应用配置项   |
| volume 挂载 | `/path/to/file` 文件 | 自动同步（~30s） | 完整配置文件 |

**第三步：验证环境变量是否注入成功**

```bash
# 部署
kubectl apply -f nginx-deployment.yaml

# 查看 Pod
kubectl get pods
# NAME                                READY   STATUS    RESTARTS   AGE
# nginx-deployment-5d5b9c8f7-xxxxx   1/1     Running   0          10s

# 进入容器查看环境变量
kubectl exec -it nginx-deployment-5d5b9c8f7-xxxxx -- env | grep -E 'APP_ENV|API_KEY'
# APP_ENV=production
# API_KEY=sk-xxxx-xxxx-xxxx
```

- **volume 挂载**：改 ConfigMap 后 Pod 自动同步，无需重启
- **env 注入**：改配置需要重启 Pod 才生效

### 5.4 生产实践：Secret 不是加密

- **base64 不是加密**：Secret 的价值是权限隔离和审计
- **Secret 默认不落盘加密**：生产应开启 etcd 加密
- **不要提交 git**：生产用 External Secrets Operator 或云密钥管理服务

---

## 6. 多服务部署实战

### 6.0 本章目标

本章是"毕业典礼"——把前面学到的 Pod、Deployment、Service、Ingress、ConfigMap、Secret 全部串起来，部署一个**多服务应用**并完整验证。

**说明**：本节用 nginx 模拟多个服务，演示 k8s 的部署、路由、配置注入等核心能力。nginx 只能返回静态内容，无法模拟服务间调用。如果需要演示完整的服务调用链路，需要构建真实的应用镜像。

### 6.1 架构规划

部署 3 个 nginx 服务，模拟微服务架构：

```
┌─────────────────────────────────────────────────────────┐
│           k8s 集群（namespace: ecommerce）               │
└─────────────────────────────────────────────────────────┘

  Ingress（子域名路由）
       │
       ├── frontend.demo.local ──▶ frontend ──▶ nginx（静态页面）
       │
       ├── api.demo.local ───────▶ api ───────▶ nginx（模拟 API）
       │
       └── backend.demo.local ───▶ backend ───▶ nginx（模拟后端）

  每个服务：
  - Deployment（2 replicas）
  - Service（ClusterIP）
  - ConfigMap（自定义 index.html）
```

**服务清单**：

| 服务     | 镜像       | 容器端口 | k8s DNS 名 | 内容          |
| -------- | ---------- | -------- | ---------- | ------------- |
| frontend | nginx:1.27 | 80       | frontend   | 前端页面      |
| api      | nginx:1.27 | 80       | api        | 模拟 API 响应 |
| backend  | nginx:1.27 | 80       | backend    | 模拟后端响应  |

### 6.2 创建命名空间

**说明**：§1-§5 的示例都运行在 k8s 自动创建的 `default` 命名空间中。从本章开始，我们创建独立的 `ecommerce` 命名空间，模拟真实项目的资源隔离。

**第一步：创建命名空间**

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

后续所有清单都带 `namespace: ecommerce`。

### 6.3 部署 frontend 服务

**第一步：创建 ConfigMap（自定义欢迎页）**

ConfigMap 用于存储配置数据。这里我们创建一个包含自定义 HTML 内容的 ConfigMap，用来替换 nginx 的默认欢迎页。

```yaml
# 01-configmap-frontend.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: frontend-config
  namespace: ecommerce
data:
  # data 字段存储配置数据，key 是文件名，value 是文件内容
  index.html: |
    <html>
    <head><title>Frontend Service</title></head>
    <body>
      <h1>Welcome to Frontend Service</h1>
      <p>This is served by the frontend Pod.</p>
    </body>
    </html>
```

**理解要点**：

- ConfigMap 创建后，`index.html` 的内容会存储在 k8s 中
- 后续通过 volume 挂载，这个文件会出现在容器的 `/usr/share/nginx/html/index.html`
- 修改 ConfigMap 后，挂载的文件会自动更新（约 30 秒），无需重启 Pod

**第二步：创建 Deployment + Service**

Deployment 负责运行 Pod，Service 负责提供稳定的访问入口。

```yaml
# 02-deploy-frontend.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: frontend
  namespace: ecommerce
spec:
  replicas: 2 # 创建 2 个 Pod 副本，实现高可用
  selector:
    matchLabels:
      app: frontend # 选择标签为 app=frontend 的 Pod
  template:
    metadata:
      labels:
        app: frontend # Pod 的标签，用于被 Service 选择
    spec:
      containers:
        - name: nginx
          image: nginx:1.27
          ports:
            - containerPort: 80 # 容器监听 80 端口
          volumeMounts:
            - name: html-content
              mountPath: /usr/share/nginx/html # 将 volume 挂载到 nginx 的 html 目录
      volumes:
        - name: html-content
          configMap:
            name: frontend-config # volume 的数据来源是 frontend-config ConfigMap
---
# Service 提供稳定的访问入口
apiVersion: v1
kind: Service
metadata:
  name: frontend-service
  namespace: ecommerce
spec:
  type: ClusterIP # 默认类型，仅在集群内部可访问
  selector:
    app: frontend # 选择标签为 app=frontend 的 Pod
  ports:
    - port: 80 # Service 对外暴露的端口
      targetPort: 80 # 转发到 Pod 的端口（= containerPort）
```

**理解要点**：

- **Deployment**：创建 2 个 nginx Pod 副本，每个 Pod 都挂载 frontend-config 的内容
- **volume 挂载**：ConfigMap 的 `index.html` 内容会出现在容器的 `/usr/share/nginx/html/index.html`
- **Service**：提供固定地址 `frontend-service:80`，自动负载均衡到 2 个 Pod
- **标签匹配**：Service 的 `selector: app: frontend` 匹配 Pod 的 `labels: app: frontend`

**第三步：部署并验证**

```bash
kubectl apply -f 01-configmap-frontend.yaml
kubectl apply -f 02-deploy-frontend.yaml
kubectl get pods -n ecommerce
```

```bash
# 验证：通过 port-forward 访问
kubectl port-forward -n ecommerce svc/frontend-service 8081:80
# 另开终端窗口：
```

```bash
# Linux / macOS
curl http://localhost:8081
```

```powershell
# Windows PowerShell
curl.exe http://localhost:8081
```

应看到 "Welcome to Frontend Service"

### 6.4 部署 api 和 backend 服务

结构与 frontend 相同，仅 ConfigMap 内容不同。api 和 backend 返回 JSON 格式数据，模拟真实的 API 服务。

**api 服务 ConfigMap**：

```yaml
# 03-configmap-api.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: api-config
  namespace: ecommerce
data:
  # 返回 JSON 格式，模拟 API 响应
  index.html: |
    {"service": "api", "status": "ok", "message": "API Service Response"}
```

**backend 服务 ConfigMap**：

```yaml
# 04-configmap-backend.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: backend-config
  namespace: ecommerce
data:
  # 返回 JSON 格式，模拟后端响应
  index.html: |
    {"service": "backend", "status": "ok", "message": "Backend Service Response"}
```

**Deployment + Service**（结构相同，改名字和 ConfigMap 引用）：

**理解要点**：

- 每个服务的 Deployment + Service 结构与 frontend 完全相同
- 区别仅在于：
  - `metadata.name`：服务名称（api / backend）
  - `labels`：标签（app: api / app: backend）
  - `configMap.name`：引用的 ConfigMap（api-config / backend-config）
- 这种"结构相同、参数不同"的模式是 k8s 部署多服务的典型方式

```yaml
# 05-deploy-api.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api
  namespace: ecommerce
spec:
  replicas: 2
  selector:
    matchLabels:
      app: api
  template:
    metadata:
      labels:
        app: api
    spec:
      containers:
        - name: nginx
          image: nginx:1.27
          ports:
            - containerPort: 80
          volumeMounts:
            - name: html-content
              mountPath: /usr/share/nginx/html
      volumes:
        - name: html-content
          configMap:
            name: api-config # ← 引用 api-config
---
apiVersion: v1
kind: Service
metadata:
  name: api-service
  namespace: ecommerce
spec:
  selector:
    app: api
  ports:
    - port: 80
      targetPort: 80
```

```yaml
# 06-deploy-backend.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: backend
  namespace: ecommerce
spec:
  replicas: 2
  selector:
    matchLabels:
      app: backend
  template:
    metadata:
      labels:
        app: backend
    spec:
      containers:
        - name: nginx
          image: nginx:1.27
          ports:
            - containerPort: 80
          volumeMounts:
            - name: html-content
              mountPath: /usr/share/nginx/html
      volumes:
        - name: html-content
          configMap:
            name: backend-config
---
apiVersion: v1
kind: Service
metadata:
  name: backend-service
  namespace: ecommerce
spec:
  selector:
    app: backend
  ports:
    - port: 80
      targetPort: 80
```

**疑问：三个服务都用 80 端口，会冲突吗？**

**不会！** 每个 Pod 都是独立的"小虚拟机"，有自己的网络命名空间。不同 Pod 可以监听相同端口，就像三台独立的电脑都可以监听 80 端口一样。

Service 通过 `selector` 匹配**特定标签**的 Pod，不会混淆：

- `frontend-service` (selector: app=frontend) → 只转发到 frontend Pod
- `api-service` (selector: app=api) → 只转发到 api Pod
- `backend-service` (selector: app=backend) → 只转发到 backend Pod

**端口冲突**只会在**同一个 Pod 内**发生（一个端口只能被一个进程监听）。

```bash
kubectl apply -f 03-configmap-api.yaml -f 05-deploy-api.yaml
kubectl apply -f 04-configmap-backend.yaml -f 06-deploy-backend.yaml
kubectl get pods -n ecommerce
# 应看到 6 个 Pod（frontend×2 + api×2 + backend×2）
```

### 6.5 配置 Ingress 统一入口

Ingress 是集群的"大门"，负责将外部请求路由到不同的 Service。

**Ingress 必须依赖 Service**

Ingress 的路由目标是 Service，不是 Pod。完整的访问链路：

```
外部请求 → Ingress（路由规则）→ Service（稳定地址）→ Pod（处理请求）
```

Service 的作用详见 §4.0。因此，创建顺序必须是：

1. Deployment（创建 Pod）
2. Service（暴露 Pod，提供稳定地址）
3. Ingress（引用 Service，暴露到集群外部）

```yaml
# 07-ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ecommerce
  namespace: ecommerce
spec:
  rules:
    # 规则 1：frontend.demo.local 路由到 frontend
    - host: frontend.demo.local
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: frontend-service
                port:
                  number: 80

    # 规则 2：api.demo.local 路由到 api
    - host: api.demo.local
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: api-service
                port:
                  number: 80

    # 规则 3：backend.demo.local 路由到 backend
    - host: backend.demo.local
      http:
        paths:
          - path: /
            pathType: Prefix
            backend:
              service:
                name: backend-service
                port:
                  number: 80
```

**路由规则说明**：

- `http://frontend.demo.local/` → frontend-service → 返回 HTML 页面
- `http://api.demo.local/` → api-service → 返回 JSON
- `http://backend.demo.local/` → backend-service → 返回 JSON
- 使用子域名比路径更清晰，符合实际微服务架构

**注意**：如果你在 §4.3 已经创建过 Ingress（host: demo.local），需要先删除，否则会冲突：

```bash
# 删除 §4.3 的 Ingress
kubectl delete ingress ecommerce -n default
```

```bash
kubectl apply -f 07-ingress.yaml
kubectl get ingress -n ecommerce
```

### 6.6 完整验证流程

**第一步：确认所有 Pod 运行正常**

```bash
kubectl get pods -n ecommerce
# NAME                        READY   STATUS    RESTARTS   AGE
# frontend-xxx-xxx            1/1     Running   0          2m
# frontend-xxx-yyy            1/1     Running   0          2m
# api-xxx-xxx                 1/1     Running   0          1m
# api-xxx-yyy                 1/1     Running   0          1m
# backend-xxx-xxx             1/1     Running   0          1m
# backend-xxx-yyy             1/1     Running   0          1m
```

**第二步：验证 Ingress 路由**

确保已配置 hosts 文件（Windows: `C:\Windows\System32\drivers\etc\hosts`）：

```
127.0.0.1 frontend.demo.local
127.0.0.1 api.demo.local
127.0.0.1 backend.demo.local
```

```bash
# Linux / macOS
# 访问 frontend
curl http://frontend.demo.local/
# <h1>Welcome to Frontend Service</h1>

# 访问 api
curl http://api.demo.local/
# {"service": "api", "status": "ok", "message": "API Service Response"}

# 访问 backend
curl http://backend.demo.local/
# {"service": "backend", "status": "ok", "message": "Backend Service Response"}
```

```powershell
# Windows PowerShell
# 访问 frontend
curl.exe http://frontend.demo.local/
# <h1>Welcome to Frontend Service</h1>

# 访问 api
curl.exe http://api.demo.local/
# {"service": "api", "status": "ok", "message": "API Service Response"}

# 访问 backend
curl.exe http://backend.demo.local/
# {"service": "backend", "status": "ok", "message": "Backend Service Response"}
```

**第三步：验证 Service DNS 发现**

```bash
# 进入 frontend Pod，验证能否通过 DNS 访问 api-service
kubectl exec -n ecommerce deploy/frontend -- curl http://api-service
# {"service": "api", "status": "ok", "message": "API Service Response"}

# 验证 backend-service
kubectl exec -n ecommerce deploy/frontend -- curl http://backend-service
# {"service": "backend", "status": "ok", "message": "Backend Service Response"}
```

这就是 k8s 的**服务发现**：Pod 之间通过 Service 名称（DNS）互相访问，无需知道 IP。

**第四步：体验自愈**

```bash
# 删除一个 frontend Pod
kubectl delete pod -n ecommerce -l app=frontend

# 观察 Pod 自动重建
kubectl get pods -n ecommerce -w
# 新 Pod 立刻补上，curl 仍正常返回
```

**第五步：体验配置热更新**

```bash
# 修改 frontend 的 ConfigMap
kubectl edit configmap frontend-config -n ecommerce
# 将 "Welcome to Frontend Service" 改为 "Welcome to Frontend v2"

# 等待约 30 秒（kubelet 同步周期），刷新页面
curl.exe http://demo.local/
# 看到新内容 "Welcome to Frontend v2"
```

volume 挂载的 ConfigMap 会自动同步，无需重启 Pod。这就是 §5.3 的内容。

### 6.7 汇总清单

把 §6.2~§6.5 的清单合并为一个多文档 YAML，一条命令部署：

```bash
# 合并所有清单
cat 00-namespace.yaml \
    01-configmap-frontend.yaml \
    02-deploy-frontend.yaml \
    03-configmap-api.yaml \
    05-deploy-api.yaml \
    04-configmap-backend.yaml \
    06-deploy-backend.yaml \
    07-ingress.yaml > ecommerce-all.yaml

# 一键部署
kubectl apply -f ecommerce-all.yaml

# 一键清理
kubectl delete -f ecommerce-all.yaml
```

实际项目中，建议把清单分文件管理，便于版本控制和团队协作。

---

## 8. 延伸阅读

重要但非主线的内容导航。学有余力或工作中遇到时再深入。

### 8.1 Helm：包管理

清单一多就会撞墙：20 个服务的 YAML 怎么版本化？环境差异（dev 用 1 副本、prod 用 10 副本）怎么表达？**Helm** 是 k8s 的包管理器：把清单打包成 Chart（模板 + 默认值），一条命令按环境参数部署：

```bash
helm install ecommerce ./ecommerce-chart -f values-prod.yaml
helm upgrade ecommerce ./ecommerce-chart --set replicas=5
helm rollback ecommerce 2
```

学习路径：先把手写 YAML 练熟（本指南主线），再学 Helm 模板化——§6 的清单就是"待模板化"素材。

### 8.2 HPA：弹性伸缩

§3.4 的 replicas 是手动旋钮，**HPA（Horizontal Pod Autoscaler）**是自动旋钮：按 CPU/内存/自定义指标自动调整副本数：

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: frontend
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: frontend
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 60 # 平均 CPU 超 60% 就扩容
```

前置条件：metrics-server 组件、所有容器写了 resources.requests（§3.4）。

### 8.3 关联阅读

**学习路线建议**：

1. **快速体验**（1 小时）：kind 建集群（§1.1）→ 部署 nginx Pod（§2.2）→ 升级成 Deployment 并杀 Pod 看自愈（§3.2）。
2. **系统学习**（半天）：按 §1 → §5 顺序读核心对象，每章动一次手；然后完整执行 §6 多服务部署。
3. **生产准备**（持续）：Helm 化清单（§8.1）、HPA 弹性（§8.2）、日志与监控接入。

- [Kubernetes 官方文档（中文）](https://kubernetes.io/zh-cn/docs/home/) — 概念与字段的权威来源
- [kind 官方文档](https://kind.sigs.k8s.io/) — 本地集群与节点镜像版本列表
