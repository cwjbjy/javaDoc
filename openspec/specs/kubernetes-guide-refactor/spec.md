# Spec: kubernetes-guide-refactor

## ADDED Requirements

### Requirement: 极简命令式语言风格

指南 SHALL 采用极简命令式语言风格，删除冗余比喻和口诀，概念介绍点到为止。

#### Scenario: 删除比喻性描述

- **WHEN** 原文包含比喻（如"驻厂监工"、"遥控器"、"记忆口诀"）
- **THEN** 替换为直接的技术描述（如"节点代理"、"命令行工具"、"组件列表"）

#### Scenario: 精简概念介绍

- **WHEN** 原文用超过 5 行解释一个概念
- **THEN** 压缩为 1-3 行核心定义 + 可选示例

### Requirement: Kubernetes Dashboard 集成

指南 SHALL 包含 Kubernetes Dashboard v2.7.0 的完整部署流程，作为 §1.5 章节。

#### Scenario: Dashboard 部署步骤完整

- **WHEN** 读者按照 §1.5 步骤操作
- **THEN** 能够成功部署 Dashboard、创建管理员、获取 Token、启动代理、浏览器访问

#### Scenario: Token 过期问题处理

- **WHEN** 读者遇到 Token 24 小时过期问题
- **THEN** 指南提供 ServiceAccount Token Secret 永久方案

#### Scenario: Dashboard 章节位置

- **WHEN** 读者完成 §1.4 kind 集群创建
- **THEN** 下一章节是 §1.5 Dashboard 安装，然后是 §1.6 kubectl 基础命令

### Requirement: 实战优先的章节结构

指南 SHALL 保持"问题 → 概念 → 实战 → 生产实践 → 回顾"结构，但每章以可执行命令和 YAML 为核心。

#### Scenario: 保留所有 YAML 示例

- **WHEN** 原文包含 Pod、Deployment、Service 等 YAML 示例
- **THEN** 精简后全部保留，不删除任何技术内容

#### Scenario: 保留所有 kubectl 命令

- **WHEN** 原文包含 kubectl 命令示例
- **THEN** 精简后全部保留，删除的是解释性文字而非命令本身

#### Scenario: 生产实践小节压缩

- **WHEN** 原文生产实践小节超过 10 行
- **THEN** 压缩为 3-5 行要点列表

### Requirement: 章节编号调整

指南 SHALL 调整 §1 章节编号，为 Dashboard 插入腾出位置。

#### Scenario: 新章节编号映射

- **WHEN** 原文 §1.5 是"第一个 kubectl 命令"
- **THEN** 精简后 §1.5 是"安装 Kubernetes Dashboard"，§1.6 是"kubectl 基础命令"，§1.7 是"本章回顾"

#### Scenario: 交叉引用更新

- **WHEN** 原文引用 §1.5 或 §1.6
- **THEN** 更新为新的章节编号

### Requirement: 指南规范验证

精简后的指南 SHALL 通过 `validate_guide.py` 结构校验。

#### Scenario: 运行验证脚本

- **WHEN** 执行 `python .agents/skills/guide-writing/scripts/validate_guide.py docs/Kubernetes/kubernetes-guide.md`
- **THEN** 输出无错误，指南符合仓库规范
