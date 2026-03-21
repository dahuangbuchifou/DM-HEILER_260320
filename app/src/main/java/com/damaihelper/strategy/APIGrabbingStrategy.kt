package com.damaihelper.strategy

import android.content.Context
import android.util.Log
import com.damaihelper.model.TicketTask
import com.damaihelper.network.DamaiApiClient
import com.damaihelper.network.SessionManager
import com.damaihelper.utils.AntiDetectionModule
import com.damaihelper.utils.PreciseTimeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * API 抢票策略 - 高性能模式
 * 
 * 优势：
 * - 毫秒级响应（比 UI 自动化快 10-50 倍）
 * - 无需界面交互，可后台运行
 * - 支持高频轮询
 * 
 * 风险：
 * - 容易被风控检测
 * - 需要有效的签名算法
 */
class APIGrabbingStrategy(private val context: Context) : TicketGrabbingStrategy {
    
    companion object {
        private const val TAG = "APIGrabbingStrategy"
        
        // 抢票超时时间（毫秒）
        private const val GRAB_TIMEOUT_MS = 3000L
        
        // 最大重试次数
        private const val MAX_RETRY_COUNT = 3
        
        // 重试间隔（毫秒）
        private const val RETRY_INTERVAL_MS = 100L
    }
    
    override val name: String = "API 抢票"
    override val priority: Int = 80
    
    private var status = StrategyStatus(name = name, isAvailable = true)
    private var lastGrabTime = 0L
    private var consecutiveFailures = 0
    
    // 延迟初始化 API 客户端
    private val sessionManager: SessionManager by lazy { SessionManager(context) }
    private val antiDetectionModule: AntiDetectionModule by lazy { AntiDetectionModule(context) }
    private val apiClient: DamaiApiClient by lazy { 
        DamaiApiClient(context, sessionManager, antiDetectionModule) 
    }
    
    override suspend fun grab(context: Context, task: TicketTask): GrabbingResult {
        val startTime = System.currentTimeMillis()
        lastGrabTime = startTime
        
        Log.i(TAG, "========== 开始 API 抢票 ==========")
        Log.i(TAG, "演出：${task.concertKeyword}")
        Log.i(TAG, "票价：${task.ticketPriceKeyword}")
        Log.i(TAG, "数量：${task.count}")
        
        return withContext(Dispatchers.IO) {
            try {
                // 1. 预检查
                val preCheckResult = preGrabCheck(task)
                if (!preCheckResult.success) {
                    consecutiveFailures++
                    updateStatus(false, startTime - System.currentTimeMillis())
                    return@withContext preCheckResult
                }
                
                // 2. 精确等待（如果还没到抢票时间）
                val currentTime = System.currentTimeMillis()
                val grabTime = task.grabTime.toString().toLongOrNull() ?: currentTime
                
                if (currentTime < grabTime) {
                    val waitTime = grabTime - currentTime
                    Log.d(TAG, "距离抢票还有 ${waitTime}ms，进入精确等待...")
                    
                    // 提前 500ms 开始准备
                    if (waitTime > 500) {
                        PreciseTimeManager.waitForMillis(grabTime - 500)
                    }
                }
                
                // 3. 执行抢票（带重试）
                var result: GrabbingResult? = null
                for (attempt in 1..MAX_RETRY_COUNT) {
                    Log.d(TAG, "抢票尝试 ${attempt}/${MAX_RETRY_COUNT}")
                    
                    result = executeAPIGrab(task)
                    
                    if (result.success) {
                        Log.i(TAG, "✅ API 抢票成功！")
                        consecutiveFailures = 0
                        updateStatus(true, System.currentTimeMillis() - startTime)
                        return@withContext result
                    }
                    
                    // 失败后短暂等待再重试
                    if (attempt < MAX_RETRY_COUNT && result.retryable) {
                        val backoffTime = RETRY_INTERVAL_MS * attempt + Random.nextLong(50)
                        Log.d(TAG, "重试等待 ${backoffTime}ms...")
                        kotlinx.coroutines.delay(backoffTime)
                    }
                }
                
                // 所有尝试都失败
                consecutiveFailures++
                updateStatus(false, System.currentTimeMillis() - startTime)
                
                Log.e(TAG, "❌ API 抢票失败：${result?.message}")
                result?.copy(
                    message = "API 抢票失败（${MAX_RETRY_COUNT}次尝试）: ${result.message}",
                    suggestedNextStrategy = "UI 自动化"
                ) ?: GrabbingResult(
                    success = false,
                    message = "API 抢票未知错误",
                    errorCode = 2003,
                    retryable = true,
                    suggestedNextStrategy = "UI 自动化"
                )
                
            } catch (e: Exception) {
                consecutiveFailures++
                Log.e(TAG, "API 抢票异常", e)
                updateStatus(false, System.currentTimeMillis() - startTime)
                
                GrabbingResult(
                    success = false,
                    message = "API 抢票异常：${e.message}",
                    errorCode = 2002,
                    retryable = consecutiveFailures < 3,
                    suggestedNextStrategy = "UI 自动化"
                )
            }
        }
    }
    
    /**
     * 抢票前检查
     */
    private suspend fun preGrabCheck(task: TicketTask): GrabbingResult {
        // 1. 检查登录状态
        if (!sessionManager.isLoggedIn()) {
            return GrabbingResult(
                success = false,
                message = "未登录，请先登录大麦账号",
                errorCode = 2010,
                retryable = false
            )
        }
        
        // 2. 检查访问令牌是否有效
        if (!sessionManager.isAccessTokenValid()) {
            try {
                Log.d(TAG, "访问令牌已过期，尝试刷新...")
                apiClient.refreshAccessToken()
            } catch (e: Exception) {
                return GrabbingResult(
                    success = false,
                    message = "令牌刷新失败，请重新登录",
                    errorCode = 2011,
                    retryable = false
                )
            }
        }
        
        // 3. 检查时间同步
        val timeSyncStatus = PreciseTimeManager.getTimeSyncStatus()
        if (!timeSyncStatus.isSynchronized) {
            Log.w(TAG, "⚠️ 时间未同步，可能影响抢票精度")
            // 不阻止抢票，但记录警告
        }
        
        // 4. 检查连续失败次数（熔断机制）
        if (consecutiveFailures >= 5) {
            Log.w(TAG, "⚠️ 连续失败 ${consecutiveFailures} 次，触发熔断")
            return GrabbingResult(
                success = false,
                message = "连续失败过多，建议切换策略",
                errorCode = 2020,
                retryable = false,
                suggestedNextStrategy = "UI 自动化"
            )
        }
        
        return GrabbingResult(success = true, message = "预检查通过")
    }
    
    /**
     * 执行 API 抢票核心逻辑
     */
    private suspend fun executeAPIGrab(task: TicketTask): GrabbingResult {
        val grabStart = System.currentTimeMillis()
        
        try {
            // 1. 获取演出详情（包含 performanceId）
            Log.d(TAG, "步骤 1: 获取演出详情...")
            val concertList = apiClient.getConcertList(keyword = task.concertKeyword)
            
            if (concertList.data.isEmpty()) {
                return GrabbingResult(
                    success = false,
                    message = "未找到演出：${task.concertKeyword}",
                    errorCode = 2030,
                    retryable = false
                )
            }
            
            val concert = concertList.data.first()
            Log.d(TAG, "找到演出：${concert.name}, ID: ${concert.concertId}")
            
            // 2. 获取演出详情（包含场次信息）
            Log.d(TAG, "步骤 2: 获取场次信息...")
            val concertDetail = apiClient.getConcertDetail(concert.concertId)
            
            // 3. 获取票价信息
            Log.d(TAG, "步骤 3: 获取票价信息...")
            val performance = concertDetail.performations.firstOrNull { 
                it.date.contains(task.grabDate) 
            } ?: concertDetail.performations.first()
            
            val priceInfo = apiClient.getPriceInfo(concert.concertId, performance.performanceId)
            
            // 4. 匹配目标票档
            Log.d(TAG, "步骤 4: 匹配票档：${task.ticketPriceKeyword}")
            val targetPrice = priceInfo.prices.find { price ->
                price.price.toString().contains(task.ticketPriceKeyword) ||
                price.name.contains(task.ticketPriceKeyword)
            } ?: priceInfo.prices.minByOrNull { 
                kotlin.math.abs(it.price - task.ticketPriceKeyword.toDoubleOrNull() ?: 0.0) 
            }
            
            if (targetPrice == null) {
                return GrabbingResult(
                    success = false,
                    message = "未找到匹配的票档：${task.ticketPriceKeyword}",
                    errorCode = 2031,
                    retryable = false
                )
            }
            
            Log.d(TAG, "目标票档：${targetPrice.name}, 价格：${targetPrice.price}, 库存：${targetPrice.available}")
            
            // 5. 检查库存
            if (targetPrice.available < task.count) {
                return GrabbingResult(
                    success = false,
                    message = "库存不足：剩余${targetPrice.available}, 需要${task.count}",
                    errorCode = 2032,
                    retryable = true
                )
            }
            
            // 6. 准备观演人信息
            Log.d(TAG, "步骤 5: 准备观演人信息...")
            val viewers = task.viewerNames.split(",").map { name ->
                DamaiApiClient.Viewer(
                    name = name.trim(),
                    idType = "身份证",
                    idNumber = "" // TODO: 从配置读取
                )
            }
            
            // 7. 创建订单
            Log.d(TAG, "步骤 6: 创建订单...")
            val orderResponse = apiClient.createOrder(
                concertId = concert.concertId,
                performanceId = performance.performanceId,
                priceId = targetPrice.priceId,
                quantity = task.count,
                viewers = viewers
            )
            
            Log.d(TAG, "订单创建成功：${orderResponse.orderId}")
            
            // 8. 提交订单
            Log.d(TAG, "步骤 7: 提交订单...")
            val submitResponse = apiClient.submitOrder(orderResponse.orderId)
            
            if (submitResponse.status == "SUCCESS" || submitResponse.status == "PENDING_PAYMENT") {
                val duration = System.currentTimeMillis() - grabStart
                Log.i(TAG, "🎉 抢票成功！订单号：${orderResponse.orderId}, 耗时：${duration}ms")
                
                return GrabbingResult(
                    success = true,
                    message = "抢票成功！订单号：${orderResponse.orderId}",
                    retryable = false,
                    timestamp = System.currentTimeMillis()
                )
            } else {
                return GrabbingResult(
                    success = false,
                    message = "订单提交失败：${submitResponse.status}",
                    errorCode = 2040,
                    retryable = true
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "API 抢票执行异常", e)
            return GrabbingResult(
                success = false,
                message = "API 执行异常：${e.message}",
                errorCode = 2050,
                retryable = true
            )
        }
    }
    
    override suspend fun isAvailable(context: Context): Boolean {
        // 检查网络状态
        val networkAvailable = isNetworkAvailable()
        
        // 检查登录状态
        val isLoggedIn = sessionManager.isLoggedIn()
        
        val available = networkAvailable && isLoggedIn
        
        if (!available) {
            Log.w(TAG, "API 策略不可用：network=$networkAvailable, login=$isLoggedIn")
        }
        
        return available
    }
    
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val networkInfo = connectivityManager?.activeNetworkInfo
        return networkInfo?.isConnected == true
    }
    
    override fun getStatus(): StrategyStatus {
        return status.copy(
            lastUsedTime = lastGrabTime,
            successCount = status.successCount,
            failureCount = status.failureCount,
            lastErrorMessage = if (consecutiveFailures > 0) "连续失败 $consecutiveFailures 次" else null
        )
    }
    
    private fun updateStatus(success: Boolean, duration: Long) {
        status = if (success) {
            status.copy(
                successCount = status.successCount + 1,
                averageResponseTime = ((status.averageResponseTime * status.successCount) + duration) / (status.successCount + 1)
            )
        } else {
            status.copy(failureCount = status.failureCount + 1)
        }
    }
}
