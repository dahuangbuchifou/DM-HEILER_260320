package com.damaihelper.network

import android.content.Context
import com.damaihelper.utils.AntiDetectionModule
import com.google.gson.Gson
import kotlinx.coroutines.delay
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 大麦网API客户端
 * 负责与大麦网API进行通信，包括登录、获取演出、创建订单等
 */
class DamaiApiClient(
    private val context: Context,
    private val sessionManager: SessionManager,
    private val antiDetectionModule: AntiDetectionModule
) {

    private val gson = Gson()
    private val random = Random(System.currentTimeMillis())

    // OkHttp客户端配置
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(RequestInterceptor())
        .addInterceptor(ResponseInterceptor())
        .build()

    // API基础URL
    private val baseUrl = "https://api.damai.cn"

    /**
     * 登录
     */
    suspend fun login(username: String, password: String): LoginResponse {
        val requestBody = mapOf(
            "username" to username,
            "password" to password,
            "timestamp" to System.currentTimeMillis().toString()
        )

        val response = executeRequest(
            method = "POST",
            path = "/api/user/login",
            body = requestBody,
            requiresAuth = false
        )

        val loginResponse = gson.fromJson(response, LoginResponse::class.java)

        // 保存会话信息
        sessionManager.saveAccessToken(loginResponse.accessToken, loginResponse.expiresIn)
        sessionManager.saveRefreshToken(loginResponse.refreshToken)
        sessionManager.saveUserInfo(
            SessionManager.UserInfo(
                userId = loginResponse.userId,
                username = username,
                email = loginResponse.email,
                phone = loginResponse.phone,
                realName = loginResponse.realName,
                // --- 修正点：补充 SessionManager.UserInfo 中缺失的参数 ---
                idType = null,  // 赋值为 null，因为 LoginResponse 中没有这些字段，且 UserInfo 允许可空
                idNumber = null,
                avatar = null
                // vipLevel 和 createdAt 有默认值，无需传入
            )
        )

        return loginResponse
    }

    /**
     * 刷新访问令牌
     */
    suspend fun refreshAccessToken(): String {
        val refreshToken = sessionManager.getRefreshToken() ?: throw Exception("刷新令牌不存在")

        val requestBody = mapOf(
            "refreshToken" to refreshToken,
            "timestamp" to System.currentTimeMillis().toString()
        )

        val response = executeRequest(
            method = "POST",
            path = "/api/user/refresh",
            body = requestBody,
            requiresAuth = false
        )

        val refreshResponse = gson.fromJson(response, RefreshTokenResponse::class.java)

        // 保存新的访问令牌和刷新令牌
        sessionManager.saveAccessToken(refreshResponse.accessToken, refreshResponse.expiresIn)
        sessionManager.saveRefreshToken(refreshResponse.refreshToken)

        return refreshResponse.accessToken
    }

    /**
     * 获取演出列表
     */
    suspend fun getConcertList(
        keyword: String = "",
        pageNum: Int = 1,
        pageSize: Int = 20
    ): ConcertListResponse {
        val queryParams = mapOf(
            "keyword" to keyword,
            "pageNum" to pageNum.toString(),
            "pageSize" to pageSize.toString()
        )

        val response = executeRequest(
            method = "GET",
            path = "/api/concert/list",
            queryParams = queryParams,
            requiresAuth = true
        )

        return gson.fromJson(response, ConcertListResponse::class.java)
    }

    /**
     * 获取演出详情
     */
    suspend fun getConcertDetail(concertId: String): ConcertDetailResponse {
        val queryParams = mapOf("concertId" to concertId)

        val response = executeRequest(
            method = "GET",
            path = "/api/concert/detail",
            queryParams = queryParams,
            requiresAuth = true
        )

        return gson.fromJson(response, ConcertDetailResponse::class.java)
    }

    /**
     * 获取票价信息
     */
    suspend fun getPriceInfo(
        concertId: String,
        performanceId: String
    ): PriceInfoResponse {
        val queryParams = mapOf(
            "concertId" to concertId,
            "performanceId" to performanceId
        )

        val response = executeRequest(
            method = "GET",
            path = "/api/concert/priceInfo",
            queryParams = queryParams,
            requiresAuth = true
        )

        return gson.fromJson(response, PriceInfoResponse::class.java)
    }

    /**
     * 创建订单
     */
    suspend fun createOrder(
        concertId: String,
        performanceId: String,
        priceId: String,
        quantity: Int,
        viewers: List<Viewer>,
        deliveryAddress: String? = null
    ): CreateOrderResponse {
        val requestBody = mapOf(
            "concertId" to concertId,
            "performanceId" to performanceId,
            "priceId" to priceId,
            "quantity" to quantity,
            "viewers" to viewers,
            "deliveryAddress" to (deliveryAddress ?: ""),
            "timestamp" to System.currentTimeMillis().toString()
        )

        val response = executeRequest(
            method = "POST",
            path = "/api/order/create",
            body = requestBody,
            requiresAuth = true
        )

        return gson.fromJson(response, CreateOrderResponse::class.java)
    }

    /**
     * 提交订单
     */
    suspend fun submitOrder(
        orderId: String,
        paymentMethod: String = "微信支付"
    ): SubmitOrderResponse {
        val requestBody = mapOf(
            "orderId" to orderId,
            "paymentMethod" to paymentMethod,
            "timestamp" to System.currentTimeMillis().toString()
        )

        val response = executeRequest(
            method = "POST",
            path = "/api/order/submit",
            body = requestBody,
            requiresAuth = true
        )

        return gson.fromJson(response, SubmitOrderResponse::class.java)
    }

    /**
     * 获取验证码
     */
    suspend fun getCaptcha(type: String, target: String): CaptchaResponse {
        val requestBody = mapOf(
            "type" to type,
            "target" to target,
            "timestamp" to System.currentTimeMillis().toString()
        )

        val response = executeRequest(
            method = "POST",
            path = "/api/captcha/get",
            body = requestBody,
            requiresAuth = true
        )

        return gson.fromJson(response, CaptchaResponse::class.java)
    }

    /**
     * 验证验证码
     */
    suspend fun verifyCaptcha(captchaId: String, code: String): VerifyCaptchaResponse {
        val requestBody = mapOf(
            "captchaId" to captchaId,
            "code" to code,
            "timestamp" to System.currentTimeMillis().toString()
        )

        val response = executeRequest(
            method = "POST",
            path = "/api/captcha/verify",
            body = requestBody,
            requiresAuth = true
        )

        return gson.fromJson(response, VerifyCaptchaResponse::class.java)
    }

    /**
     * 执行HTTP请求（带重试和错误处理）
     */
    private suspend fun executeRequest(
        method: String,
        path: String,
        body: Any? = null,
        queryParams: Map<String, String>? = null,
        requiresAuth: Boolean = true,
        retryCount: Int = 0,
        maxRetries: Int = 3
    ): String {
        try {
            // 如果需要认证且访问令牌已过期，刷新令牌
            if (requiresAuth && !sessionManager.isAccessTokenValid()) {
                refreshAccessToken()
            }

            // 构建请求
            val url = buildUrl(path, queryParams)
            val requestBuilder = when (method.uppercase()) {
                "GET" -> Request.Builder().get()
                "POST" -> {
                    val jsonBody = if (body != null) {
                        gson.toJson(body)
                    } else {
                        "{}"
                    }
                    val mediaType = "application/json".toMediaTypeOrNull()
                        ?: throw IllegalArgumentException("Invalid media type")
                    Request.Builder().post(
                        jsonBody.toRequestBody(mediaType)
                    )
                }
                "PUT" -> {
                    val jsonBody = if (body != null) {
                        gson.toJson(body)
                    } else {
                        "{}"
                    }
                    val mediaType = "application/json".toMediaTypeOrNull()
                        ?: throw IllegalArgumentException("Invalid media type")
                    Request.Builder().put(
                        jsonBody.toRequestBody(mediaType)
                    )
                }
                "DELETE" -> Request.Builder().delete()
                else -> throw IllegalArgumentException("不支持的HTTP方法: $method")
            }
            requestBuilder.url(url)

            // 添加请求头
            val headers = antiDetectionModule.generateRandomHeaders()
            for ((key, value) in headers) {
                requestBuilder.addHeader(key, value)
            }

            // 添加认证令牌
            if (requiresAuth) {
                val accessToken = sessionManager.getAccessToken()
                if (accessToken != null) {
                    requestBuilder.addHeader("Authorization", "Bearer $accessToken")
                }
            }

            // 添加其他关键请求头
            requestBuilder.addHeader("X-Timestamp", System.currentTimeMillis().toString())
            requestBuilder.addHeader("X-Device-Id", generateDeviceId())

            val request = requestBuilder.build()

            // 执行请求
            val response = httpClient.newCall(request).execute()

            // 处理响应
            if (response.isSuccessful) {
                return response.body?.string() ?: "{}"
            } else if (response.code == 401) {
                // 未授权，尝试刷新令牌
                if (retryCount < maxRetries) {
                    refreshAccessToken()
                    delay(100 + random.nextLong(100))
                    return executeRequest(
                        method, path, body, queryParams, requiresAuth,
                        retryCount + 1, maxRetries
                    )
                } else {
                    throw Exception("认证失败，无法刷新令牌")
                }
            } else if (response.code == 429) {
                // 限流，等待后重试
                if (retryCount < maxRetries) {
                    val delayMs = (1000 * Math.pow(2.0, retryCount.toDouble())).toLong()
                    delay(delayMs + random.nextLong(1000))
                    return executeRequest(
                        method, path, body, queryParams, requiresAuth,
                        retryCount + 1, maxRetries
                    )
                } else {
                    throw Exception("请求被限流，超过最大重试次数")
                }
            } else {
                throw Exception("API请求失败: ${response.code} ${response.message}")
            }
        } catch (e: Exception) {
            // 网络错误或其他异常，重试
            if (retryCount < maxRetries) {
                val delayMs = (1000 * Math.pow(2.0, retryCount.toDouble())).toLong()
                delay(delayMs + random.nextLong(1000))
                return executeRequest(
                    method, path, body, queryParams, requiresAuth,
                    retryCount + 1, maxRetries
                )
            } else {
                throw e
            }
        }
    }

    /**
     * 构建完整的URL
     */
    private fun buildUrl(path: String, queryParams: Map<String, String>?): String {
        var url = baseUrl + path
        if (!queryParams.isNullOrEmpty()) {
            url += "?" + queryParams.entries.joinToString("&") { (k, v) ->
                "$k=$v"
            }
        }
        return url
    }

    /**
     * 生成设备ID
     */
    private fun generateDeviceId(): String {
        // 由于 android.os.Build.SERIAL 在一些新设备上可能需要权限或为空，这里简单地结合时间戳。
        // 如果您有更可靠的设备ID获取方法，请替换。
        return "DM_ANDROID_" + System.currentTimeMillis()
    }

    /**
     * 请求拦截器
     */
    private inner class RequestInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            // 可以在这里添加额外的请求处理逻辑
            return chain.proceed(originalRequest)
        }
    }

    /**
     * 响应拦截器
     */
    private inner class ResponseInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val response = chain.proceed(chain.request())
            // 可以在这里添加额外的响应处理逻辑
            return response
        }
    }

    // ==================== 数据类 ====================

    data class LoginResponse(
        val userId: String,
        val username: String,
        val email: String?,
        val phone: String?,
        val realName: String?,
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Long,
        val tokenType: String = "Bearer"
    )

    data class RefreshTokenResponse(
        val accessToken: String,
        val refreshToken: String,
        val expiresIn: Long,
        val tokenType: String = "Bearer"
    )

    data class ConcertListResponse(
        val total: Int,
        val pageNum: Int,
        val pageSize: Int,
        val data: List<Concert>
    )

    data class Concert(
        val concertId: String,
        val name: String,
        val artist: String,
        val venue: String,
        val startTime: Long,
        val endTime: Long,
        val imageUrl: String?,
        val status: String // "未开始", "进行中", "已结束"
    )

    data class ConcertDetailResponse(
        val concertId: String,
        val name: String,
        val artist: String,
        val venue: String,
        val startTime: Long,
        val endTime: Long,
        val imageUrl: String?,
        val description: String?,
        val performances: List<Performance>
    )

    data class Performance(
        val performanceId: String,
        val date: String,
        val time: String,
        val status: String
    )

    data class PriceInfoResponse(
        val concertId: String,
        val performanceId: String,
        val prices: List<Price>
    )

    data class Price(
        val priceId: String,
        val name: String,
        val price: Double,
        val quantity: Int,
        val available: Int
    )

    data class CreateOrderResponse(
        val orderId: String,
        val concertId: String,
        val performanceId: String,
        val totalPrice: Double,
        val status: String
    )

    data class SubmitOrderResponse(
        val orderId: String,
        val paymentUrl: String?,
        val status: String
    )

    data class CaptchaResponse(
        val captchaId: String,
        val type: String,
        val imageUrl: String?,
        val expiresIn: Long
    )

    data class VerifyCaptchaResponse(
        val captchaId: String,
        val verified: Boolean,
        val token: String?
    )

    data class Viewer(
        val name: String,
        val idType: String = "身份证",
        val idNumber: String = ""
    )
}