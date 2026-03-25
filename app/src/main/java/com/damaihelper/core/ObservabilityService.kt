// ============================================================================
// 📦 模块：观测服务
// 📅 创建日期：2026-03-23
// 📝 说明：负责日志记录、事件追踪、截图存储和复盘数据生成
// 📚 参考：合规购票辅助系统_详细技术设计.md
// ============================================================================

package com.damaihelper.core

import android.content.Context
import android.util.Log
import com.damaihelper.model.TicketTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 事件类型枚举
 */
enum class EventType {
    // 任务生命周期
    TASK_START,
    TASK_STOP,
    TASK_PAUSE,
    TASK_RESUME,
    
    // 状态转换
    STATE_ENTER,
    STATE_EXIT,
    
    // 动作
    ACTION_START,
    ACTION_END,
    
    // 错误
    ERROR_RAISED,
    
    // 恢复
    RECOVERY_START,
    RECOVERY_END,
    
    // 用户操作
    USER_TAKEOVER,
    
    // 任务完成
    TASK_FINISHED,
    
    // 其他
    HEARTBEAT,
    PRECHECK_ITEM,
    CUSTOM
}

/**
 * 事件记录
 */
data class TaskEvent(
    val eventId: String,
    val taskId: Long,
    val runId: String,
    val timestamp: Long,
    val eventType: EventType,
    val state: TaskState? = null,
    val actionName: String? = null,
    val result: String? = null,
    val message: String,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * 任务运行摘要
 */
data class TaskRunSummary(
    val runId: String,
    val taskId: Long,
    val taskName: String,
    val startTime: Long,
    val endTime: Long?,
    val duration: Long?,
    val result: String,
    val failureReason: String?,
    val stateSequence: List<TaskState>,
    val eventCount: Int,
    val manualTakeoverCount: Int,
    val snapshotPaths: List<String>
)

/**
 * 观测服务
 */
class ObservabilityService(private val context: Context) {
    companion object {
        private const val TAG = "ObservabilityService"
        private const val LOG_DIR_NAME = "task_logs"
        private const val SNAPSHOT_DIR_NAME = "snapshots"
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 事件存储
    private val eventLogs = mutableMapOf<String, MutableList<TaskEvent>>() // runId -> events
    private val stateSequences = mutableMapOf<String, MutableList<TaskState>>() // runId -> states
    
    // 截图路径
    private val snapshotPaths = mutableMapOf<String, MutableList<String>>() // runId -> paths
    
    // 运行信息
    private val taskRuns = mutableMapOf<String, TaskRunInfo>()
    
    // 实时事件 Flow（用于 UI 订阅）
    private val liveEvents = MutableStateFlow<TaskEvent?>(null)

    // 目录
    private val logDir: File
    private val snapshotDir: File

    init {
        val baseDir = File(context.filesDir, LOG_DIR_NAME)
        logDir = baseDir
        snapshotDir = File(baseDir, SNAPSHOT_DIR_NAME)
        
        // 确保目录存在
        logDir.mkdirs()
        snapshotDir.mkdirs()
        
        Log.d(TAG, "观测服务初始化完成，日志目录：${logDir.absolutePath}")
    }

    /**
     * 开始任务运行
     */
    fun startTaskRun(task: TicketTask) {
        val runId = generateRunId(task.id)
        
        taskRuns[runId] = TaskRunInfo(
            runId = runId,
            taskId = task.id,
            taskName = task.name,
            startTime = System.currentTimeMillis()
        )
        
        eventLogs[runId] = mutableListOf()
        stateSequences[runId] = mutableListOf()
        snapshotPaths[runId] = mutableListOf()
        
        Log.d(TAG, "任务运行开始：runId=$runId, taskId=${task.id}")
    }

    /**
     * 记录事件
     */
    fun recordEvent(
        taskId: Long,
        eventType: EventType,
        message: String,
        state: TaskState? = null,
        actionName: String? = null,
        result: String? = null,
        metadata: Map<String, String> = emptyMap()
    ) {
        val runId = getActiveRunId(taskId)
        if (runId == null) {
            Log.w(TAG, "找不到活跃的运行记录，无法记录事件：taskId=$taskId")
            return
        }
        
        val event = TaskEvent(
            eventId = generateEventId(),
            taskId = taskId,
            runId = runId,
            timestamp = System.currentTimeMillis(),
            eventType = eventType,
            state = state,
            actionName = actionName,
            result = result,
            message = message,
            metadata = metadata
        )
        
        // 添加到日志
        eventLogs[runId]?.add(event)
        
        // 更新状态序列
        if (state != null) {
            stateSequences[runId]?.add(state)
        }
        
        // 发布实时事件
        liveEvents.value = event
        
        // 异步写入磁盘
        scope.launch {
            writeEventToDisk(event)
        }
        
        // 详细日志
        Log.d(TAG, "事件记录：[${eventType}] ${message}")
    }

    /**
     * 保存截图
     */
    fun saveSnapshot(taskId: Long, tag: String, imageData: ByteArray) {
        val runId = getActiveRunId(taskId)
        if (runId == null) {
            Log.w(TAG, "找不到活跃的运行记录，无法保存截图：taskId=$taskId")
            return
        }
        
        scope.launch {
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
                val filename = "${tag}_${timestamp}.png"
                val file = File(snapshotDir, filename)
                
                withContext(Dispatchers.IO) {
                    file.writeBytes(imageData)
                }
                
                snapshotPaths[runId]?.add(file.absolutePath)
                
                Log.d(TAG, "截图已保存：${file.absolutePath}")
                
                // 记录事件
                recordEvent(
                    taskId = taskId,
                    eventType = EventType.CUSTOM,
                    message = "截图保存：$tag",
                    metadata = mapOf("snapshot_path" to file.absolutePath)
                )
            } catch (e: Exception) {
                Log.e(TAG, "保存截图失败：${e.message}")
            }
        }
    }

    /**
     * 生成运行摘要
     */
    fun generateRunSummary(runId: String): TaskRunSummary? {
        val runInfo = taskRuns[runId]
        if (runInfo == null) {
            Log.w(TAG, "找不到运行信息：$runId")
            return null
        }
        
        val events = eventLogs[runId] ?: emptyList()
        val states = stateSequences[runId] ?: emptyList()
        val snapshots = snapshotPaths[runId] ?: emptyList()
        
        // 计算持续时间
        val endTime = events.lastOrNull()?.timestamp
        val duration = if (endTime != null) endTime - runInfo.startTime else null
        
        // 判断结果
        val lastState = states.lastOrNull()
        val result = when (lastState) {
            TaskState.SUCCESS -> "SUCCESS"
            TaskState.FAILED -> "FAILED"
            else -> "IN_PROGRESS"
        }
        
        // 失败原因
        val failureReason = events
            .filter { it.eventType == EventType.ERROR_RAISED }
            .lastOrNull()?.message
        
        // 人工接管次数
        val manualTakeoverCount = events.count { it.eventType == EventType.USER_TAKEOVER }
        
        return TaskRunSummary(
            runId = runId,
            taskId = runInfo.taskId,
            taskName = runInfo.taskName,
            startTime = runInfo.startTime,
            endTime = endTime,
            duration = duration,
            result = result,
            failureReason = failureReason,
            stateSequence = states,
            eventCount = events.size,
            manualTakeoverCount = manualTakeoverCount,
            snapshotPaths = snapshots
        )
    }

    /**
     * 获取任务的所有运行记录
     */
    fun getTaskRuns(taskId: Long): List<TaskRunSummary> {
        return taskRuns
            .filter { it.value.taskId == taskId }
            .mapNotNull { generateRunSummary(it.key) }
            .sortedByDescending { it.startTime }
    }

    /**
     * 获取实时事件 Flow
     */
    fun getLiveEvents(): StateFlow<TaskEvent?> {
        return liveEvents.asStateFlow()
    }

    /**
     * 获取指定运行的事件列表
     */
    fun getEvents(runId: String): List<TaskEvent> {
        return eventLogs[runId]?.toList() ?: emptyList()
    }

    /**
     * 获取指定运行的状态序列
     */
    fun getStateSequence(runId: String): List<TaskState> {
        return stateSequences[runId]?.toList() ?: emptyList()
    }

    /**
     * 导出日志文件
     */
    suspend fun exportLogs(runId: String): File? {
        return withContext(Dispatchers.IO) {
            try {
                val events = eventLogs[runId] ?: return@withContext null
                val summary = generateRunSummary(runId)
                
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
                val filename = "run_${runId}_${timestamp}.log"
                val file = File(logDir, filename)
                
                file.bufferedWriter().use { writer ->
                    // 写入摘要
                    writer.appendLine("=== 任务运行摘要 ===")
                    writer.appendLine("运行 ID: ${summary?.runId}")
                    writer.appendLine("任务名称：${summary?.taskName}")
                    writer.appendLine("开始时间：${formatTimestamp(summary?.startTime)}")
                    writer.appendLine("结束时间：${formatTimestamp(summary?.endTime)}")
                    writer.appendLine("持续时间：${formatDuration(summary?.duration)}")
                    writer.appendLine("结果：${summary?.result}")
                    writer.appendLine("失败原因：${summary?.failureReason ?: "无"}")
                    writer.appendLine("事件数量：${summary?.eventCount}")
                    writer.appendLine("人工接管次数：${summary?.manualTakeoverCount}")
                    writer.appendLine("")
                    
                    // 写入状态序列
                    writer.appendLine("=== 状态序列 ===")
                    summary?.stateSequence?.forEachIndexed { index, state ->
                        writer.appendLine("${index + 1}. ${state.name} (${state.description})")
                    }
                    writer.appendLine("")
                    
                    // 写入详细事件
                    writer.appendLine("=== 详细事件 ===")
                    events.forEach { event ->
                        writer.appendLine("[${formatTimestamp(event.timestamp)}] ")
                        writer.appendLine("  类型：${event.eventType}")
                        writer.appendLine("  状态：${event.state?.name ?: "无"}")
                        writer.appendLine("  消息：${event.message}")
                        if (event.actionName != null) {
                            writer.appendLine("  动作：${event.actionName}")
                        }
                        if (event.result != null) {
                            writer.appendLine("  结果：${event.result}")
                        }
                        writer.appendLine("")
                    }
                }
                
                Log.d(TAG, "日志已导出：${file.absolutePath}")
                file
            } catch (e: Exception) {
                Log.e(TAG, "导出日志失败：${e.message}")
                null
            }
        }
    }

    /**
     * 清理旧日志（保留最近 7 天）
     */
    fun cleanupOldLogs(daysToKeep: Int = 7) {
        scope.launch {
            try {
                val cutoffTime = System.currentTimeMillis() - daysToKeep * 24 * 60 * 60 * 1000
                
                logDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < cutoffTime) {
                        file.delete()
                        Log.d(TAG, "删除旧日志：${file.name}")
                    }
                }
                
                snapshotDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < cutoffTime) {
                        file.delete()
                        Log.d(TAG, "删除旧截图：${file.name}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "清理日志失败：${e.message}")
            }
        }
    }

    // ==================== 辅助方法 ====================

    private fun getActiveRunId(taskId: Long): String? {
        return taskRuns.entries
            .find { it.value.taskId == taskId && it.value.endTime == null }
            ?.key
    }

    private fun generateRunId(taskId: Long): String {
        val timestamp = System.currentTimeMillis()
        return "run_${taskId}_${timestamp}"
    }

    private fun generateEventId(): String {
        return "evt_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}"
    }

    private suspend fun writeEventToDisk(event: TaskEvent) {
        withContext(Dispatchers.IO) {
            try {
                val filename = "events_${event.runId}.jsonl"
                val file = File(logDir, filename)
                
                // 追加写入
                file.appendText("${eventToJson(event)}\n")
            } catch (e: Exception) {
                Log.e(TAG, "写入事件失败：${e.message}")
            }
        }
    }

    private fun eventToJson(event: TaskEvent): String {
        // 简化的 JSON 序列化（实际项目建议使用 Gson 或 kotlinx.serialization）
        return buildString {
            append("{")
            append("\"eventId\":\"${event.eventId}\",")
            append("\"taskId\":${event.taskId},")
            append("\"runId\":\"${event.runId}\",")
            append("\"timestamp\":${event.timestamp},")
            append("\"eventType\":\"${event.eventType}\",")
            append("\"state\":\"${event.state?.name}\",")
            append("\"message\":\"${event.message.replace("\"", "\\\"")}\"")
            append("}")
        }
    }

    private fun formatTimestamp(timestamp: Long?): String {
        return if (timestamp == null) "N/A"
        else SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(Date(timestamp))
    }

    private fun formatDuration(duration: Long?): String {
        return if (duration == null) "N/A"
        else {
            val seconds = duration / 1000
            val minutes = seconds / 60
            val hours = minutes / 60
            
            when {
                hours > 0 -> "${hours}h ${minutes % 60}m"
                minutes > 0 -> "${minutes}m ${seconds % 60}s"
                else -> "${seconds}s"
            }
        }
    }

    /**
     * 获取统计信息
     */
    fun getStats(): Map<String, Any> {
        val totalRuns = taskRuns.size
        val activeRuns = taskRuns.count { it.value.endTime == null }
        val totalEvents = eventLogs.values.sumOf { it.size }
        val totalSnapshots = snapshotPaths.values.sumOf { it.size }
        val logFileCount = logDir.listFiles()?.size ?: 0
        
        return mapOf(
            "totalRuns" to totalRuns,
            "activeRuns" to activeRuns,
            "totalEvents" to totalEvents,
            "totalSnapshots" to totalSnapshots,
            "logFileCount" to logFileCount
        )
    }
}

/**
 * 任务运行信息（内部使用）
 */
private data class TaskRunInfo(
    val runId: String,
    val taskId: Long,
    val taskName: String,
    val startTime: Long,
    var endTime: Long? = null
)
