// ============================================================================
// 📦 模块：通知管理器
// 📅 创建日期：2026-03-23
// 📝 说明：统一通知管理，支持分级通知和人工接管请求
// 📚 参考：合规购票辅助系统_详细技术设计.md
// ============================================================================

package com.damaihelper.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.damaihelper.R
import com.damaihelper.ui.MainActivity

/**
 * 通知优先级
 */
enum class NotificationPriority {
    INFO,      // 普通信息
    WARNING,   // 警告
    CRITICAL   // 紧急通知
}

/**
 * 通知事件
 */
data class NotificationEvent(
    val priority: NotificationPriority,
    val title: String,
    val message: String,
    val timestamp: Long = System.currentTimeMillis(),
    val taskId: Long? = null
)

/**
 * 通知管理器
 */
class NotificationManager(private val context: Context) {
    companion object {
        private const val TAG = "NotificationManager"
        
        private const val CHANNEL_ID_INFO = "ticket_info"
        private const val CHANNEL_ID_WARNING = "ticket_warning"
        private const val CHANNEL_ID_CRITICAL = "ticket_critical"
        
        private const val NOTIFICATION_ID_BASE = 1000
    }

    private val notificationManager = NotificationManagerCompat.from(context)
    private val notificationEvents = mutableListOf<NotificationEvent>()
    private var notificationIdCounter = NOTIFICATION_ID_BASE

    init {
        createNotificationChannels()
    }

    /**
     * 创建通知渠道（Android 8.0+）
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 信息渠道
            val infoChannel = NotificationChannel(
                CHANNEL_ID_INFO,
                "购票信息",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "普通购票信息通知"
                enableLights(false)
                enableVibration(false)
            }

            // 警告渠道
            val warningChannel = NotificationChannel(
                CHANNEL_ID_WARNING,
                "购票警告",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "重要警告通知"
                enableLights(true)
                enableVibration(true)
            }

            // 紧急渠道
            val criticalChannel = NotificationChannel(
                CHANNEL_ID_CRITICAL,
                "购票紧急通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "需要立即处理的紧急通知"
                enableLights(true)
                enableVibration(true)
                setBypassDnd(true) // 可绕过勿扰模式
            }

            notificationManager.createNotificationChannel(infoChannel)
            notificationManager.createNotificationChannel(warningChannel)
            notificationManager.createNotificationChannel(criticalChannel)
            
            Log.d(TAG, "通知渠道创建完成")
        }
    }

    /**
     * 发送信息通知
     */
    fun notifyInfo(message: String) {
        sendNotification(
            priority = NotificationPriority.INFO,
            title = "购票助手",
            message = message
        )
    }

    /**
     * 发送警告通知
     */
    fun notifyWarning(message: String) {
        sendNotification(
            priority = NotificationPriority.WARNING,
            title = "⚠️ 警告",
            message = message
        )
    }

    /**
     * 发送紧急通知
     */
    fun notifyCritical(message: String) {
        sendNotification(
            priority = NotificationPriority.CRITICAL,
            title = "🚨 紧急",
            message = message
        )
    }

    /**
     * 请求人工接管
     */
    fun requestManualTakeover(taskId: Long, reason: String) {
        val message = "需要您处理：$reason"
        
        // 创建通知
        val notificationId = ++notificationIdCounter
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("TASK_ID", taskId)
            putExtra("ACTION", "MANUAL_TAKEOVER")
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID_CRITICAL)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 需要人工接管")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "已处理",
                pendingIntent
            )
            .build()
        
        // 检查通知权限
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                notificationManager.notify(notificationId, notification)
                Log.d(TAG, "人工接管通知已发送：$reason")
            } catch (e: SecurityException) {
                Log.e(TAG, "发送通知失败：${e.message}")
            }
        } else {
            Log.w(TAG, "没有通知权限，无法发送通知")
        }
        
        // 记录事件
        notificationEvents.add(
            NotificationEvent(
                priority = NotificationPriority.CRITICAL,
                title = "人工接管请求",
                message = reason,
                taskId = taskId
            )
        )
    }

    /**
     * 发送通知（通用方法）
     */
    private fun sendNotification(
        priority: NotificationPriority,
        title: String,
        message: String
    ) {
        val channelId = when (priority) {
            NotificationPriority.INFO -> CHANNEL_ID_INFO
            NotificationPriority.WARNING -> CHANNEL_ID_WARNING
            NotificationPriority.CRITICAL -> CHANNEL_ID_CRITICAL
        }
        
        val notificationId = ++notificationIdCounter
        
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(getIconForPriority(priority))
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(getPriorityValue(priority))
            .setAutoCancel(true)
            .setWhen(System.currentTimeMillis())
        
        // 根据优先级设置额外属性
        when (priority) {
            NotificationPriority.CRITICAL -> {
                builder.setCategory(NotificationCompat.CATEGORY_EVENT)
                builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            }
            NotificationPriority.WARNING -> {
                builder.setCategory(NotificationCompat.CATEGORY_REMINDER)
            }
            NotificationPriority.INFO -> {
                // 普通信息无需额外设置
            }
        }
        
        val notification = builder.build()
        
        // 检查通知权限
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            try {
                notificationManager.notify(notificationId, notification)
                Log.d(TAG, "通知已发送：[$priority] $title - $message")
            } catch (e: SecurityException) {
                Log.e(TAG, "发送通知失败：${e.message}")
            }
        } else {
            Log.w(TAG, "没有通知权限，无法发送通知")
        }
        
        // 记录事件
        notificationEvents.add(
            NotificationEvent(
                priority = priority,
                title = title,
                message = message
            )
        )
    }

    /**
     * 获取通知图标
     */
    private fun getIconForPriority(priority: NotificationPriority): Int {
        return when (priority) {
            NotificationPriority.CRITICAL -> android.R.drawable.ic_dialog_alert
            NotificationPriority.WARNING -> android.R.drawable.ic_dialog_info
            NotificationPriority.INFO -> android.R.drawable.ic_dialog_email
        }
    }

    /**
     * 获取通知优先级值
     */
    private fun getPriorityValue(priority: NotificationPriority): Int {
        return when (priority) {
            NotificationPriority.CRITICAL -> NotificationCompat.PRIORITY_HIGH
            NotificationPriority.WARNING -> NotificationCompat.PRIORITY_DEFAULT
            NotificationPriority.INFO -> NotificationCompat.PRIORITY_LOW
        }
    }

    /**
     * 获取通知历史
     */
    fun getNotificationHistory(): List<NotificationEvent> {
        return notificationEvents.toList()
    }

    /**
     * 清除通知历史
     */
    fun clearHistory() {
        notificationEvents.clear()
        Log.d(TAG, "通知历史已清除")
    }

    /**
     * 取消所有通知
     */
    fun cancelAllNotifications() {
        notificationManager.cancelAll()
        Log.d(TAG, "所有通知已取消")
    }

    /**
     * 检查通知权限
     */
    fun hasNotificationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 获取通知统计
     */
    fun getStats(): Map<String, Int> {
        return mapOf(
            "total" to notificationEvents.size,
            "info" to notificationEvents.count { it.priority == NotificationPriority.INFO },
            "warning" to notificationEvents.count { it.priority == NotificationPriority.WARNING },
            "critical" to notificationEvents.count { it.priority == NotificationPriority.CRITICAL }
        )
    }
}
