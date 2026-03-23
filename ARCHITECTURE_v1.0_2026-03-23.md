# 大麦 Helper 应用架构文档

**版本：** 1.0  
**最后更新：** 2026-03-23  
**项目路径：** `/home/admin/.openclaw/workspace/DM-HEILER_260320`

---

## 📋 目录

1. [项目概述](#1-项目概述)
2. [技术架构](#2-技术架构)
3. [核心模块](#3-核心模块)
4. [数据流](#4-数据流)
5. [当前特点](#5-当前特点)
6. [优缺点分析](#6-优缺点分析)
7. [优化建议](#7-优化建议)

---

## 1. 项目概述

### 1.1 项目定位

**大麦 Helper** 是一款基于 Android 无障碍服务（AccessibilityService）的自动化抢票辅助应用，旨在帮助用户在大麦网演出开票时自动完成抢票流程。

### 1.2 核心功能

| 功能 | 说明 | 状态 |
|------|------|------|
| 任务管理 | 创建、编辑、删除抢票任务 | ✅ 已实现 |
| 定时抢票 | 在指定时间自动触发抢票 | ⚠️ 部分实现 |
| 无障碍自动化 | 自动点击、填写信息、提交订单 | ✅ 已实现 |
| 智能答题 | 自动回答购票验证问题 | ✅ 已实现 |
| 验证码识别 | OCR 识别图形验证码 | ⚠️ 待完善 |
| 反检测 | 设备指纹伪装、行为模拟 | ✅ 已实现 |
| API 抢票 | 直接调用大麦 API 抢票 | ⚠️ 待完善 |
| 混合模式 | API + UI 自动化结合 | ✅ 已实现 |

### 1.3 目标用户

- 需要抢购热门演出门票的观众
- 多次抢票失败，需要自动化辅助的用户
- 技术爱好者，愿意尝试自动化工具

---

## 2. 技术架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        UI 层 (Presentation)                      │
├─────────────────────────────────────────────────────────────────┤
│  MainActivity.kt  │  TaskConfigActivity.kt  │  TaskAdapter.kt   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        服务层 (Service)                          │
├─────────────────────────────────────────────────────────────────┤
│ TicketGrabbingAccessibilityService  │  TicketGrabbingForegroundService │
│ ConcertInfoExtractor                │  QuestionAnsweringService        │
│ CaptchaRecognitionService           │  CaptchaCoordinatorService       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       策略层 (Strategy)                          │
├─────────────────────────────────────────────────────────────────┤
│ StrategyDecisionEngine  │  APIGrabbingStrategy                  │
│ TicketGrabbingStrategy  │  UIAutomationStrategy                 │
│                         │  HybridGrabbingStrategy               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       工具层 (Utils)                             │
├─────────────────────────────────────────────────────────────────┤
│ AccessibilityUtils       │  HumanBehaviorSimulator              │
│ AntiDetectionModule      │  DeviceFingerprintSpoofing           │
│ PreciseTimeManager       │  TooManyPeopleHandler                │
│ NodeUtils                │  AdvancedBehaviorSimulator           │
│ QuestionBankManager      │                                      │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       数据层 (Data)                              │
├─────────────────────────────────────────────────────────────────┤
│  TaskDatabase (Room)  │  TaskDao  │  TicketTask  │  ConcertInfo │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       网络层 (Network)                           │
├─────────────────────────────────────────────────────────────────┤
│     DamaiApiClient      │      SessionManager                   │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 技术栈

| 类别 | 技术 | 版本 | 说明 |
|------|------|------|------|
| 语言 | Kotlin | 1.9.22 | 主要开发语言 |
| UI 框架 | AndroidX + Material | 1.6.1 / 1.11.0 | 界面组件 |
| 数据库 | Room | 2.6.1 | 本地数据存储 |
| 网络 | OkHttp3 | 4.12.0 | HTTP 客户端 |
| 序列化 | Gson | 2.10.1 | JSON 解析 |
| 协程 | kotlinx-coroutines | 1.7.3 | 异步编程 |
| 依赖注入 | 手动 | - | 未使用 DI 框架 |

### 2.3 项目结构

```
app/src/main/java/com/damaihelper/
├── captcha/                      # 验证码处理
│   ├── CaptchaCoordinatorService.kt
│   └── OcrService.kt
├── config/                       # 配置管理
│   └── CaptchaConfig.kt
├── model/                        # 数据模型
│   ├── ConcertInfo.kt
│   ├── TaskDao.kt
│   ├── TaskDatabase.kt
│   └── TicketTask.kt
├── network/                      # 网络层
│   ├── DamaiApiClient.kt
│   └── SessionManager.kt
├── service/                      # 核心服务
│   ├── CaptchaRecognitionService.kt
│   ├── ConcertInfoExtractor.kt
│   ├── QuestionAnsweringService.kt
│   ├── TicketGrabbingAccessibilityService.kt
│   └── TicketGrabbingForegroundService.kt
├── strategy/                     # 抢票策略
│   ├── APIGrabbingStrategy.kt
│   ├── StrategyDecisionEngine.kt
│   └── TicketGrabbingStrategy.kt
├── ui/                           # 用户界面
│   ├── MainActivity.kt
│   ├── TaskAdapter.kt
│   └── TaskConfigActivity.kt
└── utils/                        # 工具类
    ├── AccessibilityUtils.kt
    ├── AdvancedBehaviorSimulator.kt
    ├── AntiDetectionModule.kt
    ├── DeviceFingerprintSpoofing.kt
    ├── HumanBehaviorSimulator.kt
    ├── NodeUtils.kt
    ├── NodeUtilsExtensions.kt
    ├── PreciseTimeManager.kt
    ├── QuestionBankManager.kt
    └── TooManyPeopleHandler.kt
```

---

## 3. 核心模块

### 3.1 无障碍服务 (TicketGrabbingAccessibilityService)

**职责：** 核心自动化执行引擎

**主要功能：**
- 监听大麦 App 界面变化
- 自动点击"立即购买"、"提交订单"等按钮
- 处理"人太多"弹窗
- 自动填写观演人信息
- 协调验证码识别和答题

**关键方法：**
```kotlin
onAccessibilityEvent(event: AccessibilityEvent?)  // 界面事件监听
executeUIAutomationGrabbing(...)                   // 执行 UI 自动化
extractConcertInfo()                               // 抓取演出信息
handleTooManyPeoplePopup()                         // 处理人太多弹窗
```

### 3.2 策略决策引擎 (StrategyDecisionEngine)

**职责：** 智能选择最优抢票策略

**工作原理：**
1. 注册多个抢票策略（API、UI、混合）
2. 根据历史成功率、响应时间计算评分
3. 动态选择评分最高的策略
4. 失败时自动切换到备选策略

**策略类型：**
| 策略 | 优先级 | 说明 |
|------|--------|------|
| HybridGrabbingStrategy | 90 | API+UI 混合，优先 API |
| UIAutomationStrategy | 50 | 纯 UI 自动化 |
| APIGrabbingStrategy | ? | 纯 API 抢票 |

### 3.3 反检测模块 (AntiDetectionModule)

**职责：** 伪装设备特征，规避大麦风控

**功能层级：**
- **L1:** 设备指纹伪造（Build 信息、IMEI 等）
- **L2:** 网络层伪装（TLS 指纹、HTTP 头）
- **L3:** 行为模式随机化（点击延迟、滑动轨迹）
- **L4:** 环境风险检测（开发者模式、Hook 框架、模拟器）

**关键方法：**
```kotlin
generateRandomHeaders()           // 生成随机 HTTP 头
createStealthHttpClient()         // 创建防检测 HTTP 客户端
performAntiDetectionScan()        // 执行反检测扫描
getDeviceRiskLevel()              // 获取设备风险等级
```

### 3.4 时间管理 (PreciseTimeManager)

**职责：** 提供精确的时间同步

**实现方式：**
- 使用 NTP 服务器校时
- 确保抢票时间精确到毫秒
- 避免本地时钟偏差导致抢票失败

### 3.5 数据库 (Room)

**职责：** 本地存储抢票任务

**表结构：**
```sql
CREATE TABLE ticket_tasks (
    id INTEGER PRIMARY KEY,
    name TEXT,
    concertKeyword TEXT,
    grabDate TEXT,
    grabTime INTEGER,
    ticketPriceKeyword TEXT,
    count INTEGER,
    viewerNames TEXT,
    status TEXT,
    createTime INTEGER,
    quantity INTEGER,
    remark TEXT
)
```

---

## 4. 数据流

### 4.1 任务创建流程

```
用户输入配置 (TaskConfigActivity)
         │
         ▼
创建 TicketTask 对象
         │
         ▼
保存到 TaskDatabase (Room)
         │
         ▼
刷新 MainActivity 列表
```

### 4.2 抢票执行流程

```
到达抢票时间
         │
         ▼
ForegroundService 触发
         │
         ▼
启动 AccessibilityService
         │
         ▼
StrategyDecisionEngine 选择策略
         │
         ▼
执行抢票 (API 或 UI)
         │
         ├──→ 成功 → 通知用户 → 结束
         │
         └──→ 失败 → 切换策略 → 重试
```

### 4.3 无障碍事件处理

```
大麦 App 界面变化
         │
         ▼
onAccessibilityEvent() 触发
         │
         ▼
识别当前页面类型 (DETAIL/SKU/CAPTCHA...)
         │
         ▼
执行对应操作 (点击/填写/提交)
         │
         ▼
检测是否成功 → 更新状态
```

---

## 5. 当前特点

### 5.1 优势

| 特点 | 说明 |
|------|------|
| **多策略支持** | API、UI、混合三种模式，灵活切换 |
| **智能决策** | 根据历史数据动态选择最优策略 |
| **反检测完善** | 多层伪装，降低被封风险 |
| **模块化设计** | 各模块职责清晰，易于维护 |
| **本地存储** | Room 数据库，任务持久化 |
| **答题支持** | 内置题库，自动回答验证问题 |

### 5.2 不足

| 问题 | 影响 | 优先级 |
|------|------|--------|
| **缺少自动跳转** | 需要手动打开大麦 App | 🔴 高 |
| **定时触发不完善** | 无法精确在开抢时刻触发 | 🔴 高 |
| **验证码识别弱** | OCR 服务未完善 | 🟡 中 |
| **API 抢票未完善** | 主要依赖 UI 自动化 | 🟡 中 |
| **缺少倒计时 UI** | 用户无法直观看到剩余时间 | 🟡 中 |
| **无结果通知** | 抢票成功/失败缺少通知反馈 | 🟡 中 |
| **无网络请求日志** | 调试困难 | 🟢 低 |

---

## 6. 优缺点分析

### 6.1 架构优点

✅ **分层清晰**：UI → Service → Strategy → Utils → Data，职责分离

✅ **策略模式**：易于扩展新的抢票策略，符合开闭原则

✅ **协程支持**：异步处理完善，避免 ANR

✅ **安全性考虑**：反检测模块较为完善

✅ **可扩展性**：模块化设计，便于添加新功能

### 6.2 架构缺点

❌ **缺少依赖注入**：手动创建实例，测试困难

❌ **状态管理复杂**：任务状态分散在多处，难以追踪

❌ **错误处理不统一**：部分地方缺少异常捕获

❌ **UI 与业务耦合**：部分业务逻辑写在 Activity 中

❌ **缺少配置管理**：硬编码较多，不利于灵活配置

### 6.3 技术债务

| 问题 | 位置 | 建议 |
|------|------|------|
| 未使用 DI | 全局 | 考虑引入 Koin 或 Hilt |
| 硬编码字符串 | 多处 | 提取到 strings.xml |
| 魔法数字 | 多处 | 定义为常量 |
| 注释不足 | 部分文件 | 补充关键逻辑注释 |
| 单元测试缺失 | 全局 | 添加核心模块测试 |

---

## 7. 优化建议

### 7.1 短期优化（1-2 周）

#### 7.1.1 自动跳转功能

**目标：** 从 Helper 一键跳转到大麦演出详情页

**实现方案：**
```kotlin
// 使用 Intent 直接打开大麦 App
val intent = packageManager.getLaunchIntentForPackage("cn.damai")
intent?.apply {
    putExtra("url", "https://m.damai.cn/shows/${itemId}.html")
    startActivity(this)
}
```

**涉及文件：**
- `AccessibilityUtils.kt` - 添加跳转方法
- `MainActivity.kt` - 添加跳转按钮

#### 7.1.2 定时触发优化

**目标：** 精确在开抢时刻触发抢票

**实现方案：**
- 使用 `AlarmManager` 设置精确闹钟
- 使用 `WorkManager` 处理后台任务
- 前台服务显示倒计时

**涉及文件：**
- `TicketGrabbingForegroundService.kt` - 添加定时逻辑
- `PreciseTimeManager.kt` - 优化时间同步

#### 7.1.3 倒计时 UI

**目标：** 在主界面显示实时倒计时

**实现方案：**
- 在 `MainActivity` 添加倒计时 TextView
- 使用 `Handler` 或 `Flow` 每秒更新
- 开抢前 30 秒高亮提醒

### 7.2 中期优化（2-4 周）

#### 7.2.1 引入依赖注入

**推荐：** Koin（轻量级，适合 Android）

**好处：**
- 易于单元测试
- 代码更清晰
- 便于管理生命周期

#### 7.2.2 完善 API 抢票

**目标：** 提高 API 抢票成功率

**工作：**
- 逆向大麦 API 签名算法
- 实现完整的请求签名
- 处理 Cookie 和 Session

#### 7.2.3 验证码识别

**方案：**
- 集成第三方 OCR（百度/腾讯）
- 或使用本地 Tesseract
- 建立验证码题库

### 7.3 长期优化（1-2 月）

#### 7.3.1 架构重构

- 引入 MVVM 架构
- 使用 ViewModel + LiveData
- 分离业务逻辑和 UI

#### 7.3.2 性能优化

- 优化无障碍事件处理
- 减少内存占用
- 优化启动速度

#### 7.3.3 用户体验

- 添加引导教程
- 优化错误提示
- 增加成功/失败统计

---

## 附录

### A. 编译环境

```bash
# Android SDK
export ANDROID_HOME=/home/admin/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools

# 编译命令
cd /home/admin/.openclaw/workspace/DM-HEILER_260320
./gradlew assembleDebug
```

### B. 关键依赖

```gradle
// Kotlin
implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

// AndroidX
implementation("androidx.core:core-ktx:1.12.0")
implementation("androidx.appcompat:appcompat:1.6.1")
implementation("com.google.android.material:material:1.11.0")

// Room
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")

// Network
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.google.code.gson:gson:2.10.1")
```

### C. 权限说明

| 权限 | 用途 |
|------|------|
| `BIND_ACCESSIBILITY_SERVICE` | 无障碍服务 |
| `FOREGROUND_SERVICE` | 前台服务 |
| `POST_NOTIFICATIONS` | 发送通知 |
| `SYSTEM_ALERT_WINDOW` | 悬浮窗 |
| `WAKE_LOCK` | 防止休眠 |
| `INTERNET` | 网络访问 |

---

**文档结束**

*如有问题或建议，请更新此文档。*
