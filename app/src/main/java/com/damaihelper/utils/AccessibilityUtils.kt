package com.damaihelper.utils

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log

object AccessibilityUtils {

    private const val TAG = "AccessibilityUtils"

    /**
     * 检查无障碍服务是否已启用
     *
     * @param context 上下文
     * @return true 如果服务已启用，false 否则
     */
    fun isServiceEnabled(context: Context): Boolean {
        // 构建完整的服务名称
        val serviceName = "${context.packageName}/com.damaihelper.service.TicketGrabbingAccessibilityService"

        Log.d(TAG, "检查无障碍服务状态")
        Log.d(TAG, "包名: ${context.packageName}")
        Log.d(TAG, "服务名: $serviceName")

        try {
            // 获取已启用的无障碍服务列表
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )

            Log.d(TAG, "已启用的无障碍服务: $enabledServices")

            // 检查服务列表
            if (enabledServices.isNullOrEmpty()) {
                Log.w(TAG, "无障碍服务列表为空")
                return false
            }

            // 分割服务列表并检查
            val servicesList = enabledServices.split(":")
            for (service in servicesList) {
                Log.d(TAG, "检查服务: $service")

                // 完全匹配或部分匹配
                if (service.equals(serviceName, ignoreCase = true) ||
                    service.contains("TicketGrabbingAccessibilityService", ignoreCase = true)) {
                    Log.i(TAG, "✓ 无障碍服务已启用")
                    return true
                }
            }

            Log.w(TAG, "✗ 无障碍服务未启用")
            return false

        } catch (e: Exception) {
            Log.e(TAG, "检查无障碍服务状态失败", e)
            return false
        }
    }

    /**
     * 检查无障碍服务是否正在运行（通过实例检查）
     */
    fun isServiceRunning(): Boolean {
        val instance = com.damaihelper.service.TicketGrabbingAccessibilityService.getInstance()
        val isRunning = instance != null

        Log.d(TAG, "服务实例检查: ${if (isRunning) "运行中" else "未运行"}")
        return isRunning
    }

    /**
     * 获取无障碍服务的详细状态信息（用于调试）
     */
    fun getServiceStatusInfo(context: Context): String {
        val serviceName = "${context.packageName}/com.damaihelper.service.TicketGrabbingAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: "无"

        val isEnabled = isServiceEnabled(context)
        val isRunning = isServiceRunning()

        return buildString {
            appendLine("=== 无障碍服务状态 ===")
            appendLine("服务名称: $serviceName")
            appendLine("已启用列表: $enabledServices")
            appendLine("是否启用: $isEnabled")
            appendLine("是否运行: $isRunning")
            appendLine("=====================")
        }
    }
}