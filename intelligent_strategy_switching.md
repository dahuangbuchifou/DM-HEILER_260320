# 第7阶段：智能决策与策略切换引擎实现文档

## 概述

本阶段实现了一个**智能决策与策略切换引擎**，该引擎能够根据实时反馈和历史数据，动态选择最优的抢票策略，从而大幅提升抢票的灵活性和成功率。

## 核心架构

### 1. 策略接口 (`TicketGrabbingStrategy`)

定义了所有抢票策略必须实现的通用接口，包括：

*   **`name`**：策略名称
*   **`priority`**：策略优先级（0-100，数值越大优先级越高）
*   **`grab()`**：执行抢票的核心方法
*   **`isAvailable()`**：检查策略是否可用
*   **`getStatus()`**：获取策略的当前状态

### 2. 具体策略实现

#### 2.1 UI自动化策略 (`UIAutomationStrategy`)

*   **优先级**：50
*   **特点**：
    *   基于无障碍服务的UI自动化
    *   对大麦App的UI变动有一定的适应能力
    *   响应速度相对较慢，但稳定性较好
*   **适用场景**：
    *   API被风控阻止时
    *   需要进行复杂交互（如答题）时

#### 2.2 API抢票策略 (`APIGrabbingStrategy`)

*   **优先级**：80
*   **特点**：
    *   直接调用大麦网API
    *   速度快，效率高
    *   需要破解API签名算法
*   **适用场景**：
    *   API可用且未被风控阻止时
    *   需要快速抢票时

#### 2.3 混合模式策略 (`HybridGrabbingStrategy`)

*   **优先级**：90（最高）
*   **特点**：
    *   优先尝试API抢票
    *   API失败时自动回退到UI自动化
    *   结合了两种策略的优势
*   **适用场景**：
    *   需要最高成功率时
    *   对抢票速度和稳定性都有要求时

### 3. 决策引擎 (`StrategyDecisionEngine`)

#### 3.1 核心功能

*   **策略注册**：支持动态注册新的抢票策略
*   **智能选择**：根据当前情况选择最优策略
*   **执行管理**：执行抢票并处理失败重试
*   **统计分析**：收集和分析策略的执行数据

#### 3.2 策略评分算法

决策引擎使用以下公式计算策略的评分：

```
评分 = 优先级 × 成功率 × 响应时间因子

其中：
- 优先级：策略定义的优先级（0-100）
- 成功率：历史成功次数 / 历史总尝试次数
- 响应时间因子：1000 / 平均响应时间（毫秒）
```

这个算法综合考虑了策略的基本优先级、历史表现和响应速度，从而能够动态适应不同的网络和风控环境。

#### 3.3 执行流程

```
1. 调用 executeGrabbing() 方法
2. 选择最优策略
3. 执行该策略的 grab() 方法
4. 记录执行结果
5. 如果成功，返回结果
6. 如果失败且可重试，尝试下一个最优策略
7. 重复步骤2-6，直到成功或所有策略都失败
```

## 数据结构

### GrabbingResult

表示一次抢票的结果：

```kotlin
data class GrabbingResult(
    val success: Boolean,                    // 是否成功
    val message: String,                     // 结果信息
    val errorCode: Int = 0,                  // 错误代码
    val retryable: Boolean = true,           // 是否可重试
    val suggestedNextStrategy: String? = null, // 建议的下一个策略
    val timestamp: Long = System.currentTimeMillis()
)
```

### StrategyStatus

表示策略的当前状态：

```kotlin
data class StrategyStatus(
    val name: String,                        // 策略名称
    val isAvailable: Boolean,                // 是否可用
    val lastUsedTime: Long = 0,              // 最后使用时间
    val successCount: Int = 0,               // 成功次数
    val failureCount: Int = 0,               // 失败次数
    val averageResponseTime: Long = 0,       // 平均响应时间
    val lastErrorMessage: String? = null     // 最后的错误信息
)
```

### ExecutionRecord

表示一次策略执行的记录：

```kotlin
data class ExecutionRecord(
    val strategyName: String,                // 策略名称
    val success: Boolean,                    // 是否成功
    val duration: Long,                      // 执行时间（毫秒）
    val errorCode: Int = 0,                  // 错误代码
    val timestamp: Long = System.currentTimeMillis()
)
```

### StrategyStatistics

表示策略的统计信息：

```kotlin
data class StrategyStatistics(
    val strategyName: String,                // 策略名称
    val totalAttempts: Int,                  // 总尝试次数
    val successCount: Int,                   // 成功次数
    val failureCount: Int,                   // 失败次数
    val successRate: Double,                 // 成功率
    val averageDuration: Long                // 平均执行时间
)
```

## 使用示例

```kotlin
// 1. 创建决策引擎
val decisionEngine = StrategyDecisionEngine(context)

// 2. 执行抢票
val result = decisionEngine.executeGrabbing(context, task)

if (result.success) {
    Log.d(TAG, "抢票成功！")
} else {
    Log.e(TAG, "抢票失败: ${result.message}")
}

// 3. 获取策略统计信息
val statistics = decisionEngine.getAllStrategyStatistics()
statistics.forEach { stat ->
    Log.d(TAG, "${stat.strategyName}: 成功率=${stat.successRate}, 平均时间=${stat.averageDuration}ms")
}
```

## 与前期功能的集成

### 与TicketGrabbingAccessibilityService的集成

UI自动化策略应该调用 `TicketGrabbingAccessibilityService` 中的抢票方法：

```kotlin
class UIAutomationStrategy(private val context: Context) : TicketGrabbingStrategy {
    override suspend fun grab(context: Context, task: TicketTask): GrabbingResult {
        // 调用无障碍服务执行抢票
        val accessibilityService = TicketGrabbingAccessibilityService.getInstance()
        return accessibilityService?.executeTicketGrabbing(task) ?: GrabbingResult(...)
    }
}
```

### 与DamaiApiClient的集成

API抢票策略应该调用 `DamaiApiClient` 中的API方法：

```kotlin
class APIGrabbingStrategy(private val context: Context) : TicketGrabbingStrategy {
    override suspend fun grab(context: Context, task: TicketTask): GrabbingResult {
        val sessionManager = SessionManager(context)
        val apiClient = DamaiApiClient(context, sessionManager, AntiDetectionModule(context))
        
        // 执行API抢票流程
        val concerts = apiClient.getConcertList(keyword = task.concertKeyword)
        // ... 后续逻辑
    }
}
```

## 优势与局限

### 优势

1.  **高度灵活**：支持多种策略的动态切换，能够应对大麦网的不同反作弊策略。
2.  **自适应**：根据历史数据和实时反馈自动调整策略选择，提高成功率。
3.  **容错能力强**：当一种策略失败时，自动尝试备选策略，提高整体成功率。
4.  **可扩展**：易于添加新的抢票策略，无需修改核心引擎。

### 局限

1.  **初期数据不足**：新策略在初期可能缺乏历史数据，导致评分不准确。
2.  **实时性**：决策引擎基于历史数据做出决策，可能无法立即响应大麦网的实时风控变化。
3.  **策略复杂性**：不同策略的实现复杂度差异大，某些策略（如API抢票）需要持续维护以适应API变化。

## 下一步优化方向

### 短期优化

1.  **动态权重调整**：根据当前网络状况、时间等因素动态调整策略权重。
2.  **预测性决策**：基于历史数据预测哪种策略在当前时刻最可能成功。
3.  **并行执行**：在某些场景下，同时执行多个策略，取最先成功的结果。

### 中期优化

1.  **机器学习集成**：使用机器学习模型预测策略成功率，优化决策。
2.  **实时风控检测**：实时检测大麦网的风控策略变化，动态调整策略。
3.  **A/B测试框架**：支持对不同策略进行A/B测试，找到最优配置。

### 长期优化

1.  **深度强化学习**：使用强化学习算法，让系统自动学习最优的策略选择和参数调整。
2.  **多目标优化**：同时优化成功率、速度和隐蔽性等多个目标。
3.  **跨平台策略**：扩展到猫眼、纷玩岛等其他票务平台。

## 总结

第7阶段的智能决策与策略切换引擎是我们抢票工具的"大脑"。通过这个引擎，我们实现了从被动的单一策略到主动的多策略自适应的转变，大幅提升了工具的灵活性和成功率。这也为后续的AI功能集成（如机器学习、强化学习等）奠定了坚实的基础。

