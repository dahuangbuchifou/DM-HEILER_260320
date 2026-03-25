# 🐛 编译错误修复记录

_记录所有编译错误及解决方案，避免重复踩坑_

**创建时间：** 2026-03-25  
**项目：** DM-HEILER_260320（大麦抢票助手）  
**总错误数：** 22+ 个  
**修复轮次：** 3 轮

---

## 📋 错误汇总清单

| # | 文件 | 行号 | 错误类型 | 错误原因 | 解决方案 | 状态 |
|---|------|------|----------|----------|----------|------|
| 1 | `app/build.gradle` | 13 | 插件缺失 | 注释了 `kotlin-kapt` 插件但代码使用 Room 注解 | 启用 `id("kotlin-kapt")` | ✅ |
| 2 | `app/build.gradle` | 63-65 | 依赖缺失 | 注释了 Room 依赖但代码使用 `@Entity`/`@Dao` | 恢复 Room 依赖和 kapt | ✅ |
| 3 | `gradle.properties` | 6 | 内存不足 | JVM 内存仅 768MB，Gradle 编译 OOM | 增加到 2048MB | ✅ |
| 4 | `gradle.properties` | 8-11 | 配置错误 | 禁用了 kapt 和并行构建 | 启用 kapt 和并行构建 | ✅ |
| 5 | `TaskCoordinator.kt` | 68 | 类型不匹配 | `eventType = "TASK_START"` 使用字符串而非枚举 | 改为 `EventType.TASK_START` | ✅ |
| 6 | `TaskCoordinator.kt` | 93 | 类型不匹配 | `eventType = "TASK_STOP"` 使用字符串 | 改为 `EventType.TASK_STOP` | ✅ |
| 7 | `TaskCoordinator.kt` | 185 | 类型不匹配 | `eventType = "PRECHECK_ITEM"` 使用字符串 | 改为 `EventType.PRECHECK_ITEM` | ✅ |
| 8 | `TaskCoordinator.kt` | 233 | 类型不匹配 | `eventType = "HEARTBEAT"` 使用字符串 | 改为 `EventType.HEARTBEAT` | ✅ |
| 9 | `TaskCoordinator.kt` | 347 | 类型不匹配 | `eventType = "USER_TAKEOVER"` 使用字符串 | 改为 `EventType.USER_TAKEOVER` | ✅ |
| 10 | `TaskCoordinator.kt` | 309 | 类型不匹配 | `when` 语句用字符串匹配 `DamaiPageType` 枚举 | 改为 `DamaiPageType.QUEUING` | ✅ |
| 11 | `TaskCoordinator.kt` | 315 | 类型不匹配 | `when` 语句用字符串匹配 `DamaiPageType` 枚举 | 改为 `DamaiPageType.SELECTING` | ✅ |
| 12 | `TaskCoordinator.kt` | 321 | 类型不匹配 | `when` 语句用字符串匹配 `DamaiPageType` 枚举 | 改为 `DamaiPageType.CONFIRMING` | ✅ |
| 13 | `TaskCoordinator.kt` | 327 | 类型不匹配 | `when` 语句用字符串匹配 `DamaiPageType` 枚举 | 改为 `DamaiPageType.CAPTCHA` | ✅ |
| 14 | `TaskCoordinator.kt` | 330 | 类型不匹配 | `when` 语句用字符串匹配 `DamaiPageType` 枚举 | 改为 `DamaiPageType.ERROR` | ✅ |
| 15 | `ObservabilityService.kt` | 487 | API 错误 | `logDir.size()` 不是有效 API | 改为 `logDir.listFiles()?.size ?: 0` | ✅ |
| 16 | `DamaiPageAdapter.kt` | 214 | 未解析引用 | `GLOBAL_ACTION_BACK` 通过 AccessibilityUtils 调用 | 直接用 `service.performGlobalAction()` | ✅ |
| 17 | `DamaiPageAdapter.kt` | 216 | 未解析引用 | `GLOBAL_ACTION_BACK` 通过 AccessibilityUtils 调用 | 直接用 `service.performGlobalAction()` | ✅ |
| 18 | `DamaiPageAdapter.kt` | 214 | Receiver 类型不匹配 | `AccessibilityUtils.performGlobalAction()` 参数类型错误 | 直接调用 service 方法 | ✅ |

---

## 📝 详细修复记录

### 第 1 轮：配置修复（2026-03-25 09:33）

**问题：** 服务器内存不足导致编译 OOM，临时禁用 kapt 和 Room

**修复文件：**
- `app/build.gradle`
- `gradle.properties`

**根本原因：** 代码使用了 Room 注解（`@Entity`、`@Dao`、`@Database`），但配置中禁用了 kapt 注解处理器。

---

### 第 2 轮：类型错误修复（2026-03-25 09:45）

**问题：** 14 个编译错误，主要是类型不匹配

#### 错误模式 1：枚举 vs 字符串

**错误代码：**
```kotlin
observabilityService?.recordEvent(
    taskId = task.id,
    eventType = "TASK_START",  // ❌ 错误：字符串
    message = "任务启动：${task.name}"
)
```

**正确代码：**
```kotlin
observabilityService?.recordEvent(
    taskId = task.id,
    eventType = EventType.TASK_START,  // ✅ 正确：枚举值
    message = "任务启动：${task.name}"
)
```

**教训：** 调用方法前先看参数类型定义，不要假设是字符串。

---

#### 错误模式 2：when 语句类型匹配

**错误代码：**
```kotlin
val pageType = pageAdapter?.detectPageType()  // 返回 DamaiPageType?

when (pageType) {
    "QUEUING" -> { ... }  // ❌ 错误：用字符串匹配枚举
    "SELECTING" -> { ... }
}
```

**正确代码：**
```kotlin
val pageType = pageAdapter?.detectPageType()  // 返回 DamaiPageType?

when (pageType) {
    DamaiPageType.QUEUING -> { ... }  // ✅ 正确：用枚举值匹配
    DamaiPageType.SELECTING -> { ... }
}
```

**教训：** when 语句的模式匹配必须与表达式类型一致。

---

### 第 3 轮：API 调用修复（2026-03-25 10:36）

#### 错误模式 3：无障碍服务 API 调用

**错误代码：**
```kotlin
// DamaiPageAdapter.kt
class DamaiPageAdapter(private val service: AccessibilityService) {
    
    fun refreshPage(): PageActionResult {
        // ❌ 错误：AccessibilityUtils 没有这个方法，且参数类型不匹配
        AccessibilityUtils.performGlobalAction(service, AccessibilityServiceInfo.GLOBAL_ACTION_BACK)
    }
}
```

**正确代码：**
```kotlin
class DamaiPageAdapter(private val service: AccessibilityService) {
    
    fun refreshPage(): PageActionResult {
        // ✅ 正确：直接调用 AccessibilityService 的方法
        service.performGlobalAction(AccessibilityServiceInfo.GLOBAL_ACTION_BACK)
    }
}
```

**教训：** 
1. 调用 API 前确认方法是否存在
2. `AccessibilityService` 本身就有 `performGlobalAction()` 方法，不需要包装
3. 工具类应该包装复杂逻辑，而不是简单代理系统 API

---

## 🎯 常见问题模式总结

### 1. 枚举 vs 字符串混淆

**症状：** `Incompatible types: String and XxxEnum`

**原因：** Kotlin 是强类型语言，枚举和字符串不能互换。

**预防：**
- 定义枚举时同时定义 `toString()` 方法用于日志输出
- 使用 IDE 的类型提示，不要忽略红色波浪线
- 写代码时先看方法签名

---

### 2. when 语句类型不匹配

**症状：** `Type mismatch: inferred type is String but XxxEnum was expected`

**原因：** when 语句的分支必须与表达式类型兼容。

**预防：**
```kotlin
// 错误示例
val status: Status = Status.SUCCESS
when (status) {
    "SUCCESS" -> ...  // ❌
}

// 正确示例
when (status) {
    Status.SUCCESS -> ...  // ✅
}

// 或者用字符串时先转换
when (status.name) {
    "SUCCESS" -> ...  // ✅
}
```

---

### 3. 工具类过度包装

**症状：** 工具类方法只是简单调用系统 API，但参数类型搞错

**原因：** 为了"统一接口"而包装，但增加了复杂度。

**预防：**
- 系统 API 本身很简单时，直接调用
- 工具类应该封装**复杂逻辑**，不是简单代理
- 包装前先问：这层抽象带来了什么价值？

---

### 4. Gradle 配置与代码不一致

**症状：** 编译时提示注解无法处理、符号未解析

**原因：** build.gradle 中的配置与代码实际使用不匹配。

**预防：**
- 修改配置前先搜索代码中是否使用了相关功能
- 使用 IDE 的 Gradle 同步功能，及时发现问题
- 注释依赖前先确认代码是否需要

---

## 📚 最佳实践建议

### 编码阶段

1. **启用 IDE 检查** - 不要忽略任何红色波浪线
2. **实时编译** - 写几行就按 `Ctrl+F9` 检查
3. **查看方法签名** - 调用前看参数类型和返回值
4. **使用代码模板** - 枚举匹配用 IDE 自动生成

### 代码审查

1. **类型检查** - 所有变量、参数类型是否正确
2. **枚举使用** - 是否混用了字符串和枚举
3. **API 调用** - 调用的方法是否真实存在
4. **依赖配置** - build.gradle 与代码是否一致

### 编译失败时

1. **读完整错误** - 不要只看第一行
2. **定位第一个错误** - 后续错误可能是连锁反应
3. **搜索错误信息** - Google/StackOverflow
4. **逐行对比** - 与正确代码对比差异

---

## 🔗 相关 Commit

| Commit | 说明 | 日期 |
|--------|------|------|
| `b4e1dbb` | 恢复 kapt 和 Room 配置 | 2026-03-25 09:33 |
| `fc183f4` | 修复 EventType 类型错误 | 2026-03-25 09:45 |
| `4206fe7` | 修复 when 语句和 API 调用 | 2026-03-25 10:36 |

---

## ✅ 验证清单

编译前检查：

- [ ] 所有枚举值使用正确（不是字符串）
- [ ] when 语句分支类型匹配
- [ ] 调用的方法真实存在
- [ ] 导入语句完整
- [ ] build.gradle 配置与代码一致
- [ ] Gradle 同步成功（无红色错误）

---

**最后更新：** 2026-03-25 10:36  
**状态：** 所有已知错误已修复
