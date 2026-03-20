package com.damaihelper.captcha

import android.graphics.Bitmap
import android.util.Log
// 修正：导入 TicketGrabbingAccessibilityService
import com.damaihelper.service.CaptchaRecognitionService
import com.damaihelper.service.TicketGrabbingAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 验证码识别协调服务
 * ...
 */
// 修正：构造函数需要接收 TicketGrabbingAccessibilityService
class CaptchaCoordinatorService(private val service: TicketGrabbingAccessibilityService) {

    companion object {
        private const val TAG = "CaptchaCoordinator"
    }

    // 修正：将 'service' 实例传递给 OcrService
    private val ocrService = OcrService(service)
    private val remoteService = CaptchaRecognitionService()

    // 统计信息
    private var totalAttempts = 0
    private var localSuccessCount = 0
    private var remoteSuccessCount = 0
    private var totalFailures = 0

    /**
     * 智能识别验证码
     * ...
     */
    suspend fun recognizeCaptcha(
        captchaBitmap: Bitmap,
        forceRemote: Boolean = false
    ): CaptchaResult {
        return withContext(Dispatchers.IO) {
            totalAttempts++
            val startTime = System.currentTimeMillis()

            Log.d(TAG, "========================================")
            Log.d(TAG, "开始验证码识别 (第${totalAttempts}次)")
            Log.d(TAG, "强制使用远程: $forceRemote")
            Log.d(TAG, "========================================")

            // 如果不强制使用远程，先尝试本地 OCR
            if (!forceRemote) {
                val localResult = tryLocalOcr(captchaBitmap) // 修正后的函数

                if (localResult.success) {
                    val duration = System.currentTimeMillis() - startTime
                    localSuccessCount++

                    Log.i(TAG, "✓ 本地OCR识别成功!")
                    Log.i(TAG, "识别文本: ${localResult.text}")
                    Log.i(TAG, "耗时: ${duration}ms")
                    printStatistics()

                    return@withContext localResult
                }

                Log.w(TAG, "本地OCR识别失败: ${localResult.message}")
                Log.i(TAG, "降级到付费服务...")
            }

            // 本地识别失败或强制使用远程，使用付费 API
            val remoteResult = tryRemoteApi(captchaBitmap)

            val duration = System.currentTimeMillis() - startTime

            if (remoteResult.success) {
                remoteSuccessCount++
                Log.i(TAG, "✓ 远程API识别成功!")
                Log.i(TAG, "使用服务: ${remoteResult.serviceName}")
                Log.i(TAG, "识别文本: ${remoteResult.text}")
                Log.i(TAG, "耗时: ${duration}ms")
            } else {
                totalFailures++
                Log.e(TAG, "✗ 所有识别方法均失败!")
                Log.e(TAG, "失败原因: ${remoteResult.message}")
            }

            printStatistics()

            return@withContext remoteResult
        }
    }

    /**
     * 尝试本地 OCR 识别
     */
    // 修正：由于 OcrService.kt 中禁用了 ML Kit 且 recognizeTextSmart 不存在，
    // 我们直接返回一个失败结果，以触发远程 API
    private suspend fun tryLocalOcr(bitmap: Bitmap): CaptchaResult {
        Log.d(TAG, ">>> 阶段1: 本地 OCR 识别 (暂时禁用)")

        return CaptchaResult(
            success = false,
            text = "",
            confidence = 0f,
            message = "本地OCR暂时禁用",
            method = RecognitionMethod.LOCAL_OCR,
            serviceName = "Google ML Kit"
        )
    }

    /**
     * 尝试远程付费 API 识别
     */
    private suspend fun tryRemoteApi(bitmap: Bitmap): CaptchaResult {
        Log.d(TAG, ">>> 阶段2: 远程付费 API 识别")

        return try {
            val remoteResult = remoteService.recognizeCaptcha(
                captchaBitmap = bitmap,
                captchaType = CaptchaRecognitionService.TYPE_NORMAL
            )

            // 确定使用的服务名称
            val serviceName = when {
                remoteResult.message.contains("超级鹰") -> "超级鹰"
                remoteResult.message.contains("云码") -> "云码"
                remoteResult.message.contains("2Captcha") -> "2Captcha"
                else -> "未知服务"
            }

            CaptchaResult(
                success = remoteResult.success,
                text = remoteResult.text,
                confidence = 1.0f, // 付费服务假设置信度为100%
                message = remoteResult.message,
                method = RecognitionMethod.REMOTE_API,
                serviceName = serviceName
            )
        } catch (e: Exception) {
            Log.e(TAG, "远程API异常", e)
            CaptchaResult(
                success = false,
                text = "",
                confidence = 0f,
                message = "远程API异常: ${e.message}",
                method = RecognitionMethod.REMOTE_API,
                serviceName = "API服务"
            )
        }
    }

    /**
     * 打印统计信息
     */
    private fun printStatistics() {
        val localRate = if (totalAttempts > 0) {
            (localSuccessCount * 100.0 / totalAttempts)
        } else 0.0

        val remoteRate = if (totalAttempts > 0) {
            (remoteSuccessCount * 100.0 / totalAttempts)
        } else 0.0

        val failureRate = if (totalAttempts > 0) {
            (totalFailures * 100.0 / totalAttempts)
        } else 0.0

        Log.d(TAG, "========== 识别统计 ==========")
        Log.d(TAG, "总尝试次数: $totalAttempts")
        Log.d(TAG, "本地OCR成功: $localSuccessCount (${String.format("%.1f", localRate)}%)")
        Log.d(TAG, "远程API成功: $remoteSuccessCount (${String.format("%.1f", remoteRate)}%)")
        Log.d(TAG, "完全失败: $totalFailures (${String.format("%.1f", failureRate)}%)")

        // 成本估算（假设远程API单价 ¥0.001/次）
        val estimatedCost = remoteSuccessCount * 0.001
        Log.d(TAG, "预估成本: ¥${String.format("%.3f", estimatedCost)}")
        Log.d(TAG, "=============================")
    }

    /**
     * 获取统计信息
     */
    fun getStatistics(): Statistics {
        return Statistics(
            totalAttempts = totalAttempts,
            localSuccessCount = localSuccessCount,
            remoteSuccessCount = remoteSuccessCount,
            totalFailures = totalFailures,
            localSuccessRate = if (totalAttempts > 0) {
                localSuccessCount.toFloat() / totalAttempts
            } else 0f,
            remoteSuccessRate = if (totalAttempts > 0) {
                remoteSuccessCount.toFloat() / totalAttempts
            } else 0f
        )
    }

    /**
     * 重置统计信息
     */
    fun resetStatistics() {
        totalAttempts = 0
        localSuccessCount = 0
        remoteSuccessCount = 0
        totalFailures = 0
        Log.d(TAG, "统计信息已重置")
    }

    /**
     * 清理资源
     */
    // 修正：取消注释 cleanup() 函数，以解决 TicketGrabbingAccessibilityService 中的错误
    fun cleanup() {
        // ocrService.cleanup() // OcrService.kt 中没有 cleanup()，保持注释
        Log.d(TAG, "协调器资源已清理")
    }

    /**
     * 识别方法枚举
     */
    enum class RecognitionMethod {
        LOCAL_OCR,      // 本地 OCR
        REMOTE_API      // 远程 API
    }

    /**
     * 验证码识别结果
     */
    data class CaptchaResult(
        val success: Boolean,           // 是否成功
        val text: String,               // 识别的文本
        val confidence: Float,          // 置信度 (0-1)
        val message: String,            // 描述信息
        val method: RecognitionMethod,  // 使用的方法
        val serviceName: String         // 服务名称
    )

    /**
     * 统计信息
     */
    data class Statistics(
        val totalAttempts: Int,         // 总尝试次数
        val localSuccessCount: Int,     // 本地成功次数
        val remoteSuccessCount: Int,    // 远程成功次数
        val totalFailures: Int,         // 失败次数
        val localSuccessRate: Float,    // 本地成功率
        val remoteSuccessRate: Float    // 远程成功率
    )
}