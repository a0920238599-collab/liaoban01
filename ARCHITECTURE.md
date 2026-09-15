# v2 架构说明

## 核心原则

模型不是联系人。

联系人由本地数据构成：

- 核心人格
- 表达习惯
- 长期记忆
- 近期摘要
- 关系阶段

RunAPI 后面的 GPT / DeepSeek / Grok 只是生成引擎。

## 为什么去掉 Room/KAPT

v0.1 使用 Room + KAPT。

在 GitHub Actions 中出现 JVM target：

```text
Java = 1.8
KAPT/Kotlin = 17
```

v2 为了减少构建链复杂度，直接使用 Android SQLiteOpenHelper。

这样仍然拥有持久化数据库，但不需要 annotation processing。

## 成本控制

主聊天：
- 使用联系人自己的模型

记忆整理：
- 使用“AI设置”里的 memoryModel
- 每 6 条用户消息执行一次

主动判断：
- 使用 proactiveModel
- 本地规则先筛选，只有候选事件才调用模型

## 隐私

本机保存：

- 全量聊天记录
- 联系人核心人格
- 长期记忆
- 近期摘要

真正发给 RunAPI 的只是本轮必要上下文。

API Key 使用 Android Keystore 保护后存储。
