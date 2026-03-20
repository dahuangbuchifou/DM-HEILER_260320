package com.damaihelper.strategy

import android.content.Context
import android.util.Log
import com.damaihelper.model.TicketTask
// 修正一：导入协程相关的类
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 策略决策引擎
 * 根据实时反馈和历史数据，智能选择最优的抢票策略
 */
class StrategyDecisionEngine(private val context: Context) {
    companion object {
        private const val TAG = "StrategyDecisionEngine"
    }

    private val mutex = Mutex()
    private val strategies = mutableListOf<TicketGrabbingStrategy>()
    private val executionHistory = mutableListOf<ExecutionRecord>()
    private val maxHistorySize = 1000

    // 修正二：创建协程作用域
    // 使用 Default 调度器处理后台初始化任务
    private val engineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        // 修正三：在协程中调用 suspend 函数
        engineScope.launch {
            // 初始化策略
            registerStrategy(APIGrabbingStrategy(context))
            registerStrategy(UIAutomationStrategy(context))
            registerStrategy(HybridGrabbingStrategy(context))
        }
    }

    /**
     * 注册新的策略
     */
    suspend fun registerStrategy(strategy: TicketGrabbingStrategy) {
        mutex.withLock {
            strategies.add(strategy)
            strategies.sortByDescending { it.priority }
        }
        Log.d(TAG, "策略已注册: ${strategy.name}")
    }

    /**
     * 根据当前情况选择最优策略
     */
    suspend fun selectBestStrategy(context: Context, task: TicketTask): TicketGrabbingStrategy? {
        mutex.withLock {
            // 按优先级排序，并过滤出可用的策略
            val availableStrategies = strategies.filter { strategy ->
                try {
                    strategy.isAvailable(context)
                } catch (e: Exception) {
                    Log.w(TAG, "检查策略 ${strategy.name} 可用性时出错: ${e.message}")
                    false
                }
            }

            if (availableStrategies.isEmpty()) {
                Log.w(TAG, "没有可用的策略")
                return null
            }

            // 计算每个策略的评分
            val scoredStrategies = availableStrategies.map { strategy ->
                val score = calculateStrategyScore(strategy)
                strategy to score
            }

            // 选择评分最高的策略
            val bestStrategy = scoredStrategies.maxByOrNull { it.second }?.first
            Log.d(TAG, "选择策略: ${bestStrategy?.name}, 评分: ${scoredStrategies.find { it.first == bestStrategy }?.second}")
            return bestStrategy
        }
    }

    /**
     * 计算策略的评分
     * 综合考虑优先级、历史成功率、平均响应时间等因素
     */
    private fun calculateStrategyScore(strategy: TicketGrabbingStrategy): Double {
        val status = strategy.getStatus()
        val totalAttempts = status.successCount + status.failureCount

        // 如果策略从未被使用过，给予中等评分
        if (totalAttempts == 0) {
            return strategy.priority.toDouble()
        }

        // 计算成功率
        val successRate = status.successCount.toDouble() / totalAttempts

        // 计算响应时间因子（响应时间越短，因子越大）
        val responseTimeFactor = if (status.averageResponseTime > 0) {
            1000.0 / status.averageResponseTime
        } else {
            1.0
        }

        // 综合评分 = 优先级 * 成功率 * 响应时间因子
        val score = strategy.priority * successRate * responseTimeFactor

        Log.d(TAG, "策略 ${strategy.name} 评分: $score (优先级: ${strategy.priority}, 成功率: $successRate, 响应时间因子: $responseTimeFactor)")
        return score
    }

    /**
     * 执行抢票
     * 使用选定的策略执行抢票，如果失败则尝试备选策略
     */
    suspend fun executeGrabbing(context: Context, task: TicketTask): GrabbingResult {
        var lastResult: GrabbingResult? = null
        var attemptedStrategies = mutableSetOf<String>()

        while (attemptedStrategies.size < strategies.size) {
            val strategy = selectBestStrategy(context, task)
                ?: return GrabbingResult(
                    success = false,
                    message = "没有可用的策略",
                    errorCode = 4001,
                    retryable = false
                )

            // 避免重复尝试同一个策略
            if (strategy.name in attemptedStrategies) {
                continue
            }
            attemptedStrategies.add(strategy.name)

            Log.d(TAG, "使用策略 ${strategy.name} 执行抢票")
            val startTime = System.currentTimeMillis()

            try {
                val result = strategy.grab(context, task)
                val duration = System.currentTimeMillis() - startTime

                // 记录执行结果
                recordExecution(ExecutionRecord(
                    strategyName = strategy.name,
                    success = result.success,
                    duration = duration,
                    errorCode = result.errorCode,
                    timestamp = System.currentTimeMillis()
                ))

                if (result.success) {
                    Log.d(TAG, "策略 ${strategy.name} 抢票成功")
                    return result
                }

                lastResult = result

                // 如果策略建议了下一个策略，优先尝试该策略
                if (!result.retryable) {
                    Log.d(TAG, "策略 ${strategy.name} 返回不可重试错误，停止尝试")
                    return result
                }

                Log.d(TAG, "策略 ${strategy.name} 抢票失败，尝试下一个策略")
            } catch (e: Exception) {
                Log.e(TAG, "策略 ${strategy.name} 执行异常: ${e.message}", e)
                lastResult = GrabbingResult(
                    success = false,
                    message = "策略执行异常: ${e.message}",
                    errorCode = 4002,
                    retryable = true
                )
            }
        }

        // 所有策略都失败
        return lastResult ?: GrabbingResult(
            success = false,
            message = "所有策略都失败",
            errorCode = 4003,
            retryable = true
        )
    }

    /**
     * 记录执行结果
     */
    private suspend fun recordExecution(record: ExecutionRecord) {
        mutex.withLock {
            executionHistory.add(record)
            if (executionHistory.size > maxHistorySize) {
                executionHistory.removeAt(0)
            }
        }
    }

    /**
     * 获取策略的历史统计
     */
    suspend fun getStrategyStatistics(strategyName: String): StrategyStatistics {
        mutex.withLock {
            val records = executionHistory.filter { it.strategyName == strategyName }
            val successCount = records.count { it.success }
            val failureCount = records.size - successCount
            val averageDuration = if (records.isNotEmpty()) {
                records.map { it.duration }.average().toLong()
            } else {
                0L
            }

            return StrategyStatistics(
                strategyName = strategyName,
                totalAttempts = records.size,
                successCount = successCount,
                failureCount = failureCount,
                successRate = if (records.isNotEmpty()) successCount.toDouble() / records.size else 0.0,
                averageDuration = averageDuration
            )
        }
    }

    /**
     * 获取所有策略的统计信息
     */
    suspend fun getAllStrategyStatistics(): List<StrategyStatistics> {
        return strategies.map { getStrategyStatistics(it.name) }
    }

    /**
     * 清除执行历史
     */
    suspend fun clearHistory() {
        mutex.withLock {
            executionHistory.clear()
        }
    }
}

/**
 * 执行记录
 */
data class ExecutionRecord(
    val strategyName: String,
    val success: Boolean,
    val duration: Long,
    val errorCode: Int = 0,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 策略统计信息
 */
data class StrategyStatistics(
    val strategyName: String,
    val totalAttempts: Int,
    val successCount: Int,
    val failureCount: Int,
    val successRate: Double,
    val averageDuration: Long
)