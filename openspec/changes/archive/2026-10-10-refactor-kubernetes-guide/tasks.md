# Tasks: refactor-kubernetes-guide

## 1. 准备工作

- [x] 1.1 备份原文件 `docs/Kubernetes/kubernetes-guide.md` → `kubernetes-guide.md.bak`
- [x] 1.2 通读全文，标记所有比喻、口诀、冗余解释的位置

## 2. §1 全景图章节精简

- [x] 2.1 精简 §1.1 问题：单机部署有什么短板（30 行 → 5 行要点）
- [x] 2.2 精简 §1.2 一张图看懂 k8s 集群（删除记忆口诀，保留架构图）
- [x] 2.3 精简 §1.3 Compose 与 k8s 对照总表（删除多余解释列）
- [x] 2.4 精简 §1.4 kind 三步搭建本地集群（保留命令，删除冗余说明）

## 3. 新增 §1.5 Kubernetes Dashboard

- [x] 3.1 编写 Dashboard 部署步骤（kubectl apply -f v2.7.0 YAML）
- [x] 3.2 编写创建管理员 ServiceAccount + ClusterRoleBinding
- [x] 3.3 编写 Token Secret 永久方案（解决 24 小时过期问题）
- [x] 3.4 编写启动代理 + 浏览器访问步骤
- [x] 3.5 添加"仅用于学习集群"的安全提示

## 4. 调整 §1 章节编号

- [x] 4.1 原 §1.5 kubectl 命令 → 新 §1.6
- [x] 4.2 原 §1.6 本章回顾 → 新 §1.7
- [x] 4.3 更新目录（TOC）中的章节编号
- [x] 4.4 全局搜索并更新交叉引用（§1.5、§1.6 等）

## 5. §2 Pod 章节精简

- [x] 5.1 精简 §2.0 问题：容器能不能直接部署
- [x] 5.2 精简 §2.1 Pod 是什么（删除比喻，保留核心定义）
- [x] 5.3 保留 §2.2 第一个 YAML：运行 nginx Pod（全部 YAML 和命令）
- [x] 5.4 精简 §2.3 kubectl 常用命令（保留命令，删除冗余解释）
- [x] 5.5 压缩 §2.4 生产实践：Pod 是牛不是宠物（10 行 → 3-5 行要点）
- [x] 5.6 精简 §2.5 本章回顾

## 6. §3 Deployment 章节精简

- [x] 6.1 精简 §3.0 问题：Pod 挂了谁管
- [x] 6.2 精简 §3.1 声明式：期望状态驱动
- [x] 6.3 保留 §3.2 第一个 Deployment（全部 YAML 和命令）
- [x] 6.4 保留 §3.3 滚动更新与回滚（全部命令）
- [x] 6.5 压缩 §3.4 生产实践：副本数与资源配额
- [x] 6.6 精简 §3.5 本章回顾

## 7. §4 Service 与 Ingress 章节精简

- [x] 7.1 精简 §4.0 问题：Pod IP 是临时的
- [x] 7.2 精简 §4.1 Service 的三种类型（保留对照表）
- [x] 7.3 精简 §4.2 DNS：服务名即地址
- [x] 7.4 保留 §4.3 第一个 Service：ClusterIP（全部 YAML）
- [x] 7.5 保留 §4.4 Ingress：统一入口（全部 YAML）
- [x] 7.6 保留 §4.5 学习集群适配：kind 下如何访问
- [x] 7.7 精简 §4.6 本章回顾

## 8. §5-§12 后续章节精简

- [x] 8.1 精简 §5 ConfigMap 与 Secret
- [x] 8.2 精简 §6 存储：PV 与 PVC
- [x] 8.3 精简 §7 电商三服务迁移实战
- [x] 8.4 精简 §8 Nacos vs k8s Service 决策章
- [x] 8.5 精简 §9 健康检查与探针
- [x] 8.6 精简 §10 资源配额与 HPA
- [x] 8.7 精简 §11 日志与监控
- [x] 8.8 精简 §12 生产实践清单

## 9. 验证与收尾

- [x] 9.1 运行 `python .agents/skills/guide-writing/scripts/validate_guide.py docs/Kubernetes/kubernetes-guide.md`
- [x] 9.2 检查所有 YAML 示例格式正确
- [x] 9.3 检查所有 kubectl 命令可执行
- [x] 9.4 确认章节编号和交叉引用一致
- [x] 9.5 删除备份文件 `kubernetes-guide.md.bak`
