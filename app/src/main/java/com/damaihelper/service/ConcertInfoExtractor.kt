// ============================================================================
// 📅 修复日期：2026-03-20
// 🔧 修复内容：
//   - 增强页面文本提取容错
//   - 优化日期和票价正则匹配
//   - 添加空节点保护
//   - 🆕 新增票档选择页和确认购买页抓取
// 📝 说明：演出信息提取器 - 从大麦详情页自动抓取演出数据
// ============================================================================

package com.damaihelper.service

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.damaihelper.model.ConcertInfo
import com.damaihelper.utils.NodeUtils

class ConcertInfoExtractor(private val context: Context) {

    companion object {
        private const val TAG = "ConcertInfoExtractor"
        
        // 广播 Action
        const val ACTION_CONCERT_INFO_EXTRACTED = "com.damaihelper.CONCERT_INFO_EXTRACTED"
        const val EXTRA_CONCERT_INFO = "concert_info"
        const val EXTRA_ERROR_MESSAGE = "error_message"
    }

    /**
     * 从大麦详情页抓取演出信息
     */
    fun extractFromDetailPage(rootNode: AccessibilityNodeInfo): ConcertInfo? {
        try {
            Log.i(TAG, "========== 开始抓取演出信息 ==========")

            // 1. 抓取演出名称
            val concertName = extractConcertName(rootNode)
            Log.i(TAG, "演出名称：$concertName")

            // 2. 抓取场馆信息
            val venue = extractVenue(rootNode)
            Log.i(TAG, "场馆：$venue")

            // 3. 抓取城市
            val city = extractCity(rootNode)
            Log.i(TAG, "城市：$city")

            // 4. 抓取可选日期
            val dates = extractAvailableDates(rootNode)
            Log.i(TAG, "可选日期：${dates.joinToString(", ")}")

            // 5. 抓取可选票价
            val prices = extractAvailablePrices(rootNode)
            Log.i(TAG, "可选票价：${prices.joinToString(", ")}")

            val info = ConcertInfo(
                concertName = concertName,
                venue = venue,
                city = city,
                availableDates = dates,
                availablePrices = prices
            )

            Log.i(TAG, "========== 信息抓取完成 ==========")
            return info

        } catch (e: Exception) {
            Log.e(TAG, "抓取信息失败", e)
            return null
        }
    }

    // ==================== 🆕 新增：票档选择页和确认页抓取 ====================

    /**
     * 🆕 从票档选择页抓取信息（步骤 2）
     */
    fun extractFromTicketSelectionPage(rootNode: AccessibilityNodeInfo): ConcertInfo? {
        try {
            Log.i(TAG, "========== 开始抓取票档选择页信息 ==========")

            // 1. 抓取已选场次日期
            val selectedDate = extractSelectedDate(rootNode)
            Log.i(TAG, "已选场次：$selectedDate")

            // 2. 抓取所有票档（含状态）
            val ticketPrices = extractTicketPricesWithStatus(rootNode)
            Log.i(TAG, "票档列表：${ticketPrices.joinToString(", ")}")

            // 3. 自动选择最贵票档
            val mostExpensivePrice = selectMostExpensivePrice(ticketPrices)
            Log.i(TAG, "最贵票档：$mostExpensivePrice")

            val info = ConcertInfo(
                selectedDate = selectedDate,
                availablePrices = ticketPrices,
                selectedPrice = mostExpensivePrice,
                ticketCount = 1
            )

            Log.i(TAG, "========== 票档信息抓取完成 ==========")
            return info

        } catch (e: Exception) {
            Log.e(TAG, "抓取票档信息失败", e)
            return null
        }
    }

    /**
     * 🆕 从确认购买页抓取信息（步骤 3）
     */
    fun extractFromConfirmPage(rootNode: AccessibilityNodeInfo): ConcertInfo? {
        try {
            Log.i(TAG, "========== 开始抓取确认购买页信息 ==========")

            // 1. 抓取已选观演人
            val audienceName = extractSelectedAudience(rootNode)
            Log.i(TAG, "观演人：$audienceName")

            // 2. 抓取联系电话
            val phoneNumber = extractPhoneNumber(rootNode)
            Log.i(TAG, "联系电话：$phoneNumber")

            // 3. 抓取支付方式
            val paymentMethod = extractPaymentMethod(rootNode)
            Log.i(TAG, "支付方式：$paymentMethod")

            val info = ConcertInfo(
                audienceName = audienceName,
                phoneNumber = phoneNumber,
                paymentMethod = paymentMethod
            )

            Log.i(TAG, "========== 确认页信息抓取完成 ==========")
            return info

        } catch (e: Exception) {
            Log.e(TAG, "抓取确认页信息失败", e)
            return null
        }
    }

    /**
     * 🆕 抓取已选场次日期
     */
    private fun extractSelectedDate(rootNode: AccessibilityNodeInfo): String {
        // 查找包含日期格式的文本，如"2026-04-18 周六 19:00"
        val datePattern = Regex("\\d{4}[-./]\\d{1,2}[-./]\\d{1,2}.*(?:周 [六日天]|19:00|20:00)")
        
        val allTexts = mutableListOf<String>()
        collectTextNodes(rootNode, allTexts, 0, 6)

        for (text in allTexts) {
            if (text.contains("已选") || text.contains("场次")) {
                val match = datePattern.find(text)
                if (match != null) {
                    return match.value
                }
            }
        }

        // 备用方案：找第一个包含完整日期的文本
        for (text in allTexts) {
            val match = datePattern.find(text)
            if (match != null && text.length < 50) {
                return match.value
            }
        }

        return ""
    }

    /**
     * 🆕 抓取所有票档（含缺货/售罄状态）
     */
    private fun extractTicketPricesWithStatus(rootNode: AccessibilityNodeInfo): List<String> {
        val prices = mutableListOf<String>()
        
        // 查找包含价格且可能包含状态的节点
        val priceKeywords = listOf("元", "¥", "缺货", "售罄", "预售")
        
        val allTexts = mutableListOf<String>()
        collectTextNodes(rootNode, allTexts, 0, 8)

        for (text in allTexts) {
            // 检查是否是票档信息
            if (priceKeywords.any { text.contains(it) }) {
                // 提取票档信息（如"内场 880 元"、"看台 380 元（缺货登记）"）
                if (text.contains("元") || text.contains("¥")) {
                    val priceText = text.trim()
                    if (priceText.length in 3..30) {
                        prices.add(priceText)
                    }
                }
            }
        }

        return prices.distinct()
    }

    /**
     * 🆕 自动选择最贵票档
     */
    private fun selectMostExpensivePrice(prices: List<String>): String {
        if (prices.isEmpty()) return ""

        // 提取价格数值并排序
        val priceWithValues = prices.mapNotNull { priceText ->
            val value = Regex("\\d+").find(priceText)?.value?.toIntOrNull()
            if (value != null) priceText to value else null
        }

        // 按价格降序排序，选择最贵的
        return priceWithValues.maxByOrNull { it.second }?.first ?: ""
    }

    /**
     * 🆕 抓取已选观演人
     */
    private fun extractSelectedAudience(rootNode: AccessibilityNodeInfo): String {
        // 查找包含"已选择"或"✓"的观演人
        val audienceKeywords = listOf("已选择", "✓", "√", "选中")
        
        val allTexts = mutableListOf<String>()
        collectTextNodes(rootNode, allTexts, 0, 8)

        for (text in allTexts) {
            if (audienceKeywords.any { text.contains(it) } && text.length in 2..20) {
                // 清理文本，去掉"已选择"等标记
                val name = text.replace(Regex("已选择 |  ✓|√| 选中"), "").trim()
                if (name.isNotEmpty()) {
                    return name
                }
            }
        }

        return ""
    }

    /**
     * 🆕 抓取联系电话
     */
    private fun extractPhoneNumber(rootNode: AccessibilityNodeInfo): String {
        val phonePattern = Regex("1[3-9]\\d{9}")
        
        val allTexts = mutableListOf<String>()
        collectTextNodes(rootNode, allTexts, 0, 8)

        for (text in allTexts) {
            if (text.contains("手机") || text.contains("电话") || text.contains("联系")) {
                val match = phonePattern.find(text)
                if (match != null) {
                    return match.value
                }
            }
        }

        // 备用方案：找第一个手机号
        for (text in allTexts) {
            val match = phonePattern.find(text)
            if (match != null) {
                return match.value
            }
        }

        return ""
    }

    /**
     * 🆕 抓取支付方式
     */
    private fun extractPaymentMethod(rootNode: AccessibilityNodeInfo): String {
        val paymentMethods = listOf("支付宝", "微信支付", "微信", "银联", "信用卡")
        
        val allTexts = mutableListOf<String>()
        collectTextNodes(rootNode, allTexts, 0, 8)

        for (text in allTexts) {
            if (text.contains("支付") || text.contains("付款")) {
                for (method in paymentMethods) {
                    if (text.contains(method) && (text.contains("已选") || text.contains("✓"))) {
                        return method
                    }
                }
            }
        }

        // 默认返回支付宝
        return "支付宝"
    }

    // ==================== 原有方法 ====================

    /**
     * 抓取演出名称
     */
    private fun extractConcertName(rootNode: AccessibilityNodeInfo): String {
        // 方案 1: 通过标题节点抓取
        val titleNode = NodeUtils.findNodeByClassName(rootNode, "android.widget.TextView")
        if (titleNode != null && !titleNode.text.isNullOrEmpty()) {
            val text = titleNode.text.toString()
            // 通常标题在顶部，且字体较大
            if (text.length in 5..50) {
                return text
            }
        }

        // 方案 2: 查找包含特定关键字的节点
        val keywords = listOf("演唱会", "音乐会", "话剧", "展览", "赛事", "巡演")
        for (keyword in keywords) {
            val node = NodeUtils.findNodeByFuzzyText(rootNode, keyword, 0.6)
            if (node != null) {
                return node.text?.toString() ?: ""
            }
        }

        // 方案 3: 遍历寻找最可能的标题
        return findMostLikelyTitle(rootNode)
    }

    /**
     * 查找最可能的标题（通过启发式规则）
     */
    private fun findMostLikelyTitle(rootNode: AccessibilityNodeInfo): String {
        val candidates = mutableListOf<String>()
        collectTextNodes(rootNode, candidates, 0, 3) // 只搜索前 3 层
        
        // 选择长度适中、位置靠前的文本作为标题
        return candidates.firstOrNull { it.length in 5..50 } ?: "未知演出"
    }

    /**
     * 收集文本节点
     */
    private fun collectTextNodes(
        node: AccessibilityNodeInfo,
        results: MutableList<String>,
        currentDepth: Int,
        maxDepth: Int
    ) {
        if (currentDepth > maxDepth) return

        if (node.text != null && node.text.isNotEmpty()) {
            results.add(node.text.toString())
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectTextNodes(child, results, currentDepth + 1, maxDepth)
        }
    }

    /**
     * 抓取场馆信息
     */
    private fun extractVenue(rootNode: AccessibilityNodeInfo): String {
        // 查找包含"馆"、"场"、"厅"、"院"等关键字的节点
        val venueKeywords = listOf("体育馆", "体育场", "剧院", "音乐厅", "展览馆", "中心")
        
        for (keyword in venueKeywords) {
            val node = NodeUtils.findNodeByFuzzyText(rootNode, keyword, 0.7)
            if (node != null) {
                return node.text?.toString() ?: ""
            }
        }

        // 通过正则表达式匹配
        val allTexts = mutableListOf<String>()
        collectTextNodes(rootNode, allTexts, 0, 5)
        
        for (text in allTexts) {
            if (text.matches(Regex(".*[馆场厅院].*")) && text.length in 4..30) {
                return text
            }
        }

        return ""
    }

    /**
     * 抓取城市
     */
    private fun extractCity(rootNode: AccessibilityNodeInfo): String {
        // 常见城市列表
        val cities = listOf(
            "北京", "上海", "广州", "深圳", "杭州", "成都", "重庆", 
            "武汉", "西安", "南京", "苏州", "天津", "长沙", "郑州"
        )

        val allTexts = mutableListOf<String>()
        collectTextNodes(rootNode, allTexts, 0, 5)

        for (text in allTexts) {
            for (city in cities) {
                if (text.contains(city)) {
                    return city
                }
            }
        }

        return ""
    }

    /**
     * 抓取可选日期
     */
    private fun extractAvailableDates(rootNode: AccessibilityNodeInfo): List<String> {
        val dates = mutableListOf<String>()
        val datePattern = Regex("\\d{4}[-./年]\\d{1,2}[-./年]\\d{1,2}[日]?")
        val shortDatePattern = Regex("\\d{1,2}[-./月]\\d{1,2}[日]?")

        val allTexts = mutableListOf<String>()
        collectTextNodes(rootNode, allTexts, 0, 8)

        for (text in allTexts) {
            // 匹配完整日期格式
            datePattern.findAll(text).forEach { matchResult ->
                dates.add(matchResult.value)
            }
            
            // 匹配简短日期格式
            if (dates.isEmpty()) {
                shortDatePattern.findAll(text).forEach { matchResult ->
                    dates.add(matchResult.value)
                }
            }
        }

        return dates.distinct().take(10) // 去重并限制数量
    }

    /**
     * 抓取可选票价
     */
    private fun extractAvailablePrices(rootNode: AccessibilityNodeInfo): List<String> {
        val prices = mutableListOf<String>()
        
        // 匹配价格格式：¥xxx, xxx 元，RMBxxx
        val pricePattern = Regex("¥?\\d{2,5}元?")
        
        val allTexts = mutableListOf<String>()
        collectTextNodes(rootNode, allTexts, 0, 8)

        for (text in allTexts) {
            pricePattern.findAll(text).forEach { matchResult ->
                val priceText = matchResult.value
                // 过滤掉不合理的价格（如年份）
                val priceValue = priceText.replace(Regex("[¥元]"), "").toIntOrNull()
                if (priceValue != null && priceValue in 50..10000) {
                    prices.add(priceText)
                }
            }
        }

        return prices.distinct().sorted()
    }

    /**
     * 发送广播通知 MainActivity
     */
    fun broadcastConcertInfo(info: ConcertInfo) {
        val intent = Intent(ACTION_CONCERT_INFO_EXTRACTED).apply {
            putExtra(EXTRA_CONCERT_INFO, info)
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        Log.i(TAG, "已发送演出信息广播")
    }

    /**
     * 发送错误广播
     */
    fun broadcastError(errorMessage: String) {
        val intent = Intent(ACTION_CONCERT_INFO_EXTRACTED).apply {
            putExtra(EXTRA_ERROR_MESSAGE, errorMessage)
        }
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
        Log.e(TAG, "已发送错误广播：$errorMessage")
    }
}
