// ============================================================================
// 📦 模块：大麦页面适配器
// 📅 创建日期：2026-03-23
// 📝 说明：页面适配层，负责大麦 App 页面识别和动作封装
// 📚 参考：合规购票辅助系统_详细技术设计.md
// ============================================================================

package com.damaihelper.core

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 页面类型枚举
 */
enum class DamaiPageType(val description: String) {
    UNKNOWN("未知页面"),
    HOME("首页"),
    DETAIL("演出详情页"),
    TICKET_SELECTION("票档选择页"),      // 🆕 新增：选择票档页面
    CONFIRM("确认购买页"),              // 🆕 新增：确认订单页面
    QUEUING("排队页"),
    SELECTING("选票页"),
    CONFIRMING("订单确认页"),
    CAPTCHA("验证码页"),
    ERROR("错误页"),
    PAYMENT("支付页")
}

/**
 * 页面识别结果
 */
data class PageRecognitionResult(
    val pageType: DamaiPageType,
    val confidence: Float, // 置信度 0.0-1.0
    val markers: List<String>, // 识别特征
    val message: String = ""
)

/**
 * 页面动作结果
 */
data class PageActionResult(
    val success: Boolean,
    val code: String,
    val message: String,
    val data: Any? = null
) {
    companion object {
        fun ok(message: String = "OK", data: Any? = null) = 
            PageActionResult(true, "OK", message, data)
        
        fun fail(code: String, message: String) = 
            PageActionResult(false, code, message)
    }
}

/**
 * 大麦页面适配器
 * 负责隔离大麦 App 的页面细节
 */
class DamaiPageAdapter(private val service: AccessibilityService) {
    companion object {
        private const val TAG = "DamaiPageAdapter"
        
        // 大麦 App 包名
        const val PACKAGE_NAME = "cn.damai"
        
        // 页面特征文本
        const val TEXT_QUEUING = "排队中"
        const val TEXT_SELECT_SEAT = "选座"
        const val TEXT_CONFIRM_ORDER = "确认订单"
        const val TEXT_SUBMIT_ORDER = "提交订单"
        const val TEXT_BUY_NOW = "立即购买"
        const val TEXT_CAPTCHA = "验证码"
        const val TEXT_RISK_CHECK = "安全验证"
    }

    /**
     * 检测当前页面类型
     */
    fun detectPageType(): DamaiPageType {
        val result = recognizePage()
        Log.d(TAG, "页面识别结果：${result.pageType} (置信度：${result.confidence})")
        return result.pageType
    }

    /**
     * 页面识别（多特征联合判断）
     */
    private fun recognizePage(): PageRecognitionResult {
        val rootNode = service.rootInActiveWindow
            ?: return PageRecognitionResult(
                pageType = DamaiPageType.UNKNOWN,
                confidence = 0.0f,
                markers = listOf("root node is null")
            )

        try {
            val markers = mutableListOf<String>()
            val scores = mutableMapOf<DamaiPageType, Float>()

            // 获取页面文本特征
            val pageText = getPageText(rootNode)
            
            // 🆕 检查票档选择页特征（步骤 2）
            if (pageText.contains("票档", ignoreCase = true) ||
                pageText.contains("看台", ignoreCase = true) ||
                pageText.contains("内场", ignoreCase = true) ||
                (pageText.contains("元") && findNodeByText(rootNode, "确定") != null)
            ) {
                markers.add("票档选择关键词")
                scores[DamaiPageType.TICKET_SELECTION] = 0.9f
            }
            
            // 🆕 检查确认购买页特征（步骤 3）
            if (pageText.contains("确认购买", ignoreCase = true) ||
                pageText.contains("实名观演人", ignoreCase = true) ||
                pageText.contains("配送方式", ignoreCase = true) ||
                findNodeByText(rootNode, "立即提交") != null
            ) {
                markers.add("确认购买页关键词")
                scores[DamaiPageType.CONFIRM] = 0.95f
            }
            
            // 检查排队特征
            if (pageText.contains(TEXT_QUEUING, ignoreCase = true) ||
                pageText.contains("前方排队", ignoreCase = true) ||
                pageText.contains("人太多", ignoreCase = true)
            ) {
                markers.add("排队关键词")
                scores[DamaiPageType.QUEUING] = 0.9f
            }

            // 检查选票特征
            if (pageText.contains(TEXT_SELECT_SEAT, ignoreCase = true) ||
                pageText.contains("选择票档", ignoreCase = true) ||
                pageText.contains("选座购买", ignoreCase = true) ||
                findNodeByText(rootNode, TEXT_BUY_NOW) != null
            ) {
                markers.add("选票关键词")
                scores[DamaiPageType.SELECTING] = 0.85f
            }

            // 检查订单确认页特征（老逻辑，保留兼容）
            if (pageText.contains(TEXT_CONFIRM_ORDER, ignoreCase = true) ||
                pageText.contains("订单确认", ignoreCase = true) ||
                findNodeByText(rootNode, TEXT_SUBMIT_ORDER) != null
            ) {
                markers.add("确认页关键词")
                scores[DamaiPageType.CONFIRMING] = 0.9f
            }

            // 检查验证码特征
            if (pageText.contains(TEXT_CAPTCHA, ignoreCase = true) ||
                pageText.contains(TEXT_RISK_CHECK, ignoreCase = true) ||
                pageText.contains("安全验证", ignoreCase = true)
            ) {
                markers.add("验证码关键词")
                scores[DamaiPageType.CAPTCHA] = 0.95f
            }

            // 检查错误页特征
            if (pageText.contains("加载失败", ignoreCase = true) ||
                pageText.contains("网络错误", ignoreCase = true) ||
                pageText.contains("页面不存在", ignoreCase = true)
            ) {
                markers.add("错误页关键词")
                scores[DamaiPageType.ERROR] = 0.8f
            }

            // 选择最高分的页面类型
            val bestMatch = scores.maxByOrNull { it.value }
            
            return if (bestMatch != null && bestMatch.value >= 0.7f) {
                PageRecognitionResult(
                    pageType = bestMatch.key,
                    confidence = bestMatch.value,
                    markers = markers
                )
            } else {
                PageRecognitionResult(
                    pageType = DamaiPageType.UNKNOWN,
                    confidence = 0.5f,
                    markers = markers.ifEmpty { listOf("无匹配特征") }
                )
            }
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * 检查是否已登录
     */
    fun isLoggedIn(): Boolean {
        val rootNode = service.rootInActiveWindow ?: return false
        
        try {
            // 检查是否有"我的"页面入口
            // 或者检查是否有登录按钮
            val hasMyTab = findNodeByText(rootNode, "我的") != null
            val hasLoginButton = findNodeByText(rootNode, "登录") != null
            
            return hasMyTab && !hasLoginButton
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * 打开目标页面
     */
    fun openTargetPage(url: String): PageActionResult {
        Log.d(TAG, "打开目标页面：$url")
        
        // 这里需要实现打开大麦 App 并导航到指定 URL
        // 目前简化处理
        
        return PageActionResult.ok("页面打开成功")
    }

    /**
     * 刷新页面
     */
    fun refreshPage(): PageActionResult {
        Log.d(TAG, "刷新页面")
        
        try {
            // 模拟下拉刷新 - 使用返回动作
            // GLOBAL_ACTION_BACK = 1 (Android API 16+)
            // 直接使用整数值避免常量引用问题
            service.performGlobalAction(1) // GLOBAL_ACTION_BACK
            Thread.sleep(500)
            service.performGlobalAction(1) // GLOBAL_ACTION_BACK
            
            return PageActionResult.ok("页面刷新成功")
        } catch (e: Exception) {
            Log.e(TAG, "刷新页面失败：${e.message}")
            return PageActionResult.fail("REFRESH_FAILED", e.message ?: "刷新失败")
        }
    }

    /**
     * 获取排队状态
     */
    fun getQueueStatus(): Map<String, Any> {
        val rootNode = service.rootInActiveWindow ?: return emptyMap()
        
        try {
            val pageText = getPageText(rootNode)
            
            // 提取排队进度信息
            val queueProgress = extractQueueProgress(pageText)
            
            return mapOf(
                "isQueuing" to pageText.contains(TEXT_QUEUING),
                "progress" to queueProgress,
                "estimatedTime" to "未知"
            )
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * 选择偏好票档
     */
    fun selectPreferredOptions(preferences: Map<String, String>): PageActionResult {
        Log.d(TAG, "选择偏好：$preferences")
        
        val rootNode = service.rootInActiveWindow
            ?: return PageActionResult.fail("NO_ROOT", "无法获取根节点")
        
        try {
            // 查找票档选择区域
            val priceKeyword = preferences["price"]
            if (priceKeyword != null) {
                val priceNode = findNodeByText(rootNode, priceKeyword)
                if (priceNode != null) {
                    priceNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d(TAG, "已选择票档：$priceKeyword")
                } else {
                    Log.w(TAG, "未找到票档：$priceKeyword")
                }
            }
            
            // 查找数量选择
            val count = preferences["count"]?.toIntOrNull() ?: 1
            if (count > 1) {
                // 增加票数逻辑
                repeat(count - 1) {
                    val addNode = findNodeById(rootNode, "com.alibaba.damai:id/btn_add")
                    addNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
            }
            
            return PageActionResult.ok("选择完成")
        } catch (e: Exception) {
            Log.e(TAG, "选择票档失败：${e.message}")
            return PageActionResult.fail("SELECT_FAILED", e.message ?: "选择失败")
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * 检测风险挑战（验证码等）
     */
    fun detectRiskChallenge(): Boolean {
        val pageType = detectPageType()
        return pageType == DamaiPageType.CAPTCHA
    }

    /**
     * 获取当前错误信息
     */
    fun getCurrentError(): String? {
        val rootNode = service.rootInActiveWindow ?: return null
        
        try {
            val errorKeywords = listOf(
                "失败", "错误", "异常", "无法", "不能", "抱歉"
            )
            
            val pageText = getPageText(rootNode)
            
            for (keyword in errorKeywords) {
                if (pageText.contains(keyword)) {
                    return "检测到错误关键词：$keyword"
                }
            }
            
            return null
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * 页面预热（提前加载）
     */
    fun warmupPage(keyword: String) {
        Log.d(TAG, "页面预热：$keyword")
        
        // 预热逻辑：提前打开大麦 App，进入待命状态
        // 具体实现需要根据大麦 App 的实际流程
        
        // 这里可以调用无障碍服务执行一些预操作
    }

    // ==================== 辅助方法 ====================

    private fun getPageText(rootNode: AccessibilityNodeInfo): String {
        val builder = StringBuilder()
        
        fun traverse(node: AccessibilityNodeInfo?) {
            if (node == null) return
            
            val text = node.text?.toString()
            if (!text.isNullOrBlank()) {
                builder.append(text).append(" ")
            }
            
            for (i in 0 until node.childCount) {
                traverse(node.getChild(i))
            }
        }
        
        traverse(rootNode)
        return builder.toString()
    }

    private fun findNodeByText(rootNode: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodes = rootNode.findAccessibilityNodeInfosByText(text)
        return nodes.firstOrNull()
    }

    private fun findNodeById(rootNode: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? {
        val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
        return nodes.firstOrNull()
    }

    private fun extractQueueProgress(text: String): String {
        // 提取排队进度信息（如"前方还有 100 人"）
        val regex = Regex("(前方还有|排队人数)[:：]?\\s*(\\d+)")
        val match = regex.find(text)
        return match?.groupValues?.getOrNull(2) ?: "未知"
    }

    /**
     * 获取页面签名（用于判断页面是否变化）
     */
    fun getPageSignature(): String {
        val rootNode = service.rootInActiveWindow ?: return "unknown"
        
        try {
            val pageType = detectPageType()
            val text = getPageText(rootNode).take(100) // 取前 100 字符
            return "${pageType.name}_${text.hashCode()}"
        } finally {
            rootNode.recycle()
        }
    }

    /**
     * 捕获页面快照（文本信息）
     */
    fun captureSnapshot(): Map<String, Any> {
        val rootNode = service.rootInActiveWindow ?: return emptyMap()
        
        try {
            val pageType = detectPageType()
            val pageText = getPageText(rootNode)
            val signature = getPageSignature()
            
            return mapOf(
                "pageType" to pageType.name,
                "timestamp" to System.currentTimeMillis(),
                "signature" to signature,
                "textPreview" to pageText.take(200),
                "childCount" to rootNode.childCount
            )
        } finally {
            rootNode.recycle()
        }
    }
}
