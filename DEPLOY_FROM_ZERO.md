# 从零部署：一步一步生成 APK

## 第 0 步：不要使用旧仓库

建议新建一个完全空的 GitHub 仓库，例如：

```text
liaoban-v2
```

这样不会混入旧的 workflow、旧源码或旧提交。

---

## 第 1 步：下载并解压本项目

解压后，你会看到：

```text
.github
app
build.gradle.kts
gradle.properties
settings.gradle.kts
README.md
DEPLOY_FROM_ZERO.md
```

---

## 第 2 步：上传到 GitHub 仓库根目录

最重要：

GitHub 仓库首页必须直接看到：

```text
.github
app
build.gradle.kts
gradle.properties
settings.gradle.kts
```

不能变成：

```text
liaoban-v2
  └─ LiaoBanAI_v2_RunAPI
       ├─ app
       ├─ build.gradle.kts
       └─ ...
```

也就是说，不能多套一层目录。

---

## 第 3 步：确认 workflow 存在

在 GitHub 打开：

```text
.github/workflows/build-apk.yml
```

确认里面写的是：

```text
android-actions/setup-android@v4
```

不是 v3。

---

## 第 4 步：运行 Actions

打开：

```text
Actions
```

左侧选择：

```text
Build Android APK
```

点击：

```text
Run workflow
```

然后再次点击绿色：

```text
Run workflow
```

---

## 第 5 步：正常编译流程

应该依次看到：

```text
✓ Checkout source
✓ Set up Java 17
✓ Set up Android SDK
✓ Install Android 35
✓ Set up Gradle 8.9
✓ Check Java and Gradle
✓ Build APK
✓ Upload APK
```

---

## 第 6 步：下载 APK

成功后打开该次运行。

页面底部：

```text
Artifacts
```

点击：

```text
LiaoBanAI-v2-APK
```

下载 ZIP。

解压后得到：

```text
app-debug.apk
```

这个文件传到 Android 手机上安装即可。

---

## 如果失败

不要重新猜着改。

点击第一个红色 × 的步骤。

如果是 Build APK：

在日志中搜索：

```text
What went wrong
```

把第一次出现的真正错误，以及它上下约 15 行发给我。

不要只截最后：

```text
Process completed with exit code 1
```

因为那只是结果，不是原因。
