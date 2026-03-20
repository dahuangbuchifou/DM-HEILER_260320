package com.damaihelper.utils

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 人类行为模拟器 - 集成 A1/A2 反检测特性
 */
class HumanBehaviorSimulator {
    private val TAG = "HumanBehavior"

    companion object {
        // A1: 随机点击偏移范围（像素）
        // 增加偏移范围，模拟人手点击的不确定性
        private const val CLICK_OFFSET_MIN = -30
        private const val CLICK_OFFSET_MAX = 30

        // A2: 操作间隔随机化（毫秒）
        // 增加随机延迟范围，模拟人手操作的停顿
        private const val DELAY_MIN = 100L
        private const val DELAY_MAX = 400L

        // A6: 思考时间（毫秒）
        // 增加思考时间范围，模拟更长的用户思考和页面加载时间
        private const val THINKING_MIN = 800L
        private const val THINKING_MAX = 3500L
    }

    /**
     * 【A1 + A2】随机偏移点击（核心方法）
     * @param node 目标节点
     * @param service AccessibilityService 实例（用于 dispatchGesture）
     */
    suspend fun clickWithRandomOffset(
        node: AccessibilityNodeInfo,
        service: AccessibilityService
    ): Boolean {
        try {
            val rect = Rect()
            node.getBoundsInScreen(rect)

            if (rect.isEmpty) {
                Log.w(TAG, "节点区域为空，无法点击")
                return false
            }

            // A1: 计算随机偏移点
            val centerX = (rect.left + rect.right) / 2f
            val centerY = (rect.top + rect.bottom) / 2f

            val offsetX = Random.nextInt(CLICK_OFFSET_MIN, CLICK_OFFSET_MAX)
            val offsetY = Random.nextInt(CLICK_OFFSET_MIN, CLICK_OFFSET_MAX)

            val finalX = centerX + offsetX
            val finalY = centerY + offsetY

            // 确保点击点在屏幕内
            val clampedX = finalX.coerceIn(rect.left.toFloat(), rect.right.toFloat())
            val clampedY = finalY.coerceIn(rect.top.toFloat(), rect.bottom.toFloat())

            // 执行手势点击
            val success = performGestureClick(clampedX, clampedY, service)

            if (success) {
                Log.d(TAG, "[A1] 偏移点击: (${offsetX}, ${offsetY})")
            }

            // A2: 随机延迟
            val randomDelay = Random.nextLong(DELAY_MIN, DELAY_MAX)
            delay(randomDelay)

            return success
        } catch (e: Exception) {
            Log.e(TAG, "点击失败", e)
            return false
        }
    }

    /**
     * 执行手势点击
     */
    private fun performGestureClick(
        x: Float,
        y: Float,
        service: AccessibilityService
    ): Boolean {
        val path = Path()
        path.moveTo(x, y)

        // 模拟真实按压时长，增加随机性
        val duration = Random.nextLong(80, 250)

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        var success = false
        service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                success = true
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "手势被取消")
            }
        }, null)

        return success
    }

    /**
     * 【A6】模拟思考时间
     */
    suspend fun simulateThinkingTime(minMs: Long = THINKING_MIN, maxMs: Long = THINKING_MAX) {
        val thinkingTime = Random.nextLong(minMs, maxMs)
        Log.d(TAG, "[A6] 模拟思考: ${thinkingTime}ms")
        delay(thinkingTime)
    }

    /**
     * 【A2】随机延迟（返回延迟时间）
     * 用于兼容旧代码
     */
    fun simulateRandomDelay(minMs: Long, maxMs: Long): Long {
        return Random.nextLong(minMs, maxMs)
    }

    /**
     * 普通点击（不带偏移，用于兼容）
     */
    /**
     * 普通点击（不带偏移，用于兼容）
     */
    fun performClick(node: AccessibilityNodeInfo): Boolean {
        return try {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)  // ← 应该是这样
        } catch (e: Exception) {
            Log.e(TAG, "普通点击失败", e)
            false
        }
    }

    /**
     * 输入文本（带随机延迟）
     */
    suspend fun inputText(node: AccessibilityNodeInfo, text: String): Boolean {
        return try {
            val args = android.os.Bundle()
            args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                text
            )

            val success = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

            if (success) {
                // A2: 输入后随机延迟
                delay(Random.nextLong(200, 500))
            }

            success
        } catch (e: Exception) {
            Log.e(TAG, "输入文本失败", e)
            false
        }
    }
}