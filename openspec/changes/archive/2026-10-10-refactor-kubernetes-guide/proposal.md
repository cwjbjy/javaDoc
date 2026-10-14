# Proposal: refactor-kubernetes-guide

## Why

当前 `docs/Kubernetes/kubernetes-guide.md`（2175 行）内容扎实但偏理论化：大量比喻（"驻厂监工"、"遥控器"）、问题驱动的长段解释、概念先行实践在后。读者反馈希望更简洁直接、以实战步骤为主、能一步步搭建起来。同时缺少可视化工具（Dashboard）的介绍，纯命令行学习门槛较高。

## What Changes

- **全文语言精简**：删除冗余比喻和口诀，概念介绍点到为止，每章保留核心命令和 YAML 示例
- **新增 §1.5 Kubernetes Dashboard**：完整部署流程（v2.7.0），包括创建管理员、获取 Token、启动代理、浏览器访问；提及 Token 过期问题并给出 Secret 永久方案
- **调整章节顺序**：Dashboard 紧跟 kind 集群之后（§1.5），kubectl 命令后移（§1.6），先能"看见"再学"操作"
- **保留但精简**：对照表（删多余解释）、生产实践小节（压缩为 3-5 行要点）、ASCII 架构图、延伸阅读链接

## Capabilities

### New Capabilities

- `kubernetes-guide-refactor`: 定义精简后的 Kubernetes 实战指南内容契约——极简命令式语言风格、Dashboard 集成、实战优先的章节结构

### Modified Capabilities

<!-- 无既有 spec 的需求变化 -->

## Impact

- 修改文档：`docs/Kubernetes/kubernetes-guide.md`（全文重构，预计精简 30-40%）
- 不引入代码依赖，Dashboard 部署为 k8s 清单（YAML），服务于本地学习集群
- 运行 `validate_guide.py` 确保精简后仍符合仓库指南规范
