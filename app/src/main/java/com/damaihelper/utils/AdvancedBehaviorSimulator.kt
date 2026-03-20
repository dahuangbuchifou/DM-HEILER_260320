package com.damaihelper.utils

import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.pow
// 修正一：导入 java.util.Random
import java.util.Random

/**
 * 高级行为模拟器
 * 实现贝塞尔曲线滑动、多维度随机化、模拟用户思考时间等高级行为模拟
 */
class AdvancedBehaviorSimulator {

    // 修正二：确保 random 是 java.util.Random 的实例
    private val random = Random(System.currentTimeMillis())

    /**
     * 贝塞尔曲线点数据类
     */
    data class BezierPoint(val x: Float, val y: Float, val time: Long)

    /**
     * 使用贝塞尔曲线生成平滑的滑动轨迹
     */
    fun generateBezierCurve(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        duration: Long,
        controlPointCount: Int = 100
    ): List<BezierPoint> {
        val points = mutableListOf<BezierPoint>()

        // 生成控制点（随机偏移）
        val controlPoints = mutableListOf<Pair<Float, Float>>()
        controlPoints.add(startX to startY)

        // 添加中间控制点（随机偏移以增加自然性）
        val midX = (startX + endX) / 2
        val midY = (startY + endY) / 2
        val offsetX = (random.nextFloat() - 0.5f) * (endX - startX).coerceAtLeast(10f)
        val offsetY = (random.nextFloat() - 0.5f) * (endY - startY).coerceAtLeast(10f)
        controlPoints.add(midX + offsetX to midY + offsetY)

        controlPoints.add(endX to endY)

        // 使用二阶贝塞尔曲线公式生成曲线上的点
        for (i in 0..controlPointCount) {
            val t = i.toFloat() / controlPointCount
            val x = calculateBezierPoint(t, controlPoints.map { it.first })
            val y = calculateBezierPoint(t, controlPoints.map { it.second })
            val time = (duration * t).toLong()
            points.add(BezierPoint(x, y, time))
        }

        return points
    }

    /**
     * 计算贝塞尔曲线上的点
     */
    private fun calculateBezierPoint(t: Float, controlPoints: List<Float>): Float {
        if (controlPoints.size == 1) return controlPoints[0]

        val newPoints = mutableListOf<Float>()
        for (i in 0 until controlPoints.size - 1) {
            val p = controlPoints[i] * (1 - t) + controlPoints[i + 1] * t
            newPoints.add(p)
        }

        return calculateBezierPoint(t, newPoints)
    }

    /**
     * 生成随机的点击位置偏移
     */
    fun generateClickOffset(baseX: Float, baseY: Float, maxOffset: Float = 5f): Pair<Float, Float> {
        val offsetX = (random.nextFloat() - 0.5f) * maxOffset * 2
        val offsetY = (random.nextFloat() - 0.5f) * maxOffset * 2
        return (baseX + offsetX) to (baseY + offsetY)
    }

    /**
     * 生成随机的滑动速度变化
     */
    fun generateSpeedVariation(baseSpeed: Float, variationPercent: Float = 20f): Float {
        val variation = (random.nextFloat() - 0.5f) * variationPercent / 100f
        return baseSpeed * (1 + variation)
    }

    /**
     * 生成随机的加速度变化
     */
    fun generateAcceleration(): Float {
        return (random.nextFloat() - 0.5f) * 0.5f
    }

    /**
     * 模拟用户思考时间
     */
    fun simulateThinkingTime(minTime: Long = 500, maxTime: Long = 2000): Long {
        // 使用高斯分布生成更符合人类思考习惯的时间
        val mean = (minTime + maxTime) / 2f
        val stdDev = (maxTime - minTime) / 6f
        // 修正三：nextGaussian() 现在可用了
        var thinkingTime = (random.nextGaussian() * stdDev + mean).toLong()
        thinkingTime = thinkingTime.coerceIn(minTime, maxTime)
        return thinkingTime
    }

    /**
     * 生成随机的页面浏览路径（模拟用户浏览行为）
     */
    fun generateRandomScrollPath(screenHeight: Int, scrollCount: Int = 3): List<Int> {
        val scrollDistances = mutableListOf<Int>()
        for (i in 0 until scrollCount) {
            // 修正四：手动实现 nextInt(min, max)
            val min = -screenHeight / 3
            val max = screenHeight / 3
            val distance = random.nextInt(max - min + 1) + min
            scrollDistances.add(distance)
        }
        return scrollDistances
    }

    /**
     * 生成随机的点击非关键区域的序列
     */
    fun generateRandomClickSequence(screenWidth: Int, screenHeight: Int, clickCount: Int = 2): List<Pair<Float, Float>> {
        val clicks = mutableListOf<Pair<Float, Float>>()
        for (i in 0 until clickCount) {
            val x = random.nextFloat() * screenWidth
            val y = random.nextFloat() * screenHeight
            clicks.add(x to y)
        }
        return clicks
    }

    /**
     * 生成随机的双击操作
     */
    fun generateDoubleClickInterval(): Long {
        // 修正五：手动实现 nextLong(min, max)
        val min: Long = 100
        val max: Long = 300
        return (random.nextDouble() * (max - min) + min).toLong()
    }

    /**
     * 生成随机的长按持续时间
     */
    fun generateLongPressDuration(minDuration: Long = 500, maxDuration: Long = 1500): Long {
        // 修正六：手动实现 nextLong(min, max)
        return (random.nextDouble() * (maxDuration - minDuration) + minDuration).toLong()
    }

    /**
     * 生成随机的触摸压力值
     */
    fun generateTouchPressure(): Float {
        // 模拟正常的触摸压力，通常在0.5-1.0之间
        return 0.5f + random.nextFloat() * 0.5f
    }

    /**
     * 生成随机的触摸大小
     */
    fun generateTouchSize(): Float {
        return 0.3f + random.nextFloat() * 0.4f
    }

    /**
     * 计算贝塞尔曲线上的速度（用于模拟加速度变化）
     */
    fun calculateBezierVelocity(t: Float): Float {
        // 使用平滑的速度曲线：开始和结束时速度慢，中间速度快
        return if (t < 0.5f) {
            2 * t * t
        } else {
            1 - 2 * (1 - t).pow(2)
        }
    }

    /**
     * 生成随机的设备方向变化
     */
    fun generateDeviceOrientation(): Float {
        // 模拟正常的设备方向，通常在-15到15度之间（竖屏）
        return (random.nextFloat() - 0.5f) * 30f
    }

    /**
     * 生成随机的网络延迟
     */
    fun generateNetworkDelay(minDelay: Long = 50, maxDelay: Long = 500): Long {
        // 修正七：手动实现 nextLong(min, max)
        return (random.nextDouble() * (maxDelay - minDelay) + minDelay).toLong()
    }

    /**
     * 生成随机的操作序列间隔
     */
    fun generateOperationInterval(minInterval: Long = 200, maxInterval: Long = 1000): Long {
        // 修正八：手动实现 nextLong(min, max)
        return (random.nextDouble() * (maxInterval - minInterval) + minInterval).toLong()
    }
}