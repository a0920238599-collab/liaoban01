# 聊伴 AI v0.2 — RunAPI 版

这是一套重新整理过的 Android 工程，不建议与 v0.1 混用。

## v2 为什么重做

v0.1 的 GitHub Actions 已经成功进入真正的 APK 编译阶段，但旧工程出现：

```text
Inconsistent JVM-target compatibility
compileDebugJavaWithJavac (1.8)
kaptGenerateStubsDebugKotlin (17)
```

v2 直接做了两项结构性修复：

1. Java / Kotlin / Gradle 统一到 JDK 17。
2. 完全移除 Room + KAPT，改用 Android 自带 SQLiteOpenHelper，避免 KAPT 目标版本问题。

## v2 的 API 架构

默认：

```text
Android App
  ↓
RunAPI
  ↓
GPT / DeepSeek / Grok / 其他模型
```

RunAPI Key 不写进 APK。

安装后在：

**AI设置**

填写：

1. RunAPI 完整 Chat Completions HTTPS 接口地址
2. RunAPI API Key
3. 记忆整理模型 ID
4. 主动聊天判断模型 ID

为什么“接口地址”不在代码里写死？

因为中转平台可能调整 Base URL / 路由路径。v2 让你直接粘贴 RunAPI 后台或文档给出的完整 Chat Completions 地址，这样以后改地址不需要重新编译 APK。

## 默认联系人

### 小夏
默认模型：

```text
gpt-5.6-sol
```

### 阿深
默认模型：

```text
deepseek-v4-pro
```

### G
Grok 的实际模型 ID 没有在代码里胡乱猜。

安装后：

**联系人 → G → 设置 → 模型 ID**

把 RunAPI 当前模型列表里的 Grok 实际 ID 复制进去即可。

## 记忆结构

每轮聊天不会发送全部历史。

普通聊天只组织：

```text
固定人格
+ 固定表达风格
+ 关系阶段
+ 近期摘要
+ 最相关的最多 7 条长期记忆
+ 最近 12 条消息
+ 当前消息
```

默认每 6 条用户消息做一次后台记忆整理。

## 主动聊天

不是让模型一直在线。

手机本地每小时有机会检查一次，但只有：

- 重要事件临近；
- 超过 24 小时没聊并落在稀疏社交窗口；

才会真正调用一次主动聊天判断模型。

默认 23:00~08:00 不主动打扰。

## 从零部署 GitHub Actions

请看：

`DEPLOY_FROM_ZERO.md`

不要在旧仓库继续修。

## 安装后第一件事

打开：

**AI设置**

先保存 RunAPI 接口地址与 Key。

然后再聊天。

如果某联系人提示“模型 ID 未填写”，进入联系人设置，把 RunAPI 当前模型 ID 粘贴进去即可。
