package com.damaihelper.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import com.damaihelper.model.TicketTask
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 🎯 抢票流程控制器 - 图像识别版本
 * 
 * 工作流程：
 * 1. 检测分屏状态
 * 2. 截屏分析大麦 App 界面
 * 3. 识别当前页面元素（票档、观众人、按钮等）
 * 4. 计算点击坐标
 * 5. 执行点击操作
 * 
 * 状态机：
 * IDLE → SEARCHING → PRICE_SELECTING → AUDIENCE_SELECTING → SUBMITTING → COMPLETED
 */
class TicketGrabbingController(
    private val accessibilityService: AccessibilityService,
    private val screenCapture: ScreenCaptureService,
    private val analyzer: ScreenAnalyzer
) {

    companion object {
        private const val TAG = "GrabbingController"
    }

    enum class GrabbingState {
        IDLE,              // 空闲
        SEARCHING,         // 搜索演出
        PRICE_SELECTING,   // 选择票档
        AUDIENCE_SELECTING, // 选择观众
        SUBMITTING,        // 提交订单
        COMPLETED,         // 完成
        FAILED             // 失败
    }

    private var currentTask: TicketTask? = null
    private var currentState = GrabbingState.IDLE
    
    private val _stateFlow = MutableStateFlow(GrabbingState.IDLE)
    val stateFlow: StateFlow<GrabbingState> = _stateFlow.asStateFlow()
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    /**
     * 开始抢票流程
     */
    fun startGrabbing(task: TicketTask) {
        if (currentState != GrabbingState.IDLE) {
            Log.w(TAG, "抢票流程已在运行中")
            return
        }

        currentTask = task
        coroutineScope.launch {
            try {
                Log.i(TAG, "========== 开始图像识别抢票流程 ==========")
                Log.i(TAG, "演出：${task.concertKeyword}")
                Log.i(TAG, "票档：${task.selectedPrice}")
                Log.i(TAG, "观众：${task.audienceName}")

                // 步骤 1: 检测分屏状态
                _stateFlow.value = GrabbingState.SEARCHING
                if (!waitForSplitScreen()) {
                    Log.e(TAG, "❌ 未检测到分屏状态，请手动开启分屏")
                    _stateFlow.value = GrabbingState.FAILED
                    return@launch
                }

                // 步骤 2: 开始截屏
                screenCapture.startCapture(fps = 1)
                delay(2000)  // 等待截屏稳定

                // 步骤 3: 搜索演出（如果需要）
                // searchConcert(task.concertKeyword)

                // 步骤 4: 选择票档
                _stateFlow.value = GrabbingState.PRICE_SELECTING
                selectTicketPrice(task.selectedPrice)

                // 步骤 5: 选择观众
                _stateFlow.value = GrabbingState.AUDIENCE_SELECTING
                selectAudience(task.audienceName)

                // 步骤 6: 提交订单
                _stateFlow.value = GrabbingState.SUBMITTING
                submitOrder()

                _stateFlow.value = GrabbingState.COMPLETED
                Log.i(TAG, "========== 抢票流程完成 ==========")
            } catch (e: Exception) {
                Log.e(TAG, "抢票流程异常", e)
                _stateFlow.value = GrabbingState.FAILED
            } finally {
                screenCapture.stopCapture()
                currentTask = null
            }
        }
    }

    /**
     * 等待分屏状态
     */
    private suspend fun waitForSplitScreen(timeoutMs: Long = 10000): Boolean {
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (screenCapture.isSplitScreenMode()) {
                Log.i(TAG, "✅ 检测到分屏状态")
                return true
            }
            delay(500)
        }
        
        Log.e(TAG, "⏰ 等待分屏超时")
        return false
    }

    /**
     * 搜索演出
     */
    private suspend fun searchConcert(keyword: String) {
        Log.i(TAG, "🔍 搜索演出：$keyword")
        
        // 获取截图并识别
        val bitmap = screenCapture.getLatestFrame() ?: return
        val textBlocks = analyzer.recognizeText(bitmap)
        
        // 查找搜索框
        val searchBoxBounds = analyzer.findSearchBox(textBlocks)
        if (searchBoxBounds != null) {
            val center = analyzer.calculateClickCenter(searchBoxBounds)
            performClick(center.first, center.second)
            Log.i(TAG, "✅ 点击搜索框")
            
            // 等待用户输入关键词（需要手动输入）
            Log.w(TAG, "⚠️ 请在搜索框输入：$keyword")
            delay(5000)  // 给用户 5 秒时间输入
            
            // 点击搜索结果
            val resultBounds = analyzer.findTextBounds(textBlocks, keyword)
            if (resultBounds != null) {
                val center = analyzer.calculateClickCenter(resultBounds)
                performClick(center.first, center.second)
                Log.i(TAG, "✅ 点击搜索结果")
            }
        }
    }

    /**
     * 选择票档
     */
    private suspend fun selectTicketPrice(price: String) {
        Log.i(TAG, "🎫 选择票档：$price")
        
        // 循环检测直到找到票档
        for (i in 0..10) {
            val bitmap = screenCapture.getLatestFrame() ?: break
            val textBlocks = analyzer.recognizeText(bitmap)
            
            val priceBounds = analyzer.findTicketPriceButton(textBlocks, price)
            if (priceBounds != null) {
                val center = analyzer.calculateClickCenter(priceBounds)
                performClick(center.first, center.second)
                Log.i(TAG, "✅ 点击票档按钮：$price")
                delay(1000)  // 等待页面加载
                return
            }
            
            Log.d(TAG, "未找到票档，继续检测... ($i/10)")
            delay(1000)
        }
        
        Log.e(TAG, "❌ 未找到票档：$price")
    }

    /**
     * 选择观众
     */
    private suspend fun selectAudience(name: String) {
        Log.i(TAG, "👤 选择观众：$name")
        
        // 循环检测直到找到观众人选项
        for (i in 0..10) {
            val bitmap = screenCapture.getLatestFrame() ?: break
            val textBlocks = analyzer.recognizeText(bitmap)
            
            val audienceBounds = analyzer.findAudienceOption(textBlocks, name)
            if (audienceBounds != null) {
                val center = analyzer.calculateClickCenter(audienceBounds)
                performClick(center.first, center.second)
                Log.i(TAG, "✅ 点击观众人选项：$name")
                delay(1000)  // 等待页面加载
                return
            }
            
            Log.d(TAG, "未找到观众人，继续检测... ($i/10)")
            delay(1000)
        }
        
        Log.e(TAG, "❌ 未找到观众人：$name")
    }

    /**
     * 提交订单
     */
    private suspend fun submitOrder() {
        Log.i(TAG, "📝 提交订单")
        
        // 获取截图并识别
        val bitmap = screenCapture.getLatestFrame() ?: return
        val textBlocks = analyzer.recognizeText(bitmap)
        
        // 查找购买按钮
        val buyButtonBounds = analyzer.findBuyButton(textBlocks)
        if (buyButtonBounds != null) {
            val center = analyzer.calculateClickCenter(buyButtonBounds)
            performClick(center.first, center.second)
            Log.i(TAG, "✅ 点击购买按钮")
        } else {
            Log.e(TAG, "❌ 未找到购买按钮")
        }
    }

    /**
     * 执行点击
     */
    private fun performClick(x: Int, y: Int) {
        val gestureBuilder = GestureDescription.Builder()
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())
        
        gestureBuilder.addStroke(
            GestureDescription.StrokeDescription(
                path,
                0,
                100  // 点击持续时间 100ms
            )
        )
        
        accessibilityService.dispatchGesture(
            gestureBuilder.build(),
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.i(TAG, "✅ 点击完成：($x, $y)")
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.e(TAG, "❌ 点击取消：($x, $y)")
                }
            },
            null
        )
    }

    /**
     * 停止抢票
     */
    fun stopGrabbing() {
        screenCapture.stopCapture()
        currentTask = null
        _stateFlow.value = GrabbingState.IDLE
        Log.i(TAG, "⏹️ 抢票流程已停止")
    }

    /**
     * 获取当前状态
     */
    fun getCurrentState(): GrabbingState {
        return currentState
    }
}
