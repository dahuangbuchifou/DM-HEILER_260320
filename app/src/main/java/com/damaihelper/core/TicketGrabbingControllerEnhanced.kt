// ============================================================================
//  最新修复：2026-03-30 08:38
// 🔧 修复内容：
//   -  完整信息抓取：演出标题、时间、价格、场次、地点
//   -  自动交互流程：预约抢票、确定、立即提交等按钮
//   - 🆕 付款界面检测：到达付款界面自动停止
//   -  多轮交互支持：检测信息不完整自动点击下一步
//   - 🆕 详细日志记录：每个步骤的识别结果
//  说明：增强版图像识别抢票控制器 - 完整信息抓取 + 自动交互
// ============================================================================

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
 * 🎯 增强版抢票流程控制器 - 完整信息抓取 + 自动交互
 * 
 * 工作流程：
 * 1. 检测分屏状态
 * 2. 截屏分析大麦 App 界面
 * 3. 🆕 完整提取演唱会信息（标题、时间、价格、场次、地点）
 * 4. 识别当前页面元素（票档、观众人、按钮等）
 * 5.  检测信息完整性，不完整自动点击下一步
 * 6. 计算点击坐标
 * 7. 执行点击操作
 * 8.  到达付款界面自动停止
 * 
 * 状态机：
 * IDLE → SEARCHING → INFO_EXTRACTING → PRICE_SELECTING → AUDIENCE_SELECTING 
 * → SUBMITTING → PAYMENT_PAGE → COMPLETED/FAILED
 */
class TicketGrabbingControllerEnhanced(
    private val accessibilityService: AccessibilityService,
    private val screenCapture: ScreenCaptureService,
    private val analyzer: ScreenAnalyzer
) {

    companion object {
        private const val TAG = "GrabbingControllerEnhanced"
        
        // 🆕 付款界面关键词
        private val PAYMENT_KEYWORDS = listOf(
            "支付宝支付",
            "微信支付", 
            "银行卡支付",
            "付款金额",
            "确认支付",
            "立即支付",
            "选择支付方式"
        )
        
        // 🆕 自动交互按钮关键词
        private val ACTION_BUTTONS = listOf(
            "预约抢票",
            "确定",
            "立即提交",
            "下一步",
            "确认选座",
            "配送方式",
            "提交订单",
            "立即购买",
            "去支付",
            "确认下单"
        )
        
        // 🆕 演出信息关键词
        private val CONCERT_INFO_KEYWORDS = listOf(
            "演出时间",
            "演出地点",
            "场馆",
            "场次",
            "票档",
            "价格",
            "预售"
        )
    }

    enum class GrabbingState {
        IDLE,              // 空闲
        SEARCHING,         // 搜索演出
        INFO_EXTRACTING,   // 🆕 提取演出信息
        PRICE_SELECTING,   // 选择票档
        AUDIENCE_SELECTING, // 选择观众
        SUBMITTING,        // 提交订单
        PAYMENT_PAGE,      // 🆕 到达付款界面
        COMPLETED,         // 完成
        FAILED             // 失败
    }

    private var currentTask: TicketTask? = null
    private var currentState = GrabbingState.IDLE
    
    // 🆕 提取到的完整演出信息
    private var extractedConcertInfo: CompleteConcertInfo? = null
    
    private val _stateFlow = MutableStateFlow(GrabbingState.IDLE)
    val stateFlow: StateFlow<GrabbingState> = _stateFlow.asStateFlow()
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    
    // 🆕 信息完整性检查
    private var isInfoComplete = false

    /**
     * 开始抢票流程（增强版）
     */
    fun startGrabbing(task: TicketTask) {
        if (currentState != GrabbingState.IDLE) {
            Log.w(TAG, "抢票流程已在运行中")
            return
        }

        currentTask = task
        coroutineScope.launch {
            try {
                Log.i(TAG, "========== 🚀 开始增强版抢票流程 ==========")
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
                screenCapture.startCapture(fps = 2)  //  提高帧率到 2FPS
                delay(2000)  // 等待截屏稳定

                // 🆕 步骤 3: 提取完整演出信息
                _stateFlow.value = GrabbingState.INFO_EXTRACTING
                extractCompleteConcertInfo()
                
                if (!isInfoComplete) {
                    Log.w(TAG, "⚠️ 信息不完整，尝试自动交互获取更多信息")
                    autoNavigateToCompleteInfo()
                }

                // 步骤 4: 选择票档
                _stateFlow.value = GrabbingState.PRICE_SELECTING
                selectTicketPriceWithFallback(task.selectedPrice, task.priceTiers)

                // 步骤 5: 选择观众
                _stateFlow.value = GrabbingState.AUDIENCE_SELECTING
                selectAudience(task.audienceName, task.audienceIndex)

                // 步骤 6: 提交订单
                _stateFlow.value = GrabbingState.SUBMITTING
                submitOrderWithAutoNavigate()

                _stateFlow.value = GrabbingState.COMPLETED
                Log.i(TAG, "========== ✅ 抢票流程完成 ==========")
                
                // 🆕 输出完整信息报告
                printConcertInfoReport()
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
     *  提取完整演唱会信息
     */
    private suspend fun extractCompleteConcertInfo() {
        Log.i(TAG, "📋 开始提取完整演唱会信息...")
        
        val info = CompleteConcertInfo()
        var maxAttempts = 10
        var attempt = 0
        
        while (attempt < maxAttempts) {
            attempt++
            val bitmap = screenCapture.getLatestFrame() ?: break
            val textBlocks = analyzer.recognizeText(bitmap)
            
            Log.i(TAG, "📝 第 ${attempt} 次识别，共 ${textBlocks.size} 个文本块")
            
            // 1. 提取演出标题
            if (info.title.isEmpty()) {
                info.title = extractConcertTitle(textBlocks)
            }
            
            // 2. 提取演出时间（多场次）
            if (info.showTimes.isEmpty()) {
                info.showTimes = extractShowTimes(textBlocks)
            }
            
            // 3. 提取演出地点
            if (info.location.isEmpty()) {
                info.location = extractLocation(textBlocks)
            }
            
            // 4. 提取票价档位
            if (info.priceTiers.isEmpty()) {
                info.priceTiers = extractAllPriceTiers(textBlocks)
            }
            
            // 5. 提取库存状态
            info.stockStatus = extractStockStatus(textBlocks)
            
            // 🆕 检查信息完整性
            isInfoComplete = checkInfoCompleteness(info)
            
            if (isInfoComplete) {
                Log.i(TAG, "✅ 信息提取完整")
                extractedConcertInfo = info
                printConcertInfoReport()
                return
            }
            
            Log.d(TAG, "信息不完整，继续检测... ($attempt/$maxAttempts)")
            delay(1500)
        }
        
        extractedConcertInfo = info
        Log.w(TAG, "⚠️ 信息提取完成，但可能不完整")
    }

    /**
     * 🆕 自动导航获取完整信息
     */
    private suspend fun autoNavigateToCompleteInfo() {
        Log.i(TAG, "🔄 开始自动导航获取完整信息...")
        
        for (buttonKeyword in ACTION_BUTTONS) {
            Log.d(TAG, "尝试点击：$buttonKeyword")
            
            val bitmap = screenCapture.getLatestFrame() ?: break
            val textBlocks = analyzer.recognizeText(bitmap)
            
            val buttonBounds = analyzer.findTextBounds(textBlocks, buttonKeyword)
            if (buttonBounds != null) {
                val center = analyzer.calculateClickCenter(buttonBounds)
                performClick(center.first, center.second)
                Log.i(TAG, "✅ 点击按钮：$buttonKeyword")
                
                delay(2000)  // 等待页面加载
                
                // 重新检查信息
                extractCompleteConcertInfo()
                
                if (isInfoComplete) {
                    Log.i(TAG, "✅ 通过自动导航获取到完整信息")
                    return
                }
            }
        }
        
        Log.w(TAG, "⚠️ 自动导航未能获取完整信息")
    }

    /**
     * 🆕 提取演出标题
     */
    private fun extractConcertTitle(textBlocks: List<TextBlock>): String {
        val titlePatterns = listOf(
            Regex("【[^】]*】[^\\n]{10,}"),
            Regex("「[^」]*」[^\\n]{10,}"),
            Regex("\\d{4}[^\\n]*巡演[^\\n]{5,}"),
            Regex("\\d{4}[^\\n]*演唱会[^\\n]{5,}")
        )
        
        val allText = textBlocks.joinToString("\n") { it.text }
        
        for (pattern in titlePatterns) {
            val match = pattern.find(allText)
            if (match != null) {
                val title = match.value.trim()
                Log.i(TAG, " 提取到演出标题：$title")
                return title
            }
        }
        
        Log.w(TAG, "⚠️ 未找到演出标题")
        return ""
    }

    /**
     * 🆕 提取演出时间（多场次）
     */
    private fun extractShowTimes(textBlocks: List<TextBlock>): List<ShowTimeInfo> {
        val showTimes = mutableListOf<ShowTimeInfo>()
        val allText = textBlocks.joinToString("\n") { it.text }
        
        // 匹配日期时间格式：2026-05-15 周五 19:30
        val dateTimePattern = Regex("(\\d{4}-\\d{2}-\\d{2})[^\\n]*(周[五六日一二三四])[^\\n]*(\\d{2}:\\d{2})")
        val matches = dateTimePattern.findAll(allText)
        
        for (match in matches) {
            val date = match.groupValues[1]
            val weekday = match.groupValues[2]
            val time = match.groupValues[3]
            
            showTimes.add(ShowTimeInfo(
                date = date,
                weekday = weekday,
                time = time,
                fullText = "${date} ${weekday} ${time}"
            ))
            
            Log.i(TAG, "📅 提取到场次时间：${date} ${weekday} ${time}")
        }
        
        return showTimes
    }

    /**
     *  提取演出地点
     */
    private fun extractLocation(textBlocks: List<TextBlock>): String {
        val locationPatterns = listOf(
            Regex("([^\\n]*体育中心[^\\n]*)"),
            Regex("([^\\n]*体育馆[^\\n]*)"),
            Regex("([^\\n]*会展中心[^\\n]*)"),
            Regex("([^\\n]*大剧院[^\\n]*)"),
            Regex("([^\\n]*体育场[^\\n]*)")
        )
        
        val allText = textBlocks.joinToString("\n") { it.text }
        
        for (pattern in locationPatterns) {
            val match = pattern.find(allText)
            if (match != null) {
                val location = match.groupValues[1].trim()
                Log.i(TAG, "📍 提取到演出地点：$location")
                return location
            }
        }
        
        Log.w(TAG, "⚠️ 未找到演出地点")
        return ""
    }

    /**
     * 🆕 提取所有票价档位
     */
    private fun extractAllPriceTiers(textBlocks: List<TextBlock>): List<PriceTierInfo> {
        val priceTiers = mutableListOf<PriceTierInfo>()
        val pricePattern = Regex("(¥|￥)?\\s*(\\d+)(元)?")
        
        for (block in textBlocks) {
            val match = pricePattern.find(block.text)
            if (match != null) {
                val price = match.groupValues[2]
                val isSoldOut = block.text.contains("缺货") || block.text.contains("售罄")
                
                priceTiers.add(PriceTierInfo(
                    price = price.toInt(),
                    isSoldOut = isSoldOut,
                    fullText = block.text
                ))
                
                Log.i(TAG, "💰 提取到票价：${price}元 ${if (isSoldOut) "售罄" else "有票"}")
            }
        }
        
        // 按价格排序（从高到低）
        return priceTiers.distinctBy { it.price }.sortedByDescending { it.price }
    }

    /**
     * 🆕 提取库存状态
     */
    private fun extractStockStatus(textBlocks: List<TextBlock>): Map<Int, Boolean> {
        val stockStatus = mutableMapOf<Int, Boolean>()
        val pricePattern = Regex("(¥|￥)?\\s*(\\d+)(元)?")
        
        for (block in textBlocks) {
            val match = pricePattern.find(block.text)
            if (match != null) {
                val price = match.groupValues[2].toInt()
                val hasStock = !(block.text.contains("缺货") || block.text.contains("售罄"))
                stockStatus[price] = hasStock
            }
        }
        
        return stockStatus
    }

    /**
     * 🆕 检查信息完整性
     */
    private fun checkInfoCompleteness(info: CompleteConcertInfo): Boolean {
        val hasTitle = info.title.isNotEmpty()
        val hasTimes = info.showTimes.isNotEmpty()
        val hasLocation = info.location.isNotEmpty()
        val hasPrices = info.priceTiers.isNotEmpty()
        
        val isComplete = hasTitle && hasTimes && hasLocation && hasPrices
        
        Log.d(TAG, "信息完整性检查：标题=$hasTitle, 时间=${info.showTimes.size}场，地点=$hasLocation, 价格=${info.priceTiers.size}档")
        
        return isComplete
    }

    /**
     *  打印演唱会信息报告
     */
    private fun printConcertInfoReport() {
        val info = extractedConcertInfo ?: return
        
        Log.i(TAG, "\n" + "=".repeat(50))
        Log.i(TAG, "📋 演唱会信息报告")
        Log.i(TAG, "=".repeat(50))
        Log.i(TAG, "🎤 标题：${info.title}")
        Log.i(TAG, "📍 地点：${info.location}")
        Log.i(TAG, "📅 场次：${info.showTimes.size} 场")
        
        for ((i, time) in info.showTimes.withIndex()) {
            Log.i(TAG, "   ${i+1}. ${time.fullText}")
        }
        
        Log.i(TAG, "💰 票价：${info.priceTiers.size} 档")
        for (tier in info.priceTiers) {
            val status = if (tier.isSoldOut) "售罄" else "有票"
            Log.i(TAG, "   - ${tier.price}元 ($status)")
        }
        
        Log.i(TAG, "=".repeat(50))
    }

    /**
     * 选择票档（支持多票档备选）
     */
    private suspend fun selectTicketPriceWithFallback(targetPrice: String, priceTiers: String) {
        Log.i(TAG, "🎫 选择票档：目标=$targetPrice, 备选=$priceTiers")
        
        // 解析备选票档
        val fallbackPrices = if (priceTiers.isNotEmpty()) {
            priceTiers.split(",").map { it.trim() }
        } else {
            listOf(targetPrice)
        }
        
        // 尝试每个票档
        for (price in fallbackPrices) {
            Log.d(TAG, "尝试票档：$price")
            
            for (i in 0..10) {
                val bitmap = screenCapture.getLatestFrame() ?: break
                val textBlocks = analyzer.recognizeText(bitmap)
                
                val priceBounds = analyzer.findTicketPriceButton(textBlocks, price)
                if (priceBounds != null) {
                    val center = analyzer.calculateClickCenter(priceBounds)
                    performClick(center.first, center.second)
                    Log.i(TAG, "✅ 点击票档按钮：$price")
                    delay(1000)
                    return
                }
                
                Log.d(TAG, "未找到票档，继续检测... ($i/10)")
                delay(1000)
            }
        }
        
        Log.e(TAG, "❌ 所有票档都未找到：${fallbackPrices.joinToString(", ")}")
    }

    /**
     * 选择观众（支持索引）
     */
    private suspend fun selectAudience(name: String, index: Int) {
        Log.i(TAG, "👤 选择观众：姓名=$name, 索引=$index")
        
        for (i in 0..10) {
            val bitmap = screenCapture.getLatestFrame() ?: break
            val textBlocks = analyzer.recognizeText(bitmap)
            
            // 优先按姓名查找
            val audienceBounds = analyzer.findAudienceOption(textBlocks, name)
            if (audienceBounds != null) {
                val center = analyzer.calculateClickCenter(audienceBounds)
                performClick(center.first, center.second)
                Log.i(TAG, "✅ 点击观众人选项：$name")
                delay(1000)
                return
            }
            
            // 🆕 如果找不到姓名，尝试按索引选择（第一个观众）
            if (index == 1) {
                val firstAudienceBounds = findFirstAudienceOption(textBlocks)
                if (firstAudienceBounds != null) {
                    val center = analyzer.calculateClickCenter(firstAudienceBounds)
                    performClick(center.first, center.second)
                    Log.i(TAG, "✅ 点击第一个观众人选项（索引=$index）")
                    delay(1000)
                    return
                }
            }
            
            Log.d(TAG, "未找到观众人，继续检测... ($i/10)")
            delay(1000)
        }
        
        Log.e(TAG, "❌ 未找到观众人：$name (索引=$index)")
    }

    /**
     * 🆕 查找第一个观众人选项
     */
    private fun findFirstAudienceOption(textBlocks: List<TextBlock>): Rect? {
        // 查找包含"观众"、"观演人"的文本块
        val keywords = listOf("观众", "观演人", "实名")
        
        for (keyword in keywords) {
            for (block in textBlocks) {
                if (block.text.contains(keyword)) {
                    Log.d(TAG, "找到观众人选项：${block.text}")
                    return block.bounds
                }
            }
        }
        
        return null
    }

    /**
     * 提交订单（支持自动导航）
     */
    private suspend fun submitOrderWithAutoNavigate() {
        Log.i(TAG, "📝 提交订单（带自动导航）")
        
        var maxSteps = 10
        var step = 0
        
        while (step < maxSteps) {
            step++
            
            val bitmap = screenCapture.getLatestFrame() ?: break
            val textBlocks = analyzer.recognizeText(bitmap)
            
            // 🆕 检查是否到达付款界面
            if (isPaymentPage(textBlocks)) {
                Log.i(TAG, "✅ 到达付款界面，停止自动操作")
                _stateFlow.value = GrabbingState.PAYMENT_PAGE
                return
            }
            
            // 查找购买按钮
            val buyButtonBounds = analyzer.findBuyButton(textBlocks)
            if (buyButtonBounds != null) {
                val center = analyzer.calculateClickCenter(buyButtonBounds)
                performClick(center.first, center.second)
                Log.i(TAG, "✅ 点击购买按钮（步骤 $step）")
                delay(1500)
                continue
            }
            
            // 🆕 查找其他自动交互按钮
            var clickedButton = false
            for (buttonKeyword in ACTION_BUTTONS) {
                val buttonBounds = analyzer.findTextBounds(textBlocks, buttonKeyword)
                if (buttonBounds != null) {
                    val center = analyzer.calculateClickCenter(buttonBounds)
                    performClick(center.first, center.second)
                    Log.i(TAG, "✅ 点击按钮：$buttonKeyword（步骤 $step）")
                    clickedButton = true
                    delay(1500)
                    break
                }
            }
            
            if (!clickedButton) {
                Log.d(TAG, "未找到可点击按钮，继续检测... ($step/$maxSteps)")
                delay(1500)
            }
        }
        
        Log.w(TAG, "⚠️ 提交订单完成，但未到达付款界面")
    }

    /**
     * 🆕 检测是否为付款界面
     */
    private fun isPaymentPage(textBlocks: List<TextBlock>): Boolean {
        val allText = textBlocks.joinToString(" ") { it.text }
        
        for (keyword in PAYMENT_KEYWORDS) {
            if (allText.contains(keyword, ignoreCase = true)) {
                Log.i(TAG, "✅ 检测到付款界面关键词：$keyword")
                return true
            }
        }
        
        return false
    }

    // ============ 辅助方法（保持不变） ============

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

    private fun performClick(x: Int, y: Int) {
        val gestureBuilder = GestureDescription.Builder()
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())
        
        gestureBuilder.addStroke(
            GestureDescription.StrokeDescription(
                path,
                0,
                100
            )
        )
        
        accessibilityService.dispatchGesture(
            gestureBuilder.build(),
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.i(TAG, "✅ 点击完成：($x, $y)")
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.e(TAG, " 点击取消：($x, $y)")
                }
            },
            null
        )
    }

    fun stopGrabbing() {
        screenCapture.stopCapture()
        currentTask = null
        _stateFlow.value = GrabbingState.IDLE
        Log.i(TAG, "⏹️ 抢票流程已停止")
    }

    fun getCurrentState(): GrabbingState {
        return currentState
    }
}

// ============================================================================
// 🆕 数据模型：完整演唱会信息
// ============================================================================

/**
 * 完整演唱会信息
 */
data class CompleteConcertInfo(
    var title: String = "",                    // 演出标题
    var showTimes: List<ShowTimeInfo> = emptyList(),  // 演出时间（多场次）
    var location: String = "",                 // 演出地点
    var priceTiers: List<PriceTierInfo> = emptyList(), // 票价档位
    var stockStatus: Map<Int, Boolean> = emptyMap() // 库存状态（价格 -> 是否有票）
)

/**
 * 场次时间信息
 */
data class ShowTimeInfo(
    val date: String,      // 日期：2026-05-15
    val weekday: String,   // 星期：周五
    val time: String,      // 时间：19:30
    val fullText: String   // 完整文本：2026-05-15 周五 19:30
)

/**
 * 票价档位信息
 */
data class PriceTierInfo(
    val price: Int,        // 价格：580
    val isSoldOut: Boolean, // 是否售罄
    val fullText: String   // 完整文本：580 元 缺货登记
)
