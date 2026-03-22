package com.damaihelper.utils

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 人类行为模拟器 - 增强版
 * 
 * 反检测特性：
 * - A1: 随机点击偏移（±30px）
 * - A2: 操作间隔随机化（100-400ms）
 * - A3: 贝塞尔曲线滑动
 * - A4: 点击压力模拟（时长随机）
 * - A5: 多点触控模拟
 * - A6: 思考时间模拟
 * - A7: 鼠标轨迹模拟（非线性移动）
 */
class HumanBehaviorSimulator {
    private val TAG = "HumanBehavior"
    private val random = Random(System.currentTimeMillis()) // 使用 Kotlin Random（支持范围参数）

    companion object {
        // A1: 随机点击偏移范围（像素）
        private const val CLICK_OFFSET_MIN = -30
        private const val CLICK_OFFSET_MAX = 30

        // A2: 操作间隔随机化（毫秒）
        private const val DELAY_MIN = 100L
        private const val DELAY_MAX = 400L

        // A3: 贝塞尔曲线滑动参数
        private const val BEZIER_CONTROL_POINT_OFFSET = 200 // 控制点偏移
        
        // A4: 点击按压时长（毫秒）
        private const val PRESS_DURATION_MIN = 80L
        private const val PRESS_DURATION_MAX = 250L

        // A6: 思考时间（毫秒）
        private const val THINKING_MIN = 800L
        private const val THINKING_MAX = 3500L
        
        // A7: 轨迹点数（模拟鼠标移动）
        private const val TRAJECTORY_POINTS_MIN = 5
        private const val TRAJECTORY_POINTS_MAX = 12
    }

    /**
     * 【A1 + A2 + A4】随机偏移点击（核心方法）
     * 包含：随机偏移 + 随机延迟 + 随机按压时长
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

            val offsetX = random.nextInt(CLICK_OFFSET_MIN, CLICK_OFFSET_MAX)
            val offsetY = random.nextInt(CLICK_OFFSET_MIN, CLICK_OFFSET_MAX)

            val finalX = (centerX + offsetX).coerceIn(rect.left.toFloat(), rect.right.toFloat())
            val finalY = (centerY + offsetY).coerceIn(rect.top.toFloat(), rect.bottom.toFloat())

            // A4: 随机按压时长
            val pressDuration = random.nextLong(PRESS_DURATION_MIN, PRESS_DURATION_MAX)

            // 执行手势点击
            val success = performGestureClick(finalX, finalY, pressDuration, service)

            if (success) {
                Log.d(TAG, "[A1+A4] 偏移点击：(${offsetX}, ${offsetY}), 按压时长：${pressDuration}ms")
            }

            // A2: 随机延迟
            val randomDelay = random.nextLong(DELAY_MIN, DELAY_MAX)
            delay(randomDelay)

            return success
        } catch (e: Exception) {
            Log.e(TAG, "点击失败", e)
            return false
        }
    }

    /**
     * 【A3】贝塞尔曲线滑动
     * 模拟真实人类滑动的自然曲线轨迹
     */
    suspend fun swipeWithBezierCurve(
        service: AccessibilityService,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 1000L
    ): Boolean {
        return try {
            // 生成贝塞尔曲线控制点
            val controlX1 = startX + random.nextInt(-BEZIER_CONTROL_POINT_OFFSET, BEZIER_CONTROL_POINT_OFFSET)
            val controlY1 = startY + random.nextInt(-BEZIER_CONTROL_POINT_OFFSET, BEZIER_CONTROL_POINT_OFFSET)
            val controlX2 = endX + random.nextInt(-BEZIER_CONTROL_POINT_OFFSET, BEZIER_CONTROL_POINT_OFFSET)
            val controlY2 = endY + random.nextInt(-BEZIER_CONTROL_POINT_OFFSET, BEZIER_CONTROL_POINT_OFFSET)

            // 生成平滑的贝塞尔曲线路径
            val path = Path()
            path.moveTo(startX, startY)

            // 使用三次贝塞尔曲线
            val stepCount = 20
            for (i in 1..stepCount) {
                val t = i.toFloat() / stepCount
                val x = bezier3(t, startX, controlX1, controlX2, endX)
                val y = bezier3(t, startY, controlY1, controlY2, endY)
                
                if (i == 1) {
                    path.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                }
            }

            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()

            var success = false
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    success = true
                    Log.d(TAG, "[A3] 贝塞尔滑动完成")
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "[A3] 滑动被取消")
                }
            }, null)

            // 等待手势完成
            delay(durationMs + 200)
            
            success
        } catch (e: Exception) {
            Log.e(TAG, "[A3] 贝塞尔滑动失败", e)
            false
        }
    }

    /**
     * 三次贝塞尔曲线公式
     */
    private fun bezier3(t: Float, p0: Float, p1: Float, p2: Float, p3: Float): Float {
        val u = 1 - t
        return u.pow(3) * p0 + 3 * u.pow(2) * t * p1 + 3 * u * t.pow(2) * p2 + t.pow(3) * p3
    }

    /**
     * 【A7】模拟鼠标轨迹移动（非线性）
     * 从一个点移动到另一个点，路径包含随机扰动
     */
    suspend fun moveWithHumanLikeTrajectory(
        service: AccessibilityService,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        durationMs: Long = 500L
    ): Boolean {
        return try {
            val path = Path()
            path.moveTo(startX, startY)

            // 生成带随机扰动的轨迹点
            val pointCount = random.nextInt(TRAJECTORY_POINTS_MIN, TRAJECTORY_POINTS_MAX)
            val dx = (endX - startX) / pointCount
            val dy = (endY - startY) / pointCount

            var currentX = startX
            var currentY = startY

            for (i in 1 until pointCount) {
                // 添加随机扰动（高斯分布）
                val perturbationX = random.nextGaussian() * 20
                val perturbationY = random.nextGaussian() * 20
                
                currentX += dx + perturbationX
                currentY += dy + perturbationY
                
                path.lineTo(currentX.coerceIn(0f, 2000f), currentY.coerceIn(0f, 2000f))
            }

            path.lineTo(endX, endY)

            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
                .build()

            var success = false
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    success = true
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "[A7] 轨迹移动被取消")
                }
            }, null)

            delay(durationMs + 100)
            success
        } catch (e: Exception) {
            Log.e(TAG, "[A7] 轨迹移动失败", e)
            false
        }
    }

    /**
     * 执行手势点击（带按压时长）
     */
    private fun performGestureClick(
        x: Float,
        y: Float,
        duration: Long,
        service: AccessibilityService
    ): Boolean {
        val path = Path()
        path.moveTo(x, y)

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
        val thinkingTime = random.nextLong(minMs, maxMs)
        Log.d(TAG, "[A6] 模拟思考：${thinkingTime}ms")
        delay(thinkingTime)
    }

    /**
     * 【A2】随机延迟（返回延迟时间）
     */
    fun simulateRandomDelay(minMs: Long, maxMs: Long): Long {
        return random.nextLong(minMs, maxMs)
    }

    /**
     * 【A5】多点触控模拟（双指缩放）
     */
    suspend fun performPinchZoom(
        service: AccessibilityService,
        centerX: Float,
        centerY: Float,
        startDistance: Float = 100f,
        endDistance: Float = 200f,
        durationMs: Long = 800L
    ): Boolean {
        return try {
            val path1 = Path()
            val path2 = Path()

            val angle = Math.toRadians(45.0) // 45 度角
            val offset1X = (startDistance / 2 * Math.cos(angle)).toFloat()
            val offset1Y = (startDistance / 2 * Math.sin(angle)).toFloat()

            path1.moveTo(centerX - offset1X, centerY - offset1Y)
            path2.moveTo(centerX + offset1X, centerY + offset1Y)

            val endOffset1X = (endDistance / 2 * Math.cos(angle)).toFloat()
            val endOffset1Y = (endDistance / 2 * Math.sin(angle)).toFloat()

            path1.lineTo(centerX - endOffset1X, centerY - endOffset1Y)
            path2.lineTo(centerX + endOffset1X, centerY + endOffset1Y)

            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path1, 0, durationMs))
                .addStroke(GestureDescription.StrokeDescription(path2, 0, durationMs))
                .build()

            var success = false
            service.dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    success = true
                    Log.d(TAG, "[A5] 双指缩放完成")
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.w(TAG, "[A5] 缩放被取消")
                }
            }, null)

            delay(durationMs + 200)
            success
        } catch (e: Exception) {
            Log.e(TAG, "[A5] 双指缩放失败", e)
            false
        }
    }

    /**
     * 【A2】输入文本（带随机延迟）
     */
    suspend fun inputText(
        node: AccessibilityNodeInfo,
        text: String,
        delayBetweenChars: LongRange = 50L..200L
    ): Boolean {
        return try {
            // ClipboardManager 需要通过 Context.getSystemService 获取，此处简化处理
            // 实际使用 ACTION_SET_TEXT 直接输入文本
            
            // 模拟人类打字：逐个字符输入（可选）
            // 或使用剪贴板快速粘贴
            
            node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            delay(random.nextLong(100, 300))
            
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, 
                android.os.Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                })
            
            Log.d(TAG, "[A2] 输入文本：$text")
            true
        } catch (e: Exception) {
            Log.e(TAG, "输入文本失败", e)
            false
        }
    }

    /**
     * 普通点击（兼容旧代码）
     */
    fun performClick(node: AccessibilityNodeInfo): Boolean {
        return try {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } catch (e: Exception) {
            Log.e(TAG, "普通点击失败", e)
            false
        }
    }

    /**
     * 【A3】垂直滑动（封装）
     */
    suspend fun performVerticalScroll(
        service: AccessibilityService,
        scrollDown: Boolean = true,
        distance: Int = 300,
        duration: Long = 800L
    ): Boolean {
        val screenWidth = 1080f // 假设屏幕宽度
        val startY = if (scrollDown) 1500f else 500f
        val endY = startY + (if (scrollDown) distance else -distance)

        return swipeWithBezierCurve(
            service = service,
            startX = screenWidth / 2,
            startY = startY,
            endX = screenWidth / 2,
            endY = endY,
            durationMs = duration
        )
    }

    /**
     * 生成高斯分布的随机数（更符合人类行为）
     */
    private fun Random.nextGaussian(): Double {
        // Box-Muller 变换
        val u1 = nextDouble()
        val u2 = nextDouble()
        return sqrt(-2.0 * kotlin.math.ln(u1)) * cos(2.0 * Math.PI * u2)
    }
}
