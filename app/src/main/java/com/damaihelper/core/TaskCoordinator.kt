// ============================================================================
// 📦 模块：任务协调器
// 📅 创建日期：2026-03-23
// 📝 说明：核心任务编排器，负责驱动整个购票流程
// 📚 参考：合规购票辅助系统_详细技术设计.md
// ============================================================================

package com.damaihelper.core

import android.content.Context
import android.util.Log
import com.damaihelper.model.TicketTask
import com.damaihelper.service.TicketGrabbingAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 任务协调器 - 主控模块
 * 负责串联各模块，驱动状态流转
 */
class TaskCoordinator(
    private val context: Context,
    private val stateMachine: StateMachineEngine
) {
    companion object {
        private const val TAG = "TaskCoordinator"
        
        // 开票前预热时间（秒）
        private const val PREWARM_SECONDS = 60L
        
        // 心跳检查间隔（秒）
        private const val HEARTBEAT_INTERVAL = 30L
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val taskJobs = mutableMapOf<Long, kotlinx.coroutines.Job>()
    
    // 依赖模块（后续逐步注入）
    private var pageAdapter: DamaiPageAdapter? = null
    private var notificationManager: NotificationManager? = null
    private var observabilityService: ObservabilityService? = null

    /**
     * 设置依赖模块（依赖注入）
     */
    fun setDependencies(
        pageAdapter: DamaiPageAdapter?,
        notificationManager: NotificationManager?,
        observabilityService: ObservabilityService?
    ) {
        this.pageAdapter = pageAdapter
        this.notificationManager = notificationManager
        this.observabilityService = observabilityService
    }

    /**
     * 启动任务
     */
    fun startTask(task: TicketTask) {
        Log.d(TAG, "启动任务：${task.id} - ${task.name}")
        
        // 记录任务启动事件
        observabilityService?.recordEvent(
            taskId = task.id,
            eventType = EventType.TASK_START,
            message = "任务启动：${task.name}"
        )
        
        // 创建任务协程
        val job = scope.launch {
            try {
                executeTask(task)
            } catch (e: Exception) {
                Log.e(TAG, "任务执行异常：${e.message}", e)
                stateMachine.handleError(task.id, ErrorType.UNKNOWN, e.message ?: "未知错误")
            }
        }
        
        taskJobs[task.id] = job
    }

    /**
     * 停止任务
     */
    fun stopTask(taskId: Long, reason: String = "用户停止") {
        Log.d(TAG, "停止任务：$taskId - 原因：$reason")
        
        taskJobs[taskId]?.cancel()
        taskJobs.remove(taskId)
        
        scope.launch {
            stateMachine.stopTask(taskId, reason)
        }
        
        observabilityService?.recordEvent(
            taskId = taskId,
            eventType = EventType.TASK_STOP,
            message = reason
        )
    }

    /**
     * 暂停任务
     */
    fun pauseTask(taskId: Long) {
        Log.d(TAG, "暂停任务：$taskId")
        taskJobs[taskId]?.cancel()
    }

    /**
     * 恢复任务
     */
    fun resumeTask(task: TicketTask) {
        Log.d(TAG, "恢复任务：${task.id}")
        startTask(task)
    }

    /**
     * 执行任务主流程
     */
    private suspend fun executeTask(task: TicketTask) {
        val taskId = task.id
        
        // 1. INIT - 初始化
        if (!stateMachine.transitionTo(taskId, TaskState.INIT, "任务启动")) {
            return
        }
        initializeTask(task)
        
        // 2. PRECHECK - 预检查
        if (!stateMachine.transitionTo(taskId, TaskState.PRECHECK, "开始预检查")) {
            return
        }
        val precheckResult = runPrecheck(task)
        if (!precheckResult) {
            Log.e(TAG, "预检查失败")
            stateMachine.completeTask(taskId, false, "预检查失败")
            return
        }
        
        // 3. WAITING_OPEN - 等待开票
        val timeUntilSale = task.grabTime - System.currentTimeMillis()
        if (timeUntilSale > 0) {
            if (!stateMachine.transitionTo(taskId, TaskState.WAITING_OPEN, "等待开票时间")) {
                return
            }
            waitForSaleTime(task)
        } else {
            Log.w(TAG, "开票时间已过，立即进入页面就绪")
        }
        
        // 4. PAGE_READY - 页面就绪
        if (!stateMachine.transitionTo(taskId, TaskState.PAGE_READY, "页面就绪")) {
            return
        }
        monitorPageState(taskId)
    }

    /**
     * 初始化任务
     */
    private suspend fun initializeTask(task: TicketTask) {
        Log.d(TAG, "初始化任务：${task.id}")
        
        withContext(Dispatchers.Main) {
            // 确保无障碍服务已启动
            val service = TicketGrabbingAccessibilityService.getInstance()
            if (service == null) {
                Log.e(TAG, "无障碍服务未运行")
                notificationManager?.notifyCritical("无障碍服务未启用，请在设置中开启")
            }
        }
        
        // 初始化观测服务
        observabilityService?.startTaskRun(task)
        
        delay(500) // 模拟初始化时间
    }

    /**
     * 运行预检查
     */
    private suspend fun runPrecheck(task: TicketTask): Boolean {
        Log.d(TAG, "运行预检查：${task.id}")
        
        val checks = mutableListOf<Pair<String, Boolean>>()
        
        // 检查 1：配置完整性
        val configValid = task.grabTime > 0 && 
                         task.concertKeyword.isNotBlank() &&
                         task.ticketPriceKeyword.isNotBlank()
        checks.add("配置完整性" to configValid)
        
        // 检查 2：时间合理性
        val timeValid = task.grabTime > System.currentTimeMillis() - 60000 // 允许 1 分钟内
        checks.add("时间有效性" to timeValid)
        
        // 检查 3：无障碍服务状态
        val serviceReady = TicketGrabbingAccessibilityService.getInstance() != null
        checks.add("无障碍服务" to serviceReady)
        
        // 输出检查结果
        checks.forEach { (name, passed) ->
            Log.d(TAG, "预检查 [$name]: ${if (passed) "✓" else "✗"}")
            observabilityService?.recordEvent(
                taskId = task.id,
                eventType = EventType.PRECHECK_ITEM,
                message = "检查项：$name - ${if (passed) "通过" else "失败"}"
            )
        }
        
        val allPassed = checks.all { it.second }
        
        if (!allPassed) {
            val failedChecks = checks.filterNot { it.second }.map { it.first }
            notificationManager?.notifyWarning("预检查失败：${failedChecks.joinToString(", ")}")
        }
        
        return allPassed
    }

    /**
     * 等待开票时间
     */
    private suspend fun waitForSaleTime(task: TicketTask) {
        val saleTime = task.grabTime
        val prewarmTime = saleTime - PREWARM_SECONDS * 1000
        
        Log.d(TAG, "开票时间：${saleTime}, 预热时间：${prewarmTime}")
        
        // 通知用户即将开票
        notificationManager?.notifyInfo("距离开票还有 ${PREWARM_SECONDS / 60} 分钟")
        
        // 等待到预热时间
        val waitTime = prewarmTime - System.currentTimeMillis()
        if (waitTime > 0) {
            Log.d(TAG, "等待 ${waitTime / 1000} 秒后预热")
            
            // 心跳检查
            var remainingSeconds = waitTime / 1000
            while (remainingSeconds > 0) {
                delay(HEARTBEAT_INTERVAL * 1000)
                remainingSeconds -= HEARTBEAT_INTERVAL
                
                // 检查任务是否被取消
                if (stateMachine.getCurrentState(task.id) == TaskState.FAILED) {
                    return
                }
                
                // 心跳日志
                Log.d(TAG, "心跳检查：剩余 ${remainingSeconds} 秒")
                observabilityService?.recordEvent(
                    taskId = task.id,
                    eventType = EventType.HEARTBEAT,
                    message = "等待开票，剩余 ${remainingSeconds} 秒"
                )
            }
        }
        
        // 预热页面
        Log.d(TAG, "开始预热页面")
        notificationManager?.notifyInfo("即将开票，正在预热页面...")
        
        // 预热逻辑（后续由 PageAdapter 实现）
        pageAdapter?.warmupPage(task.concertKeyword)
        
        // 等待到开票时间
        val finalWait = saleTime - System.currentTimeMillis()
        if (finalWait > 0) {
            delay(finalWait)
        }
        
        Log.d(TAG, "开票时间到达！")
        notificationManager?.notifyCritical("开票了！开始抢票！")
    }

    /**
     * 监控页面状态
     */
    private suspend fun monitorPageState(taskId: Long) {
        Log.d(TAG, "开始监控页面状态：$taskId")
        
        // 获取无障碍服务
        val service = TicketGrabbingAccessibilityService.getInstance()
            ?: run {
                Log.e(TAG, "无障碍服务不可用")
                stateMachine.handleError(taskId, ErrorType.SESSION_ERROR, "无障碍服务未运行")
                return
            }
        
        // 持续监控页面状态
        while (true) {
            val currentState = stateMachine.getCurrentState(taskId)
            if (currentState == TaskState.SUCCESS || currentState == TaskState.FAILED) {
                break
            }
            
            try {
                // 检测当前页面类型（后续由 PageAdapter 实现）
                val pageType = pageAdapter?.detectPageType()
                
                when (pageType) {
                    "QUEUING" -> {
                        if (currentState != TaskState.QUEUING) {
                            stateMachine.transitionTo(taskId, TaskState.QUEUING, "检测到排队页面")
                            notificationManager?.notifyInfo("进入排队页面")
                        }
                    }
                    "SELECTING" -> {
                        if (currentState != TaskState.SELECTING) {
                            stateMachine.transitionTo(taskId, TaskState.SELECTING, "检测到选票页面")
                            notificationManager?.notifyInfo("可以选座了！")
                        }
                    }
                    "CONFIRMING" -> {
                        if (currentState != TaskState.CONFIRMING) {
                            stateMachine.transitionTo(taskId, TaskState.CONFIRMING, "检测到确认页面")
                            notificationManager?.notifyCritical("进入订单确认页，请快速确认！")
                        }
                    }
                    "CAPTCHA" -> {
                        stateMachine.handleError(taskId, ErrorType.RISK_CHALLENGE, "检测到验证码")
                    }
                    "ERROR" -> {
                        stateMachine.handleError(taskId, ErrorType.PAGE_ERROR, "页面异常")
                    }
                    else -> {
                        Log.d(TAG, "当前页面类型：$pageType")
                    }
                }
                
                delay(2000) // 2 秒检查一次
                
            } catch (e: Exception) {
                Log.e(TAG, "页面监控异常：${e.message}")
                stateMachine.handleError(taskId, ErrorType.UNKNOWN, e.message ?: "未知错误")
            }
        }
    }

    /**
     * 获取任务状态
     */
    fun getTaskState(taskId: Long): TaskState? {
        return stateMachine.getCurrentState(taskId)
    }

    /**
     * 获取任务状态 Flow
     */
    fun getTaskStateFlow(taskId: Long): StateFlow<TaskState>? {
        return stateMachine.getStateFlow(taskId)
    }

    /**
     * 处理用户接管
     */
    fun handleUserTakeover(taskId: Long, action: String) {
        Log.d(TAG, "用户接管：$taskId - $action")
        
        scope.launch {
            when (action) {
                "CONTINUE" -> {
                    // 用户处理完成，继续流程
                    val currentState = stateMachine.getCurrentState(taskId)
                    when (currentState) {
                        TaskState.MANUAL_TAKEOVER -> {
                            stateMachine.transitionTo(taskId, TaskState.CONFIRMING, "用户接管完成")
                        }
                        else -> {
                            Log.w(TAG, "当前状态不需要接管：$currentState")
                        }
                    }
                }
                "CANCEL" -> {
                    // 用户取消
                    stopTask(taskId, "用户取消")
                }
            }
        }
        
        observabilityService?.recordEvent(
            taskId = taskId,
            eventType = EventType.USER_TAKEOVER,
            message = "用户操作：$action"
        )
    }

    /**
     * 清理资源
     */
    fun destroy() {
        Log.d(TAG, "协调器销毁")
        scope.cancel()
    }
}
