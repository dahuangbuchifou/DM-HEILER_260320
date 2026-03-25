// ============================================================================
// 📦 模块：状态机引擎
// 📅 创建日期：2026-03-23
// 📝 说明：核心状态机管理，负责状态转换、校验和事件触发
// 📚 参考：合规购票辅助系统_状态机与模块接口.md
// ============================================================================

package com.damaihelper.core

import android.util.Log
import com.damaihelper.model.TicketTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 购票任务状态枚举
 * 对应文档中的 13 个核心状态
 */
enum class TaskState(val description: String) {
    // 初始阶段
    INIT("初始化"),
    PRECHECK("预检查"),
    WAITING_OPEN("等待开票"),
    
    // 执行阶段
    PAGE_READY("页面就绪"),
    QUEUING("排队中"),
    SELECTING("选票中"),
    CONFIRMING("确认中"),
    
    // 异常处理
    MANUAL_TAKEOVER("人工接管"),
    RETRY_PENDING("等待恢复"),
    
    // 终止状态
    SUCCESS("成功"),
    FAILED("失败")
}

/**
 * 错误类型枚举
 * 对应文档中的错误分类
 */
enum class ErrorType(val code: String) {
    CONFIG_ERROR("CONFIG_ERROR"),
    SESSION_ERROR("SESSION_ERROR"),
    PAGE_ERROR("PAGE_ERROR"),
    ACTION_ERROR("ACTION_ERROR"),
    RISK_CHALLENGE("RISK_CHALLENGE"),
    NETWORK_ERROR("NETWORK_ERROR"),
    USER_ABORT("USER_ABORT"),
    UNKNOWN("UNKNOWN")
}

/**
 * 状态转换事件
 */
data class StateTransition(
    val taskId: Long,
    val fromState: TaskState,
    val toState: TaskState,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap()
)

/**
 * 状态机配置
 */
data class StateMachineConfig(
    val maxRetryCount: Int = 3,
    val stateTimeoutMs: Long = 300_000L, // 默认 5 分钟超时
    val enableLogging: Boolean = true
)

/**
 * 状态处理器接口
 * 每个状态需要实现进入和退出逻辑
 */
interface StateHandler {
    suspend fun onEnter(context: TaskContext)
    suspend fun onExit(context: TaskContext)
    suspend fun canTransition(toState: TaskState): Boolean
}

/**
 * 任务上下文
 * 携带状态流转过程中的所有必要信息
 */
data class TaskContext(
    val task: TicketTask,
    val runId: String,
    var currentState: TaskState = TaskState.INIT,
    var retryCount: Int = 0,
    val startTime: Long = System.currentTimeMillis(),
    val metadata: MutableMap<String, Any> = mutableMapOf()
)

/**
 * 状态机引擎 - 核心实现
 */
class StateMachineEngine(
    private val config: StateMachineConfig = StateMachineConfig()
) {
    companion object {
        private const val TAG = "StateMachineEngine"
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val mutex = Mutex()
    
    // 状态存储
    private val taskContexts = mutableMapOf<Long, TaskContext>()
    private val stateHandlers = mutableMapOf<TaskState, StateHandler>()
    
    // 状态转换规则（白名单）
    private val allowedTransitions = mutableMapOf<TaskState, List<TaskState>>()
    
    // 状态事件监听器
    private val listeners = mutableListOf<(StateTransition) -> Unit>()
    
    // 当前状态 Flow（用于 UI 观察）
    private val stateFlows = mutableMapOf<Long, MutableStateFlow<TaskState>>()

    init {
        initTransitions()
    }

    /**
     * 初始化状态转换规则
     */
    private fun initTransitions() {
        allowedTransitions[TaskState.INIT] = listOf(TaskState.PRECHECK, TaskState.FAILED)
        allowedTransitions[TaskState.PRECHECK] = listOf(
            TaskState.WAITING_OPEN,
            TaskState.PAGE_READY,
            TaskState.RETRY_PENDING,
            TaskState.FAILED
        )
        allowedTransitions[TaskState.WAITING_OPEN] = listOf(
            TaskState.PAGE_READY,
            TaskState.RETRY_PENDING,
            TaskState.FAILED
        )
        allowedTransitions[TaskState.PAGE_READY] = listOf(
            TaskState.QUEUING,
            TaskState.SELECTING,
            TaskState.CONFIRMING,
            TaskState.MANUAL_TAKEOVER,
            TaskState.RETRY_PENDING
        )
        allowedTransitions[TaskState.QUEUING] = listOf(
            TaskState.SELECTING,
            TaskState.CONFIRMING,
            TaskState.MANUAL_TAKEOVER,
            TaskState.RETRY_PENDING
        )
        allowedTransitions[TaskState.SELECTING] = listOf(
            TaskState.CONFIRMING,
            TaskState.MANUAL_TAKEOVER,
            TaskState.RETRY_PENDING
        )
        allowedTransitions[TaskState.CONFIRMING] = listOf(
            TaskState.SUCCESS,
            TaskState.MANUAL_TAKEOVER,
            TaskState.RETRY_PENDING
        )
        allowedTransitions[TaskState.MANUAL_TAKEOVER] = listOf(
            TaskState.PAGE_READY,
            TaskState.SELECTING,
            TaskState.CONFIRMING,
            TaskState.FAILED
        )
        allowedTransitions[TaskState.RETRY_PENDING] = listOf(
            TaskState.PAGE_READY,
            TaskState.SELECTING,
            TaskState.CONFIRMING,
            TaskState.MANUAL_TAKEOVER,
            TaskState.FAILED
        )
        // 终止状态无转出
        allowedTransitions[TaskState.SUCCESS] = emptyList()
        allowedTransitions[TaskState.FAILED] = emptyList()
    }

    /**
     * 注册状态处理器
     */
    fun registerHandler(state: TaskState, handler: StateHandler) {
        scope.launch {
            mutex.withLock {
                stateHandlers[state] = handler
                Log.d(TAG, "注册状态处理器：${state.name}")
            }
        }
    }

    /**
     * 添加状态事件监听器
     */
    fun addListener(listener: (StateTransition) -> Unit) {
        scope.launch {
            mutex.withLock {
                listeners.add(listener)
            }
        }
    }

    /**
     * 启动新任务
     */
    suspend fun startTask(task: TicketTask): TaskContext {
        return mutex.withLock {
            val runId = generateRunId(task.id)
            val context = TaskContext(
                task = task,
                runId = runId,
                currentState = TaskState.INIT
            )
            taskContexts[task.id] = context
            
            // 创建状态 Flow
            val stateFlow = MutableStateFlow(TaskState.INIT)
            stateFlows[task.id] = stateFlow
            
            Log.d(TAG, "任务启动：taskId=${task.id}, runId=$runId, state=INIT")
            context
        }
    }

    /**
     * 状态转换（核心方法）
     */
    suspend fun transitionTo(taskId: Long, toState: TaskState, reason: String = ""): Boolean {
        return mutex.withLock {
            val context = taskContexts[taskId]
            if (context == null) {
                Log.e(TAG, "任务不存在：$taskId")
                return@withLock false
            }

            val fromState = context.currentState
            
            // 检查转换是否合法
            if (!canTransition(fromState, toState)) {
                Log.e(TAG, "非法状态转换：$fromState -> $toState")
                return@withLock false
            }

            // 执行退出回调
            stateHandlers[fromState]?.onExit(context)

            // 更新状态
            context.currentState = toState
            stateFlows[taskId]?.value = toState

            // 执行进入回调
            stateHandlers[toState]?.onEnter(context)

            // 触发事件
            val transition = StateTransition(
                taskId = taskId,
                fromState = fromState,
                toState = toState,
                reason = reason
            )
            
            listeners.forEach { listener ->
                try {
                    listener(transition)
                } catch (e: Exception) {
                    Log.e(TAG, "监听器异常：${e.message}")
                }
            }

            Log.d(TAG, "状态转换：$fromState -> $toState (原因：$reason)")
            true
        }
    }

    /**
     * 检查状态转换是否合法
     */
    fun canTransition(fromState: TaskState, toState: TaskState): Boolean {
        return allowedTransitions[fromState]?.contains(toState) == true
    }

    /**
     * 获取当前状态
     */
    fun getCurrentState(taskId: Long): TaskState? {
        return taskContexts[taskId]?.currentState
    }

    /**
     * 获取状态 Flow（用于 UI 观察）
     */
    fun getStateFlow(taskId: Long): StateFlow<TaskState>? {
        return stateFlows[taskId]?.asStateFlow()
    }

    /**
     * 获取任务上下文
     */
    fun getContext(taskId: Long): TaskContext? {
        return taskContexts[taskId]
    }

    /**
     * 标记任务完成
     */
    suspend fun completeTask(taskId: Long, success: Boolean, reason: String = "") {
        val toState = if (success) TaskState.SUCCESS else TaskState.FAILED
        transitionTo(taskId, toState, reason)
        
        // 清理上下文（延迟清理，方便查询）
        scope.launch {
            kotlinx.coroutines.delay(60_000) // 保留 1 分钟
            mutex.withLock {
                taskContexts.remove(taskId)
                stateFlows.remove(taskId)
                Log.d(TAG, "任务清理完成：$taskId")
            }
        }
    }

    /**
     * 处理异常
     */
    suspend fun handleError(taskId: Long, errorType: ErrorType, message: String) {
        val context = getContext(taskId) ?: return
        
        when (errorType) {
            ErrorType.RISK_CHALLENGE -> {
                // 验证码/风控 → 人工接管
                transitionTo(taskId, TaskState.MANUAL_TAKEOVER, message)
            }
            ErrorType.SESSION_ERROR,
            ErrorType.PAGE_ERROR,
            ErrorType.NETWORK_ERROR,
            ErrorType.ACTION_ERROR -> {
                if (context.retryCount < config.maxRetryCount) {
                    context.retryCount++
                    transitionTo(taskId, TaskState.RETRY_PENDING, message)
                } else {
                    completeTask(taskId, false, "超过最大重试次数：$message")
                }
            }
            ErrorType.USER_ABORT -> {
                completeTask(taskId, false, "用户取消：$message")
            }
            else -> {
                completeTask(taskId, false, "未知错误：$message")
            }
        }
    }

    /**
     * 生成运行 ID
     */
    private fun generateRunId(taskId: Long): String {
        val timestamp = System.currentTimeMillis()
        return "run_${taskId}_${timestamp}"
    }

    /**
     * 获取所有活跃任务
     */
    fun getActiveTasks(): List<Long> {
        return taskContexts.keys.toList()
    }

    /**
     * 停止并清理任务
     */
    suspend fun stopTask(taskId: Long, reason: String = "用户停止") {
        completeTask(taskId, false, reason)
    }
}
