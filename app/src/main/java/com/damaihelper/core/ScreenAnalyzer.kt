package com.damaihelper.core

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.text.Text
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.android.gms.tasks.Tasks

import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/**
 * 🔍 图像识别模块 - 使用 Google ML Kit
 * 
 * 功能：
 * - OCR 文字识别（中英文）
 * - 界面元素识别（票档按钮、观众人选项等）
 * - 坐标计算
 * 
 * 识别目标：
 * 1. 票档按钮 - 识别"¥380"、"¥580"等价格文字
 * 2. 观众人选项 - 识别"张三"、"李四"等姓名
 * 3. 立即购买按钮 - 识别"立即购买"、"提交订单"等文字
 * 4. 演出信息 - 识别演出名称、日期、场馆等
 */
class ScreenAnalyzer {

    companion object {
        private const val TAG = "ScreenAnalyzer"
    }
    
    // 中文 OCR 识别器（类成员变量）
    private val chineseRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    
    // 拉丁文 OCR 识别器（数字、英文）
    private val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * 识别屏幕上的所有文字
     */
    suspend fun recognizeText(bitmap: Bitmap): List<TextBlock> {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = Tasks.await(chineseRecognizer.process(inputImage))
            
            val textBlocks = visionText.textBlocks.map { block ->
                TextBlock(
                    text = block.text,
                    bounds = block.boundingBox ?: android.graphics.Rect(0, 0, 0, 0),
                    confidence = 0.9f  // ML Kit Text 没有 confidence，使用默认值
                )
            }
            
            Log.d(TAG, "识别到 ${textBlocks.size} 个文本块")
            return textBlocks
        } catch (e: Exception) {
            Log.e(TAG, "OCR 识别失败", e)
            return emptyList()
        }
    }

    /**
     * 查找特定文字的坐标
     * @param textBlocks OCR 识别结果
     * @param keyword 要查找的关键词
     * @return 文字的边界框，未找到返回 null
     */
    fun findTextBounds(textBlocks: List<TextBlock>, keyword: String): Rect? {
        for (block in textBlocks) {
            if (block.text.contains(keyword, ignoreCase = true)) {
                Log.d(TAG, "找到关键词 '$keyword' 在位置：${block.bounds}")
                return block.bounds
            }
        }
        Log.d(TAG, "未找到关键词 '$keyword'")
        return null
    }

    /**
     * 查找票档按钮
     * @param textBlocks OCR 识别结果
     * @param targetPrice 目标票价（如 "580"）
     * @return 票档按钮的坐标
     */
    fun findTicketPriceButton(textBlocks: List<TextBlock>, targetPrice: String): Rect? {
        // 尝试匹配价格
        val patterns = listOf(
            "¥$targetPrice",
            "${targetPrice}元",
            "RMB$targetPrice",
            targetPrice
        )
        
        for (pattern in patterns) {
            val bounds = findTextBounds(textBlocks, pattern)
            if (bounds != null) {
                Log.i(TAG, "✅ 找到票档按钮：$pattern @ ${bounds}")
                return bounds
            }
        }
        
        Log.w(TAG, "⚠️ 未找到票档按钮：$targetPrice")
        return null
    }

    /**
     * 查找观众人选项
     * @param textBlocks OCR 识别结果
     * @param audienceName 观众人姓名
     * @return 观众人选项的坐标
     */
    fun findAudienceOption(textBlocks: List<TextBlock>, audienceName: String): Rect? {
        val bounds = findTextBounds(textBlocks, audienceName)
        if (bounds != null) {
            Log.i(TAG, "✅ 找到观众人选项：$audienceName @ ${bounds}")
            return bounds
        }
        
        Log.w(TAG, "⚠️ 未找到观众人选项：$audienceName")
        return null
    }

    /**
     * 查找"立即购买"或"提交订单"按钮
     */
    fun findBuyButton(textBlocks: List<TextBlock>): Rect? {
        val keywords = listOf("立即购买", "提交订单", "确认下单", "去支付")
        
        for (keyword in keywords) {
            val bounds = findTextBounds(textBlocks, keyword)
            if (bounds != null) {
                Log.i(TAG, "✅ 找到购买按钮：$keyword @ ${bounds}")
                return bounds
            }
        }
        
        Log.w(TAG, "⚠️ 未找到购买按钮")
        return null
    }

    /**
     * 查找搜索框
     */
    fun findSearchBox(textBlocks: List<TextBlock>): Rect? {
        val keywords = listOf("搜索", "搜索演出", "请输入关键词")
        
        for (keyword in keywords) {
            val bounds = findTextBounds(textBlocks, keyword)
            if (bounds != null) {
                Log.i(TAG, "✅ 找到搜索框：$keyword @ ${bounds}")
                return bounds
            }
        }
        
        Log.w(TAG, "⚠️ 未找到搜索框")
        return null
    }

    /**
     * 提取屏幕上的所有价格
     */
    fun extractPrices(textBlocks: List<TextBlock>): List<String> {
        val prices = mutableListOf<String>()
        val priceRegex = Regex("(¥|￥|RMB)?\\s*(\\d+)(元)?")
        
        for (block in textBlocks) {
            val matches = priceRegex.findAll(block.text)
            for (match in matches) {
                prices.add(match.groupValues[2])  // 提取数字部分
            }
        }
        
        Log.d(TAG, "提取到价格：${prices.joinToString(", ")}")
        return prices.distinct()
    }

    /**
     * 从大麦预售页面提取演出信息
     * @return ExtractedConcertInfo 包含演出名、价格、时间等
     */
    suspend fun extractConcertInfoFromPreSalePage(bitmap: Bitmap): ExtractedConcertInfo? {
        try {
            val textBlocks = recognizeText(bitmap)
            val allText = textBlocks.joinToString("\n") { it.text }
            
            Log.i(TAG, "📝 识别到的文字：\n$allText")
            
            // 1. 提取演出名称（包含【】或「」的文字）
            var concertName = ""
            val namePatterns = listOf(
                Regex("【[^】]*】[^\\n]+"),
                Regex("「[^」]*」[^\\n]+"),
                Regex("\\d{4} 巡演[^\\n]+")
            )
            
            for (pattern in namePatterns) {
                val match = pattern.find(allText)
                if (match != null) {
                    concertName = match.value.trim()
                    break
                }
            }
            
            // 2. 提取价格区间
            var priceRange = ""
            val pricePattern = Regex("¥?(\\d+)[-~至](\\d+)")
            val priceMatch = pricePattern.find(allText)
            if (priceMatch != null) {
                priceRange = "${priceMatch.groupValues[1]}-${priceMatch.groupValues[2]}元"
            }
            
            // 3. 提取开抢时间
            var grabTime = ""
            val timePattern = Regex("(\\d{2} 月\\d{2} 日\\s*\\d{2}:\\d{2}) 开抢")
            val timeMatch = timePattern.find(allText)
            if (timeMatch != null) {
                grabTime = timeMatch.groupValues[1]
            }
            
            // 4. 提取倒计时
            var countdown = ""
            val countdownPattern = Regex("(\\d+ 天\\s*\\d+ 时\\s*\\d+ 分\\s*\\d+ 秒)")
            val countdownMatch = countdownPattern.find(allText)
            if (countdownMatch != null) {
                countdown = countdownMatch.value
            }
            
            val info = ExtractedConcertInfo(
                concertName = concertName,
                priceRange = priceRange,
                grabTime = grabTime,
                countdown = countdown
            )
            
            Log.i(TAG, "✅ 提取到演出信息：$info")
            return info
        } catch (e: Exception) {
            Log.e(TAG, "提取演出信息失败", e)
            return null
        }
    }
}

/**
 * 从大麦预售页面提取的演出信息
 */
data class ExtractedConcertInfo(
    val concertName: String,
    val priceRange: String,
    val grabTime: String,
    val countdown: String
)

    /**
     * 计算点击坐标（边界框中心）
     */
    fun calculateClickCenter(bounds: Rect): Pair<Int, Int> {
        val centerX = (bounds.left + bounds.right) / 2
        val centerY = (bounds.top + bounds.bottom) / 2
        return Pair(centerX, centerY)
    }

    /**
     * 清理资源
     */
    fun close() {
        chineseRecognizer.close()
        latinRecognizer.close()
        android.util.Log.i(TAG, "Image analyzer closed")
    }
}

/**
 * 文本块数据类
 */
data class TextBlock(
    val text: String,
    val bounds: Rect,
    val confidence: Float
)
