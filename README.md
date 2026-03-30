# 🎫 DM-HEILER - 大麦抢票助手

_基于图像识别的自动化抢票工具_

**当前版本：** v1.3.0  
**最后更新：** 2026-03-30 09:00  
**状态：** 🔄 开发中

---

##  快速开始

### 1. 拉取代码
```bash
git clone https://github.com/dahuangbuchifou/DM-HEILER_260320.git
cd DM-HEILER_260320
```

### 2. 编译项目
```bash
./gradlew assembleDebug
```

### 3. 安装到设备
```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## 📋 核心功能

### v1.3.0 新功能（最新）
- ✅ **完整信息抓取** - 标题、时间（多场次）、地点、票价、库存
- ✅ **自动交互流程** - 检测信息不完整自动点击按钮
- ✅ **付款界面检测** - 到达付款界面自动停止
- ✅ **详细日志报告** - 输出完整演唱会信息报告

### 历史功能
- 图像识别抢票（v1.1.0）
- 预约 + 秒杀模式（v1.2.0）
- 多票档备选（v1.2.1）

---

## 📖 文档导航

### 核心文档
| 文档 | 说明 |
|------|------|
| [CHANGELOG.md](CHANGELOG.md) | 版本变更日志 |
| [CHECKLIST.md](CHECKLIST.md) | 开发规范、时间同步 |
| [FIX_LOG.md](FIX_LOG.md) | 代码修复记录 |
| [docs/开发日志.md](docs/开发日志.md) | 每日开发记录 |
| [docs/使用指南.md](docs/使用指南.md) | 用户使用指南 |

### 技术文档
- [ARCHITECTURE.md](ARCHITECTURE.md) - 架构设计
- [docs/技术文档.md](docs/技术文档.md) - 技术实现细节

---

## ⚠️ 重要提示

### 支付安全
- ⚠️ **到达付款界面后必须手动完成支付**
- ⚠️ 自动操作在付款界面自动停止
- ⚠️ 不存储任何支付信息

### 使用要求
- Android 10+（支持 MediaProjection API）
- 分屏模式（助手 + 大麦）
- 截屏权限 + 无障碍服务权限

---

## 🛠️ 技术栈

- **语言：** Kotlin
- **Android Gradle Plugin:** 8.7.0
- **Kotlin:** 1.9.25
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 34 (Android 14)

### 核心库
- Room 数据库
- Coroutines + Flow
- Google ML Kit OCR
- MediaProjection API
- AccessibilityService

---

## 📊 版本历史

| 版本 | 日期 | 主题 |
|------|------|------|
| v1.3.0 | 2026-03-30 | 信息抓取增强版 |
| v1.2.1 | 2026-03-29 | 数据库迁移修复 |
| v1.2.0 | 2026-03-28 | 预约 + 秒杀模式 |
| v1.1.0 | 2026-03-27 | 图像识别抢票 |
| v1.0.0 | 2026-03-25 | 初始版本 |

详细版本记录：[CHANGELOG.md](CHANGELOG.md)

---

## 🔗 相关链接

- **GitHub:** https://github.com/dahuangbuchifou/DM-HEILER_260320
- **问题反馈:** https://github.com/dahuangbuchifou/DM-HEILER_260320/issues

---

## 📝 开发规范

### 时间同步（重要！）
每次代码调整后，必须同步更新：
1. TicketTask.kt 头部注释
2. MainActivity.kt 界面显示
3. Git Commit 信息

详见：[CHECKLIST.md](CHECKLIST.md)

### 文档更新
- **开发日志** - 每日记录
- **版本日志** - 版本升级更新
- **修复日志** - Bug 修复更新

---

_最后更新：2026-03-30 09:45_
