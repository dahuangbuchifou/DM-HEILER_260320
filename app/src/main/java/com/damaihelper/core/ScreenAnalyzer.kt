package com.damaihelper.core

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.text.TextRecognitionResult
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
        
        // 中文 OCR 识别器
        private val chineseRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        
        // 拉丁文 OCR 识别器（数字、英文）
        private val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /**
     * 识别屏幕上的所有文字
     */
    suspend fun recognizeText(bitmap: Bitmap): List<TextBlock> {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            val visionText = Tasks.await<TextRecognitionResult>(chineseRecognizer.process(inputImage))
            
            val textBlocks = visionText.textBlocks.map { block ->
                TextBlock(
                    text = block.text,
                    bounds = block.boundingBox,
                    confidence = block.confidence ?: 0f
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
        Log.i(TAG, "🔚 图像分析器已关闭")
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
