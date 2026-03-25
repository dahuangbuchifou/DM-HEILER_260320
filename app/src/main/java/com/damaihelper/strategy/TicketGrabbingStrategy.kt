package com.damaihelper.strategy

import android.content.Context
import com.damaihelper.model.TicketTask
import com.damaihelper.service.TicketGrabbingAccessibilityService

/**
 * 抢票策略接口
 * 定义所有抢票策略必须实现的方法
 */
interface TicketGrabbingStrategy {
    /**
     * 策略名称
     */
    val name: String

    /**
     * 策略优先级（0-100，数值越大优先级越高）
     */
    val priority: Int

    /**
     * 执行抢票
     * @param context Android上下文
     * @param task 抢票任务
     * @return 抢票结果
     */
    suspend fun grab(context: Context, task: TicketTask): GrabbingResult

    /**
     * 检查策略是否可用
     * @param context Android上下文
     * @return 是否可用
     */
    suspend fun isAvailable(context: Context): Boolean

    /**
     * 获取策略的当前状态
     */
    fun getStatus(): StrategyStatus
}

/**
 * 抢票结果
 */
data class GrabbingResult(
    val success: Boolean,
    val message: String,
    val errorCode: Int = 0,
    val retryable: Boolean = true,
    val suggestedNextStrategy: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 策略状态
 */
data class StrategyStatus(
    val name: String,
    val isAvailable: Boolean,
    val lastUsedTime: Long = 0,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val averageResponseTime: Long = 0,
    val lastErrorMessage: String? = null
)

/**
 * UI自动化抢票策略
 */
class UIAutomationStrategy(private val context: Context) : TicketGrabbingStrategy {
    // M1 模块：UI 元素配置
    private object UiElements {
        const val BTN_BUY_TEXT = "立即购买"
        const val BTN_CONFIRM_TEXT = "确定"
        const val BTN_SUBMIT_ORDER_TEXT = "提交订单"
        const val BTN_ADD_QUANTITY_ID = "com.alibaba.damai:id/btn_add"
    }
    override val name: String = "UI自动化"
    override val priority: Int = 50

    private var status = StrategyStatus(name = name, isAvailable = true)

    override suspend fun grab(context: Context, task: TicketTask): GrabbingResult {
        return try {
// 调用无障碍服务执行UI自动化抢票流程
	        val service = TicketGrabbingAccessibilityService.getInstance()
	            ?: throw IllegalStateException("无障碍服务未运行")

		        val result = service.executeUIAutomationGrabbing(task, UiElements.BTN_BUY_TEXT, UiElements.BTN_CONFIRM_TEXT, UiElements.BTN_SUBMIT_ORDER_TEXT, UiElements.BTN_ADD_QUANTITY_ID)
	
		        if (result.success) {
		            return GrabbingResult(
		                success = true,
		                message = "UI自动化抢票成功: ${result.message}",
		                retryable = false
		            )
		        } else {
		            return GrabbingResult(
		                success = false,
		                message = "UI自动化抢票失败: ${result.message}",
		                errorCode = 1001,
		                retryable = true,
		                suggestedNextStrategy = "API抢票"
		            )
		        }
        } catch (e: Exception) {
            GrabbingResult(
                success = false,
                message = "UI自动化抢票失败: ${e.message}",
                errorCode = 1001,
                retryable = true,
                suggestedNextStrategy = "API抢票"
            )
        }
    }

    override suspend fun isAvailable(context: Context): Boolean {
        // TODO: 检查无障碍服务是否启用
        return true
    }

    override fun getStatus(): StrategyStatus {
        return status
    }
}

/**
 * 混合模式抢票策略
 * 结合API和UI自动化的优势
 */
class HybridGrabbingStrategy(private val context: Context) : TicketGrabbingStrategy {
    override val name: String = "混合模式"
    override val priority: Int = 90

    private var status = StrategyStatus(name = name, isAvailable = true)
    private val apiStrategy = APIGrabbingStrategy(context)
    private val uiStrategy = UIAutomationStrategy(context)

    override suspend fun grab(context: Context, task: TicketTask): GrabbingResult {
        return try {
            // 优先尝试API抢票
            val apiResult = apiStrategy.grab(context, task)
            if (apiResult.success) {
                return apiResult
            }

            // API失败，回退到UI自动化
            val uiResult = uiStrategy.grab(context, task)
            if (uiResult.success) {
                return uiResult
            }

            // 两种策略都失败
            GrabbingResult(
                success = false,
                message = "混合模式抢票失败: API和UI都失败",
                errorCode = 3001,
                retryable = true
            )
        } catch (e: Exception) {
            GrabbingResult(
                success = false,
                message = "混合模式抢票异常: ${e.message}",
                errorCode = 3002,
                retryable = true
            )
        }
    }

    override suspend fun isAvailable(context: Context): Boolean {
        return apiStrategy.isAvailable(context) || uiStrategy.isAvailable(context)
    }

    override fun getStatus(): StrategyStatus {
        return status
    }
}
