# 🎯 自动抢票流程实施方案

_基于用户实际抢票流程的自动化改造_

**创建时间：** 2026-03-25 16:30  
**优先级：** P0

---

## 📋 用户需求

### 抢票流程（3 步）

```
步骤 1：演出详情页
       → 选择场次
       → 点击"立即预订"
       
步骤 2：票档选择页
       → 优先选择最贵票档（如：内场 880 元）
       → 点击"确定"
       
步骤 3：确认购买页
       → 选择观演人
       → 点击"立即提交"
       → 自主支付
```

### 核心目标

1. **手动配置一次** - 在大麦 App 走一遍流程，抓取配置
2. **自动执行** - 开售时间到达后，系统自动完成 3 步操作
3. **降低失误** - 避免人工操作慢、网速慢导致的抢票失败

---

## 🔍 当前代码分析

### 已有功能 ✅

| 模块 | 功能 | 状态 |
|------|------|------|
| ConcertInfoExtractor | 从详情页抓取演出信息 | ✅ 已有 |
| TaskConfigActivity | 任务配置界面 | ✅ 已有 |
| TicketGrabbingAccessibilityService | 无障碍服务 | ✅ 已有 |
| DamaiPageAdapter | 页面类型识别 | ✅ 已有 |

### 需要新增/修改 🔄

| 模块 | 需要修改的内容 | 优先级 |
|------|----------------|--------|
| ConcertInfo | 增加观演人、支付方式等字段 | P0 |
| ConcertInfoExtractor | 抓取票档选择页、确认页信息 | P0 |
| TicketGrabbingAccessibilityService | 新增 3 步自动点击逻辑 | P0 |
| TaskConfigActivity | 显示更多抓取信息、抢票策略选择 | P1 |
| DamaiPageAdapter | 新增票档选择页、确认页识别 | P0 |
| TaskCoordinator | 协调 3 步自动执行流程 | P0 |

---

## 🛠️ 修改计划

### 阶段 1：增强数据模型（P0）

#### 1.1 修改 ConcertInfo.kt

**新增字段：**
```kotlin
data class ConcertInfo(
    val concertName: String = "",
    val venue: String = "",
    val city: String = "",
    val availableDates: List<String> = emptyList(),
    val availablePrices: List<String> = emptyList(),
    val extractTime: Long = System.currentTimeMillis(),
    
    // === 新增字段 ===
    val selectedDate: String = "",           // 已选日期
    val selectedPrice: String = "",          // 已选票价（优先最贵）
    val ticketCount: Int = 1,                // 票数
    val audienceName: String = "",           // 观演人姓名
    val audienceIdCard: String = "",         // 身份证号（脱敏）
    val phoneNumber: String = "",            // 联系电话
    val paymentMethod: String = "支付宝"     // 支付方式
)
```

---

### 阶段 2：增强信息抓取（P0）

#### 2.1 修改 ConcertInfoExtractor.kt

**新增方法：**
```kotlin
// 从票档选择页抓取信息
fun extractFromTicketSelectionPage(rootNode: AccessibilityNodeInfo): ConcertInfo? {
    // 抓取已选场次日期
    val selectedDate = extractSelectedDate(rootNode)
    
    // 抓取所有票档（包括缺货/售罄状态）
    val ticketPrices = extractTicketPricesWithStatus(rootNode)
    
    // 自动选择最贵票档
    val mostExpensivePrice = selectMostExpensivePrice(ticketPrices)
    
    return ConcertInfo(
        selectedDate = selectedDate,
        availablePrices = ticketPrices,
        selectedPrice = mostExpensivePrice
    )
}

// 从确认购买页抓取信息
fun extractFromConfirmPage(rootNode: AccessibilityNodeInfo): ConcertInfo? {
    // 抓取已选观演人
    val audienceName = extractSelectedAudience(rootNode)
    
    // 抓取联系电话
    val phoneNumber = extractPhoneNumber(rootNode)
    
    // 抓取支付方式
    val paymentMethod = extractPaymentMethod(rootNode)
    
    return ConcertInfo(
        audienceName = audienceName,
        phoneNumber = phoneNumber,
        paymentMethod = paymentMethod
    )
}
```

---

### 阶段 3：新增自动执行逻辑（P0）

#### 3.1 修改 TicketGrabbingAccessibilityService.kt

**新增 3 步自动执行方法：**
```kotlin
// 步骤 1：点击"立即预订"
private suspend fun step1_ClickBuyNow(): Boolean {
    Log.i(TAG, "步骤 1：点击立即预订")
    
    val node = findNodeByText("立即预订")
    if (node != null) {
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.i(TAG, "✅ 步骤 1 完成")
        return true
    }
    
    Log.e(TAG, "❌ 步骤 1 失败：未找到立即预订按钮")
    return false
}

// 步骤 2：选择最贵票档并确认
private suspend fun step2_SelectMostExpensiveTicket(): Boolean {
    Log.i(TAG, "步骤 2：选择最贵票档")
    
    // 抓取所有票档
    val priceNodes = findNodesByRegex("内场\\d+ 元 | 看台\\d+ 元")
    
    if (priceNodes.isEmpty()) {
        Log.e(TAG, "❌ 步骤 2 失败：未找到票档选项")
        return false
    }
    
    // 找到最贵的票档
    val mostExpensive = priceNodes.maxByOrNull { node ->
        extractPriceFromNode(node)
    }
    
    if (mostExpensive != null) {
        mostExpensive.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        delay(300) // 等待选择生效
        
        // 点击"确定"按钮
        val confirmNode = findNodeByText("确定")
        if (confirmNode != null) {
            confirmNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.i(TAG, "✅ 步骤 2 完成：选择 ${mostExpensive.text}")
            return true
        }
    }
    
    Log.e(TAG, "❌ 步骤 2 失败")
    return false
}

// 步骤 3：选择观演人并提交订单
private suspend fun step3_SelectAudienceAndSubmit(): Boolean {
    Log.i(TAG, "步骤 3：选择观演人并提交")
    
    // 选择配置的观演人
    val audienceName = currentTask?.audienceName ?: ""
    if (audienceName.isNotEmpty()) {
        val audienceNode = findNodeByText(audienceName)
        audienceNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        delay(300)
    }
    
    // 点击"立即提交"
    val submitNode = findNodeByText("立即提交")
    if (submitNode != null) {
        submitNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.i(TAG, "✅ 步骤 3 完成：订单已提交")
        return true
    }
    
    Log.e(TAG, "❌ 步骤 3 失败：未找到立即提交按钮")
    return false
}
```

---

### 阶段 4：更新页面识别（P0）

#### 4.1 修改 DamaiPageAdapter.kt

**新增页面类型：**
```kotlin
enum class DamaiPageType(val description: String) {
    UNKNOWN("未知页面"),
    DETAIL("演出详情页"),          // ✅ 已有
    TICKET_SELECTION("票档选择页"),  // 🆕 新增
    CONFIRM("确认购买页"),          // 🆕 新增
    QUEUING("排队页"),
    SELECTING("选票页"),
    ...
}
```

**新增识别逻辑：**
```kotlin
// 票档选择页特征
if (pageText.contains("票档") || 
    pageText.contains("看台") || 
    pageText.contains("内场") ||
    findNodeByText("确定") != null) {
    scores[DamaiPageType.TICKET_SELECTION] = 0.9f
}

// 确认购买页特征
if (pageText.contains("确认购买") || 
    pageText.contains("实名观演人") ||
    findNodeByText("立即提交") != null) {
    scores[DamaiPageType.CONFIRM] = 0.9f
}
```

---

### 阶段 5：更新任务配置界面（P1）

#### 5.1 修改 TaskConfigActivity.kt

**新增配置项：**
```kotlin
// 抢票策略选择
private lateinit var strategySpinner: Spinner
// 可选值：优先最贵票档 / 优先有票票档

// 观演人选择
private lateinit var audienceRecyclerView: RecyclerView
// 显示多个观演人，可选择

// 支付方式选择
private lateinit var paymentRadioGroup: RadioGroup
// 支付宝 / 微信
```

---

## 📊 执行流程图

```
开售时间到达
    │
    ▼
┌─────────────────┐
│ 步骤 1          │
│ 点击"立即预订"   │
└────────┬────────
         │
         ▼
┌─────────────────┐
│ 等待页面加载     │
│ (检测票档选择页) │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 步骤 2          │
│ 选择最贵票档     │
│ 点击"确定"       │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 等待页面加载     │
│ (检测确认购买页) │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 步骤 3          │
│ 选择观演人       │
│ 点击"立即提交"   │
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│ 等待支付页面     │
│ 通知用户支付     │
└─────────────────┘
```

---

## ⏱️ 时间优化策略

### 当前人工操作时间
- 步骤 1：~2 秒
- 步骤 2：~5 秒（选择票档 + 确认）
- 步骤 3：~5 秒（选择观演人 + 提交）
- **总计：~12 秒**

### 自动化目标时间
- 步骤 1：<0.5 秒（页面加载完立即点击）
- 步骤 2：<1 秒（自动选择最贵 + 确认）
- 步骤 3：<1 秒（自动选择观演人 + 提交）
- **总计：<2.5 秒**

### 优化 4-5 倍！

---

## 📝 实施顺序

| 顺序 | 任务 | 预计时间 | 依赖 |
|------|------|----------|------|
| 1 | 修改 ConcertInfo 模型 | 10 分钟 | - |
| 2 | 修改 DamaiPageAdapter 页面识别 | 20 分钟 | - |
| 3 | 增强 ConcertInfoExtractor 抓取逻辑 | 40 分钟 | 1 |
| 4 | 新增 3 步自动执行方法 | 40 分钟 | 2 |
| 5 | 更新 TaskCoordinator 协调逻辑 | 20 分钟 | 3,4 |
| 6 | 更新 TaskConfigActivity 界面 | 30 分钟 | 1 |
| 7 | 真机测试验证 | 30 分钟 | 全部 |

**总计：约 3 小时**

---

## ✅ 验收标准

- [ ] 能在大麦 App 票档选择页自动选择最贵票档
- [ ] 能在确认购买页自动选择观演人并提交
- [ ] 从开售到提交订单总时间 < 3 秒
- [ ] 支持配置多个观演人
- [ ] 支持选择支付方式
- [ ] 错误处理完善（缺货/售罄时降级策略）

---

**下一步：** 开始实施阶段 1（修改数据模型）
