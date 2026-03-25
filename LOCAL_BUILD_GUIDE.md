# 📱 本地编译指南 - DM-HEILER_260320

**创建时间：** 2026-03-24 11:37  
**适用场景：** 服务器内存不足时的本地编译方案

---

## 🔧 环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| **Android Studio** | Hedgehog 2023.1.1+ | 或最新版 |
| **JDK** | 17+ | Android Studio 内置 |
| **Gradle** | 8.9 | 项目自动下载 |
| **Android SDK** | API 34 | 在 SDK Manager 安装 |
| **系统内存** | 4GB+ | 推荐 8GB |

---

## 📥 步骤 1：克隆项目

```bash
# 方式 A：HTTPS
git clone https://github.com/dahuangbuchifou/DM-HEILER_260320.git
cd DM-HEILER_260320

# 方式 B：SSH（如已配置）
git clone git@github.com:dahuangbuchifou/DM-HEILER_260320.git
cd DM-HEILER_260320
```

---

## 📥 步骤 2：恢复完整配置

**注意：** 线上版本临时禁用了 kapt 和 Room，本地编译需要恢复：

### 2.1 恢复 `app/build.gradle`

```gradle
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")  // ✅ 恢复 kapt
    id("kotlin-parcelize")
}
```

```gradle
dependencies {
    // ... 其他依赖 ...
    
    // Room Database ✅ 恢复
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
}
```

### 2.2 恢复 `gradle.properties`

```properties
# JVM 参数（本地可以用更大内存）
org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -Dfile.encoding=UTF-8

# 并行构建（本地可以开启）
org.gradle.parallel=true
org.gradle.workers.max=4

# kapt 配置
kapt.incremental.apt=true
kapt.use.worker.api=true
```

---

## 🔨 步骤 3：编译项目

### 方式 A：Android Studio（推荐）

1. **打开项目**
   - File → Open → 选择 `DM-HEILER_260320` 文件夹
   - 等待 Gradle 同步完成

2. **编译 Debug 版本**
   - Build → Make Project (Ctrl+F9)
   - 或点击 🔨 Build 图标

3. **编译 Release 版本**
   - Build → Generate Signed Bundle / APK
   - 选择 APK → Debug 或 Release

### 方式 B：命令行

```bash
# Debug 版本
./gradlew assembleDebug

# Release 版本（需要先配置签名）
./gradlew assembleRelease

# 查看编译结果
ls -lh app/build/outputs/apk/debug/
```

---

## 📦 步骤 4：安装到设备

### 方式 A：Android Studio

1. 连接 Android 设备或启动模拟器
2. 点击 ▶ Run (Shift+F10)
3. 选择目标设备

### 方式 B：ADB 命令行

```bash
# 查看连接的设备
adb devices

# 安装 APK
adb install app/build/outputs/apk/debug/app-debug.apk

# 如果已安装，覆盖安装
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🔍 常见问题

### 问题 1：Gradle 同步失败

**错误：** `Could not resolve all dependencies`

**解决：**
```bash
# 清理 Gradle 缓存
rm -rf ~/.gradle/caches

# 删除 .gradle 文件夹
rm -rf .gradle

# 重新同步
./gradlew --refresh-dependencies
```

### 问题 2：SDK 未安装

**错误：** `SDK platform not installed`

**解决：**
1. 打开 Android Studio
2. Tools → SDK Manager
3. 安装 Android 14 (API 34)

### 问题 3：内存不足

**错误：** `OutOfMemoryError` 或编译卡住

**解决：**
```properties
# 在 gradle.properties 中增加内存
org.gradle.jvmargs=-Xmx3072m
```

---

## 📊 编译产物

| 文件 | 路径 | 大小（约） |
|------|------|----------|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` | 15-25 MB |
| Release APK | `app/build/outputs/apk/release/app-release-unsigned.apk` | 10-15 MB |
| 编译日志 | `app/build/outputs/logs/manifest-merger-debug-report.txt` | - |

---

## 🚀 编译后操作

### 1. 测试安装

```bash
# 安装到设备
adb install app/build/outputs/apk/debug/app-debug.apk

# 启动应用
adb shell am start -n com.damaihelper/.MainActivity
```

### 2. 查看日志

```bash
# 实时日志
adb logcat | grep -i "damai\|MainActivity"

# 保存日志
adb logcat -d > crash-log.txt
```

### 3. 推送到服务器（可选）

```bash
# 将 APK 上传到服务器
scp app/build/outputs/apk/debug/app-debug.apk admin@your-server:/tmp/

# 或在 GitHub 创建 Release
git tag v1.0-local-build
git push origin v1.0-local-build
```

---

## 📝 与线上版本的差异

| 配置项 | 线上版本 | 本地版本 |
|--------|----------|----------|
| **JVM 内存** | 768MB | 2048MB+ |
| **并行构建** | 关闭 | 开启 |
| **kapt** | 禁用 | 启用 |
| **Room** | 注释 | 启用 |
| **工作线程** | 1 | 4 |

---

## ✅ 编译成功检查清单

- [ ] Gradle 同步完成（无红色错误）
- [ ] Build → Make Project 成功
- [ ] APK 文件生成在 `app/build/outputs/apk/debug/`
- [ ] 可以安装到设备/模拟器
- [ ] 应用可以正常启动

---

## 📞 需要帮助？

**编译问题：** 查看 `app/build/outputs/logs/` 中的日志文件

**运行问题：** 使用 `adb logcat` 查看应用日志

**代码问题：** 检查最新的 git commit 和架构文档

---

**最后更新：** 2026-03-24 11:37  
**状态：** 等待本地编译验证
