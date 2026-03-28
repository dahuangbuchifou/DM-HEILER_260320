// ============================================================================
// 📅 修复日期：2026-03-20
// 🔧 修复内容：
//   - 添加页面检测容错处理
//   - 优化演出信息抓取逻辑
//   - 增强空指针安全检查
//   - 改进倒计时和抢票流程日志
// 📝 说明：核心无障碍服务 - 自动抢票、答题、验证码处理
// ============================================================================

package com.damaihelper.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.damaihelper.captcha.CaptchaCoordinatorService
import com.damaihelper.model.TicketTask
import com.damaihelper.network.DamaiApiClient
import com.damaihelper.network.SessionManager
import com.damaihelper.utils.*
import com.damaihelper.model.ConcertInfo
import com.damaihelper.strategy.GrabbingResult
import kotlinx.coroutines.*
import kotlin.random.Random

class TicketGrabbingAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TicketGrabbing"
        private var instance: TicketGrabbingAccessibilityService? = null

        fun getInstance(): TicketGrabbingAccessibilityService? = instance
    }

    // 页面类型枚举
    private enum class PageType {
        DETAIL,          // 详情页
        SKU_SELECTION,   // 票档选择页
        TOO_MANY_PEOPLE, // 人太多弹窗
        CAPTCHA,         // 验证码
        PAYMENT,         // 支付页（成功）
        UNKNOWN          // 未知
    }

    // 任务状态
    private enum class TaskPhase {
        CREATED,
        WAITING,
        PREPARING,
        READY,
        COUNTDOWN,
        GRABBING,
        COMPLETED,
        FAILED
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())
    private var currentTask: TicketTask? = null
    private var grabbingJob: Job? = null
    private var currentPhase: TaskPhase = TaskPhase.CREATED

    // 核心服务实例
    private lateinit var humanBehaviorSimulator: HumanBehaviorSimulator
    private lateinit var questionAnsweringService: QuestionAnsweringService
    private lateinit var damaiApiClient: DamaiApiClient
    private lateinit var sessionManager: SessionManager
    private lateinit var questionBankManager: QuestionBankManager
    private lateinit var antiDetectionModule: AntiDetectionModule
    private lateinit var tooManyPeopleHandler: TooManyPeopleHandler
    private lateinit var concertInfoExtractor: ConcertInfoExtractor
    private var isExtracting = false // 防止重复抓取
    private val captchaCoordinator = CaptchaCoordinatorService(this)

    override fun onCreate() {
        super.onCreate()
        instance = this

        humanBehaviorSimulator = HumanBehaviorSimulator()
        sessionManager = SessionManager(this)
        questionBankManager = QuestionBankManager(this)
        antiDetectionModule = AntiDetectionModule(this)
        tooManyPeopleHandler = TooManyPeopleHandler(this)

        questionAnsweringService = QuestionAnsweringService(questionBankManager, humanBehaviorSimulator)
        damaiApiClient = DamaiApiClient(this, sessionManager, antiDetectionModule)

        // 初始化信息抓取器
        concertInfoExtractor = ConcertInfoExtractor(this)

        coroutineScope.launch {
            PreciseTimeManager.initialize()
        }

        Log.d(TAG, "TicketGrabbingAccessibilityService 已创建")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.packageName != "cn.damai") return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val rootNode = rootInActiveWindow ?: return

            val tooManyPeopleInfo = tooManyPeopleHandler.detectTooManyPeopleScenario(rootNode)
            if (tooManyPeopleInfo != null) {
                coroutineScope.launch {
                    tooManyPeopleHandler.executeHighFrequencyClicking(
                        this@TicketGrabbingAccessibilityService,
                        tooManyPeopleInfo
                    )
                }
                return
            }

            val questionInfo = questionAnsweringService.extractQuestionInfo(rootNode)
            if (questionInfo != null && questionInfo.correctAnswer != null) {
                coroutineScope.launch {
                    if (questionAnsweringService.autoSelectAnswer(rootNode, questionInfo)) {
                        questionAnsweringService.submitAnswer(rootNode)
                    }
                }
                return
            }
        }
    }

    override fun onInterrupt() {
        grabbingJob?.cancel()
        Log.d(TAG, "服务被中断")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        coroutineScope.cancel()
        captchaCoordinator.cleanup()
        Log.d(TAG, "服务已销毁")
    }

    /**
     * 供 UIAutomationStrategy 调用的核心 UI 自动化执行方法
     */
    fun executeUIAutomationGrabbing(
        task: TicketTask,
        btnBuyText: String,
        btnConfirmText: String,
        btnSubmitOrderText: String,
        btnAddQuantityId: String
    ): GrabbingResult {
        grabbingJob = coroutineScope.launch {
            try {
                Log.i(TAG, "========== 开始 UI 自动化抢票流程 ==========")
                performGrabbing(task, btnBuyText, btnConfirmText, btnSubmitOrderText, btnAddQuantityId)
                Log.i(TAG, "========== UI 自动化抢票流程完成 ==========")
            } catch (e: Exception) {
                Log.e(TAG, "UI 自动化抢票流程异常", e)
                currentPhase = TaskPhase.FAILED
            } finally {
                currentTask = null
            }
        }

        return GrabbingResult(success = true, message = "UI 自动化流程已启动，请查看日志", retryable = false)
    }

    fun startGrabbing(task: TicketTask) {
        if (grabbingJob?.isActive == true) {
            Log.w(TAG, "抢票任务已在运行中")
            return
        }
        currentTask = task
        grabbingJob = coroutineScope.launch {
            try {
                Log.i(TAG, "========== 开始抢票任务 ==========")
                Log.i(TAG, "演出：${task.concertKeyword}")
                Log.i(TAG, "日期：${task.grabDate}")
                Log.i(TAG, "票价：${task.ticketPriceKeyword}")

                // ✅ 1. 打开大麦 App
                Log.i(TAG, "步骤 1: 打开大麦 App...")
                launchDamaiApp()
                delay(2000)  // 等待 App 启动

                // ✅ 2. 搜索演出
                Log.i(TAG, "步骤 2: 搜索演出 \'${task.concertKeyword}\'...")
                searchConcert(task.concertKeyword)
                delay(3000)  // 等待搜索结果

                // ✅ 3. 进入详情页并选择票档
                Log.i(TAG, "步骤 3: 选择票档 \'${task.selectedPrice}\'...")
                selectTicketPrice(task.selectedPrice)
                delay(2000)

                // ✅ 4. 选择观众
                Log.i(TAG, "步骤 4: 选择观众 \'${task.audienceName}\'...")
                selectAudience(task.audienceName)
                delay(2000)

                // ✅ 5. 提交订单
                Log.i(TAG, "步骤 5: 提交订单...")
                submitOrder()

                Log.i(TAG, "========== 抢票任务完成 ==========")
            } catch (e: Exception) {
                Log.e(TAG, "抢票任务异常", e)
                currentPhase = TaskPhase.FAILED
            } finally {
                currentTask = null
            }
        }
    }


    /**
     * 打开大麦 App
     */
    private suspend fun launchDamaiApp() {
        try {
            val intent = packageManager.getLaunchIntentForPackage("cn.damai")
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                Log.i(TAG, "✅ 大麦 App 已打开")
            } else {
                Log.e(TAG, "❌ 未安装大麦 App")
            }
        } catch (e: Exception) {
            Log.e(TAG, "打开大麦 App 失败", e)
        }
    }

    /**
     * 搜索演出
     */
    private suspend fun searchConcert(keyword: String) {
        try {
            // 1. 等待大麦 App 完全加载
            delay(1000)
            
            // 2. 查找搜索框（大麦 App 首页顶部通常有搜索框）
            val searchNode = findNodeByText("搜索") ?: findNodeByText("搜索演出") ?: findNodeByText(keyword)
            
            if (searchNode != null) {
                searchNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Log.i(TAG, "✅ 点击搜索框")
                delay(500)
                
                // 3. 输入关键词（需要输入法支持）
                // 由于无障碍服务无法直接输入文字，需要用户手动输入
                Log.i(TAG, "⚠️ 请在搜索框输入：$keyword")
                
                // 4. 等待用户输入后，查找搜索按钮
                delay(3000)
                
                // 5. 点击搜索结果
                val resultNode = findNodeByText(keyword)
                if (resultNode != null) {
                    resultNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.i(TAG, "✅ 点击搜索结果：$keyword")
                }
            } else {
                Log.w(TAG, "⚠️ 未找到搜索框，请手动搜索")
            }
        } catch (e: Exception) {
            Log.e(TAG, "搜索演出失败", e)
        }
    }
    
    /**
     * 根据文本查找节点
     */
    private fun findNodeByText(text: String): AccessibilityNodeInfo? {
        val rootNode = rootInActiveWindow ?: return null
        return findNodeByTextRecursive(rootNode, text)
    }
    
    private fun findNodeByTextRecursive(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true) {
            return node
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByTextRecursive(child, text)
            if (result != null) {
                return result
            }
        }
        return null
    }

    /**
     * 选择票档
     */
    private suspend fun selectTicketPrice(price: String) {
        // TODO: 实现票档选择逻辑
        Log.i(TAG, "🎫 选择票档：$price (待实现)")
    }

    /**
     * 选择观众
     */
    private suspend fun selectAudience(name: String) {
        // TODO: 实现观众选择逻辑
        Log.i(TAG, "👤 选择观众：$name (待实现)")
    }

    /**
     * 提交订单
     */
    private suspend fun submitOrder() {
        // TODO: 实现订单提交逻辑
        Log.i(TAG, "📝 提交订单 (待实现)")
    }

    fun stopGrabbing() {
        grabbingJob?.cancel()
        currentTask = null
        Log.d(TAG, "抢票任务已停止")
    }

    /**
     * 核心抢票流程（反应堆模式）
     */
    private suspend fun performGrabbing(
        task: TicketTask,
        btnBuyText: String,
        btnConfirmText: String,
        btnSubmitOrderText: String,
        btnAddQuantityId: String
    ) {
        try {
            val grabTimeMillis = if (task.grabTime is Long) {
                task.grabTime
            } else {
                task.grabTime.toString().toLongOrNull()
                    ?: throw IllegalArgumentException("抢票时间格式错误: ${task.grabTime}")
            }

            // 阶段 1：准备
            preparePhase(task)

            // 阶段 2：就绪等待
            readyPhase(grabTimeMillis)

            // 阶段 3：倒计时
            countdownPhase(grabTimeMillis)

            // 阶段 4：反应堆抢票
            grabbingPhase(task, grabTimeMillis, btnBuyText, btnConfirmText, btnSubmitOrderText, btnAddQuantityId)

        } catch (e: Exception) {
            Log.e(TAG, "抢票流程失败", e)
            currentPhase = TaskPhase.FAILED
            throw e
        }
    }

    /**
     * 阶段 1：准备（搜索、进入详情页）
     */
    private suspend fun preparePhase(task: TicketTask) {
        Log.i(TAG, "")
        Log.i(TAG, "========================================")
        Log.i(TAG, "阶段 [1/4]: 准备阶段")
        Log.i(TAG, "========================================")
        currentPhase = TaskPhase.PREPARING

        if (!openDamaiApp()) {
            throw IllegalStateException("无法打开App")
        }
        humanBehaviorSimulator.simulateThinkingTime(3000L, 5000L)

        navigateToSearchPage()
        humanBehaviorSimulator.simulateThinkingTime(800L, 1500L)

        if (!searchConcertWithHumanBehavior(task.concertKeyword)) {
            throw IllegalStateException("搜索失败")
        }
        humanBehaviorSimulator.simulateThinkingTime(1500L, 2500L)

        if (!enterConcertDetail(task.concertKeyword)) {
            throw IllegalStateException("未找到演出")
        }
        humanBehaviorSimulator.simulateThinkingTime(2000L, 3000L)

        Log.i(TAG, "✓ 准备完成，进入就绪状态")
    }

    /**
     * 阶段 2：就绪等待（持续监控 + A3 滑动）
     */
    private suspend fun readyPhase(grabTime: Long) {
        Log.i(TAG, "")
        Log.i(TAG, "========================================")
        Log.i(TAG, "阶段 [2/4]: 就绪等待")
        Log.i(TAG, "========================================")
        currentPhase = TaskPhase.READY

        val countdownStart = grabTime - 10 * 1000
        var lastScrollTime = 0L

        while (System.currentTimeMillis() < countdownStart) {
                // ✅ 修复：分屏模式下检测大麦 App（2026-03-28）
                // 问题：分屏时焦点可能在助手 App 上，导致检测到 com.damaihelper
                // 方案：先检查当前窗口，如果是助手自己，尝试查找分屏中的大麦窗口
                val rootNode = findDamaiRootNode()
                if (rootNode == null) {
                    val currentPackage = rootInActiveWindow?.packageName?.toString() ?: "未知"
                    concertInfoExtractor.broadcastError(
                        "当前不在大麦 App 中\n" +
                                "检测到：$currentPackage\n" +
                                "请打开大麦 App 的演出详情页\n" +
                                "提示：分屏模式下请确保大麦 App 已打开"
                    )
                    return
                }

                val currentPackage = rootNode.packageName?.toString() ?: ""
                Log.i(TAG, "当前应用包名：$currentPackage")

                // 检查是否是大麦相关应用
                if (!isDamaiApp(currentPackage)) {
                    concertInfoExtractor.broadcastError(
                        "当前不在大麦 App 中\n" +
                                "检测到：$currentPackage\n" +
                                "请打开大麦 App 的演出详情页"
                    )
                    return
                }
            try {
                if (rootNode != null && detectCurrentPage(rootNode) != PageType.DETAIL) {
                    Log.w(TAG, "⚠️ 页面变化！尝试返回...")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    delay(1000)
                }
            } finally {
                // Android 会自动回收，不需要手动 recycle
            }

            val now = System.currentTimeMillis()
            if (now - lastScrollTime > Random.nextLong(20000, 40000)) {
                performHumanLikeScroll()
                lastScrollTime = now
            }

            val remaining = (grabTime - System.currentTimeMillis()) / 1000
            Log.d(TAG, "✓ 就绪等待中，距开抢 ${remaining}秒")
            delay(5000)
        }

        Log.i(TAG, "进入最后 10 秒倒计时")
    }

    /**
     * 阶段 3：倒计时（最后 10 秒）
     */
    private suspend fun countdownPhase(grabTime: Long) {
        Log.i(TAG, "")
        Log.i(TAG, "========================================")
        Log.i(TAG, "阶段 [3/4]: 倒计时（最后 10 秒）")
        Log.i(TAG, "========================================")
        currentPhase = TaskPhase.COUNTDOWN

        val endTime = grabTime - 1000
        while (System.currentTimeMillis() < endTime) {
            val remaining = (grabTime - System.currentTimeMillis()) / 1000.0
            Log.d(TAG, "⏰ 倒计时: ${String.format("%.1f", remaining)}秒")
            delay(1000)
        }

        Log.i(TAG, "🔥 准备开抢！")
    }

    /**
     * 阶段 4：反应堆抢票（核心） - UI 自动化版本
     */
    private suspend fun grabbingPhase(
        task: TicketTask,
        grabTime: Long,
        btnBuyText: String,
        btnConfirmText: String,
        btnSubmitOrderText: String,
        btnAddQuantityId: String
    ) {
        Log.i(TAG, "")
        Log.i(TAG, "========================================")
        Log.i(TAG, "阶段 [4/4]: 反应堆抢票 (UI 自动化)")
        Log.i(TAG, "========================================")
        currentPhase = TaskPhase.GRABBING

        // 1. 抢购按钮点击循环
        val clickBuyButtonJob = coroutineScope.launch {
            while (isActive) {
                val rootNode = rootInActiveWindow ?: continue
                val buyButton = NodeUtils.findNodeByFuzzyText(rootNode, btnBuyText, 0.9)
                if (buyButton != null) {
                    Log.i(TAG, "🚀 发现并点击 [${btnBuyText}] 按钮")
                    humanBehaviorSimulator.clickWithRandomOffset(buyButton, this@TicketGrabbingAccessibilityService)
                    break
                }
                delay(Random.nextLong(50, 150))
            }
        }
        clickBuyButtonJob.join()
        humanBehaviorSimulator.simulateThinkingTime(200L, 500L)

        // 2. 票档选择与确认
        Log.i(TAG, "进入票档选择阶段...")
        delay(humanBehaviorSimulator.simulateRandomDelay(800L, 1500L))

        // 2.1 选择票档
        val selectTicketJob = coroutineScope.launch {
            while (isActive) {
                val rootNode = rootInActiveWindow ?: continue
                val priceNode = NodeUtils.findNodeByFuzzyText(rootNode, task.ticketPriceKeyword, 0.8)
                if (priceNode != null) {
                    Log.i(TAG, "✅ 发现并点击目标票价: [${task.ticketPriceKeyword}]")
                    humanBehaviorSimulator.clickWithRandomOffset(priceNode, this@TicketGrabbingAccessibilityService)
                    break
                }
                delay(Random.nextLong(100, 300))
            }
        }
        selectTicketJob.join()
        humanBehaviorSimulator.simulateThinkingTime(100L, 300L)

        // 2.2 增加数量 (如果需要)
        val quantity = getTicketQuantity(task)
        if (quantity > 1) {
            Log.i(TAG, "开始增加数量到 ${quantity}...")
            for (i in 1 until quantity) {
                val rootNode = rootInActiveWindow ?: continue
                val addButton = findNodeById(rootNode, btnAddQuantityId)
                if (addButton != null) {
                    humanBehaviorSimulator.clickWithRandomOffset(addButton, this@TicketGrabbingAccessibilityService)
                    delay(humanBehaviorSimulator.simulateRandomDelay(80L, 200L))
                } else {
                    Log.w(TAG, "⚠️ 未找到数量增加按钮 ID: [${btnAddQuantityId}]")
                    break
                }
            }
        }

        // 2.3 点击确认按钮
        Log.i(TAG, "点击确认按钮...")
        val confirmJob = coroutineScope.launch {
            while (isActive) {
                val rootNode = rootInActiveWindow ?: continue
                val confirmButton = NodeUtils.findNodeByFuzzyText(rootNode, btnConfirmText, 0.9)
                if (confirmButton != null) {
                    Log.i(TAG, "✅ 发现并点击确认按钮: [${btnConfirmText}]")
                    humanBehaviorSimulator.clickWithRandomOffset(confirmButton, this@TicketGrabbingAccessibilityService)
                    break
                }
                delay(Random.nextLong(100, 300))
            }
        }
        confirmJob.join()
        humanBehaviorSimulator.simulateThinkingTime(300L, 800L)

        // 3. 提交订单
        Log.i(TAG, "进入订单提交阶段...")
        delay(humanBehaviorSimulator.simulateRandomDelay(1500L, 3000L))

        val submitOrderJob = coroutineScope.launch {
            while (isActive) {
                val rootNode = rootInActiveWindow ?: continue
                val submitButton = NodeUtils.findNodeByFuzzyText(rootNode, btnSubmitOrderText, 0.9)
                if (submitButton != null) {
                    Log.i(TAG, "🔥 发现并点击提交订单按钮: [${btnSubmitOrderText}]")
                    humanBehaviorSimulator.clickWithRandomOffset(submitButton, this@TicketGrabbingAccessibilityService)
                    break
                }
                delay(Random.nextLong(50, 150))
            }
        }
        submitOrderJob.join()
        humanBehaviorSimulator.simulateThinkingTime(500L, 1500L)

        // 4. 最终状态检查
        Log.i(TAG, "订单已提交，等待支付页面...")
        delay(humanBehaviorSimulator.simulateRandomDelay(4000L, 7000L))

        Log.i(TAG, "UI 自动化流程执行完毕，请检查是否进入支付页面。")
    }

    /**
     * 辅助方法：安全获取票数量
     */
    private fun getTicketQuantity(task: TicketTask): Int {
        return try {
            val field = task::class.java.getDeclaredField("quantity")
            field.isAccessible = true
            field.getInt(task)
        } catch (e: Exception) {
            1 // 默认数量为 1
        }
    }

    /**
     * 辅助方法：通过 ID 查找节点
     */
    private fun findNodeById(rootNode: AccessibilityNodeInfo, resourceId: String): AccessibilityNodeInfo? {
        if (rootNode.viewIdResourceName == resourceId) {
            return rootNode
        }

        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChild(i) ?: continue
            val found = findNodeById(child, resourceId)
            if (found != null) {
                return found
            }
        }

        return null
    }

    // ==================== 页面检测 ====================

    private fun detectCurrentPage(rootNode: AccessibilityNodeInfo): PageType {
        if (NodeUtils.findNodeByFuzzyText(rootNode, "支付宝|微信支付|确认支付", 0.9) != null) {
            return PageType.PAYMENT
        }

        if (NodeUtils.findNodeByFuzzyText(rootNode, "人太多|火爆|前方拥挤", 0.9) != null) {
            return PageType.TOO_MANY_PEOPLE
        }

        if (detectCaptchaNode(rootNode) != null) {
            return PageType.CAPTCHA
        }

        if (findSubmitButton(rootNode) != null ||
            NodeUtils.findNodeByFuzzyText(rootNode, "选择观众|观演人", 0.9) != null) {
            return PageType.SKU_SELECTION
        }

        if (findBuyButton(rootNode) != null ||
            NodeUtils.findNodeByFuzzyText(rootNode, "客服|详情|演出介绍", 0.9) != null) {
            return PageType.DETAIL
        }

        return PageType.UNKNOWN
    }

    private fun findBuyButton(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return NodeUtils.findNodeByFuzzyText(rootNode, "立即购买|抢购|抢票", 0.9)
    }

    private fun findSubmitButton(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return NodeUtils.findNodeByFuzzyText(rootNode, "提交订单|确认", 0.9)
    }

    // ==================== 快速选择方法 ====================

    private suspend fun selectTicketFast(priceKeyword: String) {
        val rootNode = rootInActiveWindow ?: return
        val priceNode = NodeUtils.findNodeByFuzzyText(rootNode, priceKeyword, 0.8)
        if (priceNode != null) {
            Log.d(TAG, "✓ 找到票价: $priceKeyword")
            humanBehaviorSimulator.clickWithRandomOffset(priceNode, this@TicketGrabbingAccessibilityService)
            delay(Random.nextLong(100, 300))
        }
    }

    private suspend fun selectViewersFast(viewerNames: String) {
        val rootNode = rootInActiveWindow ?: return
        val viewers = viewerNames.split(",").map { it.trim() }

        for (name in viewers) {
            val viewerNode = NodeUtils.findNodeByFuzzyText(rootNode, name, 0.9)
            if (viewerNode != null) {
                Log.d(TAG, "✓ 找到观众: $name")
                humanBehaviorSimulator.clickWithRandomOffset(viewerNode, this@TicketGrabbingAccessibilityService)
                delay(Random.nextLong(50, 150))
            }
        }
    }

    // ==================== A3: 模拟滑动 ====================

    private fun performHumanLikeScroll() {
        Log.d(TAG, "[A3] 模拟滑动")

        val isScrollDown = Random.nextBoolean()
        val distance = Random.nextInt(100, 300)
        val duration = Random.nextLong(300, 800)

        val startY = if (isScrollDown) 1000f else 1500f
        val endY = startY + (if (isScrollDown) distance else -distance)

        val path = android.graphics.Path()
        path.moveTo(540f, startY)
        path.lineTo(540f, endY)

        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(
                path, 0, duration
            ))
            .build()

        dispatchGesture(gesture, null, null)
    }

    // ==================== App 打开相关 ====================

    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            applicationContext.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            try {
                applicationContext.packageManager.getLaunchIntentForPackage(packageName) != null
            } catch (e2: Exception) {
                false
            }
        }
    }

    private fun openDamaiApp(): Boolean {
        val packages = listOf(
            "cn.damai" to "大麦App",
            "com.taobao.taobao" to "手机淘宝"
        )

        for ((pkg, name) in packages) {
            if (isAppInstalled(pkg)) {
                try {
                    val intent = applicationContext.packageManager.getLaunchIntentForPackage(pkg)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        applicationContext.startActivity(intent)
                        Log.i(TAG, "✓ 成功启动: $name")
                        return true
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "× 启动失败: $name", e)
                }
            }
        }
        return false
    }

    // ==================== UI 操作相关 ====================

    private suspend fun navigateToSearchPage(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val searchBox = NodeUtils.findNodeByClassName(rootNode, "android.widget.EditText")
        if (searchBox != null) {
            humanBehaviorSimulator.clickWithRandomOffset(searchBox, this@TicketGrabbingAccessibilityService)
            delay(1000)
            return true
        }
        return false
    }

    private suspend fun searchConcertWithHumanBehavior(keyword: String): Boolean {
        delay(500)
        val rootNode = rootInActiveWindow ?: return false
        val searchBox = NodeUtils.findNodeByClassName(rootNode, "android.widget.EditText") ?: return false

        humanBehaviorSimulator.inputText(searchBox, keyword)
        Log.i(TAG, "✓ 已输入: $keyword")
        delay(500)

        val searchButton = NodeUtils.findNodeByFuzzyText(rootNode, "搜索", 0.9)
        if (searchButton != null) {
            humanBehaviorSimulator.clickWithRandomOffset(searchButton, this@TicketGrabbingAccessibilityService)
        }

        return true
    }

    private suspend fun enterConcertDetail(keyword: String): Boolean {
        delay(2000)
        val rootNode = rootInActiveWindow ?: return false
        val concertItem = NodeUtils.findNodeByFuzzyText(rootNode, keyword, 0.7)
        if (concertItem != null) {
            Log.i(TAG, "✓ 找到演出")
            humanBehaviorSimulator.clickWithRandomOffset(concertItem, this@TicketGrabbingAccessibilityService)
            return true
        }
        return false
    }

    // ==================== 验证码处理 ====================

    private suspend fun handleCaptchaIfPresent() {
        Log.w(TAG, "验证码处理功能待实现")
        // TODO: 集成 CaptchaCoordinatorService
    }

    // ==================== M4 模块：演出信息抓取功能 ====================

    /**
     * 供外部调用的信息抓取方法
     */
    fun extractTaskInfoFromDamaiPage(): Map<String, String> {
        val rootNode = rootInActiveWindow ?: return emptyMap()
        if (rootNode.packageName != "cn.damai") {
            Log.w(TAG, "当前不在大麦 App 界面，无法提取信息")
            return emptyMap()
        }

        val extractedInfo = mutableMapOf<String, String>()

        // 1. 提取演出名称 (通常是页面顶部最大的 TextView)
        // 尝试通过 ID 或最大的文本节点来获取
        val titleNode = NodeUtils.findNodeByFuzzyText(rootNode, "演出", 0.1) // 尝试找一个大的文本节点
        extractedInfo["concertKeyword"] = titleNode?.text?.toString() ?: "未知演出"

        // 2. 提取抢票日期和时间 (通常在详情页的特定区域)
        // 这是一个难点，需要依赖特定的文本格式或 ID。这里使用通用文本匹配。
        val dateNode = NodeUtils.findNodeByFuzzyText(rootNode, "场次", 0.5)
        val dateText = dateNode?.parent?.getChild(dateNode.parent.childCount - 1)?.text?.toString()
        extractedInfo["grabDate"] = dateText ?: "未知日期"

        // 3. 提取票价信息 (通常在详情页的特定区域)
        val priceNode = NodeUtils.findNodeByFuzzyText(rootNode, "价格", 0.5)
        val priceText = priceNode?.parent?.getChild(priceNode.parent.childCount - 1)?.text?.toString()
        extractedInfo["ticketPriceKeyword"] = priceText ?: "未知票价"

        Log.i(TAG, "M4 模块：提取到的信息: $extractedInfo")
        return extractedInfo
    }

    private fun detectCaptchaNode(rootNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val keywords = listOf("验证码", "captcha", "verification")
        for (kw in keywords) {
            val node = NodeUtils.findNodeByFuzzyText(rootNode, kw, 0.7)
            if (node != null) return node
        }
        return null
    }

    // ==================== ✅ 演出信息抓取功能（优化版） ====================

    /**
     * 供外部调用的信息抓取方法（优化版）
     */
    fun startExtractingConcertInfo() {
        if (isExtracting) {
            Log.w(TAG, "正在抓取中，请勿重复操作")
            concertInfoExtractor.broadcastError("正在抓取中，请稍候...")
            return
        }

        isExtracting = true
        Log.i(TAG, "========== 开始抓取演出信息 ==========")

        coroutineScope.launch {
            try {
                delay(500) // 等待页面稳定

                // ✅ 修复：分屏模式下检测大麦 App（2026-03-28）
                // 问题：分屏时焦点可能在助手 App 上，导致检测到 com.damaihelper
                // 方案：先检查当前窗口，如果是助手自己，尝试查找分屏中的大麦窗口
                val rootNode = findDamaiRootNode()
                if (rootNode == null) {
                    val currentPackage = rootInActiveWindow?.packageName?.toString() ?: "未知"
                    concertInfoExtractor.broadcastError(
                        "当前不在大麦 App 中\n" +
                                "检测到：$currentPackage\n" +
                                "请打开大麦 App 的演出详情页\n" +
                                "提示：分屏模式下请确保大麦 App 已打开"
                    )
                    return@launch
                }

                val currentPackage = rootNode.packageName?.toString() ?: ""
                Log.i(TAG, "当前应用包名：$currentPackage")

                // 检查是否是大麦相关应用
                if (!isDamaiApp(currentPackage)) {
                    concertInfoExtractor.broadcastError(
                        "当前不在大麦 App 中\n" +
                                "检测到：$currentPackage\n" +
                                "请打开大麦 App 的演出详情页"
                    )
                    return@launch
                }
                // ✅ 优化：增强页面检测，降低严格度
                if (!isOnDamaiDetailPage(rootNode)) {
                    // 输出当前页面的所有文本用于调试
                    dumpPageText(rootNode)

                    concertInfoExtractor.broadcastError(
                        "请确保已进入演出详情页\n" +
                                "提示：页面需要包含「立即购买」或「演出介绍」等元素"
                    )
                    return@launch
                }

                Log.i(TAG, "✅ 页面检测通过，开始抓取信息...")

                // 抓取信息
                val info = concertInfoExtractor.extractFromDetailPage(rootNode!!)

                if (info != null) {
                    concertInfoExtractor.broadcastConcertInfo(info)
                    Log.i(TAG, "✅ 演出信息抓取成功")
                } else {
                    concertInfoExtractor.broadcastError("信息抓取失败，请确保页面已完全加载")
                }

            } catch (e: Exception) {
                Log.e(TAG, "抓取异常", e)
                concertInfoExtractor.broadcastError("抓取异常: ${e.message}")
            } finally {
                isExtracting = false
            }
        }
    }

    /**
     * ✅ 新增：检查是否是大麦相关应用
     */

    /**
     * ✅ 修复：分屏模式下查找大麦 App 的根节点（2026-03-28）
     * 问题：分屏时焦点可能在助手 App 上，rootInActiveWindow 返回的是助手窗口
     * 方案：遍历所有窗口，查找大麦 App 的窗口
     */
    private fun findDamaiRootNode(): AccessibilityNodeInfo? {
        // 方案 1: 先检查当前活动窗口
        val activeRoot = rootInActiveWindow
        val activePackage = activeRoot?.packageName?.toString() ?: ""
        
        // 如果当前窗口就是大麦，直接返回
        if (isDamaiApp(activePackage)) {
            Log.i(TAG, "当前窗口是大麦 App: $activePackage")
            return activeRoot
        }
        
        // 方案 2: 当前窗口是助手自己，尝试查找分屏中的大麦窗口（API 30+）
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                val windows = windows
                Log.d(TAG, "分屏检测：找到 ${windows.size} 个窗口")
                
                for (window in windows) {
                    val windowRoot = window.root
                    val windowPackage = windowRoot?.packageName?.toString() ?: ""
                    Log.d(TAG, "窗口包名：$windowPackage")
                    
                    if (isDamaiApp(windowPackage)) {
                        Log.i(TAG, "分屏中找到大麦 App: $windowPackage")
                        activeRoot?.recycle() // 回收之前的根节点
                        return windowRoot
                    }
                    windowRoot?.recycle()
                }
            } catch (e: Exception) {
                Log.w(TAG, "分屏检测失败：${e.message}")
            }
        }
        
        // 方案 3: 当前窗口是助手自己，且找不到分屏大麦，返回 null 让上层报错
        if (activePackage == packageName) {
            Log.w(TAG, "当前窗口是助手自己，未找到分屏大麦")
            activeRoot?.recycle()
            return null
        }
        
        // 其他情况返回当前活动窗口
        return activeRoot
    }

    private fun isDamaiApp(packageName: String): Boolean {
        val damaiPackages = listOf(
            "cn.damai",           // 大麦主应用
            "com.taobao.taobao",  // 淘宝（可能内嵌大麦）
            "com.tmall.wireless", // 天猫
            "com.alibaba.android.rimet" // 钉钉（可能内嵌）
        )

        val isDamai = damaiPackages.any { packageName.contains(it) }
        Log.i(TAG, "包名检测: $packageName -> ${if (isDamai) "是大麦相关" else "非大麦应用"}")

        return isDamai
    }

    /**
     * ✅ 优化：检查是否在大麦详情页（降低严格度）
     */
    private fun isOnDamaiDetailPage(rootNode: AccessibilityNodeInfo): Boolean {
        // 方案1: 查找购买按钮
        val buyButton = NodeUtils.findNodeByFuzzyText(rootNode, "立即购买|抢购|想看", 0.7)
        if (buyButton != null) {
            Log.i(TAG, "✅ 检测到购买按钮")
            return true
        }

        // 方案2: 查找详情页特征文本
        val detailIndicators = listOf(
            "演出介绍", "购票须知", "场馆", "时间", "票档",
            "选择场次", "选择票价", "客服"
        )

        for (indicator in detailIndicators) {
            val node = NodeUtils.findNodeByFuzzyText(rootNode, indicator, 0.7)
            if (node != null) {
                Log.i(TAG, "✅ 检测到特征文本: $indicator")
                return true
            }
        }

        // 方案3: 降低匹配度再试一次
        val relaxedButton = NodeUtils.findNodeByFuzzyText(rootNode, "购买", 0.5)
        if (relaxedButton != null) {
            Log.i(TAG, "✅ 检测到购买相关按钮（宽松匹配）")
            return true
        }

        Log.w(TAG, "❌ 未检测到详情页特征元素")
        return false
    }

    /**
     * ✅ 新增：调试用 - 输出页面所有文本
     */
    private fun dumpPageText(rootNode: AccessibilityNodeInfo, depth: Int = 0, maxDepth: Int = 5) {
        if (depth > maxDepth) return

        val indent = "  ".repeat(depth)

        // 输出当前节点信息
        if (rootNode.text != null && rootNode.text.isNotEmpty()) {
            Log.d(TAG, "${indent}📄 文本: ${rootNode.text}")
        }

        if (rootNode.contentDescription != null && rootNode.contentDescription.isNotEmpty()) {
            Log.d(TAG, "${indent}📝 描述: ${rootNode.contentDescription}")
        }

        // 遍历子节点
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChild(i)
            if (child != null) {
                dumpPageText(child, depth + 1, maxDepth)
            }
        }
    }

    // ==================== 🆕 新增：3 步自动抢票流程 ====================

    /**
     * 🆕 执行 3 步自动抢票流程
     */
    suspend fun executeAutoGrabbing(task: TicketTask): Boolean {
        Log.i(TAG, "========== 开始执行 3 步自动抢票 ==========")
        
        try {
            // 步骤 1：点击"立即预订"
            Log.i(TAG, "【步骤 1/3】点击立即预订")
            if (!step1_ClickBuyNow()) {
                Log.e(TAG, "❌ 步骤 1 失败")
                return false
            }
            delay(1000)

            // 步骤 2：选择最贵票档并确认
            Log.i(TAG, "【步骤 2/3】选择最贵票档")
            if (!step2_SelectMostExpensiveTicket(task.selectedPrice)) {
                Log.e(TAG, "❌ 步骤 2 失败")
                return false
            }
            delay(1000)

            // 步骤 3：选择观演人并提交订单
            Log.i(TAG, "【步骤 3/3】选择观演人并提交")
            if (!step3_SelectAudienceAndSubmit(task)) {
                Log.e(TAG, "❌ 步骤 3 失败")
                return false
            }

            Log.i(TAG, "========== 自动抢票完成 ==========")
            return true

        } catch (e: Exception) {
            Log.e(TAG, "自动抢票异常", e)
            return false
        }
    }

    private suspend fun step1_ClickBuyNow(): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val node = rootNode.findAccessibilityNodeInfosByText("立即预订").firstOrNull() 
            ?: rootNode.findAccessibilityNodeInfosByText("立即购买").firstOrNull()
            ?: return false
        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.i(TAG, "✅ 步骤 1 完成")
        return true
    }

    private suspend fun step2_SelectMostExpensiveTicket(preferredPrice: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        
        if (preferredPrice.isNotEmpty()) {
            val node = rootNode.findAccessibilityNodeInfosByText(preferredPrice).firstOrNull()
            node?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            delay(300)
        }
        
        val confirmNode = rootNode.findAccessibilityNodeInfosByText("确定").firstOrNull() ?: return false
        confirmNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.i(TAG, "✅ 步骤 2 完成")
        return true
    }

    private suspend fun step3_SelectAudienceAndSubmit(task: TicketTask): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        
        val audienceName = task.audienceName.ifEmpty { "张健夫" }
        val audienceNode = rootNode.findAccessibilityNodeInfosByText(audienceName).firstOrNull()
        audienceNode?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        delay(300)
        
        val submitNode = rootNode.findAccessibilityNodeInfosByText("立即提交").firstOrNull() ?: return false
        submitNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        Log.i(TAG, "✅ 步骤 3 完成：订单已提交")
        return true
    }

}
