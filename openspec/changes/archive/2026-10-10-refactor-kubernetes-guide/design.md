# Design: refactor-kubernetes-guide

## Context

当前 `docs/Kubernetes/kubernetes-guide.md` 是 2175 行的完整指南，覆盖从 Docker Compose 到 Kubernetes 的迁移实战。内容结构清晰（Pod → Deployment → Service → Ingress → ConfigMap → PV → 电商三服务迁移），但语言偏理论化，大量比喻和口诀增加了阅读负担。

读者画像：有 Docker Compose 经验的后端开发者，希望快速上手 k8s，关注"怎么做"而非"为什么这么设计"。

## Goals / Non-Goals

**Goals:**

- 全文语言精简化：删除冗余比喻，概念介绍点到为止
- 新增 Kubernetes Dashboard（v2.7.0）完整部署流程
- 实战优先：每章保留核心命令和 YAML，读者能一步步搭建
- 保持仓库指南规范（validate_guide.py 通过）

**Non-Goals:**

- 不删除任何技术内容（YAML、命令、架构图全部保留）
- 不改变章节主题顺序（Pod → Deployment → Service 等主线不变）
- 不引入新工具（Lens、k9s 等替代方案不在本次范围）
- 不配置 HTTPS（kind 环境用 proxy 即可）

## Decisions

### 1. Dashboard 版本选择：v2.7.0

**决策**：锁定 Kubernetes Dashboard v2.7.0

**理由**：

- v2.7.0 是稳定版，文档丰富，社区支持好
- v3.x 有较大 API 变化，学习曲线更陡
- kind 本地集群不需要最新特性

**替代方案**：

- Lens（桌面应用）：零配置但非 Web UI，不符合"可视化面板"需求
- k9s（终端 TUI）：轻量但不够直观

### 2. Dashboard 章节位置：§1.5（kind 之后，kubectl 之前）

**决策**：Dashboard 紧跟 kind 集群创建之后

**理由**：

- 先让读者"看见"集群，再学命令操作
- 降低纯命令行学习门槛
- Dashboard 部署本身需要 kubectl，但只需 `apply` 一条命令

**替代方案**：

- 放在 kubectl 之后（§1.6）：逻辑上先学命令再可视化，但读者可能已经失去耐心

### 3. Token 过期处理：Secret 永久方案

**决策**：使用 ServiceAccount Token Secret，一次配置永久有效

**理由**：

- v2.7.0 默认 Token 24 小时过期，学习体验差
- Secret 方案是官方推荐的学习环境配置
- `--duration=8760h` 方案需要每次重新生成，不如 Secret 方便

**实现**：

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

### 4. 语言精简策略：保留结构，删除修饰

**决策**：每章保留"问题 → 概念 → 实战 → 生产实践 → 回顾"结构，但删除比喻和口诀

**理由**：

- 结构本身有价值（问题驱动的学习路径）
- 比喻（"驻厂监工"、"遥控器"）对初学者有帮助，但熟练后是噪音
- 口诀（"etcd 是记忆，API Server 是嘴"）占篇幅且不易检索

**精简示例**：

```
原：记忆口诀：etcd 是记忆，API Server 是嘴，Scheduler 是腿，Controller 是手，kubelet 是驻厂监工。
新：控制平面组件：etcd（存储）、API Server（入口）、Scheduler（调度）、Controller（控制）、kubelet（节点代理）。
```

## Risks / Trade-offs

| 风险                                | 缓解措施                                           |
| ----------------------------------- | -------------------------------------------------- |
| 精简过度导致概念不清                | 保留架构图和对照表，确保可视化辅助                 |
| Dashboard 部署失败（网络/版本问题） | 提供完整 YAML 清单，不依赖外部 URL                 |
| Token Secret 方案在生产环境不安全   | 明确标注"仅用于学习集群"，生产环境建议 RBAC + OIDC |
| 章节顺序调整影响后续引用            | 全局搜索 `§1.5`、`§1.6` 等引用，统一更新           |

## Migration Plan

1. **备份原文件**：`kubernetes-guide.md` → `kubernetes-guide.md.bak`
2. **逐章精简**：按 §1 → §2 → ... 顺序，每章独立提交
3. **插入 Dashboard 章节**：在 §1.4 之后插入新 §1.5
4. **更新目录和引用**：调整 TOC、章节编号、交叉引用
5. **运行验证**：`python .agents/skills/guide-writing/scripts/validate_guide.py docs/Kubernetes/kubernetes-guide.md`
6. **删除备份**：确认无误后删除 `.bak` 文件

## Open Questions

- [ ] 精简后的目标行数？（用户表示"不一定"，但建议控制在 1500 行以内）
- [ ] 是否保留"前端视角"等特定读者群体的注释？
