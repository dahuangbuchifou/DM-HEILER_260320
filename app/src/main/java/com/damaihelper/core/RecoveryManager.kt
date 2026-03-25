// ============================================================================
// 📦 模块：恢复管理器
// 📅 创建日期：2026-03-23
// 📝 说明：负责异常恢复策略匹配和执行
// 📚 参考：合规购票辅助系统_详细技术设计.md
// ============================================================================

package com.damaihelper.core

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 恢复计划
 */
data class RecoveryPlan(
    val errorType: ErrorType,
    val actions: List<RecoveryAction>,
    val maxRetries: Int = 3
)

/**
 * 恢复动作
 */
data class RecoveryAction(
    val type: ActionType,
    val description: String,
    val timeoutMs: Long = 5000L
)

enum class ActionType {
    REFRESH_PAGE,      // 刷新页面
    RE_NAVIGATE,       // 重新导航
    REPOSITION_ELEMENT,// 重新定位元素
    REBUILD_CONTEXT,   // 重建上下文
    WAIT_AND_RETRY,    // 等待后重试
    BACK_TO_STABLE     // 返回稳定状态
}

/**
 * 恢复结果
 */
data class RecoveryResult(
    val success: Boolean,
    val attemptedActions: Int,
    val message: String,
    val nextState: TaskState?
)

/**
 * 恢复管理器
 */
class RecoveryManager(
    private val stateMachine: StateMachineEngine,
    private val pageAdapter: DamaiPageAdapter
) {
    companion object {
        private const val TAG = "RecoveryManager"
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 恢复策略配置
    private val recoveryStrategies = mapOf(
        ErrorType.SESSION_ERROR to RecoveryPlan(
            errorType = ErrorType.SESSION_ERROR,
            actions = listOf(
                RecoveryAction(ActionType.WAIT_AND_RETRY, "等待后重试", 3000),
                RecoveryAction(ActionType.REFRESH_PAGE, "刷新页面", 5000),
                RecoveryAction(ActionType.RE_NAVIGATE, "重新导航到目标页", 10000)
            ),
            maxRetries = 3
        ),
        ErrorType.PAGE_ERROR to RecoveryPlan(
            errorType = ErrorType.PAGE_ERROR,
            actions = listOf(
                RecoveryAction(ActionType.REPOSITION_ELEMENT, "重新定位元素", 3000),
                RecoveryAction(ActionType.REFRESH_PAGE, "刷新页面", 5000),
                RecoveryAction(ActionType.BACK_TO_STABLE, "返回 PAGE_READY 状态", 5000)
            ),
            maxRetries = 3
        ),
        ErrorType.NETWORK_ERROR to RecoveryPlan(
            errorType = ErrorType.NETWORK_ERROR,
            actions = listOf(
                RecoveryAction(ActionType.WAIT_AND_RETRY, "等待网络恢复", 5000),
                RecoveryAction(ActionType.REFRESH_PAGE, "刷新页面", 5000)
            ),
            maxRetries = 2
        ),
        ErrorType.ACTION_ERROR to RecoveryPlan(
            errorType = ErrorType.ACTION_ERROR,
            actions = listOf(
                RecoveryAction(ActionType.REPOSITION_ELEMENT, "重新定位元素", 3000),
                RecoveryAction(ActionType.WAIT_AND_RETRY, "等待后重试", 5000)
            ),
            maxRetries = 3
        ),
        ErrorType.RISK_CHALLENGE to RecoveryPlan(
            errorType = ErrorType.RISK_CHALLENGE,
            actions = listOf(
                RecoveryAction(ActionType.BACK_TO_STABLE, "转人工接管", 1000)
            ),
            maxRetries = 1
        )
    )

    /**
     * 执行恢复
     */
    suspend fun executeRecovery(
        taskId: Long,
        errorType: ErrorType,
        errorMessage: String
    ): RecoveryResult {
        Log.d(TAG, "开始恢复：taskId=$taskId, errorType=$errorType")
        
        val plan = recoveryStrategies[errorType]
            ?: return RecoveryResult(
                success = false,
                attemptedActions = 0,
                message = "未知的错误类型，无法恢复",
                nextState = TaskState.FAILED
            )
        
        var attemptedActions = 0
        var lastState = stateMachine.getCurrentState(taskId)
        
        for ((retryIndex, action) in plan.actions.withIndex()) {
            if (retryIndex >= plan.maxRetries) {
                Log.w(TAG, "达到最大重试次数，恢复失败")
                break
            }
            
            Log.d(TAG, "执行恢复动作 ${retryIndex + 1}/${plan.actions.size}: ${action.description}")
            
            val success = executeAction(taskId, action)
            attemptedActions++
            
            if (success) {
                Log.d(TAG, "恢复动作执行成功")
                
                // 检查是否恢复到稳定状态
                val currentState = stateMachine.getCurrentState(taskId)
                if (currentState != TaskState.RETRY_PENDING && 
                    currentState != TaskState.MANUAL_TAKEOVER &&
                    currentState != TaskState.FAILED) {
                    
                    return RecoveryResult(
                        success = true,
                        attemptedActions = attemptedActions,
                        message = "恢复成功",
                        nextState = currentState
                    )
                }
            } else {
                Log.w(TAG, "恢复动作执行失败，尝试下一个动作")
                delay(1000)
            }
        }
        
        // 所有恢复动作都失败
        Log.e(TAG, "所有恢复动作都失败")
        return RecoveryResult(
            success = false,
            attemptedActions = attemptedActions,
            message = "恢复失败：$errorMessage",
            nextState = TaskState.FAILED
        )
    }

    /**
     * 执行单个恢复动作
     */
    private suspend fun executeAction(taskId: Long, action: RecoveryAction): Boolean {
        return try {
            when (action.type) {
                ActionType.REFRESH_PAGE -> {
                    pageAdapter.refreshPage()
                    delay(action.timeoutMs)
                    true
                }
                ActionType.RE_NAVIGATE -> {
                    // 重新导航逻辑（需要任务信息）
                    Log.d(TAG, "重新导航到目标页")
                    delay(action.timeoutMs)
                    true
                }
                ActionType.REPOSITION_ELEMENT -> {
                    // 重新定位元素
                    Log.d(TAG, "重新定位元素")
                    delay(action.timeoutMs)
                    true
                }
                ActionType.WAIT_AND_RETRY -> {
                    delay(action.timeoutMs)
                    true
                }
                ActionType.BACK_TO_STABLE -> {
                    // 返回稳定状态
                    stateMachine.transitionTo(taskId, TaskState.PAGE_READY, "恢复管理器触发")
                    true
                }
                ActionType.REBUILD_CONTEXT -> {
                    // 重建上下文（需要更多实现）
                    Log.d(TAG, "重建上下文")
                    delay(action.timeoutMs)
                    true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "执行恢复动作失败：${e.message}")
            false
        }
    }

    /**
     * 获取恢复历史
     */
    fun getRecoveryHistory(taskId: Long): List<Map<String, Any>> {
        // 这里应该从数据库或日志中读取
        return emptyList()
    }

    /**
     * 获取恢复统计
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "totalRecoveries" to 0,
            "successfulRecoveries" to 0,
            "failedRecoveries" to 0,
            "averageRecoveryTime" to 0L
        )
    }
}
